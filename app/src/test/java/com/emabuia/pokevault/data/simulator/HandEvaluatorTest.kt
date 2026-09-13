package com.emabuia.pokevault.data.simulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Il verdetto sulla singola mano e' cio' che la modalita' Prova mostra in
 * grande: se sbaglia, sbaglia visibilmente.
 */
class HandEvaluatorTest {

    private fun basic(name: String = "Pikachu") =
        SimulatorCard(name = name, isBasic = true, isEnergy = false, isSupporter = false, isOutCard = false)

    private fun energy(name: String = "Lightning Energy") =
        SimulatorCard(name = name, isBasic = false, isEnergy = true, isSupporter = false, isOutCard = false)

    private fun supporter(name: String = "Iono") =
        SimulatorCard(name = name, isBasic = false, isEnergy = false, isSupporter = true, isOutCard = true)

    private fun item(name: String = "Ultra Ball") =
        SimulatorCard(name = name, isBasic = false, isEnergy = false, isSupporter = false, isOutCard = true)

    private fun filler(name: String = "Rare Candy") =
        SimulatorCard(name = name, isBasic = false, isEnergy = false, isSupporter = false, isOutCard = false)

    @Test
    fun `una mano senza Basic e' un mulligan qualunque cosa ci sia dentro`() {
        val hand = listOf(energy(), energy(), supporter(), item(), item(), filler(), filler())

        val evaluation = HandEvaluator.evaluate(hand)

        assertEquals(HandVerdict.MULLIGAN, evaluation.verdict)
        assertTrue(evaluation.weaknesses.contains(HandTrait.NO_BASIC))
    }

    @Test
    fun `due Basic con energia e out danno il verdetto migliore`() {
        val hand = listOf(basic(), basic("Charmander"), energy(), supporter(), filler(), filler(), filler())

        assertEquals(HandVerdict.GREAT, HandEvaluator.evaluate(hand).verdict)
    }

    @Test
    fun `un Basic e nient'altro resta una mano rischiosa`() {
        val hand = listOf(basic(), filler(), filler(), filler(), filler(), filler(), filler())

        val evaluation = HandEvaluator.evaluate(hand)

        assertEquals(HandVerdict.RISKY, evaluation.verdict)
        assertTrue(evaluation.weaknesses.contains(HandTrait.NO_ENERGY))
        assertTrue(evaluation.weaknesses.contains(HandTrait.NO_OUT))
    }

    @Test
    fun `le key card si riconoscono senza badare a maiuscole e spazi`() {
        val hand = listOf(basic(), energy(), item(), filler(), filler(), filler(), filler())

        val evaluation = HandEvaluator.evaluate(hand, listOf("  ULTRA BALL "))

        assertEquals(listOf("Ultra Ball"), evaluation.keyCardsFound)
        assertTrue(evaluation.strengths.contains(HandTrait.HAS_KEY_CARD))
    }

    @Test
    fun `senza key card in mano la mancanza viene segnalata`() {
        val hand = listOf(basic(), energy(), item(), filler(), filler(), filler(), filler())

        val evaluation = HandEvaluator.evaluate(hand, listOf("Iono"))

        assertTrue(evaluation.keyCardsFound.isEmpty())
        assertTrue(evaluation.weaknesses.contains(HandTrait.MISS_KEY_CARD))
    }

    @Test
    fun `senza key card selezionate non si segnala nessuna mancanza`() {
        val hand = listOf(basic(), energy(), item(), filler(), filler(), filler(), filler())

        val evaluation = HandEvaluator.evaluate(hand)

        assertFalse(evaluation.weaknesses.contains(HandTrait.MISS_KEY_CARD))
    }

    @Test
    fun `i conteggi corrispondono alle carte in mano`() {
        val hand = listOf(basic(), basic("Eevee"), energy(), energy(), supporter(), item(), filler())

        val evaluation = HandEvaluator.evaluate(hand)

        assertEquals(2, evaluation.basics)
        assertEquals(2, evaluation.energies)
        assertEquals(1, evaluation.supporters)
        // Il supporter conta anche come out: e' cosi' che li classifica il deck.
        assertEquals(2, evaluation.outs)
    }
}

/**
 * Il mazzo della modalita' Prova deve comportarsi come un mazzo vero: sette
 * carte scoperte, il resto ancora sopra, e la pescata che esce da li'.
 */
class PracticeDealerTest {

    private fun deckOf(size: Int): List<SimulatorCard> = List(size) { index ->
        SimulatorCard(
            name = "Card $index",
            isBasic = index % 4 == 0,
            isEnergy = index % 5 == 0,
            isSupporter = false,
            isOutCard = false
        )
    }

    @Test
    fun `scopre sette carte e lascia il resto nel mazzo`() {
        val hand = PracticeDealer.deal(deckOf(60), random = Random(1))

        requireNotNull(hand)
        assertEquals(7, hand.opening.size)
        assertEquals(53, hand.library.size)
        assertEquals(0, hand.turn)
        assertTrue(hand.canDraw)
    }

    @Test
    fun `un mazzo troppo corto non produce nessuna mano`() {
        assertEquals(null, PracticeDealer.deal(deckOf(6)))
    }

    @Test
    fun `la pescata esce dal mazzo e non da un nuovo mescolamento`() {
        val hand = requireNotNull(PracticeDealer.deal(deckOf(60), random = Random(7)))
        val expectedNext = hand.library.first()

        val afterDraw = PracticeDealer.drawOne(hand)

        assertEquals(listOf(expectedNext), afterDraw.draws)
        assertEquals(52, afterDraw.library.size)
        assertEquals(1, afterDraw.turn)
        assertEquals(hand.opening, afterDraw.opening)
    }

    @Test
    fun `il verdetto si aggiorna sulle carte pescate`() {
        val opening = List(7) {
            SimulatorCard("Filler $it", isBasic = false, isEnergy = false, isSupporter = false, isOutCard = false)
        }
        val library = listOf(
            SimulatorCard("Pikachu", isBasic = true, isEnergy = false, isSupporter = false, isOutCard = false)
        )
        val hand = PracticeHand(
            opening = opening,
            draws = emptyList(),
            library = library,
            mulligans = 0,
            evaluation = HandEvaluator.evaluate(opening)
        )

        assertEquals(HandVerdict.MULLIGAN, hand.evaluation.verdict)
        assertEquals(1, PracticeDealer.drawOne(hand).evaluation.basics)
    }

    @Test
    fun `il mulligan conta e rimescola`() {
        val pool = deckOf(60)
        val first = requireNotNull(PracticeDealer.deal(pool, random = Random(3)))

        val second = requireNotNull(PracticeDealer.mulligan(first, pool, random = Random(4)))

        assertEquals(1, second.mulligans)
        assertEquals(7, second.opening.size)
        assertEquals(53, second.library.size)
    }

    @Test
    fun `pescare a mazzo vuoto lascia la mano com'era`() {
        val opening = deckOf(7)
        val hand = PracticeHand(
            opening = opening,
            draws = emptyList(),
            library = emptyList(),
            mulligans = 0,
            evaluation = HandEvaluator.evaluate(opening)
        )

        assertFalse(hand.canDraw)
        assertEquals(hand, PracticeDealer.drawOne(hand))
    }

    @Test
    fun `il conteggio della sessione riporta la percentuale di mani tenute`() {
        val tally = PracticeTally(dealt = 10, kept = 7, mulliganed = 3)

        assertEquals(70, tally.keepRate)
        assertEquals(null, PracticeTally().keepRate)
    }
}
