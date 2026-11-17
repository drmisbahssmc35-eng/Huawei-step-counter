package com.voicetyper.app

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Sends a recording to any OpenAI-compatible /audio/transcriptions endpoint (Groq, OpenAI, ...). */
object CloudTranscriber {

    fun transcribe(file: File, baseUrl: String, apiKey: String, model: String, language: String?): String {
        val boundary = "----VoiceTyper${System.currentTimeMillis()}"
        val conn = URL(baseUrl.trimEnd('/') + "/audio/transcriptions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 20_000
            conn.readTimeout = 180_000
            conn.setChunkedStreamingMode(0)
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            conn.outputStream.use { out ->
                fun field(name: String, value: String) {
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
                }
                field("model", model)
                field("response_format", "json")
                field("temperature", "0")
                if (!language.isNullOrBlank()) field("language", language)
                out.write(
                    ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.m4a\"\r\n" +
                        "Content-Type: audio/mp4\r\n\r\n").toByteArray()
                )
                file.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(body).getJSONObject("error").getString("message") }
                    .getOrNull() ?: body.take(200)
                throw IOException(if (code == 401) "API key was rejected (401). Check it in Settings." else "HTTP $code: $msg")
            }
            return JSONObject(body).optString("text").trim()
        } finally {
            conn.disconnect()
        }
    }
}
