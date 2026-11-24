package com.vocalvault.app.playback

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import com.vocalvault.app.data.Recording
import com.vocalvault.app.data.Track
import java.io.File
import java.util.UUID

/** Records the mic to AAC while the vocal plays, and plays a take back. */
class Studio(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var track: Track? = null
    private var startMs = 0L
    private var startedAt = 0L

    private var player: MediaPlayer? = null

    val isRecording get() = recorder != null

    fun start(track: Track, vocalPositionMs: Long) {
        stop()?.let { File(it.path).delete() }
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val out = File(dir, "take_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(128_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(out.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = out
        this.track = track
        startMs = vocalPositionMs
        startedAt = System.currentTimeMillis()
    }

    /** Stops recording. Returns the take, or null if nothing usable was recorded. */
    fun stop(): Recording? {
        val r = recorder ?: return null
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        val out = file ?: return null
        val t = track ?: return null
        val duration = System.currentTimeMillis() - startedAt
        if (!ok || duration < 1_000) {
            out.delete()
            return null
        }
        return Recording(UUID.randomUUID().toString(), out.absolutePath, t, startMs, duration, System.currentTimeMillis())
    }

    fun playTake(recording: Recording, onDone: () -> Unit) {
        stopTake()
        player = MediaPlayer().apply {
            setDataSource(recording.path)
            setOnCompletionListener { stopTake(); onDone() }
            prepare()
            start()
        }
    }

    fun stopTake() {
        player?.runCatching { stop(); release() }
        player = null
    }

    val isPlayingTake get() = player != null
}
