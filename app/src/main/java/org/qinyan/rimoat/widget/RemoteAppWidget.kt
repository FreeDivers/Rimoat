package org.qinyan.rimoat.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.rounded.*
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.layout.*
import org.qinyan.rimoat.IrRemoteApplication
import org.qinyan.rimoat.MainActivity
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppLanguage
import org.qinyan.rimoat.data.*

class SingleRemoteWidget : RemoteAppWidget(WidgetKind.SINGLE)
class RemotePanelWidget : RemoteAppWidget(WidgetKind.PANEL)
class DeviceStatusWidget : RemoteAppWidget(WidgetKind.STATUS)
open class RemoteAppWidget(private val kind: WidgetKind) : GlanceAppWidget(errorUiLayout = R.layout.widget_error) {
    override val sizeMode = SizeMode.Responsive(WidgetLayoutSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as IrRemoteApplication
        app.widgets.awaitReady()
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val snapshot by app.widgets.state.collectAsState()
            val feedback by app.widgets.feedback.collectAsState()
            WidgetColorTheme(snapshot) {
                RemoteWidgetContent(kind, widgetId, snapshot, feedback[widgetId])
            }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        (context.applicationContext as IrRemoteApplication).widgets.store.remove(GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
    }

    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        android.util.Log.e("RimoatWidget", "Composition failed for $appWidgetId", throwable)
        val views = RemoteViews(context.packageName, R.layout.widget_error)
        val localized = AppLanguage.wrap(context)
        views.setTextViewText(R.id.widget_error_title, localized.getString(R.string.widget_error_title))
        views.setTextViewText(R.id.widget_error_action, localized.getString(R.string.widget_error_action))
        views.setOnClickPendingIntent(R.id.widget_recover, PendingIntent.getActivity(context, appWidgetId,
            configurationIntent(context, appWidgetId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }
}

@Composable
internal fun WidgetColorTheme(snapshot: WidgetSnapshot, content: @Composable () -> Unit) {
    WidgetVisualTheme(snapshot, content)
}

fun configurationIntent(context: Context, id: Int) = Intent(context, WidgetConfigurationActivity::class.java)
    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)

fun remoteWidgetIntent(context: Context, remoteId: String = "", destination: String = "remote") =
    Intent(context, MainActivity::class.java).setAction("org.qinyan.rimoat.OPEN_WIDGET")
        .putExtra("widget_remote_id", remoteId).putExtra("widget_destination", destination)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

class SendWidgetCommand : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val remote = parameters[remoteKey] ?: return
        val button = parameters[buttonKey] ?: return
        (context.applicationContext as IrRemoteApplication).widgets.perform(GlanceAppWidgetManager(context).getAppWidgetId(glanceId), WidgetShortcut(remote, button))
    }
    companion object {
        val remoteKey = ActionParameters.Key<String>("remote")
        val buttonKey = ActionParameters.Key<String>("button")
    }
}

class ReloadWidgets : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        (context.applicationContext as IrRemoteApplication).widgets.retry()
    }
}
