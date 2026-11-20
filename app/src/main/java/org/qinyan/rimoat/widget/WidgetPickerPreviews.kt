package org.qinyan.rimoat.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import org.qinyan.rimoat.i18n.AppLanguage

/** Stable providers for existing bindings, with only the selected language in the picker. */
object WidgetPickerPreviews {
    data class Entry(val language: String, val kind: WidgetKind,
        val receiver: Class<out RemoteAppWidgetReceiver>, val widget: GlanceAppWidget)

    val entries = listOf(
        Entry("", WidgetKind.SINGLE, SingleRemoteWidgetReceiver::class.java, SingleRemoteWidget()),
        Entry("", WidgetKind.PANEL, RemotePanelWidgetReceiver::class.java, RemotePanelWidget()),
        Entry("", WidgetKind.STATUS, DeviceStatusWidgetReceiver::class.java, DeviceStatusWidget()),
        Entry("en", WidgetKind.SINGLE, EnSingleRemoteWidgetReceiver::class.java, EnSingleRemoteWidget()),
        Entry("en", WidgetKind.PANEL, EnRemotePanelWidgetReceiver::class.java, EnRemotePanelWidget()),
        Entry("en", WidgetKind.STATUS, EnDeviceStatusWidgetReceiver::class.java, EnDeviceStatusWidget()),
        Entry("ja", WidgetKind.SINGLE, JaSingleRemoteWidgetReceiver::class.java, JaSingleRemoteWidget()),
        Entry("ja", WidgetKind.PANEL, JaRemotePanelWidgetReceiver::class.java, JaRemotePanelWidget()),
        Entry("ja", WidgetKind.STATUS, JaDeviceStatusWidgetReceiver::class.java, JaDeviceStatusWidget()),
        Entry("zh-CN", WidgetKind.SINGLE, HansSingleRemoteWidgetReceiver::class.java, HansSingleRemoteWidget()),
        Entry("zh-CN", WidgetKind.PANEL, HansRemotePanelWidgetReceiver::class.java, HansRemotePanelWidget()),
        Entry("zh-CN", WidgetKind.STATUS, HansDeviceStatusWidgetReceiver::class.java, HansDeviceStatusWidget()),
        Entry("zh-Hant", WidgetKind.SINGLE, HantSingleRemoteWidgetReceiver::class.java, HantSingleRemoteWidget()),
        Entry("zh-Hant", WidgetKind.PANEL, HantRemotePanelWidgetReceiver::class.java, HantRemotePanelWidget()),
        Entry("zh-Hant", WidgetKind.STATUS, HantDeviceStatusWidgetReceiver::class.java, HantDeviceStatusWidget()),
    )

    fun kind(component: ComponentName?): WidgetKind? = entries.firstOrNull {
        it.receiver.name == component?.className
    }?.kind

    /** Never disable receivers: hidden providers must continue serving existing instances. */
    @Synchronized
    fun synchronize(context: Context) {
        if (Build.VERSION.SDK_INT < 28) return
        val manager = AppWidgetManager.getInstance(context)
        val locales = AppLanguage.wrap(context).resources.configuration.locales
        val language = (0 until locales.size()).asSequence()
            .map { AppLanguage.choiceTag(locales[it].toLanguageTag()) }
            .firstOrNull(String::isNotEmpty) ?: "en"
        val selected = entries.filter { it.language == language }
        try {
            // Publish the complete replacement before hiding any current picker entries.
            // Address known components directly, even when a launcher query omits hidden ones.
            for (entry in selected) {
                manager.updateAppWidgetProviderInfo(
                    ComponentName(context, entry.receiver), "org.qinyan.rimoat.widget.visible"
                )
            }
            val providers = manager.installedProviders.associateBy { it.provider }
            val replacementReady = selected.all { entry ->
                providers[ComponentName(context, entry.receiver)]?.let {
                    it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
                } == true
            }
            if (!replacementReady) return
            for (entry in entries.filter { it.language != language }) {
                val component = ComponentName(context, entry.receiver)
                val current = providers[component] ?: continue
                if (current.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER != 0) continue
                manager.updateAppWidgetProviderInfo(component, "org.qinyan.rimoat.widget.hidden")
            }
        } catch (error: RuntimeException) {
            Log.w("RimoatWidget", "Picker synchronization deferred", error)
        }
    }
}
