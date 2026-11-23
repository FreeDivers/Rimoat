package org.qinyan.rimoat.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModelStore
import com.github.takahirom.roborazzi.*
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class RemoteListSwipeRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val store = ViewModelStore()
    private lateinit var manager: IrTransmitterManager
    private val tv = RemoteProfile("living_tv", "客厅电视", category = DeviceCategory.TV, room = "客厅")
    private val fan = RemoteProfile("bedroom_fan", "卧室风扇", category = DeviceCategory.FAN, room = "卧室")
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    @After fun cleanup() {
        compose.runOnUiThread { store.clear() }
        if (::manager.isInitialized) manager.release()
        runBlocking { job.cancelAndJoin() }
    }

    private fun showHome(
        profiles: List<RemoteProfile> = listOf(tv, fan),
        dark: Boolean = false,
        direction: LayoutDirection = LayoutDirection.Ltr,
        onOpen: (String) -> Unit = {},
        onAdd: () -> Unit = {}
    ): RemoteListViewModel {
        AppStrings.initialize(app)
        val repository = RemoteRepository(app, scope)
        runBlocking {
            repository.awaitLoaded()
            profiles.forEach { repository.saveRemote(it) }
        }
        manager = IrTransmitterManager(app, scope)
        val vm = RemoteListViewModel(repository, manager, ThemePreferences(app), BackupFileStore(app))
        store.put("home", vm)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                IrRemoteTheme(darkTheme = dark, dynamicColor = false) {
                    RemoteListScreen(vm, onOpen, onAdd)
                }
            }
        }
        compose.waitUntil(5_000) { vm.filteredRemotes.value.size == profiles.size }
        compose.waitForIdle()
        return vm
    }

    private fun swipeBlank(left: Boolean = true) {
        compose.onNodeWithTag("home_results").performTouchInput {
            val start = Offset(width * .8f, height * .75f)
            val end = Offset(width * .2f, start.y)
            swipe(if (left) start else end, if (left) end else start, durationMillis = 400)
        }
        compose.waitForIdle()
    }

    private fun assertCategory(vm: RemoteListViewModel, category: DeviceCategory?, label: Int) {
        compose.runOnIdle { assertEquals(category, vm.selectedCategory.value) }
        compose.onNodeWithText(app.getString(label)).assertIsSelected()
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage(
        "src/test/screenshots/home_swipe_$name.webp", roborazziOptions = options
    )

    @Test fun blankSwipesFollowTabsInBothDirectionsIncludingEmptyCategoriesAndEdges() {
        val vm = showHome()
        capture("all_before")
        swipeBlank(left = false)
        assertCategory(vm, null, R.string.screen_category_all)
        swipeBlank()
        assertCategory(vm, DeviceCategory.TV, R.string.screen_category_tv)
        compose.waitUntil(5_000) { vm.filteredRemotes.value.map { it.id } == listOf(tv.id) }
        compose.onNodeWithText(tv.name).assertIsDisplayed()
        compose.onNodeWithText(fan.name).assertDoesNotExist()
        capture("tv_after")
        swipeBlank()
        assertCategory(vm, DeviceCategory.AC, R.string.screen_category_ac)
        compose.waitUntil(5_000) { vm.filteredRemotes.value.isEmpty() }
        compose.onNodeWithText(app.getString(R.string.screen_filter_empty)).assertIsDisplayed()
        capture("empty_ac_after")
        val remaining = listOf(
            DeviceCategory.FAN to R.string.screen_category_fan,
            DeviceCategory.PROJECTOR to R.string.screen_category_projector,
            DeviceCategory.AUDIO to R.string.screen_category_audio,
            DeviceCategory.CUSTOM to R.string.screen_category_custom
        )
        remaining.forEach { (category, label) ->
            swipeBlank()
            assertCategory(vm, category, label)
        }
        swipeBlank()
        assertCategory(vm, DeviceCategory.CUSTOM, R.string.screen_category_custom)
        val reverse = remaining.dropLast(1).reversed() + listOf(
            DeviceCategory.AC to R.string.screen_category_ac,
            DeviceCategory.TV to R.string.screen_category_tv,
            null to R.string.screen_category_all
        )
        reverse.forEach { (category, label) ->
            swipeBlank(left = false)
            assertCategory(vm, category, label)
        }
        compose.waitUntil(5_000) { vm.filteredRemotes.value.size == 2 }
        compose.onNodeWithText(tv.name).assertIsDisplayed()
        compose.onNodeWithText(fan.name).assertIsDisplayed()
    }

    @Test fun swipingKeepsSearchRoomAndSortFilters() {
        val vm = showHome()
        compose.onNode(hasSetTextAction()).performTextInput("客厅")
        compose.runOnIdle {
            vm.selectRoom("客厅")
            vm.setRecentFirst(true)
        }
        swipeBlank()
        assertCategory(vm, DeviceCategory.TV, R.string.screen_category_tv)
        swipeBlank()
        compose.onNodeWithText(app.getString(R.string.screen_search_empty)).assertIsDisplayed()
        swipeBlank(left = false)
        assertCategory(vm, DeviceCategory.TV, R.string.screen_category_tv)
        compose.waitUntil(5_000) { vm.filteredRemotes.value.map { it.id } == listOf(tv.id) }
        compose.onNodeWithText(tv.name).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("客厅", vm.searchQuery.value)
            assertEquals("客厅", vm.selectedRoom.value)
            assertTrue(vm.recentFirst.value)
        }
    }

    @Test fun cardTapFavoriteAndDeleteKeepTheirOwnActions() {
        val opened = mutableListOf<String>()
        val vm = showHome(onOpen = { opened += it })
        compose.onNodeWithText(tv.name).performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(tv.id), opened) }
        compose.onNodeWithText(tv.name).performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { vm.allRemotes.value.first { it.id == tv.id }.isFavorite }
        assertCategory(vm, null, R.string.screen_category_all)
        capture("card_favorite")
        compose.onNodeWithText(tv.name).performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { vm.allRemotes.value.none { it.id == tv.id } }
        compose.onNodeWithText(fan.name).assertIsDisplayed()
        assertCategory(vm, null, R.string.screen_category_all)
        compose.runOnIdle { assertEquals(listOf(tv.id), opened) }
    }

    @Test fun shortOrCancelledDragsDoNotChangeTabsAndTabClicksStillWork() {
        val vm = showHome()
        compose.onNodeWithText(app.getString(R.string.screen_category_tv)).performClick()
        assertCategory(vm, DeviceCategory.TV, R.string.screen_category_tv)
        compose.onNodeWithTag("home_results").performTouchInput {
            val start = Offset(width * .7f, height * .75f)
            swipe(start, start - Offset(40f, 0f), durationMillis = 300)
        }
        assertCategory(vm, DeviceCategory.TV, R.string.screen_category_tv)
        compose.onNodeWithTag("home_results").performTouchInput {
            down(Offset(width * .8f, height * .75f))
            moveTo(Offset(width * .2f, height * .75f), delayMillis = 300)
            cancel()
        }
        assertCategory(vm, DeviceCategory.TV, R.string.screen_category_tv)
        swipeBlank(left = false)
        assertCategory(vm, null, R.string.screen_category_all)
    }

    @Test fun verticalListScrollDoesNotChangeCategory() {
        val profiles = (0..19).map { RemoteProfile("tv_$it", "电视 $it", category = DeviceCategory.TV) }
        val vm = showHome(profiles)
        val firstVisibleName = vm.filteredRemotes.value.first().name
        compose.onNodeWithText(firstVisibleName).assertIsDisplayed()
        compose.onNodeWithTag("home_results").performTouchInput { swipeUp() }
        compose.onNodeWithText(firstVisibleName).assertDoesNotExist()
        assertCategory(vm, null, R.string.screen_category_all)
        compose.runOnIdle { assertEquals(profiles.size, vm.allRemotes.value.size) }
    }

    @Test fun emptyLibrarySwipesKeepTheAddActionAvailable() {
        var adds = 0
        val vm = showHome(emptyList(), onAdd = { adds++ })
        swipeBlank()
        swipeBlank(left = false)
        compose.runOnIdle { assertNull(vm.selectedCategory.value) }
        compose.onNodeWithText(app.getString(R.string.screen_no_saved_remotes)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.screen_add_remote)).performClick()
        compose.runOnIdle { assertEquals(1, adds) }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w891dp-h411dp-land-mdpi")
    fun landscapeBlankSwipesUpdateCategoryChips() {
        val vm = showHome(dark = true)
        capture("landscape_before")
        swipeBlank()
        assertCategory(vm, DeviceCategory.TV, R.string.screen_category_tv)
        compose.waitUntil(5_000) { vm.filteredRemotes.value.map { it.id } == listOf(tv.id) }
        compose.onNodeWithText(tv.name).assertIsDisplayed()
        capture("landscape_after")
        swipeBlank(left = false)
        assertCategory(vm, null, R.string.screen_category_all)
    }

    @Test fun rightToLeftLayoutFollowsTheVisibleTabOrder() {
        val vm = showHome(direction = LayoutDirection.Rtl)
        swipeBlank(left = false)
        assertCategory(vm, DeviceCategory.TV, R.string.screen_category_tv)
        swipeBlank()
        assertCategory(vm, null, R.string.screen_category_all)
    }
}
