package com.emabuia.pokevault.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.italian.ItalianCardFacets
import com.emabuia.pokevault.data.italian.ItalianCardSearchFilter
import com.emabuia.pokevault.data.italian.ItalianExpansionFacet
import com.emabuia.pokevault.data.italian.ItalianHpBucket
import com.emabuia.pokevault.data.italian.ItalianPriceBucket
import com.emabuia.pokevault.data.italian.ItalianSearchFacets
import com.emabuia.pokevault.data.local.ItalianTranslations
import com.emabuia.pokevault.data.remote.CardMarket
import com.emabuia.pokevault.data.remote.CardMarketPrices
import com.emabuia.pokevault.data.remote.CatalogRepository
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgPlayer
import com.emabuia.pokevault.data.remote.TcgPriceInfo
import com.emabuia.pokevault.data.remote.TcgSet
import com.emabuia.pokevault.data.remote.TranslationService
import com.emabuia.pokevault.util.hasPositiveEurPrice
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private const val OTHER_SERIES_KEY = "Other"
// La parte dopo la barra puo' essere vuota: digitando "001/217" si passa per
// "001/", e trattarlo come un nome farebbe lampeggiare "nessun risultato" a
// meta' della battitura.
private val FLEX_CARD_NUMBER_REGEX = Regex("""^\s*0*\d+\s*/\s*0*\d*\s*$""")

/** Solo cifre: "67", "067". Vale come numero di carta, non come nome. */
private val BARE_CARD_NUMBER_REGEX = Regex("""^\s*\d{1,4}\s*$""")

/** Una ricerca a numero: il numero stampato e, se indicato, il totale del set. */
internal data class CardNumberQuery(val number: String, val printedTotal: Int?)

data class SeriesSetsGroup(
    val seriesKey: String,
    val seriesLabel: String,
    val sets: List<TcgSet>
)

data class SetsUiState(
    val allSets: List<TcgSet> = emptyList(),
    val seriesGroups: List<SeriesSetsGroup> = emptyList(),
    val searchQuery: String = "",
    val cardSearchQuery: String = "",
    /** True quando si sta cercando per numero: la vista non deve riordinare. */
    val isCardNumberSearch: Boolean = false,
    /** Il totale digitato non corrisponde a nessuna espansione che conosciamo. */
    val unrecognizedPrintedTotal: Int? = null,
    /** Le dimensioni che la ricerca applica record per record, prima del taglio. */
    val cardFilter: ItalianCardSearchFilter = ItalianCardSearchFilter(),
    /** Le serie selezionate: in ricerca diventano le espansioni che contengono. */
    val cardSeriesFilter: Set<String> = emptySet(),
    /** Fasce di prezzo: stanno a parte perche' il prezzo non e' nel catalogo. */
    val cardPriceFilter: Set<ItalianPriceBucket> = emptySet(),
    /** Il vocabolario dei filtri, dal catalogo: c'e' anche prima di scrivere. */
    val searchFacets: ItalianSearchFacets = ItalianSearchFacets(),
    val searchedCards: List<TcgCard> = emptyList(),
    val isSearchingCards: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isAddingCard: String? = null,
    val successMessage: String? = null
) {
    /** Quante voci di filtro sono accese, su tutte le dimensioni. */
    val activeCardFilterCount: Int
        get() = cardFilter.activeCount + cardSeriesFilter.size + cardPriceFilter.size

    val hasActiveCardFilters: Boolean get() = activeCardFilterCount > 0
}

// Usa AndroidViewModel per accedere al Context
class SetsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RepositoryProvider.tcgRepository
    private val firestoreRepository = FirestoreRepository()
    private var searchJob: Job? = null
    private var setSearchJob: Job? = null
    private var lastUnfilteredSearchCards: List<TcgCard> = emptyList()

    /** Il vocabolario di tutto il catalogo: si usa solo finche' non c'e' una ricerca. */
    private var catalogFacets: ItalianSearchFacets? = null

    var uiState by mutableStateOf(SetsUiState())
        private set

    private var lastRevalidationAtMs: Long = 0L

    private companion object {
        /**
         * Quante corrispondenze si pescano per una ricerca.
         *
         * Piu' larga di quante se ne mostrano (60) perche' e' l'insieme su cui si
         * costruiscono le voci del pannello e su cui mordono i filtri: se fosse
         * gia' tagliata, filtrare per rarita' lascerebbe le briciole.
         */
        const val CARD_SEARCH_FETCH_LIMIT = 300

        /** Quante carte finiscono davvero in lista. */
        const val CARD_SEARCH_RESULT_LIMIT = 60

        /** Finestra minima fra due rivalidazioni di rete del catalogo set. */
        const val REVALIDATION_MIN_INTERVAL_MS = 15L * 60 * 1000

        /**
         * Costruiti una volta sola: prima venivano ricreati a ogni chiamata di
         * parseReleaseDate, che il comparatore invoca due volte per confronto.
         */
        val ORDINAL_SUFFIX_REGEX = Regex("""(\d+)(st|nd|rd|th)""")
        val LONG_DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMMM, uuuu", Locale.ENGLISH)
        val SHORT_DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)

        /** Riconosce "uuuu-MM-dd" senza far lanciare e catturare un'eccezione. */
        val ISO_DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")
    }

    init {
        TranslationService.loadCache(application.applicationContext)
        loadSets()
    }

    private fun loadSets() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            val context = getApplication<Application>().applicationContext
            withContext(Dispatchers.IO) { repository.getSets(context = context) }
                .onSuccess { sets ->
                    uiState = uiState.copy(
                        allSets = sets,
                        errorMessage = null,
                        isLoading = false
                    )
                    applyFilters()
                    revalidateSetsFromNetwork(context)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = buildSetsErrorMessage(error)
                    )
                }
        }
    }

    fun updateSearch(query: String) {
        uiState = uiState.copy(searchQuery = query)
        setSearchJob?.cancel()
        setSearchJob = viewModelScope.launch {
            delay(300)
            applyFilters()
        }
    }

    fun refreshFromCache() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            withContext(Dispatchers.IO) { repository.getSets(context = context) }
                .onSuccess { sets ->
                    uiState = uiState.copy(
                        allSets = sets,
                        isLoading = false,
                        errorMessage = null
                    )
                    applyFilters()
                    revalidateSetsFromNetwork(context)
                }
        }
    }

    /**
     * Rivalidazione di rete del catalogo set, con finestra minima.
     *
     * Lo schermo chiama refreshFromCache() a ogni ON_RESUME, e sia quello sia
     * loadSets() finivano qui con forceRefresh = true: erano due round-trip
     * /sets ogni volta che l'utente tornava sul Pokedex, il che annullava di
     * fatto la cache da 7 giorni del repository.
     *
     * refresh() (pull-to-refresh esplicito) resta invece sempre forzato: la'
     * l'utente sta chiedendo esplicitamente dati freschi.
     */
    private fun revalidateSetsFromNetwork(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRevalidationAtMs < REVALIDATION_MIN_INTERVAL_MS) return
        lastRevalidationAtMs = now

        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.getSets(context = context, forceRefresh = true) }
                .onSuccess { freshSets ->
                    if (!hasSetCatalogChanged(uiState.allSets, freshSets)) return@onSuccess
                    uiState = uiState.copy(
                        allSets = freshSets,
                        errorMessage = null,
                        isLoading = false
                    )
                    applyFilters()
                }
        }
    }

    private fun hasSetCatalogChanged(current: List<TcgSet>, incoming: List<TcgSet>): Boolean {
        if (current.size != incoming.size) return true
        val currentKey = current
            .map { Triple(it.id, it.language, it.releaseDate) }
            .sortedBy { it.first }
        val incomingKey = incoming
            .map { Triple(it.id, it.language, it.releaseDate) }
            .sortedBy { it.first }
        return currentKey != incomingKey
    }

    fun refresh() {
        // Pull-to-refresh esplicito: sempre forzato, e vale come rivalidazione
        // appena avvenuta per la finestra di revalidateSetsFromNetwork.
        lastRevalidationAtMs = System.currentTimeMillis()
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            val context = getApplication<Application>().applicationContext
            withContext(Dispatchers.IO) { repository.getSets(context = context, forceRefresh = true) }
                .onSuccess { sets ->
                    uiState = uiState.copy(
                        allSets = sets,
                        errorMessage = null,
                        isLoading = false
                    )
                    applyFilters()
                }
                .onFailure {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = buildSetsErrorMessage(it)
                    )
                }
        }
    }

    /**
     * Carica il vocabolario di partenza, quello di tutto il catalogo.
     *
     * Serve solo a poter scegliere i filtri prima di aver scritto qualcosa:
     * appena c'e' una ricerca il pannello si ricostruisce su quei risultati
     * (vedi [facetsOf]). Si chiama entrando nella ricerca, non all'avvio: chi
     * apre il Pokedex per sfogliare le espansioni non deve pagare una scansione
     * del catalogo che non gli serve.
     */
    fun loadSearchFacets() {
        if (catalogFacets != null) return
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            repository.getItalianSearchFacets(context)
                .onSuccess { facets ->
                    catalogFacets = facets
                    // Stesso limite del gate di ricerca: con una lettera sola non si
                    // cerca ancora niente, quindi il pannello deve mostrare il
                    // vocabolario del catalogo, non restare vuoto.
                    if (uiState.cardSearchQuery.trim().length < 2) {
                        uiState = uiState.copy(searchFacets = facets)
                    }
                }
        }
    }

    /**
     * Ricerca carte sul catalogo italiano completo.
     *
     * Ci vuole un nome (o un numero): i filtri restringono quello che si e'
     * cercato, non lo sostituiscono. Accendere "Pokémon" da solo non vuol dire
     * "dammi i quindicimila Pokémon del catalogo" -- vuol dire "fra le carte che
     * ho cercato, tienimi i Pokémon". Sceglierli prima di scrivere resta
     * possibile: restano accesi e mordono appena parte la ricerca.
     *
     * Non c'e' piu' una modalita' "match esatto" da accendere a mano: il nome
     * identico vince comunque, perche' e' il punteggio piu' alto della scala di
     * ranking.
     *
     * Una query fatta di cifre ("001/217", "67") non e' un nome e non va cercata
     * fra i nomi: prende la strada di [searchItalianScannerCandidates], che sa
     * confrontare il numero stampato e il totale dell'espansione.
     */
    fun searchCardsByName(query: String) {
        uiState = uiState.copy(cardSearchQuery = query)
        runCardSearch()
    }

    /** Rilancia la ricerca con la query corrente: la usano i filtri quando cambiano. */
    private fun runCardSearch() {
        searchJob?.cancel()

        val query = uiState.cardSearchQuery
        if (query.trim().length < 2) {
            // Senza nome non c'e' niente da restringere. Il pannello torna al
            // vocabolario del catalogo cosi' i filtri restano scegliibili in
            // anticipo, ma nessun risultato compare finche' non si scrive.
            lastUnfilteredSearchCards = emptyList()
            uiState = uiState.copy(
                searchedCards = emptyList(),
                isSearchingCards = false,
                isCardNumberSearch = false,
                unrecognizedPrintedTotal = null,
                searchFacets = catalogFacets ?: ItalianSearchFacets()
            )
            return
        }

        searchJob = viewModelScope.launch {
            delay(250)
            uiState = uiState.copy(isSearchingCards = true)
            val context = getApplication<Application>().applicationContext

            // ITA-only: PokeWallet's direct/translated English search used to run in
            // parallel and get merged in, so results mixed our ITA cards with raw
            // English PokeWallet ones. Besides being the reported cause of "search
            // doesn't find cards well", that burned PokeWallet request budget on every
            // keystroke -- budget that should go only to prices (see MIGRATION_PLAN.md M4.5).
            //
            // Si pesca largo (300) e si restringe qui: e' l'insieme su cui si
            // costruiscono le voci del pannello, quindi deve essere quello che
            // la ricerca trova davvero, non gia' tagliato dai filtri.
            val numberQuery = parseCardNumberQuery(query)
            val found = if (numberQuery != null) {
                // Numero e totale li confronta il repository, che e' l'unico ad
                // avere tutti i totali noti di ogni espansione. Qui non si
                // riordina: rimescolare per somiglianza di nome delle cifre
                // rovinerebbe l'ordine buono.
                repository.searchItalianCardsByNumber(
                    number = numberQuery.number,
                    printedTotal = numberQuery.printedTotal,
                    context = context,
                    limit = CARD_SEARCH_FETCH_LIMIT
                ).getOrDefault(emptyList())
            } else {
                val byName = repository.searchItalianCardsByName(
                    query = query,
                    context = context,
                    limit = CARD_SEARCH_FETCH_LIMIT
                ).getOrDefault(emptyList())
                // Ordinare per somiglianza normalizza il nome a ogni confronto:
                // su trecento carte e' lavoro che sul thread della UI si vedrebbe.
                withContext(Dispatchers.Default) { rankCardSearchResults(query, byName) }
            }

            val matched = enrichItalianCardsWithSnapshotPrices(found, context)
            lastUnfilteredSearchCards = matched

            // Anche filtrare e ricostruire il pannello sono passate su trecento
            // elementi: fuori dal main, resta sul main la sola assegnazione.
            val filtered = withContext(Dispatchers.Default) {
                applyCardFilters(matched).take(CARD_SEARCH_RESULT_LIMIT) to facetsOf(matched)
            }

            uiState = uiState.copy(
                searchedCards = filtered.first,
                searchFacets = filtered.second,
                isCardNumberSearch = numberQuery != null,
                // Se il totale digitato non compare in nessun risultato vuol dire
                // che non lo conosciamo: la lista che segue sono tutte le carte
                // con quel numero, e va detto invece di farla passare per la
                // risposta esatta.
                unrecognizedPrintedTotal = numberQuery?.printedTotal
                    ?.takeIf { typed -> matched.none { it.set?.printedTotal == typed } },
                isSearchingCards = false
            )
        }
    }

    /**
     * Riapplica i filtri senza rifare la ricerca.
     *
     * Toccare un chip non cambia cosa si sta cercando, solo cosa se ne tiene:
     * rilanciare la scansione del catalogo per questo sarebbe lavoro sprecato, e
     * si vedrebbe come un lampeggio della lista a ogni tocco. Il pannello non si
     * ricostruisce: le voci restano quelle dei risultati della ricerca, altrimenti
     * accendere un filtro farebbe sparire gli altri e non si tornerebbe indietro.
     */
    private fun reapplyCardFilters() {
        if (uiState.cardSearchQuery.trim().length < 2) return
        uiState = uiState.copy(
            searchedCards = applyCardFilters(lastUnfilteredSearchCards).take(CARD_SEARCH_RESULT_LIMIT)
        )
    }

    private fun applyCardFilters(cards: List<TcgCard>): List<TcgCard> {
        val filter = uiState.cardFilter
        val series = uiState.cardSeriesFilter
        val priceBuckets = uiState.cardPriceFilter
        if (filter.isEmpty && series.isEmpty() && priceBuckets.isEmpty()) return cards

        return cards.filter { card ->
            cardMatchesFilter(card, filter) &&
                (series.isEmpty() || cardSeriesOf(card) in series) &&
                (priceBuckets.isEmpty() || cardPriceBucketOf(card) in priceBuckets)
        }
    }

    /**
     * Le voci del pannello, ricavate dai risultati della ricerca.
     *
     * E' il punto della faccenda: cercando "Charizard" il pannello offre le
     * rarita' e le espansioni dei Charizard, non quelle dell'intero catalogo.
     * Si costruisce sempre sull'insieme *non* filtrato, cosi' accendere un chip
     * non fa sparire gli altri lasciando l'utente in un vicolo cieco.
     */
    private fun facetsOf(cards: List<TcgCard>): ItalianSearchFacets {
        if (cards.isEmpty()) return ItalianSearchFacets()

        val counts = mutableMapOf<String, Int>()
        fun bump(dimension: String, value: String) {
            if (value.isBlank()) return
            counts["$dimension:$value"] = (counts["$dimension:$value"] ?: 0) + 1
        }

        val expansionCounts = mutableMapOf<String, Int>()
        val expansionLabels = mutableMapOf<String, String>()
        val expansionSeries = mutableMapOf<String, String>()

        cards.forEach { card ->
            bump(ItalianCardFacets.DIMENSION_SUPERTYPE, card.supertype.trim())
            card.types.orEmpty().forEach { bump(ItalianCardFacets.DIMENSION_TYPE, it.trim()) }
            card.rarity?.trim()?.let { bump(ItalianCardFacets.DIMENSION_RARITY, it) }
            bump(ItalianCardFacets.DIMENSION_VARIANT, ItalianCardFacets.variantOfName(card.name))
            cardHpBucketOf(card)?.let { bump(ItalianCardFacets.DIMENSION_HP, it.key) }

            val expansionId = cardExpansionIdOf(card)
            if (expansionId.isNotBlank()) {
                expansionCounts[expansionId] = (expansionCounts[expansionId] ?: 0) + 1
                expansionLabels.putIfAbsent(expansionId, card.set?.name?.takeIf { it.isNotBlank() } ?: expansionId.uppercase(Locale.ROOT))
                expansionSeries.putIfAbsent(expansionId, cardSeriesOf(card))
            }
        }

        fun distinctFor(dimension: String): List<String> =
            counts.keys.asSequence()
                .filter { it.startsWith("$dimension:") }
                .map { it.removePrefix("$dimension:") }
                .sortedByDescending { counts["$dimension:$it"] ?: 0 }
                .toList()

        return ItalianSearchFacets(
            supertypes = distinctFor(ItalianCardFacets.DIMENSION_SUPERTYPE),
            types = distinctFor(ItalianCardFacets.DIMENSION_TYPE).sorted(),
            rarities = distinctFor(ItalianCardFacets.DIMENSION_RARITY),
            variants = distinctFor(ItalianCardFacets.DIMENSION_VARIANT),
            // Le fasce restano nell'ordine dell'enum, non per frequenza: sono una
            // scala, e una scala fuori ordine non si legge.
            hpBuckets = ItalianHpBucket.entries.filter { (counts["${ItalianCardFacets.DIMENSION_HP}:${it.key}"] ?: 0) > 0 },
            expansions = expansionCounts.entries
                .map { (id, count) ->
                    ItalianExpansionFacet(
                        id = id,
                        label = expansionLabels[id] ?: id.uppercase(Locale.ROOT),
                        series = expansionSeries[id].orEmpty(),
                        cardCount = count
                    )
                }
                .sortedWith(compareByDescending<ItalianExpansionFacet> { it.cardCount }.thenBy { it.label }),
            priceBuckets = ItalianPriceBucket.entries.filter { bucket ->
                cards.any { cardPriceBucketOf(it) == bucket }
            }
        )
    }

    fun toggleCardRarityFilter(rarity: String) {
        val current = uiState.cardFilter
        uiState = uiState.copy(cardFilter = current.copy(rarities = current.rarities.toggle(rarity)))
        reapplyCardFilters()
    }

    fun toggleCardTypeFilter(type: String) {
        val current = uiState.cardFilter
        uiState = uiState.copy(cardFilter = current.copy(types = current.types.toggle(type)))
        reapplyCardFilters()
    }

    fun toggleCardSupertypeFilter(supertype: String) {
        val current = uiState.cardFilter
        uiState = uiState.copy(cardFilter = current.copy(supertypes = current.supertypes.toggle(supertype)))
        reapplyCardFilters()
    }

    fun toggleCardVariantFilter(variant: String) {
        val current = uiState.cardFilter
        uiState = uiState.copy(cardFilter = current.copy(variants = current.variants.toggle(variant)))
        reapplyCardFilters()
    }

    fun toggleCardHpFilter(bucket: ItalianHpBucket) {
        val current = uiState.cardFilter
        uiState = uiState.copy(cardFilter = current.copy(hpBuckets = current.hpBuckets.toggle(bucket)))
        reapplyCardFilters()
    }

    fun toggleCardExpansionFilter(expansionId: String) {
        val current = uiState.cardFilter
        uiState = uiState.copy(cardFilter = current.copy(expansionIds = current.expansionIds.toggle(expansionId)))
        reapplyCardFilters()
    }

    fun toggleCardSeriesFilter(series: String) {
        uiState = uiState.copy(cardSeriesFilter = uiState.cardSeriesFilter.toggle(series))
        reapplyCardFilters()
    }

    fun toggleCardPriceFilter(bucket: ItalianPriceBucket) {
        uiState = uiState.copy(cardPriceFilter = uiState.cardPriceFilter.toggle(bucket))
        reapplyCardFilters()
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (value in this) this - value else this + value

    fun clearCardResultFilters() {
        uiState = uiState.copy(
            cardFilter = ItalianCardSearchFilter(),
            cardSeriesFilter = emptySet(),
            cardPriceFilter = emptySet()
        )
        reapplyCardFilters()
    }

    /**
     * Svuota il campo del nome.
     *
     * I filtri restano accesi: la X sta dentro il campo di testo, e azzerare da
     * li' anche una selezione fatta nel pannello sarebbe una sorpresa. Per
     * quelli c'e' "Azzera" nel pannello.
     */
    fun clearCardSearch() {
        uiState = uiState.copy(cardSearchQuery = "")
        runCardSearch()
    }

    fun addCardWithDetails(tcgCard: TcgCard, variant: String, quantity: Int, condition: String, language: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isAddingCard = tcgCard.id)
            val price = tcgCard.cardmarket?.prices.minimumEurPriceOrZero()

            val card = PokemonCard(
                name = tcgCard.name, imageUrl = tcgCard.images.small,
                set = tcgCard.set?.name ?: "",
                rarity = tcgCard.rarity ?: "Unknown",
                type = tcgCard.types?.firstOrNull() ?: "Colorless",
                hp = tcgCard.hp?.toIntOrNull() ?: 0,
                supertype = tcgCard.supertype.ifBlank { "Pokémon" },
                subtypes = tcgCard.subtypes ?: emptyList(),
                estimatedValue = price,
                apiCardId = tcgCard.id, cardNumber = tcgCard.number,
                variant = variant, quantity = quantity, condition = condition, language = language
            )
            firestoreRepository.addCard(card)
                .onSuccess { uiState = uiState.copy(isAddingCard = null, successMessage = "${tcgCard.name} aggiunta!") }
                .onFailure { uiState = uiState.copy(isAddingCard = null, errorMessage = "Errore") }
        }
    }

    fun clearMessages() {
        uiState = uiState.copy(successMessage = null, errorMessage = null)
    }

    /**
     * Filtra e raggruppa fuori dal main thread.
     *
     * Prima girava dentro viewModelScope.launch, che parte su Dispatchers.Main:
     * con ~107 espansioni il lavoro bloccava il thread della UI per centinaia
     * di millisecondi, ed e' quello che si vedeva come freeze aprendo il
     * Pokedex. Qui resta sul main solo l'assegnazione dello stato.
     */
    private suspend fun applyFilters() {
        val query = uiState.searchQuery
        val allSets = uiState.allSets

        val groups = withContext(Dispatchers.Default) {
            val displayableSets = allSets.filter(::isDisplayableExpansion)
            val italianSets = displayableSets.filter { it.language.equals("ITA", ignoreCase = true) }

            val searched = if (query.isBlank()) {
                italianSets
            } else {
                italianSets.filter { set ->
                    set.name.contains(query, ignoreCase = true) ||
                        set.series.contains(query, ignoreCase = true)
                }
            }

            buildSeriesGroups(searched)
        }

        uiState = uiState.copy(seriesGroups = groups)
    }

    private fun buildSetsErrorMessage(error: Throwable): String {
        return when (error) {
            is HttpException -> {
                when (error.code()) {
                    401, 403 -> "Accesso API non autorizzato. Verifica la chiave PokeWallet."
                    429 -> "Troppe richieste. Riprova tra qualche secondo."
                    else -> "Servizio non disponibile (${error.code()})."
                }
            }

            else -> {
                val raw = error.message?.trim().orEmpty()
                if (raw.isNotBlank()) "Errore: $raw"
                else "Errore durante il caricamento di carte ed espansioni."
            }
        }
    }

    /**
     * Groups ITA sets by series -- a real value from D1 (schema/005, TCGdex's own
     * `serie.name` taxonomy, see MIGRATION_PLAN.md M4.6), not the ~150 lines of
     * fuzzy name-matching this replaced. Series groups themselves are ordered by
     * their most recent set's release date, newest first, so the whole screen
     * reads "latest releases first" the way the user asked -- group and set
     * alike, and it self-updates when a new series shows up, no code change.
     */
    /**
     * Ordine di visualizzazione dentro un gruppo di serie:
     *   1. Data di uscita DESC (la piu' recente per prima).
     *   2. I set senza data interpretabile finiscono in fondo.
     *   3. Tiebreak stabile sul nome in minuscolo.
     *
     * Deliberatamente NON dipendente dallo stato di caricamento dei loghi (vedi
     * hasPrioritizedLogo): farlo ri-ordinava l'intera lista ogni volta che un
     * logo finiva o falliva il caricamento in background, ed e' cio' che faceva
     * "saltare" i set durante lo scroll. Un logo mancante mostra un
     * placeholder, e non deve mai cambiare la posizione di un set.
     */
    private fun buildSeriesGroups(italianSets: List<TcgSet>): List<SeriesSetsGroup> {
        if (italianSets.isEmpty()) return emptyList()

        // Data e nome normalizzato calcolati UNA volta per set. Prima il
        // comparatore chiamava parseReleaseDate due volte per ogni confronto e
        // rifaceva lowercase() a ogni tiebreak: su ~107 espansioni sono oltre
        // mille parse di data (piu' quelle di maxOfOrNull), tutte sul main
        // thread, ed e' il grosso del freeze all'apertura del Pokedex.
        val sortKeys = HashMap<String, Pair<LocalDate, String>>(italianSets.size)
        italianSets.forEach { set ->
            sortKeys[set.id] = parseReleaseDate(set.releaseDate) to set.name.lowercase(Locale.ROOT)
        }
        fun dateOf(set: TcgSet): LocalDate = sortKeys[set.id]?.first ?: LocalDate.MIN
        fun nameOf(set: TcgSet): String = sortKeys[set.id]?.second.orEmpty()

        val bySeries = italianSets
            .groupBy { it.series.ifBlank { ItalianTranslations.translateSeriesName(OTHER_SERIES_KEY) } }
            .map { (seriesLabel, sets) ->
                SeriesSetsGroup(
                    seriesKey = seriesLabel,
                    seriesLabel = seriesLabel,
                    sets = sets.sortedWith(
                        Comparator<TcgSet> { a, b ->
                            val da = dateOf(a)
                            val db = dateOf(b)
                            val aMissing = da == LocalDate.MIN
                            val bMissing = db == LocalDate.MIN
                            when {
                                aMissing && !bMissing -> 1
                                !aMissing && bMissing -> -1
                                else -> db.compareTo(da) // DESC per data di uscita
                            }
                        }.thenBy { nameOf(it) }
                    )
                )
            }

        // sortedByDescending valuta la chiave una volta per elemento, e le date
        // sono gia' in sortKeys: nessun altro parse.
        return bySeries.sortedByDescending { group ->
            group.sets.maxOfOrNull { dateOf(it) } ?: LocalDate.MIN
        }
    }

    private fun isDisplayableExpansion(set: TcgSet): Boolean {
        val normalizedName = set.name.trim().lowercase(Locale.ROOT)
        if (normalizedName == "gym yeld" || normalizedName == "gym yield") return false
        return set.printedTotal > 0 || set.total > 0
    }

    /**
     * Parses a release date string to a LocalDate, trying multiple formats in order:
     *  1. ISO format: yyyy-MM-dd  (most common from PokeWallet)
     *  2. Human long: "d MMMM, yyyy" (e.g. "15 January, 2026")
     *  3. Human short: "d MMM yyyy" (e.g. "15 Jan 2026")
     * Returns LocalDate.MIN when no format matches (treated as "no date → sort last").
     */
    private fun parseReleaseDate(raw: String): LocalDate {
        val source = raw.trim()
        if (source.isBlank()) return LocalDate.MIN
        // 1. ISO -- il formato servito dal Worker per tutte le espansioni.
        //    Controllato con una regex prima di chiamare parse: il vecchio
        //    runCatching lasciava lanciare DateTimeParseException per ogni data
        //    non-ISO, e il costo di riempire lo stack trace si moltiplicava per
        //    i ~1400 confronti dell'ordinamento.
        if (ISO_DATE_REGEX.matches(source)) {
            runCatching { LocalDate.parse(source) }.getOrNull()?.let { return it }
        }
        // Ripulisce i suffissi ordinali (1st, 2nd, 3rd, 4th...) e gli underscore
        val cleaned = ORDINAL_SUFFIX_REGEX.replace(source, "$1")
            .replace('_', ' ')
            .trim()
        // 2. "d MMMM, yyyy"
        runCatching { LocalDate.parse(cleaned, LONG_DATE_FORMAT) }.getOrNull()?.let { return it }
        // 3. "d MMM yyyy"
        runCatching { LocalDate.parse(cleaned, SHORT_DATE_FORMAT) }.getOrNull()?.let { return it }
        return LocalDate.MIN
    }

    private fun rankCardSearchResults(query: String, cards: List<TcgCard>): List<TcgCard> {
        if (cards.isEmpty()) return cards

        val normalizedQuery = normalizeSearchName(query)
        if (normalizedQuery.isBlank()) return cards
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }

        return cards.sortedWith(
            compareByDescending<TcgCard> { card ->
                scoreCardNameMatch(
                    normalizedName = normalizeSearchName(card.name),
                    normalizedQuery = normalizedQuery,
                    queryTokens = queryTokens
                )
            }
                .thenByDescending { card -> if (isItalianCard(card)) 1 else 0 }
                .thenBy { card -> extractCardNumberForSearch(card.number).toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { card -> normalizeSearchName(card.name) }
                .thenBy { card -> card.id }
        )
    }

    private fun scoreCardNameMatch(
        normalizedName: String,
        normalizedQuery: String,
        queryTokens: List<String>
    ): Int {
        if (normalizedName.isBlank() || normalizedQuery.isBlank()) return 0
        return when {
            normalizedName == normalizedQuery -> 1000
            normalizedName.startsWith(normalizedQuery) -> 850
            " $normalizedName ".contains(" $normalizedQuery ") -> 700
            normalizedName.contains(normalizedQuery) -> 560
            queryTokens.isNotEmpty() && queryTokens.all { token -> normalizedName.contains(token) } -> 420
            queryTokens.any { token -> token.length >= 3 && " $normalizedName ".contains(" $token ") } -> 260
            else -> 0
        }
    }

    private fun isItalianCard(card: TcgCard): Boolean {
        val setId = card.set?.id.orEmpty().lowercase(Locale.ROOT)
        return setId.endsWith("__ita")
    }

    /** Applies pre-merged ITA snapshot prices to Italian search results (no API calls). */
    private suspend fun enrichItalianCardsWithSnapshotPrices(
        cards: List<TcgCard>,
        context: Context
    ): List<TcgCard> {
        if (cards.none { isItalianCard(it) }) return cards
        val snapshot = RepositoryProvider.italianPriceSnapshotRepository
            .getSnapshot(context)
            .getOrNull()
            ?: return cards

        return cards.map { card ->
            if (!isItalianCard(card) || card.cardmarket?.prices.hasPositiveEurPrice()) {
                return@map card
            }
            val lookupCode = card.id
                .takeIf { it.startsWith("ita:", ignoreCase = true) }
                ?.split(':')
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?: card.set?.id?.substringBefore("__").orEmpty()
            val numberKey = normalizeSnapshotNumberKey(card.number) ?: return@map card
            val entry = snapshot.priceMapFor(lookupCode)[numberKey] ?: return@map card
            val hasEur = entry.avg != null || entry.low != null || entry.trend != null
            val tcgPlayer = if (entry.usd != null || entry.usdLow != null) {
                TcgPlayer(
                    url = entry.url.takeIf { !hasEur }.orEmpty(),
                    prices = mapOf("normal" to TcgPriceInfo(low = entry.usdLow, market = entry.usd))
                )
            } else {
                card.tcgplayer
            }
            card.copy(
                cardmarket = CardMarket(
                    url = entry.url.takeIf { hasEur }.orEmpty(),
                    prices = CardMarketPrices(
                        averageSellPrice = entry.avg,
                        lowPrice = entry.low,
                        trendPrice = entry.trend,
                        avg1 = entry.avg1,
                        avg7 = entry.avg7,
                        avg30 = entry.avg30
                    )
                ),
                tcgplayer = tcgPlayer
            )
        }
    }

    private fun normalizeSnapshotNumberKey(raw: String): String? {
        val clean = raw.split("/").firstOrNull()?.trim().orEmpty()
        if (clean.isBlank()) return null
        return clean.toIntOrNull()?.toString() ?: clean.uppercase(Locale.ROOT)
    }

    private fun normalizeSearchName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val deCamel = raw.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        val normalized = Normalizer.normalize(deCamel, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return normalized
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractCardNumberForSearch(raw: String): String {
        return raw.substringBefore("/").trim().trimStart('0').ifEmpty { "0" }
    }


}

// ── Regole di corrispondenza dei filtri ──
//
// Fuori dalla classe perche' sono pure: non toccano lo stato, e cosi' si
// possono provare da sole. Sono il punto in cui una svista si vede solo in
// esercizio ("perche' la mia Metallo/Lotta non esce filtrando Metallo?").

/** True se la carta passa tutte le dimensioni selezionate nel pannello. */
internal fun cardMatchesFilter(card: TcgCard, filter: ItalianCardSearchFilter): Boolean {
    if (filter.isEmpty) return true

    if (filter.supertypes.isNotEmpty() && card.supertype.trim() !in filter.supertypes) return false
    if (filter.rarities.isNotEmpty() && card.rarity?.trim() !in filter.rarities) return false
    if (filter.variants.isNotEmpty() && ItalianCardFacets.variantOfName(card.name) !in filter.variants) return false
    if (filter.expansionIds.isNotEmpty() && cardExpansionIdOf(card) !in filter.expansionIds) return false

    // Una carta a doppio tipo passa se almeno uno dei due e' selezionato:
    // chi filtra "Metallo" si aspetta di vedere anche le Metallo/Lotta.
    if (filter.types.isNotEmpty() && card.types.orEmpty().none { it.trim() in filter.types }) return false

    if (filter.hpBuckets.isNotEmpty()) {
        // Nessun ps = nessuna fascia: una macchina non passa un filtro sui ps.
        val bucket = cardHpBucketOf(card) ?: return false
        if (bucket !in filter.hpBuckets) return false
    }

    return true
}

/** Il codice dell'espansione ITA, dal set id ("sv08__ita" -> "sv08"). */
internal fun cardExpansionIdOf(card: TcgCard): String =
    card.set?.id.orEmpty().substringBefore("__").lowercase(Locale.ROOT)

internal fun cardSeriesOf(card: TcgCard): String = card.set?.series.orEmpty()

internal fun cardHpBucketOf(card: TcgCard): ItalianHpBucket? =
    card.hp?.trim()?.toIntOrNull()?.takeIf { it > 0 }?.let(ItalianHpBucket::forHp)

/** Fascia di prezzo della carta, o null se lo snapshot non la copre. */
internal fun cardPriceBucketOf(card: TcgCard): ItalianPriceBucket? {
    val price = card.cardmarket?.prices.minimumEurPriceOrZero()
    if (price <= 0.0) return null
    return ItalianPriceBucket.forPrice(price)
}

/**
 * Una ricerca fatta di cifre: "001/217", "67/87", o il solo "067".
 *
 * Va riconosciuta prima di cercare, perche' il numero non sta fra i nomi e fra
 * i nomi non si trova: e' la ragione per cui digitare un ID non restituiva
 * niente. Il totale dopo la barra e' opzionale ed e' quello che dice di quale
 * espansione si parla, visto che la carta numero 1 esiste in tutte.
 */
internal fun parseCardNumberQuery(query: String): CardNumberQuery? {
    val clean = query.trim()
    if (clean.isBlank()) return null

    if (FLEX_CARD_NUMBER_REGEX.matchEntire(clean) != null) {
        val parts = clean.split("/")
        if (parts.size != 2) return null
        return CardNumberQuery(
            number = parts[0].trim().trimStart('0').ifEmpty { "0" },
            printedTotal = parts[1].trim().trimStart('0').toIntOrNull()
        )
    }

    // Solo cifre, senza barra: vale come numero carta. Nessuna carta si chiama
    // "067", quindi non si ruba niente alla ricerca per nome.
    if (BARE_CARD_NUMBER_REGEX.matchEntire(clean) != null) {
        return CardNumberQuery(number = clean.trimStart('0').ifEmpty { "0" }, printedTotal = null)
    }

    return null
}

