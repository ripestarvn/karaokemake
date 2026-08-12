package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import com.example.data.MidiNote
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.sin

class Sf2Sample(
    val name: String,
    val start: Int,
    val end: Int,
    val startLoop: Int,
    val endLoop: Int,
    val sampleRate: Int,
    val originalPitch: Int,
    val pitchCorrection: Int
)

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

    // SoundFont custom states
    @Volatile
    private var sf2Samples: List<Sf2Sample> = emptyList()
    @Volatile
    private var sf2PcmData: ShortArray? = null
    private var sf2File: File? = null

    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(2048)

    fun checkSoundFontMemorySafety(file: File): String? {
        if (!file.exists()) return "File SoundFont không tồn tại."
        val fileSizeMb = file.length() / (1024 * 1024)
        val maxHeapBytes = Runtime.getRuntime().maxMemory()
        val totalHeapBytes = Runtime.getRuntime().totalMemory()
        val freeHeapBytes = Runtime.getRuntime().freeMemory()
        val availableHeapBytes = maxHeapBytes - (totalHeapBytes - freeHeapBytes)
        val maxHeapMb = maxHeapBytes / (1024 * 1024)

        // Safety check:
        // A SoundFont > 50MB requires reading ~50MB byte array plus parsing ~50MB ShortArray into RAM (~100-150MB contiguous heap memory).
        // If file size > 50MB OR requires > 40% of available heap memory, refuse to load to prevent OutOfMemoryError and high RAM usage.
        if (fileSizeMb > 50 || (file.length() * 2.5) > availableHeapBytes || fileSizeMb > (maxHeapMb * 0.35)) {
            return "SoundFont quá nặng để chạy trên thiết bị (${fileSizeMb}MB). Yêu cầu quá nhiều bộ nhớ RAM nền. Đã từ chối load để tránh tràn bộ nhớ!"
        }
        return null
    }

    fun loadSoundFont(file: File?) {
        if (file == null) {
            sf2File = null
            sf2PcmData = null
            sf2Samples = emptyList()
            return
        }
        val check = checkSoundFontMemorySafety(file)
        if (check != null) {
            sf2File = null
            sf2PcmData = null
            sf2Samples = emptyList()
            return
        }
        if (sf2File == file) return
        sf2File = file
        scope.launch(Dispatchers.IO) {
            try {
                val parsed = parseSf2(file)
                if (parsed != null) {
                    sf2PcmData = parsed.first
                    sf2Samples = parsed.second
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseSf2(file: File): Pair<ShortArray, List<Sf2Sample>>? {
        if (!file.exists()) return null
        val bytes = file.readBytes()
        if (bytes.size < 12) return null
        
        if (bytes[0] != 'R'.toByte() || bytes[1] != 'I'.toByte() || bytes[2] != 'F'.toByte() || bytes[3] != 'F'.toByte()) {
            return null
        }
        
        var smplData: ShortArray? = null
        val samples = mutableListOf<Sf2Sample>()
        
        var offset = 12
        while (offset < bytes.size - 8) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = readIntLE(bytes, offset + 4)
            offset += 8
            
            if (chunkId == "LIST") {
                val listType = String(bytes, offset, 4)
                var subOffset = offset + 4
                val listEnd = offset + chunkSize
                while (subOffset < listEnd - 8) {
                    val subId = String(bytes, subOffset, 4)
                    val subSize = readIntLE(bytes, subOffset + 4)
                    subOffset += 8
                    
                    if (subId == "smpl") {
                        val numSamples = subSize / 2
                        val shorts = ShortArray(numSamples)
                        for (i in 0 until numSamples) {
                            shorts[i] = readShortLE(bytes, subOffset + i * 2)
                        }
                        smplData = shorts
                    } else if (subId == "shdr") {
                        val numHeaders = subSize / 46
                        for (i in 0 until numHeaders) {
                            val headerOffset = subOffset + i * 46
                            val nameBytes = bytes.copyOfRange(headerOffset, headerOffset + 20)
                            val name = String(nameBytes).trim { it <= ' ' || it.code == 0 }
                            val start = readIntLE(bytes, headerOffset + 20)
                            val end = readIntLE(bytes, headerOffset + 24)
                            val startLoop = readIntLE(bytes, headerOffset + 28)
                            val endLoop = readIntLE(bytes, headerOffset + 32)
                            val sRate = readIntLE(bytes, headerOffset + 36)
                            val originalPitch = bytes[headerOffset + 40].toInt() and 0xFF
                            val pitchCorrection = bytes[headerOffset + 41].toInt()
                            
                            if (end > start && sRate > 0) {
                                samples.add(Sf2Sample(name, start, end, startLoop, endLoop, sRate, originalPitch, pitchCorrection))
                            }
                        }
                    }
                    subOffset += (subSize + (subSize % 2))
                }
            }
            offset += (chunkSize + (chunkSize % 2))
        }
        
        if (smplData != null && samples.isNotEmpty()) {
            return Pair(smplData, samples)
        }
        return null
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
               ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readShortLE(bytes: ByteArray, offset: Int): Short {
        if (offset + 1 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)).toShort()
    }

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
                    var lastPitch: Int? = null
                    var samplePlaybackIndex = 0.0

                    while (isActive && isPlaying) {
                        val currentPitch = getActivePitchAtTime(playPositionMs)
                        if (currentPitch != null) {
                            val pcmData = sf2PcmData
                            val samples = sf2Samples
                            
                            val matchingSample = if (pcmData != null && samples.isNotEmpty()) {
                                samples.minByOrNull { Math.abs(it.originalPitch - currentPitch) }
                            } else {
                                null
                            }

                            if (matchingSample != null && pcmData != null) {
                                if (currentPitch != lastPitch) {
                                    samplePlaybackIndex = matchingSample.start.toDouble()
                                    lastPitch = currentPitch
                                }

                                val ratio = midiPitchToFreq(currentPitch) / midiPitchToFreq(matchingSample.originalPitch)
                                val step = (matchingSample.sampleRate.toDouble() / sampleRate) * ratio

                                for (i in shortBuffer.indices) {
                                    val idx = samplePlaybackIndex.toInt()
                                    if (idx < pcmData.size && idx < matchingSample.end) {
                                        shortBuffer[i] = pcmData[idx]
                                        samplePlaybackIndex += step
                                        
                                        if (samplePlaybackIndex >= matchingSample.end) {
                                            if (matchingSample.endLoop > matchingSample.startLoop && matchingSample.startLoop >= matchingSample.start) {
                                                samplePlaybackIndex = matchingSample.startLoop.toDouble() + (samplePlaybackIndex - matchingSample.endLoop) % (matchingSample.endLoop - matchingSample.startLoop)
                                            } else {
                                                samplePlaybackIndex = matchingSample.start.toDouble()
                                            }
                                        }
                                    } else {
                                        shortBuffer[i] = 0
                                        samplePlaybackIndex = matchingSample.start.toDouble()
                                    }
                                }
                            } else {
                                val freq = midiPitchToFreq(currentPitch)
                                val phaseIncrement = 2.0 * Math.PI * freq / sampleRate
                                for (i in shortBuffer.indices) {
                                    val raw = sin(phase) + 0.3 * sin(2.0 * phase)
                                    phase += phaseIncrement
                                    if (phase > 2.0 * Math.PI) {
                                        phase -= 2.0 * Math.PI
                                    }
                                    shortBuffer[i] = (raw * 4500.0).toInt().toShort()
                                }
                                lastPitch = null
                            }
                        } else {
                            shortBuffer.fill(0)
                            phase = 0.0
                            lastPitch = null
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
