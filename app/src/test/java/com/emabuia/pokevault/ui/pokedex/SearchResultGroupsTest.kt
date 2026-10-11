package com.emabuia.pokevault.ui.pokedex

import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgCardSet
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Il raggruppamento dei risultati di "Cerca carte". Prima stava dentro il
 * disegno della griglia; da quando la scheda della carta scorre nello stesso
 * ordine della griglia, un errore qui sposterebbe tutte e due.
 */
class SearchResultGroupsTest {

    private fun card(id: String, setId: String, number: String) =
        TcgCard(id = id, name = id, set = TcgCardSet(id = setId, name = setId), number = number)

    private val releaseDates = mapOf(
        "vecchio" to "2020-01-10",
        "nuovo" to "2025-03-28"
    )

    @Test
    fun `per nome le espansioni vanno dalla piu' recente`() {
        val cards = listOf(card("a", "vecchio", "5"), card("b", "nuovo", "7"))

        val groups = groupSearchResults(cards, preserveOrder = false, setReleaseDateById = releaseDates)

        assertEquals(listOf("nuovo", "vecchio"), groups.map { it.setId })
    }

    @Test
    fun `per numero resta l'ordine della ricerca`() {
        val cards = listOf(card("a", "vecchio", "5"), card("b", "nuovo", "5"))

        val groups = groupSearchResults(cards, preserveOrder = true, setReleaseDateById = releaseDates)

        assertEquals(listOf("vecchio", "nuovo"), groups.map { it.setId })
    }

    @Test
    fun `dentro un'espansione le carte sono in ordine di numero`() {
        val cards = listOf(
            card("c", "nuovo", "101/091"),
            card("a", "nuovo", "007/091"),
            card("b", "nuovo", "12")
        )

        val groups = groupSearchResults(cards, preserveOrder = false, setReleaseDateById = releaseDates)

        assertEquals(listOf("a", "b", "c"), groups.single().cards.map { it.id })
    }

    @Test
    fun `le carte senza espansione finiscono in un gruppo a parte, in fondo`() {
        val cards = listOf(
            TcgCard(id = "orfana", name = "orfana", number = "1"),
            card("a", "vecchio", "1")
        )

        val groups = groupSearchResults(cards, preserveOrder = false, setReleaseDateById = releaseDates)

        assertEquals(listOf("vecchio", "unknown"), groups.map { it.setId })
    }

    @Test
    fun `nessuna carta va persa`() {
        val cards = (1..30).map { card("c$it", if (it % 2 == 0) "nuovo" else "vecchio", "$it") }

        val groups = groupSearchResults(cards, preserveOrder = false, setReleaseDateById = releaseDates)

        assertEquals(cards.map { it.id }.toSet(), groups.flatMap { it.cards }.map { it.id }.toSet())
        assertEquals(cards.size, groups.sumOf { it.cards.size })
    }
}
