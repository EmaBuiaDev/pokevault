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
    val imageUrl: String = "",
    /**
     * Un Aiuto che si puo' giocare al primo turno anche iniziando per primi:
     * lo dice la carta stessa (Rubra, "Se inizi per primo, puoi usare questa
     * carta durante il tuo primo turno"). Vedi [FirstTurnRules].
     */
    val playableFirstTurnGoingFirst: Boolean = false
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
    /** Iniziando per primi: niente Aiuti al primo turno. */
    val goingFirst: OrderMetrics,
    /** Andando per secondi: anche gli Aiuti, gia' al primo turno. */
    val goingSecond: OrderMetrics,
    val keyCardByT2Rate: Double?,
    /**
     * Quante volte almeno una copia di una carta chiave e' finita fra i 6
     * Premi: li' non si pesca, si recupera solo prendendo Premi. Null se non
     * ci sono carte chiave scelte.
     */
    val keyCardPrizedRate: Double? = null,
    val problemHands: List<ProblemHandSample>,
    val mulligan: MulliganStats = MulliganStats(),
    val basics: BasicStats = BasicStats(),
    val energy: EnergyStats = EnergyStats(),
    val supporters: SupporterStats = SupporterStats()
)

/**
 * Il mulligan, calcolato e non simulato.
 *
 * E' l'unica probabilita' del simulatore che ha una formula chiusa: le prime
 * 7 carte senza Pokemon Base sono un'ipergeometrica, e ogni mulligan rimescola
 * tutto, quindi i tentativi sono indipendenti. Il numero esatto non ha il
 * rumore della simulazione, e su questo dato -- il primo che un giocatore
 * guarda quando taglia la linea dei Base -- un punto di scarto si nota.
 */
data class MulliganStats(
    /** Prime 7 carte senza Base: mulligan obbligato. */
    val firstHandRate: Double = 0.0,
    /** Almeno due mulligan di fila nella stessa partita. */
    val twoOrMoreRate: Double = 0.0,
    /** Mulligan medi per partita, cioe' carte in piu' regalate all'avversario. */
    val averagePerGame: Double = 0.0
)

/** I Pokemon Base nella mano tenuta. */
data class BasicStats(
    val inDeck: Int = 0,
    /** Mani con 1, 2, 3 e 4 o piu' Base, in percentuale: sempre quattro voci. */
    val distribution: List<Double> = listOf(0.0, 0.0, 0.0, 0.0),
    val average: Double = 0.0
) {
    /**
     * Un Base solo e niente panchina: se viene messo KO al primo attacco
     * dell'avversario la partita e' persa.
     */
    val loneBasicRate: Double get() = distribution.firstOrNull() ?: 0.0
}

/** Le carte Energia in mano, turno per turno. */
data class EnergyStats(
    val inDeck: Int = 0,
    /** Almeno una nelle 7 carte tenute. */
    val inOpeningRate: Double = 0.0,
    /** Almeno una dopo la pescata del 1° turno. */
    val byTurn1Rate: Double = 0.0,
    /** Almeno una dopo la pescata del 2° turno. */
    val byTurn2Rate: Double = 0.0,
    val averageInOpening: Double = 0.0
)

/**
 * Le carte Aiuto in mano entro il proprio 2° turno.
 *
 * Il 2° turno e' quello che conta: chi inizia non gioca Aiuti al primo, quindi
 * e' li' che li usa per la prima volta. Il 1° turno serve a chi va per secondo.
 */
data class SupporterStats(
    val inDeck: Int = 0,
    /** Almeno un Aiuto dopo la pescata del 1° turno. */
    val byTurn1Rate: Double = 0.0,
    /** Almeno un Aiuto dopo la pescata del 2° turno. */
    val byTurn2Rate: Double = 0.0,
    val averageByTurn2: Double = 0.0,
    /** Mani con 0, 1 e 2 o piu' Aiuti entro il 2° turno: sempre tre voci. */
    val distributionByTurn2: List<Double> = listOf(0.0, 0.0, 0.0)
)

/** Le metriche che cambiano a seconda di chi inizia. */
data class OrderMetrics(
    /** Una carta che pesca o cerca, e che si puo' giocare al primo turno. */
    val outByT1Rate: Double,
    /** Basic, energia entro il 2° turno, e pescata/ricerca giocabile entro il 2° turno. */
    val setupByT2Rate: Double,
    /** Il punteggio 0-100 in questo caso. */
    val score: Int
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

        if (cardPool.size < FirstTurnRules.OPENING_HAND) {
            return emptySummary()
        }

        val basicsInDeck = cardPool.count { it.isBasic }
        val energiesInDeck = cardPool.count { it.isEnergy }
        val supportersInDeck = cardPool.count { it.isSupporter }
        val normalizedKeyCards = keyCardNames
            .asSequence()
            .map { it.lowercase().trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val hasKeyCards = normalizedKeyCards.isNotEmpty()

        if (basicsInDeck == 0) {
            val none = OrderMetrics(outByT1Rate = 0.0, setupByT2Rate = 0.0, score = 0)
            return HandSimulationSummary(
                totalRuns = runs,
                consistencyScore = 0,
                starterRate = 0.0,
                mulliganRate = 100.0,
                averageBasics = 0.0,
                averageMulligans = 99.0,
                energyByT1Rate = 0.0,
                goingFirst = none,
                goingSecond = none,
                keyCardByT2Rate = if (hasKeyCards) 0.0 else null,
                keyCardPrizedRate = if (hasKeyCards) 0.0 else null,
                problemHands = listOf(
                    ProblemHandSample(
                        cards = cardPool.take(FirstTurnRules.OPENING_HAND),
                        tags = listOf("NO_BASIC_IN_DECK")
                    )
                ),
                mulligan = MulliganStats(firstHandRate = 100.0, twoOrMoreRate = 100.0, averagePerGame = 0.0),
                energy = EnergyStats(inDeck = energiesInDeck),
                supporters = SupporterStats(inDeck = supportersInDeck)
            )
        }

        // Random.Default e' seminato dal sistema. Prima si usava
        // Random(System.currentTimeMillis()): due simulazioni avviate nello
        // stesso millisecondo producevano risultati identici.
        val random = Random.Default

        var totalBasics = 0
        var energyByT1Hits = 0
        var keyCardByT2Hits = 0
        var keyCardPrizedHits = 0
        val outT1Hits = IntArray(2)
        val setupT2Hits = IntArray(2)
        // Mani tenute con 1, 2, 3, 4+ Base.
        val basicsHistogram = IntArray(4)
        var energyInOpeningHits = 0
        var energyByT2Hits = 0
        var totalOpeningEnergies = 0
        var supporterByT1Hits = 0
        var totalSupportersByT2 = 0
        // Mani con 0, 1, 2+ Aiuti entro il 2° turno.
        val supportersHistogram = IntArray(3)

        val problemCandidates = mutableListOf<ProblemHandSample>()

        repeat(runs) {
            val deal = dealLegalOpening(cardPool, random)

            val result = evaluateDeal(deal, normalizedKeyCards)

            totalBasics += result.basics
            if (result.basics > 0) basicsHistogram[(result.basics - 1).coerceAtMost(3)] += 1
            if (result.energiesInOpening > 0) energyInOpeningHits += 1
            totalOpeningEnergies += result.energiesInOpening
            if (result.energyByT2) energyByT2Hits += 1
            if (result.supportersByT1 > 0) supporterByT1Hits += 1
            totalSupportersByT2 += result.supportersByT2
            supportersHistogram[result.supportersByT2.coerceAtMost(2)] += 1
            if (result.energyByT1) energyByT1Hits += 1
            if (result.keyCardByT2 == true) keyCardByT2Hits += 1
            if (result.keyCardPrized) keyCardPrizedHits += 1
            TurnOrder.entries.forEach { order ->
                if (result.outByT1.getValue(order)) outT1Hits[order.ordinal] += 1
                if (result.setupByT2.getValue(order)) setupT2Hits[order.ordinal] += 1
            }

            if (result.tags.isNotEmpty() && problemCandidates.size < PROBLEM_HANDS_POOL) {
                problemCandidates += ProblemHandSample(cards = deal.opening, tags = result.tags)
            }
        }

        val totalRuns = runs.toDouble()
        fun percent(hits: Int) = (hits / totalRuns) * 100.0

        val keyRate = if (hasKeyCards) percent(keyCardByT2Hits) else null
        // Il mulligan ha una formula esatta: la usano tutti, voto compreso,
        // cosi' a schermo non compaiono due numeri quasi uguali per la
        // stessa cosa (uno calcolato e uno simulato).
        val mulligan = exactMulligan(deckSize = cardPool.size, basicsInDeck = basicsInDeck)
        val starterRate = 100.0 - mulligan.firstHandRate
        val energyRate = percent(energyByT1Hits)

        fun metricsFor(order: TurnOrder): OrderMetrics {
            val outRate = percent(outT1Hits[order.ordinal])
            val setupRate = percent(setupT2Hits[order.ordinal])
            return OrderMetrics(
                outByT1Rate = outRate,
                setupByT2Rate = setupRate,
                score = consistencyScore(
                    starterRate = starterRate,
                    setupByT2Rate = setupRate,
                    outByT1Rate = outRate,
                    energyByT1Rate = energyRate,
                    keyCardByT2Rate = keyRate
                )
            )
        }

        val goingFirst = metricsFor(TurnOrder.FIRST)
        val goingSecond = metricsFor(TurnOrder.SECOND)

        return HandSimulationSummary(
            totalRuns = runs,
            // Chi inizia lo decide la moneta: il voto del mazzo e' la media
            // dei due casi, che restano visibili uno per uno.
            consistencyScore = ((goingFirst.score + goingSecond.score) / 2.0).roundToInt(),
            starterRate = starterRate,
            mulliganRate = mulligan.firstHandRate,
            averageBasics = totalBasics / totalRuns,
            averageMulligans = mulligan.averagePerGame,
            energyByT1Rate = energyRate,
            goingFirst = goingFirst,
            goingSecond = goingSecond,
            keyCardByT2Rate = keyRate,
            keyCardPrizedRate = if (hasKeyCards) percent(keyCardPrizedHits) else null,
            problemHands = worstHands(problemCandidates),
            mulligan = mulligan,
            basics = BasicStats(
                inDeck = basicsInDeck,
                // Sulle sole mani valide: dealLegalOpening dopo 50 mulligan di
                // fila si arrende e tiene una mano senza Base (con un Base solo
                // nel mazzo capita circa una volta su 300), e quella mano non
                // sta in nessun gruppo.
                distribution = basicsHistogram.sum().let { legal ->
                    basicsHistogram.map { if (legal == 0) 0.0 else it * 100.0 / legal }
                },
                average = totalBasics / totalRuns
            ),
            energy = EnergyStats(
                inDeck = energiesInDeck,
                inOpeningRate = percent(energyInOpeningHits),
                byTurn1Rate = energyRate,
                byTurn2Rate = percent(energyByT2Hits),
                averageInOpening = totalOpeningEnergies / totalRuns
            ),
            supporters = SupporterStats(
                inDeck = supportersInDeck,
                byTurn1Rate = percent(supporterByT1Hits),
                byTurn2Rate = 100.0 - percent(supportersHistogram[0]),
                averageByTurn2 = totalSupportersByT2 / totalRuns,
                distributionByTurn2 = supportersHistogram.map { percent(it) }
            )
        )
    }

    /**
     * Il mulligan con la formula invece che con le run.
     *
     * Probabilita' che 7 carte prese da [deckSize] non contengano nessuno dei
     * [basicsInDeck] Base: C(N-B, 7) / C(N, 7), scritta come prodotto per non
     * passare da fattoriali enormi. Ogni mulligan rimescola il mazzo intero,
     * quindi due di fila sono p² e la media e' quella di una geometrica, p/(1-p).
     */
    internal fun exactMulligan(deckSize: Int, basicsInDeck: Int): MulliganStats {
        val hand = FirstTurnRules.OPENING_HAND
        if (deckSize < hand) return MulliganStats()
        val nonBasics = deckSize - basicsInDeck
        var p = 1.0
        for (i in 0 until hand) {
            p *= (nonBasics - i).coerceAtLeast(0).toDouble() / (deckSize - i)
        }
        if (p >= 1.0) return MulliganStats(firstHandRate = 100.0, twoOrMoreRate = 100.0, averagePerGame = 0.0)
        return MulliganStats(
            firstHandRate = p * 100.0,
            twoOrMoreRate = p * p * 100.0,
            averagePerGame = p / (1.0 - p)
        )
    }

    /** Una mano legale con i suoi Premi e il resto del mazzo, in ordine di pescata. */
    internal data class Deal(
        val opening: List<SimulatorCard>,
        val prizes: List<SimulatorCard>,
        val library: List<SimulatorCard>,
        val mulligans: Int
    )

    /**
     * Mescola finche' la mano ha un Basic, poi mette da parte i Premi.
     *
     * L'ordine e' quello del tavolo: 7 carte, mulligan finche' serve, e solo
     * con la mano tenuta le 6 carte Premio dalla cima del mazzo. Le pescate dei
     * turni vengono dopo i Premi.
     */
    internal fun dealLegalOpening(cardPool: List<SimulatorCard>, random: Random): Deal {
        var mulligans = 0
        while (true) {
            val shuffled = cardPool.shuffled(random)
            val opening = shuffled.take(FirstTurnRules.OPENING_HAND)
            if (opening.any { it.isBasic } || mulligans >= 50) {
                val rest = shuffled.drop(FirstTurnRules.OPENING_HAND)
                return Deal(
                    opening = opening,
                    prizes = rest.take(FirstTurnRules.PRIZE_CARDS),
                    library = rest.drop(FirstTurnRules.PRIZE_CARDS),
                    mulligans = mulligans
                )
            }
            mulligans += 1
        }
    }

    internal data class DealResult(
        val basics: Int,
        val energiesInOpening: Int,
        val energyByT1: Boolean,
        val energyByT2: Boolean,
        val supportersByT1: Int,
        val supportersByT2: Int,
        val outByT1: Map<TurnOrder, Boolean>,
        val setupByT2: Map<TurnOrder, Boolean>,
        val keyCardByT2: Boolean?,
        val keyCardPrized: Boolean,
        val tags: List<String>
    )

    /**
     * Cosa si riesce a fare con una mano, nei due casi.
     *
     * Turno 1: mano + 1 pescata (pescano tutti e due). Turno 2: + 1 pescata.
     * - Pescata/ricerca al T1: chi inizia solo con Strumenti (o un Aiuto che
     *   lo permette, Rubra); chi va secondo anche con gli Aiuti.
     * - Setup entro il 2° turno: Basic, energia in mano entro il 2° turno, e
     *   una pescata/ricerca giocabile entro il 2° turno. Al 2° turno l'Aiuto si
     *   gioca anche iniziando per primi.
     */
    internal fun evaluateDeal(deal: Deal, normalizedKeyCards: Set<String>): DealResult {
        val handT1 = deal.opening + listOfNotNull(deal.library.getOrNull(0))
        val handT2 = handT1 + listOfNotNull(deal.library.getOrNull(1))

        val basics = deal.opening.count { it.isBasic }
        val energyByT1 = handT1.any { it.isEnergy }
        val energyByT2 = handT2.any { it.isEnergy }
        val anyOutByT2 = handT2.any { it.isOutCard }

        val outByT1 = TurnOrder.entries.associateWith { order ->
            handT1.any { FirstTurnRules.isUsableOutOnFirstTurn(it, order) }
        }
        val setupByT2 = TurnOrder.entries.associateWith { order ->
            basics > 0 && energyByT2 && (outByT1.getValue(order) || anyOutByT2)
        }

        fun isKey(card: SimulatorCard) = normalizedKeyCards.contains(card.name.lowercase().trim())
        val keyCardByT2 = if (normalizedKeyCards.isEmpty()) null else handT2.any(::isKey)
        val keyCardPrized = normalizedKeyCards.isNotEmpty() && deal.prizes.any(::isKey)

        // Le mani problematiche si giudicano iniziando per primi: e' il caso
        // piu' duro, ed e' li' che una mano "da Aiuto" si inceppa.
        val tags = mutableListOf<String>()
        if (!energyByT1) tags += "NO_ENERGY_T1"
        if (!outByT1.getValue(TurnOrder.FIRST)) {
            tags += if (outByT1.getValue(TurnOrder.SECOND)) "SUPPORTER_ONLY_T1" else "NO_OUT_T1"
        }
        if (!setupByT2.getValue(TurnOrder.FIRST)) tags += "SETUP_RISK_T2"
        if (keyCardByT2 == false) tags += "MISS_KEYCARD_T2"
        if (keyCardPrized) tags += "KEY_CARD_PRIZED"

        return DealResult(
            basics = basics,
            energiesInOpening = deal.opening.count { it.isEnergy },
            energyByT1 = energyByT1,
            energyByT2 = energyByT2,
            supportersByT1 = handT1.count { it.isSupporter },
            supportersByT2 = handT2.count { it.isSupporter },
            outByT1 = outByT1,
            setupByT2 = setupByT2,
            keyCardByT2 = keyCardByT2,
            keyCardPrized = keyCardPrized,
            tags = tags
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
        val none = OrderMetrics(outByT1Rate = 0.0, setupByT2Rate = 0.0, score = 0)
        return HandSimulationSummary(
            totalRuns = 0,
            consistencyScore = 0,
            starterRate = 0.0,
            mulliganRate = 0.0,
            averageBasics = 0.0,
            averageMulligans = 0.0,
            energyByT1Rate = 0.0,
            goingFirst = none,
            goingSecond = none,
            keyCardByT2Rate = null,
            keyCardPrizedRate = null,
            problemHands = emptyList()
        )
    }
}
