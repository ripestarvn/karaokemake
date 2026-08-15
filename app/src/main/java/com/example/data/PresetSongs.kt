package com.example.data

object PresetSongs {
    val SONGS = listOf(
        PresetSong(
            title = "Bèo Dạt Mây Trôi",
            artist = "Dân Ca Quan Họ Bắc Ninh",
            lyrics = "Bèo dạt mây trôi chốn xa xôi\nAnh ơi em vẫn đợi bèo dạt\nMây trôi mấy cánh chim chim bay\nNgười ơi người ở đừng về",
            audioFileName = "Bèo Dạt Mây Trôi (Midi Synth)",
            durationMs = 32000L,
            syllables = listOf(
                // Line 0: "Bèo dạt mây trôi chốn xa xôi"
                TimedSyllable(0, 0, "Bèo", 1000L, 1800L),
                TimedSyllable(0, 1, "dạt", 1800L, 2600L),
                TimedSyllable(0, 2, "mây", 2600L, 3400L),
                TimedSyllable(0, 3, "trôi", 3400L, 4600L),
                TimedSyllable(0, 4, "chốn", 4600L, 5400L),
                TimedSyllable(0, 5, "xa", 5400L, 6200L),
                TimedSyllable(0, 6, "xôi", 6200L, 7800L),

                // Line 1: "Anh ơi em vẫn đợi bèo dạt"
                TimedSyllable(1, 0, "Anh", 8500L, 9300L),
                TimedSyllable(1, 1, "ơi", 9300L, 10100L),
                TimedSyllable(1, 2, "em", 10100L, 10900L),
                TimedSyllable(1, 3, "vẫn", 10900L, 11700L),
                TimedSyllable(1, 4, "đợi", 11700L, 12800L),
                TimedSyllable(1, 5, "bèo", 12800L, 13600L),
                TimedSyllable(1, 6, "dạt", 13600L, 14800L),

                // Line 2: "Mây trôi mấy cánh chim chim bay"
                TimedSyllable(2, 0, "Mây", 15500L, 16300L),
                TimedSyllable(2, 1, "trôi", 16300L, 17200L),
                TimedSyllable(2, 2, "mấy", 17200L, 18000L),
                TimedSyllable(2, 3, "cánh", 18000L, 18800L),
                TimedSyllable(2, 4, "chim", 18800L, 19600L),
                TimedSyllable(2, 5, "chim", 19600L, 20400L),
                TimedSyllable(2, 6, "bay", 20400L, 22000L),

                // Line 3: "Người ơi người ở đừng về"
                TimedSyllable(3, 0, "Người", 23000L, 23800L),
                TimedSyllable(3, 1, "ơi", 23800L, 24600L),
                TimedSyllable(3, 2, "người", 24600L, 25400L),
                TimedSyllable(3, 3, "ở", 25400L, 26200L),
                TimedSyllable(3, 4, "đừng", 26200L, 27400L),
                TimedSyllable(3, 5, "về", 27400L, 29800L)
            ),
            notes = listOf(
                // Simple pentatonic scale melody mapping (Middle C, D, E, G, A represented as MIDI pitches: 60, 62, 64, 67, 69)
                MidiNote(64, 1000L, 700L),  // Bèo (E)
                MidiNote(67, 1800L, 700L),  // dạt (G)
                MidiNote(69, 2600L, 700L),  // mây (A)
                MidiNote(72, 3400L, 1100L), // trôi (C)
                MidiNote(69, 4600L, 700L),  // chốn (A)
                MidiNote(67, 5400L, 700L),  // xa (G)
                MidiNote(64, 6200L, 1500L), // xôi (E)

                MidiNote(60, 8500L, 700L),  // Anh (C)
                MidiNote(62, 9300L, 700L),  // ơi (D)
                MidiNote(64, 10100L, 700L), // em (E)
                MidiNote(67, 10900L, 700L), // vẫn (G)
                MidiNote(64, 11700L, 1000L),// đợi (E)
                MidiNote(62, 12800L, 700L), // bèo (D)
                MidiNote(60, 13600L, 1100L),// dạt (C)

                MidiNote(64, 15500L, 700L), // Mây
                MidiNote(67, 16300L, 800L), // trôi
                MidiNote(69, 17200L, 700L), // mấy
                MidiNote(72, 18000L, 700L), // cánh
                MidiNote(69, 18800L, 700L), // chim
                MidiNote(67, 19600L, 700L), // chim
                MidiNote(64, 20400L, 1500L),// bay

                MidiNote(60, 23000L, 700L), // Người
                MidiNote(62, 23800L, 700L), // ơi
                MidiNote(64, 24600L, 700L), // người
                MidiNote(67, 25400L, 700L), // ở
                MidiNote(62, 26200L, 1100L),// đừng
                MidiNote(60, 27400L, 2200L) // về
            )
        ),
        PresetSong(
            title = "Happy Birthday",
            artist = "Universal",
            lyrics = "Happy birthday to you\nHappy birthday to you\nHappy birthday dear friend\nHappy birthday to you",
            audioFileName = "Happy Birthday (Synth)",
            durationMs = 15000L,
            syllables = listOf(
                TimedSyllable(0, 0, "Hap-", 500L, 900L),
                TimedSyllable(0, 1, "-py ", 900L, 1300L),
                TimedSyllable(0, 2, "birth-", 1300L, 1700L),
                TimedSyllable(0, 3, "-day ", 1700L, 2100L),
                TimedSyllable(0, 4, "to ", 2100L, 2700L),
                TimedSyllable(0, 5, "you", 2700L, 3700L),

                TimedSyllable(1, 0, "Hap-", 4000L, 4400L),
                TimedSyllable(1, 1, "-py ", 4400L, 4800L),
                TimedSyllable(1, 2, "birth-", 4800L, 5200L),
                TimedSyllable(1, 3, "-day ", 5200L, 5600L),
                TimedSyllable(1, 4, "to ", 5600L, 6200L),
                TimedSyllable(1, 5, "you", 6200L, 7200L),

                TimedSyllable(2, 0, "Hap-", 7500L, 7900L),
                TimedSyllable(2, 1, "-py ", 7900L, 8300L),
                TimedSyllable(2, 2, "birth-", 8300L, 8700L),
                TimedSyllable(2, 3, "-day ", 8700L, 9100L),
                TimedSyllable(2, 4, "dear ", 9100L, 9900L),
                TimedSyllable(2, 5, "friend", 9900L, 10700L),

                TimedSyllable(3, 0, "Hap-", 11000L, 11400L),
                TimedSyllable(3, 1, "-py ", 11400L, 11800L),
                TimedSyllable(3, 2, "birth-", 11800L, 12200L),
                TimedSyllable(3, 3, "-day ", 12200L, 12600L),
                TimedSyllable(3, 4, "to ", 12600L, 13200L),
                TimedSyllable(3, 5, "you", 13200L, 14200L)
            ),
            notes = listOf(
                MidiNote(60, 500L, 350L), MidiNote(60, 900L, 350L),
                MidiNote(62, 1300L, 350L), MidiNote(60, 1700L, 350L),
                MidiNote(65, 2100L, 500L), MidiNote(64, 2700L, 900L),

                MidiNote(60, 4000L, 350L), MidiNote(60, 4400L, 350L),
                MidiNote(62, 4800L, 350L), MidiNote(60, 5200L, 350L),
                MidiNote(67, 5600L, 500L), MidiNote(65, 6200L, 900L),

                MidiNote(60, 7500L, 350L), MidiNote(60, 7900L, 350L),
                MidiNote(72, 8300L, 350L), MidiNote(69, 8700L, 350L),
                MidiNote(65, 9100L, 700L), MidiNote(64, 9900L, 700L),

                MidiNote(70, 11000L, 350L), MidiNote(70, 11400L, 350L),
                MidiNote(69, 11800L, 350L), MidiNote(65, 12200L, 350L),
                MidiNote(67, 12600L, 500L), MidiNote(65, 13200L, 900L)
            )
        )
    )
}

data class PresetSong(
    val title: String,
    val artist: String,
    val lyrics: String,
    val audioFileName: String,
    val durationMs: Long,
    val syllables: List<TimedSyllable>,
    val notes: List<MidiNote>
)

data class MidiNote(
    val pitch: Int,
    val startTimeMs: Long,
    val durationMs: Long,
    val channel: Int = 0
)
