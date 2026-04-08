package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.model.MetaArchetype
import com.example.pokevault.data.model.MetaDeck
import com.example.pokevault.data.remote.LimitlessTcgRepository
import kotlinx.coroutines.launch

class MetaDeckViewModel : ViewModel() {

    private val repository = LimitlessTcgRepository()

    // Win Tournament (ex Meta Deck) - tournament winners
    var metaDecks by mutableStateOf<List<MetaDeck>>(emptyList())
        private set

    // Meta Deck (NEW) - archetype standings
    var archetypes by mutableStateOf<List<MetaArchetype>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isLoadingArchetypes by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var archetypeError by mutableStateOf<String?>(null)
        private set

    var selectedFormat by mutableStateOf("standard")
        private set

    var selectedDeck by mutableStateOf<MetaDeck?>(null)
        private set

    init {
        loadMetaDecks()
        loadArchetypes()
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

    fun loadArchetypes(format: String = selectedFormat) {
        isLoadingArchetypes = true
        archetypeError = null

        viewModelScope.launch {
            repository.getMetaArchetypes(format = format)
                .onSuccess { list ->
                    archetypes = list
                    isLoadingArchetypes = false
                }
                .onFailure { e ->
                    archetypeError = e.localizedMessage ?: "Errore nel caricamento"
                    isLoadingArchetypes = false
                }
        }
    }

    fun selectFormat(format: String) {
        if (format != selectedFormat) {
            selectedFormat = format
            loadMetaDecks(format = format)
            loadArchetypes(format = format)
        }
    }

    fun selectDeck(deck: MetaDeck?) {
        selectedDeck = deck
    }

    fun refresh() {
        repository.clearCache()
        loadMetaDecks()
        loadArchetypes()
    }

    fun clearError() {
        errorMessage = null
    }
}
