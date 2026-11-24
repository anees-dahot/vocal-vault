package com.vocalvault.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vocalvault.app.VocalVaultApp
import com.vocalvault.app.data.Playlist
import com.vocalvault.app.data.ThemeMode
import com.vocalvault.app.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VocalVaultApp
    private val store = app.store

    val favorites = store.favorites
    val savedIds = store.favorites
        .map { list -> list.map { it.trackId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val vocalTags = store.vocalTags
    val playlists = store.playlists
    val history = store.history
    val offline = store.offline
    val downloaded = store.downloaded
    val downloadProgress = app.offline.progress
    val downloadFailed = app.offline.failed
    val recordings = store.recordings
    val themeMode = store.themeMode
    val accent = store.accent
    val audioQuality = store.audioQuality
    val onboarded = store.onboarded
    fun setOnboarded() = store.setOnboarded()
    fun tipSeen(name: String) = store.tipSeen(name)
    fun markTipSeen(name: String) = store.markTipSeen(name)

    /** Most played first, at least 2 plays. */
    val mostPlayed = store.history
        .map { h -> h.filter { it.plays >= 2 }.sortedByDescending { it.plays }.map { it.track }.take(20) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleFavorite(track: Track) = store.toggleFavorite(track)
    fun toggleVocalTag(trackId: String) = store.toggleVocalTag(trackId)
    fun createPlaylist(name: String): Playlist = store.createPlaylist(name)
    fun deletePlaylist(id: String) = store.deletePlaylist(id)
    fun renamePlaylist(id: String, name: String) = store.renamePlaylist(id, name)
    fun addToPlaylist(playlistId: String, track: Track) = store.addToPlaylist(playlistId, track)
    fun removeFromPlaylist(playlistId: String, trackId: String) = store.removeFromPlaylist(playlistId, trackId)
    fun moveInPlaylist(playlistId: String, from: Int, to: Int) = store.moveInPlaylist(playlistId, from, to)
    fun clearHistory() = store.clearHistory()

    fun createPlaylistAndAdd(name: String, track: Track) {
        val playlist = store.createPlaylist(name)
        store.addToPlaylist(playlist.id, track)
    }

    fun download(track: Track) = app.offline.download(track)
    fun downloadAll(tracks: List<Track>) = tracks.forEach(app.offline::download)
    fun removeDownload(trackId: String) = app.offline.remove(trackId)

    fun setThemeMode(mode: ThemeMode) = store.setThemeMode(mode)
    fun setAccent(index: Int) = store.setAccent(index)
    fun setAudioQuality(q: com.vocalvault.app.data.AudioQuality) = store.setAudioQuality(q)

    fun importPlaylist(url: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = runCatching { app.newPipe.importPlaylist(url) }.fold(
                { (name, tracks) ->
                    if (tracks.isEmpty()) "That playlist is empty or private"
                    else { store.createPlaylist(name, tracks); "Imported \"$name\" (${tracks.size} songs)" }
                },
                { "Couldn't import: ${it.message ?: "check the link"}" },
            )
            onResult(msg)
        }
    }

    fun exportBackup(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = runCatching {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)!!.use { it.write(store.exportJson().toByteArray()) }
                }
            }.fold({ "Backup saved" }, { "Backup failed: ${it.message}" })
            onResult(msg)
        }
    }

    fun importBackup(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = runCatching {
                val raw = withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
                store.importJson(raw)
            }.fold({ it }, { "Restore failed: ${it.message}" })
            onResult(msg)
        }
    }
}
