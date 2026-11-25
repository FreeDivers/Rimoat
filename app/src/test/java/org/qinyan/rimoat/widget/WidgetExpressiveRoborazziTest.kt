package org.qinyan.rimoat.widget

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.data.AcSettings
import org.qinyan.rimoat.data.AppColorMode
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class, ExperimentalGlanceApi::class, ExperimentalGlanceRemoteViewsApi::class)
class WidgetExpressiveRoborazziTest {
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )
    private val app get() = RuntimeEnvironment.getApplication()
    private val television = RemoteProfile("television", "Living room TV", room = "Living room",
        category = DeviceCategory.TV, buttons = listOf(
            RemoteButton("power", "Power", iconName = "power", rawPattern = listOf(9000, 4500, 560)),
            RemoteButton("up", "Volume up", iconName = "add", rawPattern = listOf(9000, 4500, 560)),
            RemoteButton("down", "Volume down", iconName = "remove", rawPattern = listOf(9000, 4500, 560))
        ))
    private val airConditioner = RemoteProfile("air_conditioner", "Bedroom AC", room = "Bedroom",
        category = DeviceCategory.AC, acProtocolId = 670, acSettings = AcSettings(temperature = 26))

    @Test
    @Config(sdk = [28, 35])
    fun singleKeepsOneReachableActionAcrossSizesAndColors() = runBlocking {
        if (Build.VERSION.SDK_INT < 31) {
            val (configuration, snapshot) = fixture(WidgetKind.SINGLE)
            render("single_legacy_light", DpSize(128.dp, 64.dp), configuration,
                snapshot.copy(colorMode = AppColorMode.LIGHT)) { root ->
                assertEquals(1, assertCommandTargets(root, configuration, expectedMinimum = 1).size)
                assertTrue(texts(root).any { it.text.toString() == television.name })
            }
        } else exerciseLayouts(WidgetKind.SINGLE)
    }

    @Test fun panelAdaptsItsVisibleActionsAcrossSizesAndColors() = runBlocking {
        exerciseLayouts(WidgetKind.PANEL)
    }

    @Test fun statusKeepsTheSavedTemperatureAcrossSizesAndColors() = runBlocking {
        exerciseLayouts(WidgetKind.STATUS)
    }

    @Test fun narrowTallStatusKeepsFourActionsVisibleAtBothFontSizes() = runBlocking {
        for (fontScale in listOf(1f, 2f)) {
            RuntimeEnvironment.setFontScale(fontScale)
            assertEquals(fontScale, app.resources.configuration.fontScale, 0f)
            val (initialConfiguration, snapshot) = fixture(WidgetKind.STATUS, fontScale = fontScale)
            val configuration = initialConfiguration.copy(shortcuts = listOf(
                WidgetCommands.POWER,
                WidgetCommands.PREFIX + "TEMP_UP",
                WidgetCommands.PREFIX + "TEMP_DOWN",
                WidgetCommands.PREFIX + "MODE"
            ).map { WidgetShortcut(airConditioner.id, it) })
            val fontName = if (fontScale == 2f) "large_text" else "normal_text"
            render("status_narrow_tall_four_actions_$fontName", DpSize(152.dp, 240.dp), configuration,
                snapshot.copy(colorMode = AppColorMode.DARK)) { root ->
                assertAllConfiguredCommandTargets(root, configuration)
                val temperature = texts(root).single { it.text.toString() == "26°" }
                assertEquals("The complete temperature remains readable", 0,
                    requireNotNull(temperature.layout).getEllipsisCount(0))
            }
        }
    }

    @Test fun wideShortStatusKeepsTemperatureAndActionsVisibleAtDoubleFontSize() = runBlocking {
        RuntimeEnvironment.setFontScale(2f)
        assertEquals(2f, app.resources.configuration.fontScale, 0f)
        val (configuration, snapshot) = fixture(WidgetKind.STATUS, fontScale = 2f)
        render("status_wide_large_text", DpSize(280.dp, 144.dp), configuration,
            snapshot.copy(colorMode = AppColorMode.DARK)) { root ->
            assertAllConfiguredCommandTargets(root, configuration)
            val temperature = texts(root).single { it.text.toString() == "26°" }
            assertEquals("The complete temperature remains readable", 0,
                requireNotNull(temperature.layout).getEllipsisCount(0))
        }
    }

    @Test fun mediumWidthStatusKeepsFourActionsAndTemperatureVisible() = runBlocking {
        val (initialConfiguration, snapshot) = fixture(WidgetKind.STATUS)
        val configuration = initialConfiguration.copy(shortcuts = listOf(
            WidgetCommands.POWER,
            WidgetCommands.PREFIX + "TEMP_UP",
            WidgetCommands.PREFIX + "TEMP_DOWN",
            WidgetCommands.PREFIX + "MODE"
        ).map { WidgetShortcut(airConditioner.id, it) })
        render("status_medium_four_actions", DpSize(224.dp, 144.dp), configuration,
            snapshot.copy(colorMode = AppColorMode.LIGHT)) { root ->
            assertAllConfiguredCommandTargets(root, configuration)
            val temperature = texts(root).single { it.text.toString() == "26°" }
            assertEquals("The complete temperature remains readable", 0,
                requireNotNull(temperature.layout).getEllipsisCount(0))
        }
    }

    @Test fun doubleFontSizeKeepsNamesAndPrimaryActionsReachable() = runBlocking {
        RuntimeEnvironment.setFontScale(2f)
        assertEquals(2f, app.resources.configuration.fontScale, 0f)
        for (kind in WidgetKind.entries) {
            val (configuration, snapshot) = fixture(kind, fontScale = 2f)
            render("${kind.name.lowercase()}_large_text", DpSize(280.dp, 240.dp), configuration,
                snapshot.copy(colorMode = AppColorMode.DARK)) { root ->
                val expectedName = if (kind == WidgetKind.STATUS) airConditioner.name else television.name
                assertTrue(texts(root).any { it.text.toString() == expectedName })
                assertCommandTargets(root, configuration, expectedMinimum = 1)
                texts(root).filter { it.text.toString() == expectedName }.forEach {
                    assertTrue("Widget names scale with the resource configuration", it.textSize >= 24f)
                }
            }
        }
        val (configuration, snapshot) = fixture(WidgetKind.SINGLE, fontScale = 2f, profile = airConditioner)
        render("single_ac_wide_large_text", DpSize(280.dp, 144.dp), configuration,
            snapshot.copy(colorMode = AppColorMode.DARK)) { root ->
            assertTrue(texts(root).any { it.text.toString() == airConditioner.name })
            assertTrue(texts(root).any { it.text.toString() == "26°" })
            assertTrue("The room remains in the complete accessibility name", descendants(root).any {
                it.contentDescription?.let { description ->
                    description.contains(airConditioner.name) && description.contains(airConditioner.room)
                } == true
            })
            assertEquals(1, assertCommandTargets(root, configuration, expectedMinimum = 1).size)
        }
    }

    @Test fun temperatureLimitAndBusyStateRenderDisabledControlsWithoutChangingBindings() = runBlocking {
        val (configuration, initial) = fixture(WidgetKind.STATUS)
        val limit = airConditioner.copy(acSettings = airConditioner.acSettings.copy(temperature = 30))
        val snapshot = initial.copy(profiles = listOf(limit))
        val size = DpSize(280.dp, 240.dp)
        render("status_temperature_limit", size, configuration, snapshot) { root ->
            assertTrue(texts(root).any { it.text.toString() == "30°" })
            val targets = assertCommandTargets(root, configuration, expectedMinimum = 3,
                disabled = setOf(WidgetCommands.PREFIX + "TEMP_UP"))
            assertEquals(3, targets.size)
        }
        render("status_busy", size, configuration, snapshot,
            feedback = WidgetFeedback(text = "", busy = true)) { root ->
            assertCommandTargets(root, configuration, expectedMinimum = 3,
                disabled = configuration.shortcuts.map { it.buttonId }.toSet())
            assertTrue(texts(root).any { it.text.toString() == "30°" })
        }
    }

    private suspend fun exerciseLayouts(kind: WidgetKind) {
        val (configuration, initial) = fixture(kind)
        val layouts = buildList {
            add(Triple("compact_light", DpSize(128.dp, 64.dp), initial.copy(colorMode = AppColorMode.LIGHT)))
            add(Triple("wide_dark", DpSize(280.dp, 144.dp), initial.copy(colorMode = AppColorMode.DARK)))
            add(Triple("tall_dynamic", DpSize(280.dp, 240.dp), initial.copy(dynamicColor = true)))
            if (kind != WidgetKind.SINGLE) {
                add(Triple("narrow_light", DpSize(152.dp, 168.dp), initial.copy(colorMode = AppColorMode.LIGHT)))
            }
        }
        for ((name, size, snapshot) in layouts) {
            render("${kind.name.lowercase()}_$name", size, configuration, snapshot) { root ->
                val targets = assertCommandTargets(root, configuration, expectedMinimum = 1)
                if (kind == WidgetKind.SINGLE || size.height == 64.dp) assertEquals(1, targets.size)
                if (kind == WidgetKind.STATUS) {
                    assertTrue("The actual AC setting remains visible", texts(root).any { it.text.toString() == "26°" })
                } else {
                    assertTrue(texts(root).any { it.text.toString() == television.name })
                }
            }
        }
    }

    private fun fixture(kind: WidgetKind, fontScale: Float = 1f,
                        profile: RemoteProfile = if (kind == WidgetKind.STATUS) airConditioner else television
    ): Pair<WidgetConfiguration, WidgetSnapshot> {
        val commands = WidgetCommands.choices(profile)
        val selected = when (kind) {
            WidgetKind.SINGLE -> commands.take(1)
            WidgetKind.PANEL -> commands
            WidgetKind.STATUS -> commands.take(3)
        }.map { WidgetShortcut(profile.id, it.id) }
        return WidgetConfiguration(kind, profile.id, selected) to WidgetSnapshot(
            loaded = true, profiles = listOf(profile), commands = mapOf(profile.id to commands),
            colorMode = AppColorMode.SYSTEM, localeTags = "en", fontScale = fontScale
        )
    }

    private suspend fun render(
        name: String,
        size: DpSize,
        configuration: WidgetConfiguration,
        snapshot: WidgetSnapshot,
        feedback: WidgetFeedback? = null,
        inspect: (ViewGroup) -> Unit
    ) {
        AppStrings.initialize(app)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val resourceConfiguration = Configuration(activity.resources.configuration).apply {
                fontScale = snapshot.fontScale
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (snapshot.colorMode == AppColorMode.DARK) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val context = WidgetPreviewComposer.previewContext(activity.createConfigurationContext(resourceConfiguration))
            assertEquals(snapshot.fontScale, context.resources.configuration.fontScale, 0f)
            val state = snapshot.copy(configurations = mapOf(-1 to configuration))
            val remoteViews = GlanceRemoteViews().compose(context = context, size = size) {
                WidgetColorTheme(state) { RemoteWidgetContent(configuration.kind, -1, state, feedback) }
            }.remoteViews
            val parent = FrameLayout(context)
            activity.setContentView(parent, ViewGroup.LayoutParams(size.width.value.toInt(), size.height.value.toInt()))
            parent.addView(remoteViews.apply(context, parent))
            shadowOf(Looper.getMainLooper()).idle()
            parent.measure(View.MeasureSpec.makeMeasureSpec(size.width.value.toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size.height.value.toInt(), View.MeasureSpec.EXACTLY))
            parent.layout(0, 0, size.width.value.toInt(), size.height.value.toInt())
            inspect(parent)
            assertRenderedContentIsFullyVisible(parent)
            parent.captureRoboImage("src/test/screenshots/widget_expressive_$name.webp", roborazziOptions = options)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun assertAllConfiguredCommandTargets(root: ViewGroup, configuration: WidgetConfiguration) {
        val targets = assertCommandTargets(root, configuration, expectedMinimum = configuration.shortcuts.size)
        assertEquals("Every configured command has one visible target", configuration.shortcuts.size, targets.size)
        val pendingIntentTag = root.resources.getIdentifier("pending_intent_tag", "id", "android")
        val actual = targets.map { target ->
            val pendingIntent = target.getTag(pendingIntentTag) as PendingIntent
            val parameters = requireNotNull(shadowOf(pendingIntent).savedIntent
                .getBundleExtra("ActionCallbackBroadcastReceiver:parameters"))
            WidgetShortcut(requireNotNull(parameters.getString("remote")), requireNotNull(parameters.getString("button")))
        }
        assertEquals("Every visible command keeps its configured binding", configuration.shortcuts.toSet(), actual.toSet())
    }

    private fun assertCommandTargets(
        root: ViewGroup,
        configuration: WidgetConfiguration,
        expectedMinimum: Int,
        disabled: Set<String> = emptySet()
    ): List<View> {
        val pendingIntentTag = root.resources.getIdentifier("pending_intent_tag", "id", "android")
        assertTrue("Framework PendingIntent tag is available", pendingIntentTag != 0)
        val targets = descendants(root).mapNotNull { view ->
            val pendingIntent = view.getTag(pendingIntentTag) as? PendingIntent ?: return@mapNotNull null
            val pending = shadowOf(pendingIntent)
            val intent = pending.savedIntent
            if (intent.getStringExtra("ActionCallbackBroadcastReceiver:callbackClass") != SendWidgetCommand::class.java.name) return@mapNotNull null
            val parameters = requireNotNull(intent.getBundleExtra("ActionCallbackBroadcastReceiver:parameters"))
            val shortcut = WidgetShortcut(requireNotNull(parameters.getString("remote")), requireNotNull(parameters.getString("button")))
            assertTrue(configuration.shortcuts.contains(shortcut))
            assertTrue(pending.isBroadcast)
            assertTrue(pending.isImmutable)
            assertEquals(AppWidgetManager.INVALID_APPWIDGET_ID,
                intent.getIntExtra("ActionCallbackBroadcastReceiver:appWidgetId", -1))
            val icon = descendants(view).filterIsInstance<ImageView>().single {
                !it.contentDescription.isNullOrBlank()
            }
            // Glance moves the action to a ripple wrapper; enabled stays on the content box.
            val content = generateSequence(icon.parent as? View) { it.parent as? View }
                .takeWhile { it !== view }.last()
            assertSame("The icon content remains directly inside its action wrapper", view, content.parent)
            assertTrue("The enabled state belongs to the icon content container", content is ViewGroup)
            assertEquals("Rendered enabled state for ${shortcut.buttonId}", shortcut.buttonId !in disabled, content.isEnabled)
            assertTrue("Action width remains at least 48dp", view.width >= 48)
            assertTrue("Action height remains at least 48dp", view.height >= 48)
            val visible = Rect()
            assertTrue("Action stays within the host viewport", view.getGlobalVisibleRect(visible))
            assertEquals("The full action width is visible", view.width, visible.width())
            assertEquals("The full action height is visible", view.height, visible.height())
            assertTrue("Action keeps its full accessibility name", descendants(view).any { !it.contentDescription.isNullOrBlank() })
            view
        }
        assertTrue("Expected command targets remain present", targets.size >= expectedMinimum)
        return targets
    }

    private fun assertRenderedContentIsFullyVisible(root: ViewGroup) {
        for (text in texts(root).filter { it.text.isNotBlank() }) {
            val label = text.text.toString()
            val layout = requireNotNull(text.layout) { "Text has a measured layout: $label" }
            assertTrue("Text has a complete line: $label", layout.lineCount > 0)
            val lineTop = text.totalPaddingTop + layout.getLineTop(0) - text.scrollY
            val lineBottom = text.totalPaddingTop + layout.getLineBottom(layout.lineCount - 1) - text.scrollY
            assertTrue("Text starts within its container: $label", lineTop >= 0)
            assertTrue("Text line fits its measured height: $label ($lineBottom > ${text.height})",
                lineBottom <= text.height - text.totalPaddingBottom)
            val visible = Rect()
            val offset = Point()
            assertTrue("Text is visible in the host: $label", text.getGlobalVisibleRect(visible, offset))
            assertTrue("The host shows the complete text line: $label",
                visible.top <= offset.y + text.scrollY + lineTop &&
                    visible.bottom >= offset.y + text.scrollY + lineBottom)
        }
        for (icon in descendants(root).filterIsInstance<ImageView>().filter { !it.contentDescription.isNullOrBlank() }) {
            val visible = Rect()
            assertTrue("The icon is visible: ${icon.contentDescription}", icon.getGlobalVisibleRect(visible))
            assertEquals("The full icon width is visible: ${icon.contentDescription}", icon.width, visible.width())
            assertEquals("The full icon height is visible: ${icon.contentDescription}", icon.height, visible.height())
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun texts(view: View): List<TextView> = descendants(view).filterIsInstance<TextView>()
}
