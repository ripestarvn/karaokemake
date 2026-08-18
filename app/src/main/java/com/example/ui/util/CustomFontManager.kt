package com.example.ui.util

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

data class CustomFontItem(
    val name: String,
    val file: File,
    val isCustom: Boolean = true
)

object CustomFontManager {

    private val _customFonts = MutableStateFlow<List<CustomFontItem>>(emptyList())
    val customFonts: StateFlow<List<CustomFontItem>> = _customFonts.asStateFlow()

    private val systemFonts = listOf(
        "Default",
        "SansSerif",
        "Serif",
        "Monospace",
        "Cursive"
    )

    fun initialize(context: Context) {
        loadCustomFonts(context)
    }

    private fun getFontsDir(context: Context): File {
        val dir = File(context.filesDir, "custom_fonts")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun loadCustomFonts(context: Context) {
        val dir = getFontsDir(context)
        val files = dir.listFiles { f ->
            val ext = f.extension.lowercase()
            ext in listOf("ttf", "otf", "ttc", "woff", "woff2")
        } ?: emptyArray()

        val list = files.map { file ->
            val cleanName = file.nameWithoutExtension.replace("_", " ")
            CustomFontItem(
                name = cleanName,
                file = file,
                isCustom = true
            )
        }.sortedBy { it.name }

        _customFonts.value = list
    }

    fun importFontFromUri(context: Context, uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            var fileName = "CustomFont_${System.currentTimeMillis()}.ttf"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) {
                        fileName = name
                    }
                }
            }

            // Ensure valid extension
            val lower = fileName.lowercase()
            if (!lower.endsWith(".ttf") && !lower.endsWith(".otf") && !lower.endsWith(".woff") && !lower.endsWith(".ttc")) {
                fileName += ".ttf"
            }

            val dir = getFontsDir(context)
            val destFile = File(dir, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Verify typeface can be loaded
            try {
                Typeface.createFromFile(destFile)
            } catch (e: Exception) {
                destFile.delete()
                AppLogger.error("FontManager", "Corrupt or invalid font file: ${e.message}")
                return null
            }

            loadCustomFonts(context)
            val cleanName = destFile.nameWithoutExtension.replace("_", " ")
            AppLogger.info("FontManager", "Successfully imported font: $cleanName")
            cleanName
        } catch (e: Exception) {
            AppLogger.error("FontManager", "Failed to import font: ${e.message}")
            null
        }
    }

    fun deleteCustomFont(context: Context, fontName: String): Boolean {
        val item = _customFonts.value.firstOrNull { it.name.equals(fontName, true) }
        return if (item != null && item.file.exists()) {
            val deleted = item.file.delete()
            loadCustomFonts(context)
            AppLogger.info("FontManager", "Deleted custom font: $fontName")
            deleted
        } else false
    }

    fun getAllFontNames(): List<String> {
        val customs = _customFonts.value.map { it.name }
        return systemFonts + customs
    }

    fun getComposeFontFamily(fontName: String, context: Context): FontFamily {
        return when (fontName) {
            "Default" -> FontFamily.Default
            "SansSerif" -> FontFamily.SansSerif
            "Serif" -> FontFamily.Serif
            "Monospace" -> FontFamily.Monospace
            "Cursive" -> FontFamily.Cursive
            else -> {
                val custom = _customFonts.value.firstOrNull { it.name.equals(fontName, true) }
                if (custom != null && custom.file.exists()) {
                    try {
                        FontFamily(Font(custom.file))
                    } catch (e: Exception) {
                        AppLogger.warn("FontManager", "Cannot load custom compose font $fontName: ${e.message}")
                        FontFamily.Default
                    }
                } else {
                    // Check if file exists directly in fonts dir
                    val dir = getFontsDir(context)
                    val directFile = File(dir, "$fontName.ttf").takeIf { it.exists() }
                        ?: File(dir, "$fontName.otf").takeIf { it.exists() }
                    if (directFile != null) {
                        try {
                            FontFamily(Font(directFile))
                        } catch (e: Exception) {
                            FontFamily.Default
                        }
                    } else {
                        FontFamily.Default
                    }
                }
            }
        }
    }

    fun getAndroidTypeface(fontName: String, context: Context): Typeface {
        return when (fontName) {
            "Default" -> Typeface.DEFAULT
            "SansSerif" -> Typeface.SANS_SERIF
            "Serif" -> Typeface.SERIF
            "Monospace" -> Typeface.MONOSPACE
            "Cursive" -> Typeface.create("cursive", Typeface.NORMAL)
            else -> {
                val custom = _customFonts.value.firstOrNull { it.name.equals(fontName, true) }
                if (custom != null && custom.file.exists()) {
                    try {
                        Typeface.createFromFile(custom.file)
                    } catch (e: Exception) {
                        AppLogger.warn("FontManager", "Cannot load custom android typeface $fontName: ${e.message}")
                        Typeface.DEFAULT
                    }
                } else {
                    val dir = getFontsDir(context)
                    val directFile = File(dir, "$fontName.ttf").takeIf { it.exists() }
                        ?: File(dir, "$fontName.otf").takeIf { it.exists() }
                    if (directFile != null) {
                        try {
                            Typeface.createFromFile(directFile)
                        } catch (e: Exception) {
                            Typeface.DEFAULT
                        }
                    } else {
                        Typeface.DEFAULT
                    }
                }
            }
        }
    }
}
