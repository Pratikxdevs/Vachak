package com.vachak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vachak.ml.ModelStatus
import com.vachak.ui.theme.VachakColors

/**
 * Fixed 4s splash (latency plan Phase 0): branding + honest per-model
 * progress from ModelStatus. Non-blocking — MainActivity preload continues
 * sequentially in background after the splash; mic stays gated until READY.
 */
@Composable
fun SplashScreen() {
    val asr by ModelStatus.asr.collectAsState()
    val mt by ModelStatus.mt.collectAsState()
    val tts by ModelStatus.tts.collectAsState()
    Surface(modifier = Modifier.fillMaxSize(), color = VachakColors.DeepLavender) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Brand tile — Ol Chiki letter, the script this app teaches in.
                Box(
                    modifier = Modifier.size(88.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(VachakColors.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "ᱚ",
                        style = MaterialTheme.typography.displayMedium,
                        color = VachakColors.DeepLavender,
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp
                    )
                }
                Text(
                    "Vachak",
                    style = MaterialTheme.typography.headlineLarge,
                    color = VachakColors.Surface,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "Hindi → Santali (Ol Chiki) • Offline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VachakColors.Lavender200,
                    textAlign = TextAlign.Center
                )
                CircularProgressIndicator(color = VachakColors.Accent)
                Text(
                    "Loading voices: MT ${mt.state} • ASR ${asr.state} • TTS ${tts.state}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VachakColors.Lavender200,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
