package org.qinyan.rimoat.ui.screens

import android.app.Activity
import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import com.github.takahirom.roborazzi.*
import java.io.File
import java.util.TimeZone
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.data.db.IrDatabaseRepository
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.components.getCategoryIcon
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.AddRemoteUiState
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.qinyan.rimoat.widget.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-port-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class DeviceCategoryIconsRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val originalTimeZone = TimeZone.getDefault()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val store = ViewModelStore()
    private val repository by lazy { RemoteRepository(app, scope) }
    private lateinit var transmitter: IrTransmitterManager
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )
    private val profiles = listOf(
        RemoteProfile("ac", "客厅空调", category = DeviceCategory.AC, sourceRemoteId = "R_air_1_1_0"),
        RemoteProfile("fan", "客厅风扇", category = DeviceCategory.FAN, sourceRemoteId = "R_fan_1_1_0"),
        RemoteProfile("stb", "客厅机顶盒", category = DeviceCategory.TV, sourceRemoteId = "R_stb_1_1_0"),
        RemoteProfile("dvd", "客厅 DVD", category = DeviceCategory.AUDIO, sourceRemoteId = "R_dvd_1_1_0"),
        RemoteProfile("light", "客厅灯", category = DeviceCategory.CUSTOM, sourceRemoteId = "R_light_1_1_0")
    ).map { it.copy(room = "客厅", buttons = listOf(
        RemoteButton("power", "电源", iconName = "power", rawPattern = listOf(9000, 4500, 560))
    )) }

    @After fun release() {
        TimeZone.setDefault(originalTimeZone)
        compose.runOnUiThread { store.clear() }
        if (::transmitter.isInitialized) transmitter.release()
        runBlocking { job.cancelAndJoin() }
    }

    private fun viewModel(): RemoteListViewModel {
        AppStrings.initialize(app)
        runBlocking { repository.awaitLoaded() }
        transmitter = IrTransmitterManager(app, scope)
        return RemoteListViewModel(repository, transmitter, ThemePreferences(app), BackupFileStore(app))
            .also { store.put("icons", it) }
    }

    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(10_000) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/device_icons_$name.webp", roborazziOptions = options)
    }

    @Test fun everyCatalogTypeKeepsItsIconAfterSavingAndJsonRoundTrip() = runBlocking {
        AppStrings.initialize(app)
        val catalog = IrDatabaseRepository(app)
        val categories = catalog.getCategories()
        assertEquals(11, categories.size)
        SQLiteDatabase.openDatabase(app.getDatabasePath("catalog_v1.db").path, null,
            SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT r.remote_id, c.code FROM remotes r JOIN categories c ON r.category_id = c.id", null).use { cursor ->
                assertTrue(cursor.count > 0)
                while (cursor.moveToNext()) {
                    val source = cursor.getString(0)
                    assertSame(source, getCategoryIcon(cursor.getString(1)),
                        getCategoryIcon(RemoteProfile("catalog", "遥控器", sourceRemoteId = source)))
                }
            }
        }
        for (category in categories) {
            val brand = catalog.getBrandsForCategory(category.id).first()
            val model = catalog.getRemotesForBrand(category.id, brand.brandIdx).first()
            val profile = catalog.buildRemoteProfile(model, "遥控器")
            val restored = Json.decodeFromString<RemoteProfile>(Json.encodeToString(profile))
            assertSame(category.code, getCategoryIcon(category.code), getCategoryIcon(restored))
            assertSame(getCategoryIcon(restored), getCategoryIcon(restored.copy(name = "Renamed", room = "Study")))
        }
    }

    @Test fun manualAndUnknownSourcesUseTheSavedCategory() {
        val categories = listOf("tv", "air", "fan", "amp", "pjt", "custom")
        DeviceCategory.entries.zip(categories).forEach { (category, code) ->
            val profile = RemoteProfile("manual", "遥控器", category = category)
            assertSame(getCategoryIcon(code), getCategoryIcon(profile))
            listOf("", "external", "R_unknown_1_1_0", "air_1_1_0").forEach { source ->
                assertSame(source, getCategoryIcon(category), getCategoryIcon(profile.copy(sourceRemoteId = source)))
            }
        }
        assertSame(getCategoryIcon(DeviceCategory.CUSTOM), getCategoryIcon("unknown"))
        assertSame(getCategoryIcon("air"), getCategoryIcon("AIR"))
        assertNotSame(getCategoryIcon(DeviceCategory.AC), getCategoryIcon(DeviceCategory.FAN))
    }

    @Test fun selectingAndSavingAirConditionerKeepsTheCategoryIcon() {
        val vm = viewModel()
        val categories = runBlocking { IrDatabaseRepository(app).getCategories() }
        val ac = profiles.first()
        var state by mutableStateOf(AddRemoteUiState())
        var saved by mutableStateOf(false)
        compose.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    when {
                        saved -> RemoteListScreen(vm, {}, {})
                        state.selectedCategory != null -> ConfirmRemoteView(state,
                            onNameChange = { state = state.copy(customRemoteName = it) },
                            onSave = {
                                runBlocking { repository.saveRemote(ac.copy(name = state.customRemoteName)) }
                                saved = true
                            })
                        else -> CategorySelectionView(categories, false, {
                            state = state.copy(selectedCategory = it, customRemoteName = ac.name)
                        })
                    }
                }
            }
        }
        capture("categories")
        compose.onNodeWithText(app.getString(R.string.screen_category_ac)).performClick()
        assertEquals("air", state.selectedCategory?.code)
        capture("confirmation")
        compose.onNodeWithText(app.getString(R.string.screen_save)).performScrollTo().performClick()
        waitFor { vm.filteredRemotes.value.size == 1 }
        compose.onNodeWithText(ac.name).assertIsDisplayed()
        assertEquals(ac.copy(updatedAt = 0), repository.remotes.value.single().copy(updatedAt = 0))
        capture("saved")
    }

    @Test fun savedApplianceTypesKeepTheirIconsThroughTrashAndRoomViews() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val vm = viewModel()
        runBlocking { profiles.forEach { repository.saveRemote(it) } }
        var page by mutableStateOf(0)
        compose.setContent {
            IrRemoteTheme(darkTheme = true, dynamicColor = false) {
                when (page) {
                    0 -> RemoteListScreen(vm, {}, {})
                    1 -> TrashScreen(vm, {})
                    else -> RoomManagementScreen(vm, {})
                }
            }
        }
        waitFor { vm.filteredRemotes.value.size == profiles.size }
        profiles.forEach { compose.onNodeWithText(it.name).assertIsDisplayed() }
        capture("home_dark")
        val stb = profiles.first { it.id == "stb" }
        compose.onNodeWithContentDescription(app.getString(R.string.screen_manage_name, stb.name)).performClick()
        compose.onNodeWithText(app.getString(R.string.screen_trash_remote)).performClick()
        compose.onNodeWithText(app.getString(R.string.screen_delete)).performClick()
        waitFor { repository.remotes.value.none { it.id == stb.id } }
        val deleted = runBlocking { repository.deletedEntries().single() }
        val trashDirectory = File(app.filesDir, "remotes/.trash")
        assertTrue(File(trashDirectory, deleted.entryId).renameTo(
            File(trashDirectory, "${stb.id}-1600000000000-fixture.json")
        ))
        compose.runOnIdle { page = 1 }
        waitFor { vm.trash.value?.size == 1 }
        compose.onNodeWithText(stb.name).assertIsDisplayed()
        capture("trash_dark")
        compose.onNodeWithContentDescription(app.getString(R.string.screen_restore_name, stb.name)).performClick()
        waitFor { vm.trash.value?.isEmpty() == true }
        assertEquals(stb.copy(updatedAt = 0), repository.remotes.value.first { it.id == stb.id }.copy(updatedAt = 0))
        compose.runOnIdle { page = 2 }
        compose.onNodeWithText("客厅").performClick()
        profiles.forEach { compose.onNodeWithText(it.name).assertIsDisplayed() }
        capture("room_dark")
    }

    @Test fun widgetStatusReusesTheSavedApplianceIcons() = runBlocking {
        AppStrings.initialize(app)
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val parent = LinearLayout(host.get()).apply { orientation = LinearLayout.VERTICAL }
            host.get().setContentView(parent, ViewGroup.LayoutParams(250, 480))
            val previewProfiles = profiles.take(3)
            val snapshot = WidgetSnapshot(loaded = true, profiles = previewProfiles, colorMode = AppColorMode.LIGHT,
                commands = previewProfiles.associate { it.id to WidgetCommands.choices(it) }, localeTags = "zh-CN")
            for (profile in previewProfiles) {
                val configuration = WidgetConfiguration(WidgetKind.STATUS, profile.id,
                    listOf(WidgetShortcut(profile.id, "power")))
                val views = composeConfigurationPreview(WidgetPreviewComposer.previewContext(host.get()),
                    configuration, snapshot, DpSize(250.dp, 160.dp))
                parent.addView(views.apply(host.get(), parent), LinearLayout.LayoutParams(250, 160))
            }
            parent.measure(View.MeasureSpec.makeMeasureSpec(250, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
            parent.layout(0, 0, 250, 480)
            fun descendants(view: View): List<View> = listOf(view) +
                if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            val children = descendants(parent)
            previewProfiles.forEach { profile ->
                assertTrue(children.filterIsInstance<TextView>().any { it.text.toString() == profile.name })
                assertTrue(children.filterIsInstance<ImageView>().any {
                    it.contentDescription == profile.category.widgetLabel(app) && it.drawable != null
                })
            }
            parent.captureRoboImage("src/test/screenshots/device_icons_widgets.webp", roborazziOptions = options)
        } finally { host.pause().stop().destroy() }
    }
}
