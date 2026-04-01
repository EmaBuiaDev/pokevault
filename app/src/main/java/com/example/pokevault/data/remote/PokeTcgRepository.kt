package com.example.pokevault.data.remote

import android.content.Context
import android.util.Log
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
        // 1. Cache memoria
        if (!forceRefresh && memoryCards.containsKey(setId)) return Result.success(memoryCards[setId]!!)
        // 2. Cache disco
        if (!forceRefresh && context != null) {
            loadCardsFromCache(context, setId)?.let {
                memoryCards[setId] = it; return Result.success(it)
            }
        }
        // 3. API
        return try {
            val response = api.getCardsBySet(query = "set.id:$setId")
            memoryCards[setId] = response.data
            context?.let { saveCardsToCache(it, setId, response.data) }
            Result.success(response.data)
        } catch (e: Exception) {
            if (context != null) {
                loadCardsFromCache(context, setId, ignoreExpiry = true)?.let {
                    memoryCards[setId] = it; return Result.success(it)
                }
            }
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════
    // ALTRI METODI (invariati)
    // ══════════════════════════════════════

    suspend fun getSetInfo(setId: String): Result<TcgSet> {
        return try { Result.success(api.getSet(setId).data) }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun searchCards(name: String, page: Int = 1): Result<List<TcgCard>> {
        return try { Result.success(api.searchCards(query = "name:\"$name*\"", page = page).data) }
        catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Ricerca più tollerante: senza virgolette, ogni parola con wildcard.
     * Utile per testo OCR impreciso. Es: "Chari" → name:Chari*
     */
    suspend fun searchCardsFuzzy(name: String, page: Int = 1): Result<List<TcgCard>> {
        return try {
            // Prima prova con wildcard senza virgolette (match parziale)
            val query = "name:${name}*"
            val result = api.searchCards(query = query, page = page, pageSize = 30)
            if (result.data.isNotEmpty()) {
                return Result.success(result.data)
            }
            // Fallback: cerca solo la prima parola
            val firstWord = name.split(" ", "-").firstOrNull()?.trim() ?: name
            if (firstWord.length >= 3 && firstWord != name) {
                val fallbackResult = api.searchCards(query = "name:${firstWord}*", page = page, pageSize = 30)
                Result.success(fallbackResult.data)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getCard(cardId: String): Result<TcgCard> {
        return try { Result.success(api.getCard(cardId).data) }
        catch (e: Exception) { Result.failure(e) }
    }
}
