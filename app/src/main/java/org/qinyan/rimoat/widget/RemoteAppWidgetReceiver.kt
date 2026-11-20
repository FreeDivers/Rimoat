package org.qinyan.rimoat.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import org.qinyan.rimoat.IrRemoteApplication
import kotlinx.coroutines.launch
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf

class SingleRemoteWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = SingleRemoteWidget() }
class RemotePanelWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = RemotePanelWidget() }
class DeviceStatusWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = DeviceStatusWidget() }

abstract class RemoteAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<RestoreWidgetsWorker>()
            .setInputData(workDataOf("old" to oldWidgetIds, "new" to newWidgetIds)).build())
    }
}

/** Only restores instance metadata. Never schedules or retries an IR command. */
class RestoreWidgetsWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as IrRemoteApplication
        return try {
            app.widgets.store.remap(inputData.getIntArray("old") ?: return Result.failure(),
                inputData.getIntArray("new") ?: return Result.failure(), id.toString())
            val manager = android.appwidget.AppWidgetManager.getInstance(applicationContext)
            for (widgetId in (inputData.getIntArray("new") ?: intArrayOf())) {
                manager.updateAppWidgetOptions(widgetId, android.os.Bundle().apply {
                    putBoolean(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true)
                })
            }
            app.widgets.updateAll()
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.e("RimoatWidget", "Restore configuration failed", e)
            Result.failure()
        }
    }
}
