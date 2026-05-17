package com.emabuia.pokevault.data.italian

import android.content.Context
import com.emabuia.pokevault.BuildConfig
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
    }

    private val mutex = Mutex()
    private val httpClient = OkHttpClient.Builder().build()

    @Volatile
    private var memoryCatalog: ItalianCatalog? = null
    @Volatile
    private var memoryCatalogUpdatedAt: Long = 0L

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

        val networkResult = runCatching {
            fetchCatalogJson(url)
        }.mapCatching { rawJson ->
            ItalianCatalogNormalizer.parseCatalogJson(rawJson)
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

    private fun loadFromPrefs(context: Context, ignoreExpiry: Boolean = false): ItalianCatalog? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        if (updatedAt <= 0L) return null
        val age = System.currentTimeMillis() - updatedAt
        if (!ignoreExpiry && age > CACHE_TTL_MS) return null

        val json = prefs.getString(KEY_JSON, null)?.trim().orEmpty()
        if (json.isBlank()) return null

        return runCatching { ItalianCatalogNormalizer.parseCatalogJson(json) }.getOrNull()
    }

    private fun saveToPrefs(context: Context, catalog: ItalianCatalog) {
        val payload = ItalianCatalogNormalizer.toCatalogJson(catalog)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_JSON, payload)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    private suspend fun fetchCatalogJson(url: String): String = withContext(Dispatchers.IO) {
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
