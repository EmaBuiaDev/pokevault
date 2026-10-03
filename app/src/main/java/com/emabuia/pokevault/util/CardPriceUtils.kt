package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.remote.CardMarket
import com.emabuia.pokevault.data.remote.CardMarketPrices
import com.emabuia.pokevault.data.remote.PokeWalletPriceData
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgPlayer
import com.emabuia.pokevault.data.remote.TcgPriceInfo

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

/**
 * La carta con i prezzi dello snapshot italiano, nella stessa forma in cui li
 * mette il Pokedex (SetDetailViewModel.withPriceData, di cui e' la copia: la
 * scheda della carta legge il prezzo in alto da `cardmarket`, non dai prezzi
 * live). Una copia e non un riuso per non toccare il Pokedex, che funziona;
 * se si cambia una delle due, cambiare anche l'altra.
 */
fun TcgCard.withSnapshotPrices(priceData: PokeWalletPriceData): TcgCard {
    val cmPrices = CardMarketPrices(
        averageSellPrice = priceData.eurAvg,
        lowPrice = priceData.eurLow,
        trendPrice = priceData.eurTrend,
        avg1 = priceData.eurAvg1,
        avg7 = priceData.eurAvg7,
        avg30 = priceData.eurAvg30
    )
    val tcgPlayer = if (priceData.usdMarket != null || priceData.usdLow != null) {
        TcgPlayer(
            url = priceData.tcgPlayerUrl ?: tcgplayer?.url.orEmpty(),
            prices = mapOf(
                CardOptions.USD_ONLY_PRICE_KEY to TcgPriceInfo(
                    low = priceData.usdLow,
                    market = priceData.usdMarket
                )
            )
        )
    } else {
        tcgplayer
    }
    return copy(
        cardmarket = CardMarket(
            url = priceData.cardMarketUrl.orEmpty(),
            prices = cmPrices
        ),
        tcgplayer = tcgPlayer
    )
}

fun CardMarketPrices?.hasPositiveEurPrice(): Boolean {
    val prices = this ?: return false
    return (prices.lowPrice ?: 0.0) > 0.0 || (prices.averageSellPrice ?: 0.0) > 0.0
}
