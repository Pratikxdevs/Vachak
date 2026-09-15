package com.vachak.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vachak.engine.EngineResult
import com.vachak.sync.PackInstaller
import com.vachak.sync.PackManager
import com.vachak.ui.components.BreadcrumbTrail
import com.vachak.ui.components.GuidedEmpty
import com.vachak.ui.components.InsetWell
import com.vachak.ui.components.StatusRow
import com.vachak.ui.theme.VachakColors
import com.vachak.ui.theme.cardShadow
import com.vachak.ui.theme.tabletHPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manage Packs — installed packs, free space, install from file (SAF).
 * No network — reads from filesDir/packs/ and Room.
 */
@Composable
fun ManagePacksScreen(onBack: () -> Unit = {}, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var packs by remember { mutableStateOf(PackManager.packEntities(context)) }
    var freeSpace by remember { mutableStateOf(PackManager.freeSpaceBytes(context)) }
    var usedSpace by remember { mutableStateOf(PackManager.storageUsedBytes(context)) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(true) }
    var isInstalling by remember { mutableStateOf(false) }
    var activeId by remember { mutableStateOf(PackManager.getActivePack(context)?.let { it.substringAfterLast("/") }) }
    // Installed curriculum grades per pack (v0.2.0+): pack.path/curriculum/class/{g}/manifest.json
    var packCurriculum by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    fun refreshCurriculum(ids: List<String>) {
        scope.launch(Dispatchers.IO) {
            val found = mutableMapOf<String, List<String>>()
            for (id in ids) {
                val packsDir = java.io.File(context.filesDir, "packs/$id/curriculum/class")
                val rows = (1..5).mapNotNull { g ->
                    val mf = java.io.File(packsDir, "$g/manifest.json")
                    if (!mf.isFile) return@mapNotNull null
                    try {
                        val m = org.json.JSONObject(mf.readText())
                        val ch = m.optJSONObject("chapters")?.length() ?: 0
                        "G$g: $ch ch"
                    } catch (_: Exception) { null }
                }
                if (rows.isNotEmpty()) found[id] = rows
            }
            withContext(Dispatchers.Main) { packCurriculum = found }
        }
    }

    fun refresh() {
        packs = PackManager.packEntities(context)
        freeSpace = PackManager.freeSpaceBytes(context)
        usedSpace = PackManager.storageUsedBytes(context)
        activeId = PackManager.getActivePack(context)?.let { it.substringAfterLast("/") }
        refreshCurriculum(packs.map { it.id })
    }
    LaunchedEffect(Unit) { refreshCurriculum(packs.map { it.id }) }

    val installer = remember { PackInstaller(context) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            status = "No file selected"
            statusOk = false
            return@rememberLauncherForActivityResult
        }
        isInstalling = true
        status = "Installing pack… (checksum verified before activation)"
        statusOk = true
        scope.launch(Dispatchers.IO) {
            val result = installer.install(uri)
            withContext(Dispatchers.Main) {
                when (result) {
                    is EngineResult.Ok -> {
                        status = "Installed ${result.value.id} (${result.value.sizeBytes / (1024 * 1024)} MB)"
                        statusOk = true
                        refresh()
                    }
                    is EngineResult.Err -> {
                        status = "Install failed: ${result.message}"
                        statusOk = false
                    }
                }
                isInstalling = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = VachakColors.TextPrimary) }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BreadcrumbTrail(listOf("More", "Packs"))
                    Text("Language Packs", style = MaterialTheme.typography.titleLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Install from storage / USB — no network", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = tabletHPad(20.dp), vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Budget card — measured bytes, tabular numbers.
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
                    modifier = Modifier.fillMaxWidth().cardShadow()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Storage used", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${usedSpace / (1024 * 1024)} MB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VachakColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        LinearProgressIndicator(
                            progress = { if (freeSpace + usedSpace > 0) usedSpace.toFloat() / (freeSpace + usedSpace) else 0f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                            color = VachakColors.Lavender600,
                            trackColor = VachakColors.Lavender200
                        )
                        val budgetOk = usedSpace <= 500L * 1024 * 1024
                        StatusRow(
                            dot = if (budgetOk) VachakColors.Success else VachakColors.ErrorRed,
                            title = if (freeSpace >= 0) "Free: ${freeSpace / (1024 * 1024)} MB • budget 500 MB" else "Budget 500 MB",
                            detail = "Packs live in filesDir/packs/ • ${if (budgetOk) "within budget" else "over budget"}"
                        )
                    }
                }
            }

            // Install button
            item {
                Button(
                    onClick = { launcher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !isInstalling
                ) {
                    if (isInstalling) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    else Icon(Icons.Outlined.FileOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isInstalling) "Installing…" else "Install from file")
                }
            }
            status?.let {
                item {
                    InsetWell {
                        StatusRow(
                            dot = if (statusOk) VachakColors.Success else VachakColors.ErrorRed,
                            title = if (statusOk) "Installer" else "Install failed",
                            detail = it
                        )
                    }
                }
            }

            // Installed packs list
            item {
                Text("Installed packs (${packs.size})", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
            }
            if (packs.isEmpty()) {
                item {
                    GuidedEmpty(
                        icon = Icons.Outlined.Inventory,
                        title = "No packs installed",
                        why = "The app runs on bundled assets. Install a .vachakpack to add models or curriculum.",
                        actionLabel = "Install from file",
                        onAction = { launcher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                    )
                }
            } else {
                items(packs, key = { it.id }) { pack ->
                    val isActive = pack.isActive || pack.id == activeId
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(
                            if (isActive) 1.5.dp else 1.dp,
                            if (isActive) VachakColors.DeepLavender else VachakColors.Border
                        ),
                        modifier = Modifier.fillMaxWidth().cardShadow(RoundedCornerShape(20.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${pack.language} v${pack.version}", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(pack.id, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = VachakColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (isActive) {
                                    Surface(shape = RoundedCornerShape(50), color = VachakColors.Lavender100) {
                                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Outlined.CheckCircle, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(14.dp))
                                            Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = VachakColors.DeepLavender, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${pack.sizeBytes / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = VachakColors.TextPrimary)
                                Text(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(pack.installedAt)), style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, fontFamily = FontFamily.Monospace)
                            }
                            Text("SHA ${pack.manifestSha256.take(16)}…", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = VachakColors.TextSecondary)
                            packCurriculum[pack.id]?.let { rows ->
                                Text(
                                    "Curriculum: ${rows.joinToString(" • ")}",
                                    style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary
                                )
                            }
                            if (!isActive) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            PackManager.setActivePack(context, pack.id)
                                            refresh()
                                            status = "Active: ${pack.id} — engines reload on next use"
                                            statusOk = true
                                        }
                                    },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.height(40.dp)
                                ) { Text("Set Active") }
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.6f), thickness = 0.8.dp)
                Text(
                    "Engines reload from the active pack on next use (sequential, old sessions closed first). No INTERNET permission — offline installer only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = VachakColors.TextSecondary
                )
            }
        }
    }
}
