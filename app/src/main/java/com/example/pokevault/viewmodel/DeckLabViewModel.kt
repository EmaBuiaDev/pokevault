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

    // New Deck State
    var newDeckName by mutableStateOf("")
    var selectedCardsIds by mutableStateOf<Set<String>>(emptySet())
    var currentAnalysis by mutableStateOf(DeckAnalysis())

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
        selectedCardsIds = if (selectedCardsIds.contains(cardId)) {
            selectedCardsIds - cardId
        } else {
            selectedCardsIds + cardId
        }
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

        val avgHp = selectedCards.map { it.hp }.average()
        
        val mainTypes = typesCount.entries.sortedByDescending { it.value }.take(2).map { it.key }
        val recommendedEnergy = mainTypes.map { "$it Energy" }
        
        val synergies = mutableListOf<String>()
        if (selectedCards.any { it.name.contains("Mewtwo", true) } && selectedCards.any { it.name.contains("Mew", true) }) {
            synergies.add("Duo Psichico Leggendario")
        }
        if (typesCount.size == 1 && selectedCards.size > 5) {
            synergies.add("Mono-tipo: Massima coerenza")
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

    fun saveDeck(onSuccess: () -> Unit) {
        if (newDeckName.isBlank()) return
        
        isSaving = true
        val mainTypes = currentAnalysis.typesCount.entries.sortedByDescending { it.value }.take(2).map { it.key }
        
        val deck = Deck(
            name = newDeckName,
            cards = selectedCardsIds.toList(),
            mainTypes = mainTypes,
            averageHp = currentAnalysis.averageHp,
            totalCards = selectedCardsIds.size,
            recommendedEnergy = currentAnalysis.recommendedEnergy
        )

        viewModelScope.launch {
            repository.saveDeck(deck).onSuccess {
                isSaving = false
                resetNewDeckState()
                onSuccess()
            }.onFailure {
                isSaving = false
            }
        }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch {
            repository.deleteDeck(deckId)
        }
    }

    fun resetNewDeckState() {
        newDeckName = ""
        selectedCardsIds = emptySet()
        currentAnalysis = DeckAnalysis()
    }
    
    fun duplicateDeck(deck: Deck) {
        viewModelScope.launch {
            val duplicated = deck.copy(id = "", name = "${deck.name} (Copia)")
            repository.saveDeck(duplicated)
        }
    }
}
