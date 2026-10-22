package net.kravos.jezik.data

import java.security.MessageDigest
import java.text.Normalizer

/** Text helpers for answer checking. */
object Text {
    enum class Match { EXACT, ACCENT, TYPO, WRONG }

    private val cyr = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ђ' to "đ", 'е' to "e", 'ж' to "ž", 'з' to "z", 'и' to "i",
        'ј' to "j", 'к' to "k", 'л' to "l", 'љ' to "lj", 'м' to "m", 'н' to "n", 'њ' to "nj", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'ћ' to "ć", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "č", 'џ' to "dž", 'ш' to "š",
    )

    /** Serbian Cyrillic → Latin, so answers typed on a Cyrillic keyboard are accepted. */
    fun latin(s: String): String {
        if (s.none { it in 'Ѐ'..'ӿ' }) return s
        val b = StringBuilder()
        for (ch in s) {
            val low = ch.lowercaseChar()
            val t = cyr[low]
            if (t == null) b.append(ch) else b.append(if (ch.isUpperCase()) t.replaceFirstChar { it.uppercaseChar() } else t)
        }
        return b.toString()
    }

    /** Lowercase, no punctuation, single spaces. */
    fun norm(s: String): String = latin(s).lowercase()
        .replace(Regex("[.,!?¿¡;:\"'„“”‚‘’«»()…\\-–—]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun plain(s: String): String {
        val d = Normalizer.normalize(s.replace("đ", "dj").replace("Đ", "Dj"), Normalizer.Form.NFD)
        return d.replace(Regex("\\p{Mn}+"), "")
    }

    fun lev(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val c = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + c)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** Compares a typed answer with all accepted forms. */
    fun check(input: String, accepted: List<String>): Match {
        val a = norm(input)
        if (a.isEmpty()) return Match.WRONG
        val forms = accepted.map { norm(it) }
        if (forms.any { it == a }) return Match.EXACT
        if (forms.any { plain(it) == plain(a) }) return Match.ACCENT
        if (forms.any { it.length >= 6 && lev(plain(it), plain(a)) <= 1 + it.length / 18 }) return Match.TYPO
        return Match.WRONG
    }

    /** 0..1 similarity for speech results. */
    fun similarity(a: String, b: String): Float {
        val x = plain(norm(a)); val y = plain(norm(b))
        val m = maxOf(x.length, y.length)
        if (m == 0) return 0f
        return 1f - lev(x, y).toFloat() / m
    }

    fun tokens(s: String): List<String> = s.replace(Regex("[.,!?¿¡;:\"„“”…]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }

    /** Must match tools/gen_audio.py: sha1(text.strip())[:16]. */
    fun audioKey(text: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(text.trim().toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }.take(16)
    }
}
