package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.firebase.CollectionStats
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.util.AppLocale
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class StatsUiState(
    val stats: CollectionStats = CollectionStats(),
    val cards: List<PokemonCard> = emptyList(),
    val isLoading: Boolean = true,
    // Distribuzioni calcolate
    val cardsBySet: List<Pair<String, Int>> = emptyList(),
    val cardsByRarity: List<Pair<String, Int>> = emptyList(),
    val cardsByType: List<Pair<String, Int>> = emptyList(),
    val gradedCount: Int = 0,
    val averageValue: Double = 0.0
)

class StatsViewModel : ViewModel() {

    private val repository = FirestoreRepository()

    var uiState by mutableStateOf(StatsUiState())
        private set

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getCards()
                .catch { uiState = uiState.copy(isLoading = false) }
                .collect { cards ->
                    val stats = repository.getCollectionStats()
                    val totalCards = cards.sumOf { it.quantity }

                    val bySet = cards.groupBy { it.set.ifBlank { AppLocale.unknown } }
                        .mapValues { (_, v) -> v.sumOf { it.quantity } }
                        .entries.sortedByDescending { it.value }
                        .map { it.key to it.value }

                    val byRarity = cards.groupBy {
                            AppLocale.translateRarity(it.rarity).ifBlank { AppLocale.unknown }
                        }
                        .mapValues { (_, v) -> v.sumOf { it.quantity } }
                        .entries.sortedByDescending { it.value }
                        .map { it.key to it.value }

                    val byType = cards.groupBy {
                            AppLocale.translateType(it.type).ifBlank { AppLocale.other }
                        }
                        .mapValues { (_, v) -> v.sumOf { it.quantity } }
                        .entries.sortedByDescending { it.value }
                        .map { it.key to it.value }

                    uiState = uiState.copy(
                        stats = stats,
                        cards = cards,
                        isLoading = false,
                        cardsBySet = bySet,
                        cardsByRarity = byRarity,
                        cardsByType = byType,
                        gradedCount = cards.count { it.isGraded },
                        averageValue = if (totalCards > 0) stats.totalValue / totalCards else 0.0
                    )
                }
        }
    }
}
