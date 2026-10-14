package com.emabuia.pokevault.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le voci della sezione illustratori, uguali per il catalogo italiano e per
 * quello inglese: cambia solo come si legge un cardId ([IllustratorCardRef]).
 */
class IllustratorEntriesTest {

    // Un cardId finto "SET_N": espansione e numero.
    private fun ref(cardId: String): IllustratorCardRef? {
        val parts = cardId.split('_')
        if (parts.size != 2) return null
        return IllustratorCardRef("ita:${parts[0].lowercase()}:${parts[1]}", parts[0].lowercase(), "img/$cardId")
    }

    @Test
    fun `due grafie della stessa persona sono una voce, con tutte le carte`() {
        val entries = Illustrators.entries(
            listOf("Zu-ka" to listOf("SV1_1", "SV1_2"), "Zu-Ka" to listOf("SV2_5")),
            ::ref
        )
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(listOf("ita:sv1:1", "ita:sv1:2", "ita:sv2:5"), entry.cardApiIds)
        assertEquals(listOf("Zu-ka", "Zu-Ka"), entry.rawNames)
        assertEquals(2, entry.expansionCount)
    }

    @Test
    fun `un credito a quattro mani conta per tutti e due`() {
        val entries = Illustrators.entries(listOf("Mitsuhiro Arita + Kagemaru Himeno" to listOf("SV1_1")), ::ref)
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.cardApiIds == listOf("ita:sv1:1") })
    }

    @Test
    fun `le carte che non si riconoscono non contano, le miniature sono le prime tre`() {
        val entry = Illustrators.entries(
            listOf("Saki Hayashiro" to listOf("SV1_1", "rotta", "SV1_2", "SV1_3", "SV1_4")),
            ::ref
        ).single()
        assertEquals(4, entry.cardApiIds.size)
        assertEquals(listOf("img/SV1_1", "img/SV1_2", "img/SV1_3"), entry.previewUrls)
        assertEquals(1, entry.expansionCount)
    }

    @Test
    fun `nomi vuoti non diventano voci`() {
        assertTrue(Illustrators.entries(listOf("  " to listOf("SV1_1")), ::ref).isEmpty())
    }
}
