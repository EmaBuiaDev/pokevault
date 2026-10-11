package com.emabuia.pokevault.data.simulator

/**
 * Verdetto su una singola mano.
 *
 * La simulazione statistica risponde a "ogni quanto va bene"; la modalita'
 * Prova risponde a "questa qui, la tengo?". Sono due domande diverse e questa
 * e' quella che il giocatore si fa davvero al tavolo, per cui il verdetto deve
 * essere una parola sola e leggibile a colpo d'occhio.
 */
enum class HandVerdict {
    /** Nessun Basic: la mano non e' giocabile, si rimescola per regolamento. */
    MULLIGAN,

    /** Legale ma povera: parte, e poi si spera. */
    RISKY,

    /** Parte e ha almeno una risorsa per continuare. */
    PLAYABLE,

    /** Piu' Basic, energia e una carta che pesca o cerca. */
    GREAT
}

/**
 * Perche' la mano e' stata giudicata cosi'.
 *
 * Sono motivi e non punteggi: la UI li mostra come riga sotto al verdetto,
 * quindi devono essere pochi e ciascuno traducibile in una frase breve.
 */
enum class HandTrait {
    NO_BASIC,
    ONE_BASIC,
    MULTI_BASIC,
    NO_ENERGY,
    HAS_ENERGY,
    NO_OUT,
    HAS_OUT,
    HAS_SUPPORTER,
    HAS_KEY_CARD,
    MISS_KEY_CARD,

    /**
     * Iniziando per primi: le uniche carte che pescano o cercano sono Aiuti,
     * che al primo turno non si possono giocare. La mano si sblocca solo al
     * secondo turno.
     */
    SUPPORTER_LOCKED_T1,

    /** Una copia di una carta chiave e' fra i Premi. */
    KEY_CARD_PRIZED
}

data class HandEvaluation(
    val verdict: HandVerdict,
    val basics: Int,
    val energies: Int,
    val outs: Int,
    val supporters: Int,
    val keyCardsFound: List<String>,
    /** Cio' che la mano ha. */
    val strengths: List<HandTrait>,
    /** Cio' che le manca. */
    val weaknesses: List<HandTrait>
)

object HandEvaluator {

    /**
     * Soglie del verdetto, sui punti accumulati sotto.
     *
     * Il massimo raggiungibile e' 6 (2 Basic + energia + out + supporter + key
     * card), per cui GREAT a 5 vuol dire "quasi tutto", PLAYABLE a 3 "parte e
     * ha una risorsa".
     */
    private const val SCORE_GREAT = 5
    private const val SCORE_PLAYABLE = 3

    /**
     * @param order chi inizia. Null = non lo si sa ancora: si giudica come
     *   prima, contando come utile ogni carta che pesca o cerca.
     * @param prizes le carte Premio della mano, per dire se una carta chiave
     *   e' finita li'.
     */
    fun evaluate(
        hand: List<SimulatorCard>,
        keyCardNames: Collection<String> = emptyList(),
        order: TurnOrder? = null,
        prizes: List<SimulatorCard> = emptyList()
    ): HandEvaluation {
        val normalizedKeys = keyCardNames
            .asSequence()
            .map { it.lowercase().trim() }
            .filter { it.isNotBlank() }
            .toSet()

        val basics = hand.count { it.isBasic }
        val energies = hand.count { it.isEnergy }
        val supporters = hand.count { it.isSupporter }
        val outs = hand.count { it.isOutCard }
        // Le carte che pescano o cercano e che si possono giocare al primo
        // turno: iniziando per primi gli Aiuti no (tranne Rubra).
        val usableOuts = if (order == null) outs
        else hand.count { FirstTurnRules.isUsableOutOnFirstTurn(it, order) }
        val keyCardsFound = hand
            .filter { normalizedKeys.contains(it.name.lowercase().trim()) }
            .map { it.name }
            .distinct()

        val strengths = mutableListOf<HandTrait>()
        val weaknesses = mutableListOf<HandTrait>()

        when {
            basics == 0 -> weaknesses += HandTrait.NO_BASIC
            basics == 1 -> strengths += HandTrait.ONE_BASIC
            else -> strengths += HandTrait.MULTI_BASIC
        }
        if (energies > 0) strengths += HandTrait.HAS_ENERGY else weaknesses += HandTrait.NO_ENERGY
        when {
            usableOuts > 0 -> strengths += HandTrait.HAS_OUT
            outs > 0 -> weaknesses += HandTrait.SUPPORTER_LOCKED_T1
            else -> weaknesses += HandTrait.NO_OUT
        }
        if (supporters > 0) strengths += HandTrait.HAS_SUPPORTER
        if (normalizedKeys.isNotEmpty()) {
            if (keyCardsFound.isNotEmpty()) strengths += HandTrait.HAS_KEY_CARD
            else weaknesses += HandTrait.MISS_KEY_CARD
        }
        if (normalizedKeys.isNotEmpty() && prizes.any { normalizedKeys.contains(it.name.lowercase().trim()) }) {
            weaknesses += HandTrait.KEY_CARD_PRIZED
        }

        val verdict = if (basics == 0) {
            HandVerdict.MULLIGAN
        } else {
            // L'out (pescata o ricerca) vale doppio: e' l'unica carta che puo'
            // rimediare a tutto il resto della mano, mentre un'energia in piu'
            // non trova ne' Basic ne' supporter.
            var score = if (basics >= 2) 2 else 1
            if (energies > 0) score += 1
            // Un Aiuto bloccato al primo turno vale meta': la mano si
            // sblocca, ma un turno dopo.
            if (usableOuts > 0) score += 2 else if (outs > 0) score += 1
            if (keyCardsFound.isNotEmpty()) score += 1

            when {
                score >= SCORE_GREAT -> HandVerdict.GREAT
                score >= SCORE_PLAYABLE -> HandVerdict.PLAYABLE
                else -> HandVerdict.RISKY
            }
        }

        return HandEvaluation(
            verdict = verdict,
            basics = basics,
            energies = energies,
            outs = outs,
            supporters = supporters,
            keyCardsFound = keyCardsFound,
            strengths = strengths,
            weaknesses = weaknesses
        )
    }
}
