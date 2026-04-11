package com.emabuia.pokevault.util

object Constants {
    // Firebase Collections
    const val USERS_COLLECTION = "users"
    const val CARDS_COLLECTION = "cards"

    // PokéTCG API
    const val POKETCG_BASE_URL = "https://api.pokemontcg.io/v2/"

    // Tipi Pokémon con colori (hex) - chiavi localizzate
    val POKEMON_TYPES: Map<String, String>
        get() = AppLocale.getTypes().zip(
            listOf(
                "#EF4444", "#3B82F6", "#22C55E", "#EAB308", "#8B5CF6", "#F97316",
                "#6366F1", "#6B7280", "#7C3AED", "#EC4899", "#9CA3AF", "#D1D5DB"
            )
        ).toMap()

    // Rarità carte - localizzate
    val CARD_RARITIES: List<String>
        get() = AppLocale.getRarities()

    // Condizioni carta - localizzate
    val CARD_CONDITIONS: List<String>
        get() = AppLocale.getConditions()
}
