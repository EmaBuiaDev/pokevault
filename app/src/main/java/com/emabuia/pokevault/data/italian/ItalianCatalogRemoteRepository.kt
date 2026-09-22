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
import com.google.gson.Gson
import java.net.URLEncoder

class ItalianCatalogRemoteRepository {

    companion object {
        private const val PREFS_NAME = "italian_catalog_cache_v3"
        private const val KEY_JSON = "json"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val CACHE_TTL_MS = 5L * 60L * 1000L
        private const val REMOTE_CACHE_BUCKET_MS = 5L * 60L * 1000L
        private const val PREFS_ILLUSTRATORS_NAME = "italian_illustrators_cache_v1"
        private const val KEY_ILLUSTRATORS_JSON = "json"
        private const val KEY_ILLUSTRATORS_UPDATED_AT = "updated_at"
        // Ventiquattro ore, e non i cinque minuti del catalogo: questa risposta
        // e' cio' che apre la sezione illustratori, e chi la riapre nella stessa
        // giornata non deve ripagare la rete. Il catalogo cambia quando si
        // ingesta un set -- giorni, non minuti -- e un artista in meno per
        // qualche ora non e' un dato sbagliato, e' un dato vecchio di poco.
        private const val ILLUSTRATORS_TTL_MS = 24L * 60L * 60L * 1000L
    }

    private val mutex = Mutex()
    private val expansionCardsMutex = Mutex()
    private val httpClient = OkHttpClient.Builder().build()

    @Volatile
    private var memoryCatalog: ItalianCatalog? = null
    @Volatile
    private var memoryCatalogUpdatedAt: Long = 0L

    private val memoryExpansionCards = HashMap<String, List<ItalianCardRecord>>()
    private val memoryExpansionCardsUpdatedAt = HashMap<String, Long>()

    private val expansionsSummaryMutex = Mutex()
    @Volatile
    private var memoryExpansionsSummary: List<ItalianExpansionSummary>? = null
    @Volatile
    private var memoryExpansionsSummaryUpdatedAt: Long = 0L

    private val illustratorsMutex = Mutex()
    @Volatile
    private var memoryIllustrators: ItalianIllustratorsResponse? = null
    @Volatile
    private var memoryIllustratorsUpdatedAt: Long = 0L

    private val illustratorCardsMutex = Mutex()
    private val memoryIllustratorCards = HashMap<String, List<ItalianCardRecord>>()
    private val memoryIllustratorCardsUpdatedAt = HashMap<String, Long>()

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

    // Fetches just one expansion's cards (GET /v1/expansions/{id}/cards) instead of the
    // whole ~15k-card catalog blob -- used by set detail screens, which only need the
    // cards of the set being opened. Memory-only cache (no SharedPreferences): this is
    // a small, session-scoped payload, not the heavyweight blob getCatalog() persists.
    // Callers must fall back to getCatalog() + cardsByExpansion() on failure so this
    // path can never make card resolution less reliable than before it existed.
    suspend fun getExpansionCards(
        baseUrl: String,
        expansionId: String,
        forceRefresh: Boolean = false
    ): Result<List<ItalianCardRecord>> = expansionCardsMutex.withLock {
        val key = expansionId.trim().lowercase(java.util.Locale.ROOT)
        if (key.isBlank()) return@withLock Result.success(emptyList())

        if (!forceRefresh) {
            val updatedAt = memoryExpansionCardsUpdatedAt[key] ?: 0L
            val age = System.currentTimeMillis() - updatedAt
            if (updatedAt > 0L && age <= CACHE_TTL_MS) {
                memoryExpansionCards[key]?.let { return@withLock Result.success(it) }
            }
        }

        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isBlank()) {
            return@withLock Result.failure(IllegalStateException("Base URL non configurato"))
        }

        val result = runCatching {
            val rawJson = fetchExpansionCardsJson("$normalizedBase/v1/expansions/$key/cards")
            withContext(Dispatchers.Default) {
                ItalianCatalogNormalizer.parseExpansionCardsResponse(rawJson)
            }
        }

        result.onSuccess { cards ->
            memoryExpansionCards[key] = cards
            memoryExpansionCardsUpdatedAt[key] = System.currentTimeMillis()
        }

        result
    }

    private suspend fun fetchExpansionCardsJson(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Espansione ITA non disponibile: HTTP ${response.code}")
            }
            response.body?.string()?.trim().orEmpty().ifBlank {
                error("Risposta espansione ITA vuota")
            }
        }
    }

    // Fetches the lightweight expansion manifest (GET /v1/expansions) instead of the
    // full catalog -- used to build the Pokedex sets list. Memory-only cache, same TTL
    // as getCatalog(). Callers must fall back to getCatalog() on failure/empty result.
    suspend fun getExpansionsSummary(
        baseUrl: String,
        forceRefresh: Boolean = false
    ): Result<List<ItalianExpansionSummary>> = expansionsSummaryMutex.withLock {
        if (!forceRefresh) {
            val updatedAt = memoryExpansionsSummaryUpdatedAt
            val age = System.currentTimeMillis() - updatedAt
            if (updatedAt > 0L && age <= CACHE_TTL_MS) {
                memoryExpansionsSummary?.let { return@withLock Result.success(it) }
            }
        }

        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isBlank()) {
            return@withLock Result.failure(IllegalStateException("Base URL non configurato"))
        }

        val result = runCatching {
            val rawJson = fetchExpansionCardsJson("$normalizedBase/v1/expansions")
            withContext(Dispatchers.Default) {
                ItalianCatalogNormalizer.parseExpansionsResponse(rawJson)
            }
        }

        result.onSuccess { summaries ->
            memoryExpansionsSummary = summaries
            memoryExpansionsSummaryUpdatedAt = System.currentTimeMillis()
        }

        result
    }

    /**
     * L'indice degli illustratori (GET /v1/illustrators): 388 voci, una
     * sessantina di KB compressi.
     *
     * Esiste per NON passare dal catalogo intero. L'app sapeva gia' raggruppare
     * le carte per illustratore da sola, ma per farlo tirava /ita/catalog.json
     * -- 10,4 MB che, col TTL da cinque minuti, si riscaricano di continuo
     * durante l'uso. Qui il raggruppamento lo fa D1, e la risposta sta in
     * SharedPreferences per un giorno.
     *
     * Chi chiama deve ricadere sul catalogo completo se questo fallisce: la
     * rotta e' nuova e un'app aggiornata puo' trovarsi davanti un worker che
     * non la conosce ancora.
     */
    suspend fun getIllustrators(
        context: Context,
        baseUrl: String,
        forceRefresh: Boolean = false
    ): Result<ItalianIllustratorsResponse> = illustratorsMutex.withLock {
        if (!forceRefresh) {
            val memoryAge = System.currentTimeMillis() - memoryIllustratorsUpdatedAt
            memoryIllustrators?.takeIf { memoryIllustratorsUpdatedAt > 0L && memoryAge <= ILLUSTRATORS_TTL_MS }
                ?.let { return@withLock Result.success(it) }

            loadIllustratorsFromPrefs(context)?.let { cached ->
                memoryIllustrators = cached
                memoryIllustratorsUpdatedAt = System.currentTimeMillis()
                return@withLock Result.success(cached)
            }
        }

        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isBlank()) {
            return@withLock Result.failure(IllegalStateException("Base URL non configurato"))
        }

        val result = runCatching {
            val rawJson = fetchExpansionCardsJson("$normalizedBase/v1/illustrators")
            withContext(Dispatchers.Default) {
                ItalianIllustratorsNormalizer.parseIllustrators(rawJson)
            }
        }

        result.onSuccess { payload ->
            memoryIllustrators = payload
            memoryIllustratorsUpdatedAt = System.currentTimeMillis()
            saveIllustratorsToPrefs(context, payload)
        }

        // Senza rete si serve la copia vecchia invece di un errore: un indice
        // di ieri apre comunque la sezione, un errore la lascia bianca.
        if (result.isFailure) {
            loadIllustratorsFromPrefs(context, ignoreExpiry = true)?.let { stale ->
                memoryIllustrators = stale
                memoryIllustratorsUpdatedAt = System.currentTimeMillis()
                return@withLock Result.success(stale)
            }
        }

        result
    }

    /**
     * Le carte di un illustratore (GET /v1/illustrators/{nome}/cards).
     *
     * [rawName] e' il nome GREZZO come sta in D1, non la chiave normalizzata:
     * il match lato server e' esatto. Una voce nata dall'unione di piu' grafie
     * va chiesta una volta per grafia.
     *
     * Cache di sola memoria come getExpansionCards: e' un carico di sessione,
     * non il blob pesante che vale la pena persistere.
     */
    suspend fun getIllustratorCards(
        baseUrl: String,
        rawName: String,
        forceRefresh: Boolean = false
    ): Result<List<ItalianCardRecord>> = illustratorCardsMutex.withLock {
        val key = rawName.trim()
        if (key.isBlank()) return@withLock Result.success(emptyList())

        if (!forceRefresh) {
            val updatedAt = memoryIllustratorCardsUpdatedAt[key] ?: 0L
            val age = System.currentTimeMillis() - updatedAt
            if (updatedAt > 0L && age <= ILLUSTRATORS_TTL_MS) {
                memoryIllustratorCards[key]?.let { return@withLock Result.success(it) }
            }
        }

        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isBlank()) {
            return@withLock Result.failure(IllegalStateException("Base URL non configurato"))
        }

        // I nomi hanno spazi, punti e accenti: senza encode l'URL e' malformato
        // e la rotta risponde 404 come se l'illustratore non esistesse.
        val encoded = URLEncoder.encode(key, "UTF-8").replace("+", "%20")

        val result = runCatching {
            val rawJson = fetchExpansionCardsJson("$normalizedBase/v1/illustrators/$encoded/cards")
            withContext(Dispatchers.Default) {
                ItalianIllustratorsNormalizer.parseIllustratorCards(rawJson)
            }
        }

        result.onSuccess { cards ->
            memoryIllustratorCards[key] = cards
            memoryIllustratorCardsUpdatedAt[key] = System.currentTimeMillis()
        }

        result
    }

    private suspend fun loadIllustratorsFromPrefs(
        context: Context,
        ignoreExpiry: Boolean = false
    ): ItalianIllustratorsResponse? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_ILLUSTRATORS_NAME, Context.MODE_PRIVATE)
        val updatedAt = prefs.getLong(KEY_ILLUSTRATORS_UPDATED_AT, 0L)
        if (updatedAt <= 0L) return@withContext null
        val age = System.currentTimeMillis() - updatedAt
        if (!ignoreExpiry && age > ILLUSTRATORS_TTL_MS) return@withContext null

        val json = prefs.getString(KEY_ILLUSTRATORS_JSON, null)?.trim().orEmpty()
        if (json.isBlank()) return@withContext null

        runCatching { ItalianIllustratorsNormalizer.parseIllustrators(json) }
            .getOrNull()
            ?.takeIf { it.illustrators.isNotEmpty() }
    }

    private suspend fun saveIllustratorsToPrefs(context: Context, payload: ItalianIllustratorsResponse) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_ILLUSTRATORS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_ILLUSTRATORS_JSON, Gson().toJson(payload))
                .putLong(KEY_ILLUSTRATORS_UPDATED_AT, System.currentTimeMillis())
                .apply()
        }
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
