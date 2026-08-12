package com.example.audio

import com.example.data.MidiNote
import com.example.data.TimedSyllable
import java.io.InputStream
import java.nio.charset.Charset

data class MidiParseResult(
    val notes: List<MidiNote>,
    val syllables: List<TimedSyllable>,
    val lyricsText: String,
    val durationMs: Long
)

object MidiParser {

    fun parseMidiOrKar(inputStream: InputStream): MidiParseResult {
        val bytes = inputStream.readBytes()
        if (bytes.size < 14) {
            return MidiParseResult(emptyList(), emptyList(), "", 180000L)
        }

        // Check header "MThd"
        if (bytes[0] != 'M'.toByte() || bytes[1] != 'T'.toByte() || bytes[2] != 'h'.toByte() || bytes[3] != 'd'.toByte()) {
            return MidiParseResult(emptyList(), emptyList(), "", 180000L)
        }

        val format = readShortBE(bytes, 8).toInt()
        val numTracks = readShortBE(bytes, 10).toInt()
        val division = readShortBE(bytes, 12).toInt()

        if (division <= 0) {
            return MidiParseResult(emptyList(), emptyList(), "", 180000L)
        }

        val ticksPerQuarter = division.toDouble()

        // 1. First pass: Collect track data and tempo changes
        val tempoChanges = mutableListOf<Pair<Long, Long>>() // (tick, usPerQuarter)
        tempoChanges.add(0L to 500000L) // Default 120 BPM

        val rawTracks = mutableListOf<ByteArray>()
        var offset = 14

        for (i in 0 until numTracks) {
            if (offset + 8 > bytes.size) break
            val trackHeader = String(bytes, offset, 4)
            val trackLen = readIntBE(bytes, offset + 4)
            offset += 8

            if (trackHeader == "MTrk" && offset + trackLen <= bytes.size) {
                val trackData = bytes.copyOfRange(offset, offset + trackLen)
                rawTracks.add(trackData)
                
                // Parse tempo events from this track
                parseTempoEvents(trackData, tempoChanges)
            }
            offset += trackLen
        }

        // Sort tempo changes by tick
        tempoChanges.sortBy { it.first }

        // Helper function to convert tick to milliseconds
        fun tickToMs(tick: Long): Long {
            var totalMs = 0.0
            var currentTick = 0L
            var currentUsPerQuarter = 500000L

            for ((t, us) in tempoChanges) {
                if (tick <= t) break
                val deltaTicks = t - currentTick
                totalMs += (deltaTicks.toDouble() * currentUsPerQuarter) / (ticksPerQuarter * 1000.0)
                currentTick = t
                currentUsPerQuarter = us
            }

            if (tick > currentTick) {
                val deltaTicks = tick - currentTick
                totalMs += (deltaTicks.toDouble() * currentUsPerQuarter) / (ticksPerQuarter * 1000.0)
            }

            return totalMs.toLong()
        }

        // 2. Second pass: Parse notes and lyric/text events across all tracks
        val notes = mutableListOf<MidiNote>()
        val rawLyrics = mutableListOf<Triple<Long, String, Boolean>>() // (ms, text, isLineBreak)

        for (trackData in rawTracks) {
            parseTrackEvents(trackData, ::tickToMs, notes, rawLyrics)
        }

        // Sort notes by start time
        notes.sortBy { it.startTimeMs }

        // 3. Process raw lyrics into TimedSyllables
        val syllables = mutableListOf<TimedSyllable>()
        val fullLyricsBuilder = StringBuilder()

        var currentLineIndex = 0
        var currentSyllableIndex = 0
        var lineHasContent = false

        for ((timeMs, rawText, isExplicitLineBreak) in rawLyrics) {
            var text = rawText

            // Handle KAR headers (@T, @L, @K, @V)
            if (text.startsWith("@")) continue

            var isLineBreak = isExplicitLineBreak
            if (text.startsWith("\\") || text.startsWith("/")) {
                isLineBreak = true
                text = text.substring(1)
            }

            if (isLineBreak && lineHasContent) {
                currentLineIndex++
                currentSyllableIndex = 0
                fullLyricsBuilder.append("\n")
                lineHasContent = false
            }

            val cleanText = text.trim()
            if (cleanText.isNotEmpty()) {
                syllables.add(
                    TimedSyllable(
                        lineIndex = currentLineIndex,
                        syllableIndex = currentSyllableIndex++,
                        text = cleanText,
                        startTimeMs = timeMs,
                        endTimeMs = timeMs + 500L
                    )
                )
                fullLyricsBuilder.append(text)
                lineHasContent = true
            }
        }

        // Fix endTimeMs of syllables to align smoothly with next syllable start
        for (i in 0 until syllables.size - 1) {
            val curr = syllables[i]
            val next = syllables[i + 1]
            if (curr.lineIndex == next.lineIndex) {
                val duration = (next.startTimeMs - curr.startTimeMs).coerceAtLeast(100L)
                syllables[i] = curr.copy(endTimeMs = curr.startTimeMs + duration)
            } else {
                syllables[i] = curr.copy(endTimeMs = curr.startTimeMs + 600L)
            }
        }

        val maxNoteTime = notes.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 180000L
        val maxLyricTime = syllables.maxOfOrNull { it.endTimeMs } ?: 180000L
        val totalDurationMs = maxOf(maxNoteTime, maxLyricTime, 30000L)

        return MidiParseResult(
            notes = notes,
            syllables = syllables,
            lyricsText = fullLyricsBuilder.toString().trim(),
            durationMs = totalDurationMs
        )
    }

    private fun parseTempoEvents(trackData: ByteArray, tempoChanges: MutableList<Pair<Long, Long>>) {
        var pos = 0
        var currentTick = 0L
        var runningStatus = 0

        while (pos < trackData.size) {
            val (delta, bytesRead) = readVlq(trackData, pos)
            pos += bytesRead
            currentTick += delta

            if (pos >= trackData.size) break
            var status = trackData[pos].toInt() and 0xFF

            if (status == 0xFF) {
                // Meta Event
                pos++
                if (pos >= trackData.size) break
                val metaType = trackData[pos].toInt() and 0xFF
                pos++
                val (len, lenBytes) = readVlq(trackData, pos)
                pos += lenBytes

                if (metaType == 0x51 && len == 3L && pos + 3 <= trackData.size) {
                    val usPerQuarter = ((trackData[pos].toLong() and 0xFF) shl 16) or
                            ((trackData[pos + 1].toLong() and 0xFF) shl 8) or
                            (trackData[pos + 2].toLong() and 0xFF)
                    if (usPerQuarter > 0) {
                        tempoChanges.add(currentTick to usPerQuarter)
                    }
                }
                pos += len.toInt()
            } else if (status == 0xF0 || status == 0xF7) {
                // Sysex
                pos++
                val (len, lenBytes) = readVlq(trackData, pos)
                pos += lenBytes + len.toInt()
            } else {
                if (status < 0x80) {
                    status = runningStatus
                } else {
                    pos++
                    runningStatus = status
                }

                val cmd = status and 0xF0
                when (cmd) {
                    0x80, 0x90, 0xA0, 0xB0, 0xE0 -> pos += 2
                    0xC0, 0xD0 -> pos += 1
                }
            }
        }
    }

    private fun parseTrackEvents(
        trackData: ByteArray,
        tickToMs: (Long) -> Long,
        notes: MutableList<MidiNote>,
        rawLyrics: MutableList<Triple<Long, String, Boolean>>
    ) {
        var pos = 0
        var currentTick = 0L
        var runningStatus = 0

        // Channel -> Pitch -> Note Start Time Tick
        val activeNotes = Array(16) { HashMap<Int, Long>() }

        while (pos < trackData.size) {
            val (delta, bytesRead) = readVlq(trackData, pos)
            pos += bytesRead
            currentTick += delta

            if (pos >= trackData.size) break
            var status = trackData[pos].toInt() and 0xFF

            if (status == 0xFF) {
                // Meta Event
                pos++
                if (pos >= trackData.size) break
                val metaType = trackData[pos].toInt() and 0xFF
                pos++
                val (len, lenBytes) = readVlq(trackData, pos)
                pos += lenBytes

                if ((metaType == 0x01 || metaType == 0x05) && len > 0 && pos + len.toInt() <= trackData.size) {
                    // Lyric (0x05) or Text (0x01)
                    val textBytes = trackData.copyOfRange(pos, pos + len.toInt())
                    var textStr = try {
                        String(textBytes, Charsets.UTF_8)
                    } catch (e: Exception) {
                        String(textBytes, Charset.forName("ISO-8859-1"))
                    }

                    val ms = tickToMs(currentTick)
                    val isLineBreak = textStr.startsWith("\\") || textStr.startsWith("/")
                    rawLyrics.add(Triple(ms, textStr, isLineBreak))
                }
                pos += len.toInt()
            } else if (status == 0xF0 || status == 0xF7) {
                pos++
                val (len, lenBytes) = readVlq(trackData, pos)
                pos += lenBytes + len.toInt()
            } else {
                if (status < 0x80) {
                    status = runningStatus
                } else {
                    pos++
                    runningStatus = status
                }

                val cmd = status and 0xF0
                val channel = status and 0x0F

                when (cmd) {
                    0x90 -> { // Note On
                        if (pos + 1 < trackData.size) {
                            val pitch = trackData[pos].toInt() and 0xFF
                            val velocity = trackData[pos + 1].toInt() and 0xFF
                            pos += 2

                            if (velocity > 0) {
                                activeNotes[channel][pitch] = currentTick
                            } else {
                                // Velocity 0 = Note Off
                                val startTick = activeNotes[channel].remove(pitch)
                                if (startTick != null) {
                                    val startMs = tickToMs(startTick)
                                    val endMs = tickToMs(currentTick)
                                    val dur = (endMs - startMs).coerceAtLeast(50L)
                                    notes.add(MidiNote(pitch, startMs, dur))
                                }
                            }
                        }
                    }
                    0x80 -> { // Note Off
                        if (pos + 1 < trackData.size) {
                            val pitch = trackData[pos].toInt() and 0xFF
                            pos += 2
                            val startTick = activeNotes[channel].remove(pitch)
                            if (startTick != null) {
                                val startMs = tickToMs(startTick)
                                val endMs = tickToMs(currentTick)
                                val dur = (endMs - startMs).coerceAtLeast(50L)
                                notes.add(MidiNote(pitch, startMs, dur))
                            }
                        }
                    }
                    0xA0, 0xB0, 0xE0 -> pos += 2
                    0xC0, 0xD0 -> pos += 1
                }
            }
        }
    }

    private fun readVlq(data: ByteArray, startPos: Int): Pair<Long, Int> {
        var value = 0L
        var bytesRead = 0
        var pos = startPos

        while (pos < data.size) {
            val b = data[pos].toInt() and 0xFF
            pos++
            bytesRead++
            value = (value shl 7) or (b and 0x7F).toLong()
            if ((b and 0x80) == 0) break
        }

        return Pair(value, bytesRead)
    }

    private fun readShortBE(bytes: ByteArray, offset: Int): Short {
        if (offset + 1 >= bytes.size) return 0
        return (((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)).toShort()
    }

    private fun readIntBE(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
    }
}
