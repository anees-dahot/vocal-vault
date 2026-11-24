package com.vocalvault.app.data

import android.net.Uri
import org.json.JSONObject

/**
 * One YouTube / YouTube Music track. Audio is extracted with NewPipe and played by ExoPlayer.
 * trackId ("yt:<videoId>") is the stable key for favorites, playlists and vocal tags.
 */
data class Track(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUrl: String?,
) {
    val trackId: String get() = "yt:$videoId"

    val isLikelyVocal: Boolean get() = VocalMatcher.isVocal(title)

    /** PlaybackService resolves this to a fresh stream URL right before playback. */
    fun playbackUri(): Uri = Uri.parse("$SCHEME://yt/$videoId")

    fun toJson(): JSONObject = JSONObject().apply {
        put("type", "yt")
        put("id", videoId)
        put("title", title)
        put("artist", artist)
        put("durationMs", durationMs)
        artworkUrl?.let { put("artworkUrl", it) }
    }

    companion object {
        const val SCHEME = "vocalvault"

        fun fromJson(json: JSONObject): Track? = runCatching {
            if (json.optString("type", "yt") != "yt") return@runCatching null
            Track(
                videoId = json.getString("id"),
                title = json.optString("title"),
                artist = json.optString("artist"),
                durationMs = json.optLong("durationMs"),
                artworkUrl = json.optString("artworkUrl").takeIf { it.isNotBlank() },
            )
        }.getOrNull()

        fun fromJson(raw: String?): Track? =
            raw?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }
    }
}

/**
 * Heuristic that flags a track as "vocal only" based on its title. Users can also tag
 * any track manually, which always wins.
 */
object VocalMatcher {

    private val positiveKeywords = listOf(
        "vocal only", "vocals only", "vocal", "vocals", "acapella", "a cappella",
        "a-cappella", "acappella", "capella", "isolated", "vox", "stem", "stems"
    )

    private val negativeKeywords = listOf(
        "instrumental", "karaoke", "backing track", "minus one", "off vocal",
        "no vocals", "without vocals", "no lead"
    )

    // Whole-word matching so "stem" does not hit "system" or "vox" hit "voxel".
    private fun wordRegex(words: List<String>) = Regex(
        words.joinToString("|", prefix = "(?<![a-z0-9])(?:", postfix = ")(?![a-z0-9])") {
            it.replace(" ", "[\\s_.-]+")
        }
    )

    private val positive = wordRegex(positiveKeywords)
    private val negative = wordRegex(negativeKeywords)

    fun isVocal(vararg fields: String): Boolean {
        val haystack = fields.joinToString(" ").lowercase().replace('_', ' ')
        if (negative.containsMatchIn(haystack)) return false
        return positive.containsMatchIn(haystack)
    }
}
