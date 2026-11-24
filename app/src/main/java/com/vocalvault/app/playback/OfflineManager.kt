package com.vocalvault.app.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.vocalvault.app.data.LibraryStore
import com.vocalvault.app.data.NewPipeDownloader
import com.vocalvault.app.data.NewPipeRepository
import com.vocalvault.app.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Full-song downloads into a separate, never-evicted cache. Always best quality.
 * Playback reads this cache first (see PlaybackService) only once a download is complete,
 * so a half-downloaded file is never stitched together with a different-bitrate stream.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class OfflineManager(
    private val cache: SimpleCache,
    client: OkHttpClient,
    private val newPipe: NewPipeRepository,
    private val store: LibraryStore,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val jobs = HashMap<String, Future<*>>()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    /** trackId -> 0..1 for queued/in-progress downloads. */
    val progress = _progress.asStateFlow()

    private val _failed = MutableStateFlow<Set<String>>(emptySet())
    val failed = _failed.asStateFlow()

    private val dataSourceFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setCacheKeyFactory { spec -> downloadKey(spec.uri.lastPathSegment.orEmpty()) }
        .setUpstreamDataSourceFactory(
            ResolvingDataSource.Factory(OkHttpDataSource.Factory(client).setUserAgent(NewPipeDownloader.USER_AGENT)) { spec ->
                val id = spec.uri.lastPathSegment ?: throw IOException("Bad track uri")
                spec.withUri(Uri.parse(newPipe.getStreamUrlBlocking(id, lowTier = false)))
            }
        )

    /** Resume anything pinned but not finished (e.g. app was killed mid-download). */
    fun resumePending() {
        store.offline.value.filterNot { store.isDownloaded(it.trackId) }.forEach(::download)
    }

    fun download(track: Track) {
        store.pinOffline(track)
        if (store.isDownloaded(track.trackId)) return
        synchronized(jobs) {
            if (jobs[track.trackId]?.isDone == false) return
            _failed.value = _failed.value - track.trackId
            _progress.value = _progress.value + (track.trackId to 0f)
            jobs[track.trackId] = executor.submit {
                try {
                    CacheWriter(
                        dataSourceFactory.createDataSource(),
                        DataSpec.Builder().setUri(track.playbackUri()).build(),
                        null,
                    ) { length, cached, _ ->
                        if (length > 0) _progress.value = _progress.value + (track.trackId to cached.toFloat() / length)
                    }.cache()
                    store.setDownloaded(track.trackId, true)
                } catch (e: InterruptedException) {
                    // Cancelled.
                } catch (e: Exception) {
                    if (!Thread.currentThread().isInterrupted) _failed.value = _failed.value + track.trackId
                } finally {
                    _progress.value = _progress.value - track.trackId
                }
            }
        }
    }

    fun remove(trackId: String) {
        synchronized(jobs) { jobs.remove(trackId)?.cancel(true) }
        val videoId = trackId.removePrefix("yt:")
        executor.execute { runCatching { cache.removeResource(downloadKey(videoId)) } }
        store.unpinOffline(trackId)
        _failed.value = _failed.value - trackId
    }

    fun downloadedBytes(): Long = runCatching { cache.cacheSpace }.getOrDefault(0L)

    companion object {
        fun downloadKey(videoId: String) = "dl:$videoId"
    }
}
