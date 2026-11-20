package org.qinyan.rimoat.hardware

import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.R
import kotlinx.coroutines.flow.StateFlow

/**
 * Supported IR transmission channel types across built-in, USB dongles, 3.5mm audio and simulation.
 */
enum class TransmitterType(private val sourceName: String) {
    BUILT_IN("内置红外"),
    USB("USB 外设"),
    USB_TIQIAA("遥控精灵 USB"),
    USB_ELKSMART("小蛮腰 USB"),
    USB_SERIAL("串口红外模块"),
    AUDIO("音频口红外"),
    MOCK("模拟模式"),
    NONE("无可用设备");

    val displayName: String get() = org.qinyan.rimoat.i18n.localizedLabel(sourceName)

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
