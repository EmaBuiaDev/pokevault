package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.model.MetaArchetype
import com.emabuia.pokevault.data.model.MetaDeck
import com.emabuia.pokevault.data.remote.LimitlessTcgRepository
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

    // Timestamp (in ms) dell'ultima sincronizzazione con l'API Limitless.
    // Viene aggiornato in automatico quando una load() termina con successo.
    // Se c'è già una voce valida in cache condivisa, parte da quel valore,
    // così la UI mostra l'età reale dei dati anche subito dopo la navigazione.
    var lastUpdated by mutableStateOf<Long?>(
        LimitlessTcgRepository.lastCacheTimestamp("standard")
    )
        private set

    // Rate limit: impedisce refresh troppo ravvicinati (60 s) che
    // spammerebbero inutilmente l'API Limitless.
    private var lastManualRefreshAt: Long = 0L
    private val refreshCooldownMs = 60_000L

    val refreshCooldownSeconds: Long
        get() {
            val elapsed = System.currentTimeMillis() - lastManualRefreshAt
            val remaining = refreshCooldownMs - elapsed
            return (remaining / 1000L).coerceAtLeast(0L)
        }

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
                    lastUpdated = LimitlessTcgRepository.lastCacheTimestamp(format)
                        ?: System.currentTimeMillis()
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
                    lastUpdated = LimitlessTcgRepository.lastCacheTimestamp(format)
                        ?: System.currentTimeMillis()
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
            lastUpdated = LimitlessTcgRepository.lastCacheTimestamp(format)
        }
    }

    fun selectDeck(deck: MetaDeck?) {
        selectedDeck = deck
    }

    /**
     * Forza un refresh dei meta deck ignorando la cache.
     * Ritorna `false` se siamo ancora dentro il cooldown (ed in quel caso
     * non fa niente), `true` se il refresh è stato avviato.
     */
    fun refresh(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastManualRefreshAt < refreshCooldownMs) {
            return false
        }
        lastManualRefreshAt = now
        repository.clearCache()
        loadMetaDecks()
        loadArchetypes()
        return true
    }

    fun clearError() {
        errorMessage = null
    }
}
