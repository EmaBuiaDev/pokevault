package com.emabuia.pokevault.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

// ── Modelli risposta API ──

data class SetsResponse(
    val data: List<TcgSet> = emptyList()
)

data class CardsResponse(
    val data: List<TcgCard> = emptyList(),
    val totalCount: Int = 0
)

data class SingleCardResponse(
    val data: TcgCard
)

data class TcgSet(
    val id: String = "",
    val name: String = "",
    val series: String = "",
    val printedTotal: Int = 0,
    val total: Int = 0,
    val releaseDate: String = "",
    val images: SetImages = SetImages()
)

data class SetImages(
    val symbol: String = "",
    val logo: String = ""
)

data class TcgCard(
    val id: String = "",
    val name: String = "",
    val supertype: String = "",
    val subtypes: List<String>? = null,
    val hp: String? = null,
    val types: List<String>? = null,
    val set: TcgCardSet? = null,
    val number: String = "",
    val rarity: String? = null,
    val images: CardImages = CardImages(),
    val tcgplayer: TcgPlayer? = null,
    val cardmarket: CardMarket? = null
)

data class TcgCardSet(
    val id: String = "",
    val name: String = "",
    val series: String = ""
)

data class CardImages(
    val small: String = "",
    val large: String = ""
)

data class TcgPlayer(
    val url: String = "",
    val prices: Map<String, TcgPriceInfo>? = null
)

data class TcgPriceInfo(
    val low: Double? = null,
    val mid: Double? = null,
    val high: Double? = null,
    val market: Double? = null
)

data class CardMarket(
    val url: String = "",
    val prices: CardMarketPrices? = null
)

data class CardMarketPrices(
    val averageSellPrice: Double? = null,
    val lowPrice: Double? = null,
    val trendPrice: Double? = null,
    val lowPriceExPlus: Double? = null,
    val suggestedPrice: Double? = null,
    val avg1: Double? = null,
    val avg7: Double? = null,
    val avg30: Double? = null,
    val reverseHoloLow: Double? = null,
    val reverseHoloTrend: Double? = null
)

// ── Retrofit Interface ──

interface PokeTcgApiService {

    @GET("v2/sets")
    suspend fun getSets(
        @Query("orderBy") orderBy: String = "-releaseDate",
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50
    ): SetsResponse

    @GET("v2/sets/{id}")
    suspend fun getSet(
        @Path("id") setId: String
    ): SingleSetResponse

    @GET("v2/cards")
    suspend fun getCardsBySet(
        @Query("q") query: String,
        @Query("orderBy") orderBy: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 250
    ): CardsResponse

    @GET("v2/cards/{id}")
    suspend fun getCard(
        @Path("id") cardId: String
    ): SingleCardResponse

    @GET("v2/cards")
    suspend fun searchCards(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): CardsResponse
}

data class SingleSetResponse(
    val data: TcgSet
)
