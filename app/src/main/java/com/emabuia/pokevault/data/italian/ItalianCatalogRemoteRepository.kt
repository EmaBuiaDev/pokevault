package com.emabuia.pokevault.data.italian

import android.content.Context
import com.emabuia.pokevault.BuildConfig
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ItalianCatalogRemoteRepository {

    companion object {
        private const val PREFS_NAME = "italian_catalog_cache_v3"
        private const val KEY_JSON = "json"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val CACHE_TTL_MS = 5L * 60L * 1000L
        private const val REMOTE_CACHE_BUCKET_MS = 5L * 60L * 1000L

        private const val EXPANSIONS_PREFS_NAME = "italian_expansions_cache_v1"
        private const val KEY_EXPANSIONS_JSON = "expansions_json"
        private const val KEY_EXPANSIONS_UPDATED_AT = "expansions_updated_at"

        private const val EXPANSION_CARDS_PREFS_NAME = "italian_expansion_cards_cache_v1"
        private const val CATALOG_JSON_MARKER = "ita/catalog.json"
    }

    private val mutex = Mutex()
    private val expansionsMutex = Mutex()
    private val expansionCardsMutexes = ConcurrentHashMap<String, Mutex>()
    private val httpClient = OkHttpClient.Builder().build()

    @Volatile
    private var memoryCatalog: ItalianCatalog? = null
    @Volatile
    private var memoryCatalogUpdatedAt: Long = 0L

    @Volatile
    private var memoryExpansions: List<ItalianExpansionManifest>? = null
    @Volatile
    private var memoryExpansionsUpdatedAt: Long = 0L

    private val memoryExpansionCards = ConcurrentHashMap<String, List<ItalianCardRecord>>()
    private val memoryExpansionCardsUpdatedAt = ConcurrentHashMap<String, Long>()

    suspend fun getCatalog(context: Context, forceRefresh: Boolean = false): Result<ItalianCatalog> = mutex.withLock {
        if (!forceRefresh) {
            val memoryAge = System.currentTimeMillis() - memoryCatalogUpdatedAt
            memoryCatalog?.takeIf { memoryCatalogUpdatedAt > 0L && memoryAge <= CACHE_TTL_MS }
                ?.let { return Result.success(it) }

            val cached = loadFromPrefs(context)
            if (cached != null) {
                memoryCatalog = cached
                memoryCatalogUpdatedAt = System.currentTimeMillis()
                return Result.success(cached)
            }
        }

        val url = BuildConfig.ITALIAN_CATALOG_URL.trim()
        if (url.isBlank()) {
            return Result.failure(IllegalStateException("ITALIAN_CATALOG_URL non configurato"))
        }

        val networkResult: Result<ItalianCatalog> = runCatching {
            val rawJson = fetchJson(url)
            // Move heavy Gson parse off the caller's thread (avoid blocking Main).
            withContext(Dispatchers.Default) {
                ItalianCatalogNormalizer.parseCatalogJson(rawJson)
            }
        }

        networkResult.onSuccess { catalog ->
            memoryCatalog = catalog
            memoryCatalogUpdatedAt = System.currentTimeMillis()
            saveToPrefs(context, catalog)
        }

        if (networkResult.isFailure) {
            loadFromPrefs(context, ignoreExpiry = true)?.let { stale ->
                memoryCatalog = stale
                memoryCatalogUpdatedAt = System.currentTimeMillis()
                return Result.success(stale)
            }
        }

        networkResult
    }

    /**
     * Lightweight per-expansion manifest (id, card count, sort order, logo key)
     * from GET /v1/expansions. Used for the sets list instead of downloading
     * the whole catalog blob just to read expansion metadata.
     */
    suspend fun getExpansions(context: Context, forceRefresh: Boolean = false): Result<List<ItalianExpansionManifest>> =
        expansionsMutex.withLock {
            if (!forceRefresh) {
                val memoryAge = System.currentTimeMillis() - memoryExpansionsUpdatedAt
                memoryExpansions?.takeIf { memoryExpansionsUpdatedAt > 0L && memoryAge <= CACHE_TTL_MS }
                    ?.let { return Result.success(it) }

                val cached = loadExpansionsFromPrefs(context)
                if (cached != null) {
                    memoryExpansions = cached
                    memoryExpansionsUpdatedAt = System.currentTimeMillis()
                    return Result.success(cached)
                }
            }

            val networkResult: Result<List<ItalianExpansionManifest>> = runCatching {
                val rawJson = fetchJson(resolveApiUrl("v1/expansions"))
                withContext(Dispatchers.Default) {
                    ItalianCatalogNormalizer.parseExpansionsApiResponse(rawJson)
                }
            }

            networkResult.onSuccess { expansions ->
                memoryExpansions = expansions
                memoryExpansionsUpdatedAt = System.currentTimeMillis()
                saveExpansionsToPrefs(context, expansions)
            }

            if (networkResult.isFailure) {
                loadExpansionsFromPrefs(context, ignoreExpiry = true)?.let { stale ->
                    memoryExpansions = stale
                    memoryExpansionsUpdatedAt = System.currentTimeMillis()
                    return Result.success(stale)
                }
            }

            networkResult
        }

    /**
     * Cards for a single expansion from GET /v1/expansions/{id}/cards. Used
     * whenever only one set's cards are needed (set detail, overlay lookups)
     * instead of downloading and filtering the whole catalog blob.
     */
    suspend fun getExpansionCards(
        context: Context,
        expansionId: String,
        forceRefresh: Boolean = false
    ): Result<List<ItalianCardRecord>> {
        val normalizedId = expansionId.trim().lowercase(Locale.ROOT)
        if (normalizedId.isBlank()) return Result.success(emptyList())

        return expansionCardsMutexes.getOrPut(normalizedId) { Mutex() }.withLock {
            if (!forceRefresh) {
                val memoryAge = System.currentTimeMillis() - (memoryExpansionCardsUpdatedAt[normalizedId] ?: 0L)
                memoryExpansionCards[normalizedId]?.takeIf {
                    (memoryExpansionCardsUpdatedAt[normalizedId] ?: 0L) > 0L && memoryAge <= CACHE_TTL_MS
                }?.let { return Result.success(it) }

                val cached = loadExpansionCardsFromPrefs(context, normalizedId)
                if (cached != null) {
                    memoryExpansionCards[normalizedId] = cached
                    memoryExpansionCardsUpdatedAt[normalizedId] = System.currentTimeMillis()
                    return Result.success(cached)
                }
            }

            val networkResult: Result<List<ItalianCardRecord>> = runCatching {
                val rawJson = fetchJson(resolveApiUrl("v1/expansions/$normalizedId/cards"))
                withContext(Dispatchers.Default) {
                    ItalianCatalogNormalizer.parseExpansionCardsApiResponse(rawJson, normalizedId)
                }
            }

            networkResult.onSuccess { cards ->
                memoryExpansionCards[normalizedId] = cards
                memoryExpansionCardsUpdatedAt[normalizedId] = System.currentTimeMillis()
                saveExpansionCardsToPrefs(context, normalizedId, cards)
            }

            if (networkResult.isFailure) {
                loadExpansionCardsFromPrefs(context, normalizedId, ignoreExpiry = true)?.let { stale ->
                    memoryExpansionCards[normalizedId] = stale
                    memoryExpansionCardsUpdatedAt[normalizedId] = System.currentTimeMillis()
                    return Result.success(stale)
                }
            }

            networkResult
        }
    }

    private suspend fun loadExpansionsFromPrefs(context: Context, ignoreExpiry: Boolean = false): List<ItalianExpansionManifest>? =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(EXPANSIONS_PREFS_NAME, Context.MODE_PRIVATE)
            val updatedAt = prefs.getLong(KEY_EXPANSIONS_UPDATED_AT, 0L)
            if (updatedAt <= 0L) return@withContext null
            val age = System.currentTimeMillis() - updatedAt
            if (!ignoreExpiry && age > CACHE_TTL_MS) return@withContext null

            val json = prefs.getString(KEY_EXPANSIONS_JSON, null)?.trim().orEmpty()
            if (json.isBlank()) return@withContext null

            runCatching { ItalianCatalogNormalizer.parseExpansionManifestJson(json) }.getOrNull()
        }

    private suspend fun saveExpansionsToPrefs(context: Context, expansions: List<ItalianExpansionManifest>) {
        withContext(Dispatchers.IO) {
            val payload = ItalianCatalogNormalizer.toExpansionManifestJson(expansions)
            val prefs = context.getSharedPreferences(EXPANSIONS_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_EXPANSIONS_JSON, payload)
                .putLong(KEY_EXPANSIONS_UPDATED_AT, System.currentTimeMillis())
                .apply()
        }
    }

    private suspend fun loadExpansionCardsFromPrefs(
        context: Context,
        expansionId: String,
        ignoreExpiry: Boolean = false
    ): List<ItalianCardRecord>? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(EXPANSION_CARDS_PREFS_NAME, Context.MODE_PRIVATE)
        val updatedAt = prefs.getLong("${expansionId}_updated_at", 0L)
        if (updatedAt <= 0L) return@withContext null
        val age = System.currentTimeMillis() - updatedAt
        if (!ignoreExpiry && age > CACHE_TTL_MS) return@withContext null

        val json = prefs.getString("${expansionId}_json", null)?.trim().orEmpty()
        if (json.isBlank()) return@withContext null

        runCatching { ItalianCatalogNormalizer.parseCards(json) }.getOrNull()
    }

    private suspend fun saveExpansionCardsToPrefs(context: Context, expansionId: String, cards: List<ItalianCardRecord>) {
        withContext(Dispatchers.IO) {
            val payload = ItalianCatalogNormalizer.toCanonicalJson(cards)
            val prefs = context.getSharedPreferences(EXPANSION_CARDS_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("${expansionId}_json", payload)
                .putLong("${expansionId}_updated_at", System.currentTimeMillis())
                .apply()
        }
    }

    private fun resolveApiUrl(path: String): String {
        val catalogUrl = BuildConfig.ITALIAN_CATALOG_URL.trim()
        val markerIndex = catalogUrl.indexOf(CATALOG_JSON_MARKER)
        val base = if (markerIndex >= 0) catalogUrl.substring(0, markerIndex) else catalogUrl
        val normalizedBase = if (base.endsWith("/")) base else "$base/"
        return "$normalizedBase$path"
    }

    private suspend fun loadFromPrefs(context: Context, ignoreExpiry: Boolean = false): ItalianCatalog? =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
            if (updatedAt <= 0L) return@withContext null
            val age = System.currentTimeMillis() - updatedAt
            if (!ignoreExpiry && age > CACHE_TTL_MS) return@withContext null

            val json = prefs.getString(KEY_JSON, null)?.trim().orEmpty()
            if (json.isBlank()) return@withContext null

            runCatching { ItalianCatalogNormalizer.parseCatalogJson(json) }.getOrNull()
        }

    private suspend fun saveToPrefs(context: Context, catalog: ItalianCatalog) {
        withContext(Dispatchers.IO) {
            val payload = ItalianCatalogNormalizer.toCatalogJson(catalog)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_JSON, payload)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply()
        }
    }

    private suspend fun fetchJson(url: String): String = withContext(Dispatchers.IO) {
        val cacheBucket = System.currentTimeMillis() / REMOTE_CACHE_BUCKET_MS
        val resolvedUrl = url.toHttpUrlOrNull()
            ?.newBuilder()
            ?.setQueryParameter("cv", cacheBucket.toString())
            ?.build()
            ?.toString()
            ?: url

        val request = Request.Builder()
            .url(resolvedUrl)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Catalogo ITA non disponibile: HTTP ${response.code}")
            }
            response.body?.string()?.trim().orEmpty().ifBlank {
                error("Catalogo ITA vuoto")
            }
        }
    }
}
