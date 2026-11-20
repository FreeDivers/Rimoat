package org.qinyan.rimoat.hardware

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/** Learning and transmission must never claim the same dongle simultaneously. */
internal object UsbSessionAccess {
    val mutex = Mutex()
    class LearningSession {
        val released = CompletableDeferred<Unit>()
        @Volatile var cancelling = false
    }
    private val learning = AtomicReference<LearningSession?>(null)

    fun beginLearning(): LearningSession = LearningSession().also { learning.set(it) }
    fun cancelLearning(session: LearningSession) { session.cancelling = true }
    fun finishLearning(session: LearningSession) {
        learning.compareAndSet(session, null)
        session.released.complete(Unit)
    }
    suspend fun awaitCancelledLearning() {
        val session = learning.get() ?: return
        if (session.cancelling) withTimeoutOrNull(2_000) { session.released.await() }
    }
}
