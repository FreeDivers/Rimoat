package org.qinyan.rimoat.widget

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Looper
import android.os.LocaleList
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.junit.Rule
import org.junit.runner.RunWith
import org.qinyan.rimoat.data.AcSettings
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.AcMode
import org.qinyan.rimoat.data.ButtonKey
import org.qinyan.rimoat.data.AppColorMode
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.labelRes
import org.qinyan.rimoat.R
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class, ExperimentalGlanceApi::class, ExperimentalGlanceRemoteViewsApi::class)
class WidgetExpressiveRoborazziTest {
    @get:Rule val screenshotDifferences = org.junit.rules.ErrorCollector()

    @get:Rule val compose = createComposeRule()
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )
    private val app get() = RuntimeEnvironment.getApplication()
    private val television = RemoteProfile("television", "Living room TV", room = "Living room",
        category = DeviceCategory.TV, buttons = listOf(
            RemoteButton("power", "Power", iconName = "power", rawPattern = listOf(9000, 4500, 560), key = ButtonKey.POWER),
            RemoteButton("up", "Volume up", iconName = "add", rawPattern = listOf(9000, 4500, 560)),
            RemoteButton("down", "Volume down", iconName = "remove", rawPattern = listOf(9000, 4500, 560))
        ))
    private val airConditioner = RemoteProfile("air_conditioner", "Bedroom AC", room = "Bedroom",
        category = DeviceCategory.AC, acProtocolId = 670, acSettings = AcSettings(temperature = 26))

    @Test fun shortcutDescriptionsPreserveNamesAndResolveAllOptionalStates() {
        for (tag in listOf("en", "zh-CN", "zh-Hant", "ja")) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            })
            val profile = television.copy(name = "TV  One", room = "Room  Two")
            val command = context.getString(R.string.widget_power)
            val state = context.getString(R.string.widget_on)
            val failure = context.getString(R.string.widget_failure_retry, "Disconnected")
            assertEquals("TV  One Room  Two $command $state $failure",
                widgetShortcutDescription(context, profile, command, state, failure))
            assertEquals("TV  One Room  Two $command",
                widgetShortcutDescription(context, profile, command, null, null))
            assertEquals("TV  One $command $state",
                widgetShortcutDescription(context, profile.copy(room = ""), command, state, null))
            assertEquals("TV  One $command",
                widgetShortcutDescription(context, profile.copy(room = ""), command, null, null))
            assertEquals(command, widgetShortcutDescription(context, null, command, null, null))
        }
    }

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

    @Test fun timerDurationUsesLocalizedUnitsInReadingAndActionDescription() = runBlocking {
        val locales = listOf("en", "zh-CN", "zh-Hant", "ja")
        val timer = RemoteButton("timer", AcAction.TIMER.name, key = ButtonKey.TIMER, acAction = AcAction.TIMER)
        val profile = airConditioner.copy(acSettings = AcSettings(temperature = 26, timerMinutes = 90),
            buttons = listOf(timer))
        val (initialConfiguration, initialSnapshot) = fixture(WidgetKind.STATUS, profile = profile)
        val configuration = initialConfiguration.copy(shortcuts = listOf(
            WidgetShortcut(profile.id, WidgetCommands.POWER), WidgetShortcut(profile.id, timer.id)))
        for (localeTags in locales) {
            render("timer_duration_$localeTags", DpSize(280.dp, 240.dp), configuration,
                initialSnapshot.copy(localeTags = localeTags, colorMode = AppColorMode.LIGHT),
                capture = false) { root ->
                assertAllConfiguredCommandTargets(root, configuration)
                val duration = org.qinyan.rimoat.i18n.localizedTimerDurationLabel(90, root.context)
                val reading = texts(root).single { it.text.toString() == duration }
                assertEquals("The full localized duration remains visible", 0,
                    requireNotNull(reading.layout).getEllipsisCount(0))
                val expectedDescription = root.context.getString(R.string.widget_shortcut_room_state_description,
                    profile.name, profile.room, root.context.getString(R.string.control_timer), duration)
                assertEquals("The timer action announces the localized duration", 1,
                    descendants(root).filterIsInstance<ImageView>().count {
                        it.contentDescription?.toString() == expectedDescription
                    })
            }
        }
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
            feedback = WidgetFeedback(text = org.qinyan.rimoat.i18n.UiText.Resource(R.string.widget_sending), busy = true)) { root ->
            assertCommandTargets(root, configuration, expectedMinimum = 3,
                disabled = configuration.shortcuts.map { it.buttonId }.toSet())
            assertTrue(texts(root).any { it.text.toString() == "30°" })
        }
    }

    @Test fun sharedIconsKeepTheirIdentityAcrossLanguagesAndThemes() = runBlocking {
        for ((language, tags) in listOf("en" to "en", "hans" to "zh-CN", "hant" to "zh-Hant", "ja" to "ja")) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tags))
            })
            fun key(id: String, key: ButtonKey?, label: String) = RemoteButton(id, label,
                rawPattern = listOf(9000, 4500, 560), key = key, isLearned = true)
            val profile = television.copy(name = context.getString(R.string.widget_category_tv), room = "", buttons = listOf(
                key("source", ButtonKey.SOURCE, context.getString(ButtonKey.SOURCE.labelRes)),
                key("confirm", ButtonKey.OK, context.getString(ButtonKey.OK.labelRes)),
                key("custom", null, context.getString(R.string.control_power)),
                key("power", ButtonKey.POWER, "Custom key")
            ))
            val ac = airConditioner.copy(name = context.getString(R.string.widget_category_ac), room = "",
                acSettings = AcSettings(mode = AcMode.HEAT))
            val actions = listOf(AcAction.MODE, AcAction.SWING, AcAction.HORIZONTAL_SWING, AcAction.AUXILIARY_HEAT,
                AcAction.VENTILATION, AcAction.DRY, AcAction.QUICK_COOL, AcAction.QUICK_HEAT)
            val commands = WidgetCommands.choices(profile)
            val acCommands = actions.map { WidgetCommand(WidgetCommands.PREFIX + it.name, it.name, null) }
            val configuration = WidgetConfiguration(WidgetKind.PANEL, shortcuts =
                commands.map { WidgetShortcut(profile.id, it.id) } + acCommands.map { WidgetShortcut(ac.id, it.id) })
            val initial = WidgetSnapshot(loaded = true, profiles = listOf(profile, ac), localeTags = tags,
                commands = mapOf(profile.id to commands, ac.id to acCommands))
            assertFalse(commands[2].isPower(profile))
            assertTrue(commands[3].isPower(profile))
            val active = ac.acSettings.apply(AcAction.SWING).apply(AcAction.HORIZONTAL_SWING)
                .apply(AcAction.AUXILIARY_HEAT).apply(AcAction.VENTILATION).apply(AcAction.DRY)
            assertTrue(active.isSwingOn && active.horizontalSwingOn && active.auxiliaryHeatOn && active.ventilationOn && active.dryOn)
            for ((mode, color) in listOf("light" to AppColorMode.LIGHT, "dark" to AppColorMode.DARK)) {
                val snapshot = initial.copy(colorMode = color)
                render("shared_icons_${language}_${mode}_before", DpSize(280.dp, 320.dp), configuration, snapshot) {
                    assertAllConfiguredCommandTargets(it, configuration)
                }
                render("shared_icons_${language}_${mode}_after", DpSize(280.dp, 320.dp), configuration,
                    snapshot.copy(profiles = listOf(profile, ac.copy(acSettings = active)))) {
                    assertAllConfiguredCommandTargets(it, configuration)
                }
            }
        }
    }

    @Test fun statusAccessibilityAnnouncesLocalizedPowerChanges() = runBlocking {
        for ((language, tags) in listOf("en" to "en", "hans" to "zh-CN", "hant" to "zh-Hant", "ja" to "ja")) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tags))
            })
            val before = airConditioner.copy(name = context.getString(R.string.widget_category_ac), room = "",
                acSettings = airConditioner.acSettings.copy(powerOn = false))
            val after = before.copy(acSettings = before.acSettings.apply(AcAction.POWER_ON))
            assertFalse(before.acSettings.powerOn)
            assertTrue(after.acSettings.powerOn)
            assertEquals(before.acSettings.temperature, after.acSettings.temperature)
            val (configuration, initial) = fixture(WidgetKind.STATUS, profile = before)
            for ((phase, profile) in listOf("before" to before, "after" to after)) {
                val snapshot = initial.copy(profiles = listOf(profile), localeTags = tags, colorMode = AppColorMode.LIGHT)
                render("status_accessibility_${language}_$phase", DpSize(280.dp, 240.dp), configuration, snapshot) { root ->
                    assertAllConfiguredCommandTargets(root, configuration)
                    val settings = profile.acSettings
                    val powerState = context.getString(if (settings.powerOn) R.string.widget_on else R.string.widget_off)
                    val status = context.getString(R.string.control_ac_status,
                        context.getString(R.string.control_target_temp), powerState, settings.temperature,
                        org.qinyan.rimoat.i18n.localizedLabel(settings.mode, context),
                        org.qinyan.rimoat.i18n.localizedLabel(settings.fanSpeed, context))
                    val expected = context.getString(R.string.widget_open_remote_status, profile.name, status)
                    val statusNodes = descendants(root).filter { it.contentDescription?.toString() == expected }
                    assertEquals("The Glance root exposes the complete localized state", 1, statusNodes.size)
                    assertTrue("The state belongs to the clickable widget root",
                        generateSequence(statusNodes.single()) { it.parent as? View }.any { it.isClickable })
                    val reading = if (settings.powerOn) "${settings.temperature}°" else context.getString(R.string.widget_off)
                    assertTrue("The visible reading agrees with the spoken power state",
                        texts(root).any { it.text.toString() == reading })
                }
            }
        }
    }

    @Test fun sharedIconsStayReachableInNarrowDynamicWidgetsWithLargeText() = runBlocking {
        RuntimeEnvironment.setFontScale(2f)
        assertEquals(2f, app.resources.configuration.fontScale, 0f)
        val keys = listOf(ButtonKey.SOURCE, ButtonKey.OK, ButtonKey.POWER)
        val profile = television.copy(name = "TV", room = "", buttons = keys.map { key ->
            RemoteButton(key.name, app.getString(key.labelRes), key = key,
                rawPattern = listOf(9000, 4500, 560))
        })
        val commands = WidgetCommands.choices(profile)
        val configuration = WidgetConfiguration(WidgetKind.PANEL, shortcuts = commands.map { WidgetShortcut(profile.id, it.id) })
        val state = WidgetSnapshot(loaded = true, profiles = listOf(profile), commands = mapOf(profile.id to commands),
            dynamicColor = true, colorMode = AppColorMode.DARK, fontScale = 2f, localeTags = "en")
        render("shared_icons_narrow_dynamic_large_text_before", DpSize(152.dp, 240.dp), configuration, state) {
            assertAllConfiguredCommandTargets(it, configuration)
        }
        render("shared_icons_narrow_dynamic_large_text_busy", DpSize(152.dp, 240.dp), configuration, state,
            feedback = WidgetFeedback(org.qinyan.rimoat.i18n.UiText.Resource(R.string.widget_sending), busy = true)) {
            assertCommandTargets(it, configuration, configuration.shortcuts.size,
                disabled = configuration.shortcuts.map { shortcut -> shortcut.buttonId }.toSet())
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h891dp-mdpi")
    fun configurationUsesSharedInputAndConfirmationIconsAfterSelection() = runBlocking {
        RuntimeEnvironment.setFontScale(2f)
        assertEquals(2f, app.resources.configuration.fontScale, 0f)
        AppStrings.initialize(app)
        val profile = television.copy(name = "TV", room = "", buttons = listOf(
            RemoteButton("source", "Input", key = ButtonKey.SOURCE, rawPattern = listOf(9000, 4500, 560)),
            RemoteButton("confirm", "OK", key = ButtonKey.OK, rawPattern = listOf(9000, 4500, 560))
        ))
        val commands = WidgetCommands.choices(profile)
        val snapshot = WidgetSnapshot(loaded = true, profiles = listOf(profile), commands = mapOf(profile.id to commands),
            fontScale = 2f, localeTags = "en")
        val source = WidgetConfiguration(WidgetKind.SINGLE, profile.id, listOf(WidgetShortcut(profile.id, "source")))
        val confirm = source.copy(shortcuts = listOf(WidgetShortcut(profile.id, "confirm")))
        val size = DpSize(280.dp, 64.dp)
        val sourcePreview = composeConfigurationPreview(app, source, snapshot, size)
        val confirmPreview = composeConfigurationPreview(app, confirm, snapshot, size)
        val component = ComponentName(app, SingleRemoteWidgetReceiver::class.java)
        val provider = AppWidgetProviderInfo().apply { this.provider = component }
        ReflectionHelpers.setField(provider, "providerInfo", ActivityInfo().apply {
            packageName = app.packageName
            name = component.className
            applicationInfo = app.applicationInfo
        })
        val manager = AppWidgetManager.getInstance(app)
        shadowOf(manager).addInstalledProvider(provider)
        shadowOf(manager).bindAppWidgetId(42, component)
        var draft by mutableStateOf(source)
        var saves = 0
        compose.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                WidgetConfigurationContent(42, WidgetKind.SINGLE, snapshot, draft,
                    WidgetPreview(size, if (draft == source) sourcePreview else confirmPreview),
                    saving = false, error = null, onChange = { draft = it }, onCancel = {},
                    onSave = { saves++ }, onRetry = {}, onAddRemote = {})
            }
        }
        compose.onNodeWithText("Input").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureScreenshot("src/test/screenshots/widget_expressive_configuration_input_before.webp", roborazziOptions = options)
        compose.onNodeWithContentDescription(app.getString(R.string.widget_change_button)).performClick()
        compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { assertEquals(confirm, draft) }
        compose.onNodeWithText("OK").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureScreenshot("src/test/screenshots/widget_expressive_configuration_confirm_after.webp", roborazziOptions = options)
        compose.onNodeWithText(app.getString(R.string.widget_done)).performClick()
        compose.runOnIdle { assertEquals(1, saves) }
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
        capture: Boolean = true,
        inspect: (ViewGroup) -> Unit
    ) {
        AppStrings.initialize(app)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val resourceConfiguration = Configuration(activity.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(snapshot.localeTags))
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
            if (capture) parent.captureScreenshot("src/test/screenshots/widget_expressive_$name.webp", roborazziOptions = options)
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

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.captureScreenshot(
        path: String, roborazziOptions: RoborazziOptions
    ) {
        screenshotDifferences.checkSucceeds {
            captureRoboImage(path, roborazziOptions = roborazziOptions)
        }
    }

    private fun View.captureScreenshot(path: String, roborazziOptions: RoborazziOptions) {
        screenshotDifferences.checkSucceeds {
            captureRoboImage(path, roborazziOptions = roborazziOptions)
        }
    }
}
