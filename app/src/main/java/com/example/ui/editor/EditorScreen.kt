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
import com.example.api.GeminiClient
import com.example.data.KaraokeProject
import com.example.data.PresetSongs
import com.example.data.TimedSyllable
import com.example.viewmodel.KaraokeViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    viewModel: KaraokeViewModel,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.importCustomAudio(uri)
            Toast.makeText(context, "Đã nhập âm thanh thành công!", Toast.LENGTH_SHORT).show()
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

    // Tab categories
    var activeTab by remember { mutableStateOf("lời bài hát") }
    val tabs = listOf("Nhập lời", "lời bài hát", "Nhập âm thanh", "Tùy biến", "Xuất")

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
                    shadowRadius = viewModel.customShadowRadius.collectAsState().value
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
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Quay lại", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

            // 3. Multi-Track Timeline layout centering double channel audio waves (Bottom rows)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f)
                    .background(Color(0xFF141414), RoundedCornerShape(8.dp))
                    .border(width = 1.dp, color = Color.DarkGray)
            ) {
                // Scrollable content representing synchronized tracks
                Row(modifier = Modifier.fillMaxSize()) {
                    // Static Left Side Labels Column (Music, T1, T2 Labels as seen in image 2)
                    Column(
                        modifier = Modifier
                            .width(40.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF232323))
                            .drawBehind {
                                drawLine(
                                    color = Color.Black,
                                    start = Offset(size.width, 0f),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = density
                                )
                            },
                        verticalArrangement = Arrangement.SpaceAround,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎵", fontSize = 14.sp)
                        Text("T1", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("T2", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Horizontal Scrolling Timeline Content Area
                    val scrollState = rememberScrollState()
                    val pxPerSecond = 50f // density factor for horizontal scrolling representation
                    val durationSeconds = (activeProject?.audioDurationMs ?: 180000L) / 1000f
                    val totalTimelineWidth = (durationSeconds * pxPerSecond).dp

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(scrollState)
                    ) {
                        // Background Grid & Rule Markers
                        Canvas(modifier = Modifier.fillMaxHeight().width(totalTimelineWidth)) {
                            // Draw nice second division ticks and time markings
                            val numTicks = durationSeconds.toInt()
                            for (i in 0..numTicks step 2) {
                                val x = i * pxPerSecond
                                drawLine(
                                    color = Color.DarkGray,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1f
                                )
                            }
                        }

                        // Playback track columns
                        Column(
                            modifier = Modifier
                                .width(totalTimelineWidth)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceAround
                        ) {
                            // Track 1: Dual Audio Waveform channels
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .background(Color(0xFF0F1E29))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val count = ch1Wave.size
                                    if (count > 0) {
                                        val stepX = size.width / count
                                        val centerY = size.height / 2f
                                        for (i in 0 until count) {
                                            val x = i * stepX
                                            val h1 = ch1Wave[i] * (centerY - 2)
                                            val h2 = ch2Wave[i] * (centerY - 2)
                                            // Channel 1 top
                                            drawLine(
                                                color = Color(0xFF00BCD4),
                                                start = Offset(x, centerY - h1),
                                                end = Offset(x, centerY),
                                                strokeWidth = 2f
                                            )
                                            // Channel 2 bottom
                                            drawLine(
                                                color = Color(0xFF00E676),
                                                start = Offset(x, centerY),
                                                end = Offset(x, centerY + h2),
                                                strokeWidth = 2f
                                            )
                                        }
                                    }
                                }
                            }

                            // Track 2: Even Syllables Line 1 timeline
                            TimelineTrack(
                                syllables = syncedSyllables.filter { it.lineIndex % 2 == 0 },
                                pxPerSecond = pxPerSecond,
                                onSelectSyllable = { selectedSyllableForEdit = it }
                            )

                            // Track 3: Odd Syllables Line 2 timeline
                            TimelineTrack(
                                syllables = syncedSyllables.filter { it.lineIndex % 2 != 0 },
                                pxPerSecond = pxPerSecond,
                                onSelectSyllable = { selectedSyllableForEdit = it }
                            )
                        }

                        // Interactive Timeline Yellow/Gold playhead line matches Image 3
                        val playheadX = (playPositionMs / 1000f) * pxPerSecond
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(2.dp)
                                .offset(x = playheadX.dp)
                                .background(Color(0xFFD0BCFF))
                        )
                    }
                }
            }
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
                tabs.forEach { tab ->
                    val isSelected = tab == activeTab
                    Box(
                        modifier = Modifier
                            .clickable { activeTab = tab }
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
                            text = tab,
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
                    "Nhập lời" -> {
                        var promptForAI by remember { mutableStateOf("") }
                        var aiGenerating by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Nhập lời gốc bài hát:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            OutlinedTextField(
                                value = activeProject?.lyricsText ?: "",
                                onValueChange = { newLyrics ->
                                    activeProject?.let {
                                        viewModel.parseLyricsToSyllablesQueue(newLyrics)
                                        // Save raw text to db
                                        scope.launch {
                                            viewModel.saveActiveProject()
                                        }
                                    }
                                },
                                textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.5f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.Gray
                                )
                            )

                            Divider(color = Color.White.copy(alpha = 0.1f))

                            // Gemini AI assistant block integration using server-side Gemini capability
                            Text("💡 SÁNG TÁC LỜI NHANH BẰNG GEMINI AI:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedTextField(
                                    value = promptForAI,
                                    onValueChange = { promptForAI = it },
                                    placeholder = { Text("Chủ đề: Tình yêu, mùa xuân...", fontSize = 10.sp, color = Color.Gray) },
                                    textStyle = TextStyle(color = Color.White, fontSize = 10.sp),
                                    modifier = Modifier.weight(1.2f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFD0BCFF),
                                        unfocusedBorderColor = Color.DarkGray
                                    )
                                )

                                Button(
                                    onClick = {
                                        if (promptForAI.isNotBlank()) {
                                            aiGenerating = true
                                            scope.launch {
                                                val res = GeminiClient.generateLyrics(promptForAI)
                                                if (res.isNotBlank()) {
                                                    // Copy generated to active lyrics inputs
                                                    activeProject?.let {
                                                        val updatedProj = it.copy(lyricsText = res)
                                                        viewModel.parseLyricsToSyllablesQueue(res)
                                                        viewModel.saveActiveProject()
                                                    }
                                                    Toast.makeText(context, "Sáng tác thành công!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Lỗi tạo lời hoặc chưa cấu hình API Key.", Toast.LENGTH_LONG).show()
                                                }
                                                aiGenerating = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    if (aiGenerating) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF381E72), strokeWidth = 2.dp)
                                    } else {
                                        Text("Viết bằng AI", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    "lời bài hát" -> {
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

                            // THE BIG RED 3D BUTTON! Matches Image 2 & 3.
                            // Adds custom ripple and click sizing scale anims for tactile mashing behavior
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
                                                    "Hãy bấm phát nhạc trước khi đồng bộ!",
                                                    Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        }
                                    }
                                    .testTag("big_red_sync_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                // 3O bottom depth shadow layers
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

                            // Sync utilities row
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

                    "Nhập âm thanh" -> {
                        // MIDI imports + preset audio configuration options
                        var selectedSf2Soundfont by remember { mutableStateOf("Retro 8-Bit Wavetable") }
                        val soundfonts = listOf("Retro 8-Bit Wavetable", "Acoustic Grand (sf2)", "Classic Synth Wavetable", "Orchestral Keys")

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
                                    onClick = { audioPickerLauncher.launch("audio/*") },
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
                                Divider(color = Color.LightGray.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("SoundFont sf2 Synth Chọn Mẫu:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            items(soundfonts) { sf ->
                                val isSelected = sf == selectedSf2Soundfont
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) Color(0xFFD0BCFF).copy(alpha = 0.2f) else Color.Transparent,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { selectedSf2Soundfont = sf }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedSf2Soundfont = sf },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFD0BCFF))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(sf, color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    "Tùy biến" -> {
                        // Subtitle styles, size, colors, stroke customizers
                        val fonts = listOf("Default", "SansSerif", "Serif", "Monospace")

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Font selectors
                            item {
                                Text("Phông Chữ:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    fonts.forEach { font ->
                                        val isSelected = font == viewModel.customFontName.collectAsState().value
                                        Card(
                                            modifier = Modifier.clickable { viewModel.customFontName.value = font },
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFD0BCFF) else Color.Gray),
                                            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFDE1D1D).copy(alpha = 0.2f) else Color.Transparent)
                                        ) {
                                            Text(font, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                }
                            }

                            // Font size
                            item {
                                Text("Kích Thước Chữ: ${viewModel.customFontSize.collectAsState().value.toInt()}", fontSize = 11.sp, color = Color.White)
                                Slider(
                                    value = viewModel.customFontSize.collectAsState().value,
                                    onValueChange = { viewModel.customFontSize.value = it },
                                    valueRange = 16f..42f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
                                )
                            }

                            // Stroke settings
                            item {
                                Text("Độ Dày Viền: ${viewModel.customStrokeWidth.collectAsState().value.toInt()}px", fontSize = 11.sp, color = Color.White)
                                Slider(
                                    value = viewModel.customStrokeWidth.collectAsState().value,
                                    onValueChange = { viewModel.customStrokeWidth.value = it },
                                    valueRange = 0f..12f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFD0BCFF), activeTrackColor = Color(0xFFD0BCFF))
                                )
                            }

                            // Background layout types
                            item {
                                Text("Nền Video Preview:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                val bgTypes = listOf("CHECKERBOARD", "SOLID_GREEN", "BLACK", "GRADIENT")
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    bgTypes.forEach { bg ->
                                        val isSelected = bg == viewModel.customBackgroundType.collectAsState().value
                                        Card(
                                            modifier = Modifier.clickable { viewModel.customBackgroundType.value = bg },
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFD0BCFF) else Color.Gray),
                                            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFDE1D1D).copy(alpha = 0.2f) else Color.Transparent)
                                        ) {
                                            Text(bg, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                }
                            }

                            // Colors selectors simple sliders
                            item {
                                Text("Màu Chữ Chờ: Trắng nịnh mắt", fontSize = 10.sp, color = Color.LightGray)
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val colors = listOf(0xFFFFFFFF to "Trắng", 0xFFFFFF00 to "Vàng", 0xFF00FFFF to "Cyan")
                                    colors.forEach { (clr, label) ->
                                        val isSel = viewModel.customTextColorIdle.collectAsState().value == clr
                                        Card(
                                            modifier = Modifier.clickable { viewModel.customTextColorIdle.value = clr },
                                            colors = CardDefaults.cardColors(containerColor = Color(clr))
                                        ) {
                                            Text(label, color = if (clr == 0xFFFFFFFF) Color.Black else Color.White, fontSize = 9.sp, modifier = Modifier.padding(4.dp))
                                        }
                                    }
                                }
                            }

                            item {
                                Text("Màu Chữ Hát (Active): Đỏ cá tính", fontSize = 10.sp, color = Color.LightGray)
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val colors = listOf(0xFFFF3330 to "Đỏ", 0xFF00FF00 to "Xanh Lá", 0xFFFF00FF to "Hồng")
                                    colors.forEach { (clr, label) ->
                                        val isSel = viewModel.customTextColorActive.collectAsState().value == clr
                                        Card(
                                            modifier = Modifier.clickable { viewModel.customTextColorActive.value = clr },
                                            colors = CardDefaults.cardColors(containerColor = Color(clr))
                                        ) {
                                            Text(label, color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Xuất" -> {
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
                                        // Slow render simulation representing frames generator
                                        for (i in 1..20) {
                                            delay(150)
                                            exportProgress = i / 20f
                                        }
                                        val mp4File = viewModel.saveMp4File()
                                        exportedFilepath = mp4File?.absolutePath ?: "Thư mục Downloads/KaraokeVideo"
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
    shadowRadius: Float
) {
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
            // Group syllables of the same line index
            val linesMap = syncedSyllables.groupBy { it.lineIndex }
            val allLineIndexes = linesMap.keys.sorted()

            if (allLineIndexes.isEmpty()) {
                // Default teaser text
                Text(
                    text = "[ Karaoke Subtitles Overlay ]",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            } else {
                // Professional Dual-Line Karaoke logic: 
                // Determine the current active line based on current playback playhead
                val currentLineIdx = allLineIndexes.firstOrNull { idx ->
                    val syls = linesMap[idx] ?: emptyList()
                    val lineStart = syls.firstOrNull()?.startTimeMs ?: 0L
                    val nextIdx = allLineIndexes.getOrNull(allLineIndexes.indexOf(idx) + 1)
                    val nextStart = nextIdx?.let { linesMap[it]?.firstOrNull()?.startTimeMs } ?: Long.MAX_VALUE
                    currentTimeMs >= lineStart && currentTimeMs < nextStart
                } ?: allLineIndexes.firstOrNull() ?: 0

                // Assign the dynamic even-indexed and odd-indexed visual display rows
                val evenLineIdx = if (currentLineIdx % 2 == 0) currentLineIdx else (currentLineIdx + 1)
                val oddLineIdx = if (currentLineIdx % 2 == 0) (currentLineIdx + 1) else currentLineIdx

                val evenSyllables = linesMap[evenLineIdx]?.sortedBy { it.syllableIndex }
                val oddSyllables = linesMap[oddLineIdx]?.sortedBy { it.syllableIndex }

                // Row 1 (Top): Renders the active or upcoming even-indexed lyrics line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = (fontSize * 1.5f).dp)
                        .padding(horizontal = 40.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (evenSyllables != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            evenSyllables.forEach { syl ->
                                val activePercent = when {
                                    currentTimeMs < syl.startTimeMs -> 0f
                                    currentTimeMs > syl.endTimeMs -> 1f
                                    else -> {
                                        val total = (syl.endTimeMs - syl.startTimeMs).toFloat()
                                        if (total > 0) (currentTimeMs - syl.startTimeMs) / total else 1f
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

                // Row 2 (Bottom): Renders the active or upcoming odd-indexed lyrics line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = (fontSize * 1.5f).dp)
                        .padding(horizontal = 40.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (oddSyllables != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            oddSyllables.forEach { syl ->
                                val activePercent = when {
                                    currentTimeMs < syl.startTimeMs -> 0f
                                    currentTimeMs > syl.endTimeMs -> 1f
                                    else -> {
                                        val total = (syl.endTimeMs - syl.startTimeMs).toFloat()
                                        if (total > 0) (currentTimeMs - syl.startTimeMs) / total else 1f
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
