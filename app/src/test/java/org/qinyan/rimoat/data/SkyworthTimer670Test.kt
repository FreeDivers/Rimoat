package org.qinyan.rimoat.data

import org.junit.Assert.*
import org.junit.Test

class SkyworthTimer670Test {
    @Test fun fullDayRetainsTwentyFourHoursAndUpdatesChecksum() {
        for (timerFlag in listOf(0x20, 0x40)) {
            val original = SkyworthTimer670Fixture.frame(1440, timerFlag)
            val restored = SkyworthTimer670.restoreHours(original, 1440)
            val bytes = SkyworthTimer670Fixture.decode(restored)

            assertEquals(24, bytes[7])
            assertEquals(0, bytes[8] and 0x3f)
            assertEquals(if (timerFlag == 0x20) 0x7e else 0x9e, bytes[13])
            assertEquals(0x65, SkyworthTimer670Fixture.decode(original)[7])
        }
    }

    @Test fun everySupportedDurationPreservesMinutesFlagsAndUnrelatedPulses() {
        for (powerOn in listOf(true, false)) {
            for (minutes in AcTimerOptions.forProtocol(670, powerOn).filter { it > 0 }) {
                for (mode in listOf(0x65, 0x6a, 0x69, 0x61, 0x62)) {
                    val original = SkyworthTimer670Fixture.frame(minutes, if (powerOn) 0x20 else 0x40, mode)
                    val saved = original.copyOf()
                    val restored = SkyworthTimer670.restoreHours(original, minutes)
                    val bytes = SkyworthTimer670Fixture.decode(restored)
                    assertEquals("Timer duration $minutes", minutes, bytes[7] * 60 + (bytes[8] and 0x3f))
                    assertEquals(bytes.take(13).sum() and 0xff, bytes[13])
                    assertArrayEquals(saved, original)
                    for (index in original.indices) {
                        val bit = (index - 6) / 2
                        val changedByte = index >= 6 && index % 2 == 0 && bit / 8 in listOf(7, 13)
                        if (!changedByte) assertEquals("Pulse $index", original[index], restored[index])
                    }
                }
            }
        }
    }

    @Test fun cancellationPreservesNativeFrame() {
        val original = SkyworthTimer670Fixture.frame(0)
        val saved = original.copyOf()
        assertSame(original, SkyworthTimer670.restoreHours(original, 0))
        assertArrayEquals(saved, original)
    }

    @Test fun outOfRangeDurationsAreRejected() {
        for (minutes in listOf(-1, 1441)) {
            assertThrows(IllegalArgumentException::class.java) {
                SkyworthTimer670.restoreHours(SkyworthTimer670Fixture.frame(0), minutes)
            }
        }
    }

    @Test fun invalidChecksumAndMismatchedMinutesAreRejected() {
        val original = SkyworthTimer670Fixture.frame(1439)
        assertThrows(IllegalStateException::class.java) {
            SkyworthTimer670.restoreHours(original, 1440)
        }
        val corrupted = original.copyOf().apply { this[214] = if (this[214] == 554) 1662 else 554 }
        assertThrows(IllegalStateException::class.java) {
            SkyworthTimer670.restoreHours(corrupted, 1439)
        }
    }
}

/** Synthetic timer payloads at the hour-restoration boundary. */
internal object SkyworthTimer670Fixture {
    fun frame(minutes: Int, timerFlag: Int = 0x20, mode: Int = 0x65): IntArray {
        val bytes = intArrayOf(0xb9, 0, 0, timerFlag, 0, 0, 0, mode, 0x80 or (minutes % 60), 0, 0, 0, 0x0d, 0)
        bytes[13] = bytes.take(13).sum() and 0xff
        return (listOf(38000, 3047, 3047, 3047, 4432) + bytes.flatMap { byte ->
            (7 downTo 0).flatMap { bit -> listOf(554, if (byte and (1 shl bit) == 0) 554 else 1662) }
        } + 554).toIntArray()
    }

    fun decode(frame: IntArray): List<Int> = frame.drop(5).dropLast(1).toList().chunked(16).map { pulses ->
        pulses.chunked(2).fold(0) { byte, pulse ->
            require(pulse[0] == 554 && pulse[1] in listOf(554, 1662))
            (byte shl 1) or if (pulse[1] == 1662) 1 else 0
        }
    }
}
