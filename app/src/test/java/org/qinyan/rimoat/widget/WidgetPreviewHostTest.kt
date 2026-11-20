package org.qinyan.rimoat.widget

import android.app.Application
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowViewGroup
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35], qualifiers = "en-mdpi", application = Application::class,
    shadows = [WidgetPreviewHostTest.FrameworkWidgetHostShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WidgetPreviewHostTest {
    // Exercise framework asynchronous inflation instead of the simplified host shadow.
    @Implements(AppWidgetHostView::class)
    class FrameworkWidgetHostShadow : ShadowViewGroup()

    @Test fun boundProviderRendersAsyncConfigurationPreview() = runBlocking {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_IrRemote)
        controller.setup()
        try {
            val activity = controller.get()
            val provider = ComponentName(activity, RemotePanelWidgetReceiver::class.java)
            val info = AppWidgetProviderInfo().apply { this.provider = provider }
            ReflectionHelpers.setField(info, "providerInfo", ActivityInfo().apply {
                packageName = activity.packageName
                name = provider.className
                applicationInfo = activity.applicationInfo
            })
            val pending = ArrayDeque<Runnable>()
            val host = createConfigurationPreviewHost(activity, 7, info, pending::addLast)
            assertEquals(7, host.appWidgetId)
            assertSame(info, host.appWidgetInfo)
            assertNotNull(LayoutInflater.from(activity).factory2)
            assertNull(LayoutInflater.from(host.context).factory2)
            activity.setContentView(host)
            val profile = RemoteProfile("remote", "Preview TV", buttons = listOf(
                RemoteButton("power", "Power", iconName = "power", rawPattern = listOf(9000, 4500, 560))))
            val configuration = WidgetConfiguration(WidgetKind.PANEL, shortcuts = listOf(WidgetShortcut(profile.id, "power")))
            val snapshot = WidgetSnapshot(loaded = true, localeTags = "en", profiles = listOf(profile),
                commands = mapOf(profile.id to WidgetCommands.choices(profile)))
            val views = composeConfigurationPreview(WidgetPreviewComposer.previewContext(activity),
                configuration, snapshot, DpSize(250.dp, 160.dp))
            host.updateAppWidget(views)
            assertFalse(pending.isEmpty())
            while (pending.isNotEmpty()) pending.removeFirst().run()
            shadowOf(Looper.getMainLooper()).idle()
            host.measure(View.MeasureSpec.makeMeasureSpec(250, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 250, 160)
            fun texts(view: View): List<String> = when (view) {
                is TextView -> listOf(view.text.toString())
                is android.view.ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                else -> emptyList()
            }
            assertTrue(texts(host).contains(profile.name))
            if (android.os.Build.VERSION.SDK_INT == 31) {
                host.captureRoboImage("src/test/screenshots/widget_preview_host_api31.webp",
                    roborazziOptions = RoborazziOptions(recordOptions =
                        RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())))
            }
        } finally { controller.pause().stop().destroy() }
    }
}
