package com.example.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.KaraokeProject
import com.example.data.MidiNote
import com.example.data.TimedSyllable
import com.example.ui.util.Localization

private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

fun midiPitchToName(pitch: Int): String {
    val note = NOTE_NAMES[pitch % 12]
    val octave = (pitch / 12) - 1
    return "$note$octave"
}

@Composable
fun StereoWaveformTimeline(
    activeProject: KaraokeProject?,
    ch1Wave: List<Float>,
    ch2Wave: List<Float>,
    midiNotes: List<MidiNote>,
    syncedSyllables: List<TimedSyllable>,
    playPositionMs: Long,
    onSeek: (Long) -> Unit,
    onSelectSyllable: (TimedSyllable) -> Unit,
    currentLang: Localization.Language,
    showWaveform: Boolean = true,
    onToggleWaveform: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf("WAVEFORM") } // "WAVEFORM" or "PIANOROLL"
    var selectedChannel by remember { mutableStateOf(-1) } // -1 = All channels

    val isMidi = remember(midiNotes) { midiNotes.isNotEmpty() }
    val durationSeconds = ((activeProject?.audioDurationMs ?: 180000L) / 1000f).coerceAtLeast(10f)
    val pxPerSecond = 50f
    val totalTimelineWidth = (durationSeconds * pxPerSecond).dp
    val scrollState = rememberScrollState()

    // Auto scroll with playhead
    LaunchedEffect(playPositionMs) {
        val currentPlayheadPx = (playPositionMs / 1000f) * pxPerSecond
        val viewportPx = 400f
        if (currentPlayheadPx > scrollState.value + viewportPx * 0.8f || currentPlayheadPx < scrollState.value) {
            scrollState.animateScrollTo((currentPlayheadPx - viewportPx * 0.3f).toInt().coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141619), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF2C323C), RoundedCornerShape(8.dp))
    ) {
        // Top Toolbar: View Mode Switcher + Waveform Toggle + Channel Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1D23), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // View Mode & Waveform Toggle Buttons
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF252A33))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Waveform view tab
                if (showWaveform) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (viewMode == "WAVEFORM") Color(0xFF4FA3D1) else Color.Transparent)
                            .clickable { viewMode = "WAVEFORM" }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("〰️", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                Localization.get("waveform_stereo", currentLang),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (viewMode == "WAVEFORM") Color.White else Color.LightGray
                            )
                        }
                    }

                    if (isMidi) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (viewMode == "PIANOROLL") Color(0xFF9C27B0) else Color.Transparent)
                                .clickable { viewMode = "PIANOROLL" }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎹", fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    Localization.get("piano_roll", currentLang),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (viewMode == "PIANOROLL") Color.White else Color.LightGray
                                )
                            }
                        }
                    }
                } else {
                    // Lyric timeline expanded indicator
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF381E72))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📝", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (currentLang == Localization.Language.VN) "Timeline Lời (Mở rộng)" else "Lyrics Timeline (Expanded)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE8DEF8)
                            )
                        }
                    }
                }
            }

            // Right side toolbar: Quick Waveform On/Off toggle & Channel info
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Waveform toggle button
                if (onToggleWaveform != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (showWaveform) Color(0xFF2E3846) else Color(0xFF422C1A))
                            .clickable { onToggleWaveform() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("〰️", fontSize = 9.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${Localization.get("waveform_toggle_btn", currentLang)}: ${if (showWaveform) Localization.get("waveform_on", currentLang) else Localization.get("waveform_off", currentLang)}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (showWaveform) Color(0xFF81D4FA) else Color(0xFFFFCC80)
                            )
                        }
                    }
                }

                if (showWaveform) {
                    // Channel Filter selector when in Piano Roll mode
                    if (isMidi && viewMode == "PIANOROLL") {
                        val availableChannels = remember(midiNotes) {
                            midiNotes.map { it.channel }.distinct().sorted()
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                Localization.get("channel_filter", currentLang) + ":",
                                fontSize = 9.sp,
                                color = Color.LightGray
                            )

                            // All Channels Chip
                            FilterChip(
                                selected = selectedChannel == -1,
                                onClick = { selectedChannel = -1 },
                                label = { Text(Localization.get("channel_all", currentLang), fontSize = 9.sp) },
                                modifier = Modifier.height(24.dp),
                                shape = RoundedCornerShape(4.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF9C27B0),
                                    selectedLabelColor = Color.White
                                )
                            )

                            availableChannels.forEach { ch ->
                                val label = when (ch) {
                                    0 -> if (currentLang == Localization.Language.VN) "Kênh 1 (Melody)" else "Ch 1 (Melody)"
                                    3 -> if (currentLang == Localization.Language.VN) "Kênh 4 (Bass)" else "Ch 4 (Bass)"
                                    9 -> if (currentLang == Localization.Language.VN) "Kênh 10 (Trống)" else "Ch 10 (Drums)"
                                    else -> if (currentLang == Localization.Language.VN) "Kênh ${ch + 1}" else "Ch ${ch + 1}"
                                }
                                FilterChip(
                                    selected = selectedChannel == ch,
                                    onClick = { selectedChannel = ch },
                                    label = { Text(label, fontSize = 9.sp) },
                                    modifier = Modifier.height(24.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF673AB7),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    } else {
                        // Waveform Channel Info Tags
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Localization.get("waveform_left", currentLang), fontSize = 9.sp, color = Color(0xFF4FA3D1), fontWeight = FontWeight.Bold)
                            Text("•", fontSize = 9.sp, color = Color.Gray)
                            Text(Localization.get("waveform_right", currentLang), fontSize = 9.sp, color = Color(0xFF3BA99C), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Timeline Area (Tracks + Playhead)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Static Left Column (Track Headers / Pitch Labels)
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1E2128))
                    .drawBehind {
                        drawLine(
                            color = Color(0xFF2C323C),
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1f
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (showWaveform) {
                    if (viewMode == "WAVEFORM") {
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceAround,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🎵", fontSize = 12.sp)
                            Text("L", color = Color(0xFF4FA3D1), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            Text("R", color = Color(0xFF3BA99C), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            Text("T1", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            Text("T2", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    } else {
                        // Piano Roll Pitch labels
                        Column(
                            modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("C6", color = Color(0xFFD0BCFF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text("G5", color = Color.LightGray, fontSize = 8.sp)
                            Text("C5", color = Color(0xFFD0BCFF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text("G4", color = Color.LightGray, fontSize = 8.sp)
                            Text("C4", color = Color(0xFFD0BCFF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text("C3", color = Color.Gray, fontSize = 8.sp)
                        }
                    }
                } else {
                    // Expanded Lyric Mode: Only show Lyric Track labels
                    Column(
                        modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.SpaceAround,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎤", fontSize = 14.sp)
                        Text("T1", color = Color(0xFF80D8FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("T2", color = Color(0xFFFFAB40), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Scrollable Timeline Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(scrollState)
                    .pointerInput(durationSeconds) {
                        detectTapGestures { offset ->
                            val clickedSeconds = offset.x / pxPerSecond
                            val clickedMs = (clickedSeconds * 1000L).toLong().coerceIn(0L, (durationSeconds * 1000).toLong())
                            onSeek(clickedMs)
                        }
                    }
            ) {
                // Background Grid & Time Ruler
                Canvas(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(totalTimelineWidth)
                ) {
                    val width = size.width
                    val height = size.height
                    val numSeconds = durationSeconds.toInt()

                    // Horizontal semitone pitch lines for piano roll
                    if (showWaveform && viewMode == "PIANOROLL") {
                        val numRows = 12
                        for (r in 0..numRows) {
                            val y = (height / numRows) * r
                            drawLine(
                                color = if (r % 4 == 0) Color(0xFF323846) else Color(0xFF1E222A),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1f
                            )
                        }
                    }

                    // Vertical Time ticks and second markers
                    for (s in 0..numSeconds) {
                        val x = s * pxPerSecond
                        val isMajor = (s % 2 == 0)
                        drawLine(
                            color = if (isMajor) Color(0xFF3A4250) else Color(0xFF222730),
                            start = Offset(x, if (isMajor) 0f else 10f),
                            end = Offset(x, height),
                            strokeWidth = if (isMajor) 1f else 0.5f
                        )
                    }
                }

                // Tracks Content
                if (showWaveform) {
                    if (viewMode == "WAVEFORM") {
                        Column(
                            modifier = Modifier
                                .width(totalTimelineWidth)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceAround
                        ) {
                            // Double Channel Stereo Waveform Area (Left on Top, Right on Bottom)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .background(Color(0xFF0F1318))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val count = ch1Wave.size
                                    val w = size.width
                                    val h = size.height
                                    val midY = h / 2f
                                    val topCenterY = h / 4f
                                    val bottomCenterY = 3f * h / 4f

                                    // Channel baseline dividers
                                    drawLine(
                                        color = Color(0xFF262D38),
                                        start = Offset(0f, topCenterY),
                                        end = Offset(w, topCenterY),
                                        strokeWidth = 1f
                                    )
                                    drawLine(
                                        color = Color(0xFF384354),
                                        start = Offset(0f, midY),
                                        end = Offset(w, midY),
                                        strokeWidth = 1.5f
                                    )
                                    drawLine(
                                        color = Color(0xFF262D38),
                                        start = Offset(0f, bottomCenterY),
                                        end = Offset(w, bottomCenterY),
                                        strokeWidth = 1f
                                    )

                                    if (count > 0) {
                                        val stepX = w / count
                                        for (i in 0 until count) {
                                            val x = i * stepX
                                            val val1 = ch1Wave[i]
                                            val val2 = if (i < ch2Wave.size) ch2Wave[i] else val1

                                            val amp1 = val1 * (h / 4f - 2f)
                                            val amp2 = val2 * (h / 4f - 2f)

                                            // Left Channel spikes (Top)
                                            drawLine(
                                                color = Color(0xFF4FA3D1),
                                                start = Offset(x, topCenterY - amp1),
                                                end = Offset(x, topCenterY + amp1),
                                                strokeWidth = 2f
                                            )

                                            // Right Channel spikes (Bottom)
                                            drawLine(
                                                color = Color(0xFF3BA99C),
                                                start = Offset(x, bottomCenterY - amp2),
                                                end = Offset(x, bottomCenterY + amp2),
                                                strokeWidth = 2f
                                            )
                                        }
                                    }
                                }
                            }

                            // Track 2: Line 1 Syllables
                            TimelineTrack(
                                syllables = syncedSyllables.filter { it.lineIndex % 2 == 0 },
                                pxPerSecond = pxPerSecond,
                                onSelectSyllable = onSelectSyllable
                            )

                            // Track 3: Line 2 Syllables
                            TimelineTrack(
                                syllables = syncedSyllables.filter { it.lineIndex % 2 != 0 },
                                pxPerSecond = pxPerSecond,
                                onSelectSyllable = onSelectSyllable
                            )
                        }
                    } else {
                        // MIDI Piano Roll View
                        Box(
                            modifier = Modifier
                                .width(totalTimelineWidth)
                                .fillMaxHeight()
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val h = size.height
                                val minPitch = 40 // E2
                                val maxPitch = 88 // E6
                                val pitchRange = (maxPitch - minPitch).toFloat()

                                val filteredNotes = if (selectedChannel == -1) {
                                    midiNotes
                                } else {
                                    midiNotes.filter { it.channel == selectedChannel }
                                }

                                filteredNotes.forEach { note ->
                                    val startX = (note.startTimeMs / 1000f) * pxPerSecond
                                    val noteWidth = ((note.durationMs / 1000f) * pxPerSecond).coerceAtLeast(6f)
                                    val normPitch = ((note.pitch - minPitch) / pitchRange).coerceIn(0.05f, 0.95f)
                                    val noteY = (1f - normPitch) * (h - 18f)

                                    val noteColor = when (note.channel) {
                                        0 -> Color(0xFFFFB74D) // Melody Gold
                                        3 -> Color(0xFF4CAF50) // Bass Green
                                        9 -> Color(0xFFFF5252) // Drums Red
                                        else -> Color(0xFFBA68C8) // Chords Violet
                                    }

                                    // Draw rounded note block
                                    drawRoundRect(
                                        color = noteColor,
                                        topLeft = Offset(startX, noteY),
                                        size = Size(noteWidth, 14f),
                                        cornerRadius = CornerRadius(3f, 3f)
                                    )

                                    // Draw subtle border
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.6f),
                                        topLeft = Offset(startX, noteY),
                                        size = Size(noteWidth, 14f),
                                        cornerRadius = CornerRadius(3f, 3f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                                    )
                                }
                            }

                            // Syllable alignment overlay below notes
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 2.dp),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                TimelineTrack(
                                    syllables = syncedSyllables,
                                    pxPerSecond = pxPerSecond,
                                    onSelectSyllable = onSelectSyllable
                                )
                            }
                        }
                    }
                } else {
                    // Waveform HIDDEN: Full Height Lyric Timeline Tracks
                    Column(
                        modifier = Modifier
                            .width(totalTimelineWidth)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Expanded Track 1: Odd Lines
                        TimelineTrack(
                            syllables = syncedSyllables.filter { it.lineIndex % 2 == 0 },
                            pxPerSecond = pxPerSecond,
                            onSelectSyllable = onSelectSyllable,
                            expanded = true,
                            trackColor = Color(0xFF0288D1),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )

                        // Expanded Track 2: Even Lines
                        TimelineTrack(
                            syllables = syncedSyllables.filter { it.lineIndex % 2 != 0 },
                            pxPerSecond = pxPerSecond,
                            onSelectSyllable = onSelectSyllable,
                            expanded = true,
                            trackColor = Color(0xFFF57C00),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }

                // Interactive Yellow Playhead
                val playheadX = (playPositionMs / 1000f) * pxPerSecond
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .offset(x = playheadX.dp)
                        .background(Color(0xFFFFD54F))
                ) {
                    // Playhead Top Pointer
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .offset(x = (-4).dp, y = 0.dp)
                            .background(Color(0xFFFFD54F), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineTrack(
    syllables: List<TimedSyllable>,
    pxPerSecond: Float,
    onSelectSyllable: (TimedSyllable) -> Unit,
    expanded: Boolean = false,
    trackColor: Color = Color(0xFF6750A4),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (expanded) 60.dp else 28.dp)
            .background(Color(0xFF1E2128).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
    ) {
        syllables.forEach { syl ->
            val startX = (syl.startTimeMs / 1000f) * pxPerSecond
            val durationMs = (syl.endTimeMs - syl.startTimeMs).coerceAtLeast(100L)
            val sylWidth = ((durationMs / 1000f) * pxPerSecond).coerceAtLeast(16f)

            Box(
                modifier = Modifier
                    .offset(x = startX.dp, y = if (expanded) 4.dp else 2.dp)
                    .width(sylWidth.dp)
                    .height(if (expanded) 52.dp else 24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(trackColor)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .clickable { onSelectSyllable(syl) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (expanded) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = syl.text,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${syl.startTimeMs}ms",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 8.sp
                        )
                    }
                } else {
                    Text(
                        text = syl.text,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
