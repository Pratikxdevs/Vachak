package com.vachak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
fun GradeCard(
    grade: Int,
    lessonCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(140.dp).heightIn(min = 120.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = VachakColors.SoftLavender,
        border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Lavender100)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Outlined.School, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(20.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Grade $grade", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("$lessonCount lessons", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
            }
        }
    }
}

@Composable
fun FlashcardDeckCard(
    title: String,
    subtitle: String,
    count: String,
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(160.dp).height(180.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Surface(shape = RoundedCornerShape(14.dp), color = VachakColors.Lavender100, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(glyph, style = MaterialTheme.typography.titleLarge, color = VachakColors.DeepLavender, fontWeight = FontWeight.Bold)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp, maxLines = 2)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(count, style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                Surface(shape = CircleShape, color = VachakColors.SoftLavender, modifier = Modifier.size(28.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Outlined.AutoStories, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
