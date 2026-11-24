package com.vocalvault.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vocalvault.app.VocalVaultApp
import com.vocalvault.app.data.Playlist
import com.vocalvault.app.data.Track
import com.vocalvault.app.playback.PlayerUiState
import com.vocalvault.app.playback.PlayerViewModel
import com.vocalvault.app.ui.theme.Accent
import com.vocalvault.app.ui.theme.Bg
import com.vocalvault.app.ui.theme.Surface1
import com.vocalvault.app.ui.theme.Surface2
import com.vocalvault.app.ui.theme.TextHi
import com.vocalvault.app.ui.theme.TextLow
import com.vocalvault.app.ui.theme.TextMid

private enum class Tab(val label: String, val icon: ImageVector, val activeIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Library("Library", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    Takes("Takes", Icons.Outlined.Mic, Icons.Rounded.Mic),
    /** Not in the pill: opened by the round search button. */
    Search("Search", Icons.Rounded.Search, Icons.Rounded.Search),
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as VocalVaultApp
    val libraryVm: LibraryViewModel = viewModel()
    val playerVm: PlayerViewModel = viewModel()
    val searchVm: SearchViewModel = viewModel()

    val searchState by searchVm.state.collectAsStateWithLifecycle()
    val recents by searchVm.recentSearches.collectAsStateWithLifecycle()
    val suggestions by searchVm.suggestions.collectAsStateWithLifecycle()
    val saved by libraryVm.favorites.collectAsStateWithLifecycle()
    val savedIds by libraryVm.savedIds.collectAsStateWithLifecycle()
    val vocalTags by libraryVm.vocalTags.collectAsStateWithLifecycle()
    val playlists by libraryVm.playlists.collectAsStateWithLifecycle()
    val history by libraryVm.history.collectAsStateWithLifecycle()
    val mostPlayed by libraryVm.mostPlayed.collectAsStateWithLifecycle()
    val offline by libraryVm.offline.collectAsStateWithLifecycle()
    val downloaded by libraryVm.downloaded.collectAsStateWithLifecycle()
    val downloading by libraryVm.downloadProgress.collectAsStateWithLifecycle()
    val recordings by libraryVm.recordings.collectAsStateWithLifecycle()
    val themeMode by libraryVm.themeMode.collectAsStateWithLifecycle()
    val accent by libraryVm.accent.collectAsStateWithLifecycle()
    val audioQuality by libraryVm.audioQuality.collectAsStateWithLifecycle()
    val onboarded by libraryVm.onboarded.collectAsStateWithLifecycle()
    var gestureTip by remember { mutableStateOf(!libraryVm.tipSeen("np_gestures")) }
    val haptics = rememberHaptics()

    val player by playerVm.state.collectAsStateWithLifecycle()
    val sleepEndsAt by playerVm.sleepEndsAt.collectAsStateWithLifecycle()
    val queue by playerVm.queue.collectAsStateWithLifecycle()
    val loop by playerVm.loop.collectAsStateWithLifecycle()
    val lyrics by playerVm.lyrics.collectAsStateWithLifecycle()
    val versions by playerVm.versions.collectAsStateWithLifecycle()
    val recording by playerVm.recording.collectAsStateWithLifecycle()
    val playingTakeId by playerVm.playingTakeId.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var libraryRoute by rememberSaveable { mutableStateOf(LibraryDestination.Root.route) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var addSheetTrack by remember { mutableStateOf<Track?>(null) }
    var createOpen by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val updateState by app.updater.state.collectAsStateWithLifecycle()
    val autoUpdate by app.updater.autoCheck.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { app.updater.checkOnLaunch() }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun startRecording() {
        if (playerVm.startRecording()) toast("Recording… sing along") else toast("Couldn't start the microphone")
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else toast("Microphone permission is needed to record")
    }
    val toggleRecord: () -> Unit = {
        if (recording) {
            if (playerVm.stopRecording() != null) toast("Take saved to Library → Your takes") else toast("Take too short, not saved")
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { libraryVm.exportBackup(it, ::toast) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { libraryVm.importBackup(it, ::toast) }
    }

    val current = player.track
    val play: (List<Track>, Int, Boolean) -> Unit = { list, index, shuffle ->
        if (list.isNotEmpty()) playerVm.play(list, index, shuffle)
    }
    val openCollection: (LibraryDestination) -> Unit = { dest ->
        libraryRoute = dest.route
        tab = Tab.Library
    }

    val songActions = SongActions(
        savedIds = savedIds,
        vocalTags = vocalTags,
        downloaded = downloaded,
        downloading = downloading,
        toggleFavorite = libraryVm::toggleFavorite,
        toggleVocal = { libraryVm.toggleVocalTag(it.trackId) },
        addToPlaylist = { addSheetTrack = it },
        playNext = { playerVm.playNext(it); toast("Playing next") },
        addToQueue = { playerVm.addToQueue(it); toast("Added to queue") },
        download = { libraryVm.download(it); toast("Downloading…") },
        removeDownload = { libraryVm.removeDownload(it.trackId) },
        openExternal = { t ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/watch?v=${t.videoId}"))) }
        },
    )

    BackHandler(enabled = showNowPlaying) { showNowPlaying = false }
    BackHandler(enabled = !showNowPlaying && tab != Tab.Home && libraryRoute == LibraryDestination.Root.route) { tab = Tab.Home }

    val hasMini = player.title.isNotEmpty()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = navInset + if (hasMini) 150.dp else 90.dp

    CompositionLocalProvider(
        LocalSongActions provides songActions,
        LocalPlayingInfo provides PlayingInfo(player.mediaId, player.isPlaying),
    ) {
      SharedTransitionLayout {
        val sharedScope = this
        Box(Modifier.fillMaxSize().background(Bg)) {
            Box(Modifier.fillMaxSize()) {
                val needsStatusPad = !(tab == Tab.Library && libraryRoute != LibraryDestination.Root.route)
                Box(if (needsStatusPad) Modifier.fillMaxSize().statusBarsPadding() else Modifier.fillMaxSize()) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = tab,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                        label = "tab",
                    ) { shown ->
                    when (shown) {
                        Tab.Home -> HomeScreen(
                            player = player,
                            history = history,
                            mostPlayed = mostPlayed,
                            saved = saved,
                            offline = offline,
                            downloaded = downloaded,
                            playlists = playlists,
                            recordings = recordings,
                            bottomPadding = bottomPadding,
                            onPlay = play,
                            onResume = { playerVm.togglePlayPause() },
                            onOpenCollection = openCollection,
                            onOpenSettings = { settingsOpen = true },
                            onGoSearch = { tab = Tab.Search },
                        )

                        Tab.Search -> SearchScreen(
                            state = searchState,
                            recents = recents,
                            suggestions = suggestions,
                            bottomPadding = bottomPadding,
                            onQueryChange = searchVm::onQueryChange,
                            onSearch = searchVm::search,
                            onClear = searchVm::clearResults,
                            onLoadMore = searchVm::loadMore,
                            onRemoveRecent = searchVm::removeRecent,
                            onClearRecents = searchVm::clearRecents,
                            onPlay = { list, i -> play(list, i, false) },
                        )

                        Tab.Takes -> TakesScreen(
                            recordings = recordings,
                            playingTakeId = playingTakeId,
                            bottomPadding = bottomPadding,
                            onBack = null,
                            onPlay = playerVm::playTake,
                            onStop = playerVm::stopTake,
                            onDelete = playerVm::deleteTake,
                        )

                        Tab.Library -> LibraryScreen(
                            destination = LibraryDestination.from(libraryRoute),
                            onNavigate = { libraryRoute = it.route },
                            saved = saved,
                            playlists = playlists,
                            offline = offline,
                            downloaded = downloaded,
                            vocalTags = vocalTags,
                            recordings = recordings,
                            playingTakeId = playingTakeId,
                            bottomPadding = bottomPadding,
                            onPlay = play,
                            onCreatePlaylist = { createOpen = true },
                            onImportPlaylist = { importOpen = true },
                            onDeletePlaylist = libraryVm::deletePlaylist,
                            onRenamePlaylist = libraryVm::renamePlaylist,
                            onRemoveFromPlaylist = libraryVm::removeFromPlaylist,
                            onMoveInPlaylist = libraryVm::moveInPlaylist,
                            onDownloadAll = { libraryVm.downloadAll(it); toast("Downloading ${tracksLabel(it.size)}…") },
                            onRemoveDownload = libraryVm::removeDownload,
                            onPlayTake = playerVm::playTake,
                            onStopTake = playerVm::stopTake,
                            onDeleteTake = playerVm::deleteTake,
                        )
                    }
                    }
                }
            }

            // Floating chrome: mini player capsule over a glass tab pill + round search button.
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Bg.copy(alpha = 0.85f)), startY = 0f, endY = 160f))
                    .navigationBarsPadding()
                    .padding(bottom = 6.dp)
            ) {
                AnimatedVisibility(visible = hasMini && !showNowPlaying, enter = fadeIn(tween(250)), exit = fadeOut(tween(150))) {
                    Column {
                        MiniPlayer(
                            track = current,
                            state = player,
                            onOpen = { haptics.tick(); showNowPlaying = true },
                            onToggle = { haptics.tick(); playerVm.togglePlayPause() },
                            onNext = { haptics.tick(); playerVm.next() },
                            onPrevious = { haptics.tick(); playerVm.previous() },
                            artModifier = with(sharedScope) { Modifier.sharedElement(rememberSharedContentState("np-art"), this@AnimatedVisibility) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                BottomBar(tab = tab, onSelect = {
                    if (it != tab) haptics.tick()
                    if (it == tab && it == Tab.Library) libraryRoute = LibraryDestination.Root.route
                    tab = it
                })
            }

            AnimatedVisibility(
                visible = showNowPlaying,
                enter = slideInVertically(tween(380)) { it / 5 } + fadeIn(tween(260)),
                exit = slideOutVertically(tween(300)) { it / 5 } + fadeOut(tween(220)),
                modifier = Modifier.fillMaxSize(),
            ) {
                NowPlayingScreen(
                    track = current,
                    state = player,
                    isFavorite = current != null && current.trackId in savedIds,
                    isDownloaded = current != null && current.trackId in downloaded,
                    downloadProgress = current?.let { downloading[it.trackId] },
                    sleepEndsAt = sleepEndsAt,
                    loop = loop,
                    lyrics = lyrics,
                    versions = versions,
                    queue = queue,
                    recording = recording,
                    audioQuality = audioQuality,
                    onClose = { showNowPlaying = false },
                    onTogglePlayPause = playerVm::togglePlayPause,
                    onNext = playerVm::next,
                    onPrevious = playerVm::previous,
                    onSeek = playerVm::seekToFraction,
                    onToggleShuffle = playerVm::toggleShuffle,
                    onCycleRepeat = playerVm::cycleRepeat,
                    onSetSpeed = playerVm::setSpeed,
                    onSetPitch = playerVm::setPitch,
                    onStartSleep = playerVm::startSleepTimer,
                    onSleepEndOfTrack = playerVm::startSleepAtEndOfTrack,
                    onCancelSleep = playerVm::cancelSleepTimer,
                    onToggleFavorite = { current?.let(libraryVm::toggleFavorite) },
                    onToggleDownload = {
                        current?.let {
                            if (it.trackId in downloaded || it.trackId in downloading) libraryVm.removeDownload(it.trackId)
                            else { libraryVm.download(it); toast("Downloading…") }
                        }
                    },
                    onCycleLoop = playerVm::cycleLoop,
                    onClearLoop = playerVm::clearLoop,
                    onToggleRecord = toggleRecord,
                    onLoadVersions = playerVm::loadVersions,
                    onSwapVersion = playerVm::swapVersion,
                    onSkipTo = playerVm::skipToQueueIndex,
                    onRemoveFromQueue = playerVm::removeFromQueue,
                    onMoveInQueue = playerVm::moveInQueue,
                    onClearUpcoming = playerVm::clearUpcoming,
                    onSetAudioQuality = libraryVm::setAudioQuality,
                    artModifier = with(sharedScope) { Modifier.sharedElement(rememberSharedContentState("np-art"), this@AnimatedVisibility) },
                    showGestureTip = gestureTip,
                    onGestureTipSeen = { gestureTip = false; libraryVm.markTipSeen("np_gestures") },
                )
            }

            AnimatedVisibility(visible = !onboarded, enter = fadeIn(), exit = fadeOut(tween(400)), modifier = Modifier.fillMaxSize()) {
                OnboardingScreen(onDone = libraryVm::setOnboarded)
            }
        }
      }

        AddToPlaylistSheet(
            track = addSheetTrack,
            playlists = playlists,
            onDismiss = { addSheetTrack = null },
            onPick = { playlistId ->
                addSheetTrack?.let {
                    libraryVm.addToPlaylist(playlistId, it)
                    toast("Added to \"${playlists.firstOrNull { p -> p.id == playlistId }?.name.orEmpty()}\"")
                }
                addSheetTrack = null
            },
            onCreate = { name ->
                addSheetTrack?.let {
                    libraryVm.createPlaylistAndAdd(name, it)
                    toast("Added to \"$name\"")
                }
                addSheetTrack = null
            },
        )
    }

    if (createOpen) {
        TextInputDialog("New playlist", "Create", { createOpen = false }, { libraryVm.createPlaylist(it); createOpen = false })
    }
    if (importOpen) {
        TextInputDialog(
            title = "Import YouTube playlist",
            confirmLabel = "Import",
            hint = "Paste a playlist link",
            onDismiss = { importOpen = false },
            onConfirm = {
                importOpen = false
                toast("Importing…")
                libraryVm.importPlaylist(it, ::toast)
            },
        )
    }
    if (settingsOpen) {
        SettingsSheet(
            themeMode = themeMode,
            accent = accent,
            quality = audioQuality,
            downloadsBytes = remember(downloaded) { app.offline.downloadedBytes() },
            downloadCount = downloaded.size,
            onThemeMode = libraryVm::setThemeMode,
            onAccent = libraryVm::setAccent,
            onQuality = libraryVm::setAudioQuality,
            onImportPlaylist = { settingsOpen = false; importOpen = true },
            onBackup = { backupLauncher.launch("vocal-vault-backup.json") },
            onRestore = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            onClearHistory = { libraryVm.clearHistory(); toast("History cleared") },
            updateState = updateState,
            autoUpdate = autoUpdate,
            onCheckUpdate = { app.updater.check(manual = true) },
            onAutoUpdate = app.updater::setAutoCheck,
            onDismiss = { settingsOpen = false },
        )
    }
    // Waits for onboarding, and stays above the settings sheet.
    if (onboarded) UpdateDialog(app.updater)
}

/** Frosted pill with three tabs, plus a separate round search button. */
@Composable
private fun BottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
    val glass = Surface2.copy(alpha = 0.92f)
    val border = Color.White.copy(alpha = if (LocalAppColorsIsDark()) 0.10f else 0.0f)
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(60.dp).shadow(16.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp))
                .background(glass).border(0.5.dp, border, RoundedCornerShape(30.dp)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Tab.Home, Tab.Library, Tab.Takes).forEach { item ->
                val active = item == tab
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(26.dp))
                        .background(if (active) TextHi.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { onSelect(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(if (active) item.activeIcon else item.icon, contentDescription = null, tint = if (active) Accent else TextHi, modifier = Modifier.size(23.dp))
                    Text(item.label, style = MaterialTheme.typography.labelSmall, color = if (active) Accent else TextHi)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        val searchActive = tab == Tab.Search
        Box(
            Modifier.size(60.dp).shadow(16.dp, CircleShape).clip(CircleShape)
                .background(if (searchActive) Accent else glass).border(0.5.dp, border, CircleShape)
                .clickable { onSelect(Tab.Search) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = "Search", tint = if (searchActive) Color.White else TextHi, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun LocalAppColorsIsDark() = com.vocalvault.app.ui.theme.LocalAppColors.current.isDark

/** Floating capsule mini player (Apple Music), tinted by artwork (Spotify), swipe to skip (YT Music). */
@Composable
private fun MiniPlayer(
    track: Track?,
    state: PlayerUiState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    artModifier: Modifier = Modifier,
) {
    val fraction = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val tint = rememberArtworkTint(track?.artworkUrl)
    val bg = lerp(Surface2, tint, 0.35f)
    var dragX by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .graphicsLayer { translationX = dragX * 0.4f }
            .shadow(16.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragX < -120f) onNext() else if (dragX > 120f) onPrevious()
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                ) { change, amount -> change.consume(); dragX += amount }
            }
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track?.artworkUrl, artModifier.size(40.dp), corner = 20.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state.title, style = MaterialTheme.typography.titleSmall, color = TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (state.isBuffering) "Loading…" else state.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggle) {
                if (state.isBuffering) CircularProgressIndicator(color = TextHi, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = TextHi,
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = onNext, enabled = state.hasNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = if (state.hasNext) TextHi else TextLow, modifier = Modifier.size(28.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 28.dp).fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(TextHi.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(TextHi))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(
    track: Track?,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    if (track == null) return
    var createOpen by remember(track.trackId) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp)) {
            Text("Add to playlist", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Text(track.title, style = MaterialTheme.typography.bodyMedium, color = TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clickable { createOpen = true }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Text("New playlist", style = MaterialTheme.typography.titleMedium)
            }
            LazyColumn(Modifier.fillMaxWidth().height((playlists.size.coerceAtMost(6) * 68).dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    val already = track.trackId in playlist.trackIds
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !already) { onPick(playlist.id) }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Mosaic(playlist.tracks, Modifier.size(52.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tracksLabel(playlist.tracks.size), style = MaterialTheme.typography.bodyMedium, color = TextMid)
                        }
                        if (already) Icon(Icons.Rounded.Check, contentDescription = "Already added", tint = Accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    if (createOpen) {
        TextInputDialog("New playlist", "Create", { createOpen = false }, { onCreate(it); createOpen = false })
    }
}
