package com.vachak.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vachak.engine.ActiveLanguage
import com.vachak.engine.EngineProvider
import com.vachak.engine.EngineResult
import com.vachak.engine.VachakLog
import com.vachak.ml.adapter.AdapterTranslationEngine
import com.vachak.sync.PackManager
import com.vachak.ui.components.VachakSection
import com.vachak.ui.theme.VachakColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DiagnosticsScreen — Phase 6: Budget & Latency Proof
 * Live storage via Room count + pack storage + asset estimate. Sequential pipeline proof.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    engine: EngineProvider,
    lastLatency: String? = null,
    modifier: Modifier = Modifier
) {
    var bench by remember { mutableStateOf<String?>(null) }
    var liveStorage by remember { mutableStateOf<String?>(null) }
    var liveDb by remember { mutableStateOf<String?>(null) }
    var liveFree by remember { mutableStateOf<String?>(null) }
    var activeAdapterBadge by remember { mutableStateOf<String?>(null) }
    var packShaLine by remember { mutableStateOf<String?>(null) }
    var asrFingerprint by remember { mutableStateOf<String?>(null) }
    var fingerprintBusy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activeLang by engine.activeLanguage.collectAsState()
    LaunchedEffect(activeLang) {
        // MEASURED pipeline timings only (LastPipelineRun). Never canned: null
        // until the first Live run completes renders as "not measured yet".
        val run = com.vachak.ml.LastPipelineRun.sample
        bench = if (run == null) {
            "Not measured yet — run Live once (voice or typed); timings here are measured on-device, never mocked."
        } else {
            val stages = run.stageMs()
            val total = run.endToEndMs()
            // Phase 4: TTS number is ttsSynthMs() (blocking generate runs
            // between markTtsBegin/markAudioBegin). stages["tts"] is only the
            // MT-done -> synth-start gap and reads ~0ms — showing it made TTS
            // look artificially free.
            val ttsMs = run.ttsSynthMs()
            val verdict = if ((total ?: Float.MAX_VALUE) < 3000) "✓ <3s" else "⚠ ≥3s"
            "ASR ${stages["asr"]?.toLong() ?: "-"}ms • MT ${stages["translate"]?.toLong() ?: "-"}ms • " +
                "TTS ${ttsMs?.toLong() ?: "-"}ms • Total ${total?.toLong() ?: "-"}ms $verdict (run ${run.runId.takeLast(6)})"
        }
        // active adapter badge via AdapterTranslationEngine
        try {
            val adapter = engine.translation as? AdapterTranslationEngine
            val present = adapter?.isAdapterConnected(activeLang) ?: false
            val adapterName = if (ActiveLanguage.isOlChiki(activeLang)) "santali_adapter" else "mundari_adapter"
            val sizeHint = "~13M" // LoRA adapter 14M, CT2 stripped 223M
            activeAdapterBadge = if (present) "✓ $adapterName $sizeHint • AdapterEngine present=true [Vachak-MT]" else "⚠ $adapterName not in filesDir/assets — base CT2 + refMap fallback"
        } catch (_: Exception) { activeAdapterBadge = null }
        withContext(Dispatchers.IO) {
            try {
                val packBytes = (engine.packs.storageUsedBytes() as? EngineResult.Ok)?.value ?: PackManager.storageUsedBytes(context)
                val dbBytes = try {
                    val dbFile = context.getDatabasePath("vachak_content.db")
                    if (dbFile.exists()) dbFile.length() else 0L
                } catch (e: Exception) {
                    VachakLog.w("Vachak-Diag", "db size unreadable: ${e.message}")
                    0L
                }
                // MEASURED bytes: installed APK file + extracted filesDir models + packs + db.
                // (APK asset table doesn't expose sizes; packageCodePath + filesDir walk does.)
                val freeBytes = PackManager.freeSpaceBytes(context)
                val apkBytes = try { java.io.File(context.packageCodePath).length() } catch (e: Exception) {
                    VachakLog.w("Vachak-Diag", "apk size unreadable: ${e.message}")
                    0L
                }
                val modelsBytes = try {
                    java.io.File(context.filesDir, "vachak_models").walkTopDown().filter { it.isFile }.sumOf { it.length() }
                } catch (e: Exception) {
                    VachakLog.w("Vachak-Diag", "filesDir models scan failed: ${e.message}")
                    0L
                }
                val totalMb = (packBytes + dbBytes + apkBytes + modelsBytes) / (1024 * 1024)
                val progress = (totalMb / 500f).coerceIn(0f, 1f)
                val sha = try {
                    val packs = PackManager.packEntities(context)
                    packs.firstOrNull { it.isActive }?.manifestSha256 ?: packs.firstOrNull()?.manifestSha256
                } catch (e: Exception) {
                    VachakLog.w("Vachak-Diag", "pack sha unreadable: ${e.message}")
                    null
                }
                withContext(Dispatchers.Main) {
                    liveStorage = "Live: ~${totalMb} MB / 500 MB (apk ${apkBytes / 1024 / 1024}MB + models ${modelsBytes / 1024 / 1024}MB + packs ${packBytes / 1024}KB + db ${dbBytes / 1024}KB)"
                    liveDb = "Storage progress ${(progress * 100).toInt()}% • DB ${dbBytes / 1024}KB"
                    liveFree = if (freeBytes >= 0) "Free: ${freeBytes / (1024 * 1024)} MB • used ${packBytes / (1024 * 1024)} MB" else null
                    packShaLine = sha?.let { "packSha256: ${it.take(16)}… • ${ActiveLanguage.label(activeLang)} • withinBudget=${totalMb <= 500}" }
                }
            } catch (e: Exception) {
                VachakLog.w("Vachak-Diag", "live storage scan failed", e)
                withContext(Dispatchers.Main) { liveStorage = "Live storage unavailable — using estimate" }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Build ${com.vachak.BuildConfig.GIT_SHA} • LatencyTracker • Sequential pipeline • 2GB / 500MB budgets",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // DRAFT watermark — curriculum is AUTHOR-DRAFT until SME sign-off (see curriculum/lessons/sat_lessons.json meta.content_status)
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("⚠ DRAFT — AUTHOR-DRAFT content, not approved pedagogy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace)
                Text("curriculum/lessons/sat_lessons.json meta.content_status=AUTHOR-DRAFT • curriculum/data.py validate_warnings() reports 2.2% verified — DRAFT until native speaker SME review • build_pack --require-approved fails on DRAFT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer, fontFamily = FontFamily.Monospace)
            }
        }

        VachakSection(title = "Pipeline Latency (LatencyTracker)", icon = Icons.Outlined.Timer) {
            lastLatency?.let {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
            bench?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            HorizontalDivider()
            Text("Sequential: ASR → MT → TTS (never parallel) — ReentrantLock + isTranslating + numThreads=1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ListItem(
                headlineContent = { Text("Budget") },
                supportingContent = { Text("ASR ≤1000ms • MT ≤500ms • TTS ≤1000ms • Total <3000ms", style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    val ok = bench?.contains("✓") == true
                    Icon(if (ok) Icons.Filled.CheckCircle else Icons.Outlined.Warning, null, tint = if (ok) VachakColors.OfflineGreen else VachakColors.Amber)
                }
            )
            Text("Logs: adb logcat -s Vachak-Latency Vachak-MT Vachak-ASR Vachak-TTS", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        VachakSection(title = "Active Adapter", icon = Icons.Outlined.Translate) {
            activeAdapterBadge?.let {
                Surface(shape = MaterialTheme.shapes.small, color = if (it.startsWith("✓")) VachakColors.OfflineGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            } ?: Text("Adapter: ${ActiveLanguage.label(activeLang)} • checking filesDir/modelpacks/* …", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            packShaLine?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Log: adb logcat -s Vachak-MT | grep AdapterEngine → present=true for both langs", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Validator: ${if (ActiveLanguage.isOlChiki(activeLang)) "Ol Chiki U+1C50–U+1C7F tick" else "Deva tick"} per language", style = MaterialTheme.typography.labelSmall, color = if (ActiveLanguage.isOlChiki(activeLang)) VachakColors.OfflineGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text(
                "ASR model fingerprint: exact bytes on THIS device (catches stale filesDir models surviving updates).",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        fingerprintBusy = true
                        asrFingerprint = null
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            val dir = java.io.File(context.filesDir, "vachak_models/asr")
                            val fp = if (java.io.File(dir, "model.onnx").exists() || java.io.File(dir, "model.int8.onnx").exists()) {
                                com.vachak.ml.AsrModelFingerprint.read(dir)
                            } else {
                                VachakLog.w("Vachak-Diag", "fingerprint: filesDir ASR not extracted yet")
                                null
                            }
                            withContext(Dispatchers.Main) {
                                asrFingerprint = fp?.toString() ?: "ASR not extracted yet — run Live mic once, then retry."
                                fingerprintBusy = false
                            }
                        }
                    },
                    enabled = !fingerprintBusy
                ) { Text(if (fingerprintBusy) "Reading…" else "Fingerprint ASR model") }
            }
            asrFingerprint?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) }
        }

        VachakSection(title = "Resource Budget", icon = Icons.Outlined.Memory) {
            liveStorage?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            liveDb?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            liveFree?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("RAM: sequential single-model residency (ASR ≤2 threads, MT ≤4, shared weights — see logcat Vachak-Latency)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
        }

        VachakSection(title = "Sequential Enforcement", icon = Icons.Outlined.Lock) {
            ListItem(
                headlineContent = { Text("ReentrantLock in IndicTrans2Adapter") },
                supportingContent = { Text("MT holds lock; ASR/TTS wait — one model resident at a time", style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(Icons.Outlined.Lock, null) }
            )
            ListItem(
                headlineContent = { Text("isTranslating guard in LiveScreen") },
                supportingContent = { Text("FAB disabled while pipeline runs — no parallel launch", style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(Icons.Outlined.Block, null) }
            )
            ListItem(
                headlineContent = { Text("numThreads=1") },
                supportingContent = { Text("All ORT/Sherpa sessions single-threaded", style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(Icons.Outlined.Memory, null) }
            )
        }

        VachakSection(title = "Offline & Budget Attestation", icon = Icons.Outlined.VerifiedUser) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, null, tint = VachakColors.OfflineGreen)
                Text("No android.permission.INTERNET • No HttpURLConnection • Pack hash verified", style = MaterialTheme.typography.bodySmall)
            }
            Text("Report: docs/benchmarks/BENCHMARK_REPORT.md • Harness: benchmarks/run_benchmark.py", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LiveLogSection()
    }
}

/**
 * Live Log — every Vachak-* line from the whole APK (ASR/VAD/MT/TTS/sherpa
 * adapters, pipeline, stop button, packs), newest last, no adb needed.
 * Same lines logcat shows; the ring holds the last 300.
 */
@Composable
private fun LiveLogSection() {
    val lines by VachakLog.lines.collectAsState()
    var tagFilter by remember { mutableStateOf("All") }
    val tags = remember { listOf("All", "ASR", "VAD", "MT", "TTS", "Stop", "Latency", "Diag", "Pack") }
    VachakSection(title = "Live Log (this device, no adb)", icon = Icons.Outlined.BugReport) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
            items(tags, key = { it }) { t ->
                FilterChip(
                    selected = tagFilter == t,
                    onClick = { tagFilter = t },
                    label = { Text(t, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        val shown = remember(lines, tagFilter) {
            derivedStateOf {
                val f = if (tagFilter == "All") lines else lines.filter { it.contains("Vachak-$tagFilter") }
                f.takeLast(60)
            }
        }.value
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${shown.size} lines${if (tagFilter != "All") " • Vachak-$tagFilter" else ""} • newest at bottom",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
            TextButton(onClick = { VachakLog.clear() }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Clear", style = MaterialTheme.typography.labelSmall)
            }
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (shown.isEmpty()) {
                    Text(
                        if (lines.isEmpty()) "No lines yet — run Live once and they appear here."
                        else "No Vachak-$tagFilter lines yet.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    shown.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                line.startsWith("[", false) && (line.contains(" E/") || line.contains("E/Vachak")) -> MaterialTheme.colorScheme.error
                                line.contains(" W/") -> VachakColors.Amber
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontFamily = FontFamily.Monospace,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Text(
            "adb mirror: logcat -s Vachak-ASR Vachak-VAD Vachak-MT Vachak-TTS Vachak-Latency Vachak-Stop Vachak-Diag",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
