package org.qinyan.rimoat

import android.app.Application
import android.content.ContextWrapper
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
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
import org.junit.runner.RunWith
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedAcRawButtonLabel
import org.qinyan.rimoat.i18n.localizedLabel
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
    private val app get() = RuntimeEnvironment.getApplication()
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))
    private fun capture(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.webp", roborazziOptions = options)
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

    private fun learningFlow(prefix: String, dark: Boolean = false, fontScale: Float = 1f) {
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
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    IrRemoteTheme(darkTheme = dark, dynamicColor = false) { RemoteControlScreen(vm, onBack = {}) }
                }
            }
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
            awaitUi { vm.acState.value.temperature == 27 }
            compose.onNodeWithContentDescription(localizedLabel(AcAction.MODE.label)).performScrollTo().performClick()
            awaitUi { vm.acState.value.mode == AcMode.HEAT }
            compose.onNodeWithContentDescription(increase).performScrollTo()
            capture("${prefix}_dynamic")

            val learned = vm.remoteProfile.value!!.buttons.single()
            val label = localizedAcRawButtonLabel(learned)
            if (compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isEmpty()) {
                val more = compose.onNodeWithText(localizedLabel("更多功能"))
                if (!more.isDisplayed()) more.performScrollTo()
                more.performClick()
            }
            compose.onNodeWithContentDescription(label).performScrollTo().assertIsDisplayed().performClick()
            awaitUi { sent.size == 3 && !vm.isTransmitting.value }
            compose.runOnIdle {
                assertEquals(recorded, sent.last())
                assertEquals(listOf(AcAction.TEMP_UP, AcAction.MODE), encoder.actions)
                assertFalse(vm.remoteProfile.value!!.useStaticAcButtons)
                assertEquals(27, vm.acState.value.temperature)
                assertEquals(AcMode.HEAT, vm.acState.value.mode)
                assertNull(vm.operationError.value)
            }
            assertEquals(vm.acState.value, runBlocking { repo.getRemote(profile.id) }!!.acSettings)
            if (compose.onAllNodesWithTag("remote_secondary").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("remote_secondary").performTouchInput { swipeUp() }
                compose.onNodeWithContentDescription(label).assertIsDisplayed()
            }
            capture("${prefix}_recorded")
        } finally {
            compose.runOnIdle { store.clear() }
            runBlocking { scope.coroutineContext.job.cancelAndJoin() }
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
