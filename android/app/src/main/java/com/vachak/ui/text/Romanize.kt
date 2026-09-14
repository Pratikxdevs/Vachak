package com.vachak.ui.text

/**
 * Deterministic romanization for the conversation cards (mock fidelity: every
 * Hindi/Santali bubble shows a Latin gloss line underneath).
 *
 * Offline lookup tables, no models: Devanagari via a Hunterian-lite map,
 * Ol Chiki via the standard Santali Latin orthography (ᱚ=o, ᱟ=a, ᱮ=e,
 * ᱤ=i, ᱩ=u, ᱦ=h, ᱧ/ᱝ/ᱱ=n/ng, ᱨ=r, ᱞ=l, ᱥ=s, ᱫ=d, ᱜ=g, ᱠ=k, ᱪ=c, ᱡ=j,
 * ᱴ/ᱰ=t/d, ᱛ=t, ᱯ=p, ᱵ=b, ᱢ=m, ᱭ=y, ᱣ=w, ᱷ=h, digits ᱐-᱙=0-9).
 * Unknown codepoints pass through unchanged. Best-effort gloss, not a
 * transliteration standard — labeled as such in UI ("Sounds like").
 */
object Romanize {
    private val devaConsonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "c", 'छ' to "ch", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "w", 'ळ' to "l",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h"
    )
    private val devaVowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u",
        'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o",
        'औ' to "au", 'ऑ' to "o"
    )
    private val devaSigns = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
        'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
        'ं' to "n", 'ँ' to "n", 'ः' to "h", '्' to "", 'ॉ' to "o",
        'ॆ' to "e", 'ॊ' to "o", 'ॎ' to "", 'ॐ' to "om", 'ऽ' to "'"
    )
    private val devaDigits = mapOf(
        '०' to "0", '१' to "1", '२' to "2", '३' to "3", '४' to "4",
        '५' to "5", '६' to "6", '७' to "7", '८' to "8", '९' to "9"
    )
    private val olChiki = mapOf(
        'ᱚ' to "o", 'ᱟ' to "a", 'ᱮ' to "e", 'ᱤ' to "i", 'ᱩ' to "u",
        'ᱦ' to "h", 'ᱧ' to "nj", 'ᱝ' to "ng", 'ᱪ' to "c", 'ᱡ' to "j",
        'ᱴ' to "t", 'ᱰ' to "d", 'ᱬ' to "n", 'ᱛ' to "t", 'ᱫ' to "d",
        'ᱱ' to "n", 'ᱯ' to "p", 'ᱵ' to "b", 'ᱢ' to "m", 'ᱭ' to "y",
        'ᱨ' to "r", 'ᱞ' to "l", 'ᱣ' to "w", 'ᱥ' to "s", 'ᱷ' to "h",
        'ᱸ' to "n", 'ᱹ' to "n", 'ᱺ' to "h", 'ᱻ' to "'",
        '᱐' to "0", '᱑' to "1", '᱒' to "2", '᱓' to "3", '᱔' to "4",
        '᱕' to "5", '᱖' to "6", '᱗' to "7", '᱘' to "8", '᱙' to "9"
    )
    // Ol Chiki diacritic marks that modify (not replace) the previous letter.
    private val olMarks = mapOf(
        'ᱷ' to "h", 'ᱸ' to "n", 'ᱹ' to "n", 'ᱺ' to "h", 'ᱻ' to "'"
    )

    /** Romanize Devanagari (inherent-a handling: consonant + virama drops it). */
    fun devanagari(text: String): String {
        if (text.isBlank()) return ""
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val cons = devaConsonants[ch]
            if (cons != null) {
                val next = text.getOrNull(i + 1)
                when {
                    next == '्' -> { sb.append(cons); i += 2; continue }
                    next != null && devaSigns.containsKey(next) -> { sb.append(cons); /* sign handled below */ }
                    else -> sb.append(cons + "a")
                }
                i++
                continue
            }
            val vow = devaVowels[ch]
            if (vow != null) { sb.append(vow); i++; continue }
            val sign = devaSigns[ch]
            if (sign != null) {
                // Drop the inherent 'a' the consonant branch added, then apply sign.
                if (sb.endsWith("a") && sign.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
                sb.append(sign); i++; continue
            }
            val dig = devaDigits[ch]
            if (dig != null) { sb.append(dig); i++; continue }
            sb.append(ch); i++
        }
        // Schwa deletion at word ends (Hindi romanization): "कमल"→kamal,
        // "क्या"→kya, "आप"→aap. Guards keep "आ"→aa intact.
        return sb.toString().split(" ").joinToString(" ") { w ->
            var t = w
            if (t.length > 2 && t.endsWith("aa")) t = t.dropLast(1)
            if (t.length > 2 && t.endsWith("a") && t[t.length - 2] !in "aeiouAEIOU") t = t.dropLast(1)
            t
        }.replace(Regex("\\s+"), " ").trim()
    }

    /** Romanize Ol Chiki letter by letter (Santali Latin orthography). */
    fun olChiki(text: String): String {
        if (text.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in text) {
            val m = olChiki[ch]
            if (m != null) {
                if (ch in olMarks && sb.isNotEmpty()) {
                    // Marks like ᱷ (ud) aspirate the previous consonant: append.
                    if (sb.endsWith("a") && (ch == 'ᱷ' || ch == 'ᱺ')) sb.deleteCharAt(sb.length - 1)
                }
                sb.append(m)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /** Auto-pick by script: Ol Chiki range → Santali map, else Devanagari map. */
    fun auto(text: String): String {
        if (text.isBlank()) return ""
        return if (text.any { it.code in 0x1C50..0x1C7F }) olChiki(text) else devanagari(text)
    }
}
