package com.emabuia.pokevault.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il confronto dei nomi, uguale per la ricerca italiana e per quella inglese. */
class CardNameMatchTest {

    private fun score(name: String, query: String): Int {
        val q = CardNameMatch.normalize(query)
        return CardNameMatch.score(CardNameMatch.normalize(name), q, q.split(" ").filter { it.isNotBlank() })
    }

    @Test
    fun `accenti, maiuscole e segni non contano`() {
        assertEquals("pokemon ex", CardNameMatch.normalize("Pokémon-EX"))
        assertEquals("mega charizard", CardNameMatch.normalize("MegaCharizard".replace("aC", "a C")))
        assertEquals("", CardNameMatch.normalize("  "))
    }

    @Test
    fun `la scala - identico, comincia, parola, contiene, nessuno`() {
        assertEquals(1000, score("Pikachu", "pikachu"))
        assertEquals(850, score("Pikachu ex", "pikachu"))
        assertEquals(700, score("Detective Pikachu", "pikachu"))
        assertEquals(560, score("Raikachuu", "kachu"))
        assertEquals(0, score("Raichu", "pikachu"))
        assertTrue(score("Charizard ex", "zard ex") > 0)
    }
}
