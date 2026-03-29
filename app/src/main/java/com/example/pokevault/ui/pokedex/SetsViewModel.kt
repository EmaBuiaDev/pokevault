package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.remote.PokeTcgRepository
import com.example.pokevault.data.remote.TcgSet
import kotlinx.coroutines.launch

data class SetsUiState(
    val allSets: List<TcgSet> = emptyList(),
    val filteredSets: List<TcgSet> = emptyList(),
    val seriesList: List<String> = emptyList(),
    val selectedSeries: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class SetsViewModel : ViewModel() {

    private val repository = PokeTcgRepository()

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
