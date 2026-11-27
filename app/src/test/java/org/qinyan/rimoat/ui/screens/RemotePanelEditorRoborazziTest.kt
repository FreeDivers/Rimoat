package org.qinyan.rimoat.ui.screens

import android.app.Application
import android.content.ContextWrapper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelStore
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.ButtonKey
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemotePanelLayout
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.components.icon
import org.qinyan.rimoat.ui.components.resolveButtonIcon
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
class RemotePanelEditorRoborazziTest {
    @get:Rule val screenshotDifferences = org.junit.rules.ErrorCollector()

    @get:Rule
    val composeRule = createComposeRule()

    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))

    private fun key(id: String, label: String, icon: String? = null, semanticKey: ButtonKey? = null) =
        RemoteButton(id, label, iconName = icon, key = semanticKey, rawPattern = listOf(9000, 4500, 560))

    private val profile = RemoteProfile(
        id = "panel_editor_fan", name = "风扇", category = DeviceCategory.FAN,
        buttons = listOf(
            key("power", "电源", "power", ButtonKey.POWER), key("speed", "风速", "speed", ButtonKey.FAN_SPEED),
            key("swing", "摇头", "oscillation", ButtonKey.OSCILLATION), key("timer", "定时", "timer", ButtonKey.TIMER),
            key("mode", "模式", "mode", ButtonKey.MODE), key("natural", "自然风", "nature", ButtonKey.WIND_TYPE),
            key("light", "灯光", semanticKey = ButtonKey.LIGHT), key("sleep", "睡眠", semanticKey = ButtonKey.SLEEP)
        )
    )

    @Test
    fun tilesMirrorPanelGridAndSupportHideAndDragReorder() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val saved = mutableStateOf<RemotePanelLayout?>(null)
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                RemotePanelEditor(profile, emptySet(), onSave = { saved.value = it }, onBack = {})
            }
        }
        fun tile(label: String) = composeRule.onNodeWithContentDescription(app.getString(R.string.panel_show_control, label))

        tile("风速").assertIsOn()
        composeRule.onRoot().captureScreenshot("src/test/screenshots/panel_editor_before.webp", roborazziOptions = options)

        tile("定时").performClick()
        composeRule.waitForIdle()
        tile("定时").assertIsOff()
        assertTrue("key:timer" in saved.value!!.hidden)

        // Long-press the first key and drop it on the first slot of the next row.
        val from = tile("风速").fetchSemanticsNode().boundsInRoot.center
        val to = tile("模式").fetchSemanticsNode().boundsInRoot.center
        val steps = 12
        val delta = (to - from) / steps.toFloat()
        tile("风速").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(steps) { moveBy(delta); advanceEventTime(16) }
            up()
        }
        composeRule.waitForIdle()
        assertEquals(listOf("key:swing", "key:timer", "key:mode", "key:speed"), saved.value!!.order.take(4))
        assertTrue("key:timer" in saved.value!!.hidden)
        tile("风速").assertIsOn()
        composeRule.onRoot().captureScreenshot("src/test/screenshots/panel_editor_after.webp", roborazziOptions = options)
    }

    @Test
    fun sameFrameDragAndReleasePersistsLatestOrder() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val saved = mutableStateOf<RemotePanelLayout?>(null)
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                RemotePanelEditor(profile, emptySet(), onSave = { saved.value = it }, onBack = {})
            }
        }
        fun tile(label: String) = composeRule.onNodeWithContentDescription(app.getString(R.string.panel_show_control, label))
        val from = tile("风速").fetchSemanticsNode().boundsInRoot.center
        val to = tile("模式").fetchSemanticsNode().boundsInRoot.center
        tile("风速").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(to - from)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(listOf("key:swing", "key:timer", "key:mode", "key:speed"), saved.value?.order?.take(4))
    }

    @Test
    fun twoSameFrameMovesAndReleaseUseLatestGroupOrder() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val saved = mutableStateOf<RemotePanelLayout?>(null)
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                RemotePanelEditor(profile, emptySet(), onSave = { saved.value = it }, onBack = {})
            }
        }
        fun tile(label: String) = composeRule.onNodeWithContentDescription(app.getString(R.string.panel_show_control, label))
        val from = tile("风速").fetchSemanticsNode().boundsInRoot.center
        val mid = tile("摇头").fetchSemanticsNode().boundsInRoot.center
        val to = tile("定时").fetchSemanticsNode().boundsInRoot.center
        tile("风速").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(mid - from)
            moveBy(to - mid)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(listOf("key:swing", "key:timer", "key:speed", "key:mode"), saved.value?.order?.take(4))
    }

    @Test
    fun panelSaveFailureShowsInEditorAndReturningDoesNotClearErrorOrRepeatMarkUsed() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val dir = Files.createTempDirectory("panel-save-error-").toFile()
        val context = object : ContextWrapper(app) {
            override fun getFilesDir(): File = dir
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        try {
            val repo = RemoteRepository(context, scope)
            runBlocking {
                repo.awaitLoaded()
                repo.saveRemote(profile)
            }
            val vm = RemoteControlViewModel(profile.id, repository = repo, initialProfile = profile)
                .also { store.put("control", it) }

            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    RemoteControlScreen(viewModel = vm, onBack = {})
                }
            }
            composeRule.waitUntil(5_000) {
                runBlocking { repo.getRemote(profile.id)?.lastUsedAt ?: 0L } > 0L
            }
            val firstUsedAt = runBlocking { repo.getRemote(profile.id)!!.lastUsedAt }
            Thread.sleep(20)

            // Block AtomicLocalFile.write(roomsFile) by replacing rooms.json with a non-empty directory.
            val roomsFile = File(dir, "rooms.json")
            if (roomsFile.exists()) assertTrue(roomsFile.delete())
            File(roomsFile, "lock").apply {
                parentFile!!.mkdirs()
                writeText("locked")
            }

            composeRule.onNodeWithContentDescription(app.getString(R.string.panel_edit_title)).performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasContentDescription(app.getString(R.string.panel_show_control, "定时")))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription(app.getString(R.string.panel_show_control, "定时")).performClick()

            val expectedErrorPrefix = app.getString(R.string.msg_remote_save_failed, "").substringBefore("%1\$s").trim()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasText(expectedErrorPrefix, substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasText(expectedErrorPrefix, substring = true)).assertIsDisplayed()

            // Return immediately after another failing edit so any pending error must survive the transition.
            composeRule.onNodeWithContentDescription(app.getString(R.string.panel_show_control, "睡眠")).performClick()
            composeRule.onNodeWithContentDescription(app.getString(R.string.screen_back)).performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasContentDescription(app.getString(R.string.panel_edit_title)))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasText(expectedErrorPrefix, substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(firstUsedAt, runBlocking { repo.getRemote(profile.id)!!.lastUsedAt })
            composeRule.onNode(hasText(expectedErrorPrefix, substring = true)).assertIsDisplayed()
        } finally {
            composeRule.runOnIdle { store.clear() }
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    @Test
    fun renamedGenericDirectionKeysAndNumericKeysAreExcludedFromEditor() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val customProfile = RemoteProfile(
            id = "panel_editor_custom_dpad", name = "投影仪", category = DeviceCategory.PROJECTOR,
            buttons = listOf(
                key("up", "向上"), key("nav_down", "Down"),
                key("custom_left", "左移").copy(builtinLabel = "左"),
                key("right", "Right"), key("remote_enter", "Confirm"),
                key("tv_num_1", "频道一"), key("mute", "静音")
            )
        )
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                RemotePanelEditor(customProfile, emptySet(), onSave = {}, onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(app.getString(R.string.panel_show_control, "静音")).assertIsDisplayed()
        listOf("向上", "Down", "左移", "Right", "Confirm", "频道一").forEach { label ->
            assertTrue(
                "$label should stay fixed and not appear in editor",
                composeRule.onAllNodes(hasContentDescription(app.getString(R.string.panel_show_control, label)))
                    .fetchSemanticsNodes().isEmpty()
            )
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h891dp-mdpi")
    fun englishNarrowEditorKeepsSemanticAndCustomKeysReachable() =
        exerciseSemanticKeys("en_light", dark = false)

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h891dp-mdpi")
    fun chineseNarrowEditorKeepsSemanticAndCustomKeysReachable() =
        exerciseSemanticKeys("zh_dark", dark = true)

    @Test
    @Config(qualifiers = "b+zh+Hant-w320dp-h891dp-mdpi")
    fun traditionalChineseNarrowEditorKeepsSemanticAndCustomKeysReachable() =
        exerciseSemanticKeys("zh_hant_amoled", dark = true, pureBlack = true)

    @Test
    @Config(qualifiers = "ja-w320dp-h891dp-mdpi")
    fun japaneseNarrowEditorKeepsSemanticAndCustomKeysReachable() =
        exerciseSemanticKeys("ja_dynamic", dark = false, dynamic = true)

    private fun exerciseSemanticKeys(name: String, dark: Boolean, pureBlack: Boolean = false, dynamic: Boolean = false) {
        val originalFontScale = RuntimeEnvironment.getApplication().resources.configuration.fontScale
        RuntimeEnvironment.setFontScale(2f)
        try {
            val app = RuntimeEnvironment.getApplication()
            assertEquals(2f, app.resources.configuration.fontScale, 0f)
            assertEquals(320, app.resources.configuration.screenWidthDp)
            AppStrings.initialize(app)
            val source = key("learned_input", app.getString(R.string.control_source), semanticKey = ButtonKey.SOURCE)
            val custom = key("scene", "Scene")
            assertSame(ButtonKey.SOURCE.icon, resolveButtonIcon(source))
            assertNull(resolveButtonIcon(custom))
            val customProfile = RemoteProfile("semantic_editor", "Remote", category = DeviceCategory.CUSTOM,
                buttons = listOf(source, custom))
            val saved = mutableStateOf<RemotePanelLayout?>(null)
            composeRule.setContent {
                IrRemoteTheme(darkTheme = dark, dynamicColor = dynamic, pureBlackBackground = pureBlack) {
                    RemotePanelEditor(customProfile, emptySet(), onSave = { saved.value = it }, onBack = {})
                }
            }
            fun tile(button: RemoteButton) = composeRule.onNodeWithContentDescription(
                app.getString(R.string.panel_show_control, button.label))
            for (button in customProfile.buttons) {
                val node = tile(button).assertIsDisplayed().assertIsOn()
                val bounds = node.fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.width >= 48f && bounds.height >= 48f)
                assertTrue(bounds.left >= 0f && bounds.right <= 320f)
            }
            composeRule.onRoot().captureScreenshot("src/test/screenshots/panel_editor_semantic_${name}_before.webp",
                roborazziOptions = options)
            tile(custom).performClick()
            composeRule.waitForIdle()
            tile(custom).assertIsOff().assertIsDisplayed()
            tile(source).assertIsOn().assertIsDisplayed()
            assertEquals(setOf("key:scene"), saved.value?.hidden)
            assertEquals(listOf(source, custom), customProfile.buttons)
            composeRule.onRoot().captureScreenshot("src/test/screenshots/panel_editor_semantic_${name}_after.webp",
                roborazziOptions = options)
        } finally {
            RuntimeEnvironment.setFontScale(originalFontScale)
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.captureScreenshot(
        path: String, roborazziOptions: RoborazziOptions
    ) {
        screenshotDifferences.checkSucceeds {
            captureRoboImage(path, roborazziOptions = roborazziOptions)
        }
    }
}
