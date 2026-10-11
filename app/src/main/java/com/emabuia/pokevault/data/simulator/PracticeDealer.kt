package com.emabuia.pokevault.data.simulator

import kotlin.random.Random

/**
 * Una mano vera, quella che si vede sul tavolo della modalita' Prova.
 *
 * Tiene anche il resto del mazzo ([library]) perche' la pescata del turno deve
 * uscire dallo stesso mescolamento dell'apertura: ripescare da un mazzo
 * rimescolato ogni volta darebbe probabilita' sbagliate proprio nel punto in
 * cui l'utente sta guardando la singola mano.
 */
data class PracticeHand(
    val opening: List<SimulatorCard>,
    /** Carte pescate dopo l'apertura, nell'ordine in cui sono uscite. */
    val draws: List<SimulatorCard>,
    val library: List<SimulatorCard>,
    /** Quanti mulligan sono stati presi prima di arrivare a questa mano. */
    val mulligans: Int,
    /** Verdetto su [cards], cioe' apertura piu' pescate gia' fatte. */
    val evaluation: HandEvaluation,
    /**
     * Le 6 carte Premio, messe da parte dalla cima del mazzo dopo la mano
     * iniziale: le pescate vengono dopo di loro, come al tavolo.
     */
    val prizes: List<SimulatorCard> = emptyList(),
    /** Chi inizia la partita: decide cosa si puo' giocare al primo turno. */
    val turnOrder: TurnOrder = TurnOrder.FIRST
) {
    val cards: List<SimulatorCard> get() = opening + draws

    /** 0 = apertura, 1 = dopo la pescata del T1, e cosi' via. */
    val turn: Int get() = draws.size

    val canDraw: Boolean get() = library.isNotEmpty()
}

/** Quanto e' andata la sessione di prova, per la riga di riepilogo. */
data class PracticeTally(
    val dealt: Int = 0,
    val kept: Int = 0,
    val mulliganed: Int = 0
) {
    /** Percentuale di mani tenute, null finche' non se ne e' vista nessuna. */
    val keepRate: Int?
        get() {
            val total = kept + mulliganed
            return if (total == 0) null else (kept * 100) / total
        }
}

object PracticeDealer {

    const val OPENING_SIZE = FirstTurnRules.OPENING_HAND

    /**
     * Mescola il mazzo, scopre sette carte e mette da parte i Premi.
     *
     * Non applica il mulligan automatico: in Prova la mano senza Basic va
     * *vista*, e' il momento in cui si capisce perche' il mazzo brickava. I
     * Premi si mettono da parte comunque: con un mulligan si rimescola tutto.
     * Con un mazzo troppo corto per averli si gioca senza.
     */
    fun deal(
        pool: List<SimulatorCard>,
        keyCardNames: Collection<String> = emptyList(),
        mulligans: Int = 0,
        turnOrder: TurnOrder = TurnOrder.FIRST,
        random: Random = Random.Default
    ): PracticeHand? {
        if (pool.size < OPENING_SIZE) return null

        val shuffled = pool.shuffled(random)
        val opening = shuffled.take(OPENING_SIZE)
        val rest = shuffled.drop(OPENING_SIZE)
        val prizeCount = if (rest.size > FirstTurnRules.PRIZE_CARDS) FirstTurnRules.PRIZE_CARDS else 0
        val prizes = rest.take(prizeCount)

        return PracticeHand(
            opening = opening,
            draws = emptyList(),
            library = rest.drop(prizeCount),
            mulligans = mulligans,
            evaluation = HandEvaluator.evaluate(opening, keyCardNames, turnOrder, prizes),
            prizes = prizes,
            turnOrder = turnOrder
        )
    }

    /** Rimescola tutto e riparte, contando un mulligan in piu'. Chi inizia non cambia. */
    fun mulligan(
        current: PracticeHand,
        pool: List<SimulatorCard>,
        keyCardNames: Collection<String> = emptyList(),
        random: Random = Random.Default
    ): PracticeHand? = deal(pool, keyCardNames, current.mulligans + 1, current.turnOrder, random)

    /** Pesca la prima carta della [PracticeHand.library]. */
    fun drawOne(
        current: PracticeHand,
        keyCardNames: Collection<String> = emptyList()
    ): PracticeHand {
        val next = current.library.firstOrNull() ?: return current
        val draws = current.draws + next

        return current.copy(
            draws = draws,
            library = current.library.drop(1),
            evaluation = HandEvaluator.evaluate(
                current.opening + draws,
                keyCardNames,
                orderForTurn(current.turnOrder, draws.size),
                current.prizes
            )
        )
    }

    /** Stessa mano, giudicata per l'altro ordine di gioco. */
    fun withTurnOrder(
        current: PracticeHand,
        turnOrder: TurnOrder,
        keyCardNames: Collection<String> = emptyList()
    ): PracticeHand = current.copy(
        turnOrder = turnOrder,
        evaluation = HandEvaluator.evaluate(
            current.cards,
            keyCardNames,
            orderForTurn(turnOrder, current.draws.size),
            current.prizes
        )
    )

    /**
     * L'ordine che conta per giudicare la mano al turno [turn] (0 = apertura,
     * 1 = primo turno). Dal secondo turno anche chi ha iniziato gioca gli
     * Aiuti: da li' l'ordine non cambia piu' niente, e non si passa.
     */
    internal fun orderForTurn(order: TurnOrder, turn: Int): TurnOrder? =
        if (turn >= 2) null else order
}
