package com.vachak.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vachak.ui.theme.VachakColors

/**
 * Floating debug log viewer for Vachak-* tags.
 * Offline in-proc 200-line ring buffer (VachakLogger).
 * Toggle via SettingsScreen; overlay is Box overlay in VachakApp.
 */
@Composable
fun DebugOverlay(
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val lines by VachakLogger.lines.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 180.dp, max = 320.dp).padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E),
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Debug • Vachak-* (${lines.size}/200)", style = MaterialTheme.typography.labelMedium, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { VachakLogger.clear() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Delete, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Close, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                    }
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            if (lines.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No Vachak-* logs yet. Tap mic or translate.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(lines) { line ->
                        Text(line, style = MaterialTheme.typography.labelSmall, color = when {
                            line.contains(" E/") -> Color(0xFFFF8A80)
                            line.contains(" W/") -> Color(0xFFFFE082)
                            line.contains("Vachak-MT") -> Color(0xFF80CBC4)
                            line.contains("Vachak-ASR") || line.contains("Vachak-VAD") -> Color(0xFF90CAF9)
                            line.contains("Vachak-TTS") -> Color(0xFFCE93D8)
                            line.contains("Vachak-Latency") -> Color(0xFFA5D6A7)
                            else -> Color.White.copy(alpha = 0.85f)
                        }, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("adb logcat -s Vachak-MT Vachak-ASR Vachak-TTS Vachak-Latency", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                Text("offline ring buffer", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun LanguageSwitcher(
    activeLang: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = com.vachak.engine.ActiveLanguage.label(activeLang)
    Box(modifier = modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            leadingIcon = { Text(if (com.vachak.engine.ActiveLanguage.isOlChiki(activeLang)) "ᱚ" else "ᱛ", style = MaterialTheme.typography.labelMedium, color = VachakColors.DeepLavender) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            com.vachak.engine.ActiveLanguage.all().forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(code); expanded = false },
                    leadingIcon = { Text(if (com.vachak.engine.ActiveLanguage.isOlChiki(code)) "ᱚ" else "ᱛ") }
                )
            }
        }
    }
}
