package com.vachak.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tablet gutter: on phones returns [base]; on wide screens (>= [maxContent]dp)
 * returns half the excess, so content columns never stretch past [maxContent]
 * and line length stays readable on 10" tablets. Apply to horizontal content
 * padding of scroll containers (headers keep edge chrome).
 */
@Composable
fun tabletHPad(base: Dp = 24.dp, maxContent: Int = 840): Dp {
    val w = LocalConfiguration.current.screenWidthDp
    if (w <= maxContent) return base
    return remember(w, base) { (((w - maxContent) / 2).dp).coerceAtLeast(base) }
}

/** True on tablets / wide screens (rail nav, roomier spacing). */
@Composable
fun isWideScreen(): Boolean {
    val w = LocalConfiguration.current.screenWidthDp
    return remember(w) { w >= 840 }
}
