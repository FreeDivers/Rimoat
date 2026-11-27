package org.qinyan.rimoat.hardware.audio

import org.qinyan.rimoat.testing.AppStringsResourceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPcmBuilderTest {
    @get:org.junit.Rule val strings = AppStringsResourceRule()


    @Test
    fun testAudioPcmStereoAntiPhase() {
        val pattern = intArrayOf(1000, 1000) // 1ms mark, 1ms space
        val pcm = AudioPcmBuilder.buildStereoPcm(
            carrierHz = 38000,
            patternMicros = pattern,
            sampleRate = 48000
        )

        assertTrue(pcm.isNotEmpty())
        assertEquals(0, pcm.size % 2) // Interleaved stereo: pairs of (L, R)

        // For MARK frames, Left and Right must be exactly anti-phase: L == -R
        // Check first 10 frames (20 samples)
        var nonZeroFound = false
        for (i in 0 until 40 step 2) {
            val left = pcm[i].toInt()
            val right = pcm[i + 1].toInt()
            assertEquals("Stereo channels must be anti-phase", -left, right)
            if (left != 0) nonZeroFound = true
        }
        assertTrue("MARK frames must contain non-zero audio samples", nonZeroFound)

        // For SPACE frames (the second 1ms), samples must be silence (0)
        // 1ms mark at 48kHz = 48 frames = 96 samples
        val spaceStartSample = 96
        for (i in spaceStartSample until (spaceStartSample + 40) step 2) {
            if (i + 1 < pcm.size) {
                assertEquals(0.toShort(), pcm[i])
                assertEquals(0.toShort(), pcm[i + 1])
            }
        }
    }

    @Test
    fun testAudioPcmFrameCountCalculation() {
        // 1000us at 48000Hz -> 48 frames -> 96 stereo samples
        val pattern = intArrayOf(1000)
        val pcm = AudioPcmBuilder.buildStereoPcm(38000, pattern, 48000)
        assertEquals(96, pcm.size)
    }

    @Test
    fun testCalculatePlaybackDurationMs() {
        // 48000 frames = 96000 stereo samples = 1000 ms
        val durationMs = AudioPcmBuilder.calculatePlaybackDurationMs(96000, 48000)
        assertEquals(1000L, durationMs)
    }
    @Test fun rejectsSilentAliasedAndUnboundedSignals() {
        for (carrier in listOf(48000, 56000, 100000)) {
            assertTrue(runCatching { AudioPcmBuilder.buildStereoPcm(carrier, intArrayOf(1000, 1000)) }.isFailure)
        }
        for (pattern in listOf(intArrayOf(0, 1000), intArrayOf(Int.MIN_VALUE), intArrayOf(Int.MAX_VALUE), intArrayOf(2000000))) {
            assertTrue(runCatching { AudioPcmBuilder.buildStereoPcm(38000, pattern) }.isFailure)
        }
        org.junit.Assert.assertArrayEquals(
            AudioPcmBuilder.buildStereoPcm(38000, intArrayOf(1000, 1000)),
            AudioPcmBuilder.buildStereoPcm(38000, intArrayOf(1000, -1000)))
    }

}

