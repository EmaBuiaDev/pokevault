package com.emabuia.pokevault.ocr

import com.emabuia.pokevault.data.catalog.CatalogLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La lingua della carta inquadrata, dal testo letto (Fase 4 del piano EN). */
class CardLanguageDetectorTest {

    private val english = """
        BASIC Pikachu HP 60
        Thunder Shock 20
        Flip a coin. If heads, your opponent's Active Pokémon is now Paralyzed.
        weakness resistance retreat
        Illus. Atsuko Nishida 025/191
    """.trimIndent()

    private val italian = """
        BASE Pikachu PS 60
        Tuonoshock 20
        Lancia una moneta. Se esce testa, il Pokémon attivo del tuo avversario viene paralizzato.
        debolezza resistenza ritirata
        Illus. Atsuko Nishida 025/191
    """.trimIndent()

    @Test
    fun `una carta inglese e una italiana`() {
        assertEquals(CatalogLang.EN, CardLanguageDetector.detect(english))
        assertEquals(CatalogLang.IT, CardLanguageDetector.detect(italian))
    }

    @Test
    fun `accenti e maiuscole non contano`() {
        assertEquals(CatalogLang.IT, CardLanguageDetector.detect("ABILITÀ  DEBOLEZZA  RITIRATA"))
    }

    @Test
    fun `l'apostrofo separa le parole - dell'avversario conta come italiano`() {
        val (english, italian) = CardLanguageDetector.counts("dell'avversario all'attacco")
        assertEquals(0, english)
        assertEquals(4, italian)
        // "opponent's" e' "opponent".
        assertEquals(1, CardLanguageDetector.counts("opponent's").first)
    }

    @Test
    fun `Illus non conta per nessuna lingua - e' stampato su tutte e due`() {
        assertEquals(0 to 0, CardLanguageDetector.counts("Illus. Atsuko Nishida"))
        assertEquals(CatalogLang.IT, CardLanguageDetector.detect("Debolezza Ritirata Illus. Atsuko Nishida"))
    }

    @Test
    fun `con troppo poco testo non si decide`() {
        assertNull(CardLanguageDetector.detect("Pikachu 025/191"))
        assertNull(CardLanguageDetector.detect(""))
        // Una parola sola per lingua: pari, non si decide.
        assertNull(CardLanguageDetector.detect("Weakness Debolezza"))
    }

    @Test
    fun `i voti dei fotogrammi - serve una lingua ripetuta e in vantaggio`() {
        val votes = LanguageVotes(minVotes = 2, windowMs = 3_500)
        votes.record(CatalogLang.EN, 0)
        assertNull(votes.winner(0))
        votes.record(null, 100)
        votes.record(CatalogLang.EN, 200)
        assertEquals(CatalogLang.EN, votes.winner(200))
        votes.record(CatalogLang.IT, 300)
        votes.record(CatalogLang.IT, 400)
        // Pari: un fotogramma letto male non fa cambiare catalogo.
        assertNull(votes.winner(400))
        votes.reset()
        assertNull(votes.winner(400))
    }

    @Test
    fun `i voti vecchi scadono - la carta di prima non pesa su quella dopo`() {
        val votes = LanguageVotes(minVotes = 2, windowMs = 3_500)
        repeat(10) { votes.record(CatalogLang.EN, it * 100L) }
        // Quattro secondi dopo, due fotogrammi italiani bastano.
        votes.record(CatalogLang.IT, 5_000)
        votes.record(CatalogLang.IT, 5_100)
        assertEquals(CatalogLang.IT, votes.winner(5_100))
    }
}
