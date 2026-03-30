package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.CardOptions
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.data.remote.PokeTcgRepository
import com.example.pokevault.data.remote.TcgCard
import com.example.pokevault.data.remote.TcgSet
import kotlinx.coroutines.flow.catch
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
    val completionPercent: Int get() {
        val printed = set?.printedTotal ?: totalCount
        val ownedOfPrinted = cards
            .filter { (it.number.toIntOrNull() ?: Int.MAX_VALUE) <= printed }
            .count { it.id in ownedCardIds }
        return if (printed > 0) (ownedOfPrinted * 100 / printed) else 0
    }
}

class SetDetailViewModel : ViewModel() {

    private val tcgRepository = PokeTcgRepository()
    private val firestoreRepository = FirestoreRepository()

    var uiState by mutableStateOf(SetDetailUiState())
        private set

    fun loadSet(setId: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)

            // Carica info set (per logo e printedTotal)
            try {
                tcgRepository.getSetInfo(setId).onSuccess { setInfo ->
                    uiState = uiState.copy(set = setInfo)
                }
            } catch (_: Exception) { }

            // Carica carte
            tcgRepository.getCardsBySet(setId)
                .onSuccess { cards ->
                    if (uiState.set == null && cards.isNotEmpty()) {
                        uiState = uiState.copy(
                            set = TcgSet(
                                id = setId,
                                name = cards.firstOrNull()?.set?.name ?: setId,
                                series = cards.firstOrNull()?.set?.series ?: ""
                            )
                        )
                    }
                    uiState = uiState.copy(cards = cards, isLoading = false)
                }
                .onFailure { error ->
                    uiState = uiState.copy(isLoading = false, errorMessage = "Errore: ${error.message}")
                }

            loadOwnedCards()
        }
    }

    private fun loadOwnedCards() {
        viewModelScope.launch {
            firestoreRepository.getCards()
                .catch { }
                .collect { ownedCards ->
                    val ownedIds = ownedCards
                        .filter { it.apiCardId.isNotBlank() }
                        .map { it.apiCardId }
                        .toSet()
                    uiState = uiState.copy(ownedCardIds = ownedIds)
                }
        }
    }

    // Aggiunta carta con dettagli (variante, quantità, condizione, lingua)
    fun addCardWithDetails(
        tcgCard: TcgCard,
        variant: String,
        quantity: Int,
        condition: String,
        language: String
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(isAddingCard = tcgCard.id)

            // Prendi prezzo della variante specifica
            val variantKey = CardOptions.getVariantApiKey(variant)
            val price = tcgCard.tcgplayer?.prices?.get(variantKey)?.market
                ?: tcgCard.cardmarket?.prices?.averageSellPrice
                ?: 0.0

            val card = PokemonCard(
                name = tcgCard.name,
                imageUrl = tcgCard.images.small,
                set = tcgCard.set?.name ?: "",
                rarity = tcgCard.rarity ?: "Unknown",
                type = tcgCard.types?.firstOrNull() ?: "Colorless",
                hp = tcgCard.hp?.toIntOrNull() ?: 0,
                estimatedValue = price,
                apiCardId = tcgCard.id,
                cardNumber = tcgCard.number,
                variant = variant,
                quantity = quantity,
                condition = condition,
                language = language
            )

            firestoreRepository.addCard(card)
                .onSuccess {
                    uiState = uiState.copy(
                        isAddingCard = null,
                        successMessage = "${tcgCard.name} ($variant) aggiunta!"
                    )
                }
                .onFailure {
                    uiState = uiState.copy(
                        isAddingCard = null,
                        errorMessage = "Errore nell'aggiunta"
                    )
                }
        }
    }

    // Rimuovi carta
    fun removeCard(tcgCard: TcgCard) {
        viewModelScope.launch {
            firestoreRepository.deleteCardByApiId(tcgCard.id)
                .onSuccess {
                    uiState = uiState.copy(successMessage = "${tcgCard.name} rimossa")
                }
                .onFailure {
                    uiState = uiState.copy(errorMessage = "Errore nella rimozione")
                }
        }
    }

    // Legacy toggle (per compatibilità)
    fun toggleCard(tcgCard: TcgCard) {
        if (tcgCard.id in uiState.ownedCardIds) {
            removeCard(tcgCard)
        } else {
            addCardWithDetails(tcgCard, "Normal", 1, "Mint", "🇮🇹 Italiano")
        }
    }

    fun setViewMode(mode: String) { uiState = uiState.copy(viewMode = mode) }
    fun clearMessages() { uiState = uiState.copy(errorMessage = null, successMessage = null) }
}
