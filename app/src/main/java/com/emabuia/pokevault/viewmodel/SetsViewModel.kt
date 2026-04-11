package com.emabuia.pokevault.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.PokeTcgRepository
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgSet
import com.emabuia.pokevault.data.remote.TranslationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SetsUiState(
    val allSets: List<TcgSet> = emptyList(),
    val filteredSets: List<TcgSet> = emptyList(),
    val seriesList: List<String> = emptyList(),
    val selectedSeries: String? = null,
    val searchQuery: String = "",
    val cardSearchQuery: String = "",
    val searchedCards: List<TcgCard> = emptyList(),
    val isSearchingCards: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isAddingCard: String? = null,
    val successMessage: String? = null
)

// Usa AndroidViewModel per accedere al Context
class SetsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PokeTcgRepository()
    private val firestoreRepository = FirestoreRepository()
    private var searchJob: Job? = null
    private var setSearchJob: Job? = null

    var uiState by mutableStateOf(SetsUiState())
        private set

    init {
        TranslationService.loadCache(application.applicationContext)
        loadSets()
    }

    private fun loadSets() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            val context = getApplication<Application>().applicationContext
            repository.getSets(context = context)
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
        setSearchJob?.cancel()
        setSearchJob = viewModelScope.launch {
            delay(300)
            applyFilters()
        }
    }

    fun filterBySeries(series: String?) {
        uiState = uiState.copy(selectedSeries = series)
        applyFilters()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            val context = getApplication<Application>().applicationContext
            repository.getSets(context = context, forceRefresh = true)
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

    fun searchCardsByName(query: String) {
        uiState = uiState.copy(cardSearchQuery = query)
        searchJob?.cancel()
        if (query.length < 2) {
            uiState = uiState.copy(searchedCards = emptyList(), isSearchingCards = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(500)
            uiState = uiState.copy(isSearchingCards = true)
            val context = getApplication<Application>().applicationContext

            // Search with original query + translate in parallel
            val directDeferred = async { repository.searchCards(query) }
            val translatedDeferred = async {
                val translated = TranslationService.translateItToEn(query, context)
                if (translated != null && translated.lowercase() != query.lowercase()) {
                    repository.searchCards(translated)
                } else null
            }

            val directCards = directDeferred.await().getOrDefault(emptyList())
            val translatedCards = translatedDeferred.await()?.getOrDefault(emptyList()) ?: emptyList()

            val allCards = (directCards + translatedCards).distinctBy { it.id }
            uiState = uiState.copy(searchedCards = allCards, isSearchingCards = false)
        }
    }

    fun clearCardSearch() {
        searchJob?.cancel()
        uiState = uiState.copy(cardSearchQuery = "", searchedCards = emptyList(), isSearchingCards = false)
    }

    fun addCardWithDetails(tcgCard: TcgCard, variant: String, quantity: Int, condition: String, language: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isAddingCard = tcgCard.id)
            val variantKey = CardOptions.getVariantApiKey(variant)
            val price = tcgCard.tcgplayer?.prices?.get(variantKey)?.market
                ?: tcgCard.cardmarket?.prices?.lowPrice
                ?: tcgCard.cardmarket?.prices?.averageSellPrice ?: 0.0

            val card = PokemonCard(
                name = tcgCard.name, imageUrl = tcgCard.images.small,
                set = tcgCard.set?.name ?: "",
                rarity = tcgCard.rarity ?: "Unknown",
                type = tcgCard.types?.firstOrNull() ?: "Colorless",
                hp = tcgCard.hp?.toIntOrNull() ?: 0, estimatedValue = price,
                apiCardId = tcgCard.id, cardNumber = tcgCard.number,
                variant = variant, quantity = quantity, condition = condition, language = language
            )
            firestoreRepository.addCard(card)
                .onSuccess { uiState = uiState.copy(isAddingCard = null, successMessage = "${tcgCard.name} aggiunta!") }
                .onFailure { uiState = uiState.copy(isAddingCard = null, errorMessage = "Errore") }
        }
    }

    fun clearMessages() {
        uiState = uiState.copy(successMessage = null, errorMessage = null)
    }

    private fun applyFilters() {
        val filtered = uiState.allSets.filter { set ->
            val matchesSearch = uiState.searchQuery.isBlank() ||
                set.name.contains(uiState.searchQuery, ignoreCase = true) ||
                set.series.contains(uiState.searchQuery, ignoreCase = true)
            val matchesSeries = uiState.selectedSeries == null ||
                set.series == uiState.selectedSeries
            matchesSearch && matchesSeries
        }
        uiState = uiState.copy(filteredSets = filtered)
    }
}
