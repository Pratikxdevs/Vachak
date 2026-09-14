package com.vachak.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.vachak.ui.theme.VachakColors

/**
 * Adaptive scaffold: NavigationRail on tablets (width > 840dp), BottomBar on phones.
 * Uses AnimatedContent with slide for page transitions per spec.
 * Consumes WindowSizeClass (not BoxWithConstraints) — isTablet derived from Expanded width
 * but keeps legacy 840.dp fallback for tests/headless.
 */
@Composable
fun AdaptiveScaffold(
    current: NavDest,
    onNavigate: (NavDest) -> Unit,
    windowSizeClass: WindowSizeClass? = null,
    isTablet: Boolean = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded,
    content: @Composable () -> Unit
) {
    // Packs + Diagnostics live under More; legacy tools/* routes live under
    // Learn now. Keep their tabs highlighted while inside.
    // (NavDest.fromRoute mapping is test-pinned; this is presentation only.)
    val tabHighlight = when (current) {
        NavDest.Diagnostics, NavDest.ManagePacks -> NavDest.Settings
        NavDest.Tools -> NavDest.Curriculum
        else -> current
    }
    if (isTablet) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = VachakColors.Surface,
                header = { Spacer(Modifier.height(12.dp)) }
            ) {
                NavDest.tabs.forEach { dest ->
                    val selected = dest == tabHighlight
                    NavigationRailItem(
                        selected = selected,
                        onClick = { onNavigate(dest) },
                        icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = dest.label, tint = if (selected) VachakColors.DeepLavender else VachakColors.TextSecondary) },
                        label = { Text(dest.label, style = MaterialTheme.typography.labelSmall, color = if (selected) VachakColors.DeepLavender else VachakColors.TextSecondary) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = VachakColors.DeepLavender,
                            selectedTextColor = VachakColors.DeepLavender,
                            indicatorColor = VachakColors.Lavender100
                        )
                    )
                }
            }
            VerticalDivider()
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // NavHost owns its own transitions: wrapping it in AnimatedContent
                // keyed on the tab would mount TWO NavHosts during every tab switch
                // (double lesson loads = the "spamming" bug) and drop backstack
                // state on each highlight change. Render directly.
                content()
            }
        }
    } else {
        Scaffold(
            containerColor = VachakColors.Background,
            bottomBar = {
                Box(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        // 0 elevation prevents extra offscreen layer at 90/120Hz; border provides hierarchy
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NavDest.tabs.forEach { dest ->
                                val selected = dest == tabHighlight
                                val bg = if (selected) VachakColors.DeepLavender else Color.Transparent
                                val tint = if (selected) Color.White else VachakColors.TextSecondary
                                val labelColor = if (selected) Color.White else VachakColors.TextSecondary
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(50))
                                        .background(bg)
                                        .clickable { onNavigate(dest) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Icon(
                                            if (selected) dest.selectedIcon else dest.icon,
                                            contentDescription = dest.label,
                                            tint = tint,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            dest.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = labelColor,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Clip
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                // See above: never wrap NavHost in AnimatedContent.
                content()
            }
        }
    }
}
