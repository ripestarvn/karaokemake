package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.dialogs.SettingsDialog
import com.example.ui.dialogs.UsageNotesDialog
import com.example.ui.editor.EditorScreen
import com.example.ui.home.HomeScreen
import com.example.ui.settings.AppSettings
import com.example.ui.theme.KaraokeStudioTheme
import com.example.ui.util.CustomFontManager
import com.example.viewmodel.KaraokeViewModel

sealed class Screen {
    object Home : Screen()
    data class Editor(val projectId: Int) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CustomFontManager.initialize(this)
        val appSettings = AppSettings.getInstance(this)

        setContent {
            val themeMode by appSettings.themeMode.collectAsState()
            val currentLang by appSettings.language.collectAsState()
            val showUsageNotesStartup by appSettings.showUsageNotesOnStartup.collectAsState()

            var showSettingsDialog by remember { mutableStateOf(false) }
            var showUsageNotesDialog by remember { mutableStateOf(false) }

            // Auto show Usage Notes on launch if preference is enabled
            var hasCheckedStartupNotes by remember { mutableStateOf(false) }
            LaunchedEffect(showUsageNotesStartup) {
                if (!hasCheckedStartupNotes) {
                    hasCheckedStartupNotes = true
                    if (showUsageNotesStartup) {
                        showUsageNotesDialog = true
                    }
                }
            }

            KaraokeStudioTheme(themeMode = themeMode, dynamicColor = false) {
                val viewModel: KaraokeViewModel = viewModel()
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                BackHandler(enabled = currentScreen is Screen.Editor) {
                    viewModel.stopPlayback()
                    viewModel.selectProject(-1)
                    currentScreen = Screen.Home
                }

                when (val screen = currentScreen) {
                    is Screen.Home -> {
                        val projects by viewModel.allProjects.collectAsState()
                        HomeScreen(
                            projects = projects,
                            appSettings = appSettings,
                            onOpenSettings = { showSettingsDialog = true },
                            onOpenUsageNotes = { showUsageNotesDialog = true },
                            onCreateProject = { title, artist, lyrics, preset ->
                                viewModel.createAndSelectProject(title, artist, lyrics, preset)
                            },
                            onEditProject = { id ->
                                viewModel.selectProject(id)
                                currentScreen = Screen.Editor(id)
                            },
                            onDeleteProject = { id ->
                                viewModel.deleteProject(id)
                            }
                        )

                        // Trigger auto-navigation to editor on new project creation
                        val activeProject by viewModel.activeProject.collectAsState()
                        LaunchedEffect(activeProject) {
                            activeProject?.let {
                                if (currentScreen == Screen.Home) {
                                    currentScreen = Screen.Editor(it.id)
                                }
                            }
                        }
                    }
                    is Screen.Editor -> {
                        EditorScreen(
                            viewModel = viewModel,
                            appSettings = appSettings,
                            onOpenSettings = { showSettingsDialog = true },
                            onOpenUsageNotes = { showUsageNotesDialog = true },
                            onBackToHome = {
                                viewModel.stopPlayback()
                                viewModel.selectProject(-1) // resets activeProject selection safely
                                currentScreen = Screen.Home
                            }
                        )
                    }
                }

                if (showSettingsDialog) {
                    SettingsDialog(
                        appSettings = appSettings,
                        onDismiss = { showSettingsDialog = false },
                        onOpenUsageNotes = {
                            showSettingsDialog = false
                            showUsageNotesDialog = true
                        }
                    )
                }

                if (showUsageNotesDialog) {
                    UsageNotesDialog(
                        initialLanguage = currentLang,
                        onDismiss = { doNotShowAgain ->
                            if (doNotShowAgain) {
                                appSettings.setShowUsageNotesOnStartup(false)
                            }
                            showUsageNotesDialog = false
                        }
                    )
                }
            }
        }
    }
}
