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
    fun `con troppo poco testo non si decide`() {
        assertNull(CardLanguageDetector.detect("Pikachu 025/191"))
        assertNull(CardLanguageDetector.detect(""))
        // Una parola sola per lingua: pari, non si decide.
        assertNull(CardLanguageDetector.detect("Weakness Debolezza"))
    }

    @Test
    fun `i voti dei fotogrammi - serve una lingua ripetuta e in vantaggio`() {
        val votes = LanguageVotes(minVotes = 2)
        votes.record(CatalogLang.EN)
        assertNull(votes.winner())
        votes.record(null)
        votes.record(CatalogLang.EN)
        assertEquals(CatalogLang.EN, votes.winner())
        votes.record(CatalogLang.IT)
        votes.record(CatalogLang.IT)
        // Pari: un fotogramma letto male non fa cambiare catalogo.
        assertNull(votes.winner())
        votes.reset()
        assertNull(votes.winner())
    }
}
