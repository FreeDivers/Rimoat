package org.qinyan.rimoat

import android.app.Application
import android.content.ContextWrapper
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelStore
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.ErrorCollector
import org.junit.runner.RunWith
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.TransmissionReceipt
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedAcRawButtonLabel
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.i18n.labelRes
import org.qinyan.rimoat.ui.screens.RemoteControlScreen
import org.qinyan.rimoat.ui.screens.RemoteListScreen
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class AcLearningControlsRoborazziTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()
    @get:Rule val screenshotErrors = ErrorCollector()
    private val app get() = RuntimeEnvironment.getApplication()
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))
    private fun capture(name: String) {
        screenshotErrors.checkSucceeds(java.util.concurrent.Callable {
            compose.onRoot().captureRoboImage("src/test/screenshots/$name.webp", roborazziOptions = options)
        })
    }
    private fun awaitUi(condition: () -> Boolean) = compose.waitUntil(5_000) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private class Encoder : AcSignalEncoder {
        val actions = mutableListOf<AcAction>()
        private val supported = setOf(AcAction.POWER_ON, AcAction.POWER_OFF, AcAction.TEMP_UP,
            AcAction.TEMP_DOWN, AcAction.MODE, AcAction.FAN)
        override suspend fun protocolFor(sourceRemoteId: String) = 27
        override suspend fun supportedModes(protocol: Int) = listOf(AcMode.COOL, AcMode.HEAT)
        override suspend fun availableActions(protocol: Int) = supported
        override suspend fun supportsAction(protocol: Int, settings: AcSettings, action: AcAction) = action in supported
        override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction): List<IrSignal.Raw> {
            actions += action
            return listOf(IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560)))
        }
    }

    private fun repository(dir: File, scope: CoroutineScope): RemoteRepository =
        RemoteRepository(object : ContextWrapper(app) { override fun getFilesDir() = dir }, scope)

    @Test fun learningKeepsTemperatureAndModeControlsInPortrait() = learningFlow("ac_learned_portrait")

    @Test
    @Config(qualifiers = "en-rUS-w891dp-h411dp-land-mdpi")
    fun learnedKeysRemainAccessibleInDarkLandscape() = learningFlow("ac_learned_landscape", dark = true)

    @Test
    @Config(qualifiers = "ja-rJP-w320dp-h891dp-mdpi")
    fun learnedLabelsFitNarrowScreenWithLargeText() = learningFlow("ac_learned_large_text", fontScale = 2f)

    @Test fun learnedControlsUseAmoledTheme() =
        learningFlow("ac_learned_amoled", dark = true, pureBlack = true)

    @Test fun learnedControlsUseDynamicLightTheme() =
        learningFlow("ac_learned_dynamic_light", dynamic = true)

    @Test fun learnedControlsUseDynamicDarkTheme() =
        learningFlow("ac_learned_dynamic_dark", dark = true, dynamic = true)

    private fun learningFlow(prefix: String, dark: Boolean = false, fontScale: Float = 1f,
        pureBlack: Boolean = false, dynamic: Boolean = false) {
        RuntimeEnvironment.setFontScale(fontScale)
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        val repo = repository(temporary.newFolder(), scope)
        val button = RemoteButton("temperature", AcAction.TEMP_UP.label, iconName = "add", sourceKeyType = AcAction.TEMP_UP.keyType,
            acAction = AcAction.TEMP_UP)
        val profile = RemoteProfile("ac", "AC", category = DeviceCategory.AC, acProtocolId = 27, buttons = listOf(button))
        runBlocking { repo.awaitLoaded(); repo.saveRemote(profile) }
        val encoder = Encoder()
        val sent = mutableListOf<IrSignal>()
        val vm = RemoteControlViewModel(profile.id, repository = repo, initialProfile = profile,
            acSignalEncoder = encoder, onTransmitSignal = { sent += it }).also { store.put("control", it) }
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = dark, dynamicColor = dynamic, pureBlackBackground = pureBlack) {
                    RemoteControlScreen(vm, onBack = {})
                }
            }
            assertEquals(fontScale, app.resources.configuration.fontScale, 0.01f)
            awaitUi { AcAction.TEMP_UP in vm.availableAcActions.value }
            val increase = app.getString(R.string.control_temp_up)
            compose.onNodeWithContentDescription(increase).assertIsEnabled()
            capture("${prefix}_before")
            compose.onNodeWithContentDescription(app.getString(R.string.control_enter_learning)).performClick()
            compose.onNodeWithContentDescription(app.getString(R.string.control_learn, increase)).performScrollTo().performClick()
            val recorded = IrSignal.Raw(39500, intArrayOf(2400, 1200, 600))
            compose.runOnIdle {
                val target = checkNotNull(vm.learningDialogButton.value)
                assertEquals(button.id, target.id)
                vm.saveLearnedButton(target, recorded)
            }
            awaitUi {
                assertNull(vm.learningError.value?.resolve(app))
                vm.remoteProfile.value?.buttons?.single()?.isLearned == true && !vm.isSavingButton.value
            }
            compose.onNodeWithContentDescription(app.getString(R.string.screen_leave_learning)).performClick()
            compose.onNodeWithContentDescription(increase).performScrollTo().assertIsEnabled().performClick()
            awaitUi { sent.size == 1 && !vm.isTransmitting.value }
            assertEquals(recorded, sent.single())
            assertEquals(26, vm.acState.value.temperature)
            compose.onNodeWithContentDescription(localizedLabel(AcAction.MODE)).performScrollTo().performClick()
            awaitUi { vm.acState.value.mode == AcMode.HEAT && !vm.isTransmitting.value }
            compose.onNodeWithContentDescription(increase).performScrollTo()
            capture("${prefix}_dynamic")

            val learned = vm.remoteProfile.value!!.buttons.single()
            val label = localizedAcRawButtonLabel(learned)
            compose.onNodeWithContentDescription(label).assertDoesNotExist()
            compose.onNodeWithContentDescription(increase).performScrollTo().assertIsDisplayed().performClick()
            awaitUi { sent.size == 3 && !vm.isTransmitting.value }
            compose.runOnIdle {
                assertEquals(recorded, sent.last())
                assertEquals(AcAction.TEMP_UP, learned.acAction)
                assertEquals(listOf(AcAction.MODE), encoder.actions)
                assertFalse(vm.remoteProfile.value!!.useStaticAcButtons)
                assertEquals(26, vm.acState.value.temperature)
                assertEquals(AcMode.HEAT, vm.acState.value.mode)
                assertNull(vm.operationError.value)
            }
            assertEquals(vm.acState.value, runBlocking { repo.getRemote(profile.id) }!!.acSettings)
            capture("${prefix}_recorded")
        } finally {
            compose.runOnIdle { store.clear() }
            runBlocking { scope.coroutineContext.job.cancelAndJoin() }
            RuntimeEnvironment.setFontScale(1f)
        }
    }

    @Test fun learnedTemplateUsesStandardAcPositions() = staticLearningFlow("ac_learned_static_light")

    @Test fun independentPowerSignalsResolveStableMetadataWithoutAcAction() {
        AppStrings.initialize(app)
        val powerOn = RemoteButton("power_on", "Start", sourceKeyType = AcAction.POWER_ON.keyType,
            isLearned = true, rawPattern = listOf(2000, 1000, 500))
        val powerOff = RemoteButton("power_off", "Stop", key = ButtonKey.POWER_OFF,
            builtinLabel = ButtonKey.POWER_OFF.name,
            isLearned = true, rawPattern = listOf(2400, 1200, 600))
        val profile = RemoteProfile("independent_power", "AC", category = DeviceCategory.AC,
            useStaticAcButtons = true, buttons = listOf(powerOn, powerOff))
        val sent = mutableListOf<IrSignal>()
        val store = ViewModelStore()
        val encoder = Encoder()
        val vm = RemoteControlViewModel(profile.id, initialProfile = profile,
            acSignalEncoder = encoder, onTransmitSignal = { sent += it }).also { store.put("control", it) }
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) { RemoteControlScreen(vm, onBack = {}) }
            }
            compose.onNodeWithContentDescription(app.getString(AcAction.POWER_ON.labelRes))
                .assertIsEnabled().performClick()
            awaitUi { sent.size == 1 && !vm.isTransmitting.value }
            compose.onNodeWithContentDescription(app.getString(AcAction.POWER_OFF.labelRes))
                .assertIsEnabled().performClick()
            awaitUi { sent.size == 2 && !vm.isTransmitting.value }
            compose.runOnIdle {
                assertEquals(listOf(powerOn.toIrSignal(), powerOff.toIrSignal()), sent)
                assertEquals(profile.acSettings, vm.acState.value)
                assertTrue(encoder.actions.isEmpty())
                assertNull(vm.operationError.value)
            }
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w891dp-h411dp-land-mdpi")
    fun learnedTemplateUsesStandardPositionsInDarkLandscape() =
        staticLearningFlow("ac_learned_static_landscape", dark = true)

    @Test fun learnedTemplateUsesAmoledSurfaces() =
        staticLearningFlow("ac_learned_static_amoled", dark = true, pureBlack = true)

    @Test
    @Config(qualifiers = "ja-rJP-w320dp-h891dp-mdpi")
    fun learnedTemplateFitsNarrowLargeTextWithDynamicColors() =
        staticLearningFlow("ac_learned_static_dynamic_large_text", dynamic = true, fontScale = 2f)

    private fun staticLearningFlow(prefix: String, dark: Boolean = false, pureBlack: Boolean = false,
        dynamic: Boolean = false, fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        val dir = temporary.newFolder()
        val repo = repository(dir, scope)
        val custom = RemoteButton("custom_temperature", app.getString(AcAction.TEMP_UP.labelRes),
            isLearned = true, rawPattern = listOf(1800, 900, 500))
        val power = RemoteButton("power", ButtonKey.POWER.name, key = ButtonKey.POWER,
            builtinLabel = ButtonKey.POWER.name, acAction = AcAction.POWER_ON,
            isLearned = true, rawPattern = listOf(2000, 1000, 500))
        val profile = RemoteProfile("static_ac", "AC", category = DeviceCategory.AC,
            useStaticAcButtons = true, buttons = listOf(power, custom))
        runBlocking { repo.awaitLoaded(); repo.saveRemote(profile) }
        val sent = mutableListOf<IrSignal>()
        val transmissionFinished = CompletableDeferred<Unit>()
        val encoder = Encoder()
        val executor = RemoteCommandExecutor(
            readProfile = { repo.getRemote(it) },
            commitSettings = { _, _ -> error("A learned signal cannot commit encoded AC state") },
            encoder = encoder,
            route = { TransmitterType.BUILT_IN },
            transmit = { signals, _, _, beforeTransmission ->
                beforeTransmission()
                sent += signals
                transmissionFinished.await()
                TransmissionReceipt(simulated = false)
            },
            journal = RemoteCommandJournal(File(dir, "commands.json"))
        )
        val vm = RemoteControlViewModel(profile.id, repository = repo, initialProfile = profile,
            acSignalEncoder = encoder, commandExecutor = executor).also { store.put("control", it) }
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = dark, dynamicColor = dynamic, pureBlackBackground = pureBlack) {
                    RemoteControlScreen(vm, onBack = {})
                }
            }
            awaitUi { vm.remoteProfile.value != null }
            assertEquals(fontScale, app.resources.configuration.fontScale, 0.01f)
            val increase = app.getString(AcAction.TEMP_UP.labelRes)
            val missingSignal = app.getString(R.string.control_reason_missing_signal)
            compose.onNodeWithContentDescription(increase).performScrollTo().assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, missingSignal)
            ).performClick()
            compose.runOnIdle { assertTrue(sent.isEmpty()) }
            compose.onNodeWithText(app.getString(R.string.control_target_temp)).assertDoesNotExist()
            compose.onNodeWithContentDescription(app.getString(R.string.screen_resend_settings)).assertDoesNotExist()
            capture("${prefix}_before")

            compose.onNodeWithContentDescription(app.getString(R.string.control_enter_learning)).performClick()
            compose.onNodeWithContentDescription(app.getString(R.string.control_learn, increase)).performScrollTo().performClick()
            val recorded = IrSignal.Raw(39500, intArrayOf(2400, 1200, 600))
            compose.runOnIdle {
                val target = checkNotNull(vm.learningDialogButton.value)
                assertEquals(AcAction.TEMP_UP, target.acAction)
                vm.saveLearnedButton(target, recorded)
            }
            awaitUi {
                vm.remoteProfile.value?.buttons?.any { it.acAction == AcAction.TEMP_UP && it.isLearned } == true &&
                    !vm.isSavingButton.value
            }
            compose.onNodeWithContentDescription(app.getString(R.string.screen_leave_learning)).performClick()
            compose.onNodeWithContentDescription(increase).performScrollTo().assertIsEnabled().performClick()
            awaitUi { vm.isTransmitting.value && sent.size == 1 }
            compose.onNodeWithContentDescription(increase).assertIsNotEnabled()
            capture("${prefix}_busy")
            transmissionFinished.complete(Unit)
            awaitUi { !vm.isTransmitting.value }
            compose.onNodeWithContentDescription(increase).assertIsEnabled()
            capture("${prefix}_after")
            compose.runOnIdle {
                assertEquals(recorded, sent.single())
                assertTrue(encoder.actions.isEmpty())
                assertEquals(profile.acSettings, vm.acState.value)
            }
            val mode = app.getString(AcAction.MODE.labelRes)
            compose.onNodeWithContentDescription(mode).performScrollTo().assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, missingSignal)
            ).performClick()
            compose.runOnIdle { assertEquals(1, sent.size) }
            val customLabel = localizedAcRawButtonLabel(custom)
            if (compose.onAllNodesWithContentDescription(customLabel).fetchSemanticsNodes().isEmpty()) {
                val more = compose.onNodeWithText(app.getString(R.string.control_more_page))
                if (!more.isDisplayed()) more.performScrollTo()
                more.performClick()
            }
            compose.onNodeWithContentDescription(customLabel).performScrollTo().performClick()
            awaitUi { sent.size == 2 && !vm.isTransmitting.value }
            compose.runOnIdle {
                assertEquals(custom.toIrSignal(), sent.last())
                assertEquals(profile.acSettings, vm.acState.value)
                assertTrue(encoder.actions.isEmpty())
                assertNull(vm.operationError.value)
            }
        } finally {
            transmissionFinished.complete(Unit)
            compose.runOnIdle { store.clear() }
            runBlocking { scope.coroutineContext.job.cancelAndJoin() }
            RuntimeEnvironment.setFontScale(1f)
        }
    }

    @Test fun defaultListOrderSurvivesAcOperationAndRecentSortStillWorks() {
        AppStrings.initialize(app)
        val dir = temporary.newFolder()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        val repo = repository(dir, scope)
        val older = RemoteProfile("older", "Bedroom AC", category = DeviceCategory.AC, acProtocolId = 27, updatedAt = 100)
        val newer = older.copy(id = "newer", name = "Living room AC", updatedAt = 200)
        runBlocking {
            repo.awaitLoaded()
            repo.saveRemote(older); repo.saveRemote(newer)
            for (profile in listOf(older, newer)) {
                File(dir, "remotes/${profile.id}.json").writeText(Json.encodeToString(RemoteProfile.serializer(), profile))
            }
            repo.loadAllRemotes()
        }
        val transmitter = IrTransmitterManager(app).apply { setForceMockMode(true) }
        val vm = RemoteListViewModel(repo, transmitter, ThemePreferences(app)).also { store.put("list", it) }
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) { RemoteListScreen(vm, {}, {}) }
            }
            awaitUi { vm.filteredRemotes.value.size == 2 }
            fun assertAbove(first: String, second: String) {
                assertTrue(compose.onNodeWithText(first).fetchSemanticsNode().boundsInRoot.top <
                    compose.onNodeWithText(second).fetchSemanticsNode().boundsInRoot.top)
            }
            assertAbove(newer.name, older.name)
            capture("ac_order_before")
            runBlocking {
                repo.updateAcSettings(older.id, older.acSettings.copy(temperature = 27))
                repo.markUsed(older.id)
            }
            compose.waitForIdle()
            assertAbove(newer.name, older.name)
            capture("ac_order_after")
            compose.onNodeWithContentDescription(app.getString(R.string.screen_sort)).performClick()
            compose.onNodeWithText(app.getString(R.string.screen_recent)).performClick()
            awaitUi { vm.filteredRemotes.value.firstOrNull()?.id == older.id }
            compose.waitForIdle()
            assertAbove(older.name, newer.name)
            capture("ac_order_recent")
        } finally {
            compose.runOnIdle { store.clear() }
            transmitter.release()
            runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        }
    }
}
