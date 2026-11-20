package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.hardware.UsbAccessState
import org.qinyan.rimoat.hardware.serial.SerialIrSettings
import org.junit.Assert.*
import org.junit.Test

class TransmitterChannelStateTest {
    private fun channels(
        available: Set<TransmitterType> = emptySet(),
        selected: TransmitterType? = null,
        devices: List<UsbAccessState> = emptyList()
    ) = transmitterChannels(available, selected, devices, SerialIrSettings(), true)

    @Test fun absentHardwareCannotBeSelectedAndIsNotShownAsNeedingConfiguration() {
        val states = channels()
        assertTrue(states.none { it.available || it.needsAttention })
        assertEquals("未连接", states.single { it.type == TransmitterType.USB_SERIAL }.status)
        assertEquals("此设备无内置红外", states.single { it.type == TransmitterType.BUILT_IN }.status)
    }

    @Test fun aConnectedUnapprovedDeviceRemainsVisibleButNotSelectable() {
        val device = UsbAccessState("/dev/test", "USB", false, transmitterType = TransmitterType.USB_TIQIAA)
        val state = channels(devices = listOf(device)).single { it.type == device.transmitterType }
        assertTrue(state.needsAttention)
        assertFalse(state.available)
        assertEquals("需要 USB 授权", state.status)
    }

    @Test fun aDisconnectedManualChoiceIsNotHiddenAmongCollapsedChannels() {
        val state = channels(selected = TransmitterType.USB_ELKSMART)
            .single { it.type == TransmitterType.USB_ELKSMART }
        assertTrue(state.selected)
        assertTrue(state.needsAttention)
        assertFalse(state.available)
        assertEquals("设备已断开", state.status)
    }

    @Test fun permissionRetryShowsPendingStateUntilItFinishes() {
        val device = UsbAccessState("/dev/test", "USB", false, pending = true, denied = true,
            transmitterType = TransmitterType.USB_SERIAL)
        val state = channels(devices = listOf(device)).single { it.type == TransmitterType.USB_SERIAL }
        assertEquals("等待系统授权", state.status)
        assertTrue(state.needsAttention)
        assertFalse(state.available)
    }

    @Test fun serialPermissionAloneDoesNotMakeAnUnconfiguredModuleUsable() {
        val device = UsbAccessState("/dev/test", "USB", true, transmitterType = TransmitterType.USB_SERIAL)
        val state = channels(devices = listOf(device)).single { it.type == TransmitterType.USB_SERIAL }
        assertEquals("需要配置模块", state.status)
        assertTrue(state.needsAttention)
        assertFalse(state.available)
    }

    @Test fun oneReadyAccessoryRemainsUsableWhenAnotherNeedsPermission() {
        val type = TransmitterType.USB_TIQIAA
        val devices = listOf(
            UsbAccessState("/dev/one", "USB 1", true, transmitterType = type),
            UsbAccessState("/dev/two", "USB 2", false, transmitterType = type)
        )
        val state = channels(available = setOf(type), devices = devices).single { it.type == type }
        assertTrue(state.available)
        assertFalse(state.needsAttention)
        assertEquals(2, state.devices.size)
    }
}
