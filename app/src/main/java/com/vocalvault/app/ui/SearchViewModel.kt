package com.vocalvault.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vocalvault.app.VocalVaultApp
import com.vocalvault.app.data.VocalCursor
import com.vocalvault.app.data.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val canLoadMore: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val searchedOnce: Boolean = false,
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VocalVaultApp
    private val newPipe = app.newPipe
    private val store = app.store

    val recentSearches = store.recentSearches

    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()
    private var suggestJob: Job? = null

    /** Debounced autocomplete while the user types. */
    fun onQueryChange(text: String) {
        suggestJob?.cancel()
        if (text.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            kotlinx.coroutines.delay(220)
            _suggestions.value = newPipe.suggestions(text)
        }
    }

    private var job: Job? = null
    private var cursor: VocalCursor? = null

    fun search(query: String) {
        if (query.isBlank()) return
        suggestJob?.cancel()
        _suggestions.value = emptyList()
        store.addRecentSearch(query)
        job?.cancel()
        _state.value = _state.value.copy(query = query, loading = true, error = null, results = emptyList(), canLoadMore = false)
        job = viewModelScope.launch {
            runCatching { newPipe.searchVocals(query) }
                .onSuccess { r ->
                    cursor = r.cursor
                    _state.value = _state.value.copy(results = r.tracks, canLoadMore = r.hasMore, loading = false, searchedOnce = true)
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.value = _state.value.copy(loading = false, searchedOnce = true, error = e.message ?: "Search failed. Check your connection.")
                }
        }
    }

    fun loadMore() {
        val s = _state.value
        val c = cursor ?: return
        if (s.loading || !s.canLoadMore) return
        _state.value = s.copy(loading = true, error = null)
        job = viewModelScope.launch {
            runCatching { newPipe.searchVocals(s.query, c) }
                .onSuccess { r ->
                    cursor = r.cursor
                    _state.value = _state.value.copy(
                        results = _state.value.results + r.tracks,
                        canLoadMore = r.hasMore && r.tracks.isNotEmpty(),
                        loading = false,
                    )
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "Couldn't load more. Check your connection.")
                }
        }
    }

    fun clearResults() {
        job?.cancel()
        _state.value = _state.value.copy(query = "", results = emptyList(), searchedOnce = false, loading = false, error = null)
    }

    fun removeRecent(query: String) = store.removeRecentSearch(query)

    fun clearRecents() = store.clearRecentSearches()
}
