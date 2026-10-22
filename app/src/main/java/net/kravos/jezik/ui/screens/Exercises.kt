package net.kravos.jezik.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import net.kravos.jezik.data.Ex
import net.kravos.jezik.ui.LocalApp
import net.kravos.jezik.ui.components.Chunky
import net.kravos.jezik.ui.components.OptState
import net.kravos.jezik.ui.components.OptionCard
import net.kravos.jezik.ui.components.SpeakerButton
import net.kravos.jezik.ui.components.WordTile
import net.kravos.jezik.ui.theme.K
import net.kravos.jezik.ui.theme.LocalPalette

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 18.dp))
}

/** Big + slow ("turtle") playback buttons for listening exercises. */
@Composable
private fun ListenButtons(text: String) {
    val sp = LocalApp.current.speaker
    LaunchedEffect(text) { sp.play(text) }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        SpeakerButton(96.dp) { sp.play(text) }
        Spacer(Modifier.width(18.dp))
        Chunky(Modifier.size(64.dp), color = K.Blue, shape = RoundedCornerShape(14.dp), onClick = { sp.play(text, slow = true) }) {
            Text("🐢", fontSize = 28.sp)
        }
    }
}

/** Slovenian prompt with a small speaker in front; plays once on show. */
@Composable
private fun SlPrompt(text: String, size: Int = 24, autoplay: Boolean = true) {
    val sp = LocalApp.current.speaker
    if (autoplay) LaunchedEffect(text) { sp.play(text) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SpeakerButton(44.dp) { sp.play(text) }
        Spacer(Modifier.width(12.dp))
        Bubble(text, size)
    }
}

@Composable
private fun Bubble(text: String, size: Int) {
    val pal = LocalPalette.current
    Box(Modifier.clip(RoundedCornerShape(14.dp)).border(2.dp, pal.border, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(text, fontSize = size.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun IntroView(ex: Ex.Intro) {
    val sp = LocalApp.current.speaker
    val pal = LocalPalette.current
    LaunchedEffect(ex.uid) { sp.play(ex.word.sl) }
    Text("NOVA REČ", color = K.Purple, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 1.sp)
    Spacer(Modifier.height(28.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SpeakerButton(64.dp) { sp.play(ex.word.sl) }
        Spacer(Modifier.height(18.dp))
        Text(ex.word.sl, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(ex.word.meaning, fontSize = 22.sp, color = pal.dim, textAlign = TextAlign.Center)
        if (ex.word.note.isNotBlank()) {
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("💡", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Text(ex.word.note, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
fun PickView(ex: Ex.Pick, pending: String?, locked: Boolean, onPick: (String) -> Unit) {
    Title(ex.title)
    when {
        ex.listen -> ListenButtons(ex.prompt)
        ex.promptIsSl -> SlPrompt(ex.prompt, 28)
        else -> Text(ex.prompt, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(26.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ex.options.forEach { o ->
            val st = when {
                locked && o == ex.answer -> OptState.RIGHT
                locked && o == pending -> OptState.WRONG
                locked -> OptState.NORMAL
                o == pending -> OptState.SELECTED
                else -> OptState.NORMAL
            }
            OptionCard(o, st, Modifier.fillMaxWidth(), enabled = !locked) { onPick(o) }
        }
    }
}

@Composable
fun FillView(ex: Ex.Fill, pending: String?, locked: Boolean, onPick: (String) -> Unit) {
    val pal = LocalPalette.current
    Title("Popuni prazninu")
    val shown = ex.g.prompt.replace("___", if (pending != null) pending else "_____")
    Text(shown, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    Text(ex.g.sr, color = pal.dim, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(26.dp))
    ex.options.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { o ->
                val st = when {
                    locked && o == ex.g.answer -> OptState.RIGHT
                    locked && o == pending -> OptState.WRONG
                    locked -> OptState.NORMAL
                    o == pending -> OptState.SELECTED
                    else -> OptState.NORMAL
                }
                OptionCard(o, st, Modifier.weight(1f), enabled = !locked) { onPick(o) }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArrangeView(ex: Ex.Arrange, locked: Boolean, onChange: (String?) -> Unit) {
    val pal = LocalPalette.current
    val chosen = remember(ex.uid) { mutableStateListOf<Int>() }
    fun push() = onChange(if (chosen.isEmpty()) null else chosen.joinToString(" ") { ex.tiles[it] })

    Title(ex.title)
    when {
        ex.listen -> ListenButtons(ex.prompt)
        ex.promptIsSl -> SlPrompt(ex.prompt, 20)
        else -> Bubble(ex.prompt, 20)
    }
    Spacer(Modifier.height(22.dp))
    Box(Modifier.fillMaxWidth().heightIn(min = 112.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 54.dp)) {
            HorizontalDivider(thickness = 2.dp, color = pal.border)
            Spacer(Modifier.height(52.dp))
            HorizontalDivider(thickness = 2.dp, color = pal.border)
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chosen.forEach { i ->
                WordTile(ex.tiles[i], used = false) { if (!locked) { chosen.remove(i); push() } }
            }
        }
    }
    Spacer(Modifier.height(28.dp))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ex.tiles.forEachIndexed { i, t ->
            WordTile(t, used = i in chosen) { if (!locked) { chosen.add(i); push() } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypeView(ex: Ex.Type, locked: Boolean, onChange: (String?) -> Unit) {
    val pal = LocalPalette.current
    var v by remember(ex.uid) { mutableStateOf(TextFieldValue("")) }
    fun set(n: TextFieldValue) { v = n; onChange(n.text.ifBlank { null }) }

    Title(ex.title)
    if (ex.listen) ListenButtons(ex.prompt) else Bubble(ex.prompt, 22)
    Spacer(Modifier.height(22.dp))
    OutlinedTextField(
        value = v,
        onValueChange = { if (!locked) set(it) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        placeholder = { Text(if (ex.answerIsSl) "Piši na ${LocalApp.current.course.loc}" else "Piši na srpskom", color = pal.dim) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = pal.border, focusedBorderColor = K.Blue, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
    val chars = LocalApp.current.course.chars
    if (ex.answerIsSl && chars.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chars.forEach { ch ->
                WordTile(ch, used = false) {
                    if (!locked) {
                        val s = v.selection.start.coerceIn(0, v.text.length)
                        val e = v.selection.end.coerceIn(0, v.text.length)
                        val t = v.text.substring(0, s) + ch + v.text.substring(e)
                        set(TextFieldValue(t, TextRange(s + 1)))
                    }
                }
            }
        }
    }
}

@Composable
fun SpeakView(ex: Ex.Speak, locked: Boolean, onResult: (String?) -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val sp = LocalApp.current.speaker
    val langTag = LocalApp.current.course.tts
    var listening by remember(ex.uid) { mutableStateOf(false) }
    var heard by remember(ex.uid) { mutableStateOf<String?>(null) }
    var msg by remember(ex.uid) { mutableStateOf<String?>(null) }
    val recognizer = remember(ex.uid) { runCatching { SpeechRecognizer.createSpeechRecognizer(ctx) }.getOrNull() }
    DisposableEffect(recognizer) { onDispose { runCatching { recognizer?.destroy() } } }

    fun start() {
        val r = recognizer ?: run { msg = "Prepoznavanje govora nije dostupno"; return }
        sp.stop()
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true; msg = null }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                msg = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) "Nisam ništa razumeo — probaj ponovo" else "Greška mikrofona ($error) — probaj ponovo"
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                heard = list.firstOrNull()
                onResult(list.joinToString("\n").ifBlank { null })
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        runCatching { r.startListening(i) }.onFailure { msg = "Ne mogu da pokrenem mikrofon" }
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) start() else msg = "Treba dozvola za mikrofon" }

    Title("Izgovori ovu rečenicu")
    SlPrompt(ex.text, 22)
    Text(ex.meaning, color = pal.dim, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
    Spacer(Modifier.height(40.dp))
    Chunky(
        Modifier.fillMaxWidth(), color = if (listening) K.Red else MaterialTheme.colorScheme.background,
        shadow = if (listening) K.RedDark else pal.border, border = if (listening) K.Red else pal.border, enabled = !locked,
        onClick = {
            if (listening) { recognizer?.stopListening(); listening = false }
            else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
            else perm.launch(Manifest.permission.RECORD_AUDIO)
        },
    ) {
        Row(Modifier.padding(vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Mic, null, tint = if (listening) Color.White else K.Blue, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (listening) "SLUŠAM…" else "PRITISNI I GOVORI", fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp,
                color = if (listening) Color.White else K.Blue,
            )
        }
    }
    heard?.let { Text("„$it“", fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp)) }
    msg?.let { Text(it, color = K.Red, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
}

@Composable
fun MatchView(ex: Ex.Match, onMiss: (List<String>) -> Unit, onSolved: () -> Unit) {
    val sp = LocalApp.current.speaker
    val left = remember(ex.uid) { ex.pairs.map { it.first }.shuffled() }
    val right = remember(ex.uid) { ex.pairs.map { it.second }.shuffled() }
    val solved = remember(ex.uid) { mutableStateListOf<String>() } // solved left (sl) items
    var selL by remember(ex.uid) { mutableStateOf<String?>(null) }
    var selR by remember(ex.uid) { mutableStateOf<String?>(null) }
    var bad by remember(ex.uid) { mutableStateOf<Pair<String, String>?>(null) }

    fun tryPair() {
        val l = selL ?: return
        val r = selR ?: return
        if (ex.pairs.any { it.first == l && it.second == r }) {
            solved.add(l)
            selL = null; selR = null
            if (solved.size == ex.pairs.size) onSolved()
        } else {
            bad = l to r
            onMiss(ex.words.filter { it.sl == l || it.sr.first() == r }.map { it.key })
            selL = null; selR = null
        }
    }
    LaunchedEffect(bad) { if (bad != null) { delay(700); bad = null } }

    Title("Spoji parove")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            left.forEach { l ->
                val st = when {
                    l in solved -> OptState.DONE
                    bad?.first == l -> OptState.WRONG
                    selL == l -> OptState.SELECTED
                    else -> OptState.NORMAL
                }
                OptionCard(l, st, Modifier.fillMaxWidth(), fontSize = 16) { sp.play(l); selL = l; tryPair() }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            right.forEach { r ->
                val done = ex.pairs.any { it.second == r && it.first in solved }
                val st = when {
                    done -> OptState.DONE
                    bad?.second == r -> OptState.WRONG
                    selR == r -> OptState.SELECTED
                    else -> OptState.NORMAL
                }
                OptionCard(r, st, Modifier.fillMaxWidth(), fontSize = 16) { selR = r; tryPair() }
            }
        }
    }
}
