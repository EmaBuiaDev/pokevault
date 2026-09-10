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
private val FLEX_CARD_NUMBER_REGEX = Regex("""^\s*0*\d+\s*/\s*0*\d+\s*$""")

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
    val isExactCardSearch: Boolean = false,
    val cardRarityFilter: Set<String> = emptySet(),
    val cardTypeFilter: Set<String> = emptySet(),
    val cardSupertypeFilter: Set<String> = emptySet(),
    val cardSubtypeFilter: Set<String> = emptySet(),
    val availableCardRarities: List<String> = emptyList(),
    val availableCardTypes: List<String> = emptyList(),
    val availableCardSupertypes: List<String> = emptyList(),
    val availableCardSubtypes: List<String> = emptyList(),
    val searchedCards: List<TcgCard> = emptyList(),
    val isSearchingCards: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isAddingCard: String? = null,
    val successMessage: String? = null
)

// Usa AndroidViewModel per accedere al Context
class SetsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RepositoryProvider.tcgRepository
    private val firestoreRepository = FirestoreRepository()
    private var searchJob: Job? = null
    private var setSearchJob: Job? = null
    private var lastUnfilteredSearchCards: List<TcgCard> = emptyList()

    var uiState by mutableStateOf(SetsUiState())
        private set

    private var lastRevalidationAtMs: Long = 0L

    private companion object {
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

    fun searchCardsByName(query: String) {
        uiState = uiState.copy(cardSearchQuery = query)
        searchJob?.cancel()
        if (query.length < 2) {
            lastUnfilteredSearchCards = emptyList()
            uiState = uiState.copy(
                searchedCards = emptyList(),
                isSearchingCards = false,
                availableCardRarities = emptyList(),
                availableCardTypes = emptyList(),
                availableCardSupertypes = emptyList(),
                availableCardSubtypes = emptyList(),
                cardRarityFilter = emptySet(),
                cardTypeFilter = emptySet(),
                cardSupertypeFilter = emptySet(),
                cardSubtypeFilter = emptySet()
            )
            return
        }
        val exactMode = uiState.isExactCardSearch
        searchJob = viewModelScope.launch {
            delay(250)
            uiState = uiState.copy(isSearchingCards = true)
            val context = getApplication<Application>().applicationContext

            // ITA-only: PokeWallet's direct/translated English search used to run in
            // parallel and get merged in, so results mixed our ITA cards with raw
            // English PokeWallet ones. Besides being the reported cause of "search
            // doesn't find cards well", that burned PokeWallet request budget on every
            // keystroke -- budget that should go only to prices (see MIGRATION_PLAN.md M4.5).
            val italianCards = repository.searchItalianCardsByName(
                query = query,
                context = context,
                exactMode = exactMode,
                limit = 60
            ).getOrDefault(emptyList())

            val filteredCards = if (exactMode) applyExactCardFilter(query, italianCards) else italianCards
            val rankedCards = rankCardSearchResults(query, filteredCards)
            val finalCards = enrichItalianCardsWithSnapshotPrices(rankedCards, context)
            lastUnfilteredSearchCards = finalCards

            // New search results -> filter chips reset to what's actually available
            // in this result set (real values from D1, see MIGRATION_PLAN.md M4.6),
            // rather than carrying over a filter that may no longer apply.
            uiState = uiState.copy(
                searchedCards = finalCards,
                isSearchingCards = false,
                availableCardRarities = finalCards.mapNotNull { it.rarity?.trim()?.takeIf(String::isNotBlank) }.distinct().sorted(),
                availableCardTypes = finalCards.flatMap { it.types.orEmpty() }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted(),
                availableCardSupertypes = finalCards.map { it.supertype.trim() }.filter { it.isNotBlank() }.distinct().sorted(),
                availableCardSubtypes = finalCards.flatMap { it.subtypes.orEmpty() }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted(),
                cardRarityFilter = emptySet(),
                cardTypeFilter = emptySet(),
                cardSupertypeFilter = emptySet(),
                cardSubtypeFilter = emptySet()
            )
        }
    }

    fun setExactCardSearch(enabled: Boolean) {
        if (uiState.isExactCardSearch == enabled) return
        uiState = uiState.copy(isExactCardSearch = enabled)

        val currentQuery = uiState.cardSearchQuery
        if (currentQuery.length >= 2) {
            searchCardsByName(currentQuery)
        }
    }

    fun toggleCardRarityFilter(rarity: String) {
        val current = uiState.cardRarityFilter
        uiState = uiState.copy(cardRarityFilter = if (rarity in current) current - rarity else current + rarity)
        applyCardResultFilters()
    }

    fun toggleCardTypeFilter(type: String) {
        val current = uiState.cardTypeFilter
        uiState = uiState.copy(cardTypeFilter = if (type in current) current - type else current + type)
        applyCardResultFilters()
    }

    fun toggleCardSupertypeFilter(supertype: String) {
        val current = uiState.cardSupertypeFilter
        uiState = uiState.copy(cardSupertypeFilter = if (supertype in current) current - supertype else current + supertype)
        applyCardResultFilters()
    }

    fun toggleCardSubtypeFilter(subtype: String) {
        val current = uiState.cardSubtypeFilter
        uiState = uiState.copy(cardSubtypeFilter = if (subtype in current) current - subtype else current + subtype)
        applyCardResultFilters()
    }

    fun clearCardResultFilters() {
        uiState = uiState.copy(
            cardRarityFilter = emptySet(),
            cardTypeFilter = emptySet(),
            cardSupertypeFilter = emptySet(),
            cardSubtypeFilter = emptySet()
        )
        applyCardResultFilters()
    }

    private fun applyCardResultFilters() {
        val rarityFilter = uiState.cardRarityFilter
        val typeFilter = uiState.cardTypeFilter
        val supertypeFilter = uiState.cardSupertypeFilter
        val subtypeFilter = uiState.cardSubtypeFilter
        val filtered = lastUnfilteredSearchCards.filter { card ->
            (rarityFilter.isEmpty() || card.rarity?.trim() in rarityFilter) &&
                (typeFilter.isEmpty() || card.types.orEmpty().any { it.trim() in typeFilter }) &&
                (supertypeFilter.isEmpty() || card.supertype.trim() in supertypeFilter) &&
                (subtypeFilter.isEmpty() || card.subtypes.orEmpty().any { it.trim() in subtypeFilter })
        }
        uiState = uiState.copy(searchedCards = filtered)
    }

    fun clearCardSearch() {
        searchJob?.cancel()
        lastUnfilteredSearchCards = emptyList()
        uiState = uiState.copy(
            cardSearchQuery = "",
            searchedCards = emptyList(),
            isSearchingCards = false,
            availableCardRarities = emptyList(),
            availableCardTypes = emptyList(),
            availableCardSupertypes = emptyList(),
            availableCardSubtypes = emptyList(),
            cardRarityFilter = emptySet(),
            cardTypeFilter = emptySet(),
            cardSupertypeFilter = emptySet(),
            cardSubtypeFilter = emptySet()
        )
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

    private fun applyExactCardFilter(query: String, cards: List<TcgCard>): List<TcgCard> {
        if (cards.isEmpty()) return cards

        val fullNumber = FLEX_CARD_NUMBER_REGEX.matchEntire(query)
        if (fullNumber != null) {
            val parts = query.split("/")
            if (parts.size == 2) {
                val number = parts[0].trim().trimStart('0').ifEmpty { "0" }
                val total = parts[1].trim().trimStart('0').ifEmpty { "0" }
                val totalInt = total.toIntOrNull()
                val exactSetIds = if (totalInt != null) {
                    uiState.allSets.filter { it.printedTotal == totalInt }.map { it.id }.toSet()
                } else {
                    emptySet()
                }
                val totalVariants = linkedSetOf(total, total.padStart(3, '0'))

                val strictByCardNumberAndTotal = cards
                    .filter { card ->
                        extractCardNumberForSearch(card.number) == number &&
                            extractPrintedTotalForSearch(card.number) in totalVariants
                    }
                val numberOnly = cards.filter { card -> extractCardNumberForSearch(card.number) == number }
                if (strictByCardNumberAndTotal.isNotEmpty()) {
                    val exactSetScoped = strictByCardNumberAndTotal.filter { card ->
                        card.set?.id.orEmpty() in exactSetIds
                    }
                    return if (exactSetScoped.isNotEmpty()) exactSetScoped else strictByCardNumberAndTotal
                }
                return numberOnly
            }
        }

        val normalizedQuery = normalizeSearchName(query)
        if (normalizedQuery.isBlank()) return cards

        val exact = cards.filter { normalizeSearchName(it.name) == normalizedQuery }
        if (exact.isNotEmpty()) return exact

        val prefix = cards.filter { normalizeSearchName(it.name).startsWith("$normalizedQuery ") }
        if (prefix.isNotEmpty()) return prefix

        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        return cards.filter { card ->
            val normalizedName = normalizeSearchName(card.name)
            queryTokens.isNotEmpty() && queryTokens.all { token ->
                " $normalizedName ".contains(" $token ")
            }
        }
    }

    private fun rankCardSearchResults(query: String, cards: List<TcgCard>): List<TcgCard> {
        if (cards.isEmpty()) return cards

        val fullNumber = FLEX_CARD_NUMBER_REGEX.matchEntire(query)
        if (fullNumber != null) return cards

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

    private fun extractPrintedTotalForSearch(raw: String): String {
        val total = raw.substringAfter("/", "").trim()
        if (total.isBlank()) return ""
        return total.trimStart('0').ifEmpty { "0" }
    }

}
