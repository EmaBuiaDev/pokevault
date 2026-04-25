package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.remote.CardMarketPrices

fun CardMarketPrices?.minimumEurPriceOrZero(): Double {
    val prices = this ?: return 0.0
    return when {
        (prices.lowPrice ?: 0.0) > 0.0 -> prices.lowPrice ?: 0.0
        (prices.averageSellPrice ?: 0.0) > 0.0 -> prices.averageSellPrice ?: 0.0
        else -> 0.0
    }
}

fun CardMarketPrices?.hasPositiveEurPrice(): Boolean {
    val prices = this ?: return false
    return (prices.lowPrice ?: 0.0) > 0.0 || (prices.averageSellPrice ?: 0.0) > 0.0
}
