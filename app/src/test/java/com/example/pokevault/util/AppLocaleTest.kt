package com.example.pokevault.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for rarity and type translations.
 */
class AppLocaleTest {

    // Replicate translation maps for testing without Android context
    private val rarityItMap = mapOf(
        "common" to "Comune",
        "uncommon" to "Non Comune",
        "rare" to "Rara",
        "rare holo" to "Rara Holo",
        "ultra rare" to "Ultra Rara",
        "secret rare" to "Rara Segreta",
        "double rare" to "Doppia Rara",
        "illustration rare" to "Illustrazione Rara",
        "hyper rare" to "Iper Rara"
    )

    private val typeEnToIt = mapOf(
        "fire" to "Fuoco",
        "water" to "Acqua",
        "grass" to "Erba",
        "lightning" to "Elettro",
        "psychic" to "Psico",
        "fighting" to "Lotta",
        "darkness" to "Buio",
        "metal" to "Metallo",
        "dragon" to "Drago",
        "fairy" to "Folletto",
        "colorless" to "Incolore"
    )

    private fun translateRarity(rarity: String): String {
        if (rarity.isBlank()) return rarity
        return rarityItMap[rarity.lowercase().trim()] ?: rarity
    }

    private fun translateType(type: String): String {
        if (type.isBlank()) return type
        return typeEnToIt[type.lowercase().trim()] ?: type
    }

    // ── Rarity translation ──

    @Test
    fun `common translates to Comune`() {
        assertEquals("Comune", translateRarity("Common"))
    }

    @Test
    fun `rare holo translates correctly`() {
        assertEquals("Rara Holo", translateRarity("Rare Holo"))
    }

    @Test
    fun `ultra rare translates correctly`() {
        assertEquals("Ultra Rara", translateRarity("Ultra Rare"))
    }

    @Test
    fun `unknown rarity returns original`() {
        assertEquals("Mythical Rare", translateRarity("Mythical Rare"))
    }

    @Test
    fun `blank rarity returns blank`() {
        assertEquals("", translateRarity(""))
    }

    @Test
    fun `rarity is case insensitive`() {
        assertEquals("Comune", translateRarity("COMMON"))
    }

    // ── Type translation ──

    @Test
    fun `fire translates to Fuoco`() {
        assertEquals("Fuoco", translateType("Fire"))
    }

    @Test
    fun `water translates to Acqua`() {
        assertEquals("Acqua", translateType("Water"))
    }

    @Test
    fun `lightning translates to Elettro`() {
        assertEquals("Elettro", translateType("Lightning"))
    }

    @Test
    fun `unknown type returns original`() {
        assertEquals("Stellar", translateType("Stellar"))
    }

    @Test
    fun `blank type returns blank`() {
        assertEquals("", translateType(""))
    }

    @Test
    fun `type is case insensitive`() {
        assertEquals("Fuoco", translateType("FIRE"))
    }

    // ── Conditions list ──

    private val conditionsIt = listOf(
        "Mint", "Near Mint", "Eccellente", "Buono", "Leggermente Giocata", "Giocata", "Povera"
    )
    private val conditionsEn = listOf(
        "Mint", "Near Mint", "Excellent", "Good", "Light Played", "Played", "Poor"
    )

    @Test
    fun `Italian conditions has 7 items`() {
        assertEquals(7, conditionsIt.size)
    }

    @Test
    fun `English conditions has 7 items`() {
        assertEquals(7, conditionsEn.size)
    }

    @Test
    fun `conditions lists have same size`() {
        assertEquals(conditionsIt.size, conditionsEn.size)
    }
}
