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
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Pre-merged ITA price snapshot served by the Cloudflare worker. One download
 * replaces the per-card ITA -> ENG counterpart resolution chain, and keeps
 * PokeWallet out of the per-user path entirely: a set the snapshot covers costs
 * zero upstream calls no matter how many cards are opened, which matters
 * because PokeWallet allows 100 calls/hour for the whole user base.
 *
 * Two endpoints, picked by what the caller actually needs:
 *  - `/ita/prices/{code}.json` for ONE expansion (~20 KB), used when opening a
 *    set. This is the latency path.
 *  - `/ita/prices.json` for the whole catalogue (~354 KB gzipped), only for
 *    callers that genuinely span sets, e.g. pricing mixed search results.
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

/** Payload of `/ita/prices/{code}.json` -- one expansion, alias already resolved. */
data class ItalianExpansionPrices(
    val expansionId: String = "",
    val baseSetCode: String = "",
    val updatedAt: Long = 0L,
    val builtAt: Long = 0L,
    val prices: Map<String, ItalianPriceEntry> = emptyMap()
)

class ItalianPriceSnapshotRepository {

    companion object {
        /**
         * The snapshot used to live in SharedPreferences, which loads and
         * rewrites its whole XML file as a unit: a ~2.5 MB JSON string there
         * was re-read and re-parsed on every cold start just to price one set.
         * Plain files under filesDir instead, with lastModified() as the
         * timestamp, so there is no wrapper object and no second parse.
         */
        private const val LEGACY_PREFS_NAME = "italian_price_snapshot_v1"
        private const val CACHE_DIR_NAME = "ita_prices"
        private const val SNAPSHOT_FILE_NAME = "snapshot.json"
        private const val EXPANSION_FILE_PREFIX = "exp_"
        private const val CACHE_TTL_MS = 12L * 60L * 60L * 1000L // 12h fresh
        private const val ENDPOINT_PATH = "ita/prices.json"
        private const val EXPANSION_ENDPOINT_DIR = "ita/prices"
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
    @Volatile
    private var legacyPrefsCleared: Boolean = false

    /** Per-expansion L1, keyed by every code that resolved to it. */
    private val expansionMemoryCache =
        ConcurrentHashMap<String, Pair<Map<String, PokeWalletPriceData>, Long>>()

    /** Fast, non-suspending access to the snapshot already in memory (may be null). */
    fun cachedSnapshotOrNull(): ItalianPriceSnapshot? = memorySnapshot

    suspend fun getSnapshot(context: Context, forceRefresh: Boolean = false): Result<ItalianPriceSnapshot> =
        mutex.withLock {
            if (!forceRefresh) {
                val memoryAge = System.currentTimeMillis() - memoryUpdatedAt
                memorySnapshot?.takeIf { memoryUpdatedAt > 0L && memoryAge <= CACHE_TTL_MS }
                    ?.let { return Result.success(it) }

                loadSnapshotFromDisk(context)?.let { cached ->
                    memorySnapshot = cached
                    memoryUpdatedAt = System.currentTimeMillis()
                    return Result.success(cached)
                }
            }

            val url = resolveEndpointUrl(ENDPOINT_PATH)
                ?: return Result.failure(IllegalStateException("POKEWALLET_PROXY_URL non configurato"))

            val networkResult: Result<ItalianPriceSnapshot> = runCatching {
                val rawJson = fetchJson(url)
                withContext(Dispatchers.Default) {
                    parseSnapshot(rawJson) ?: error("Snapshot prezzi ITA non valido")
                }
            }

            networkResult.onSuccess { snapshot ->
                memorySnapshot = snapshot
                memoryUpdatedAt = System.currentTimeMillis()
                saveSnapshotToDisk(context, snapshot)
            }

            if (networkResult.isFailure) {
                loadSnapshotFromDisk(context, ignoreExpiry = true)?.let { stale ->
                    memorySnapshot = stale
                    memoryUpdatedAt = System.currentTimeMillis()
                    return Result.success(stale)
                }
            }

            networkResult
        }

    /**
     * Price map for a single ITA lookup code (expansion id, English base set
     * code or raw set code -- the worker resolves the alias), already converted
     * to the app-wide [PokeWalletPriceData].
     *
     * Order matters for latency: a full snapshot already in memory answers for
     * free, so only the first set opened on a cold start pays a request -- and
     * that request carries one expansion, not the whole catalogue.
     */
    suspend fun getPriceMap(
        context: Context,
        lookupCode: String,
        forceRefresh: Boolean = false
    ): Map<String, PokeWalletPriceData> {
        val normalized = lookupCode.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return emptyMap()

        if (!forceRefresh) {
            expansionMemoryCache[normalized]
                ?.takeIf { System.currentTimeMillis() - it.second <= CACHE_TTL_MS }
                ?.let { return it.first }

            // A whole snapshot already loaded (e.g. by the search screen) makes
            // every expansion free -- never re-download a slice of it.
            memorySnapshot
                ?.takeIf { memoryUpdatedAt > 0L && System.currentTimeMillis() - memoryUpdatedAt <= CACHE_TTL_MS }
                ?.priceMapFor(normalized)
                ?.takeIf { it.isNotEmpty() }
                ?.let { entries ->
                    val mapped = entries.mapValues { (_, entry) -> entry.toPriceData() }
                    expansionMemoryCache[normalized] = mapped to System.currentTimeMillis()
                    return mapped
                }

            loadExpansionFromDisk(context, normalized)?.let { payload ->
                return cacheExpansion(normalized, payload)
            }
        }

        val url = resolveEndpointUrl("$EXPANSION_ENDPOINT_DIR/$normalized.json")
        if (url != null) {
            val payload = runCatching {
                val rawJson = fetchJson(url)
                withContext(Dispatchers.Default) { parseExpansion(rawJson) }
            }.getOrElse { error ->
                Timber.d(error, "Prezzi per espansione non disponibili per %s, uso lo snapshot completo", normalized)
                null
            }
            if (payload != null) {
                saveExpansionToDisk(context, normalized, payload)
                return cacheExpansion(normalized, payload)
            }
        }

        // Fallback: the whole snapshot. Covers a worker too old to serve the
        // per-expansion route, and an expansion the snapshot has not reached yet.
        val snapshot = getSnapshot(context, forceRefresh).getOrNull() ?: return emptyMap()
        val mapped = snapshot.priceMapFor(normalized).mapValues { (_, entry) -> entry.toPriceData() }
        if (mapped.isNotEmpty()) {
            expansionMemoryCache[normalized] = mapped to System.currentTimeMillis()
        }
        return mapped
    }

    private fun cacheExpansion(
        requestedCode: String,
        payload: ItalianExpansionPrices
    ): Map<String, PokeWalletPriceData> {
        val mapped = payload.prices.mapValues { (_, entry) -> entry.toPriceData() }
        val now = System.currentTimeMillis()
        expansionMemoryCache[requestedCode] = mapped to now
        // Also under the resolved id, so "pbl" and "me05" share one entry.
        payload.expansionId.trim().lowercase(Locale.ROOT)
            .takeIf { it.isNotBlank() }
            ?.let { expansionMemoryCache[it] = mapped to now }
        return mapped
    }

    private fun resolveEndpointUrl(path: String): String? {
        val base = BuildConfig.POKEWALLET_PROXY_URL.trim().trimEnd('/')
        if (base.isBlank()) return null
        return "$base/$path"
    }

    private fun parseSnapshot(json: String): ItalianPriceSnapshot? {
        val parsed = runCatching {
            gson.fromJson(json, ItalianPriceSnapshot::class.java)
        }.getOrNull() ?: return null
        if (parsed.builtAt <= 0L || parsed.expansions.isEmpty()) return null
        return parsed
    }

    private fun parseExpansion(json: String): ItalianExpansionPrices? {
        val parsed = runCatching {
            gson.fromJson(json, ItalianExpansionPrices::class.java)
        }.getOrNull() ?: return null
        if (parsed.prices.isEmpty()) return null
        return parsed
    }

    private fun cacheDir(context: Context): File =
        File(context.filesDir, CACHE_DIR_NAME).apply { if (!exists()) mkdirs() }

    /** One file per expansion; the prefix keeps them apart from the full snapshot. */
    private fun expansionFile(context: Context, code: String): File {
        val safeCode = code.replace(Regex("[^a-z0-9._-]"), "_")
        return File(cacheDir(context), "$EXPANSION_FILE_PREFIX$safeCode.json")
    }

    private fun File.isFresh(ignoreExpiry: Boolean): Boolean {
        if (!exists() || length() == 0L) return false
        if (ignoreExpiry) return true
        val age = System.currentTimeMillis() - lastModified()
        return age in 0..CACHE_TTL_MS
    }

    private suspend fun loadSnapshotFromDisk(
        context: Context,
        ignoreExpiry: Boolean = false
    ): ItalianPriceSnapshot? = withContext(Dispatchers.IO) {
        clearLegacyPrefs(context)
        val file = File(cacheDir(context), SNAPSHOT_FILE_NAME)
        if (!file.isFresh(ignoreExpiry)) return@withContext null
        runCatching { parseSnapshot(file.readText()) }.getOrNull()
    }

    private suspend fun saveSnapshotToDisk(context: Context, snapshot: ItalianPriceSnapshot) {
        withContext(Dispatchers.IO) {
            runCatching {
                File(cacheDir(context), SNAPSHOT_FILE_NAME).writeText(gson.toJson(snapshot))
            }.onFailure { Timber.w(it, "Salvataggio snapshot prezzi ITA fallito") }
        }
    }

    private suspend fun loadExpansionFromDisk(
        context: Context,
        code: String
    ): ItalianExpansionPrices? = withContext(Dispatchers.IO) {
        val file = expansionFile(context, code)
        if (!file.isFresh(ignoreExpiry = false)) return@withContext null
        runCatching { parseExpansion(file.readText()) }.getOrNull()
    }

    private suspend fun saveExpansionToDisk(
        context: Context,
        code: String,
        payload: ItalianExpansionPrices
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                expansionFile(context, code).writeText(gson.toJson(payload))
            }.onFailure { Timber.w(it, "Salvataggio prezzi espansione %s fallito", code) }
        }
    }

    /** Reclaims the megabytes the old SharedPreferences blob still occupies. */
    private fun clearLegacyPrefs(context: Context) {
        if (legacyPrefsCleared) return
        legacyPrefsCleared = true
        runCatching { context.deleteSharedPreferences(LEGACY_PREFS_NAME) }
    }

    private suspend fun fetchJson(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Prezzi ITA non disponibili: HTTP ${response.code}")
            }
            response.body?.string()?.trim().orEmpty().ifBlank {
                error("Prezzi ITA: risposta vuota")
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
