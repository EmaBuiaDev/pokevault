package com.emabuia.pokevault.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.emabuia.pokevault.data.remote.RepositoryProvider
import timber.log.Timber

class CacheCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = RepositoryProvider.database
            val now = System.currentTimeMillis()

            // Delete records older than 2x TTL
            val setsThreshold = now - 14L * 24 * 60 * 60 * 1000    // 14 days
            val cardsThreshold = now - 60L * 24 * 60 * 60 * 1000   // 60 days
            val pricesThreshold = now - 48L * 60 * 60 * 1000        // 48 hours

            db.setDao().deleteExpired(setsThreshold)
            db.cardDao().deleteExpired(cardsThreshold)
            db.priceDao().deleteExpired(pricesThreshold)

            Timber.d("CacheCleanupWorker: pulizia completata")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "CacheCleanupWorker: eccezione durante pulizia")
            Result.retry()
        }
    }
}
