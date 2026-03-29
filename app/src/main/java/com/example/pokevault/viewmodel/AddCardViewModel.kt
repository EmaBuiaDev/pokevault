package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.PokemonCard
import kotlinx.coroutines.launch

data class AddCardUiState(
    val name: String = "",
    val set: String = "",
    val rarity: String = "Common",
    val type: String = "Fuoco",
    val hp: String = "",
    val estimatedValue: String = "",
    val quantity: String = "1",
    val condition: String = "Near Mint (NM)",
    val isGraded: Boolean = false,
    val grade: String = "",
    val notes: String = "",
    val imageUrl: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

class AddCardViewModel : ViewModel() {

    private val repository = FirestoreRepository()

    var uiState by mutableStateOf(AddCardUiState())
        private set

    fun updateName(value: String) { uiState = uiState.copy(name = value) }
    fun updateSet(value: String) { uiState = uiState.copy(set = value) }
    fun updateRarity(value: String) { uiState = uiState.copy(rarity = value) }
    fun updateType(value: String) { uiState = uiState.copy(type = value) }
    fun updateHp(value: String) { uiState = uiState.copy(hp = value.filter { it.isDigit() }) }
    fun updateEstimatedValue(value: String) {
        uiState = uiState.copy(estimatedValue = value.filter { it.isDigit() || it == '.' })
    }
    fun updateQuantity(value: String) { uiState = uiState.copy(quantity = value.filter { it.isDigit() }) }
    fun updateCondition(value: String) { uiState = uiState.copy(condition = value) }
    fun updateIsGraded(value: Boolean) { uiState = uiState.copy(isGraded = value) }
    fun updateGrade(value: String) { uiState = uiState.copy(grade = value) }
    fun updateNotes(value: String) { uiState = uiState.copy(notes = value) }
    fun updateImageUrl(value: String) { uiState = uiState.copy(imageUrl = value) }

    fun saveCard() {
        if (uiState.name.isBlank()) {
            uiState = uiState.copy(errorMessage = "Inserisci il nome della carta")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            val card = PokemonCard(
                name = uiState.name.trim(),
                imageUrl = uiState.imageUrl.trim(),
                set = uiState.set.trim(),
                rarity = uiState.rarity,
                type = uiState.type,
                hp = uiState.hp.toIntOrNull() ?: 0,
                isGraded = uiState.isGraded,
                grade = if (uiState.isGraded) uiState.grade.toFloatOrNull() else null,
                estimatedValue = uiState.estimatedValue.toDoubleOrNull() ?: 0.0,
                quantity = uiState.quantity.toIntOrNull() ?: 1,
                condition = uiState.condition,
                notes = uiState.notes.trim()
            )

            repository.addCard(card)
                .onSuccess {
                    uiState = uiState.copy(isLoading = false, isSaved = true)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "Errore nel salvataggio: ${error.message}"
                    )
                }
        }
    }

    // Pre-compila i campi (es. da Pokédex)
    fun prefillFromPokedex(
        name: String,
        imageUrl: String,
        set: String,
        rarity: String,
        type: String,
        hp: Int
    ) {
        uiState = uiState.copy(
            name = name,
            imageUrl = imageUrl,
            set = set,
            rarity = rarity,
            type = type,
            hp = hp.toString()
        )
    }

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }
}
