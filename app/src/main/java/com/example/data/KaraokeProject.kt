package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Entity(tableName = "karaoke_projects")
data class KaraokeProject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val lastModified: Long = System.currentTimeMillis(),
    val lyricsText: String = "",
    val timedSyllablesJson: String = "[]",
    val audioFileName: String = "Bèo Dạt Mây Trôi",
    val audioDurationMs: Long = 180000L,
    val backgroundType: String = "CHECKERBOARD", // CHECKERBOARD, SOLID_GREEN, BLACK, GRADIENT
    val fontName: String = "Default",
    val fontSize: Float = 28f,
    val textColorIdle: Long = 4294967295L, // White (0xFFFFFFFF)
    val textColorActive: Long = 4294901760L, // Red (0xFFFF0000)
    val strokeColor: Long = 4278190080L, // Black (0xFF000000)
    val strokeWidth: Float = 6f,
    val shadowColor: Long = 2281701376L, // Semi-trans Black (0x88000000)
    val shadowRadius: Float = 6f,
    val soundfontPath: String = "",
    val screenPreset: String = "HDV 1080 (1920x1080)",
    val layoutMode: String = "Two Rows", // "Two Rows", "One Row", "Centered"
    val row1Align: String = "Left", // "Left", "Center", "Right"
    val row1OffsetY: Float = 0f,
    val row2Align: String = "Right", // "Left", "Center", "Right"
    val row2OffsetY: Float = 0f,
    val stepInMs: Long = 2000L, // Step In lead-in time (msec)
    val stepOutMs: Long = 2000L, // Step Out lead-out time (msec)
    val globalOffsetMs: Long = 0L, // Global sync offset shift (msec)
    val enableSignals: Boolean = true, // Countdown signal dots before line
    val signalDotsCount: Int = 4, // Number of countdown dots
    val signalColor: Long = 4278190335L, // Blue (0xFF0000FF)
    val signalDurationMs: Long = 4000L // Countdown duration (4000ms)
)

data class TimedSyllable(
    val lineIndex: Int,
    val syllableIndex: Int,
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val joinWithNext: Boolean = false
)

object JsonHelper {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, TimedSyllable::class.java)
    private val adapter = moshi.adapter<List<TimedSyllable>>(listType)

    fun toJson(syllables: List<TimedSyllable>): String {
        return try {
            adapter.toJson(syllables) ?: "[]"
        } catch (e: Exception) {
            "[]"
        }
    }

    fun fromJson(json: String): List<TimedSyllable> {
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
