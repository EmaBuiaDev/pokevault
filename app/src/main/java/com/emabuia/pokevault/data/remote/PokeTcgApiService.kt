package com.emabuia.pokevault.data.remote

// Shared domain models used across ViewModels/UI.
// Data is now sourced from PokeWallet and mapped into these models.

data class TcgSet(
    val id: String = "",
    val name: String = "",
    val series: String = "",
    val language: String? = null,
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
