package com.example.pokevault.data.model

import com.google.firebase.Timestamp

data class MatchLog(
    val id: String = "",
    val location: String = "",          // Negozio, città, evento
    val date: Timestamp? = null,        // Quando ha giocato
    val format: String = "",            // Standard, Expanded, GLC, ecc.
    val deckName: String = "",          // Mazzo usato
    val deckList: String = "",          // Lista mazzo (opzionale)
    val result: String = "",            // W, L, T (Win, Loss, Tie)
    val opponentName: String = "",      // Nome avversario
    val opponentDeck: String = "",      // Mazzo avversario
    val notes: String = "",             // Note, tech, matchup, sensazioni
    val createdAt: Timestamp? = null
) {
    companion object {
        val FORMATS = listOf("Standard", "Expanded", "GLC", "Unlimited", "Theme", "Other")
        val RESULTS = listOf("W", "L", "T")  // Win, Loss, Tie
    }
}
