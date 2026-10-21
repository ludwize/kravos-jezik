package net.kravos.jezik

import android.app.Application
import net.kravos.jezik.audio.Speaker
import net.kravos.jezik.data.Course
import net.kravos.jezik.data.Http
import net.kravos.jezik.data.Progress
import net.kravos.jezik.work.WordWorker

class App : Application() {
    lateinit var progress: Progress
        private set
    lateinit var course: Course
        private set
    lateinit var speaker: Speaker
        private set

    /** Set by the emulator smoke test: every answer counts as correct. */
    var testMode = false

    override fun onCreate() {
        super.onCreate()
        Http.init(this)
        progress = Progress(this)
        course = Course.load(this, "sl")
        speaker = Speaker(this, course, progress)
        WordWorker.schedule(this)
    }
}
