package com.example.pokevault.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://api.pokemontcg.io/"

    // Registrati gratis su https://dev.pokemontcg.io/ per una API key
    private const val API_KEY = "" // TODO: inserisci la tua API key

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

    private var cachedSets: List<TcgSet>? = null
    private val cachedCards = mutableMapOf<String, List<TcgCard>>()

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

    suspend fun getSetInfo(setId: String): Result<TcgSet> {
        return try {
            val response = api.getSet(setId)
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

    suspend fun searchCards(name: String, page: Int = 1): Result<List<TcgCard>> {
        return try {
            val response = api.searchCards(query = "name:\"$name*\"", page = page)
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCard(cardId: String): Result<TcgCard> {
        return try {
            val response = api.getCard(cardId)
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
