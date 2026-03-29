package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.remote.PokeTcgRepository
import com.example.pokevault.data.remote.TcgCard
import com.example.pokevault.data.remote.TcgSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SetsUiState(
    val allSets: List<TcgSet> = emptyList(),
    val filteredSets: List<TcgSet> = emptyList(),
    val seriesList: List<String> = emptyList(),
    val selectedSeries: String? = null,
    val searchQuery: String = "",
    // Ricerca globale carte
    val cardSearchQuery: String = "",
    val searchedCards: List<TcgCard> = emptyList(),
    val isSearchingCards: Boolean = false,
    // Stato generale
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class SetsViewModel : ViewModel() {

    private val repository = PokeTcgRepository()
    private var searchJob: Job? = null

    var uiState by mutableStateOf(SetsUiState())
        private set

    init {
        loadSets()
    }

    private fun loadSets() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            repository.getSets()
                .onSuccess { sets ->
                    val series = sets.map { it.series }.distinct()
                    uiState = uiState.copy(
                        allSets = sets,
                        filteredSets = sets,
                        seriesList = series,
                        isLoading = false
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "Errore: ${error.message}"
                    )
                }
        }
    }

    fun updateSearch(query: String) {
        uiState = uiState.copy(searchQuery = query)
        applyFilters()
    }

    fun filterBySeries(series: String?) {
        uiState = uiState.copy(selectedSeries = series)
        applyFilters()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            repository.getSets(forceRefresh = true)
                .onSuccess { sets ->
                    val series = sets.map { it.series }.distinct()
                    uiState = uiState.copy(
                        allSets = sets,
                        seriesList = series,
                        isLoading = false
                    )
                    applyFilters()
                }
                .onFailure {
                    uiState = uiState.copy(isLoading = false)
                }
        }
    }

    // ── Ricerca globale carte con debounce ──
    fun searchCardsByName(query: String) {
        uiState = uiState.copy(cardSearchQuery = query)

        // Cancella ricerca precedente
        searchJob?.cancel()

        if (query.length < 2) {
            uiState = uiState.copy(searchedCards = emptyList(), isSearchingCards = false)
            return
        }

        searchJob = viewModelScope.launch {
            // Debounce: aspetta 500ms prima di cercare
            delay(500)
            uiState = uiState.copy(isSearchingCards = true)

            repository.searchCards(query)
                .onSuccess { cards ->
                    uiState = uiState.copy(
                        searchedCards = cards,
                        isSearchingCards = false
                    )
                }
                .onFailure {
                    uiState = uiState.copy(
                        searchedCards = emptyList(),
                        isSearchingCards = false
                    )
                }
        }
    }

    fun clearCardSearch() {
        searchJob?.cancel()
        uiState = uiState.copy(
            cardSearchQuery = "",
            searchedCards = emptyList(),
            isSearchingCards = false
        )
    }

    private fun applyFilters() {
        val filtered = uiState.allSets.filter { set ->
            val matchesSearch = uiState.searchQuery.isBlank() ||
                set.name.contains(uiState.searchQuery, ignoreCase = true)
            val matchesSeries = uiState.selectedSeries == null ||
                set.series == uiState.selectedSeries
            matchesSearch && matchesSeries
        }
        uiState = uiState.copy(filteredSets = filtered)
    }
}
