package org.qinyan.rimoat.i18n

import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.db.IrCategory
import org.qinyan.rimoat.data.db.IrRemoteModel
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.testing.collectScreenshotDifference
import org.qinyan.rimoat.ui.learning.HardwareStatusBanner
import org.qinyan.rimoat.ui.learning.IrWaveformPreview
import org.qinyan.rimoat.ui.screens.CategorySelectionView
import org.qinyan.rimoat.ui.screens.ConfirmRemoteView
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.AddRemoteUiState
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-port-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class LocalizationCopyRoborazziTest {
    @get:Rule val screenshotDifferences = ErrorCollector()
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    @After fun resetFontScale() {
        RuntimeEnvironment.setFontScale(1f)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        screenshotDifferences.collectScreenshotDifference {
            compose.onRoot().captureRoboImage(
                "src/test/screenshots/localized_copy_$name.webp",
                roborazziOptions = options
            )
        }
    }

    private fun verifyCategories(localeSuffix: String) {
        AppStrings.initialize(app)
        val categories = listOf(
            IrCategory(1, "电视", "TV", "tv"),
            IrCategory(2, "空调", "Air conditioner", "air"),
            IrCategory(3, "功放", "Amplifier", "amp"),
            IrCategory(4, "网络盒子", "Streaming box", "box"),
            IrCategory(5, "机顶盒", "Set-top box", "stb"),
            IrCategory(6, "IPTV", "IPTV", "ipt")
        )
        compose.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    CategorySelectionView(categories, isLoading = false, onCategoryClick = {})
                }
            }
        }
        compose.onNodeWithText(app.getString(R.string.screen_category_audio)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.screen_category_streaming_box)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.screen_category_set_top_box)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.screen_category_iptv)).assertIsDisplayed()
        capture("categories_$localeSuffix")
    }

    private fun verifyConfirmAndLearningCopy(localeSuffix: String, expectedWholeKhz: String, expectedFractionalKhz: String) {
        AppStrings.initialize(app)
        val tvCategory = IrCategory(1, "电视", "TV", "tv")
        val model = IrRemoteModel("code_2", 1, "电视", 1, "示例", "Example", 1, "", 2)
        val defaultName = UiText.resource(
            R.string.screen_default_remote_name,
            UiText.CatalogBrand("示例", "Example"),
            tvCategory.displayText
        )
        val confirmState = AddRemoteUiState(
            selectedCategory = tvCategory,
            remotes = listOf(model, model.copy(remoteId = "code_2_alt")),
            currentRemoteIndex = 1,
            defaultRemoteName = defaultName
        )
        val signalWhole = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560, 560))
        val signalFractional = IrSignal.Raw(36700, intArrayOf(9000, 4500, 560, 1690))
        var showConfirm by mutableStateOf(false)
        compose.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    if (showConfirm) {
                        ConfirmRemoteView(state = confirmState, onNameChange = {}, onSave = {})
                    } else {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HardwareStatusBanner(
                                learnerType = LearnerType.USB_ELKSMART,
                                isAvailable = true,
                                onEnableMockMode = {}
                            )
                            HardwareStatusBanner(
                                learnerType = LearnerType.USB_TIQIAA,
                                isAvailable = true,
                                onEnableMockMode = {}
                            )
                            HardwareStatusBanner(
                                learnerType = LearnerType.NONE,
                                isAvailable = false,
                                onEnableMockMode = {}
                            )
                            IrWaveformPreview(signal = signalWhole)
                            IrWaveformPreview(signal = signalFractional)
                        }
                    }
                }
            }
        }
        compose.onNodeWithText(app.getString(R.string.control_elksmart)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.control_tiqiaa)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.screen_no_receiver)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.screen_frequency_khz, expectedWholeKhz)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.screen_frequency_khz, expectedFractionalKhz)).assertIsDisplayed()
        capture("receiver_waveform_$localeSuffix")

        compose.runOnIdle { showConfirm = true }
        val brandText = if (localeSuffix in setOf("zh", "hant")) "示例" else "Example"
        compose.onNodeWithText(
            app.getString(R.string.screen_save_summary, brandText, app.getString(R.string.screen_category_tv), 2)
        ).assertIsDisplayed()
        capture("confirm_$localeSuffix")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-port-mdpi")
    fun simplifiedChineseCopyAndCategories() {
        verifyCategories("zh")
    }

    @Test
    @Config(qualifiers = "zh-rTW-w411dp-h891dp-port-mdpi")
    fun traditionalChineseCopyAndCategories() {
        verifyCategories("hant")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-port-mdpi")
    fun englishCopyAndCategories() {
        verifyCategories("en")
    }

    @Test
    @Config(qualifiers = "ja-rJP-w411dp-h891dp-port-mdpi")
    fun japaneseCopyAndCategories() {
        verifyCategories("ja")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-port-mdpi")
    fun simplifiedChineseReceiverWaveformAndConfirm() {
        verifyConfirmAndLearningCopy("zh", "38", "36.7")
    }

    @Test
    @Config(qualifiers = "zh-rTW-w411dp-h891dp-port-mdpi")
    fun traditionalChineseReceiverWaveformAndConfirm() {
        verifyConfirmAndLearningCopy("hant", "38", "36.7")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-port-mdpi")
    fun englishReceiverWaveformAndConfirm() {
        verifyConfirmAndLearningCopy("en", "38", "36.7")
    }

    @Test
    @Config(qualifiers = "ja-rJP-w411dp-h891dp-port-mdpi")
    fun japaneseReceiverWaveformAndConfirm() {
        verifyConfirmAndLearningCopy("ja", "38", "36.7")
    }

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h568dp-port-mdpi")
    fun narrowLargeTextConfirmAndReceiverStayReadable() {
        RuntimeEnvironment.setFontScale(2f)
        AppStrings.initialize(app)
        val tvCategory = IrCategory(1, "电视", "TV", "tv")
        val model = IrRemoteModel("code_2", 1, "电视", 1, "示例", "Example", 1, "", 2)
        val confirmState = AddRemoteUiState(
            selectedCategory = tvCategory,
            remotes = listOf(model, model.copy(remoteId = "code_2_alt")),
            currentRemoteIndex = 1,
            defaultRemoteName = UiText.resource(
                R.string.screen_default_remote_name,
                UiText.CatalogBrand("示例", "Example"),
                tvCategory.displayText
            )
        )
        compose.setContent {
            IrRemoteTheme(darkTheme = true, pureBlackBackground = true, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    ConfirmRemoteView(state = confirmState, onNameChange = {}, onSave = {})
                }
            }
        }
        compose.onNodeWithText(
            app.getString(R.string.screen_save_summary, "Example", app.getString(R.string.screen_category_tv), 2)
        ).assertIsDisplayed()
        capture("confirm_narrow_amoled_large")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-port-mdpi")
    fun confirmDefaultNameFollowsRuntimeLocaleContext() {
        val tvCategory = IrCategory(1, "电视", "TV", "tv")
        val model = IrRemoteModel("code_1", 1, "电视", 1, "示例", "Example", 1, "", 1)
        val state = AddRemoteUiState(
            selectedCategory = tvCategory,
            remotes = listOf(model),
            currentRemoteIndex = 0,
            defaultRemoteName = UiText.resource(
                R.string.screen_default_remote_name,
                UiText.CatalogBrand("示例", "Example"),
                tvCategory.displayText
            )
        )
        val japaneseContext = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("ja"))
        })
        compose.setContent {
            CompositionLocalProvider(LocalContext provides japaneseContext) {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    Surface(Modifier.fillMaxSize()) {
                        ConfirmRemoteView(state = state, onNameChange = {}, onSave = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Example テレビ").assertIsDisplayed()
        compose.onNodeWithText("Example テレビ、コードセット 1").assertIsDisplayed()
    }
}
