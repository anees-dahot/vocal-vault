package com.vocalvault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vocalvault.app.BuildConfig
import com.vocalvault.app.data.AudioQuality
import com.vocalvault.app.data.ThemeMode
import com.vocalvault.app.ui.theme.AccentOptions
import com.vocalvault.app.ui.theme.Accent
import com.vocalvault.app.ui.theme.Surface1
import com.vocalvault.app.ui.theme.Surface2
import com.vocalvault.app.ui.theme.TextHi
import com.vocalvault.app.ui.theme.TextMid
import com.vocalvault.app.update.UpdateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    themeMode: ThemeMode,
    accent: Int,
    quality: AudioQuality,
    downloadsBytes: Long,
    downloadCount: Int,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (Int) -> Unit,
    onQuality: (AudioQuality) -> Unit,
    onImportPlaylist: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onClearHistory: () -> Unit,
    updateState: UpdateState,
    autoUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))

            Group("Appearance")
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m -> Chip(m.label, m == themeMode, { onThemeMode(m) }) }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentOptions.forEachIndexed { i, option ->
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(option.color)
                            .border(2.dp, if (i == accent) TextHi else Surface1, CircleShape)
                            .clickable { onAccent(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (i == accent) Icon(Icons.Rounded.Check, contentDescription = option.name, tint = option.onColor, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Group("Streaming quality")
            AudioQuality.entries.forEach { q ->
                Row(
                    Modifier.fillMaxWidth().clickable { onQuality(q) }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(q.label, style = MaterialTheme.typography.bodyLarge, color = if (q == quality) Accent else TextHi)
                        Text(q.description, style = MaterialTheme.typography.bodyMedium, color = TextMid)
                    }
                    if (q == quality) Icon(Icons.Rounded.Check, contentDescription = null, tint = Accent)
                }
            }

            Group("Library")
            SheetItem(Icons.Rounded.Link, "Import a YouTube playlist", onImportPlaylist)
            SheetItem(Icons.Rounded.FileUpload, "Back up library to a file", onBackup)
            SheetItem(Icons.Rounded.FileDownload, "Restore from backup", onRestore)
            SheetItem(Icons.Rounded.DeleteSweep, "Clear listening history", onClearHistory)

            Group("Storage")
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Storage, contentDescription = null, tint = TextMid)
                Spacer(Modifier.width(18.dp))
                Text("${tracksLabel(downloadCount)} downloaded · ${"%.1f".format(downloadsBytes / 1_048_576f)} MB", style = MaterialTheme.typography.bodyLarge)
            }

            Group("Updates")
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onCheckUpdate).padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = TextMid)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (updateState) {
                            is UpdateState.Available, is UpdateState.Ready -> "Update available"
                            is UpdateState.Downloading -> "Downloading update"
                            else -> "Check for updates"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        when (updateState) {
                            UpdateState.Checking -> "Checking…"
                            UpdateState.UpToDate -> "You have the latest version, ${BuildConfig.VERSION_NAME}"
                            is UpdateState.Available -> "Version ${updateState.release.version}"
                            is UpdateState.Downloading -> "${(updateState.progress * 100).toInt()}%"
                            is UpdateState.Ready -> "Version ${updateState.release.version} is ready to install"
                            is UpdateState.Failed -> updateState.message
                            UpdateState.Idle -> "Version ${BuildConfig.VERSION_NAME}"
                        },
                        style = MaterialTheme.typography.bodyMedium, color = TextMid,
                    )
                }
                if (updateState == UpdateState.Checking) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                }
            }
            Row(
                Modifier.fillMaxWidth().toggleable(autoUpdate, role = Role.Switch, onValueChange = onAutoUpdate)
                    .padding(start = 62.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Check automatically", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = autoUpdate, onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                )
            }

            Box(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).clip(RoundedCornerShape(10.dp)).background(Surface2).padding(14.dp)) {
                Text(
                    "Vocal Vault streams public YouTube uploads. Lyrics come from LRCLIB.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMid,
                )
            }
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = TextMid,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
    )
}
