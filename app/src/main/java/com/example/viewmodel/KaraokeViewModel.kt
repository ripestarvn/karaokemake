package com.example.viewmodel

import android.app.Application
import android.os.Environment
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.LinearGradient
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaExtractor
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioSynthesizer
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class KaraokeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = ProjectRepository(database.projectDao())

    val allProjects: StateFlow<List<KaraokeProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeProject = MutableStateFlow<KaraokeProject?>(null)
    val activeProject: StateFlow<KaraokeProject?> = _activeProject.asStateFlow()

    // Editor Playback states
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playPositionMs = MutableStateFlow(0L)
    val playPositionMs: StateFlow<Long> = _playPositionMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Loaded MIDI song details
    private val _midiNotes = MutableStateFlow<List<MidiNote>>(emptyList())
    val midiNotes: StateFlow<List<MidiNote>> = _midiNotes.asStateFlow()

    // Waveform simulation data (Left/Right Peak values for Track 1)
    private val _channel1Wave = MutableStateFlow<List<Float>>(emptyList())
    val channel1Wave: StateFlow<List<Float>> = _channel1Wave.asStateFlow()

    private val _channel2Wave = MutableStateFlow<List<Float>>(emptyList())
    val channel2Wave: StateFlow<List<Float>> = _channel2Wave.asStateFlow()

    // Timeline list of aligned syllables
    private val _syncedSyllables = MutableStateFlow<List<TimedSyllable>>(emptyList())
    val syncedSyllables: StateFlow<List<TimedSyllable>> = _syncedSyllables.asStateFlow()

    // Syllables that have been imported from raw lyrics but not yet timed
    private val _syllablesQueue = MutableStateFlow<List<SyllableToSync>>(emptyList())
    val syllablesQueue: StateFlow<List<SyllableToSync>> = _syllablesQueue.asStateFlow()

    private val _currentSyncQueueIndex = MutableStateFlow(0)
    val currentSyncQueueIndex: StateFlow<Int> = _currentSyncQueueIndex.asStateFlow()

    // UI Custom settings override
    val customFontName = MutableStateFlow("SansSerif")
    val customFontSize = MutableStateFlow(28f)
    val customTextColorIdle = MutableStateFlow(0xFFFFFFFF)
    val customTextColorActive = MutableStateFlow(0xFFFF3B30)
    val customStrokeColor = MutableStateFlow(0xFF000000)
    val customStrokeWidth = MutableStateFlow(6f)
    val customShadowColor = MutableStateFlow(0xAA000000)
    val customShadowRadius = MutableStateFlow(6f)
    val customBackgroundType = MutableStateFlow("CHECKERBOARD") // CHECKERBOARD, SOLID_GREEN, BLACK, GRADIENT

    // Audio synthesizer
    private val synthesizer = AudioSynthesizer()
    private var playbackJob: Job? = null

    // For custom imported fonts (.ttf paths)
    val importedFontPaths = mutableStateListOf<File>()

    init {
        // Core initialization
        generateDefaultWaveform()
        loadUploadedFonts()
    }

    private fun loadUploadedFonts() {
        val storageDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
        val fontFiles = storageDir.listFiles { _, name -> name.endsWith(".ttf", ignoreCase = true) }
        if (fontFiles != null) {
            importedFontPaths.addAll(fontFiles)
        }
    }

    fun importCustomFont(file: File) {
        val destDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = File(destDir, file.name)
        try {
            file.copyTo(destFile, overwrite = true)
            if (!importedFontPaths.contains(destFile)) {
                importedFontPaths.add(destFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun importCustomAudio(uri: android.net.Uri) {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        var fileName = "imported_audio_${System.currentTimeMillis()}.mp3"
        val cursor = resolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    val displayName = it.getString(nameIndex)
                    if (!displayName.isNullOrEmpty()) {
                        fileName = displayName
                    }
                }
            }
        }

        val destDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = File(destDir, fileName)

        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            var durationMs = 180000L
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(destFile.absolutePath)
                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durationStr != null) {
                    durationMs = durationStr.toLong()
                }
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val currentProject = _activeProject.value
            if (currentProject != null) {
                val updated = currentProject.copy(
                    audioFileName = destFile.absolutePath,
                    audioDurationMs = durationMs,
                    lastModified = System.currentTimeMillis()
                )
                viewModelScope.launch {
                    repository.updateProject(updated)
                    _activeProject.value = updated
                    generateDefaultWaveform()
                    seekTo(0L)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateDefaultWaveform() {
        val ch1 = mutableListOf<Float>()
        val ch2 = mutableListOf<Float>()
        val random = java.util.Random(42)
        for (i in 0..500) {
            // Generate nice continuous peaks resembling double waveform channels
            val base1 = 0.3f + 0.4f * kotlin.math.sin(i / 15.0f) * kotlin.math.cos(i / 40.0f)
            val noise1 = 0.1f * random.nextFloat()
            ch1.add((base1 + noise1).coerceIn(0.1f, 0.95f))

            val base2 = 0.25f + 0.35f * kotlin.math.cos(i / 12.0f) * kotlin.math.sin(i / 35.0f)
            val noise2 = 0.1f * random.nextFloat()
            ch2.add((base2 + noise2).coerceIn(0.1f, 0.9f))
        }
        _channel1Wave.value = ch1
        _channel2Wave.value = ch2
    }

    fun selectProject(projectId: Int) {
        if (projectId < 0) {
            _activeProject.value = null
            _syncedSyllables.value = emptyList()
            return
        }
        viewModelScope.launch {
            val project = repository.getProjectById(projectId)
            if (project != null) {
                _activeProject.value = project
                _syncedSyllables.value = JsonHelper.fromJson(project.timedSyllablesJson)
                
                // Set custom options in state
                customFontName.value = project.fontName
                customFontSize.value = project.fontSize
                customTextColorIdle.value = project.textColorIdle
                customTextColorActive.value = project.textColorActive
                customStrokeColor.value = project.strokeColor
                customStrokeWidth.value = project.strokeWidth
                customShadowColor.value = project.shadowColor
                customShadowRadius.value = project.shadowRadius
                customBackgroundType.value = project.backgroundType

                // Load appropriate backing track details
                val matchedPreset = PresetSongs.SONGS.firstOrNull { it.title.equals(project.title, ignoreCase = true) }
                if (matchedPreset != null) {
                    _midiNotes.value = matchedPreset.notes
                } else {
                    _midiNotes.value = emptyList()
                }

                // Parse queue from lyrics (for untimed editing)
                parseLyricsToSyllablesQueue(project.lyricsText)
                
                // Track start from beginning
                seekTo(0L)
            }
        }
    }

    // Creating new project
    fun createAndSelectProject(title: String, artist: String, lyricsText: String, presetSong: PresetSong? = null) {
        viewModelScope.launch {
            val defaultProject = KaraokeProject(
                title = title,
                artist = artist,
                lyricsText = lyricsText,
                timedSyllablesJson = if (presetSong != null) JsonHelper.toJson(presetSong.syllables) else "[]",
                audioFileName = presetSong?.audioFileName ?: "Bèo Dạt Mây Trôi",
                audioDurationMs = presetSong?.durationMs ?: 180000L
            )
            val newId = repository.insertProject(defaultProject)
            selectProject(newId.toInt())
        }
    }

    fun deleteProject(id: Int) {
        viewModelScope.launch {
            repository.deleteProjectById(id)
            if (_activeProject.value?.id == id) {
                _activeProject.value = null
                stopPlayback()
            }
        }
    }

    fun saveActiveProject() {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            val updated = proj.copy(
                lastModified = System.currentTimeMillis(),
                fontName = customFontName.value,
                fontSize = customFontSize.value,
                textColorIdle = customTextColorIdle.value,
                textColorActive = customTextColorActive.value,
                strokeColor = customStrokeColor.value,
                strokeWidth = customStrokeWidth.value,
                shadowColor = customShadowColor.value,
                shadowRadius = customShadowRadius.value,
                backgroundType = customBackgroundType.value,
                timedSyllablesJson = JsonHelper.toJson(_syncedSyllables.value)
            )
            repository.updateProject(updated)
            _activeProject.value = updated
        }
    }

    // Playback Controls
    fun togglePlayback() {
        if (_isPlaying.value) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        _isPlaying.value = true
        val speed = _playbackSpeed.value
        val pos = _playPositionMs.value
        val duration = _activeProject.value?.audioDurationMs ?: 180000L

        val audioPath = _activeProject.value?.audioFileName
        val hasCustomAudio = audioPath != null && File(audioPath).exists()
        synthesizer.start(_midiNotes.value, pos, speed, if (hasCustomAudio) audioPath else null)

        playbackJob = viewModelScope.launch(Dispatchers.Main) {
            var lastTickSystemTime = System.currentTimeMillis()
            while (isActive && _isPlaying.value) {
                delay(16) // roughly 60 fps
                val now = System.currentTimeMillis()
                val delta = (now - lastTickSystemTime) * _playbackSpeed.value
                lastTickSystemTime = now

                val nextPos = _playPositionMs.value + delta.toLong()
                if (nextPos >= duration) {
                    _playPositionMs.value = duration
                    stopPlayback()
                } else {
                    _playPositionMs.value = nextPos
                    synthesizer.updatePosition(nextPos)
                }
            }
        }
    }

    fun stopPlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        synthesizer.stop()
    }

    fun seekTo(timeMs: Long) {
        val duration = _activeProject.value?.audioDurationMs ?: 180000L
        val bounded = timeMs.coerceIn(0L, duration)
        _playPositionMs.value = bounded
        synthesizer.updatePosition(bounded)
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        synthesizer.updateSpeed(speed)
    }

    // Parse untimed lyrics to a sync queue
    fun parseLyricsToSyllablesQueue(text: String) {
        val lines = text.split("\n")
        val queue = mutableListOf<SyllableToSync>()
        lines.forEachIndexed { lIdx, line ->
            val words = line.trim().split("\\s+".toRegex())
            words.forEachIndexed { wIdx, word ->
                if (word.isNotEmpty()) {
                    queue.add(SyllableToSync(lIdx, wIdx, word))
                }
            }
        }
        _syllablesQueue.value = queue
        
        // Match current index based on existing synced syllables if any
        val totalSynced = _syncedSyllables.value.size
        _currentSyncQueueIndex.value = totalSynced.coerceAtMost(queue.size)
    }

    // Timing builder: BIG RED BUTTON sync handler
    fun tapSyncSyllable() {
        val q = _syllablesQueue.value
        val currentIdx = _currentSyncQueueIndex.value
        if (currentIdx < q.size) {
            val syl = q[currentIdx]
            val nowMs = _playPositionMs.value
            
            // Allocate 600ms or until next syllable automatically
            val defEndTime = nowMs + 600L
            
            val newTimed = TimedSyllable(
                lineIndex = syl.lineIndex,
                syllableIndex = syl.syllableIndex,
                text = syl.text,
                startTimeMs = nowMs,
                endTimeMs = defEndTime
            )

            // Dynamic lookback to close previous syllable's end time to this syllable's start time for seamless transition!
            val updatedList = _syncedSyllables.value.toMutableList()
            if (updatedList.isNotEmpty() && updatedList.last().lineIndex == syl.lineIndex) {
                val lastSyl = updatedList.last()
                if (lastSyl.endTimeMs > nowMs) {
                    updatedList[updatedList.size - 1] = lastSyl.copy(endTimeMs = nowMs)
                }
            }

            updatedList.add(newTimed)
            _syncedSyllables.value = updatedList
            _currentSyncQueueIndex.value = currentIdx + 1
            
            // Auto save progress to model
            saveActiveProject()
        }
    }

    fun resetSynchronization() {
        _syncedSyllables.value = emptyList()
        _currentSyncQueueIndex.value = 0
        saveActiveProject()
    }

    // Manual syllable timeline edits
    fun updateSyllableTiming(lineIndex: Int, syllableIndex: Int, startMs: Long, endMs: Long) {
        val currentList = _syncedSyllables.value.toMutableList()
        val index = currentList.indexOfFirst { it.lineIndex == lineIndex && it.syllableIndex == syllableIndex }
        if (index != -1) {
            currentList[index] = currentList[index].copy(startTimeMs = startMs, endTimeMs = endMs)
            _syncedSyllables.value = currentList
            saveActiveProject()
        }
    }

    fun deleteSyllableFromTimeline(lineIndex: Int, syllableIndex: Int) {
        val currentList = _syncedSyllables.value.toMutableList()
        currentList.removeAll { it.lineIndex == lineIndex && it.syllableIndex == syllableIndex }
        _syncedSyllables.value = currentList
        saveActiveProject()
    }

    // Export subtitles as .srt format string
    fun exportToSrt(): String {
        val syllables = _syncedSyllables.value
        if (syllables.isEmpty()) return ""

        val srtBuilder = StringBuilder()
        val linesGrouped = syllables.groupBy { it.lineIndex }

        var blockIndex = 1
        linesGrouped.keys.sorted().forEach { lineIdx ->
            val sylInLine = linesGrouped[lineIdx]?.sortedBy { it.syllableIndex } ?: return@forEach
            val startTimeMs = sylInLine.first().startTimeMs
            val endTimeMs = sylInLine.last().endTimeMs
            val completeLineText = sylInLine.joinToString(" ") { it.text }

            srtBuilder.append("$blockIndex\n")
            srtBuilder.append("${formatSrtTime(startTimeMs)} --> ${formatSrtTime(endTimeMs)}\n")
            srtBuilder.append("$completeLineText\n\n")
            blockIndex++
        }
        return srtBuilder.toString()
    }

    fun saveSrtFile(): File? {
        val content = exportToSrt()
        if (content.isEmpty()) return null

        try {
            val downloadDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && !downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            val fileName = "Karaoke_${_activeProject.value?.title?.replace(" ", "_") ?: "Project"}.srt"
            val file = File(downloadDir, fileName)
            val stream = FileOutputStream(file)
            stream.write(content.toByteArray())
            stream.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    val exportProgressFlow = MutableStateFlow(0f)

    // Helper to convert ARGB image pixels to universal YUV420 semiplanar (NV12) format
    private fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize

        var r: Int
        var g: Int
        var b: Int
        var y: Int
        var u: Int
        var v: Int
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                r = (argb[index] and 0xff0000) ushr 16
                g = (argb[index] and 0xff00) ushr 8
                b = argb[index] and 0xff

                y = ((66 * r + 129 * g + 25 * b + 128) ushr 8) + 16
                u = ((-38 * r - 74 * g + 112 * b + 128) ushr 8) + 128
                v = ((112 * r - 94 * g - 18 * b + 128) ushr 8) + 128

                yuv420sp[yIndex++] = (if (y < 0) 0 else if (y > 255) 255 else y).toByte()
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (if (v < 0) 0 else if (v > 255) 255 else v).toByte()
                    yuv420sp[uvIndex++] = (if (u < 0) 0 else if (u > 255) 255 else u).toByte()
                }
                index++
            }
        }
    }

    // Helper to draw syllables line-by-line beautifully
    private fun drawSyllablesLine(
        canvas: Canvas,
        syllables: List<TimedSyllable>,
        currentTimeMs: Long,
        startX: Float,
        baseY: Float,
        fontSize: Float,
        colorIdle: Int,
        colorActive: Int,
        strokeColor: Int,
        strokeWidth: Float,
        shadowColor: Int,
        shadowRadius: Float,
        customTypeface: Typeface
    ) {
        val strokePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            color = strokeColor
            textSize = fontSize
            typeface = customTypeface
        }

        val fillPaintIdle = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = colorIdle
            textSize = fontSize
            typeface = customTypeface
            if (shadowRadius > 0) {
                setShadowLayer(shadowRadius, 2f, 2f, shadowColor)
            }
        }

        val fillPaintActive = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = colorActive
            textSize = fontSize
            typeface = customTypeface
            if (shadowRadius > 0) {
                setShadowLayer(shadowRadius, 2f, 2f, shadowColor)
            }
        }

        var currentX = startX
        syllables.forEach { syl ->
            val text = syl.text + " "
            val wordWidth = fillPaintIdle.measureText(text)

            val activePercent = when {
                currentTimeMs < syl.startTimeMs -> 0f
                currentTimeMs > syl.endTimeMs -> 1f
                else -> {
                    val total = (syl.endTimeMs - syl.startTimeMs).toFloat()
                    if (total > 0) (currentTimeMs - syl.startTimeMs) / total else 1f
                }
            }

            // Draw Idle text (Stroke, then Fill)
            canvas.drawText(text, currentX, baseY, strokePaint)
            canvas.drawText(text, currentX, baseY, fillPaintIdle)

            // Draw Active overlay if activePercent > 0
            if (activePercent > 0f) {
                canvas.save()
                canvas.clipRect(currentX, 0f, currentX + (wordWidth * activePercent), canvas.height.toFloat())
                canvas.drawText(text, currentX, baseY, strokePaint)
                canvas.drawText(text, currentX, baseY, fillPaintActive)
                canvas.restore()
            }

            currentX += wordWidth
        }
    }

    // High fidelity offline mp4 video renderer
    fun saveMp4File(): File? {
        exportProgressFlow.value = 0.01f
        val downloadDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir != null && !downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        val fileName = "Karaoke_${_activeProject.value?.title?.replace(" ", "_") ?: "Project"}.mp4"
        val file = File(downloadDir, fileName)

        // Determine correct video duration
        val syllables = _syncedSyllables.value
        val maxSylTime = if (syllables.isNotEmpty()) syllables.maxOf { it.endTimeMs } else 0L
        val defaultDuration = _activeProject.value?.audioDurationMs ?: 180000L
        val videoDurationMs = if (maxSylTime > 0) Math.min(maxSylTime + 3000L, defaultDuration) else defaultDuration

        // Extract background audio track if available
        val audioPath = _activeProject.value?.audioFileName
        val audioExtractor = MediaExtractor()
        var hasAudio = false
        var audioTrackIndexInFile = -1
        var audioFormat: MediaFormat? = null

        if (audioPath != null && File(audioPath).exists()) {
            try {
                audioExtractor.setDataSource(audioPath)
                for (i in 0 until audioExtractor.trackCount) {
                    val format = audioExtractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndexInFile = i
                        audioFormat = format
                        hasAudio = true
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                hasAudio = false
            }
        }

        var isMuxerStarted = false
        var videoTrackIndex = -1
        var audioTrackIndex = -1

        val width = 640
        val height = 360
        val fps = 20
        val frameDurationMs = 1000L / fps
        val totalFrames = videoDurationMs / frameDurationMs

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val argb = IntArray(width * height)
        val yuv = ByteArray(width * height * 3 / 2)

        try {
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (hasAudio && audioFormat != null) {
                audioTrackIndex = muxer.addTrack(audioFormat)
            }

            // Set up H264 video codec format
            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 1200000) // 1.2 Mbps is extremely beautiful for 360p
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 5000L

            // Style configuration matching current VM/custom settings
            val fontName = customFontName.value
            val fontSize = 24f // Optimized for 360p height
            val colorIdle = customTextColorIdle.value.toInt()
            val colorActive = customTextColorActive.value.toInt()
            val strokeColor = customStrokeColor.value.toInt()
            val strokeWidth = 5f
            val shadowColor = customShadowColor.value.toInt()
            val shadowRadius = 4f
            val bgType = customBackgroundType.value

            var customTypeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            if (fontName != "SansSerif" && fontName != "Serif" && fontName != "Monospace") {
                val fontDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                if (fontDir != null) {
                    val ttfFile = File(fontDir, fontName)
                    if (ttfFile.exists()) {
                        try {
                            customTypeface = Typeface.createFromFile(ttfFile)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } else {
                customTypeface = when (fontName) {
                    "Serif" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    "Monospace" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    else -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
            }

            val linesMap = syllables.groupBy { it.lineIndex }
            val allLineIndexes = linesMap.keys.sorted()

            // Main Frame Generation Code
            for (frame in 0..totalFrames) {
                val currentTimeMs = frame * frameDurationMs
                val presentationTimeUs = currentTimeMs * 1000L

                // 1. Draw Background
                when (bgType) {
                    "SOLID_GREEN" -> canvas.drawColor(android.graphics.Color.parseColor("#00B140"))
                    "BLACK" -> canvas.drawColor(android.graphics.Color.BLACK)
                    "GRADIENT" -> {
                        val lg = LinearGradient(0f, 0f, 0f, height.toFloat(),
                            android.graphics.Color.parseColor("#1F1C2C"),
                            android.graphics.Color.parseColor("#928DAB"),
                            Shader.TileMode.CLAMP
                        )
                        val p = Paint().apply { shader = lg }
                        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
                    }
                    else -> { // CHECKERBOARD
                        canvas.drawColor(android.graphics.Color.parseColor("#121212"))
                        val gridPaint = Paint().apply {
                            color = android.graphics.Color.parseColor("#1D1D1D")
                            this.setStrokeWidth(2f)
                        }
                        for (x in 0..width step 40) {
                            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), gridPaint)
                        }
                        for (y in 0..height step 40) {
                            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), gridPaint)
                        }
                    }
                }

                // 2. Draw Lyrics Subtitles Overlay
                if (allLineIndexes.isNotEmpty()) {
                    var currentLineIdx = allLineIndexes.firstOrNull { idx ->
                        val syls = linesMap[idx] ?: emptyList()
                        val lineStart = syls.firstOrNull()?.startTimeMs ?: 0L
                        val nextIdx = allLineIndexes.getOrNull(allLineIndexes.indexOf(idx) + 1)
                        val nextStart = nextIdx?.let { linesMap[it]?.firstOrNull()?.startTimeMs } ?: Long.MAX_VALUE
                        currentTimeMs >= lineStart && currentTimeMs < nextStart
                    } ?: allLineIndexes.firstOrNull() ?: 0

                    val evenLineIdx = if (currentLineIdx % 2 == 0) currentLineIdx else (currentLineIdx + 1)
                    val oddLineIdx = if (currentLineIdx % 2 == 0) (currentLineIdx + 1) else currentLineIdx

                    val evenSyllables = linesMap[evenLineIdx]?.sortedBy { it.syllableIndex }
                    val oddSyllables = linesMap[oddLineIdx]?.sortedBy { it.syllableIndex }

                    val padPrep = 3000L
                    val padPost = 2500L

                    // Even Row
                    if (evenSyllables != null) {
                        val firstStart = evenSyllables.firstOrNull()?.startTimeMs ?: 0L
                        val lastEnd = evenSyllables.lastOrNull()?.endTimeMs ?: 0L
                        if (currentTimeMs >= (firstStart - padPrep) && currentTimeMs <= (lastEnd + padPost)) {
                            drawSyllablesLine(canvas, evenSyllables, currentTimeMs, 40f, 130f, fontSize,
                                colorIdle, colorActive, strokeColor, strokeWidth, shadowColor, shadowRadius, customTypeface)
                        }
                    }

                    // Odd Row
                    if (oddSyllables != null) {
                        val firstStart = oddSyllables.firstOrNull()?.startTimeMs ?: 0L
                        val lastEnd = oddSyllables.lastOrNull()?.endTimeMs ?: 0L
                        if (currentTimeMs >= (firstStart - padPrep) && currentTimeMs <= (lastEnd + padPost)) {
                            val linePaint = Paint().apply {
                                textSize = fontSize
                                typeface = customTypeface
                            }
                            var totalWidth = 0f
                            oddSyllables.forEach { totalWidth += linePaint.measureText(it.text + " ") }
                            val startX = (width.toFloat() - 40f) - totalWidth
                            drawSyllablesLine(canvas, oddSyllables, currentTimeMs, Math.max(40f, startX), 250f, fontSize,
                                colorIdle, colorActive, strokeColor, strokeWidth, shadowColor, shadowRadius, customTypeface)
                        }
                    }
                } else {
                    val teaserPaint = Paint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.argb(128, 255, 255, 255)
                        textSize = 18f
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    canvas.drawText("[ Karaoke Video Subtitles Overlay ]", (width / 2).toFloat(), (height / 2).toFloat(), teaserPaint)
                }

                // 3. Queue frames to MediaCodec
                bitmap.getPixels(argb, 0, width, 0, 0, width, height)
                encodeYUV420SP(yuv, argb, width, height)

                var inputQueued = false
                while (!inputQueued) {
                    val inputBufferIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            inputBuffer.put(yuv)
                            codec.queueInputBuffer(inputBufferIndex, 0, yuv.size, presentationTimeUs, 0)
                            inputQueued = true
                        }
                    } else {
                        videoTrackIndex = drainOutput(codec, muxer, bufferInfo, videoTrackIndex) { isMuxerStarted = true }
                    }
                }

                // Dequeue output from encoder
                videoTrackIndex = drainOutput(codec, muxer, bufferInfo, videoTrackIndex) { isMuxerStarted = true }

                // Update Progress Flow callback
                exportProgressFlow.value = (frame.toFloat() / totalFrames).coerceIn(0f, 1f)
            }

            // Flush Codec by submitting End Of Stream
            var eosQueued = false
            val endPresentationTimeUs = videoDurationMs * 1000L
            while (!eosQueued) {
                val inputBufferIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputBufferIndex >= 0) {
                    codec.queueInputBuffer(inputBufferIndex, 0, 0, endPresentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    eosQueued = true
                } else {
                    videoTrackIndex = drainOutput(codec, muxer, bufferInfo, videoTrackIndex) { isMuxerStarted = true }
                }
            }

            // Drain remaining frames
            var isEOSFinished = false
            while (!isEOSFinished) {
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (videoTrackIndex < 0) {
                        val newFormat = codec.outputFormat
                        videoTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        isMuxerStarted = true
                    }
                } else if (outputBufferIndex >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        isEOSFinished = true
                    }
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && isMuxerStarted && videoTrackIndex >= 0) {
                        if (bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                        }
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                } else {
                    break
                }
            }

            // 4. Mux Audio Track if available (Copy original high-fidelity sample buffers)
            if (hasAudio && audioTrackIndex >= 0 && audioTrackIndexInFile >= 0) {
                audioExtractor.selectTrack(audioTrackIndexInFile)
                val audioBuffer = java.nio.ByteBuffer.allocate(1024 * 1024)
                val audioBufferInfo = MediaCodec.BufferInfo()
                while (true) {
                    val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                    if (sampleSize < 0) break
                    val presentationTimeUs = audioExtractor.sampleTime

                    // Stop copying if audio goes beyond video segment
                    if (presentationTimeUs > endPresentationTimeUs) {
                        break
                    }

                    audioBufferInfo.offset = 0
                    audioBufferInfo.size = sampleSize
                    audioBufferInfo.presentationTimeUs = presentationTimeUs
                    audioBufferInfo.flags = audioExtractor.sampleFlags

                    muxer.writeSampleData(audioTrackIndex, audioBuffer, audioBufferInfo)
                    audioExtractor.advance()
                }
            }

            exportProgressFlow.value = 1f
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {}
            try {
                if (isMuxerStarted) {
                    muxer?.stop()
                }
                muxer?.release()
            } catch (e: Exception) {}
            try {
                audioExtractor.release()
            } catch (e: Exception) {}
            bitmap.recycle()
        }
    }

    private fun drainOutput(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        trackIndex: Int,
        onMuxerStart: () -> Unit
    ): Int {
        var videoTrack = trackIndex
        var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
        while (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED || outputBufferIndex >= 0) {
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (videoTrack < 0) {
                    val newFormat = codec.outputFormat
                    videoTrack = muxer.addTrack(newFormat)
                    muxer.start()
                    onMuxerStart()
                }
            } else {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && videoTrack >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrack, outputBuffer, bufferInfo)
                    }
                }
                codec.releaseOutputBuffer(outputBufferIndex, false)
            }
            outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
        }
        return videoTrack
    }

    private fun formatSrtTime(timeMs: Long): String {
        val hrs = timeMs / 3600000
        val mins = (timeMs % 3600000) / 60000
        val secs = (timeMs % 60000) / 1000
        val ms = timeMs % 1000
        return String.format("%02d:%02d:%02d,%03d", hrs, mins, secs, ms)
    }

    override fun onCleared() {
        synthesizer.release()
        super.onCleared()
    }
}

data class SyllableToSync(
    val lineIndex: Int,
    val syllableIndex: Int,
    val text: String
)
