package com.emabuia.pokevault.ui.deck

import com.emabuia.pokevault.data.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckListFilterTest {

    private val collezione1 = Deck(id = "c1", name = "Dragapult")
    private val prova1 = Deck(id = "p1", name = "Gardevoir", deckOnly = true)
    private val collezione2 = Deck(id = "c2", name = "Charizard")
    private val prova2 = Deck(id = "p2", name = "Raging Bolt", deckOnly = true)
    private val tutti = listOf(collezione1, prova1, collezione2, prova2)

    private fun List<DeckListRow>.deckIds() = filterIsInstance<DeckListRow.Item>().map { it.deck.id }

    @Test
    fun `su Tutti prima la collezione poi le prove, ognuna con la sua intestazione`() {
        val rows = buildDeckListRows(tutti, DeckListFilter.ALL)
        assertEquals(listOf("c1", "c2", "p1", "p2"), rows.deckIds())
        assertTrue(rows[0] is DeckListRow.Header)
        assertEquals(2, (rows[0] as DeckListRow.Header).count)
        assertTrue(rows[3] is DeckListRow.Header)
        assertEquals(6, rows.size)
    }

    @Test
    fun `senza deck di prova niente intestazioni`() {
        val rows = buildDeckListRows(listOf(collezione1, collezione2), DeckListFilter.ALL)
        assertEquals(listOf("c1", "c2"), rows.deckIds())
        assertEquals(2, rows.size)
    }

    @Test
    fun `i filtri tengono solo il loro tipo e l'ordine originale`() {
        assertEquals(listOf("c1", "c2"), buildDeckListRows(tutti, DeckListFilter.COLLECTION).deckIds())
        assertEquals(listOf("p1", "p2"), buildDeckListRows(tutti, DeckListFilter.TEST).deckIds())
        assertTrue(buildDeckListRows(listOf(collezione1), DeckListFilter.TEST).isEmpty())
    }

    @Test
    fun `le chiavi delle righe non si ripetono`() {
        // LazyColumn va in crash con due chiavi uguali.
        val keys = buildDeckListRows(tutti, DeckListFilter.ALL).map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
