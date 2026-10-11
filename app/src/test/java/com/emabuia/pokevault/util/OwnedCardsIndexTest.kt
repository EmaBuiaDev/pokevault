package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.model.PokemonCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le carte possedute come le vede la ricerca carte: bordo verde e stampe. */
class OwnedCardsIndexTest {

    private fun owned(apiCardId: String, variant: String = "Normal") =
        PokemonCard(apiCardId = apiCardId, variant = variant, quantity = 1)

    @Test
    fun `le stampe della stessa carta si raccolgono, i doppioni collassano`() {
        val index = OwnedCardsIndex.from(
            listOf(
                owned("sv4pt5-1", "Normal"),
                owned("sv4pt5-1", "Reverse Holo"),
                owned("sv4pt5-1", "Normal"),
                owned("swsh1-25", "Holo")
            )
        )
        assertEquals(setOf("sv4pt5-1", "swsh1-25"), index.ids)
        assertEquals(setOf("Normal", "Reverse Holo"), index.variants["sv4pt5-1"])
        assertEquals(setOf("Holo"), index.variants["swsh1-25"])
    }

    @Test
    fun `le carte senza id del catalogo non si possono riconoscere`() {
        val index = OwnedCardsIndex.from(listOf(owned(""), owned("  ")))
        assertTrue(index.ids.isEmpty())
    }

    @Test
    fun `senza stampa la carta c'e' lo stesso, senza pallini`() {
        val index = OwnedCardsIndex.from(listOf(owned("sv4pt5-7", variant = "")))
        assertEquals(setOf("sv4pt5-7"), index.ids)
        assertEquals(null, index.variants["sv4pt5-7"])
    }

    @Test
    fun `una copia appena aggiunta compare subito, senza perdere le altre stampe`() {
        val index = OwnedCardsIndex.from(listOf(owned("sv4pt5-1", "Normal")))
            .plus("sv4pt5-1", "Holo")
            .plus("sv4pt5-9", "Normal")
        assertEquals(setOf("sv4pt5-1", "sv4pt5-9"), index.ids)
        assertEquals(setOf("Normal", "Holo"), index.variants["sv4pt5-1"])
    }

    @Test
    fun `un id vuoto non si aggiunge`() {
        val index = OwnedCardsIndex().plus("", "Holo")
        assertTrue(index.ids.isEmpty())
    }
}
