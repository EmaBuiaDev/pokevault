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
        loadStats()
    }

    private fun loadCards() {
        viewModelScope.launch {
            repository.getCards()
                .catch { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "Errore caricamento: ${error.message}"
                    )
                }
                .collect { cards ->
                    uiState = uiState.copy(
                        cards = cards,
                        filteredCards = applyFilters(cards, uiState.searchQuery, uiState.selectedType),
                        isLoading = false
                    )
                }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val stats = repository.getCollectionStats()
            uiState = uiState.copy(stats = stats)
        }
    }

    fun updateSearchQuery(query: String) {
        uiState = uiState.copy(
            searchQuery = query,
            filteredCards = applyFilters(uiState.cards, query, uiState.selectedType)
        )
    }

    fun filterByType(type: String?) {
        uiState = uiState.copy(
            selectedType = type,
            filteredCards = applyFilters(uiState.cards, uiState.searchQuery, type)
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
                    loadStats()
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
        type: String?
    ): List<PokemonCard> {
        return cards.filter { card ->
            val matchesQuery = query.isBlank() ||
                card.name.contains(query, ignoreCase = true) ||
                card.set.contains(query, ignoreCase = true)
            val matchesType = type == null || card.type == type
            matchesQuery && matchesType
        }
    }
}
