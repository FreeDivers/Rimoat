package org.qinyan.rimoat.hardware.learning

import android.hardware.usb.UsbDevice

/**
 * Filter and identification logic for supported USB infrared receiver/learning hardware.
 */
object UsbDeviceFilter {
    const val TIQIAA_VID_SILABS = 0x10C4
    const val TIQIAA_VID_MS = 0x045E
    const val TIQIAA_PID = 0x8468

    const val ELKSMART_VID = 0x045C
    val ELKSMART_PIDS = setOf(
        0x0131,
        0x0132,
        0x0134,
        0x014A,
        0x0184,
        0x0195,
        0x02AA
    )

    fun isTiqiaa(vid: Int, pid: Int): Boolean {
        return pid == TIQIAA_PID && (vid == TIQIAA_VID_SILABS || vid == TIQIAA_VID_MS)
    }

    fun isTiqiaa(device: UsbDevice): Boolean {
        return isTiqiaa(device.vendorId, device.productId)
    }

    fun isElkSmart(vid: Int, pid: Int): Boolean {
        return vid == ELKSMART_VID && pid in ELKSMART_PIDS
    }

    fun isElkSmart(device: UsbDevice): Boolean {
        return isElkSmart(device.vendorId, device.productId)
    }

    fun isSupported(vid: Int, pid: Int): Boolean {
        return isTiqiaa(vid, pid) || isElkSmart(vid, pid)
    }

    fun isSupported(device: UsbDevice): Boolean {
        return isSupported(device.vendorId, device.productId)
    }
}
