package com.example.pokevault.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.CardOptions
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.data.remote.PokeTcgRepository
import com.example.pokevault.data.remote.TcgCard
import com.example.pokevault.data.remote.TcgSet
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class SetDetailUiState(
    val set: TcgSet? = null,
    val cards: List<TcgCard> = emptyList(),
    val ownedCardIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isAddingCard: String? = null,
    val viewMode: String = "grid",
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    val ownedCount: Int get() = cards.count { it.id in ownedCardIds }
    val totalCount: Int get() = cards.size
    
    // OTTIMIZZAZIONE: Il totale deve corrispondere alla lista reale delle carte caricate
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

    fun loadSet(setId: String) {
        if (currentSetId == setId) return
        currentSetId = setId

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            val context = getApplication<Application>().applicationContext

            val setInfoDeferred = async { tcgRepository.getSetInfo(setId) }
            val cardsDeferred = async { tcgRepository.getCardsBySet(setId, context = context) }

            val setInfoResult = setInfoDeferred.await()
            val cardsResult = cardsDeferred.await()

            var setNameForFirestore = ""

            setInfoResult.onSuccess { setInfo ->
                uiState = uiState.copy(set = setInfo)
                setNameForFirestore = setInfo.name
            }

            cardsResult.onSuccess { cards ->
                if (uiState.set == null && cards.isNotEmpty()) {
                    val fallbackSet = TcgSet(
                        id = setId,
                        name = cards.firstOrNull()?.set?.name ?: setId,
                        series = cards.firstOrNull()?.set?.series ?: ""
                    )
                    uiState = uiState.copy(set = fallbackSet)
                    setNameForFirestore = fallbackSet.name
                }
                uiState = uiState.copy(cards = cards, isLoading = false)
            }.onFailure { error ->
                uiState = uiState.copy(isLoading = false, errorMessage = "Errore: ${error.message}")
            }

            if (setNameForFirestore.isNotBlank()) {
                observeOwnedCards(setNameForFirestore)
            }
        }
    }

    private fun observeOwnedCards(setName: String) {
        viewModelScope.launch {
            firestoreRepository.getOwnedCardsBySet(setName)
                .catch { }
                .collectLatest { ownedCards ->
                    val ownedIds = ownedCards
                        .filter { it.apiCardId.isNotBlank() }
                        .map { it.apiCardId }
                        .toSet()
                    uiState = uiState.copy(ownedCardIds = ownedIds)
                }
        }
    }

    fun addCardWithDetails(tcgCard: TcgCard, variant: String, quantity: Int, condition: String, language: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isAddingCard = tcgCard.id)
            val variantKey = CardOptions.getVariantApiKey(variant)
            val price = tcgCard.tcgplayer?.prices?.get(variantKey)?.market
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
