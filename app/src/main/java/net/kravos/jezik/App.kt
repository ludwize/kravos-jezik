package net.kravos.jezik

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.kravos.jezik.audio.Speaker
import net.kravos.jezik.data.Course
import net.kravos.jezik.data.Http
import net.kravos.jezik.data.Progress
import net.kravos.jezik.data.Settings
import net.kravos.jezik.work.WordWorker

class App : Application() {
    lateinit var settings: Settings
        private set
    /** Bundled course languages ("sl", "es", …). */
    lateinit var langs: List<String>
        private set

    private val courses = mutableMapOf<String, Course>()
    private val progresses = mutableMapOf<String, Progress>()
    private val speakers = mutableMapOf<String, Speaker>()

    private val _lang = MutableStateFlow("sl")
    /** The language being learned right now; the whole UI is rebuilt when it changes. */
    val lang: StateFlow<String> = _lang

    val course: Course get() = courseFor(_lang.value)
    val progress: Progress get() = progressFor(_lang.value)
    val speaker: Speaker get() = speakerFor(_lang.value)

    /** Set by the emulator smoke test: every answer counts as correct. */
    var testMode = false

    override fun onCreate() {
        super.onCreate()
        Http.init(this)
        settings = Settings(this)
        langs = Course.available(this).ifEmpty { listOf("sl") }
        _lang.value = settings.lang.takeIf { it in langs } ?: langs.first()
        WordWorker.schedule(this)
    }

    @Synchronized fun courseFor(l: String): Course = courses.getOrPut(l) { Course.load(this, l) }
    @Synchronized fun progressFor(l: String): Progress = progresses.getOrPut(l) { Progress(this, courseFor(l), settings) }
    @Synchronized fun speakerFor(l: String): Speaker = speakers.getOrPut(l) { Speaker(this, courseFor(l), progressFor(l)) }

    fun switchTo(l: String) {
        if (l == _lang.value || l !in langs) return
        speakers[_lang.value]?.stop()
        settings.lang = l
        _lang.value = l
    }
}
