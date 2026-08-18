package com.example.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel(val label: String, val badgeColor: Long) {
    ACTION("ACTION", 0xFF0288D1),
    INFO("INFO", 0xFF4CAF50),
    WARN("WARN", 0xFFFF9800),
    ERROR("ERROR", 0xFFF44336)
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val timeFormatted: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val details: String? = null
)

object AppLogger {
    private const val MAX_LOGS = 500
    private val idCounter = AtomicLong(1)
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val exportDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private val logList = mutableListOf<LogEntry>()
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    init {
        log(LogLevel.INFO, "AppLogger", "Karaoke Studio Activity & Diagnostic Logger initialized")
    }

    @Synchronized
    fun log(level: LogLevel, tag: String, message: String, details: String? = null) {
        val now = System.currentTimeMillis()
        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = now,
            timeFormatted = dateFormat.format(Date(now)),
            level = level,
            tag = tag,
            message = message,
            details = details
        )

        // Native Android Logcat output without performance cost
        when (level) {
            LogLevel.ACTION -> Log.d("KaraokeStudio:$tag", "[ACTION] $message ${details ?: ""}")
            LogLevel.INFO -> Log.i("KaraokeStudio:$tag", "$message ${details ?: ""}")
            LogLevel.WARN -> Log.w("KaraokeStudio:$tag", "$message ${details ?: ""}")
            LogLevel.ERROR -> Log.e("KaraokeStudio:$tag", "$message ${details ?: ""}")
        }

        logList.add(0, entry) // Newest first
        if (logList.size > MAX_LOGS) {
            logList.removeAt(logList.lastIndex)
        }
        _logs.value = logList.toList()
    }

    fun action(tag: String, message: String, details: String? = null) =
        log(LogLevel.ACTION, tag, message, details)

    fun info(tag: String, message: String, details: String? = null) =
        log(LogLevel.INFO, tag, message, details)

    fun warn(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.WARN, tag, message, throwable?.stackTraceToString())

    fun error(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable?.stackTraceToString())

    @Synchronized
    fun clear() {
        logList.clear()
        _logs.value = emptyList()
        log(LogLevel.INFO, "AppLogger", "Logs cleared by user")
    }

    fun exportFormattedText(filterLevel: LogLevel? = null): String {
        val currentLogs = _logs.value
        val filtered = if (filterLevel == null) currentLogs else currentLogs.filter { it.level == filterLevel }
        val sb = java.lang.StringBuilder()
        sb.append("=== KARAOKE STUDIO ACTIVITY & SYSTEM LOGS ===\n")
        sb.append("Export Time: ${exportDateFormat.format(Date())}\n")
        sb.append("Total Entries: ${filtered.size}\n\n")

        filtered.reversed().forEach { entry ->
            val time = exportDateFormat.format(Date(entry.timestamp))
            sb.append("[$time] [${entry.level.name}] [${entry.tag}] ${entry.message}\n")
            if (!entry.details.isNullOrBlank()) {
                sb.append("  Details: ${entry.details}\n")
            }
        }
        return sb.toString()
    }

    fun copyToClipboard(context: Context, filterLevel: LogLevel? = null): Boolean {
        return try {
            val text = exportFormattedText(filterLevel)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Karaoke Studio Logs", text)
            clipboard.setPrimaryClip(clip)
            action("AppLogger", "Copied ${if (filterLevel == null) "all" else filterLevel.name} logs to clipboard (${text.length} chars)")
            true
        } catch (e: Exception) {
            error("AppLogger", "Failed to copy logs to clipboard", e)
            false
        }
    }
}
