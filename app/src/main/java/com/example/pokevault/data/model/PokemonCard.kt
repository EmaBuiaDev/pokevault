package com.example.pokevault.data.model

data class PokemonCard(
    val id: String,
    val name: String,
    val imageUrl: String = "",
    val set: String = "",
    val rarity: String = "",
    val type: String = "",
    val hp: Int = 0,
    val isGraded: Boolean = false,
    val grade: Float? = null,
    val estimatedValue: Double = 0.0
)

data class MenuSection(
    val title: String,
    val icon: String,
    val route: String,
    val badgeCount: Int = 0
)
