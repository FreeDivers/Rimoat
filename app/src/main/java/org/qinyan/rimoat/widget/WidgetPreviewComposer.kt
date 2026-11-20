package org.qinyan.rimoat.widget

import android.content.Context
import android.util.LruCache
import android.widget.RemoteViews
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs configuration previews outside the editor's composition scope.
 *
 * Glance writes its layout bookkeeping to a DataStore while composing. Cancelling the
 * composition half-way (for example by leaving the configuration page, or by a draft change
 * restarting the effect) can leave that store busy, and every later preview then waits forever.
 * Requests therefore live in an application-lifetime scope and are cached, so closing the page
 * never interrupts a composition that already started.
 */
internal object WidgetPreviewComposer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val cache = object : LruCache<String, RemoteViews>(6) {
        override fun sizeOf(key: String, value: RemoteViews) = 1
    }
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<RemoteViews>>()

    fun previewContext(context: Context): Context = context.applicationContext.createConfigurationContext(
        android.content.res.Configuration(context.resources.configuration)
    )

    fun cached(key: String): RemoteViews? = synchronized(cache) { cache.get(key) }

    /**
     * Glance keeps layout bookkeeping per appWidgetId. Preview compositions use the default
     * negative fake ids, so their files are named `appWidgetLayout--<id>`; real widgets are
     * positive. Delete only old fake-id files so a long-lived install does not accumulate them.
     */
    fun cleanStaleLayouts(context: Context) {
        val cutoff = System.currentTimeMillis() - 10 * 60_000L
        val directory = java.io.File(context.applicationContext.filesDir, "datastore")
        directory.listFiles()?.forEach { file ->
            if (file.name.startsWith("appWidgetLayout--") && file.lastModified() < cutoff) file.delete()
        }
    }

    /**
     * Returns the cached preview or starts one in the application scope. Callers may await the
     * result with their own timeout and cancel freely; the composition itself keeps running.
     */
    fun request(key: String, compose: suspend () -> RemoteViews): CompletableDeferred<RemoteViews> {
        cached(key)?.let { return CompletableDeferred(it) }
        inFlight[key]?.let { return it }
        val deferred = CompletableDeferred<RemoteViews>()
        val existing = inFlight.putIfAbsent(key, deferred)
        if (existing != null) return existing
        scope.async {
            try {
                val views = lock.withLock {
                    synchronized(cache) { cache.get(key) } ?: compose().also { built ->
                        synchronized(cache) { cache.put(key, built) }
                    }
                }
                deferred.complete(views)
            } catch (e: kotlinx.coroutines.CancellationException) {
                deferred.cancel(e)
                throw e
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
            } finally {
                inFlight.remove(key, deferred)
            }
        }
        return deferred
    }
}
