package net.kravos.jezik.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.kravos.jezik.data.Course
import net.kravos.jezik.data.Http
import net.kravos.jezik.data.Progress
import net.kravos.jezik.data.Text
import java.util.Locale

/**
 * Plays Slovenian audio: bundled Piper recordings first (generated at build time),
 * the system text-to-speech as a fallback.
 */
class Speaker(ctx: Context, private val course: Course, private val progress: Progress) {
    private val app = ctx.applicationContext
    private val dir = "audio/${course.lang}"
    private val clips: Set<String> = runCatching { app.assets.list(dir)?.filter { it.endsWith(".ogg") }?.toSet() }.getOrNull() ?: emptySet()
    val clipCount: Int get() = clips.size

    private var tts: TextToSpeech? = null
    private val _ttsLang = MutableStateFlow<Boolean?>(null)
    /** null = still starting, true = system TTS speaks Slovenian, false = it does not. */
    val ttsLang: StateFlow<Boolean?> = _ttsLang

    private var player: MediaPlayer? = null

    init {
        Log.i(Http.TAG, "audio clips bundled: ${clips.size}")
        tts = TextToSpeech(app) { st ->
            val ok = if (st == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale.forLanguageTag(course.tts)) ?: TextToSpeech.LANG_NOT_SUPPORTED
                r >= TextToSpeech.LANG_AVAILABLE
            } else false
            _ttsLang.value = ok
            Log.i(Http.TAG, "tts init status=$st slovenian=$ok")
        }
    }

    fun hasClip(text: String) = "${Text.audioKey(text)}.ogg" in clips

    /** Whether listening exercises make sense at all. */
    fun canPlay(): Boolean = clips.isNotEmpty() || _ttsLang.value == true

    fun play(text: String, slow: Boolean = false) {
        if (!progress.soundOn) return
        stop()
        val f = "${Text.audioKey(text)}.ogg"
        if (f in clips) {
            val ok = runCatching {
                val afd = app.assets.openFd("$dir/$f")
                val mp = MediaPlayer()
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                mp.prepare()
                if (slow) mp.playbackParams = mp.playbackParams.setSpeed(0.7f)
                if (!mp.isPlaying) mp.start()
                mp.setOnCompletionListener { it.release(); if (player === it) player = null }
                player = mp
            }.onFailure { Log.w(Http.TAG, "clip failed: ${it.message}") }.isSuccess
            if (ok) return
        }
        if (_ttsLang.value == true) {
            tts?.setSpeechRate(if (slow) 0.6f else 0.95f)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "k")
        }
    }

    fun stop() {
        runCatching { player?.stop(); player?.release() }
        player = null
        runCatching { tts?.stop() }
    }
}
