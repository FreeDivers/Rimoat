package org.qinyan.rimoat.data

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.hardware.IrSignal

/** The native implementation serializes access to the original encoder's global buffer. */
internal object NativeAcEncoder {
    private val loaded: Boolean by lazy {
        try { System.loadLibrary("rimoat_encoder"); supportedAbi() }
        catch (_: UnsatisfiedLinkError) { false }
    }
    val available: Boolean get() = loaded
    private external fun supportedAbi(): Boolean

    private external fun encodeFrame(protocol: Int, key: Int, counter: Int, timer: Int, state: ByteArray): IntArray?

    fun encode(protocol: Int, settings: AcSettings, action: AcAction, counter: Int = settings.counter): IrSignal.Raw? {
        check(available) { AppStrings.get(R.string.msg_ac_encoder_unavailable) }
        val original = encodeFrame(protocol, action.keyType, counter, settings.timerMinutes, settings.nativeState()) ?: return null
        val frame = if (protocol == 670 && action == AcAction.TIMER)
            SkyworthTimer670.restoreHours(original, settings.timerMinutes) else original
        return IrSignal.Raw(frame[0], frame.copyOfRange(1, frame.size))
    }
}
