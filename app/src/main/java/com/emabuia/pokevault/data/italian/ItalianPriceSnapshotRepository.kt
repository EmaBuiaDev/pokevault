package com.emabuia.pokevault.data.italian

import android.content.Context
import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.data.remote.PokeWalletPriceData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pre-merged ITA price snapshot served by the Cloudflare worker
 * (`/ita/prices.json`). One small JSON download replaces the per-card
 * ITA -> ENG counterpart resolution chain for the common path.
 */
data class ItalianPriceEntry(
    val avg: Double? = null,
    val low: Double? = null,
    val trend: Double? = null,
    val avg1: Double? = null,
    val avg7: Double? = null,
    val avg30: Double? = null,
    /** TCGPlayer USD fallback for sets without CardMarket data upstream. */
    val usd: Double? = null,
    val usdLow: Double? = null,
    val url: String? = null
)

data class ItalianPriceExpansionEntry(
    val baseSetCode: String = "",
    val prices: Map<String, ItalianPriceEntry> = emptyMap()
)

data class ItalianPriceSnapshot(
    val version: Int = 0,
    val builtAt: Long = 0L,
    val expansions: Map<String, ItalianPriceExpansionEntry> = emptyMap(),
    val aliases: Map<String, String> = emptyMap()
) {
    /** Resolves an expansion id or raw set code alias to its price map. */
    fun priceMapFor(lookupCode: String): Map<String, ItalianPriceEntry> {
        val normalized = lookupCode.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return emptyMap()
        val expansionId = aliases[normalized] ?: normalized
        return expansions[expansionId]?.prices ?: emptyMap()
    }
}

class ItalianPriceSnapshotRepository {

    companion object {
        private const val PREFS_NAME = "italian_price_snapshot_v1"
        private const val KEY_JSON = "json"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val CACHE_TTL_MS = 12L * 60L * 60L * 1000L // 12h fresh
        private const val ENDPOINT_PATH = "ita/prices.json"
    }

    private val mutex = Mutex()
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var memorySnapshot: ItalianPriceSnapshot? = null
    @Volatile
    private var memoryUpdatedAt: Long = 0L

    /** Fast, non-suspending access to the snapshot already in memory (may be null). */
    fun cachedSnapshotOrNull(): ItalianPriceSnapshot? = memorySnapshot

    suspend fun getSnapshot(context: Context, forceRefresh: Boolean = false): Result<ItalianPriceSnapshot> =
        mutex.withLock {
            if (!forceRefresh) {
                val memoryAge = System.currentTimeMillis() - memoryUpdatedAt
                memorySnapshot?.takeIf { memoryUpdatedAt > 0L && memoryAge <= CACHE_TTL_MS }
                    ?.let { return Result.success(it) }

                loadFromPrefs(context)?.let { cached ->
                    memorySnapshot = cached
                    memoryUpdatedAt = System.currentTimeMillis()
                    return Result.success(cached)
                }
            }

            val url = resolveEndpointUrl()
                ?: return Result.failure(IllegalStateException("POKEWALLET_PROXY_URL non configurato"))

            val networkResult: Result<ItalianPriceSnapshot> = runCatching {
                val rawJson = fetchSnapshotJson(url)
                withContext(Dispatchers.Default) {
                    parseSnapshot(rawJson) ?: error("Snapshot prezzi ITA non valido")
                }
            }

            networkResult.onSuccess { snapshot ->
                memorySnapshot = snapshot
                memoryUpdatedAt = System.currentTimeMillis()
                saveToPrefs(context, snapshot)
            }

            if (networkResult.isFailure) {
                loadFromPrefs(context, ignoreExpiry = true)?.let { stale ->
                    memorySnapshot = stale
                    memoryUpdatedAt = System.currentTimeMillis()
                    return Result.success(stale)
                }
            }

            networkResult
        }

    /**
     * Convenience: resolves the price map for an ITA lookup code (expansion id
     * or raw set code) already converted to the app-wide [PokeWalletPriceData].
     */
    suspend fun getPriceMap(
        context: Context,
        lookupCode: String,
        forceRefresh: Boolean = false
    ): Map<String, PokeWalletPriceData> {
        val snapshot = getSnapshot(context, forceRefresh).getOrNull() ?: return emptyMap()
        return snapshot.priceMapFor(lookupCode).mapValues { (_, entry) -> entry.toPriceData() }
    }

    private fun resolveEndpointUrl(): String? {
        val base = BuildConfig.POKEWALLET_PROXY_URL.trim().trimEnd('/')
        if (base.isBlank()) return null
        return "$base/$ENDPOINT_PATH"
    }

    private fun parseSnapshot(json: String): ItalianPriceSnapshot? {
        val parsed = runCatching {
            gson.fromJson(json, ItalianPriceSnapshot::class.java)
        }.getOrNull() ?: return null
        if (parsed.builtAt <= 0L || parsed.expansions.isEmpty()) return null
        return parsed
    }

    private suspend fun loadFromPrefs(context: Context, ignoreExpiry: Boolean = false): ItalianPriceSnapshot? =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
            if (updatedAt <= 0L) return@withContext null
            val age = System.currentTimeMillis() - updatedAt
            if (!ignoreExpiry && age > CACHE_TTL_MS) return@withContext null

            val json = prefs.getString(KEY_JSON, null)?.trim().orEmpty()
            if (json.isBlank()) return@withContext null
            parseSnapshot(json)
        }

    private suspend fun saveToPrefs(context: Context, snapshot: ItalianPriceSnapshot) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_JSON, gson.toJson(snapshot))
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply()
        }
    }

    private suspend fun fetchSnapshotJson(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Snapshot prezzi ITA non disponibile: HTTP ${response.code}")
            }
            response.body?.string()?.trim().orEmpty().ifBlank {
                error("Snapshot prezzi ITA vuoto")
            }
        }
    }
}

fun ItalianPriceEntry.toPriceData(): PokeWalletPriceData {
    val hasEur = avg != null || low != null || trend != null
    return PokeWalletPriceData(
        eurAvg = avg,
        eurLow = low,
        eurTrend = trend,
        eurAvg1 = avg1,
        eurAvg7 = avg7,
        eurAvg30 = avg30,
        usdMarket = usd,
        usdLow = usdLow,
        cardMarketUrl = url.takeIf { hasEur },
        tcgPlayerUrl = url.takeIf { !hasEur }
    )
}
