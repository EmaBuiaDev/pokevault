package com.emabuia.pokevault.data.simulator

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le regole del primo turno del GCC Pokemon, come le segue il simulatore:
 * 7 carte, 6 Premi dopo la mano, una pescata al primo turno per tutti e due,
 * e niente Aiuti al primo turno per chi inizia (tranne Rubra).
 */
class FirstTurnRulesTest {

    private fun card(
        name: String,
        basic: Boolean = false,
        energy: Boolean = false,
        supporter: Boolean = false,
        out: Boolean = false,
        firstTurnSupporter: Boolean = false
    ) = SimulatorCard(
        name = name,
        isBasic = basic,
        isEnergy = energy,
        isSupporter = supporter,
        isOutCard = out,
        playableFirstTurnGoingFirst = firstTurnSupporter
    )

    private val pikachu = card("Pikachu", basic = true)
    private val energy = card("Energia", energy = true)
    private val kissara = card("Kissara", supporter = true, out = true)
    private val minorBall = card("Minor Ball", out = true)
    private val rubra = card("Rubra", supporter = true, out = true, firstTurnSupporter = true)
    private fun filler(n: Int) = List(n) { card("Filler $it") }

    // --- Chi puo' usare cosa al primo turno -------------------------------

    @Test
    fun `chi inizia non usa gli Aiuti al primo turno, chi va secondo si`() {
        assertFalse(FirstTurnRules.isUsableOutOnFirstTurn(kissara, TurnOrder.FIRST))
        assertTrue(FirstTurnRules.isUsableOutOnFirstTurn(kissara, TurnOrder.SECOND))
    }

    @Test
    fun `gli Strumenti valgono per tutti e due`() {
        assertTrue(FirstTurnRules.isUsableOutOnFirstTurn(minorBall, TurnOrder.FIRST))
        assertTrue(FirstTurnRules.isUsableOutOnFirstTurn(minorBall, TurnOrder.SECOND))
    }

    @Test
    fun `Rubra si gioca al primo turno anche iniziando per primi`() {
        assertTrue(FirstTurnRules.isUsableOutOnFirstTurn(rubra, TurnOrder.FIRST))
    }

    @Test
    fun `una carta che non pesca non conta in nessun caso`() {
        assertFalse(FirstTurnRules.isUsableOutOnFirstTurn(energy, TurnOrder.SECOND))
    }

    // --- La mano nella simulazione ----------------------------------------

    private fun deal(opening: List<SimulatorCard>, prizes: List<SimulatorCard> = filler(6), library: List<SimulatorCard> = filler(10)) =
        HandSimulationEngine.Deal(opening = opening, prizes = prizes, library = library, mulligans = 0)

    @Test
    fun `una mano che parte solo con un Aiuto e' buona per secondi e lenta per primi`() {
        val hand = listOf(pikachu, energy, kissara) + filler(4)
        val result = HandSimulationEngine.evaluateDeal(deal(hand), emptySet())

        assertFalse(result.outByT1.getValue(TurnOrder.FIRST))
        assertTrue(result.outByT1.getValue(TurnOrder.SECOND))
        assertTrue("SUPPORTER_ONLY_T1" in result.tags)
        assertFalse("NO_OUT_T1" in result.tags)
    }

    @Test
    fun `iniziando per primi l'Aiuto si gioca al secondo turno, e il setup entro T2 c'e'`() {
        val hand = listOf(pikachu, energy, kissara) + filler(4)
        val result = HandSimulationEngine.evaluateDeal(deal(hand), emptySet())

        assertTrue(result.setupByT2.getValue(TurnOrder.FIRST))
        assertTrue(result.setupByT2.getValue(TurnOrder.SECOND))
    }

    @Test
    fun `con uno Strumento che cerca la mano parte in tutti e due i casi`() {
        val hand = listOf(pikachu, energy, minorBall) + filler(4)
        val result = HandSimulationEngine.evaluateDeal(deal(hand), emptySet())

        assertTrue(result.outByT1.getValue(TurnOrder.FIRST))
        assertTrue(result.outByT1.getValue(TurnOrder.SECOND))
        assertFalse("SUPPORTER_ONLY_T1" in result.tags)
    }

    @Test
    fun `la pescata del primo turno viene dopo i Premi`() {
        // Minor Ball fra i Premi non si pesca; in cima al mazzo dopo i Premi si.
        val hand = listOf(pikachu, energy) + filler(5)
        val prizedOnly = HandSimulationEngine.evaluateDeal(
            deal(hand, prizes = listOf(minorBall) + filler(5), library = filler(10)),
            emptySet()
        )
        assertFalse(prizedOnly.outByT1.getValue(TurnOrder.SECOND))

        val drawn = HandSimulationEngine.evaluateDeal(
            deal(hand, prizes = filler(6), library = listOf(minorBall) + filler(9)),
            emptySet()
        )
        assertTrue(drawn.outByT1.getValue(TurnOrder.FIRST))
    }

    @Test
    fun `una carta chiave fra i Premi viene segnalata`() {
        val hand = listOf(pikachu, energy) + filler(5)
        val result = HandSimulationEngine.evaluateDeal(
            deal(hand, prizes = listOf(card("Caramella Rara")) + filler(5)),
            setOf("caramella rara")
        )
        assertTrue(result.keyCardPrized)
        assertTrue("KEY_CARD_PRIZED" in result.tags)
    }

    @Test
    fun `la mano tenuta lascia 6 Premi e il resto del mazzo`() {
        val pool = List(20) { card("Basic $it", basic = true) } + filler(40)
        val dealt = HandSimulationEngine.dealLegalOpening(pool, Random(7))

        assertEquals(7, dealt.opening.size)
        assertEquals(6, dealt.prizes.size)
        assertEquals(47, dealt.library.size)
        assertEquals(pool.size, (dealt.opening + dealt.prizes + dealt.library).size)
    }

    @Test
    fun `il voto del mazzo e' la media dei due casi, e chi inizia non va mai meglio`() {
        val deck = buildList {
            repeat(14) { add(card("Basic $it", basic = true)) }
            repeat(12) { add(card("Energia $it", energy = true)) }
            repeat(12) { add(card("Aiuto $it", supporter = true, out = true)) }
            repeat(4) { add(card("Ultra Ball $it", out = true)) }
            addAll(filler(18))
        }
        val summary = HandSimulationEngine.run(deck, runs = 2000, keyCardNames = emptyList())

        assertTrue(summary.goingFirst.outByT1Rate <= summary.goingSecond.outByT1Rate)
        assertTrue(summary.goingFirst.setupByT2Rate <= summary.goingSecond.setupByT2Rate)
        val mean = (summary.goingFirst.score + summary.goingSecond.score) / 2.0
        assertEquals(mean, summary.consistencyScore.toDouble(), 0.51)
    }

    @Test
    fun `una carta chiave a una copia finisce nei Premi circa una volta su nove`() {
        // 6 Premi su 53 carte fuori dalla mano: ~11%. Larghezza generosa,
        // basta a dire che i Premi ci sono e non sono 0 o 50%.
        val deck = List(20) { card("Basic $it", basic = true) } + card("Chiave") + filler(39)
        val summary = HandSimulationEngine.run(deck, runs = 4000, keyCardNames = listOf("Chiave"))

        val prized = summary.keyCardPrizedRate!!
        assertTrue("prized=$prized", prized in 6.0..17.0)
    }

    // --- Il giudizio sulla singola mano (Prova) -----------------------------

    @Test
    fun `senza sapere chi inizia il giudizio resta quello di prima`() {
        val hand = listOf(pikachu, energy, kissara) + filler(4)
        val before = HandEvaluator.evaluate(hand)
        assertTrue(HandTrait.HAS_OUT in before.strengths)
        assertFalse(HandTrait.SUPPORTER_LOCKED_T1 in before.weaknesses)
    }

    @Test
    fun `iniziando per primi una mano con solo Aiuti vale meno`() {
        // Due Base, energia e un Aiuto: ottima per secondi, giocabile per primi.
        val hand = listOf(pikachu, card("Bulbasaur", basic = true), energy, kissara) + filler(3)
        val first = HandEvaluator.evaluate(hand, order = TurnOrder.FIRST)
        val second = HandEvaluator.evaluate(hand, order = TurnOrder.SECOND)

        assertTrue(HandTrait.SUPPORTER_LOCKED_T1 in first.weaknesses)
        assertTrue(HandTrait.HAS_OUT in second.strengths)
        assertEquals(HandVerdict.GREAT, second.verdict)
        assertEquals(HandVerdict.PLAYABLE, first.verdict)
    }

    @Test
    fun `con un Base solo la mano resta giocabile, ma il limite e' segnalato`() {
        val hand = listOf(pikachu, energy, kissara) + filler(4)
        val first = HandEvaluator.evaluate(hand, order = TurnOrder.FIRST)

        assertEquals(HandVerdict.PLAYABLE, first.verdict)
        assertTrue(HandTrait.SUPPORTER_LOCKED_T1 in first.weaknesses)
    }

    @Test
    fun `la Prova mette da parte 6 Premi e pesca dopo di loro`() {
        val pool = List(20) { card("Basic $it", basic = true) } + filler(40)
        val hand = PracticeDealer.deal(pool, turnOrder = TurnOrder.SECOND, random = Random(3))!!

        assertEquals(6, hand.prizes.size)
        assertEquals(47, hand.library.size)
        assertEquals(TurnOrder.SECOND, hand.turnOrder)

        val afterDraw = PracticeDealer.drawOne(hand)
        assertEquals(hand.library.first(), afterDraw.draws.single())
    }

    @Test
    fun `dal secondo turno chi inizia gioca gli Aiuti`() {
        assertEquals(TurnOrder.FIRST, PracticeDealer.orderForTurn(TurnOrder.FIRST, 1))
        assertNull(PracticeDealer.orderForTurn(TurnOrder.FIRST, 2))
    }

    @Test
    fun `cambiare chi inizia non cambia le carte, solo il giudizio`() {
        val pool = List(20) { card("Basic $it", basic = true) } + List(40) { kissara }
        val hand = PracticeDealer.deal(pool, turnOrder = TurnOrder.SECOND, random = Random(5))!!
        val switched = PracticeDealer.withTurnOrder(hand, TurnOrder.FIRST)

        assertEquals(hand.cards, switched.cards)
        assertEquals(hand.prizes, switched.prizes)
        assertEquals(TurnOrder.FIRST, switched.turnOrder)
    }
}
