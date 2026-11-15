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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google sign-in + sync with the same Lovable Cloud (Supabase) database the website uses.
 *
 * Sign-in: the app opens [LOGIN_PAGE] on the website in the phone's browser. After "Continue with Google"
 * the page redirects to progresspal://auth#access_token=…&refresh_token=… which [MainActivity] passes to
 * [handleRedirect].
 *
 * Sync: every local change is diffed against the previous state and pushed as upserts/deletes
 * (see [AppStore.mutate]); the full dataset is pulled on sign-in, app resume and every minute.
 */
object Cloud {
    const val SUPABASE_URL = "https://zfkgfptihjdkhnmprobz.supabase.co"
    // Public "anon" key (also shipped in the website); row-level security protects the data.
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inpma2dmcHRpaGpka2hubXByb2J6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI5NTk2ODQsImV4cCI6MjA4ODUzNTY4NH0.vzU0lFQRQaOhgXCk3EMvKylkCfam-IJO7Zd3EBiMQ2s"
    const val LOGIN_PAGE = "https://progress-pal-02.lovable.app/app-login"

    sealed interface State {
        data object SignedOut : State
        data class SignedIn(val email: String?, val syncing: Boolean = false, val error: String? = null) : State
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private lateinit var prefs: android.content.SharedPreferences

    private val _state = MutableStateFlow<State>(State.SignedOut)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Shown once on first launch; becomes true after sign-in or "use without account". */
    private val _onboarded = MutableStateFlow(true)
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var expiresAt: Long = 0 // epoch seconds
    private var userId: String? = null

    val signedIn get() = refreshToken != null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("cloud", Context.MODE_PRIVATE)
        accessToken = prefs.getString("access", null)
        refreshToken = prefs.getString("refresh", null)
        expiresAt = prefs.getLong("expires", 0)
        userId = prefs.getString("uid", null)
        _onboarded.value = prefs.getBoolean("onboarded", false) || signedIn
        if (signedIn) {
            _state.value = State.SignedIn(prefs.getString("email", null))
            pull()
        }
    }

    fun skipSignIn() {
        prefs.edit().putBoolean("onboarded", true).apply()
        _onboarded.value = true
    }

    fun startSignIn(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LOGIN_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Handles progresspal://auth#access_token=…&refresh_token=…&expires_at=… */
    fun handleRedirect(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "progresspal" || uri.host != "auth") return false
        val params = (uri.encodedFragment ?: uri.encodedQuery ?: "").split("&").mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to Uri.decode(it.substring(i + 1))
        }.toMap()
        val access = params["access_token"] ?: return false
        val refresh = params["refresh_token"] ?: return false
        saveSession(access, refresh, params["expires_at"]?.toLongOrNull() ?: (System.currentTimeMillis() / 1000 + 3000))
        prefs.edit().putBoolean("onboarded", true).apply()
        _onboarded.value = true
        _state.value = State.SignedIn(prefs.getString("email", null), syncing = true)
        firstSync()
        return true
    }

    fun signOut() {
        val token = accessToken
        scope.launch { runCatching { if (token != null) http("POST", "/auth/v1/logout", null, token) } }
        accessToken = null; refreshToken = null; userId = null; expiresAt = 0; base = null
        prefs.edit().remove("access").remove("refresh").remove("expires").remove("uid").remove("email").apply()
        _state.value = State.SignedOut
    }

    private fun saveSession(access: String, refresh: String, expires: Long) {
        accessToken = access; refreshToken = refresh; expiresAt = expires
        val claims = decodeJwt(access)
        userId = claims?.get("sub")?.jsonPrimitive?.contentOrNull ?: userId
        val email = claims?.get("email")?.jsonPrimitive?.contentOrNull
        prefs.edit()
            .putString("access", access).putString("refresh", refresh).putLong("expires", expires)
            .putString("uid", userId).apply { if (email != null) putString("email", email) }
            .apply()
    }

    private fun decodeJwt(token: String): JsonObject? = runCatching {
        val payload = token.split(".")[1]
        val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        json.parseToJsonElement(String(bytes)).jsonObject
    }.getOrNull()

    // ───────────────────────── HTTP ─────────────────────────

    private class HttpError(val code: Int, msg: String) : IOException("HTTP $code: $msg")

    private fun http(method: String, path: String, body: String?, token: String?, extraHeaders: Map<String, String> = emptyMap()): String {
        val conn = URL(SUPABASE_URL + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("apikey", ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${token ?: ANON_KEY}")
            conn.setRequestProperty("Content-Type", "application/json")
            extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().decodeToString() } ?: ""
            if (code !in 200..299) throw HttpError(code, text.take(300))
            return text
        } finally {
            conn.disconnect()
        }
    }

    /** Returns a valid access token, refreshing it when needed. Null = signed out. */
    private fun token(): String? {
        val refresh = refreshToken ?: return null
        if (accessToken != null && System.currentTimeMillis() / 1000 < expiresAt - 60) return accessToken
        return try {
            val res = http("POST", "/auth/v1/token?grant_type=refresh_token", """{"refresh_token":${JsonPrimitive(refresh)}}""", null)
            val o = json.parseToJsonElement(res).jsonObject
            val access = o["access_token"]!!.jsonPrimitive.content
            val newRefresh = o["refresh_token"]?.jsonPrimitive?.contentOrNull ?: refresh
            val exp = o["expires_at"]?.jsonPrimitive?.longOrNull
                ?: (System.currentTimeMillis() / 1000 + (o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600))
            saveSession(access, newRefresh, exp)
            access
        } catch (e: HttpError) {
            if (e.code == 400 || e.code == 401) {
                // Refresh token revoked/expired: sign out but keep local data.
                signOut()
            }
            null
        }
    }

    private fun get(table: String, token: String): JsonArray =
        json.parseToJsonElement(http("GET", "/rest/v1/$table?select=*", null, token)).jsonArray

    private fun upsert(table: String, rows: List<JsonObject>, token: String, onConflict: String? = null) {
        if (rows.isEmpty()) return
        val q = if (onConflict != null) "?on_conflict=$onConflict" else ""
        rows.chunked(200).forEach { chunk ->
            http(
                "POST", "/rest/v1/$table$q", JsonArray(chunk).toString(), token,
                mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
            )
        }
    }

    private fun delete(table: String, ids: Collection<String>, token: String) {
        ids.chunked(100).forEach { chunk ->
            val list = URLEncoder.encode(chunk.joinToString(",", "(", ")"), "UTF-8")
            http("DELETE", "/rest/v1/$table?id=in.$list", null, token, mapOf("Prefer" to "return=minimal"))
        }
    }

    // ───────────────────────── Mapping ─────────────────────────

    private fun JsonObject.str(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() }
    private fun JsonObject.bool(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.booleanOrNull ?: false

    private fun colorOf(s: String?) = ProjectColor.entries.find { it.name.equals(s, true) } ?: ProjectColor.GREEN
    private fun repeatOf(s: String?) = Repeat.entries.find { it.name.equals(s, true) } ?: Repeat.NONE
    private fun statusOf(s: String?) = ChallengeStatus.entries.find { it.name.equals(s, true) } ?: ChallengeStatus.FAILED

    private fun fromCloud(projects: JsonArray, tasks: JsonArray, completions: JsonArray, challenges: JsonArray, cTasks: JsonArray): AppData {
        val tasksByProject = tasks.map { it.jsonObject }.groupBy { it.str("project_id") }
        val ps = projects.map { it.jsonObject }.map { p ->
            Project(
                id = p.str("id")!!, name = p.str("name") ?: "", color = colorOf(p.str("color")),
                createdAt = p.long("created_at") ?: 0, target = p.str("target"), deadline = p.long("deadline"),
                completedAt = p.long("completed_at"),
                tasks = (tasksByProject[p.str("id")] ?: emptyList()).sortedBy { it.long("created_at") }.map { t ->
                    Task(
                        id = t.str("id")!!, title = t.str("title") ?: "", completed = t.bool("completed"),
                        createdAt = t.long("created_at") ?: 0, deadline = t.long("deadline"), repeat = repeatOf(t.str("repeat")),
                        lastResetAt = t.long("last_reset_at"), dueTime = t.str("due_time"),
                        repeatDays = (t["repeat_days"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull },
                        sourceTaskId = t.str("source_task_id"),
                    )
                },
            )
        }.sortedBy { it.createdAt }
        val comp = completions.map { it.jsonObject }
        val cTasksBy = cTasks.map { it.jsonObject }.groupBy { it.str("challenge_id") }
        val chs = challenges.map { it.jsonObject }.map { c ->
            Challenge(
                id = c.str("id")!!, name = c.str("name") ?: "", durationMinutes = (c.long("duration_minutes") ?: 60).toInt(),
                startedAt = c.long("started_at") ?: 0, completedAt = c.long("completed_at"), status = statusOf(c.str("status")),
                pausedAt = c.long("paused_at"), totalPausedMs = c.long("total_paused_ms") ?: 0, createdAt = c.long("created_at") ?: 0,
                tasks = (cTasksBy[c.str("id")] ?: emptyList()).sortedBy { it.long("created_at") }.map { t ->
                    ChallengeTask(
                        id = t.str("id")!!, title = t.str("title") ?: "", completed = t.bool("completed"),
                        timerSeconds = (t.long("timer_seconds") ?: 0).toInt(), startedAt = t.long("started_at"),
                        sourceTaskId = t.str("source_task_id"),
                    )
                },
            )
        }.sortedByDescending { it.createdAt }
        return AppData(
            projects = ps,
            dateCompletions = comp.associate { "${it.str("task_id")}:${it.str("completion_date")}" to it.bool("completed") },
            completions = comp.filter { it.bool("completed") }.map {
                CompletionRecord(it.str("task_id") ?: "", it.str("completion_date") ?: "", true, it.long("created_at") ?: 0)
            },
            challenges = chs,
        )
    }

    private fun Project.row(uid: String) = buildJsonObject {
        put("id", id); put("user_id", uid); put("name", name); put("color", color.name.lowercase())
        put("target", target); put("deadline", deadline); put("completed_at", completedAt); put("created_at", createdAt)
    }

    private fun Task.row(projectId: String) = buildJsonObject {
        put("id", id); put("project_id", projectId); put("title", title); put("completed", completed)
        put("deadline", deadline); put("repeat", repeat.name.lowercase()); put("last_reset_at", lastResetAt)
        put("due_time", dueTime); put("source_task_id", sourceTaskId); put("created_at", createdAt)
        put("repeat_days", repeatDays?.let { days -> buildJsonArray { days.forEach { add(JsonPrimitive(it)) } } } ?: JsonNull)
    }

    private fun Challenge.row(uid: String) = buildJsonObject {
        put("id", id); put("user_id", uid); put("name", name); put("duration_minutes", durationMinutes)
        put("started_at", startedAt); put("completed_at", completedAt); put("status", status.name.lowercase())
        put("paused_at", pausedAt); put("total_paused_ms", totalPausedMs); put("created_at", createdAt)
    }

    private fun ChallengeTask.row(challengeId: String) = buildJsonObject {
        put("id", id); put("challenge_id", challengeId); put("title", title); put("completed", completed)
        put("timer_seconds", timerSeconds); put("started_at", startedAt); put("source_task_id", sourceTaskId)
    }

    /** (taskId, date) → (completed, createdAt) as it should be stored in task_completions. */
    private fun completionMap(d: AppData): Map<Pair<String, String>, Pair<Boolean, Long>> {
        val m = HashMap<Pair<String, String>, Pair<Boolean, Long>>()
        d.dateCompletions.forEach { (k, v) -> m[k.substringBefore(':') to k.substringAfter(':')] = v to 0L }
        d.completions.forEach { r -> m[r.taskId to r.date] = true to r.createdAt }
        return m
    }

    // ───────────────────────── Push (diff) ─────────────────────────

    /** Called by [AppStore] after every local change. */
    fun onLocalChange(old: AppData, new: AppData) {
        if (!signedIn || old === new) return
        if (base == null) base = old
        scope.launch { lock.withLock { pushPending() } }
    }

    /** Last state known to be saved in the cloud; local edits since then are pushed as a diff. */
    @Volatile private var base: AppData? = null

    /** Pushes everything changed since [base]. On failure (e.g. offline) the diff is kept for next time. */
    private fun pushPending(): Boolean {
        val b = base ?: return true
        val current = AppStore.data.value
        if (b === current) return true
        return runCatching { push(b, current) }
            .onSuccess { base = current }
            .onFailure { markError(it) }
            .isSuccess
    }

    private fun push(old: AppData, new: AppData) {
        val token = token() ?: return
        val uid = userId ?: return

        val oldP = old.projects.associateBy { it.id }
        val newP = new.projects.associateBy { it.id }
        val oldT = old.projects.flatMap { p -> p.tasks.map { it.id to (p.id to it) } }.toMap()
        val newT = new.projects.flatMap { p -> p.tasks.map { it.id to (p.id to it) } }.toMap()
        val oldC = old.challenges.associateBy { it.id }
        val newC = new.challenges.associateBy { it.id }
        val oldCT = old.challenges.flatMap { c -> c.tasks.map { it.id to (c.id to it) } }.toMap()
        val newCT = new.challenges.flatMap { c -> c.tasks.map { it.id to (c.id to it) } }.toMap()
        val oldM = completionMap(old)
        val newM = completionMap(new)

        // Upserts parent → child.
        upsert("projects", newP.values.filter { oldP[it.id]?.copy(tasks = emptyList()) != it.copy(tasks = emptyList()) }.map { it.row(uid) }, token)
        upsert("tasks", newT.filter { (id, v) -> oldT[id] != v }.map { (_, v) -> v.second.row(v.first) }, token)
        upsert("challenges", newC.values.filter { oldC[it.id]?.copy(tasks = emptyList()) != it.copy(tasks = emptyList()) }.map { it.row(uid) }, token)
        upsert("challenge_tasks", newCT.filter { (id, v) -> oldCT[id] != v }.map { (_, v) -> v.second.row(v.first) }, token)
        val now = System.currentTimeMillis()
        upsert(
            "task_completions",
            (newM.keys + oldM.keys).filter { k -> k.first in newT && (newM[k]?.first ?: false) != (oldM[k]?.first ?: false) }.map { k ->
                buildJsonObject {
                    put("task_id", k.first); put("completion_date", k.second); put("completed", newM[k]?.first ?: false)
                    put("created_at", newM[k]?.second?.takeIf { it > 0 } ?: now)
                }
            },
            token, onConflict = "task_id,completion_date",
        )

        // Deletes child → parent.
        val goneTasks = oldT.keys - newT.keys
        if (goneTasks.isNotEmpty()) {
            goneTasks.chunked(100).forEach { chunk ->
                val list = URLEncoder.encode(chunk.joinToString(",", "(", ")"), "UTF-8")
                runCatching { http("DELETE", "/rest/v1/task_completions?task_id=in.$list", null, token, mapOf("Prefer" to "return=minimal")) }
            }
            delete("tasks", goneTasks, token)
        }
        delete("projects", oldP.keys - newP.keys, token)
        delete("challenge_tasks", oldCT.keys - newCT.keys, token)
        delete("challenges", oldC.keys - newC.keys, token)
        clearError()
    }

    // ───────────────────────── Pull ─────────────────────────

    private fun fetchAll(token: String): AppData = fromCloud(
        get("projects", token), get("tasks", token), get("task_completions", token),
        get("challenges", token), get("challenge_tasks", token),
    )

    /** Replaces local data with the cloud copy (after any pending pushes finish). */
    fun pull() {
        if (!signedIn) return
        scope.launch {
            lock.withLock {
                setSyncing(true)
                runCatching {
                    if (!pushPending()) return@runCatching // don't overwrite unsent edits
                    val token = token() ?: return@runCatching
                    val fresh = fetchAll(token)
                    AppStore.replaceFromCloud(fresh)
                    base = fresh
                    clearError()
                }.onFailure { markError(it) }
                setSyncing(false)
            }
        }
    }

    /** After signing in: keep both the cloud data and anything made on this phone before signing in. */
    private fun firstSync() {
        scope.launch {
            lock.withLock {
                setSyncing(true)
                runCatching {
                    val token = token() ?: return@runCatching
                    val cloud = fetchAll(token)
                    val local = AppStore.data.value
                    val cloudProjectIds = cloud.projects.map { it.id }.toSet()
                    val cloudChallengeIds = cloud.challenges.map { it.id }.toSet()
                    val merged = cloud.copy(
                        projects = cloud.projects + local.projects.filter { it.id !in cloudProjectIds },
                        challenges = local.challenges.filter { it.id !in cloudChallengeIds } + cloud.challenges,
                        dateCompletions = local.dateCompletions + cloud.dateCompletions,
                        completions = (cloud.completions + local.completions).distinctBy { it.taskId to it.date },
                    )
                    push(cloud, merged)
                    AppStore.replaceFromCloud(merged)
                    base = merged
                    clearError()
                }.onFailure { markError(it) }
                setSyncing(false)
            }
        }
    }

    private fun setSyncing(on: Boolean) {
        (_state.value as? State.SignedIn)?.let { _state.value = it.copy(email = prefs.getString("email", null), syncing = on) }
    }

    private fun markError(e: Throwable) {
        (_state.value as? State.SignedIn)?.let {
            _state.value = it.copy(error = if (e is IOException && e !is HttpError) "Offline — will sync when back online" else "Sync problem: ${e.message?.take(120)}")
        }
    }

    private fun clearError() {
        (_state.value as? State.SignedIn)?.let { if (it.error != null) _state.value = it.copy(error = null) }
    }
}
