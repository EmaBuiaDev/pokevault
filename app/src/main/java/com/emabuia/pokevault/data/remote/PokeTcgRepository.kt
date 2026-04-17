package com.emabuia.pokevault.data.remote

import android.content.Context
import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.util.AppLocale
import timber.log.Timber
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object RetrofitClient {
    private const val BASE_URL = "https://api.pokemontcg.io/"
    private val API_KEY = com.emabuia.pokevault.BuildConfig.POKETCG_API_KEY

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
            if (API_KEY.isNotBlank()) request.addHeader("X-Api-Key", API_KEY)
            chain.proceed(request.build())
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: PokeTcgApiService by lazy {
        Retrofit.Builder().baseUrl(BASE_URL).client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(PokeTcgApiService::class.java)
    }
}

class PokeTcgRepository {

    private val api = RetrofitClient.apiService
    private val tcgdexRepository = TcgdexRepository()
    private val gson = Gson()

    // Cache memoria (thread-safe)
    @Volatile
    private var memorySets: List<TcgSet>? = null
    private val memoryCards = java.util.concurrent.ConcurrentHashMap<String, List<TcgCard>>()

    companion object {
        private const val PREFS_NAME = "pokevault_cache"
        private const val SETS_CACHE_KEY = "cached_sets_v6"
        private const val SETS_TIME_KEY = "cached_sets_time_v6"
        private const val CARDS_PREFIX = "cached_cards_"
        private const val CARDS_TIME_PREFIX = "cached_cards_time_"
        private const val CARDS_TOTAL_PREFIX = "cached_cards_total_"
        private const val CACHE_DURATION = 24 * 60 * 60 * 1000L // 24 ore
        private const val CARDS_CACHE_DURATION = 6 * 60 * 60 * 1000L // 6 ore per carte

        // Pattern compilati una sola volta per evitare recompilation ad ogni ricerca
        private val SANITIZE_SPECIAL = Regex("[+\\-=&|><!(){}\\[\\]^~?:\\\\/]")
        private val SANITIZE_NON_WORD = Regex("[^a-zA-ZÀ-ÿ0-9\\s'-]")
        private val SANITIZE_MULTI_SPACE = Regex("\\s+")
    }

    // ══════════════════════════════════════
    // CACHE SETS
    // ══════════════════════════════════════

    private fun saveSetsToCache(context: Context, sets: List<TcgSet>) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(SETS_CACHE_KEY, gson.toJson(sets))
                .putLong(SETS_TIME_KEY, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Timber.w(e, "Errore salvataggio cache sets")
        }
    }

    private fun loadSetsFromCache(context: Context, ignoreExpiry: Boolean = false): List<TcgSet>? {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val time = prefs.getLong(SETS_TIME_KEY, 0)
            if (!ignoreExpiry && System.currentTimeMillis() - time > CACHE_DURATION) return null
            val json = prefs.getString(SETS_CACHE_KEY, null) ?: return null
            return gson.fromJson(json, Array<TcgSet>::class.java).toList()
        } catch (e: Exception) {
            Timber.w(e, "Errore lettura cache sets")
            return null
        }
    }

    suspend fun getSets(context: Context? = null, forceRefresh: Boolean = false): Result<List<TcgSet>> {
        if (!forceRefresh && memorySets != null) return Result.success(memorySets!!)
        if (!forceRefresh && context != null) {
            loadSetsFromCache(context)?.let { memorySets = it; return Result.success(it) }
        }
        return try {
            val startedAt = System.currentTimeMillis()
            val allSets = mutableListOf<TcgSet>()
            var page = 1
            do {
                val response = api.getSets(page = page, pageSize = 250)
                allSets.addAll(response.data)
                page++
            } while (response.data.size == 250)

            val primarySets = allSets.distinctBy { it.id }
            val extraSets = tcgdexRepository.getMissingSets(primarySets)
                .onFailure { Timber.w(it, "TCGdex extra sets non disponibili, continuo con PokemonTCG") }
                .getOrDefault(emptyList())

            val mergedSets = mergePrimaryWithExtras(primarySets, extraSets)
            Timber.i(
                "Loaded sets primary=%d extra=%d merged=%d durationMs=%d",
                primarySets.size,
                extraSets.size,
                mergedSets.size,
                System.currentTimeMillis() - startedAt
            )

            memorySets = mergedSets
            context?.let { saveSetsToCache(it, mergedSets) }
            Result.success(mergedSets)
        } catch (e: Exception) {
            if (context != null) {
                loadSetsFromCache(context, ignoreExpiry = true)?.let {
                    memorySets = it; return Result.success(it)
                }
            }
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════
    // CACHE CARTE PER SET
    // ══════════════════════════════════════

    private fun saveCardsToCache(context: Context, setId: String, cards: List<TcgCard>, totalCount: Int) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("$CARDS_PREFIX$setId", gson.toJson(cards))
                .putLong("$CARDS_TIME_PREFIX$setId", System.currentTimeMillis())
                .putInt("$CARDS_TOTAL_PREFIX$setId", totalCount)
                .commit()
        } catch (e: Exception) {
            Timber.w(e, "Errore salvataggio cache cards per set $setId")
        }
    }

    private fun loadCardsFromCache(context: Context, setId: String, ignoreExpiry: Boolean = false): List<TcgCard>? {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val time = prefs.getLong("$CARDS_TIME_PREFIX$setId", 0)
            if (!ignoreExpiry && System.currentTimeMillis() - time > CARDS_CACHE_DURATION) return null
            val json = prefs.getString("$CARDS_PREFIX$setId", null) ?: return null
            val cards: List<TcgCard> = gson.fromJson(json, Array<TcgCard>::class.java).toList()
            val expectedTotal = prefs.getInt("$CARDS_TOTAL_PREFIX$setId", -1)
            if (expectedTotal <= 0) return null
            if (cards.size < expectedTotal) return null
            return cards
        } catch (e: Exception) {
            Timber.w(e, "Errore lettura cache cards per set $setId")
            return null
        }
    }

    suspend fun getCardsBySet(setId: String, context: Context? = null, forceRefresh: Boolean = false): Result<List<TcgCard>> {
        if (isTcgdexSetId(setId)) {
            if (!forceRefresh) {
                val cached = memoryCards[setId] ?: (context?.let { loadCardsFromCache(it, setId) })
                if (cached != null && cached.isNotEmpty()) {
                    memoryCards[setId] = cached
                    return Result.success(cached)
                }
            }

            return tcgdexRepository.getCardsBySet(setId).onSuccess { cards ->
                memoryCards[setId] = cards
                context?.let { saveCardsToCache(it, setId, cards, cards.size) }
            }
        }

        // 1. Controllo Cache
        if (!forceRefresh) {
            val cached = memoryCards[setId] ?: (context?.let { loadCardsFromCache(it, setId) })
            if (cached != null && cached.isNotEmpty()) {
                memoryCards[setId] = cached
                return Result.success(cached)
            }
        }

        // 2. API con Paginazione completa
        return try {
            val uniqueCards = fetchAllCards(setId)
            memoryCards[setId] = uniqueCards
            context?.let { saveCardsToCache(it, setId, uniqueCards, uniqueCards.size) }
            Result.success(uniqueCards)
        } catch (e: Exception) {
            val fallback = memoryCards[setId] ?: (context?.let { loadCardsFromCache(it, setId, ignoreExpiry = true) })
            if (fallback != null) return Result.success(fallback)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════
    // ALTRI METODI
    // ══════════════════════════════════════

    private suspend fun fetchAllCards(setId: String, orderBy: String? = null): List<TcgCard> {
        val cardMap = linkedMapOf<String, TcgCard>()
        var page = 1
        var totalCount = 0

        while (true) {
            val response = api.getCardsBySet(query = "set.id:$setId", orderBy = orderBy, page = page, pageSize = 250)
            val fetched = response.data.size
            if (page == 1) totalCount = response.totalCount
            for (card in response.data) { cardMap[card.id] = card }

            if (fetched == 0) break
            if (totalCount > 0 && cardMap.size >= totalCount) break
            if (totalCount <= 0 && fetched < 250) break
            page++
        }

        // Se la paginazione ha prodotto duplicati, riprova con sort inverso
        if (totalCount > 0 && cardMap.size < totalCount && orderBy == null) {
            val retryCards = fetchAllCards(setId, orderBy = "-number")
            for (card in retryCards) { cardMap[card.id] = card }
        }

        return cardMap.values.toList()
    }

    suspend fun getSetInfo(setId: String): Result<TcgSet> {
        memorySets?.firstOrNull { it.id == setId }?.let { return Result.success(it) }
        if (isTcgdexSetId(setId)) {
            return tcgdexRepository.getSetInfo(setId)
        }
        return try { Result.success(api.getSet(setId).data) }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun searchCards(query: String, page: Int = 1): Result<List<TcgCard>> {
        if (query.isBlank()) return Result.success(emptyList())
        
        val trimmed = query.trim()
        val isIt = AppLocale.isItalian
        
        // Gestione numero con totale (es. 001/217)
        val fullNumberRegex = Regex("""^(\d+)/(\d+)$""")
        val match = fullNumberRegex.find(trimmed)
        
        if (match != null) {
            val number = match.groupValues[1].trimStart('0').ifEmpty { "0" }
            val total = match.groupValues[2].trimStart('0').ifEmpty { "0" }

            // Cerchiamo i set che hanno quel totale stampato
            val candidateSets = getCandidateSetsByPrintedTotal(total, tolerance = 1)
            
            return if (candidateSets.isNotEmpty()) {
                val setIds = candidateSets.joinToString(" OR ") { "set.id:${it.id}" }
                val apiQuery = "($setIds) number:\"$number\""
                try {
                    val res = api.searchCards(query = apiQuery, page = page)
                    Result.success(res.data.distinctBy { it.id })
                } catch (e: Exception) { Result.failure(e) }
            } else {
                // Fallback: cerca solo per numero se non troviamo il set corrispondente al totale
                try {
                    val res = api.searchCards(query = "number:\"$number\"", page = page)
                    Result.success(res.data.distinctBy { it.id })
                } catch (e: Exception) { Result.failure(e) }
            }
        }

        val apiQuery = when {
            trimmed.matches(Regex("""^\d+$""")) -> {
                val number = trimmed.trimStart('0').ifEmpty { "0" }
                "number:\"$number\""
            }
            isIt && trimmed.equals("energia", ignoreCase = true) -> "supertype:energy"
            isIt && trimmed.equals("allenatore", ignoreCase = true) -> "supertype:trainer"
            isIt && trimmed.equals("aiuto", ignoreCase = true) -> "subtypes:supporter"
            isIt && trimmed.equals("strumento", ignoreCase = true) -> "subtypes:item"
            isIt && trimmed.equals("stadio", ignoreCase = true) -> "subtypes:stadium"
            isIt && trimmed.equals("fuoco", ignoreCase = true) -> "types:fire"
            isIt && trimmed.equals("acqua", ignoreCase = true) -> "types:water"
            isIt && trimmed.equals("erba", ignoreCase = true) -> "types:grass"
            isIt && trimmed.equals("elettro", ignoreCase = true) -> "types:lightning"
            isIt && trimmed.equals("psico", ignoreCase = true) -> "types:psychic"
            isIt && trimmed.equals("lotta", ignoreCase = true) -> "types:fighting"
            isIt && trimmed.equals("buio", ignoreCase = true) -> "types:darkness"
            isIt && trimmed.equals("metallo", ignoreCase = true) -> "types:metal"
            isIt && trimmed.equals("drago", ignoreCase = true) -> "types:dragon"
            isIt && trimmed.equals("folletto", ignoreCase = true) -> "types:fairy"
            isIt && trimmed.equals("incolore", ignoreCase = true) -> "types:colorless"
            else -> "name:\"${sanitizeQuery(trimmed)}*\""
        }

        return try {
            val res = api.searchCards(query = apiQuery, page = page)
            Result.success(res.data.distinctBy { it.id })
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun searchCardsFuzzy(name: String, page: Int = 1): Result<List<TcgCard>> {
        val clean = sanitizeQuery(name)
        if (clean.isBlank()) return Result.success(emptyList())
        return try {
            val result = api.searchCards(query = "name:\"$clean*\"", page = page, pageSize = 30)
            if (result.data.isNotEmpty()) {
                return Result.success(result.data.distinctBy { it.id })
            }
            val firstWord = clean.split(" ").firstOrNull()?.trim() ?: clean
            if (firstWord.length >= 3 && firstWord != clean) {
                val fallback = api.searchCards(query = "name:\"$firstWord*\"", page = page, pageSize = 30)
                Result.success(fallback.data.distinctBy { it.id })
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun searchByNameAndNumber(name: String?, number: String?, setId: String? = null): Result<List<TcgCard>> {
        val cleanName = name?.let { sanitizeQuery(it) }?.takeIf { it.isNotBlank() }
        val cleanNumber = number?.trim()?.trimStart('0')?.takeIf { it.isNotBlank() }
        val cleanSetId = setId?.trim()?.takeIf { it.isNotBlank() }

        if (cleanName == null && cleanNumber == null) return Result.success(emptyList())

        return try {
            if (cleanName != null && cleanNumber != null && cleanSetId != null) {
                val setQuery = "name:\"$cleanName*\" number:\"$cleanNumber\" set.id:$cleanSetId"
                val setResult = api.searchCards(query = setQuery, pageSize = 10)
                if (setResult.data.isNotEmpty()) {
                    return Result.success(setResult.data.distinctBy { it.id })
                }
            }

            if (cleanName != null && cleanNumber != null) {
                val query = "name:\"$cleanName*\" number:\"$cleanNumber\""
                val result = api.searchCards(query = query, pageSize = 10)
                if (result.data.isNotEmpty()) return Result.success(result.data.distinctBy { it.id })
            }

            if (cleanName != null && cleanName.length >= 3) {
                val query = "name:\"$cleanName*\""
                val result = api.searchCards(query = query, pageSize = 10)
                if (result.data.isNotEmpty()) {
                    if (cleanNumber != null) {
                        val filtered = result.data.filter { it.number == cleanNumber }
                        if (filtered.isNotEmpty()) return Result.success(filtered.distinctBy { it.id })
                    }
                    return Result.success(result.data.distinctBy { it.id })
                }
                val firstWord = cleanName.split(" ").firstOrNull()?.trim() ?: cleanName
                if (firstWord.length >= 3 && firstWord != cleanName) {
                    val fallback = api.searchCards(query = "name:\"$firstWord*\"", pageSize = 15)
                    if (fallback.data.isNotEmpty()) {
                        if (cleanNumber != null) {
                            val filtered = fallback.data.filter { it.number == cleanNumber }
                            if (filtered.isNotEmpty()) return Result.success(filtered.distinctBy { it.id })
                        }
                        return Result.success(fallback.data.distinctBy { it.id })
                    }
                }
            }

            if (cleanNumber != null) {
                val result = api.searchCards(query = "number:\"$cleanNumber\"", pageSize = 20)
                return Result.success(result.data.distinctBy { it.id })
            }

            Result.success(emptyList())
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun sanitizeQuery(raw: String): String {
        return raw
            .replace(SANITIZE_SPECIAL, "")
            .replace(SANITIZE_NON_WORD, "")
            .replace(SANITIZE_MULTI_SPACE, " ")
            .trim()
    }

    fun getCandidateSetIdsByPrintedTotal(total: String?, tolerance: Int = 1): List<String> {
        return getCandidateSetsByPrintedTotal(total, tolerance).map { it.id }
    }

    fun getPrintedTotalForSet(setId: String?): Int? {
        val safeSetId = setId?.takeIf { it.isNotBlank() } ?: return null
        return memorySets?.firstOrNull { it.id == safeSetId }?.printedTotal
    }

    private fun getCandidateSetsByPrintedTotal(total: String?, tolerance: Int): List<TcgSet> {
        val parsedTotal = total?.toIntOrNull() ?: return emptyList()
        val sets = memorySets ?: return emptyList()

        val exactMatches = sets.filter {
            !isTcgdexSetId(it.id) && it.printedTotal == parsedTotal
        }
        if (exactMatches.isNotEmpty()) {
            return exactMatches.sortedByDescending { it.releaseDate }
        }

        if (tolerance <= 0) return emptyList()

        return sets
            .asSequence()
            .filter { !isTcgdexSetId(it.id) }
            .map { set -> set to abs(set.printedTotal - parsedTotal) }
            .filter { (_, diff) -> diff in 1..tolerance }
            .sortedWith(compareBy<Pair<TcgSet, Int>> { it.second }.thenByDescending { it.first.releaseDate })
            .map { it.first }
            .toList()
    }

    suspend fun getCard(cardId: String): Result<TcgCard> {
        return try { Result.success(api.getCard(cardId).data) }
        catch (e: Exception) { Result.failure(e) }
    }
}
