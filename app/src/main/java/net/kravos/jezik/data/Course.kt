package net.kravos.jezik.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Word(val sl: String, val sr: List<String>, val note: String, val lessonId: String, val unitIdx: Int) {
    val key: String get() = sl.lowercase()
    val meaning: String get() = sr.joinToString(", ")
}

data class Sentence(val sl: List<String>, val sr: List<String>, val lessonId: String) {
    val text: String get() = sl.first()
    val meaning: String get() = sr.first()
}

data class Grammar(val prompt: String, val sr: String, val answer: String, val wrong: List<String>, val full: String)

sealed class StoryItem {
    data class Line(val who: String, val sl: String, val sr: String) : StoryItem()
    data class Question(val q: String, val answer: String, val wrong: List<String>) : StoryItem()
}

enum class LessonType { LESSON, STORY }

data class Lesson(
    val id: String,
    val type: LessonType,
    val title: String,
    val tip: String,
    val words: List<Word>,
    val sentences: List<Sentence>,
    val grammar: List<Grammar>,
    val story: List<StoryItem>,
    val unitIdx: Int,
    /** Position in the whole course (0-based). */
    val order: Int,
)

data class CourseUnit(val id: String, val idx: Int, val title: String, val desc: String, val guide: List<String>, val lessons: List<Lesson>)

class Course(
    val lang: String,
    val name: String,
    val native: String,
    val flag: String,
    val tts: String,
    /** "na slovenačkom" / "na španskom". */
    val loc: String,
    /** "na slovenački" / "na španski". */
    val acc: String,
    /** Special letters offered as buttons when typing. */
    val chars: List<String>,
    /** Name of the voice used for the bundled recordings. */
    val voice: String,
    val units: List<CourseUnit>,
) {
    val lessons: List<Lesson> = units.flatMap { it.lessons }
    val words: List<Word> = lessons.flatMap { it.words }.distinctBy { it.key }
    val sentences: List<Sentence> = lessons.flatMap { it.sentences }
    private val byId = lessons.associateBy { it.id }
    private val wordByKey = words.associateBy { it.key }

    fun lesson(id: String): Lesson? = byId[id]
    fun word(key: String): Word? = wordByKey[key]

    /** Words of lessons up to and including [order]: the distractor pool for a lesson. */
    fun wordsUpTo(order: Int): List<Word> = lessons.filter { it.order <= order }.flatMap { it.words }.distinctBy { it.key }

    fun sentencesUpTo(order: Int): List<Sentence> = lessons.filter { it.order <= order }.flatMap { it.sentences }

    companion object {
        fun load(ctx: Context, lang: String): Course {
            val o = JSONObject(ctx.assets.open("courses/$lang.json").bufferedReader().use { it.readText() })
            var order = 0
            val units = o.getJSONArray("units").objs().mapIndexed { ui, u ->
                val lessons = u.getJSONArray("lessons").objs().map { l ->
                    val id = l.getString("id")
                    val type = if (l.getString("type") == "story") LessonType.STORY else LessonType.LESSON
                    Lesson(
                        id = id,
                        type = type,
                        title = l.getString("title"),
                        tip = l.optString("tip"),
                        words = l.optJSONArray("words").objs().map { Word(it.getString("sl"), it.getJSONArray("sr").strs(), it.optString("note"), id, ui) },
                        sentences = l.optJSONArray("sentences").objs().map { Sentence(it.getJSONArray("sl").strs(), it.getJSONArray("sr").strs(), id) },
                        grammar = l.optJSONArray("grammar").objs().map {
                            Grammar(it.getString("prompt"), it.getString("sr"), it.getString("answer"), it.getJSONArray("wrong").strs(), it.getString("full"))
                        },
                        story = l.optJSONArray("lines").objs().map {
                            if (it.has("q")) StoryItem.Question(it.getString("q"), it.getString("answer"), it.getJSONArray("wrong").strs())
                            else StoryItem.Line(it.getString("who"), it.getString("sl"), it.getString("sr"))
                        },
                        unitIdx = ui,
                        order = order++,
                    )
                }
                CourseUnit(u.getString("id"), ui, u.getString("title"), u.optString("desc"), u.optJSONArray("guide").strs(), lessons)
            }
            return Course(
                o.getString("lang"), o.getString("name"), o.optString("native"), o.optString("flag"), o.optString("tts", "sl-SI"),
                o.optString("loc", o.getString("name").lowercase()), o.optString("acc", o.getString("name").lowercase()),
                o.optJSONArray("chars").strs(), o.optString("voice"), units,
            )
        }

        /** Language codes of the bundled courses, in display order. */
        fun available(ctx: Context): List<String> {
            val all = runCatching { ctx.assets.list("courses")?.filter { it.endsWith(".json") }?.map { it.removeSuffix(".json") } }.getOrNull().orEmpty()
            val order = listOf("sl", "es")
            return all.sortedBy { order.indexOf(it).let { i -> if (i < 0) 99 else i } }
        }
    }
}

internal fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
internal fun JSONArray?.strs(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
