package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.remote.CardMarketPrices
import com.emabuia.pokevault.data.remote.PokeWalletPriceData
import com.emabuia.pokevault.data.remote.TcgCard
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests per CardPriceUtils
 * 
 * Testa la logica di calcolo dei prezzi dalle varie fonti
 */
class CardPriceUtilsTest {

    @Test
    fun testMinimumEurPriceWithNullPrices() {
        // Arrange
        val prices: CardMarketPrices? = null

        // Act
        val result = prices.minimumEurPriceOrZero()

        // Assert
        assertEquals(0.0, result, 0.0)
    }

    @Test
    fun testMinimumEurPriceWithLowPrice() {
        // Arrange
        val prices = CardMarketPrices(
            lowPrice = 10.5,
            averageSellPrice = 15.0
        )

        // Act
        val result = prices.minimumEurPriceOrZero()

        // Assert
        assertEquals(10.5, result, 0.01)
    }

    @Test
    fun testMinimumEurPriceWithAverageSellPrice() {
        // Arrange
        val prices = CardMarketPrices(
            lowPrice = null,
            averageSellPrice = 15.0
        )

        // Act
        val result = prices.minimumEurPriceOrZero()

        // Assert
        assertEquals(15.0, result, 0.01)
    }

    @Test
    fun testMinimumEurPriceWithBothZero() {
        // Arrange
        val prices = CardMarketPrices(
            lowPrice = 0.0,
            averageSellPrice = 0.0
        )

        // Act
        val result = prices.minimumEurPriceOrZero()

        // Assert
        assertEquals(0.0, result, 0.0)
    }

    @Test
    fun testHasPositiveEurPriceWithNullPrices() {
        // Arrange
        val prices: CardMarketPrices? = null

        // Act
        val result = prices.hasPositiveEurPrice()

        // Assert
        assertFalse(result)
    }

    @Test
    fun testHasPositiveEurPriceWithValidPrice() {
        // Arrange
        val prices = CardMarketPrices(
            lowPrice = 10.5,
            averageSellPrice = null
        )

        // Act
        val result = prices.hasPositiveEurPrice()

        // Assert
        assertTrue(result)
    }

    @Test
    fun testHasPositiveEurPriceWithZeroPrices() {
        // Arrange
        val prices = CardMarketPrices(
            lowPrice = 0.0,
            averageSellPrice = 0.0
        )

        // Act
        val result = prices.hasPositiveEurPrice()

        // Assert
        assertFalse(result)
    }

    @Test
    fun snapshotPricesFillTheCardLikeThePokedex() {
        val card = TcgCard(id = "ita:me05:30", name = "Slowbro", number = "30")
        val data = PokeWalletPriceData(
            eurAvg = 0.03, eurLow = 0.02, eurTrend = 0.04,
            eurAvg1 = 0.04, eurAvg7 = 0.04, eurAvg30 = 0.03,
            cardMarketUrl = "https://www.cardmarket.com/it/Pokemon/Products?idProduct=895000"
        )

        val priced = card.withSnapshotPrices(data)

        assertEquals(0.02, priced.cardmarket?.prices.minimumEurPriceOrZero(), 0.0001)
        assertEquals(0.04, priced.cardmarket?.prices?.trendPrice ?: 0.0, 0.0001)
        assertEquals("https://www.cardmarket.com/it/Pokemon/Products?idProduct=895000", priced.cardmarket?.url)
        assertNull("senza dollari il TCGplayer resta com era", priced.tcgplayer)
        assertEquals("il resto della carta non cambia", card.copy(cardmarket = priced.cardmarket), priced)
    }

    @Test
    fun minimumEurOrZeroPrefersLowThenTrendThenAvg() {
        assertEquals(0.0, (null as PokeWalletPriceData?).minimumEurOrZero(), 0.0)
        assertEquals(1.0, PokeWalletPriceData(eurLow = 1.0, eurTrend = 2.0, eurAvg = 3.0).minimumEurOrZero(), 0.0)
        assertEquals(2.0, PokeWalletPriceData(eurLow = 0.0, eurTrend = 2.0, eurAvg = 3.0).minimumEurOrZero(), 0.0)
        assertEquals(3.0, PokeWalletPriceData(eurAvg = 3.0).minimumEurOrZero(), 0.0)
    }
}
