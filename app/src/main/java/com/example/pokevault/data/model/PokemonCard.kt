package com.example.pokevault.data.model

import com.google.firebase.Timestamp

data class PokemonCard(
    val id: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val set: String = "",
    val rarity: String = "",
    val type: String = "",
    val hp: Int = 0,
    val isGraded: Boolean = false,
    val grade: Float? = null,
    val estimatedValue: Double = 0.0,
    val quantity: Int = 1,
    val condition: String = "Near Mint (NM)",
    val notes: String = "",
    val addedAt: Timestamp? = null,
    // Nuovi campi per collegamento API
    val apiCardId: String = "",      // ID dalla PokéTCG API (es. "sv6-001")
    val cardNumber: String = ""       // Numero carta nel set (es. "001/142")
)

data class MenuSection(
    val title: String,
    val icon: String,
    val route: String,
    val badgeCount: Int = 0
)
