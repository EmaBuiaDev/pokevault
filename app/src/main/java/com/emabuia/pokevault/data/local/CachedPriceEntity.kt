package com.emabuia.pokevault.data.local

import androidx.room.Entity
import com.emabuia.pokevault.data.remote.PokeWalletPriceData

@Entity(
    tableName = "prices",
    primaryKeys = ["cardId", "setCode"]
)
data class CachedPriceEntity(
    val cardId: String,
    val setCode: String,
    val eurAvg: Double?,
    val eurLow: Double?,
    val eurTrend: Double?,
    val eurAvg1: Double?,
    val eurAvg7: Double?,
    val eurAvg30: Double?,
    val eurVariantType: String?,
    val usdMarket: Double?,
    val usdLow: Double?,
    val cardMarketUrl: String?,
    val tcgPlayerUrl: String?,
    val cachedAt: Long = System.currentTimeMillis()
)

fun CachedPriceEntity.toPriceData(): PokeWalletPriceData = PokeWalletPriceData(
    eurAvg = eurAvg,
    eurLow = eurLow,
    eurTrend = eurTrend,
    eurAvg1 = eurAvg1,
    eurAvg7 = eurAvg7,
    eurAvg30 = eurAvg30,
    eurVariantType = eurVariantType,
    usdMarket = usdMarket,
    usdLow = usdLow,
    cardMarketUrl = cardMarketUrl,
    tcgPlayerUrl = tcgPlayerUrl
)

fun PokeWalletPriceData.toEntity(cardId: String, setCode: String): CachedPriceEntity =
    CachedPriceEntity(
        cardId = cardId,
        setCode = setCode,
        eurAvg = eurAvg,
        eurLow = eurLow,
        eurTrend = eurTrend,
        eurAvg1 = eurAvg1,
        eurAvg7 = eurAvg7,
        eurAvg30 = eurAvg30,
        eurVariantType = eurVariantType,
        usdMarket = usdMarket,
        usdLow = usdLow,
        cardMarketUrl = cardMarketUrl,
        tcgPlayerUrl = tcgPlayerUrl
    )
