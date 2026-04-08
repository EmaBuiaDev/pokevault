package com.example.pokevault.data.model

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
