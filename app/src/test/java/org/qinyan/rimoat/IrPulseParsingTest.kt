package org.qinyan.rimoat

import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.SignalType
import org.qinyan.rimoat.hardware.IrSignal
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class IrPulseParsingTest {

    @Test
    fun parsePulsesCsv_positiveAndNegativeNumbers_convertedToAbsoluteIntegers() {
        val sampleCsv = "9000,-4500,560,-560,560,-1680, 560 , -560 "
        val pattern = sampleCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull()?.let { v -> abs(v) } }

        assertEquals(8, pattern.size)
        assertEquals(listOf(9000, 4500, 560, 560, 560, 1680, 560, 560), pattern)
    }

    @Test
    fun remoteButton_toIrSignal_producesCorrectRawSignal() {
        val pattern = listOf(9000, 4500, 560, 560)
        val button = RemoteButton(
            id = "btn_power",
            label = "电源",
            signalType = SignalType.RAW,
            frequencyHz = 38000,
            rawPattern = pattern
        )

        val signal = button.toIrSignal()
        assertTrue(signal is IrSignal.Raw)
        val raw = signal as IrSignal.Raw
        assertEquals(38000, raw.frequencyHz)
        assertArrayEquals(intArrayOf(9000, 4500, 560, 560), raw.patternMicros)
    }

    @Test
    fun labelCleaning_removesPrefixesAndNormalizesNames() {
        fun cleanLabel(keyName: String): String {
            return when {
                keyName.startsWith("数字键 ") -> keyName.removePrefix("数字键 ").trim()
                keyName == "OK键" -> "OK"
                keyName == "菜单键" -> "菜单"
                keyName == "信源键" -> "信源"
                keyName == "上翻" -> "上"
                keyName == "下翻" -> "下"
                keyName == "左翻" -> "左"
                keyName == "右翻" -> "右"
                else -> keyName
            }
        }

        assertEquals("5", cleanLabel("数字键 5"))
        assertEquals("OK", cleanLabel("OK键"))
        assertEquals("菜单", cleanLabel("菜单键"))
        assertEquals("上", cleanLabel("上翻"))
        assertEquals("电源", cleanLabel("电源"))
    }
}

