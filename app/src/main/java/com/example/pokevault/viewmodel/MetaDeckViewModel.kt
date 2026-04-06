package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.model.MetaDeck
import com.example.pokevault.data.remote.LimitlessTcgRepository
import kotlinx.coroutines.launch

class MetaDeckViewModel : ViewModel() {

    private val repository = LimitlessTcgRepository()

    var metaDecks by mutableStateOf<List<MetaDeck>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var selectedFormat by mutableStateOf("standard")
        private set

    var selectedDeck by mutableStateOf<MetaDeck?>(null)
        private set

    init {
        loadMetaDecks()
    }

    fun loadMetaDecks(format: String = selectedFormat, limit: Int = 50) {
        selectedFormat = format
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            repository.getMetaDecks(format = format, limit = limit)
                .onSuccess { decks ->
                    metaDecks = decks
                    isLoading = false
                }
                .onFailure { e ->
                    errorMessage = e.localizedMessage ?: "Errore nel caricamento dei meta deck"
                    isLoading = false
                }
        }
    }

    fun selectFormat(format: String) {
        if (format != selectedFormat) {
            loadMetaDecks(format = format)
        }
    }

    fun selectDeck(deck: MetaDeck?) {
        selectedDeck = deck
    }

    fun refresh() {
        repository.clearCache()
        loadMetaDecks()
    }

    fun clearError() {
        errorMessage = null
    }
}
