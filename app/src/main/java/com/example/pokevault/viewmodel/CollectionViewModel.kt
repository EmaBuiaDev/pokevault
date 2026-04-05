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

data class CollectionUiState(
    val cards: List<PokemonCard> = emptyList(),
    val filteredCards: List<PokemonCard> = emptyList(),
    val stats: CollectionStats = CollectionStats(),
    val isLoading: Boolean = true,
    val isGridView: Boolean = true,
    val searchQuery: String = "",
    val selectedType: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class CollectionViewModel : ViewModel() {

    private val repository = FirestoreRepository()

    var uiState by mutableStateOf(CollectionUiState())
        private set

    init {
        loadCards()
    }

    private fun loadCards() {
        viewModelScope.launch {
            repository.getCards()
                .catch { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "Errore: ${error.message}"
                    )
                }
                .collect { cards ->
                    // Ricalcola statistiche localmente per reattività immediata
                    val newStats = CollectionStats(
                        totalCards = cards.sumOf { it.quantity },
                        uniqueCards = cards.map { it.apiCardId.ifBlank { "${it.name}_${it.set}_${it.cardNumber}" } }.toSet().size,
                        totalValue = cards.sumOf { it.estimatedValue * it.quantity }
                    )
                    
                    uiState = uiState.copy(
                        cards = cards,
                        filteredCards = applyFilters(cards, uiState.searchQuery, uiState.selectedType),
                        stats = newStats,
                        isLoading = false
                    )
                }
        }
    }

    fun updateSearchQuery(query: String) {
        uiState = uiState.copy(
            searchQuery = query,
            filteredCards = applyFilters(uiState.cards, query, uiState.selectedType)
        )
    }

    fun filterByType(setName: String?) {
        uiState = uiState.copy(
            selectedType = setName,
            filteredCards = applyFilters(uiState.cards, uiState.searchQuery, setName)
        )
    }

    fun toggleViewMode() {
        uiState = uiState.copy(isGridView = !uiState.isGridView)
    }

    fun deleteCard(cardId: String) {
        viewModelScope.launch {
            repository.deleteCard(cardId)
                .onSuccess {
                    uiState = uiState.copy(successMessage = "Carta eliminata")
                }
                .onFailure { error ->
                    uiState = uiState.copy(errorMessage = "Errore: ${error.message}")
                }
        }
    }

    fun clearMessages() {
        uiState = uiState.copy(errorMessage = null, successMessage = null)
    }

    private fun applyFilters(
        cards: List<PokemonCard>,
        query: String,
        setFilter: String?
    ): List<PokemonCard> {
        return cards.filter { card ->
            val matchesQuery = query.isBlank() ||
                card.name.contains(query, ignoreCase = true) ||
                card.set.contains(query, ignoreCase = true) ||
                card.rarity.contains(query, ignoreCase = true)
            val matchesSet = setFilter == null || card.set == setFilter
            matchesQuery && matchesSet
        }
    }
}
