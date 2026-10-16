package com.emabuia.pokevault.data.model

data class MetaDeck(
    val id: String,
    val archetype: String?,
    val player: String?,
    val tournament: String?,
    val tournamentId: String?,
    val date: String?,
    val placement: Int?,
    val winrate: Double?,
    val link: String?,
    val cards: List<MetaDeckCard>
)

data class MetaDeckCard(
    val name: String,
    val set: String?,
    val number: String?,
    val qty: Int,
    val type: String // "pokemon", "trainer", "energy"
)

/**
 * Rappresenta i risultati di un torneo competitivo con i top 3 piazzati.
 * Usato nella sezione "Win Tournament" per mostrare i vincitori per torneo.
 */
data class TournamentResult(
    val tournamentId: String,
    val tournamentName: String,
    val date: String?,
    val players: Int,
    val top3: List<MetaDeck>,  // Ordinati per placement (1, 2, 3)
    /**
     * false per un torneo giocato di persona, true per uno online, null
     * quando il dettaglio non e' stato recuperato.
     *
     * Finora la sezione mostrava insieme i Regional in presenza e le serate su
     * PTCG Live senza distinguerli: due cose che si leggono in modo diverso,
     * perche' un piazzamento a un evento dal vivo pesa quanto il viaggio che e'
     * costato.
     */
    val isOnline: Boolean? = null,
    val organizerName: String? = null,
    val organizerLogo: String? = null
) {
    /** Vero solo quando sappiamo con certezza che si e' giocato di persona. */
    val isLive: Boolean get() = isOnline == false
}

/** Che tipo di tornei mostrare nella sezione Win Tournament. */
enum class TournamentKind {
    ALL,
    LIVE,
    ONLINE
}

/**
 * Rappresenta un archetipo del meta competitivo,
 * aggregato da più tornei (come limitlesstcg.com/decks).
 */
data class MetaArchetype(
    val name: String,               // Nome archetipo (es. "Charizard ex")
    val count: Int,                 // Quanti deck usano questo archetipo
    /**
     * Quota dei deck nei top 32 dei tornei letti (0-100). Non e' la quota
     * dell'intero torneo: Limitless da' l'archetipo dei primi, e la schermata
     * lo dice ("nei top 32").
     */
    val metaShare: Double,
    /** Vittorie su partite giocate, sommate su tutti i deck (0-1). Vedi [games]. */
    val avgWinrate: Double,
    val topPlacement: Int,          // Miglior piazzamento
    /** I migliori piazzamenti. Non e' un andamento nel tempo: per quello c'e' [trend]. */
    val recentResults: List<Int>,
    val sampleDeck: MetaDeck?,      // Un deck di esempio per import
    // Dalla 3.1.9. Con valori predefiniti: la cache su disco scritta prima
    // non li ha, e Gson li lascerebbe a zero comunque.
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
    /** Giocatori del torneo in cui e' arrivato [topPlacement]: "1° su 128". */
    val topPlacementPlayers: Int = 0,
    /** Quante volte un deck di questo archetipo e' entrato nei primi 8. */
    val top8Count: Int = 0,
    /**
     * Punti di quota guadagnati o persi fra i tornei piu' vecchi e i piu'
     * recenti della finestra. Null se una delle due meta' non ha dati.
     */
    val trend: Double? = null,
    /** Su quanti tornei e quanti deck e' calcolato tutto: uguale per ogni archetipo. */
    val sampleTournaments: Int = 0,
    val sampleDecks: Int = 0
) {
    val games: Int get() = wins + losses + ties
}
