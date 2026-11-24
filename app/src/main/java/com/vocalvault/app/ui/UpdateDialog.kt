package com.vocalvault.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocalvault.app.ui.theme.Accent
import com.vocalvault.app.ui.theme.Surface1
import com.vocalvault.app.ui.theme.Surface2
import com.vocalvault.app.ui.theme.TextMid
import com.vocalvault.app.update.UpdateState
import com.vocalvault.app.update.Updater

@Composable
fun UpdateDialog(updater: Updater) {
    val state by updater.state.collectAsStateWithLifecycle()
    val visible by updater.prompt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (updater.canInstall()) updater.install(context)
    }
    if (!visible) return

    val s = state
    val release = when (s) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.Ready -> s.release
        is UpdateState.Failed -> s.release
        else -> null
    } ?: return
    val dismiss = { if (s is UpdateState.Downloading) updater.hide() else updater.later() }

    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = Surface1,
        title = { Text(if (s is UpdateState.Ready) "Ready to install" else "Version ${release.version} is out") },
        text = {
            Column {
                val notes = plainNotes(release.notes)
                if (notes.isNotEmpty()) {
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodyMedium, color = TextMid,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    )
                }
                when (s) {
                    is UpdateState.Downloading -> {
                        Spacer(Modifier.height(18.dp))
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Surface2)
                        Spacer(Modifier.height(8.dp))
                        Text("${(s.progress * 100).toInt()}% of ${megabytes(release.size)}", style = MaterialTheme.typography.bodyMedium, color = TextMid)
                    }
                    is UpdateState.Failed -> {
                        Spacer(Modifier.height(14.dp))
                        Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    is UpdateState.Available -> {
                        Spacer(Modifier.height(14.dp))
                        Text("Download size ${megabytes(release.size)}", style = MaterialTheme.typography.bodyMedium, color = TextMid)
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (s) {
                is UpdateState.Available -> TextButton(onClick = updater::download) { Text("Update", color = Accent) }
                is UpdateState.Failed -> TextButton(onClick = updater::download) { Text("Try again", color = Accent) }
                is UpdateState.Downloading -> TextButton(onClick = updater::hide) { Text("Hide", color = Accent) }
                is UpdateState.Ready -> TextButton(onClick = {
                    if (updater.canInstall()) updater.install(context)
                    else runCatching { permission.launch(updater.installPermissionIntent()) }
                }) { Text("Install", color = Accent) }
                else -> Unit
            }
        },
        dismissButton = {
            if (s is UpdateState.Downloading) TextButton(onClick = updater::cancelDownload) { Text("Cancel", color = TextMid) }
            else TextButton(onClick = updater::later) { Text("Later", color = TextMid) }
        },
    )
}

private fun megabytes(bytes: Long) = "%.1f MB".format(bytes / 1_048_576f)

/** Release notes are Markdown; show the bullets without the syntax. */
private fun plainNotes(markdown: String): String = markdown.lines().joinToString("\n") { line ->
    line.trim().replace(Regex("^#+\\s*"), "").replace(Regex("^[-*]\\s+"), "• ").replace("**", "").replace("`", "")
}.trim()
