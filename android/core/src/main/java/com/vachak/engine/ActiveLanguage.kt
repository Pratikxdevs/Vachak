package com.vachak.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for active translation language.
 * Mundari = unr_Deva (Karya Flores, DEFAULT), Santali (Ol Chiki) = sat_Olck.
 * Exposes StateFlow so every screen reacts without hard-coded LanguagePair.
 * Persists via caller (EngineProvider/PackManager) if needed; in-memory default unr_Deva.
 */
object ActiveLanguage {
    private val _flow = MutableStateFlow("unr_Deva")
    val flow: StateFlow<String> = _flow.asStateFlow()

    val current: String get() = _flow.value

    fun set(lang: String) {
        val n = normalize(lang)
        if (n != _flow.value) _flow.value = n
    }

    fun pair(): LanguagePair = LanguagePair("hi", _flow.value)

    fun label(lang: String = _flow.value): String = when (normalize(lang)) {
        "sat_Olck" -> "Santali (Ol Chiki)"
        "unr_Deva" -> "Mundari"
        else -> lang
    }

    fun all(): List<Pair<String,String>> = listOf(
        "sat_Olck" to "Santali (Ol Chiki)",
        "unr_Deva" to "Mundari"
    )

    fun normalize(raw: String): String = when (raw.lowercase()) {
        "sat", "sat_olck", "sat-olck", "olck", "ol_ck", "sat_olchiki" -> "sat_Olck"
        "unr", "unr_deva", "mun", "mun_deva", "mundari", "mund" -> "unr_Deva"
        else -> raw
    }

    fun isOlChiki(lang: String = _flow.value): Boolean = normalize(lang) == "sat_Olck"
}
