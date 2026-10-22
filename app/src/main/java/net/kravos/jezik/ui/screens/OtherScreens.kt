package net.kravos.jezik.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import net.kravos.jezik.BuildConfig
import net.kravos.jezik.data.Text as T
import net.kravos.jezik.data.ThemeMode
import net.kravos.jezik.data.Word
import net.kravos.jezik.ui.LocalApp
import net.kravos.jezik.ui.LocalNav
import net.kravos.jezik.ui.Route
import net.kravos.jezik.ui.components.BigButton
import net.kravos.jezik.ui.components.Card
import net.kravos.jezik.ui.components.Chunky
import net.kravos.jezik.ui.components.OptState
import net.kravos.jezik.ui.components.OptionCard
import net.kravos.jezik.ui.components.SectionTitle
import net.kravos.jezik.ui.components.TopTitle
import net.kravos.jezik.ui.theme.K
import net.kravos.jezik.ui.theme.LocalPalette
import net.kravos.jezik.update.Release
import net.kravos.jezik.update.Updater
import java.time.LocalDate

// ------------------------------------------------------------------ Ponavljanje

@Composable
fun ReviewScreen(pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val pal = LocalPalette.current
    val rev by app.progress.rev.collectAsState()
    val due = remember(rev) { app.progress.due(app.course) }
    val learned = remember(rev) { app.progress.learnedCount() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
        item { StatsBar() }
        item { TopTitle("Ponavljanje") }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(K.Blue).padding(18.dp),
            ) {
                Text("🧠", fontSize = 40.sp)
                Text(
                    if (due.isEmpty()) "Danas nema reči za ponavljanje" else "${due.size} ${wordsWord(due.size)} čeka ponavljanje",
                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                )
                Text(
                    "Reči se vraćaju posle 1, 2, 4, 8… dana. Ako pogrešiš, kreću ispočetka.",
                    color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                )
                Chunky(Modifier.fillMaxWidth(), color = Color.White, shadow = K.darker(K.Blue, 0.75f), onClick = { nav.go(Route.Session(null)) }) {
                    Text(
                        if (due.isEmpty() && learned > 0) "VEŽBAJ SLABE REČI" else "POČNI PONAVLJANJE", color = K.Blue,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
            }
        }
        item { SectionTitle("Vodiči i podsetnici") }
        item {
            ToolRow("⚠️", "Lažni prijatelji", "Reči koje zvuče poznato, a znače nešto drugo") { nav.go(Route.FalseFriends) }
        }
        items(app.course.units) { u ->
            ToolRow("📘", "Jedinica ${u.idx + 1}: ${u.title}", u.desc) { nav.go(Route.Guide(u.idx)) }
        }
        item {
            Text(
                "Naučeno reči: $learned od ${app.course.words.size}", color = pal.dim, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

private fun wordsWord(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "reč"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "reči"
    else -> "reči"
}

@Composable
private fun ToolRow(icon: String, title: String, sub: String, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp))
            .border(2.dp, pal.border, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 26.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(sub, color = pal.dim, fontSize = 13.sp)
        }
        Text("›", fontSize = 24.sp, color = pal.dim)
    }
}

@Composable
fun FalseFriendsScreen(pad: PaddingValues, onBack: () -> Unit) {
    val app = LocalApp.current
    val ff = remember { app.course.words.filter { it.note.contains("lažni", ignoreCase = true) } }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
        item { TopTitle("Lažni prijatelji", onBack) }
        item {
            Text(
                "Ove reči zvuče kao srpske, ali znače nešto drugo. Dodirni red da čuješ izgovor.",
                color = LocalPalette.current.dim, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        items(ff) { WordRow(it) }
    }
}

// ------------------------------------------------------------------ Rečnik

@Composable
fun WordsScreen(pad: PaddingValues) {
    val app = LocalApp.current
    val pal = LocalPalette.current
    val rev by app.progress.rev.collectAsState()
    var q by remember { mutableStateOf("") }
    var onlyLearned by remember { mutableStateOf(false) }
    val list = remember(q, onlyLearned, rev) {
        val n = T.plain(T.norm(q))
        app.course.words.filter { w ->
            (!onlyLearned || app.progress.isLearned(w.key)) &&
                (n.isEmpty() || T.plain(T.norm(w.sl)).contains(n) || w.sr.any { T.plain(T.norm(it)).contains(n) })
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
        item { StatsBar() }
        item { TopTitle("Rečnik") }
        item {
            OutlinedTextField(
                q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Traži na ${app.course.loc} ili srpskom", color = pal.dim) },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = pal.border, focusedBorderColor = K.Blue),
            )
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionCard("Sve (${app.course.words.size})", if (!onlyLearned) OptState.SELECTED else OptState.NORMAL, fontSize = 14) { onlyLearned = false }
                OptionCard("Naučene (${app.progress.learnedCount()})", if (onlyLearned) OptState.SELECTED else OptState.NORMAL, fontSize = 14) { onlyLearned = true }
            }
        }
        if (list.isEmpty()) item {
            Text(if (onlyLearned) "Još nema naučenih reči — završi prvu lekciju." else "Ništa nije pronađeno.", color = pal.dim, modifier = Modifier.padding(20.dp))
        }
        items(list, key = { it.key }) { WordRow(it) }
    }
}

@Composable
private fun WordRow(w: Word) {
    val app = LocalApp.current
    val pal = LocalPalette.current
    val srs = app.progress.srsOf(w.key)
    Row(
        Modifier.fillMaxWidth().clickable { app.speaker.play(w.sl) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(w.sl, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(w.meaning, color = pal.dim, fontSize = 14.sp)
            if (w.note.isNotBlank()) Text("💡 ${w.note}", color = K.Blue, fontSize = 12.sp)
        }
        // strength: 0..5 bars
        val s = ((srs?.box ?: 0).coerceAtMost(5))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            (1..5).forEach { i ->
                Box(Modifier.width(5.dp).height((6 + i * 3).dp).clip(RoundedCornerShape(2.dp)).background(if (i <= s) K.Green else pal.border))
            }
        }
    }
}

// ------------------------------------------------------------------ Profil

@Composable
fun ProfileScreen(pad: PaddingValues, release: Release?, onCheckUpdate: () -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val pal = LocalPalette.current
    val rev by app.progress.rev.collectAsState()
    val p = app.progress
    val today = LocalDate.now().toEpochDay()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
        item { StatsBar() }
        item { TopTitle("Profil") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(K.Orange).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${p.streak()}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp)
                    Text("${daysWord(p.streak())} zaredom", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        if (p.streakAliveToday()) "Danas si već vežbao — bravo!" else "Uradi jednu lekciju danas da sačuvaš niz.",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text("🔥", fontSize = 64.sp)
            }
        }
        item {
            Card {
                Text("Ova nedelja", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                val days = (6 downTo 0).map { today - it }
                val vals = days.map { p.xpOn(it) }
                val max = (vals.maxOrNull() ?: 0).coerceAtLeast(p.dailyGoal)
                val names = listOf("P", "U", "S", "Č", "P", "S", "N")
                Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    days.forEachIndexed { i, d ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (vals[i] > 0) Text("${vals[i]}", fontSize = 10.sp, color = pal.dim)
                            Box(
                                Modifier.width(22.dp).height((90f * vals[i] / max).coerceAtLeast(4f).dp).clip(RoundedCornerShape(6.dp))
                                    .background(if (vals[i] >= p.dailyGoal) K.Gold else if (vals[i] > 0) K.Orange else pal.border),
                            )
                            Text(
                                names[LocalDate.ofEpochDay(d).dayOfWeek.value - 1], fontSize = 12.sp, fontWeight = if (d == today) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (d == today) MaterialTheme.colorScheme.onBackground else pal.dim, modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("⚡", "${p.totalXp()}", "ukupno XP", Modifier.weight(1f))
                Stat("📖", "${p.learnedCount()}", "naučenih reči", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val lessons = app.course.lessons
                Stat("⭐", "${lessons.count { it.type == net.kravos.jezik.data.LessonType.LESSON && p.isDone(it.id) }}", "lekcija", Modifier.weight(1f))
                Stat("📚", "${lessons.count { it.type == net.kravos.jezik.data.LessonType.STORY && p.isDone(it.id) }}", "priča", Modifier.weight(1f))
            }
        }
        item {
            Card {
                Text("Dnevni cilj", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                listOf(10 to "Opušteno", 20 to "Redovno", 30 to "Ozbiljno", 50 to "Intenzivno").forEach { (g, l) ->
                    OptionCard("$l · $g XP dnevno", if (p.dailyGoal == g) OptState.SELECTED else OptState.NORMAL, Modifier.fillMaxWidth().padding(vertical = 4.dp), fontSize = 15) { p.dailyGoal = g }
                }
            }
        }
        item {
            Chunky(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.background, shadow = pal.border, border = pal.border, onClick = { nav.go(Route.Settings) }) {
                Text("PODEŠAVANJA", fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = K.Blue, modifier = Modifier.padding(vertical = 14.dp))
            }
        }
    }
    @Suppress("unused") val r = rev
    @Suppress("unused") val rr = release
    @Suppress("unused") val c = onCheckUpdate
}

@Composable
private fun Stat(icon: String, value: String, label: String, modifier: Modifier) {
    val pal = LocalPalette.current
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).border(2.dp, pal.border, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(label, color = pal.dim, fontSize = 12.sp)
        }
    }
}

// ------------------------------------------------------------------ Podešavanja

@Composable
fun SettingsScreen(pad: PaddingValues, release: Release?, onBack: () -> Unit, onCheckUpdate: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val scope = rememberCoroutineScope()
    val p = app.progress
    val rev by p.rev.collectAsState()
    val theme by p.theme.collectAsState()
    val ttsLang by app.speaker.ttsLang.collectAsState()
    var progress by remember { mutableStateOf<Float?>(null) }
    var updMsg by remember { mutableStateOf<String?>(null) }
    var auto by remember { mutableStateOf(p.autoUpdateCheck) }
    var confirmReset by remember { mutableStateOf(false) }
    val speechAvail = remember { runCatching { android.speech.SpeechRecognizer.isRecognitionAvailable(ctx) }.getOrDefault(false) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
        item { TopTitle("Podešavanja", onBack) }
        item {
            Card {
                Text("Jezik koji učiš", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                LanguageList {}
            }
        }
        item {
            Card {
                Text("Vežbe", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Toggle("Zvuk", "Izgovor reči i rečenica", p.soundOn) { p.soundOn = it }
                Toggle("Vežbe slušanja", "Prepoznaj i napiši ono što čuješ", p.listenOn) { p.listenOn = it }
                Toggle("Vežbe govora", if (speechAvail) "Izgovori rečenicu u mikrofon" else "Na ovom telefonu nema prepoznavanja govora", p.speakOn && speechAvail) { p.speakOn = it }
                Toggle("Otključaj sve lekcije", "Slobodno skakanje po kursu", p.unlockAll) { p.unlockAll = it }
            }
        }
        item {
            Card {
                Text("Glas", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                val clips = app.speaker.clipCount
                Text(
                    buildString {
                        append(if (clips > 0) "Ugrađeni snimci: $clips" else "Nema ugrađenih snimaka")
                        append("\nSistemski glas za ${app.course.acc}: ")
                        append(when (ttsLang) { null -> "proveravam…"; true -> "da"; false -> "ne" })
                    },
                    color = pal.dim, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { app.speaker.play(app.course.words.first().sl) }) { Text("Probaj zvuk") }
                    TextButton(onClick = {
                        runCatching { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text("Podešavanja glasa") }
                }
            }
        }
        item {
            Card {
                Text("Ažuriranje", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Verzija ${BuildConfig.VERSION_NAME}", fontSize = 15.sp)
                        Text(
                            when {
                                release == null -> "Nije provereno"
                                Updater.isNewer(release) -> "Dostupna je verzija 1.0.${release.build}"
                                else -> "Imaš najnoviju verziju"
                            }, fontSize = 12.sp, color = pal.dim,
                        )
                    }
                    if (release != null && Updater.isNewer(release)) {
                        TextButton(enabled = progress == null, onClick = {
                            scope.launch {
                                try {
                                    progress = 0f
                                    val f = Updater.download(ctx, release) { progress = it }
                                    progress = null
                                    if (!Updater.install(ctx, f)) updMsg = "Dozvoli instalaciju iz ove aplikacije, pa ponovo pritisni Instaliraj."
                                } catch (e: Exception) {
                                    progress = null; updMsg = "Greška: ${e.message}"
                                }
                            }
                        }) { Text("Instaliraj") }
                    } else TextButton(onClick = onCheckUpdate) { Text("Proveri") }
                }
                progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
                updMsg?.let { Text(it, fontSize = 12.sp) }
                Toggle("Automatski proveri pri pokretanju", null, auto) { auto = it; p.autoUpdateCheck = it }
            }
        }
        item {
            Card {
                Text("Tema", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.DARK to "Tamna", ThemeMode.LIGHT to "Svetla", ThemeMode.SYSTEM to "Sistem").forEach { (m, l) ->
                        OptionCard(l, if (theme == m) OptState.SELECTED else OptState.NORMAL, fontSize = 14) { p.setTheme(m) }
                    }
                }
            }
        }
        item {
            Card {
                Text("O kursu", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "${app.course.flag} ${app.course.name} za govornike srpskog: ${app.course.units.size} jedinica, ${app.course.lessons.size} lekcija i priča, ${app.course.words.size} reči. " +
                        "Snimci: ${app.course.voice}. Samo za ličnu upotrebu.",
                    color = pal.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
                TextButton(onClick = { confirmReset = true }) { Text("Resetuj napredak (${app.course.name.lowercase()})", color = K.Red) }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Resetuj napredak za ${app.course.name.lowercase()}?") },
            text = { Text("Brišu se završene lekcije, XP, niz dana i ponavljanje reči za ovaj jezik. Ovo ne može da se vrati.") },
            confirmButton = { TextButton(onClick = { confirmReset = false }) { Text("Odustani") } },
            dismissButton = { TextButton(onClick = { p.reset(); confirmReset = false }) { Text("Resetuj", color = K.Red) } },
        )
    }
    @Suppress("unused") val r = rev
}

@Composable
private fun Toggle(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = LocalPalette.current.dim)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

