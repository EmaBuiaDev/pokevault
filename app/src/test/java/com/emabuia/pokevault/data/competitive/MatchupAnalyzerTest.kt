package com.emabuia.pokevault.data.competitive

import com.emabuia.pokevault.data.model.MatchLog
import com.emabuia.pokevault.data.model.Tournament
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le statistiche del match log nascono da dati che l'utente ha scritto a mano,
 * quindi sporchi: lo stesso archetipo con tre grafie, partite senza avversario,
 * tornei senza mazzo. Il conto deve reggere tutto questo senza mentire.
 */
class MatchupAnalyzerTest {

    private var nextId = 0

    private fun match(
        result: String,
        opponentDeck: String = "",
        tournamentId: String = "t1"
    ) = MatchLog(
        id = "m${nextId++}",
        tournamentId = tournamentId,
        round = 1,
        result = result,
        opponentDeck = opponentDeck
    )

    private fun tournament(id: String, deckName: String) =
        Tournament(id = id, deckName = deckName, type = "Cup")

    @Test
    fun `senza partite il riepilogo e' vuoto`() {
        val summary = MatchupAnalyzer.analyze(emptyList(), emptyList())

        assertTrue(summary.isEmpty)
        assertEquals(0, summary.played)
        assertNull(summary.winRate)
        assertTrue(summary.matchups.isEmpty())
        assertNull(summary.bestMatchup)
    }

    @Test
    fun `il record somma i risultati`() {
        val summary = MatchupAnalyzer.analyze(
            listOf(match("W"), match("W"), match("L"), match("T")),
            emptyList()
        )

        assertEquals(2, summary.wins)
        assertEquals(1, summary.losses)
        assertEquals(1, summary.ties)
        assertEquals(4, summary.played)
    }

    @Test
    fun `il win rate ignora i pareggi`() {
        // 1-1-8: sulle due partite decise e' 50%, non 10%.
        val matches = listOf(match("W"), match("L")) + List(8) { match("T") }

        val summary = MatchupAnalyzer.analyze(matches, emptyList())

        assertEquals(50, summary.winRate)
        assertEquals(2, summary.decided)
    }

    @Test
    fun `un risultato non valido viene ignorato`() {
        val summary = MatchupAnalyzer.analyze(
            listOf(match("W"), match(""), match("X")),
            emptyList()
        )

        assertEquals(1, summary.played)
    }

    @Test
    fun `le grafie diverse dello stesso mazzo finiscono nello stesso matchup`() {
        val summary = MatchupAnalyzer.analyze(
            listOf(
                match("W", "Charizard ex"),
                match("L", "charizard ex"),
                match("W", "  CHARIZARD EX  ")
            ),
            emptyList()
        )

        assertEquals(1, summary.matchups.size)
        val matchup = summary.matchups.first()
        assertEquals(2, matchup.wins)
        assertEquals(1, matchup.losses)
        assertEquals(3, matchup.played)
    }

    @Test
    fun `l'etichetta del matchup e' la grafia piu' usata`() {
        val summary = MatchupAnalyzer.analyze(
            listOf(
                match("W", "Charizard ex"),
                match("W", "Charizard ex"),
                match("L", "charizard EX")
            ),
            emptyList()
        )

        assertEquals("Charizard ex", summary.matchups.first().opponentDeck)
    }

    @Test
    fun `mazzi diversi restano matchup diversi`() {
        val summary = MatchupAnalyzer.analyze(
            listOf(match("W", "Charizard ex"), match("L", "Gardevoir ex")),
            emptyList()
        )

        assertEquals(2, summary.matchups.size)
    }

    @Test
    fun `i matchup sono ordinati per partite giocate`() {
        val matches = List(5) { match("W", "Gardevoir") } + List(2) { match("L", "Charizard") }

        val summary = MatchupAnalyzer.analyze(matches, emptyList())

        assertEquals("Gardevoir", summary.matchups[0].opponentDeck)
        assertEquals("Charizard", summary.matchups[1].opponentDeck)
    }

    @Test
    fun `le partite senza mazzo avversario si contano a parte`() {
        val summary = MatchupAnalyzer.analyze(
            listOf(match("W", "Charizard"), match("L", ""), match("W", "   ")),
            emptyList()
        )

        assertEquals(1, summary.matchups.size)
        assertEquals(2, summary.matchesWithoutOpponentDeck)
        // Restano comunque nel record complessivo: sono partite giocate.
        assertEquals(3, summary.played)
    }

    @Test
    fun `sotto la soglia non si elegge un matchup migliore`() {
        // Tre partite vinte su tre: sembra un 100%, ma e' rumore.
        val summary = MatchupAnalyzer.analyze(
            List(3) { match("W", "Charizard") },
            emptyList()
        )

        assertEquals(1, summary.matchups.size)
        assertNull(summary.bestMatchup)
        assertNull(summary.worstMatchup)
    }

    @Test
    fun `sopra la soglia il migliore e il peggiore emergono`() {
        val matches =
            List(4) { match("W", "Gardevoir") } +
                List(4) { match("L", "Charizard") }

        val summary = MatchupAnalyzer.analyze(matches, emptyList())

        assertEquals("Gardevoir", summary.bestMatchup?.opponentDeck)
        assertEquals(100, summary.bestMatchup?.winRate)
        assertEquals("Charizard", summary.worstMatchup?.opponentDeck)
        assertEquals(0, summary.worstMatchup?.winRate)
    }

    @Test
    fun `a parita' di percentuale vince il matchup piu' giocato`() {
        val matches =
            List(4) { match("W", "Pochi") } +
                List(10) { match("W", "Tanti") }

        val summary = MatchupAnalyzer.analyze(matches, emptyList())

        assertEquals("Tanti", summary.bestMatchup?.opponentDeck)
    }

    @Test
    fun `un matchup di soli pareggi non ha un win rate`() {
        val summary = MatchupAnalyzer.analyze(
            List(5) { match("T", "Mirror") },
            emptyList()
        )

        assertNull(summary.matchups.first().winRate)
        // E non puo' diventare ne' il migliore ne' il peggiore.
        assertNull(summary.bestMatchup)
    }

    @Test
    fun `il bilancio per mazzo unisce le partite ai tornei`() {
        val matches = listOf(
            match("W", tournamentId = "t1"),
            match("W", tournamentId = "t1"),
            match("L", tournamentId = "t2")
        )
        val tournaments = listOf(
            tournament("t1", "Charizard ex"),
            tournament("t2", "Charizard ex")
        )

        val summary = MatchupAnalyzer.analyze(matches, tournaments)

        assertEquals(1, summary.deckStats.size)
        val deck = summary.deckStats.first()
        assertEquals("Charizard ex", deck.deckName)
        assertEquals(2, deck.wins)
        assertEquals(1, deck.losses)
        assertEquals(2, deck.tournaments)
    }

    @Test
    fun `le partite di un torneo senza mazzo restano fuori dal bilancio per mazzo`() {
        val matches = listOf(match("W", tournamentId = "t1"), match("W", tournamentId = "t2"))
        val tournaments = listOf(tournament("t1", "Charizard ex"), tournament("t2", ""))

        val summary = MatchupAnalyzer.analyze(matches, tournaments)

        assertEquals(1, summary.deckStats.size)
        assertEquals(1, summary.deckStats.first().played)
        // Ma nel record complessivo ci sono entrambe.
        assertEquals(2, summary.played)
    }

    @Test
    fun `la striscia di forma tiene i piu' recenti nell'ordine ricevuto`() {
        val matches = listOf(match("W"), match("L"), match("T"))

        val summary = MatchupAnalyzer.analyze(matches, emptyList())

        assertEquals(listOf("W", "L", "T"), summary.recentForm)
    }

    @Test
    fun `la striscia di forma si ferma a dieci`() {
        val summary = MatchupAnalyzer.analyze(List(30) { match("W") }, emptyList())

        assertEquals(MatchupAnalyzer.RECENT_FORM_SIZE, summary.recentForm.size)
    }

    @Test
    fun `i mazzi avversari noti escono dal piu' frequente`() {
        val matches =
            List(3) { match("W", "Gardevoir") } +
                List(5) { match("L", "Charizard") } +
                listOf(match("W", ""))

        val known = MatchupAnalyzer.knownOpponentDecks(matches)

        assertEquals(listOf("Charizard", "Gardevoir"), known)
    }

    @Test
    fun `la normalizzazione ignora accenti e punteggiatura`() {
        assertEquals(
            MatchupAnalyzer.normalizeDeckName("Palafin - Chien-Pao"),
            MatchupAnalyzer.normalizeDeckName("palafin chien pao")
        )
        assertEquals("", MatchupAnalyzer.normalizeDeckName("   "))
    }

    @Test
    fun `la normalizzazione non accorpa archetipi diversi`() {
        // "Zard" resta distinto da "Charizard": accorparli falserebbe il
        // bilancio senza che l'utente possa accorgersene.
        assertTrue(
            MatchupAnalyzer.normalizeDeckName("Zard") !=
                MatchupAnalyzer.normalizeDeckName("Charizard")
        )
    }
}
