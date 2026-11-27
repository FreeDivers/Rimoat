package org.qinyan.rimoat.widget

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.TransmissionReceipt
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.i18n.AppStrings
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class RemoteCommandRecoveryRoborazziTest {
    @get:Rule val temporary = TemporaryFolder()
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    @Test fun corruptReceiptsRecoverAndAllowWidgetTemperatureCommand() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val context = WidgetPreviewComposer.previewContext(app)
        val damagedContent = """{"remote":"""
        val file = File(temporary.newFolder(), "remote-commands.json").apply { writeText(damagedContent) }
        val journal = RemoteCommandJournal(file)
        journal.load()
        assertTrue(journal.records.value.isEmpty())
        val backup = file.parentFile!!.listFiles()!!.single { it.name.startsWith("remote-commands-corrupt-") }
        assertEquals(damagedContent, backup.readText())

        val button = RemoteButton("temperature_up", AcAction.TEMP_UP.label, iconName = "add", acAction = AcAction.TEMP_UP)
        val profile = RemoteProfile("remote", "客厅空调", category = DeviceCategory.AC,
            buttons = listOf(button), acProtocolId = 27)
        val signal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560))
        val sent = mutableListOf<IrSignal>()
        val encoder = object : AcSignalEncoder {
            override suspend fun protocolFor(sourceRemoteId: String) = 27
            override suspend fun supportedModes(protocol: Int) = listOf(AcMode.COOL)
            override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction): List<IrSignal.Raw> {
                assertEquals(27, protocol)
                assertEquals(27, settings.temperature)
                assertEquals(AcAction.TEMP_UP, action)
                return listOf(signal)
            }
        }
        val executor = RemoteCommandExecutor(
            readProfile = { id -> profile.takeIf { it.id == id } },
            commitSettings = { _, _ -> fail("Simulated commands must not commit appliance settings") },
            encoder = encoder,
            route = { TransmitterType.MOCK },
            transmit = { signals, _, route, before ->
                assertEquals(TransmitterType.MOCK, route)
                before()
                sent += signals
                TransmissionReceipt(simulated = true)
            },
            journal = journal
        )
        val configuration = WidgetConfiguration(WidgetKind.STATUS, profile.id,
            listOf(WidgetShortcut(profile.id, button.id)))
        fun snapshot() = WidgetSnapshot(loaded = true, profiles = listOf(profile),
            commands = mapOf(profile.id to WidgetCommands.choices(profile)),
            records = journal.records.value, simulated = true, localeTags = "zh-CN")
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            suspend fun capture(name: String, expectedTemperature: Int) {
                val views = composeConfigurationPreview(context, configuration, snapshot(), DpSize(250.dp, 160.dp))
                val parent = FrameLayout(host.get())
                host.get().setContentView(parent)
                parent.addView(views.apply(host.get(), parent))
                parent.measure(View.MeasureSpec.makeMeasureSpec(250, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY))
                parent.layout(0, 0, 250, 160)
                fun texts(view: View): List<String> = when (view) {
                    is TextView -> listOf(view.text.toString())
                    is android.view.ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                    else -> emptyList()
                }
                assertTrue(texts(parent).contains(profile.name))
                assertTrue(texts(parent).contains("$expectedTemperature°"))
                parent.captureRoboImage("src/test/screenshots/$name.webp", roborazziOptions = options)
            }
            capture("remote_command_recovery_before", 26)
            val result = executor.executeButton(profile.id, button.id)!!
            assertTrue(result.simulated)
            assertNull(result.warning)
            assertArrayEquals(signal.patternMicros, (sent.single() as IrSignal.Raw).patternMicros)
            assertEquals(27, journal.records.value.getValue(profile.id).demoSettings!!.temperature)
            capture("remote_command_recovery_after", 27)
            val reopened = RemoteCommandJournal(file)
            reopened.load()
            assertEquals(journal.records.value, reopened.records.value)
            assertEquals(damagedContent, backup.readText())
        } finally {
            host.pause().stop().destroy()
        }
    }

    @Test fun globalSendingDisablesOtherWidgetInstanceAndRecovers() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val context = WidgetPreviewComposer.previewContext(app)
        val buttons = listOf(
            RemoteButton("power", "电源", iconName = "power", rawPattern = listOf(9000, 4500, 560)),
            RemoteButton("vol_up", "音量+", iconName = "add", rawPattern = listOf(9000, 4500, 560))
        )
        val profile = RemoteProfile("tv_other", "客厅电视", category = DeviceCategory.TV, buttons = buttons)
        val configuration = WidgetConfiguration(
            WidgetKind.PANEL,
            profile.id,
            buttons.map { WidgetShortcut(profile.id, it.id) }
        )
        val baseSnapshot = WidgetSnapshot(
            loaded = true,
            profiles = listOf(profile),
            commands = mapOf(profile.id to WidgetCommands.choices(profile)),
            localeTags = "zh-CN"
        )
        fun descendants(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

        fun assertAllButtonsEnabled(root: ViewGroup, expectedEnabled: Boolean) {
            val pendingIntentTag = root.resources.getIdentifier("pending_intent_tag", "id", "android")
            val targets = descendants(root).mapNotNull { view ->
                val pendingIntent = view.getTag(pendingIntentTag) as? PendingIntent ?: return@mapNotNull null
                val intent = org.robolectric.Shadows.shadowOf(pendingIntent).savedIntent
                if (intent.getStringExtra("ActionCallbackBroadcastReceiver:callbackClass") != SendWidgetCommand::class.java.name) {
                    return@mapNotNull null
                }
                val icon = descendants(view).filterIsInstance<ImageView>().single { !it.contentDescription.isNullOrBlank() }
                val content = generateSequence(icon.parent as? View) { it.parent as? View }.takeWhile { it !== view }.last()
                assertEquals(expectedEnabled, content.isEnabled)
                view
            }
            assertEquals(2, targets.size)
        }

        val host = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            suspend fun renderAndAssert(name: String, snapshot: WidgetSnapshot, expectedEnabled: Boolean) {
                val views = composeConfigurationPreview(context, configuration, snapshot, DpSize(250.dp, 160.dp))
                val parent = FrameLayout(host.get())
                host.get().setContentView(parent)
                parent.addView(views.apply(host.get(), parent))
                parent.measure(View.MeasureSpec.makeMeasureSpec(250, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY))
                parent.layout(0, 0, 250, 160)
                assertAllButtonsEnabled(parent, expectedEnabled)
                parent.captureRoboImage("src/test/screenshots/$name.webp", roborazziOptions = options)
            }

            renderAndAssert("widget_global_sending_before", baseSnapshot.copy(sending = false), expectedEnabled = true)
            renderAndAssert("widget_global_sending_busy", baseSnapshot.copy(sending = true), expectedEnabled = false)
            renderAndAssert("widget_global_sending_busy_dark", baseSnapshot.copy(sending = true, colorMode = AppColorMode.DARK), expectedEnabled = false)
            renderAndAssert("widget_global_sending_after", baseSnapshot.copy(sending = false), expectedEnabled = true)
        } finally {
            host.pause().stop().destroy()
        }
    }
}
