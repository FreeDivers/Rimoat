package org.qinyan.rimoat.hardware.audio

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import org.qinyan.rimoat.hardware.IrProtocolEncoder
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitter
import org.qinyan.rimoat.hardware.TransmitterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * IR transmitter for 3.5mm headphone jack accessories.
 *
 * Drives anti-parallel infrared diodes plugged into the audio jack using ultrasonic differential PCM.
 */
class AudioIrTransmitter(
    private val context: Context,
    refreshImmediately: Boolean = true
) : IrTransmitter {
    private val TAG = "AudioIrTransmitter"
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    override val type: TransmitterType = TransmitterType.AUDIO

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        if (refreshImmediately) refreshState()
    }

    fun updateHeadsetPlugged(plugged: Boolean) {
        _isReady.value = plugged
    }

    @Suppress("DEPRECATION")
    fun refreshState() {
        val am = audioManager
        if (am == null) {
            _isReady.value = false
            return
        }
        val isPlugged = am.isWiredHeadsetOn
        _isReady.value = isPlugged
    }

    override suspend fun transmit(signal: IrSignal): Result<Unit> = withContext(Dispatchers.IO) {
        refreshState()
        if (!_isReady.value) {
            return@withContext Result.failure(IllegalStateException(AppStrings.get(R.string.msg_audio_output_missing, "未检测到有线音频输出，请连接红外发射头")))
        }
        val rawSignal = when (signal) {
            is IrSignal.Raw -> signal
            is IrSignal.Nec -> IrProtocolEncoder.encodeNec(signal)
        }

        val pcm = try {
            AudioPcmBuilder.buildStereoPcm(
                carrierHz = rawSignal.frequencyHz,
                patternMicros = rawSignal.patternMicros
            )
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(e)
        }

        if (pcm.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException(AppStrings.get(R.string.msg_audio_pcm_empty, "音频 PCM 样本为空")))
        }

        val bufferSizeBytes = pcm.size * 2

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(AudioPcmBuilder.SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        var track: AudioTrack? = null

        try {
            track = AudioTrack(
                attributes,
                format,
                bufferSizeBytes,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                track.setVolume(1.0f)
            } else {
                @Suppress("DEPRECATION")
                track.setStereoVolume(1.0f, 1.0f)
            }

            val written = track.write(pcm, 0, pcm.size)
            if (written != pcm.size) {
                return@withContext Result.failure(IllegalStateException(AppStrings.get(R.string.msg_audio_write_incomplete, "音频样本未完整写入: %1\$s/%2\$s", (written).toString(), (pcm.size).toString())))
            }

            Log.d(TAG, "Playing ultrasonic IR audio: ${pcm.size} samples, duration ~${AudioPcmBuilder.calculatePlaybackDurationMs(pcm.size)}ms")
            track.play()

            val durationMs = AudioPcmBuilder.calculatePlaybackDurationMs(pcm.size)
            delay(durationMs + 60L) // Wait for playback to fully drain

            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            Log.e(TAG, "AudioTrack IR transmit error: ${e.message}", e)
            Result.failure(e)
        } finally {
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
        }
    }
}
