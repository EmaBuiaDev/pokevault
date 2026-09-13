package com.emabuia.pokevault.data.simulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il punteggio di consistenza e' il numero grande in cima all'analisi: e' il
 * primo che l'utente legge, ed e' il solo che non puo' ricavare da solo dalle
 * percentuali sotto.
 */
class HandSimulationEngineTest {

    private fun card(
        name: String,
        basic: Boolean = false,
        energy: Boolean = false,
        supporter: Boolean = false,
        out: Boolean = false
    ) = SimulatorCard(
        name = name,
        isBasic = basic,
        isEnergy = energy,
        isSupporter = supporter,
        isOutCard = out
    )

    private fun balancedDeck(): List<SimulatorCard> = buildList {
        repeat(14) { add(card("Basic $it", basic = true)) }
        repeat(12) { add(card("Energy $it", energy = true)) }
        repeat(10) { add(card("Iono $it", supporter = true, out = true)) }
        repeat(10) { add(card("Ultra Ball $it", out = true)) }
        repeat(14) { add(card("Filler $it")) }
    }

    @Test
    fun `metriche tutte al massimo danno cento`() {
        val score = HandSimulationEngine.consistencyScore(
            starterRate = 100.0,
            setupByT2Rate = 100.0,
            outByT1Rate = 100.0,
            energyByT1Rate = 100.0,
            keyCardByT2Rate = null
        )

        assertEquals(100, score)
    }

    @Test
    fun `senza key card i pesi restanti si rinormalizzano`() {
        // Tutte le metriche a 60: il punteggio deve essere 60 e non 54, che
        // sarebbe il risultato di lasciare fuori il peso delle key card.
        val score = HandSimulationEngine.consistencyScore(
            starterRate = 60.0,
            setupByT2Rate = 60.0,
            outByT1Rate = 60.0,
            energyByT1Rate = 60.0,
            keyCardByT2Rate = null
        )

        assertEquals(60, score)
    }

    @Test
    fun `lo starter rate pesa piu' delle altre metriche`() {
        val starterBassa = HandSimulationEngine.consistencyScore(
            starterRate = 0.0,
            setupByT2Rate = 100.0,
            outByT1Rate = 100.0,
            energyByT1Rate = 100.0,
            keyCardByT2Rate = null
        )
        val energiaBassa = HandSimulationEngine.consistencyScore(
            starterRate = 100.0,
            setupByT2Rate = 100.0,
            outByT1Rate = 100.0,
            energyByT1Rate = 0.0,
            keyCardByT2Rate = null
        )

        assertTrue(starterBassa < energiaBassa)
    }

    @Test
    fun `un mazzo senza Basic non parte mai`() {
        val deck = List(60) { card("Filler $it") }

        val summary = HandSimulationEngine.run(deck, runs = 100, keyCardNames = emptyList())

        assertEquals(0, summary.consistencyScore)
        assertEquals(100.0, summary.mulliganRate, 0.001)
        assertEquals(0.0, summary.starterRate, 0.001)
        assertEquals(listOf("NO_BASIC_IN_DECK"), summary.problemHands.single().tags)
    }

    @Test
    fun `un mazzo di soli Basic parte sempre`() {
        val deck = List(60) { card("Basic $it", basic = true) }

        val summary = HandSimulationEngine.run(deck, runs = 200, keyCardNames = emptyList())

        assertEquals(100.0, summary.starterRate, 0.001)
        assertEquals(0.0, summary.mulliganRate, 0.001)
        assertEquals(0.0, summary.averageMulligans, 0.001)
        assertEquals(7.0, summary.averageBasics, 0.001)
    }

    @Test
    fun `un mazzo troppo corto non produce risultati`() {
        val summary = HandSimulationEngine.run(List(6) { card("X$it", basic = true) }, 100, emptyList())

        assertEquals(0, summary.totalRuns)
        assertTrue(summary.problemHands.isEmpty())
    }

    @Test
    fun `zero run non produce risultati`() {
        val summary = HandSimulationEngine.run(balancedDeck(), runs = 0, keyCardNames = emptyList())

        assertEquals(0, summary.totalRuns)
    }

    @Test
    fun `il tasso key card esiste solo quando le key card sono state scelte`() {
        val deck = balancedDeck()

        assertNull(HandSimulationEngine.run(deck, 200, emptyList()).keyCardByT2Rate)
        assertNotNull(HandSimulationEngine.run(deck, 200, listOf("Iono 0")).keyCardByT2Rate)
    }

    @Test
    fun `le mani problematiche sono al massimo sei e senza doppioni`() {
        val summary = HandSimulationEngine.run(balancedDeck(), runs = 2000, keyCardNames = emptyList())

        assertTrue(summary.problemHands.size <= 6)
        val signatures = summary.problemHands.map { hand -> hand.cards.map { it.name }.sorted() }
        assertEquals(signatures.size, signatures.distinct().size)
    }

    @Test
    fun `le mani problematiche piu' gravi vengono prima`() {
        val summary = HandSimulationEngine.run(balancedDeck(), runs = 2000, keyCardNames = emptyList())

        val tagCounts = summary.problemHands.map { it.tags.size }
        assertEquals(tagCounts.sortedDescending(), tagCounts)
    }

    @Test
    fun `le percentuali di un mazzo equilibrato restano fra zero e cento`() {
        val summary = HandSimulationEngine.run(balancedDeck(), runs = 1000, keyCardNames = emptyList())

        listOf(
            summary.starterRate,
            summary.mulliganRate,
            summary.energyByT1Rate,
            summary.outByT1Rate,
            summary.setupByT2Rate
        ).forEach { rate ->
            assertTrue(rate in 0.0..100.0)
        }
        assertTrue(summary.consistencyScore in 0..100)
        assertEquals(100.0, summary.starterRate + summary.mulliganRate, 0.001)
    }
}
