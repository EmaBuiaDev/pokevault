package com.emabuia.pokevault.data.remote

import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class TcgdexCardCount(
    val total: Int = 0,
    val official: Int = 0
)

data class TcgdexSerie(
    val id: String = "",
    val name: String = ""
)

data class TcgdexSetSummary(
    val id: String = "",
    val name: String = "",
    val logo: String = "",
    val symbol: String = "",
    val cardCount: TcgdexCardCount = TcgdexCardCount()
)

data class TcgdexCardSummary(
    val id: String = "",
    val localId: String = "",
    val name: String = "",
    val image: String = ""
)

data class TcgdexSetDetail(
    val id: String = "",
    val name: String = "",
    val logo: String = "",
    val symbol: String = "",
    val releaseDate: String = "",
    val serie: TcgdexSerie? = null,
    val cardCount: TcgdexCardCount = TcgdexCardCount(),
    val cards: List<TcgdexCardSummary> = emptyList()
)

private interface TcgdexApiService {

    @GET("v2/en/sets")
    suspend fun getSets(): List<TcgdexSetSummary>

    @GET("v2/en/sets/{id}")
    suspend fun getSet(
        @Path("id") setId: String
    ): TcgdexSetDetail
}

private object TcgdexRetrofitClient {
    private const val BASE_URL = "https://api.tcgdex.net/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: TcgdexApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TcgdexApiService::class.java)
    }
}

class TcgdexRepository {

    private val api = TcgdexRetrofitClient.apiService
    private val detailCache = ConcurrentHashMap<String, TcgdexSetDetail>()

    suspend fun getMissingSets(existingSets: List<TcgSet>): Result<List<TcgSet>> {
        return try {
            val primaryKeys = existingSets
                .map { buildSetMergeKey(it.name, it.series, canonicalPrimaryCount(it)) }
                .toHashSet()
            val loosePrimaryKeys = existingSets
                .map { buildLooseSetMergeKey(it.name, canonicalPrimaryCount(it)) }
                .toHashSet()

            val summaries = api.getSets()
            val candidateIds = summaries
                .asSequence()
                .filter(::hasTcgdexSummaryAssets)
                .filterNot {
                    buildLooseSetMergeKey(it.name, canonicalTcgdexCount(it.cardCount)) in loosePrimaryKeys
                }
                .map { it.id }
                .toList()

            val details = fetchDetails(candidateIds)

            val extras = details
                .asSequence()
                .filter(::hasCompleteTcgdexAssets)
                .map { detail -> detail.toTcgSet() }
                .filterNot { extra ->
                    buildSetMergeKey(extra.name, extra.series, canonicalPrimaryCount(extra)) in primaryKeys
                }
                .distinctBy { it.id }
                .toList()

            Result.success(extras)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSetInfo(setId: String): Result<TcgSet> {
        return try {
            val detail = getSetDetail(setId)
            Result.success(detail.toTcgSet())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCardsBySet(setId: String): Result<List<TcgCard>> {
        return try {
            val detail = getSetDetail(setId)
            val cards = detail.cards
                .filter { it.image.isNotBlank() }
                .map { card -> detail.toTcgCard(card) }
            Result.success(cards)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getSetDetail(setId: String): TcgdexSetDetail {
        val rawSetId = decodeTcgdexSetId(setId)
        return detailCache[rawSetId] ?: api.getSet(rawSetId).also { detailCache[rawSetId] = it }
    }

    private suspend fun fetchDetails(setIds: List<String>): List<TcgdexSetDetail> = coroutineScope {
        if (setIds.isEmpty()) return@coroutineScope emptyList()

        setIds.chunked(8).flatMap { chunk ->
            chunk.map { setId ->
                async(Dispatchers.IO) {
                    runCatching {
                        detailCache[setId] ?: api.getSet(setId).also { detailCache[setId] = it }
                    }.onFailure { Timber.w(it, "Errore fetch TCGdex set %s", setId) }
                        .getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun TcgdexSetDetail.toTcgSet(): TcgSet {
        val count = canonicalTcgdexCount(cardCount)
        return TcgSet(
            id = encodeTcgdexSetId(id),
            name = name,
            series = serie?.name.orEmpty(),
            printedTotal = count,
            total = cardCount.total.takeIf { it > 0 } ?: count,
            releaseDate = releaseDate,
            images = SetImages(
                symbol = symbol,
                logo = logo
            )
        )
    }

    private fun TcgdexSetDetail.toTcgCard(card: TcgdexCardSummary): TcgCard {
        val setInfo = TcgCardSet(
            id = encodeTcgdexSetId(id),
            name = name,
            series = serie?.name.orEmpty()
        )
        return TcgCard(
            id = card.id,
            name = card.name,
            set = setInfo,
            number = card.localId,
            images = CardImages(
                small = "${card.image}/low.webp",
                large = "${card.image}/high.webp"
            )
        )
    }
}
