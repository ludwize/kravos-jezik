package net.kravos.jezik.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.time.LocalDate

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Spaced-repetition state of one word (Leitner boxes). */
data class Srs(val box: Int, val due: Long, val seen: Int, val wrong: Int)

/**
 * All learner state, in SharedPreferences. Every change bumps [rev] so screens recompose.
 */
class Progress(context: Context) {
    private val sp = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    private val _rev = MutableStateFlow(0)
    val rev: StateFlow<Int> = _rev
    private fun bump() { _rev.value = _rev.value + 1 }

    private val done: MutableMap<String, Int> = loadMap("done")
    private val xp: MutableMap<String, Int> = loadMap("xp")
    private val srs: MutableMap<String, Srs> = loadSrs()

    private val _theme = MutableStateFlow(runCatching { ThemeMode.valueOf(sp.getString("theme", "DARK")!!) }.getOrDefault(ThemeMode.DARK))
    val theme: StateFlow<ThemeMode> = _theme
    fun setTheme(m: ThemeMode) { sp.edit().putString("theme", m.name).apply(); _theme.value = m }

    // ---- settings
    var dailyGoal: Int
        get() = sp.getInt("goal", 20)
        set(v) { sp.edit().putInt("goal", v).apply(); bump() }
    var soundOn: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) { sp.edit().putBoolean("sound", v).apply(); bump() }
    var listenOn: Boolean
        get() = sp.getBoolean("listen", true)
        set(v) { sp.edit().putBoolean("listen", v).apply(); bump() }
    var speakOn: Boolean
        get() = sp.getBoolean("speak", true)
        set(v) { sp.edit().putBoolean("speak", v).apply(); bump() }
    var unlockAll: Boolean
        get() = sp.getBoolean("unlockAll", false)
        set(v) { sp.edit().putBoolean("unlockAll", v).apply(); bump() }
    var autoUpdateCheck: Boolean
        get() = sp.getBoolean("autoUpdate", true)
        set(v) = sp.edit().putBoolean("autoUpdate", v).apply()
    var skippedBuild: Int
        get() = sp.getInt("skippedBuild", 0)
        set(v) = sp.edit().putInt("skippedBuild", v).apply()
    /** "Ne mogu sada da govorim / slušam" — pauses those exercises for an hour. */
    var speakSnoozeUntil: Long
        get() = sp.getLong("speakSnooze", 0)
        set(v) = sp.edit().putLong("speakSnooze", v).apply()
    var listenSnoozeUntil: Long
        get() = sp.getLong("listenSnooze", 0)
        set(v) = sp.edit().putLong("listenSnooze", v).apply()

    fun speakActive() = speakOn && System.currentTimeMillis() > speakSnoozeUntil
    fun listenActive() = listenOn && soundOn && System.currentTimeMillis() > listenSnoozeUntil

    // ---- lessons
    fun timesDone(id: String): Int = done[id] ?: 0
    fun isDone(id: String) = timesDone(id) > 0

    fun isUnlocked(course: Course, l: Lesson): Boolean {
        if (unlockAll || l.order == 0) return true
        val prev = course.lessons.getOrNull(l.order - 1) ?: return true
        return isDone(prev.id) || isDone(l.id)
    }

    /** First lesson not done yet (the "current" node on the path). */
    fun current(course: Course): Lesson? = course.lessons.firstOrNull { !isDone(it.id) }

    // ---- xp & streak
    private fun today() = LocalDate.now().toEpochDay()
    fun xpOn(day: Long): Int = xp[day.toString()] ?: 0
    fun todayXp() = xpOn(today())
    fun totalXp() = xp.values.sum()

    fun streak(): Int {
        var d = today()
        if (xpOn(d) == 0) d -= 1
        var n = 0
        while (xpOn(d) > 0) { n++; d-- }
        return n
    }

    fun streakAliveToday() = xpOn(today()) > 0

    /** Records a finished lesson/review/story and the per-word results. */
    fun finish(lessonId: String?, gained: Int, results: Map<String, Boolean>) {
        val t = today()
        xp[t.toString()] = xpOn(t) + gained
        if (lessonId != null) done[lessonId] = timesDone(lessonId) + 1
        results.forEach { (k, ok) -> grade(k, ok, t) }
        saveAll()
        bump()
    }

    // ---- spaced repetition
    private val intervals = longArrayOf(0, 1, 2, 4, 8, 16, 35, 70)

    private fun grade(key: String, ok: Boolean, today: Long) {
        val cur = srs[key] ?: Srs(0, today, 0, 0)
        srs[key] = if (ok) {
            val box = (cur.box + 1).coerceAtMost(intervals.size - 1)
            Srs(box, today + intervals[box], cur.seen + 1, cur.wrong)
        } else {
            Srs(1, today, cur.seen + 1, cur.wrong + 1)
        }
    }

    fun srsOf(key: String): Srs? = srs[key]
    fun learnedCount() = srs.size
    fun isLearned(key: String) = srs.containsKey(key)

    fun due(course: Course): List<Word> {
        val t = today()
        return srs.filter { it.value.due <= t }.entries
            .sortedWith(compareBy({ it.value.due }, { it.value.box }))
            .mapNotNull { course.word(it.key) }
    }

    /** Learned words, weakest first (for practice when nothing is due). */
    fun weakest(course: Course): List<Word> =
        srs.entries.sortedWith(compareBy({ it.value.box }, { -it.value.wrong })).mapNotNull { course.word(it.key) }

    fun reset() {
        done.clear(); xp.clear(); srs.clear()
        saveAll(); bump()
    }

    // ---- persistence
    private fun loadMap(name: String): MutableMap<String, Int> = runCatching {
        val o = JSONObject(sp.getString(name, "{}")!!)
        o.keys().asSequence().associateWith { o.getInt(it) }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun loadSrs(): MutableMap<String, Srs> = runCatching {
        val o = JSONObject(sp.getString("srs", "{}")!!)
        o.keys().asSequence().associateWith {
            val a = o.getJSONArray(it)
            Srs(a.getInt(0), a.getLong(1), a.getInt(2), a.getInt(3))
        }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun saveAll() {
        val s = JSONObject()
        srs.forEach { (k, v) -> s.put(k, org.json.JSONArray().put(v.box).put(v.due).put(v.seen).put(v.wrong)) }
        sp.edit()
            .putString("done", JSONObject(done as Map<*, *>).toString())
            .putString("xp", JSONObject(xp as Map<*, *>).toString())
            .putString("srs", s.toString())
            .apply()
    }
}
