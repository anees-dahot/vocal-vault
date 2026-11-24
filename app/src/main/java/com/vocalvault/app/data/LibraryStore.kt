package com.vocalvault.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<Track>,
) {
    val trackIds: List<String> get() = tracks.map { it.trackId }
}

data class HistoryEntry(val track: Track, val plays: Int, val lastPlayed: Long)

/** A take the user sang over a vocal. [startMs] is where the vocal was when recording began. */
data class Recording(
    val id: String,
    val path: String,
    val track: Track,
    val startMs: Long,
    val durationMs: Long,
    val createdAt: Long,
)

enum class ThemeMode(val label: String) { System("System"), Dark("Dark"), Light("Light") }

/**
 * Persistence for saved tracks (favorites), manual "vocal" tags, playlists, history,
 * offline pins, recordings and settings. Tracks are online, so full metadata is stored
 * alongside each string trackId.
 */
class LibraryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrate()
    }

    private val _favorites = MutableStateFlow(loadTracks(KEY_FAVORITES))
    /** Saved tracks, newest first. */
    val favorites: StateFlow<List<Track>> = _favorites.asStateFlow()

    private val _vocalTags = MutableStateFlow(prefs.getStringSet(KEY_VOCAL_TAGS, emptySet()).orEmpty().toSet())
    val vocalTags: StateFlow<Set<String>> = _vocalTags.asStateFlow()

    private val _playlists = MutableStateFlow(loadPlaylists())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _history = MutableStateFlow(loadHistory())
    /** Play history, most recent first. */
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _offline = MutableStateFlow(loadTracks(KEY_OFFLINE))
    /** Tracks the user pinned for offline. Download state lives in OfflineManager. */
    val offline: StateFlow<List<Track>> = _offline.asStateFlow()

    private val _downloaded = MutableStateFlow(prefs.getStringSet(KEY_DOWNLOADED, emptySet()).orEmpty().toSet())
    /** trackIds whose full audio is in the download cache. */
    val downloaded: StateFlow<Set<String>> = _downloaded.asStateFlow()

    private val _recordings = MutableStateFlow(loadRecordings())
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "Dark") }.getOrDefault(ThemeMode.Dark)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accent = MutableStateFlow(prefs.getInt(KEY_ACCENT, 0))
    /** Index into the theme's accent palette. */
    val accent: StateFlow<Int> = _accent.asStateFlow()

    private val _recentSearches = MutableStateFlow(
        runCatching {
            val arr = JSONArray(prefs.getString(KEY_RECENTS, "[]"))
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
    )
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _audioQuality = MutableStateFlow(
        runCatching { AudioQuality.valueOf(prefs.getString(KEY_QUALITY, null) ?: "Auto") }.getOrDefault(AudioQuality.Auto)
    )
    val audioQuality: StateFlow<AudioQuality> = _audioQuality.asStateFlow()

    // ---- First-run flags ----------------------------------------------------------------

    private val _onboarded = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    fun setOnboarded() {
        _onboarded.value = true
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    /** One-time hints, keyed by name ("np_gestures", ...). */
    fun tipSeen(name: String) = prefs.getBoolean("tip_$name", false)
    fun markTipSeen(name: String) = prefs.edit().putBoolean("tip_$name", true).apply()

    // ---- Settings -----------------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setAccent(index: Int) {
        _accent.value = index
        prefs.edit().putInt(KEY_ACCENT, index).apply()
    }

    fun setAudioQuality(quality: AudioQuality) {
        _audioQuality.value = quality
        prefs.edit().putString(KEY_QUALITY, quality.name).apply()
    }

    var playbackSpeed: Float
        get() = prefs.getFloat(KEY_SPEED, 1f)
        set(value) = prefs.edit().putFloat(KEY_SPEED, value).apply()

    var playbackPitch: Float
        get() = prefs.getFloat(KEY_PITCH, 1f)
        set(value) = prefs.edit().putFloat(KEY_PITCH, value).apply()

    // ---- Recent searches ----------------------------------------------------------------

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _recentSearches.value = (listOf(q) + _recentSearches.value.filterNot { it.equals(q, ignoreCase = true) }).take(12)
        saveRecents()
    }

    fun removeRecentSearch(query: String) {
        _recentSearches.value = _recentSearches.value - query
        saveRecents()
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        saveRecents()
    }

    private fun saveRecents() {
        prefs.edit().putString(KEY_RECENTS, JSONArray(_recentSearches.value).toString()).apply()
    }

    // ---- Favorites / tags ---------------------------------------------------------------

    fun isFavorite(trackId: String): Boolean = _favorites.value.any { it.trackId == trackId }

    fun toggleFavorite(track: Track) {
        val current = _favorites.value
        _favorites.value = if (current.any { it.trackId == track.trackId }) {
            current.filterNot { it.trackId == track.trackId }
        } else {
            listOf(track) + current
        }
        saveTracks(KEY_FAVORITES, _favorites.value)
    }

    fun toggleVocalTag(trackId: String) {
        val current = _vocalTags.value
        _vocalTags.value = if (trackId in current) current - trackId else current + trackId
        prefs.edit().putStringSet(KEY_VOCAL_TAGS, _vocalTags.value).apply()
    }

    // ---- History ------------------------------------------------------------------------

    fun recordPlay(track: Track) {
        val now = System.currentTimeMillis()
        val existing = _history.value.firstOrNull { it.track.trackId == track.trackId }
        val entry = HistoryEntry(track, (existing?.plays ?: 0) + 1, now)
        _history.value = (listOf(entry) + _history.value.filterNot { it.track.trackId == track.trackId }).take(200)
        saveHistory()
    }

    fun clearHistory() {
        _history.value = emptyList()
        saveHistory()
    }

    private fun saveHistory() {
        val arr = JSONArray()
        _history.value.forEach { arr.put(JSONObject().put("track", it.track.toJson()).put("plays", it.plays).put("last", it.lastPlayed)) }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    private fun loadHistory(): List<HistoryEntry> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val track = o.optJSONObject("track")?.let(Track::fromJson) ?: return@mapNotNull null
            HistoryEntry(track, o.optInt("plays", 1), o.optLong("last"))
        }
    }.getOrDefault(emptyList())

    // ---- Offline ------------------------------------------------------------------------

    fun pinOffline(track: Track) {
        if (_offline.value.any { it.trackId == track.trackId }) return
        _offline.value = listOf(track) + _offline.value
        saveTracks(KEY_OFFLINE, _offline.value)
    }

    fun unpinOffline(trackId: String) {
        _offline.value = _offline.value.filterNot { it.trackId == trackId }
        saveTracks(KEY_OFFLINE, _offline.value)
        setDownloaded(trackId, false)
    }

    fun setDownloaded(trackId: String, done: Boolean) {
        _downloaded.value = if (done) _downloaded.value + trackId else _downloaded.value - trackId
        prefs.edit().putStringSet(KEY_DOWNLOADED, _downloaded.value).apply()
    }

    fun isDownloaded(trackId: String) = trackId in _downloaded.value

    // ---- Recordings ---------------------------------------------------------------------

    fun addRecording(recording: Recording) {
        _recordings.value = listOf(recording) + _recordings.value
        saveRecordings()
    }

    fun deleteRecording(id: String) {
        _recordings.value.firstOrNull { it.id == id }?.let { runCatching { java.io.File(it.path).delete() } }
        _recordings.value = _recordings.value.filterNot { it.id == id }
        saveRecordings()
    }

    private fun saveRecordings() {
        val arr = JSONArray()
        _recordings.value.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("path", it.path).put("track", it.track.toJson())
                    .put("start", it.startMs).put("duration", it.durationMs).put("created", it.createdAt)
            )
        }
        prefs.edit().putString(KEY_RECORDINGS, arr.toString()).apply()
    }

    private fun loadRecordings(): List<Recording> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_RECORDINGS, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val track = o.optJSONObject("track")?.let(Track::fromJson) ?: return@mapNotNull null
            Recording(o.getString("id"), o.getString("path"), track, o.optLong("start"), o.optLong("duration"), o.optLong("created"))
        }.filter { java.io.File(it.path).exists() }
    }.getOrDefault(emptyList())

    // ---- Playlists ----------------------------------------------------------------------

    fun createPlaylist(name: String, tracks: List<Track> = emptyList()): Playlist {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "New playlist" },
            tracks = tracks.distinctBy { it.trackId },
        )
        _playlists.value = _playlists.value + playlist
        savePlaylists()
        return playlist
    }

    fun renamePlaylist(id: String, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        updatePlaylists { if (it.id == id) it.copy(name = clean) else it }
    }

    fun deletePlaylist(id: String) {
        _playlists.value = _playlists.value.filterNot { it.id == id }
        savePlaylists()
    }

    fun addToPlaylist(playlistId: String, track: Track) = updatePlaylists { playlist ->
        if (playlist.id == playlistId && track.trackId !in playlist.trackIds) {
            playlist.copy(tracks = playlist.tracks + track)
        } else {
            playlist
        }
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) = updatePlaylists { playlist ->
        if (playlist.id == playlistId) playlist.copy(tracks = playlist.tracks.filterNot { it.trackId == trackId })
        else playlist
    }

    fun moveInPlaylist(playlistId: String, from: Int, to: Int) = updatePlaylists { playlist ->
        if (playlist.id != playlistId || from !in playlist.tracks.indices || to !in playlist.tracks.indices) playlist
        else playlist.copy(tracks = playlist.tracks.toMutableList().apply { add(to, removeAt(from)) })
    }

    private fun updatePlaylists(transform: (Playlist) -> Playlist) {
        _playlists.value = _playlists.value.map(transform)
        savePlaylists()
    }

    // ---- Backup -------------------------------------------------------------------------

    /** Library as one JSON document (saved, playlists, tags, history, settings). */
    fun exportJson(): String = JSONObject()
        .put("app", "vocalvault")
        .put("version", 1)
        .put("saved", tracksToJson(_favorites.value))
        .put("playlists", playlistsToJson())
        .put("vocalTags", JSONArray(_vocalTags.value.toList()))
        .put("history", JSONArray(prefs.getString(KEY_HISTORY, "[]")))
        .put("offline", tracksToJson(_offline.value))
        .put("theme", _themeMode.value.name)
        .put("accent", _accent.value)
        .put("quality", _audioQuality.value.name)
        .toString(2)

    /** Merges a backup into the library. Returns a short summary, or throws on bad input. */
    fun importJson(raw: String): String {
        val root = JSONObject(raw)
        require(root.optString("app") == "vocalvault") { "Not a Vocal Vault backup" }
        val saved = tracksFromJson(root.optJSONArray("saved"))
        val savedIds = _favorites.value.map { it.trackId }.toSet()
        _favorites.value = _favorites.value + saved.filterNot { it.trackId in savedIds }
        saveTracks(KEY_FAVORITES, _favorites.value)

        val pls = root.optJSONArray("playlists") ?: JSONArray()
        var added = 0
        for (i in 0 until pls.length()) {
            val o = pls.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (_playlists.value.any { it.id == id }) continue
            _playlists.value = _playlists.value + Playlist(id.ifBlank { UUID.randomUUID().toString() }, o.optString("name", "Playlist"), tracksFromJson(o.optJSONArray("tracks")))
            added++
        }
        savePlaylists()

        root.optJSONArray("vocalTags")?.let { arr ->
            _vocalTags.value = _vocalTags.value + (0 until arr.length()).map { arr.getString(it) }
            prefs.edit().putStringSet(KEY_VOCAL_TAGS, _vocalTags.value).apply()
        }
        if (_history.value.isEmpty()) {
            root.optJSONArray("history")?.let {
                prefs.edit().putString(KEY_HISTORY, it.toString()).apply()
                _history.value = loadHistory()
            }
        }
        tracksFromJson(root.optJSONArray("offline")).forEach(::pinOffline)
        return "${saved.size} saved songs, $added playlists restored"
    }

    // ---- Persistence --------------------------------------------------------------------

    private fun tracksToJson(tracks: List<Track>) = JSONArray().apply { tracks.forEach { put(it.toJson()) } }

    private fun tracksFromJson(array: JSONArray?): List<Track> =
        if (array == null) emptyList()
        else (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(Track::fromJson) }

    private fun loadTracks(key: String): List<Track> =
        runCatching { tracksFromJson(JSONArray(prefs.getString(key, "[]"))) }.getOrDefault(emptyList())

    private fun saveTracks(key: String, tracks: List<Track>) {
        prefs.edit().putString(key, tracksToJson(tracks).toString()).apply()
    }

    private fun loadPlaylists(): List<Playlist> {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                Playlist(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    tracks = tracksFromJson(item.optJSONArray("tracks")),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun playlistsToJson() = JSONArray().apply {
        _playlists.value.forEach { playlist ->
            put(JSONObject().put("id", playlist.id).put("name", playlist.name).put("tracks", tracksToJson(playlist.tracks)))
        }
    }

    private fun savePlaylists() {
        prefs.edit().putString(KEY_PLAYLISTS, playlistsToJson().toString()).apply()
    }

    /**
     * v1 stored Long MediaStore IDs for local files. Local playback is gone, so those IDs can't
     * be played anymore: drop them, keep playlist names (now empty) so nothing silently vanishes.
     */
    private fun migrate() {
        if (prefs.getInt(KEY_SCHEMA, 1) >= SCHEMA) return
        val oldPlaylists = runCatching { JSONArray(prefs.getString(KEY_PLAYLISTS, "[]")) }.getOrNull()
        val migrated = JSONArray()
        if (oldPlaylists != null) {
            for (i in 0 until oldPlaylists.length()) {
                val item = oldPlaylists.optJSONObject(i) ?: continue
                migrated.put(
                    JSONObject()
                        .put("id", item.optString("id", UUID.randomUUID().toString()))
                        .put("name", item.optString("name", "Playlist"))
                        .put("tracks", item.optJSONArray("tracks") ?: JSONArray())
                )
            }
        }
        prefs.edit()
            .remove("favorites")
            .remove("vocal_tags")
            .putString(KEY_PLAYLISTS, migrated.toString())
            .putInt(KEY_SCHEMA, SCHEMA)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "vocal_vault"
        const val SCHEMA = 2
        const val KEY_SCHEMA = "schema"
        const val KEY_FAVORITES = "saved_tracks"
        const val KEY_VOCAL_TAGS = "vocal_tags_v2"
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_RECENTS = "recent_searches"
        const val KEY_QUALITY = "audio_quality"
        const val KEY_SPEED = "speed"
        const val KEY_PITCH = "pitch"
        const val KEY_HISTORY = "history"
        const val KEY_OFFLINE = "offline_tracks"
        const val KEY_DOWNLOADED = "downloaded_ids"
        const val KEY_RECORDINGS = "recordings"
        const val KEY_THEME = "theme_mode"
        const val KEY_ACCENT = "accent"
        const val KEY_ONBOARDED = "onboarded"
    }
}
