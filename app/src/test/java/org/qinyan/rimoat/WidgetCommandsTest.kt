package org.qinyan.rimoat

import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.*
import org.qinyan.rimoat.widget.*
import org.qinyan.rimoat.ui.components.RemoteKeyFallbackIcon
import org.qinyan.rimoat.ui.components.resolveButtonIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WidgetCommandsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val power = RemoteButton("power", "电源", "power", rawPattern = listOf(9000, 4500, 560),
        alternateRawPattern = listOf(8000, 4000, 560), key = ButtonKey.POWER)
    private fun tv() = RemoteProfile("tv", "电视", category = DeviceCategory.TV, buttons = listOf(power))
    private fun ac() = RemoteProfile("ac", "空调", category = DeviceCategory.AC, acProtocolId = 27)
    private class Encoder : AcSignalEncoder {
        val temperatures = mutableListOf<Int>()
        var beforeEncode: suspend () -> Unit = {}
        override suspend fun protocolFor(sourceRemoteId: String) = 27
        override suspend fun supportedModes(protocol: Int) = listOf(AcMode.COOL, AcMode.HEAT)
        override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction): List<IrSignal.Raw> {
            beforeEncode()
            temperatures += settings.temperature
            return listOf(IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560)))
        }
    }
    private inner class Rig(initial: RemoteProfile = ac()) {
        var profile: RemoteProfile? = initial
        var mock = false
        var failure = false
        var persistenceFailure = false
        var beforeSend: suspend () -> Unit = {}
        val encoder = Encoder()
        val sent = mutableListOf<IrSignal>()
        val journalFile = File(temporary.newFolder(), "remote-commands.json")
        val journal = RemoteCommandJournal(journalFile)
        val executor = RemoteCommandExecutor({ profile }, { _, settings ->
            if (persistenceFailure) error("disk full")
            profile = profile!!.copy(acSettings = settings)
        }, encoder, { if (mock) TransmitterType.MOCK else TransmitterType.BUILT_IN }, { signals, _, _, before ->
            beforeSend(); before()
            if (failure) error("detached")
            sent += signals
            TransmissionReceipt(mock)
        }, journal)
        suspend fun up(reject: Boolean = false) = executor.changeAc("ac", rejectIfBusy = reject) { state, _ -> state.apply(AcAction.TEMP_UP) to AcAction.TEMP_UP }
    }

    @Test fun instancePersistenceOrderAndRestoreAreIsolated() = runBlocking {
        val file = File(temporary.newFolder(), "widgets.json")
        val store = WidgetStore(file)
        val first = WidgetConfiguration(WidgetKind.PANEL, shortcuts = listOf(WidgetShortcut("tv", "power"), WidgetShortcut("ac", WidgetCommands.POWER)), arrangement = WidgetArrangement.LIST)
        val second = WidgetConfiguration(WidgetKind.SINGLE, "tv", listOf(WidgetShortcut("tv", "power")))
        store.save(1, first); store.save(2, second)
        store.remap(intArrayOf(1, 2), intArrayOf(2, 3)) // overlapping restore IDs must not destroy a source
        val restored = WidgetStore(file); restored.load()
        restored.remap(intArrayOf(1, 2), intArrayOf(2, 3)) // restarting the metadata worker is idempotent
        assertEquals(first, restored.configurations.value[2]); assertEquals(second, restored.configurations.value[3])
        restored.remove(2)
        assertEquals(mapOf(3 to second), restored.configurations.value)
    }
    @Test fun corruptWidgetStoreRecoversAndPreservesOriginalBytes() = runBlocking {
        val content = "{\"configurations\":".encodeToByteArray() + 0xFF.toByte()
        val file = File(temporary.newFolder(), "widgets.json").apply { writeBytes(content) }
        val store = WidgetStore(file)

        store.load()

        assertTrue(store.configurations.value.isEmpty())
        val backup = file.parentFile.listFiles()!!.filter { it != file }.single()
        assertArrayEquals(content, backup.readBytes())

        val configuration = WidgetConfiguration(WidgetKind.PANEL)
        WidgetStore(file).save(1, configuration)
        val restored = WidgetStore(file)
        restored.load()

        assertEquals(mapOf(1 to configuration), restored.configurations.value)
        assertEquals(listOf(backup), file.parentFile.listFiles()!!.filter { it != file })
        assertArrayEquals(content, backup.readBytes())
    }

    @Test fun invalidWidgetBindingDoesNotBlockOtherConfigurationsOrReconfiguration() = runBlocking {
        val tvPower = WidgetShortcut("tv", "power")
        val fanPower = WidgetShortcut("fan", "power")
        val first = WidgetConfiguration(WidgetKind.SINGLE, "tv", listOf(tvPower))
        val invalid = first.copy(shortcuts = listOf(fanPower))
        val replacement = invalid.copy(remoteId = "fan")
        val panel = WidgetConfiguration(WidgetKind.PANEL, shortcuts = listOf(tvPower, fanPower))
        val entries = Json.encodeToString(mapOf(1 to first, 2 to invalid, 3 to panel))
        val undecodable = """{"1":${Json.encodeToString(first)},"2":{"kind":"UNKNOWN"},"3":${Json.encodeToString(panel)}}"""

        for (content in listOf(entries, """{"configurations":$entries,"completedRestores":[]}""", undecodable)) {
            val file = File(temporary.newFolder(), "widgets.json").apply { writeText(content) }
            val store = WidgetStore(file)
            store.load()

            assertEquals(mapOf(1 to first, 3 to panel), store.configurations.value)

            WidgetStore(file).remove(3)
            WidgetStore(file).save(2, replacement)
            val restored = WidgetStore(file)
            restored.load()

            assertEquals(mapOf(1 to first, 2 to replacement), restored.configurations.value)
        }
    }
    @Test fun invalidBindingsAreRejected() {
        assertTrue(runCatching { WidgetConfiguration(WidgetKind.SINGLE, "tv", listOf(WidgetShortcut("ac", "x"))).validate() }.isFailure)
        assertTrue(runCatching { WidgetConfiguration(WidgetKind.PANEL, shortcuts = List(2) { WidgetShortcut("a", "b") }).validate() }.isFailure)
        assertTrue(runCatching { WidgetConfiguration(WidgetKind.PANEL, shortcuts = List(13) { WidgetShortcut("a", "$it") }).validate() }.isFailure)
    }
    @Test fun defaultPowerHonorsCapabilitiesAndLearnedSignals() {
        assertEquals("power", WidgetCommands.default(tv())?.buttonId)
        assertEquals(WidgetCommands.POWER, WidgetCommands.default(ac())?.buttonId)
        assertTrue(WidgetCommands.choices(ac(), emptySet()).isEmpty())
        assertNull(WidgetCommands.default(tv().copy(buttons = listOf(RemoteButton("blank", "电源")))))
        assertTrue(WidgetCommands.choices(ac().copy(useStaticAcButtons = true)).isEmpty())
    }

    @Test fun learnedAcShortcutReplaysRawWhileDynamicShortcutStillChangesTemperature() = runBlocking {
        val raw = IrSignal.Raw(39500, intArrayOf(2400, 1200, 600))
        val learned = RemoteButton("temperature", AcAction.TEMP_UP.label, sourceKeyType = AcAction.TEMP_UP.keyType)
            .withLearnedSignal(raw)
        val rig = Rig(ac().copy(buttons = listOf(learned)))
        val choices = WidgetCommands.choices(rig.profile!!)
        assertTrue(choices.any { it.id == WidgetCommands.PREFIX + "TEMP_UP" })
        assertTrue(choices.any { it.id == learned.id })
        assertNotEquals(choices.first { it.id == learned.id }.displayLabel(rig.profile),
            choices.first { it.id == WidgetCommands.PREFIX + "TEMP_UP" }.displayLabel(rig.profile))
        rig.executor.executeButton("ac", learned.id)
        assertEquals(listOf(raw), rig.sent)
        assertTrue(rig.encoder.temperatures.isEmpty())
        assertEquals(26, rig.profile!!.acSettings.temperature)
        rig.up()
        assertEquals(listOf(27), rig.encoder.temperatures)
        assertEquals(27, rig.profile!!.acSettings.temperature)
    }
    @Test fun defaultPowerPrefersStableMetadataOverNames() {
        val named = power.copy(id = "named", iconName = null, key = null)
        val candidates = listOf(ButtonKey.POWER, ButtonKey.POWER_ON, ButtonKey.POWER_OFF, ButtonKey.SECOND_POWER)
            .map { power.copy(label = "Custom key", iconName = null, key = it) }
        for (candidate in candidates) {
            val profile = tv().copy(buttons = listOf(named, candidate))
            assertEquals(candidate.toString(), candidate.id, WidgetCommands.default(profile)?.buttonId)
            assertTrue(WidgetCommands.choices(profile).last().isPower(profile))
        }
    }
    @Test fun defaultPowerUsesStableKeysAndOnlyAvailableChoices() {
        val input = power.copy(id = "input", label = "Input", iconName = null, key = ButtonKey.SOURCE)
        val renamed = tv().copy(buttons = listOf(input, power.copy(label = "Custom key", iconName = null)))
        assertEquals(power.id, WidgetCommands.default(renamed)?.buttonId)
        assertEquals(input.id, WidgetCommands.default(renamed, WidgetCommands.choices(renamed).take(1))?.buttonId)
        assertNull(WidgetCommands.default(renamed, emptyList()))
        val profile = tv().copy(buttons = listOf(input, power.copy(rawPattern = emptyList())))
        assertEquals(input.id, WidgetCommands.default(profile)?.buttonId)
        for (button in listOf(
            power.copy(id = "custom", label = "Power", iconName = null, key = null),
            power.copy(id = "power_saving", label = "Power", iconName = null, key = ButtonKey.ECO),
            power.copy(label = "电源", iconName = null, key = ButtonKey.VOLUME_UP),
            power.copy(label = "开关", iconName = null, key = null),
            power.copy(label = "Power", iconName = null, acAction = AcAction.MODE, key = ButtonKey.MODE)
        )) {
            val nonPower = tv().copy(buttons = listOf(input, button))
            assertFalse(WidgetCommands.choices(nonPower).last().isPower(nonPower))
            assertEquals(input.id, WidgetCommands.default(nonPower)?.buttonId)
        }
    }
    @Test fun widgetIconsUseStableIdentityAndMatchTheRemotePanel() {
        val source = power.copy(id = "learned_source", label = "Custom input", iconName = null,
            key = ButtonKey.SOURCE, isLearned = true)
        val confirm = power.copy(id = "learned_confirm", label = "My confirmation", iconName = null,
            key = ButtonKey.OK, isLearned = true)
        val custom = power.copy(id = "custom", label = "OK", iconName = null, key = null)
        val profile = tv().copy(buttons = listOf(source, confirm, custom))
        val commands = WidgetCommands.choices(profile)
        val state = WidgetSnapshot(profiles = listOf(profile), commands = mapOf(profile.id to commands))
        assertEquals(resolveButtonIcon(source), widgetCommandVector(profile, commands[0], state))
        assertEquals(Icons.Rounded.Check, widgetCommandVector(profile, commands[1], state))
        assertEquals(RemoteKeyFallbackIcon, widgetCommandVector(profile, commands[2], state))
        for (name in listOf("确认", "确定", "Power", "开关")) {
            val renamed = profile.copy(buttons = listOf(custom.copy(label = name)))
            val command = WidgetCommands.choices(renamed).single()
            assertEquals(RemoteKeyFallbackIcon, widgetCommandVector(renamed, command, state))
            assertFalse(command.isPower(renamed))
        }
    }
    @Test fun syntheticPowerActionsDoNotDependOnTheirLabels() {
        val profile = ac()
        for (id in listOf(WidgetCommands.POWER, "@ac:POWER_ON", "@ac:POWER_OFF")) {
            val command = WidgetCommand(id, "Custom key", null)
            assertTrue(command.isPower(profile))
            assertEquals(id, WidgetCommands.default(profile, listOf(WidgetCommand("other", "Other", null), command))?.buttonId)
        }
    }
    @Test fun stateDoesNotInventPowerOrVolumeForIndependentCodes() {
        assertEquals("电视", widgetDeviceStatus(tv(), null, false).primary)
        assertEquals("电源", widgetDeviceStatus(tv(), RemoteCommandRecord(label = "电源", buttonId = "power"), false).primary)
        assertEquals("26°", widgetDeviceStatus(ac(), null, false).primary)
        assertFalse(widgetCommandEnabled(WidgetShortcut("ac", "@ac:TEMP_UP"), ac().copy(acSettings = AcSettings(temperature = 30)), null, false))
        assertFalse(widgetCommandEnabled(WidgetShortcut("ac", "@ac:TEMP_DOWN"), ac().copy(acSettings = AcSettings(temperature = 16)), null, false))
        assertTrue(widgetCommandEnabled(WidgetShortcut("tv", "power"), tv(), null, false))
    }
    @Test fun independentStatusUsesCurrentStableButtonAndSeparatesSimulation() = runBlocking {
        val rig = Rig(tv()); rig.mock = true
        rig.executor.executeButton("tv", "power")
        val record = rig.journal.records.value.getValue("tv")
        assertEquals("power", record.buttonId)
        for (category in listOf(DeviceCategory.TV, DeviceCategory.FAN, DeviceCategory.AUDIO, DeviceCategory.PROJECTOR, DeviceCategory.CUSTOM)) {
            val profile = tv().copy(category = category, buttons = listOf(power.copy(label = "新名称")))
            assertEquals("新名称", widgetDeviceStatus(profile, record, true).command?.label)
            assertNull(widgetDeviceStatus(profile, record, false).command)
            assertNull(widgetDeviceStatus(profile.copy(buttons = emptyList()), record, true).command)
        }
        val legacy = RemoteCommandRecord(label = "电源")
        assertEquals("power", widgetDeviceStatus(tv(), legacy, false).command?.id)
        assertNull(widgetDeviceStatus(tv().copy(buttons = listOf(power, power.copy(id = "second"))), legacy, false).command)
        val restored = RemoteCommandJournal(File(temporary.newFolder(), "old.json").apply {
            writeText("""{"tv":{"label":"电源","sentAt":1,"simulated":false}}""")
        })
        restored.load()
        assertEquals("power", widgetDeviceStatus(tv(), restored.records.value["tv"], false).command?.id)
    }
    @Test fun concurrentCommandsReadCommittedState() = runBlocking {
        val rig = Rig()
        withTimeout(5000) { coroutineScope { List(4) { async { rig.up() } }.awaitAll() } }
        assertEquals(listOf(27, 28, 29, 30), rig.encoder.temperatures)
        assertEquals(30, rig.profile!!.acSettings.temperature)
    }
    @Test fun simulationIsSharedAndNeverCommitsRealSettings() = runBlocking {
        val rig = Rig(); rig.mock = true
        rig.up(); rig.up()
        assertEquals(26, rig.profile!!.acSettings.temperature)
        assertEquals("28°", widgetDeviceStatus(rig.profile!!, rig.journal.records.value["ac"], true).primary)
        rig.mock = false; rig.up()
        assertEquals(27, rig.profile!!.acSettings.temperature)
        assertNull(rig.journal.records.value["ac"]!!.demoSettings)
    }
    @Test fun changingProfileInvalidatesSimulationBase() = runBlocking {
        val rig = Rig(); rig.mock = true; rig.up()
        rig.profile = ac().copy(acSettings = AcSettings(temperature = 19))
        rig.up()
        assertEquals(20, rig.journal.records.value["ac"]!!.demoSettings!!.temperature)
        assertEquals(19, rig.profile!!.acSettings.temperature)
    }
    @Test fun deletionDuringEncodingPreventsPhysicalSend() = runBlocking {
        val rig = Rig(); rig.encoder.beforeEncode = { rig.profile = null }
        assertTrue(runCatching { rig.up() }.isFailure)
        assertTrue(rig.sent.isEmpty())
    }
    @Test fun editedButtonDuringHardwareWaitDoesNotSendStaleSignal() = runBlocking {
        val rig = Rig(tv()); rig.beforeSend = { rig.profile = tv().copy(buttons = emptyList()) }
        assertTrue(runCatching { rig.executor.executeButton("tv", "power") }.isFailure)
        assertTrue(rig.sent.isEmpty())
    }
    @Test fun failedSendKeepsSettingsAndRecord() = runBlocking {
        val rig = Rig(); rig.failure = true
        assertTrue(runCatching { rig.up() }.isFailure)
        assertEquals(26, rig.profile!!.acSettings.temperature)
        assertTrue(rig.journal.records.value.isEmpty())
    }
    @Test fun busyWidgetDoesNotAccumulateCommands() = runBlocking {
        val rig = Rig(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        rig.encoder.beforeEncode = { entered.complete(Unit); release.await() }
        withTimeout(5000) {
            val first = async { rig.up() }; entered.await()
            assertTrue(runCatching { rig.up(reject = true) }.isFailure)
            release.complete(Unit); first.await()
        }
        assertEquals(1, rig.sent.size)
    }
    @Test fun alternatingSignalCounterIsSharedAndSeparatesSimulation() = runBlocking {
        val rig = Rig(tv())
        rig.executor.executeButton("tv", "power"); rig.executor.executeButton("tv", "power")
        rig.mock = true; rig.executor.executeButton("tv", "power")
        assertEquals(listOf(9000, 8000, 9000), rig.sent.map { (it as IrSignal.Raw).patternMicros.first() })
    }
    @Test fun persistenceFailureAfterSendIsAWarningWithoutRetry() = runBlocking {
        val rig = Rig(); rig.persistenceFailure = true
        assertEquals(org.qinyan.rimoat.i18n.UiText.Resource(R.string.msg_sent_settings_failed), rig.up()!!.warning)
        assertEquals(1, rig.sent.size)
    }
    @Test fun journalConcurrentInitializationDoesNotEraseReceipt() = runBlocking {
        val journal = RemoteCommandJournal(File(temporary.newFolder(), "commands.json"))
        withTimeout(5000) { coroutineScope {
            val loads = List(10) { async { journal.load() } }
            val saves = List(10) { id -> async { journal.record("$id", RemoteCommandRecord(label = "$id")) } }
            (loads + saves).awaitAll()
        } }
        assertEquals(10, journal.records.value.size)
    }
    @Test fun corruptJournalIsPreservedAndRecoversAcrossReloads() = runBlocking {
        for (content in listOf("", "{\"tv\":", """{"tv":{"sentAt":"invalid"}}""")) {
            val file = File(temporary.newFolder(), "remote-commands.json").apply { writeText(content) }
            val journal = RemoteCommandJournal(file)

            journal.load()

            assertTrue(journal.records.value.isEmpty())
            assertCorruptJournalBackup(file, content)
            val restored = RemoteCommandJournal(file)
            restored.load()
            assertTrue(restored.records.value.isEmpty())
            assertCorruptJournalBackup(file, content)

            val record = RemoteCommandRecord(label = "power", buttonId = "power")
            journal.record("tv", record)
            val saved = RemoteCommandJournal(file)
            saved.load()
            assertEquals(mapOf("tv" to record), saved.records.value)
        }
    }
    @Test fun corruptJournalDoesNotBlockButtonTransmission() = runBlocking {
        val rig = Rig(tv())
        val content = "{\"tv\":"
        rig.journalFile.writeText(content)

        val result = rig.executor.executeButton("tv", "power")

        assertNotNull(result)
        assertNull(result!!.warning)
        assertEquals(listOf(9000), rig.sent.map { (it as IrSignal.Raw).patternMicros.first() })
        assertEquals("power", rig.journal.records.value.getValue("tv").buttonId)
        assertCorruptJournalBackup(rig.journalFile, content)
    }
    @Test fun corruptJournalDoesNotBlockAcTransmission() = runBlocking {
        val rig = Rig()
        val content = "{\"ac\":"
        rig.journalFile.writeText(content)

        val result = rig.up()

        assertNotNull(result)
        assertNull(result!!.warning)
        assertEquals(1, rig.sent.size)
        assertEquals(listOf(27), rig.encoder.temperatures)
        assertEquals(27, rig.profile!!.acSettings.temperature)
        assertEquals(result.label, rig.journal.records.value.getValue("ac").label)
        assertCorruptJournalBackup(rig.journalFile, content)
    }
    @Test fun journalReadFailurePropagatesAndAllowsRetry() = runBlocking {
        val file = File(temporary.newFolder(), "remote-commands.json")
        assertTrue(file.mkdir())
        val sentinel = File(file, "sentinel").apply { writeText("retained") }
        val journal = RemoteCommandJournal(file)

        assertTrue(runCatching { journal.load() }.exceptionOrNull() is IOException)
        assertEquals("retained", sentinel.readText())
        assertEquals(listOf(file), file.parentFile.listFiles()!!.toList())

        assertTrue(sentinel.delete())
        assertTrue(file.delete())
        file.writeText("""{"tv":{"label":"power","buttonId":"power"}}""")
        journal.load()
        assertEquals("power", journal.records.value.getValue("tv").buttonId)
    }

    private fun assertCorruptJournalBackup(file: File, content: String) {
        val backups = file.parentFile.listFiles()!!.filter { it.name.startsWith("remote-commands-corrupt-") }
        assertEquals(1, backups.size)
        assertTrue(backups.single().name.matches(Regex("""remote-commands-corrupt-\d+-[0-9a-f-]{36}\.json""")))
        assertEquals(content, backups.single().readText())
    }
}
