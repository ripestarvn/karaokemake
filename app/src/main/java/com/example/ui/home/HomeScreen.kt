package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.KaraokeProject
import com.example.data.PresetSongs
import com.example.ui.settings.AppSettings
import com.example.ui.util.Localization
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeHeader(
    appSettings: AppSettings,
    onOpenSettings: () -> Unit,
    onOpenUsageNotes: () -> Unit
) {
    val currentLang by appSettings.language.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = Localization.get("app_title", currentLang),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = Localization.get("app_subtitle", currentLang),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onOpenUsageNotes,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Usage Notes",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.testTag("home_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    projects: List<KaraokeProject>,
    appSettings: AppSettings,
    onOpenSettings: () -> Unit,
    onOpenUsageNotes: () -> Unit,
    onCreateProject: (String, String, String, com.example.data.PresetSong?) -> Unit,
    onEditProject: (Int) -> Unit,
    onDeleteProject: (Int) -> Unit
) {
    val currentLang by appSettings.language.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HomeHeader(
                appSettings = appSettings,
                onOpenSettings = onOpenSettings,
                onOpenUsageNotes = onOpenUsageNotes
            )

            // New Project Card (Hero)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCreateDialog = true }
                    .testTag("create_project_card_button"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Localization.get("new_project", currentLang),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = Localization.get("new_project_sub", currentLang),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tạo mới",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Recent Projects Label block
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Localization.get("recent_projects", currentLang),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${projects.size} " + if (currentLang == Localization.Language.VN) "dự án" else "projects",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "🎤",
                            fontSize = 44.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            Localization.get("no_projects_title", currentLang),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            Localization.get("no_projects_desc", currentLang),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(projects) { project ->
                        ProjectItemRow(
                            project = project,
                            currentLang = currentLang,
                            onEdit = { onEditProject(project.id) },
                            onDelete = { onDeleteProject(project.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            currentLang = currentLang,
            onDismiss = { showCreateDialog = false },
            onCreate = { title, artist, lyrics, preset ->
                onCreateProject(title, artist, lyrics, preset)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun ProjectItemRow(
    project: KaraokeProject,
    currentLang: Localization.Language,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val formattedDate = remember(project.lastModified) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        sdf.format(Date(project.lastModified))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Thumbnail with visual equalizer wave
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(modifier = Modifier.width(36.dp).height(3.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Box(modifier = Modifier.width(22.dp).height(3.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape))
                    Box(modifier = Modifier.width(28.dp).height(3.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Project Info Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = project.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (currentLang == Localization.Language.VN) "Nghệ sĩ: ${project.artist}" else "Artist: ${project.artist}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$formattedDate",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEdit,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("edit_project_btn_${project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Chỉnh sửa",
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.testTag("delete_project_btn_${project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Xóa",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectDialog(
    currentLang: Localization.Language,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, com.example.data.PresetSong?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var lyrics by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf<com.example.data.PresetSong?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (currentLang == Localization.Language.VN) "TẠO DỰ ÁN MỚI" else "CREATE NEW PROJECT",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Preset selector
                Text(
                    text = if (currentLang == Localization.Language.VN) "Chọn từ bài hát mẫu (tự động có nhạc & nốt):" else "Choose from preset songs (auto filled):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetSongs.SONGS.forEach { preset ->
                        val isSelected = selectedPreset == preset
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedPreset = preset
                                    title = preset.title
                                    artist = preset.artist
                                    lyrics = preset.lyrics
                                },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    preset.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (currentLang == Localization.Language.VN) "Tên bài hát" else "Song Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(if (currentLang == Localization.Language.VN) "Nghệ sĩ" else "Artist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = lyrics,
                    onValueChange = { lyrics = it },
                    label = { Text(if (currentLang == Localization.Language.VN) "Lời bài hát (Xuống dòng cho mỗi câu)" else "Lyrics (Line by line)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (currentLang == Localization.Language.VN) "Hủy" else "Cancel")
                    }

                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onCreate(title, artist, lyrics, selectedPreset)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (currentLang == Localization.Language.VN) "Tạo dự án" else "Create",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
