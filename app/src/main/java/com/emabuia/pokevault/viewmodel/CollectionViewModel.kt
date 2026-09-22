package com.emabuia.pokevault.viewmodel

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.CollectionStats
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.collectionCardKey
import com.emabuia.pokevault.data.model.collectionGroupKey
import com.emabuia.pokevault.data.remote.CatalogRepository
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.CardCategory
import com.emabuia.pokevault.util.CardGroup
import com.emabuia.pokevault.util.CollectionBrowser
import com.emabuia.pokevault.util.CollectionFacets
import com.emabuia.pokevault.util.CollectionFilter
import com.emabuia.pokevault.util.CollectionLayout
import com.emabuia.pokevault.util.CollectionSort
import com.emabuia.pokevault.util.ExpansionGroupSection
import com.emabuia.pokevault.util.ExpansionOrder
import com.emabuia.pokevault.util.ValueBucket
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CollectionUiState(
    val cards: List<PokemonCard> = emptyList(),
    /** Tutte le carte, una per tessera. */
    val groups: List<CardGroup> = emptyList(),
    /** Quelle che passano i filtri, gia' ordinate. */
    val visibleGroups: List<CardGroup> = emptyList(),
    /** Le stesse, divise per espansione. */
    val sections: List<ExpansionGroupSection> = emptyList(),
    val facets: CollectionFacets = CollectionFacets(),
    val stats: CollectionStats = CollectionStats(),
    val isLoading: Boolean = true,
    val isGridView: Boolean = true,
    val gridColumns: Int = 4,
    val layout: CollectionLayout = CollectionLayout.BY_EXPANSION,
    val filter: CollectionFilter = CollectionFilter(),
    val sort: CollectionSort = CollectionSort.NUMBER,
    val expansionOrder: ExpansionOrder = ExpansionOrder.NAME,
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    val searchQuery: String get() = filter.query
    val visibleQuantity: Int get() = visibleGroups.sumOf { it.totalQuantity }
    val visibleValue: Double get() = visibleGroups.sumOf { it.totalValue }
}

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

    private var recomputeJob: Job? = null
    private var hydrationJob: Job? = null
    private var prefs: SharedPreferences? = null

    init {
        loadCards()
    }

    // ── Preferenze di vista ────────────────────────────────────────────────

    /**
     * Vista, colonne e ordinamenti sopravvivono al riavvio. Prima si tornava
     * ogni volta a quattro colonne e ordine per numero, e chi usa le tre
     * colonne doveva riimpostarle a ogni apertura dell'app.
     *
     * I filtri invece NO: riaprire l'app e trovarsi meta' collezione nascosta
     * da un filtro dimenticato sembra un bug.
     */
    fun attachPreferences(sharedPreferences: SharedPreferences) {
        if (prefs != null) return
        prefs = sharedPreferences
        val restored = uiState.copy(
            isGridView = sharedPreferences.getBoolean(PREF_GRID, uiState.isGridView),
            gridColumns = sharedPreferences.getInt(PREF_COLUMNS, uiState.gridColumns).coerceIn(2, 6),
            layout = enumOr(sharedPreferences.getString(PREF_LAYOUT, null), uiState.layout),
            sort = enumOr(sharedPreferences.getString(PREF_SORT, null), uiState.sort),
            expansionOrder = enumOr(sharedPreferences.getString(PREF_EXPANSION_ORDER, null), uiState.expansionOrder)
        )
        if (restored != uiState) {
            uiState = restored
            scheduleRecompute()
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private fun savePreferences() {
        prefs?.edit()
            ?.putBoolean(PREF_GRID, uiState.isGridView)
            ?.putInt(PREF_COLUMNS, uiState.gridColumns)
            ?.putString(PREF_LAYOUT, uiState.layout.name)
            ?.putString(PREF_SORT, uiState.sort.name)
            ?.putString(PREF_EXPANSION_ORDER, uiState.expansionOrder.name)
            ?.apply()
    }

    // ── Caricamento ────────────────────────────────────────────────────────

    private fun loadCards() {
        viewModelScope.launch {
            repository.getCards()
                .catch { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "${AppLocale.errorPrefix}: ${error.message}"
                    )
                }
                .collect { cards ->
                    applyCardsSnapshot(cards)

                    scheduleMissingPriceHydration(cards)
                    fixLegacyExpansionCodes(cards)
                }
        }
    }

    /**
     * Un nuovo elenco di carte: si ricostruiscono le tessere e si riapplicano
     * i filtri, tutto fuori dal thread principale.
     *
     * Se mentre si calcolava l'utente ha cambiato un filtro o scritto nella
     * ricerca, il risultato e' gia' vecchio: si rifa' coi criteri nuovi invece
     * di mostrarlo. Prima uno snapshot arrivato a meta' di una ricerca poteva
     * rimettere a schermo i risultati della ricerca precedente.
     */
    private suspend fun applyCardsSnapshot(cards: List<PokemonCard>) {
        val criteria = uiState
        val unknown = AppLocale.unknownExpansion
        val computed = withContext(Dispatchers.Default) {
            val stats = CollectionStats(
                totalCards = cards.sumOf { it.quantity },
                // Carte diverse, non stampe diverse: la collezione mostra una
                // tessera per carta, e contare separatamente Normale e Reverse
                // faceva dire "2" dove sullo schermo se ne vede una. Stesso
                // calcolo di FirestoreRepository.getStats(): vanno cambiati
                // insieme, o Statistiche e Collezione dicono due numeri diversi.
                uniqueCards = cards.map { it.collectionCardKey() }.toSet().size,
                totalValue = cards.sumOf { it.estimatedValue * it.quantity }
            )
            val groups = CollectionBrowser.group(cards, unknown)
            val visible = CollectionBrowser.sort(CollectionBrowser.filter(groups, criteria.filter), criteria.sort)
            Computed(
                stats = stats,
                groups = groups,
                facets = CollectionBrowser.facets(groups),
                visible = visible,
                sections = CollectionBrowser.sections(visible, criteria.expansionOrder)
            )
        }

        val criteriaChanged = uiState.filter != criteria.filter ||
            uiState.sort != criteria.sort ||
            uiState.expansionOrder != criteria.expansionOrder

        uiState = uiState.copy(
            cards = cards,
            groups = computed.groups,
            facets = computed.facets,
            stats = computed.stats,
            visibleGroups = if (criteriaChanged) uiState.visibleGroups else computed.visible,
            sections = if (criteriaChanged) uiState.sections else computed.sections,
            isLoading = false
        )
        if (criteriaChanged) scheduleRecompute()
    }

    private class Computed(
        val stats: CollectionStats,
        val groups: List<CardGroup>,
        val facets: CollectionFacets,
        val visible: List<CardGroup>,
        val sections: List<ExpansionGroupSection>
    )

    /**
     * Riapplica filtri e ordinamenti alle tessere gia' costruite.
     *
     * Una modifica nuova annulla il calcolo in corso e riparte coi criteri
     * aggiornati: a schermo arriva sempre il risultato dell'ultima scelta, mai
     * quello di una intermedia.
     */
    private fun scheduleRecompute(debounceMs: Long = 0L) {
        recomputeJob?.cancel()
        recomputeJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val snapshot = uiState
            val (visible, sections) = withContext(Dispatchers.Default) {
                val v = CollectionBrowser.sort(CollectionBrowser.filter(snapshot.groups, snapshot.filter), snapshot.sort)
                v to CollectionBrowser.sections(v, snapshot.expansionOrder)
            }
            uiState = uiState.copy(visibleGroups = visible, sections = sections)
        }
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

    // ── Filtri ─────────────────────────────────────────────────────────────

    private fun updateFilter(debounceMs: Long = 0L, change: (CollectionFilter) -> CollectionFilter) {
        uiState = uiState.copy(filter = change(uiState.filter))
        scheduleRecompute(debounceMs)
    }

    fun updateSearchQuery(query: String) {
        // Con debounce: ogni tasto non deve rifiltrare l'intera collezione.
        updateFilter(SEARCH_DEBOUNCE_MS) { it.copy(query = query) }
    }

    fun setCategory(category: CardCategory) = updateFilter {
        // I tipi (Fuoco/Acqua/...) esistono solo sui Pokemon: scegliendo
        // Allenatori o Energie restavano attivi e svuotavano la lista.
        val keepTypes = category == CardCategory.ALL || category == CardCategory.POKEMON
        it.copy(category = category, types = if (keepTypes) it.types else emptySet())
    }

    fun toggleType(type: String) = updateFilter { it.copy(types = it.types.toggle(type)) }
    fun toggleRarity(rarity: String) = updateFilter { it.copy(rarities = it.rarities.toggle(rarity)) }
    fun toggleExpansion(expansion: String) = updateFilter { it.copy(expansions = it.expansions.toggle(expansion)) }
    fun toggleVariant(variant: String) = updateFilter { it.copy(variants = it.variants.toggle(variant)) }
    fun toggleLanguage(language: String) = updateFilter { it.copy(languages = it.languages.toggle(language)) }
    fun toggleValue(bucket: ValueBucket) = updateFilter { it.copy(values = it.values.toggle(bucket)) }
    fun setOnlyDuplicates(only: Boolean) = updateFilter { it.copy(onlyDuplicates = only) }

    /** Azzera i filtri, non la ricerca: quella si vede e si cancella da sola. */
    fun clearFilters() = updateFilter { CollectionFilter(query = it.query) }

    /** Tutto, ricerca compresa: dallo stato "nessun risultato". */
    fun clearFiltersAndSearch() = updateFilter { CollectionFilter() }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

    // ── Ordinamento e vista ────────────────────────────────────────────────

    fun setSort(sort: CollectionSort) {
        if (sort == uiState.sort) return
        uiState = uiState.copy(sort = sort)
        savePreferences()
        scheduleRecompute()
    }

    fun setExpansionOrder(order: ExpansionOrder) {
        if (order == uiState.expansionOrder) return
        uiState = uiState.copy(expansionOrder = order)
        savePreferences()
        scheduleRecompute()
    }

    fun setLayout(layout: CollectionLayout) {
        if (layout == uiState.layout) return
        uiState = uiState.copy(layout = layout)
        savePreferences()
    }

    /**
     * Dal "Vedi tutte" della Home: tutte le carte, le ultime aggiunte in cima.
     * I filtri si azzerano, o un filtro dimenticato nasconderebbe proprio le
     * carte che si e' venuti a vedere.
     */
    fun showRecentFirst() {
        uiState = uiState.copy(
            layout = CollectionLayout.ALL,
            sort = CollectionSort.NEWEST,
            filter = CollectionFilter()
        )
        savePreferences()
        scheduleRecompute()
    }

    fun toggleViewMode() {
        uiState = uiState.copy(isGridView = !uiState.isGridView)
        savePreferences()
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
        savePreferences()
    }

    // ── Cancellazione ──────────────────────────────────────────────────────

    fun deleteCard(cardId: String) {
        viewModelScope.launch {
            repository.deleteCard(cardId)
                .onSuccess {
                    uiState = uiState.copy(successMessage = AppLocale.cardDeleted)
                }
                .onFailure { error ->
                    uiState = uiState.copy(errorMessage = "${AppLocale.errorPrefix}: ${error.message}")
                }
        }
    }

    /**
     * Cancella per chiave, accettando tutte e due le forme.
     *
     * La collezione raggruppa per carta ([collectionCardKey]) e non piu' per
     * singola stampa, quindi le chiavi che arrivano da li' non contengono la
     * variante: cancellare una tessera vuol dire togliere tutte le sue stampe.
     * Le chiavi con la variante restano valide -- le usa chi vuole togliere
     * una stampa sola.
     */
    fun deleteMultipleGroups(groupKeys: Set<String>) {
        viewModelScope.launch {
            val originalCards = uiState.cards
            val cardsToDelete = originalCards.filter { card ->
                card.collectionGroupKey() in groupKeys || card.collectionCardKey() in groupKeys
            }
            if (cardsToDelete.isEmpty()) return@launch

            val deletedIds = cardsToDelete.map { it.id }.toSet()
            val remainingCards = originalCards.filterNot { card -> card.id in deletedIds }
            applyCardsSnapshot(remainingCards)

            repository.deleteCards(cardsToDelete)
                .onSuccess {
                    uiState = uiState.copy(successMessage = AppLocale.cardsDeleted(cardsToDelete.size))
                }
                .onFailure { error ->
                    uiState = uiState.copy(errorMessage = "${AppLocale.errorPrefix}: ${error.message}")
                    applyCardsSnapshot(originalCards)
                }
        }
    }

    fun clearMessages() {
        uiState = uiState.copy(errorMessage = null, successMessage = null)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val PRICE_HYDRATION_BATCH_SIZE = 8
        const val PREF_GRID = "collection_grid"
        const val PREF_COLUMNS = "collection_columns"
        const val PREF_LAYOUT = "collection_layout"
        const val PREF_SORT = "collection_sort"
        const val PREF_EXPANSION_ORDER = "collection_expansion_order"
    }
}
