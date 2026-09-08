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

// — Vachak Universal Design System — Soft Editorial Lavender (UI_REBUILD §11)
// Mapped to Material Theme where possible; retains custom #FAF9FF background for editorial feel
object VachakColors {
    val Background = Color(0xFFFAF9FF) // editorial, slightly lighter than backgroundLight #FEF7FF
    val Surface = Color(0xFFFFFFFF)
    val SoftLavender = Color(0xFFF7F2FF)
    val Lavender100 = Color(0xFFEEE5FF) // maps to primaryContainerLight #ECDCFF
    val Lavender200 = Color(0xFFE2D2F3)
    val Lavender300 = Color(0xFFD4BFF0) // maps to inversePrimaryLight #D4BBFC
    val Lavender400 = Color(0xFFC4A7E7)
    val Lavender500 = Color(0xFFA984D6)
    val Lavender600 = Color(0xFF8B6BB5) // maps to secondary #635B70 / primary #69548D
    val Lavender700 = Color(0xFF70539A)
    val DeepLavender = Color(0xFF69548D) // updated to primaryLight #69548D (was #3E3157)
    val TextPrimary = Color(0xFF1D1A20) // onBackgroundLight
    val TextSecondary = Color(0xFF49454E) // blended onSurfaceVariant
    val Border = Color(0xFFE7E1EF) // outlineVariant
    val PrimaryDark = Color(0xFF171717)
    val OnPrimaryDark = Color.White
    val Success = Color(0xFF2E7D32)
    val SuccessLight = Color(0xFFE8F5E9)
    val Amber = Color(0xFFF9A825)
    val ErrorRed = Color(0xFFBA1A1A) // errorLight
    // Legacy aliases
    val Forest = DeepLavender
    val ForestDark = Color(0xFF2A2040)
    val ForestLight = Lavender600
    val PalashOrange = Lavender500
    val PalashOrangeDark = Lavender700
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
