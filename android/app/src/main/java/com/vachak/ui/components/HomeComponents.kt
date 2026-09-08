package com.vachak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vachak.ui.theme.VachakColors

// ── Filter pill (home §7) ─────────────────────────────────────────────
@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    val bg = if (selected) VachakColors.Lavender100 else VachakColors.Surface
    val contentColor = if (selected) VachakColors.DeepLavender else VachakColors.TextSecondary
    val border = if (selected) VachakColors.Lavender200 else VachakColors.Border
    Surface(
        modifier = modifier.height(36.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = bg,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
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

// ── Continue Learning feature card (home §8-15) ───────────────────────
@Composable
fun ContinueLearningCard(
    title: String,
    gradeLabel: String,
    description: String,
    progressLabel: String,
    progress: Float,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = VachakColors.SoftLavender,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // top label + overflow
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("CONTINUE LEARNING", style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender600, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Outlined.MoreHoriz, contentDescription = "More", tint = VachakColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
            // title + visual row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(gradeLabel, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, lineHeight = 18.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                // placeholder visual — soft white rounded container with Ol Chiki hint
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("ᱚ", style = MaterialTheme.typography.headlineMedium, color = VachakColors.DeepLavender.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                    }
                }
            }
            // progress
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = VachakColors.Lavender600, modifier = Modifier.size(16.dp))
                    Text(progressLabel, style = MaterialTheme.typography.labelMedium, color = VachakColors.TextSecondary)
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    color = VachakColors.Lavender500,
                    trackColor = Color.White.copy(alpha = 0.8f)
                )
            }
            // CTA
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                Text("Continue Lesson", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Secondary lesson row (home §16) ──────────────────────────────────
@Composable
fun SecondaryLessonRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = VachakColors.SoftLavender.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Lavender100)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Outlined.Calculate, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
            }
            Surface(shape = CircleShape, color = VachakColors.Lavender100, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Outlined.ChevronRight, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ── Quick action card (home §17) ─────────────────────────────────────
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
    Surface(
        modifier = modifier.clickable(onClick = onClick).heightIn(min = 120.dp),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                }
                Surface(shape = CircleShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.size(28.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = VachakColors.TextPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, lineHeight = 16.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

// ── Recent lesson row (home §19-21) ──────────────────────────────────
@Composable
fun RecentLessonRow(
    title: String,
    subtitle: String,
    status: String,
    statusIcon: ImageVector?,
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
            Text(title, style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.Medium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (status) {
                "Completed" -> {
                    Icon(Icons.Filled.CheckCircle, null, tint = VachakColors.Lavender600, modifier = Modifier.size(18.dp))
                    Text(status, style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                }
                "50%" -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                        CircularProgressIndicator(progress = { 0.5f }, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = VachakColors.Lavender600, trackColor = VachakColors.Border)
                    }
                    Text(status, style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary, fontWeight = FontWeight.Medium)
                }
                "In Progress" -> {
                    Icon(Icons.Outlined.Schedule, null, tint = VachakColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Text(status, style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                }
                else -> {
                    if (statusIcon != null) Icon(statusIcon, null, tint = VachakColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Text(status, style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                }
            }
            Surface(shape = CircleShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Outlined.ChevronRight, null, tint = VachakColors.TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}


// ── Decorative arcs (Home/Curriculum header, static, no recomposition) ──
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

// ── Section header (home §17, §19) ────────────────────────────────────
@Composable
fun HomeSectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = VachakColors.Lavender600)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ChevronRight, null, tint = VachakColors.Lavender600, modifier = Modifier.size(16.dp))
            }
        }
    }
}
