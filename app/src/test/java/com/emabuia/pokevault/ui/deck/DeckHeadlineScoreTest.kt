package com.emabuia.pokevault.ui.deck

import com.emabuia.pokevault.data.model.PokemonCard
import org.junit.Assert.*
import org.junit.Test

/**
 * Quali Pokemon rappresentano un mazzo nell'elenco.
 *
 * Il primo tentativo ordinava per numero di copie, e su un mazzo Charizard ex
 * mostrava Charmander: di una linea evolutiva si giocano quattro basi e due o
 * tre carte finali, quindi contare le copie premia sempre la carta sbagliata.
 * Questi test tengono fermo il criterio, perche' l'errore non si vede leggendo
 * il codice -- si vede solo aprendo l'app e riconoscendo il Pokemon sbagliato.
 */
class DeckHeadlineScoreTest {

    private fun pokemon(name: String, stage: String? = null) = PokemonCard(
        id = name,
        name = name,
        supertype = "Pokémon",
        subtypes = stage?.let { listOf(it) } ?: emptyList()
    )

    @Test
    fun testLaCartaConRuleBoxBatteLaBaseAnchePiuNumerosa() {
        val charmander = headlineScore(pokemon("Charmander", "Basic"), copies = 4)
        val charizardEx = headlineScore(pokemon("Charizard ex", "Stage 2"), copies = 2)

        assertTrue(
            "Charizard ex ($charizardEx) deve rappresentare il mazzo piu' di Charmander ($charmander)",
            charizardEx > charmander
        )
    }

    @Test
    fun testTutteLeRuleBoxContano() {
        val base = headlineScore(pokemon("Bidoof", "Basic"), copies = 4)
        listOf("Mew V", "Mew VMAX", "Mew VSTAR", "Pikachu-GX", "Koraidon ex").forEach { nome ->
            assertTrue(
                "$nome deve valere piu' di una base da quattro copie",
                headlineScore(pokemon(nome), copies = 1) > base
            )
        }
    }

    @Test
    fun testAParitaViceLoStadioPiuAlto() {
        val base = headlineScore(pokemon("Charmander", "Basic"), copies = 2)
        val fase1 = headlineScore(pokemon("Charmeleon", "Stage 1"), copies = 2)
        val fase2 = headlineScore(pokemon("Charizard", "Stage 2"), copies = 2)

        assertTrue(fase2 > fase1)
        assertTrue(fase1 > base)
    }

    /** Lo stadio arriva dal catalogo e puo' essere in italiano. */
    @Test
    fun testLoStadioInItalianoValeQuantoQuelloInInglese() {
        assertEquals(
            headlineScore(pokemon("Charizard", "Stage 2"), copies = 2),
            headlineScore(pokemon("Charizard", "Fase 2"), copies = 2)
        )
        assertEquals(
            headlineScore(pokemon("Charmeleon", "Stage 1"), copies = 2),
            headlineScore(pokemon("Charmeleon", "Fase 1"), copies = 2)
        )
    }

    /** A parita' di tutto il resto, le copie sono ancora il criterio. */
    @Test
    fun testAParitaViceChiHaPiuCopie() {
        val quattro = headlineScore(pokemon("Lumineon V"), copies = 4)
        val una = headlineScore(pokemon("Radiant Greninja"), copies = 1)
        assertTrue(quattro > una)
    }

    /**
     * "ex" e "v" si riconoscono come parole intere: un Pokemon che se le
     * ritrova dentro al nome non e' una carta con la rule box.
     */
    @Test
    fun testNessunFalsoPositivoDentroAiNomi() {
        val exeggutor = headlineScore(pokemon("Exeggutor", "Stage 1"), copies = 2)
        val atteso = headlineScore(pokemon("Charmeleon", "Stage 1"), copies = 2)
        assertEquals(atteso, exeggutor)
    }
}
