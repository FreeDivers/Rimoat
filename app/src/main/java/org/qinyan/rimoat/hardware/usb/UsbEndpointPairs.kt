package org.qinyan.rimoat.hardware.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

internal data class UsbEndpointPair(val outEp: UsbEndpoint, val inEp: UsbEndpoint)

internal fun usbEndpointPairs(
    intf: UsbInterface,
    transferType: Int = UsbConstants.USB_ENDPOINT_XFER_BULK
): List<UsbEndpointPair> {
    val endpoints = (0 until intf.endpointCount).map(intf::getEndpoint).filter { it.type == transferType }
    val outputs = endpoints.filter { it.direction == UsbConstants.USB_DIR_OUT }
    val inputs = endpoints.filter { it.direction == UsbConstants.USB_DIR_IN }
    val matched = outputs.mapNotNull { out ->
        inputs.singleOrNull { it.endpointNumber == out.endpointNumber }?.let { UsbEndpointPair(out, it) }
    }
    if (matched.isNotEmpty()) return matched
    // Only a unique pair is safe when the interface uses asymmetric endpoint numbers.
    return if (outputs.size == 1 && inputs.size == 1) listOf(UsbEndpointPair(outputs.single(), inputs.single()))
        else emptyList()
}
