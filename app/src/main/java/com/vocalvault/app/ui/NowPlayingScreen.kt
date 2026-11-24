package com.vocalvault.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.vocalvault.app.data.AudioQuality
import com.vocalvault.app.data.Track
import com.vocalvault.app.playback.LyricsState
import com.vocalvault.app.playback.PlayerUiState
import com.vocalvault.app.playback.PlayerViewModel
import com.vocalvault.app.playback.VersionsState
import com.vocalvault.app.ui.theme.Accent
import com.vocalvault.app.ui.theme.Bg
import com.vocalvault.app.ui.theme.ForceDark
import com.vocalvault.app.ui.theme.Surface1
import com.vocalvault.app.ui.theme.Surface2
import com.vocalvault.app.ui.theme.TextHi
import com.vocalvault.app.ui.theme.TextLow
import com.vocalvault.app.ui.theme.TextMid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private val speedPresets = listOf(0.5f, 0.75f, 0.85f, 1f, 1.15f, 1.25f, 1.5f)

private fun speedLabel(speed: Float): String =
    if (abs(speed - speed.roundToInt()) < 0.001f) "%.1f×".format(speed) else "%.2f×".format(speed).replace("0×", "×")

/** Total semitones (fractional) from a pitch factor. */
private fun factorToSemis(f: Float): Float = (12 * ln(f.toDouble()) / ln(2.0)).toFloat()
private fun semisToFactor(semis: Float): Float = 2.0.pow(semis / 12.0).toFloat()

private enum class Panel(val label: String, val icon: ImageVector) {
    Queue("Up next", Icons.AutoMirrored.Rounded.QueueMusic),
    Lyrics("Lyrics", Icons.Rounded.Lyrics),
    Versions("Versions", Icons.Rounded.SwapHoriz),
}

/** Dominant colour of the artwork, darkened so white text always reads. */
@Composable
fun rememberArtworkTint(url: String?): Color {
    val context = LocalContext.current
    val fallback = Surface1
    val raw by produceState(fallback, url, fallback) {
        if (url == null) {
            value = fallback
            return@produceState
        }
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(96).build()
        val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return@produceState
        val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
        val swatch = palette.vibrantSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch ?: palette.darkMutedSwatch
        value = swatch?.let { lerp(Color(it.rgb), Color.Black, 0.4f) } ?: fallback
    }
    val animated by animateColorAsState(raw, tween(700), label = "tint")
    return animated
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    track: Track?,
    state: PlayerUiState,
    isFavorite: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    sleepEndsAt: Long,
    loop: PlayerViewModel.LoopRange?,
    lyrics: LyricsState,
    versions: VersionsState,
    queue: List<Track>,
    recording: Boolean,
    audioQuality: AudioQuality,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onStartSleep: (Int) -> Unit,
    onSleepEndOfTrack: () -> Unit,
    onCancelSleep: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleDownload: () -> Unit,
    onCycleLoop: () -> Unit,
    onClearLoop: () -> Unit,
    onToggleRecord: () -> Unit,
    onLoadVersions: () -> Unit,
    onSwapVersion: (Track) -> Unit,
    onSkipTo: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit,
    onClearUpcoming: () -> Unit,
    onSetAudioQuality: (AudioQuality) -> Unit,
    artModifier: Modifier = Modifier,
    showGestureTip: Boolean = false,
    onGestureTipSeen: () -> Unit = {},
) = ForceDark {
    var sheet by remember { mutableStateOf<String?>(null) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    val haptics = rememberHaptics()
    var dragFraction by remember(state.mediaId) { mutableStateOf<Float?>(null) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepEndsAt) {
        while (sleepEndsAt > 0L) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val sleepAtEndOfTrack = sleepEndsAt < 0L
    val sleepMinutesLeft = if (sleepEndsAt > 0L) {
        (((sleepEndsAt - nowMs).coerceAtLeast(0L) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    } else 0

    val tint = rememberArtworkTint(track?.artworkUrl)
    val duration = state.durationMs
    val fraction = dragFraction ?: if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownPosition = dragFraction?.let { (duration * it).toLong() } ?: state.positionMs
    val semis = factorToSemis(state.pitch)

    LaunchedEffect(panel, track?.trackId) {
        if (panel == Panel.Versions && (versions !is VersionsState.Loaded || versions.forTrackId != track?.trackId) && versions !is VersionsState.Loading) onLoadVersions()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg).clickable(enabled = false) {}) {
        // Apple Music-style living background: blurred artwork under the artwork colour.
        // blur() is a no-op before Android 12, where a sharp copy would look broken.
        if (track?.artworkUrl != null && android.os.Build.VERSION.SDK_INT >= 31) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(90.dp).graphicsLayer { alpha = 0.55f },
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint.copy(alpha = 0.75f), 0.55f to tint.copy(alpha = 0.55f), 1f to Bg)))

        val artSize = minOf(maxWidth - 48.dp, maxHeight * 0.44f)

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Grabber (Apple Music): tap or swipe the artwork down to close.
            Box(
                Modifier.fillMaxWidth().height(28.dp).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(TextHi.copy(alpha = 0.45f))) }

            if (recording) {
                Row(
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE5243B)).clickable(onClick = onToggleRecord)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.width(8.dp))
                    Text("Recording · tap to stop", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }

            AnimatedContent(
                targetState = panel,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "panel",
            ) { current ->
                if (current == null) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.weight(1f))
                        Box(contentAlignment = Alignment.BottomCenter) {
                            SwipeArtwork(track, state.isPlaying, artSize, onNext, onPrevious, onClose, onTogglePlayPause, artModifier)
                            if (showGestureTip) GestureTip(onGestureTipSeen)
                        }
                        Spacer(Modifier.weight(1f))
                        TitleRow(state, track, isFavorite, onToggleFavorite, onMore = { sheet = "more" }, big = true)
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(track?.artworkUrl, Modifier.size(56.dp).clickable { panel = null }, corner = 8.dp)
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.weight(1f)) { TitleRow(state, track, isFavorite, onToggleFavorite, onMore = { sheet = "more" }, big = false) }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (current) {
                                Panel.Lyrics -> LyricsPanel(lyrics, state.positionMs) { ms -> if (duration > 0) onSeek(ms.toFloat() / duration) }
                                Panel.Queue -> QueuePanel(queue, state.queueIndex, state.shuffle, state.repeatMode, onToggleShuffle, onCycleRepeat, onSkipTo, onRemoveFromQueue, onMoveInQueue, onClearUpcoming)
                                Panel.Versions -> VersionsPanel(versions, onLoadVersions) { onSwapVersion(it); panel = null }
                            }
                        }
                    }
                }
            }

            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFF8A80), maxLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }

            Spacer(Modifier.height(14.dp))
            SeekBar(
                fraction = fraction,
                loopA = loop?.a?.takeIf { duration > 0 }?.let { it.toFloat() / duration },
                loopB = loop?.b?.takeIf { duration > 0 }?.let { it.toFloat() / duration },
                onDrag = { if (dragFraction == null && it != null) haptics.tick(); dragFraction = it },
                onSeek = { haptics.tick(); onSeek(it); dragFraction = null },
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(shownPosition), style = MaterialTheme.typography.labelSmall, color = TextMid)
                Spacer(Modifier.weight(1f))
                Text("-" + formatDuration((duration - shownPosition).coerceAtLeast(0)), style = MaterialTheme.typography.labelSmall, color = TextMid)
            }

            // Transport: three big glyphs, nothing else (Apple Music).
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { haptics.tick(); onPrevious() }, modifier = Modifier.size(72.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = TextHi, modifier = Modifier.size(48.dp))
                }
                val pressScale by animateFloatAsState(if (state.isPlaying) 1f else 0.92f, spring(Spring.DampingRatioMediumBouncy), label = "play")
                Box(
                    Modifier.size(84.dp).scale(pressScale).bouncyClick(scaleTo = 0.86f) { haptics.tick(); onTogglePlayPause() }.clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(color = TextHi, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = TextHi,
                            modifier = Modifier.size(64.dp),
                        )
                    }
                }
                IconButton(onClick = { haptics.tick(); onNext() }, enabled = state.hasNext, modifier = Modifier.size(72.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = if (state.hasNext) TextHi else TextLow, modifier = Modifier.size(48.dp))
                }
            }

            // Lyrics · Practice · Queue
            val practiceActive = abs(state.speed - 1f) > 0.001f || abs(semis) > 0.04f || loop != null || sleepMinutesLeft > 0 || sleepAtEndOfTrack
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PanelIcon(Icons.Rounded.Lyrics, "Lyrics", panel == Panel.Lyrics) { panel = if (panel == Panel.Lyrics) null else Panel.Lyrics }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (practiceActive) Accent else TextHi.copy(alpha = 0.16f))
                        .clickable { haptics.tick(); sheet = "practice" }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (practiceActive) practiceSummary(state.speed, semis, loop, recording) else "Practice",
                        style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1,
                    )
                }
                PanelIcon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", panel == Panel.Queue) { panel = if (panel == Panel.Queue) null else Panel.Queue }
            }
        }
    }

    when (sheet) {
        "more" -> track?.let {
            SongSheet(
                it,
                extraActions = listOf(
                    SongMenuAction("Audio quality: ${audioQuality.label}", Icons.Rounded.HighQuality) { sheet = "quality" },
                    SongMenuAction("Other versions of this song", Icons.Rounded.SwapHoriz) { panel = Panel.Versions },
                ),
                onDismiss = { if (sheet == "more") sheet = null },
            )
        }
        "practice" -> PracticeSheet(
            speed = state.speed,
            semis = semis,
            loop = loop,
            sleepLabel = when { sleepAtEndOfTrack -> "End of song"; sleepMinutesLeft > 0 -> "$sleepMinutesLeft min"; else -> "Off" },
            recording = recording,
            canRecord = track != null,
            onSpeed = { sheet = "speed" },
            onKey = { sheet = "key" },
            onLoop = { haptics.confirm(); onCycleLoop() },
            onClearLoop = onClearLoop,
            onTimer = { sheet = "sleep" },
            onRecord = { haptics.heavy(); sheet = null; onToggleRecord() },
            onVersions = { sheet = null; panel = Panel.Versions },
            onDismiss = { sheet = null },
        )
        "speed" -> SpeedSheet(state.speed, onSetSpeed) { sheet = null }
        "key" -> KeySheet(semis, { onSetPitch(semisToFactor(it)) }) { sheet = null }
        "quality" -> OptionSheet(
            title = "Audio quality",
            subtitle = "Applies from the next song. Downloads are always best quality.",
            options = AudioQuality.entries.map { "${it.label} — ${it.description}" },
            selected = AudioQuality.entries.indexOf(audioQuality),
            onPick = { onSetAudioQuality(AudioQuality.entries[it]); sheet = null },
            onDismiss = { sheet = null },
        )
        "sleep" -> {
            val choices = listOf(0, -1, 5, 15, 30, 45, 60, 90)
            OptionSheet(
                title = "Sleep timer",
                subtitle = when {
                    sleepAtEndOfTrack -> "Stops when this song ends."
                    sleepMinutesLeft > 0 -> "Stops in about $sleepMinutesLeft min."
                    else -> "Pause playback after a while."
                },
                options = choices.map { when (it) { 0 -> "Off"; -1 -> "End of this song"; else -> "$it minutes" } },
                selected = when { sleepAtEndOfTrack -> 1; sleepMinutesLeft == 0 -> 0; else -> -1 },
                onPick = {
                    when (choices[it]) {
                        0 -> onCancelSleep()
                        -1 -> onSleepEndOfTrack()
                        else -> onStartSleep(choices[it])
                    }
                    sheet = null
                },
                onDismiss = { sheet = null },
            )
        }
    }
}

private fun keyLabel(semis: Float): String {
    if (abs(semis) < 0.04f) return "Key"
    val st = semis.roundToInt()
    val cents = ((semis - st) * 100).roundToInt()
    val base = if (st > 0) "+$st" else "$st"
    return if (cents == 0) base else "$base ${if (cents > 0) "+" else ""}${cents}¢"
}

@Composable
private fun SwipeArtwork(
    track: Track?,
    playing: Boolean,
    size: androidx.compose.ui.unit.Dp,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    sharedModifier: Modifier = Modifier,
) {
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    // Apple Music: artwork shrinks when paused.
    val scale by animateFloatAsState(if (playing) 1f else 0.84f, spring(dampingRatio = 0.62f, stiffness = 260f), label = "art")
    Artwork(
        track?.artworkUrl,
        sharedModifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = dragX * 0.5f
                translationY = dragY.coerceAtLeast(0f) * 0.5f
                rotationZ = dragX / 60f
            }
            .shadow(if (playing) 30.dp else 12.dp, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        val threshold = 80.dp.toPx()
                        when {
                            dragY > threshold && abs(dragY) > abs(dragX) -> onClose()
                            dragX < -threshold -> { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onNext() }
                            dragX > threshold -> { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPrevious() }
                        }
                        dragX = 0f; dragY = 0f
                    },
                    onDragCancel = { dragX = 0f; dragY = 0f },
                ) { change, amount ->
                    change.consume()
                    dragX += amount.x
                    dragY += amount.y
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onToggle() }) },
        corner = 12.dp,
    )
}

@Composable
private fun TitleRow(
    state: PlayerUiState,
    track: Track?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit,
    big: Boolean,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                state.title.ifBlank { "Nothing playing" },
                style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                state.artist.ifBlank { "Search for a song to start" },
                style = if (big) MaterialTheme.typography.bodyLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp)) else MaterialTheme.typography.bodyMedium,
                color = TextMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val haptics = rememberHaptics()
        CircleButton(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (isFavorite) "Remove from Saved" else "Save", enabled = track != null, tint = if (isFavorite) Accent else TextHi) { haptics.toggle(!isFavorite); onToggleFavorite() }
        Spacer(Modifier.width(10.dp))
        CircleButton(Icons.Rounded.MoreHoriz, "More", enabled = track != null, onClick = onMore)
    }
}

@Composable
private fun CircleButton(icon: ImageVector, description: String, enabled: Boolean, tint: Color = TextHi, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(TextHi.copy(alpha = 0.14f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun PanelIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (active) TextHi.copy(alpha = 0.9f) else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = if (active) Bg else TextMid, modifier = Modifier.size(24.dp)) }
}

private fun practiceSummary(speed: Float, semis: Float, loop: PlayerViewModel.LoopRange?, recording: Boolean): String = buildList {
    if (abs(speed - 1f) > 0.001f) add(speedLabel(speed))
    if (abs(semis) > 0.04f) add(keyLabel(semis))
    if (loop != null) add(if (loop.b == null) "A set" else "Loop")
    if (isEmpty()) add("Timer on")
}.joinToString(" · ")

/** Practice tools as big tiles (Speed, Key, Loop, Timer) plus one Record button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PracticeSheet(
    speed: Float,
    semis: Float,
    loop: PlayerViewModel.LoopRange?,
    sleepLabel: String,
    recording: Boolean,
    canRecord: Boolean,
    onSpeed: () -> Unit,
    onKey: () -> Unit,
    onLoop: () -> Unit,
    onClearLoop: () -> Unit,
    onTimer: () -> Unit,
    onRecord: () -> Unit,
    onVersions: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text("Practice", style = MaterialTheme.typography.titleLarge, color = TextHi)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PracticeTile("Speed", speedLabel(speed), Icons.Rounded.Speed, abs(speed - 1f) > 0.001f, Modifier.weight(1f), onSpeed)
                PracticeTile("Key", if (abs(semis) < 0.04f) "Original" else keyLabel(semis), Icons.Rounded.GraphicEq, abs(semis) > 0.04f, Modifier.weight(1f), onKey)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PracticeTile(
                    "Loop",
                    when { loop == null -> "Tap to set A"; loop.b == null -> "${formatDuration(loop.a)} · tap for B"; else -> "${formatDuration(loop.a)} – ${formatDuration(loop.b)}" },
                    Icons.Rounded.AllInclusive, loop != null, Modifier.weight(1f), onLoop,
                    trailing = if (loop != null) ({ Icon(Icons.Rounded.Close, contentDescription = "Clear loop", tint = TextHi, modifier = Modifier.size(18.dp).clickable(onClick = onClearLoop)) }) else null,
                )
                PracticeTile("Timer", sleepLabel, Icons.Rounded.Bedtime, sleepLabel != "Off", Modifier.weight(1f), onTimer)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp))
                    .background(if (recording) Color(0xFFE5243B) else Accent).clickable(enabled = canRecord, onClick = onRecord),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(if (recording) "Stop recording" else "Record a take", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            Text(
                "Headphones give the cleanest take. Takes are saved in the Takes tab.",
                style = MaterialTheme.typography.bodyMedium, color = TextMid, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            TextButton(onClick = onVersions, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Vocal sounds off? Try another version", color = Accent)
            }
        }
    }
}

@Composable
private fun PracticeTile(
    label: String,
    value: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.height(96.dp).clip(RoundedCornerShape(16.dp))
            .background(if (active) Accent.copy(alpha = 0.22f) else Surface2)
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (active) Accent else TextMid, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = TextMid, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color = Accent,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val color = when {
            !enabled -> TextLow
            active -> activeColor
            else -> TextMid
        }
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** Thin seek bar that grows while dragging. Shows the A-B loop region. */
@Composable
private fun SeekBar(fraction: Float, loopA: Float?, loopB: Float?, onDrag: (Float?) -> Unit, onSeek: (Float) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var last by remember { mutableFloatStateOf(0f) }
    val accent = Accent
    val thick by animateFloatAsState(if (dragging) 8f else 4f, label = "thick")
    Box(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(Unit) { detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        last = (it.x / size.width).coerceIn(0f, 1f)
                        onDrag(last)
                    },
                    onDragEnd = { dragging = false; onSeek(last) },
                    onDragCancel = { dragging = false; onDrag(null) },
                ) { change, _ ->
                    last = (change.position.x / size.width).coerceIn(0f, 1f)
                    onDrag(last)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val trackH = thick.dp.toPx() / 2f * 1f
            val y = size.height / 2 - trackH / 2
            val r = CornerRadius(trackH / 2)
            drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(0f, y), Size(size.width, trackH), r)
            if (loopA != null) {
                val end = loopB ?: loopA
                drawRect(accent.copy(alpha = 0.45f), Offset(size.width * loopA, y - 3f), Size((size.width * (end - loopA)).coerceAtLeast(3f), trackH + 6f))
                drawRect(accent, Offset(size.width * loopA - 1.5f, 0f), Size(3f, size.height))
                if (loopB != null) drawRect(accent, Offset(size.width * loopB - 1.5f, 0f), Size(3f, size.height))
            }
            drawRoundRect(Color.White, Offset(0f, y), Size(size.width * fraction, trackH), r)
            if (dragging) drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(size.width * fraction, size.height / 2))
        }
    }
}

// ---- Panels -----------------------------------------------------------------------------

@Composable
private fun LyricsPanel(state: LyricsState, positionMs: Long, onSeekMs: (Long) -> Unit) {
    when (state) {
        LyricsState.Idle, LyricsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TextHi, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
        LyricsState.None -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyMessage("No lyrics yet", "Couldn't find lyrics for this upload. Try another version from Practice.", icon = Icons.Rounded.Lyrics)
        }
        is LyricsState.Loaded -> {
            val lines = state.lyrics.synced
            if (lines.isEmpty()) {
                Text(
                    state.lyrics.plain.orEmpty(),
                    style = MaterialTheme.typography.titleLarge.copy(lineHeight = MaterialTheme.typography.titleLarge.lineHeight * 1.2f),
                    color = TextHi,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                )
                return
            }
            // Sung vocals often start a beat late; lean slightly ahead.
            val current = lines.indexOfLast { it.timeMs <= positionMs + 250 }.coerceAtLeast(0)
            val listState = rememberLazyListState()
            LaunchedEffect(current) { listState.animateScrollToItem((current - 2).coerceAtLeast(0)) }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 24.dp)) {
                itemsIndexed(lines) { i, line ->
                    val active = i == current
                    val alpha by animateFloatAsState(if (active) 1f else if (i < current) 0.35f else 0.5f, label = "lyric")
                    val lineScale by animateFloatAsState(if (active) 1.04f else 1f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "lyricScale")
                    Text(
                        line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                        color = TextHi.copy(alpha = alpha),
                        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = lineScale; scaleY = lineScale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                            .clip(RoundedCornerShape(8.dp)).clickable { onSeekMs(line.timeMs) }.padding(vertical = 8.dp),
                    )
                }
                item {
                    Text("Lyrics from LRCLIB", style = MaterialTheme.typography.labelSmall, color = TextLow, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun QueuePanel(
    queue: List<Track>,
    currentIndex: Int,
    shuffle: Boolean,
    repeatMode: Int,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSkipTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClearUpcoming: () -> Unit,
) {
    // Local copy so drags feel instant; the player's queue catches up a frame later.
    var local by remember(queue) { mutableStateOf(queue) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
    val reorder = rememberReorderState(listState, firstIndex = 1, count = { local.size }) { from, to ->
        local = local.toMutableList().apply { add(to, removeAt(from)) }
        onMove(from, to)
    }
    val keys = remember(local) {
        val seen = HashMap<String, Int>()
        local.map { t -> val n = seen.merge(t.trackId, 1, Int::plus)!!; "${t.trackId}#$n" }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Up next", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (currentIndex < local.lastIndex) TextButton(onClick = onClearUpcoming) { Text("Clear", color = TextMid) }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QueueToggle(Icons.Rounded.Shuffle, "Shuffle", shuffle, Modifier.weight(1f), onToggleShuffle)
                QueueToggle(
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    when (repeatMode) { Player.REPEAT_MODE_ONE -> "Repeat one"; Player.REPEAT_MODE_ALL -> "Repeat all"; else -> "Repeat" },
                    repeatMode != Player.REPEAT_MODE_OFF, Modifier.weight(1f), onCycleRepeat,
                )
            }
        }
        itemsIndexed(local, key = { i, _ -> keys[i] }) { index, item ->
            val isCurrent = index == currentIndex
            Row(
                Modifier.fillMaxWidth().reorderItem(reorder, index)
                    .background(if (reorder.draggingIndex == index) Surface2 else Color.Transparent)
                    .clickable { onSkipTo(index) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Artwork(item.artworkUrl, Modifier.size(46.dp))
                    if (isCurrent) EqualizerBars(true, color = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge, color = if (isCurrent) Accent else if (index < currentIndex) TextLow else TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.artist, style = MaterialTheme.typography.bodyMedium, color = TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!isCurrent) {
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove from queue", tint = TextMid, modifier = Modifier.size(18.dp))
                    }
                    DragHandle(reorder, index)
                }
            }
        }
    }
}

@Composable
private fun QueueToggle(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (active) TextHi.copy(alpha = 0.9f) else TextHi.copy(alpha = 0.12f)).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Bg else TextHi, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) Bg else TextHi)
    }
}

@Composable
private fun VersionsPanel(state: VersionsState, onRetry: () -> Unit, onPick: (Track) -> Unit) {
    when (state) {
        VersionsState.Idle, VersionsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = TextHi, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(10.dp))
                Text("Finding other vocal uploads…", style = MaterialTheme.typography.bodyMedium, color = TextMid)
            }
        }
        is VersionsState.Failed -> EmptyMessage("Couldn't load versions", state.message, icon = Icons.Rounded.CloudOff, action = "Retry", onAction = onRetry)
        is VersionsState.Loaded -> {
            if (state.tracks.isEmpty()) {
                EmptyMessage("No other versions", "This looks like the only vocal upload of this song.", icon = Icons.Rounded.SwapHoriz)
                return
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text("Sounds off? Swap to another upload. Position is kept.", style = MaterialTheme.typography.bodyMedium, color = TextMid, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(state.tracks, key = { it.trackId }) { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(t) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(t.artworkUrl, Modifier.size(46.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${t.artist} · ${formatDuration(t.durationMs)}", style = MaterialTheme.typography.bodyMedium, color = TextMid, maxLines = 1)
                        }
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = "Use this version", tint = TextMid)
                    }
                }
            }
        }
    }
}

// ---- Sheets -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(speed: Float, onSet: (Float) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(speed) }
    val haptics = rememberHaptics()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text("Speed", style = MaterialTheme.typography.titleLarge)
            Text("Slow down to learn a run. The key stays the same.", style = MaterialTheme.typography.bodyMedium, color = TextMid)
            Text(speedLabel(value), style = MaterialTheme.typography.displaySmall, color = Accent, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center)
            Slider(
                value = value,
                onValueChange = { val v = (it * 20).roundToInt() / 20f; if (v != value) haptics.tick(); value = v },
                onValueChangeFinished = { onSet(value) },
                valueRange = 0.5f..1.5f,
                steps = 19,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(speedPresets) { p -> Chip(speedLabel(p), abs(p - value) < 0.001f, { value = p; onSet(p) }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeySheet(semis: Float, onSet: (Float) -> Unit, onDismiss: () -> Unit) {
    var st by remember { mutableIntStateOf(semis.roundToInt().coerceIn(-6, 6)) }
    var cents by remember { mutableIntStateOf(((semis - semis.roundToInt()) * 100).roundToInt().coerceIn(-50, 50)) }
    fun apply() = onSet(st + cents / 100f)
    val haptics = rememberHaptics()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Key", style = MaterialTheme.typography.titleLarge)
                    Text("Shift to fit your range. Fine-tune if the vocal is slightly off.", style = MaterialTheme.typography.bodyMedium, color = TextMid)
                }
                TextButton(onClick = { st = 0; cents = 0; apply() }) { Text("Reset", color = Accent) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Rounded.Remove, "Down a semitone", enabled = st > -6) { haptics.tick(); st--; apply() }
                Column(Modifier.width(140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (st > 0) "+$st" else "$st", style = MaterialTheme.typography.displaySmall, color = if (st == 0) TextHi else Accent)
                    Text("semitones", style = MaterialTheme.typography.labelMedium, color = TextMid)
                }
                RoundIcon(Icons.Rounded.Add, "Up a semitone", enabled = st < 6) { haptics.tick(); st++; apply() }
            }
            Text("Fine tune: ${if (cents > 0) "+" else ""}$cents cents", style = MaterialTheme.typography.labelLarge, color = TextMid)
            Slider(
                value = cents.toFloat(),
                onValueChange = { val c = (it / 5).roundToInt() * 5; if (c != cents) haptics.tick(); cents = c },
                onValueChangeFinished = { apply() },
                valueRange = -50f..50f,
                steps = 19,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
            )
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(Surface2).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = if (enabled) TextHi else TextLow) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSheet(
    title: String,
    subtitle: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMid, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            Spacer(Modifier.height(8.dp))
            options.forEachIndexed { index, label ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(index) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = if (index == selected) Accent else TextHi, modifier = Modifier.weight(1f))
                    if (index == selected) Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
                }
            }
        }
    }
}

/** One-time hint over the artwork; fades after a few seconds or on tap. */
@Composable
private fun GestureTip(onSeen: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(5_000); visible = false; onSeen() }
    androidx.compose.animation.AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier.padding(bottom = 14.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f))
                .clickable { visible = false; onSeen() }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Swipe to skip · double-tap to pause", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}
