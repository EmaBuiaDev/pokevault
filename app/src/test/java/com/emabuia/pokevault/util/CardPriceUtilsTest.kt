package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.remote.CardMarketPrices
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
}
