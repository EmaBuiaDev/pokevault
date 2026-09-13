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
    val metaShare: Double,          // Percentuale meta share (0-100)
    val avgWinrate: Double,         // Win rate medio (0-1)
    val topPlacement: Int,          // Miglior piazzamento
    val recentResults: List<Int>,   // Ultimi piazzamenti (per trend)
    val sampleDeck: MetaDeck?       // Un deck di esempio per import
)
