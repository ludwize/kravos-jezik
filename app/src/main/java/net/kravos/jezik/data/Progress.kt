package net.kravos.jezik.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Spaced-repetition state of one word (Leitner boxes). */
data class Srs(val box: Int, val due: Long, val seen: Int, val wrong: Int)

/**
 * App-wide settings (shared by all languages). Kept in the original "progress" file,
 * where v1 already stored them, so nothing needs migrating.
 */
class Settings(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(runCatching { ThemeMode.valueOf(sp.getString("theme", "DARK")!!) }.getOrDefault(ThemeMode.DARK))
    val theme: StateFlow<ThemeMode> = _theme
    fun setTheme(m: ThemeMode) { sp.edit().putString("theme", m.name).apply(); _theme.value = m }

    var lang: String
        get() = sp.getString("lang", "sl")!!
        set(v) = sp.edit().putString("lang", v).apply()
    var soundOn: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit().putBoolean("sound", v).apply()
    var listenOn: Boolean
        get() = sp.getBoolean("listen", true)
        set(v) = sp.edit().putBoolean("listen", v).apply()
    var speakOn: Boolean
        get() = sp.getBoolean("speak", true)
        set(v) = sp.edit().putBoolean("speak", v).apply()
    var autoUpdateCheck: Boolean
        get() = sp.getBoolean("autoUpdate", true)
        set(v) = sp.edit().putBoolean("autoUpdate", v).apply()
    var skippedBuild: Int
        get() = sp.getInt("skippedBuild", 0)
        set(v) = sp.edit().putInt("skippedBuild", v).apply()
    var speakSnoozeUntil: Long
        get() = sp.getLong("speakSnooze", 0)
        set(v) = sp.edit().putLong("speakSnooze", v).apply()
    var listenSnoozeUntil: Long
        get() = sp.getLong("listenSnooze", 0)
        set(v) = sp.edit().putLong("listenSnooze", v).apply()
}

/**
 * Learner state of ONE language course. Slovenian keeps the v1 file ("progress"), other
 * languages get "progress_<lang>". Every change bumps [rev] so screens recompose.
 */
class Progress(context: Context, private val course: Course, private val settings: Settings) {
    private val sp = if (course.lang == "sl") settings.sp else context.getSharedPreferences("progress_${course.lang}", Context.MODE_PRIVATE)

    private val _rev = MutableStateFlow(0)
    val rev: StateFlow<Int> = _rev
    private fun bump() { _rev.value = _rev.value + 1 }

    private val done: MutableMap<String, Int> = loadMap("done")
    private val xp: MutableMap<String, Int> = loadMap("xp")
    private val srs: MutableMap<String, Srs> = loadSrs()

    init { migrateIds() }

    // ---- shared settings (delegated so screens can keep using app.progress.*)
    val theme: StateFlow<ThemeMode> get() = settings.theme
    fun setTheme(m: ThemeMode) = settings.setTheme(m)
    var soundOn: Boolean
        get() = settings.soundOn
        set(v) { settings.soundOn = v; bump() }
    var listenOn: Boolean
        get() = settings.listenOn
        set(v) { settings.listenOn = v; bump() }
    var speakOn: Boolean
        get() = settings.speakOn
        set(v) { settings.speakOn = v; bump() }
    var autoUpdateCheck: Boolean
        get() = settings.autoUpdateCheck
        set(v) { settings.autoUpdateCheck = v }
    var skippedBuild: Int
        get() = settings.skippedBuild
        set(v) { settings.skippedBuild = v }
    var speakSnoozeUntil: Long
        get() = settings.speakSnoozeUntil
        set(v) { settings.speakSnoozeUntil = v }
    var listenSnoozeUntil: Long
        get() = settings.listenSnoozeUntil
        set(v) { settings.listenSnoozeUntil = v }

    fun speakActive() = speakOn && System.currentTimeMillis() > speakSnoozeUntil
    fun listenActive() = listenOn && soundOn && System.currentTimeMillis() > listenSnoozeUntil

    // ---- per-language settings
    var dailyGoal: Int
        get() = sp.getInt("goal", 20)
        set(v) { sp.edit().putInt("goal", v).apply(); bump() }
    var unlockAll: Boolean
        get() = sp.getBoolean("unlockAll", false)
        set(v) { sp.edit().putBoolean("unlockAll", v).apply(); bump() }

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

    fun lessonsDone(): Int = course.lessons.count { isDone(it.id) }
    fun started(): Boolean = done.isNotEmpty() || xp.isNotEmpty()

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

    /**
     * v1 lesson ids were "u<unit>-<slug>"; since 1.0.4 they are "<slug>" (lessons) and "p-<slug>" (stories),
     * so inserting new units does not shift them.
     */
    private fun migrateIds() {
        val old = done.keys.filter { Regex("^u\\d+-.+").matches(it) }
        if (old.isEmpty()) return
        old.forEach { k ->
            val slug = k.substringAfter('-')
            val target = when {
                course.lesson(slug) != null -> slug
                course.lesson("p-$slug") != null -> "p-$slug"
                else -> null
            }
            val n = done.remove(k) ?: 0
            if (target != null) done[target] = maxOf(n, done[target] ?: 0)
        }
        saveAll()
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
        srs.forEach { (k, v) -> s.put(k, JSONArray().put(v.box).put(v.due).put(v.seen).put(v.wrong)) }
        sp.edit()
            .putString("done", JSONObject(done as Map<*, *>).toString())
            .putString("xp", JSONObject(xp as Map<*, *>).toString())
            .putString("srs", s.toString())
            .apply()
    }
}
