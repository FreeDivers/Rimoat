package org.qinyan.rimoat.hardware.serial

import org.qinyan.rimoat.testing.AppStringsResourceRule
import org.qinyan.rimoat.data.SerialIrSettingsStore
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SerialIrSettingsTest {
    @get:org.junit.Rule val strings = AppStringsResourceRule()

    private val settings = SerialIrSettings(SerialIrModule.YS_IRTM, vendorId = 0x1A86, productId = 0x7523)
    private fun device(path: String, serial: String? = null) = SerialDeviceOption(path, "CH340", 0x1A86, 0x7523, 1, true, serial)
    @Test fun permissionCandidatesStaySeparateFromVerifiedSerialMatches() {
        val config = SerialIrSettings(module = SerialIrModule.YS_IRTM, vendorId = 1, productId = 2, serialNumber = "fixture")
        val pending = SerialDeviceOption("usb-fixture", "USB", 1, 2, 1, false, null)
        org.junit.Assert.assertNull(selectSerialDevice(config, listOf(pending), null))
        org.junit.Assert.assertEquals(listOf(pending), serialPermissionCandidates(config, listOf(pending)))
        org.junit.Assert.assertTrue(serialPermissionCandidates(config, listOf(pending.copy(productId = 3))).isEmpty())
        val wrong = pending.copy(granted = true, serialNumber = "different")
        org.junit.Assert.assertNull(selectSerialDevice(config, listOf(wrong), null))
        org.junit.Assert.assertTrue(serialPermissionCandidates(config, listOf(wrong)).isEmpty())
        val verified = pending.copy(granted = true, serialNumber = "fixture")
        org.junit.Assert.assertEquals(verified, selectSerialDevice(config, listOf(verified), null))
    }

    @Test fun unconfiguredAndAmbiguousBridgesNeverBecomeIrDevices() {
        val a = device("a"); val b = device("b")
        assertNull(selectSerialDevice(SerialIrSettings(), listOf(a), null))
        assertEquals(a, selectSerialDevice(settings, listOf(a), null))
        assertNull(selectSerialDevice(settings, listOf(a, b), null))
        assertEquals(b, selectSerialDevice(settings, listOf(a, b), "b"))
        assertNull(selectSerialDevice(settings.copy(serialNumber = "known"), listOf(a, b), "a"))
        assertEquals(device("new", "known"), selectSerialDevice(settings.copy(serialNumber = "known"), listOf(device("new", "known")), "old"))
        assertNull(selectSerialDevice(settings.copy(portIndex = 1), listOf(a), null))
    }
    @Test fun localConfigurationSurvivesRestartAndFailedSavePreservesFile() {
        val dir = Files.createTempDirectory("serial-settings").toFile()
        try {
            val file = File(dir, "serial_ir.json")
            assertEquals(SerialIrSettings(), SerialIrSettingsStore(file).read())
            SerialIrSettingsStore(file).write(settings)
            assertEquals(settings, SerialIrSettingsStore(file).read())
            assertThrows(IllegalArgumentException::class.java) { SerialIrSettingsStore(file).write(settings.copy(baudRate = 7)) }
            assertEquals(settings, SerialIrSettingsStore(file).read())
            file.writeText("broken")
            assertThrows(Exception::class.java) { SerialIrSettingsStore(file).read() }
        } finally { dir.deleteRecursively() }
    }
}
