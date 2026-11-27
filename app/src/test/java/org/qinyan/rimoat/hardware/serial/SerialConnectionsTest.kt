package org.qinyan.rimoat.hardware.serial

import org.qinyan.rimoat.testing.AppStringsResourceRule
import org.junit.Assert.*
import org.junit.Test

class SerialConnectionsTest {
    @get:org.junit.Rule val strings = AppStringsResourceRule()

    private val device = SerialDeviceOption("/dev/bus/usb/001/002", "USB 串口", 0x1A86, 0x7523, 2, false, null)

    @Test fun refreshAndAuthorizationKeepTheSelectedConnection() {
        val tracker = SerialConnections()
        val initial = tracker.snapshot(listOf(device)).single()
        val authorized = tracker.snapshot(listOf(device.copy(granted = true, serialNumber = "A"))).single()
        assertEquals(initial.connectionId, authorized.connectionId)
        assertEquals(authorized, selectedSerialConnection(listOf(authorized), initial.connectionId))
    }

    @Test fun detachInvalidatesEvenWhenTheSamePathReturnsBeforeAScan() {
        val tracker = SerialConnections()
        val initial = tracker.snapshot(listOf(device)).single()
        tracker.detached(device.deviceName)
        val replacement = tracker.snapshot(listOf(device)).single()
        assertNotEquals(initial.connectionId, replacement.connectionId)
        assertThrows(IllegalArgumentException::class.java) {
            selectedSerialConnection(listOf(replacement), initial.connectionId)
        }
    }

    @Test fun missingDeviceAndNewProcessCannotReuseTheDraftBinding() {
        val tracker = SerialConnections()
        val initial = tracker.snapshot(listOf(device)).single()
        tracker.snapshot(emptyList())
        assertNotEquals(initial.connectionId, tracker.snapshot(listOf(device)).single().connectionId)
        assertNotEquals(initial.connectionId, SerialConnections().snapshot(listOf(device)).single().connectionId)
    }

    @Test fun identityChangeInvalidatesThePathAndDoesNotAffectOtherDevices() {
        val tracker = SerialConnections()
        val other = device.copy(deviceName = "other")
        val initial = tracker.snapshot(listOf(device.copy(serialNumber = "A"), other))
        tracker.snapshot(listOf(device, other)) // permission no longer exposes a serial
        val changed = tracker.snapshot(listOf(device.copy(serialNumber = "B"), other))
        assertNotEquals(initial[0].connectionId, changed[0].connectionId)
        assertEquals(initial[1].connectionId, changed[1].connectionId)
        tracker.detached(null)
        assertNotEquals(changed[1].connectionId, tracker.snapshot(listOf(other)).single().connectionId)
    }

    @Test fun blankOrDuplicateConnectionIdsAreNeverAccepted() {
        assertThrows(IllegalArgumentException::class.java) { selectedSerialConnection(listOf(device), "") }
        val selected = device.copy(connectionId = "live")
        assertThrows(IllegalArgumentException::class.java) { selectedSerialConnection(listOf(selected, selected), "live") }
    }
}
