package com.emabuia.pokevault.viewmodel

import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.SetCodeMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quali carte della collezione usa l'import di una decklist.
 *
 * Il caso vero (16/10/2026): "2 Bayleef MEG 9" diventava una Bayleef di
 * Megaevoluzione e una di Ascesa Eroica, che ha lo stesso numero 9. Il set
 * delle carte del catalogo ("ita:me01:9") non si riconosceva mai, e ogni carta
 * finiva fra quelle "con espansione diversa".
 */
class DeckImportMatcherTest {

    private fun card(id: String, name: String, apiId: String, number: String, qty: Int = 1, set: String = "") =
        PokemonCard(id = id, name = name, apiCardId = apiId, cardNumber = number, quantity = qty, set = set)

    // ── Il set delle carte del catalogo ──────────────────────────────────

    @Test
    fun `il set si riconosce dagli id del catalogo, italiano e inglese`() {
        fun matches(imported: String, apiId: String) =
            SetCodeMapper.matchesImportedSet(imported, "Nome italiano del set", apiId.substringBefore("-"), apiId)
        assertTrue(matches("MEG", "ita:me01:9"))
        assertTrue(matches("ASC", "ita:me2pt5:9"))
        assertTrue(matches("TWM", "ita:sv06:128"))
        assertTrue(matches("sv6", "ita:sv06:128")) // i codici del Meta Deck
        assertTrue(matches("DRI", "eng:sv10:207"))
        assertTrue(matches("POR", "ita:me03:121"))
        assertTrue(matches("PRE", "ita:sv8pt5:177"))
        assertTrue(matches("MEP", "ita:mep:1"))
        assertTrue(matches("CRI", "ita:me04:113"))
        assertTrue(matches("PFL", "ita:me02:129"))
        assertTrue(matches("PBL", "ita:me05:107"))
    }

    @Test
    fun `set diversi restano diversi`() {
        fun matches(imported: String, apiId: String) =
            SetCodeMapper.matchesImportedSet(imported, "", apiId.substringBefore("-"), apiId)
        assertFalse(matches("MEG", "ita:me2pt5:9"))
        assertFalse(matches("ASC", "ita:me01:9"))
        assertFalse(matches("MEG", "ita:mep:9"))
        assertFalse(matches("TWM", "ita:sv05:128"))
    }

    // ── La stampa giusta ────────────────────────────────────────────────

    @Test
    fun `due Bayleef 9 di set diversi, la lista vuole MEG e prende solo quella`() {
        val meg = card("doc-meg", "Bayleef", "ita:me01:9", "9")
        val asc = card("doc-asc", "Bayleef", "ita:me2pt5:9", "9")
        val match = DeckImportMatcher.find(listOf(asc, meg), "Bayleef", "MEG", "9")
        assertEquals(listOf("doc-meg"), match.cards.map { it.id })
        assertFalse(match.usedFallbackSet)
    }

    @Test
    fun `la decklist in inglese trova la copia italiana della stessa stampa`() {
        val lylia = card("doc-lylia", "Determinazione di Lylia", "ita:me01:184", "184")
        val match = DeckImportMatcher.find(listOf(lylia), "Lillie's Determination", "MEG", "184")
        assertEquals(listOf("doc-lylia"), match.cards.map { it.id })
        assertFalse(match.usedFallbackSet)
    }

    @Test
    fun `i codici del Meta Deck non generano piu' avvisi di espansione diversa`() {
        val dreepy = card("doc-dreepy", "Dreepy", "ita:sv06:128", "128", qty = 4)
        val match = DeckImportMatcher.find(listOf(dreepy), "Dreepy", "sv6", "128")
        assertEquals(listOf("doc-dreepy"), match.cards.map { it.id })
        assertFalse(match.usedFallbackSet)
    }

    @Test
    fun `anche la copia inglese della stessa stampa vale`() {
        val en = card("doc-en", "Bayleef", "eng:me01:9", "9")
        val match = DeckImportMatcher.find(listOf(en), "Bayleef", "MEG", "9")
        assertEquals(listOf("doc-en"), match.cards.map { it.id })
        assertFalse(match.usedFallbackSet)
    }

    @Test
    fun `stesso set ma numero diverso non e' la stessa stampa`() {
        val altra = card("doc-x", "Determinazione di Lylia", "ita:me01:186", "186")
        val match = DeckImportMatcher.find(listOf(altra), "Lillie's Determination", "MEG", "184")
        assertTrue(match.cards.isEmpty())
    }

    @Test
    fun `numeri con gli zeri davanti sono la stessa carta`() {
        val c = card("doc-1", "Mega Excadrill-ex", "ita:me05:65", "065")
        assertTrue(DeckImportMatcher.sameNumber(c, "65"))
        assertTrue(DeckImportMatcher.sameNumber(card("d", "x", "ita:me05:1", "1"), "001"))
        assertFalse(DeckImportMatcher.sameNumber(c, "66"))
    }

    // ── Ripieghi: come prima ────────────────────────────────────────────

    @Test
    fun `se hai solo un'altra stampa la usa, ma lo dice`() {
        val asc = card("doc-asc", "Bayleef", "ita:me2pt5:9", "9")
        val match = DeckImportMatcher.find(listOf(asc), "Bayleef", "MEG", "9")
        assertEquals(listOf("doc-asc"), match.cards.map { it.id })
        assertTrue(match.usedFallbackSet)
    }

    @Test
    fun `col solo nome le copie dello stesso set vengono prima`() {
        val asc = card("doc-asc", "Bayleef", "ita:me2pt5:20", "20")
        val meg = card("doc-meg", "Bayleef", "ita:me01:30", "30")
        val match = DeckImportMatcher.find(listOf(asc, meg), "Bayleef", "MEG", "9")
        assertEquals(listOf("doc-meg", "doc-asc"), match.cards.map { it.id })
        assertFalse(match.usedFallbackSet)
    }

    @Test
    fun `le carte con id vecchi si trovano come prima`() {
        val legacy = card("doc-old", "Dreepy", "sv6-128", "128", set = "Twilight Masquerade")
        val match = DeckImportMatcher.find(listOf(legacy), "Dreepy", "TWM", "128")
        assertEquals(listOf("doc-old"), match.cards.map { it.id })
        assertFalse(match.usedFallbackSet)
    }

    @Test
    fun `per gli id vecchi set e numero non bastano senza il nome`() {
        val legacy = card("doc-old", "Altra carta", "sv6-128", "128", set = "Twilight Masquerade")
        val match = DeckImportMatcher.find(listOf(legacy), "Dreepy", "TWM", "128")
        assertTrue(match.cards.isEmpty())
    }

    @Test
    fun `le energie base si trovano per tipo, non per stampa`() {
        val erba = card("doc-erba", "Energia Erba", "ita:sve:9", "9")
        val match = DeckImportMatcher.find(listOf(erba), "Basic {G} Energy", "GEN", "75")
        assertEquals(listOf("doc-erba"), match.cards.map { it.id })
        assertFalse(match.usedFallbackSet)
    }

    @Test
    fun `senza set vale nome e numero`() {
        val c = card("doc", "Bayleef", "ita:me01:9", "9")
        val match = DeckImportMatcher.find(listOf(c), "Bayleef", null, "9")
        assertEquals(listOf("doc"), match.cards.map { it.id })
        assertFalse(match.usedFallbackSet)
    }
}
