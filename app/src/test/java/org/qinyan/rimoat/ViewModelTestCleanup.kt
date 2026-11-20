package org.qinyan.rimoat

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestDispatcher

/** Real IO may resume after advanceUntilIdle; keep Main installed until cancellation completes. */
@OptIn(ExperimentalCoroutinesApi::class)
fun clearViewModelsAndAwait(store: ViewModelStore, dispatcher: TestDispatcher) {
    val jobs = store.keys().mapNotNull { store[it]?.viewModelScope?.coroutineContext?.get(Job) }
    store.clear()
    val deadline = System.nanoTime() + 5_000_000_000L
    do {
        dispatcher.scheduler.advanceUntilIdle()
        if (jobs.all { it.isCompleted }) return
        check(System.nanoTime() < deadline) { "ViewModel jobs did not finish before Main reset" }
        Thread.sleep(5)
    } while (true)
}
