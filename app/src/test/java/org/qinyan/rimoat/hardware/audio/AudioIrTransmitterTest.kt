package org.qinyan.rimoat.hardware.audio

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.AcMode
import org.qinyan.rimoat.data.AcSettings
import org.qinyan.rimoat.data.AcSignalEncoder
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteCommandExecutor
import org.qinyan.rimoat.data.RemoteCommandJournal
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.i18n.AppStrings
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAudioTrack
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@Implements(AudioTrack::class)
class FakeAudioTrackShadow : ShadowAudioTrack() {
    @Implementation
    fun write(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int {
        return sizeInShorts
    }

    @Implementation
    override fun play() {
        playCount.incrementAndGet()
    }

    @Implementation
    fun stop() {
        stopCount.incrementAndGet()
    }

    @Implementation
    fun release() {
        releaseCount.incrementAndGet()
    }

    companion object {
        val playCount = AtomicInteger(0)
        val stopCount = AtomicInteger(0)
        val releaseCount = AtomicInteger(0)

        fun resetCounts() {
            playCount.set(0)
            stopCount.set(0)
            releaseCount.set(0)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [FakeAudioTrackShadow::class])
class AudioIrTransmitterTest {
    private lateinit var app: Application
    private lateinit var audioManager: AudioManager
    private lateinit var tempDir: File

    @Suppress("DEPRECATION")
    @Before
    fun setup() {
        FakeAudioTrackShadow.resetCounts()
        app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isWiredHeadsetOn = true
        shadowOf(audioManager).setStreamMaxVolume(15)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 10, 0)
        shadowOf(audioManager).setIsStreamMute(AudioManager.STREAM_MUSIC, false)
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        tempDir = File(app.cacheDir, "audio-ir-test-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        FakeAudioTrackShadow.resetCounts()
        tempDir.deleteRecursively()
    }

    private fun sampleSignal() = IrSignal.Raw(38000, intArrayOf(1000, 1000))

    @Test
    fun rejectsTransmissionWhenMediaVolumeIsZero() = runBlocking {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        val transmitter = AudioIrTransmitter(app)

        val result = transmitter.transmit(sampleSignal())

        assertTrue("Expected failure when STREAM_MUSIC volume is 0", result.isFailure)
        assertEquals(app.getString(org.qinyan.rimoat.R.string.msg_audio_volume_muted), result.exceptionOrNull()?.message)
        assertEquals("AudioTrack must not play when volume is 0", 0, FakeAudioTrackShadow.playCount.get())
        assertNull(shadowOf(audioManager).lastAudioFocusRequest)
    }

    @Test
    fun rejectsTransmissionWhenMediaStreamIsMuted() = runBlocking {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 10, 0)
        shadowOf(audioManager).setIsStreamMute(AudioManager.STREAM_MUSIC, true)
        val transmitter = AudioIrTransmitter(app)

        val result = transmitter.transmit(sampleSignal())

        assertTrue("Expected failure when STREAM_MUSIC is muted", result.isFailure)
        assertEquals(app.getString(org.qinyan.rimoat.R.string.msg_audio_volume_muted), result.exceptionOrNull()?.message)
        assertEquals("AudioTrack must not play when media stream is muted", 0, FakeAudioTrackShadow.playCount.get())
        assertNull(shadowOf(audioManager).lastAudioFocusRequest)
    }

    @Test
    fun requestsTransientExclusiveAudioFocusAndAbandonsAfterPlayback() = runBlocking {
        val transmitter = AudioIrTransmitter(app)

        val result = transmitter.transmit(sampleSignal())

        assertTrue("Expected success with audible volume and granted focus: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(1, FakeAudioTrackShadow.playCount.get())
        val shadow = shadowOf(audioManager)
        val requested = shadow.lastAudioFocusRequest
        assertNotNull("Expected audio focus request during playback", requested)
        val focusRequest = requested!!.audioFocusRequest
        assertNotNull("Expected AudioFocusRequest on API 26+", focusRequest)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, focusRequest.focusGain)
        assertEquals(focusRequest, shadow.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun failsWhenAudioFocusIsDenied() = runBlocking {
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        val transmitter = AudioIrTransmitter(app)

        val result = transmitter.transmit(sampleSignal())

        assertTrue("Expected failure when audio focus request is denied", result.isFailure)
        assertEquals(app.getString(org.qinyan.rimoat.R.string.msg_audio_focus_denied), result.exceptionOrNull()?.message)
        assertEquals("AudioTrack must not play when audio focus is denied", 0, FakeAudioTrackShadow.playCount.get())
        assertNotNull(shadowOf(audioManager).lastAudioFocusRequest)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.N], shadows = [FakeAudioTrackShadow::class])
    fun requestsAndAbandonsLegacyAudioFocusOnApi24() = runBlocking {
        val transmitter = AudioIrTransmitter(app)

        val result = transmitter.transmit(sampleSignal())

        assertTrue("Expected success on API 24: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(1, FakeAudioTrackShadow.playCount.get())
        val shadow = shadowOf(audioManager)
        val requested = shadow.lastAudioFocusRequest
        assertNotNull("Expected legacy audio focus request on API 24", requested)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, requested!!.durationHint)
        assertEquals(AudioManager.STREAM_MUSIC, requested.streamType)
        assertEquals(requested.listener, shadow.lastAbandonedAudioFocusListener)
    }

    @Test
    fun zeroVolumeRejectionDoesNotRecordSuccessOrCommitAcState() = runBlocking {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = IrTransmitterManager(app, scope)
        try {
            manager.refreshHardware()
            manager.selectTransmitterType(TransmitterType.AUDIO)
            val initialSettings = AcSettings(powerOn = true, temperature = 26, mode = AcMode.COOL)
            var currentProfile = RemoteProfile(
                id = "ac-audio",
                name = "Test AC",
                category = DeviceCategory.AC,
                acProtocolId = 27,
                acSettings = initialSettings
            )
            var committedCount = 0
            val journal = RemoteCommandJournal(File(tempDir, "journal.json"))
            val encoder = object : AcSignalEncoder {
                override suspend fun supportsAction(protocol: Int, settings: AcSettings, action: AcAction) = true
                override suspend fun protocolFor(sourceRemoteId: String) = 27
                override suspend fun supportedModes(protocol: Int) = AcMode.entries.toList()
                override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction) = listOf(sampleSignal())
            }
            val executor = RemoteCommandExecutor(
                readProfile = { currentProfile },
                commitSettings = { _, next ->
                    committedCount++
                    currentProfile = currentProfile.copy(acSettings = next)
                },
                encoder = encoder,
                route = { manager.currentTransmitterType() },
                transmit = { signals, owner, expectedType, beforeTransmission ->
                    manager.transmitWithReceipt(signals, owner, expectedType, beforeTransmission).getOrThrow()
                },
                journal = journal
            )

            val execution = runCatching {
                executor.changeAc("ac-audio") { state, _ ->
                    state.copy(temperature = state.temperature + 1) to AcAction.TEMP_UP
                }
            }

            assertTrue("Command execution should fail when media volume is 0", execution.isFailure)
            assertEquals("AC settings must not be committed on rejected audio send", 0, committedCount)
            assertEquals(initialSettings, currentProfile.acSettings)
            assertTrue("Command journal must not record a rejected send", journal.records.value.isEmpty())
            val log = manager.lastTransmitLog.value
            assertNotNull(log)
            assertEquals(TransmitterType.AUDIO, log!!.transmitterType)
            assertFalse("Last transmit log must not be marked successful", log.isSuccess)
        } finally {
            manager.release()
            scope.cancel()
        }
    }
}
