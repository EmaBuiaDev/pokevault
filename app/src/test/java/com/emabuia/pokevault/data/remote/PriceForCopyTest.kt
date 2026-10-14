package com.emabuia.pokevault.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Prezzo e link Cardmarket nel dettaglio di una copia in Collezione. */
class PriceForCopyTest {

    private val base = PokeWalletPriceData(
        eurLow = 1.0,
        eurAvg = 1.5,
        cardMarketUrl = "https://www.cardmarket.com/it/Pokemon/Products?idProduct=100",
        variantPrices = mapOf(
            "Reverse" to PokeWalletPriceData(eurLow = 3.0),
            "Normal|stamp=pokemon-day" to PokeWalletPriceData(
                eurLow = 9.0,
                cardMarketUrl = "https://www.cardmarket.com/it/Pokemon/Products?idProduct=200"
            )
        )
    )

    @Test
    fun `la stampa base prende prezzo e link della carta`() {
        val shown = base.forCopy("Normal")
        assertEquals(1.0, shown.eurLow)
        assertEquals(base.cardMarketUrl, shown.cardMarketUrl)
    }

    @Test
    fun `la Reverse ha il suo prezzo e il link della carta`() {
        val shown = base.forCopy("Reverse")
        assertEquals(3.0, shown.eurLow)
        assertNull(shown.eurAvg)
        assertEquals(base.cardMarketUrl, shown.cardMarketUrl)
    }

    @Test
    fun `la timbrata porta al suo prodotto Cardmarket`() {
        val shown = base.forCopy("Normal|stamp=pokemon-day")
        assertEquals(9.0, shown.eurLow)
        assertEquals("https://www.cardmarket.com/it/Pokemon/Products?idProduct=200", shown.cardMarketUrl)
    }

    @Test
    fun `una speciale senza prezzo suo resta senza prezzo ma col link della carta`() {
        val shown = base.forCopy("Reverse|foil=pokeball")
        assertFalse(shown.hasEurPrices)
        assertEquals(base.cardMarketUrl, shown.cardMarketUrl)
    }

    @Test
    fun `senza stampa vale la voce della carta`() {
        assertEquals(1.0, base.forCopy(null).eurLow)
        assertEquals(1.0, base.forCopy("").eurLow)
    }
}
