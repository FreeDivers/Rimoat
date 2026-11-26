package org.qinyan.rimoat.widget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.IrRemoteApplication
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = IrRemoteApplication::class, shadows = [PickerMetadataShadow::class])
class WidgetRuntimeConcurrencyTest {
    @Test fun concurrentStartNeverLeaksDuplicateObservationsOrCorruptsCache() {
        val app = RuntimeEnvironment.getApplication() as IrRemoteApplication
        try {
            val profiles = (1..80).map { index ->
                RemoteProfile(
                    id = "remote_$index",
                    name = "Remote $index",
                    category = DeviceCategory.TV,
                    buttons = (1..16).map { btn ->
                        RemoteButton(
                            id = "btn_${index}_$btn",
                            label = "Button $btn",
                            rawPattern = listOf(9000, 4500, 560)
                        )
                    }
                )
            }
            runBlocking {
                app.repository.awaitLoaded()
                profiles.forEach { app.repository.saveRemote(it) }
                app.widgets.awaitReady()
            }

            // 1) Concurrent start() race: unsynchronized read-cancel-write on `observation`
            //    leaves orphaned observation coroutines alive inside applicationScope.
            // 2) Concurrent start() while mapLatest iterates `cachedCommands` triggers
            //    ConcurrentModificationException on the plain HashMap, putting state into error.
            val failures = AtomicInteger(0)
            val appJob = app.applicationScope.coroutineContext[Job]!!
            // Count baseline children (shortcuts collector etc.) before blasting start().
            val baselineChildren = appJob.children.count()

            val threads = 8
            val gate = CountDownLatch(1)
            val workers = (1..threads).map { worker ->
                thread(name = "widget-start-race-$worker") {
                    gate.await(5, TimeUnit.SECONDS)
                    repeat(80) { iter ->
                        app.widgets.start()
                        if (app.widgets.state.value.error != null) failures.incrementAndGet()
                        if (iter % 10 == 0) Thread.yield()
                    }
                }
            }
            gate.countDown()
            workers.forEach { it.join(15_000) }

            // Also test interleaved start() + profile removal/addition to trigger retainAll vs put.
            runBlocking(Dispatchers.Default) {
                val starter = async {
                    repeat(60) {
                        app.widgets.start()
                        if (app.widgets.state.value.error != null) failures.incrementAndGet()
                        delay(1)
                    }
                }
                val mutator = async {
                    repeat(20) { iter ->
                        val temp = RemoteProfile(
                            id = "temp_$iter",
                            name = "Temp $iter",
                            category = DeviceCategory.TV,
                            buttons = listOf(RemoteButton("p", "Power", rawPattern = listOf(9000, 4500, 560)))
                        )
                        app.repository.saveRemote(temp)
                        app.repository.deleteRemote(temp.id)
                    }
                }
                listOf(starter, mutator).awaitAll()
            }

            // Wait for any cancelled jobs to finish and the active observation to settle.
            runBlocking {
                val deadline = System.currentTimeMillis() + 5_000
                while (System.currentTimeMillis() < deadline) {
                    if (app.widgets.state.value.error != null) {
                        failures.incrementAndGet()
                        break
                    }
                    if (appJob.children.count() <= baselineChildren &&
                        app.widgets.state.value.commands.size == profiles.size
                    ) break
                    delay(20)
                }
            }

            val finalState = app.widgets.state.value
            val activeChildren = appJob.children.count()
            assertNull("WidgetRuntime must not enter error state: ${finalState.error}", finalState.error)
            assertEquals("No transient error states expected", 0, failures.get())
            assertTrue(
                "Only one WidgetRuntime observation coroutine may stay active (baseline=$baselineChildren, actual=$activeChildren)",
                activeChildren <= baselineChildren
            )
            assertEquals(profiles.size, finalState.commands.size)
        } finally {
            app.transmitterManager.release()
            app.learnerManager.unregisterReceiver()
            runBlocking { app.applicationScope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }
}
