package com.example.pokevault.viewmodel

import androidx.compose.runtime.derivedStateOf
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
    var selectedCardsIds by mutableStateOf<List<String>>(emptyList())
    var coverImageUrl by mutableStateOf("")
    var currentAnalysis by mutableStateOf(DeckAnalysis())
    var validationError by mutableStateOf<String?>(null)
        private set

    // Optimized map for quick lookups during UI rendering
    private val cardIdToKeyMap by derivedStateOf {
        ownedCards.associate { it.id to getCardKey(it) }
    }

    // Counts of each card key currently in the deck
    private val deckQuantitiesByKey by derivedStateOf {
        selectedCardsIds.mapNotNull { cardIdToKeyMap[it] }
            .groupingBy { it }
            .eachCount()
    }

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
            repository.getCards().collectLatest { cards ->
                ownedCards = cards.sortedWith(
                    compareBy<PokemonCard> { 
                        val s = it.supertype.lowercase()
                        when {
                            s.contains("pok") -> 0
                            s.contains("train") -> 1
                            s.contains("energ") -> 2
                            else -> 3
                        }
                    }.thenBy { it.name }
                )
            }
        }
    }

    fun getCardKey(card: PokemonCard): String =
        card.apiCardId.ifEmpty { "${card.name}-${card.set}-${card.cardNumber}-${card.variant}" }

    fun getQuantityInDeck(card: PokemonCard): Int {
        return deckQuantitiesByKey[getCardKey(card)] ?: 0
    }

    fun getTotalOwnedQuantity(card: PokemonCard): Int {
        val key = getCardKey(card)
        return ownedCards.filter { getCardKey(it) == key }.sumOf { it.quantity }
    }

    private fun isEnergy(card: PokemonCard): Boolean {
        return card.supertype.lowercase().contains("energ") || 
               card.subtypes.any { it.lowercase().contains("energ") } ||
               card.name.lowercase().contains("energy")
    }

    fun addCardToDeck(card: PokemonCard) {
        val key = getCardKey(card)
        val inDeckCount = getQuantityInDeck(card)
        val totalOwned = getTotalOwnedQuantity(card)
        
        if (inDeckCount >= totalOwned) {
            validationError = "Hai solo $totalOwned copie di questa carta."
            return
        }

        if (selectedCardsIds.size >= 60) {
            validationError = "Limite massimo di 60 carte raggiunto."
            return
        }

        if (!isEnergy(card)) {
            val sameNameCount = selectedCardsIds.count { id ->
                ownedCards.find { it.id == id }?.name == card.name
            }
            if (sameNameCount >= 4) {
                validationError = "Massimo 4 copie di ${card.name}."
                return
            }
        }

        val availableId = ownedCards
            .filter { getCardKey(it) == key }
            .firstOrNull { doc ->
                val docInDeckCount = selectedCardsIds.count { it == doc.id }
                docInDeckCount < doc.quantity
            }?.id

        if (availableId != null) {
            selectedCardsIds = selectedCardsIds + availableId
            if (coverImageUrl.isEmpty()) coverImageUrl = card.imageUrl
            validationError = null
            analyzeDeck()
        }
    }

    fun removeCardFromDeck(card: PokemonCard) {
        val key = getCardKey(card)
        val idToRemove = selectedCardsIds.findLast { id ->
            cardIdToKeyMap[id] == key
        }
        
        if (idToRemove != null) {
            selectedCardsIds = selectedCardsIds - idToRemove
            if (coverImageUrl == card.imageUrl && !selectedCardsIds.any { id -> ownedCards.find { it.id == id }?.imageUrl == card.imageUrl }) {
                 val nextCardId = selectedCardsIds.firstOrNull()
                 coverImageUrl = ownedCards.find { it.id == nextCardId }?.imageUrl ?: ""
            }
            validationError = null
            analyzeDeck()
        }
    }

    private fun analyzeDeck() {
        val cardMap = ownedCards.associateBy { it.id }
        val selectedCards = selectedCardsIds.mapNotNull { cardMap[it] }
        
        if (selectedCards.isEmpty()) {
            currentAnalysis = DeckAnalysis()
            return
        }

        val typesCount = selectedCards.flatMap { it.type.split(",").map { t -> t.trim() } }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()

        val supertypesCount = selectedCards.groupingBy { 
            val s = it.supertype.lowercase()
            when {
                s.contains("pok") -> "Pokémon"
                s.contains("train") -> "Trainer"
                s.contains("energ") -> "Energy"
                else -> "Pokémon"
            }
        }.eachCount()

        val avgHp = if (selectedCards.any { it.hp > 0 }) selectedCards.filter { it.hp > 0 }.map { it.hp }.average() else 0.0
        
        currentAnalysis = DeckAnalysis(
            typesCount = typesCount,
            averageHp = avgHp,
            recommendedEnergy = emptyList(),
            synergies = emptyList(),
            commonWeaknesses = listOf("Variabile"),
            supertypesCount = supertypesCount
        )
    }

    fun prepareEdit(deck: Deck) {
        resetNewDeckState()
        editingDeckId = deck.id
        newDeckName = deck.name
        selectedCardsIds = deck.cards
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
            cards = selectedCardsIds,
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
        selectedCardsIds = emptyList()
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
