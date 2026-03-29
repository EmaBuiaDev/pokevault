package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.firebase.FirestoreRepository
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
    val completionPercent: Int get() = if (totalCount > 0) (ownedCount * 100 / totalCount) else 0
}

class SetDetailViewModel : ViewModel() {

    private val tcgRepository = PokeTcgRepository()
    private val firestoreRepository = FirestoreRepository()

    var uiState by mutableStateOf(SetDetailUiState())
        private set

    fun loadSet(setId: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)

            // Carica info set (per logo)
            try {
                val setResponse = tcgRepository.getSetInfo(setId)
                setResponse.onSuccess { setInfo ->
                    uiState = uiState.copy(set = setInfo)
                }
            } catch (_: Exception) { }

            // Carica carte del set
            tcgRepository.getCardsBySet(setId)
                .onSuccess { cards ->
                    // Se non abbiamo ancora il set info, prendilo dalle carte
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
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "Errore: ${error.message}"
                    )
                }

            // Carica carte possedute
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

    fun toggleCard(tcgCard: TcgCard) {
        viewModelScope.launch {
            val isOwned = tcgCard.id in uiState.ownedCardIds

            uiState = uiState.copy(isAddingCard = tcgCard.id)

            if (isOwned) {
                firestoreRepository.deleteCardByApiId(tcgCard.id)
                    .onSuccess {
                        uiState = uiState.copy(
                            isAddingCard = null,
                            successMessage = "${tcgCard.name} rimossa"
                        )
                    }
                    .onFailure {
                        uiState = uiState.copy(
                            isAddingCard = null,
                            errorMessage = "Errore nella rimozione"
                        )
                    }
            } else {
                val price = tcgCard.cardmarket?.prices?.averageSellPrice
                    ?: tcgCard.tcgplayer?.prices?.values?.firstOrNull()?.market
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
                    cardNumber = tcgCard.number
                )

                firestoreRepository.addCard(card)
                    .onSuccess {
                        uiState = uiState.copy(
                            isAddingCard = null,
                            successMessage = "${tcgCard.name} aggiunta!"
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
    }

    fun setViewMode(mode: String) {
        uiState = uiState.copy(viewMode = mode)
    }

    fun clearMessages() {
        uiState = uiState.copy(errorMessage = null, successMessage = null)
    }
}
