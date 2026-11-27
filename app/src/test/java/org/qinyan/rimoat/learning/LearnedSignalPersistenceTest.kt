package org.qinyan.rimoat.learning

import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.AcMode
import org.qinyan.rimoat.data.AcSettings
import org.qinyan.rimoat.data.AcSignalEncoder
import org.qinyan.rimoat.data.ButtonKey
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.LegacyRemoteMigration
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteCommandExecutor
import org.qinyan.rimoat.data.RemoteCommandJournal
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.SignalType
import org.qinyan.rimoat.hardware.BuiltInIrTransmitter
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.TransmissionReceipt
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.hardware.learning.IrSignalSanitizer
import org.qinyan.rimoat.hardware.learning.MockIrLearner
import org.qinyan.rimoat.ui.components.acButtonForAction
import org.qinyan.rimoat.ui.components.acRawExtensionButtons
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LearnedSignalPersistenceTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testLearnedSignalRoundTripSerialization() {
        val necSignal = MockIrLearner.getDummyNecSignal()
        val sanitized = IrSignalSanitizer.sanitize(necSignal.frequencyHz, necSignal.patternMicros).getOrThrow()

        val button = IrSignalSanitizer.toRemoteButton(
            signal = sanitized,
            label = "TV Power",
            id = "btn_power_tv",
            row = 0,
            col = 0
        )

        val profile = RemoteProfile(
            id = "custom_learned_remote_1",
            name = "Living Room TV Learned",
            brand = "Learned",
            category = DeviceCategory.TV,
            buttons = listOf(button)
        )

        // Serialize to JSON
        val encodedJson = json.encodeToString(RemoteProfile.serializer(), profile)
        assertTrue(encodedJson.contains("\"TV Power\""))
        assertTrue(encodedJson.contains("\"RAW\""))

        // Deserialize from JSON
        val decodedProfile = json.decodeFromString(RemoteProfile.serializer(), encodedJson)
        assertEquals(profile.id, decodedProfile.id)
        assertEquals(1, decodedProfile.buttons.size)

        val decodedButton = decodedProfile.buttons[0]
        assertEquals(button.id, decodedButton.id)
        assertEquals("TV Power", decodedButton.label)
        assertEquals(SignalType.RAW, decodedButton.signalType)
        assertEquals(38000, decodedButton.frequencyHz)
        assertEquals(button.rawPattern, decodedButton.rawPattern)

        // Convert to IrSignal
        val irSignal = decodedButton.toIrSignal()
        assertTrue(irSignal is IrSignal.Raw)
        val raw = irSignal as IrSignal.Raw
        assertEquals(38000, raw.frequencyHz)
        assertArrayEquals(sanitized.patternMicros, raw.patternMicros)

        // Replay validation through BuiltInIrTransmitter
        val transmitValidation = BuiltInIrTransmitter.sanitizeAndValidate(raw.frequencyHz, raw.patternMicros)
        assertTrue(transmitValidation.isSuccess)
        val (transmitFreq, transmitPattern) = transmitValidation.getOrThrow()
        assertEquals(38000, transmitFreq)
        assertArrayEquals(sanitized.patternMicros, transmitPattern)
    }

    @Test
    fun testWithLearnedSignalReplacement() {
        val initialButton = RemoteButton(
            id = "btn_vol_up",
            label = "Vol+",
            signalType = SignalType.NEC,
            necAddress = 0x00FF,
            necCommand = 0x12
        )

        val newLearnedSignal = IrSignalSanitizer.sanitize(
            38000,
            intArrayOf(9000, 4500, 560, 1690, 560, 560)
        ).getOrThrow()

        val updatedButton = initialButton.withLearnedSignal(newLearnedSignal)
        assertEquals("btn_vol_up", updatedButton.id)
        assertEquals("Vol+", updatedButton.label)
        assertEquals(SignalType.RAW, updatedButton.signalType)
        assertEquals(38000, updatedButton.frequencyHz)
        assertEquals(6, updatedButton.rawPattern.size)
        assertEquals(null, updatedButton.necAddress)
        assertEquals(null, updatedButton.necCommand)
    }

    @Test
    fun relearningStandardAcKeyPreservesItsIdentityAndReplacesAllOldSignals() {
        val original = RemoteButton(
            id = "learned_temperature_up",
            label = "Warmer",
            key = ButtonKey.TEMPERATURE_UP,
            acAction = AcAction.TEMP_UP,
            builtinLabel = ButtonKey.TEMPERATURE_UP.name,
            signalType = SignalType.NEC,
            necAddress = 0x12AB,
            necCommand = 0x34,
            alternateRawPattern = listOf(8000, 4000, 500)
        )
        val signal = IrSignal.Raw(40000, intArrayOf(9000, 4500, 560, 1690, 560, 560))

        val learned = original.withLearnedSignal(signal)

        assertEquals(original.id, learned.id)
        assertEquals(original.label, learned.label)
        assertEquals(original.key, learned.key)
        assertEquals(original.acAction, learned.acAction)
        assertEquals(original.builtinLabel, learned.builtinLabel)
        assertTrue(learned.isLearned)
        assertEquals(SignalType.RAW, learned.signalType)
        assertNull(learned.necAddress)
        assertNull(learned.necCommand)
        assertFalse(learned.necExtended)
        assertTrue(learned.alternateRawPattern.isEmpty())
        for (pressIndex in 0..1) {
            val replay = learned.toIrSignal(pressIndex) as IrSignal.Raw
            assertEquals(signal.frequencyHz, replay.frequencyHz)
            assertArrayEquals(signal.patternMicros, replay.patternMicros)
        }
    }

    @Test
    fun learnedAcKeysKeepStableMetadataAndCustomNamesAfterDiskRoundTrip() {
        val signal = IrSignal.Raw(40000, intArrayOf(9000, 4500, 560, 1690, 560, 560))
        val templateKey = RemoteButton(
            id = "template_temperature_up",
            label = ButtonKey.TEMPERATURE_UP.name,
            builtinLabel = ButtonKey.TEMPERATURE_UP.name,
            key = ButtonKey.TEMPERATURE_UP,
            acAction = AcAction.TEMP_UP
        ).withLearnedSignal(signal)
        val customKey = RemoteButton(
            id = "custom_warmer",
            label = "My warmer button",
            key = ButtonKey.TEMPERATURE_UP,
            acAction = AcAction.TEMP_UP
        ).withLearnedSignal(signal)
        val profile = RemoteProfile(
            id = "learned_ac",
            name = "Study AC",
            category = DeviceCategory.AC,
            useStaticAcButtons = true,
            buttons = listOf(templateKey, customKey)
        )
        val file = temporary.newFile("learned-ac.json")

        file.writeText(json.encodeToString(RemoteProfile.serializer(), profile))
        val restored = json.decodeFromString(RemoteProfile.serializer(), file.readText())

        assertEquals(profile, restored)
        assertEquals(ButtonKey.TEMPERATURE_UP, restored.buttons.first().key)
        assertEquals(AcAction.TEMP_UP, restored.buttons.first().acAction)
        assertEquals(ButtonKey.TEMPERATURE_UP.name, restored.buttons.first().builtinLabel)
        assertEquals("My warmer button", restored.buttons.last().label)
        assertNull(restored.buttons.last().builtinLabel)
    }

    @Test
    fun learnedStandardAcActionSendsCapturedRawWithoutEncodingOrChangingState() = runBlocking {
        val signal = IrSignal.Raw(40000, intArrayOf(9000, 4500, 560, 1690, 560, 560))
        val learned = RemoteButton(
            id = "learned_temperature_up",
            label = ButtonKey.TEMPERATURE_UP.name,
            key = ButtonKey.TEMPERATURE_UP,
            acAction = AcAction.TEMP_UP
        ).withLearnedSignal(signal)
        val encoder = object : AcSignalEncoder {
            override suspend fun protocolFor(sourceRemoteId: String): Int? =
                error("A learned button must not resolve an AC protocol")

            override suspend fun supportedModes(protocol: Int): List<AcMode> =
                error("A learned button must not query AC modes")

            override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction): List<IrSignal.Raw> =
                error("A learned button must send its captured signal")
        }
        for ((staticButtons, protocol) in listOf(true to null, false to 27, false to null)) {
            val profile = RemoteProfile(
                id = "learned_ac",
                name = "Study AC",
                category = DeviceCategory.AC,
                buttons = listOf(learned),
                acProtocolId = protocol,
                useStaticAcButtons = staticButtons,
                acSettings = AcSettings(temperature = 24)
            )
            val sent = mutableListOf<IrSignal>()
            val journal = RemoteCommandJournal(File(temporary.newFolder(), "commands.json"))
            val executor = RemoteCommandExecutor(
                readProfile = { profile },
                commitSettings = { _, _ -> error("Captured signals must not alter the stored AC state") },
                encoder = encoder,
                route = { TransmitterType.BUILT_IN },
                transmit = { signals, _, route, beforeTransmission ->
                    assertEquals(TransmitterType.BUILT_IN, route)
                    beforeTransmission()
                    sent += signals
                    TransmissionReceipt(simulated = false)
                },
                journal = journal
            )

            val result = executor.executeButton(profile.id, learned.id)

            val transmitted = sent.single() as IrSignal.Raw
            assertEquals(signal.frequencyHz, transmitted.frequencyHz)
            assertArrayEquals(signal.patternMicros, transmitted.patternMicros)
            assertNull(result?.settings)
            assertEquals(false, result?.simulated)
            assertEquals(learned.id, journal.records.value[profile.id]?.buttonId)
            assertNull(journal.records.value[profile.id]?.demoSettings)
            assertEquals(24, profile.acSettings.temperature)
        }
    }

    @Test
    fun legacyLearnedDefaultsMigrateAtBothSchemaVersionsWithoutRenamingCustomKeys() {
        val legacyName = AcAction.MODE.label
        val original = RemoteButton(
            id = "legacy_default_mode",
            label = legacyName,
            builtinLabel = legacyName,
            isLearned = true,
            frequencyHz = 40000,
            rawPattern = listOf(9000, 4500, 560, 1690, 560, 560)
        )
        val renamed = original.copy(id = "legacy_renamed_mode", label = "My mode button")
        val custom = original.copy(id = "custom_mode", builtinLabel = null)

        for (schemaVersion in listOf(0, LegacyRemoteMigration.KEY_SCHEMA_VERSION)) {
            val profile = RemoteProfile(
                id = "legacy_learned_ac",
                name = "Study AC",
                category = DeviceCategory.AC,
                useStaticAcButtons = true,
                keySchemaVersion = schemaVersion,
                buttons = listOf(original, renamed, custom)
            )

            val migrated = LegacyRemoteMigration.normalizeKeys(profile)

            migrated.buttons.take(2).forEachIndexed { index, button ->
                assertEquals(profile.buttons[index].id, button.id)
                assertEquals(profile.buttons[index].label, button.label)
                assertEquals(ButtonKey.MODE, button.key)
                assertEquals(ButtonKey.MODE.name, button.builtinLabel)
                assertEquals(AcAction.MODE, button.acAction)
                assertEquals(original.frequencyHz, button.frequencyHz)
                assertEquals(original.rawPattern, button.rawPattern)
            }
            assertEquals(legacyName, migrated.buttons.last().label)
            assertNull(migrated.buttons.last().builtinLabel)
            assertNull(migrated.buttons.last().acAction)
            assertEquals(custom.rawPattern, migrated.buttons.last().rawPattern)
            val migratedCustom = migrated.buttons.last()
            assertEquals(listOf(migratedCustom), acRawExtensionButtons(migrated))
            assertNull(acButtonForAction(migrated.copy(buttons = listOf(migratedCustom)), AcAction.MODE))
            assertEquals(migrated, LegacyRemoteMigration.normalizeKeys(migrated))
        }
    }
}
