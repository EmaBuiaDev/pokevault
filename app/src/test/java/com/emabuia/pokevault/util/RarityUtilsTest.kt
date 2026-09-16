package com.emabuia.pokevault.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * getRarityInfo e' una catena di `when` con rami `contains`, quindi l'ordine
 * conta piu' del contenuto: un ramo generico messo troppo in alto si mangia in
 * silenzio le rarita' piu' specifiche, e una stringa che nessun ramo riconosce
 * finisce in "Altro" senza che niente fallisca. Questi test fissano l'esito
 * delle rarita' che si somigliano di piu'.
 */
class RarityUtilsTest {

    @Test
    fun `futuristic rare e' riconosciuta e non finisce in Altro`() {
        val info = RarityUtils.getRarityInfo("Futuristic rare")
        assertEquals(14, info.sortOrder)
        assertEquals("✵", info.emoji)
    }

    @Test
    fun `futuristic rare regge maiuscole spazi e la forma italiana`() {
        assertEquals(14, RarityUtils.getRarityInfo("FUTURISTIC RARE").sortOrder)
        assertEquals(14, RarityUtils.getRarityInfo("  Futuristic Rare  ").sortOrder)
        assertEquals(14, RarityUtils.getRarityInfo("Rara Futuristica").sortOrder)
    }

    @Test
    fun `futuristic rare e' lucida`() {
        assertTrue(RarityUtils.hasFoilFinish("Futuristic rare"))
    }

    @Test
    fun `futuristic rare non ruba le altre rarita' che contengono rare`() {
        assertEquals(7, RarityUtils.getRarityInfo("Special illustration rare").sortOrder)
        assertEquals(5, RarityUtils.getRarityInfo("Illustration rare").sortOrder)
        assertEquals(6, RarityUtils.getRarityInfo("Ultra Rare").sortOrder)
        assertEquals(3, RarityUtils.getRarityInfo("Double rare").sortOrder)
        assertEquals(13, RarityUtils.getRarityInfo("Secret Rare").sortOrder)
        assertEquals(2, RarityUtils.getRarityInfo("Rare").sortOrder)
        assertEquals(0, RarityUtils.getRarityInfo("Common").sortOrder)
        assertEquals(1, RarityUtils.getRarityInfo("Uncommon").sortOrder)
    }

    @Test
    fun `rarita' sconosciuta o assente resta Altro e senza foil`() {
        assertEquals(12, RarityUtils.getRarityInfo("Mythical Rare").sortOrder)
        assertEquals(12, RarityUtils.getRarityInfo(null).sortOrder)
        assertFalse(RarityUtils.hasFoilFinish(null))
        assertFalse(RarityUtils.hasFoilFinish("Mythical Rare"))
    }
}
