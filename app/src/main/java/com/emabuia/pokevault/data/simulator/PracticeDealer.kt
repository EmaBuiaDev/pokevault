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

    /**
     * Le 7 carte non hanno Pokemon Base: per regolamento si rivelano e si
     * rimescolano. Non e' una scelta, e finche' non si fa non si pesca.
     */
    val needsMulligan: Boolean get() = opening.none { it.isBasic }

    val canDraw: Boolean get() = library.isNotEmpty() && !needsMulligan

    /**
     * Il giudizio sulle sole 7 carte iniziali, qualunque cosa sia stata pescata
     * dopo: e' la mano che si salva e si vota.
     */
    fun openingEvaluation(keyCardNames: Collection<String> = emptyList()): HandEvaluation =
        HandEvaluator.evaluate(opening, keyCardNames, turnOrder, prizes)
}

/**
 * Com'e' andata la sessione di prova.
 *
 * Fino alla 3.1.8 contava le mani "tenute" e quelle "rimescolate" per scelta:
 * ma il regolamento non permette di rimescolare una mano che ha un Pokemon
 * Base, quindi quel numero non corrispondeva a niente del gioco. Ora conta le
 * partite iniziate, i mulligan obbligati e come partivano le mani.
 */
data class PracticeTally(
    /** Mani valide viste, cioe' partite iniziate. */
    val games: Int = 0,
    /** Mulligan obbligati, in tutto. */
    val mulligans: Int = 0,
    /** Partite iniziate con una mano ottima o giocabile. */
    val goodStarts: Int = 0,
    /**
     * Le mani valutate dall'utente, per id della mano salvata: vero se il voto
     * concorda con l'app sulla domanda "parte bene?". Una mappa e non due
     * contatori perche' il voto si puo' cambiare o togliere.
     */
    val agreements: Map<String, Boolean> = emptyMap()
) {
    val rated: Int get() = agreements.size
    val agreed: Int get() = agreements.values.count { it }

    /** Registra (o aggiorna) il voto dell'utente su una mano. */
    fun withRating(savedId: String, user: HandRating, app: HandRating): PracticeTally =
        copy(agreements = agreements + (savedId to (user.startsWell == app.startsWell)))

    /** Il voto tolto: la mano non conta piu'. */
    fun withoutRating(savedId: String): PracticeTally = copy(agreements = agreements - savedId)

    /** Percentuale di partenze buone, null finche' non si e' iniziata una partita. */
    val goodStartRate: Int?
        get() = if (games == 0) null else (goodStarts * 100) / games

    /** Conta una mano nuova appena scoperta: valida (una partita) o da rimescolare. */
    fun record(hand: PracticeHand): PracticeTally = when {
        hand.needsMulligan -> this
        else -> copy(
            games = games + 1,
            goodStarts = goodStarts + if (hand.evaluation.verdict.isGoodStart) 1 else 0
        )
    }

    fun recordMulligan(): PracticeTally = copy(mulligans = mulligans + 1)
}

private val HandVerdict.isGoodStart: Boolean
    get() = this == HandVerdict.GREAT || this == HandVerdict.PLAYABLE

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
        if (current.needsMulligan) return current
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
