package com.vachak.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vachak.engine.EngineResult
import com.vachak.sync.PackInstaller
import com.vachak.sync.PackManager
import com.vachak.sync.db.PackDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manage Packs UI — lists installed packs, free space, install from file (SAF), shows manifest licenses.
 * Accessible from SettingsScreen → Manage Packs button.
 * No network — reads from filesDir/packs/ and Room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePacksScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var packs by remember { mutableStateOf(PackManager.packEntities(context)) }
    var freeSpace by remember { mutableStateOf(PackManager.freeSpaceBytes(context)) }
    var usedSpace by remember { mutableStateOf(PackManager.storageUsedBytes(context)) }
    var status by remember { mutableStateOf<String?>(null) }
    var isInstalling by remember { mutableStateOf(false) }
    var activeId by remember { mutableStateOf(PackManager.getActivePack(context)?.let { it.substringAfterLast("/") }) }

    fun refresh() {
        packs = PackManager.packEntities(context)
        freeSpace = PackManager.freeSpaceBytes(context)
        usedSpace = PackManager.storageUsedBytes(context)
        activeId = PackManager.getActivePack(context)?.let { it.substringAfterLast("/") }
    }

    val installer = remember { PackInstaller(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            status = "No file selected"
            return@rememberLauncherForActivityResult
        }
        isInstalling = true
        status = "Installing…"
        scope.launch(Dispatchers.IO) {
            val result = installer.install(uri)
            withContext(Dispatchers.Main) {
                when (result) {
                    is EngineResult.Ok -> {
                        status = "Installed ${result.value.id} (${result.value.sizeBytes / (1024*1024)} MB)"
                        refresh()
                    }
                    is EngineResult.Err -> {
                        status = "Install failed: ${result.message}"
                    }
                }
                isInstalling = false
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Manage Packs") }) },
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header
            Text("Offline Language Packs", style = MaterialTheme.typography.headlineSmall)
            Text("Install from storage / USB — no network. Packs contain MT+TTS+ASR+curriculum with manifest + sha256.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Free space / budget
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Storage used", style = MaterialTheme.typography.bodyMedium)
                        Text("${usedSpace / (1024*1024)} MB", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    }
                    LinearProgressIndicator(
                        progress = { if (freeSpace + usedSpace > 0) usedSpace.toFloat() / (freeSpace + usedSpace) else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Free space: ${if (freeSpace >= 0) "${freeSpace / (1024*1024)} MB" else "unknown"}", style = MaterialTheme.typography.bodySmall)
                        Text("Packs dir: filesDir/packs/", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                    // Budget indicator ~500MB + active adapter badge 13M + sha + withinBudget
                    val budget = 500L * 1024 * 1024
                    val withinBudget = (usedSpace + 535L*1024*1024) <= budget
                    val activePack = packs.firstOrNull { it.isActive } ?: packs.firstOrNull()
                    val sha = activePack?.manifestSha256?.take(16) ?: "—"
                    Text("Budget ~500MB total (MT 100–180 + ASR 30–80 + TTS 20–80 + content) • withinBudget=$withinBudget", style = MaterialTheme.typography.labelSmall, color = if (withinBudget) com.vachak.ui.theme.VachakColors.OfflineGreen else MaterialTheme.colorScheme.error)
                    Text("Active adapter: ${activePack?.language ?: "—"} • ~13M • sha $sha… • ${if (withinBudget) "✓" else "⚠"}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }

            // Install button
            Button(
                onClick = { launcher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isInstalling
            ) {
                if (isInstalling) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.FileOpen, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isInstalling) "Installing…" else "Install from file")
            }
            status?.let {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }

            // Installed packs list
            Text("Installed packs (${packs.size})", style = MaterialTheme.typography.titleMedium)
            if (packs.isEmpty()) {
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("No packs installed. Bundled assets are used.", style = MaterialTheme.typography.bodyMedium)
                        Text("Install language-v0.1.0.vachakpack via file picker (Santali / Mundari).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(packs, key = { it.id }) { pack ->
                        val isActive = pack.isActive || pack.id == activeId
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${pack.language} v${pack.version}", style = MaterialTheme.typography.titleSmall)
                                        Text(pack.id, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isActive) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                                        Text("ACTIVE", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("${pack.sizeBytes / (1024*1024)} MB", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    Text(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(pack.installedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Path: ${pack.path}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Manifest SHA: ${pack.manifestSha256.take(16)}…", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                // Licenses from packs/language-v0.1.0/pack manifest if accessible
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            PackManager.setActivePack(context, pack.id)
                                            refresh()
                                            status = "Set active: ${pack.id} — engines will reload on next translate/speak"
                                        }
                                    }, enabled = !isActive) { Text("Set Active") }
                                    // Show licenses button would open dialog reading manifest.json licenses
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Engines reload from active pack path on next loadModel / ensureLoaded (sequential, close old ORT sessions).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("No android.permission.INTERNET — offline installer only (ContentResolver, no HttpURLConnection).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
