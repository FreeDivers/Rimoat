package org.qinyan.rimoat

import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.PresetRemotes
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.SignalType
import org.qinyan.rimoat.hardware.IrSignal
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProfileSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun necAddressFormatSurvivesSerializationAndSignalConversion() {
        for (address in listOf(0x0000, 0x00FE, 0x00FF, 0x1234, 0xFFFF)) {
            for (extended in listOf(false, true)) {
                val button = RemoteButton("power", "Power", signalType = SignalType.NEC,
                    necAddress = address, necCommand = 0x45, necExtended = extended)
                val profile = RemoteProfile("remote", "Remote", buttons = listOf(button))
                val encoded = json.encodeToString(RemoteProfile.serializer(), profile)
                val decoded = json.decodeFromString(RemoteProfile.serializer(), encoded)

                assertEquals(profile, decoded)
                assertEquals(IrSignal.Nec(address, 0x45, extended), decoded.buttons.single().toIrSignal())
            }
        }
    }

    @Test
    fun savedNecButtonsWithoutFormatKeepTheirAddressConvention() {
        for (address in listOf(0x0000, 0x00FE, 0x00FF, 0x0100, 0x01FE, 0xFFFF)) {
            val encoded = """{"id":"power","label":"Power","signalType":"NEC","necAddress":$address,"necCommand":69}"""
            val button = json.decodeFromString(RemoteButton.serializer(), encoded)

            assertEquals(address > 0xFF, button.necExtended)
            assertEquals(IrSignal.Nec(address, 0x45), button.toIrSignal())
        }
    }

    @Test
    fun learningRawSignalClearsNecAddressFormat() {
        val button = RemoteButton("power", "Power", signalType = SignalType.NEC,
            necAddress = 0x00FE, necCommand = 0x45, necExtended = true)
        val signal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560))
        val learned = button.withLearnedSignal(signal)

        assertEquals(false, learned.necExtended)
        assertEquals(null, learned.necAddress)
        assertEquals(null, learned.necCommand)
        assertEquals(signal, learned.toIrSignal())
    }

    @Test
    fun testSerializationRoundTrip() {
        val original = RemoteProfile(
            id = "custom_test_1",
            name = "客厅测试电视",
            brand = "索尼 (Sony)",
            category = DeviceCategory.TV,
            buttons = listOf(
                RemoteButton(
                    id = "btn_power",
                    label = "电源",
                    iconName = "power",
                    signalType = SignalType.NEC,
                    necAddress = 0x01FE,
                    necCommand = 0x48
                ),
                RemoteButton(
                    id = "btn_vol_up",
                    label = "音量+",
                    iconName = "volume_up",
                    signalType = SignalType.RAW,
                    frequencyHz = 38000,
                    rawPattern = listOf(9000, 4500, 560, 560)
                )
            )
        )

        val jsonString = json.encodeToString(RemoteProfile.serializer(), original)
        val decoded = json.decodeFromString(RemoteProfile.serializer(), jsonString)

        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.brand, decoded.brand)
        assertEquals(original.category, decoded.category)
        assertEquals(2, decoded.buttons.size)

        val btn0 = decoded.buttons[0]
        assertEquals(SignalType.NEC, btn0.signalType)
        val sig0 = btn0.toIrSignal()
        assertTrue(sig0 is IrSignal.Nec)
        assertEquals(0x01FE, (sig0 as IrSignal.Nec).address)
        assertEquals(0x48, sig0.command)

        val btn1 = decoded.buttons[1]
        assertEquals(SignalType.RAW, btn1.signalType)
        val sig1 = btn1.toIrSignal()
        assertTrue(sig1 is IrSignal.Raw)
        assertEquals(38000, (sig1 as IrSignal.Raw).frequencyHz)
        assertEquals(4, sig1.patternMicros.size)
    }

    @Test
    fun testPresetRemotesIntegrity() {
        val presets = PresetRemotes.allPresets
        assertEquals(4, presets.size)

        for (preset in presets) {
            assertTrue("Preset ${preset.name} has no buttons", preset.buttons.isNotEmpty())
            val serialized = json.encodeToString(RemoteProfile.serializer(), preset)
            val deserialized = json.decodeFromString(RemoteProfile.serializer(), serialized)
            assertEquals(preset.id, deserialized.id)
            assertEquals(preset.buttons.size, deserialized.buttons.size)
        }
    }
}

