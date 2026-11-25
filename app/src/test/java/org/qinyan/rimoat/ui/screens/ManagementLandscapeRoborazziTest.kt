package org.qinyan.rimoat.ui.screens

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.*
import java.io.File
import java.util.TimeZone
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w640dp-h360dp-land-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class ManagementLandscapeRoborazziTest {
    @get:Rule val composeRule = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val app get() = RuntimeEnvironment.getApplication()
    private val originalTimeZone = TimeZone.getDefault()
    private val repository by lazy { RemoteRepository(app, scope) }
    private val transmitter by lazy { IrTransmitterManager(app, scope) }
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))

    @After fun release() {
        TimeZone.setDefault(originalTimeZone)
        transmitter.release()
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    private fun viewModel(): RemoteListViewModel {
        AppStrings.initialize(app)
        runBlocking { repository.awaitLoaded() }
        return RemoteListViewModel(repository, transmitter, ThemePreferences(app), BackupFileStore(app))
    }
    private fun capture(name: String) {
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/management_landscape_$name.webp", roborazziOptions = options)
    }
    private fun waitFor(condition: () -> Boolean) = composeRule.waitUntil(10_000) { shadowOf(Looper.getMainLooper()).idle(); condition() }
    private fun assertPanes() {
        val left = composeRule.onNodeWithTag("two_pane_primary").fetchSemanticsNode().boundsInRoot
        val right = composeRule.onNodeWithTag("two_pane_secondary").fetchSemanticsNode().boundsInRoot
        assertTrue(left.right < right.left)
        assertEquals(left.top, right.top, .1f)
    }

    @Test fun roomSelectionAndAssignmentUpdateBothPanes() {
        val vm = viewModel()
        runBlocking {
            repository.saveRemote(RemoteProfile("room_tv", "客厅电视", room = "客厅", category = DeviceCategory.TV))
            repository.saveRemote(RemoteProfile("room_fan", "卧室风扇", room = "卧室", category = DeviceCategory.FAN))
        }
        composeRule.setContent { IrRemoteTheme(darkTheme = false, dynamicColor = false) { RoomManagementScreen(vm) {} } }
        composeRule.waitForIdle()
        assertPanes()
        composeRule.onAllNodesWithText(app.getString(R.string.screen_new_room))
            .filter(hasAnyAncestor(hasTestTag("two_pane_primary"))).assertCountEquals(1).onFirst().assertIsDisplayed().assertHasClickAction()
        composeRule.onAllNodesWithText("卧室").filter(hasAnyAncestor(hasTestTag("two_pane_primary"))).onFirst().performClick()
        composeRule.onNodeWithText("卧室风扇").assertIsDisplayed()
        capture("rooms_before")
        composeRule.onNodeWithText(app.getString(R.string.screen_assign_remotes)).performScrollTo().performClick()
        composeRule.onNodeWithText("客厅电视").performScrollTo().performClick()
        waitFor { repository.remotes.value.firstOrNull { it.id == "room_tv" }?.room == "卧室" }
        composeRule.onNodeWithText(app.getString(R.string.screen_done)).performScrollTo().performClick()
        composeRule.onNodeWithText("客厅电视").performScrollTo().assertIsDisplayed()
        capture("rooms_assigned")
    }

    @Test fun trashKeepsDistinctHistoryAndSelectsRemainingEntryAfterRestore() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val vm = viewModel()
        runBlocking {
            repository.saveRemote(RemoteProfile("history_remote", "第一份遥控器"))
            repository.deleteRemote("history_remote")
            repository.saveRemote(RemoteProfile("history_remote", "第二份遥控器"))
            repository.deleteRemote("history_remote")
        }
        val trashDirectory = File(app.filesDir, "remotes/.trash")
        val deleted = runBlocking { repository.deletedEntries() }
        assertEquals(2, deleted.size)
        deleted.forEach { entry ->
            val deletedAt = if (entry.profile.name == "第一份遥控器") {
                1_600_000_000_000L
            } else {
                1_600_000_060_000L
            }
            assertTrue(File(trashDirectory, entry.entryId).renameTo(
                File(trashDirectory, "${entry.profile.id}-$deletedAt-fixture.json")
            ))
        }
        composeRule.setContent { IrRemoteTheme(darkTheme = false, dynamicColor = false) { TrashScreen(vm) {} } }
        waitFor { vm.trash.value?.size == 2 }
        assertPanes()
        composeRule.onAllNodesWithText("第二份遥控器").filter(hasAnyAncestor(hasTestTag("two_pane_primary"))).onFirst().performClick()
        capture("trash_before")
        composeRule.onNodeWithContentDescription(app.getString(R.string.screen_restore_name, "第二份遥控器")).performClick()
        waitFor { vm.trash.value?.size == 1 }
        assertEquals("第二份遥控器", repository.remotes.value.firstOrNull { it.id == "history_remote" }?.name)
        composeRule.onNodeWithContentDescription(app.getString(R.string.screen_restore_name, "第一份遥控器")).assertIsDisplayed()
        capture("trash_restored")
    }

    @Test fun restoreAndDeleteShareSubmissionGate() {
        val vm = viewModel()
        runBlocking {
            repository.saveRemote(RemoteProfile("busy_remote", "保留的遥控器"))
            repository.deleteRemote("busy_remote")
        }
        val entry = runBlocking { repository.deletedEntries().single() }
        var deletes = 0
        composeRule.runOnUiThread {
            vm.restoreDeleted(entry.profile.id, entry.entryId)
            assertTrue(vm.trashBusy.value)
            vm.permanentlyDelete(entry.entryId) { deletes++ }
        }
        waitFor { !vm.trashBusy.value }
        assertEquals(0, deletes)
        assertEquals("保留的遥控器", repository.remotes.value.single().name)
    }

    @Test fun licensesSearchSelectsMatchingTextWithoutLeavingTwoPanes() {
        AppStrings.initialize(app)
        var selected by mutableStateOf<String?>(null)
        composeRule.setContent { IrRemoteTheme(darkTheme = false, dynamicColor = false) {
            OpenSourceLicensesScreen(onBack = {}, onLibraryClick = { selected = it }, libraryId = selected)
        } }
        waitFor { composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        assertPanes()
        capture("licenses_before")
        composeRule.onNode(hasSetTextAction()).performTextInput("mik3y")
        composeRule.waitForIdle()
        assertEquals("com.github.mik3y:usb-serial-for-android:3.9.0", selected)
        composeRule.onAllNodesWithText("usb-serial-for-android").assertCountEquals(2)
        composeRule.onNode(hasText("com.github.mik3y:usb-serial-for-android:3.9.0") and hasAnyAncestor(hasTestTag("two_pane_secondary"))).assertIsDisplayed()
        capture("licenses_filtered")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("no_matching_library")
        composeRule.onAllNodesWithText(app.getString(R.string.licenses_empty)).assertCountEquals(2)
    }

    @Test @Config(qualifiers = "en-rUS-w480dp-h320dp-land-mdpi")
    fun aboutLinksRemainReachableAtDoubleFontSize() {
        RuntimeEnvironment.setFontScale(2f)
        AppStrings.initialize(app)
        var opened = 0
        composeRule.setContent { IrRemoteTheme(darkTheme = true, dynamicColor = false) { AboutScreen({}, { opened++ }) } }
        assertPanes()
        capture("about_large_before")
        composeRule.onNodeWithText(app.getString(R.string.about_license)).performScrollTo().performClick()
        assertEquals(1, opened)
        capture("about_large_scrolled")
    }
}
