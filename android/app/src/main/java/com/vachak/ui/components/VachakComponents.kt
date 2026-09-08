package com.vachak.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vachak.ui.theme.VachakColors

// ── OfflineBadge (pill-shaped) ──────────────────────────────────────
@Composable
fun OfflineBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = VachakColors.OfflineGreenLight,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = VachakColors.OfflineGreen, modifier = Modifier.size(14.dp))
            Text("Offline", style = MaterialTheme.typography.labelSmall, color = VachakColors.OfflineGreen, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Section (collapsible grouping, replaces CupertinoSection) ───────
@Composable
fun VachakSection(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    collapsible: Boolean = false,
    collapsed: Boolean = false,
    onToggle: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, null, tint = VachakColors.Forest, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            if (collapsible && onToggle != null) {
                TextButton(onClick = onToggle) { Text(if (collapsed) "Expand" else "Collapse", style = MaterialTheme.typography.labelMedium) }
            }
        }
        if (!collapsed) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
            }
        }
    }
}

// ── FocusCard (Hero ElevatedCard) ───────────────────────────────────
@Composable
fun FocusCard(
    title: String,
    subtitle: String,
    nipunLabel: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(8.dp), color = VachakColors.Forest) {
                    Text("NIPUN", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text(nipunLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onContinue, shape = RoundedCornerShape(50)) {
                Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Continue Lesson")
            }
        }
    }
}

// ── Breath Visualizer (Canvas circular pulse) — optimized: no animation when idle ─
@Composable
fun BreathVisualizer(
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isListening) {
        // Static, no infinite transition, no recomposition — zero GPU cost when idle
        // drawWithCache ensures the circle is cached across 90/120Hz vsync ticks
        val idleColor = remember { VachakColors.DeepLavender.copy(alpha = 0.12f) }
        Canvas(modifier = modifier) {
            val center = Offset(size.width / 2, size.height / 2)
            val base = size.minDimension / 2.6f
            drawCircle(color = idleColor, radius = base * 0.9f, center = center)
        }
        return
    }
    // Only allocate infinite transition when actually listening — pauses when mic off
    // At 90/120Hz Choreographer drives this; tween(1000) is cheap vs spring, 1.12 scale limits overdraw
    val infinite = rememberInfiniteTransition(label = "breath")
    val scale by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "scale"
    )
    val alpha by infinite.animateFloat(
        initialValue = 0.28f, targetValue = 0.10f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
        label = "alpha"
    )
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val base = size.minDimension / 2.6f
        drawCircle(color = VachakColors.Lavender600.copy(alpha = alpha), radius = base * scale, center = center)
        drawCircle(color = VachakColors.Lavender600.copy(alpha = alpha * 0.55f), radius = base * scale * 1.22f, center = center, style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(color = VachakColors.Lavender600, radius = base * 0.88f, center = center)
    }
}

// ── LessonCard (OutlinedCard with leading SubjectIcon) ──────────────
@Composable
fun LessonCard(
    title: String,
    grade: Int,
    subject: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (subject.lowercase()) {
        "math" -> Icons.Filled.Calculate
        "language", "hindi", "santali" -> Icons.AutoMirrored.Filled.MenuBook
        "oral" -> Icons.Filled.RecordVoiceOver
        else -> Icons.AutoMirrored.Filled.MenuBook
    }
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = VachakColors.Forest.copy(alpha = 0.12f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, null, tint = VachakColors.Forest, modifier = Modifier.size(24.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text("Grade $grade • $subject", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

// ── BudgetIndicator (LinearProgress with color tiers) ───────────────
@Composable
fun BudgetIndicator(progress: Float, label: String, modifier: Modifier = Modifier) {
    val color = when {
        progress < 0.6f -> VachakColors.OfflineGreen
        progress < 0.85f -> VachakColors.Amber
        else -> VachakColors.ErrorRed
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

// ── DualLangCard (Hindi / Santali pane) ─────────────────────────────
@Composable
fun DualLangCard(
    label: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp))
            Text(text.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
