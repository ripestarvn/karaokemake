package com.example.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.example.ui.util.Localization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("karaoke_studio_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        try {
            ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(
        try {
            Localization.Language.valueOf(prefs.getString("app_language", Localization.Language.EN.name) ?: Localization.Language.EN.name)
        } catch (e: Exception) {
            Localization.Language.EN
        }
    )
    val language: StateFlow<Localization.Language> = _language.asStateFlow()

    private val _showWaveform = MutableStateFlow(
        prefs.getBoolean("show_waveform", true)
    )
    val showWaveform: StateFlow<Boolean> = _showWaveform.asStateFlow()

    private val _exportFolder = MutableStateFlow(
        prefs.getString("export_folder", "Movies/KaraokeStudio") ?: "Movies/KaraokeStudio"
    )
    val exportFolder: StateFlow<String> = _exportFolder.asStateFlow()

    private val _defaultSoundfont = MutableStateFlow(
        prefs.getString("default_soundfont", "") ?: ""
    )
    val defaultSoundfont: StateFlow<String> = _defaultSoundfont.asStateFlow()

    private val _showUsageNotesOnStartup = MutableStateFlow(
        prefs.getBoolean("show_usage_notes_startup", true)
    )
    val showUsageNotesOnStartup: StateFlow<Boolean> = _showUsageNotesOnStartup.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setLanguage(lang: Localization.Language) {
        _language.value = lang
        prefs.edit().putString("app_language", lang.name).apply()
    }

    fun setShowWaveform(show: Boolean) {
        _showWaveform.value = show
        prefs.edit().putBoolean("show_waveform", show).apply()
    }

    fun setExportFolder(folder: String) {
        _exportFolder.value = folder
        prefs.edit().putString("export_folder", folder).apply()
    }

    fun setDefaultSoundfont(path: String) {
        _defaultSoundfont.value = path
        prefs.edit().putString("default_soundfont", path).apply()
    }

    fun setShowUsageNotesOnStartup(show: Boolean) {
        _showUsageNotesOnStartup.value = show
        prefs.edit().putBoolean("show_usage_notes_startup", show).apply()
    }

    companion object {
        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
        }
    }
}
