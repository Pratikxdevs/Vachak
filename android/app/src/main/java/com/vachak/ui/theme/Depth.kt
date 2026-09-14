package com.vachak.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Light-first depth language (classroom tablets, glare, battery).
 *
 * Rules, not vibes:
 * - Elevation comes from LIGHT + SHADOW together: each raised layer is a
 *   lighter surface step (see [VachakLayer]) plus a pine-tinted shadow.
 * - Recessed content (tables, progress beds, log wells) goes DARKER, never
 *   lighter — [VachakLayer.Well] with a hairline border, zero shadow.
 * - Selected/active elements are ELEVATED (shadow up + tinted surface), never
 *   just recolored — elevation is the "this matters" signal.
 * - All colors resolve from MaterialTheme.colorScheme, so a future dark theme
 *   inherits the same layer relationships without per-screen changes.
 */
object VachakDepth {
    /** Resting cards, list rows, section containers. */
    val Card: Dp = 3.dp
    /** Selected cards, active tabs, primary CTAs, bottom sheets. */
    val Raised: Dp = 10.dp
    /** Dialogs, menus, drag handles, anything above a scrim. */
    val Overlay: Dp = 16.dp
    /** Hairline for recessed wells and dividers on sand. */
    val WellBorder: Dp = 1.dp
}

/** Layered-surface roles. Page < Section < Card; Well is recessed below Page. */
enum class VachakLayer {
    Page, Section, Card, Well
}

@Composable
@ReadOnlyComposable
fun VachakLayer.color(): Color = when (this) {
    VachakLayer.Page -> MaterialTheme.colorScheme.background
    VachakLayer.Section -> MaterialTheme.colorScheme.surfaceContainerLow
    VachakLayer.Card -> MaterialTheme.colorScheme.surfaceContainerLowest
    VachakLayer.Well -> MaterialTheme.colorScheme.surfaceContainerHigh
}

/**
 * Pine-tinted shadow: warm ambient (black, faint) + pine spot (brand depth).
 * Single call site for every shadow in the app — tune here, change everywhere.
 */
fun Modifier.vachakShadow(
    elevation: Dp,
    shape: Shape = RoundedCornerShape(20.dp),
    spotAlpha: Float = 0.14f,
    ambientAlpha: Float = 0.08f
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = Color.Black.copy(alpha = ambientAlpha),
    spotColor = VachakColors.DeepLavender.copy(alpha = spotAlpha)
)

/** Resting card shadow. */
fun Modifier.cardShadow(shape: Shape = RoundedCornerShape(20.dp)): Modifier =
    vachakShadow(VachakDepth.Card, shape)

/** Selected / active / CTA shadow — visibly lifted. */
fun Modifier.raisedShadow(shape: Shape = RoundedCornerShape(20.dp)): Modifier =
    vachakShadow(VachakDepth.Raised, shape, spotAlpha = 0.18f)

/** Dialog / sheet shadow. */
fun Modifier.overlayShadow(shape: Shape = RoundedCornerShape(28.dp)): Modifier =
    vachakShadow(VachakDepth.Overlay, shape, spotAlpha = 0.20f)
