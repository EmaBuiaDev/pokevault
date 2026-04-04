package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.Deck
import com.example.pokevault.data.model.DeckAnalysis
import com.example.pokevault.data.model.PokemonCard
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DeckLabViewModel : ViewModel() {
    private val repository = FirestoreRepository()

    var decks by mutableStateOf<List<Deck>>(emptyList())
        private set

    var ownedCards by mutableStateOf<List<PokemonCard>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    // New/Edit Deck State
    var editingDeckId by mutableStateOf<String?>(null)
    var newDeckName by mutableStateOf("")
    var selectedCardsIds by mutableStateOf<Set<String>>(emptySet())
    var coverImageUrl by mutableStateOf("")
    var currentAnalysis by mutableStateOf(DeckAnalysis())
    var validationError by mutableStateOf<String?>(null)
        private set

    init {
        loadDecks()
        loadOwnedCards()
    }

    private fun loadDecks() {
        viewModelScope.launch {
            repository.getDecks().collectLatest {
                decks = it
            }
        }
    }

    private fun loadOwnedCards() {
        viewModelScope.launch {
            repository.getCards().collectLatest {
                ownedCards = it
            }
        }
    }

    fun toggleCardSelection(cardId: String) {
        val card = ownedCards.find { it.id == cardId } ?: return
        
        if (!selectedCardsIds.contains(cardId)) {
            // Rule: Max 60 cards
            if (selectedCardsIds.size >= 60) {
                validationError = "Limite massimo di 60 carte raggiunto."
                return
            }
            
            // Rule: Max 4 cards with same name (unless Energy)
            if (!card.supertype.equals("Energy", ignoreCase = true)) {
                val sameNameCount = ownedCards.filter { it.id in selectedCardsIds && it.name == card.name }.size
                if (sameNameCount >= 4) {
                    validationError = "Massimo 4 copie di ${card.name}."
                    return
                }
            }
            
            selectedCardsIds = selectedCardsIds + cardId
            if (coverImageUrl.isEmpty()) coverImageUrl = card.imageUrl
        } else {
            selectedCardsIds = selectedCardsIds - cardId
            if (coverImageUrl == card.imageUrl) {
                val nextCardId = selectedCardsIds.firstOrNull()
                coverImageUrl = ownedCards.find { it.id == nextCardId }?.imageUrl ?: ""
            }
        }
        
        validationError = null
        analyzeDeck()
    }

    private fun analyzeDeck() {
        val selectedCards = ownedCards.filter { it.id in selectedCardsIds }
        if (selectedCards.isEmpty()) {
            currentAnalysis = DeckAnalysis()
            return
        }

        val typesCount = selectedCards.flatMap { it.type.split(",").map { t -> t.trim() } }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()

        val supertypesCount = selectedCards.groupingBy { it.supertype }.eachCount()
        val avgHp = if (selectedCards.any { it.hp > 0 }) selectedCards.filter { it.hp > 0 }.map { it.hp }.average() else 0.0
        
        val mainTypes = typesCount.entries.sortedByDescending { it.value }.take(2).map { it.key }
        val recommendedEnergy = mainTypes.map { "$it Energy" }
        
        val synergies = mutableListOf<String>()
        if (selectedCards.any { it.name.contains("Mewtwo", true) } && selectedCards.any { it.name.contains("Mew", true) }) {
            synergies.add("Duo Mew & Mewtwo")
        }
        if (typesCount.size == 1 && selectedCards.size > 10) {
            synergies.add("Specialista Mono-Tipo")
        }

        currentAnalysis = DeckAnalysis(
            typesCount = typesCount,
            averageHp = avgHp,
            recommendedEnergy = recommendedEnergy,
            synergies = synergies,
            commonWeaknesses = listOf("Variabile"),
            supertypesCount = supertypesCount
        )
    }

    fun prepareEdit(deck: Deck) {
        editingDeckId = deck.id
        newDeckName = deck.name
        selectedCardsIds = deck.cards.toSet()
        coverImageUrl = deck.coverImageUrl
        analyzeDeck()
    }

    fun saveDeck(onSuccess: () -> Unit) {
        if (newDeckName.isBlank()) {
            validationError = "Inserisci un nome per il deck."
            return
        }
        if (selectedCardsIds.isEmpty()) {
            validationError = "Seleziona almeno una carta."
            return
        }
        
        isSaving = true
        val mainTypes = currentAnalysis.typesCount.entries.sortedByDescending { it.value }.take(2).map { it.key }
        
        val deck = Deck(
            id = editingDeckId ?: "",
            name = newDeckName,
            cards = selectedCardsIds.toList(),
            mainTypes = mainTypes,
            averageHp = currentAnalysis.averageHp,
            totalCards = selectedCardsIds.size,
            recommendedEnergy = currentAnalysis.recommendedEnergy,
            coverImageUrl = coverImageUrl
        )

        viewModelScope.launch {
            repository.saveDeck(deck).onSuccess {
                isSaving = false
                resetNewDeckState()
                onSuccess()
            }.onFailure {
                isSaving = false
                validationError = "Errore database: ${it.localizedMessage}"
            }
        }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch {
            repository.deleteDeck(deckId)
        }
    }

    fun resetNewDeckState() {
        editingDeckId = null
        newDeckName = ""
        selectedCardsIds = emptySet()
        coverImageUrl = ""
        currentAnalysis = DeckAnalysis()
        validationError = null
    }
    
    fun duplicateDeck(deck: Deck) {
        viewModelScope.launch {
            val duplicated = deck.copy(id = "", name = "${deck.name} (Copia)")
            repository.saveDeck(duplicated)
        }
    }

    fun clearError() {
        validationError = null
    }

    fun selectCoverCard(url: String) {
        coverImageUrl = url
    }
}
