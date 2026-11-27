package org.qinyan.rimoat.widget

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.LocaleList
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fetchImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.AcSettings
import org.qinyan.rimoat.data.AppColorMode
import org.qinyan.rimoat.data.ButtonKey
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.i18n.AppStrings
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import kotlin.math.roundToInt

/** Render picker artwork from the same Glance content used by the configuration preview. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class WidgetPickerPreviewRoborazziTest {
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    @Test fun englishPreviews() = renderLanguage("en", "en")
    @Test fun japanesePreviews() = renderLanguage("ja", "ja")
    @Test fun simplifiedChinesePreviews() = renderLanguage("hans", "zh-CN")
    @Test fun traditionalChinesePreviews() = renderLanguage("hant", "zh-Hant")

    private fun renderLanguage(language: String, localeTags: String) = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        for (dark in listOf(false, true)) {
            for (kind in WidgetKind.entries) {
                val controller = Robolectric.buildActivity(Activity::class.java).setup()
                try {
                    val activity = controller.get()
                    activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    val resourceConfiguration = Configuration(activity.resources.configuration).apply {
                        setLocales(LocaleList.forLanguageTags(localeTags))
                        fontScale = 1f
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    }
                    val context = WidgetPreviewComposer.previewContext(
                        activity.createConfigurationContext(resourceConfiguration)
                    )
                    val density = context.resources.displayMetrics.density
                    assertEquals("Picker artwork is rendered at three pixels per dp", 3f, density, 0f)
                    val size = if (kind == WidgetKind.SINGLE) DpSize(128.dp, 64.dp) else DpSize(160.dp, 144.dp)
                    val width = (size.width.value * density).roundToInt()
                    val height = (size.height.value * density).roundToInt()
                    val (configuration, snapshot) = fixture(context, kind, localeTags, dark)
                    val views = composeConfigurationPreview(context, configuration, snapshot, size)
                    val parent = FrameLayout(context)
                    activity.setContentView(parent, ViewGroup.LayoutParams(width, height))
                    parent.addView(views.apply(context, parent))
                    shadowOf(Looper.getMainLooper()).idle()
                    parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    parent.layout(0, 0, width, height)

                    assertEquals(width, parent.width)
                    assertEquals(height, parent.height)
                    assertContent(parent, configuration, snapshot, density)
                    val mode = if (dark) "dark" else "light"
                    val bitmap = checkNotNull(parent.fetchImage(options.recordOptions))
                    try {
                        for (x in listOf(0, width - 1)) for (y in listOf(0, height - 1)) {
                            assertEquals("The system widget corners remain transparent", 0,
                                Color.alpha(bitmap.getPixel(x, y)))
                        }
                        assertEquals("The widget surface is rendered", 255,
                            Color.alpha(bitmap.getPixel(width / 2, height / 2)))
                        bitmap.captureRoboImage(
                            "src/test/screenshots/widget_picker_${language}_${kind.name.lowercase()}_$mode.webp",
                            roborazziOptions = options
                        )
                    } finally {
                        bitmap.recycle()
                    }
                } finally {
                    controller.pause().stop().destroy()
                }
            }
        }
    }

    private fun fixture(context: Context, kind: WidgetKind, localeTags: String, dark: Boolean):
        Pair<WidgetConfiguration, WidgetSnapshot> {
        fun appliance(id: String, category: DeviceCategory, name: String) = RemoteProfile(
            id = id, name = name, category = category,
            buttons = listOf(RemoteButton("power", context.getString(R.string.widget_power),
                key = ButtonKey.POWER, iconName = "power", rawPattern = listOf(9000, 4500, 560)))
        )
        val television = appliance("preview_tv", DeviceCategory.TV, context.getString(R.string.widget_category_tv))
        val fan = appliance("preview_fan", DeviceCategory.FAN, context.getString(R.string.widget_category_fan))
        val airConditioner = RemoteProfile(
            id = "preview_ac", name = context.getString(R.string.widget_category_ac),
            category = DeviceCategory.AC, acProtocolId = 670, acSettings = AcSettings(temperature = 26)
        )
        val profiles = when (kind) {
            WidgetKind.SINGLE -> listOf(television)
            WidgetKind.PANEL -> listOf(television, fan)
            WidgetKind.STATUS -> listOf(airConditioner)
        }
        val commands = profiles.associate { it.id to WidgetCommands.choices(it) }
        val shortcuts = if (kind == WidgetKind.STATUS) commands.getValue(airConditioner.id).take(3)
            .map { WidgetShortcut(airConditioner.id, it.id) }
        else profiles.map { checkNotNull(WidgetCommands.default(it, commands.getValue(it.id), context)) }
        val configuration = WidgetConfiguration(kind, profiles.first().id, shortcuts)
        configuration.validate()
        return configuration to WidgetSnapshot(
            loaded = true, profiles = profiles, commands = commands,
            dynamicColor = false, colorMode = if (dark) AppColorMode.DARK else AppColorMode.LIGHT,
            fontScale = 1f, localeTags = localeTags
        )
    }

    private fun assertContent(root: ViewGroup, configuration: WidgetConfiguration,
                              snapshot: WidgetSnapshot, density: Float) {
        val children = descendants(root)
        val texts = children.filterIsInstance<TextView>().filter { it.text.isNotBlank() }
        snapshot.profiles.forEach { profile ->
            assertTrue("Localized appliance name remains in the preview: ${profile.name}",
                texts.any { it.text.toString() == profile.name })
        }
        if (configuration.kind == WidgetKind.STATUS) assertTrue(texts.any { it.text.toString() == "26°" })
        val pendingIntentTag = root.resources.getIdentifier("pending_intent_tag", "id", "android")
        val commands = children.filter { child ->
            val pending = child.getTag(pendingIntentTag) as? PendingIntent
            pending != null && shadowOf(pending).savedIntent
                .getStringExtra("ActionCallbackBroadcastReceiver:callbackClass") == SendWidgetCommand::class.java.name
        }
        assertEquals("Every preview shortcut has a complete visible action", configuration.shortcuts.size, commands.size)
        commands.forEach { action ->
            val bounds = Rect()
            assertTrue(action.getGlobalVisibleRect(bounds))
            assertEquals(action.width, bounds.width())
            assertEquals(action.height, bounds.height())
            assertTrue(action.width >= 48 * density && action.height >= 48 * density)
        }
        for (text in texts) {
            val layout = checkNotNull(text.layout)
            assertTrue(layout.lineCount > 0)
            assertTrue("Text fits its measured height: ${text.text}",
                text.totalPaddingTop + layout.getLineBottom(layout.lineCount - 1) <= text.height - text.totalPaddingBottom)
        }
        children.filterIsInstance<ImageView>().filter { !it.contentDescription.isNullOrBlank() }.forEach { icon ->
            val bounds = Rect()
            assertTrue("The complete icon remains visible", icon.getGlobalVisibleRect(bounds))
            assertEquals(icon.width, bounds.width())
            assertEquals(icon.height, bounds.height())
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
