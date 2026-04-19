package com.emabuia.pokevault.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.emabuia.pokevault.data.remote.RepositoryProvider
import timber.log.Timber

class SetsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            Timber.d("SetsSyncWorker: avvio sync set in background")
            val result = RepositoryProvider.tcgRepository.getSets(
                context = applicationContext,
                forceRefresh = true
            )
            if (result.isSuccess) {
                Timber.d("SetsSyncWorker: sync completato, %d set aggiornati", result.getOrNull()?.size ?: 0)
                Result.success()
            } else {
                Timber.w("SetsSyncWorker: errore sync → retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "SetsSyncWorker: eccezione durante sync")
            Result.retry()
        }
    }
}
