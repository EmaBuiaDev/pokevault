package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.CollectionStats
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.util.AppLocale
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

enum class SortOrder {
    NEWEST, PRICE_ASC, PRICE_DESC, NAME_ASC, NUMBER
}

enum class SupertypeFilter {
    ALL, POKEMON, TRAINER, ENERGY
}

data class CollectionUiState(
    val cards: List<PokemonCard> = emptyList(),
    val filteredCards: List<PokemonCard> = emptyList(),
    val stats: CollectionStats = CollectionStats(),
    val isLoading: Boolean = true,
    val isGridView: Boolean = true,
    val searchQuery: String = "",
    val selectedSet: String? = null,
    val selectedType: String? = null,
    val selectedRarity: String? = null,
    val supertypeFilter: SupertypeFilter = SupertypeFilter.ALL,
    val sortOrder: SortOrder = SortOrder.NEWEST,
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
                        filteredCards = applyFilters(cards),
                        stats = newStats,
                        isLoading = false
                    )
                }
        }
    }

    fun updateSearchQuery(query: String) {
        uiState = uiState.copy(searchQuery = query)
        refreshFilteredCards()
    }

    fun filterBySet(setName: String?) {
        uiState = uiState.copy(selectedSet = setName)
        refreshFilteredCards()
    }

    fun filterByType(type: String?) {
        uiState = uiState.copy(selectedType = type)
        refreshFilteredCards()
    }

    fun filterBySupertype(filter: SupertypeFilter) {
        uiState = uiState.copy(supertypeFilter = filter)
        refreshFilteredCards()
    }

    fun filterByRarity(rarity: String?) {
        uiState = uiState.copy(selectedRarity = rarity)
        refreshFilteredCards()
    }

    fun updateSortOrder(order: SortOrder) {
        uiState = uiState.copy(sortOrder = order)
        refreshFilteredCards()
    }

    private fun refreshFilteredCards() {
        uiState = uiState.copy(
            filteredCards = applyFilters(uiState.cards)
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

    fun deleteMultipleGroups(groupKeys: Set<String>) {
        viewModelScope.launch {
            val cardsToDelete = uiState.cards.filter { card ->
                val key = card.apiCardId.ifBlank { "${card.name}_${card.set}_${card.cardNumber}" }
                key in groupKeys
            }
            val results = cardsToDelete.map { card ->
                repository.deleteCard(card.id)
            }
            val deletedCount = results.count { it.isSuccess }
            uiState = uiState.copy(
                successMessage = "$deletedCount carte eliminate"
            )
        }
    }

    fun clearMessages() {
        uiState = uiState.copy(errorMessage = null, successMessage = null)
    }

    private fun applyFilters(cards: List<PokemonCard>): List<PokemonCard> {
        val filtered = cards.filter { card ->
            val matchesQuery = uiState.searchQuery.isBlank() ||
                card.name.contains(uiState.searchQuery, ignoreCase = true) ||
                card.set.contains(uiState.searchQuery, ignoreCase = true) ||
                card.rarity.contains(uiState.searchQuery, ignoreCase = true)
            
            val matchesSet = uiState.selectedSet == null || card.set == uiState.selectedSet
            
            // Corretto il filtraggio per tipo: traduciamo il tipo della carta prima del confronto
            val matchesType = uiState.selectedType == null || 
                AppLocale.translateType(card.type).equals(uiState.selectedType, ignoreCase = true)
            
            val matchesRarity = uiState.selectedRarity == null ||
                card.rarity.equals(uiState.selectedRarity, ignoreCase = true)

            val matchesSupertype = when (uiState.supertypeFilter) {
                SupertypeFilter.ALL -> true
                SupertypeFilter.POKEMON -> card.classify() == "Pokémon"
                SupertypeFilter.TRAINER -> card.classify() == "Trainer"
                SupertypeFilter.ENERGY -> card.classify() == "Energy"
            }

            matchesQuery && matchesSet && matchesType && matchesRarity && matchesSupertype
        }

        return when (uiState.sortOrder) {
            SortOrder.NEWEST -> filtered.reversed() // Assumendo che l'ordine originale sia cronologico (addedAt)
            SortOrder.PRICE_ASC -> filtered.sortedBy { it.estimatedValue }
            SortOrder.PRICE_DESC -> filtered.sortedByDescending { it.estimatedValue }
            SortOrder.NAME_ASC -> filtered.sortedBy { it.name }
            SortOrder.NUMBER -> filtered.sortedBy { it.cardNumber.toIntOrNull() ?: Int.MAX_VALUE }
        }
    }
}
