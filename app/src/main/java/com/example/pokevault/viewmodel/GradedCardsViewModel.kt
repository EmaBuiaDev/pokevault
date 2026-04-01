package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.PokemonCard
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class GradedCardsUiState(
    val allCards: List<PokemonCard> = emptyList(),
    val filteredCards: List<PokemonCard> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedCompany: String? = null,
    val totalGraded: Int = 0,
    val totalValue: Double = 0.0,
    val averageGrade: Float = 0f,
    val companyCounts: Map<String, Int> = emptyMap()
)

class GradedCardsViewModel : ViewModel() {

    private val repository = FirestoreRepository()

    var uiState by mutableStateOf(GradedCardsUiState())
        private set

    init {
        loadGradedCards()
    }

    private fun loadGradedCards() {
        viewModelScope.launch {
            repository.getCards()
                .catch { uiState = uiState.copy(isLoading = false) }
                .collect { cards ->
                    val graded = cards.filter { it.isGraded }
                    val grades = graded.mapNotNull { it.grade }
                    val companyCounts = graded
                        .groupBy { it.gradingCompany.ifBlank { "N/D" } }
                        .mapValues { it.value.size }

                    uiState = uiState.copy(
                        allCards = graded,
                        filteredCards = applyFilters(graded, uiState.searchQuery, uiState.selectedCompany),
                        isLoading = false,
                        totalGraded = graded.size,
                        totalValue = graded.sumOf { it.estimatedValue * it.quantity },
                        averageGrade = if (grades.isNotEmpty()) grades.average().toFloat() else 0f,
                        companyCounts = companyCounts
                    )
                }
        }
    }

    fun updateSearch(query: String) {
        uiState = uiState.copy(
            searchQuery = query,
            filteredCards = applyFilters(uiState.allCards, query, uiState.selectedCompany)
        )
    }

    fun filterByCompany(company: String?) {
        uiState = uiState.copy(
            selectedCompany = company,
            filteredCards = applyFilters(uiState.allCards, uiState.searchQuery, company)
        )
    }

    private fun applyFilters(
        cards: List<PokemonCard>,
        query: String,
        company: String?
    ): List<PokemonCard> {
        return cards.filter { card ->
            val matchesQuery = query.isBlank() ||
                card.name.contains(query, ignoreCase = true) ||
                card.set.contains(query, ignoreCase = true)
            val matchesCompany = company == null || card.gradingCompany == company
            matchesQuery && matchesCompany
        }.sortedByDescending { it.grade ?: 0f }
    }
}
