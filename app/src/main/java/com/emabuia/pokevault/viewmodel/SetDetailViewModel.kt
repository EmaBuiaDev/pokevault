package com.emabuia.pokevault.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import com.emabuia.pokevault.BuildConfig
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
import com.emabuia.pokevault.util.AppLocale
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class SetDetailUiState(
    val set: TcgSet? = null,
    val cards: List<TcgCard> = emptyList(),
    val ownedCardIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isLoadingCards: Boolean = true,
    val isAddingCard: String? = null,
    val viewMode: String = "grid",
    val searchQuery: String = "",
    val translatedQuery: String = "",
    val showOnlyMissing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    val ownedCount: Int get() = cards.count { it.id in ownedCardIds }
    val totalCount: Int get() = cards.size
    val displayTotal: Int get() = if (cards.isNotEmpty()) cards.size else (set?.total ?: 0)
    val completionPercent: Int get() =
        if (displayTotal > 0) (ownedCount * 100 / displayTotal) else 0
}

class SetDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val tcgRepository = PokeTcgRepository()
    private val firestoreRepository = FirestoreRepository()

    var uiState by mutableStateOf(SetDetailUiState())
        private set

    private var currentSetId: String? = null
    private var translationJob: Job? = null

    init {
        TranslationService.loadCache(application.applicationContext)
    }

    fun loadSet(setId: String) {
        if (currentSetId == setId) return
        currentSetId = setId

        val context = getApplication<Application>().applicationContext
        uiState = uiState.copy(isLoading = true, isLoadingCards = true)

        // Load set info independently - show it as soon as it arrives
        viewModelScope.launch {
            tcgRepository.getSetInfo(setId).onSuccess { setInfo ->
                uiState = uiState.copy(set = setInfo, isLoading = false)
                observeOwnedCards(setInfo.name)
            }
        }

        // Load cards independently - show them as soon as they arrive
        viewModelScope.launch {
            tcgRepository.getCardsBySet(setId, context = context)
                .onSuccess { cards ->
                    if (uiState.set == null && cards.isNotEmpty()) {
                        val fallbackSet = TcgSet(
                            id = setId,
                            name = cards.firstOrNull()?.set?.name ?: setId,
                            series = cards.firstOrNull()?.set?.series ?: ""
                        )
                        uiState = uiState.copy(set = fallbackSet)
                        observeOwnedCards(fallbackSet.name)
                    }
                    uiState = uiState.copy(cards = cards, isLoading = false, isLoadingCards = false)
                }
                .onFailure { error ->
                    uiState = uiState.copy(isLoading = false, isLoadingCards = false, errorMessage = "Errore: ${error.message}")
                }
        }
    }

    private fun observeOwnedCards(setName: String) {
        viewModelScope.launch {
            firestoreRepository.getOwnedCardsBySet(setName)
                .catch { e ->
                    if (BuildConfig.DEBUG) {
                        android.util.Log.w("SetDetailVM", "Errore osservazione carte possedute", e)
                    }
                }
                .collectLatest { ownedCards ->
                    val ownedIds = ownedCards
                        .filter { it.apiCardId.isNotBlank() }
                        .map { it.apiCardId }
                        .toSet()
                    uiState = uiState.copy(ownedCardIds = ownedIds)
                }
        }
    }

    fun updateSearchQuery(query: String) {
        // Check cached translation first for instant results
        val cached = TranslationService.getCached(query)
        uiState = uiState.copy(searchQuery = query, translatedQuery = cached ?: "")

        // Translate async if no cache and query is long enough
        translationJob?.cancel()
        if (query.length >= 3 && cached == null) {
            translationJob = viewModelScope.launch {
                delay(400)
                val context = getApplication<Application>().applicationContext
                val translated = TranslationService.translateItToEn(query, context)
                if (translated != null && uiState.searchQuery == query) {
                    uiState = uiState.copy(translatedQuery = translated)
                }
            }
        }
    }

    fun toggleShowOnlyMissing() {
        uiState = uiState.copy(showOnlyMissing = !uiState.showOnlyMissing)
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
                set = tcgCard.set?.name ?: uiState.set?.name ?: "", 
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

    fun addMultipleCards(cards: List<TcgCard>, preferredVariant: String) {
        viewModelScope.launch {
            var addedCount = 0
            cards.forEach { tcgCard ->
                val availableVariants = CardOptions.getVariantsForCard(
                    tcgCard.tcgplayer?.prices?.keys ?: emptySet(), tcgCard.rarity
                )
                val actualVariant = if (preferredVariant in availableVariants) preferredVariant
                    else availableVariants.firstOrNull() ?: "Holo"

                val variantKey = CardOptions.getVariantApiKey(actualVariant)
                val price = tcgCard.tcgplayer?.prices?.get(variantKey)?.market
                    ?: tcgCard.cardmarket?.prices?.lowPrice
                    ?: tcgCard.cardmarket?.prices?.averageSellPrice ?: 0.0

                val card = PokemonCard(
                    name = tcgCard.name, imageUrl = tcgCard.images.small,
                    set = tcgCard.set?.name ?: uiState.set?.name ?: "",
                    rarity = tcgCard.rarity ?: "Unknown",
                    type = tcgCard.types?.firstOrNull() ?: "Colorless",
                    hp = tcgCard.hp?.toIntOrNull() ?: 0, estimatedValue = price,
                    apiCardId = tcgCard.id, cardNumber = tcgCard.number,
                    variant = actualVariant, quantity = 1, condition = "Near Mint", language = "🇮🇹 Italiano"
                )
                firestoreRepository.addCard(card).onSuccess { addedCount++ }
            }
            uiState = uiState.copy(
                successMessage = if (AppLocale.isItalian) "$addedCount carte aggiunte!" else "$addedCount cards added!"
            )
        }
    }

    fun removeCard(tcgCard: TcgCard) {
        viewModelScope.launch {
            firestoreRepository.deleteCardByApiId(tcgCard.id)
                .onSuccess { uiState = uiState.copy(successMessage = "${tcgCard.name} rimossa") }
                .onFailure { uiState = uiState.copy(errorMessage = "Errore") }
        }
    }

    fun setViewMode(mode: String) { uiState = uiState.copy(viewMode = mode) }
    fun clearMessages() { uiState = uiState.copy(errorMessage = null, successMessage = null) }
}
