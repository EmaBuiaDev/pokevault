package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.remote.CardMarketPrices
import com.emabuia.pokevault.data.remote.PokeWalletPriceData

fun CardMarketPrices?.minimumEurPriceOrZero(): Double {
    val prices = this ?: return 0.0
    return when {
        (prices.lowPrice ?: 0.0) > 0.0 -> prices.lowPrice ?: 0.0
        (prices.averageSellPrice ?: 0.0) > 0.0 -> prices.averageSellPrice ?: 0.0
        else -> 0.0
    }
}

/**
 * Il prezzo da salvare per una carta prezzata dallo snapshot italiano: il
 * minimo, poi la tendenza, poi la media -- lo stesso ordine che usavano gia'
 * il recupero in Collezione e TradeRadar. 0 se non c'e' niente.
 */
fun PokeWalletPriceData?.minimumEurOrZero(): Double {
    val data = this ?: return 0.0
    return listOf(data.eurLow, data.eurTrend, data.eurAvg).firstOrNull { (it ?: 0.0) > 0.0 } ?: 0.0
}

fun CardMarketPrices?.hasPositiveEurPrice(): Boolean {
    val prices = this ?: return false
    return (prices.lowPrice ?: 0.0) > 0.0 || (prices.averageSellPrice ?: 0.0) > 0.0
}
