package org.qinyan.rimoat.learning

import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.SignalType
import org.qinyan.rimoat.hardware.BuiltInIrTransmitter
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.learning.IrSignalSanitizer
import org.qinyan.rimoat.hardware.learning.MockIrLearner
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedSignalPersistenceTest {

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
}
