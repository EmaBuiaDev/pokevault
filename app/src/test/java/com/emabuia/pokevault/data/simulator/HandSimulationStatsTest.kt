package com.emabuia.pokevault.data.simulator

import java.math.BigInteger
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le statistiche dell'analisi: mulligan, Pokemon Base, Energia, carte Aiuto.
 *
 * Le regole su cui si basano sono quelle del regolamento ufficiale (Scarlatto
 * e Violetto): 7 carte, mulligan obbligato solo senza Pokemon Base, 6 Premi
 * messi da parte dopo la mano, una carta pescata all'inizio di ogni turno,
 * anche il primo.
 */
class HandSimulationStatsTest {

    private fun card(
        name: String,
        basic: Boolean = false,
        energy: Boolean = false,
        supporter: Boolean = false,
        out: Boolean = false
    ) = SimulatorCard(name = name, isBasic = basic, isEnergy = energy, isSupporter = supporter, isOutCard = out)

    private fun deck(basics: Int, energies: Int, supporters: Int, size: Int = 60): List<SimulatorCard> = buildList {
        repeat(basics) { add(card("Basic $it", basic = true)) }
        repeat(energies) { add(card("Energy $it", energy = true)) }
        repeat(supporters) { add(card("Supporter $it", supporter = true, out = true)) }
        repeat(size - basics - energies - supporters) { add(card("Filler $it")) }
    }

    private fun choose(n: Int, k: Int): BigInteger {
        if (k < 0 || k > n) return BigInteger.ZERO
        var result = BigInteger.ONE
        for (i in 0 until k) {
            result = result.multiply(BigInteger.valueOf((n - i).toLong())).divide(BigInteger.valueOf((i + 1).toLong()))
        }
        return result
    }

    /** C(N-B, 7) / C(N, 7), con gli interi esatti. */
    private fun hypergeometricNoBasic(deckSize: Int, basics: Int): Double =
        choose(deckSize - basics, 7).toBigDecimal()
            .divide(choose(deckSize, 7).toBigDecimal(), java.math.MathContext.DECIMAL64)
            .toDouble()

    // ── Mulligan ────────────────────────────────────────────────────────────

    @Test
    fun `il mulligan esatto coincide con la formula ipergeometrica`() {
        listOf(6, 10, 12, 15, 20).forEach { basics ->
            val p = hypergeometricNoBasic(60, basics)
            val stats = HandSimulationEngine.exactMulligan(deckSize = 60, basicsInDeck = basics)

            assertEquals("prima mano, $basics Base", p * 100.0, stats.firstHandRate, 1e-9)
            assertEquals("due di fila, $basics Base", p * p * 100.0, stats.twoOrMoreRate, 1e-9)
            assertEquals("media, $basics Base", p / (1.0 - p), stats.averagePerGame, 1e-9)
        }
    }

    @Test
    fun `con 10 Base su 60 il mulligan e' circa una volta su quattro`() {
        // C(50,7)/C(60,7) = 99.884.400 / 386.206.920 = 0,25863
        // cioe' (50/60)(49/59)(48/58)(47/57)(46/56)(45/55)(44/54).
        assertEquals(0.25863, 99_884_400.0 / 386_206_920.0, 0.00001)
        val stats = HandSimulationEngine.exactMulligan(deckSize = 60, basicsInDeck = 10)
        assertEquals(25.863, stats.firstHandRate, 0.001)
    }

    @Test
    fun `il mazzo mescolato davvero da' lo stesso mulligan della formula`() {
        val pool = deck(basics = 10, energies = 12, supporters = 10)
        val random = Random(42)
        val runs = 20_000
        val mulliganedFirst = (1..runs).count { HandSimulationEngine.dealLegalOpening(pool, random).mulligans > 0 }

        val simulated = mulliganedFirst * 100.0 / runs
        val exact = HandSimulationEngine.exactMulligan(60, 10).firstHandRate
        assertEquals(exact, simulated, 1.5)
    }

    @Test
    fun `solo Base vuol dire nessun mulligan, nessun Base vuol dire sempre`() {
        assertEquals(0.0, HandSimulationEngine.exactMulligan(60, 60).firstHandRate, 0.0)
        assertEquals(0.0, HandSimulationEngine.exactMulligan(60, 60).averagePerGame, 0.0)
        assertEquals(100.0, HandSimulationEngine.exactMulligan(60, 0).firstHandRate, 0.0)
        // Con 54 Base su 60 restano solo 6 carte che non lo sono: 7 carte
        // senza Base sono impossibili.
        assertEquals(0.0, HandSimulationEngine.exactMulligan(60, 54).firstHandRate, 0.0)
        assertTrue(HandSimulationEngine.exactMulligan(60, 53).firstHandRate > 0.0)
    }

    @Test
    fun `l'analisi usa il mulligan esatto anche nel voto`() {
        val summary = HandSimulationEngine.run(deck(basics = 10, energies = 12, supporters = 10), 500, emptyList())
        val exact = HandSimulationEngine.exactMulligan(60, 10)

        assertEquals(exact.firstHandRate, summary.mulligan.firstHandRate, 0.0)
        assertEquals(exact.firstHandRate, summary.mulliganRate, 0.0)
        assertEquals(100.0 - exact.firstHandRate, summary.starterRate, 1e-9)
    }

    // ── Pokemon Base nella mano tenuta ──────────────────────────────────────

    @Test
    fun `la distribuzione dei Base copre tutte le mani tenute`() {
        val summary = HandSimulationEngine.run(deck(basics = 12, energies = 12, supporters = 10), 3000, emptyList())

        assertEquals(4, summary.basics.distribution.size)
        assertEquals(100.0, summary.basics.distribution.sum(), 1e-6)
        assertEquals(12, summary.basics.inDeck)
        assertTrue(summary.basics.average >= 1.0)
    }

    @Test
    fun `con un solo Base nel mazzo si parte sempre con un Base solo`() {
        val summary = HandSimulationEngine.run(deck(basics = 1, energies = 12, supporters = 10), 3000, emptyList())

        assertEquals(100.0, summary.basics.loneBasicRate, 1e-6)
        // La media resta su tutte le run, comprese le rarissime mani senza Base
        // tenute dopo 50 mulligan: per questo non e' esattamente 1.
        assertEquals(1.0, summary.basics.average, 0.02)
    }

    // ── Energia ─────────────────────────────────────────────────────────────

    @Test
    fun `l'energia in mano cresce turno per turno`() {
        val energy = HandSimulationEngine.run(deck(basics = 12, energies = 10, supporters = 10), 3000, emptyList()).energy

        assertEquals(10, energy.inDeck)
        assertTrue(energy.inOpeningRate <= energy.byTurn1Rate)
        assertTrue(energy.byTurn1Rate <= energy.byTurn2Rate)
        assertTrue(energy.averageInOpening > 0.0)
    }

    @Test
    fun `senza carte Energia le percentuali sono zero`() {
        val energy = HandSimulationEngine.run(deck(basics = 12, energies = 0, supporters = 10), 300, emptyList()).energy

        assertEquals(0.0, energy.inOpeningRate, 0.0)
        assertEquals(0.0, energy.byTurn2Rate, 0.0)
        assertEquals(0.0, energy.averageInOpening, 0.0)
    }

    // ── Carte Aiuto ─────────────────────────────────────────────────────────

    @Test
    fun `le carte Aiuto al 2° turno sono coerenti fra loro`() {
        val supporters = HandSimulationEngine.run(deck(basics = 12, energies = 10, supporters = 12), 3000, emptyList()).supporters

        assertEquals(12, supporters.inDeck)
        assertEquals(3, supporters.distributionByTurn2.size)
        assertEquals(100.0, supporters.distributionByTurn2.sum(), 1e-6)
        assertEquals(100.0 - supporters.distributionByTurn2[0], supporters.byTurn2Rate, 1e-9)
        assertTrue(supporters.byTurn1Rate <= supporters.byTurn2Rate)
    }

    @Test
    fun `senza carte Aiuto nessuna mano ne ha`() {
        val supporters = HandSimulationEngine.run(deck(basics = 12, energies = 10, supporters = 0), 300, emptyList()).supporters

        assertEquals(0.0, supporters.byTurn2Rate, 0.0)
        assertEquals(100.0, supporters.distributionByTurn2[0], 1e-6)
        assertEquals(0.0, supporters.averageByTurn2, 0.0)
    }

    /**
     * Le pescate del 1° e del 2° turno escono dal mazzo DOPO i Premi: un Aiuto
     * fra i Premi non si pesca, uno in cima al mazzo si'.
     */
    @Test
    fun `gli Aiuti si contano sulle pescate vere, non sui Premi`() {
        val filler = List(6) { card("Filler $it") }
        val deal = HandSimulationEngine.Deal(
            opening = listOf(card("Pikachu", basic = true)) + filler,
            prizes = List(6) { card("Prized Supporter $it", supporter = true, out = true) },
            library = listOf(
                card("Turn 1 Supporter", supporter = true, out = true),
                card("Turn 2 Supporter", supporter = true, out = true),
                card("Later Supporter", supporter = true, out = true)
            ),
            mulligans = 0
        )

        val result = HandSimulationEngine.evaluateDeal(deal, emptySet())

        assertEquals(1, result.supportersByT1)
        assertEquals(2, result.supportersByT2)
    }

    @Test
    fun `l'energia della mano iniziale non conta le pescate`() {
        val deal = HandSimulationEngine.Deal(
            opening = listOf(card("Pikachu", basic = true), card("Energy A", energy = true)) + List(5) { card("F$it") },
            prizes = List(6) { card("P$it", energy = true) },
            library = listOf(card("Energy B", energy = true), card("Energy C", energy = true)),
            mulligans = 0
        )

        val result = HandSimulationEngine.evaluateDeal(deal, emptySet())

        assertEquals(1, result.energiesInOpening)
        assertTrue(result.energyByT1)
        assertTrue(result.energyByT2)
    }
}
