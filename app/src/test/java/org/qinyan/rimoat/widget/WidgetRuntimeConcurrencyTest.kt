package org.qinyan.rimoat.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.IrRemoteApplication
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
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

    @Test fun cancelledPerformPushesInterruptedUpdateAndReleasesLock() {
        val app = RuntimeEnvironment.getApplication() as IrRemoteApplication
        try {
            val profile = RemoteProfile(
                id = "tv_cancel",
                name = "Cancel TV",
                category = DeviceCategory.TV,
                buttons = listOf(RemoteButton("power", "Power", iconName = "power", rawPattern = listOf(9000, 4500, 560)))
            )
            val shortcut = WidgetShortcut(profile.id, "power")
            val updatedWidgets = CopyOnWriteArrayList<Triple<Int, Boolean, WidgetFeedback?>>()
            val busyEntered = CompletableDeferred<Unit>()
            val allowContinue = CompletableDeferred<Unit>()
            val finallyActive = CompletableDeferred<Boolean>()

            runBlocking {
                app.repository.awaitLoaded()
                app.repository.saveRemote(profile)
                app.widgets.awaitReady()
                app.widgets.save(11, WidgetConfiguration(WidgetKind.SINGLE, profile.id, listOf(shortcut)))
                app.widgets.updateWidgetById = { widgetId ->
                    // Suspend in the busy update so cancellation happens while perform holds actionMutex.
                    val feedback = app.widgets.feedback.value[widgetId]
                    updatedWidgets += Triple(widgetId, currentCoroutineContext().isActive, feedback)
                    if (feedback?.busy == true) {
                        busyEntered.complete(Unit)
                        allowContinue.await()
                    } else if (feedback?.error == true) {
                        delay(1)
                        finallyActive.complete(currentCoroutineContext().isActive)
                    }
                }

                withTimeout(5_000) {
                    val job = launch(Dispatchers.Default) {
                        app.widgets.perform(11, shortcut)
                    }
                    busyEntered.await()
                    job.cancel()
                    job.join()
                    assertTrue("finally update must run inside NonCancellable", finallyActive.await())
                }

                val interrupted = app.widgets.feedback.value[11]
                assertNotNull(interrupted)
                assertTrue(interrupted!!.error)
                assertFalse(interrupted.busy)
                assertEquals(org.qinyan.rimoat.i18n.UiText.Resource(R.string.widget_interrupted), interrupted.text)
                assertFalse(app.widgets.state.value.sending)

                // Verify the mutex was unlocked and a follow-up command succeeds cleanly.
                app.widgets.updateWidgetById = {}
                app.widgets.perform(11, shortcut)
                assertNull(app.widgets.feedback.value[11])
            }
        } finally {
            app.transmitterManager.release()
            app.learnerManager.unregisterReceiver()
            runBlocking { app.applicationScope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    @Test fun concurrentPerformMarksGlobalSendingAndRefreshesSecondWidget() {
        val app = RuntimeEnvironment.getApplication() as IrRemoteApplication
        try {
            val profile = RemoteProfile(
                id = "tv_busy",
                name = "Busy TV",
                category = DeviceCategory.TV,
                buttons = listOf(RemoteButton("power", "Power", iconName = "power", rawPattern = listOf(9000, 4500, 560)))
            )
            val shortcut = WidgetShortcut(profile.id, "power")
            val firstBusy = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val secondSawSending = CompletableDeferred<Boolean>()
            val fullUpdatesDuringPerform = AtomicInteger(0)

            runBlocking {
                app.repository.awaitLoaded()
                app.repository.saveRemote(profile)
                app.widgets.awaitReady()
                app.widgets.save(21, WidgetConfiguration(WidgetKind.SINGLE, profile.id, listOf(shortcut)))
                app.widgets.save(22, WidgetConfiguration(WidgetKind.SINGLE, profile.id, listOf(shortcut)))
                // Wait for any initial debounced snapshot updates to settle.
                delay(150)

                app.widgets.updateAllWidgets = {
                    fullUpdatesDuringPerform.incrementAndGet()
                }
                app.widgets.updateWidgetById = { widgetId ->
                    if (widgetId == 21 && app.widgets.feedback.value[21]?.busy == true) {
                        firstBusy.complete(Unit)
                        releaseFirst.await()
                    } else if (widgetId == 22) {
                        secondSawSending.complete(app.widgets.state.value.sending)
                    }
                }

                withTimeout(5_000) {
                    val first = async(Dispatchers.Default) { app.widgets.perform(21, shortcut) }
                    firstBusy.await()
                    assertTrue(app.widgets.state.value.sending)
                    assertEquals("perform must only update the active widget, not call updateAll", 0, fullUpdatesDuringPerform.get())

                    app.widgets.perform(22, shortcut)
                    assertTrue("Rejected second widget must refresh with global sending=true", secondSawSending.await())
                    assertNull("Rejected widget does not queue a command or overwrite error feedback", app.widgets.feedback.value[22])

                    releaseFirst.complete(Unit)
                    first.await()
                }

                assertFalse(app.widgets.state.value.sending)
                assertNull(app.widgets.feedback.value[21])
            }
        } finally {
            app.transmitterManager.release()
            app.learnerManager.unregisterReceiver()
            runBlocking { app.applicationScope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    @Test fun configurationChangedNoOpAndRapidUpdatesAreCoalesced() {
        val app = RuntimeEnvironment.getApplication() as IrRemoteApplication
        try {
            val updateAllCount = AtomicInteger(0)
            runBlocking {
                app.repository.awaitLoaded()
                app.widgets.awaitReady()
                delay(150)
                app.widgets.updateDebounceMs = 40L
                app.widgets.updateAllWidgets = {
                    updateAllCount.incrementAndGet()
                }

                // Calling configurationChanged repeatedly without locale/fontScale change must not emit updateAll.
                repeat(5) {
                    app.widgets.configurationChanged()
                }
                delay(120)
                assertEquals(0, updateAllCount.get())

                // Burst of repository changes must coalesce into far fewer updateAll calls.
                repeat(6) { idx ->
                    app.repository.saveRemote(
                        RemoteProfile(
                            id = "burst_$idx",
                            name = "Burst $idx",
                            category = DeviceCategory.TV,
                            buttons = listOf(RemoteButton("power", "Power", rawPattern = listOf(9000, 4500, 560)))
                        )
                    )
                }
                delay(200)
                assertTrue("Expected coalesced updateAll calls (actual=${updateAllCount.get()})",
                    updateAllCount.get() in 1..2)
            }
        } finally {
            app.transmitterManager.release()
            app.learnerManager.unregisterReceiver()
            runBlocking { app.applicationScope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }
}
