package com.emabuia.pokevault.data.model

import com.google.firebase.Timestamp

data class Album(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val pokemonType: String = "",       // Filtro per tipo Pokémon (Fire, Water, etc.) - vuoto = tutti
    val expansion: String = "",         // Filtro per espansione specifica - vuoto = tutte
    val supertype: String = "",         // Filtro per categoria (Pokémon, Trainer, Energy) - vuoto = tutti
    val size: Int = 9,                  // Grandezza album (9, 18, 36, 72, 120)
    val theme: String = "classic",      // Tematica visuale (classic, fire, water, grass, electric, dark, psychic)
    val cardIds: List<String> = emptyList(), // ID delle carte nell'album
    val coverImageUrl: String = "",
    val createdAt: Timestamp? = null
)
