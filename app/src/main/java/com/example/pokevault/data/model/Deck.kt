package com.example.pokevault.data.model

import com.google.firebase.Timestamp

data class Deck(
    val id: String = "",
    val name: String = "",
    val cards: List<String> = emptyList(), // List of card IDs (from Firestore)
    val createdAt: Timestamp? = null,
    val mainTypes: List<String> = emptyList(),
    val averageHp: Double = 0.0,
    val totalCards: Int = 0,
    val recommendedEnergy: List<String> = emptyList(),
    val coverImageUrl: String = ""
)

data class DeckAnalysis(
    val typesCount: Map<String, Int> = emptyMap(),
    val commonWeaknesses: List<String> = emptyList(),
    val averageHp: Double = 0.0,
    val recommendedEnergy: List<String> = emptyList(),
    val synergies: List<String> = emptyList(),
    val supertypesCount: Map<String, Int> = emptyMap()
)
