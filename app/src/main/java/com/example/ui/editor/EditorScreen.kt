package com.example.ui.editor

import android.app.Activity
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import com.example.data.KaraokeProject
import com.example.data.PresetSongs
import com.example.data.TimedSyllable
import com.example.ui.settings.AppSettings
import com.example.ui.util.Localization
import com.example.viewmodel.KaraokeViewModel
import com.example.viewmodel.SyllableToSync
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    viewModel: KaraokeViewModel,
    appSettings: AppSettings,
    onOpenSettings: () -> Unit,
    onOpenUsageNotes: () -> Unit,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val midiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.importCustomMidiOrKar(uri)
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.importCustomAudio(uri)
            Toast.makeText(context, "Đã nhập âm thanh thành công!", Toast.LENGTH_SHORT).show()
        }
    }

    val soundfontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.importCustomSoundFont(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.userNotification.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Programmatically lock/force landscape orientation in editor, fallback on exit
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val activeProject by viewModel.activeProject.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playPositionMs by viewModel.playPositionMs.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()

    val syllablesQueue by viewModel.syllablesQueue.collectAsState()
    val currentSyncIndex by viewModel.currentSyncQueueIndex.collectAsState()
    val syncedSyllables by viewModel.syncedSyllables.collectAsState()

    val ch1Wave by viewModel.channel1Wave.collectAsState()
    val ch2Wave by viewModel.channel2Wave.collectAsState()
    val midiNotes by viewModel.midiNotes.collectAsState()
    val currentLang by appSettings.language.collectAsState()
    val showWaveform by appSettings.showWaveform.collectAsState()

    // Tab categories
    val tabs = listOf("tab_lyrics", "tab_sync", "tab_audio", "tab_layout", "tab_export")
    var activeTab by remember { mutableStateOf("tab_sync") }

    // Export dialog triggers
    var exportingState by remember { mutableStateOf<String?>(null) } // "SRT", "MP4"
    var exportProgress by remember { mutableStateOf(0f) }
    var exportedFilepath by remember { mutableStateOf<String?>(null) }

    // Manual syllable editor state
    var selectedSyllableForEdit by remember { mutableStateOf<TimedSyllable?>(null) }

    // Core layout is Landscape row splits
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- LEFT COLUMN: Video Previews + Player Bar + Timeline Trails (70% Width) ---
        Column(
            modifier = Modifier
                .weight(0.68f)
                .fillMaxHeight()
                .padding(8.dp)
        ) {
            // 1. Live Karaoke Video Preview Area (Checkerboard template background)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.48f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, Color.Black)
            ) {
                // Draws checkered transparent layout or solide colors as configured
                val bgType by viewModel.customBackgroundType.collectAsState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            when (bgType) {
                                "CHECKERBOARD" -> {
                                    val sizePx = 30f
                                    val colors = listOf(Color(0xFF8C8C8C), Color(0xFF6C6C6C))
                                    for (x in 0..this.size.width.toInt() step sizePx.toInt()) {
                                        for (y in 0..this.size.height.toInt() step sizePx.toInt()) {
                                            val colorIdx = ((x / sizePx.toInt()) + (y / sizePx.toInt())) % 2
                                            drawRect(
                                                color = colors[colorIdx],
                                                topLeft = Offset(x.toFloat(), y.toFloat()),
                                                size = Size(sizePx, sizePx)
                                            )
                                        }
                                    }
                                }
                                "SOLID_GREEN" -> drawRect(Color(0xFF00FF00))
                                "BLACK" -> drawRect(Color.Black)
                                "GRADIENT" -> {
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color(0xFF4A148C), Color(0xFF311B92))
                                        )
                                    )
                                }
                            }
                        }
                )

                // Subtitle display layer over the checkerboard background
                KaraokeSubtitlesDisplay(
                    syncedSyllables = syncedSyllables,
                    currentTimeMs = playPositionMs,
                    fontSize = viewModel.customFontSize.collectAsState().value,
                    fontName = viewModel.customFontName.collectAsState().value,
                    textColorIdle = Color(viewModel.customTextColorIdle.collectAsState().value),
                    textColorActive = Color(viewModel.customTextColorActive.collectAsState().value),
                    strokeColor = Color(viewModel.customStrokeColor.collectAsState().value),
                    strokeWidth = viewModel.customStrokeWidth.collectAsState().value,
                    shadowColor = Color(viewModel.customShadowColor.collectAsState().value),
                    shadowRadius = viewModel.customShadowRadius.collectAsState().value,
                    layoutMode = viewModel.customLayoutMode.collectAsState().value,
                    row1Align = viewModel.customRow1Align.collectAsState().value,
                    row1OffsetY = viewModel.customRow1OffsetY.collectAsState().value,
                    row2Align = viewModel.customRow2Align.collectAsState().value,
                    row2OffsetY = viewModel.customRow2OffsetY.collectAsState().value,
                    stepInMs = viewModel.customStepInMs.collectAsState().value,
                    stepOutMs = viewModel.customStepOutMs.collectAsState().value,
                    globalOffsetMs = viewModel.customGlobalOffsetMs.collectAsState().value,
                    enableSignals = viewModel.customEnableSignals.collectAsState().value,
                    signalDotsCount = viewModel.customSignalDotsCount.collectAsState().value,
                    signalColor = Color(viewModel.customSignalColor.collectAsState().value),
                    signalDurationMs = viewModel.customSignalDurationMs.collectAsState().value
                )

                // Floating project details & Back Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable {
                                viewModel.stopPlayback()
                                onBackToHome()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Localization.get("back", currentLang), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${activeProject?.title ?: "Untitled"} - ${activeProject?.artist ?: "Unknown"}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onOpenUsageNotes,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Usage Notes", modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = onOpenSettings,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color(0xFFD0BCFF)
                            ),
                            modifier = Modifier.size(32.dp).testTag("editor_settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Playback progress seek bar and control layout details (Middle row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color(0xFF2C2C2C), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { viewModel.togglePlayback() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Wave playhead time stamp metrics
                Text(
                    text = formatTimeCode(playPositionMs),
                    color = Color(0xFFD0BCFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                val duration = activeProject?.audioDurationMs ?: 180000L
                Slider(
                    value = playPositionMs.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..duration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFD0BCFF),
                        activeTrackColor = Color(0xFFD0BCFF),
                        inactiveTrackColor = Color.Gray
                    ),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formatTimeCode(duration),
                    color = Color.LightGray,
                    fontSize = 11.sp
                )

                // Master Playback speed multiplier slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.width(110.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Speed", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${String.format("%.2f", playbackSpeed)}x",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(36.dp)
                    )
                    Slider(
                        value = playbackSpeed,
                        onValueChange = { viewModel.setSpeed(it) },
                        valueRange = 0.5f..1.5f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Stereo Waveform and Piano Roll Multi-Track Timeline
            StereoWaveformTimeline(
                activeProject = activeProject,
                ch1Wave = ch1Wave,
                ch2Wave = ch2Wave,
                midiNotes = midiNotes,
                syncedSyllables = syncedSyllables,
                playPositionMs = playPositionMs,
                onSeek = { viewModel.seekTo(it) },
                onSelectSyllable = { selectedSyllableForEdit = it },
                currentLang = currentLang,
                showWaveform = showWaveform,
                onToggleWaveform = { appSettings.setShowWaveform(!showWaveform) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f)
            )
        }

        // --- RIGHT COLUMN: Tab Sidebar Panels (30% Width) ---
        Column(
            modifier = Modifier
                .weight(0.32f)
                .fillMaxHeight()
                .background(Color(0xFF252528))
                .drawBehind {
                    drawLine(
                        color = Color.Black,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = density
                    )
                }
        ) {
            // Tab Header selectors matching top elements in Image 2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(Color(0xFF1E1E20)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tabKey ->
                    val isSelected = tabKey == activeTab
                    Box(
                        modifier = Modifier
                            .clickable { activeTab = tabKey }
                            .background(if (isSelected) Color(0xFF252528) else Color(0xFF1E1E20))
                            .drawBehind {
                                if (isSelected) {
                                    drawLine(
                                        color = Color(0xFFD0BCFF),
                                        start = Offset(0f, size.height),
                                        end = Offset(size.width, size.height),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = Localization.get(tabKey, currentLang),
                            color = if (isSelected) Color.White else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tab Panels detailed content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(12.dp)
            ) {
                when (activeTab) {
                    "tab_lyrics" -> {
                        var localLyricsText by remember(activeProject?.lyricsText) {
                            mutableStateOf(activeProject?.lyricsText ?: "")
                        }
                        var lyricsViewMode by remember { mutableStateOf("TEXT") }
                        val lineCount = remember(localLyricsText) {
                            if (localLyricsText.isBlank()) 0 else localLyricsText.lines().count { it.isNotBlank() }
                        }
                        val wordCount = remember(localLyricsText) {
                            if (localLyricsText.isBlank()) 0 else localLyricsText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                        }

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Header bar with counters and submode
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "SOẠN THẢO LỜI BÀI HÁT",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD0BCFF)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF381E72), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "$lineCount dòng • $wordCount từ",
                                            color = Color(0xFFE8DEF8),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (lyricsViewMode == "TEXT") Color(0xFF6750A4) else Color.Transparent)
                                            .clickable { lyricsViewMode = "TEXT" }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text("Toàn Văn", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (lyricsViewMode == "LINES") Color(0xFF6750A4) else Color.Transparent)
                                            .clickable { lyricsViewMode = "LINES" }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text("Từng Dòng ($lineCount)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Quick Action Toolbar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.updateLyricsText(localLyricsText)
                                        Toast.makeText(context, "Đã lưu lời bài hát & phân tách từ!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759), contentColor = Color.White),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Done, contentDescription = "Lưu lời", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lưu & Phân Tách Từ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clip = clipboard?.primaryClip
                                        if (clip != null && clip.itemCount > 0) {
                                            val text = clip.getItemAt(0).text?.toString() ?: ""
                                            if (text.isNotEmpty()) {
                                                val combined = if (localLyricsText.isBlank()) text else "$localLyricsText\n$text"
                                                localLyricsText = combined
                                                viewModel.updateLyricsText(combined)
                                                Toast.makeText(context, "Đã dán lời từ bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Bộ nhớ tạm đang trống!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFF6750A4)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Dán", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Dán Lời", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.formatLyricsLines()
                                        localLyricsText = activeProject?.lyricsText ?: localLyricsText
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD0BCFF)),
                                    border = BorderStroke(1.dp, Color.Gray),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Build, contentDescription = "Chuẩn hóa", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Chuẩn Hóa", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                OutlinedButton(
                                    onClick = {
                                        localLyricsText = ""
                                        viewModel.clearLyrics()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Xóa hết", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Xóa Lời", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (lyricsViewMode == "TEXT") {
                                OutlinedTextField(
                                    value = localLyricsText,
                                    onValueChange = { newLyrics ->
                                        localLyricsText = newLyrics
                                        viewModel.updateLyricsText(newLyrics)
                                    },
                                    placeholder = {
                                        Text(
                                            "Nhập hoặc dán lời bài hát tại đây...\n• Hỗ trợ bài hát dài từ 200 - 300+ dòng không giới hạn\n• Mỗi dòng là một câu hát trong Karaoke\n• Bấm 'Lưu & Phân Tách Từ' hoặc sang tab 'Đồng bộ' để bắt đầu gõ nhịp.",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    },
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        lineHeight = 17.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFD0BCFF),
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                        unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                )
                            } else {
                                val lines = localLyricsText.lines()
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(lines.size) { index ->
                                        val line = lines[index]
                                        val wordsInLine = line.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                                            border = BorderStroke(0.5.dp, Color.DarkGray),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .background(Color(0xFF6750A4), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("${index + 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (line.isBlank()) "[ Dòng trống ]" else line,
                                                    color = if (line.isBlank()) Color.Gray else Color.White,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    "$wordsInLine từ",
                                                    color = Color.LightGray,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "tab_sync" -> {
                        // Recording / Synchronization Panel featuring the BIG RED 3D BUTTON!
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "TRÌNH ĐỒNG BỘ RHYTHM",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD0BCFF),
                                modifier = Modifier.align(Alignment.Start)
                            )

                            // Display current syncing syllables block context
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val nextSyllable = syllablesQueue.getOrNull(currentSyncIndex)
                                Text(Localization.get("waiting_word", currentLang), fontSize = 10.sp, color = Color.LightGray)
                                Text(
                                    text = nextSyllable?.text ?: "[ ${if (currentLang == Localization.Language.VN) "Đã đồng bộ hết" else "All Synced"} ]",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (nextSyllable != null) Color(0xFF4CAF50) else Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${Localization.get("progress", currentLang)}: ${currentSyncIndex}/${syllablesQueue.size} ${Localization.get("words", currentLang)}",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }

                            // THE BIG RED 3D BUTTON! Matches Image 2 & 3.
                            var isClicked by remember { mutableStateOf(false) }
                            val scale by animateFloatAsState(if (isClicked) 0.85f else 1f)

                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (isPlaying) {
                                            viewModel.tapSyncSyllable()
                                            isClicked = true
                                            scope.launch {
                                                delay(80)
                                                isClicked = false
                                            }
                                        } else {
                                            Toast
                                                .makeText(
                                                    context,
                                                    if (currentLang == Localization.Language.VN) "Hãy bấm phát nhạc trước khi đồng bộ!" else "Please press play before syncing!",
                                                    Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        }
                                    }
                                    .testTag("big_red_sync_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                // 3D bottom depth shadow layers
                                Box(
                                    modifier = Modifier
                                        .size(130.dp)
                                        .background(Color(0xFF5E0B0B), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(126.dp)
                                        .offset(y = 4.dp)
                                        .background(Color(0xFF8C0E0E), CircleShape)
                                )
                                // Active top red pill with gradient rendering
                                Box(
                                    modifier = Modifier
                                        .size(118.dp)
                                        .offset(y = if (isClicked) 4.dp else 0.dp)
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(Color(0xFFFF5252), Color(0xFFDD2C00))
                                            ),
                                            shape = CircleShape
                                        )
                                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            Localization.get("tap_rhythm", currentLang),
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Text(
                                Localization.get("sync_hint", currentLang),
                                fontSize = 10.sp,
                                color = Color.LightGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            // Sync utilities row (Undo 1 word + Reset)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.undoLastSyncSyllable()
                                        Toast.makeText(
                                            context,
                                            if (currentLang == Localization.Language.VN) "Đã lùi 1 từ & quay lại mốc thời gian!" else "Stepped back 1 word & timeline!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF6750A4),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1.2f).testTag("undo_sync_word_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Undo", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = Localization.get("undo_word", currentLang),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = { viewModel.resetSynchronization() },
                                    border = BorderStroke(1.dp, Color(0xFFEF5350)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(0.8f)
                                ) {
                                    Text(if (currentLang == Localization.Language.VN) "Reset Tất Cả" else "Reset All", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    "tab_audio" -> {
                        val importedSoundfonts = viewModel.importedSoundfontPaths

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text("Nguồn Nhạc Đang Dùng:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                                    border = BorderStroke(1.dp, Color.Gray),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Audio track", tint = Color.Green)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            val displayName = activeProject?.audioFileName?.let { name ->
                                                if (name.contains("/")) {
                                                    File(name).name
                                                } else {
                                                    name
                                                }
                                            } ?: "Sample MIDI backing file"
                                            Text(displayName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            val minutes = (activeProject?.audioDurationMs ?: 180000L) / 60000
                                            val seconds = ((activeProject?.audioDurationMs ?: 180000L) % 60000) / 1000
                                            Text("Thời lượng: ${minutes}p ${seconds}s", color = Color.LightGray, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            item {
                                Button(
                                    onClick = { midiPickerLauncher.launch(arrayOf("*/*")) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF9500),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = "Import MIDI KAR", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("NHẬP FILE MIDI / KAR (.mid, .kar)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            item {
                                Button(
                                    onClick = { audioPickerLauncher.launch(arrayOf("audio/*", "*/*")) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD0BCFF),
                                        contentColor = Color(0xFF381E72)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = "Import Audio", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("NHẬP ÂM THANH MỚI (.mp3, .wav)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { soundfontPickerLauncher.launch(arrayOf("*/*")) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF34C759),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = "Import SoundFont", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("NHẬP SOUNDFONT MỚI (.sf2)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            item {
                                Divider(color = Color.LightGray.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Nguồn Nhạc Cụ MIDI (SoundFont):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            // 1. Default system wavetable synth option
                            item {
                                val isSelected = activeProject?.soundfontPath.isNullOrEmpty()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) Color(0xFFD0BCFF).copy(alpha = 0.2f) else Color.Transparent,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { viewModel.resetProjectSoundfont() }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.resetProjectSoundfont() },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFD0BCFF))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Hệ Thống Giả Lập Mặc Định", color = Color.White, fontSize = 11.sp)
                                }
                            }

                            // 2. Custom imported SoundFont files
                            items(importedSoundfonts) { file ->
                                val isSelected = activeProject?.soundfontPath == file.absolutePath
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) Color(0xFFD0BCFF).copy(alpha = 0.2f) else Color.Transparent,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { viewModel.selectProjectSoundfont(file) }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.selectProjectSoundfont(file) },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFD0BCFF))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(file.name, color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    "tab_layout" -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Screen Preset
                            item {
                                Text("CẤU HÌNH MÀN HÌNH (SCREEN PRESET)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
                                Spacer(modifier = Modifier.height(4.dp))
                                val presets = listOf("HDV 1080 (1920x1080)", "720p HD (1280x720)", "Shorts 9:16 (1080x1920)", "4K UHD (3840x2160)")
                                val currentPreset = viewModel.customScreenPreset.collectAsState().value
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    presets.forEach { preset ->
                                        val isSel = preset == currentPreset
                                        Card(
                                            modifier = Modifier.clickable {
                                                viewModel.customScreenPreset.value = preset
                                                scope.launch { viewModel.saveActiveProject() }
                                            },
                                            border = BorderStroke(1.dp, if (isSel) Color(0xFFD0BCFF) else Color.Gray),
                                            colors = CardDefaults.cardColors(containerColor = if (isSel) Color(0xFFD0BCFF).copy(alpha = 0.25f) else Color.Transparent)
                                        ) {
                                            Text(preset, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                                        }
                                    }
                                }
                            }

                            item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

                            // Layout Mode (Chế độ hiển thị)
                            item {
                                Text("CHẾ ĐỘ BỐ CỤC (LAYOUT MODE)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
                                Spacer(modifier = Modifier.height(4.dp))
                                val modes = listOf("Two Rows" to "2 Hàng Karaoke", "One Row" to "1 Hàng Đơn", "Centered" to "Căn Giữa 2 Hàng")
                                val currentMode = viewModel.customLayoutMode.collectAsState().value
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    modes.forEach { (modeKey, modeLabel) ->
                                        val isSel = modeKey == currentMode
                                        Card(
                                            modifier = Modifier.weight(1f).clickable {
                                                viewModel.customLayoutMode.value = modeKey
                                                scope.launch { viewModel.saveActiveProject() }
                                            },
                                            border = BorderStroke(1.dp, if (isSel) Color(0xFFD0BCFF) else Color.Gray),
                                            colors = CardDefaults.cardColors(containerColor = if (isSel) Color(0xFFD0BCFF).copy(alpha = 0.25f) else Color.Transparent)
                                        ) {
                                            Text(modeLabel, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                                        }
                                    }
                                }
                            }

                            item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

                            // Row 1 Alignment & Offset Y
                            item {
                                Text("HÀNG 1 (CÂU LẺ/TOP ROW)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(4.dp))
                                val aligns = listOf("Left" to "Căn Trái", "Center" to "Căn Giữa", "Right" to "Căn Phải")
                                val currentR1Align = viewModel.customRow1Align.collectAsState().value
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    aligns.forEach { (k, label) ->
                                        val isSel = k == currentR1Align
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(if (isSel) Color(0xFFD0BCFF) else Color.DarkGray, RoundedCornerShape(4.dp))
                                                .clickable {
                                                    viewModel.customRow1Align.value = k
                                                    scope.launch { viewModel.saveActiveProject() }
                                                }
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(label, color = if (isSel) Color.Black else Color.White, fontSize = 9.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val r1Y = viewModel.customRow1OffsetY.collectAsState().value
                                Text("Vị trí Dọc (Offset Y): ${r1Y.toInt()} dp", fontSize = 10.sp, color = Color.LightGray)
                                Slider(
                                    value = r1Y,
                                    onValueChange = {
                                        viewModel.customRow1OffsetY.value = it
                                        scope.launch { viewModel.saveActiveProject() }
                                    },
                                    valueRange = -60f..60f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
                                )
                            }

                            item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

                            // Row 2 Alignment & Offset Y
                            item {
                                Text("HÀNG 2 (CÂU CHẴN/BOTTOM ROW)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(4.dp))
                                val aligns = listOf("Left" to "Căn Trái", "Center" to "Căn Giữa", "Right" to "Căn Phải")
                                val currentR2Align = viewModel.customRow2Align.collectAsState().value
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    aligns.forEach { (k, label) ->
                                        val isSel = k == currentR2Align
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(if (isSel) Color(0xFFD0BCFF) else Color.DarkGray, RoundedCornerShape(4.dp))
                                                .clickable {
                                                    viewModel.customRow2Align.value = k
                                                    scope.launch { viewModel.saveActiveProject() }
                                                }
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(label, color = if (isSel) Color.Black else Color.White, fontSize = 9.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val r2Y = viewModel.customRow2OffsetY.collectAsState().value
                                Text("Vị trí Dọc (Offset Y): ${r2Y.toInt()} dp", fontSize = 10.sp, color = Color.LightGray)
                                Slider(
                                    value = r2Y,
                                    onValueChange = {
                                        viewModel.customRow2OffsetY.value = it
                                        scope.launch { viewModel.saveActiveProject() }
                                    },
                                    valueRange = -60f..60f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
                                )
                            }

                            item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

                            // Time / Timing Options (Step In / Step Out / Global Sync Shift)
                            item {
                                Text("CĂN CHỈNH THỜI GIAN HÁT (TIME SYNC)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
                                Spacer(modifier = Modifier.height(4.dp))
                                val stepIn = viewModel.customStepInMs.collectAsState().value
                                Text("Step In (Chuẩn bị hát): ${stepIn} ms", fontSize = 10.sp, color = Color.White)
                                Slider(
                                    value = stepIn.toFloat(),
                                    onValueChange = {
                                        viewModel.customStepInMs.value = it.toLong()
                                        scope.launch { viewModel.saveActiveProject() }
                                    },
                                    valueRange = 500f..5000f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
                                )

                                val stepOut = viewModel.customStepOutMs.collectAsState().value
                                Text("Step Out (Giữ câu hát): ${stepOut} ms", fontSize = 10.sp, color = Color.White)
                                Slider(
                                    value = stepOut.toFloat(),
                                    onValueChange = {
                                        viewModel.customStepOutMs.value = it.toLong()
                                        scope.launch { viewModel.saveActiveProject() }
                                    },
                                    valueRange = 500f..5000f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
                                )

                                val offset = viewModel.customGlobalOffsetMs.collectAsState().value
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Bù trừ nhịp toàn bài: ${offset} ms", fontSize = 10.sp, color = Color.White)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Button(
                                            onClick = {
                                                viewModel.customGlobalOffsetMs.value = offset - 100L
                                                scope.launch { viewModel.saveActiveProject() }
                                            },
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) { Text("-100ms", fontSize = 8.sp) }
                                        Button(
                                            onClick = {
                                                viewModel.customGlobalOffsetMs.value = offset + 100L
                                                scope.launch { viewModel.saveActiveProject() }
                                            },
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) { Text("+100ms", fontSize = 8.sp) }
                                    }
                                }
                            }
                        }
                    }

                    "Tùy biến" -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Countdown Signals (4 Chấm tín hiệu nhịp trước câu hát)
                            item {
                                Text("TÍN HIỆU ĐẾM NGƯỢC RHYTHM (SIGNALS)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
                                Spacer(modifier = Modifier.height(4.dp))
                                val enableSig = viewModel.customEnableSignals.collectAsState().value
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Hiển thị 4 chấm đếm nhịp trước câu", fontSize = 10.sp, color = Color.White)
                                    Switch(
                                        checked = enableSig,
                                        onCheckedChange = {
                                            viewModel.customEnableSignals.value = it
                                            scope.launch { viewModel.saveActiveProject() }
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD0BCFF))
                                    )
                                }

                                if (enableSig) {
                                    val sigDuration = viewModel.customSignalDurationMs.collectAsState().value
                                    Text("Thời gian đếm ngược: ${sigDuration} ms", fontSize = 10.sp, color = Color.LightGray)
                                    Slider(
                                        value = sigDuration.toFloat(),
                                        onValueChange = {
                                            viewModel.customSignalDurationMs.value = it.toLong()
                                            scope.launch { viewModel.saveActiveProject() }
                                        },
                                        valueRange = 1500f..6000f,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
                                    )

                                    val dotsCount = viewModel.customSignalDotsCount.collectAsState().value
                                    Text("Số chấm tín hiệu: ${dotsCount} chấm", fontSize = 10.sp, color = Color.LightGray)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(3, 4, 5).forEach { count ->
                                            val isSel = count == dotsCount
                                            Box(
                                                modifier = Modifier
                                                    .background(if (isSel) Color(0xFFD0BCFF) else Color.DarkGray, RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        viewModel.customSignalDotsCount.value = count
                                                        scope.launch { viewModel.saveActiveProject() }
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("$count chấm", color = if (isSel) Color.Black else Color.White, fontSize = 9.sp)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Màu chấm sáng (Lit Color):", fontSize = 10.sp, color = Color.LightGray)
                                    val sigColors = listOf(
                                        4278190335L to "Xanh Dương",
                                        4294967040L to "Vàng Brilliant",
                                        4294901760L to "Đỏ Chói",
                                        4278255360L to "Xanh Lá"
                                    )
                                    val activeSigColor = viewModel.customSignalColor.collectAsState().value
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        sigColors.forEach { (clrLong, name) ->
                                            val isSel = activeSigColor == clrLong
                                            Card(
                                                modifier = Modifier.clickable {
                                                    viewModel.customSignalColor.value = clrLong
                                                    scope.launch { viewModel.saveActiveProject() }
                                                },
                                                colors = CardDefaults.cardColors(containerColor = Color(clrLong)),
                                                border = BorderStroke(if (isSel) 2.dp else 0.dp, Color.White)
                                            ) {
                                                Text(name, color = if (clrLong == 4294967040L) Color.Black else Color.White, fontSize = 8.sp, modifier = Modifier.padding(6.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

                            // Font & Size
                            item {
                                Text("KIỂU CHỮ & CỠ CHỮ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(4.dp))
                                val fonts = listOf("Default", "SansSerif", "Serif", "Monospace")
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    fonts.forEach { font ->
                                        val isSelected = font == viewModel.customFontName.collectAsState().value
                                        Card(
                                            modifier = Modifier.clickable {
                                                viewModel.customFontName.value = font
                                                scope.launch { viewModel.saveActiveProject() }
                                            },
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFD0BCFF) else Color.Gray),
                                            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFDE1D1D).copy(alpha = 0.2f) else Color.Transparent)
                                        ) {
                                            Text(font, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Kích Thước Chữ: ${viewModel.customFontSize.collectAsState().value.toInt()} sp", fontSize = 10.sp, color = Color.White)
                                Slider(
                                    value = viewModel.customFontSize.collectAsState().value,
                                    onValueChange = {
                                        viewModel.customFontSize.value = it
                                        scope.launch { viewModel.saveActiveProject() }
                                    },
                                    valueRange = 16f..42f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
                                )
                            }

                            item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

                            // Color Fill (Active & Idle)
                            item {
                                Text("MÀU CHỮ HÁT & CHỜ (FILL COLOR)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(4.dp))

                                Text("Màu chữ đã hát (Active):", fontSize = 10.sp, color = Color.LightGray)
                                val activeColors = listOf(
                                    0xFFFF3B30 to "Đỏ",
                                    0xFFFFFF00 to "Vàng",
                                    0xFF00FF00 to "Xanh Lá",
                                    0xFF00FFFF to "Cyan",
                                    0xFFFF00FF to "Hồng"
                                )
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    activeColors.forEach { (clr, label) ->
                                        val isSel = viewModel.customTextColorActive.collectAsState().value == clr
                                        Card(
                                            modifier = Modifier.clickable {
                                                viewModel.customTextColorActive.value = clr
                                                scope.launch { viewModel.saveActiveProject() }
                                            },
                                            colors = CardDefaults.cardColors(containerColor = Color(clr)),
                                            border = BorderStroke(if (isSel) 2.dp else 0.dp, Color.White)
                                        ) {
                                            Text(label, color = if (clr == 0xFFFFFF00) Color.Black else Color.White, fontSize = 8.sp, modifier = Modifier.padding(6.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Màu chữ chờ (Idle):", fontSize = 10.sp, color = Color.LightGray)
                                val idleColors = listOf(
                                    0xFFFFFFFF to "Trắng",
                                    0xFFAAAAAA to "Xám",
                                    0xFFFFFFE0 to "Vàng Nhạt",
                                    0xFFE0FFFF to "Cyan Nhạt"
                                )
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    idleColors.forEach { (clr, label) ->
                                        val isSel = viewModel.customTextColorIdle.collectAsState().value == clr
                                        Card(
                                            modifier = Modifier.clickable {
                                                viewModel.customTextColorIdle.value = clr
                                                scope.launch { viewModel.saveActiveProject() }
                                            },
                                            colors = CardDefaults.cardColors(containerColor = Color(clr)),
                                            border = BorderStroke(if (isSel) 2.dp else 0.dp, Color.White)
                                        ) {
                                            Text(label, color = if (clr == 0xFFFFFFFF || clr == 0xFFFFFFE0) Color.Black else Color.White, fontSize = 8.sp, modifier = Modifier.padding(6.dp))
                                        }
                                    }
                                }
                            }

                            item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

                            // Stroke & Shadow
                            item {
                                Text("VIỀN & BÓNG CHỮ (STROKE & SHADOW)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(4.dp))
                                val strokeW = viewModel.customStrokeWidth.collectAsState().value
                                Text("Độ dày viền: ${strokeW.toInt()} px", fontSize = 10.sp, color = Color.White)
                                Slider(
                                    value = strokeW,
                                    onValueChange = {
                                        viewModel.customStrokeWidth.value = it
                                        scope.launch { viewModel.saveActiveProject() }
                                    },
                                    valueRange = 0f..12f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
                                )

                                val shadowR = viewModel.customShadowRadius.collectAsState().value
                                Text("Độ lan bóng: ${shadowR.toInt()} px", fontSize = 10.sp, color = Color.White)
                                Slider(
                                    value = shadowR,
                                    onValueChange = {
                                        viewModel.customShadowRadius.value = it
                                        scope.launch { viewModel.saveActiveProject() }
                                    },
                                    valueRange = 0f..12f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
                                )
                            }

                            item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

                            // Background
                            item {
                                Text("NỀN VIDEO PREVIEW (BACKGROUND)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(4.dp))
                                val bgTypes = listOf("CHECKERBOARD" to "Caro Trong Suốt", "SOLID_GREEN" to "Phông Xanh (Key)", "BLACK" to "Đen Tuyền", "GRADIENT" to "Gradient Tím")
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    bgTypes.forEach { (bgKey, bgLabel) ->
                                        val isSelected = bgKey == viewModel.customBackgroundType.collectAsState().value
                                        Card(
                                            modifier = Modifier.clickable {
                                                viewModel.customBackgroundType.value = bgKey
                                                scope.launch { viewModel.saveActiveProject() }
                                            },
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFD0BCFF) else Color.Gray),
                                            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFDE1D1D).copy(alpha = 0.2f) else Color.Transparent)
                                        ) {
                                            Text(bgLabel, color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "tab_export" -> {
                        // High resolution outputs configuration, srt alignment
                        var fps by remember { mutableStateOf("60") }
                        var resolution by remember { mutableStateOf("1080p") }
                        var format by remember { mutableStateOf("MP4") }

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("CÀI ĐẶT VIDEO ĐẦU RA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Fps:", fontSize = 10.sp, color = Color.LightGray)
                                    Row {
                                        listOf("30", "60").forEach { item ->
                                            val isSel = item == fps
                                            Box(
                                                modifier = Modifier
                                                    .background(if (isSel) Color(0xFFD0BCFF) else Color.DarkGray, RoundedCornerShape(4.dp))
                                                    .clickable { fps = item }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(item, color = Color.White, fontSize = 10.sp)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Độ Phân Giải:", fontSize = 10.sp, color = Color.LightGray)
                                    Row {
                                        listOf("1080p", "4K").forEach { item ->
                                            val isSel = item == resolution
                                            Box(
                                                modifier = Modifier
                                                    .background(if (isSel) Color(0xFFD0BCFF) else Color.DarkGray, RoundedCornerShape(4.dp))
                                                    .clickable { resolution = item }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Text(item, color = Color.White, fontSize = 10.sp)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                    }
                                }
                            }

                            Divider(color = Color.LightGray.copy(alpha = 0.1f))

                            Button(
                                onClick = {
                                    if (syncedSyllables.isEmpty()) {
                                        Toast.makeText(context, "Chưa có lời hát nào được đồng bộ!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    exportingState = "SRT"
                                    exportProgress = 0f
                                    scope.launch {
                                        // Fast delay simulation
                                        for (i in 1..10) {
                                            delay(100)
                                            exportProgress = i / 10f
                                        }
                                        val srtFile = viewModel.saveSrtFile()
                                        exportedFilepath = srtFile?.absolutePath ?: "Thư mục Downloads"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                                modifier = Modifier.fillMaxWidth()
                             ) {
                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                     Icon(Icons.Default.List, contentDescription = "Srt", modifier = Modifier.size(16.dp))
                                     Spacer(modifier = Modifier.width(6.dp))
                                     Text("Xuất phụ đề SRT chuyên nghiệp", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                 }
                             }

                             Button(
                                onClick = {
                                    if (syncedSyllables.isEmpty()) {
                                        Toast.makeText(context, "Chưa có lời hát nào được đồng bộ!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    exportingState = "MP4"
                                    exportProgress = 0f
                                    scope.launch {
                                        // Collect real rendering progress from VM in background
                                        val progressJob = launch {
                                            viewModel.exportProgressFlow.collect { progress ->
                                                exportProgress = progress
                                            }
                                        }
                                        // Run the real media transcoder in IO thread
                                        val mp4File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            viewModel.saveMp4File()
                                        }
                                        progressJob.cancel()
                                        exportProgress = 1f
                                        exportedFilepath = mp4File?.absolutePath ?: "LỖI XUẤT PHIM"
                                        if (mp4File == null) {
                                            Toast.makeText(context, "Lỗi trích xuất định dạng MP4!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Đã xuất video karaoke thành công!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
                                modifier = Modifier.fillMaxWidth()
                             ) {
                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                     Icon(Icons.Default.PlayArrow, contentDescription = "Render", modifier = Modifier.size(16.dp))
                                     Spacer(modifier = Modifier.width(6.dp))
                                     Text("XUẤT VIDEO KARAOKE MP4", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                 }
                             }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOUGES PORTALS: Manual Syllable Fine-Timer Details Dialouge ---
    if (selectedSyllableForEdit != null) {
        val syl = selectedSyllableForEdit!!
        var sTime by remember(syl.lineIndex, syl.syllableIndex) { mutableStateOf(syl.startTimeMs) }
        var eTime by remember(syl.lineIndex, syl.syllableIndex) { mutableStateOf(syl.endTimeMs) }
        var editTxt by remember(syl.lineIndex, syl.syllableIndex) { mutableStateOf(syl.text) }

        Dialog(onDismissRequest = { selectedSyllableForEdit = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = BorderStroke(1.dp, Color(0xFFD0BCFF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.width(280.dp).padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("CHỈNH SỬA TIMELINE", fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF), fontSize = 14.sp)
                    Divider(color = Color.DarkGray)

                    OutlinedTextField(
                        value = editTxt,
                        onValueChange = { editTxt = it },
                        label = { Text("Từ/Âm tiết", color = Color.LightGray) },
                        textStyle = TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFD0BCFF))
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Bắt đầu:", color = Color.White, fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.background(Color.DarkGray, RoundedCornerShape(4.dp)).clickable { sTime = (sTime - 100).coerceAtLeast(0) }.padding(6.dp)) {
                                Text("-100ms", color = Color.White, fontSize = 9.sp)
                            }
                            Text("${sTime}ms", color = Color(0xFFD0BCFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(modifier = Modifier.background(Color.DarkGray, RoundedCornerShape(4.dp)).clickable { sTime = sTime + 100 }.padding(6.dp)) {
                                Text("+100ms", color = Color.White, fontSize = 9.sp)
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Kết thúc:", color = Color.White, fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.background(Color.DarkGray, RoundedCornerShape(4.dp)).clickable { eTime = (eTime - 100).coerceAtLeast(sTime) }.padding(6.dp)) {
                                Text("-100ms", color = Color.White, fontSize = 9.sp)
                            }
                            Text("${eTime}ms", color = Color(0xFFD0BCFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(modifier = Modifier.background(Color.DarkGray, RoundedCornerShape(4.dp)).clickable { eTime = eTime + 100 }.padding(6.dp)) {
                                Text("+100ms", color = Color.White, fontSize = 9.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.deleteSyllableFromTimeline(syl.lineIndex, syl.syllableIndex)
                                selectedSyllableForEdit = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Xóa", fontSize = 10.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                viewModel.updateSyllableTiming(syl.lineIndex, syl.syllableIndex, sTime, eTime)
                                selectedSyllableForEdit = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Lưu", fontSize = 10.sp, color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGUES PORTALS: Interactive Exporting State Progress Dialog ---
    if (exportingState != null) {
        Dialog(onDismissRequest = {}) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = BorderStroke(1.dp, Color(0xFFD0BCFF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.width(300.dp).padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val label = if (exportingState == "SRT") "Đang gói tài liệu phụ đề SRT..." else "Đang vẽ từng khung hình Karaoke (MP4)..."
                    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    LinearProgressIndicator(
                        progress = exportProgress,
                        color = Color(0xFFD0BCFF),
                        trackColor = Color.DarkGray,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "${(exportProgress * 100).toInt()}% Hoàn thành",
                        color = Color(0xFFD0BCFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (exportedFilepath != null) {
                        Divider(color = Color.DarkGray)
                        Text("Yêu cầu trích xuất thành công!", color = Color.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Đã lưu tại:\n$exportedFilepath",
                            fontSize = 9.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        )
                        Button(
                            onClick = {
                                exportingState = null
                                exportedFilepath = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF))
                        ) {
                            Text("Xác Nhận", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineTrack(
    syllables: List<TimedSyllable>,
    pxPerSecond: Float,
    onSelectSyllable: (TimedSyllable) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Color(0xFF1A1A1D))
            .drawBehind {
                drawLine(
                    color = Color.DarkGray,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = density
                )
            }
    ) {
        syllables.forEach { syl ->
            val startSec = syl.startTimeMs / 1000f
            val endSec = syl.endTimeMs / 1000f
            val durationSec = endSec - startSec

            val startX = startSec * pxPerSecond
            val width = (durationSec * pxPerSecond).coerceAtLeast(15f) // minimum width

            Box(
                modifier = Modifier
                    .offset(x = startX.dp)
                    .width(width.dp)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp, horizontal = 1.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF424242))
                    .border(1.dp, Color(0xFFD0BCFF), RoundedCornerShape(4.dp))
                    .clickable { onSelectSyllable(syl) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = syl.text,
                    color = Color(0xFFD0BCFF),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun CountdownSignalDotsRow(
    currentTimeMs: Long,
    firstStartMs: Long,
    durationMs: Long,
    dotsCount: Int,
    signalColor: Color
) {
    if (currentTimeMs in (firstStartMs - durationMs)..firstStartMs) {
        val elapsed = (currentTimeMs - (firstStartMs - durationMs)).toFloat()
        val progress = (elapsed / durationMs.toFloat()).coerceIn(0f, 1f)
        val litCount = (progress * dotsCount).toInt().coerceIn(0, dotsCount)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            for (i in 0 until dotsCount) {
                val isLit = i < litCount
                Box(
                    modifier = Modifier
                        .size(if (isLit) 12.dp else 9.dp)
                        .clip(CircleShape)
                        .background(if (isLit) signalColor else Color.Gray.copy(alpha = 0.4f))
                        .border(
                            width = 1.dp,
                            color = if (isLit) Color.White else Color.DarkGray,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

// Complex customized subtitles draw implementation
@Composable
fun KaraokeSubtitlesDisplay(
    syncedSyllables: List<TimedSyllable>,
    currentTimeMs: Long,
    fontSize: Float,
    fontName: String,
    textColorIdle: Color,
    textColorActive: Color,
    strokeColor: Color,
    strokeWidth: Float,
    shadowColor: Color,
    shadowRadius: Float,
    layoutMode: String = "Two Rows",
    row1Align: String = "Left",
    row1OffsetY: Float = 0f,
    row2Align: String = "Right",
    row2OffsetY: Float = 0f,
    stepInMs: Long = 2000L,
    stepOutMs: Long = 2000L,
    globalOffsetMs: Long = 0L,
    enableSignals: Boolean = true,
    signalDotsCount: Int = 4,
    signalColor: Color = Color.Blue,
    signalDurationMs: Long = 4000L
) {
    val effectiveTimeMs = currentTimeMs + globalOffsetMs

    fun alignToAlignment(alignStr: String): Alignment = when (alignStr.uppercase()) {
        "CENTER" -> Alignment.Center
        "RIGHT" -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val linesMap = syncedSyllables.groupBy { it.lineIndex }
            val allLineIndexes = linesMap.keys.sorted()

            if (allLineIndexes.isEmpty()) {
                Text(
                    text = "[ Karaoke Subtitles Overlay ]",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            } else {
                val currentLineIdx = allLineIndexes.firstOrNull { idx ->
                    val syls = linesMap[idx] ?: emptyList()
                    val lineStart = syls.minOfOrNull { it.startTimeMs } ?: 0L
                    val lineEnd = syls.maxOfOrNull { it.endTimeMs } ?: 0L
                    effectiveTimeMs >= lineStart && effectiveTimeMs <= lineEnd
                } ?: allLineIndexes.firstOrNull { idx ->
                    val syls = linesMap[idx] ?: emptyList()
                    val lineStart = syls.minOfOrNull { it.startTimeMs } ?: 0L
                    effectiveTimeMs < lineStart
                } ?: allLineIndexes.lastOrNull() ?: 0

                val evenLineIdx = if (currentLineIdx % 2 == 0) currentLineIdx else (currentLineIdx + 1)
                val oddLineIdx = if (currentLineIdx % 2 == 0) (currentLineIdx + 1) else currentLineIdx

                val evenSyllables = linesMap[evenLineIdx]?.sortedBy { it.syllableIndex }
                val oddSyllables = linesMap[oddLineIdx]?.sortedBy { it.syllableIndex }

                // Row 1 (Top / Even line)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = row1OffsetY.dp)
                        .heightIn(min = (fontSize * 1.5f).dp)
                        .padding(horizontal = 24.dp),
                    contentAlignment = alignToAlignment(row1Align)
                ) {
                    if (evenSyllables != null) {
                        val firstStart = evenSyllables.firstOrNull()?.startTimeMs ?: 0L
                        val lastEnd = evenSyllables.lastOrNull()?.endTimeMs ?: 0L
                        if (effectiveTimeMs >= (firstStart - stepInMs) && effectiveTimeMs <= (lastEnd + stepOutMs)) {
                            Column(horizontalAlignment = if (row1Align.equals("Center", true)) Alignment.CenterHorizontally else Alignment.Start) {
                                if (enableSignals) {
                                    CountdownSignalDotsRow(
                                        currentTimeMs = effectiveTimeMs,
                                        firstStartMs = firstStart,
                                        durationMs = signalDurationMs,
                                        dotsCount = signalDotsCount,
                                        signalColor = signalColor
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    evenSyllables.forEach { syl ->
                                        val activePercent = when {
                                            effectiveTimeMs < syl.startTimeMs -> 0f
                                            effectiveTimeMs > syl.endTimeMs -> 1f
                                            else -> {
                                                val total = (syl.endTimeMs - syl.startTimeMs).toFloat()
                                                if (total > 0) (effectiveTimeMs - syl.startTimeMs) / total else 1f
                                            }
                                        }

                                        TextWithStrokeAndShadow(
                                            text = syl.text + " ",
                                            activePercent = activePercent,
                                            fontSize = fontSize,
                                            fontName = fontName,
                                            colorIdle = textColorIdle,
                                            colorActive = textColorActive,
                                            strokeColor = strokeColor,
                                            strokeWidthPx = strokeWidth,
                                            shadowColor = shadowColor,
                                            shadowRadiusPx = shadowRadius
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Row 2 (Bottom / Odd line)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = row2OffsetY.dp)
                        .heightIn(min = (fontSize * 1.5f).dp)
                        .padding(horizontal = 24.dp),
                    contentAlignment = alignToAlignment(row2Align)
                ) {
                    if (oddSyllables != null && layoutMode != "One Row") {
                        val firstStart = oddSyllables.firstOrNull()?.startTimeMs ?: 0L
                        val lastEnd = oddSyllables.lastOrNull()?.endTimeMs ?: 0L
                        if (effectiveTimeMs >= (firstStart - stepInMs) && effectiveTimeMs <= (lastEnd + stepOutMs)) {
                            Column(horizontalAlignment = if (row2Align.equals("Center", true)) Alignment.CenterHorizontally else Alignment.End) {
                                if (enableSignals) {
                                    CountdownSignalDotsRow(
                                        currentTimeMs = effectiveTimeMs,
                                        firstStartMs = firstStart,
                                        durationMs = signalDurationMs,
                                        dotsCount = signalDotsCount,
                                        signalColor = signalColor
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    oddSyllables.forEach { syl ->
                                        val activePercent = when {
                                            effectiveTimeMs < syl.startTimeMs -> 0f
                                            effectiveTimeMs > syl.endTimeMs -> 1f
                                            else -> {
                                                val total = (syl.endTimeMs - syl.startTimeMs).toFloat()
                                                if (total > 0) (effectiveTimeMs - syl.startTimeMs) / total else 1f
                                            }
                                        }

                                        TextWithStrokeAndShadow(
                                            text = syl.text + " ",
                                            activePercent = activePercent,
                                            fontSize = fontSize,
                                            fontName = fontName,
                                            colorIdle = textColorIdle,
                                            colorActive = textColorActive,
                                            strokeColor = strokeColor,
                                            strokeWidthPx = strokeWidth,
                                            shadowColor = shadowColor,
                                            shadowRadiusPx = shadowRadius
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Standard composable displaying customized dual drawing outlines to support stroke and shadow dynamically
@Composable
fun TextWithStrokeAndShadow(
    text: String,
    activePercent: Float,
    fontSize: Float,
    fontName: String,
    colorIdle: Color,
    colorActive: Color,
    strokeColor: Color,
    strokeWidthPx: Float,
    shadowColor: Color,
    shadowRadiusPx: Float
) {
    // Custom native canvas drawing allows incredible outline details in Jetpack Compose
    Canvas(
        modifier = Modifier
            .height((fontSize * 1.6f).dp)
            .width((text.length * fontSize * 0.62f).dp)
    ) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = fontSize * density
            textAlign = android.graphics.Paint.Align.LEFT
            typeface = when (fontName) {
                "Serif" -> android.graphics.Typeface.SERIF
                "Monospace" -> android.graphics.Typeface.MONOSPACE
                "SansSerif" -> android.graphics.Typeface.SANS_SERIF
                else -> android.graphics.Typeface.DEFAULT_BOLD
            }
        }

        // Draw Shadows of text
        if (shadowRadiusPx > 0f) {
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = shadowColor.toArgb()
            drawContext.canvas.nativeCanvas.drawText(
                text,
                shadowRadiusPx * 0.6f,
                paint.textSize + shadowRadiusPx * 0.6f,
                paint
            )
        }

        // 1. Draw outer stroke border
        if (strokeWidthPx > 0f) {
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = strokeWidthPx
            paint.color = strokeColor.toArgb()
            drawContext.canvas.nativeCanvas.drawText(text, 0f, paint.textSize, paint)
        }

        // 2. Draw interior idle text fill
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = colorIdle.toArgb()
        drawContext.canvas.nativeCanvas.drawText(text, 0f, paint.textSize, paint)

        // 3. Draw highlighted portion on top flowing from left to right! (Classic Karaoke Swipe)
        if (activePercent > 0f) {
            // Measure string width
            val textWidth = paint.measureText(text)
            val clipWidth = textWidth * activePercent

            // Save canvas details to crop
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.clipRect(0f, 0f, clipWidth, paint.textSize + 20f)

            // Re-draw stroke under the crop for pristine rendering
            if (strokeWidthPx > 0f) {
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = strokeWidthPx
                paint.color = strokeColor.toArgb()
                drawContext.canvas.nativeCanvas.drawText(text, 0f, paint.textSize, paint)
            }

            paint.style = android.graphics.Paint.Style.FILL
            paint.color = colorActive.toArgb()
            drawContext.canvas.nativeCanvas.drawText(text, 0f, paint.textSize, paint)

            drawContext.canvas.nativeCanvas.restore()
        }
    }
}

// Helpers
fun formatTimeCode(ms: Long): String {
    val mins = ms / 60000
    val secs = (ms % 60000) / 1000
    val frames = (ms % 1000) / 10
    return String.format("%02d:%02d:%02d", mins, secs, frames)
}


@Composable
fun DongBoTab(
    syllablesQueue: List<SyllableToSync>,
    currentSyncIndex: Int,
    isPlaying: Boolean,
    viewModel: KaraokeViewModel,
    scope: CoroutineScope,
    context: Context
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "TRÌNH ĐỒNG BỘ RHYTHM",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD0BCFF),
            modifier = Modifier.align(Alignment.Start)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val nextSyllable = syllablesQueue.getOrNull(currentSyncIndex)
            Text("Từ đang chờ đồng bộ:", fontSize = 10.sp, color = Color.LightGray)
            Text(
                text = nextSyllable?.text ?: "[ Đã đồng bộ hết ]",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = if (nextSyllable != null) Color.Green else Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Tiến trình: ${currentSyncIndex}/${syllablesQueue.size} từ",
                fontSize = 11.sp,
                color = Color.White
            )
        }

        var isClicked by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(if (isClicked) 0.85f else 1f)

        Box(
            modifier = Modifier
                .size(140.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isPlaying) {
                        viewModel.tapSyncSyllable()
                        isClicked = true
                        scope.launch {
                            delay(80)
                            isClicked = false
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Hãy bấm phát nhạc trước khi đồng bộ!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .testTag("big_red_sync_button"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .background(Color(0xFF5E0B0B), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(126.dp)
                    .offset(y = 4.dp)
                    .background(Color(0xFF8C0E0E), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .offset(y = if (isClicked) 4.dp else 0.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF5252), Color(0xFFDD2C00))
                        ),
                        shape = CircleShape
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "BẤM THEO\nNHỊP",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Text(
            "Nhấn nút Đỏ theo nhịp nhạc để đồng bộ chữ vào timeline",
            fontSize = 10.sp,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.resetSynchronization() },
                border = BorderStroke(1.dp, Color.Red),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset Đồng Bộ", fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AmThanhTab(
    activeProject: KaraokeProject?,
    viewModel: KaraokeViewModel,
    onPickMidi: () -> Unit,
    onPickAudio: () -> Unit,
    onPickSoundfont: () -> Unit
) {
    val importedSoundfonts = viewModel.importedSoundfontPaths

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Nguồn Nhạc Đang Dùng:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, Color.Gray),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Audio track", tint = Color.Green)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        val displayName = activeProject?.audioFileName?.let { name ->
                            if (name.contains("/")) {
                                File(name).name
                            } else {
                                name
                            }
                        } ?: "Sample MIDI backing file"
                        Text(displayName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val minutes = (activeProject?.audioDurationMs ?: 180000L) / 60000
                        val seconds = ((activeProject?.audioDurationMs ?: 180000L) % 60000) / 1000
                        Text("Thời lượng: ${minutes}p ${seconds}s", color = Color.LightGray, fontSize = 10.sp)
                    }
                }
            }
        }

        item {
            Button(
                onClick = onPickMidi,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9500),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Import MIDI KAR", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("NHẬP FILE MIDI / KAR (.mid, .kar)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Button(
                onClick = onPickAudio,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD0BCFF),
                    contentColor = Color(0xFF381E72)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Import Audio", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("NHẬP ÂM THANH MỚI (.mp3, .wav)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onPickSoundfont,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF34C759),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Import SoundFont", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("NHẬP SOUNDFONT MỚI (.sf2)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Divider(color = Color.LightGray.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(4.dp))
            Text("Nguồn Nhạc Cụ MIDI (SoundFont):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            val isSelected = activeProject?.soundfontPath.isNullOrEmpty()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) Color(0xFFD0BCFF).copy(alpha = 0.2f) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { viewModel.resetProjectSoundfont() }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { viewModel.resetProjectSoundfont() },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFD0BCFF))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Hệ Thống Giả Lập Mặc Định", color = Color.White, fontSize = 11.sp)
            }
        }

        items(importedSoundfonts) { file ->
            val isSelected = activeProject?.soundfontPath == file.absolutePath
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) Color(0xFFD0BCFF).copy(alpha = 0.2f) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { viewModel.selectProjectSoundfont(file) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { viewModel.selectProjectSoundfont(file) },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFD0BCFF))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(file.name, color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun LayoutAndTimeTab(
    viewModel: KaraokeViewModel,
    scope: CoroutineScope
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("CẤU HÌNH MÀN HÌNH (SCREEN PRESET)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
            Spacer(modifier = Modifier.height(4.dp))
            val presets = listOf("HDV 1080 (1920x1080)", "720p HD (1280x720)", "Shorts 9:16 (1080x1920)", "4K UHD (3840x2160)")
            val currentPreset = viewModel.customScreenPreset.collectAsState().value
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { preset ->
                    val isSel = preset == currentPreset
                    Card(
                        modifier = Modifier.clickable {
                            viewModel.customScreenPreset.value = preset
                            scope.launch { viewModel.saveActiveProject() }
                        },
                        border = BorderStroke(1.dp, if (isSel) Color(0xFFD0BCFF) else Color.Gray),
                        colors = CardDefaults.cardColors(containerColor = if (isSel) Color(0xFFD0BCFF).copy(alpha = 0.25f) else Color.Transparent)
                    ) {
                        Text(preset, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                }
            }
        }

        item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

        item {
            Text("CHẾ ĐỘ BỐ CỤC (LAYOUT MODE)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
            Spacer(modifier = Modifier.height(4.dp))
            val modes = listOf("Two Rows" to "2 Hàng Karaoke", "One Row" to "1 Hàng Đơn", "Centered" to "Căn Giữa 2 Hàng")
            val currentMode = viewModel.customLayoutMode.collectAsState().value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                modes.forEach { (modeKey, modeLabel) ->
                    val isSel = modeKey == currentMode
                    Card(
                        modifier = Modifier.weight(1f).clickable {
                            viewModel.customLayoutMode.value = modeKey
                            scope.launch { viewModel.saveActiveProject() }
                        },
                        border = BorderStroke(1.dp, if (isSel) Color(0xFFD0BCFF) else Color.Gray),
                        colors = CardDefaults.cardColors(containerColor = if (isSel) Color(0xFFD0BCFF).copy(alpha = 0.25f) else Color.Transparent)
                    ) {
                        Text(modeLabel, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    }
                }
            }
        }

        item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

        item {
            Text("HÀNG 1 (CÂU LẺ/TOP ROW)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            val aligns = listOf("Left" to "Căn Trái", "Center" to "Căn Giữa", "Right" to "Căn Phải")
            val currentR1Align = viewModel.customRow1Align.collectAsState().value
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                aligns.forEach { (k, label) ->
                    val isSel = k == currentR1Align
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isSel) Color(0xFFD0BCFF) else Color.DarkGray, RoundedCornerShape(4.dp))
                            .clickable {
                                viewModel.customRow1Align.value = k
                                scope.launch { viewModel.saveActiveProject() }
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (isSel) Color.Black else Color.White, fontSize = 9.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val r1Y = viewModel.customRow1OffsetY.collectAsState().value
            Text("Vị trí Dọc (Offset Y): ${r1Y.toInt()} dp", fontSize = 10.sp, color = Color.LightGray)
            Slider(
                value = r1Y,
                onValueChange = {
                    viewModel.customRow1OffsetY.value = it
                    scope.launch { viewModel.saveActiveProject() }
                },
                valueRange = -60f..60f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
            )
        }

        item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

        item {
            Text("HÀNG 2 (CÂU CHẴN/BOTTOM ROW)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            val aligns = listOf("Left" to "Căn Trái", "Center" to "Căn Giữa", "Right" to "Căn Phải")
            val currentR2Align = viewModel.customRow2Align.collectAsState().value
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                aligns.forEach { (k, label) ->
                    val isSel = k == currentR2Align
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isSel) Color(0xFFD0BCFF) else Color.DarkGray, RoundedCornerShape(4.dp))
                            .clickable {
                                viewModel.customRow2Align.value = k
                                scope.launch { viewModel.saveActiveProject() }
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (isSel) Color.Black else Color.White, fontSize = 9.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val r2Y = viewModel.customRow2OffsetY.collectAsState().value
            Text("Vị trí Dọc (Offset Y): ${r2Y.toInt()} dp", fontSize = 10.sp, color = Color.LightGray)
            Slider(
                value = r2Y,
                onValueChange = {
                    viewModel.customRow2OffsetY.value = it
                    scope.launch { viewModel.saveActiveProject() }
                },
                valueRange = -60f..60f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
            )
        }

        item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

        item {
            Text("CĂN CHỈNH THỜI GIAN HÁT (TIME SYNC)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
            Spacer(modifier = Modifier.height(4.dp))
            val stepIn = viewModel.customStepInMs.collectAsState().value
            Text("Step In (Chuẩn bị hát): ${stepIn} ms", fontSize = 10.sp, color = Color.White)
            Slider(
                value = stepIn.toFloat(),
                onValueChange = {
                    viewModel.customStepInMs.value = it.toLong()
                    scope.launch { viewModel.saveActiveProject() }
                },
                valueRange = 500f..5000f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
            )

            val stepOut = viewModel.customStepOutMs.collectAsState().value
            Text("Step Out (Giữ câu hát): ${stepOut} ms", fontSize = 10.sp, color = Color.White)
            Slider(
                value = stepOut.toFloat(),
                onValueChange = {
                    viewModel.customStepOutMs.value = it.toLong()
                    scope.launch { viewModel.saveActiveProject() }
                },
                valueRange = 500f..5000f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
            )

            val offset = viewModel.customGlobalOffsetMs.collectAsState().value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bù trừ nhịp toàn bài: ${offset} ms", fontSize = 10.sp, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = {
                            viewModel.customGlobalOffsetMs.value = offset - 100L
                            scope.launch { viewModel.saveActiveProject() }
                        },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("-100ms", fontSize = 8.sp) }
                    Button(
                        onClick = {
                            viewModel.customGlobalOffsetMs.value = offset + 100L
                            scope.launch { viewModel.saveActiveProject() }
                        },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("+100ms", fontSize = 8.sp) }
                }
            }
        }
    }
}

@Composable
fun TuyBienTab(
    viewModel: KaraokeViewModel,
    scope: CoroutineScope
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("TÍN HIỆU ĐẾM NGƯỢC RHYTHM (SIGNALS)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
            Spacer(modifier = Modifier.height(4.dp))
            val enableSig = viewModel.customEnableSignals.collectAsState().value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hiển thị 4 chấm đếm nhịp trước câu", fontSize = 10.sp, color = Color.White)
                Switch(
                    checked = enableSig,
                    onCheckedChange = {
                        viewModel.customEnableSignals.value = it
                        scope.launch { viewModel.saveActiveProject() }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD0BCFF))
                )
            }

            if (enableSig) {
                val sigDuration = viewModel.customSignalDurationMs.collectAsState().value
                Text("Thời gian đếm ngược: ${sigDuration} ms", fontSize = 10.sp, color = Color.LightGray)
                Slider(
                    value = sigDuration.toFloat(),
                    onValueChange = {
                        viewModel.customSignalDurationMs.value = it.toLong()
                        scope.launch { viewModel.saveActiveProject() }
                    },
                    valueRange = 1500f..6000f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
                )

                val dotsCount = viewModel.customSignalDotsCount.collectAsState().value
                Text("Số chấm tín hiệu: ${dotsCount} chấm", fontSize = 10.sp, color = Color.LightGray)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 4, 5).forEach { count ->
                        val isSel = count == dotsCount
                        Box(
                            modifier = Modifier
                                .background(if (isSel) Color(0xFFD0BCFF) else Color.DarkGray, RoundedCornerShape(4.dp))
                                .clickable {
                                    viewModel.customSignalDotsCount.value = count
                                    scope.launch { viewModel.saveActiveProject() }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("$count chấm", color = if (isSel) Color.Black else Color.White, fontSize = 9.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("Màu chấm sáng (Lit Color):", fontSize = 10.sp, color = Color.LightGray)
                val sigColors = listOf(
                    4278190335L to "Xanh Dương",
                    4294967040L to "Vàng Brilliant",
                    4294901760L to "Đỏ Chói",
                    4278255360L to "Xanh Lá"
                )
                val activeSigColor = viewModel.customSignalColor.collectAsState().value
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sigColors.forEach { (clrLong, name) ->
                        val isSel = activeSigColor == clrLong
                        Card(
                            modifier = Modifier.clickable {
                                viewModel.customSignalColor.value = clrLong
                                scope.launch { viewModel.saveActiveProject() }
                            },
                            colors = CardDefaults.cardColors(containerColor = Color(clrLong)),
                            border = BorderStroke(if (isSel) 2.dp else 0.dp, Color.White)
                        ) {
                            Text(name, color = if (clrLong == 4294967040L) Color.Black else Color.White, fontSize = 8.sp, modifier = Modifier.padding(6.dp))
                        }
                    }
                }
            }
        }

        item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

        item {
            Text("KIỂU CHỮ & CỠ CHỮ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            val fonts = listOf("Default", "SansSerif", "Serif", "Monospace")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                fonts.forEach { font ->
                    val isSelected = font == viewModel.customFontName.collectAsState().value
                    Card(
                        modifier = Modifier.clickable {
                            viewModel.customFontName.value = font
                            scope.launch { viewModel.saveActiveProject() }
                        },
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFD0BCFF) else Color.Gray),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFDE1D1D).copy(alpha = 0.2f) else Color.Transparent)
                    ) {
                        Text(font, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Kích Thước Chữ: ${viewModel.customFontSize.collectAsState().value.toInt()} sp", fontSize = 10.sp, color = Color.White)
            Slider(
                value = viewModel.customFontSize.collectAsState().value,
                onValueChange = {
                    viewModel.customFontSize.value = it
                    scope.launch { viewModel.saveActiveProject() }
                },
                valueRange = 16f..42f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
            )
        }

        item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

        item {
            Text("MÀU CHỮ HÁT & CHỜ (FILL COLOR)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))

            Text("Màu chữ đã hát (Active):", fontSize = 10.sp, color = Color.LightGray)
            val activeColors = listOf(
                0xFFFF3B30 to "Đỏ",
                0xFFFFFF00 to "Vàng",
                0xFF00FF00 to "Xanh Lá",
                0xFF00FFFF to "Cyan",
                0xFFFF00FF to "Hồng"
            )
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                activeColors.forEach { (clr, label) ->
                    val isSel = viewModel.customTextColorActive.collectAsState().value == clr
                    Card(
                        modifier = Modifier.clickable {
                            viewModel.customTextColorActive.value = clr
                            scope.launch { viewModel.saveActiveProject() }
                        },
                        colors = CardDefaults.cardColors(containerColor = Color(clr)),
                        border = BorderStroke(if (isSel) 2.dp else 0.dp, Color.White)
                    ) {
                        Text(label, color = if (clr == 0xFFFFFF00) Color.Black else Color.White, fontSize = 8.sp, modifier = Modifier.padding(6.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Màu chữ chờ (Idle):", fontSize = 10.sp, color = Color.LightGray)
            val idleColors = listOf(
                0xFFFFFFFF to "Trắng",
                0xFFAAAAAA to "Xám",
                0xFFFFFFE0 to "Vàng Nhạt",
                0xFFE0FFFF to "Cyan Nhạt"
            )
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                idleColors.forEach { (clr, label) ->
                    val isSel = viewModel.customTextColorIdle.collectAsState().value == clr
                    Card(
                        modifier = Modifier.clickable {
                            viewModel.customTextColorIdle.value = clr
                            scope.launch { viewModel.saveActiveProject() }
                        },
                        colors = CardDefaults.cardColors(containerColor = Color(clr)),
                        border = BorderStroke(if (isSel) 2.dp else 0.dp, Color.White)
                    ) {
                        Text(label, color = if (clr == 0xFFFFFFFF || clr == 0xFFFFFFE0) Color.Black else Color.White, fontSize = 8.sp, modifier = Modifier.padding(6.dp))
                    }
                }
            }
        }

        item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

        item {
            Text("VIỀN & BÓNG CHỮ (STROKE & SHADOW)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            val strokeW = viewModel.customStrokeWidth.collectAsState().value
            Text("Độ dày viền: ${strokeW.toInt()} px", fontSize = 10.sp, color = Color.White)
            Slider(
                value = strokeW,
                onValueChange = {
                    viewModel.customStrokeWidth.value = it
                    scope.launch { viewModel.saveActiveProject() }
                },
                valueRange = 0f..12f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
            )

            val shadowR = viewModel.customShadowRadius.collectAsState().value
            Text("Độ lan bóng: ${shadowR.toInt()} px", fontSize = 10.sp, color = Color.White)
            Slider(
                value = shadowR,
                onValueChange = {
                    viewModel.customShadowRadius.value = it
                    scope.launch { viewModel.saveActiveProject() }
                },
                valueRange = 0f..12f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
            )
        }

        item { Divider(color = Color.LightGray.copy(alpha = 0.15f)) }

        item {
            Text("NỀN VIDEO PREVIEW (BACKGROUND)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            val bgTypes = listOf("CHECKERBOARD" to "Caro Trong Suốt", "SOLID_GREEN" to "Phông Xanh (Key)", "BLACK" to "Đen Tuyền", "GRADIENT" to "Gradient Tím")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bgTypes.forEach { (bgKey, bgLabel) ->
                    val isSelected = bgKey == viewModel.customBackgroundType.collectAsState().value
                    Card(
                        modifier = Modifier.clickable {
                            viewModel.customBackgroundType.value = bgKey
                            scope.launch { viewModel.saveActiveProject() }
                        },
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFD0BCFF) else Color.Gray),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFDE1D1D).copy(alpha = 0.2f) else Color.Transparent)
                    ) {
                        Text(bgLabel, color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun XuatTab(
    syncedSyllables: List<TimedSyllable>,
    viewModel: KaraokeViewModel,
    scope: CoroutineScope,
    context: Context,
    onStartExport: (String) -> Unit,
    onUpdateProgress: (Float) -> Unit,
    onExportComplete: (String?) -> Unit
) {
    var fps by remember { mutableStateOf("60") }
    var resolution by remember { mutableStateOf("1080p") }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("CÀI ĐẶT VIDEO ĐẦU RA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Fps:", fontSize = 10.sp, color = Color.LightGray)
                Row {
                    listOf("30", "60").forEach { item ->
                        val isSel = item == fps
                        Box(
                            modifier = Modifier
                                .background(if (isSel) Color(0xFFD0BCFF) else Color.DarkGray, RoundedCornerShape(4.dp))
                                .clickable { fps = item }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(item, color = Color.White, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text("Độ Phân Giải:", fontSize = 10.sp, color = Color.LightGray)
                Row {
                    listOf("1080p", "4K").forEach { item ->
                        val isSel = item == resolution
                        Box(
                            modifier = Modifier
                                .background(if (isSel) Color(0xFFD0BCFF) else Color.DarkGray, RoundedCornerShape(4.dp))
                                .clickable { resolution = item }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(item, color = Color.White, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }

        Divider(color = Color.LightGray.copy(alpha = 0.1f))

        Button(
            onClick = {
                if (syncedSyllables.isEmpty()) {
                    Toast.makeText(context, "Chưa có lời hát nào được đồng bộ!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                onStartExport("SRT")
                scope.launch {
                    for (i in 1..10) {
                        delay(100)
                        onUpdateProgress(i / 10f)
                    }
                    val srtFile = viewModel.saveSrtFile()
                    onExportComplete(srtFile?.absolutePath ?: "Thư mục Downloads")
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.List, contentDescription = "Srt", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Xuất phụ đề SRT chuyên nghiệp", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Button(
            onClick = {
                if (syncedSyllables.isEmpty()) {
                    Toast.makeText(context, "Chưa có lời hát nào được đồng bộ!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                onStartExport("MP4")
                scope.launch {
                    val progressJob = launch {
                        viewModel.exportProgressFlow.collect { progress ->
                            onUpdateProgress(progress)
                        }
                    }
                    val mp4File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        viewModel.saveMp4File()
                    }
                    progressJob.cancel()
                    onUpdateProgress(1f)
                    onExportComplete(mp4File?.absolutePath ?: "LỖI XUẤT PHIM")
                    if (mp4File == null) {
                        Toast.makeText(context, "Lỗi trích xuất định dạng MP4!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Đã xuất video karaoke thành công!", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Render", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("XUẤT VIDEO KARAOKE MP4", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
