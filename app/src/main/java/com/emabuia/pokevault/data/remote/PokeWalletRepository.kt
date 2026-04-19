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
    private val CACHE_DURATION_MS = 24L * 60 * 60 * 1000 // 24 hours
    @Volatile
    private var cacheHitCount: Long = 0
    @Volatile
    private var cacheMissCount: Long = 0
    @Volatile
    private var networkCallCount: Long = 0

    data class CacheDiagnostics(
        val hits: Long,
        val misses: Long,
        val networkCalls: Long
    )

    fun getDiagnostics(): CacheDiagnostics {
        return CacheDiagnostics(
            hits = cacheHitCount,
            misses = cacheMissCount,
            networkCalls = networkCallCount
        )
    }

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
            recordCacheHit("prices:memory:$cacheKey")
            return Result.success(memoryCached.first)
        }

        // L2: Room DB
        try {
            val roomCached = db.priceDao().getPrice(cacheKey, setCode.lowercase())
            if (roomCached != null && System.currentTimeMillis() - roomCached.cachedAt < CACHE_DURATION_MS) {
                val data = roomCached.toPriceData()
                priceCache[cacheKey] = Pair(data, roomCached.cachedAt)
                recordCacheHit("prices:room:$cacheKey")
                return Result.success(data)
            }
        } catch (e: Exception) {
            Timber.w(e, "Errore lettura cache prezzi Room")
        }

        recordCacheMiss("prices:$cacheKey")

        // L3: Network
        return try {
            recordNetworkCall("prices:$cacheKey")
            val queryPrimary = if (setCode.isNotBlank() && cleanNumber.isNotBlank()) {
                "$setCode $cleanNumber"
            } else {
                "$cardName $cleanNumber".trim()
            }

            val queryFallback = "$cardName $cleanNumber".trim()

            fun pickMatch(results: List<PokeWalletCard>): PokeWalletCard? {
                return results.firstOrNull { card ->
                    val apiNum = card.cardInfo?.cardNumber?.split("/")?.firstOrNull()?.trim() ?: ""
                    val hasEur = card.cardmarket?.prices?.any { (it.avg ?: 0.0) > 0.0 || (it.low ?: 0.0) > 0.0 } == true
                    apiNum.equals(cleanNumber, ignoreCase = true) && hasEur
                } ?: results.firstOrNull { card ->
                    val hasEur = card.cardmarket?.prices?.any { (it.avg ?: 0.0) > 0.0 || (it.low ?: 0.0) > 0.0 } == true
                    card.cardInfo?.cleanName?.contains(cardName, ignoreCase = true) == true && hasEur
                } ?: results.firstOrNull { card ->
                    card.cardmarket?.prices?.any { (it.avg ?: 0.0) > 0.0 || (it.low ?: 0.0) > 0.0 } == true
                }
            }

            val responsePrimary = apiService.search(queryPrimary, limit = 10)
            val matchPrimary = pickMatch(responsePrimary.results)
            val match = if (matchPrimary != null || queryFallback == queryPrimary) {
                matchPrimary
            } else {
                val responseFallback = apiService.search(queryFallback, limit = 10)
                pickMatch(responseFallback.results)
            }

            if (match != null) {
                val priceData = match.toPriceData()
                val hasEur = (priceData.eurAvg ?: 0.0) > 0.0 || (priceData.eurLow ?: 0.0) > 0.0
                if (hasEur) {
                    priceCache[cacheKey] = Pair(priceData, System.currentTimeMillis())
                    try {
                        db.priceDao().upsertPrice(priceData.toEntity(cacheKey, setCode.lowercase()))
                    } catch (e: Exception) {
                        Timber.w(e, "Errore salvataggio cache prezzi Room")
                    }
                }
                Result.success(priceData)
            } else {
                Result.failure(NoSuchElementException("Nessun prezzo trovato per $cardName"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun recordCacheHit(tag: String) {
        cacheHitCount++
        if (BuildConfig.DEBUG) {
            Timber.d("PW_CACHE_HIT[%s] hit=%d miss=%d net=%d", tag, cacheHitCount, cacheMissCount, networkCallCount)
        }
    }

    private fun recordCacheMiss(tag: String) {
        cacheMissCount++
        if (BuildConfig.DEBUG) {
            Timber.d("PW_CACHE_MISS[%s] hit=%d miss=%d net=%d", tag, cacheHitCount, cacheMissCount, networkCallCount)
        }
    }

    private fun recordNetworkCall(tag: String) {
        networkCallCount++
        if (BuildConfig.DEBUG) {
            Timber.d("PW_NETWORK[%s] hit=%d miss=%d net=%d", tag, cacheHitCount, cacheMissCount, networkCallCount)
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
