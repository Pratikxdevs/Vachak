package com.vachak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vachak.engine.ActiveLanguage
import com.vachak.engine.EngineProvider
import com.vachak.ui.components.ProfileCard
import com.vachak.ui.components.SettingsGroup
import com.vachak.ui.components.SettingsRow
import com.vachak.ui.components.SettingsSectionHeader
import com.vachak.ui.debug.VachakLogger
import com.vachak.ui.theme.VachakColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    engine: EngineProvider,
    onManagePacks: (() -> Unit)? = null,
    onDiagnostics: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showSignOutConfirm by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { com.vachak.ui.prefs.VachakPrefs(context) }
    var teacherName by remember { mutableStateOf(prefs.teacherName) }
    var grade by remember { mutableStateOf(prefs.grade) }
    var notificationsOn by remember { mutableStateOf(prefs.notificationsOn) }
    var autoPlay by remember { mutableStateOf(prefs.autoPlayTts) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showGradeDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    val activeLang by engine.activeLanguage.collectAsState()
    val debugEnabled by VachakLogger.enabled.collectAsState()
    var langExpanded by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = remember(configuration.screenWidthDp) { configuration.screenWidthDp >= 840 }
    val hPad = if (isTablet) 32.dp else 20.dp
    Box(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = hPad, end = hPad, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header (settings.md §3) — compact
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                        Text("Settings", style = MaterialTheme.typography.headlineLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("Manage your profile and app preferences.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Surface(shape = RoundedCornerShape(50), color = VachakColors.SuccessLight, modifier = Modifier.height(32.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(VachakColors.Success))
                            Text("Offline", style = MaterialTheme.typography.labelSmall, color = VachakColors.Success, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Profile (settings.md §4)
            item {
                ProfileCard(name = teacherName, role = "Teacher", onEdit = { nameDraft = teacherName; showNameDialog = true })
            }

            // Learning Preferences
            item {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsSectionHeader(title = "Learning Preferences")
                    SettingsGroup {
                        SettingsRow(icon = Icons.Outlined.Language, title = "Language", value = "Hindi (source)", onClick = { showSourceDialog = true })
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        // Active target language switcher Santali <-> Mundari
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
                                    Text(ActiveLanguage.label(activeLang), style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, fontSize = 14.sp)
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
                                Text("✓ Ol Chiki U+1C50", style = MaterialTheme.typography.labelSmall, color = VachakColors.Success, fontWeight = FontWeight.SemiBold)
                                Text("tick per language", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("✓ Deva", style = MaterialTheme.typography.labelSmall, color = VachakColors.Success, fontWeight = FontWeight.SemiBold)
                                Text("Mundari adapter", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                            }
                        }
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(icon = Icons.Outlined.School, title = "Grade / Curriculum", value = "Grade $grade", onClick = { showGradeDialog = true })
                    }
                }
            }

            // Offline & Storage (settings.md §10)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsSectionHeader(title = "Offline & Storage")
                    SettingsGroup {
                        SettingsRow(icon = Icons.Outlined.Folder, title = "Language Packs", description = "Manage downloaded language packs", onClick = { onManagePacks?.invoke() })
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(icon = Icons.Outlined.Storage, title = "Storage", value = "312 MB of 500 MB used", onClick = { onDiagnostics?.invoke() })
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(icon = Icons.Outlined.CloudOff, title = "Offline Content", value = "Available for offline use", onClick = { onManagePacks?.invoke() })
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
                        SettingsRow(icon = Icons.Outlined.Palette, title = "Appearance", value = "Light", onClick = { showAppearanceDialog = true })
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
                                    Text(if (autoPlay) "Playback enabled • Auto-play" else "Playback enabled • Tap to play", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, fontSize = 14.sp)
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
                        SettingsRow(icon = Icons.Outlined.Speed, title = "Diagnostics", description = "Latency, RAM, storage & models", onClick = { onDiagnostics?.invoke() })
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        // Debug overlay toggle (Vachak-* ring buffer 200 lines)
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
                                    Text("Vachak-* log viewer (200 lines)", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, fontSize = 13.sp)
                                }
                            }
                            Switch(checked = debugEnabled, onCheckedChange = { VachakLogger.setEnabled(it) })
                        }
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(icon = Icons.Outlined.Info, title = "About Vachak", value = "Version 1.0.0 • Offline-first", onClick = { showAboutDialog = true })
                    }
                }
            }

            // Account
            item {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsSectionHeader(title = "Account")
                    SettingsGroup {
                        SettingsRow(icon = Icons.AutoMirrored.Outlined.Logout, title = "Sign Out", onClick = { showSignOutConfirm = true })
                    }
                }
            }

            // Footer
            item {
                Text(
                    "Vachak 1.0.0 • SIH26042 • Jharkhand • No INTERNET permission • GPL-3.0 Piper",
                    style = MaterialTheme.typography.bodySmall,
                    color = VachakColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign out?") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Sign Out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
            },
            containerColor = Color.White
        )
    }
    if (showNameDialog) {
        AlertDialog(
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
    if (showGradeDialog) {
        AlertDialog(
            onDismissRequest = { showGradeDialog = false },
            title = { Text("Grade / Curriculum") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { g ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { grade = g; prefs.grade = g; showGradeDialog = false }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(selected = grade == g, onClick = { grade = g; prefs.grade = g; showGradeDialog = false })
                            Text("Grade $g", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGradeDialog = false }) { Text("Done") }
            },
            containerColor = Color.White
        )
    }
    if (showAppearanceDialog) {
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            title = { Text("Appearance") },
            text = { Text("The offline build ships the light lavender theme. Dark theme is not bundled (APK budget).") },
            confirmButton = {
                TextButton(onClick = { showAppearanceDialog = false }) { Text("OK") }
            },
            containerColor = Color.White
        )
    }
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help") },
            text = {
                Text(
                    "1. Open Live and tap the mic — speak Hindi.\n" +
                        "2. Watch the Hindi appear live, then the Santali (Ol Chiki) translation.\n" +
                        "3. Play audio with the speaker buttons.\n" +
                        "4. Tools → Worksheets → Generate → Print for an offline PDF.\n" +
                        "5. Everything runs offline after first launch — no internet needed.",
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
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Source language") },
            text = { Text("Hindi (Devanagari) is the fixed speech and typing input — the ASR model is Hindi-only. Target is Santali (Ol Chiki) or Mundari via the toggle.") },
            confirmButton = {
                TextButton(onClick = { showSourceDialog = false }) { Text("OK") }
            },
            containerColor = Color.White
        )
    }
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Vachak") },
            text = {
                Text(
                    "Vachak 1.0.0 • SIH26042 • Jharkhand\nOffline-first Hindi→Santali (Ol Chiki) classroom aid.\nNo INTERNET permission.\nASR: IndicConformer (sherpa-onnx) • MT: IndicTrans2 INT8 • TTS: sherpa-onnx.\nSee THIRD_PARTY_NOTICES for licenses.",
                    style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            },
            containerColor = Color.White
        )
    }
}
