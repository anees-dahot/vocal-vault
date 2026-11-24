package com.vocalvault.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import java.util.concurrent.Executors
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vocalvault.app.MainActivity
import com.vocalvault.app.R
import com.vocalvault.app.VocalVaultApp
import com.vocalvault.app.data.NewPipeDownloader
import com.vocalvault.app.data.Track
import java.io.IOException

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaLibraryService.MediaLibrarySession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val prefetchExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var prefetchWriter: CacheWriter? = null
    private val sleepRunnable = Runnable {
        player?.pause()
        setSleepEndsAt(0L)
    }

    override fun onCreate() {
        super.onCreate()

        val app = application as VocalVaultApp
        val httpFactory = OkHttpDataSource.Factory(app.httpClient)
            .setUserAgent(NewPipeDownloader.USER_AGENT)
        // vocalvault://yt/<id> is resolved to a fresh YouTube stream URL on ExoPlayer's loader
        // thread, so queues never hold expired links.
        val resolvingFactory = ResolvingDataSource.Factory(httpFactory) { spec -> resolve(app, spec) }

        // Disk cache in front of the network. Keyed by (video, bitrate tier) - not by the
        // short-lived stream URL - so cached songs replay and seek with no connection at all.
        val cacheFactory = CacheDataSource.Factory()
            .setCache(app.mediaCache)
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setCacheKeyFactory { spec ->
                if (spec.uri.scheme == Track.SCHEME) app.newPipe.cacheKey(spec.uri.lastPathSegment.orEmpty())
                else spec.uri.toString()
            }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        this.cacheFactory = cacheFactory

        // Audio is tiny, so buffer far ahead (up to 10 min) and start after 1.5 s.
        // On a weak connection this rides out long dropouts instead of stuttering.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 600_000, 1_500, 3_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Completed downloads are read first. Only finished downloads get their real key, so a
        // partial download is never mixed with a stream of a different bitrate.
        val offlineFactory = CacheDataSource.Factory()
            .setCache(app.downloadCache)
            .setUpstreamDataSourceFactory(cacheFactory)
            .setCacheWriteDataSinkFactory(null)
            .setCacheKeyFactory { spec ->
                val id = spec.uri.lastPathSegment.orEmpty()
                if (spec.uri.scheme == Track.SCHEME && app.store.isDownloaded("yt:$id")) OfflineManager.downloadKey(id)
                else "none:${spec.uri}"
            }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(offlineFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(10))

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player = exoPlayer
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            private var retriedFor: String? = null
            private var networkRetries = 0
            private var recordedFor: String? = null

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                retriedFor = null
                recordedFor = null
                networkRetries = 0
                prefetchNext(exoPlayer)
                saveQueue(exoPlayer)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                prefetchNext(exoPlayer)
                saveQueue(exoPlayer)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveQueue(exoPlayer)
                val item = exoPlayer.currentMediaItem
                if (isPlaying && item != null && item.mediaId != recordedFor) {
                    recordedFor = item.mediaId
                    Track.fromJson(item.mediaMetadata.extras?.getString(EXTRA_TRACK))?.let(app.store::recordPlay)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // "End of track" sleep timer fired: stop pausing after every song.
                if (reason == androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                    exoPlayer.pauseAtEndOfMediaItems
                ) {
                    exoPlayer.pauseAtEndOfMediaItems = false
                    setSleepEndsAt(0L)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val id = exoPlayer.currentMediaItem?.mediaId
                // Expired/blocked stream URL: drop cached URLs and retry once for this item.
                val http = generateSequence(error.cause) { it.cause }
                    .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
                    .firstOrNull()
                if (http != null && http.responseCode in setOf(403, 410) && id != retriedFor) {
                    retriedFor = id
                    app.newPipe.clearStreamCache()
                    exoPlayer.prepare()
                    exoPlayer.play()
                    return
                }
                // Dropped connection: retry with backoff instead of stopping for good.
                val isNetwork = error.errorCode in androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED..
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                if (isNetwork && networkRetries < 3) {
                    networkRetries++
                    handler.postDelayed({
                        if (exoPlayer.playerError != null) {
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                    }, 2_000L * networkRetries)
                }
            }
        })
        restoreQueue(exoPlayer)

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibraryService.MediaLibrarySession.Builder(this, exoPlayer, SessionCallback())
            .setSessionActivity(sessionActivity)
            .build()
    }

    private var cacheFactory: CacheDataSource.Factory? = null
    private var prefetchedUri: android.net.Uri? = null

    /**
     * Warm the next track: extract its stream URL (the slow part on a bad connection) and
     * download its first ~512 KB into the disk cache, so the skip starts instantly.
     */
    private fun prefetchNext(exoPlayer: ExoPlayer) {
        val factory = cacheFactory ?: return
        val nextIndex = exoPlayer.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val uri = exoPlayer.getMediaItemAt(nextIndex).localConfiguration?.uri ?: return
        if (uri == prefetchedUri) return
        prefetchedUri = uri
        prefetchWriter?.cancel()
        prefetchExecutor.execute {
            runCatching {
                val writer = CacheWriter(
                    factory.createDataSource(),
                    DataSpec.Builder().setUri(uri).setPosition(0).setLength(512L * 1024).build(),
                    null,
                    null,
                )
                prefetchWriter = writer
                writer.cache()
            }
        }
    }

    private val queuePrefs by lazy { getSharedPreferences("playback_queue", MODE_PRIVATE) }

    /** Persist queue + position so the app reopens where the user left off. */
    private fun saveQueue(exoPlayer: ExoPlayer) {
        val tracks = org.json.JSONArray()
        for (i in 0 until exoPlayer.mediaItemCount) {
            val json = exoPlayer.getMediaItemAt(i).mediaMetadata.extras?.getString(EXTRA_TRACK) ?: continue
            tracks.put(org.json.JSONObject(json))
        }
        queuePrefs.edit()
            .putString("tracks", tracks.toString())
            .putInt("index", exoPlayer.currentMediaItemIndex)
            .putLong("position", exoPlayer.currentPosition.coerceAtLeast(0L))
            .apply()
    }

    /** Load the last queue paused, without touching the network until the user hits play. */
    private fun restoreQueue(exoPlayer: ExoPlayer) {
        val tracks = runCatching {
            val arr = org.json.JSONArray(queuePrefs.getString("tracks", "[]"))
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Track::fromJson) }
        }.getOrDefault(emptyList())
        if (tracks.isEmpty()) return
        val index = queuePrefs.getInt("index", 0).coerceIn(0, tracks.lastIndex)
        exoPlayer.setMediaItems(tracks.map { it.toMediaItem() }, index, queuePrefs.getLong("position", 0L))
    }

    private fun resolve(app: VocalVaultApp, spec: DataSpec): DataSpec {
        val uri = spec.uri
        if (uri.scheme != Track.SCHEME) return spec
        val videoId = uri.takeIf { it.host == "yt" }?.lastPathSegment ?: throw IOException("Bad track uri")
        // The cache key carries the tier chosen for this video; resolve to that same stream.
        val lowTier = spec.key?.endsWith(":lo") ?: app.newPipe.useLowTier(videoId)
        val streamUrl = try {
            app.newPipe.getStreamUrlBlocking(videoId, lowTier)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("YouTube stream unavailable: ${e.message}", e)
        }
        return spec.withUri(android.net.Uri.parse(streamUrl))
    }

    private fun setSleepEndsAt(endsAt: Long) {
        mediaSession?.setSessionExtras(Bundle().apply { putLong(EXTRA_SLEEP_ENDS_AT, endsAt) })
    }

    private inner class SessionCallback : MediaLibraryService.MediaLibrarySession.Callback {
        private val store get() = (application as VocalVaultApp).store

        override fun onGetLibraryRoot(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<androidx.media3.common.MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Vocal Vault"), params))

        override fun onGetItem(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<androidx.media3.common.MediaItem>> {
            val item = findTrack(mediaId)?.toMediaItem() ?: folder(mediaId, mediaId)
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        override fun onGetChildren(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<androidx.media3.common.MediaItem>>> {
            val children: List<androidx.media3.common.MediaItem> = when {
                parentId == ROOT -> listOf(
                    folder(NODE_RECENT, "Recently played"),
                    folder(NODE_SAVED, "Saved"),
                    folder(NODE_DOWNLOADS, "Downloads"),
                    folder(NODE_PLAYLISTS, "Playlists"),
                )
                parentId == NODE_RECENT -> store.history.value.take(50).map { it.track.toMediaItem() }
                parentId == NODE_SAVED -> store.favorites.value.map { it.toMediaItem() }
                parentId == NODE_DOWNLOADS -> store.offline.value.filter { store.isDownloaded(it.trackId) }.map { it.toMediaItem() }
                parentId == NODE_PLAYLISTS -> store.playlists.value.map { folder("playlist:${it.id}", it.name) }
                parentId.startsWith("playlist:") ->
                    store.playlists.value.firstOrNull { it.id == parentId.removePrefix("playlist:") }?.tracks.orEmpty().map { it.toMediaItem() }
                else -> emptyList()
            }
            val from = (page * pageSize).coerceAtMost(children.size)
            val paged = children.subList(from, (from + pageSize).coerceAtMost(children.size))
            return Futures.immediateFuture(LibraryResult.ofItemList(com.google.common.collect.ImmutableList.copyOf(paged), params))
        }

        /** Controllers in other processes (Android Auto, Assistant) send items without URIs. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<androidx.media3.common.MediaItem>,
        ): ListenableFuture<MutableList<androidx.media3.common.MediaItem>> =
            Futures.immediateFuture(
                mediaItems.mapNotNull { item ->
                    if (item.localConfiguration != null) item
                    else Track.fromJson(item.mediaMetadata.extras?.getString(EXTRA_TRACK))?.toMediaItem()
                        ?: findTrack(item.mediaId)?.toMediaItem()
                }.toMutableList()
            )

        private fun findTrack(mediaId: String): Track? =
            (store.favorites.value.asSequence() + store.history.value.asSequence().map { it.track } +
                store.offline.value.asSequence() + store.playlists.value.asSequence().flatMap { it.tracks })
                .firstOrNull { it.trackId == mediaId }

        private fun folder(id: String, title: String) = androidx.media3.common.MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SLEEP_START, Bundle.EMPTY))
                .add(SessionCommand(CMD_SLEEP_CANCEL, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SLEEP_START -> {
                    val minutes = args.getInt(ARG_MINUTES, 0)
                    handler.removeCallbacks(sleepRunnable)
                    player?.pauseAtEndOfMediaItems = minutes == SLEEP_END_OF_TRACK
                    if (minutes == SLEEP_END_OF_TRACK) {
                        setSleepEndsAt(SLEEP_END_OF_TRACK.toLong())
                    } else if (minutes > 0) {
                        val delayMs = minutes * 60_000L
                        handler.postDelayed(sleepRunnable, delayMs)
                        setSleepEndsAt(System.currentTimeMillis() + delayMs)
                    }
                }
                CMD_SLEEP_CANCEL -> {
                    handler.removeCallbacks(sleepRunnable)
                    player?.pauseAtEndOfMediaItems = false
                    setSleepEndsAt(0L)
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        const val CMD_SLEEP_START = "com.vocalvault.SLEEP_START"
        const val CMD_SLEEP_CANCEL = "com.vocalvault.SLEEP_CANCEL"
        const val ARG_MINUTES = "minutes"
        const val EXTRA_SLEEP_ENDS_AT = "sleepEndsAt"
        private const val ROOT = "root"
        private const val NODE_RECENT = "recent"
        private const val NODE_SAVED = "saved"
        private const val NODE_DOWNLOADS = "downloads"
        private const val NODE_PLAYLISTS = "playlists"
        /** Sleep "minutes" value meaning: pause when the current song ends. */
        const val SLEEP_END_OF_TRACK = -1
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibraryService.MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = player
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        player?.let(::saveQueue)
        handler.removeCallbacks(sleepRunnable)
        prefetchWriter?.cancel()
        prefetchExecutor.shutdownNow()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
