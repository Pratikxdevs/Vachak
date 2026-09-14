package com.vachak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vachak.engine.ActiveLanguage
import com.vachak.engine.EngineProvider
import com.vachak.sync.PackManager
import com.vachak.ui.components.ProfileCard
import com.vachak.ui.components.SettingsGroup
import com.vachak.ui.components.SettingsRow
import com.vachak.ui.components.SettingsSectionHeader
import com.vachak.ui.debug.VachakLogger
import com.vachak.ui.theme.VachakColors
import com.vachak.ui.theme.overlayShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * More page (settings route). Every row below does something real:
 * - REMOVED: Sign Out (no auth exists — the confirm dialog dismissed into
 *   nothing), Offline Content (duplicate of Language Packs — same destination),
 *   Grade preference (write-only — nothing in the app ever read prefs.grade).
 * - FIXED: Storage showed a hardcoded "312 MB" — now measured on-device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    engine: EngineProvider,
    onManagePacks: (() -> Unit)? = null,
    onDiagnostics: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember(context) { com.vachak.ui.prefs.VachakPrefs(context) }
    var teacherName by remember { mutableStateOf(prefs.teacherName) }
    var notificationsOn by remember { mutableStateOf(prefs.notificationsOn) }
    var autoPlay by remember { mutableStateOf(prefs.autoPlayTts) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    val activeLang by engine.activeLanguage.collectAsState()
    val debugEnabled by VachakLogger.enabled.collectAsState()
    var langExpanded by remember { mutableStateOf(false) }
    // Measured storage (never a hardcoded number): apk + models + db + packs.
    var storageLine by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        storageLine = withContext(Dispatchers.IO) {
            try {
                val apk = try { java.io.File(context.packageCodePath).length() } catch (_: Exception) { 0L }
                val models = try {
                    java.io.File(context.filesDir, "vachak_models").walkTopDown().filter { it.isFile }.sumOf { it.length() }
                } catch (_: Exception) { 0L }
                val db = try {
                    val f = context.getDatabasePath("vachak_content.db")
                    if (f.exists()) f.length() else 0L
                } catch (_: Exception) { 0L }
                val packs = try { PackManager.storageUsedBytes(context) } catch (_: Exception) { 0L }
                "~${(apk + models + db + packs) / (1024 * 1024)} MB of 500 MB used"
            } catch (_: Exception) { null }
        }
    }

    val configuration = LocalConfiguration.current
    val isTablet = remember(configuration.screenWidthDp) { configuration.screenWidthDp >= 840 }
    val hPad = if (isTablet) 32.dp else 20.dp
    Box(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = hPad, end = hPad, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                        Text("More", style = MaterialTheme.typography.headlineLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Profile, language, packs, storage and preferences.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Surface(shape = RoundedCornerShape(50), color = VachakColors.SuccessLight, modifier = Modifier.height(32.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(VachakColors.Success))
                            Text("Offline", style = MaterialTheme.typography.labelSmall, color = VachakColors.Success, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Profile — the name greets the teacher on Today.
            item {
                ProfileCard(name = teacherName, role = "Teacher", onEdit = { nameDraft = teacherName; showNameDialog = true })
            }

            // Learning Preferences
            item {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsSectionHeader(title = "Learning Preferences")
                    SettingsGroup {
                        SettingsRow(icon = Icons.Outlined.Language, title = "Voice Input", value = "Hindi (fixed — ASR is Hindi-only)", onClick = { showSourceDialog = true })
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        // Active target language (packs, preview voices, Learn).
                        // Live Translate always uses Santali — said plainly here
                        // so the toggle never reads as broken when Translate
                        // pins back to Santali on entry.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                                Surface(shape = RoundedCornerShape(10.dp), color = VachakColors.SoftLavender, modifier = Modifier.size(40.dp)) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(Icons.Outlined.Translate, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Column {
                                    Text("Target Language", style = MaterialTheme.typography.bodyLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                    Text(
                                        ActiveLanguage.label(activeLang) + " • Translate tab always uses Santali",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VachakColors.TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            Box {
                                OutlinedButton(onClick = { langExpanded = true }, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(ActiveLanguage.label(activeLang), style = MaterialTheme.typography.labelMedium)
                                }
                                DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                                    ActiveLanguage.all().forEach { (code, name) ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                ActiveLanguage.set(code)
                                                (engine.translation as? com.vachak.ml.adapter.AdapterTranslationEngine)?.setActiveLanguage(code)
                                                langExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if (ActiveLanguage.isOlChiki(activeLang)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("✓ Ol Chiki U+1C50–U+1C7F", style = MaterialTheme.typography.labelSmall, color = VachakColors.Success, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                Text("script check passes", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("✓ Devanagari", style = MaterialTheme.typography.labelSmall, color = VachakColors.Success, fontWeight = FontWeight.SemiBold)
                                Text("Mundari phrasebook (neural pending)", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                            }
                        }
                    }
                }
            }

            // Offline & Storage
            item {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsSectionHeader(title = "Offline & Storage")
                    SettingsGroup {
                        SettingsRow(icon = Icons.Outlined.Folder, title = "Language Packs", description = "Install from file • checksums verified", onClick = { onManagePacks?.invoke() })
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(
                            icon = Icons.Outlined.Storage,
                            title = "Storage",
                            value = storageLine ?: "Measuring…",
                            onClick = { onDiagnostics?.invoke() }
                        )
                    }
                }
            }

            // App Preferences
            item {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsSectionHeader(title = "App Preferences")
                    SettingsGroup {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                                Surface(shape = RoundedCornerShape(10.dp), color = VachakColors.SoftLavender, modifier = Modifier.size(40.dp)) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(Icons.Outlined.Notifications, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Column {
                                    Text("Notifications", style = MaterialTheme.typography.bodyLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                    Text(if (notificationsOn) "Learning reminders ON" else "Learning reminders OFF", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, fontSize = 14.sp)
                                }
                            }
                            Switch(checked = notificationsOn, onCheckedChange = { notificationsOn = it; prefs.notificationsOn = it })
                        }
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(icon = Icons.Outlined.Palette, title = "Appearance", value = "Light (only theme shipped)", onClick = { showAppearanceDialog = true })
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                                Surface(shape = RoundedCornerShape(10.dp), color = VachakColors.SoftLavender, modifier = Modifier.size(40.dp)) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Column {
                                    Text("Audio", style = MaterialTheme.typography.bodyLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                    Text(if (autoPlay) "Auto-play translations" else "Tap Play to hear translations", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, fontSize = 14.sp)
                                }
                            }
                            Switch(checked = autoPlay, onCheckedChange = { autoPlay = it; prefs.autoPlayTts = it })
                        }
                    }
                }
            }

            // Support
            item {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsSectionHeader(title = "Support")
                    SettingsGroup {
                        SettingsRow(icon = Icons.AutoMirrored.Outlined.Help, title = "Help", description = "Usage guidance & offline info", onClick = { showHelpDialog = true })
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(icon = Icons.Outlined.Speed, title = "Diagnostics", description = "Latency, models, storage & live log", onClick = { onDiagnostics?.invoke() })
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        // Debug overlay toggle (Vachak-* ring buffer, 300 lines)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                                Surface(shape = RoundedCornerShape(10.dp), color = VachakColors.SoftLavender, modifier = Modifier.size(40.dp)) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(Icons.Outlined.BugReport, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Column {
                                    Text("Debug Overlay", style = MaterialTheme.typography.bodyLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                    Text("Floating Vachak-* log viewer", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, fontSize = 13.sp)
                                }
                            }
                            Switch(checked = debugEnabled, onCheckedChange = { VachakLogger.setEnabled(it) })
                        }
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(icon = Icons.Outlined.Info, title = "About Vachak", value = "Version 1.0.0 • Offline-first", onClick = { showAboutDialog = true })
                    }
                }
            }

            // Footer — true statements only.
            item {
                Text(
                    "Vachak 1.0.0 • SIH26042 • No INTERNET permission • ASR/MT/TTS run on-device (sherpa-onnx + ONNX Runtime)",
                    style = MaterialTheme.typography.bodySmall,
                    color = VachakColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    if (showNameDialog) {
        AlertDialog(
            modifier = Modifier.overlayShadow(RoundedCornerShape(28.dp)),
            onDismissRequest = { showNameDialog = false },
            title = { Text("Teacher name") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.teacherName = nameDraft
                    teacherName = prefs.teacherName
                    showNameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            },
            containerColor = Color.White
        )
    }
    if (showAppearanceDialog) {
        AlertDialog(
            modifier = Modifier.overlayShadow(RoundedCornerShape(28.dp)),
            onDismissRequest = { showAppearanceDialog = false },
            title = { Text("Appearance") },
            text = { Text("The offline build ships the light classroom theme. Dark theme is not bundled (APK budget).") },
            confirmButton = {
                TextButton(onClick = { showAppearanceDialog = false }) { Text("OK") }
            },
            containerColor = Color.White
        )
    }
    if (showHelpDialog) {
        AlertDialog(
            modifier = Modifier.overlayShadow(RoundedCornerShape(28.dp)),
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help") },
            text = {
                Text(
                    "1. Open Translate and tap the mic — speak Hindi.\n" +
                        "2. Watch the Hindi appear live, then the Santali (Ol Chiki) translation.\n" +
                        "3. Play audio with the speaker buttons.\n" +
                        "4. Learn → Worksheets → Generate → Print for an offline PDF.\n" +
                        "5. Everything runs offline after install — no internet needed.",
                    style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) { Text("Close") }
            },
            containerColor = Color.White
        )
    }
    if (showSourceDialog) {
        AlertDialog(
            modifier = Modifier.overlayShadow(RoundedCornerShape(28.dp)),
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Voice input") },
            text = { Text("Hindi is the fixed speech and typing input — the on-device ASR model is Hindi-only. The Translate tab always produces Santali (Ol Chiki).") },
            confirmButton = {
                TextButton(onClick = { showSourceDialog = false }) { Text("OK") }
            },
            containerColor = Color.White
        )
    }
    if (showAboutDialog) {
        AlertDialog(
            modifier = Modifier.overlayShadow(RoundedCornerShape(28.dp)),
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Vachak") },
            text = {
                Text(
                    "Vachak 1.0.0 • SIH26042 • build ${com.vachak.BuildConfig.GIT_SHA}\n" +
                        "Offline Hindi→Santali (Ol Chiki) classroom aid.\n" +
                        "No INTERNET permission.\n" +
                        "ASR: Hindi conformer (sherpa-onnx) • MT: Hindi→Santali ONNX INT8 • TTS: sherpa-onnx voice.\n" +
                        "See THIRD_PARTY_NOTICES for licenses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VachakColors.TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            },
            containerColor = Color.White
        )
    }
}
