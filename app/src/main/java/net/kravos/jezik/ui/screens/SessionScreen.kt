package net.kravos.jezik.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.speech.SpeechRecognizer
import net.kravos.jezik.data.Ex
import net.kravos.jezik.data.SessionBuilder
import net.kravos.jezik.data.Text
import net.kravos.jezik.ui.LocalApp
import net.kravos.jezik.ui.LocalNav
import net.kravos.jezik.ui.components.BigButton
import net.kravos.jezik.ui.components.ProgressPill
import net.kravos.jezik.ui.theme.K
import net.kravos.jezik.ui.theme.LocalPalette
import net.kravos.jezik.widget.WordWidget

data class Verdict(val ok: Boolean, val title: String, val answer: String? = null, val meaning: String? = null)

data class Summary(val xp: Int, val accuracy: Int, val seconds: Long, val streak: Int, val todayXp: Int, val goal: Int, val isLesson: Boolean)

private val praise = listOf("Odlično!", "Bravo!", "Tačno!", "Sjajno!", "Super!", "Tako je!")

@Composable
fun SessionScreen(lessonId: String?) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val speaker = app.speaker
    val lesson = lessonId?.let { app.course.lesson(it) }

    val speechAvail = remember { runCatching { SpeechRecognizer.isRecognitionAvailable(ctx) }.getOrDefault(false) }
    val builder = remember {
        SessionBuilder(app.course, app.progress, app.progress.listenActive() && speaker.canPlay(), app.progress.speakActive() && speechAvail)
    }
    val queue = remember { mutableStateListOf<Ex>().apply { addAll(if (lesson != null) builder.lesson(lesson) else builder.review()) } }
    var idx by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<String?>(null) }
    var verdict by remember { mutableStateOf<Verdict?>(null) }
    val results = remember { mutableMapOf<String, Boolean>() }
    val retries = remember { mutableMapOf<String, Int>() }
    var mistakes by remember { mutableIntStateOf(0) }
    var graded by remember { mutableIntStateOf(0) }
    val start = remember { System.currentTimeMillis() }
    var summary by remember { mutableStateOf<Summary?>(null) }
    var confirmQuit by remember { mutableStateOf(false) }

    fun finish() {
        val acc = if (graded == 0) 100 else ((graded - mistakes).coerceAtLeast(0) * 100 / graded)
        val xp = if (lesson != null) 10 + (if (!app.progress.isDone(lesson.id)) 5 else 0) + (if (mistakes == 0) 5 else 0)
        else 5 + results.size / 2
        app.progress.finish(lesson?.id, xp, results)
        WordWidget.refreshAsync(ctx)
        summary = Summary(xp, acc, (System.currentTimeMillis() - start) / 1000, app.progress.streak(), app.progress.todayXp(), app.progress.dailyGoal, lesson != null)
    }

    fun advance() {
        speaker.stop()
        pending = null
        verdict = null
        if (idx + 1 >= queue.size) finish() else idx++
    }

    fun grade(ex: Ex, v: Verdict) {
        verdict = v
        if (ex !is Ex.Match) graded++
        ex.word?.let { results[it.key] = (results[it.key] ?: true) && v.ok }
        if (ex is Ex.Match) ex.words.forEach { if (!results.containsKey(it.key)) results[it.key] = true }
        if (!v.ok) {
            mistakes++
            val key = "${ex.javaClass.simpleName}|${ex.sl}|${ex.word?.key}"
            val n = retries[key] ?: 0
            if (n < 2) { retries[key] = n + 1; queue.add(builder.again(ex)) }
        }
        if (ex !is Ex.Speak && ex !is Ex.Intro) ex.sl?.let { speaker.play(it) }
    }

    fun check() {
        val ex = queue[idx]
        grade(ex, if (app.testMode) Verdict(true, praise.random()) else evaluate(ex, pending))
    }

    /** "Ne mogu sada da slušam/govorim": pause that kind of exercise for an hour and drop the rest of them. */
    fun snooze(listen: Boolean) {
        val until = System.currentTimeMillis() + 60 * 60 * 1000
        if (listen) app.progress.listenSnoozeUntil = until else app.progress.speakSnoozeUntil = until
        val rest = queue.subList(idx + 1, queue.size).filterNot { if (listen) it.needsListening else it is Ex.Speak }
        while (queue.size > idx + 1) queue.removeAt(queue.lastIndex)
        queue.addAll(rest)
        advance()
    }

    BackHandler(enabled = summary == null) { confirmQuit = true }

    summary?.let { s -> Completion(s) { nav.back() }; return }

    if (queue.isEmpty()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✨", fontSize = 64.sp)
            Spacer(Modifier.height(12.dp))
            Text("Nema ničega za ponavljanje", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text("Završi neku lekciju pa se vrati — ovde stižu reči kad dođe vreme da ih obnoviš.", color = LocalPalette.current.dim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(24.dp))
            BigButton("Nazad") { nav.back() }
        }
        return
    }

    val ex = queue[idx]
    val locked = verdict != null

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Close, "Prekini", Modifier.clip(CircleShape).clickable { confirmQuit = true }.padding(10.dp).size(26.dp), tint = LocalPalette.current.dim)
            ProgressPill(idx.toFloat() / queue.size, Modifier.weight(1f))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
                when (ex) {
                    is Ex.Intro -> IntroView(ex)
                    is Ex.Pick -> PickView(ex, pending, locked) { pending = it }
                    is Ex.Fill -> FillView(ex, pending, locked) { pending = it }
                    is Ex.Arrange -> ArrangeView(ex, locked) { pending = it }
                    is Ex.Type -> TypeView(ex, locked) { pending = it }
                    is Ex.Speak -> SpeakView(ex, locked) { pending = it }
                    is Ex.Match -> MatchView(ex, onMiss = { keys -> keys.forEach { results[it] = false } }) {
                        if (verdict == null) grade(ex, Verdict(true, praise.random()))
                    }
                }
            }
        }
        BottomBar(ex, verdict, pending, app.testMode, onCheck = { check() }, onNext = { advance() }, onSnooze = { snooze(it) })
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Prekini lekciju?") },
            text = { Text("Napredak iz ove lekcije neće biti sačuvan.") },
            confirmButton = { TextButton(onClick = { confirmQuit = false }) { Text("Nastavi učenje") } },
            dismissButton = { TextButton(onClick = { confirmQuit = false; speaker.stop(); nav.back() }) { Text("Prekini", color = K.Red) } },
        )
    }
}

private fun evaluate(ex: Ex, pending: String?): Verdict = when (ex) {
    is Ex.Pick -> if (pending == ex.answer) Verdict(true, praise.random(), meaning = ex.word?.let { "${it.sl} = ${it.meaning}" })
    else Verdict(false, "Tačan odgovor:", ex.answer, ex.word?.let { "${it.sl} = ${it.meaning}" })
    is Ex.Fill -> if (pending == ex.g.answer) Verdict(true, praise.random(), ex.g.full, ex.g.sr)
    else Verdict(false, "Tačan odgovor:", ex.g.full, ex.g.sr)
    is Ex.Arrange -> {
        val m = Text.check(pending.orEmpty(), ex.accepted)
        if (m == Text.Match.EXACT || m == Text.Match.ACCENT) Verdict(true, praise.random(), meaning = if (ex.listen) ex.meaning else null)
        else Verdict(false, "Tačan odgovor:", ex.accepted.first(), if (ex.listen || !ex.promptIsSl) ex.meaning else null)
    }
    is Ex.Type -> when (Text.check(pending.orEmpty(), ex.accepted)) {
        Text.Match.EXACT -> Verdict(true, praise.random(), meaning = if (ex.listen) ex.meaning else null)
        Text.Match.ACCENT -> Verdict(true, "Tačno, ali pazi na znakove:", ex.accepted.first(), ex.meaning)
        Text.Match.TYPO -> Verdict(true, "Mala greška u kucanju:", ex.accepted.first(), ex.meaning)
        Text.Match.WRONG -> Verdict(false, "Tačan odgovor:", ex.accepted.first(), ex.meaning)
    }
    is Ex.Speak -> {
        val heard = pending.orEmpty().split('\n').filter { it.isNotBlank() }
        val best = heard.maxOfOrNull { Text.similarity(it, ex.text) } ?: 0f
        if (best >= 0.72f) Verdict(true, "Odlično izgovoreno!", meaning = ex.meaning)
        else Verdict(false, "Nije baš jasno. Treba:", ex.text, heard.firstOrNull()?.let { "Čuo sam: \"$it\"" })
    }
    is Ex.Match, is Ex.Intro -> Verdict(true, praise.random())
}

@Composable
private fun BottomBar(ex: Ex, v: Verdict?, pending: String?, testMode: Boolean, onCheck: () -> Unit, onNext: () -> Unit, onSnooze: (Boolean) -> Unit) {
    val pal = LocalPalette.current
    if (v == null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            if (ex.needsListening) SnoozeLink("Ne mogu sada da slušam") { onSnooze(true) }
            if (ex is Ex.Speak) SnoozeLink("Ne mogu sada da govorim") { onSnooze(false) }
            when (ex) {
                is Ex.Intro -> BigButton("Dalje", onClick = onNext)
                is Ex.Match -> if (testMode) BigButton("Proveri", onClick = onCheck) else Spacer(Modifier.height(52.dp))
                else -> BigButton("Proveri", enabled = testMode || !pending.isNullOrBlank(), onClick = onCheck)
            }
        }
        return
    }
    val color = if (v.ok) K.Green else K.Red
    Column(Modifier.fillMaxWidth().background(if (v.ok) pal.okBg else pal.badBg).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Text(if (v.ok) "✓" else "✕", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text(v.title, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        v.answer?.let { Text(it, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
        v.meaning?.let { Text(it, color = color.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(12.dp))
        BigButton(if (v.ok) "Dalje" else "U redu", color = color, onClick = onNext)
    }
}

@Composable
private fun SnoozeLink(text: String, onClick: () -> Unit) {
    Text(
        text.uppercase(), Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        color = LocalPalette.current.dim, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.6.sp, textAlign = TextAlign.Center,
    )
}

@Composable
private fun Completion(s: Summary, onDone: () -> Unit) {
    val pal = LocalPalette.current
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(if (s.accuracy == 100) "🏆" else "🎉", fontSize = 84.sp)
        Spacer(Modifier.height(10.dp))
        Text(if (s.isLesson) "Lekcija završena!" else "Ponavljanje završeno!", color = K.Gold, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, textAlign = TextAlign.Center)
        if (s.accuracy == 100) Text("Bez ijedne greške!", color = pal.dim, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("UKUPNO XP", "+${s.xp}", K.Gold, Modifier.weight(1f))
            StatBox("TAČNOST", "${s.accuracy}%", K.Green, Modifier.weight(1f))
            StatBox("VREME", "%d:%02d".format(s.seconds / 60, s.seconds % 60), K.Blue, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Text("🔥 ${s.streak} ${daysWord(s.streak)} zaredom", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = K.Orange)
        Spacer(Modifier.height(14.dp))
        Text("Dnevni cilj: ${s.todayXp.coerceAtMost(s.goal)} / ${s.goal} XP", color = pal.dim, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        ProgressPill(s.todayXp.toFloat() / s.goal, Modifier.fillMaxWidth(), K.Orange)
        Spacer(Modifier.weight(1f))
        BigButton("Nastavi", onClick = onDone)
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(color)) {
        Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), textAlign = TextAlign.Center)
        Box(
            Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 2.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background).padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
    }
}

fun daysWord(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "dan"
    else -> "dana"
}
