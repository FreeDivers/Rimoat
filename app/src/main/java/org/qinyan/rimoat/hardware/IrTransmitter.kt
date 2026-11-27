package org.qinyan.rimoat.hardware

import androidx.annotation.StringRes
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.R
import kotlinx.coroutines.flow.StateFlow

/**
 * Supported IR transmission channel types across built-in, USB dongles, 3.5mm audio and simulation.
 */
enum class TransmitterType(@param:StringRes val labelRes: Int) {
    BUILT_IN(R.string.control_builtin_ir),
    USB(R.string.control_usb),
    USB_TIQIAA(R.string.control_tiqiaa),
    USB_ELKSMART(R.string.control_elksmart),
    USB_SERIAL(R.string.control_serial_module),
    AUDIO(R.string.control_audio_ir),
    MOCK(R.string.control_simulation_mode),
    NONE(R.string.control_no_devices);

    val displayName: String get() = AppStrings.get(labelRes)

    val isUsb: Boolean get() = this == USB || this == USB_TIQIAA || this == USB_ELKSMART || this == USB_SERIAL
    val isPhysical: Boolean get() = this != MOCK && this != NONE
}

/**
 * Unified interface for infrared signal transmitters.
 */
interface IrTransmitter {
    val type: TransmitterType
    val isReady: StateFlow<Boolean>
    suspend fun transmit(signal: IrSignal): Result<Unit>
}

/** Unified alias required by architectural specifications */
typealias IrSender = IrTransmitter
