package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.firebase.CollectionStats
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.PokemonCard
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val repository = FirestoreRepository()

    var searchQuery by mutableStateOf("")
        private set

    var collectionCards by mutableStateOf<List<PokemonCard>>(emptyList())
        private set

    var stats by mutableStateOf(CollectionStats())
        private set

    var isLoading by mutableStateOf(true)
        private set

    init {
        loadCards()
        loadStats()
    }

    private fun loadCards() {
        viewModelScope.launch {
            repository.getCards()
                .catch { isLoading = false }
                .collect { cards ->
                    collectionCards = cards
                    isLoading = false
                }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            stats = repository.getCollectionStats()
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun getFilteredCards(): List<PokemonCard> {
        return if (searchQuery.isBlank()) {
            collectionCards
        } else {
            collectionCards.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.set.contains(searchQuery, ignoreCase = true)
            }
        }
    }
}
