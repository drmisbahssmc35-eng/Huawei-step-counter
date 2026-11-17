package com.voicetyper.app

import android.content.Context
import org.json.JSONArray

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("voice_typer", Context.MODE_PRIVATE)

    var engine: String
        get() = sp.getString("engine", ENGINE_SYSTEM) ?: ENGINE_SYSTEM
        set(v) = sp.edit().putString("engine", v).apply()

    /** BCP-47 tag such as "en-US" or "bn-BD"; empty = phone default / auto-detect. */
    var language: String
        get() = sp.getString("language", "") ?: ""
        set(v) = sp.edit().putString("language", v).apply()

    var autoCopy: Boolean
        get() = sp.getBoolean("auto_copy", true)
        set(v) = sp.edit().putBoolean("auto_copy", v).apply()

    /** false = every new recording starts with an empty text box. */
    var appendMode: Boolean
        get() = sp.getBoolean("append", false)
        set(v) = sp.edit().putBoolean("append", v).apply()

    var apiBase: String
        get() = sp.getString("api_base", GROQ_BASE) ?: GROQ_BASE
        set(v) = sp.edit().putString("api_base", v.trim()).apply()

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    var model: String
        get() = sp.getString("model", GROQ_MODEL) ?: GROQ_MODEL
        set(v) = sp.edit().putString("model", v.trim()).apply()

    fun history(): List<String> {
        val a = runCatching { JSONArray(sp.getString("history", "[]")) }.getOrElse { JSONArray() }
        return (0 until a.length()).map { a.getString(it) }
    }

    fun saveHistory(list: List<String>) {
        sp.edit().putString("history", JSONArray(list.take(MAX_HISTORY)).toString()).apply()
    }

    companion object {
        const val ENGINE_SYSTEM = "system"
        const val ENGINE_CLOUD = "cloud"
        const val GROQ_BASE = "https://api.groq.com/openai/v1"
        const val GROQ_MODEL = "whisper-large-v3"
        const val OPENAI_BASE = "https://api.openai.com/v1"
        const val OPENAI_MODEL = "gpt-4o-transcribe"
        const val MAX_HISTORY = 30
    }
}
