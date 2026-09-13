package com.emabuia.pokevault.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ── Modelli risposta LimitlessTCG API ──

data class LimitlessTournament(
    val id: String = "",
    val game: String = "",
    val format: String = "",
    val name: String = "",
    val date: String = "",
    val players: Int = 0
)

/**
 * Rappresenta un giocatore nei risultati di un torneo.
 * "player" è lo username, "name" è il nome visualizzato.
 * La decklist può arrivare in formati diversi (mappa per categoria o lista piatta).
 */
data class LimitlessStanding(
    val player: String = "",
    val name: String = "",
    val placing: Int = 0,
    val record: LimitlessRecord? = null,
    val decklist: Any? = null, // Può essere Map<String,List> o List - parsing flessibile
    val deck: LimitlessDeckInfo? = null,
    val country: String? = null
)

data class LimitlessRecord(
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0
)

data class LimitlessDeckInfo(
    val name: String? = null,
    val icons: Any? = null // Può essere List<String> o List<Object> - struttura variabile
)

/**
 * L'organizzatore del torneo, dal solo endpoint `/details`.
 *
 * Serve a dire *chi* ha organizzato un evento: la lista dei tornei espone solo
 * un `organizerId` numerico, che da solo non significa niente per chi legge.
 */
data class LimitlessOrganizer(
    val id: Int = 0,
    val name: String = "",
    val logo: String? = null
)

/**
 * Il dettaglio di un torneo.
 *
 * Esiste per un campo solo: [isOnline]. L'endpoint `/tournaments` non lo
 * espone, quindi finora l'app non aveva modo di distinguere un Regional in
 * presenza da una serata su PTCG Live, e la sezione Win Tournament mescolava
 * le due cose senza dirlo.
 */
data class LimitlessTournamentDetails(
    val id: String = "",
    val game: String = "",
    val format: String = "",
    val name: String = "",
    val date: String = "",
    val players: Int = 0,
    val organizer: LimitlessOrganizer? = null,
    /** false quando il torneo si e' giocato di persona. */
    val isOnline: Boolean? = null,
    val isPublic: Boolean? = null,
    val decklists: Boolean? = null
)

// ── Retrofit Interface ──

interface LimitlessTcgApiService {

    @GET("tournaments")
    suspend fun getTournaments(
        @Query("game") game: String = "PTCG",
        @Query("format") format: String = "standard",
        @Query("limit") limit: Int = 10,
        @Query("page") page: Int = 1
    ): List<LimitlessTournament>

    @GET("tournaments/{id}/details")
    suspend fun getTournamentDetails(
        @Path("id") tournamentId: String
    ): LimitlessTournamentDetails

    @GET("tournaments/{id}/standings")
    suspend fun getTournamentStandings(
        @Path("id") tournamentId: String
    ): List<LimitlessStanding>
}
