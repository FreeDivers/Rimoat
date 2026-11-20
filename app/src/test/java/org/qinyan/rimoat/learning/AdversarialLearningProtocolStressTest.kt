package org.qinyan.rimoat.learning

import org.qinyan.rimoat.hardware.learning.ElkSmartProtocol
import org.qinyan.rimoat.hardware.learning.IrSignalSanitizer
import org.qinyan.rimoat.hardware.learning.TiqiaaProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/**
 * Adversarial and stress test harness for IR learning protocol decoders and pulse sanitizers.
 *
 * Stress-tests boundary conditions, corrupt packets, buffer overruns, carry overflows,
 * optical glitches, and parity invariants.
 */
class AdversarialLearningProtocolStressTest {

    // =========================================================================
    // 1. IrSignalSanitizer Adversarial Tests
    // =========================================================================

    @Test
    fun testSanitizerEmptyAndSubMinimumPulsePatterns() {
        // Empty
        assertTrue(IrSignalSanitizer.sanitize(38000, intArrayOf()).isFailure)

        // 1 to 5 pulses (below MIN_PULSE_COUNT = 6)
        for (count in 1..5) {
            val pulses = IntArray(count) { 1000 }
            val res = IrSignalSanitizer.sanitize(38000, pulses)
            assertTrue("Expected failure for count=$count", res.isFailure)
        }
    }

    @Test
    fun testSanitizerAllZeroesAndIntMinValue() {
        // All zeroes
        val zeroes = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
        val resZeroes = IrSignalSanitizer.sanitize(38000, zeroes)
        assertTrue(resZeroes.isFailure)
        assertTrue(resZeroes.exceptionOrNull()?.message?.contains("Invalid pulse duration") == true)

        // Int.MIN_VALUE: abs(Int.MIN_VALUE) remains negative in 32-bit signed int,
        // and must reject the entire waveform to preserve mark/space polarity.
        val withMinVal = intArrayOf(Int.MIN_VALUE, 9000, 4500, 560, 560, 560, 1690)
        val resMinVal = IrSignalSanitizer.sanitize(38000, withMinVal)
        assertTrue(resMinVal.isFailure)
        assertTrue(IrSignalSanitizer.sanitize(38000, intArrayOf(9000, 4500, 0, 560, 560, 560, 1690)).isFailure)

        // Only Int.MIN_VALUE items
        val onlyMinVal = intArrayOf(Int.MIN_VALUE, Int.MIN_VALUE)
        assertTrue(IrSignalSanitizer.sanitize(38000, onlyMinVal).isFailure)
    }

    @Test
    fun testSanitizerNegativePulsesNormalizedToPositive() {
        val negativeInput = intArrayOf(-9000, -4500, -560, -560, -560, -1690)
        val res = IrSignalSanitizer.sanitize(38000, negativeInput)
        assertTrue(res.isSuccess)
        val out = res.getOrThrow().patternMicros
        assertEquals(6, out.size)
        for (p in out) {
            assertTrue("Pulse must be strictly positive: $p", p > 0)
        }
        assertArrayEquals(intArrayOf(9000, 4500, 560, 560, 560, 1690), out)
    }

    @Test
    fun testSanitizerOpticalSpikeGlitchFilteringPairs() {
        // 1 spike pair: (60µs mark, 80µs space) + 6 valid pulses -> pair dropped, 6 remain
        val onePair = intArrayOf(60, 80, 9000, 4500, 560, 560, 560, 1690)
        val res1 = IrSignalSanitizer.sanitize(38000, onePair)
        assertTrue(res1.isSuccess)
        assertEquals(6, res1.getOrThrow().patternMicros.size)
        assertEquals(9000, res1.getOrThrow().patternMicros[0])

        // 3 consecutive spike pairs (6 noise pulses) + 6 valid pulses = 12 total -> 6 dropped, 6 remain
        val threePairs = intArrayOf(20, 30, 40, 50, 60, 70, 9000, 4500, 560, 560, 560, 1690)
        val res3 = IrSignalSanitizer.sanitize(38000, threePairs)
        assertTrue(res3.isSuccess)
        assertEquals(6, res3.getOrThrow().patternMicros.size)
        assertEquals(9000, res3.getOrThrow().patternMicros[0])

        // Spikes that would reduce count below 6: not dropped, but tested against total duration
        val shortSpikes = intArrayOf(50, 50, 50, 50, 50, 50)
        val resShort = IrSignalSanitizer.sanitize(38000, shortSpikes)
        // Total duration 300 µs < 1000 µs MIN_TOTAL_DURATION_US
        assertTrue(resShort.isFailure)
    }

    @Test
    fun testSanitizerDurationBoundariesAndFrameworkLimit() {
        // Less than 1000 µs: 6 pulses of 160 µs = 960 µs
        val sub1000 = intArrayOf(160, 160, 160, 160, 160, 160)
        assertTrue(IrSignalSanitizer.sanitize(38000, sub1000).isFailure)

        // Exactly 1000 µs total duration
        val exact1000 = intArrayOf(200, 200, 150, 150, 150, 150)
        val resExact = IrSignalSanitizer.sanitize(38000, exact1000)
        assertTrue(resExact.isSuccess)

        // Exceeds 2,000,000 µs framework limit
        val overMax = intArrayOf(1_000_000, 1_000_001, 500, 500, 500, 500)
        assertTrue(IrSignalSanitizer.sanitize(38000, overMax).isFailure)

        // Integer MAX_VALUE pulse must not cause signed arithmetic wrap-around
        val hugeVal = intArrayOf(Int.MAX_VALUE, 500, 500, 500, 500, 500)
        val resHuge = IrSignalSanitizer.sanitize(38000, hugeVal)
        assertTrue(resHuge.isFailure)
        assertTrue(resHuge.exceptionOrNull()?.message?.contains("exceeds framework limit") == true)
    }

    @Test
    fun testSanitizerParityPaddingInvariant() {
        // Any odd length valid input must result in an even length output with 40,000 µs trailing space
        val oddLengths = listOf(7, 9, 13, 25, 67, 101)
        for (len in oddLengths) {
            val oddInput = IntArray(len) { if (it < 2) 4000 else 600 }
            val res = IrSignalSanitizer.sanitize(38000, oddInput)
            assertTrue("Expected success for length $len", res.isSuccess)
            val output = res.getOrThrow().patternMicros
            assertEquals("Output size must be even for odd input length $len", 0, output.size % 2)
            assertEquals("Output size must be input size + 1", len + 1, output.size)
            assertEquals(IrSignalSanitizer.TRAILING_SPACE_PADDING_US, output.last())
        }

        // Even length inputs must NOT be padded
        val evenLengths = listOf(6, 8, 10, 24, 66, 100)
        for (len in evenLengths) {
            val evenInput = IntArray(len) { if (it < 2) 4000 else 600 }
            val res = IrSignalSanitizer.sanitize(38000, evenInput)
            assertTrue("Expected success for length $len", res.isSuccess)
            val output = res.getOrThrow().patternMicros
            assertEquals("Output size must be even for even input length $len", 0, output.size % 2)
            assertEquals("Output size must equal input size", len, output.size)
            assertEquals(600, output.last())
        }
    }

    @Test
    fun testSanitizerCarrierFrequencyClampingAdversarial() {
        val pattern = intArrayOf(9000, 4500, 560, 560, 560, 1690)

        // Extreme negative frequencies
        assertEquals(38000, IrSignalSanitizer.sanitize(-100000, pattern).getOrThrow().frequencyHz)
        assertEquals(38000, IrSignalSanitizer.sanitize(-1, pattern).getOrThrow().frequencyHz)
        assertEquals(38000, IrSignalSanitizer.sanitize(0, pattern).getOrThrow().frequencyHz)

        // Clamp to MIN_FREQUENCY_HZ (15000)
        assertEquals(15000, IrSignalSanitizer.sanitize(1, pattern).getOrThrow().frequencyHz)
        assertEquals(15000, IrSignalSanitizer.sanitize(14999, pattern).getOrThrow().frequencyHz)

        // Clamp to MAX_FREQUENCY_HZ (60000)
        assertEquals(60000, IrSignalSanitizer.sanitize(60001, pattern).getOrThrow().frequencyHz)
        assertEquals(60000, IrSignalSanitizer.sanitize(500000, pattern).getOrThrow().frequencyHz)

        // Valid frequencies within [15000, 60000]
        assertEquals(36000, IrSignalSanitizer.sanitize(36000, pattern).getOrThrow().frequencyHz)
        assertEquals(38000, IrSignalSanitizer.sanitize(38000, pattern).getOrThrow().frequencyHz)
        assertEquals(40000, IrSignalSanitizer.sanitize(40000, pattern).getOrThrow().frequencyHz)
        assertEquals(56000, IrSignalSanitizer.sanitize(56000, pattern).getOrThrow().frequencyHz)
    }

    @Test
    fun testGuessProtocolSafety() {
        assertEquals("RAW", IrSignalSanitizer.guessProtocol(intArrayOf()))
        assertEquals("RAW", IrSignalSanitizer.guessProtocol(intArrayOf(1000)))
        assertEquals("RAW", IrSignalSanitizer.guessProtocol(intArrayOf(9000, 4500)))

        // Boundary NEC
        val nec = IntArray(66) { if (it == 0) 9000 else if (it == 1) 4500 else 560 }
        assertEquals("NEC (38kHz)", IrSignalSanitizer.guessProtocol(nec))

        // Boundary Sony
        val sony25 = IntArray(25) { if (it == 0) 2400 else if (it == 1) 600 else 600 }
        assertEquals("SONY (40kHz)", IrSignalSanitizer.guessProtocol(sony25))

        // HVAC / AC
        val hvac = IntArray(80) { 500 }
        assertEquals("AC/HVAC (Raw)", IrSignalSanitizer.guessProtocol(hvac))
    }

    // =========================================================================
    // 2. USB frame protocol adversarial tests
    // =========================================================================

    @Test
    fun testTiqiaaInboundAssemblerCorruptedHeadersAndLengths() {
        val assembler = TiqiaaProtocol.InboundPacketAssembler()

        // 1. Packet smaller than 5 bytes
        assertNull(assembler.processPacket(byteArrayOf()))
        assertNull(assembler.processPacket(byteArrayOf(0x01)))
        assertNull(assembler.processPacket(byteArrayOf(0x01, 5)))
        assertNull(assembler.processPacket(byteArrayOf(0x01, 5, 1, 1)))

        // 2. First byte not 0x01
        val invalidHeader = byteArrayOf(0x02, 6, 1, 1, 1, 10, 20, 30)
        assertNull(assembler.processPacket(invalidHeader))

        // 3. Negative / invalid receivedLength argument
        val validPkt = byteArrayOf(0x01, 6, 1, 1, 1, 10, 20, 30)
        assertNull(assembler.processPacket(validPkt, receivedLength = -1))
        assertNull(assembler.processPacket(validPkt, receivedLength = 3))
        assertNull(assembler.processPacket(validPkt, receivedLength = 100)) // exceeds data.size

        // 4. Corrupt chunkLen: data[1] <= 3
        val zeroChunkLen = byteArrayOf(0x01, 3, 1, 1, 1, 10)
        assertNull(assembler.processPacket(zeroChunkLen))
        val negChunkLen = byteArrayOf(0x01, 1, 1, 1, 1, 10)
        assertNull(assembler.processPacket(negChunkLen))

        // 5. Corrupt chunkLen: exceeds MAX_CHUNK (56)
        val overMaxChunk = byteArrayOf(0x01, 60, 1, 1, 1, 10)
        assertNull(assembler.processPacket(overMaxChunk))

        // 6. Corrupt chunkLen: header claims more bytes than actually received
        val truncatedPacket = byteArrayOf(0x01, 20, 1, 1, 1, 10, 20) // chunkLen=17, needs 22 bytes, only 7 given
        assertNull(assembler.processPacket(truncatedPacket))
    }

    @Test
    fun testTiqiaaInboundAssemblerEnvelopeCountIndexEdgeCases() {
        val assembler = TiqiaaProtocol.InboundPacketAssembler()

        // Count = 0
        val zeroCount = byteArrayOf(0x01, 6, 1, 0, 1, 10, 20, 30)
        assertNull(assembler.processPacket(zeroCount))

        // Index = 0 (1-based index required)
        val zeroIndex = byteArrayOf(0x01, 6, 1, 1, 0, 10, 20, 30)
        assertNull(assembler.processPacket(zeroIndex))

        // Index > Count
        val indexTooLarge = byteArrayOf(0x01, 6, 1, 1, 2, 10, 20, 30)
        assertNull(assembler.processPacket(indexTooLarge))

        // Envelope mismatch between chunks
        val chunk1Env1 = byteArrayOf(0x01, 6, 1, 2, 1, 10, 20, 30)
        assertNull(assembler.processPacket(chunk1Env1))
        val chunk2Env2 = byteArrayOf(0x01, 6, 2, 2, 2, 40, 50, 60) // envelope 2 != 1
        assertNull(assembler.processPacket(chunk2Env2))

        // Count mismatch between chunks
        val c1 = byteArrayOf(0x01, 6, 5, 3, 1, 10, 20, 30)
        assertNull(assembler.processPacket(c1))
        val c2MismatchedCount = byteArrayOf(0x01, 6, 5, 2, 2, 40, 50, 60) // count 2 != 3
        assertNull(assembler.processPacket(c2MismatchedCount))
    }

    @Test
    fun testTiqiaaInboundAssemblerBufferOverflowProtection() {
        // Default max buffer size is 1024 bytes.
        // Attempt to feed 25 chunks * 56 bytes = 1400 bytes.
        val assembler = TiqiaaProtocol.InboundPacketAssembler(maxBufferSize = 1024)
        val totalChunks = 25
        var overflowDetected = false

        for (i in 1..totalChunks) {
            val chunk = ByteArray(5 + 56)
            chunk[0] = 0x01
            chunk[1] = (56 + 3).toByte()
            chunk[2] = 7 // envelope
            chunk[3] = totalChunks.toByte()
            chunk[4] = i.toByte()
            for (k in 5 until chunk.size) chunk[k] = 0x55.toByte()

            val res = assembler.processPacket(chunk)
            if (i * 56 > 1024) {
                // Must return null and reset when exceeding buffer capacity
                assertNull("Buffer overflow must reject chunk $i safely", res)
                overflowDetected = true
            }
        }
        assertTrue("Overflow check must have triggered", overflowDetected)

        // After overflow reset, assembler must cleanly accept a valid single chunk
        val recoveryChunk = byteArrayOf(0x01, 5, 10, 1, 1, 99, 100)
        val recovered = assembler.processPacket(recoveryChunk)
        assertNotNull(recovered)
        assertArrayEquals(byteArrayOf(99, 100), recovered)
    }

    @Test
    fun testTiqiaaParseFrameCorruptions() {
        // Frame < 7 bytes
        assertNull(TiqiaaProtocol.parseFrame(byteArrayOf('S'.code.toByte(), 'T'.code.toByte())))

        // Corrupted headers / footers
        val badHeader = byteArrayOf('X'.code.toByte(), 'T'.code.toByte(), 1, 'L'.code.toByte(), 0, 'E'.code.toByte(), 'N'.code.toByte())
        assertNull(TiqiaaProtocol.parseFrame(badHeader))

        val badFooter = byteArrayOf('S'.code.toByte(), 'T'.code.toByte(), 1, 'L'.code.toByte(), 0, 'E'.code.toByte(), 'X'.code.toByte())
        assertNull(TiqiaaProtocol.parseFrame(badFooter))

        // Unknown type
        val unknownType = byteArrayOf('S'.code.toByte(), 'T'.code.toByte(), 1, 'Z'.code.toByte(), 0, 'E'.code.toByte(), 'N'.code.toByte())
        assertNull(TiqiaaProtocol.parseFrame(unknownType))

        // Type 'D' with corrupt timing body (first byte is space)
        val badDFrame = byteArrayOf(
            'S'.code.toByte(), 'T'.code.toByte(), 1, 'D'.code.toByte(), 0,
            0x10.toByte(), // Polarity bit 0 (space) -> invalid initial pulse!
            'E'.code.toByte(), 'N'.code.toByte()
        )
        assertNull(TiqiaaProtocol.parseFrame(badDFrame))
    }

    @Test
    fun testTiqiaaDecodeTimingPayloadEdgeCases() {
        // Less than 8 bytes
        assertEquals(0, TiqiaaProtocol.decodeTimingPayload(ByteArray(7)).size)

        // Empty body (8 bytes frame: ST, seq, D, meta, EN)
        val emptyBody = byteArrayOf('S'.code.toByte(), 'T'.code.toByte(), 1, 'D'.code.toByte(), 0, 0, 'E'.code.toByte(), 'N'.code.toByte())
        // body has only 1 byte (index 5) with value 0
        assertEquals(0, TiqiaaProtocol.decodeTimingPayload(emptyBody).size)

        // Consecutive zero-duration bytes (0x80 = mark duration 0, 0x00 = space duration 0)
        // must be ignored and not cause bogus transitions
        val zeroBytesFrame = byteArrayOf(
            'S'.code.toByte(), 'T'.code.toByte(), 1, 'D'.code.toByte(), 0,
            0x80.toByte(), // mark 0 -> ignored
            0x80.toByte(), // mark 0 -> ignored
            0x90.toByte(), // mark 16 ticks = 256 µs
            0x00.toByte(), // space 0 -> ignored
            0x10.toByte(), // space 16 ticks = 256 µs
            'E'.code.toByte(), 'N'.code.toByte()
        )
        val decoded = TiqiaaProtocol.decodeTimingPayload(zeroBytesFrame)
        assertEquals(2, decoded.size)
        assertEquals(256, decoded[0])
        assertEquals(256, decoded[1])
    }

    @Test
    fun testTiqiaaWrapPayloadBoundaryChunking() {
        // Empty payload
        val emptyFrames = TiqiaaProtocol.wrapPayload(ByteArray(0), 1)
        assertTrue(emptyFrames.isEmpty())

        // Exactly 56 bytes (1 chunk)
        val p56 = ByteArray(56) { it.toByte() }
        val frames56 = TiqiaaProtocol.wrapPayload(p56, 1)
        assertEquals(1, frames56.size)
        assertEquals(5 + 56, frames56[0].size)

        // 57 bytes (2 chunks: 56 + 1)
        val p57 = ByteArray(57) { it.toByte() }
        val frames57 = TiqiaaProtocol.wrapPayload(p57, 1)
        assertEquals(2, frames57.size)
        assertEquals(5 + 56, frames57[0].size)
        assertEquals(5 + 1, frames57[1].size)
        assertEquals(2.toByte(), frames57[0][3]) // total = 2
        assertEquals(1.toByte(), frames57[0][4]) // index = 1
        assertEquals(2.toByte(), frames57[1][4]) // index = 2
    }

    // =========================================================================
    // 3. ElkSmartProtocol Adversarial Tests
    // =========================================================================

    @Test
    fun testElkSmartPrefixAndHeaderValidation() {
        // Less than 4 bytes
        assertFalse(ElkSmartProtocol.hasPrefix(ByteArray(0), 0xFC))
        assertFalse(ElkSmartProtocol.hasPrefix(ByteArray(3), 0xFC))

        // Mismatched bytes
        val mixed = byteArrayOf(0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte(), 0xFD.toByte())
        assertFalse(ElkSmartProtocol.hasPrefix(mixed, 0xFC))

        // Authorize response size check
        assertFalse(ElkSmartProtocol.isAuthorizeResponse(ByteArray(5)))
        assertFalse(ElkSmartProtocol.isAuthorizeResponse(ByteArray(7)))

        // Learn response length header extraction
        val headerLenZero = byteArrayOf(0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0x00, 0x00)
        assertEquals(0, ElkSmartProtocol.getExpectedPayloadLength(headerLenZero))

        val headerLen65535 = byteArrayOf(0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertEquals(65535, ElkSmartProtocol.getExpectedPayloadLength(headerLen65535))

        val headerShort = byteArrayOf(0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0x01)
        assertEquals(-1, ElkSmartProtocol.getExpectedPayloadLength(headerShort))
    }

    @Test
    fun testElkSmartDecodeCarryRunsAndOverflowStress() {
        // 1. Unresolved carry at end: single 0xFF
        assertNull(ElkSmartProtocol.decodeLearnPayload(listOf(0xFF.toByte())))

        // 2. Unresolved carry at end: multiple 0xFF
        assertNull(ElkSmartProtocol.decodeLearnPayload(listOf(0x10.toByte(), 0xFF.toByte(), 0xFF.toByte())))

        // 3. Zero duration without carry: value = 0 -> decoded = 0 -> must return null
        assertNull(ElkSmartProtocol.decodeLearnPayload(listOf(0x00.toByte())))

        // 4. Zero duration WITH carry: 0xFF followed by 0x00 -> decoded = 0 * 16 + 4080 = 4080 -> valid!
        val withCarryZero = listOf(0xFF.toByte(), 0x00.toByte())
        val decodedCarryZero = ElkSmartProtocol.decodeLearnPayload(withCarryZero)
        assertNotNull(decodedCarryZero)
        val patternCarryZero = ElkSmartProtocol.decodeOpaqueFrameToPattern(decodedCarryZero!!)
        assertEquals(1, patternCarryZero.size)
        assertEquals(4080, patternCarryZero[0])

        // 5. Heavy carry stress test: 5,000 x 0xFF bytes followed by 0x20 (32)
        // carry = 5000 * 4080 = 20,400,000 µs
        // decoded = 32 * 16 + 20,400,000 = 20,400,512 µs
        val heavyPayload = ArrayList<Byte>(5001)
        repeat(5000) { heavyPayload.add(0xFF.toByte()) }
        heavyPayload.add(0x20.toByte())

        val heavyDecoded = ElkSmartProtocol.decodeLearnPayload(heavyPayload)
        assertNotNull(heavyDecoded)
        val heavyPattern = ElkSmartProtocol.decodeOpaqueFrameToPattern(heavyDecoded!!)
        assertEquals(1, heavyPattern.size)
        assertEquals(20400512, heavyPattern[0])
    }

    @Test
    fun testElkSmartDecodeOpaqueFrameEdgeCases() {
        // Empty frame
        val emptyPattern = ElkSmartProtocol.decodeOpaqueFrameToPattern(ByteArray(0))
        assertEquals(0, emptyPattern.size)

        // Non-multiple of 4 frames must throw IllegalArgumentException
        for (size in listOf(1, 2, 3, 5, 6, 7)) {
            try {
                ElkSmartProtocol.decodeOpaqueFrameToPattern(ByteArray(size))
                fail("Expected IllegalArgumentException for size $size")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message?.contains("multiple of 4") == true)
            }
        }

        // Test 32-bit LE word boundaries
        val frame = byteArrayOf(
            0xFF.toByte(), 0x00, 0x00, 0x00, // 255
            0x00, 0x01, 0x00, 0x00,         // 256
            0xFF.toByte(), 0xFF.toByte(), 0x00, 0x00, // 65535
            0x00, 0x00, 0x01, 0x00          // 65536
        )
        val pattern = ElkSmartProtocol.decodeOpaqueFrameToPattern(frame)
        assertEquals(4, pattern.size)
        assertEquals(255, pattern[0])
        assertEquals(256, pattern[1])
        assertEquals(65535, pattern[2])
        assertEquals(65536, pattern[3])
    }

    // =========================================================================
    // 4. Fuzz / Random Noise Resistance
    // =========================================================================

    @Test
    fun testTiqiaaAssemblerFuzzWithRandomBytesNeverCrashes() {
        val assembler = TiqiaaProtocol.InboundPacketAssembler()
        val rng = Random(42)

        // Send 1,000 completely random byte packets of varying lengths
        for (i in 0 until 1000) {
            val len = rng.nextInt(0, 128)
            val randomPacket = ByteArray(len)
            rng.nextBytes(randomPacket)
            try {
                assembler.processPacket(randomPacket)
            } catch (t: Throwable) {
                fail("Tiqiaa assembler crashed on random packet (iter $i, len $len): ${t.message}")
            }
        }
    }

    @Test
    fun testElkSmartDecoderFuzzWithRandomBytesNeverCrashes() {
        val rng = Random(1337)

        // Send 1,000 completely random byte lists of varying lengths
        for (i in 0 until 1000) {
            val len = rng.nextInt(0, 100)
            val randomPayload = ArrayList<Byte>(len)
            for (j in 0 until len) {
                randomPayload.add(rng.nextInt(256).toByte())
            }
            try {
                val decoded = ElkSmartProtocol.decodeLearnPayload(randomPayload)
                if (decoded != null) {
                    val pattern = ElkSmartProtocol.decodeOpaqueFrameToPattern(decoded)
                    // Sanitize should also handle whatever pattern comes out
                    IrSignalSanitizer.sanitize(38000, pattern)
                }
            } catch (t: Throwable) {
                fail("ElkSmart decoder crashed on random payload (iter $i, len $len): ${t.message}")
            }
        }
    }
}

