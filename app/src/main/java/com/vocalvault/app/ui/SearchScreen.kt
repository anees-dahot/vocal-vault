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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vocalvault.app.data.Track
import com.vocalvault.app.ui.theme.Accent
import com.vocalvault.app.ui.theme.Surface1
import com.vocalvault.app.ui.theme.TextHi
import com.vocalvault.app.ui.theme.TextLow
import com.vocalvault.app.ui.theme.TextMid

/** Spotify-style browse tiles: tapping one runs the search. */
private data class BrowseTile(val label: String, val query: String, val color: Color)

private val browseTiles = listOf(
    BrowseTile("Pop", "pop hits", Color(0xFFE8115B)),
    BrowseTile("R&B", "rnb", Color(0xFF8C67AB)),
    BrowseTile("Hip-Hop", "hip hop", Color(0xFFBA5D07)),
    BrowseTile("Rock", "rock", Color(0xFFE91429)),
    BrowseTile("K-Pop", "kpop", Color(0xFF148A08)),
    BrowseTile("Bollywood", "bollywood", Color(0xFFDC148C)),
    BrowseTile("Ballads", "ballad", Color(0xFF477D95)),
    BrowseTile("Gospel", "gospel", Color(0xFF503750)),
    BrowseTile("Disney", "disney", Color(0xFF1E3264)),
    BrowseTile("80s", "80s", Color(0xFFAF2896)),
    BrowseTile("Soul", "soul", Color(0xFF0D73EC)),
    BrowseTile("Country", "country", Color(0xFFD84000)),
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    recents: List<String>,
    suggestions: List<String>,
    bottomPadding: Dp,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onLoadMore: () -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(state.query) }
    val focusManager = LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    fun submit(q: String) {
        if (q.isBlank()) return
        text = q
        keyboard?.hide()
        focusManager.clearFocus()
        onSearch(q)
    }

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.results.size) {
        if (nearEnd && state.canLoadMore && !state.loading && state.error == null && state.results.isNotEmpty()) onLoadMore()
    }

    val showingResults = state.searchedOnce || state.loading

    // "More from" artists found in the results (titles are "Artist - Song").
    val artists = remember(state.results) {
        val parsed = state.results.map { com.vocalvault.app.data.LyricsRepository.cleanTitle(it.title, it.artist) }
        // A name that is also a song title ("Someone Like You - Adele") is not an artist.
        val songTitles = parsed.map { it.second.lowercase() }.toSet()
        parsed.map { it.first }
            .filter { it.length in 2..30 && it.lowercase() !in songTitles }
            .groupBy { it.lowercase() }
            .filter { it.value.size >= 2 }
            .values.sortedByDescending { it.size }.take(5).map { it.first() }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        item {
            Text("Search", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp))
        }
        // Sticky search field, like every big music app.
        stickyHeader {
            Box(Modifier.fillMaxWidth().background(com.vocalvault.app.ui.theme.Bg).padding(horizontal = 16.dp, vertical = 6.dp)) {
                SearchField(
                    value = text,
                    onValueChange = {
                        text = it
                        onQueryChange(it)
                        if (it.isEmpty()) onClear()
                    },
                    onSubmit = { submit(text) },
                )
            }
        }

        // Live autocomplete while typing something new.
        if (text.isNotBlank() && text.trim() != state.query.trim()) {
            val list = (listOf(text.trim()) + suggestions).distinctBy { it.lowercase() }
            items(list, key = { "s-$it" }) { q ->
                Row(
                    Modifier.fillMaxWidth().clickable { submit(q) }.padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = TextLow, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(highlight(q, text), style = MaterialTheme.typography.bodyLarge, color = TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                    IconButton(onClick = { text = q; onQueryChange(q) }) {
                        Icon(Icons.Rounded.NorthWest, contentDescription = "Fill in", tint = TextLow, modifier = Modifier.size(18.dp))
                    }
                }
            }
            return@LazyColumn
        }

        if (!showingResults) {
            if (recents.isNotEmpty()) {
                item {
                    SectionHeader("Recent", action = "Clear", onAction = onClearRecents)
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recents, key = { "r-$it" }) { q ->
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(Surface1).clickable { submit(q) }
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.History, contentDescription = null, tint = TextLow, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(q, style = MaterialTheme.typography.labelLarge, color = TextHi, maxLines = 1)
                                Box(Modifier.size(28.dp).clip(CircleShape).clickable { onRemoveRecent(q) }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = TextLow, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
            item { SectionHeader("Browse vocals") }
            items(browseTiles.chunked(2)) { row ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tile ->
                        Box(
                            Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(8.dp)).background(tile.color)
                                .clickable { submit(tile.query) }.padding(12.dp),
                        ) {
                            Text(tile.label, style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Box(
                                Modifier.align(Alignment.BottomEnd).size(46.dp).rotate(22f).clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.18f))
                            )
                        }
                    }
                }
            }
            return@LazyColumn
        }

        // Quick-refine chips (YT Music style).
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("acapella", "vocals only", "live vocals", "harmonies")) { refine ->
                    Chip(refine, selected = state.query.endsWith(refine, ignoreCase = true), onClick = {
                        val base = state.query.replace(Regex("(?i)\\s*(acapella|vocals only|live vocals|harmonies)$"), "")
                        submit("$base $refine".trim())
                    })
                }
            }
        }

        if (artists.size > 1) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(artists) { a ->
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(Surface1).clickable { submit(a) }.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(Accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                Text(a.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = Accent)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("More from $a", style = MaterialTheme.typography.labelLarge, color = TextHi, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (state.error != null && state.results.isEmpty()) {
            item {
                EmptyMessage(title = "Couldn't search", message = state.error, icon = Icons.Rounded.WifiOff, action = "Try again", onAction = { onSearch(state.query) })
            }
        }

        if (state.searchedOnce && !state.loading && state.results.isEmpty() && state.error == null) {
            item {
                EmptyMessage(
                    title = "No vocals for \"${state.query}\"",
                    message = "Try just the song title, or the artist name.",
                    icon = Icons.Rounded.SearchOff,
                )
            }
        }

        // Spotify "Top result" card.
        state.results.firstOrNull()?.let { top ->
            item {
                Text("Top result", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 10.dp))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Surface1)
                        .clickable { onPlay(state.results, 0) }.padding(16.dp),
                ) {
                    Artwork(top.artworkUrl, Modifier.size(96.dp), corner = 8.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(top.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(top.artist, style = MaterialTheme.typography.bodyMedium, color = TextMid, modifier = Modifier.weight(1f), maxLines = 1)
                        BigPlayButton(onClick = { onPlay(state.results, 0) }, size = 48.dp)
                    }
                }
                Text("Songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp))
            }
        }

        itemsIndexed(state.results.drop(1), key = { _, t -> t.trackId }) { i, track ->
            TrackRow(track = track, onClick = { onPlay(state.results, i + 1) })
        }

        if (state.error != null && state.results.isNotEmpty() && !state.loading) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.error, style = MaterialTheme.typography.bodyMedium, color = TextMid, modifier = Modifier.weight(1f))
                    TextButton(onClick = onLoadMore) { Text("Retry", color = Accent, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}


/** Bold the part the user hasn't typed yet (Apple / Google style suggestions). */
private fun highlight(suggestion: String, typed: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        val t = typed.trim()
        if (t.isNotEmpty() && suggestion.startsWith(t, ignoreCase = true)) {
            append(suggestion.substring(0, t.length))
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold))
            append(suggestion.substring(t.length))
            pop()
        } else append(suggestion)
    }
