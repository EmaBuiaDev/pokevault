package com.emabuia.pokevault.data.model

import com.google.firebase.Timestamp

data class Tournament(
    val id: String = "",
    val location: String = "",
    val date: Timestamp? = null,
    val participants: Int = 0,
    val registrationFee: Double = 0.0,
    val type: String = "",              // Challenge, Cup, Local
    val format: String = "",            // Standard, Expanded, GLC, ecc.
    val deckName: String = "",          // Nome mazzo scelto
    val deckId: String = "",            // Riferimento al deck dell'utente (opzionale)
    val createdAt: Timestamp? = null
) {
    companion object {
        val TYPES = listOf("Challenge", "Cup", "Local")
        val FORMATS = listOf("Standard", "Expanded", "GLC", "Unlimited", "Theme", "Other")
    }
}
