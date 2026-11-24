package com.vocalvault.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class SubQuery(val query: String, val contentFilter: String)

/** Opaque paging state for [NewPipeRepository.searchVocals]. */
class VocalCursor(internal val pages: Map<SubQuery, Page?>, internal val seen: Set<String>)

data class VocalResults(val tracks: List<Track>, val cursor: VocalCursor, val hasMore: Boolean)

private val VOCAL_WORDS = setOf("acapella", "cappella", "capella", "acappella", "vocal", "vocals", "only", "isolated", "vox", "stem", "stems")

enum class AudioQuality(val label: String, val description: String) {
    Auto("Auto", "Low bitrate on slow connections, best otherwise"),
    DataSaver("Data saver", "About 50 kbps. Plays on very slow internet"),
    Best("Best", "Highest bitrate available"),
}

data class SearchPage(
    val tracks: List<Track>,
    val nextPage: Page?,
)

/**
 * YouTube / YouTube Music search + audio stream extraction through NewPipeExtractor.
 * No API key needed.
 */
class NewPipeRepository(context: Context, client: OkHttpClient) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    /** Current user setting; set by the app from LibraryStore. */
    var qualityProvider: () -> AudioQuality = { AudioQuality.Auto }

    // Once a video starts loading at one bitrate it must stay there: the player reads one
    // continuous file, and the disk cache keys bytes by (video, tier).
    private val pinnedLowTier = ConcurrentHashMap<String, Boolean>()

    private val service = ServiceList.YouTube

    private data class CachedUrl(val url: String, val expiresAt: Long)

    private val streamCache = ConcurrentHashMap<String, CachedUrl>()

    init {
        NewPipe.init(NewPipeDownloader(client))
    }

    /**
     * Vocal-only search. The user types a plain song/artist; the app itself:
     *  1. runs several vocal-biased queries across YouTube and YouTube Music in parallel,
     *  2. merges and de-duplicates them,
     *  3. keeps only titles that read as vocal-only (acapella / isolated vocals / vocals only),
     *  4. keeps only results that actually match the search words, best matches first.
     * Pages are fetched until enough vocal hits are found. Pass the returned cursor to load more.
     */
    suspend fun searchVocals(query: String, cursor: VocalCursor? = null): VocalResults = withContext(Dispatchers.IO) {
        val base = query.trim()
        val alreadyVocal = VocalMatcher.isVocal(base)
        val queries = if (alreadyVocal) {
            listOf(SubQuery(base, YoutubeSearchQueryHandlerFactory.VIDEOS), SubQuery(base, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS))
        } else {
            listOf(
                SubQuery("$base acapella", YoutubeSearchQueryHandlerFactory.VIDEOS),
                SubQuery("$base vocals only", YoutubeSearchQueryHandlerFactory.VIDEOS),
                SubQuery("$base acapella", YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            )
        }
        val queryWords = words(base).filterNot { it in VOCAL_WORDS }.toSet()

        var pages: Map<SubQuery, Page?> = cursor?.pages ?: queries.associateWith { null }
        var started = cursor != null
        val seen = cursor?.seen?.toMutableSet() ?: mutableSetOf()
        val hits = mutableListOf<Pair<Track, Double>>()
        var rounds = 0

        while (hits.size < 12 && rounds < 3) {
            val active = pages.filter { (_, page) -> !started || page != null }
            if (active.isEmpty()) break
            val fetched = coroutineScope {
                active.map { (sub, page) -> async { sub to runCatching { fetchPage(sub, page) }.getOrNull() } }.awaitAll()
            }
            if (fetched.all { it.second == null }) {
                if (!started) throw IOException("Search failed. Check your connection.")
                break
            }
            started = true
            pages = pages + fetched.associate { (sub, page) -> sub to page?.nextPage }

            // Round-robin merge keeps a mix from every query instead of one dominating.
            val lists = fetched.mapNotNull { it.second?.tracks }
            val merged = (0 until (lists.maxOfOrNull { it.size } ?: 0)).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
            for (track in merged) {
                if (!seen.add(track.videoId)) continue
                if (!VocalMatcher.isVocal(track.title)) continue
                val score = relevance(track, queryWords)
                if (queryWords.isNotEmpty() && score < 0.5) continue
                hits += track to score
            }
            rounds++
        }

        VocalResults(
            tracks = hits.sortedByDescending { it.second }.map { it.first },
            cursor = VocalCursor(pages, seen),
            hasMore = pages.values.any { it != null },
        )
    }

    private fun fetchPage(sub: SubQuery, page: Page?): SearchPage {
        val handler = service.searchQHFactory.fromQuery(sub.query, listOf(sub.contentFilter), "")
        return if (page == null) {
            val info = SearchInfo.getInfo(service, handler)
            SearchPage(info.relatedItems.mapNotNull { (it as? StreamInfoItem)?.toTrack() }, info.nextPage)
        } else {
            val more = SearchInfo.getMoreItems(service, handler, page)
            SearchPage(more.items.mapNotNull { (it as? StreamInfoItem)?.toTrack() }, more.nextPage)
        }
    }

    /** Fraction of the user's search words found in title + artist. */
    private fun relevance(track: Track, queryWords: Set<String>): Double {
        if (queryWords.isEmpty()) return 1.0
        val hay = words(track.title + " " + track.artist)
        return queryWords.count { word -> hay.any { it == word || (word.length > 3 && it.startsWith(word)) } }.toDouble() / queryWords.size
    }

    private fun words(text: String): Set<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }.toSet()

    private fun StreamInfoItem.toTrack(): Track? {
        val id = runCatching { service.streamLHFactory.getId(url) }.getOrNull() ?: return null
        if (streamType.name.contains("LIVE")) return null
        return Track(
            videoId = id,
            title = name,
            artist = uploaderName.orEmpty().removeSuffix(" - Topic"),
            durationMs = duration.coerceAtLeast(0L) * 1000L,
            artworkUrl = thumbnails.maxByOrNull { it.height }?.url,
        )
    }

    /** True if [videoId] should use the low-bitrate stream. Decided once per video per session. */
    fun useLowTier(videoId: String): Boolean = pinnedLowTier.getOrPut(videoId) {
        when (qualityProvider()) {
            AudioQuality.DataSaver -> true
            AudioQuality.Best -> false
            AudioQuality.Auto -> isSlowNetwork()
        }
    }

    /** Disk-cache key for a track at its pinned tier. */
    fun cacheKey(videoId: String): String = "yt:$videoId:${if (useLowTier(videoId)) "lo" else "hi"}"

    private fun isSlowNetwork(): Boolean {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return true
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return true
        val down = caps.linkDownstreamBandwidthKbps
        return down in 1 until 3_000
    }

    /** Blocking. Audio-only progressive stream URL for [videoId] at the requested tier. Cached for a few hours. */
    fun getStreamUrlBlocking(videoId: String, lowTier: Boolean = useLowTier(videoId)): String {
        val cacheId = "$videoId:$lowTier"
        streamCache[cacheId]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.url }

        val info = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$videoId")
        val best = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .sortedWith(
                compareByDescending<AudioStream> { it.audioTrackType?.name.let { t -> t == null || t == "ORIGINAL" } }
                    .thenBy {
                        // Unknown bitrate (-1) must never win either sort.
                        val kbps = it.averageBitrate
                        if (lowTier) (if (kbps > 0) kbps else Int.MAX_VALUE) else -kbps.coerceAtLeast(0)
                    }
            )
            .firstOrNull()
            ?: throw IOException("No playable audio stream for $videoId")

        // YouTube URLs live ~6h; refresh well before that.
        streamCache[cacheId] = CachedUrl(best.content, System.currentTimeMillis() + 4 * 60 * 60 * 1000L)
        return best.content
    }

    /** YouTube's own autocomplete for [query]. Empty on any failure. */
    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching { service.suggestionExtractor.suggestionList(query.trim()) }.getOrDefault(emptyList()).take(8)
    }

    /** Title + tracks of a public YouTube / YouTube Music playlist link. */
    suspend fun importPlaylist(url: String): Pair<String, List<Track>> = withContext(Dispatchers.IO) {
        val info = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(service, url.trim())
        val tracks = info.relatedItems.mapNotNull { it.toTrack() }.toMutableList()
        var next = info.nextPage
        while (next != null && tracks.size < 500) {
            val more = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getMoreItems(service, url.trim(), next)
            tracks += more.items.mapNotNull { it.toTrack() }
            next = more.nextPage
        }
        info.name.ifBlank { "Imported playlist" } to tracks.distinctBy { it.videoId }
    }

    /** Other vocal uploads of the same song, for when one version sounds bad. */
    suspend fun alternatives(track: Track): List<Track> {
        val (artist, title) = LyricsRepository.cleanTitle(track.title, track.artist)
        return searchVocals("$artist $title".trim()).tracks.filterNot { it.videoId == track.videoId }
    }

    fun clearStreamCache() {
        streamCache.clear()
    }
}
