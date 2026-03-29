package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.pokevault.data.model.PokemonCard

class HomeViewModel : ViewModel() {

    var searchQuery by mutableStateOf("")
        private set

    var isGridView by mutableStateOf(true)
        private set

    // Carte di esempio per la collezione
    var collectionCards by mutableStateOf(
        listOf(
            PokemonCard(
                id = "1",
                name = "Charizard",
                type = "Fuoco",
                hp = 180,
                set = "Base Set",
                rarity = "Holo Rare"
            ),
            PokemonCard(
                id = "2",
                name = "Pikachu",
                type = "Elettro",
                hp = 60,
                set = "Base Set",
                rarity = "Common"
            ),
            PokemonCard(
                id = "3",
                name = "Mewtwo",
                type = "Psico",
                hp = 150,
                set = "Base Set",
                rarity = "Holo Rare"
            ),
            PokemonCard(
                id = "4",
                name = "Blastoise",
                type = "Acqua",
                hp = 170,
                set = "Base Set",
                rarity = "Holo Rare"
            )
        )
    )
        private set

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun toggleViewMode() {
        isGridView = !isGridView
    }

    fun getFilteredCards(): List<PokemonCard> {
        return if (searchQuery.isBlank()) {
            collectionCards
        } else {
            collectionCards.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.type.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    fun getTotalCards(): Int = collectionCards.size

    fun getTotalValue(): Double = collectionCards.sumOf { it.estimatedValue }
}
