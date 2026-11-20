package org.qinyan.rimoat.widget

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.i18n.UiText
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class WidgetRecoveryRoborazziTest {
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))

    @Test fun previewContextRetainsConfigurationWithoutActivityInItsBaseChain() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val config = Configuration(controller.get().resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags("ja")); fontScale = 1.5f
                uiMode = Configuration.UI_MODE_NIGHT_YES
            }
            val source = controller.get().createConfigurationContext(config)
            val preview = WidgetPreviewComposer.previewContext(source)
            var current: Context? = preview
            while (current != null) {
                assertFalse(current is Activity)
                current = (current as? ContextWrapper)?.baseContext
            }
            assertEquals("ja", preview.resources.configuration.locales[0].language)
            assertEquals(1.5f, preview.resources.configuration.fontScale, 0f)
            assertEquals(Configuration.UI_MODE_NIGHT_YES,
                preview.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun widgetErrorPreviewRecoversToUsableRemotePanel() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val context = WidgetPreviewComposer.previewContext(app)
        val profile = RemoteProfile("remote", "客厅电视", buttons = listOf(
            RemoteButton("power", "电源", iconName = "power", rawPattern = listOf(9000, 4500, 560))))
        val configuration = WidgetConfiguration(WidgetKind.SINGLE, profile.id, listOf(WidgetShortcut(profile.id, "power")))
        val error = UiText.Resource(R.string.msg_remote_file_unreadable)
        val failed = WidgetSnapshot(loaded = true, localeTags = "zh-CN").withRepositoryError(error)
        val recovered = failed.copy(profiles = listOf(profile), commands = mapOf(profile.id to WidgetCommands.choices(profile)))
            .withRepositoryError(error)
        assertNull(recovered.error)
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        try {
        for ((name, snapshot) in listOf("widget_recovery_before" to failed, "widget_recovery_after" to recovered)) {
            val views = composeConfigurationPreview(context, configuration, snapshot, DpSize(250.dp, 160.dp))
            val parent = FrameLayout(host.get())
            host.get().setContentView(parent)
            val rendered = views.apply(host.get(), parent)
            parent.addView(rendered)
            parent.measure(View.MeasureSpec.makeMeasureSpec(250, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY))
            parent.layout(0, 0, 250, 160)
            parent.captureRoboImage("src/test/screenshots/$name.webp", roborazziOptions = options)
            fun texts(view: View): List<String> = when (view) {
                is android.widget.TextView -> listOf(view.text.toString())
                is android.view.ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                else -> emptyList()
            }
            if (snapshot.error != null) assertTrue(texts(parent).contains(context.getString(R.string.widget_read_failed)))
            else assertTrue(texts(parent).contains(profile.name))
        }
        } finally { host.pause().stop().destroy() }
    }
}
