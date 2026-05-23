package com.emabuia.pokevault.data.remote

import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.data.local.toPriceData
import com.emabuia.pokevault.data.local.toEntity
import kotlinx.coroutines.CompletableDeferred
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

class PokeWalletRepository {

    companion object {
        private const val MAX_SET_LOOKUP_CANDIDATES = 6
        const val PRICE_STALE_AFTER_MS: Long = 48L * 60 * 60 * 1000 // 48 hours
    }

    private val apiService: PokeWalletApiService by lazy {
        PokeWalletRetrofitClient.create(BuildConfig.POKEWALLET_API_KEY)
    }

    private val db get() = RepositoryProvider.database

    // L1 in-memory cache
    private val priceCache = ConcurrentHashMap<String, Pair<PokeWalletPriceData, Long>>()
    private val setPriceCache = ConcurrentHashMap<String, Pair<Map<String, PokeWalletPriceData>, Long>>()
    private val inFlightPriceRequests = ConcurrentHashMap<String, CompletableDeferred<Result<PokeWalletPriceData>>>()
    private val inFlightSetPriceRequests = ConcurrentHashMap<String, CompletableDeferred<Result<Map<String, PokeWalletPriceData>>>>()
    private val CACHE_DURATION_MS = PRICE_STALE_AFTER_MS
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

    suspend fun getSetPriceMap(
        setCode: String,
        forceRefresh: Boolean = false
    ): Result<Map<String, PokeWalletPriceData>> {
        val proxyEnabled = BuildConfig.POKEWALLET_PROXY_ENABLED && BuildConfig.POKEWALLET_PROXY_URL.isNotBlank()
        if (!proxyEnabled && BuildConfig.POKEWALLET_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("POKEWALLET_API_KEY not configured"))
        }

        val canonicalSetCode = SetCodeMapper.normalizeDecklistSetCode(setCode)
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: setCode.trim().lowercase()
        if (canonicalSetCode.isBlank()) {
            return Result.failure(IllegalArgumentException("setCode required"))
        }

        if (forceRefresh) {
            setPriceCache.remove(canonicalSetCode)
        }

        val memoryCached = setPriceCache[canonicalSetCode]
        if (!forceRefresh && memoryCached != null && System.currentTimeMillis() - memoryCached.second < CACHE_DURATION_MS) {
            recordCacheHit("set-prices:memory:$canonicalSetCode")
            return Result.success(memoryCached.first)
        }

        val pendingRequest = CompletableDeferred<Result<Map<String, PokeWalletPriceData>>>()
        val existingRequest = inFlightSetPriceRequests.putIfAbsent(canonicalSetCode, pendingRequest)
        if (existingRequest != null) {
            recordCacheHit("set-prices:inflight:$canonicalSetCode")
            return existingRequest.await()
        }

        return try {
            recordNetworkCall("set-prices:$canonicalSetCode")
            val cacheBust = if (forceRefresh) System.currentTimeMillis().toString() else null
            suspend fun mapSetResponse(setCodeToUse: String): Map<String, PokeWalletPriceData> {
                val response = apiService.getSet(
                    setCode = setCodeToUse,
                    limit = 250,
                    cacheBust = cacheBust
                )
                return response.cards.mapNotNull { card ->
                    val numberKey = normalizeCardNumberKey(card.cardInfo?.cardNumber.orEmpty()) ?: return@mapNotNull null
                    val priceData = card.toPriceData()
                    if (!priceData.hasEurPrices) return@mapNotNull null
                    numberKey to priceData
                }.toMap()
            }

            val candidateSetCodes = prioritizedSetLookupCandidates(setCode)

            var mapped = emptyMap<String, PokeWalletPriceData>()
            for (candidate in candidateSetCodes) {
                val resolved = mapSetResponse(candidate)
                if (resolved.isNotEmpty()) {
                    mapped = resolved
                    break
                }
            }

            if (mapped.isNotEmpty()) {
                val now = System.currentTimeMillis()
                setPriceCache[canonicalSetCode] = mapped to now
                mapped.forEach { (numberKey, priceData) ->
                    val cacheKey = "${canonicalSetCode}_${numberKey.lowercase()}"
                    priceCache[cacheKey] = priceData to now
                    try {
                        db.priceDao().upsertPrice(priceData.toEntity(cacheKey, canonicalSetCode))
                    } catch (e: Exception) {
                        Timber.w(e, "Errore salvataggio cache prezzi Room")
                    }
                }
            }

            Result.success(mapped)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            inFlightSetPriceRequests.remove(canonicalSetCode, pendingRequest)
        }
            .also { pendingRequest.complete(it) }
    }

    suspend fun getCardPrices(
        cardName: String,
        setCode: String,
        cardNumber: String,
        forceRefresh: Boolean = false
    ): Result<PokeWalletPriceData> {
        val proxyEnabled = BuildConfig.POKEWALLET_PROXY_ENABLED && BuildConfig.POKEWALLET_PROXY_URL.isNotBlank()
        if (!proxyEnabled && BuildConfig.POKEWALLET_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("POKEWALLET_API_KEY not configured"))
        }

        val cleanNumber = cardNumber.split("/").firstOrNull()?.trim() ?: cardNumber
        val cacheKey = "${setCode.lowercase()}_${cleanNumber.lowercase()}"
        val requestedSetCanonical = SetCodeMapper.normalizeDecklistSetCode(setCode)

        if (forceRefresh) {
            priceCache.remove(cacheKey)
        }

        // L1: Memory
        val memoryCached = priceCache[cacheKey]
        if (!forceRefresh && memoryCached != null && System.currentTimeMillis() - memoryCached.second < CACHE_DURATION_MS) {
            recordCacheHit("prices:memory:$cacheKey")
            return Result.success(memoryCached.first)
        }

        // L2: Room DB
        try {
            val roomCached = db.priceDao().getPrice(cacheKey, setCode.lowercase())
            if (!forceRefresh && roomCached != null && System.currentTimeMillis() - roomCached.cachedAt < CACHE_DURATION_MS) {
                val data = roomCached.toPriceData()
                priceCache[cacheKey] = Pair(data, roomCached.cachedAt)
                recordCacheHit("prices:room:$cacheKey")
                return Result.success(data)
            }
        } catch (e: Exception) {
            Timber.w(e, "Errore lettura cache prezzi Room")
        }

        recordCacheMiss("prices:$cacheKey")

        val pendingRequest = CompletableDeferred<Result<PokeWalletPriceData>>()
        val existingRequest = inFlightPriceRequests.putIfAbsent(cacheKey, pendingRequest)
        if (existingRequest != null) {
            recordCacheHit("prices:inflight:$cacheKey")
            return existingRequest.await()
        }

        // L3: Network
        val result = try {
            recordNetworkCall("prices:$cacheKey")
            val cacheBust = if (forceRefresh) System.currentTimeMillis().toString() else null
            val queryPrimary = if (setCode.isNotBlank() && cleanNumber.isNotBlank()) {
                "$setCode $cleanNumber"
            } else {
                "$cardName $cleanNumber".trim()
            }

            val queryFallback = "$cardName $cleanNumber".trim()

            fun hasEur(card: PokeWalletCard): Boolean {
                return card.cardmarket?.prices?.any { (it.avg ?: 0.0) > 0.0 || (it.low ?: 0.0) > 0.0 } == true
            }

            fun sameRequestedSet(card: PokeWalletCard): Boolean {
                if (requestedSetCanonical.isNullOrBlank()) return true
                val info = card.cardInfo
                val candidates = linkedSetOf<String>()
                SetCodeMapper.normalizeDecklistSetCode(info?.setCode)?.let { candidates += it }
                SetCodeMapper.normalizeDecklistSetCode(info?.setId)?.let { candidates += it }
                SetCodeMapper.normalizeDecklistSetCode(info?.setName)?.let { candidates += it }
                return requestedSetCanonical in candidates
            }

            fun cardNumberMatches(card: PokeWalletCard): Boolean {
                val apiNum = card.cardInfo?.cardNumber?.split("/")?.firstOrNull()?.trim() ?: ""
                return apiNum.equals(cleanNumber, ignoreCase = true)
            }

            fun pickMatch(results: List<PokeWalletCard>): PokeWalletCard? {
                return results.firstOrNull { card ->
                    cardNumberMatches(card) && sameRequestedSet(card) && hasEur(card)
                } ?: results.firstOrNull { card ->
                    cardNumberMatches(card) && hasEur(card)
                } ?: results.firstOrNull { card ->
                    sameRequestedSet(card) && card.cardInfo?.cleanName?.contains(cardName, ignoreCase = true) == true && hasEur(card)
                } ?: results.firstOrNull { card ->
                    sameRequestedSet(card) && hasEur(card)
                }
            }

            var setResponse: PokeWalletSetDetailResponse? = null
            for (candidate in prioritizedSetLookupCandidates(setCode)) {
                val response = runCatching {
                    apiService.getSet(
                        setCode = candidate,
                        limit = 250,
                        cacheBust = cacheBust
                    )
                }.getOrNull()
                if (response != null) {
                    setResponse = response
                    break
                }
            }
            val setMatch = setResponse?.cards?.firstOrNull { card ->
                cardNumberMatches(card) && sameRequestedSet(card) && hasEur(card)
            } ?: setResponse?.cards?.firstOrNull { card ->
                cardNumberMatches(card) && hasEur(card)
            }

            val match = if (setMatch != null) {
                setMatch
            } else {
                val responsePrimary = apiService.search(queryPrimary, limit = 10, cacheBust = cacheBust)
                val matchPrimary = pickMatch(responsePrimary.results)
                if (matchPrimary != null || queryFallback == queryPrimary) {
                    matchPrimary
                } else {
                    val responseFallback = apiService.search(queryFallback, limit = 10, cacheBust = cacheBust)
                    pickMatch(responseFallback.results)
                }
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
        } finally {
            inFlightPriceRequests.remove(cacheKey, pendingRequest)
        }

        pendingRequest.complete(result)
        return result
    }

    suspend fun isCardPriceStale(
        setCode: String,
        cardNumber: String,
        staleAfterMs: Long = PRICE_STALE_AFTER_MS
    ): Boolean {
        val cleanNumber = cardNumber.split("/").firstOrNull()?.trim() ?: cardNumber
        if (setCode.isBlank() || cleanNumber.isBlank()) return true

        val cacheKey = "${setCode.lowercase()}_${cleanNumber.lowercase()}"
        val now = System.currentTimeMillis()

        val memoryCached = priceCache[cacheKey]
        if (memoryCached != null) {
            return now - memoryCached.second >= staleAfterMs
        }

        return try {
            val roomCached = db.priceDao().getPrice(cacheKey, setCode.lowercase())
            if (roomCached != null) {
                val data = roomCached.toPriceData()
                priceCache[cacheKey] = Pair(data, roomCached.cachedAt)
                now - roomCached.cachedAt >= staleAfterMs
            } else {
                true
            }
        } catch (e: Exception) {
            Timber.w(e, "Errore lettura stale-state prezzi Room")
            true
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

    private fun normalizeCardNumberKey(raw: String): String? {
        val clean = raw.split("/").firstOrNull()?.trim().orEmpty()
        if (clean.isBlank()) return null
        return clean.toIntOrNull()?.toString() ?: clean.uppercase()
    }

    private fun buildSetLookupCandidates(rawSetCode: String): List<String> {
        val candidates = linkedSetOf<String>()
        rawSetCode.trim().takeIf { it.isNotBlank() }?.let { raw ->
            candidates += raw
            candidates += raw.uppercase()
            candidates += raw.lowercase()
            SetCodeMapper.normalizeDecklistSetCode(raw)?.let { normalized ->
                candidates += normalized
                candidates += normalized.uppercase()
                candidates += normalized.lowercase()
            }
            SetCodeMapper.searchTokensForSetQuery(raw).forEach { token ->
                candidates += token
                SetCodeMapper.normalizeDecklistSetCode(token)?.let { normalizedToken ->
                    candidates += normalizedToken
                    candidates += normalizedToken.uppercase()
                    candidates += normalizedToken.lowercase()
                }
            }
        }
        return candidates.filter { it.isNotBlank() }
    }

    private fun prioritizedSetLookupCandidates(rawSetCode: String): List<String> {
        val canonical = SetCodeMapper.normalizeDecklistSetCode(rawSetCode)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val prioritized = linkedSetOf<String>()
        if (canonical != null) {
            prioritized += canonical
            prioritized += canonical.lowercase()
            prioritized += canonical.uppercase()
        }

        rawSetCode.trim().takeIf { it.isNotBlank() }?.let { raw ->
            prioritized += raw
            prioritized += raw.lowercase()
            prioritized += raw.uppercase()
        }

        buildSetLookupCandidates(rawSetCode).forEach { prioritized += it }
        return prioritized
            .filter { it.isNotBlank() }
            .take(MAX_SET_LOOKUP_CANDIDATES)
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
