package net.kravos.jezik.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.kravos.jezik.widget.WordWidget
import java.util.concurrent.TimeUnit

/** Refreshes the "Reč dana" widget a few times a day so it rolls over after midnight. */
class WordWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { WordWidget().updateAll(applicationContext) }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<WordWorker>(4, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("word-of-day", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
