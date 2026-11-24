package com.vocalvault.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.vocalvault.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
    /** Lowercase hex, as published by GitHub for the asset. */
    val sha256: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Ready(val release: Release, val apk: File) : UpdateState
    /** [release] is null when the check itself failed. */
    data class Failed(val message: String, val release: Release?) : UpdateState
}

/** Finds, downloads and verifies new versions published as GitHub releases. */
class Updater(private val context: Context, private val client: OkHttpClient) {

    private val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir = File(context.cacheDir, "updates")
    private var job: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Whether the update dialog is on screen. */
    private val _prompt = MutableStateFlow(false)
    val prompt: StateFlow<Boolean> = _prompt.asStateFlow()

    private val _autoCheck = MutableStateFlow(prefs.getBoolean(KEY_AUTO, true))
    val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    fun setAutoCheck(enabled: Boolean) {
        _autoCheck.value = enabled
        prefs.edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    /** Quiet launch check: at most daily, never in debug builds, and silent on failure. */
    fun checkOnLaunch() {
        if (BuildConfig.DEBUG || !_autoCheck.value) return
        val sinceLast = System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0L)
        if (sinceLast in 0 until CHECK_INTERVAL_MS) return
        check(manual = false)
    }

    fun check(manual: Boolean) {
        when (_state.value) {
            is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Ready -> {
                if (manual) _prompt.value = true
                return
            }
            UpdateState.Checking -> return
            else -> Unit
        }
        _state.value = UpdateState.Checking
        job = scope.launch {
            try {
                val release = fetchLatest()
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                if (release == null || !isNewer(release.version, BuildConfig.VERSION_NAME)) {
                    dir.deleteRecursively()
                    _state.value = UpdateState.UpToDate
                } else {
                    _state.value = UpdateState.Available(release)
                    if (manual || prefs.getString(KEY_SKIPPED, null) != release.version) _prompt.value = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                _state.value = if (manual) {
                    UpdateState.Failed("Couldn't check for updates. Check your connection.", null)
                } else {
                    UpdateState.Idle
                }
            }
        }
    }

    fun download() {
        val release = when (val s = _state.value) {
            is UpdateState.Available -> s.release
            is UpdateState.Failed -> s.release ?: return
            else -> return
        }
        _state.value = UpdateState.Downloading(release, 0f)
        val previous = job
        job = scope.launch {
            // A cancelled download may still be winding down on the same file.
            previous?.join()
            try {
                val apk = fetchApk(release) { _state.value = UpdateState.Downloading(release, it) }
                _state.value = UpdateState.Ready(release, apk)
                _prompt.value = true
            } catch (e: Exception) {
                if (!isActive || e is CancellationException) return@launch
                Log.w(TAG, "Update download failed", e)
                val message = if (e is BadApkException) e.message!! else "Download failed. Check your connection and free space."
                _state.value = UpdateState.Failed(message, release)
            }
        }
    }

    fun cancelDownload() {
        val s = _state.value as? UpdateState.Downloading ?: return
        job?.cancel()
        _state.value = UpdateState.Available(s.release)
        _prompt.value = false
    }

    /** Closes the dialog and doesn't raise it again on its own for this version. */
    fun later() {
        val s = _state.value
        val release = when (s) {
            is UpdateState.Available -> s.release
            is UpdateState.Ready -> s.release
            is UpdateState.Failed -> s.release
            else -> null
        }
        release?.let { prefs.edit().putString(KEY_SKIPPED, it.version).apply() }
        if (s is UpdateState.Failed && release != null) _state.value = UpdateState.Available(release)
        _prompt.value = false
    }

    /** Hides the dialog and lets a running download carry on. */
    fun hide() {
        _prompt.value = false
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent() =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Hands the verified APK to the system installer. */
    fun install(activity: Context) {
        val s = _state.value as? UpdateState.Ready ?: return
        if (!s.apk.exists()) {
            // The OS may clear the cache while the dialog waits.
            _state.value = UpdateState.Failed("The download is gone. Try again.", s.release)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", s.apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            _state.value = UpdateState.Failed("No installer found on this device.", s.release)
        }
    }

    private fun fetchLatest(): Release? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "VocalVault")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null // Nothing published yet.
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return parseRelease(response.body!!.string())
        }
    }

    private fun parseRelease(body: String): Release? {
        val json = JSONObject(body)
        val assets = json.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
        val url = apk.getString("browser_download_url")
        if (!url.startsWith("https://github.com/$REPO/releases/download/")) throw IOException("Unexpected asset host")
        val digest = apk.optString("digest")
        if (!digest.startsWith("sha256:")) throw IOException("Asset has no checksum")
        return Release(
            version = json.getString("tag_name").removePrefix("v"),
            notes = if (json.isNull("body")) "" else json.getString("body").trim(),
            apkUrl = url,
            size = apk.getLong("size"),
            sha256 = digest.removePrefix("sha256:").lowercase(),
        )
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun fetchApk(release: Release, onProgress: (Float) -> Unit): File {
        val target = File(dir, "vocal-vault-${release.version}.apk")
        if (target.exists() && sha256(target) == release.sha256) return target.also(::verify)

        dir.deleteRecursively()
        dir.mkdirs()
        val part = File(dir, "download.part")
        val digest = MessageDigest.getInstance("SHA-256")
        val call = client.newCall(Request.Builder().url(release.apkUrl).header("User-Agent", "VocalVault").build())
        val job = currentCoroutineContext().job
        // Unblocks a stalled read as soon as the user cancels.
        val handle = job.invokeOnCompletion(onCancelling = true) { if (it != null) call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body!!.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var shown = -1
                        while (true) {
                            if (!job.isActive) throw CancellationException()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            total += read
                            val percent = (total * 100 / release.size.coerceAtLeast(1)).toInt().coerceAtMost(100)
                            if (percent != shown && job.isActive) {
                                shown = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            if (digest.digest().toHex() != release.sha256) throw BadApkException("Download was corrupted. Try again.")
            if (!part.renameTo(target)) throw IOException("Couldn't move the download")
            verify(target)
            return target
        } finally {
            handle.dispose()
            part.delete()
        }
    }

    /** Catches what the system installer would reject with a vague error. */
    @Suppress("DEPRECATION")
    private fun verify(apk: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(apk.path, flags)
        val own = pm.getPackageInfo(context.packageName, flags)
        val problem = when {
            info == null -> "The update file can't be read."
            info.packageName != context.packageName -> "The update is for a different app."
            PackageInfoCompat.getLongVersionCode(info) <= PackageInfoCompat.getLongVersionCode(own) ->
                "This version is already installed."
            // Some Android versions don't return archive signers; the installer still enforces them.
            signers(info).let { it.isNotEmpty() && it != signers(own) } ->
                "This update is signed differently from the installed app, so it can't be installed over it."
            else -> null
        }
        if (problem != null) {
            apk.delete()
            throw BadApkException(problem)
        }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }.toSet()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private class BadApkException(message: String) : Exception(message)

    companion object {
        private const val TAG = "Updater"
        private const val REPO = BuildConfig.UPDATE_REPO
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val KEY_AUTO = "auto_check"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_SKIPPED = "skipped_version"

        /** Numeric, dot-by-dot: 1.10.0 is newer than 1.9. */
        fun isNewer(candidate: String, installed: String): Boolean {
            val a = parts(candidate)
            val b = parts(installed)
            for (i in 0 until maxOf(a.size, b.size)) {
                val diff = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
                if (diff != 0) return diff > 0
            }
            return false
        }

        private fun parts(version: String) =
            version.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
