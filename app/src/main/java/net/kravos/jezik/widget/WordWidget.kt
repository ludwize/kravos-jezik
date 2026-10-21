package net.kravos.jezik.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.kravos.jezik.App
import net.kravos.jezik.R
import net.kravos.jezik.data.Word
import net.kravos.jezik.ui.MainActivity
import java.time.LocalDate

/** "Reč dana" — one word per day from the words learned so far (or from the first unit). */
object WordOfDay {
    fun pick(app: App, day: Long = LocalDate.now().toEpochDay()): Pair<Word, String?> {
        val c = app.course
        val learned = c.words.filter { app.progress.isLearned(it.key) }
        val pool = learned.ifEmpty { c.units.first().lessons.flatMap { it.words } }.ifEmpty { c.words }
        // stable but scrambled order per day
        val w = pool[((day * 2654435761L) % pool.size).toInt().let { if (it < 0) it + pool.size else it }]
        val example = c.sentences.firstOrNull { s -> s.text.lowercase().split(Regex("[^\\p{L}]+")).contains(w.sl.lowercase()) }
        return w to example?.let { "${it.text} — ${it.meaning}" }
    }
}

class WordWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(160.dp, 80.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as App
        val (w, ex) = WordOfDay.pick(app)
        val streak = app.progress.streak()
        provideContent { Content(w, ex, streak) }
    }

    @Composable
    private fun Content(w: Word, example: String?, streak: Int) {
        val white = ColorProvider(Color.White)
        val dim = ColorProvider(Color(0xB3FFFFFF))
        val green = ColorProvider(Color(0xFF58CC02))
        val big = LocalSize.current.height.value >= 150
        val intent = Intent().setClassName("net.kravos.jezik", "net.kravos.jezik.ui.MainActivity").putExtra(MainActivity.EXTRA_WORDS, true)
        Column(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(horizontal = 14.dp, vertical = 12.dp)
                .clickable(actionStartActivity(intent)),
        ) {
            Row {
                Text("REČ DANA", style = TextStyle(color = green, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Text(if (streak > 0) "   🔥 $streak" else "", style = TextStyle(color = dim, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(w.sl, style = TextStyle(color = white, fontSize = 24.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(w.meaning, style = TextStyle(color = dim, fontSize = 15.sp), maxLines = 1)
            if (big && w.note.isNotBlank()) {
                Spacer(GlanceModifier.height(6.dp))
                Text("💡 ${w.note}", style = TextStyle(color = dim, fontSize = 12.sp), maxLines = 2)
            }
            if (big && example != null) {
                Spacer(GlanceModifier.height(6.dp))
                Text(example, style = TextStyle(color = white, fontSize = 13.sp), maxLines = 3)
            }
        }
    }

    companion object {
        fun refreshAsync(ctx: Context) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { WordWidget().updateAll(ctx.applicationContext) } }
        }
    }
}

class WordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WordWidget()
}
