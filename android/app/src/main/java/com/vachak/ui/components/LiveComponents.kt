package com.vachak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vachak.ui.screens.ConversationItem
import com.vachak.ui.theme.VachakColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmt(ts: Long): String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ts))

/** Measured-stage footer: "ASR 412ms • MT 380ms • TTS 610ms • Total 1.9s ✓<3s". Missing stages are skipped, never zero-filled. */
private fun timingLine(asrMs: Long?, mtMs: Long?, ttsMs: Long?, totalMs: Long?): String {
    val parts = mutableListOf<String>()
    asrMs?.let { parts += "ASR ${it}ms" }
    mtMs?.let { parts += "MT ${it}ms" }
    ttsMs?.let { parts += "TTS ${it}ms" }
    totalMs?.let {
        val secs = it / 1000f
        val verdict = if (it < 3000) "✓<3s" else "⚠≥3s"
        parts += "Total ${"%.1f".format(secs)}s $verdict"
    }
    return parts.joinToString(" • ")
}

@Composable
fun LiveTranscriptionStrip(
    text: String,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = VachakColors.SoftLavender,
        border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Lavender200)
    ) {
        Column(modifier = Modifier.padding(12.dp).heightIn(max = 100.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Hindi · Live", style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender600, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
                if (isListening) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(VachakColors.Success))
                        Text("Listening…", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                    }
                }
            }
            Text(
                text.ifBlank { if (isListening) "Listening…" else "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = VachakColors.TextPrimary,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ConversationMessagePair(
    item: ConversationItem,
    onPlayHindi: () -> Unit = {},
    onPlaySantali: () -> Unit = {},
    onRetry: () -> Unit = {},
    // Active target language label — "Santali (Ol Chiki)" by default, "Mundari" if toggled.
    // Kept as a param (not read from ActiveLanguage directly) so previews/tests stay deterministic.
    targetLabel: String = "Santali (Ol Chiki)",
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hindi block — soft lavender
        Surface(shape = RoundedCornerShape(16.dp), color = VachakColors.SoftLavender, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Hindi", style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender700, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                    Text(fmt(item.timestampMillis), style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                }
                Text(item.hindiText, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary, fontSize = 16.sp, lineHeight = 22.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPlayHindi, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play", style = MaterialTheme.typography.labelMedium, color = VachakColors.DeepLavender)
                    }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(item.hindiText)) }, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = VachakColors.TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", style = MaterialTheme.typography.labelMedium, color = VachakColors.TextSecondary)
                    }
                }
            }
        }
        // divider
        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 8.dp))
        // Target-language block — near-white / pale lavender
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(targetLabel, style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender600, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                when {
                    item.isTranslating -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = VachakColors.Lavender600)
                            Text("Translating…", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary)
                        }
                    }
                    item.error != null -> {
                        Text("Couldn't translate this message.", style = MaterialTheme.typography.bodyMedium, color = VachakColors.ErrorRed)
                        Text(item.error ?: "", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(50), modifier = Modifier.height(36.dp)) { Text("Try Again", style = MaterialTheme.typography.labelMedium) }
                    }
                    item.santaliText != null -> {
                        Text(item.santaliText, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary, fontSize = 16.sp, lineHeight = 22.sp)
                        // Measured pipeline timings for this item (null until the run completes).
                        if (item.asrMs != null || item.mtMs != null || item.ttsMs != null || item.totalMs != null) {
                            Text(
                                timingLine(item.asrMs, item.mtMs, item.ttsMs, item.totalMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = VachakColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onPlaySantali, contentPadding = PaddingValues(0.dp)) {
                                Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play $targetLabel", style = MaterialTheme.typography.labelMedium, color = VachakColors.DeepLavender)
                            }
                            TextButton(onClick = { clipboard.setText(AnnotatedString(item.santaliText)) }, contentPadding = PaddingValues(0.dp)) {
                                Icon(Icons.Outlined.ContentCopy, null, tint = VachakColors.TextSecondary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy", style = MaterialTheme.typography.labelMedium, color = VachakColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onTranslate: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), tonalElevation = 0.dp, shadowElevation = 0.dp, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Type in Hindi…", color = VachakColors.TextSecondary) },
                leadingIcon = { Icon(Icons.Outlined.Keyboard, null, tint = VachakColors.TextSecondary, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VachakColors.Lavender300,
                    unfocusedBorderColor = VachakColors.Border,
                    focusedContainerColor = VachakColors.Background,
                    unfocusedContainerColor = VachakColors.Background
                ),
                maxLines = 4
            )
            Button(
                onClick = onTranslate,
                enabled = enabled && text.isNotBlank(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White),
                modifier = Modifier.height(48.dp),
                contentPadding = PaddingValues(horizontal = 18.dp)
            ) {
                Text("Translate", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
