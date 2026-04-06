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
