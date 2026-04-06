package com.example.pokevault.data.remote

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

data class LimitlessPlayer(
    val name: String = "",
    val placing: Int = 0,
    val record: LimitlessRecord? = null,
    val decklist: List<LimitlessDeckCard>? = null,
    val deck: LimitlessDeckInfo? = null,
    val country: String? = null
)

data class LimitlessRecord(
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0
)

data class LimitlessDeckCard(
    val count: Int = 0,
    val name: String = "",
    val set: String = "",
    val number: String = "",
    val type: String? = null // pokemon, trainer, energy
)

data class LimitlessDeckInfo(
    val name: String? = null,
    val icons: List<LimitlessDeckIcon>? = null
)

data class LimitlessDeckIcon(
    val name: String? = null,
    val set: String? = null,
    val number: String? = null
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

    @GET("tournaments/{id}/players")
    suspend fun getTournamentPlayers(
        @Path("id") tournamentId: String
    ): List<LimitlessPlayer>
}
