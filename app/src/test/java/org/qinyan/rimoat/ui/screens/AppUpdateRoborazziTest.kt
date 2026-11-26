package org.qinyan.rimoat.ui.screens

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import com.github.takahirom.roborazzi.*
import java.io.File
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.data.update.*
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi", application = Application::class, shadows = [ShadowFileProvider::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class AppUpdateRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val stores = ViewModelStore()
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))
    private val release = AppRelease("v1.2.0", "### 简体中文\n- 在设置中检查并下载更新\n- 改善遥控器操作体验\n### English\n- Check for and download updates in settings\n- Improve remote controls",
        ReleaseAsset("Rimoat-v1.2.0.apk", "", 32 * 1048576))

    @After fun cleanup() { stores.clear() }
    private fun settle() {
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }
    private fun capture(name: String) = compose.onNode(isDialog()).captureRoboImage(
        "src/test/screenshots/update_$name.webp", roborazziOptions = options)
    private fun text(id: Int) = app.getString(id)

    @Test fun settingsEntryChecksShowsChineseNotesDownloadsAndCancels() {
        AppStrings.initialize(app)
        compose.mainClock.autoAdvance = true
        val response = CompletableDeferred<AppRelease>()
        val repo = object : UpdateRepository {
            override val installedVersion = "1.1.0"
            override suspend fun latestRelease() = response.await()
            override suspend fun download(release: AppRelease, progress: (Long, Long) -> Unit): File {
                progress(8 * 1048576, 32 * 1048576)
                awaitCancellation()
            }
        }
        val update = AppUpdateViewModel(repo).also { stores.put("update", it) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = RemoteRepository(app, scope)
        val manager = IrTransmitterManager(app, scope)
        val vm = RemoteListViewModel(repository, manager, ThemePreferences(app), BackupFileStore(app))
            .also { stores.put("settings", it) }
        try {
            runBlocking { repository.awaitLoaded() }
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    SettingsScreen(vm, onBack = {}, updateViewModel = update)
                }
            }
            settle()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(text(R.string.update_check)))
            settle()
            compose.onRoot().captureRoboImage("src/test/screenshots/update_settings_entry.webp", roborazziOptions = options)
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText(text(R.string.update_check)).performClick(); settle()
            compose.onNodeWithTag("update_loading").assertIsDisplayed()
            compose.onNodeWithText(text(R.string.update_checking)).assertIsDisplayed()
            capture("checking")
            compose.runOnIdle { response.complete(release) }; settle()
            compose.onNodeWithText("- 在设置中检查并下载更新", substring = true).assertIsDisplayed()
            compose.onNodeWithText("- Check for", substring = true).assertDoesNotExist()
            val updateButton = compose.onNodeWithText(text(R.string.update_now))
            val cancelButton = compose.onNodeWithText(text(R.string.cancel))
            assertTrue(updateButton.fetchSemanticsNode().boundsInRoot.right < cancelButton.fetchSemanticsNode().boundsInRoot.left)
            capture("available_zh")
            updateButton.performClick(); settle()
            compose.onNodeWithTag("update_progress").assertIsDisplayed()
            assertEquals(0.25f, compose.onNodeWithTag("update_progress").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.01f)
            capture("downloading")
            cancelButton.performClick(); settle()
            compose.onNode(isDialog()).assertDoesNotExist()
            assertEquals(UpdatePhase.CLOSED, update.state.value.phase)
        } finally {
            manager.release()
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    private fun show(initial: AppUpdateState, dark: Boolean = false): MutableState<AppUpdateState> {
        compose.mainClock.autoAdvance = false
        val state = mutableStateOf(initial)
        compose.setContent {
            IrRemoteTheme(darkTheme = dark, dynamicColor = false) {
                AppUpdateDialog(state.value, "1.1.0",
                    onCancel = { state.value = AppUpdateState() },
                    onCheck = { state.value = AppUpdateState(UpdatePhase.CHECKING) },
                    onDownload = { state.value = state.value.copy(phase = UpdatePhase.DOWNLOADING, failure = null) },
                    onInstall = { state.value = state.value.copy(phase = UpdatePhase.INSTALLING) },
                    onPermission = { state.value = state.value.copy(phase = UpdatePhase.WAITING_PERMISSION) })
            }
        }
        settle()
        return state
    }

    @Test fun timeoutRetryAndPermissionGuidanceStayActionable() {
        val state = show(AppUpdateState(UpdatePhase.ERROR, failure = UpdateFailure.CHECK_TIMEOUT))
        compose.onNodeWithText(text(R.string.update_error_timeout)).assertIsDisplayed()
        capture("timeout")
        compose.onNodeWithText(text(R.string.update_retry)).performClick(); settle()
        compose.onNodeWithTag("update_loading").assertIsDisplayed()
        compose.runOnIdle { state.value = AppUpdateState(UpdatePhase.PERMISSION, release) }; settle()
        compose.onNodeWithText(text(R.string.update_permission_message)).assertIsDisplayed()
        capture("permission")
        compose.onNodeWithText(text(R.string.update_open_settings)).performClick(); settle()
        assertEquals(UpdatePhase.WAITING_PERMISSION, state.value.phase)
    }

    @Test fun digestFailureShowsErrorAfterOneDownloadAndAllowsManualRetry() {
        Shadows.shadowOf(app.packageManager).getInternalMutablePackageInfo(app.packageName).versionName = "1.1.0"
        val assetUrl = "https://github.com/FreeDivers/Rimoat/releases/download/v1.2.0/app.apk"
        val downloads = AtomicInteger()
        val finishDownload = CountDownLatch(1)
        val repo = AppUpdateRepository(app) { url ->
            val isDownload = url.endsWith("app.apk")
            if (isDownload) downloads.incrementAndGet()
            object : HttpURLConnection(URL(url)) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getInputStream() = if (isDownload) {
                    object : ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) {
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            if (available() == 2) check(finishDownload.await(30, TimeUnit.SECONDS))
                            return super.read(buffer, offset, minOf(length, 2))
                        }
                    }
                } else ByteArrayInputStream("""{"tag_name":"v1.2.0","assets":[{"name":"app.apk","browser_download_url":"$assetUrl","size":4,"digest":"sha256:invalid"}]}""".toByteArray())
            }
        }
        val vm = AppUpdateViewModel(repo).also { stores.put("update", it) }
        compose.mainClock.autoAdvance = false
        fun waitFor(condition: () -> Boolean) {
            compose.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                condition()
            }
            settle()
        }
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) { AppUpdateHost(vm) }
            }
            settle()
            compose.runOnIdle { vm.check() }
            waitFor { vm.state.value.phase == UpdatePhase.AVAILABLE }
            compose.onNodeWithText(text(R.string.update_now)).performClick()
            waitFor { vm.state.value.downloadedBytes == 2L }
            compose.onNodeWithTag("update_progress").assertIsDisplayed()
            compose.onNodeWithText(text(R.string.update_retry)).assertDoesNotExist()
            capture("validation_downloading")
            finishDownload.countDown()
            waitFor { vm.state.value.phase == UpdatePhase.ERROR }
            compose.onNodeWithText(text(R.string.update_error_apk)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.update_retry)).assertIsEnabled()
            capture("validation_error")
            assertEquals(UpdateFailure.INVALID_APK, vm.state.value.failure)
            assertEquals(1, downloads.get())
            assertTrue(File(app.cacheDir, "updates").listFiles().orEmpty().isEmpty())
            compose.onNodeWithText(text(R.string.update_retry)).performClick()
            waitFor { vm.state.value.phase == UpdatePhase.ERROR }
            assertEquals(2, downloads.get())
            compose.onNodeWithText(text(R.string.cancel)).performClick()
            settle()
            compose.onNode(isDialog()).assertDoesNotExist()
        } finally {
            finishDownload.countDown()
            compose.runOnIdle { vm.cancel() }
        }
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-night-mdpi")
    fun downloadErrorOffersRetryInDarkTheme() {
        show(AppUpdateState(UpdatePhase.ERROR, release, failure = UpdateFailure.DOWNLOAD), dark = true)
        compose.onNodeWithText(text(R.string.update_error_download)).assertIsDisplayed()
        capture("download_error_dark")
        compose.onNodeWithText(text(R.string.update_retry)).performClick(); settle()
        compose.onNodeWithTag("update_progress").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "b+zh+Hant-w411dp-h891dp-mdpi")
    fun traditionalChineseUsesChineseReleaseSection() {
        show(AppUpdateState(UpdatePhase.AVAILABLE, release))
        compose.onNodeWithText("- 在设置中检查并下载更新", substring = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.update_now)).assertIsDisplayed()
        capture("available_hant")
    }

    @Test @Config(qualifiers = "ja-w640dp-h360dp-land-mdpi")
    fun otherLanguagesUseEnglishNotesInLandscape() {
        show(AppUpdateState(UpdatePhase.AVAILABLE, release))
        compose.onNodeWithText("- Check for", substring = true).assertIsDisplayed()
        compose.onNodeWithText("- 在设置", substring = true).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.cancel)).assertIsDisplayed()
        capture("available_landscape_ja")
    }

    @Test @Config(qualifiers = "en-w320dp-h640dp-mdpi")
    fun narrowLargeTextKeepsReleaseNotesScrollableAndActionsVisible() {
        RuntimeEnvironment.setFontScale(2f)
        show(AppUpdateState(UpdatePhase.AVAILABLE, release))
        compose.onNodeWithText(text(R.string.update_now)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.cancel)).assertIsDisplayed()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("- Check for", substring = true).performScrollTo().assertIsDisplayed()
        capture("available_large_text")
    }

    @Test fun restoredPermissionRequestLaunchesInstallerOnlyOnceAfterGrant() {
        val directory = File(app.cacheDir, "updates").apply { mkdirs() }
        val file = File.createTempFile("release-test-", ".apk", directory).apply { writeBytes(byteArrayOf(1)) }
        val repo = object : UpdateRepository {
            override val installedVersion = "1.1.0"
            override suspend fun latestRelease(): AppRelease = error("Must reuse downloaded APK")
            override suspend fun download(release: AppRelease, progress: (Long, Long) -> Unit): File = error("Must reuse downloaded APK")
        }
        val vm = AppUpdateViewModel(repo, SavedStateHandle(mapOf("update_apk" to file.absolutePath,
            "update_tag" to "v1.2.0", "update_waiting" to true))).also { stores.put("update", it) }
        val pm = Shadows.shadowOf(app.packageManager)
        pm.setCanRequestPackageInstalls(false)
        val registry = TestInstallerRegistry()
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent {
                IrRemoteTheme(dynamicColor = false) {
                    CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) { AppUpdateHost(vm) }
                }
            }
            settle()
            compose.onNodeWithText(text(R.string.update_open_settings)).performClick(); settle()
            assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, registry.intents.single().action)
            compose.runOnIdle {
                pm.setCanRequestPackageInstalls(true)
                registry.complete()
            }
            settle()
            assertEquals(2, registry.intents.size)
            assertEquals(Intent.ACTION_VIEW, registry.intents.last().action)
            assertEquals("content", registry.intents.last().data?.scheme)
            compose.runOnIdle { vm.permissionResult(true) }; settle()
            assertEquals(2, registry.intents.size)
            compose.runOnIdle { registry.complete() }; settle()
            compose.onNodeWithText(text(R.string.update_install)).assertIsDisplayed()
            capture("installer_returned")
        } finally { file.delete() }
    }

    private class TestInstallerRegistry : ActivityResultRegistryOwner {
        val intents = mutableListOf<Intent>()
        private var requestCode = 0
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(code: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                requestCode = code
                intents += input as Intent
            }
        }
        fun complete() {
            assertTrue(activityResultRegistry.dispatchResult(requestCode, ActivityResult(android.app.Activity.RESULT_CANCELED, null)))
        }
    }
}
