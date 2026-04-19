package com.emabuia.pokevault.data.remote

import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.data.local.toPriceData
import com.emabuia.pokevault.data.local.toEntity
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

class PokeWalletRepository {

    private val apiService: PokeWalletApiService by lazy {
        PokeWalletRetrofitClient.create(BuildConfig.POKEWALLET_API_KEY)
    }

    private val db get() = RepositoryProvider.database

    // L1 in-memory cache
    private val priceCache = ConcurrentHashMap<String, Pair<PokeWalletPriceData, Long>>()
    private val CACHE_DURATION_MS = 12L * 60 * 60 * 1000 // 12 hours

    suspend fun getCardPrices(
        cardName: String,
        setCode: String,
        cardNumber: String
    ): Result<PokeWalletPriceData> {
        if (BuildConfig.POKEWALLET_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("POKEWALLET_API_KEY not configured"))
        }

        val cleanNumber = cardNumber.split("/").firstOrNull()?.trim() ?: cardNumber
        val cacheKey = "${setCode.lowercase()}_${cleanNumber.lowercase()}"

        // L1: Memory
        val memoryCached = priceCache[cacheKey]
        if (memoryCached != null && System.currentTimeMillis() - memoryCached.second < CACHE_DURATION_MS) {
            return Result.success(memoryCached.first)
        }

        // L2: Room DB
        try {
            val roomCached = db.priceDao().getPrice(cacheKey, setCode.lowercase())
            if (roomCached != null && System.currentTimeMillis() - roomCached.cachedAt < CACHE_DURATION_MS) {
                val data = roomCached.toPriceData()
                priceCache[cacheKey] = Pair(data, roomCached.cachedAt)
                return Result.success(data)
            }
        } catch (e: Exception) {
            Timber.w(e, "Errore lettura cache prezzi Room")
        }

        // L3: Network
        return try {
            val query = if (setCode.isNotBlank() && cleanNumber.isNotBlank()) {
                "$setCode $cleanNumber"
            } else {
                cardName
            }

            val response = apiService.search(query, limit = 10)

            val match = response.results.firstOrNull { card ->
                val apiNum = card.cardInfo?.cardNumber?.split("/")?.firstOrNull()?.trim() ?: ""
                apiNum.equals(cleanNumber, ignoreCase = true)
            } ?: response.results.firstOrNull { card ->
                card.cardInfo?.cleanName?.contains(cardName, ignoreCase = true) == true
            } ?: response.results.firstOrNull()

            if (match != null) {
                val priceData = match.toPriceData()
                priceCache[cacheKey] = Pair(priceData, System.currentTimeMillis())
                try {
                    db.priceDao().upsertPrice(priceData.toEntity(cacheKey, setCode.lowercase()))
                } catch (e: Exception) {
                    Timber.w(e, "Errore salvataggio cache prezzi Room")
                }
                Result.success(priceData)
            } else {
                Result.failure(NoSuchElementException("Nessun prezzo trovato per $cardName"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun PokeWalletCard.toPriceData(): PokeWalletPriceData {
        val cmPrice = cardmarket?.prices
            ?.firstOrNull { it.variantType == "normal" }
            ?: cardmarket?.prices?.firstOrNull()

        val tcgPrice = tcgplayer?.prices
            ?.firstOrNull { it.subTypeName?.equals("Normal", ignoreCase = true) == true }
            ?: tcgplayer?.prices?.firstOrNull()

        return PokeWalletPriceData(
            eurAvg = cmPrice?.avg,
            eurLow = cmPrice?.low,
            eurTrend = cmPrice?.trend,
            eurAvg1 = cmPrice?.avg1,
            eurAvg7 = cmPrice?.avg7,
            eurAvg30 = cmPrice?.avg30,
            eurVariantType = cmPrice?.variantType,
            usdMarket = tcgPrice?.marketPrice,
            usdLow = tcgPrice?.lowPrice,
            cardMarketUrl = cardmarket?.productUrl,
            tcgPlayerUrl = tcgplayer?.url
        )
    }
}
