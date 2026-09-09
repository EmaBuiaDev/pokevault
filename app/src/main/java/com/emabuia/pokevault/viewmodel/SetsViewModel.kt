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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    /**
     * Display order inside a single series group:
     *   1. Release date DESC (most recent first).
     *   2. Sets without a parseable release date go to the bottom.
     *   3. Stable tiebreaker by lowercase name.
     *
     * Deliberately NOT keyed on logo-load state (see hasPrioritizedLogo):
     * doing so used to re-sort the whole list every time a logo finished or
     * failed loading in the background, which is what made sets visibly
     * "jump" while scrolling. A missing logo shows a placeholder; it must
     * never change a set's position.
     */
    private val setDisplayComparator: Comparator<TcgSet> =
        Comparator<TcgSet> { a, b ->
            val da = parseReleaseDate(a.releaseDate)
            val db = parseReleaseDate(b.releaseDate)
            val aMissing = da == LocalDate.MIN
            val bMissing = db == LocalDate.MIN
            when {
                aMissing && !bMissing -> 1
                !aMissing && bMissing -> -1
                else -> db.compareTo(da) // DESC by release date
            }
        }.thenBy { it.name.lowercase(Locale.ROOT) }

    init {
        TranslationService.loadCache(application.applicationContext)
        loadSets()
    }

    private fun loadSets() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            val context = getApplication<Application>().applicationContext
            repository.getSets(context = context)
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
            repository.getSets(context = context)
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

    private fun revalidateSetsFromNetwork(context: Context) {
        viewModelScope.launch {
            repository.getSets(context = context, forceRefresh = true)
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
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            val context = getApplication<Application>().applicationContext
            repository.getSets(context = context, forceRefresh = true)
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

    private fun applyFilters() {
        val displayableSets = uiState.allSets.filter(::isDisplayableExpansion)
        val italianSets = displayableSets.filter { it.language.equals("ITA", ignoreCase = true) }

        val searched = if (uiState.searchQuery.isBlank()) {
            italianSets
        } else {
            italianSets.filter { set ->
                set.name.contains(uiState.searchQuery, ignoreCase = true) ||
                    set.series.contains(uiState.searchQuery, ignoreCase = true)
            }
        }

        uiState = uiState.copy(seriesGroups = buildSeriesGroups(searched))
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
    private fun buildSeriesGroups(italianSets: List<TcgSet>): List<SeriesSetsGroup> {
        if (italianSets.isEmpty()) return emptyList()

        return italianSets
            .groupBy { it.series.ifBlank { ItalianTranslations.translateSeriesName(OTHER_SERIES_KEY) } }
            .map { (seriesLabel, sets) ->
                SeriesSetsGroup(
                    seriesKey = seriesLabel,
                    seriesLabel = seriesLabel,
                    sets = sets.sortedWith(setDisplayComparator)
                )
            }
            .sortedByDescending { group ->
                group.sets.maxOfOrNull { parseReleaseDate(it.releaseDate) } ?: LocalDate.MIN
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
        // 1. ISO
        runCatching { LocalDate.parse(source) }.getOrNull()?.let { return it }
        // Clean ordinal suffixes (1st, 2nd, 3rd, 4th…) and underscores
        val cleaned = source
            .replace(Regex("""(\d+)(st|nd|rd|th)"""), "$1")
            .replace('_', ' ')
            .trim()
        // 2. "d MMMM, yyyy"
        runCatching {
            LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("d MMMM, uuuu", Locale.ENGLISH))
        }.getOrNull()?.let { return it }
        // 3. "d MMM yyyy"
        runCatching {
            LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH))
        }.getOrNull()?.let { return it }
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
