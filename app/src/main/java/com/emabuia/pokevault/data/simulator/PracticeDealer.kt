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
    val evaluation: HandEvaluation
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

    const val OPENING_SIZE = 7

    /**
     * Mescola il mazzo e scopre sette carte.
     *
     * Non applica il mulligan automatico: in Prova la mano senza Basic va
     * *vista*, e' il momento in cui si capisce perche' il mazzo brickava.
     */
    fun deal(
        pool: List<SimulatorCard>,
        keyCardNames: Collection<String> = emptyList(),
        mulligans: Int = 0,
        random: Random = Random.Default
    ): PracticeHand? {
        if (pool.size < OPENING_SIZE) return null

        val shuffled = pool.shuffled(random)
        val opening = shuffled.take(OPENING_SIZE)

        return PracticeHand(
            opening = opening,
            draws = emptyList(),
            library = shuffled.drop(OPENING_SIZE),
            mulligans = mulligans,
            evaluation = HandEvaluator.evaluate(opening, keyCardNames)
        )
    }

    /** Rimescola tutto e riparte, contando un mulligan in piu'. */
    fun mulligan(
        current: PracticeHand,
        pool: List<SimulatorCard>,
        keyCardNames: Collection<String> = emptyList(),
        random: Random = Random.Default
    ): PracticeHand? = deal(pool, keyCardNames, current.mulligans + 1, random)

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
            evaluation = HandEvaluator.evaluate(current.opening + draws, keyCardNames)
        )
    }
}
