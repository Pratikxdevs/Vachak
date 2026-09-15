package com.vachak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vachak.engine.EngineProvider
import com.vachak.ui.components.BreadcrumbTrail
import com.vachak.ui.navigation.loadPackSummary
import com.vachak.ui.theme.VachakColors
import com.vachak.ui.theme.cardShadow
import com.vachak.ui.theme.tabletHPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface ChapterUi {
    data object Loading : ChapterUi
    data class Ready(val title: String, val subject: String) : ChapterUi
    data object Missing : ChapterUi
}

/**
 * Chapter page — Phase 13: header + TWO options only (Worksheets, Flashcards).
 * No gallery, no Read list, no inline questions, no notices. The worksheet and
 * deck screens own their content; this page only routes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterScreen(
    engine: EngineProvider,
    grade: Int,
    slug: String,
    onOpenWorksheets: () -> Unit,
    onOpenFlashcards: () -> Unit,
    onOpenPacks: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var ui by remember(grade, slug) { mutableStateOf<ChapterUi>(ChapterUi.Loading) }

    LaunchedEffect(grade, slug) {
        ui = ChapterUi.Loading
        val meta = withContext(Dispatchers.IO) {
            runCatching {
                loadPackSummary(ctx)
                    ?.firstOrNull { it.grade == grade }
                    ?.chapterTitles?.firstOrNull { it.slug == slug }
            }.getOrNull()
        }
        ui = if (meta != null) ChapterUi.Ready(meta.title, meta.subject)
        else ChapterUi.Missing
    }

    Column(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = VachakColors.TextPrimary) }
                Column(modifier = Modifier.weight(1f)) {
                    BreadcrumbTrail(listOf("Learn", "Grade $grade"))
                    Text(
                        (ui as? ChapterUi.Ready)?.title ?: slug,
                        style = MaterialTheme.typography.titleLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 2
                    )
                    val subj = (ui as? ChapterUi.Ready)?.subject.orEmpty()
                    Text(
                        if (subj.isNotBlank()) "Grade $grade • $subj" else "Grade $grade",
                        style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1
                    )
                }
            }
        }

        when (val state = ui) {
            is ChapterUi.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = VachakColors.Lavender600, modifier = Modifier.size(32.dp))
                    Text("Opening chapter…", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                }
            }
            is ChapterUi.Missing -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Chapter not found", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("It may belong to a pack that isn't installed yet.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                    Button(onClick = onOpenPacks, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White)) {
                        Text("Open Packs")
                    }
                }
            }
            is ChapterUi.Ready -> Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = tabletHPad(), vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PracticeCard(
                    title = "Worksheets",
                    subtitle = "",
                    icon = Icons.Outlined.Description,
                    onClick = onOpenWorksheets,
                    modifier = Modifier.fillMaxWidth()
                )
                PracticeCard(
                    title = "Flashcards",
                    subtitle = "",
                    icon = Icons.Outlined.Style,
                    onClick = onOpenFlashcards,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PracticeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier.cardShadow(RoundedCornerShape(20.dp)),
        onClick = onClick
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = VachakColors.DeepLavender, modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                }
            }
        }
    }
}
