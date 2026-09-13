package com.emabuia.pokevault.data.simulator

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Una carta del mazzo, ridotta a cio' che serve per simulare.
 *
 * [id] e [imageUrl] non servono alla matematica: servono alla UI, che dal
 * momento in cui le mani si vedono davvero (modalita' Prova) deve poter
 * disegnare la carta e non solo scriverne il nome.
 */
data class SimulatorCard(
    val name: String,
    val isBasic: Boolean,
    val isEnergy: Boolean,
    val isSupporter: Boolean,
    val isOutCard: Boolean,
    val id: String = "",
    val imageUrl: String = ""
)

data class ProblemHandSample(
    val cards: List<SimulatorCard>,
    val tags: List<String>
)

data class HandSimulationSummary(
    val totalRuns: Int,
    /**
     * Sintesi 0-100 delle metriche sotto, vedi [HandSimulationEngine.consistencyScore].
     *
     * Esiste perche' otto percentuali affiancate non dicono all'utente se il
     * mazzo gira: dicono otto numeri. Il punteggio da' il verdetto, le
     * percentuali restano sotto per chi vuole sapere da dove viene.
     */
    val consistencyScore: Int,
    val starterRate: Double,
    val mulliganRate: Double,
    val averageBasics: Double,
    val averageMulligans: Double,
    val energyByT1Rate: Double,
    val outByT1Rate: Double,
    val setupByT2Rate: Double,
    val keyCardByT2Rate: Double?,
    val problemHands: List<ProblemHandSample>
)

object HandSimulationEngine {

    /**
     * Peso di ogni metrica nel punteggio di consistenza.
     *
     * Lo starter rate pesa piu' di tutto perche' una mano senza Basic non e'
     * una partita giocata male: e' una partita non iniziata. Subito dopo viene
     * il setup entro T2, l'unica metrica che guarda la mano nel suo insieme
     * invece di una singola risorsa. Le key card pesano solo quando l'utente ne
     * ha scelte: altrimenti il loro peso si ridistribuisce sulle altre (vedi
     * [consistencyScore]).
     */
    private const val WEIGHT_STARTER = 0.30
    private const val WEIGHT_SETUP_T2 = 0.25
    private const val WEIGHT_OUT_T1 = 0.20
    private const val WEIGHT_ENERGY_T1 = 0.15
    private const val WEIGHT_KEY_T2 = 0.10

    /** Quante mani problematiche tenere da mostrare. */
    private const val PROBLEM_HANDS_KEPT = 6

    /** Quante candidate raccogliere prima di scegliere le [PROBLEM_HANDS_KEPT] peggiori. */
    private const val PROBLEM_HANDS_POOL = 60

    fun run(
        cardPool: List<SimulatorCard>,
        runs: Int,
        keyCardNames: List<String>
    ): HandSimulationSummary {
        if (runs <= 0) {
            return emptySummary()
        }

        if (cardPool.size < 7) {
            return emptySummary()
        }

        val basicsInDeck = cardPool.count { it.isBasic }
        val normalizedKeyCards = keyCardNames
            .asSequence()
            .map { it.lowercase().trim() }
            .filter { it.isNotBlank() }
            .toSet()

        if (basicsInDeck == 0) {
            return HandSimulationSummary(
                totalRuns = runs,
                consistencyScore = 0,
                starterRate = 0.0,
                mulliganRate = 100.0,
                averageBasics = 0.0,
                averageMulligans = 99.0,
                energyByT1Rate = 0.0,
                outByT1Rate = 0.0,
                setupByT2Rate = 0.0,
                keyCardByT2Rate = normalizedKeyCards.takeIf { it.isNotEmpty() }?.let { 0.0 },
                problemHands = listOf(
                    ProblemHandSample(
                        cards = cardPool.take(7),
                        tags = listOf("NO_BASIC_IN_DECK")
                    )
                )
            )
        }

        // Random.Default e' seminato dal sistema. Prima si usava
        // Random(System.currentTimeMillis()): due simulazioni avviate nello
        // stesso millisecondo producevano risultati identici.
        val random = Random.Default

        var firstHandStarterHits = 0
        var firstHandMulligans = 0
        var totalBasics = 0
        var totalMulligans = 0
        var energyByT1Hits = 0
        var outByT1Hits = 0
        var setupByT2Hits = 0
        var keyCardByT2Hits = 0

        val problemCandidates = mutableListOf<ProblemHandSample>()

        repeat(runs) {
            var mulligansThisRun = 0
            var opening: List<SimulatorCard>
            var remaining: List<SimulatorCard>

            // Simulate mulligan loop until a legal opening hand is found.
            while (true) {
                val shuffled = cardPool.shuffled(random)
                opening = shuffled.take(7)
                remaining = shuffled.drop(7)

                val hasStarter = opening.any { it.isBasic }
                if (mulligansThisRun == 0) {
                    if (hasStarter) {
                        firstHandStarterHits += 1
                    } else {
                        firstHandMulligans += 1
                    }
                }

                if (hasStarter) break
                mulligansThisRun += 1
                if (mulligansThisRun >= 50) break
            }

            totalMulligans += mulligansThisRun

            val drawT1 = remaining.getOrNull(0)
            val drawT2 = remaining.getOrNull(1)

            val handT1 = opening + listOfNotNull(drawT1)
            val handT2 = handT1 + listOfNotNull(drawT2)

            val basics = opening.count { it.isBasic }
            val hasEnergyT1 = handT1.any { it.isEnergy }
            val hasSupporterT1 = handT1.any { it.isSupporter }
            val hasOutT1 = handT1.any { it.isOutCard }
            val hasEnergyT2 = handT2.any { it.isEnergy }
            val keyCardByT2 = normalizedKeyCards.takeIf { it.isNotEmpty() }?.let { keys ->
                handT2.any { keys.contains(it.name.lowercase().trim()) }
            }

            val setupByT2 = basics > 0 && hasEnergyT2 && (hasSupporterT1 || hasOutT1)

            totalBasics += basics
            if (hasEnergyT1) energyByT1Hits += 1
            if (hasOutT1) outByT1Hits += 1
            if (setupByT2) setupByT2Hits += 1
            if (keyCardByT2 == true) keyCardByT2Hits += 1

            val tags = mutableListOf<String>()
            if (!hasEnergyT1) tags += "NO_ENERGY_T1"
            if (!hasOutT1) tags += "NO_OUT_T1"
            if (!setupByT2) tags += "SETUP_RISK_T2"
            if (normalizedKeyCards.isNotEmpty() && keyCardByT2 == false) tags += "MISS_KEYCARD_T2"

            if (tags.isNotEmpty() && problemCandidates.size < PROBLEM_HANDS_POOL) {
                problemCandidates += ProblemHandSample(cards = opening, tags = tags)
            }
        }

        val totalRuns = runs.toDouble()
        val keyRate = normalizedKeyCards.takeIf { it.isNotEmpty() }?.let {
            (keyCardByT2Hits / totalRuns) * 100.0
        }

        val starterRate = (firstHandStarterHits / totalRuns) * 100.0
        val energyRate = (energyByT1Hits / totalRuns) * 100.0
        val outRate = (outByT1Hits / totalRuns) * 100.0
        val setupRate = (setupByT2Hits / totalRuns) * 100.0

        return HandSimulationSummary(
            totalRuns = runs,
            consistencyScore = consistencyScore(
                starterRate = starterRate,
                setupByT2Rate = setupRate,
                outByT1Rate = outRate,
                energyByT1Rate = energyRate,
                keyCardByT2Rate = keyRate
            ),
            starterRate = starterRate,
            mulliganRate = (firstHandMulligans / totalRuns) * 100.0,
            averageBasics = totalBasics / totalRuns,
            averageMulligans = totalMulligans / totalRuns,
            energyByT1Rate = energyRate,
            outByT1Rate = outRate,
            setupByT2Rate = setupRate,
            keyCardByT2Rate = keyRate,
            problemHands = worstHands(problemCandidates)
        )
    }

    /**
     * Media pesata delle metriche, 0-100.
     *
     * Quando non ci sono key card il loro peso non va perso ne' regalato: i
     * pesi rimasti si rinormalizzano, cosi' un mazzo senza key card selezionate
     * non parte svantaggiato di dieci punti rispetto a uno che ne ha.
     */
    internal fun consistencyScore(
        starterRate: Double,
        setupByT2Rate: Double,
        outByT1Rate: Double,
        energyByT1Rate: Double,
        keyCardByT2Rate: Double?
    ): Int {
        val entries = buildList {
            add(WEIGHT_STARTER to starterRate)
            add(WEIGHT_SETUP_T2 to setupByT2Rate)
            add(WEIGHT_OUT_T1 to outByT1Rate)
            add(WEIGHT_ENERGY_T1 to energyByT1Rate)
            if (keyCardByT2Rate != null) add(WEIGHT_KEY_T2 to keyCardByT2Rate)
        }

        val totalWeight = entries.sumOf { it.first }
        if (totalWeight <= 0.0) return 0

        val weighted = entries.sumOf { (weight, value) -> weight * value } / totalWeight
        return weighted.roundToInt().coerceIn(0, 100)
    }

    /**
     * Le mani peggiori fra le candidate, senza doppioni.
     *
     * Prima si tenevano le prime dodici mani con almeno un tag: siccome
     * "SETUP_RISK_T2" scatta su gran parte delle mani, quelle dodici erano di
     * fatto le prime dodici run e non le piu' istruttive. Qui si raccoglie un
     * bacino piu' largo e si tengono quelle con piu' problemi insieme, le sole
     * su cui valga la pena ragionare.
     */
    private fun worstHands(candidates: List<ProblemHandSample>): List<ProblemHandSample> {
        return candidates
            .distinctBy { hand -> hand.cards.map { it.name }.sorted().joinToString("|") }
            .sortedByDescending { it.tags.size }
            .take(PROBLEM_HANDS_KEPT)
    }

    private fun emptySummary(): HandSimulationSummary {
        return HandSimulationSummary(
            totalRuns = 0,
            consistencyScore = 0,
            starterRate = 0.0,
            mulliganRate = 0.0,
            averageBasics = 0.0,
            averageMulligans = 0.0,
            energyByT1Rate = 0.0,
            outByT1Rate = 0.0,
            setupByT2Rate = 0.0,
            keyCardByT2Rate = null,
            problemHands = emptyList()
        )
    }
}
