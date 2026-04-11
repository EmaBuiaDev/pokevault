package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.CollectionStats
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.PokeTcgRepository
import com.emabuia.pokevault.util.AppLocale
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class SetCompletion(
    val setName: String,
    val ownedUnique: Int,
    val totalCards: Int,
    val symbolUrl: String? = null
) {
    val percentage: Float get() = if (totalCards > 0) ownedUnique.toFloat() / totalCards else 0f
}

data class StatsUiState(
    val stats: CollectionStats = CollectionStats(),
    val cards: List<PokemonCard> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    // Distribuzioni calcolate
    val cardsBySet: List<Pair<String, Int>> = emptyList(),
    val setCompletions: List<SetCompletion> = emptyList(),
    val cardsByRarity: List<Pair<String, Int>> = emptyList(),
    val cardsByType: List<Pair<String, Int>> = emptyList(),
    val gradedCount: Int = 0,
    val averageValue: Double = 0.0
)

class StatsViewModel : ViewModel() {

    private val repository = FirestoreRepository()
    private val tcgRepository = PokeTcgRepository()

    var uiState by mutableStateOf(StatsUiState())
        private set

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getCards()
                .catch { e ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Errore sconosciuto"
                    )
                }
                .collect { cards ->
                    val stats = repository.getCollectionStats()
                    val totalCards = cards.sumOf { it.quantity }

                    val bySet = cards.groupBy { it.set.ifBlank { AppLocale.unknown } }
                        .mapValues { (_, v) -> v.sumOf { it.quantity } }
                        .entries.sortedByDescending { it.value }
                        .map { it.key to it.value }

                    // Calcolo completamento set
                    val setsResult = tcgRepository.getSets()
                    val allSets = setsResult.getOrNull() ?: emptyList()
                    
                    val completions = cards.filter { it.set.isNotBlank() }
                        .groupBy { it.set }
                        .map { (setName, setCards) ->
                            val uniqueOwned = setCards.map { it.apiCardId }.distinct().count { it.isNotBlank() }
                            val tcgSet = allSets.find { it.name == setName }
                            val totalInSet = tcgSet?.total ?: 0
                            SetCompletion(setName, uniqueOwned, totalInSet, tcgSet?.images?.symbol)
                        }
                        .filter { it.totalCards > 0 }
                        .sortedByDescending { it.percentage }

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
                        setCompletions = completions,
                        cardsByRarity = byRarity,
                        cardsByType = byType,
                        gradedCount = cards.count { it.isGraded },
                        averageValue = if (totalCards > 0) stats.totalValue / totalCards else 0.0
                    )
                }
        }
    }
}
