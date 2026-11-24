package com.vocalvault.app

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.vocalvault.app.data.LibraryStore
import com.vocalvault.app.data.LyricsRepository
import com.vocalvault.app.data.NewPipeRepository
import com.vocalvault.app.playback.OfflineManager
import com.vocalvault.app.update.Updater
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(UnstableApi::class)
class VocalVaultApp : Application() {

    private val databaseProvider by lazy { StandaloneDatabaseProvider(this) }

    /** Played and prefetched audio, so replays and seeks don't need the network. One instance per process. */
    val mediaCache: SimpleCache by lazy {
        SimpleCache(File(cacheDir, "media"), LeastRecentlyUsedCacheEvictor(500L * 1024 * 1024), databaseProvider)
    }

    /** Songs the user downloaded. Never evicted; lives in filesDir so the OS won't clear it. */
    val downloadCache: SimpleCache by lazy {
        SimpleCache(File(filesDir, "downloads"), NoOpCacheEvictor(), databaseProvider)
    }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    lateinit var store: LibraryStore
        private set

    lateinit var newPipe: NewPipeRepository
        private set

    val lyrics by lazy { LyricsRepository(httpClient) }

    val offline by lazy { OfflineManager(downloadCache, httpClient, newPipe, store) }

    val updater by lazy { Updater(this, httpClient) }

    override fun onCreate() {
        super.onCreate()
        store = LibraryStore(this)
        newPipe = NewPipeRepository(this, httpClient)
        newPipe.qualityProvider = { store.audioQuality.value }
        offline.resumePending()
    }
}
