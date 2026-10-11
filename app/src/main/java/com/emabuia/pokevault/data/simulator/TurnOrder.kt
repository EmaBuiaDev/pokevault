package com.emabuia.pokevault.data.simulator

/**
 * Chi gioca il primo turno della partita.
 *
 * Cambia cosa si puo' fare con la mano al primo turno (regolamento del GCC
 * Pokemon, da Sole e Luna in poi):
 * - tutti e due pescano una carta all'inizio del proprio primo turno;
 * - chi inizia per primo non puo' giocare carte Aiuto ne' attaccare in quel
 *   turno; chi va per secondo puo' fare tutto.
 *
 * Per questo una mano che "parte" solo grazie a un Aiuto e' ottima andando per
 * secondi e lenta andando per primi: puo' usare solo gli Strumenti (e gli
 * Stadi) fino al proprio secondo turno.
 */
enum class TurnOrder { FIRST, SECOND }

/** Regole del primo turno, in un posto solo. */
object FirstTurnRules {

    /** Carte Premio messe da parte dopo la mano iniziale. */
    const val PRIZE_CARDS = 6

    /** Carte della mano iniziale. */
    const val OPENING_HAND = 7

    /**
     * Si puo' usare questa carta, come carta che pesca o cerca, al proprio
     * primo turno? Gli Aiuti no se si inizia per primi, tranne quelli che lo
     * dicono sulla carta (vedi [SimulatorCard.playableFirstTurnGoingFirst]).
     */
    fun isUsableOutOnFirstTurn(card: SimulatorCard, order: TurnOrder): Boolean {
        if (!card.isOutCard) return false
        if (order == TurnOrder.SECOND) return true
        return !card.isSupporter || card.playableFirstTurnGoingFirst
    }
}
