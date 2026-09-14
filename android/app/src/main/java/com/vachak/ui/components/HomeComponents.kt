package com.vachak.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vachak.ui.theme.VachakColors
import com.vachak.ui.theme.VachakLayer
import com.vachak.ui.theme.cardShadow
import com.vachak.ui.theme.color
import com.vachak.ui.theme.raisedShadow

private val EaseSnap = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Tactile press: exactly 0.96 over 140ms ease-out. Wrap custom clickables. */
@Composable
fun pressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 140, easing = EaseSnap),
        label = "press"
    )
    return scale
}

// ── Filter pill ─────────────────────────────────────────────────────
// Selected pills ELEVATE (tint + pine border + shadow); unselected recede.
@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    val bg = if (selected) VachakColors.Lavender100 else VachakLayer.Card.color()
    val contentColor = if (selected) VachakColors.DeepLavender else VachakColors.TextSecondary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) VachakColors.DeepLavender else VachakColors.Border
        ),
        modifier = modifier.height(36.dp).then(
            if (selected) Modifier.raisedShadow(RoundedCornerShape(50)) else Modifier
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, tint = contentColor, modifier = Modifier.size(16.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
        }
    }
}

// ── Continue Learning hero ──────────────────────────────────────────
// Pine feature card: the ONE thing that pops. Progress sits in a recessed
// pine well; CTA is marigold. Count chip is tabular so digits never jitter.
@Composable
fun ContinueLearningCard(
    title: String,
    gradeLabel: String,
    description: String,
    progressLabel: String,
    progress: Float,
    countText: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().raisedShadow(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = VachakColors.DeepLavender,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("CONTINUE LEARNING", style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender200, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.14f)) {
                    Text(
                        countText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(gradeLabel, style = MaterialTheme.typography.bodySmall, color = VachakColors.Lavender200)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f), lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                // Ol Chiki mark — glass tile, the card's character detail.
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.14f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("ᱚ", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // Recessed progress well: darker pine bed, lighter bar.
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = VachakColors.ForestDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = VachakColors.Lavender200, modifier = Modifier.size(16.dp))
                        Text(progressLabel, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
                    }
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        color = VachakColors.Accent,
                        trackColor = Color.White.copy(alpha = 0.22f)
                    )
                }
            }
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = VachakColors.Accent, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                Text("Continue Lesson", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Secondary lesson row ────────────────────────────────────────────
@Composable
fun SecondaryLessonRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = VachakLayer.Card.color(),
        border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
        modifier = modifier.fillMaxWidth().cardShadow()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = VachakColors.Lavender100, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Outlined.Calculate, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Up next", style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender600, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                Text(title, style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = CircleShape, color = VachakColors.Lavender100, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Outlined.ChevronRight, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ── Quick action card ───────────────────────────────────────────────
@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = VachakColors.Surface
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
        tonalElevation = 0.dp,
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .cardShadow()
            .heightIn(min = 120.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                }
                Surface(shape = CircleShape, color = VachakColors.Surface, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.size(28.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        // Optical nudge: forward arrows read centered 1dp left.
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = VachakColors.TextPrimary, modifier = Modifier.size(14.dp).padding(end = 1.dp))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── Recent lesson row ───────────────────────────────────────────────
// Status is a color-coded chip WITH text — color never the only signal.
@Composable
fun RecentLessonRow(
    title: String,
    subtitle: String,
    completed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = VachakColors.Lavender100, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(18.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = if (completed) VachakColors.SuccessLight else VachakLayer.Section.color(),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (completed) VachakColors.Success.copy(alpha = 0.35f) else VachakColors.Border)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (completed) {
                    Icon(Icons.Filled.CheckCircle, null, tint = VachakColors.Success, modifier = Modifier.size(14.dp))
                    Text("Done", style = MaterialTheme.typography.labelSmall, color = VachakColors.Success, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Outlined.Schedule, null, tint = VachakColors.TextSecondary, modifier = Modifier.size(14.dp))
                    Text("Ongoing", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ── Decorative arcs (header, static, no recomposition) ──
@Composable
fun HomeDecorativeArcs(modifier: Modifier = Modifier) {
    // Cache colors to avoid copy(alpha) allocation on every frame (120Hz = 120 allocs/sec)
    val arc1 = androidx.compose.runtime.remember { VachakColors.Lavender200.copy(alpha = 0.35f) }
    val arc2 = androidx.compose.runtime.remember { VachakColors.Lavender100.copy(alpha = 0.5f) }
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height * 0.45f
        drawArc(
            color = arc1,
            startAngle = 180f, sweepAngle = 90f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.45f, -h * 0.15f),
            size = androidx.compose.ui.geometry.Size(w * 0.9f, h),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 28f)
        )
        drawArc(
            color = arc2,
            startAngle = 180f, sweepAngle = 90f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.55f, -h * 0.05f),
            size = androidx.compose.ui.geometry.Size(w * 0.75f, h * 0.85f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 20f)
        )
    }
}

// ── Section header with optional tabular count ──────────────────────
@Composable
fun HomeSectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    count: Int? = null,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            if (count != null) {
                Surface(shape = RoundedCornerShape(50), color = VachakLayer.Section.color()) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = VachakColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = VachakColors.Lavender600)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ChevronRight, null, tint = VachakColors.Lavender600, modifier = Modifier.size(16.dp))
            }
        }
    }
}
