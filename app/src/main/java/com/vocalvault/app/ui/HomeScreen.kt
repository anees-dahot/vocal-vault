package com.vocalvault.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vocalvault.app.data.HistoryEntry
import com.vocalvault.app.data.Playlist
import com.vocalvault.app.data.Track
import com.vocalvault.app.playback.PlayerUiState
import com.vocalvault.app.ui.theme.Accent
import com.vocalvault.app.ui.theme.Bg
import com.vocalvault.app.ui.theme.OnAccent
import com.vocalvault.app.ui.theme.Surface1
import com.vocalvault.app.ui.theme.TextHi
import com.vocalvault.app.ui.theme.TextMid

private class Pick(
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val artUrl: String?,
    val icon: ImageVector? = null,
    val gradient: List<Color>? = null,
    val onClick: () -> Unit,
)

@Composable
fun HomeScreen(
    player: PlayerUiState,
    history: List<HistoryEntry>,
    mostPlayed: List<Track>,
    saved: List<Track>,
    offline: List<Track>,
    downloaded: Set<String>,
    playlists: List<Playlist>,
    recordings: List<com.vocalvault.app.data.Recording>,
    bottomPadding: Dp,
    onPlay: (List<Track>, Int, Boolean) -> Unit,
    onResume: () -> Unit,
    onOpenCollection: (LibraryDestination) -> Unit,
    onOpenSettings: () -> Unit,
    onGoSearch: () -> Unit,
) {
    val recent = history.map { it.track }
    val downloadedTracks = offline.filter { it.trackId in downloaded }
    val isEmpty = history.isEmpty() && saved.isEmpty() && playlists.isEmpty()

    val picks = buildList {
        mostPlayed.firstOrNull()?.let { t -> add(Pick("Your most played", t.title, t.artist, t.artworkUrl) { onPlay(mostPlayed, 0, false) }) }
        if (saved.isNotEmpty()) add(Pick("Made for practice", "Saved songs", tracksLabel(saved.size), null, Icons.Rounded.Favorite, SavedGradient) { onOpenCollection(LibraryDestination.Saved) })
        recent.firstOrNull()?.takeIf { it.trackId != mostPlayed.firstOrNull()?.trackId }?.let { t ->
            add(Pick("Pick up where you left off", t.title, t.artist, t.artworkUrl) { onPlay(recent, 0, false) })
        }
        if (downloadedTracks.isNotEmpty()) add(Pick("Offline", "Downloads", tracksLabel(downloadedTracks.size), null, Icons.Rounded.DownloadDone, DownloadsGradient) { onOpenCollection(LibraryDestination.Downloads) })
        playlists.firstOrNull { it.tracks.isNotEmpty() }?.let { p ->
            add(Pick("Your playlist", p.name, tracksLabel(p.tracks.size), p.tracks.first().artworkUrl) { onOpenCollection(LibraryDestination.PlaylistDest(p.id)) })
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Home", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Surface1).clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Person, contentDescription = "Settings", tint = TextMid, modifier = Modifier.size(22.dp)) }
            }
        }

        if (isEmpty) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(Accent, Accent.copy(alpha = 0.55f)))).padding(24.dp),
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, tint = OnAccent, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Find a vocal to sing", style = MaterialTheme.typography.headlineSmall, color = OnAccent)
                    Text(
                        "Search any song. Slow it down, change key, loop a line and record yourself.",
                        style = MaterialTheme.typography.bodyMedium, color = OnAccent.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Bg).clickable(onClick = onGoSearch).padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text("Search", style = MaterialTheme.typography.labelLarge, color = TextHi)
                    }
                }
            }
            return@LazyColumn
        }

        if (picks.isNotEmpty()) {
            item {
                Text("Top picks for you", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 10.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(picks) { FeaturedCard(it) }
                }
            }
        }
        if (recent.isNotEmpty()) item { Shelf("Recently played", recent.take(20), onPlay) }
        if (playlists.isNotEmpty()) {
            item {
                SectionHeader("Your playlists", action = "See all", onAction = { onOpenCollection(LibraryDestination.Root) })
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        MediaCard(p.name, tracksLabel(p.tracks.size), { onOpenCollection(LibraryDestination.PlaylistDest(p.id)) }, width = 160.dp) { m -> Mosaic(p.tracks, m, corner = 10.dp) }
                    }
                }
            }
        }
        if (mostPlayed.size > 1) item { Shelf("On repeat", mostPlayed, onPlay) }
        if (downloadedTracks.isNotEmpty()) item { Shelf("Downloaded", downloadedTracks, onPlay) }
    }
}

/** Tall card coloured by its artwork (Apple Music "Top Picks"). */
@Composable
private fun FeaturedCard(pick: Pick) {
    val tint = rememberArtworkTint(pick.artUrl)
    val bg = pick.gradient?.let { Brush.verticalGradient(it) } ?: Brush.verticalGradient(listOf(tint, lerpDark(tint)))
    Column(Modifier.width(250.dp)) {
        Text(pick.eyebrow, style = MaterialTheme.typography.labelMedium, color = TextMid, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier.width(250.dp).height(310.dp).shadow(8.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                .background(bg).clickable(onClick = pick.onClick).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            if (pick.icon != null) {
                Icon(pick.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(84.dp))
            } else {
                Artwork(pick.artUrl, Modifier.size(170.dp).shadow(16.dp, RoundedCornerShape(8.dp)), corner = 8.dp)
            }
            Spacer(Modifier.weight(1f))
            Text(pick.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            Text(pick.subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f), maxLines = 1, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun lerpDark(c: Color) = androidx.compose.ui.graphics.lerp(c, Color.Black, 0.45f)

@Composable
private fun Shelf(title: String, tracks: List<Track>, onPlay: (List<Track>, Int, Boolean) -> Unit) {
    val playing = LocalPlayingInfo.current
    SectionHeader(title)
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(tracks.size) { i ->
            val t = tracks[i]
            MediaCard(t.title, t.artist, { onPlay(tracks, i, false) }, width = 160.dp) { m ->
                Box(contentAlignment = Alignment.BottomStart) {
                    Artwork(t.artworkUrl, m, corner = 10.dp)
                    if (t.trackId == playing.trackId) EqualizerBars(playing.isPlaying, Modifier.padding(10.dp), color = Color.White)
                }
            }
        }
    }
}
