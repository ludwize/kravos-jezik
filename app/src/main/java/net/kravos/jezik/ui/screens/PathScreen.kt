package net.kravos.jezik.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kravos.jezik.data.CourseUnit
import net.kravos.jezik.data.Lesson
import net.kravos.jezik.data.LessonType
import net.kravos.jezik.ui.LocalApp
import net.kravos.jezik.ui.LocalNav
import net.kravos.jezik.ui.Route
import net.kravos.jezik.ui.components.BigButton
import net.kravos.jezik.ui.components.Chunky
import net.kravos.jezik.ui.theme.K
import net.kravos.jezik.ui.theme.LocalPalette
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsBar() {
    val app = LocalApp.current
    val pal = LocalPalette.current
    app.progress.rev.collectAsState().value
    val streak = app.progress.streak()
    val today = app.progress.todayXp()
    val goal = app.progress.dailyGoal
    var picker by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable { picker = true }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(app.course.flag, fontSize = 26.sp)
            Spacer(Modifier.width(6.dp))
            Text(app.course.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (app.langs.size > 1) Text(" ▾", color = pal.dim, fontSize = 14.sp)
        }
        Spacer(Modifier.weight(1f))
        Text("🔥", fontSize = 20.sp, color = if (app.progress.streakAliveToday()) Color.Unspecified else pal.dim)
        Text(" $streak", fontWeight = FontWeight.ExtraBold, color = if (app.progress.streakAliveToday()) K.Orange else pal.dim, fontSize = 17.sp)
        Spacer(Modifier.width(16.dp))
        Text("⚡", fontSize = 20.sp)
        Text(" $today/$goal", fontWeight = FontWeight.ExtraBold, color = if (today >= goal) K.Gold else pal.dim, fontSize = 17.sp)
        Spacer(Modifier.width(8.dp))
    }
    if (picker) {
        val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { picker = false }, sheetState = st, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
                Text("Koji jezik učiš?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
                LanguageList { picker = false }
            }
        }
    }
}

/** One card per bundled course; picking one switches the whole app to it. */
@Composable
fun LanguageList(onPicked: () -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val pal = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        app.langs.forEach { l ->
            val c = app.courseFor(l)
            val p = app.progressFor(l)
            val sel = l == app.lang.value
            Chunky(
                Modifier.fillMaxWidth(), color = if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
                shadow = if (sel) K.Blue else pal.border, border = if (sel) K.Blue else pal.border, depth = 3.dp,
                onClick = { onPicked(); if (!sel) { nav.tab(net.kravos.jezik.ui.Tab.LEARN); app.switchTo(l) } },
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.flag, fontSize = 34.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = if (sel) K.Blue else MaterialTheme.colorScheme.onBackground)
                        Text(
                            if (!p.started()) "${c.native} · ${c.units.size} jedinica · još nisi počeo"
                            else "🔥 ${p.streak()} · ⚡ ${p.totalXp()} XP · ${p.lessonsDone()}/${c.lessons.size} lekcija",
                            color = pal.dim, fontSize = 13.sp,
                        )
                    }
                    if (sel) Icon(Icons.Filled.Check, "Izabrano", tint = K.Blue)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathScreen(pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val rev by app.progress.rev.collectAsState()
    val course = app.course
    val current = remember(rev) { app.progress.current(course) }
    var sheet by remember { mutableStateOf<Lesson?>(null) }
    val list = rememberLazyListState()

    LaunchedEffect(Unit) {
        val u = current?.unitIdx ?: 0
        if (u > 0) list.scrollToItem(u)
    }

    Column(Modifier.fillMaxSize()) {
        StatsBar()
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 40.dp)) {
            course.units.forEach { u ->
                item(key = u.id) {
                    UnitBlock(u, current, rev) { sheet = it }
                }
            }
            item {
                Text(
                    "Kraj kursa za sada — novi nivoi stižu uskoro.", color = LocalPalette.current.dim, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
    }

    sheet?.let { l ->
        val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = st, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            LessonSheet(l) {
                sheet = null
                nav.go(if (l.type == LessonType.STORY) Route.Story(l.id) else Route.Session(l.id))
            }
        }
    }
}

@Composable
private fun UnitBlock(u: CourseUnit, current: Lesson?, rev: Int, onLesson: (Lesson) -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val color = K.units[u.idx % K.units.size]
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clip(RoundedCornerShape(16.dp)).background(color).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("JEDINICA ${u.idx + 1}", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 0.8.sp)
                Text(u.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(u.desc, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            Chunky(color = color, shadow = K.darker(color, 0.7f), border = Color.White.copy(alpha = 0.35f), shape = RoundedCornerShape(12.dp), onClick = { nav.go(Route.Guide(u.idx)) }) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("VODIČ", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }
        }
        u.lessons.forEachIndexed { i, l ->
            val x = (sin(i * 1.15) * 72).dp
            val done = app.progress.isDone(l.id)
            val unlocked = app.progress.isUnlocked(app.course, l)
            val isCurrent = current?.id == l.id
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Column(Modifier.offset(x = x), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isCurrent) StartBubble(color)
                    PathNode(l, color, done, unlocked, isCurrent) { onLesson(l) }
                    Text(
                        l.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        color = if (unlocked) MaterialTheme.colorScheme.onBackground else LocalPalette.current.dim,
                        modifier = Modifier.width(120.dp).padding(top = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StartBubble(color: Color) {
    Box(
        Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, LocalPalette.current.border, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text("POČNI", color = color, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp) }
}

@Composable
private fun PathNode(l: Lesson, color: Color, done: Boolean, unlocked: Boolean, current: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val story = l.type == LessonType.STORY
    val face = when {
        !unlocked -> pal.locked
        done && !story -> K.Gold
        else -> if (story) K.Purple else color
    }
    val size = if (current) 78.dp else 70.dp
    Box(
        Modifier.size(size + 14.dp).then(if (current) Modifier.border(5.dp, face.copy(alpha = 0.35f), CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Chunky(Modifier.size(size), color = face, shadow = if (unlocked) K.darker(face, 0.75f) else K.darker(pal.locked, 0.8f), shape = CircleShape, depth = 7.dp, onClick = onClick) {
            Icon(
                when {
                    !unlocked -> Icons.Filled.Lock
                    story -> Icons.AutoMirrored.Filled.MenuBook
                    done -> Icons.Filled.Check
                    else -> Icons.Filled.Star
                },
                l.title, tint = if (unlocked) Color.White else pal.dim, modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun LessonSheet(l: Lesson, onStart: () -> Unit) {
    val app = LocalApp.current
    val pal = LocalPalette.current
    val unlocked = app.progress.isUnlocked(app.course, l)
    val done = app.progress.timesDone(l.id)
    val story = l.type == LessonType.STORY
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
        Text(if (story) "PRIČA" else "LEKCIJA", color = if (story) K.Purple else K.Green, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 1.sp)
        Text(l.title, style = MaterialTheme.typography.titleLarge)
        if (done > 0) Text("Završeno ${done}×", color = pal.dim, fontSize = 13.sp)
        if (l.tip.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(14.dp)) {
                Text("💡", fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Text(l.tip, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        if (!story && l.words.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Reči: " + l.words.take(8).joinToString(", ") { it.sl } + if (l.words.size > 8) "…" else "", color = pal.dim, fontSize = 14.sp)
        }
        Spacer(Modifier.height(20.dp))
        if (unlocked) {
            BigButton(
                when {
                    story && done > 0 -> "Pročitaj ponovo"
                    story -> "Počni priču +15 XP"
                    done > 0 -> "Vežbaj ponovo +10 XP"
                    else -> "Počni +15 XP"
                },
                color = if (story) K.Purple else K.Green, onClick = onStart,
            )
        } else {
            Text("🔒 Završi prethodnu lekciju da otključaš ovu.", color = pal.dim, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            BigButton("Ipak počni", color = K.Blue, onClick = onStart)
        }
    }
}

/** Grammar guidebook for one unit. */
@Composable
fun GuideScreen(unitIdx: Int, pad: PaddingValues, onBack: () -> Unit) {
    val app = LocalApp.current
    val pal = LocalPalette.current
    val u = app.course.units.getOrNull(unitIdx) ?: return
    val color = K.units[u.idx % K.units.size]
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(color).statusBarsPadding().padding(20.dp)) {
                Text("← Nazad", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(bottom = 12.dp))
                Text("VODIČ · JEDINICA ${u.idx + 1}", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 0.8.sp)
                Text(u.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            }
        }
        items(u.guide.size) { i ->
            Text(u.guide[i], fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }
        u.lessons.filter { it.type == LessonType.LESSON }.forEach { l ->
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp)).border(2.dp, pal.border, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Text(l.title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    if (l.tip.isNotBlank()) Text(l.tip, fontSize = 14.sp, color = pal.dim, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                    l.sentences.take(4).forEach { s ->
                        Row(Modifier.fillMaxWidth().clickable { app.speaker.play(s.text) }.padding(vertical = 5.dp)) {
                            Text("🔊 ", fontSize = 14.sp)
                            Column {
                                Text(s.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = color)
                                Text(s.meaning, fontSize = 13.sp, color = pal.dim)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("unused") private val keep = Arrangement.Center
