package com.example.pokevault.data.remote

import android.content.Context
import android.util.Log
import com.example.pokevault.util.AppLocale
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://api.pokemontcg.io/"
    private val API_KEY = com.example.pokevault.BuildConfig.POKETCG_API_KEY

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
    private val gson = Gson()

    // Cache memoria
    private var memorySets: List<TcgSet>? = null
    private val memoryCards = mutableMapOf<String, List<TcgCard>>()

    companion object {
        private const val PREFS_NAME = "pokevault_cache"
        private const val SETS_CACHE_KEY = "cached_sets"
        private const val SETS_TIME_KEY = "cached_sets_time"
        private const val CARDS_PREFIX = "cached_cards_"
        private const val CARDS_TIME_PREFIX = "cached_cards_time_"
        private const val CACHE_DURATION = 24 * 60 * 60 * 1000L // 24 ore
        private const val CARDS_CACHE_DURATION = 6 * 60 * 60 * 1000L // 6 ore per carte
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
            Log.w("PokeTcgRepository", "Errore salvataggio cache sets", e)
        }
    }

    private fun loadSetsFromCache(context: Context, ignoreExpiry: Boolean = false): List<TcgSet>? {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val time = prefs.getLong(SETS_TIME_KEY, 0)
            if (!ignoreExpiry && System.currentTimeMillis() - time > CACHE_DURATION) return null
            val json = prefs.getString(SETS_CACHE_KEY, null) ?: return null
            return gson.fromJson(json, object : TypeToken<List<TcgSet>>() {}.type)
        } catch (e: Exception) {
            Log.w("PokeTcgRepository", "Errore lettura cache sets", e)
            return null
        }
    }

    suspend fun getSets(context: Context? = null, forceRefresh: Boolean = false): Result<List<TcgSet>> {
        if (!forceRefresh && memorySets != null) return Result.success(memorySets!!)
        if (!forceRefresh && context != null) {
            loadSetsFromCache(context)?.let { memorySets = it; return Result.success(it) }
        }
        return try {
            val allSets = mutableListOf<TcgSet>()
            var page = 1
            do {
                val response = api.getSets(page = page, pageSize = 250)
                allSets.addAll(response.data)
                page++
            } while (response.data.size == 250)
            memorySets = allSets
            context?.let { saveSetsToCache(it, allSets) }
            Result.success(allSets)
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

    private fun saveCardsToCache(context: Context, setId: String, cards: List<TcgCard>) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("$CARDS_PREFIX$setId", gson.toJson(cards))
                .putLong("$CARDS_TIME_PREFIX$setId", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.w("PokeTcgRepository", "Errore salvataggio cache cards per set $setId", e)
        }
    }

    private fun loadCardsFromCache(context: Context, setId: String, ignoreExpiry: Boolean = false): List<TcgCard>? {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val time = prefs.getLong("$CARDS_TIME_PREFIX$setId", 0)
            if (!ignoreExpiry && System.currentTimeMillis() - time > CARDS_CACHE_DURATION) return null
            val json = prefs.getString("$CARDS_PREFIX$setId", null) ?: return null
            return gson.fromJson(json, object : TypeToken<List<TcgCard>>() {}.type)
        } catch (e: Exception) {
            Log.w("PokeTcgRepository", "Errore lettura cache cards per set $setId", e)
            return null
        }
    }

    suspend fun getCardsBySet(setId: String, context: Context? = null, forceRefresh: Boolean = false): Result<List<TcgCard>> {
        // 1. Controllo Cache (Memoria o Disco)
        if (!forceRefresh) {
            val cached = memoryCards[setId] ?: (context?.let { loadCardsFromCache(it, setId) })
            
            // LOGICA DI SICUREZZA: Se abbiamo dati e NON sono esattamente 250, 
            // assumiamo che il set sia completo. Se sono esattamente 250, sospettiamo il vecchio bug
            // e procediamo comunque all'API per scaricare il resto.
            if (cached != null && cached.isNotEmpty()) {
                val isSuspiciouslyTruncated = cached.size == 250 
                if (!isSuspiciouslyTruncated) {
                    memoryCards[setId] = cached
                    return Result.success(cached)
                }
            }
        }

        // 2. API con Paginazione
        return try {
            val allCards = mutableListOf<TcgCard>()
            var page = 1
            var fetchedCount: Int
            do {
                val response = api.getCardsBySet(query = "set.id:$setId", page = page, pageSize = 250)
                allCards.addAll(response.data)
                fetchedCount = response.data.size
                page++
            } while (fetchedCount == 250)

            memoryCards[setId] = allCards
            context?.let { saveCardsToCache(it, setId, allCards) }
            Result.success(allCards)
        } catch (e: Exception) {
            // Fallback su cache in caso di errore
            val fallback = memoryCards[setId] ?: (context?.let { loadCardsFromCache(it, setId, ignoreExpiry = true) })
            if (fallback != null) return Result.success(fallback)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════
    // ALTRI METODI
    // ══════════════════════════════════════

    suspend fun getSetInfo(setId: String): Result<TcgSet> {
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
            val total = match.groupValues[2]
            
            // Cerchiamo i set che hanno quel totale stampato
            val candidateSets = memorySets?.filter { it.printedTotal.toString() == total } ?: emptyList()
            
            return if (candidateSets.isNotEmpty()) {
                val setIds = candidateSets.joinToString(" OR ") { "set.id:${it.id}" }
                val apiQuery = "($setIds) number:\"$number\""
                try {
                    Result.success(api.searchCards(query = apiQuery, page = page).data)
                } catch (e: Exception) { Result.failure(e) }
            } else {
                // Fallback: cerca solo per numero se non troviamo il set corrispondente al totale
                try {
                    Result.success(api.searchCards(query = "number:\"$number\"", page = page).data)
                } catch (e: Exception) { Result.failure(e) }
            }
        }

        val apiQuery = when {
            // Caso solo numero (es. "001")
            trimmed.matches(Regex("""^\d+$""")) -> {
                val number = trimmed.trimStart('0').ifEmpty { "0" }
                "number:\"$number\""
            }
            // Caso 2: Termini comuni in italiano
            isIt && trimmed.equals("energia", ignoreCase = true) -> "supertype:energy"
            isIt && trimmed.equals("allenatore", ignoreCase = true) -> "supertype:trainer"
            isIt && trimmed.equals("aiuto", ignoreCase = true) -> "subtypes:supporter"
            isIt && trimmed.equals("strumento", ignoreCase = true) -> "subtypes:item"
            isIt && trimmed.equals("stadio", ignoreCase = true) -> "subtypes:stadium"
            // Caso 3: Tipi in italiano
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
            // Default: Cerca per nome con wildcard
            else -> "name:\"${sanitizeQuery(trimmed)}*\""
        }

        return try {
            Result.success(api.searchCards(query = apiQuery, page = page).data)
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Ricerca tollerante per lo scanner.
     * Sanitizza il testo OCR, prova prima il nome completo, poi solo la prima parola.
     */
    suspend fun searchCardsFuzzy(name: String, page: Int = 1): Result<List<TcgCard>> {
        val clean = sanitizeQuery(name)
        if (clean.isBlank()) return Result.success(emptyList())
        return try {
            // Prima prova: nome completo con wildcard (tra virgolette per gestire spazi)
            val result = api.searchCards(query = "name:\"$clean*\"", page = page, pageSize = 30)
            if (result.data.isNotEmpty()) {
                return Result.success(result.data)
            }
            // Fallback: solo prima parola con wildcard
            val firstWord = clean.split(" ").firstOrNull()?.trim() ?: clean
            if (firstWord.length >= 3 && firstWord != clean) {
                val fallback = api.searchCards(query = "name:\"$firstWord*\"", page = page, pageSize = 30)
                Result.success(fallback.data)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Ricerca precisa per lo scanner: combina nome + numero carta.
     * Il numero (es. "25" da "025/198") è il dato OCR più affidabile.
     * Cerca con: name + number → solo name → solo number
     */
    suspend fun searchByNameAndNumber(name: String?, number: String?): Result<List<TcgCard>> {
        val cleanName = name?.let { sanitizeQuery(it) }?.takeIf { it.isNotBlank() }
        val cleanNumber = number?.trim()?.trimStart('0')?.takeIf { it.isNotBlank() }

        if (cleanName == null && cleanNumber == null) return Result.success(emptyList())

        return try {
            // Strategia 1: nome + numero (match molto preciso)
            if (cleanName != null && cleanNumber != null) {
                val query = "name:\"$cleanName*\" number:\"$cleanNumber\""
                val result = api.searchCards(query = query, pageSize = 10)
                if (result.data.isNotEmpty()) return Result.success(result.data)
            }

            // Strategia 2: solo nome con wildcard
            if (cleanName != null && cleanName.length >= 3) {
                val query = "name:\"$cleanName*\""
                val result = api.searchCards(query = query, pageSize = 10)
                if (result.data.isNotEmpty()) {
                    // Se abbiamo anche il numero, filtra lato client
                    if (cleanNumber != null) {
                        val filtered = result.data.filter { it.number == cleanNumber }
                        if (filtered.isNotEmpty()) return Result.success(filtered)
                    }
                    return Result.success(result.data)
                }
                // Prova solo prima parola
                val firstWord = cleanName.split(" ").firstOrNull()?.trim() ?: cleanName
                if (firstWord.length >= 3 && firstWord != cleanName) {
                    val fallback = api.searchCards(query = "name:\"$firstWord*\"", pageSize = 15)
                    if (fallback.data.isNotEmpty()) {
                        if (cleanNumber != null) {
                            val filtered = fallback.data.filter { it.number == cleanNumber }
                            if (filtered.isNotEmpty()) return Result.success(filtered)
                        }
                        return Result.success(fallback.data)
                    }
                }
            }

            // Strategia 3: solo numero (meno preciso, molti risultati)
            if (cleanNumber != null) {
                val result = api.searchCards(query = "number:\"$cleanNumber\"", pageSize = 20)
                return Result.success(result.data)
            }

            Result.success(emptyList())
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Rimuove caratteri speciali che rompono la query Lucene dell'API PokéTCG.
     */
    private fun sanitizeQuery(raw: String): String {
        return raw
            .replace(Regex("[+\\-=&|><!(){}\\[\\]^~?:\\\\/]"), "") // chars speciali Lucene
            .replace(Regex("[^a-zA-ZÀ-ÿ0-9\\s'-]"), "")            // solo lettere, numeri, spazi, apostrofo, trattino
            .replace(Regex("\\s+"), " ")                             // spazi multipli
            .trim()
    }

    suspend fun getCard(cardId: String): Result<TcgCard> {
        return try { Result.success(api.getCard(cardId).data) }
        catch (e: Exception) { Result.failure(e) }
    }
}
