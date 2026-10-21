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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import net.kravos.jezik.data.StoryItem
import net.kravos.jezik.ui.LocalApp
import net.kravos.jezik.ui.LocalNav
import net.kravos.jezik.ui.components.BigButton
import net.kravos.jezik.ui.components.OptState
import net.kravos.jezik.ui.components.OptionCard
import net.kravos.jezik.ui.components.ProgressPill
import net.kravos.jezik.ui.components.SpeakerButton
import net.kravos.jezik.ui.theme.K
import net.kravos.jezik.ui.theme.LocalPalette
import net.kravos.jezik.widget.WordWidget

/** Duolingo-Stories-like dialogue: lines appear one by one, tap a line for the translation, questions block until answered. */
@Composable
fun StoryScreen(lessonId: String) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val lesson = app.course.lesson(lessonId) ?: run { nav.back(); return }
    val items = lesson.story
    val speakers = remember { items.filterIsInstance<StoryItem.Line>().map { it.who }.distinct() }
    val colors = listOf(K.Purple, K.Blue, K.Orange, K.Teal, K.Pink)

    var shown by remember { mutableIntStateOf(1) }
    val open = remember { mutableStateMapOf<Int, Boolean>() }
    val answers = remember { mutableStateMapOf<Int, String>() } // question index -> picked option
    val opts = remember { items.map { if (it is StoryItem.Question) (it.wrong + it.answer).shuffled() else emptyList() } }
    var mistakes by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf<Summary?>(null) }
    val start = remember { System.currentTimeMillis() }
    val list = rememberLazyListState()

    val last = items.getOrNull(shown - 1)
    val blocked = last is StoryItem.Question && answers[shown - 1] != last.answer && !app.testMode
    val finished = shown >= items.size && !blocked

    LaunchedEffect(shown) {
        (items.getOrNull(shown - 1) as? StoryItem.Line)?.let { app.speaker.play(it.sl) }
        list.animateScrollToItem((shown + 1).coerceAtMost(items.size + 1))
    }

    BackHandler(enabled = done == null) { app.speaker.stop(); nav.back() }

    done?.let { s ->
        StoryDone(s, lesson.title) { nav.back() }
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Close, "Zatvori", Modifier.clip(CircleShape).clickable { app.speaker.stop(); nav.back() }.padding(10.dp).size(26.dp), tint = pal.dim)
            ProgressPill(shown.toFloat() / items.size, Modifier.weight(1f), K.Purple)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list) {
            item {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📖", fontSize = 48.sp)
                    Text(lesson.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text("Dodirni rečenicu za prevod", color = pal.dim, fontSize = 13.sp)
                }
            }
            itemsIndexed(items.take(shown)) { i, it ->
                when (it) {
                    is StoryItem.Line -> {
                        val c = colors[speakers.indexOf(it.who).coerceAtLeast(0) % colors.size]
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
                                Text(it.who.take(1), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { open[i] = !(open[i] ?: false); app.speaker.play(it.sl) }.padding(12.dp),
                            ) {
                                Text(it.who, color = c, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(it.sl, fontSize = 18.sp, modifier = Modifier.padding(top = 2.dp))
                                if (open[i] == true) Text(it.sr, color = pal.dim, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            SpeakerButton(36.dp, c) { app.speaker.play(it.sl) }
                        }
                    }
                    is StoryItem.Question -> {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("❓ ${it.q}", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(bottom = 10.dp))
                            val picked = answers[i]
                            val solved = picked == it.answer
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                opts[i].forEach { o ->
                                    val st = when {
                                        solved && o == it.answer -> OptState.RIGHT
                                        solved -> OptState.DONE
                                        o == picked -> OptState.WRONG
                                        else -> OptState.NORMAL
                                    }
                                    OptionCard(o, st, Modifier.fillMaxWidth(), enabled = !solved, fontSize = 16) {
                                        answers[i] = o
                                        if (o != it.answer) mistakes++
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        Box(Modifier.fillMaxWidth().padding(18.dp)) {
            if (finished) {
                BigButton("Završi priču") {
                    val xp = 15 + if (mistakes == 0) 5 else 0
                    app.progress.finish(lesson.id, xp, emptyMap())
                    WordWidget.refreshAsync(ctx)
                    val q = items.count { it is StoryItem.Question }
                    done = Summary(xp, if (q == 0) 100 else ((q - mistakes).coerceAtLeast(0) * 100 / q), (System.currentTimeMillis() - start) / 1000, app.progress.streak(), app.progress.todayXp(), app.progress.dailyGoal, true)
                }
            } else {
                BigButton("Dalje", enabled = !blocked, color = K.Purple) { shown++ }
            }
        }
    }
}

@Composable
private fun StoryDone(s: Summary, title: String, onDone: () -> Unit) {
    val pal = LocalPalette.current
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text("📚", fontSize = 84.sp)
        Text("Priča završena!", color = K.Purple, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        Text(title, color = pal.dim, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(20.dp))
        Text("+${s.xp} XP  ·  tačnost ${s.accuracy}%", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = K.Gold)
        Text("🔥 ${s.streak} ${daysWord(s.streak)} zaredom", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = K.Orange, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.weight(1f))
        BigButton("Nastavi", color = K.Purple, onClick = onDone)
    }
}
