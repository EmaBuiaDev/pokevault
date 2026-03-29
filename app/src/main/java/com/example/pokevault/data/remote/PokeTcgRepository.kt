package com.example.pokevault.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://api.pokemontcg.io/"

    // Se hai una API key, inseriscila qui (opzionale ma consigliata)
    // Registrati gratis su https://dev.pokemontcg.io/
    private const val API_KEY = "c1906986-6518-4fd0-9c42-c935bbc9cfde" // TODO: inserisci la tua API key

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
            if (API_KEY.isNotBlank()) {
                request.addHeader("X-Api-Key", API_KEY)
            }
            chain.proceed(request.build())
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: PokeTcgApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PokeTcgApiService::class.java)
    }
}

class PokeTcgRepository {

    private val api = RetrofitClient.apiService

    // Cache in memoria
    private var cachedSets: List<TcgSet>? = null
    private val cachedCards = mutableMapOf<String, List<TcgCard>>()

    // ── Lista tutti i set (con cache) ──
    suspend fun getSets(forceRefresh: Boolean = false): Result<List<TcgSet>> {
        if (!forceRefresh && cachedSets != null) {
            return Result.success(cachedSets!!)
        }
        return try {
            val allSets = mutableListOf<TcgSet>()
            var page = 1
            do {
                val response = api.getSets(page = page, pageSize = 250)
                allSets.addAll(response.data)
                page++
            } while (response.data.size == 250)

            cachedSets = allSets
            Result.success(allSets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Carte di un set specifico (con cache) ──
    suspend fun getCardsBySet(setId: String, forceRefresh: Boolean = false): Result<List<TcgCard>> {
        if (!forceRefresh && cachedCards.containsKey(setId)) {
            return Result.success(cachedCards[setId]!!)
        }
        return try {
            val response = api.getCardsBySet(query = "set.id:$setId")
            cachedCards[setId] = response.data
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Cerca carte per nome ──
    suspend fun searchCards(name: String, page: Int = 1): Result<List<TcgCard>> {
        return try {
            val response = api.searchCards(query = "name:\"$name*\"", page = page)
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Dettaglio singola carta ──
    suspend fun getCard(cardId: String): Result<TcgCard> {
        return try {
            val response = api.getCard(cardId)
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
