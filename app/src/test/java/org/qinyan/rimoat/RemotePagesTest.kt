package org.qinyan.rimoat

import android.app.Application
import android.content.ContextWrapper
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.labelRes
import org.qinyan.rimoat.ui.components.FunctionCard
import org.qinyan.rimoat.ui.components.FunctionGroup
import org.qinyan.rimoat.ui.components.RemoteFunctionGroups
import org.qinyan.rimoat.ui.components.buildFunctionCards
import org.qinyan.rimoat.ui.components.remoteExtraButtons
import org.qinyan.rimoat.ui.components.tvStandardButtons
import org.qinyan.rimoat.ui.screens.RemoteControlScreen
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class RemotePagesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    private fun remote(category: DeviceCategory, labels: List<String>, name: String = "客厅风扇") = LegacyRemoteMigration.normalizeKeys(RemoteProfile(
        id = "test", name = name, category = category,
        buttons = labels.mapIndexed { index, label ->
            RemoteButton("key_$index", label, rawPattern = listOf(9000, 4500, 560))
        }
    ))

    @Test fun televisionKeepsDigitsAndDirectionKeysOnRegularPage() {
        val p = remote(DeviceCategory.TV, listOf("电源", "上", "下", "左", "右", "OK", "返回", "0", "1", "播放", "字幕"))
        assertEquals(listOf("播放", "字幕"), remoteExtraButtons(p).map { it.label })
        assertEquals(7, tvStandardButtons(p).all.size)
    }

    @Test fun simpleRemotesHaveNoEmptySecondPage() {
        DeviceCategory.entries.forEach { assertTrue(remoteExtraButtons(remote(it, listOf("电源", "音量+"))).isEmpty()) }
    }

    @Test fun genericKeepsNumbersAndOnlyFirstMatchingDirectionsSoDuplicateKeysRemainAccessible() {
        val p = remote(DeviceCategory.CUSTOM, listOf("电源", "上", "下", "左", "右", "OK", "0", "1", "A", "B", "C", "D", "E", "F", "G", "上"))
        assertEquals(listOf("G", "上"), remoteExtraButtons(p).map { it.label })
    }

    @Test fun acTemperatureLimitReasonDialogShowsConciseCopy() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val encoder = object : AcSignalEncoder {
            override suspend fun protocolFor(sourceRemoteId: String) = 1
            override suspend fun supportedModes(protocol: Int) = listOf(AcMode.COOL, AcMode.HEAT)
            override suspend fun availableActions(protocol: Int) = setOf(
                AcAction.POWER_ON, AcAction.POWER_OFF, AcAction.TEMP_UP, AcAction.TEMP_DOWN, AcAction.MODE, AcAction.FAN
            )
            override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction) =
                listOf(IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560)))
        }
        val minAc = RemoteProfile(
            id = "ac_min",
            name = "客厅空调",
            category = DeviceCategory.AC,
            acProtocolId = 1,
            acSettings = AcSettings(powerOn = true, mode = AcMode.COOL, temperature = 16)
        )
        val maxAc = minAc.copy(acSettings = AcSettings(powerOn = true, mode = AcMode.COOL, temperature = 30))
        val store = ViewModelStore()
        val vm = RemoteControlViewModel("ac_min", initialProfile = minAc, acSignalEncoder = encoder).also { store.put("ac_limit", it) }

        try {
            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    RemoteControlScreen(viewModel = vm, onBack = {})
                }
            }
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.availableAcActions.value.contains(AcAction.TEMP_UP)
            }
            composeRule.waitForIdle()

            repeat(3) { composeRule.onNodeWithContentDescription("降低温度").performClick() }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("原因").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("已达最低温度 16 °C。").assertIsDisplayed()
            composeRule.onNodeWithText("学习模式").assertDoesNotExist()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/remote_ac_temp_min_reason_dialog.webp", roborazziOptions = options)

            composeRule.onNodeWithText("关闭").performClick()
            composeRule.waitForIdle()

            vm.setProfileForTesting(maxAc)
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.availableAcActions.value.contains(AcAction.TEMP_DOWN)
            }
            composeRule.waitForIdle()
            repeat(3) { composeRule.onNodeWithContentDescription("升高温度").performClick() }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("原因").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("已达最高温度 30 °C。").assertIsDisplayed()
            composeRule.onNodeWithText("学习模式").assertDoesNotExist()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/remote_ac_temp_max_reason_dialog.webp", roborazziOptions = options)
        } finally {
            store.clear()
        }
    }

    @Test fun interleavedFunctionGroupsProduceSingleHeading() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val buttons = listOf(
            RemoteButton("turbo", "Turbo", acAction = AcAction.TURBO, key = ButtonKey.TURBO),
            RemoteButton("sleep", "Sleep", acAction = AcAction.SLEEP, key = ButtonKey.SLEEP),
            RemoteButton("vent", "Vent", acAction = AcAction.VENTILATION, key = ButtonKey.VENTILATION),
            RemoteButton("aux", "Aux", acAction = AcAction.AUXILIARY_HEAT, key = ButtonKey.AUXILIARY_HEAT),
            RemoteButton("dry", "Dry", acAction = AcAction.DRY, key = ButtonKey.DRY),
            RemoteButton("eco", "Eco", acAction = AcAction.ECO, key = ButtonKey.ECO),
            RemoteButton("quiet", "Quiet", acAction = AcAction.QUIET, key = ButtonKey.MUTE)
        )
        val cards = buildFunctionCards(buttons)
        assertEquals(2, cards.size)
        val modeCard = cards[0] as FunctionCard.Single
        assertEquals(FunctionGroup.OPERATING_MODE, modeCard.group)
        assertEquals(listOf("turbo", "sleep", "eco", "quiet"), modeCard.buttons.map { it.id })
        val airCard = cards[1] as FunctionCard.Single
        assertEquals(FunctionGroup.AIR, airCard.group)
        assertEquals(listOf("vent", "aux", "dry"), airCard.buttons.map { it.id })

        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                ProvideExpressiveCanvas {
                    RemoteFunctionGroups(buttons, onSend = {}, onLearn = null, learning = false, preserveOrder = true)
                }
            }
        }
        composeRule.onAllNodesWithText(app.getString(R.string.control_mode_group)).assertCountEquals(1)
        composeRule.onAllNodesWithText(app.getString(R.string.control_air_group)).assertCountEquals(1)
    }

    @Test fun smallAdjacentGroupsMergeIntoOneCardAndIsolatedSmallMergesIntoOther() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val buttons = listOf(
            RemoteButton("light", "Light", key = ButtonKey.LIGHT),
            RemoteButton("mute", "Mute", key = ButtonKey.MUTE),
            RemoteButton("ion", "Ion", key = ButtonKey.ION),
            RemoteButton("custom_1", "C1"),
            RemoteButton("custom_2", "C2"),
            RemoteButton("custom_3", "C3"),
            RemoteButton("eco", "Eco", key = ButtonKey.ECO)
        )
        val cards = buildFunctionCards(buttons)
        assertEquals(2, cards.size)
        val merged = cards[0] as FunctionCard.Merged
        assertEquals(
            listOf(FunctionGroup.DISPLAY, FunctionGroup.SOUND, FunctionGroup.AIR),
            merged.groups.map { it.first }
        )
        val other = cards[1] as FunctionCard.Single
        assertEquals(FunctionGroup.OTHER, other.group)
        assertEquals(listOf("custom_1", "custom_2", "custom_3", "eco"), other.buttons.map { it.id })

        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                ProvideExpressiveCanvas {
                    RemoteFunctionGroups(buttons, onSend = {}, onLearn = null, learning = false)
                }
            }
        }
        val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        listOf(
            R.string.control_display_group,
            R.string.control_sound_group,
            R.string.control_air_group,
            R.string.control_other_group
        ).forEach { res ->
            composeRule.onNode(
                androidx.compose.ui.test.hasText(app.getString(res)) and isHeading
            ).assertIsDisplayed()
        }
    }

    @Test fun portraitTvDpadUsesStableIdentityWithoutLabelLookupOrDuplicateCreation() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val dir = Files.createTempDirectory("portrait-tv-dpad-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        val learner = IrLearnerManager(app, scope)
        learner.mockLearner.setSimulatedDelayMs(0)
        learner.setForceMockMode(true)
        val transmitter = IrTransmitterManager(app, scope)
        val pattern = listOf(9000, 4500, 560, 1690)
        val profile = RemoteProfile(
            id = "tv_renamed",
            name = "Living room TV",
            category = DeviceCategory.TV,
            keySchemaVersion = 2,
            buttons = listOf(
                RemoteButton("tv_power", "Power", key = ButtonKey.POWER, rawPattern = pattern),
                RemoteButton("tv_up", "Move Up", key = ButtonKey.UP, rawPattern = pattern)
            )
        )
        try {
            val repo = RemoteRepository(object : ContextWrapper(app) { override fun getFilesDir() = dir }, scope)
            runBlocking {
                repo.awaitLoaded()
                repo.saveRemote(profile)
            }
            val vm = RemoteControlViewModel(
                remoteId = profile.id,
                initialProfile = profile,
                repository = repo,
                transmitterManager = transmitter,
                learnerManager = learner
            ).also { store.put("tv", it) }

            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    RemoteControlScreen(viewModel = vm, onBack = {})
                }
            }
            composeRule.waitForIdle()

            // Renamed "Move Up" must remain enabled via ButtonKey.UP rather than failing label lookup.
            val upLabel = app.getString(R.string.control_up)
            composeRule.onNodeWithContentDescription(upLabel).assertIsEnabled().performClick()
            composeRule.waitForIdle()

            // Enter learning mode, learn missing Down key, and verify ASCII direction_down is persisted without duplicates.
            composeRule.onNodeWithContentDescription(app.getString(R.string.control_enter_learning)).performClick()
            composeRule.waitForIdle()
            val learnDown = app.getString(R.string.control_learn, app.getString(R.string.control_down))
            composeRule.onNodeWithContentDescription(learnDown).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(app.getString(R.string.screen_start_record)).performClick()
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.capturedLearningSignal.value != null
            }
            composeRule.onNodeWithText(app.getString(R.string.screen_save_button)).performClick()
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                repo.remotes.value.single().buttons.any { it.id == "direction_down" }
            }
            // Re-learn Down to confirm it updates direction_down instead of creating a duplicate.
            composeRule.onNodeWithContentDescription(learnDown).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(app.getString(R.string.screen_start_record)).performClick()
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.capturedLearningSignal.value != null
            }
            composeRule.onNodeWithText(app.getString(R.string.screen_save_button)).performClick()
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.learningDialogButton.value == null
            }
            val saved = repo.remotes.value.single()
            assertEquals(1, saved.buttons.count { it.key == ButtonKey.DOWN })
            assertEquals("direction_down", saved.buttons.single { it.key == ButtonKey.DOWN }.id)
            assertTrue(saved.buttons.none { it.id.contains("上") || it.id.contains("下") })
        } finally {
            store.clear()
            learner.cancel()
            learner.unregisterReceiver()
            transmitter.release()
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    @Test fun migratedLegacyHiddenDirectionsStayHiddenInPortraitLearningMode() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val dir = Files.createTempDirectory("migrated-hidden-portrait-").toFile()
        val remotesDir = File(dir, "remotes").apply { mkdirs() }
        val legacy = RemoteProfile(
            id = "legacy_tv_hidden",
            name = "客厅旧电视",
            category = DeviceCategory.TV,
            keySchemaVersion = 1,
            buttons = listOf(
                RemoteButton("power", "电源", builtinLabel = "电源", key = ButtonKey.POWER,
                    rawPattern = listOf(9000, 4500, 560)),
                RemoteButton("direction_上", "上", builtinLabel = "上", key = ButtonKey.UP,
                    isLearned = true, rawPattern = listOf(9000, 4500, 560))
            ),
            panelLayout = RemotePanelLayout(hidden = setOf("key:direction_下", "key:direction_左"))
        )
        File(remotesDir, "${legacy.id}.json").writeText(Json { encodeDefaults = true }.encodeToString(legacy))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        try {
            val repo = RemoteRepository(object : ContextWrapper(app) { override fun getFilesDir() = dir }, scope)
            val migrated = runBlocking { repo.loadAllRemotes().single() }
            assertEquals(setOf("key:direction_down", "key:direction_left"), migrated.panelLayout.hidden)
            assertEquals("direction_up", migrated.buttons.single { it.key == ButtonKey.UP }.id)

            val vm = RemoteControlViewModel(legacy.id, initialProfile = migrated, repository = repo).also { store.put("tv", it) }
            vm.setLearningMode(true)
            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    RemoteControlScreen(viewModel = vm, onBack = {})
                }
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(app.getString(R.string.control_learn, app.getString(R.string.control_up))).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(app.getString(R.string.control_learn, app.getString(R.string.control_right))).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(app.getString(R.string.control_learn, app.getString(R.string.control_down))).assertDoesNotExist()
            composeRule.onNodeWithContentDescription(app.getString(R.string.control_learn, app.getString(R.string.control_left))).assertDoesNotExist()
            composeRule.onRoot().captureRoboImage(
                "src/test/screenshots/remote_tv_migrated_hidden_learning_portrait.webp",
                roborazziOptions = options
            )
        } finally {
            store.clear()
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
            dir.deleteRecursively()
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-mdpi")
    fun learnedTvRemoteRendersDpadIconsAndGroupHeadingsInEnglishPortrait() =
        exerciseLearnedTvLocale("en")

    @Test
    @Config(qualifiers = "ja-w411dp-h891dp-mdpi")
    fun learnedTvRemoteRendersDpadIconsAndGroupHeadingsInJapanesePortrait() =
        exerciseLearnedTvLocale("ja")

    private fun exerciseLearnedTvLocale(localeSuffix: String) {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val pattern = listOf(9000, 4500, 560, 1690)
        fun learned(id: String, key: ButtonKey) = RemoteButton(
            id = id,
            label = app.getString(key.labelRes),
            builtinLabel = key.name,
            key = key,
            isLearned = true,
            rawPattern = pattern
        )
        val profile = RemoteProfile(
            id = "learned_tv_$localeSuffix",
            name = "Living room TV",
            category = DeviceCategory.TV,
            keySchemaVersion = 2,
            buttons = listOf(
                learned("power", ButtonKey.POWER),
                learned("source", ButtonKey.SOURCE),
                learned("mute", ButtonKey.MUTE),
                learned("direction_up", ButtonKey.UP),
                learned("direction_down", ButtonKey.DOWN),
                learned("direction_left", ButtonKey.LEFT),
                learned("direction_right", ButtonKey.RIGHT),
                learned("direction_ok", ButtonKey.OK),
                learned("back", ButtonKey.BACK),
                learned("home", ButtonKey.HOME),
                learned("menu", ButtonKey.MENU),
                learned("vol_up", ButtonKey.VOLUME_UP),
                learned("vol_down", ButtonKey.VOLUME_DOWN),
                learned("ch_up", ButtonKey.CHANNEL_UP),
                learned("ch_down", ButtonKey.CHANNEL_DOWN),
                learned("play", ButtonKey.PLAY),
                learned("pause", ButtonKey.PAUSE),
                learned("stop", ButtonKey.STOP),
                learned("subtitles", ButtonKey.SUBTITLES),
                learned("brightness", ButtonKey.BRIGHTNESS),
                learned("osd", ButtonKey.OSD),
                learned("timer", ButtonKey.TIMER),
                learned("sleep", ButtonKey.SLEEP),
                learned("info", ButtonKey.INFO)
            )
        )
        val store = ViewModelStore()
        val vm = RemoteControlViewModel(profile.id, initialProfile = profile).also { store.put("tv", it) }
        try {
            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    RemoteControlScreen(viewModel = vm, onBack = {})
                }
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(app.getString(R.string.control_up)).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(app.getString(R.string.control_ok)).assertIsDisplayed()
            composeRule.onRoot().captureRoboImage(
                "src/test/screenshots/remote_tv_learned_${localeSuffix}_portrait.webp",
                roborazziOptions = options
            )
            composeRule.onNodeWithText(app.getString(R.string.control_more_page)).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(app.getString(R.string.control_play)).assertIsDisplayed()
            composeRule.onNodeWithText(app.getString(R.string.control_picture_group)).assertIsDisplayed()
            composeRule.onRoot().captureRoboImage(
                "src/test/screenshots/remote_tv_learned_${localeSuffix}_portrait_more.webp",
                roborazziOptions = options
            )
        } finally {
            store.clear()
        }
    }
}
