package com.example.ui.dialogs

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.util.AppLogger
import com.example.ui.util.LogEntry
import com.example.ui.util.LogLevel
import com.example.ui.util.Localization

@Composable
fun LogViewerDialog(
    currentLang: Localization.Language,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allLogs by AppLogger.logs.collectAsState()
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) } // null = All
    var searchQuery by remember { mutableStateOf("") }
    var expandedEntryId by remember { mutableStateOf<Long?>(null) }

    val filteredLogs = remember(allLogs, selectedLevel, searchQuery) {
        allLogs.filter { entry ->
            val matchLevel = selectedLevel == null || entry.level == selectedLevel
            val matchQuery = searchQuery.isBlank() ||
                    entry.message.contains(searchQuery, ignoreCase = true) ||
                    entry.tag.contains(searchQuery, ignoreCase = true) ||
                    (entry.details?.contains(searchQuery, ignoreCase = true) == true)
            matchLevel && matchQuery
        }
    }

    val actionCount = remember(allLogs) { allLogs.count { it.level == LogLevel.ACTION } }
    val infoCount = remember(allLogs) { allLogs.count { it.level == LogLevel.INFO } }
    val warnCount = remember(allLogs) { allLogs.count { it.level == LogLevel.WARN } }
    val errorCount = remember(allLogs) { allLogs.count { it.level == LogLevel.ERROR } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF14161B),
            border = BorderStroke(1.dp, Color(0xFF2C323D))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📋", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = Localization.get("activity_logs_title", currentLang),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${allLogs.size} ${if (currentLang == Localization.Language.VN) "bản ghi" else "entries"} (${filteredLogs.size} ${if (currentLang == Localization.Language.VN) "đang hiện" else "shown"})",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action buttons: Copy & Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val success = AppLogger.copyToClipboard(context, selectedLevel)
                            if (success) {
                                Toast.makeText(context, Localization.get("logs_copied_toast", currentLang), Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedLevel == null) Localization.get("copy_all_logs", currentLang) else "${Localization.get("copy_logs", currentLang)} (${selectedLevel?.name})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            AppLogger.clear()
                            Toast.makeText(context, Localization.get("logs_cleared_toast", currentLang), Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80)),
                        border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFF8A80))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = Localization.get("clear_logs", currentLang),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(if (currentLang == Localization.Language.VN) "Tìm theo từ khóa, thẻ tag hoặc lỗi..." else "Filter by keyword, tag or error...", fontSize = 11.sp, color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF1E2128),
                        unfocusedContainerColor = Color(0xFF1E2128),
                        focusedBorderColor = Color(0xFF0288D1),
                        unfocusedBorderColor = Color(0xFF2C323D)
                    ),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Level Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // ALL chip
                    FilterChip(
                        selected = selectedLevel == null,
                        onClick = { selectedLevel = null },
                        label = { Text("${Localization.get("log_level_all", currentLang)} (${allLogs.size})", fontSize = 10.sp) },
                        shape = RoundedCornerShape(6.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF384354),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E2128),
                            labelColor = Color.LightGray
                        )
                    )

                    // ACTION chip
                    FilterChip(
                        selected = selectedLevel == LogLevel.ACTION,
                        onClick = { selectedLevel = if (selectedLevel == LogLevel.ACTION) null else LogLevel.ACTION },
                        label = { Text("${Localization.get("log_level_action", currentLang)} ($actionCount)", fontSize = 10.sp) },
                        shape = RoundedCornerShape(6.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0288D1),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E2128),
                            labelColor = Color.LightGray
                        )
                    )

                    // INFO chip
                    FilterChip(
                        selected = selectedLevel == LogLevel.INFO,
                        onClick = { selectedLevel = if (selectedLevel == LogLevel.INFO) null else LogLevel.INFO },
                        label = { Text("${Localization.get("log_level_info", currentLang)} ($infoCount)", fontSize = 10.sp) },
                        shape = RoundedCornerShape(6.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF388E3C),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E2128),
                            labelColor = Color.LightGray
                        )
                    )

                    // WARN chip
                    FilterChip(
                        selected = selectedLevel == LogLevel.WARN,
                        onClick = { selectedLevel = if (selectedLevel == LogLevel.WARN) null else LogLevel.WARN },
                        label = { Text("${Localization.get("log_level_warn", currentLang)} ($warnCount)", fontSize = 10.sp) },
                        shape = RoundedCornerShape(6.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF57C00),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E2128),
                            labelColor = Color.LightGray
                        )
                    )

                    // ERROR chip
                    FilterChip(
                        selected = selectedLevel == LogLevel.ERROR,
                        onClick = { selectedLevel = if (selectedLevel == LogLevel.ERROR) null else LogLevel.ERROR },
                        label = { Text("${Localization.get("log_level_error", currentLang)} ($errorCount)", fontSize = 10.sp) },
                        shape = RoundedCornerShape(6.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFD32F2F),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E2128),
                            labelColor = Color.LightGray
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Log List
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF1A1D23), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📝", fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = Localization.get("no_logs_yet", currentLang),
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF0F1115), RoundedCornerShape(8.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { entry ->
                            LogItemRow(
                                entry = entry,
                                isExpanded = expandedEntryId == entry.id,
                                onToggleExpand = {
                                    expandedEntryId = if (expandedEntryId == entry.id) null else entry.id
                                },
                                onCopyEntry = {
                                    val text = "[${entry.timeFormatted}] [${entry.level.name}] [${entry.tag}] ${entry.message}\n${entry.details ?: ""}"
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Log Entry", text))
                                    Toast.makeText(context, Localization.get("logs_copied_toast", currentLang), Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItemRow(
    entry: LogEntry,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopyEntry: () -> Unit
) {
    val levelColor = Color(entry.level.badgeColor)
    val hasDetails = !entry.details.isNullOrBlank()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181B22)),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (entry.level == LogLevel.ERROR) Color(0xFFB71C1C) else Color(0xFF262B35)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (hasDetails) onToggleExpand() }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Time
                    Text(
                        text = entry.timeFormatted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )

                    // Level Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(levelColor)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = entry.level.name,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Tag Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF282F3B))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = entry.tag,
                            fontSize = 9.sp,
                            color = Color(0xFF80D8FF),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCopyEntry,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(13.dp))
                    }
                    if (hasDetails) {
                        IconButton(
                            onClick = onToggleExpand,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Message
            Text(
                text = entry.message,
                fontSize = 11.sp,
                color = if (entry.level == LogLevel.ERROR) Color(0xFFFF8A80) else Color(0xFFE0E0E0),
                lineHeight = 15.sp
            )

            // Expandable details (stacktrace or extra payload)
            AnimatedVisibility(visible = isExpanded && hasDetails) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(Color(0xFF0C0E12), RoundedCornerShape(4.dp))
                        .padding(6.dp)
                ) {
                    Text(
                        text = entry.details ?: "",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFFB74D),
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}
