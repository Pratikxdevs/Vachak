package com.vachak.ui.theme

import androidx.compose.ui.graphics.Color

// — Material Theme Builder export — Lavender palette (primary #69548D)
val primaryLight = Color(0xFF69548D)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFECDCFF)
val onPrimaryContainerLight = Color(0xFF513C73)
val secondaryLight = Color(0xFF635B70)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFEADEF7)
val onSecondaryContainerLight = Color(0xFF4B4357)
val tertiaryLight = Color(0xFF6B538C)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFEDDCFF)
val onTertiaryContainerLight = Color(0xFF533C73)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF93000A)
val backgroundLight = Color(0xFFFEF7FF)
val onBackgroundLight = Color(0xFF1D1A20)
val surfaceLight = Color(0xFFF5FAFB)
val onSurfaceLight = Color(0xFF171D1E)
val surfaceVariantLight = Color(0xFFDBE4E6)
val onSurfaceVariantLight = Color(0xFF3F484A)
val outlineLight = Color(0xFF6F797A)
val outlineVariantLight = Color(0xFFBFC8CA)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF2B3133)
val inverseOnSurfaceLight = Color(0xFFECF2F3)
val inversePrimaryLight = Color(0xFFD4BBFC)
val surfaceDimLight = Color(0xFFD5DBDC)
val surfaceBrightLight = Color(0xFFF5FAFB)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFEFF5F6)
val surfaceContainerLight = Color(0xFFE9EFF0)
val surfaceContainerHighLight = Color(0xFFE3E9EA)
val surfaceContainerHighestLight = Color(0xFFDEE3E5)

val primaryDark = Color(0xFFD4BBFC)
val onPrimaryDark = Color(0xFF3A255B)
val primaryContainerDark = Color(0xFF513C73)
val onPrimaryContainerDark = Color(0xFFECDCFF)
val secondaryDark = Color(0xFFCEC2DB)
val onSecondaryDark = Color(0xFF342D40)
val secondaryContainerDark = Color(0xFF4B4357)
val onSecondaryContainerDark = Color(0xFFEADEF7)
val tertiaryDark = Color(0xFFD7BBFB)
val onTertiaryDark = Color(0xFF3B255A)
val tertiaryContainerDark = Color(0xFF533C73)
val onTertiaryContainerDark = Color(0xFFEDDCFF)
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF151218)
val onBackgroundDark = Color(0xFFE7E0E8)
val surfaceDark = Color(0xFF0E1415)
val onSurfaceDark = Color(0xFFDEE3E5)
val surfaceVariantDark = Color(0xFF3F484A)
val onSurfaceVariantDark = Color(0xFFBFC8CA)
val outlineDark = Color(0xFF899294)
val outlineVariantDark = Color(0xFF3F484A)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFDEE3E5)
val inverseOnSurfaceDark = Color(0xFF2B3133)
val inversePrimaryDark = Color(0xFF69548D)
val surfaceDimDark = Color(0xFF0E1415)
val surfaceBrightDark = Color(0xFF343A3B)
val surfaceContainerLowestDark = Color(0xFF090F10)
val surfaceContainerLowDark = Color(0xFF171D1E)
val surfaceContainerDark = Color(0xFF1B2122)
val surfaceContainerHighDark = Color(0xFF252B2C)
val surfaceContainerHighestDark = Color(0xFF303637)

// — Vachak Design System — Warm Classroom identity (deep pine + marigold on warm paper)
// NOTE: field names (Lavender*, Forest, PalashOrange) are legacy and kept to avoid
// churn across ~470 call sites — values are the new palette, NOT lavender.
// Mapped to Material Theme where possible; retains custom #FAF6EE background.
object VachakColors {
    val Background = Color(0xFFFAF6EE) // warm paper
    val Surface = Color(0xFFFFFFFF)
    val SoftLavender = Color(0xFFF2EDE0) // warm sand tint
    val Lavender100 = Color(0xFFDCEFE6) // maps to primaryContainerLight
    val Lavender200 = Color(0xFFC2E2D3)
    val Lavender300 = Color(0xFF9CD2B9)
    val Lavender400 = Color(0xFF63B393)
    val Lavender500 = Color(0xFF3E9B78)
    val Lavender600 = Color(0xFF2C7D60) // brand mid
    val Lavender700 = Color(0xFF235F4B)
    val DeepLavender = Color(0xFF1D5B46) // brand primary — deep pine
    val TextPrimary = Color(0xFF201A13)
    val TextSecondary = Color(0xFF5C5346)
    val Border = Color(0xFFE6DDC9)
    val PrimaryDark = Color(0xFF211B12) // warm-black CTA
    val OnPrimaryDark = Color.White
    val Accent = Color(0xFFE59A12) // marigold — highlights, progress, badges only
    val AccentDeep = Color(0xFF9A5F06) // marigold text on light surfaces
    val AccentLight = Color(0xFFFFEFC7) // marigold tint surface
    val Success = Color(0xFF2E7D32)
    val SuccessLight = Color(0xFFE4F2E5)
    val Amber = Color(0xFFE59A12)
    val ErrorRed = Color(0xFFBA1A1A) // errorLight
    // Legacy aliases
    val Forest = DeepLavender
    val ForestDark = Color(0xFF14342A)
    val ForestLight = Lavender600
    val PalashOrange = Accent
    val PalashOrangeDark = AccentDeep
    val PaperWhite = Background
    val PaperWhiteDark = SoftLavender
    val OfflineGreen = Success
    val OfflineGreenLight = SuccessLight
    val OnForest = Color.White
    val OnPaper = TextPrimary
    val Outline = Border
    val OutlineVariant = Lavender200
    val SurfaceVariant = Lavender100
}
