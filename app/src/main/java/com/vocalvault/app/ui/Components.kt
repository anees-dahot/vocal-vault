package com.vocalvault.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.composed
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.SubcomposeAsyncImage
import com.vocalvault.app.data.Track
import com.vocalvault.app.ui.theme.Accent
import com.vocalvault.app.ui.theme.Bg
import com.vocalvault.app.ui.theme.OnAccent
import com.vocalvault.app.ui.theme.Surface1
import com.vocalvault.app.ui.theme.Surface2
import com.vocalvault.app.ui.theme.TextHi
import com.vocalvault.app.ui.theme.TextLow
import com.vocalvault.app.ui.theme.TextMid

// ---- App-wide song state + actions ------------------------------------------------------

data class SongMenuAction(val label: String, val icon: ImageVector? = null, val onClick: () -> Unit)

/** Everything a song row / song sheet needs. Provided once by AppRoot. */
@Immutable
class SongActions(
    val savedIds: Set<String>,
    val vocalTags: Set<String>,
    val downloaded: Set<String>,
    val downloading: Map<String, Float>,
    val toggleFavorite: (Track) -> Unit,
    val toggleVocal: (Track) -> Unit,
    val addToPlaylist: (Track) -> Unit,
    val playNext: (Track) -> Unit,
    val addToQueue: (Track) -> Unit,
    val download: (Track) -> Unit,
    val removeDownload: (Track) -> Unit,
    val openExternal: (Track) -> Unit,
)

val LocalSongActions = compositionLocalOf<SongActions?> { null }

@Immutable
data class PlayingInfo(val trackId: String = "", val isPlaying: Boolean = false)

val LocalPlayingInfo = compositionLocalOf { PlayingInfo() }

// ---- Formatting -------------------------------------------------------------------------

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val totalSeconds = durationMs / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

fun formatLongDuration(durationMs: Long): String {
    val minutes = durationMs / 60_000
    return if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
}

fun tracksLabel(count: Int): String = if (count == 1) "1 song" else "$count songs"

// ---- Artwork ----------------------------------------------------------------------------

@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(corner)).background(Surface2),
        contentAlignment = Alignment.Center,
    ) {
        val placeholder: @Composable () -> Unit = {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = TextLow, modifier = Modifier.size(22.dp))
        }
        if (url == null) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() } },
                error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() } },
            )
        }
    }
}

/** 2x2 cover built from the first four tracks, like a real playlist tile. */
@Composable
fun Mosaic(tracks: List<Track>, modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    val arts = tracks.mapNotNull { it.artworkUrl }.distinct()
    if (arts.size < 4) {
        Artwork(arts.firstOrNull(), modifier, corner)
        return
    }
    Column(modifier.clip(RoundedCornerShape(corner))) {
        Row(Modifier.weight(1f)) {
            Artwork(arts[0], Modifier.weight(1f).fillMaxSize(), 0.dp)
            Artwork(arts[1], Modifier.weight(1f).fillMaxSize(), 0.dp)
        }
        Row(Modifier.weight(1f)) {
            Artwork(arts[2], Modifier.weight(1f).fillMaxSize(), 0.dp)
            Artwork(arts[3], Modifier.weight(1f).fillMaxSize(), 0.dp)
        }
    }
}

/** Gradient tile with an icon, for "Saved songs" / "Downloads" (Spotify's Liked Songs tile). */
@Composable
fun GradientTile(icon: ImageVector, colors: List<Color>, modifier: Modifier = Modifier, corner: Dp = 6.dp, iconSize: Dp = 24.dp) {
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

val SavedGradient = listOf(Color(0xFF4F2BFF), Color(0xFFB3A6FF))
val DownloadsGradient = listOf(Color(0xFF0E7A55), Color(0xFF5BE3A6))
val RecordingsGradient = listOf(Color(0xFFE0245E), Color(0xFFFF9A6B))

/** Three bouncing bars shown on the playing song (Apple Music / Spotify). */
@Composable
fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = Accent) {
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = listOf(420, 300, 520).map { ms ->
        transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(ms), RepeatMode.Reverse), label = "bar$ms")
    }
    Row(modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEach { h ->
            Box(
                Modifier.width(3.dp).fillMaxHeight(if (playing) h.value else 0.3f)
                    .clip(RoundedCornerShape(1.dp)).background(color)
            )
        }
    }
}

/** Small "VOCAL" badge, like Apple Music's explicit badge. */
@Composable
fun VocalBadge() {
    Box(
        Modifier.clip(RoundedCornerShape(3.dp)).background(TextMid.copy(alpha = 0.22f)).padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text("VOCAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMid, letterSpacing = 0.4.sp)
    }
}

@Composable
fun DownloadIndicator(trackId: String) {
    val actions = LocalSongActions.current ?: return
    val progress = actions.downloading[trackId]
    when {
        progress != null -> CircularProgressIndicator(
            progress = { progress.coerceIn(0.02f, 1f) },
            color = Accent, trackColor = TextLow.copy(alpha = 0.3f), strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp),
        )
        trackId in actions.downloaded -> Icon(Icons.Rounded.DownloadDone, contentDescription = "Downloaded", tint = Accent, modifier = Modifier.size(14.dp))
    }
}

// ---- Song row + action sheet ------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraActions: List<SongMenuAction> = emptyList(),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val playing = LocalPlayingInfo.current
    val actions = LocalSongActions.current
    val isCurrent = track.trackId == playing.trackId
    val isVocal = track.isLikelyVocal || (actions != null && track.trackId in actions.vocalTags)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { sheetOpen = true })
            .padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Box(contentAlignment = Alignment.Center) {
            Artwork(track.artworkUrl, Modifier.size(50.dp))
            if (isCurrent) {
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.45f)))
                EqualizerBars(playing.isPlaying, color = Color.White)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) Accent else TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (isVocal) VocalBadge()
                DownloadIndicator(track.trackId)
                Text(
                    text = buildString {
                        append(track.artist)
                        if (track.durationMs > 0) append(" · ").append(formatDuration(track.durationMs))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing() else {
            IconButton(onClick = { sheetOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = TextMid, modifier = Modifier.size(20.dp))
            }
        }
    }

    if (sheetOpen) SongSheet(track, extraActions, onDismiss = { sheetOpen = false })
}

/** Song action sheet with artwork header (Spotify / YT Music style). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongSheet(track: Track, extraActions: List<SongMenuAction> = emptyList(), onDismiss: () -> Unit) {
    val actions = LocalSongActions.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artworkUrl, Modifier.size(56.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = TextMid, maxLines = 1)
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(Surface2))
            fun act(block: () -> Unit) = { onDismiss(); block() }
            if (actions != null) {
                val saved = track.trackId in actions.savedIds
                val tagged = track.trackId in actions.vocalTags
                val downloaded = track.trackId in actions.downloaded || track.trackId in actions.downloading
                SheetItem(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next", act { actions.playNext(track) })
                SheetItem(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", act { actions.addToQueue(track) })
                SheetItem(if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (saved) "Remove from Saved" else "Save", act { actions.toggleFavorite(track) }, tint = if (saved) Accent else null)
                SheetItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", act { actions.addToPlaylist(track) })
                SheetItem(
                    if (downloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                    if (downloaded) "Remove download" else "Download",
                    act { if (downloaded) actions.removeDownload(track) else actions.download(track) },
                    tint = if (downloaded) Accent else null,
                )
                SheetItem(Icons.Rounded.RecordVoiceOver, if (tagged) "Unmark as vocal" else "Mark as vocal", act { actions.toggleVocal(track) })
            }
            extraActions.forEach { SheetItem(it.icon ?: Icons.Rounded.GraphicEq, it.label, act(it.onClick)) }
            if (actions != null) SheetItem(Icons.AutoMirrored.Rounded.OpenInNew, "Open in YouTube Music", act { actions.openExternal(track) })
        }
    }
}

@Composable
fun SheetItem(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: TextMid, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextHi)
    }
}

// ---- Cards, chips, headers --------------------------------------------------------------

/** Square card with two lines below (Apple Music shelf item). */
@Composable
fun MediaCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
    art: @Composable (Modifier) -> Unit,
) {
    Column((if (width == Dp.Unspecified) modifier else modifier.width(width)).bouncyClick(onClick = onClick)) {
        art(Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Compact tile for the 2-column quick-pick grid (Spotify home). */
@Composable
fun QuickTile(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, playing: Boolean = false, art: @Composable (Modifier) -> Unit) {
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(6.dp)).background(Surface1).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art(Modifier.size(56.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextHi, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (playing) EqualizerBars(true, Modifier.padding(end = 10.dp))
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Accent else Surface1)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) OnAccent else TextHi)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 26.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextHi, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = TextMid, style = MaterialTheme.typography.labelLarge) }
    }
}

/** Big round play button (Spotify) */
@Composable
fun BigPlayButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp, icon: ImageVector = Icons.Rounded.PlayArrow) {
    Box(
        modifier.size(size).bouncyClick(scaleTo = 0.9f, onClick = onClick).clip(CircleShape).background(Accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = "Play", tint = OnAccent, modifier = Modifier.size(size * 0.55f))
    }
}


/** Empty / status screen: soft icon disc, title, one line, optional action. */
@Composable
fun EmptyMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(Accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(18.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextHi, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextMid, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.bouncyClick(onClick = onAction).clip(RoundedCornerShape(50)).background(Accent).padding(horizontal = 22.dp, vertical = 11.dp)
            ) { Text(action, style = MaterialTheme.typography.labelLarge, color = OnAccent) }
        }
    }
}

// ---- Motion + haptics -------------------------------------------------------------------

/** Click that springs down to 96% while pressed (Apple-style tap feedback). */
fun Modifier.bouncyClick(enabled: Boolean = true, scaleTo: Float = 0.96f, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) scaleTo else 1f,
        spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/** Small set of named haptics so screens stay consistent. */
class Haptics(private val h: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    fun tick() = h.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
    fun confirm() = h.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
    fun toggle(on: Boolean) = h.performHapticFeedback(
        if (on) androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn else androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOff
    )
    fun heavy() = h.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
}

@Composable
fun rememberHaptics(): Haptics {
    val h = androidx.compose.ui.platform.LocalHapticFeedback.current
    return remember(h) { Haptics(h) }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Songs, artists, acapellas",
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = TextMid, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = TextLow)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextHi),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = TextMid, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initial: String = "",
    hint: String = "Playlist name",
) {
    var text by remember(initial) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextHi),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    .clip(RoundedCornerShape(10.dp)).background(Surface2).padding(12.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = TextLow)
                        inner()
                    }
                },
            )
            androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(confirmLabel, color = Accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMid) } },
    )
}

/** Back-compat name used by playlist dialogs. */
@Composable
fun PlaylistNameDialog(title: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, initialName: String = "") =
    TextInputDialog(title, confirmLabel, onDismiss, onConfirm, initialName)

// ---- Drag to reorder --------------------------------------------------------------------

/**
 * Minimal drag-to-reorder for a LazyColumn. [firstIndex] is the lazy index of the first
 * reorderable item (to skip headers). Drag via [Modifier.dragHandle].
 */
class ReorderState(
    private val listState: LazyListState,
    private val firstIndex: Int,
    private val count: () -> Int,
    private val onMove: (Int, Int) -> Unit,
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    fun start(index: Int) { draggingIndex = index; offset = 0f }

    fun drag(dy: Float) {
        val from = draggingIndex ?: return
        offset += dy
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.index == from + firstIndex } ?: return
        val center = current.offset + current.size / 2f + offset
        val target = items.firstOrNull {
            it.index != current.index && (it.index - firstIndex) in 0 until count() &&
                center >= it.offset && center <= it.offset + it.size
        } ?: return
        val to = target.index - firstIndex
        onMove(from, to)
        offset += current.offset - target.offset
        draggingIndex = to
    }

    fun end() { draggingIndex = null; offset = 0f }
}

@Composable
fun rememberReorderState(listState: LazyListState, firstIndex: Int, count: () -> Int, onMove: (Int, Int) -> Unit): ReorderState {
    val move by rememberUpdatedState(onMove)
    val c by rememberUpdatedState(count)
    return remember(listState, firstIndex) { ReorderState(listState, firstIndex, { c() }, { a, b -> move(a, b) }) }
}

fun Modifier.reorderItem(state: ReorderState, index: Int): Modifier =
    if (state.draggingIndex == index) this.zIndex(1f).graphicsLayer { translationY = state.offset; shadowElevation = 16f }
    else this

@Composable
fun DragHandle(state: ReorderState, index: Int) {
    val currentIndex by rememberUpdatedState(index)
    Icon(
        Icons.Rounded.DragHandle,
        contentDescription = "Drag to reorder",
        tint = TextMid,
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .size(24.dp)
            .pointerInput(state) {
                detectDragGestures(
                    onDragStart = { state.start(currentIndex) },
                    onDragEnd = { state.end() },
                    onDragCancel = { state.end() },
                ) { change, amount ->
                    change.consume()
                    state.drag(amount.y)
                }
            },
    )
}
