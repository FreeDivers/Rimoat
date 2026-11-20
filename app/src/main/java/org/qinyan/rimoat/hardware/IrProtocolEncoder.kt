package org.qinyan.rimoat.hardware

/**
 * Encodes structured protocol signals (such as NEC) into raw microsecond pulse patterns.
 */
object IrProtocolEncoder {

    const val DEFAULT_NEC_FREQUENCY_HZ = 38000

    private const val NEC_HDR_MARK = 9000
    private const val NEC_HDR_SPACE = 4500
    private const val NEC_BIT_MARK = 560
    private const val NEC_ONE_SPACE = 1690
    private const val NEC_ZERO_SPACE = 560
    private const val NEC_STOP_MARK = 560

    /**
     * Encodes an [IrSignal.Nec] into an [IrSignal.Raw] containing alternating mark/space microsecond durations.
     *
     * In standard NEC:
     * - Address is 8-bit (or 16-bit extended NEC)
     * - Command is 8-bit, followed by inverted 8-bit command
     */
    fun encodeNec(nec: IrSignal.Nec, frequencyHz: Int = DEFAULT_NEC_FREQUENCY_HZ): IrSignal.Raw {
        val pattern = ArrayList<Int>(67)

        // 1. Leader pulse
        pattern.add(NEC_HDR_MARK)
        pattern.add(NEC_HDR_SPACE)

        // 2. Address bytes (LSB first)
        val addr = nec.address
        val isExtendedNec = (addr and 0xFF00) != 0
        val byte0 = addr and 0xFF
        val byte1 = if (isExtendedNec) {
            (addr shr 8) and 0xFF
        } else {
            byte0.inv() and 0xFF
        }

        // 3. Command bytes (LSB first)
        val cmd = nec.command
        val byte2 = cmd and 0xFF
        val byte3 = byte2.inv() and 0xFF

        encodeByte(byte0, pattern)
        encodeByte(byte1, pattern)
        encodeByte(byte2, pattern)
        encodeByte(byte3, pattern)

        // 4. Stop mark
        pattern.add(NEC_STOP_MARK)

        return IrSignal.Raw(frequencyHz, pattern.toIntArray())
    }

    private fun encodeByte(dataByte: Int, out: ArrayList<Int>) {
        for (i in 0 until 8) {
            val bit = (dataByte shr i) and 1
            out.add(NEC_BIT_MARK)
            if (bit == 1) {
                out.add(NEC_ONE_SPACE)
            } else {
                out.add(NEC_ZERO_SPACE)
            }
        }
    }
}

