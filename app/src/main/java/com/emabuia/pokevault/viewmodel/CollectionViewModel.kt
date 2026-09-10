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
import com.emabuia.pokevault.data.remote.PokeTcgRepository
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import com.emabuia.pokevault.data.model.CardClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val tcgRepository = PokeTcgRepository()
    // Synchronized perché emissioni rapide del Flow possono lanciare hydration concorrenti
    // e questi insiemi tracciano lo stato condiviso fra di esse.
    private val hydratedPriceCardIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val hydratingPriceCardIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    var uiState by mutableStateOf(CollectionUiState())
        private set

    private var filterJob: Job? = null
    private var hydrationJob: Job? = null

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
                    scheduleMissingPriceHydration(cards)
                }
        }
    }

    private suspend fun applyCardsSnapshot(cards: List<PokemonCard>) {
        val criteria = uiState
        // Statistiche e filtro scorrono l'intera collezione: su Dispatchers.Default,
        // non sul main thread come prima.
        val (newStats, filtered) = withContext(Dispatchers.Default) {
            val stats = CollectionStats(
                totalCards = cards.sumOf { it.quantity },
                uniqueCards = cards.map { it.collectionGroupKey() }.toSet().size,
                totalValue = cards.sumOf { it.estimatedValue * it.quantity }
            )
            stats to applyFilters(cards, criteria)
        }

        uiState = uiState.copy(
            cards = cards,
            filteredCards = filtered,
            stats = newStats,
            isLoading = false
        )
    }

    /**
     * Recupera i prezzi mancanti, un lotto alla volta.
     *
     * Girava dentro il collector dello snapshot, e ogni updateCard provoca un
     * nuovo snapshot: era un ciclo di amplificazione delle scritture, tenuto a
     * bada solo dai due insiemi di guardia. Ora un lotto alla volta: finche' il
     * giro precedente e' in corso i nuovi snapshot non ne avviano altri, e il
     * successivo riparte dallo snapshot che il giro stesso ha prodotto.
     */
    private fun scheduleMissingPriceHydration(cards: List<PokemonCard>) {
        if (hydrationJob?.isActive == true) return

        val candidates = cards
            .filter { card ->
                card.estimatedValue <= 0.0 &&
                    card.apiCardId.isNotBlank() &&
                    card.id !in hydratedPriceCardIds &&
                    card.id !in hydratingPriceCardIds
            }
            .take(PRICE_HYDRATION_BATCH_SIZE)

        if (candidates.isEmpty()) return

        hydrationJob = viewModelScope.launch {
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

    fun updateSearchQuery(query: String) {
        uiState = uiState.copy(searchQuery = query)
        // Con debounce: prima ogni tasto premuto rifiltrava e riordinava l'intera
        // collezione in modo sincrono sul main thread.
        refreshFilteredCards(debounceMs = SEARCH_DEBOUNCE_MS)
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

    private fun refreshFilteredCards(debounceMs: Long = 0L) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val source = uiState.cards
            val criteria = uiState
            val filtered = withContext(Dispatchers.Default) { applyFilters(source, criteria) }
            uiState = uiState.copy(filteredCards = filtered)
        }
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

    /**
     * Filtro e ordinamento della collezione.
     *
     * Prende i criteri come parametro invece di leggere uiState: viene eseguita
     * su Dispatchers.Default, e leggere lo stato da un altro thread mentre
     * l'utente continua a digitare avrebbe potuto mischiare criteri di due
     * ricerche diverse a meta' calcolo.
     */
    private fun applyFilters(
        cards: List<PokemonCard>,
        criteria: CollectionUiState
    ): List<PokemonCard> {
        val query = criteria.searchQuery
        val hasQuery = query.isNotBlank()
        // Normalizzazioni e liste di marcatori sollevate fuori dal loop: prima
        // venivano ricostruite per ogni carta a ogni tasto premuto.
        val selectedSetNormalized = criteria.selectedSet?.let { normalizeSetForFilter(it) }
        val unknownSetNormalized = normalizeSetForFilter("Espansione sconosciuta")

        val filtered = cards.filter { card ->
            val matchesQuery = !hasQuery ||
                card.name.contains(query, ignoreCase = true) ||
                card.set.contains(query, ignoreCase = true) ||
                card.rarity.contains(query, ignoreCase = true)

            val matchesSet = selectedSetNormalized == null ||
                selectedSetNormalized == normalizeSetForFilter(card.set) ||
                (selectedSetNormalized == unknownSetNormalized && card.set.isBlank())

            // Il filtro tipo si applica solo nel contesto Pokemon.
            val matchesType = when {
                criteria.selectedType == null -> true
                criteria.supertypeFilter == SupertypeFilter.TRAINER ||
                    criteria.supertypeFilter == SupertypeFilter.ENERGY -> true
                else -> AppLocale.translateType(card.type).equals(criteria.selectedType, ignoreCase = true)
            }

            val matchesRarity = criteria.selectedRarity == null ||
                card.rarity.equals(criteria.selectedRarity, ignoreCase = true)

            val matchesSupertype = when (criteria.supertypeFilter) {
                SupertypeFilter.ALL -> true
                SupertypeFilter.POKEMON -> card.classify() == CardClassifier.POKEMON
                SupertypeFilter.TRAINER -> card.classify() == CardClassifier.TRAINER
                SupertypeFilter.ENERGY -> card.classify() == CardClassifier.ENERGY
            }

            matchesQuery && matchesSet && matchesType && matchesRarity && matchesSupertype
        }

        return when (criteria.sortOrder) {
            // getCards() non ha un orderBy, quindi Firestore restituisce ordine di
            // document-id: il precedente reversed() "assumendo che l'ordine sia
            // cronologico" produceva un ordinamento di fatto arbitrario.
            // L'ordinamento resta lato client di proposito: un orderBy("addedAt")
            // su Firestore ESCLUDE i documenti che non hanno il campo, e i record
            // storici possono non averlo. Qui invece finiscono in fondo.
            SortOrder.NEWEST -> filtered.sortedByDescending { it.addedAt?.seconds ?: Long.MIN_VALUE }
            SortOrder.PRICE_ASC -> filtered.sortedBy { it.estimatedValue }
            SortOrder.PRICE_DESC -> filtered.sortedByDescending { it.estimatedValue }
            SortOrder.NAME_ASC -> filtered.sortedBy { it.name }
            SortOrder.NUMBER -> filtered.sortedWith(cardNumberComparator)
        }
    }

    /**
     * Ordinamento per numero di carta allineato a quello della vista raggruppata
     * (CollectionScreen): la parte numerica si confronta come numero, il resto
     * come testo. Il precedente sortedBy { cardNumber.toIntOrNull() ?: MAX_VALUE }
     * ammassava insieme ogni numero non puramente numerico ("TG12", "025/198"),
     * e dava un ordine diverso da quello mostrato nella vista per espansione.
     */
    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val PRICE_HYDRATION_BATCH_SIZE = 8
    }

    private val cardNumberComparator = compareBy<PokemonCard>(
        { it.cardNumber.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE },
        { it.cardNumber }
    )
}
