package com.vocalvault.app.playback

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.vocalvault.app.VocalVaultApp
import com.vocalvault.app.data.Recording
import com.vocalvault.app.data.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val ready: Boolean = false,
    val isPlaying: Boolean = false,
    val mediaId: String = "",
    val track: Track? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: Uri? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queueSize: Int = 0,
    val queueIndex: Int = -1,
    val isBuffering: Boolean = false,
    val error: String? = null,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VocalVaultApp
    private val store = app.store
    private val studio = Studio(application)

    /** A-B loop points in ms, or null when off. B is null while only A is set. */
    data class LoopRange(val a: Long, val b: Long?)

    private val _loop = MutableStateFlow<LoopRange?>(null)
    val loop = _loop.asStateFlow()

    private val _lyrics = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val lyrics = _lyrics.asStateFlow()

    private val _versions = MutableStateFlow<VersionsState>(VersionsState.Idle)
    val versions = _versions.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording = _recording.asStateFlow()

    private val _playingTakeId = MutableStateFlow<String?>(null)
    val playingTakeId = _playingTakeId.asStateFlow()

    private var lyricsJob: kotlinx.coroutines.Job? = null
    private var lastTrackId: String? = null

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            _sleepEndsAt.value = extras.getLong(PlaybackService.EXTRA_SLEEP_ENDS_AT, 0L)
        }
    }

    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java))
    ).setListener(controllerListener).buildAsync()

    private var controller: MediaController? = null
    private val pendingActions = ArrayDeque<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerUiState())
    val state = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    /** Current play queue in playback order (as added; shuffle order not reflected). */
    val queue = _queue.asStateFlow()

    private val _sleepEndsAt = MutableStateFlow(0L)
    val sleepEndsAt = _sleepEndsAt.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) refreshQueue()
            refresh()
        }
    }

    init {
        controllerFuture.addListener({
            val mediaController = runCatching { controllerFuture.get() }.getOrNull()
                ?: return@addListener
            controller = mediaController
            _sleepEndsAt.value =
                mediaController.sessionExtras.getLong(PlaybackService.EXTRA_SLEEP_ENDS_AT, 0L)
            mediaController.addListener(listener)
            mediaController.setPlaybackParameters(
                PlaybackParameters(store.playbackSpeed, store.playbackPitch)
            )
            refreshQueue()
            refresh()
            while (pendingActions.isNotEmpty()) {
                pendingActions.removeFirst().invoke(mediaController)
            }
        }, ContextCompat.getMainExecutor(application))

        viewModelScope.launch {
            while (isActive) {
                val c = controller
                if (c?.isPlaying == true) {
                    val range = _loop.value
                    if (range?.b != null && c.currentPosition >= range.b) c.seekTo(range.a)
                    refresh()
                }
                delay(if (_loop.value?.b != null) 50L else 250L)
            }
        }
    }

    fun play(tracks: List<Track>, startIndex: Int, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        val items = tracks.map { it.toMediaItem() }
        withController { mediaController ->
            mediaController.setMediaItems(items, index, 0L)
            mediaController.shuffleModeEnabled = shuffle
            mediaController.prepare()
            mediaController.play()
        }
    }

    /** Insert right after the current song. Starts playback if nothing is loaded. */
    fun playNext(track: Track) = withController { player ->
        if (player.mediaItemCount == 0) {
            play(listOf(track), 0)
        } else {
            player.addMediaItem(player.currentMediaItemIndex + 1, track.toMediaItem())
        }
    }

    fun addToQueue(track: Track) = withController { player ->
        if (player.mediaItemCount == 0) play(listOf(track), 0) else player.addMediaItem(track.toMediaItem())
    }

    fun skipToQueueIndex(index: Int) = withController { player ->
        if (index in 0 until player.mediaItemCount) {
            player.seekTo(index, 0L)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun removeFromQueue(index: Int) = withController { player ->
        if (index in 0 until player.mediaItemCount && index != player.currentMediaItemIndex) {
            player.removeMediaItem(index)
        }
    }

    fun moveInQueue(from: Int, to: Int) = withController { player ->
        val count = player.mediaItemCount
        if (from in 0 until count && to in 0 until count && from != to) player.moveMediaItem(from, to)
    }

    fun clearUpcoming() = withController { player ->
        val next = player.currentMediaItemIndex + 1
        if (next < player.mediaItemCount) player.removeMediaItems(next, player.mediaItemCount)
    }

    /** Tap 1 sets A, tap 2 sets B and starts looping, tap 3 clears. */
    fun cycleLoop() = withController { player ->
        val pos = player.currentPosition
        _loop.value = when (val l = _loop.value) {
            null -> LoopRange(pos, null)
            else -> if (l.b == null && pos > l.a + 500) l.copy(b = pos) else null
        }
    }

    fun clearLoop() { _loop.value = null }

    fun loadVersions() {
        val track = _state.value.track ?: return
        _versions.value = VersionsState.Loading
        viewModelScope.launch {
            _versions.value = runCatching { app.newPipe.alternatives(track) }
                .fold({ VersionsState.Loaded(track.trackId, it) }, { VersionsState.Failed(it.message ?: "Couldn't load versions") })
        }
    }

    /** Swap the current queue item for another upload of the same song, keeping position. */
    fun swapVersion(replacement: Track) = withController { player ->
        val index = player.currentMediaItemIndex
        val pos = player.currentPosition
        player.replaceMediaItem(index, replacement.toMediaItem())
        player.seekTo(index, pos)
        player.prepare()
        player.play()
    }

    fun startRecording(): Boolean {
        val track = _state.value.track ?: return false
        return runCatching {
            studio.start(track, controller?.currentPosition ?: 0L)
            _recording.value = true
            withController { if (!it.isPlaying) it.play() }
        }.isSuccess
    }

    fun stopRecording(): Recording? {
        val take = studio.stop()
        _recording.value = false
        take?.let(store::addRecording)
        return take
    }

    /** Plays a take together with its vocal, lined up where the take started. */
    fun playTake(take: Recording) {
        stopTake()
        withController { player ->
            player.setMediaItems(listOf(take.track.toMediaItem()), 0, take.startMs)
            player.prepare()
            player.play()
        }
        runCatching {
            studio.playTake(take) {
                _playingTakeId.value = null
                controller?.pause()
            }
            _playingTakeId.value = take.id
        }
    }

    fun stopTake() {
        if (studio.isPlayingTake) controller?.pause()
        studio.stopTake()
        _playingTakeId.value = null
    }

    fun deleteTake(id: String) {
        if (_playingTakeId.value == id) stopTake()
        store.deleteRecording(id)
    }

    private fun onTrackChanged(track: Track?) {
        _loop.value = null
        _versions.value = VersionsState.Idle
        lyricsJob?.cancel()
        if (track == null) {
            _lyrics.value = LyricsState.Idle
            return
        }
        _lyrics.value = LyricsState.Loading
        lyricsJob = viewModelScope.launch {
            _lyrics.value = runCatching { app.lyrics.lyricsFor(track) }
                .fold({ if (it.isEmpty) LyricsState.None else LyricsState.Loaded(it) }, { LyricsState.None })
        }
    }

    fun togglePlayPause() = withController { player ->
        when {
            player.isPlaying -> player.pause()
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0L)
                player.play()
            }
            player.playbackState == Player.STATE_IDLE -> {
                player.prepare()
                player.play()
            }
            else -> player.play()
        }
    }

    fun pause() {
        controller?.takeIf { it.isPlaying }?.pause()
    }

    fun next() = withController { player ->
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun previous() = withController { player ->
        if (player.currentPosition > 4_000L || !player.hasPreviousMediaItem()) {
            player.seekTo(0L)
        } else {
            player.seekToPreviousMediaItem()
        }
    }

    fun seekToFraction(fraction: Float) = withController { player ->
        val duration = player.duration
        if (duration > 0L && duration != C.TIME_UNSET) {
            player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        }
    }

    fun toggleShuffle() = withController { player ->
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun cycleRepeat() = withController { player ->
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) {
        store.playbackSpeed = speed
        withController { player ->
            player.setPlaybackParameters(
                PlaybackParameters(speed, player.playbackParameters.pitch)
            )
        }
    }

    fun setPitch(pitch: Float) {
        store.playbackPitch = pitch
        withController { player ->
            player.setPlaybackParameters(
                PlaybackParameters(player.playbackParameters.speed, pitch)
            )
        }
    }

    val audioQuality = store.audioQuality

    fun setAudioQuality(quality: com.vocalvault.app.data.AudioQuality) = store.setAudioQuality(quality)

    fun startSleepTimer(minutes: Int) {
        _sleepEndsAt.value = System.currentTimeMillis() + minutes * 60_000L
        withController { player ->
            player.sendCustomCommand(
                SessionCommand(PlaybackService.CMD_SLEEP_START, Bundle.EMPTY),
                Bundle().apply { putInt(PlaybackService.ARG_MINUTES, minutes) },
            )
        }
    }

    fun startSleepAtEndOfTrack() {
        _sleepEndsAt.value = PlaybackService.SLEEP_END_OF_TRACK.toLong()
        withController { player ->
            player.sendCustomCommand(
                SessionCommand(PlaybackService.CMD_SLEEP_START, Bundle.EMPTY),
                Bundle().apply { putInt(PlaybackService.ARG_MINUTES, PlaybackService.SLEEP_END_OF_TRACK) },
            )
        }
    }

    fun cancelSleepTimer() {
        _sleepEndsAt.value = 0L
        withController { player ->
            player.sendCustomCommand(
                SessionCommand(PlaybackService.CMD_SLEEP_CANCEL, Bundle.EMPTY),
                Bundle.EMPTY,
            )
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        val current = controller
        if (current != null) action(current) else pendingActions.addLast(action)
    }

    private fun refreshQueue() {
        val player = controller ?: return
        _queue.value = (0 until player.mediaItemCount).mapNotNull { i ->
            Track.fromJson(player.getMediaItemAt(i).mediaMetadata.extras?.getString(EXTRA_TRACK))
        }
    }

    private fun friendlyError(e: androidx.media3.common.PlaybackException): String = when (e.errorCode) {
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Connection lost. Tap play if it doesn't resume."
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "This song is blocked or expired. Try another."
        else -> if (e.cause?.message?.contains("stream", ignoreCase = true) == true)
            "Couldn't load this song. Tap play to retry."
        else "Playback failed. Tap play to retry."
    }

    private fun refresh() {
        val player = controller ?: return
        val metadata = player.mediaMetadata
        val duration = player.duration
        val currentTrack = Track.fromJson(metadata.extras?.getString(EXTRA_TRACK))
        if (currentTrack?.trackId != lastTrackId) {
            lastTrackId = currentTrack?.trackId
            onTrackChanged(currentTrack)
        }
        _state.value = PlayerUiState(
            ready = true,
            isPlaying = player.isPlaying,
            mediaId = player.currentMediaItem?.mediaId.orEmpty(),
            track = currentTrack,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            artworkUri = metadata.artworkUri,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            // Before prepare() (e.g. restored queue) the player has no duration yet; use the track's.
            durationMs = if (duration > 0L && duration != C.TIME_UNSET) duration else currentTrack?.durationMs ?: 0L,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            speed = player.playbackParameters.speed,
            pitch = player.playbackParameters.pitch,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            queueSize = player.mediaItemCount,
            queueIndex = player.currentMediaItemIndex,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            error = player.playerError?.let(::friendlyError),
        )
    }

    override fun onCleared() {
        if (studio.isRecording) stopRecording()
        studio.stopTake()
        val current = controller
        if (current != null) {
            current.release()
        } else {
            MediaController.releaseFuture(controllerFuture)
        }
        controller = null
        super.onCleared()
    }
}

const val EXTRA_TRACK = "vocalvault.track"

sealed interface LyricsState {
    data object Idle : LyricsState
    data object Loading : LyricsState
    data object None : LyricsState
    data class Loaded(val lyrics: com.vocalvault.app.data.Lyrics) : LyricsState
}

sealed interface VersionsState {
    data object Idle : VersionsState
    data object Loading : VersionsState
    data class Loaded(val forTrackId: String, val tracks: List<Track>) : VersionsState
    data class Failed(val message: String) : VersionsState
}

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(trackId)
    .setUri(playbackUri())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(artworkUrl?.let(Uri::parse))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(Bundle().apply { putString(EXTRA_TRACK, toJson().toString()) })
            .build()
    )
    .build()
