package com.example.pokevault.util

object Constants {
    // Firebase Collections
    const val USERS_COLLECTION = "users"
    const val CARDS_COLLECTION = "cards"

    // PokéTCG API
    const val POKETCG_BASE_URL = "https://api.pokemontcg.io/v2/"

    // Tipi Pokémon con colori (hex)
    val POKEMON_TYPES = mapOf(
        "Fuoco" to "#EF4444",
        "Acqua" to "#3B82F6",
        "Erba" to "#22C55E",
        "Elettro" to "#EAB308",
        "Psico" to "#8B5CF6",
        "Lotta" to "#F97316",
        "Buio" to "#6366F1",
        "Metallo" to "#6B7280",
        "Drago" to "#7C3AED",
        "Folletto" to "#EC4899",
        "Normale" to "#9CA3AF",
        "Incolore" to "#D1D5DB"
    )

    // Rarità carte
    val CARD_RARITIES = listOf(
        "Common",
        "Uncommon",
        "Rare",
        "Holo Rare",
        "Ultra Rare",
        "Secret Rare",
        "Amazing Rare",
        "Full Art",
        "Alt Art",
        "Rainbow Rare",
        "Gold Rare"
    )

    // Condizioni carta
    val CARD_CONDITIONS = listOf(
        "Mint (M)",
        "Near Mint (NM)",
        "Excellent (EX)",
        "Good (GD)",
        "Light Played (LP)",
        "Played (PL)",
        "Poor (PR)"
    )
}
