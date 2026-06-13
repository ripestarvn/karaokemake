package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import com.example.data.MidiNote
import kotlinx.coroutines.*
import kotlin.math.sin

class AudioSynthesizer {
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private val sampleRate = 22050
    private var synthJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var notes: List<MidiNote> = emptyList()
    @Volatile
    private var playPositionMs: Long = 0L
    @Volatile
    private var isPlaying = false
    @Volatile
    private var playbackSpeed = 1.0f

    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(2048)

    fun start(songNotes: List<MidiNote>, initialTimeMs: Long, speed: Float, audioFilePath: String? = null) {
        stop()
        notes = songNotes
        playPositionMs = initialTimeMs
        playbackSpeed = speed
        isPlaying = true

        if (audioFilePath != null && java.io.File(audioFilePath).exists()) {
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(audioFilePath)
                    prepare()
                    seekTo(initialTimeMs.toInt())
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        playbackParams = playbackParams.setSpeed(speed)
                    }
                    start()
                }
                mediaPlayer = mp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                val miniTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = miniTrack
                miniTrack.play()

                synthJob = scope.launch {
                    val shortBuffer = ShortArray(512)
                    var phase = 0.0

                    while (isActive && isPlaying) {
                        val currentPitch = getActivePitchAtTime(playPositionMs)
                        if (currentPitch != null) {
                            val freq = midiPitchToFreq(currentPitch)
                            val phaseIncrement = 2.0 * Math.PI * freq / sampleRate
                            for (i in shortBuffer.indices) {
                                // Synthesize a clean, soft square / triangle mix
                                val raw = sin(phase) + 0.3 * sin(2.0 * phase)
                                phase += phaseIncrement
                                if (phase > 2.0 * Math.PI) {
                                    phase -= 2.0 * Math.PI
                                }
                                shortBuffer[i] = (raw * 4500.0).toInt().toShort()
                            }
                        } else {
                            shortBuffer.fill(0)
                            phase = 0.0
                        }

                        try {
                            audioTrack?.write(shortBuffer, 0, shortBuffer.size)
                        } catch (e: Exception) {
                            break
                        }

                        val durationProcessedMs = (shortBuffer.size.toDouble() / sampleRate * 1000.0 * playbackSpeed).toLong()
                        playPositionMs += durationProcessedMs

                        delay(5)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateSpeed(speed: Float) {
        playbackSpeed = speed
        mediaPlayer?.let { mp ->
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    mp.playbackParams = mp.playbackParams.setSpeed(speed)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updatePosition(timeMs: Long) {
        playPositionMs = timeMs
        mediaPlayer?.let { mp ->
            try {
                if (Math.abs(mp.currentPosition - timeMs) > 150) {
                    mp.seekTo(timeMs.toInt())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        isPlaying = false
        synthJob?.cancel()
        synthJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore safely
        }
        audioTrack = null

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore safely
        }
        mediaPlayer = null
    }

    private fun getActivePitchAtTime(timeMs: Long): Int? {
        val note = notes.firstOrNull { timeMs >= it.startTimeMs && timeMs < (it.startTimeMs + it.durationMs) }
        return note?.pitch
    }

    private fun midiPitchToFreq(pitch: Int): Double {
        return 440.0 * Math.pow(2.0, (pitch - 69).toDouble() / 12.0)
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
