package net.kravos.jezik.data

import kotlin.random.Random

/** One screen of a lesson. [sl] is the Slovenian text to play after answering, [word] is graded for SRS. */
sealed class Ex {
    abstract val uid: Int
    abstract val word: Word?
    abstract val sl: String?

    /** New word card. */
    data class Intro(override val uid: Int, override val word: Word) : Ex() {
        override val sl get() = word.sl
    }

    /** Choose the translation. [listen] hides the prompt and only plays it. */
    data class Pick(
        override val uid: Int,
        val title: String,
        val prompt: String,
        val options: List<String>,
        val answer: String,
        val promptIsSl: Boolean,
        val listen: Boolean,
        override val word: Word?,
        override val sl: String?,
    ) : Ex()

    /** Tap pairs. */
    data class Match(override val uid: Int, val pairs: List<Pair<String, String>>, val words: List<Word>) : Ex() {
        override val word: Word? get() = null
        override val sl: String? get() = null
    }

    /** Build a sentence from word tiles. */
    data class Arrange(
        override val uid: Int,
        val title: String,
        val prompt: String,
        val tiles: List<String>,
        val accepted: List<String>,
        val promptIsSl: Boolean,
        val listen: Boolean,
        override val sl: String?,
        val meaning: String,
    ) : Ex() {
        override val word: Word? get() = null
    }

    /** Type the answer. */
    data class Type(
        override val uid: Int,
        val title: String,
        val prompt: String,
        val accepted: List<String>,
        val answerIsSl: Boolean,
        val listen: Boolean,
        override val word: Word?,
        override val sl: String?,
        val meaning: String,
    ) : Ex()

    /** Say the sentence out loud. */
    data class Speak(override val uid: Int, val text: String, val meaning: String) : Ex() {
        override val word: Word? get() = null
        override val sl: String? get() = text
    }

    /** Fill the gap (grammar). */
    data class Fill(override val uid: Int, val g: Grammar, val options: List<String>) : Ex() {
        override val word: Word? get() = null
        override val sl: String? get() = g.full
    }

    val needsListening: Boolean
        get() = when (this) {
            is Pick -> listen
            is Arrange -> listen
            is Type -> listen
            else -> false
        }
}

/**
 * Builds Duolingo-style sessions from course content.
 * [canListen]: audio exists and listening exercises are on. [canSpeak]: speech recognition available and on.
 */
class SessionBuilder(
    private val course: Course,
    private val progress: Progress,
    private val canListen: Boolean,
    private val canSpeak: Boolean,
    seed: Long = System.nanoTime(),
) {
    private val rng = Random(seed)
    private var uid = 1
    private fun id() = uid++

    fun lesson(l: Lesson): List<Ex> {
        val pool = course.wordsUpTo(l.order + 2)
        val first = !progress.isDone(l.id)
        val ws = l.words.shuffled(rng)
        val ss = l.sentences.shuffled(rng)
        val out = mutableListOf<Ex>()

        // 1) words: intro (first time) followed by a quick check, alternating direction
        ws.take(6).forEachIndexed { i, w ->
            if (first && !progress.isLearned(w.key)) out += Ex.Intro(id(), w)
            out += when {
                i % 3 == 2 && canListen -> listenPick(w, pool)
                i % 2 == 0 -> pickSlSr(w, pool)
                else -> pickSrSl(w, pool)
            }
        }
        if (ws.size >= 4) out += match(ws.take(5))
        ws.drop(6).forEach { w ->
            if (first && !progress.isLearned(w.key)) out += Ex.Intro(id(), w)
            out += if (canListen && rng.nextBoolean()) listenPick(w, pool) else pickSrSl(w, pool)
        }

        // 2) sentences and grammar, mixed
        val mix = mutableListOf<Ex>()
        val others = course.sentencesUpTo(l.order + 2)
        ss.forEachIndexed { i, s ->
            mix += when (i % 5) {
                0 -> arrangeSl(s, others)
                1 -> arrangeSr(s, others)
                2 -> if (canListen) listenArrange(s, others) else arrangeSl(s, others)
                3 -> typeSl(s)
                else -> if (canSpeak) Ex.Speak(id(), s.text, s.meaning) else arrangeSl(s, others)
            }
        }
        l.grammar.forEach { mix += fill(it) }
        ws.shuffled(rng).take(2).forEach { mix += typeWord(it) }
        if (canListen) ws.shuffled(rng).firstOrNull()?.let { mix += dictation(it) }
        out += mix.shuffled(rng)
        return out
    }

    /** Spaced-repetition review: due words first, then the weakest ones. */
    fun review(max: Int = 14): List<Ex> {
        val due = progress.due(course)
        val words = (due + progress.weakest(course)).distinctBy { it.key }.take(max)
        if (words.isEmpty()) return emptyList()
        val pool = course.words
        val out = words.mapIndexed { i, w ->
            when ((i + rng.nextInt(4)) % 4) {
                0 -> pickSlSr(w, pool)
                1 -> if (canListen) listenPick(w, pool) else pickSrSl(w, pool)
                2 -> typeWord(w)
                else -> pickSrSl(w, pool)
            }
        }.toMutableList()
        val doneLessons = course.lessons.filter { progress.isDone(it.id) }.map { it.id }.toSet()
        val sents = course.sentences.filter { it.lessonId in doneLessons }.shuffled(rng).take(3)
        sents.forEachIndexed { i, s -> out += if (i == 1 && canListen) listenArrange(s, course.sentences) else arrangeSl(s, course.sentences) }
        return out.shuffled(rng)
    }

    // ---- builders

    private fun distract(w: Word, pool: List<Word>, sl: Boolean, n: Int = 3): List<String> {
        val wsr = w.sr.map { it.lowercase() }.toSet()
        // a distractor must not share any meaning with the target (teden/nedelja both mean "nedelja")
        val ok = pool.filter { p -> p.key != w.key && p.sr.none { it.lowercase() in wsr } }
        val own = if (sl) setOf(w.sl.lowercase()) else wsr
        val cand = ok.map { if (sl) it.sl else it.sr.first() }.filter { it.lowercase() !in own }.distinct()
        // prefer same unit, then anything
        val same = ok.filter { it.unitIdx == w.unitIdx }.map { if (sl) it.sl else it.sr.first() }.filter { it.lowercase() !in own }
        return (same.shuffled(rng) + cand.shuffled(rng)).distinct().take(n)
    }

    private fun pickSlSr(w: Word, pool: List<Word>): Ex = Ex.Pick(
        id(), "Šta znači ova reč?", w.sl, (distract(w, pool, sl = false) + w.sr.first()).shuffled(rng), w.sr.first(),
        promptIsSl = true, listen = false, word = w, sl = w.sl,
    )

    private fun pickSrSl(w: Word, pool: List<Word>): Ex = Ex.Pick(
        id(), "Kako se kaže na slovenačkom?", w.meaning, (distract(w, pool, sl = true) + w.sl).shuffled(rng), w.sl,
        promptIsSl = false, listen = false, word = w, sl = w.sl,
    )

    private fun listenPick(w: Word, pool: List<Word>): Ex = Ex.Pick(
        id(), "Šta čuješ?", w.sl, (distract(w, pool, sl = true) + w.sl).shuffled(rng), w.sl,
        promptIsSl = true, listen = true, word = w, sl = w.sl,
    )

    private fun match(ws: List<Word>): Ex = Ex.Match(id(), ws.map { it.sl to it.sr.first() }, ws)

    private fun extraTiles(target: List<String>, others: List<String>, n: Int): List<String> {
        val lower = target.map { it.lowercase() }.toSet()
        return others.flatMap { Text.tokens(it) }.filter { it.lowercase() !in lower }.distinctBy { it.lowercase() }.shuffled(rng).take(n)
    }

    private fun arrangeSl(s: Sentence, others: List<Sentence>): Ex {
        val t = Text.tokens(s.text)
        return Ex.Arrange(
            id(), "Prevedi na slovenački", s.meaning, (t + extraTiles(t, others.map { it.text }, 3)).shuffled(rng),
            s.sl, promptIsSl = false, listen = false, sl = s.text, meaning = s.meaning,
        )
    }

    private fun arrangeSr(s: Sentence, others: List<Sentence>): Ex {
        val t = Text.tokens(s.meaning)
        return Ex.Arrange(
            id(), "Prevedi na srpski", s.text, (t + extraTiles(t, others.map { it.meaning }, 3)).shuffled(rng),
            s.sr, promptIsSl = true, listen = false, sl = s.text, meaning = s.meaning,
        )
    }

    private fun listenArrange(s: Sentence, others: List<Sentence>): Ex {
        val t = Text.tokens(s.text)
        return Ex.Arrange(
            id(), "Složi ono što čuješ", s.text, (t + extraTiles(t, others.map { it.text }, 2)).shuffled(rng),
            s.sl, promptIsSl = true, listen = true, sl = s.text, meaning = s.meaning,
        )
    }

    private fun typeSl(s: Sentence): Ex = Ex.Type(
        id(), "Napiši na slovenačkom", s.meaning, s.sl, answerIsSl = true, listen = false, word = null, sl = s.text, meaning = s.meaning,
    )

    private fun typeWord(w: Word): Ex = Ex.Type(
        id(), "Napiši na slovenačkom", w.meaning, listOf(w.sl), answerIsSl = true, listen = false, word = w, sl = w.sl, meaning = w.meaning,
    )

    private fun dictation(w: Word): Ex = Ex.Type(
        id(), "Napiši šta čuješ", w.sl, listOf(w.sl), answerIsSl = true, listen = true, word = w, sl = w.sl, meaning = w.meaning,
    )

    private fun fill(g: Grammar): Ex = Ex.Fill(id(), g, (g.wrong.take(3) + g.answer).shuffled(rng))

    /** Re-queued copy of an exercise answered wrong. */
    fun again(e: Ex): Ex = when (e) {
        is Ex.Intro -> e.copy(uid = id())
        is Ex.Pick -> e.copy(uid = id(), options = e.options.shuffled(rng))
        is Ex.Match -> e.copy(uid = id())
        is Ex.Arrange -> e.copy(uid = id(), tiles = e.tiles.shuffled(rng))
        is Ex.Type -> e.copy(uid = id())
        is Ex.Speak -> e.copy(uid = id())
        is Ex.Fill -> e.copy(uid = id(), options = e.options.shuffled(rng))
    }
}
