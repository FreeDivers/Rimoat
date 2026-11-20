package org.qinyan.rimoat

import org.junit.Assert.*
import org.junit.Test
import org.qinyan.rimoat.data.*

class FlipperIrImporterTest {
    private fun document(body: String) = "Filetype: IR signals file\nVersion: 1\n#\n$body"
    private val raw = "name: Power\ntype: raw\nfrequency: 38000\nduty_cycle: 0.33\ndata: 9000 4500 560"
    private fun parsed(protocol: String, address: String, command: String) =
        "name: Power\ntype: parsed\nprotocol: $protocol\naddress: $address\ncommand: $command"

    @Test fun rawKeepsCarrierDurationsAndStableIdentityWithoutConfirmedFunctions() {
        val first = FlipperIrImporter.decode(document(raw), "Living room.ir").backup.remotes.single()
        val second = FlipperIrImporter.decode(document(raw), "Renamed.ir").backup.remotes.single()
        assertEquals(first.id, second.id)
        assertEquals("Living room", first.name)
        assertEquals(listOf(9000, 4500, 560), first.buttons.single().rawPattern)
        assertEquals(38000, first.buttons.single().frequencyHz)
        assertTrue(first.verifiedFunctions.isEmpty())
    }

    @Test fun necUsesStandardInvertedBytesAndNecExtRetainsAllSixteenCommandBits() {
        val nec = FlipperIrImporter.decode(document(parsed("NEC", "12 00 00 00", "34 00 00 00")), "nec.ir")
            .backup.remotes.single().buttons.single()
        assertEquals(SignalType.NEC, nec.signalType)
        assertEquals(0x12, nec.necAddress)
        assertEquals(0x34, nec.necCommand)
        val ext = FlipperIrImporter.decode(document(parsed("NECext", "34 12 00 00", "78 56 00 00")), "ext.ir")
            .backup.remotes.single().buttons.single()
        assertEquals(SignalType.RAW, ext.signalType)
        assertEquals(67, ext.rawPattern.size)
        var bits = 0L
        repeat(32) { bit -> if (ext.rawPattern[3 + bit * 2] == 1690) bits = bits or (1L shl bit) }
        assertEquals(0x56781234L, bits)
    }

    @Test fun unsupportedProtocolsAreExplicitlySkippedAndAllUnsupportedCanBePreviewed() {
        val source = document(parsed("RC6", "00 00 00 00", "01 00 00 00"))
        val result = FlipperIrImporter.decode(source, "unsupported.ir")
        assertTrue(result.backup.remotes.isEmpty())
        assertEquals(listOf(SkippedIrSignal("Power", "RC6")), result.skippedSignals)
    }

    @Test fun rejectsMalformedRawWrongEndianWidthAndOutOfRangeFields() {
        val cases = listOf(raw.replace("38000", "0"), raw.replace("0.33", "NaN"), raw.replace("9000 4500 560", "-9000 4500"),
            raw.replace("9000 4500 560", "2000001"), parsed("NEC", "12 01 00 00", "01 00 00 00"),
            parsed("NECext", "12 34", "01 00 00 00"), raw + "\nfrequency: 40000")
        cases.forEach { value -> assertTrue(value, runCatching { FlipperIrImporter.decode(document(value), "bad.ir") }.isFailure) }
    }
}
