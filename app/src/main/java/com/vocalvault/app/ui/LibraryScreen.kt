package com.vocalvault.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vocalvault.app.data.Playlist
import com.vocalvault.app.data.Recording
import com.vocalvault.app.data.Track
import com.vocalvault.app.ui.theme.Accent
import com.vocalvault.app.ui.theme.Bg
import com.vocalvault.app.ui.theme.Surface1
import com.vocalvault.app.ui.theme.Surface2
import com.vocalvault.app.ui.theme.TextHi
import com.vocalvault.app.ui.theme.TextLow
import com.vocalvault.app.ui.theme.TextMid
import java.text.DateFormat
import java.util.Date

/** Library navigation, encoded as a string so it survives rotation via rememberSaveable. */
sealed interface LibraryDestination {
    val route: String
    data object Root : LibraryDestination { override val route = "root" }
    data object Saved : LibraryDestination { override val route = "saved" }
    data object Downloads : LibraryDestination { override val route = "downloads" }
    data object Recordings : LibraryDestination { override val route = "takes" }
    data class PlaylistDest(val id: String) : LibraryDestination { override val route = "pl:$id" }

    companion object {
        fun from(route: String): LibraryDestination = when {
            route == "saved" -> Saved
            route == "downloads" -> Downloads
            route == "takes" -> Recordings
            route.startsWith("pl:") -> PlaylistDest(route.removePrefix("pl:"))
            else -> Root
        }
    }
}


@Composable
fun LibraryScreen(
    destination: LibraryDestination,
    onNavigate: (LibraryDestination) -> Unit,
    saved: List<Track>,
    playlists: List<Playlist>,
    offline: List<Track>,
    downloaded: Set<String>,
    vocalTags: Set<String>,
    recordings: List<Recording>,
    playingTakeId: String?,
    bottomPadding: Dp,
    onPlay: (List<Track>, Int, Boolean) -> Unit,
    onCreatePlaylist: () -> Unit,
    onImportPlaylist: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onRemoveFromPlaylist: (String, String) -> Unit,
    onMoveInPlaylist: (String, Int, Int) -> Unit,
    onDownloadAll: (List<Track>) -> Unit,
    onRemoveDownload: (String) -> Unit,
    onPlayTake: (Recording) -> Unit,
    onStopTake: () -> Unit,
    onDeleteTake: (String) -> Unit,
) {
    BackHandler(enabled = destination != LibraryDestination.Root) { onNavigate(LibraryDestination.Root) }
    val back = { onNavigate(LibraryDestination.Root) }
    val downloadedTracks = offline.filter { it.trackId in downloaded }

    when (destination) {
        LibraryDestination.Saved -> {
            var vocalOnly by rememberSaveable { mutableStateOf(false) }
            val shown = if (vocalOnly) saved.filter { it.isLikelyVocal || it.trackId in vocalTags } else saved
            CollectionScreen(
                title = "Saved songs",
                tracks = shown,
                cover = { m -> GradientTile(Icons.Rounded.Favorite, SavedGradient, m, corner = 10.dp, iconSize = 72.dp) },
                bottomPadding = bottomPadding,
                onBack = back,
                onPlay = onPlay,
                downloaded = downloaded,
                onDownloadAll = onDownloadAll,
                filters = { if (saved.isNotEmpty()) Chip("Vocals only", vocalOnly, { vocalOnly = !vocalOnly }) },
                empty = { EmptyMessage(if (saved.isEmpty()) "Songs you save show up here" else "No vocal-tagged songs", "Tap the heart on any song.", icon = Icons.Rounded.FavoriteBorder) },
            )
            return
        }
        LibraryDestination.Downloads -> {
            CollectionScreen(
                title = "Downloads",
                tracks = downloadedTracks,
                cover = { m -> GradientTile(Icons.Rounded.Download, DownloadsGradient, m, corner = 10.dp, iconSize = 72.dp) },
                bottomPadding = bottomPadding,
                onBack = back,
                onPlay = onPlay,
                downloaded = downloaded,
                onDownloadAll = null,
                rowActions = { t -> listOf(SongMenuAction("Remove download", Icons.Rounded.RemoveCircleOutline) { onRemoveDownload(t.trackId) }) },
                empty = { EmptyMessage("Nothing downloaded", "Downloaded songs play with no connection. Long-press a song, then Download.", icon = Icons.Rounded.CloudOff) },
            )
            return
        }
        LibraryDestination.Recordings -> {
            TakesScreen(recordings, playingTakeId, bottomPadding, back, onPlayTake, onStopTake, onDeleteTake)
            return
        }
        is LibraryDestination.PlaylistDest -> {
            val playlist = playlists.firstOrNull { it.id == destination.id }
            if (playlist == null) { androidx.compose.runtime.LaunchedEffect(Unit) { back() }; return }
            var editing by rememberSaveable(playlist.id) { mutableStateOf(false) }
            var menu by remember { mutableStateOf(false) }
            var rename by remember { mutableStateOf(false) }
            var confirmDelete by remember { mutableStateOf(false) }
            CollectionScreen(
                title = playlist.name,
                tracks = playlist.tracks,
                cover = { m -> Mosaic(playlist.tracks, m, corner = 10.dp) },
                bottomPadding = bottomPadding,
                onBack = back,
                onPlay = onPlay,
                downloaded = downloaded,
                onDownloadAll = onDownloadAll,
                editing = editing,
                onMove = { a, b -> onMoveInPlaylist(playlist.id, a, b) },
                rowActions = { t -> listOf(SongMenuAction("Remove from playlist", Icons.Rounded.RemoveCircleOutline) { onRemoveFromPlaylist(playlist.id, t.trackId) }) },
                topActions = {
                    if (editing) {
                        TextButton(onClick = { editing = false }) { Text("Done", color = Accent) }
                    } else Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = TextHi) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Surface2) {
                            DropdownMenuItem(text = { Text("Edit order") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; editing = true })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = true })
                            DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
                empty = { EmptyMessage("This playlist is empty", "Long-press any song, then Add to playlist.", icon = Icons.AutoMirrored.Rounded.PlaylistAdd) },
            )
            if (rename) PlaylistNameDialog("Rename playlist", "Save", { rename = false }, { onRenamePlaylist(playlist.id, it); rename = false }, playlist.name)
            if (confirmDelete) AlertDialog(
                onDismissRequest = { confirmDelete = false },
                containerColor = Surface1,
                title = { Text("Delete \"${playlist.name}\"?") },
                text = { Text("This can't be undone.", color = TextMid) },
                confirmButton = { TextButton(onClick = { confirmDelete = false; back(); onDeletePlaylist(playlist.id) }) { Text("Delete", color = Accent) } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = TextMid) } },
            )
            return
        }
        LibraryDestination.Root -> Unit
    }

    // ---- Root: Apple Music-style list ----
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Library", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onCreatePlaylist) { Icon(Icons.Rounded.Add, contentDescription = "New playlist", tint = Accent) }
            }
        }
        item { NavRow(Icons.Rounded.Favorite, "Saved songs", tracksLabel(saved.size)) { onNavigate(LibraryDestination.Saved) } }
        item { NavRow(Icons.Rounded.DownloadDone, "Downloads", tracksLabel(downloadedTracks.size)) { onNavigate(LibraryDestination.Downloads) } }
        item { NavRow(Icons.Rounded.Link, "Import from YouTube", null, onImportPlaylist) }

        item {
            Text("Playlists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 8.dp))
        }
        item {
            LibraryRow("New playlist…", "", onCreatePlaylist) { m ->
                Box(m.background(Surface1), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(28.dp)) }
            }
        }
        items(playlists, key = { it.id }) { p ->
            LibraryRow(p.name, tracksLabel(p.tracks.size), { onNavigate(LibraryDestination.PlaylistDest(p.id)) }) { m -> Mosaic(p.tracks, m) }
        }

        if (saved.isNotEmpty()) {
            item { Text("Recently saved", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 10.dp)) }
            items(saved.take(6).chunked(2)) { pair ->
                Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { t ->
                        MediaCard(t.title, t.artist, { onPlay(saved, saved.indexOf(t), false) }, Modifier.weight(1f), width = androidx.compose.ui.unit.Dp.Unspecified) { m ->
                            Artwork(t.artworkUrl, m, corner = 8.dp)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Accent icon + label + chevron, hairline under it (Apple Music Library). */
@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, detail: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(18.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(19f, androidx.compose.ui.unit.TextUnitType.Sp)), modifier = Modifier.weight(1f))
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = TextMid)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextLow, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.padding(start = 62.dp).fillMaxWidth().height(0.5.dp).background(Surface2))
    }
}

@Composable
private fun LibraryRow(title: String, subtitle: String, onClick: () -> Unit, art: @Composable (Modifier) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art(Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMid, maxLines = 1)
        }
    }
}

/** Shared page for Saved, Downloads and playlists: Spotify-style gradient header + big play button. */
@Composable
private fun CollectionScreen(
    title: String,
    tracks: List<Track>,
    cover: @Composable (Modifier) -> Unit,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int, Boolean) -> Unit,
    downloaded: Set<String>,
    onDownloadAll: ((List<Track>) -> Unit)?,
    editing: Boolean = false,
    onMove: (Int, Int) -> Unit = { _, _ -> },
    rowActions: (Track) -> List<SongMenuAction> = { emptyList() },
    topActions: @Composable () -> Unit = {},
    filters: @Composable () -> Unit = {},
    empty: @Composable () -> Unit,
) {
    val tint = rememberArtworkTint(tracks.firstOrNull()?.artworkUrl)
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, firstIndex = 1, count = { tracks.size }, onMove = onMove)
    val allDownloaded = tracks.isNotEmpty() && tracks.all { it.trackId in downloaded }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        item {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(tint, Bg))).statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextHi) }
                    Spacer(Modifier.weight(1f))
                    topActions()
                }
                cover(Modifier.size(208.dp).shadow(24.dp, RoundedCornerShape(10.dp)))
                Spacer(Modifier.height(18.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp))
                Text(
                    "${tracksLabel(tracks.size)} · ${formatLongDuration(tracks.sumOf { it.durationMs })}",
                    style = MaterialTheme.typography.bodyMedium, color = TextMid,
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    filters()
                    if (onDownloadAll != null && tracks.isNotEmpty()) {
                        IconButton(onClick = { onDownloadAll(tracks) }, enabled = !allDownloaded) {
                            Icon(
                                if (allDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                                contentDescription = "Download all",
                                tint = if (allDownloaded) Accent else TextMid,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (tracks.isNotEmpty()) {
                        IconButton(onClick = { onPlay(tracks, (tracks.indices).random(), true) }) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = TextMid, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        BigPlayButton(onClick = { onPlay(tracks, 0, false) })
                    }
                }
            }
        }
        if (tracks.isEmpty()) item { empty() }
        itemsIndexed(tracks, key = { _, t -> t.trackId }) { index, track ->
            TrackRow(
                track = track,
                onClick = { if (!editing) onPlay(tracks, index, false) },
                modifier = Modifier.reorderItem(reorder, index).background(if (reorder.draggingIndex == index) Surface1 else androidx.compose.ui.graphics.Color.Transparent),
                extraActions = rowActions(track),
                trailing = if (editing) ({ DragHandle(reorder, index) }) else null,
            )
        }
    }
}

@Composable
fun TakesScreen(
    recordings: List<Recording>,
    playingTakeId: String?,
    bottomPadding: Dp,
    onBack: (() -> Unit)?,
    onPlay: (Recording) -> Unit,
    onStop: () -> Unit,
    onDelete: (String) -> Unit,
) {
    var confirm by remember { mutableStateOf<Recording?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        item {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(RecordingsGradient[0].copy(alpha = 0.55f), Bg))).statusBarsPadding()) {
                if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextHi) }
                else Spacer(Modifier.height(18.dp))
                Text("Takes", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 16.dp))
                Text(
                    "Your voice plays together with the vocal, from where you started recording.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMid, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                )
            }
        }
        if (recordings.isEmpty()) item { EmptyMessage("Sing your first take", "Open any song, tap Practice, then Record a take. Headphones give the cleanest sound.", icon = Icons.Rounded.Mic) }
        items(recordings, key = { it.id }) { r ->
            val playing = r.id == playingTakeId
            Row(
                Modifier.fillMaxWidth().clickable { if (playing) onStop() else onPlay(r) }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Artwork(r.track.artworkUrl, Modifier.size(52.dp))
                    Box(Modifier.size(30.dp).clip(CircleShape).background(if (playing) Accent else Bg.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (playing) "Stop" else "Play take", tint = TextHi, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.track.title, style = MaterialTheme.typography.bodyLarge, color = if (playing) Accent else TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.createdAt))} · ${formatDuration(r.durationMs)}",
                        style = MaterialTheme.typography.bodyMedium, color = TextMid, maxLines = 1,
                    )
                }
                IconButton(onClick = { confirm = r }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete take", tint = TextLow) }
            }
        }
    }
    confirm?.let { r ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Surface1,
            title = { Text("Delete this take?") },
            text = { Text("The recording file is removed for good.", color = TextMid) },
            confirmButton = { TextButton(onClick = { onDelete(r.id); confirm = null }) { Text("Delete", color = Accent) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = TextMid) } },
        )
    }
}
