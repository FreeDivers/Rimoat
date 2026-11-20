package org.qinyan.rimoat.data

/** Restores the hour field overwritten by protocol 670's timer encoder. */
internal object SkyworthTimer670 {
    fun restoreHours(original: IntArray, minutes: Int): IntArray {
        require(minutes in 0..1440)
        // Cancellation has no hour value to restore; preserve the native command.
        if (minutes == 0) return original
        check(original.size == 230)
        check(original.take(5) == listOf(38000, 3047, 3047, 3047, 4432))
        check((5..229 step 2).all { original[it] == 554 })
        val bytes = IntArray(14)
        for (bit in 0 until 112) {
            val value = when (original[6 + bit * 2]) {
                554 -> 0
                1662 -> 1
                else -> error("Unexpected Skyworth timer pulse")
            }
            bytes[bit / 8] = bytes[bit / 8] or (value shl (7 - bit % 8))
        }
        check(bytes[0] == 0xb9 && bytes[12] == 0x0d)
        check(bytes[13] == (bytes.take(13).sum() and 255))
        check((bytes[3] and 0x60) in listOf(0x20, 0x40))
        check(bytes[7] in listOf(0x65, 0x6a, 0x69, 0x61, 0x62))
        check((bytes[8] and 63) == minutes % 60)
        bytes[7] = minutes / 60 % 24
        bytes[13] = bytes.take(13).sum() and 255
        return original.copyOf().apply {
            for (byte in listOf(7, 13)) for (bit in 0..7) {
                this[6 + (byte * 8 + bit) * 2] =
                    if (bytes[byte] and (1 shl (7 - bit)) != 0) 1662 else 554
            }
        }
    }
}
