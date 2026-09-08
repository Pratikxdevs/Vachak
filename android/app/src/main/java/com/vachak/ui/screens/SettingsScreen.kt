package com.vachak.ui.screens

import androidx.compose.foundation.background
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
                ProfileCard(name = "Vaibhav", role = "Teacher", onEdit = {})
            }

            // Learning Preferences
            item {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsSectionHeader(title = "Learning Preferences")
                    SettingsGroup {
                        SettingsRow(icon = Icons.Outlined.Language, title = "Language", value = "Hindi (source)", onClick = {})
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
                        SettingsRow(icon = Icons.Outlined.School, title = "Grade / Curriculum", value = "Grade 1", onClick = {})
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
                        SettingsRow(icon = Icons.Outlined.CloudOff, title = "Offline Content", value = "Available for offline use", onClick = {})
                    }
                }
            }

            // App Preferences
            item {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsSectionHeader(title = "App Preferences")
                    SettingsGroup {
                        SettingsRow(icon = Icons.Outlined.Notifications, title = "Notifications", value = "Learning reminders ON", onClick = {})
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(icon = Icons.Outlined.Palette, title = "Appearance", value = "Light", onClick = {})
                        HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(icon = Icons.AutoMirrored.Outlined.VolumeUp, title = "Audio", value = "Playback enabled • Auto-play", onClick = {})
                    }
                }
            }

            // Support
            item {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsSectionHeader(title = "Support")
                    SettingsGroup {
                        SettingsRow(icon = Icons.AutoMirrored.Outlined.Help, title = "Help", description = "Usage guidance & offline info", onClick = {})
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
                        SettingsRow(icon = Icons.Outlined.Info, title = "About Vachak", value = "Version 1.0.0 • Offline-first", onClick = {})
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
}
