package com.progresspal.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** One MCQ inside an exam, with the answer given (if any). */
@Serializable
data class OQuestion(
    val text: String,
    val a: String,
    val b: String,
    val c: String,
    val d: String,
    /** "A".."D" */
    val correct: String,
    val explanation: String = "",
    val topic: String = "",
    val hash: String,
    val userAnswer: String? = null,
    val isCorrect: Boolean? = null,
    val answeredAt: Long? = null,
    val timesShown: Int = 1,
) {
    fun option(k: String) = when (k) { "A" -> a; "B" -> b; "C" -> c; else -> d }
}

@Serializable
data class OExam(
    val id: String = UUID.randomUUID().toString(),
    val number: Int,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val questions: List<OQuestion> = emptyList(),
    val timeTakenS: Int? = null,
) {
    val completed get() = completedAt != null
    val correctCount get() = questions.count { it.isCorrect == true }
    val wrongCount get() = questions.count { it.isCorrect == false }
    val scorePct get() = if (questions.isEmpty()) 0 else correctCount * 100 / questions.size
}

@Serializable
data class OrthoData(
    val exams: List<OExam> = emptyList(),
    val dailyGoal: Int = 50,
    /** Selected chapter ids; null = all chapters. */
    val chapters: List<String>? = null,
    val bestStreak: Int = 0,
    val celebrated: List<String> = emptyList(),
)

data class OChapter(val id: String, val name: String, val apley: Boolean) {
    val label get() = "${if (apley) "Apley's" else "Campbell's"}: $name"
}

data class ORank(val title: String, val target: Int, val emoji: String)

/** Career ranks unlocked by total questions solved. */
val ORTHO_RANKS = listOf(
    ORank("Orthopaedic Intern", 1000, "🩺"), ORank("Orthopaedic Trainee", 1500, "📚"),
    ORank("Orthopaedic Resident Phase A", 2000, "🔬"), ORank("Orthopaedic Resident Phase B", 3000, "🦴"),
    ORank("MS (Orthopaedics)", 5000, "🎓"), ORank("FCPS Part 2 (Thesis)", 7000, "📝"),
    ORank("FCPS (Orthopaedics)", 9000, "🏅"), ORank("Junior Consultant", 10000, "👨‍⚕️"),
    ORank("Senior Consultant", 12000, "🏆"), ORank("Assistant Professor", 15000, "📖"),
    ORank("Associate Professor", 17000, "🌟"), ORank("Professor", 20000, "👑"),
)

private val APLEY = listOf(
    "Diagnosis in Orthopaedics", "Infection", "Inflammatory Rheumatic Disorders", "Crystal Deposition Disorders",
    "Osteoarthritis", "Metabolic & Endocrine Disorders of Bone", "Genetic Disorders, Skeletal Dysplasias & Malformations",
    "Tumours", "Peripheral Nerve Lesions", "Principles of Fractures", "Injuries of the Shoulder, Upper Arm & Elbow",
    "Injuries of the Forearm & Wrist", "Injuries of the Hand", "Injuries of the Spine", "Injuries of the Pelvis",
    "Injuries of the Hip & Femur", "Injuries of the Knee & Leg", "Injuries of the Ankle & Foot",
    "The Shoulder & Pectoral Girdle", "The Elbow", "The Wrist", "The Hand", "The Neck", "The Back", "The Hip",
    "The Knee", "The Ankle & Foot", "Neuromuscular Disorders", "Paediatric Orthopaedics",
)
private val CAMPBELL = listOf(
    "Surgical Techniques & Approaches", "Fractures of the Lower Extremity", "Fractures of the Upper Extremity",
    "Fractures & Dislocations in Children", "Malunions & Nonunions", "Arthroplasty — Hip", "Arthroplasty — Knee",
    "Arthroplasty — Shoulder & Elbow", "Arthroscopy", "Sports Medicine — Knee", "Sports Medicine — Shoulder",
    "Spine — General Principles", "Spine — Cervical", "Spine — Thoracolumbar", "Spine — Deformity",
    "Hand, Upper Extremity & Microsurgery", "Foot & Ankle", "Congenital & Developmental Disorders",
    "Neuromuscular Disorders", "Musculoskeletal Tumours", "Osteotomy", "Soft Tissue Procedures", "Amputations",
    "Infections", "Peripheral Nerve Injuries",
)

/** Apley & Solomon's (10th ed.) and Campbell's Operative Orthopaedics (14th ed.) chapters. */
val ORTHO_CHAPTERS: List<OChapter> =
    APLEY.mapIndexed { i, n -> OChapter("apley-${i + 1}", "${i + 1}. $n", true) } +
        CAMPBELL.mapIndexed { i, n -> OChapter("campbell-${i + 1}", "${i + 1}. $n", false) }

/**
 * Orthopaedic Genius (OrthoPrep): AI-generated FRCS / MS / FCPS standard MCQ exams. Questions come
 * from the app's own question generator online; exams, answers and stats are kept on the phone.
 */
object OrthoExam {
    const val EXAM_SECONDS = 600
    const val EXAM_SIZE = 10
    private const val KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imlna2tjcW9zZXdnYnBsaXl6bnpvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzE2NjA3MjAsImV4cCI6MjA4NzIzNjcyMH0.lDUoWwn01QEu6-dBof4GRes5QQsxcEz5RVFVk4mpY90"
    private val ENDPOINTS = listOf(
        "https://igkkcqosewgbpliyznzo.supabase.co/functions/v1/generate-questions",
        "https://igkkcqosewgbpliyznzo.lovable.cloud/functions/v1/generate-questions",
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var file: File
    private val _data = MutableStateFlow(OrthoData())
    val data: StateFlow<OrthoData> = _data

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.applicationContext.filesDir, "ortho_exam.json")
        if (file.exists()) runCatching { _data.value = json.decodeFromString<OrthoData>(file.readText()) }
    }

    @Synchronized
    private fun update(f: (OrthoData) -> OrthoData) {
        val next = f(_data.value)
        _data.value = next
        if (::file.isInitialized) runCatching { file.writeText(json.encodeToString(next)) }
    }

    fun exam(id: String) = _data.value.exams.firstOrNull { it.id == id }

    fun setGoal(goal: Int) = update { it.copy(dailyGoal = goal.coerceAtLeast(10)) }
    fun setChapters(ids: Collection<String>) = update { d ->
        d.copy(chapters = if (ids.size == ORTHO_CHAPTERS.size) null else ORTHO_CHAPTERS.map { it.id }.filter { it in ids })
    }
    fun selectedChapters(d: OrthoData = _data.value): Set<String> = (d.chapters ?: ORTHO_CHAPTERS.map { it.id }).toSet()
    fun markCelebrated(key: String) = update { it.copy(celebrated = it.celebrated + key) }
    fun deleteExam(id: String) = update { d -> d.copy(exams = d.exams.filterNot { it.id == id }) }

    // ---- Stats ----

    fun answered(d: OrthoData = _data.value) = d.exams.flatMap { e -> e.questions.filter { it.answeredAt != null } }

    private fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Questions solved per day. */
    fun daily(d: OrthoData = _data.value): Map<LocalDate, Int> = answered(d).groupingBy { dayOf(it.answeredAt!!) }.eachCount()

    fun todaySolved(d: OrthoData = _data.value) = daily(d)[LocalDate.now()] ?: 0

    /** Days in a row (ending today, or yesterday if today's goal isn't met yet) that met the goal. */
    fun streak(d: OrthoData = _data.value): Int {
        val met = daily(d).filterValues { it >= d.dailyGoal }.keys
        var day = LocalDate.now()
        if (day !in met) day = day.minusDays(1)
        var n = 0
        while (day in met) { n++; day = day.minusDays(1) }
        return n
    }

    fun recordBestStreak(s: Int) { if (s > _data.value.bestStreak) update { it.copy(bestStreak = s) } }

    data class Weak(val topic: String, val total: Int, val wrong: Int) { val pct get() = wrong * 100 / total.coerceAtLeast(1) }

    fun weakTopics(d: OrthoData = _data.value): List<Weak> =
        answered(d).groupBy { it.topic.ifBlank { "General" } }
            .map { (t, qs) -> Weak(t, qs.size, qs.count { it.isCorrect == false }) }
            .filter { it.wrong > 0 }.sortedByDescending { it.pct }

    // ---- Exams ----

    /** The exam in progress whose time hasn't run out; ones that ran out are closed. */
    fun openExam(): OExam? {
        val now = System.currentTimeMillis()
        _data.value.exams.filter { !it.completed && now - it.startedAt >= EXAM_SECONDS * 1000L }.forEach { complete(it.id) }
        return _data.value.exams.firstOrNull { !it.completed }
    }

    /** Asks the question generator for a new exam: fresh questions mixed with up to 3 earlier wrong ones. */
    suspend fun generate(): OExam {
        val d = _data.value
        val all = answered(d)
        // Hashes answered correctly or shown 3+ times are not asked again.
        val latest = all.groupBy { it.hash }.mapValues { (_, qs) -> qs.maxBy { it.timesShown } }
        val exclude = latest.filterValues { it.isCorrect == true || it.timesShown >= 3 }.keys
        val wrong = all.filter { it.isCorrect == false && it.timesShown < 3 && it.hash !in exclude }
            .distinctBy { it.hash }.shuffled().take(3)
        val chapters = selectedChapters(d)
        val chapterNames = if (chapters.size == ORTHO_CHAPTERS.size) ORTHO_CHAPTERS.map { it.label }
        else ORTHO_CHAPTERS.filter { it.id in chapters }.map { it.label }

        val body = buildJsonObject {
            put("count", EXAM_SIZE)
            put("excludeTopics", JsonArray(exclude.take(50).map { JsonPrimitive(it) }))
            put("previousWrongQuestions", buildJsonArray {
                wrong.forEach { q ->
                    add(buildJsonObject {
                        put("question_text", q.text); put("option_a", q.a); put("option_b", q.b)
                        put("option_c", q.c); put("option_d", q.d); put("correct_answer", q.correct)
                        put("explanation", q.explanation); put("topic", q.topic); put("question_hash", q.hash)
                    })
                }
            })
            put("selectedChapters", JsonArray(chapterNames.map { JsonPrimitive(it) }))
        }.toString()

        val text = post(body)
        val root = json.parseToJsonElement(text).jsonObject
        val qs = root["questions"]?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull.orEmpty()
            val qt = s("question_text"); if (qt.isBlank()) return@mapNotNull null
            OQuestion(
                text = qt, a = s("option_a"), b = s("option_b"), c = s("option_c"), d = s("option_d"),
                correct = s("correct_answer").trim().uppercase().take(1).ifBlank { "A" },
                explanation = s("explanation"), topic = s("topic"),
                hash = s("question_hash").ifBlank { hashOf(qt) },
            )
        }.orEmpty()
        if (qs.isEmpty()) throw IOException(root["error"]?.jsonPrimitive?.contentOrNull ?: "No questions returned")

        val exam = OExam(number = (d.exams.maxOfOrNull { it.number } ?: 0) + 1, questions = qs)
        update { it.copy(exams = listOf(exam) + it.exams) }
        return exam
    }

    /** Records the answer to question [index]; returns true when it was right. */
    fun answer(examId: String, index: Int, choice: String): Boolean {
        var right = false
        update { d ->
            val shownBefore = answered(d).groupBy { it.hash }.mapValues { (_, qs) -> qs.maxOf { it.timesShown } }
            d.copy(exams = d.exams.map { e ->
                if (e.id != examId) e else e.copy(questions = e.questions.mapIndexed { i, q ->
                    if (i != index || q.userAnswer != null) q else {
                        right = choice == q.correct
                        q.copy(userAnswer = choice, isCorrect = right, answeredAt = System.currentTimeMillis(), timesShown = (shownBefore[q.hash] ?: 0) + 1)
                    }
                })
            })
        }
        return right
    }

    fun complete(examId: String) = update { d ->
        d.copy(exams = d.exams.map { e ->
            if (e.id != examId || e.completed) e else {
                val now = System.currentTimeMillis()
                e.copy(completedAt = now, timeTakenS = ((now - e.startedAt) / 1000).toInt().coerceAtMost(EXAM_SECONDS))
            }
        })
    }

    private fun hashOf(text: String) =
        Base64.encodeToString(text.take(50).toByteArray(), Base64.NO_WRAP).replace(Regex("[^a-zA-Z0-9]"), "")

    private suspend fun post(body: String): String = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (u in ENDPOINTS) {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 20_000
                c.readTimeout = 150_000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("apikey", KEY)
                c.setRequestProperty("Authorization", "Bearer $KEY")
                c.outputStream.use { it.write(body.toByteArray()) }
                val code = c.responseCode
                val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                c.disconnect()
                if (code in 200..299) return@withContext txt
                val err = runCatching { json.parseToJsonElement(txt).jsonObject["error"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                last = IOException(err ?: "Question server error $code")
                if (code == 429 || code == 402) throw last
            } catch (e: IOException) {
                last = e
                if (e.message?.contains("Rate limited") == true || e.message?.contains("credits") == true) break
            }
        }
        throw last ?: IOException("Could not reach the question server")
    }
}
