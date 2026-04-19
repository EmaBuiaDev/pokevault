package com.emabuia.pokevault.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emabuia.pokevault.data.remote.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(
    tableName = "cards",
    indices = [Index(value = ["setId"])]
)
data class CachedCardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val supertype: String,
    val subtypesJson: String?,
    val hp: String?,
    val typesJson: String?,
    val setId: String,
    val setName: String,
    val setSeries: String,
    val number: String,
    val rarity: String?,
    val smallImageUrl: String,
    val largeImageUrl: String,
    val tcgplayerUrl: String?,
    val tcgplayerPricesJson: String?,
    val cardmarketUrl: String?,
    val cardmarketAvgSellPrice: Double?,
    val cardmarketLowPrice: Double?,
    val cardmarketTrendPrice: Double?,
    val cardmarketAvg1: Double?,
    val cardmarketAvg7: Double?,
    val cardmarketAvg30: Double?,
    val cachedAt: Long = System.currentTimeMillis()
) {
    companion object {
        private val gson = Gson()

        fun fromStringList(json: String?): List<String>? =
            json?.let { gson.fromJson(it, object : TypeToken<List<String>>() {}.type) }

        fun toStringListJson(list: List<String>?): String? =
            list?.let { gson.toJson(it) }

        fun fromPriceMap(json: String?): Map<String, TcgPriceInfo>? =
            json?.let { gson.fromJson(it, object : TypeToken<Map<String, TcgPriceInfo>>() {}.type) }

        fun toPriceMapJson(map: Map<String, TcgPriceInfo>?): String? =
            map?.let { gson.toJson(it) }
    }
}

fun CachedCardEntity.toTcgCard(): TcgCard = TcgCard(
    id = id,
    name = name,
    supertype = supertype,
    subtypes = CachedCardEntity.fromStringList(subtypesJson),
    hp = hp,
    types = CachedCardEntity.fromStringList(typesJson),
    set = TcgCardSet(id = setId, name = setName, series = setSeries),
    number = number,
    rarity = rarity,
    images = CardImages(small = smallImageUrl, large = largeImageUrl),
    tcgplayer = tcgplayerUrl?.let { url ->
        TcgPlayer(
            url = url,
            prices = CachedCardEntity.fromPriceMap(tcgplayerPricesJson)
        )
    },
    cardmarket = cardmarketUrl?.let { url ->
        CardMarket(
            url = url,
            prices = CardMarketPrices(
                averageSellPrice = cardmarketAvgSellPrice,
                lowPrice = cardmarketLowPrice,
                trendPrice = cardmarketTrendPrice,
                avg1 = cardmarketAvg1,
                avg7 = cardmarketAvg7,
                avg30 = cardmarketAvg30
            )
        )
    }
)

fun TcgCard.toEntity(): CachedCardEntity = CachedCardEntity(
    id = id,
    name = name,
    supertype = supertype,
    subtypesJson = CachedCardEntity.toStringListJson(subtypes),
    hp = hp,
    typesJson = CachedCardEntity.toStringListJson(types),
    setId = set?.id.orEmpty(),
    setName = set?.name.orEmpty(),
    setSeries = set?.series.orEmpty(),
    number = number,
    rarity = rarity,
    smallImageUrl = images.small,
    largeImageUrl = images.large,
    tcgplayerUrl = tcgplayer?.url,
    tcgplayerPricesJson = CachedCardEntity.toPriceMapJson(tcgplayer?.prices),
    cardmarketUrl = cardmarket?.url,
    cardmarketAvgSellPrice = cardmarket?.prices?.averageSellPrice,
    cardmarketLowPrice = cardmarket?.prices?.lowPrice,
    cardmarketTrendPrice = cardmarket?.prices?.trendPrice,
    cardmarketAvg1 = cardmarket?.prices?.avg1,
    cardmarketAvg7 = cardmarket?.prices?.avg7,
    cardmarketAvg30 = cardmarket?.prices?.avg30
)
