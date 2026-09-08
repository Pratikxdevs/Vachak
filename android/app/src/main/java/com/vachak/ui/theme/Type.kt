package com.vachak.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vachak.R

// Offline-bundled fonts — no runtime network (AGENTS.md: fully offline after sync, Android 9+)
// Lexend variable TTF covers W100-W900, used for English/Latin
// Noto Sans Devanagari for Hindi (U+0900–U+097F) — 4 weights
// Noto Sans Ol Chiki for Santali U+1C50–U+1C7F (MTB-MLE: honor community script, not Devanagari transliteration)
// All OFL, ~1.1MB total, within 500MB budget

val LexendFamily = FontFamily(
    Font(R.font.lexend, weight = FontWeight.W400),
    Font(R.font.lexend, weight = FontWeight.W500),
    Font(R.font.lexend, weight = FontWeight.W600),
    Font(R.font.lexend, weight = FontWeight.W700)
)

val NotoDevanagariFamily = FontFamily(
    Font(R.font.noto_sans_devanagari, weight = FontWeight.W400),
    Font(R.font.noto_sans_devanagari_medium, weight = FontWeight.W500),
    Font(R.font.noto_sans_devanagari_semibold, weight = FontWeight.W600),
    Font(R.font.noto_sans_devanagari_bold, weight = FontWeight.W700)
)

val NotoOlChikiFamily = FontFamily(
    Font(R.font.noto_sans_ol_chiki, weight = FontWeight.W400)
)

// Composite fallback: Lexend (Latin) → Noto Devanagari (Hindi) → Noto Ol Chiki (Santali U+1C50-1C7F)
// Compose resolves glyphs by fallback order; Noto Ol Chiki ensures no tofu on Android 9 tablets
val VachakFontFamily = FontFamily(
    Font(R.font.lexend, weight = FontWeight.W400),
    Font(R.font.noto_sans_devanagari, weight = FontWeight.W400),
    Font(R.font.noto_sans_ol_chiki, weight = FontWeight.W400),
    Font(R.font.lexend, weight = FontWeight.W500),
    Font(R.font.noto_sans_devanagari_medium, weight = FontWeight.W500),
    Font(R.font.lexend, weight = FontWeight.W600),
    Font(R.font.noto_sans_devanagari_semibold, weight = FontWeight.W600),
    Font(R.font.lexend, weight = FontWeight.W700),
    Font(R.font.noto_sans_devanagari_bold, weight = FontWeight.W700)
)

val VachakTypography = Typography(
    displayLarge = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W700, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W600, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W600, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W600, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W500, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W600, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W600, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W500, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W500, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W500, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W500, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = VachakFontFamily, fontWeight = FontWeight.W500, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
