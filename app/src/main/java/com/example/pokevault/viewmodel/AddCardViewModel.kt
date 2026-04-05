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
    val condition: String = "Near Mint",
    val isGraded: Boolean = false,
    val grade: String = "",
    val gradingCompany: String = "PSA",
    val notes: String = "",
    val imageUrl: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    // Edit mode
    val isEditMode: Boolean = false,
    val editCardId: String = ""
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
        uiState = uiState.copy(estimatedValue = value.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.'))
    }
    fun updateQuantity(value: String) { uiState = uiState.copy(quantity = value.filter { it.isDigit() }) }
    fun updateCondition(value: String) { uiState = uiState.copy(condition = value) }
    fun updateIsGraded(value: Boolean) { uiState = uiState.copy(isGraded = value) }
    
    fun updateGrade(value: String) {
        // 1. Converti virgola in punto e tieni solo cifre e un punto
        val sanitized = value.replace(',', '.')
        var dotCount = 0
        val filtered = sanitized.filter { 
            if (it == '.') {
                dotCount++
                dotCount <= 1
            } else {
                it.isDigit()
            }
        }

        if (filtered.isEmpty()) {
            uiState = uiState.copy(grade = "")
            return
        }

        // 2. Controllo numerico immediato
        val gradeVal = filtered.toFloatOrNull()
        if (gradeVal != null) {
            if (gradeVal > 10f) {
                // Se è > 10, teniamo il valore precedente o resettiamo a 10
                uiState = uiState.copy(grade = "10")
                return
            }
        }
        
        uiState = uiState.copy(grade = filtered)
    }
    
    fun updateGradingCompany(value: String) { uiState = uiState.copy(gradingCompany = value) }
    fun updateNotes(value: String) { uiState = uiState.copy(notes = value) }
    fun updateImageUrl(value: String) { uiState = uiState.copy(imageUrl = value) }

    fun loadCardForEdit(cardId: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            repository.getCard(cardId)
                .onSuccess { card ->
                    uiState = uiState.copy(
                        name = card.name,
                        set = card.set,
                        rarity = card.rarity.ifBlank { "Common" },
                        type = card.type.ifBlank { "Fuoco" },
                        hp = if (card.hp > 0) card.hp.toString() else "",
                        estimatedValue = if (card.estimatedValue > 0) card.estimatedValue.toString() else "",
                        quantity = card.quantity.toString(),
                        condition = card.condition.ifBlank { "Near Mint" },
                        isGraded = card.isGraded,
                        grade = card.grade?.toString() ?: "",
                        gradingCompany = card.gradingCompany.ifBlank { "PSA" },
                        notes = card.notes,
                        imageUrl = card.imageUrl,
                        isLoading = false,
                        isEditMode = true,
                        editCardId = cardId
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "Errore nel caricamento: ${error.message}"
                    )
                }
        }
    }

    fun saveCard() {
        if (uiState.name.isBlank()) {
            uiState = uiState.copy(errorMessage = "Inserisci il nome della carta")
            return
        }

        // Validazione finale gradazione
        if (uiState.isGraded) {
            val gradeVal = uiState.grade.toFloatOrNull()
            if (gradeVal == null || gradeVal < 0 || gradeVal > 10) {
                uiState = uiState.copy(errorMessage = "Il voto deve essere compreso tra 0 e 10")
                return
            }
            if (uiState.gradingCompany.isBlank()) {
                uiState = uiState.copy(errorMessage = "Seleziona un'azienda di grading")
                return
            }
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
                gradingCompany = if (uiState.isGraded) uiState.gradingCompany else "",
                estimatedValue = uiState.estimatedValue.toDoubleOrNull() ?: 0.0,
                quantity = uiState.quantity.toIntOrNull() ?: 1,
                condition = uiState.condition,
                notes = uiState.notes.trim()
            )

            val result = if (uiState.isEditMode) {
                repository.updateCard(uiState.editCardId, card)
                    .map { uiState.editCardId }
            } else {
                repository.addCard(card)
            }

            result
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
