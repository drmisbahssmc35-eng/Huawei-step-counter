package com.voicetyper.app

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records the mic to a small AAC (.m4a) file for cloud transcription. */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start() {
        val f = File(context.cacheDir, "dictation.m4a")
        f.delete()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else newLegacyRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(48_000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            throw e
        }
        recorder = r
        file = f
        startedAt = System.currentTimeMillis()
    }

    @Suppress("DEPRECATION")
    private fun newLegacyRecorder() = MediaRecorder()

    /** 0..1 loudness since the last call. */
    fun level(): Float {
        val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        return Math.sqrt(amp / 32767.0).toFloat().coerceIn(0f, 1f)
    }

    /** Returns the recorded file, or null if nothing usable was recorded. */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        val tooShort = System.currentTimeMillis() - startedAt < 500
        return if (ok && !tooShort) file else null
    }

    fun cancel() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
    }
}
