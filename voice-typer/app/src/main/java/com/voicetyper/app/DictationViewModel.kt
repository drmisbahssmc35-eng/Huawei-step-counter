package com.voicetyper.app

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicetyper.app.Prefs.Companion.ENGINE_CLOUD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DictationViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application get() = getApplication()
    val prefs = Prefs(app)

    var text by mutableStateOf("")
    var partial by mutableStateOf("")
        private set
    var listening by mutableStateOf(false)
        private set
    var transcribing by mutableStateOf(false)
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var status by mutableStateOf("Tap the mic and start speaking")
    var engine by mutableStateOf(prefs.engine)
        private set
    var language by mutableStateOf(prefs.language)
        private set
    val history = mutableStateListOf<String>().apply { addAll(prefs.history()) }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wantListening = false
    private var activeEngine = engine
    private val recorder = AudioRecorder(app)
    private var levelJob: Job? = null

    fun updateEngine(e: String) {
        if (listening || transcribing) return
        engine = e
        prefs.engine = e
    }

    fun updateLanguage(tag: String) {
        if (listening || transcribing) return
        language = tag
        prefs.language = tag
    }

    fun start() {
        if (listening || transcribing) return
        if (!prefs.appendMode) text = ""
        partial = ""
        activeEngine = engine
        if (activeEngine == ENGINE_CLOUD) startCloud() else startSystem()
    }

    fun stop() {
        if (!listening) return
        if (activeEngine == ENGINE_CLOUD) {
            stopCloud()
            return
        }
        wantListening = false
        status = "Finishing…"
        recognizer?.stopListening()
        // Safety net in case the recognizer never reports back.
        main.postDelayed({ if (listening) finishSession() }, 2500)
    }

    // ---------------------------------------------------------------- Google / phone recognizer

    private fun startSystem() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            status = "No speech service on this phone. Update the Google app, or switch to Whisper AI."
            return
        }
        wantListening = true
        listening = true
        status = "Starting…"
        val r = recognizer ?: createRecognizer().also { recognizer = it }
        r.startListening(recognizerIntent())
    }

    /** Prefer Google's recognizer (best accuracy, supports Bangla); fall back to the phone default. */
    private fun createRecognizer(): SpeechRecognizer {
        val services = ctx.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        val google = services.firstOrNull { it.serviceInfo.packageName == "com.google.android.googlequicksearchbox" }
            ?: services.firstOrNull { it.serviceInfo.packageName == "com.google.android.tts" }
        val r = if (google != null) {
            SpeechRecognizer.createSpeechRecognizer(
                ctx, ComponentName(google.serviceInfo.packageName, google.serviceInfo.name)
            )
        } else {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        }
        r.setRecognitionListener(listener)
        return r
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        if (language.isNotEmpty()) {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
        }
        // Let the speaker pause to think without cutting them off.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        if (Build.VERSION.SDK_INT >= 33) {
            // Automatic punctuation and capitalization.
            putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
            putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, false)
        }
    }

    /** Keeps listening across pauses until the user taps stop. */
    private fun restartSoon() {
        main.postDelayed({ if (wantListening) recognizer?.startListening(recognizerIntent()) }, 120)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (wantListening) status = "Listening… speak now"
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            level = 0f
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
        }

        override fun onResults(results: Bundle?) {
            val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            partial = ""
            appendSegment(best)
            if (wantListening) restartSoon() else finishSession()
        }

        override fun onError(error: Int) {
            level = 0f
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    if (wantListening) restartSoon() else finishSession()

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    if (wantListening) {
                        recognizer?.cancel()
                        restartSoon()
                    } else {
                        finishSession()
                    }

                SpeechRecognizer.ERROR_CLIENT ->
                    if (!wantListening) finishSession()

                else -> finishSession(errorText(error))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun errorText(code: Int) = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Network problem. Check your internet connection"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed"
        SpeechRecognizer.ERROR_SERVER -> "Speech server error. Try again"
        12 -> "This language isn't supported here. Try Whisper AI"
        13 -> "Language pack not downloaded. Get it in Google app → Settings → Voice"
        else -> "Speech recognition error ($code)"
    }

    // ---------------------------------------------------------------- Whisper (cloud)

    private fun startCloud() {
        if (prefs.apiKey.isBlank()) {
            status = "Add your API key in Settings (⚙) to use Whisper AI"
            return
        }
        try {
            recorder.start()
        } catch (e: Exception) {
            status = "Couldn't start the microphone: ${e.message}"
            return
        }
        listening = true
        status = "Recording… tap again when you're done"
        levelJob = viewModelScope.launch {
            while (isActive) {
                level = recorder.level()
                delay(80)
            }
        }
    }

    private fun stopCloud() {
        levelJob?.cancel()
        level = 0f
        listening = false
        val file = recorder.stop()
        if (file == null) {
            status = "Recording was too short. Hold on a little longer"
            return
        }
        transcribing = true
        status = "Transcribing…"
        val whisperLang = language.substringBefore('-').ifBlank { null }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    CloudTranscriber.transcribe(file, prefs.apiBase, prefs.apiKey, prefs.model, whisperLang)
                }
            }
            transcribing = false
            result.onSuccess {
                appendSegment(it)
                finishSession()
            }.onFailure {
                finishSession("Transcription failed: ${it.message}")
            }
        }
    }

    // ---------------------------------------------------------------- shared

    private fun appendSegment(raw: String) {
        var seg = raw.trim()
        if (seg.isEmpty()) return
        val cur = text.trimEnd()
        if (cur.isEmpty() || cur.last() in ".!?।") seg = seg.replaceFirstChar { it.uppercase() }
        text = if (cur.isEmpty()) seg else "$cur $seg"
    }

    private fun finishSession(error: String? = null) {
        main.removeCallbacksAndMessages(null)
        wantListening = false
        if (partial.isNotBlank()) {
            appendSegment(partial)
            partial = ""
        }
        listening = false
        level = 0f
        val t = text.trim()
        status = when {
            t.isEmpty() -> error ?: "Didn't catch anything. Tap the mic and try again"
            prefs.autoCopy -> {
                copy(t, toast = false)
                if (error != null) "$error. Text so far was copied" else "✓ Copied. Ready to paste anywhere"
            }
            else -> error ?: "Done. Tap Copy to copy the text"
        }
        if (t.isNotEmpty()) addHistory(t)
    }

    fun copy(t: String = text.trim(), toast: Boolean = true) {
        if (t.isEmpty()) return
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Voice Typer", t))
        // Android 13+ shows its own "Copied" confirmation.
        if (toast && Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
        if (toast) status = "✓ Copied. Ready to paste anywhere"
    }

    private fun addHistory(t: String) {
        // In append mode the newest entry grows; replace it instead of adding near-duplicates.
        if (history.isNotEmpty() && t.startsWith(history[0])) history.removeAt(0)
        history.remove(t)
        history.add(0, t)
        while (history.size > Prefs.MAX_HISTORY) history.removeAt(history.lastIndex)
        prefs.saveHistory(history.toList())
    }

    fun deleteHistory(t: String) {
        history.remove(t)
        prefs.saveHistory(history.toList())
    }

    fun clearHistory() {
        history.clear()
        prefs.saveHistory(emptyList())
    }

    override fun onCleared() {
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        recorder.cancel()
    }
}
