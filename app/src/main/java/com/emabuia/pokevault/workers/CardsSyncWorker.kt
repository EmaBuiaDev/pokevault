package com.emabuia.pokevault.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.emabuia.pokevault.data.remote.RepositoryProvider
import timber.log.Timber

class CardsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = RepositoryProvider.database
            val repo = RepositoryProvider.tcgRepository
            val threshold = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000 // 30 days

            val visitedSetIds = db.cardDao().getVisitedSetIds()
            val staleSetIds = visitedSetIds.filter { setId ->
                val lastTime = db.cardDao().getLastCacheTimeForSet(setId) ?: 0L
                lastTime < threshold
            }.take(10) // Max 10 sets per run to limit credit usage

            Timber.d("CardsSyncWorker: %d/%d set da aggiornare", staleSetIds.size, visitedSetIds.size)

            var successCount = 0
            for (setId in staleSetIds) {
                val result = repo.getCardsBySet(setId, context = applicationContext, forceRefresh = false)
                if (result.isSuccess) successCount++
            }

            Timber.d("CardsSyncWorker: %d/%d set aggiornati con successo", successCount, staleSetIds.size)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "CardsSyncWorker: eccezione durante sync")
            Result.retry()
        }
    }
}
