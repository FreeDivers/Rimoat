package org.qinyan.rimoat.hardware.usb

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Parcelable
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.hardware.IrProtocolEncoder
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.learning.ElkSmartOpeningAttempt
import org.qinyan.rimoat.hardware.learning.ElkSmartOpeningConnectionShadow
import org.qinyan.rimoat.hardware.learning.ElkSmartOpeningManagerShadow
import org.qinyan.rimoat.hardware.learning.UsbDeviceFilter
import org.qinyan.rimoat.i18n.AppStrings
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35], application = Application::class,
    shadows = [ElkSmartOpeningManagerShadow::class, ElkSmartOpeningConnectionShadow::class]
)
class ElkSmartUsbTransmitterTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = Shadow.extract<ElkSmartOpeningManagerShadow>(
        app.getSystemService(Context.USB_SERVICE) as UsbManager
    )

    @Before fun setUpDevice() {
        AppStrings.initialize(app)
        val endpoints = listOf(1, 0x81).map { address ->
            ReflectionHelpers.callConstructor(
                UsbEndpoint::class.java,
                ClassParameter.from(Int::class.javaPrimitiveType, address),
                ClassParameter.from(Int::class.javaPrimitiveType, UsbConstants.USB_ENDPOINT_XFER_BULK),
                ClassParameter.from(Int::class.javaPrimitiveType, 64),
                ClassParameter.from(Int::class.javaPrimitiveType, 1)
            )
        }
        val intf = ReflectionHelpers.callConstructor(
            UsbInterface::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(String::class.java, "fixture"),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(Int::class.javaPrimitiveType, 0)
        ).also { ReflectionHelpers.setField(it, "mEndpoints", endpoints.toTypedArray<Parcelable>()) }
        val device = Shadow.newInstanceOf(UsbDevice::class.java).also {
            ReflectionHelpers.setField(it, "mName", "fixture-usb")
            ReflectionHelpers.setField(it, "mVendorId", UsbDeviceFilter.ELKSMART_VID)
            ReflectionHelpers.setField(it, "mProductId", 0x0131)
            ReflectionHelpers.setField(it, "mInterfaces", arrayOf(intf))
        }
        manager.devices[device.deviceName] = device
    }

    @Test fun outOfRangeNecNeverOpensOrWritesUsb() = assertInvalidNec()

    @Test @Config(qualifiers = "en")
    fun englishRangeErrorUsesExistingResource() = assertInvalidNec()

    @Test @Config(qualifiers = "zh-rCN")
    fun simplifiedChineseRangeErrorUsesExistingResource() = assertInvalidNec()

    @Test @Config(qualifiers = "b+zh+Hant")
    fun traditionalChineseRangeErrorUsesExistingResource() = assertInvalidNec()

    @Test @Config(qualifiers = "ja")
    fun japaneseRangeErrorUsesExistingResource() = assertInvalidNec()

    private fun assertInvalidNec() = runBlocking {
        val attempt = ElkSmartOpeningAttempt()
        manager.attempts.add(attempt)
        val transmitter = ElkSmartUsbTransmitter(app)
        val invalid = listOf(
            IrSignal.Nec(-1, 0), IrSignal.Nec(65536, 0), IrSignal.Nec(0x12345, 0),
            IrSignal.Nec(Int.MIN_VALUE, 0), IrSignal.Nec(Int.MAX_VALUE, 0),
            IrSignal.Nec(0, -1), IrSignal.Nec(0, 256), IrSignal.Nec(0, 0x1FF),
            IrSignal.Nec(0, Int.MIN_VALUE), IrSignal.Nec(0, Int.MAX_VALUE)
        )
        for (signal in invalid) {
            for (extended in listOf(false, true)) {
                val result = transmitter.transmit(signal.copy(extended = extended))
                assertTrue("Invalid NEC must fail: $signal", result.exceptionOrNull() is IllegalArgumentException)
                assertEquals(app.getString(R.string.msg_nec_parameters_range), result.exceptionOrNull()?.message)
                assertEquals(0, manager.openCalls)
                assertTrue(attempt.writes.isEmpty())
                assertTrue(transmitter.isReady.value)
            }
        }
    }

    @Test fun validNecBoundariesAndRawStillWriteExpectedPackets() = runBlocking {
        val transmitter = ElkSmartUsbTransmitter(app)
        val signals = listOf(
            IrSignal.Nec(0, 0), IrSignal.Nec(255, 255), IrSignal.Nec(65535, 255),
            IrSignal.Nec(0x00FE, 0x45, extended = true),
            IrSignal.Raw(patternMicros = intArrayOf(9000, -4500, 560, -560))
        )
        for (signal in signals) {
            val attempt = ElkSmartOpeningAttempt()
            manager.attempts.add(attempt)
            transmitter.transmit(signal).getOrThrow()
            val raw = when (signal) {
                is IrSignal.Nec -> IrProtocolEncoder.encodeNec(signal)
                is IrSignal.Raw -> signal.copy(patternMicros = signal.patternMicros.map { kotlin.math.abs(it) }.toIntArray())
            }
            val expected = ElkSmartProtocolFormatter.encode(raw.frequencyHz, raw.patternMicros)
            val packets = attempt.writes.drop(1).map { it.second }
            assertEquals(expected.size, packets.size)
            expected.zip(packets).forEach { (expectedPacket, actual) -> assertArrayEquals(expectedPacket, actual) }
            assertEquals(listOf(0), attempt.releases)
            assertEquals(1, attempt.closes)
            assertFalse(attempt.transferredOnMain)
        }
    }
}
