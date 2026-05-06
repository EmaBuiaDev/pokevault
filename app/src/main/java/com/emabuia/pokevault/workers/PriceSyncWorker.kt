package com.emabuia.pokevault.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.emabuia.pokevault.data.local.toPriceData
import com.emabuia.pokevault.data.local.toEntity
import com.emabuia.pokevault.data.remote.RepositoryProvider
import timber.log.Timber

class PriceSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = RepositoryProvider.database
            val repo = RepositoryProvider.pokeWalletRepository
            val threshold = System.currentTimeMillis() - 24L * 60 * 60 * 1000 // 24 hours

            val stalePrices = db.priceDao().getExpiredPrices(threshold, limit = 50)
            Timber.d("PriceSyncWorker: %d prezzi da aggiornare", stalePrices.size)

            var successCount = 0
            for (price in stalePrices) {
                // I cardId hanno formato "{setCode}_{number}" ma alcune entry
                // legacy potrebbero non avere il separatore: in quel caso
                // facciamo fallback sull'intero id come numero per evitare
                // di passare una stringa vuota a getCardPrices.
                val cardNumber = price.cardId.substringAfter('_', missingDelimiterValue = price.cardId)
                if (cardNumber.isBlank()) {
                    Timber.w("PriceSyncWorker: skip cardId malformato '%s'", price.cardId)
                    continue
                }
                val result = repo.getCardPrices(
                    cardName = "",
                    setCode = price.setCode,
                    cardNumber = cardNumber
                )
                if (result.isSuccess) successCount++
            }

            Timber.d("PriceSyncWorker: %d/%d prezzi aggiornati", successCount, stalePrices.size)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "PriceSyncWorker: eccezione durante sync")
            Result.retry()
        }
    }
}
