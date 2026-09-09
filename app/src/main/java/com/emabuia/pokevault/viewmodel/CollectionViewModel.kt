package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.CollectionStats
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.collectionGroupKey
import com.emabuia.pokevault.data.remote.CatalogRepository
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

enum class SortOrder {
    NEWEST, PRICE_ASC, PRICE_DESC, NAME_ASC, NUMBER
}

enum class SupertypeFilter {
    ALL, POKEMON, TRAINER, ENERGY
}

data class CollectionUiState(
    val cards: List<PokemonCard> = emptyList(),
    val filteredCards: List<PokemonCard> = emptyList(),
    val stats: CollectionStats = CollectionStats(),
    val isLoading: Boolean = true,
    val isGridView: Boolean = true,
    val gridColumns: Int = 4,
    val searchQuery: String = "",
    val selectedSet: String? = null,
    val selectedType: String? = null,
    val selectedRarity: String? = null,
    val supertypeFilter: SupertypeFilter = SupertypeFilter.ALL,
    val sortOrder: SortOrder = SortOrder.NUMBER,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class CollectionViewModel : ViewModel() {

    private val repository = FirestoreRepository()
    private val tcgRepository = CatalogRepository()
    // Synchronized perché emissioni rapide del Flow possono lanciare hydration concorrenti
    // e questi insiemi tracciano lo stato condiviso fra di esse.
    private val hydratedPriceCardIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val hydratingPriceCardIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    // Il Flow di Firestore riemette a ogni scrittura: senza questa guardia la
    // correzione dei codici espansione ripartirebbe sopra se stessa a ogni update.
    @Volatile
    private var legacyExpansionFixRunning = false

    var uiState by mutableStateOf(CollectionUiState())
        private set

    init {
        loadCards()
    }

    private fun loadCards() {
        viewModelScope.launch {
            repository.getCards()
                .catch { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "Errore: ${error.message}"
                    )
                }
                .collect { cards ->
                    applyCardsSnapshot(cards)

                    hydrateMissingPrices(cards)
                    fixLegacyExpansionCodes(cards)
                }
        }
    }

    private fun applyCardsSnapshot(cards: List<PokemonCard>) {
        val newStats = CollectionStats(
            totalCards = cards.sumOf { it.quantity },
            uniqueCards = cards.map { it.collectionGroupKey() }.toSet().size,
            totalValue = cards.sumOf { it.estimatedValue * it.quantity }
        )

        uiState = uiState.copy(
            cards = cards,
            filteredCards = applyFilters(cards),
            stats = newStats,
            isLoading = false
        )
    }

    private fun hydrateMissingPrices(cards: List<PokemonCard>) {
        val candidates = cards
            .filter { card ->
                card.estimatedValue <= 0.0 &&
                    card.apiCardId.isNotBlank() &&
                    card.id !in hydratedPriceCardIds &&
                    card.id !in hydratingPriceCardIds
            }
            .take(8)

        if (candidates.isEmpty()) return

        viewModelScope.launch {
            candidates.forEach { card ->
                hydratingPriceCardIds += card.id
                try {
                    val remoteCard = tcgRepository.getCard(card.apiCardId).getOrNull()
                    val eurPrice = remoteCard?.cardmarket?.prices.minimumEurPriceOrZero()

                    if (eurPrice > 0.0) {
                        repository.updateCard(card.id, card.copy(estimatedValue = eurPrice))
                    }
                } finally {
                    hydratingPriceCardIds -= card.id
                    hydratedPriceCardIds += card.id
                }
            }
        }
    }

    /**
     * Corregge le carte salvate con il CODICE dell'espansione al posto del nome
     * (es. "SV08" invece di "Scintille Folgoranti"): fino al fix in
     * CatalogRepository, tutto cio' che nasceva da ricerca/scanner/import portava
     * con se' il codice, e quel valore finiva scritto in `PokemonCard.set`.
     *
     * Riscrive SOLO i valori che corrispondono esattamente a un id di espansione
     * noto: un nome vero ("Scintille Folgoranti") o un nome inglese ("Surging
     * Sparks") non combaciano con nessun id, quindi non vengono mai toccati.
     * Di conseguenza e' anche idempotente -- dopo la correzione nessuna carta
     * combacia piu', e le esecuzioni successive non scrivono nulla.
     */
    private fun fixLegacyExpansionCodes(cards: List<PokemonCard>) {
        if (legacyExpansionFixRunning) return
        val candidates = cards.filter { it.set.isNotBlank() }
        if (candidates.isEmpty()) return

        viewModelScope.launch {
            legacyExpansionFixRunning = true
            try {
                val namesById = tcgRepository.italianExpansionNamesById()
                if (namesById.isEmpty()) return@launch // manifest non raggiungibile: non indovinare

                candidates.forEach { card ->
                    val realName = namesById[card.set.trim().lowercase()] ?: return@forEach
                    if (realName != card.set) {
                        repository.updateCardSetName(card.id, realName)
                    }
                }
            } finally {
                legacyExpansionFixRunning = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        uiState = uiState.copy(searchQuery = query)
        refreshFilteredCards()
    }

    fun filterBySet(setName: String?) {
        uiState = uiState.copy(selectedSet = setName)
        refreshFilteredCards()
    }

    fun filterByType(type: String?) {
        uiState = uiState.copy(selectedType = type)
        refreshFilteredCards()
    }

    fun filterBySupertype(filter: SupertypeFilter) {
        uiState = uiState.copy(
            supertypeFilter = filter,
            // I tipi (Fuoco/Acqua/...) valgono solo per i Pokémon.
            selectedType = if (filter == SupertypeFilter.POKEMON || filter == SupertypeFilter.ALL) uiState.selectedType else null
        )
        refreshFilteredCards()
    }

    fun filterByRarity(rarity: String?) {
        uiState = uiState.copy(selectedRarity = rarity)
        refreshFilteredCards()
    }

    fun updateSortOrder(order: SortOrder) {
        uiState = uiState.copy(sortOrder = order)
        refreshFilteredCards()
    }

    private fun refreshFilteredCards() {
        uiState = uiState.copy(
            filteredCards = applyFilters(uiState.cards)
        )
    }

    fun toggleViewMode() {
        uiState = uiState.copy(isGridView = !uiState.isGridView)
    }

    fun toggleGridColumns() {
        val nextColumns = when (uiState.gridColumns) {
            2 -> 3
            3 -> 4
            4 -> 5
            5 -> 6
            else -> 2
        }
        uiState = uiState.copy(gridColumns = nextColumns)
    }

    fun deleteCard(cardId: String) {
        viewModelScope.launch {
            repository.deleteCard(cardId)
                .onSuccess {
                    uiState = uiState.copy(successMessage = "Carta eliminata")
                }
                .onFailure { error ->
                    uiState = uiState.copy(errorMessage = "Errore: ${error.message}")
                }
        }
    }

    fun deleteMultipleGroups(groupKeys: Set<String>) {
        viewModelScope.launch {
            val originalCards = uiState.cards
            val cardsToDelete = originalCards.filter { card ->
                card.collectionGroupKey() in groupKeys
            }
            if (cardsToDelete.isEmpty()) return@launch

            val deletedIds = cardsToDelete.map { it.id }.toSet()
            val remainingCards = originalCards.filterNot { card -> card.id in deletedIds }
            applyCardsSnapshot(remainingCards)

            repository.deleteCards(cardsToDelete)
                .onSuccess {
                    uiState = uiState.copy(successMessage = "${cardsToDelete.size} carte eliminate")
                }
                .onFailure { error ->
                    uiState = uiState.copy(errorMessage = "Errore: ${error.message}")
                    applyCardsSnapshot(originalCards)
                }
        }
    }

    fun clearMessages() {
        uiState = uiState.copy(errorMessage = null, successMessage = null)
    }

    private fun normalizeSetForFilter(value: String?): String {
        val displayed = AppLocale.displaySetName(value?.trim().orEmpty())
        return displayed
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
    }

    private fun applyFilters(cards: List<PokemonCard>): List<PokemonCard> {
        val filtered = cards.filter { card ->
            val matchesQuery = uiState.searchQuery.isBlank() ||
                card.name.contains(uiState.searchQuery, ignoreCase = true) ||
                card.set.contains(uiState.searchQuery, ignoreCase = true) ||
                card.rarity.contains(uiState.searchQuery, ignoreCase = true)
            
            val selectedSetNormalized = normalizeSetForFilter(uiState.selectedSet)
            val cardRawSetNormalized = normalizeSetForFilter(card.set)
            val matchesSet = uiState.selectedSet == null ||
                selectedSetNormalized == cardRawSetNormalized ||
                (selectedSetNormalized == normalizeSetForFilter("Espansione sconosciuta") && card.set.isBlank())
            
            // Il filtro tipo si applica solo nel contesto Pokémon.
            val matchesType = when {
                uiState.selectedType == null -> true
                uiState.supertypeFilter == SupertypeFilter.TRAINER || uiState.supertypeFilter == SupertypeFilter.ENERGY -> true
                // Le poche carte a doppio tipo hanno type = "Tipo1, Tipo2" (una sola stringa):
                // va confrontato ogni tipo separatamente, altrimenti nessuna chip le trova mai
                // (translateType() cerca la stringa intera nella mappa e non trova nulla).
                else -> card.type.split(",").any { singleType ->
                    AppLocale.translateType(singleType.trim()).equals(uiState.selectedType, ignoreCase = true)
                }
            }
            
            val matchesRarity = uiState.selectedRarity == null ||
                card.rarity.equals(uiState.selectedRarity, ignoreCase = true)

            val supertypeLower = card.supertype.lowercase()
            val typeLower = card.type.lowercase()
            val subtypeLower = card.subtypes.map { it.lowercase() }
            val trainerMarkers = listOf("trainer", "allenat", "supporter", "item", "stadium", "stadio", "tool", "strumento", "aiuto")
            val energyMarkers = listOf("energy", "energia", "energ")
            val hasTrainerMarkers = trainerMarkers.any { marker ->
                supertypeLower.contains(marker) || typeLower.contains(marker) || subtypeLower.any { it.contains(marker) }
            }
            val hasEnergyMarkers = energyMarkers.any { marker ->
                supertypeLower.contains(marker) || typeLower.contains(marker) || subtypeLower.any { it.contains(marker) }
            }

            val matchesSupertype = when (uiState.supertypeFilter) {
                SupertypeFilter.ALL -> true
                SupertypeFilter.POKEMON -> !hasTrainerMarkers && !hasEnergyMarkers && card.classify() == "Pokémon"
                SupertypeFilter.TRAINER -> hasTrainerMarkers || card.classify() == "Trainer"
                SupertypeFilter.ENERGY -> hasEnergyMarkers || card.classify() == "Energy"
            }

            matchesQuery && matchesSet && matchesType && matchesRarity && matchesSupertype
        }

        return when (uiState.sortOrder) {
            SortOrder.NEWEST -> filtered.reversed() // Assumendo che l'ordine originale sia cronologico (addedAt)
            SortOrder.PRICE_ASC -> filtered.sortedBy { it.estimatedValue }
            SortOrder.PRICE_DESC -> filtered.sortedByDescending { it.estimatedValue }
            SortOrder.NAME_ASC -> filtered.sortedBy { it.name }
            SortOrder.NUMBER -> filtered.sortedBy { it.cardNumber.toIntOrNull() ?: Int.MAX_VALUE }
        }
    }
}
