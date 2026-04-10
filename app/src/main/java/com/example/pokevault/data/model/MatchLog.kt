package com.example.pokevault.data.model

import com.google.firebase.Timestamp

data class MatchLog(
    val id: String = "",
    val tournamentId: String = "",      // Riferimento al torneo
    val round: Int = 0,                 // Numero turno
    val result: String = "",            // W, L, T (Win, Loss, Tie)
    val opponentName: String = "",      // Nome avversario
    val opponentDeck: String = "",      // Mazzo avversario
    val notes: String = "",             // Note, tech, matchup, sensazioni
    val createdAt: Timestamp? = null
) {
    companion object {
        val RESULTS = listOf("W", "L", "T")
    }
}
