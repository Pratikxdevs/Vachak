package com.vachak.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vachak.ui.screens.ConversationItem
import com.vachak.ui.theme.VachakColors
import com.vachak.ui.theme.VachakLayer
import com.vachak.ui.theme.cardShadow
import com.vachak.ui.theme.color
import com.vachak.ui.theme.raisedShadow
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
    sourceLabel: String = "Hindi · Live",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = VachakLayer.Card.color(),
        border = BorderStroke(1.dp, VachakColors.Lavender200)
    ) {
        Column(modifier = Modifier.padding(12.dp).heightIn(max = 100.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(sourceLabel, style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender600, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
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
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * One bidirectional turn: Hindi ⇄ Santali as a single unit. Hindi sits on the
 * layered section surface (source, recedes); Santali rides a raised white card
 * (result, pops). Error and in-progress states name themselves inline — a
 * translating row never looks finished, a failed row never looks empty.
 */
@Composable
fun ConversationMessagePair(
    item: ConversationItem,
    onPlayHindi: () -> Unit = {},
    onPlaySantali: () -> Unit = {},
    onRetry: () -> Unit = {},
    // Locked by the Live page to Santali (Ol Chiki) — kept as a param (not read
    // from ActiveLanguage directly) so previews/tests stay deterministic.
    targetLabel: String = "Santali (Ol Chiki)",
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Hindi block — layered section surface, no border (layering, not chrome).
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = VachakLayer.Section.color(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Hindi", style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender700, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                    Text(fmt(item.timestampMillis), style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary, fontFamily = FontFamily.Monospace)
                }
                Text(item.hindiText, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary, fontSize = 16.sp, lineHeight = 22.sp)
                val hindiGloss = remember(item.hindiText) { com.vachak.ui.text.Romanize.devanagari(item.hindiText) }
                if (hindiGloss.isNotBlank() && hindiGloss != item.hindiText) {
                    Text(hindiGloss, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPlayHindi, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play", style = MaterialTheme.typography.labelMedium, color = VachakColors.DeepLavender)
                    }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(item.hindiText)) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = VachakColors.TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", style = MaterialTheme.typography.labelMedium, color = VachakColors.TextSecondary)
                    }
                }
            }
        }
        // Santali block — raised white card: the result pops off the source.
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = VachakLayer.Card.color(),
            border = BorderStroke(1.dp, VachakColors.Border),
            modifier = Modifier.fillMaxWidth().cardShadow(RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(targetLabel, style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender600, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                when {
                    item.isTranslating -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = VachakColors.Lavender600)
                            Text("Translating… Hindi → $targetLabel", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary)
                        }
                    }
                    item.error != null -> {
                        StatusRow(
                            dot = VachakColors.ErrorRed,
                            title = "Couldn't translate this message",
                            detail = item.error ?: ""
                        )
                        androidx.compose.material3.OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.height(36.dp)
                        ) { Text("Try Again", style = MaterialTheme.typography.labelMedium) }
                    }
                    item.santaliText != null -> {
                        Text(item.santaliText, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary, fontSize = 16.sp, lineHeight = 22.sp)
                        if (item.isSynthesizing) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = VachakColors.Lavender600)
                                Text("Synthesizing voice…", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                            }
                        }
                        val targetGloss = remember(item.santaliText) { com.vachak.ui.text.Romanize.auto(item.santaliText ?: "") }
                        if (targetGloss.isNotBlank() && targetGloss != item.santaliText) {
                            Text(targetGloss, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                        if (item.asrMs != null || item.mtMs != null || item.ttsMs != null || item.totalMs != null) {
                            Text(
                                timingLine(item.asrMs, item.mtMs, item.ttsMs, item.totalMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = VachakColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onPlaySantali, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play", style = MaterialTheme.typography.labelMedium, color = VachakColors.DeepLavender)
                            }
                            TextButton(onClick = { clipboard.setText(AnnotatedString(item.santaliText)) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
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
    sourceHint: String = "Type in Hindi…",
    enabled: Boolean = true,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Concentric radii: 20dp card − 6dp padding = 14dp field.
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = VachakLayer.Card.color(),
        border = BorderStroke(1.dp, VachakColors.Border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth().cardShadow()
    ) {
        Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(sourceHint, color = VachakColors.TextSecondary) },
                leadingIcon = { Icon(Icons.Outlined.Keyboard, null, tint = VachakColors.TextSecondary, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.weight(1f)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VachakColors.Lavender300,
                    unfocusedBorderColor = VachakColors.Border,
                    focusedContainerColor = VachakLayer.Section.color(),
                    unfocusedContainerColor = VachakLayer.Section.color()
                ),
                maxLines = 4
            )
            Button(
                onClick = onTranslate,
                enabled = enabled && text.isNotBlank(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White),
                modifier = Modifier.height(48.dp).then(
                    if (enabled && text.isNotBlank()) Modifier.raisedShadow(RoundedCornerShape(50))
                    else Modifier
                ),
                contentPadding = PaddingValues(horizontal = 18.dp)
            ) {
                Text("Translate", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
