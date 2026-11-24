package com.vocalvault.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

data class LyricLine(val timeMs: Long, val text: String)

/** [synced] is empty when only plain lyrics exist. */
data class Lyrics(val synced: List<LyricLine>, val plain: String?) {
    val isEmpty get() = synced.isEmpty() && plain.isNullOrBlank()
}

/** Lyrics from LRCLIB (free, no key). Titles are cleaned of "acapella"/"(Official…)" noise first. */
class LyricsRepository(private val client: OkHttpClient) {

    private val cache = ConcurrentHashMap<String, Lyrics>()

    suspend fun lyricsFor(track: Track): Lyrics = withContext(Dispatchers.IO) {
        cache[track.trackId]?.let { return@withContext it }
        val (artist, title) = cleanTitle(track.title, track.artist)
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .apply { if (artist.isNotBlank()) addQueryParameter("artist_name", artist) }
            .build()
        val result = fetch(url.toString()) ?: fetch(
            "https://lrclib.net/api/search".toHttpUrl().newBuilder().addQueryParameter("q", "$artist $title".trim()).build().toString()
        ) ?: Lyrics(emptyList(), null)
        cache[track.trackId] = result
        result
    }

    private fun fetch(url: String): Lyrics? {
        val request = Request.Builder().url(url).header("User-Agent", "VocalVault/1.0").build()
        val body = client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null
        val arr = JSONArray(body)
        if (arr.length() == 0) return null
        // Prefer a result with synced lyrics.
        val items = (0 until arr.length()).map { arr.getJSONObject(it) }
        val best = items.firstOrNull { !it.optString("syncedLyrics").isNullOrBlank() && it.optString("syncedLyrics") != "null" } ?: items.first()
        val synced = best.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }?.let(::parseLrc).orEmpty()
        val plain = best.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        return Lyrics(synced, plain).takeUnless { it.isEmpty }
    }

    companion object {
        private val lrcLine = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")

        fun parseLrc(lrc: String): List<LyricLine> = lrc.lines().flatMap { line ->
            val stamps = lrcLine.findAll(line).toList()
            if (stamps.isEmpty()) return@flatMap emptyList()
            val text = line.substring(stamps.last().range.last + 1).trim()
            stamps.map { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3].let { if (it.isEmpty()) 0L else it.padEnd(3, '0').take(3).toLong() }
                LyricLine(min * 60_000 + sec * 1000 + frac, text)
            }
        }.sortedBy { it.timeMs }

        private val noise = Regex(
            """(?i)\b(acapella|a cappella|acappella|capella|vocals? only|isolated vocals?|vocals?|vox|stems?|official|audio|video|lyrics?|hd|hq|4k|remastered)\b"""
        )

        /** "Artist - Song (Acapella) [Vocals Only]" -> ("Artist", "Song"). */
        fun cleanTitle(rawTitle: String, uploader: String): Pair<String, String> {
            val stripped = rawTitle.replace(Regex("""[(\[{][^)\]}]*[)\]}]"""), " ")
            val parts = stripped.split(Regex("""\s[-–—|]\s""")).map { it.replace(noise, " ").replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
            return when {
                parts.size >= 2 -> parts[0] to parts[1]
                else -> uploader.trim() to (parts.firstOrNull() ?: rawTitle)
            }
        }
    }
}
