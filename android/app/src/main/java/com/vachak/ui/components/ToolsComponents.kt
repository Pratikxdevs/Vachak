package com.vachak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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

@Composable
fun PrimaryToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = "✨ AI Powered"
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = VachakColors.SoftLavender,
        border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Lavender100)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(icon, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(28.dp))
                    }
                }
                Surface(shape = RoundedCornerShape(50), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Lavender200)) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (badge != null) Text(badge, style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender600, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary, lineHeight = 20.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = Modifier.size(64.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(glyph, style = MaterialTheme.typography.titleLarge, color = VachakColors.DeepLavender, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = VachakColors.PrimaryDark, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionSmallCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = VachakColors.Lavender100, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(20.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, lineHeight = 16.sp)
            }
            Surface(shape = CircleShape, color = VachakColors.SoftLavender, modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Outlined.ChevronRight, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun HistoryRow(
    title: String,
    subtitle: String,
    meta: String,
    icon: ImageVector,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = VachakColors.Lavender100, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(icon, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
            Text(meta, style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
        }
        Surface(shape = RoundedCornerShape(50), color = VachakColors.SoftLavender, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Lavender100)) {
            Text(badge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender700, fontWeight = FontWeight.SemiBold)
        }
        Icon(Icons.Outlined.MoreVert, null, tint = VachakColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}
