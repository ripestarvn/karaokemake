package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.editor.EditorScreen
import com.example.ui.home.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.KaraokeViewModel

sealed class Screen {
    object Home : Screen()
    data class Editor(val projectId: Int) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(dynamicColor = false) {
                val viewModel: KaraokeViewModel = viewModel()
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                when (val screen = currentScreen) {
                    is Screen.Home -> {
                        val projects by viewModel.allProjects.collectAsState()
                        HomeScreen(
                            projects = projects,
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
                            onBackToHome = {
                                viewModel.stopPlayback()
                                viewModel.selectProject(-1) // resets activeProject selection safely
                                currentScreen = Screen.Home
                            }
                        )
                    }
                }
            }
        }
    }
}
