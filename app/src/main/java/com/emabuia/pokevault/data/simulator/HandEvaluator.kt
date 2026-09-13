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
    MISS_KEY_CARD
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

    fun evaluate(
        hand: List<SimulatorCard>,
        keyCardNames: Collection<String> = emptyList()
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
        if (outs > 0) strengths += HandTrait.HAS_OUT else weaknesses += HandTrait.NO_OUT
        if (supporters > 0) strengths += HandTrait.HAS_SUPPORTER
        if (normalizedKeys.isNotEmpty()) {
            if (keyCardsFound.isNotEmpty()) strengths += HandTrait.HAS_KEY_CARD
            else weaknesses += HandTrait.MISS_KEY_CARD
        }

        val verdict = if (basics == 0) {
            HandVerdict.MULLIGAN
        } else {
            // L'out (pescata o ricerca) vale doppio: e' l'unica carta che puo'
            // rimediare a tutto il resto della mano, mentre un'energia in piu'
            // non trova ne' Basic ne' supporter.
            var score = if (basics >= 2) 2 else 1
            if (energies > 0) score += 1
            if (outs > 0) score += 2
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
