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
import com.example.audio.MidiParser
import com.example.data.*
import com.example.ui.util.AppLogger
import com.example.ui.util.CustomFontManager
import com.example.ui.util.RomajiConverter
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
    val customScreenPreset = MutableStateFlow("HDV 1080 (1920x1080)")
    val customLayoutMode = MutableStateFlow("Two Rows")
    val customRow1Align = MutableStateFlow("Left")
    val customRow1OffsetY = MutableStateFlow(0f)
    val customRow2Align = MutableStateFlow("Right")
    val customRow2OffsetY = MutableStateFlow(0f)
    val customStepInMs = MutableStateFlow(2000L)
    val customStepOutMs = MutableStateFlow(2000L)
    val customGlobalOffsetMs = MutableStateFlow(0L)
    val customEnableSignals = MutableStateFlow(true)
    val customSignalDotsCount = MutableStateFlow(4)
    val customSignalColor = MutableStateFlow(0xFF0000FF)
    val customSignalDurationMs = MutableStateFlow(4000L)

    // Notification channel for UI feedback
    val userNotification = MutableSharedFlow<String>()

    // Audio synthesizer
    private val synthesizer = AudioSynthesizer()
    private var playbackJob: Job? = null

    // For custom imported fonts (.ttf paths)
    val importedFontPaths = mutableStateListOf<File>()
    // For custom imported soundfonts (.sf2 paths)
    val importedSoundfontPaths = mutableStateListOf<File>()

    init {
        // Core initialization
        generateDefaultWaveform()
        loadUploadedFonts()
        loadUploadedSoundfonts()
    }

    private fun loadUploadedFonts() {
        val storageDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
        val fontFiles = storageDir.listFiles { _, name -> name.endsWith(".ttf", ignoreCase = true) }
        if (fontFiles != null) {
            importedFontPaths.addAll(fontFiles)
        }
    }

    private fun loadUploadedSoundfonts() {
        val storageDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
        val sf2Files = storageDir.listFiles { _, name -> name.endsWith(".sf2", ignoreCase = true) }
        if (sf2Files != null) {
            importedSoundfontPaths.addAll(sf2Files)
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

    fun importCustomSoundFont(uri: android.net.Uri) {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        var fileName = "imported_soundfont_${System.currentTimeMillis()}.sf2"
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

        val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = File(destDir, fileName)

        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            // RAM and File size memory safety check
            val memoryError = synthesizer.checkSoundFontMemorySafety(destFile)
            if (memoryError != null) {
                destFile.delete()
                viewModelScope.launch {
                    userNotification.emit(memoryError)
                }
                return
            }

            if (!importedSoundfontPaths.contains(destFile)) {
                importedSoundfontPaths.add(destFile)
            }
            
            // Set for active project
            selectProjectSoundfont(destFile)
        } catch (e: Exception) {
            e.printStackTrace()
            viewModelScope.launch {
                userNotification.emit("Lỗi khi nhập SoundFont: ${e.localizedMessage}")
            }
        }
    }

    fun selectProjectSoundfont(file: File) {
        val memoryError = synthesizer.checkSoundFontMemorySafety(file)
        if (memoryError != null) {
            viewModelScope.launch {
                userNotification.emit(memoryError)
            }
            return
        }
        val currentProject = _activeProject.value ?: return
        val updated = currentProject.copy(
            soundfontPath = file.absolutePath,
            lastModified = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.updateProject(updated)
            _activeProject.value = updated
            synthesizer.loadSoundFont(file)
            userNotification.emit("Đã kích hoạt SoundFont: ${file.name}")
        }
    }

    fun resetProjectSoundfont() {
        val currentProject = _activeProject.value ?: return
        val updated = currentProject.copy(
            soundfontPath = "",
            lastModified = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.updateProject(updated)
            _activeProject.value = updated
            synthesizer.loadSoundFont(null)
            userNotification.emit("Đã chuyển về tổng hợp âm thanh mặc định")
        }
    }

    fun importCustomMidiOrKar(uri: android.net.Uri) {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var displayName = "imported_midi_${System.currentTimeMillis()}"
                val cursor = resolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val name = it.getString(nameIndex)
                            if (!name.isNullOrEmpty()) {
                                displayName = name
                            }
                        }
                    }
                }

                val baseName = if (displayName.endsWith(".mid", true) || displayName.endsWith(".kar", true)) {
                    displayName.substringBeforeLast(".")
                } else {
                    displayName
                }

                val destDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return@launch
                if (!destDir.exists()) destDir.mkdirs()
                val destFile = File(destDir, "$baseName.mid")

                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val result = destFile.inputStream().use { input ->
                    MidiParser.parseMidiOrKar(input)
                }

                if (result.notes.isEmpty() && result.syllables.isEmpty()) {
                    userNotification.emit("File MIDI/KAR không hợp lệ hoặc không chứa dữ liệu nốt nhạc/lời bài hát.")
                    return@launch
                }

                val currentProject = _activeProject.value
                if (currentProject != null) {
                    val newSyllablesJson = if (result.syllables.isNotEmpty()) {
                        JsonHelper.toJson(result.syllables)
                    } else {
                        currentProject.timedSyllablesJson
                    }

                    val newLyricsText = if (result.lyricsText.isNotBlank()) {
                        result.lyricsText
                    } else {
                        currentProject.lyricsText
                    }

                    val newDuration = if (result.durationMs > 10000L) result.durationMs else currentProject.audioDurationMs

                    val updatedProject = currentProject.copy(
                        audioFileName = destFile.absolutePath,
                        lyricsText = newLyricsText,
                        timedSyllablesJson = newSyllablesJson,
                        audioDurationMs = newDuration,
                        lastModified = System.currentTimeMillis()
                    )

                    repository.updateProject(updatedProject)
                    _activeProject.value = updatedProject
                    if (result.notes.isNotEmpty()) {
                        _midiNotes.value = result.notes
                    }
                    if (result.syllables.isNotEmpty()) {
                        _syncedSyllables.value = result.syllables
                    }
                    parseLyricsToSyllablesQueue(newLyricsText)
                    seekTo(0L)

                    userNotification.emit("Đã nhập thành công file MIDI/KAR! (${result.notes.size} nốt nhạc, ${result.syllables.size} âm tiết)")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                userNotification.emit("Lỗi khi nhập file MIDI/KAR: ${e.localizedMessage}")
            }
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
                val loadedSyllables = JsonHelper.fromJson(project.timedSyllablesJson)
                _syncedSyllables.value = loadedSyllables
                AppLogger.action("Project", "Loaded project #${project.id} '${project.title}' by '${project.artist}' (${loadedSyllables.size} syllables)")
                
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
                customScreenPreset.value = project.screenPreset
                customLayoutMode.value = project.layoutMode
                customRow1Align.value = project.row1Align
                customRow1OffsetY.value = project.row1OffsetY
                customRow2Align.value = project.row2Align
                customRow2OffsetY.value = project.row2OffsetY
                customStepInMs.value = project.stepInMs
                customStepOutMs.value = project.stepOutMs
                customGlobalOffsetMs.value = project.globalOffsetMs
                customEnableSignals.value = project.enableSignals
                customSignalDotsCount.value = project.signalDotsCount
                customSignalColor.value = project.signalColor
                customSignalDurationMs.value = project.signalDurationMs

                // Load appropriate backing track details
                val matchedPreset = PresetSongs.SONGS.firstOrNull { it.title.equals(project.title, ignoreCase = true) }
                if (matchedPreset != null) {
                    _midiNotes.value = matchedPreset.notes
                } else {
                    _midiNotes.value = emptyList()
                }

                // Parse queue from lyrics (for untimed editing)
                parseLyricsToSyllablesQueue(project.lyricsText)
                
                // Load SoundFont if configured
                if (project.soundfontPath.isNotEmpty()) {
                    val sf2File = File(project.soundfontPath)
                    if (sf2File.exists()) {
                        synthesizer.loadSoundFont(sf2File)
                    }
                }

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
            AppLogger.action("Project", "Created project '$title' by '$artist' (Preset: ${presetSong?.title ?: "Custom"})")
            selectProject(newId.toInt())
        }
    }

    fun deleteProject(id: Int) {
        viewModelScope.launch {
            repository.deleteProjectById(id)
            AppLogger.action("Project", "Deleted project #$id")
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
                lyricsText = _activeProject.value?.lyricsText ?: proj.lyricsText,
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
                screenPreset = customScreenPreset.value,
                layoutMode = customLayoutMode.value,
                row1Align = customRow1Align.value,
                row1OffsetY = customRow1OffsetY.value,
                row2Align = customRow2Align.value,
                row2OffsetY = customRow2OffsetY.value,
                stepInMs = customStepInMs.value,
                stepOutMs = customStepOutMs.value,
                globalOffsetMs = customGlobalOffsetMs.value,
                enableSignals = customEnableSignals.value,
                signalDotsCount = customSignalDotsCount.value,
                signalColor = customSignalColor.value,
                signalDurationMs = customSignalDurationMs.value,
                timedSyllablesJson = JsonHelper.toJson(_syncedSyllables.value)
            )
            repository.updateProject(updated)
            _activeProject.value = updated
        }
    }

    fun updateLyricsText(newLyrics: String) {
        val currentProject = _activeProject.value ?: return
        val updated = currentProject.copy(
            lyricsText = newLyrics,
            lastModified = System.currentTimeMillis()
        )
        _activeProject.value = updated
        parseLyricsToSyllablesQueue(newLyrics)
        viewModelScope.launch {
            repository.updateProject(updated)
        }
    }

    fun formatLyricsLines() {
        val currentProject = _activeProject.value ?: return
        val lines = currentProject.lyricsText.lines()
        val formattedLines = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim().replace("\\s+".toRegex(), " ")
            if (trimmed.isNotEmpty()) {
                val capitalized = trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                formattedLines.add(capitalized)
            }
        }
        val result = formattedLines.joinToString("\n")
        updateLyricsText(result)
        viewModelScope.launch {
            userNotification.emit("Đã chuẩn hóa và căn chỉnh ${formattedLines.size} dòng lời bài hát!")
        }
    }

    fun clearLyrics() {
        updateLyricsText("")
        _syncedSyllables.value = emptyList()
        _currentSyncQueueIndex.value = 0
        saveActiveProject()
        viewModelScope.launch {
            userNotification.emit("Đã xóa trắng phần lời bài hát!")
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
                val currentMpPos = synthesizer.getCurrentPosition()
                val nextPos = if (currentMpPos != null && hasCustomAudio && currentMpPos > 0L) {
                    currentMpPos
                } else {
                    val now = System.currentTimeMillis()
                    val delta = (now - lastTickSystemTime) * _playbackSpeed.value
                    lastTickSystemTime = now
                    _playPositionMs.value + delta.toLong()
                }

                if (nextPos >= duration) {
                    _playPositionMs.value = duration
                    stopPlayback()
                } else {
                    _playPositionMs.value = nextPos
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
        synthesizer.seekTo(bounded)
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        synthesizer.updateSpeed(speed)
    }

    // Parse untimed lyrics to a sync queue
    fun parseLyricsToSyllablesQueue(text: String) {
        val lines = text.split("\n")
        val queue = mutableListOf<SyllableToSync>()
        var globalSyllableIndex = 0

        lines.forEachIndexed { lIdx, line ->
            val words = line.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
            words.forEach { word ->
                // Check if word contains hyphens '-' for syllable separation (e.g. Ma-ta, ko-ko-ro, hoo-die)
                if (word.contains("-") && word.length > 1) {
                    val parts = word.split("-").filter { it.isNotEmpty() }
                    parts.forEachIndexed { pIdx, part ->
                        val isLastPart = pIdx == parts.size - 1
                        queue.add(
                            SyllableToSync(
                                lineIndex = lIdx,
                                syllableIndex = globalSyllableIndex++,
                                text = part,
                                originalWord = word,
                                joinWithNext = !isLastPart
                            )
                        )
                    }
                } else {
                    queue.add(
                        SyllableToSync(
                            lineIndex = lIdx,
                            syllableIndex = globalSyllableIndex++,
                            text = word,
                            originalWord = word,
                            joinWithNext = false
                        )
                    )
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
                endTimeMs = defEndTime,
                joinWithNext = syl.joinWithNext
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
            
            AppLogger.action("Sync", "Synced '${syl.text}' at ${nowMs}ms (${currentIdx + 1}/${q.size})")
            // Auto save progress to model
            saveActiveProject()
        }
    }

    fun convertSyncedSyllablesToHiragana() {
        val current = _syncedSyllables.value
        if (current.isEmpty()) return
        val converted = current.map { syl ->
            syl.copy(text = RomajiConverter.toHiragana(syl.text))
        }
        _syncedSyllables.value = converted
        saveActiveProject()
        AppLogger.action("Romaji", "Converted ${current.size} synced syllables to Hiragana")
    }

    fun convertSyncedSyllablesToKatakana() {
        val current = _syncedSyllables.value
        if (current.isEmpty()) return
        val converted = current.map { syl ->
            syl.copy(text = RomajiConverter.toKatakana(syl.text))
        }
        _syncedSyllables.value = converted
        saveActiveProject()
        AppLogger.action("Romaji", "Converted ${current.size} synced syllables to Katakana")
    }

    fun applyOriginalLyricsToTimeline(originalText: String): Int {
        val current = _syncedSyllables.value
        if (current.isEmpty() || originalText.isBlank()) return 0

        val origLines = originalText.lines().filter { it.isNotBlank() }
        val currentLinesMap = current.groupBy { it.lineIndex }
        val updatedList = mutableListOf<TimedSyllable>()

        currentLinesMap.keys.sorted().forEachIndexed { lineOrder, lineIdx ->
            val sylsInLine = currentLinesMap[lineIdx]?.sortedBy { it.syllableIndex } ?: emptyList()
            val origLine = origLines.getOrNull(lineOrder)

            if (origLine != null) {
                val origWords = origLine.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
                if (origWords.size == sylsInLine.size) {
                    sylsInLine.forEachIndexed { sIdx, syl ->
                        updatedList.add(syl.copy(text = origWords[sIdx]))
                    }
                } else {
                    val origChars = origLine.replace("\\s+".toRegex(), "").map { it.toString() }
                    if (origChars.size == sylsInLine.size) {
                        sylsInLine.forEachIndexed { sIdx, syl ->
                            updatedList.add(syl.copy(text = origChars[sIdx], joinWithNext = true))
                        }
                    } else {
                        val step = (origWords.size.toFloat() / sylsInLine.size.toFloat())
                        sylsInLine.forEachIndexed { sIdx, syl ->
                            val targetWordIdx = (sIdx * step).toInt().coerceIn(0, origWords.size - 1)
                            updatedList.add(syl.copy(text = origWords[targetWordIdx]))
                        }
                    }
                }
            } else {
                updatedList.addAll(sylsInLine)
            }
        }

        _syncedSyllables.value = updatedList
        saveActiveProject()
        AppLogger.action("Lyrics", "Applied original script lyrics to timeline with preserved timing (${updatedList.size} syllables)")
        return updatedList.size
    }

    fun replaceSyncedSyllablesWithOriginalScript(originalText: String): Int = applyOriginalLyricsToTimeline(originalText)

    fun resetSynchronization() {
        _syncedSyllables.value = emptyList()
        _currentSyncQueueIndex.value = 0
        saveActiveProject()
        AppLogger.warn("Sync", "Reset all syllable timing synchronization")
    }

    // Step back 1 word to allow re-synchronization
    fun undoLastSyncSyllable() {
        val currentList = _syncedSyllables.value.toMutableList()
        if (currentList.isNotEmpty() && _currentSyncQueueIndex.value > 0) {
            val removed = currentList.removeAt(currentList.size - 1)
            _syncedSyllables.value = currentList
            _currentSyncQueueIndex.value = (_currentSyncQueueIndex.value - 1).coerceAtLeast(0)
            val targetSeek = (removed.startTimeMs - 1000L).coerceAtLeast(0L)
            seekTo(targetSeek)
            saveActiveProject()
            AppLogger.action("Sync", "Undid syllable '${removed.text}', jumped back to ${targetSeek}ms")
        }
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
            val completeLineText = buildString {
                sylInLine.forEach { syl ->
                    append(syl.text)
                    if (!syl.joinWithNext) {
                        append(" ")
                    }
                }
            }.trim()

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

    // Helper to safely write a Bitmap's ARGB pixels to android.media.Image (YUV420_888 / FlexibleYUV)
    private fun writeBitmapToImage(bitmap: Bitmap, image: android.media.Image, argbBuffer: IntArray) {
        val width = image.width
        val height = image.height
        bitmap.getPixels(argbBuffer, 0, width, 0, 0, width, height)

        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yOffset = yBuffer.position()
        val uOffset = uBuffer.position()
        val vOffset = vBuffer.position()

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride

        val yPixelStride = yPlane.pixelStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        var r: Int
        var g: Int
        var b: Int
        var y: Int
        var u: Int
        var v: Int

        // Write Y plane
        for (row in 0 until height) {
            val yRowStart = row * yRowStride
            for (col in 0 until width) {
                val pixel = argbBuffer[row * width + col]
                r = (pixel shr 16) and 0xFF
                g = (pixel shr 8) and 0xFF
                b = pixel and 0xFF

                y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuffer.put(yOffset + yRowStart + col * yPixelStride, (if (y < 0) 0 else if (y > 255) 255 else y).toByte())
            }
        }

        // Write U and V planes
        for (row in 0 until height step 2) {
            val uRowStart = (row / 2) * uRowStride
            val vRowStart = (row / 2) * vRowStride
            for (col in 0 until width step 2) {
                val pixel = argbBuffer[row * width + col]
                r = (pixel shr 16) and 0xFF
                g = (pixel shr 8) and 0xFF
                b = pixel and 0xFF

                u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                uBuffer.put(uOffset + uRowStart + (col / 2) * uPixelStride, (if (u < 0) 0 else if (u > 255) 255 else u).toByte())
                vBuffer.put(vOffset + vRowStart + (col / 2) * vPixelStride, (if (v < 0) 0 else if (v > 255) 255 else v).toByte())
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
            val text = syl.text + if (syl.joinWithNext) "" else " "
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

        val width = 1920
        val height = 1080
        val fps = 20
        val frameDurationMs = 1000L / fps
        val totalFrames = videoDurationMs / frameDurationMs

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val argb = IntArray(width * height)

        try {
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (hasAudio && audioFormat != null) {
                try {
                    audioTrackIndex = muxer.addTrack(audioFormat)
                } catch (e: Exception) {
                    e.printStackTrace()
                    audioTrackIndex = -1
                    hasAudio = false
                }
            }

            // Set up H264 video codec format in pristine 1080p
            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 8000000) // 8.0 Mbps for crisp 1080p
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 5000L

            // Proportional and customized scale calculations matching live simulation perfectly
            val scaleFactor = height.toFloat() / 360f
            val fontName = customFontName.value
            val fontSize = customFontSize.value * scaleFactor
            val colorIdle = customTextColorIdle.value.toInt()
            val colorActive = customTextColorActive.value.toInt()
            val strokeColor = customStrokeColor.value.toInt()
            val strokeWidth = customStrokeWidth.value * scaleFactor
            val shadowColor = customShadowColor.value.toInt()
            val shadowRadius = customShadowRadius.value * scaleFactor
            val bgType = customBackgroundType.value

            val customTypeface = CustomFontManager.getAndroidTypeface(fontName, getApplication())

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
                            this.setStrokeWidth(3f)
                        }
                        for (x in 0..width step 80) {
                            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), gridPaint)
                        }
                        for (y in 0..height step 80) {
                            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), gridPaint)
                        }
                    }
                }

                // 2. Draw Lyrics Subtitles Overlay (Scaled and positioned bottom-aligned)
                if (allLineIndexes.isNotEmpty()) {
                    var currentLineIdx = allLineIndexes.firstOrNull { idx ->
                        val syls = linesMap[idx] ?: emptyList()
                        val lineStart = syls.minOfOrNull { it.startTimeMs } ?: 0L
                        val lineEnd = syls.maxOfOrNull { it.endTimeMs } ?: 0L
                        currentTimeMs >= lineStart && currentTimeMs <= lineEnd
                    } ?: allLineIndexes.firstOrNull { idx ->
                        val syls = linesMap[idx] ?: emptyList()
                        val lineStart = syls.minOfOrNull { it.startTimeMs } ?: 0L
                        currentTimeMs < lineStart
                    } ?: allLineIndexes.lastOrNull() ?: 0

                    val evenLineIdx = if (currentLineIdx % 2 == 0) currentLineIdx else (currentLineIdx + 1)
                    val oddLineIdx = if (currentLineIdx % 2 == 0) (currentLineIdx + 1) else currentLineIdx

                    val evenSyllables = linesMap[evenLineIdx]?.sortedBy { it.syllableIndex }
                    val oddSyllables = linesMap[oddLineIdx]?.sortedBy { it.syllableIndex }

                    val padPrep = 3000L
                    val padPost = 2500L

                    val paddingX = 40f * scaleFactor
                    val row2Y = height.toFloat() - (55f * scaleFactor)
                    val row1Y = row2Y - (fontSize * 1.35f)

                    // Even Row (Top row, aligned left)
                    if (evenSyllables != null) {
                        val firstStart = evenSyllables.firstOrNull()?.startTimeMs ?: 0L
                        val lastEnd = evenSyllables.lastOrNull()?.endTimeMs ?: 0L
                        if (currentTimeMs >= (firstStart - padPrep) && currentTimeMs <= (lastEnd + padPost)) {
                            drawSyllablesLine(canvas, evenSyllables, currentTimeMs, paddingX, row1Y, fontSize,
                                colorIdle, colorActive, strokeColor, strokeWidth, shadowColor, shadowRadius, customTypeface)
                        }
                    }

                    // Odd Row (Bottom row, aligned right)
                    if (oddSyllables != null) {
                        val firstStart = oddSyllables.firstOrNull()?.startTimeMs ?: 0L
                        val lastEnd = oddSyllables.lastOrNull()?.endTimeMs ?: 0L
                        if (currentTimeMs >= (firstStart - padPrep) && currentTimeMs <= (lastEnd + padPost)) {
                            val linePaint = Paint().apply {
                                textSize = fontSize
                                typeface = customTypeface
                            }
                            var totalWidth = 0f
                            oddSyllables.forEach { totalWidth += linePaint.measureText(it.text + if (it.joinWithNext) "" else " ") }
                            val startX = (width.toFloat() - paddingX) - totalWidth
                            drawSyllablesLine(canvas, oddSyllables, currentTimeMs, Math.max(paddingX, startX), row2Y, fontSize,
                                colorIdle, colorActive, strokeColor, strokeWidth, shadowColor, shadowRadius, customTypeface)
                        }
                    }
                } else {
                    val teaserPaint = Paint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.argb(128, 255, 255, 255)
                        textSize = 36f
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    canvas.drawText("[ Karaoke Video Subtitles Overlay ]", (width / 2).toFloat(), (height / 2).toFloat(), teaserPaint)
                }

                // 3. Queue frames to MediaCodec utilizing Image interface
                var inputQueued = false
                while (!inputQueued) {
                    val inputBufferIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputImage = codec.getInputImage(inputBufferIndex)
                        if (inputImage != null) {
                            writeBitmapToImage(bitmap, inputImage, argb)
                            codec.queueInputBuffer(inputBufferIndex, 0, width * height * 3 / 2, presentationTimeUs, 0)
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
    val text: String,
    val originalWord: String = text,
    val joinWithNext: Boolean = false
)
