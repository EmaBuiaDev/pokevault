package com.emabuia.pokevault.data.model

import com.google.firebase.Timestamp

enum class GoalCriteriaType {
    SET,        // tutte le carte di un set specifico
    RARITY,     // tutte le carte di una rarità specifica
    SUPERTYPE,  // Pokémon / Trainer / Energy
    TYPE,       // Tipo Pokémon (Fire, Water, ecc.)
    CUSTOM      // selezione manuale da catalogo TCG API
}

data class GoalAlbum(
    val id: String = "",
    val name: String = "",
    val criteriaType: GoalCriteriaType = GoalCriteriaType.SET,
    val criteriaValue: String = "",          // es. "sv1", "Rare Holo EX", "Fire"
    val targetCardApiIds: List<String> = emptyList(), // api ids fetchati al momento della creazione
    val createdAt: Timestamp? = null
)
