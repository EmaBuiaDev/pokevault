package com.emabuia.pokevault.viewmodel

import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.DeckAnalysis
import com.emabuia.pokevault.data.model.DeckImportParser
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests per la logica del DeckLab.
 *
 * DeckLabViewModel non è testabile direttamente (dipende da Firebase
 * hardcodato nel costruttore), quindi si testano i modelli puri
 * che costituiscono la business logic: Deck, DeckAnalysis, DeckImportParser.
 */
class DeckLabViewModelTest {

    // ── Deck model ──────────────────────────────────────────────────────────

    @Test
    fun testDeckDefaultValues() {
        val deck = Deck()
        assertTrue(deck.cards.isEmpty())
        assertEquals(0, deck.totalCards)
        assertEquals("", deck.name)
    }

    @Test
    fun testDeckDisplayCoverImageUrls_singleUrl() {
        val deck = Deck(coverImageUrl = "https://example.com/cover.jpg")
        val urls = deck.displayCoverImageUrls()
        assertEquals(1, urls.size)
        assertEquals("https://example.com/cover.jpg", urls[0])
    }

    @Test
    fun testDeckDisplayCoverImageUrls_deduplicatesUrls() {
        val url = "https://example.com/cover.jpg"
        val deck = Deck(
            coverImageUrl = url,
            coverImageUrls = listOf(url, "https://example.com/other.jpg")
        )
        val urls = deck.displayCoverImageUrls()
        assertFalse(urls.contains(url) && urls.count { it == url } > 1)
    }

    @Test
    fun testDeckDisplayCoverImageUrls_ignoresBlank() {
        val deck = Deck(coverImageUrl = "", coverImageUrls = listOf("  ", ""))
        val urls = deck.displayCoverImageUrls()
        assertTrue(urls.isEmpty())
    }

    // ── DeckAnalysis model ──────────────────────────────────────────────────

    @Test
    fun testDeckAnalysisDefaultValues() {
        val analysis = DeckAnalysis()
        assertTrue(analysis.typesCount.isEmpty())
        assertTrue(analysis.commonWeaknesses.isEmpty())
        assertEquals(0.0, analysis.averageHp, 0.0)
        assertTrue(analysis.recommendedEnergy.isEmpty())
    }

    @Test
    fun testDeckAnalysisWithData() {
        val analysis = DeckAnalysis(
            typesCount = mapOf("Fire" to 10, "Water" to 5),
            averageHp = 120.0,
            recommendedEnergy = listOf("Fire Energy", "Water Energy")
        )
        assertEquals(2, analysis.typesCount.size)
        assertEquals(10, analysis.typesCount["Fire"])
        assertEquals(120.0, analysis.averageHp, 0.0)
        assertEquals(2, analysis.recommendedEnergy.size)
    }

    // ── DeckImportParser ────────────────────────────────────────────────────

    @Test
    fun testParseEmptyTextReturnsEmptyResult() {
        val result = DeckImportParser.parse("")
        assertTrue(result.cards.isEmpty())
    }

    @Test
    fun testParseSingleCard() {
        val result = DeckImportParser.parse("4 Charizard ex SVI 125")
        assertEquals(1, result.cards.size)
        val card = result.cards[0]
        assertEquals("Charizard ex", card.name)
        assertEquals(4, card.qty)
        assertNotNull(card.set)
        assertEquals("125", card.number)
    }

    @Test
    fun testParseCardWithoutSetNumber() {
        val result = DeckImportParser.parse("2 Pikachu")
        assertEquals(1, result.cards.size)
        assertEquals("Pikachu", result.cards[0].name)
        assertEquals(2, result.cards[0].qty)
        assertNull(result.cards[0].set)
        assertNull(result.cards[0].number)
    }

    @Test
    fun testParseSectionHeaders() {
        val decklist = """
            Pokémon: 2
            2 Pikachu
            Trainer: 1
            1 Ultra Ball
        """.trimIndent()
        val result = DeckImportParser.parse(decklist)
        assertEquals(2, result.cards.size)
        assertEquals("pokemon", result.cards[0].type)
        assertEquals("trainer", result.cards[1].type)
    }

    @Test
    fun testParseIgnoresCommentLines() {
        val decklist = """
            // This is a comment
            4 Charizard ex
            # Another comment
            2 Pikachu
        """.trimIndent()
        val result = DeckImportParser.parse(decklist)
        assertEquals(2, result.cards.size)
    }

    @Test
    fun testParseInvalidQtyIsIgnored() {
        // qty = 0 non è valido
        val result = DeckImportParser.parse("0 Pikachu")
        assertTrue(result.cards.isEmpty())
    }
}
