package com.progresspal.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Google sign-in for Orthopaedic Genius with the OrthoPrep web app's own account (its Lovable Cloud
 * database), so exams and answers are shared between the phone and the website.
 *
 * Sign-in opens [LOGIN_PAGE] in the browser; after "Continue with Google" it redirects to
 * progresspal://ortho-auth#access_token=…&refresh_token=…&expires_at=…, handled by [handleRedirect].
 */
object OrthoCloud {
    private const val URL_BASE = "https://igkkcqosewgbpliyznzo.supabase.co"
    const val LOGIN_PAGE = "https://orthopedics-genius.lovable.app/app-login"

    data class Status(val email: String? = null, val syncing: Boolean = false, val error: String? = null, val lastSync: Long = 0)

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private lateinit var prefs: android.content.SharedPreferences

    private var access: String? = null
    private var refresh: String? = null
    private var expiresAt: Long = 0
    private var userId: String? = null

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status
    val signedIn get() = refresh != null

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("ortho_cloud", Context.MODE_PRIVATE)
        access = prefs.getString("access", null)
        refresh = prefs.getString("refresh", null)
        expiresAt = prefs.getLong("expires", 0)
        userId = prefs.getString("uid", null)
        if (signedIn) _status.value = Status(email = prefs.getString("email", null))
    }

    fun startSignIn(context: Context) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LOGIN_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** progresspal://ortho-auth#access_token=…&refresh_token=…&expires_at=… */
    fun handleRedirect(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "progresspal" || uri.host != "ortho-auth") return false
        val p = (uri.encodedFragment ?: uri.encodedQuery ?: "").split("&").mapNotNull {
            val i = it.indexOf('='); if (i <= 0) null else it.substring(0, i) to Uri.decode(it.substring(i + 1))
        }.toMap()
        val a = p["access_token"] ?: return false
        val r = p["refresh_token"] ?: return false
        save(a, r, p["expires_at"]?.toLongOrNull() ?: (System.currentTimeMillis() / 1000 + 3000))
        sync()
        return true
    }

    fun signOut() {
        access = null; refresh = null; userId = null; expiresAt = 0
        prefs.edit().clear().apply()
        _status.value = Status()
    }

    private fun save(a: String, r: String, exp: Long) {
        access = a; refresh = r; expiresAt = exp
        val claims = runCatching {
            val bytes = Base64.decode(a.split(".")[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            json.parseToJsonElement(String(bytes)).jsonObject
        }.getOrNull()
        userId = claims?.get("sub")?.jsonPrimitive?.contentOrNull ?: userId
        val email = claims?.get("email")?.jsonPrimitive?.contentOrNull ?: prefs.getString("email", null)
        prefs.edit().putString("access", a).putString("refresh", r).putLong("expires", exp)
            .putString("uid", userId).putString("email", email).apply()
        _status.value = _status.value.copy(email = email)
    }

    // ───────── HTTP ─────────

    private class HttpError(val code: Int, msg: String) : IOException("HTTP $code: $msg")

    private fun http(method: String, path: String, body: String?, token: String?, headers: Map<String, String> = emptyMap()): String {
        val c = URL(URL_BASE + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.setRequestProperty("apikey", OrthoExam.ANON_KEY)
            c.setRequestProperty("Authorization", "Bearer ${token ?: OrthoExam.ANON_KEY}")
            c.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() } ?: ""
            if (code !in 200..299) throw HttpError(code, text.take(300))
            return text
        } finally { c.disconnect() }
    }

    /** A valid access token (refreshed when needed); null when signed out. */
    private fun token(): String? {
        val r = refresh ?: return null
        if (access != null && System.currentTimeMillis() / 1000 < expiresAt - 60) return access
        return try {
            val o = json.parseToJsonElement(http("POST", "/auth/v1/token?grant_type=refresh_token", """{"refresh_token":${JsonPrimitive(r)}}""", null)).jsonObject
            val a = o["access_token"]!!.jsonPrimitive.content
            val exp = o["expires_at"]?.jsonPrimitive?.longOrNull ?: (System.currentTimeMillis() / 1000 + (o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600))
            save(a, o["refresh_token"]?.jsonPrimitive?.contentOrNull ?: r, exp)
            a
        } catch (e: HttpError) {
            if (e.code == 400 || e.code == 401) signOut()
            null
        }
    }

    private fun getAll(table: String, select: String, order: String, token: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        var offset = 0
        while (true) {
            val page = json.parseToJsonElement(http("GET", "/rest/v1/$table?select=$select&order=$order&limit=1000&offset=$offset", null, token)).jsonArray
            out += page.map { it.jsonObject }
            if (page.size < 1000) break
            offset += 1000
        }
        return out
    }

    private fun upsert(table: String, rows: List<JsonObject>, token: String) {
        rows.chunked(200).forEach { chunk ->
            http("POST", "/rest/v1/$table", JsonArray(chunk).toString(), token, mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
        }
    }

    // ───────── Sync ─────────

    /** Pull everything from the account, merge, then upload what only the phone has. */
    fun sync() { if (signedIn) scope.launch { syncNow() } }

    suspend fun syncNow() {
        if (!signedIn) return
        lock.withLock {
            _status.value = _status.value.copy(syncing = true, error = null)
            try {
                val t = token() ?: return
                val exams = getAll("exams", "*", "exam_number.asc", t)
                val qs = getAll("questions", "*", "created_at.asc", t).groupBy { it.str("exam_id") }
                val cloud = exams.mapNotNull { e ->
                    val id = e.str("id") ?: return@mapNotNull null
                    OExam(
                        id = id, number = e.int("exam_number") ?: 0,
                        startedAt = e.time("started_at") ?: 0,
                        completedAt = if (e.str("status") == "completed") (e.time("completed_at") ?: e.time("started_at")) else null,
                        timeTakenS = e.int("time_taken_seconds"),
                        questions = (qs[id] ?: emptyList()).sortedBy { it.int("question_order") ?: 0 }.map { q ->
                            OQuestion(
                                id = q.str("id") ?: "", text = q.str("question_text") ?: "",
                                a = q.str("option_a") ?: "", b = q.str("option_b") ?: "", c = q.str("option_c") ?: "", d = q.str("option_d") ?: "",
                                correct = q.str("correct_answer") ?: "A", explanation = q.str("explanation") ?: "", topic = q.str("topic") ?: "",
                                hash = q.str("question_hash") ?: "", userAnswer = q.str("user_answer"),
                                isCorrect = q["is_correct"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.booleanOrNull,
                                answeredAt = q.time("answered_at"), timesShown = q.int("times_shown") ?: 1, synced = true,
                            )
                        },
                        synced = true,
                    )
                }
                OrthoExam.mergeCloud(cloud)
                push(t)
                _status.value = _status.value.copy(syncing = false, lastSync = System.currentTimeMillis())
            } catch (e: Exception) {
                _status.value = _status.value.copy(syncing = false, error = e.message ?: "Sync failed")
            }
        }
    }

    /** Upload new / changed exams and answered questions. */
    fun pushSoon() { if (signedIn) scope.launch { lock.withLock { runCatching { token()?.let { push(it) } } } } }

    private fun push(t: String) {
        val uid = userId ?: return
        val d = OrthoExam.data.value
        val exams = d.exams.filter { !it.synced && it.questions.isNotEmpty() }
        val questions = d.exams.flatMap { e -> e.questions.mapIndexed { i, q -> Triple(e, i, q) } }
            .filter { (_, _, q) -> !q.synced && q.answeredAt != null }
        if (exams.isEmpty() && questions.isEmpty()) return
        upsert("exams", exams.map { e ->
            buildJsonObject {
                put("id", e.id); put("user_id", uid); put("exam_number", e.number)
                put("started_at", Instant.ofEpochMilli(e.startedAt).toString())
                put("completed_at", e.completedAt?.let { JsonPrimitive(Instant.ofEpochMilli(it).toString()) } ?: JsonNull)
                put("total_questions", e.questions.size); put("correct_answers", e.correctCount); put("wrong_answers", e.wrongCount)
                put("time_taken_seconds", e.timeTakenS?.let { JsonPrimitive(it) } ?: JsonNull)
                put("status", if (e.completed) "completed" else "in_progress")
            }
        }, t)
        upsert("questions", questions.map { (e, i, q) ->
            buildJsonObject {
                put("id", q.id); put("user_id", uid); put("exam_id", e.id)
                put("question_text", q.text); put("option_a", q.a); put("option_b", q.b); put("option_c", q.c); put("option_d", q.d)
                put("correct_answer", q.correct); put("explanation", q.explanation); put("topic", q.topic)
                put("question_hash", q.hash); put("question_order", i + 1)
                put("user_answer", q.userAnswer); put("is_correct", q.isCorrect)
                put("answered_at", Instant.ofEpochMilli(q.answeredAt!!).toString()); put("times_shown", q.timesShown)
            }
        }, t)
        OrthoExam.markSynced(exams, questions.map { it.third })
    }

    private fun JsonObject.str(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull
    private fun JsonObject.time(k: String) = str(k)?.let { s -> runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull() }
}
