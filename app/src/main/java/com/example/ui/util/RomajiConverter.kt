package com.example.ui.util

/**
 * Utility for converting Romanized Japanese (Romaji) to Hiragana and Katakana.
 * Handles sokuon (double consonants -> っ/ッ), digraphs (kya, sho, chu, etc.), and single kana.
 */
object RomajiConverter {

    private val ROMAJI_TO_HIRAGANA_MAP = linkedMapOf(
        // Three-letter combinations (digraphs + long sounds)
        "kya" to "きゃ", "kyu" to "きゅ", "kyo" to "きょ",
        "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ", "shi" to "し",
        "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ", "chi" to "ち",
        "tsu" to "つ",
        "nya" to "にゃ", "nyu" to "にゅ", "nyo" to "にょ",
        "hya" to "ひゃ", "hyu" to "ひゅ", "hyo" to "ひょ",
        "mya" to "みゃ", "myu" to "みゅ", "myo" to "みょ",
        "rya" to "りゃ", "ryu" to "りゅ", "ryo" to "りょ",
        "gya" to "ぎゃ", "gyu" to "ぎゅ", "gyo" to "ぎょ",
        "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ", "ji" to "じ",
        "bya" to "びゃ", "byu" to "びゅ", "byo" to "びょ",
        "pya" to "ぴゃ", "pyu" to "ぴゅ", "pyo" to "ぴょ",

        // Two-letter combinations
        "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
        "sa" to "さ", "si" to "し", "su" to "す", "se" to "せ", "so" to "そ",
        "ta" to "た", "ti" to "ち", "tu" to "つ", "te" to "て", "to" to "と",
        "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
        "ha" to "は", "hi" to "ひ", "fu" to "ふ", "hu" to "ふ", "he" to "へ", "ho" to "ほ",
        "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も",
        "ya" to "や", "yu" to "ゆ", "yo" to "よ",
        "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
        "wa" to "わ", "wo" to "を",
        "ga" to "が", "gi" to "ぎ", "gu" to "ぐ", "ge" to "げ", "go" to "ご",
        "za" to "ざ", "zi" to "じ", "zu" to "ず", "ze" to "ぜ", "zo" to "ぞ",
        "da" to "だ", "di" to "ぢ", "du" to "づ", "de" to "で", "do" to "ど",
        "ba" to "ば", "bi" to "び", "bu" to "ぶ", "be" to "べ", "bo" to "ぼ",
        "pa" to "ぱ", "pi" to "ぴ", "pu" to "ぷ", "pe" to "ぺ", "po" to "ぽ",
        "nn" to "ん",

        // Single vowels and 'n'
        "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お",
        "n" to "ん"
    )

    private val ROMAJI_TO_KATAKANA_MAP = linkedMapOf(
        // Three-letter combinations
        "kya" to "キャ", "kyu" to "キュ", "kyo" to "キョ",
        "sha" to "シャ", "shu" to "シュ", "sho" to "ショ", "shi" to "シ",
        "cha" to "チャ", "chu" to "チュ", "cho" to "チョ", "chi" to "チ",
        "tsu" to "ツ",
        "nya" to "ニャ", "nyu" to "ニュ", "nyo" to "ニョ",
        "hya" to "ヒャ", "hyu" to "ヒュ", "hyo" to "ヒョ",
        "mya" to "ミャ", "myu" to "ミュ", "myo" to "ミョ",
        "rya" to "リャ", "ryu" to "リュ", "ryo" to "リョ",
        "gya" to "ギャ", "gyu" to "ギュ", "gyo" to "ギョ",
        "ja" to "ジャ", "ju" to "ジュ", "jo" to "ジョ", "ji" to "ジ",
        "bya" to "ビャ", "byu" to "ビュ", "byo" to "ビョ",
        "pya" to "ピャ", "pyu" to "ピュ", "pyo" to "ピョ",

        // Two-letter combinations
        "ka" to "カ", "ki" to "キ", "ku" to "ク", "ke" to "ケ", "ko" to "コ",
        "sa" to "サ", "si" to "シ", "su" to "ス", "se" to "セ", "so" to "ソ",
        "ta" to "タ", "ti" to "チ", "tu" to "ツ", "te" to "テ", "to" to "ト",
        "na" to "ナ", "ni" to "ニ", "nu" to "ヌ", "ne" to "ネ", "no" to "ノ",
        "ha" to "ハ", "hi" to "ヒ", "fu" to "フ", "hu" to "フ", "he" to "ヘ", "ho" to "ホ",
        "ma" to "マ", "mi" to "ミ", "mu" to "ム", "me" to "メ", "mo" to "モ",
        "ya" to "ヤ", "yu" to "ユ", "yo" to "ヨ",
        "ra" to "ラ", "ri" to "リ", "ru" to "ル", "re" to "レ", "ro" to "ロ",
        "wa" to "ワ", "wo" to "ヲ",
        "ga" to "ガ", "gi" to "ギ", "gu" to "グ", "ge" to "ゲ", "go" to "ゴ",
        "za" to "ザ", "zi" to "ジ", "zu" to "ズ", "ze" to "ゼ", "zo" to "ゾ",
        "da" to "ダ", "di" to "ヂ", "du" to "ヅ", "de" to "デ", "do" to "ド",
        "ba" to "バ", "bi" to "ビ", "bu" to "ブ", "be" to "ベ", "bo" to "ボ",
        "pa" to "パ", "pi" to "ピ", "pu" to "プ", "pe" to "ペ", "po" to "ポ",
        "nn" to "ン",

        // Single vowels and 'n'
        "a" to "ア", "i" to "イ", "u" to "ウ", "e" to "エ", "o" to "オ",
        "n" to "ン"
    )

    fun toHiragana(input: String): String {
        return convert(input, ROMAJI_TO_HIRAGANA_MAP, "っ")
    }

    fun toKatakana(input: String): String {
        return convert(input, ROMAJI_TO_KATAKANA_MAP, "ッ")
    }

    private fun convert(input: String, mapping: Map<String, String>, sokuonChar: String): String {
        if (input.isBlank()) return input

        val sb = StringBuilder()
        var i = 0
        val lower = input.lowercase()

        while (i < input.length) {
            val origChar = input[i]

            // If not ASCII letter, keep original character (punctuation, spaces, kanji, etc.)
            if (!origChar.isLetter() || origChar.code > 127) {
                sb.append(origChar)
                i++
                continue
            }

            // Check for sokuon (small tsu) like "kk", "tt", "ss", "pp" (except 'nn')
            if (i + 1 < input.length && lower[i] == lower[i + 1] && lower[i] !in listOf('a', 'e', 'i', 'o', 'u', 'n')) {
                sb.append(sokuonChar)
                i++
                continue
            }

            // Try 3-char match
            if (i + 3 <= input.length) {
                val sub3 = lower.substring(i, i + 3)
                if (mapping.containsKey(sub3)) {
                    sb.append(mapping[sub3])
                    i += 3
                    continue
                }
            }

            // Try 2-char match
            if (i + 2 <= input.length) {
                val sub2 = lower.substring(i, i + 2)
                if (mapping.containsKey(sub2)) {
                    sb.append(mapping[sub2])
                    i += 2
                    continue
                }
            }

            // Try 1-char match
            val sub1 = lower.substring(i, i + 1)
            if (mapping.containsKey(sub1)) {
                sb.append(mapping[sub1])
                i += 1
                continue
            }

            // Fallback: append original char
            sb.append(origChar)
            i++
        }

        return sb.toString()
    }
}
