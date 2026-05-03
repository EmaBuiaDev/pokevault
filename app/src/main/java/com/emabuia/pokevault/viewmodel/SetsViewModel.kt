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
import com.emabuia.pokevault.data.remote.PokeTcgRepository
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgSet
import com.emabuia.pokevault.data.remote.TranslationService
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDate
import java.util.Locale

private const val LOGO_CACHE_PREFS = "sets_logo_cache"
private const val LOGO_CACHE_MISSING_URLS_KEY = "missing_logo_urls"
private const val OTHER_SERIES_KEY = "Other"
private val FLEX_CARD_NUMBER_REGEX = Regex("""^\s*0*\d+\s*/\s*0*\d+\s*$""")

private data class OtherSubgroup(
    val key: String,
    val label: String,
    val sets: List<TcgSet>
)

data class SeriesSetsGroup(
    val seriesKey: String,
    val seriesLabel: String,
    val sets: List<TcgSet>
)

data class LanguageMacroGroup(
    val macro: String,
    val seriesGroups: List<SeriesSetsGroup>
)

data class SetsUiState(
    val allSets: List<TcgSet> = emptyList(),
    val filteredSets: List<TcgSet> = emptyList(),
    val selectedLanguageMacro: String = "ENG",
    val languageCountByMacro: Map<String, Int> = emptyMap(),
    val macroGroups: List<LanguageMacroGroup> = emptyList(),
    val seriesList: List<String> = emptyList(),
    val seriesCountByLabel: Map<String, Int> = emptyMap(),
    val selectedSeries: String? = null,
    val searchQuery: String = "",
    val cardSearchQuery: String = "",
    val isExactCardSearch: Boolean = false,
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
    private val logoCachePrefs = application.applicationContext
        .getSharedPreferences(LOGO_CACHE_PREFS, Context.MODE_PRIVATE)
    private val knownMissingLogoUrls = logoCachePrefs
        .getStringSet(LOGO_CACHE_MISSING_URLS_KEY, emptySet())
        ?.asSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toMutableSet()
        ?: mutableSetOf()

    private val languageMacros = listOf("ENG", "JAP", "CHN")
    private val officialSeriesOrder = listOf(
        "Mega Evolutions",
        "Scarlet & Violet",
        "Sword & Shield",
        "Sun & Moon",
        "XY",
        "Black & White",
        "HeartGold & SoulSilver",
        "Platinum",
        "Diamond & Pearl",
        "EX",
        "e-Card",
        "Neo",
        "Gym",
        "Base",
        "Other"
    )

    var uiState by mutableStateOf(SetsUiState())
        private set

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

    fun filterBySeries(series: String?) {
        uiState = uiState.copy(selectedSeries = series)
        applyFilters()
    }

    fun filterByLanguageMacro(languageMacro: String) {
        if (uiState.selectedLanguageMacro == languageMacro) return
        uiState = uiState.copy(selectedLanguageMacro = languageMacro)
        applyFilters()
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
                }
        }
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
            uiState = uiState.copy(searchedCards = emptyList(), isSearchingCards = false)
            return
        }
        val exactMode = uiState.isExactCardSearch
        searchJob = viewModelScope.launch {
            delay(500)
            uiState = uiState.copy(isSearchingCards = true)
            val context = getApplication<Application>().applicationContext
            val isFullNumberQuery = FLEX_CARD_NUMBER_REGEX.matches(query)

            // Search with original query + translate in parallel
            val directDeferred = async { repository.searchCards(query) }
            val translatedDeferred = async {
                if (isFullNumberQuery) {
                    null
                } else {
                    val translated = TranslationService.translateItToEn(query, context)
                    if (translated != null && translated.lowercase() != query.lowercase()) {
                        repository.searchCards(translated)
                    } else null
                }
            }

            val directCards = directDeferred.await().getOrDefault(emptyList())
            val translatedCards = translatedDeferred.await()?.getOrDefault(emptyList()) ?: emptyList()

            val allCards = (directCards + translatedCards).distinctBy { it.id }
            val finalCards = if (exactMode) applyExactCardFilter(query, allCards) else allCards
            uiState = uiState.copy(searchedCards = finalCards, isSearchingCards = false)
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

    fun clearCardSearch() {
        searchJob?.cancel()
        uiState = uiState.copy(cardSearchQuery = "", searchedCards = emptyList(), isSearchingCards = false)
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
                hp = tcgCard.hp?.toIntOrNull() ?: 0, estimatedValue = price,
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

    fun onSetLogoLoadFailed(logoUrl: String) {
        val normalized = logoUrl.trim()
        if (normalized.isBlank()) return
        val wasAdded = knownMissingLogoUrls.add(normalized)
        if (!wasAdded) return

        logoCachePrefs.edit()
            .putStringSet(LOGO_CACHE_MISSING_URLS_KEY, knownMissingLogoUrls.toSet())
            .apply()

        applyFilters()
    }

    private fun applyFilters() {
        val displayableSets = uiState.allSets.filter(::isDisplayableExpansion)

        val languageCountByMacro = languageMacros.associateWith { macro ->
            displayableSets.count { it.language == macro }
        }
        val macroGroups = buildMacroGroups(displayableSets)
        val selectedMacroGroup = macroGroups.firstOrNull { it.macro == uiState.selectedLanguageMacro }
            ?: macroGroups.firstOrNull()
        val selectedMacro = selectedMacroGroup?.macro ?: uiState.selectedLanguageMacro

        val scopedSeriesGroups = selectedMacroGroup?.seriesGroups.orEmpty()
        val scopedSeries = scopedSeriesGroups
            .filter { it.sets.isNotEmpty() }
            .map { it.seriesLabel }
        val seriesCountByLabel = scopedSeriesGroups
            .associate { it.seriesLabel to it.sets.size }
            .filterValues { it > 0 }
        val selectedSeries = uiState.selectedSeries?.takeIf { it in scopedSeries }

        val selectedSeriesSets = if (selectedSeries == null) {
            scopedSeriesGroups.flatMap { it.sets }.distinctBy { it.id }
        } else {
            scopedSeriesGroups.firstOrNull { it.seriesLabel == selectedSeries }?.sets.orEmpty()
        }

        val filtered = selectedSeriesSets.filter { set ->
            val matchesSearch = uiState.searchQuery.isBlank() ||
                set.name.contains(uiState.searchQuery, ignoreCase = true) ||
                set.series.contains(uiState.searchQuery, ignoreCase = true)
            matchesSearch
        }

        uiState = uiState.copy(
            selectedLanguageMacro = selectedMacro,
            languageCountByMacro = languageCountByMacro,
            macroGroups = macroGroups,
            seriesList = scopedSeries,
            seriesCountByLabel = seriesCountByLabel,
            selectedSeries = selectedSeries,
            filteredSets = filtered
        )
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

    private fun buildMacroGroups(allSets: List<TcgSet>): List<LanguageMacroGroup> {
        return languageMacros.map { macro ->
            val setsInMacro = allSets.filter { it.language == macro }
            val groupedByCanonicalSeries = setsInMacro.groupBy { set ->
                canonicalSeries(set.series)
            }
            val rawOtherSets = groupedByCanonicalSeries[OTHER_SERIES_KEY].orEmpty()

            // Build derived chips from Other first so we can de-duplicate the base Other chip.
            val officialLabelsWithoutOther = officialSeriesOrder
                .filterNot { it == OTHER_SERIES_KEY }
                .map { ItalianTranslations.translateSeriesName(it) }
                .toSet()

            val otherSubgroups = buildOtherSubgroups(rawOtherSets, officialLabelsWithoutOther)
            val subgroupSetIds = otherSubgroups
                .asSequence()
                .flatMap { subgroup -> subgroup.sets.asSequence().map { it.id } }
                .toSet()

            val reducedOtherSets = sortSetsForDisplay(
                rawOtherSets.filterNot { set -> set.id in subgroupSetIds }
            )

            val officialSeriesGroups = officialSeriesOrder
                .filterNot { it == OTHER_SERIES_KEY }
                .map { canonicalSeries ->
                    val orderedSets = sortSetsForDisplay(groupedByCanonicalSeries[canonicalSeries].orEmpty())
                    SeriesSetsGroup(
                        seriesKey = canonicalSeries,
                        seriesLabel = ItalianTranslations.translateSeriesName(canonicalSeries),
                        sets = orderedSets
                    )
                }

            val appendedOtherSubgroups = otherSubgroups.map { subgroup ->
                SeriesSetsGroup(
                    seriesKey = subgroup.key,
                    seriesLabel = subgroup.label,
                    sets = subgroup.sets
                )
            }

            val otherTailGroup = SeriesSetsGroup(
                seriesKey = OTHER_SERIES_KEY,
                seriesLabel = ItalianTranslations.translateSeriesName(OTHER_SERIES_KEY),
                sets = reducedOtherSets
            )

            // Final order: official groups (except Other) + derived Other chips + Other as last/rightmost.
            LanguageMacroGroup(
                macro = macro,
                seriesGroups = officialSeriesGroups + appendedOtherSubgroups + otherTailGroup
            )
        }
    }

    private fun sortSetsForDisplay(sets: List<TcgSet>): List<TcgSet> {
        return sets.sortedWith(
            compareBy<TcgSet> { !hasPrioritizedLogo(it) }
                .thenByDescending { parseReleaseDate(it.releaseDate) }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
    }

    private fun buildOtherSubgroups(otherSets: List<TcgSet>, existingLabels: Set<String>): List<OtherSubgroup> {
        if (otherSets.isEmpty()) return emptyList()

        val grouped = otherSets
            .groupBy { deriveOtherFamilyKey(it.name) }
            .filterKeys { it.isNotBlank() }
            .mapNotNull { (familyKey, sets) ->
                if (sets.size < 2) return@mapNotNull null
                val label = familyLabelFromKey(familyKey)
                if (label.isBlank() || label in existingLabels) return@mapNotNull null
                OtherSubgroup(
                    key = "other::$familyKey",
                    label = label,
                    sets = sortSetsForDisplay(sets)
                )
            }

        return grouped.sortedByDescending { subgroup ->
            subgroup.sets.maxOfOrNull { parseReleaseDate(it.releaseDate) } ?: LocalDate.MIN
        }
    }

    private fun deriveOtherFamilyKey(rawName: String): String {
        val normalized = rawName
            .trim()
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\b(19|20)\\d{2}\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return ""

        if (normalized.contains("mcdonald")) {
            return "mcdonalds"
        }

        if (normalized.contains("pop series") || Regex("\\bpop\\b").containsMatchIn(normalized)) {
            return "pop"
        }

        if (
            normalized.contains("prize pack") ||
            normalized.contains("price pack") ||
            Regex("\\bprize\\b").containsMatchIn(normalized) ||
            Regex("\\bprice\\b").containsMatchIn(normalized)
        ) {
            return "prize pack"
        }

        if (normalized.contains("world") && normalized.contains("championship")) {
            return "world championships"
        }

        if (normalized.contains("play") && normalized.contains("pokemon")) {
            return "play pokemon"
        }

        if (normalized.contains("evolution") && normalized.contains("collection")) {
            return "evolution collection"
        }

        if (
            normalized.contains("trick") &&
            (normalized.contains("trade") || normalized.contains("treat"))
        ) {
            return "trick or trade"
        }

        val genericStopwords = setOf(
            "pokemon", "pokémon", "tcg", "set", "series", "promo", "promos",
            "collection", "cards", "card", "the", "and"
        )
        val tokens = normalized
            .split(" ")
            .filter { token -> token.isNotBlank() && token !in genericStopwords }

        if (tokens.size < 2) return ""

        // Use first tokens as stable bucket key to group close name variants.
        val key = tokens.take(3).joinToString(" ")
        return if (key.length >= 8) key else ""
    }

    private fun familyLabelFromKey(key: String): String {
        if (key == "mcdonalds") return "McDonalds"
        if (key == "pop") return "POP"
        if (key == "prize pack") return "Prize Pack"
        if (key == "world championships") return "World Championships"
        if (key == "play pokemon") return "Play Pokemon"
        if (key == "evolution collection") return "Evolution Collection"
        if (key == "trick or trade") return "Trick or Trade"

        return key
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }
    }

    private fun canonicalSeries(raw: String): String {
        val normalized = raw
            .trim()
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return when {
            normalized in setOf("mega evolution", "mega evolutions", "mega evoluzione") -> "Mega Evolutions"
            normalized in setOf("scarlet and violet", "scarlatto e violetto", "scarlatto e violetto") -> "Scarlet & Violet"
            normalized in setOf("sword and shield", "spada e scudo") -> "Sword & Shield"
            normalized in setOf("sun and moon", "sole e luna") -> "Sun & Moon"
            normalized == "xy" -> "XY"
            normalized in setOf("black and white", "nero e bianco") -> "Black & White"
            normalized == "heartgold and soulsilver" || normalized == "heartgold soulsilver" -> "HeartGold & SoulSilver"
            normalized == "platinum" || normalized == "platino" -> "Platinum"
            normalized in setOf("diamond and pearl", "diamante e perla") -> "Diamond & Pearl"
            normalized == "ex" -> "EX"
            normalized in setOf("e card", "ecard") -> "e-Card"
            normalized == "neo" -> "Neo"
            normalized == "gym" -> "Gym"
            normalized == "base" || normalized == "legendary collection" -> "Base"
            normalized == "other" || normalized == "altro" -> "Other"
            else -> OTHER_SERIES_KEY
        }
    }

    private fun isDisplayableExpansion(set: TcgSet): Boolean {
        val normalizedName = set.name.trim().lowercase(Locale.ROOT)
        if (normalizedName == "gym yeld" || normalizedName == "gym yield") {
            return false
        }
        // Hide empty expansions: both printed and effective total are zero.
        return set.printedTotal > 0 || set.total > 0
    }

    private fun hasPrioritizedLogo(set: TcgSet): Boolean {
        val logoUrl = set.images.logo.trim()
        return logoUrl.isNotBlank() && !knownMissingLogoUrls.contains(logoUrl)
    }

    private fun parseReleaseDate(raw: String): LocalDate {
        return runCatching { LocalDate.parse(raw) }.getOrDefault(LocalDate.MIN)
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
                val candidates = if (strictByCardNumberAndTotal.isNotEmpty()) strictByCardNumberAndTotal else numberOnly

                return candidates
                    .sortedWith(
                        compareByDescending<TcgCard> { card ->
                            val setId = card.set?.id.orEmpty()
                            if (setId in exactSetIds) 1 else 0
                        }.thenByDescending { card ->
                            val set = uiState.allSets.firstOrNull { it.id == card.set?.id }
                            parseReleaseDate(set?.releaseDate.orEmpty())
                        }
                    )
            }
        }

        val normalizedQuery = normalizeSearchName(query)
        if (normalizedQuery.isBlank()) return cards

        val exact = cards.filter { normalizeSearchName(it.name) == normalizedQuery }
        if (exact.isNotEmpty()) {
            return exact.sortedBy { extractCardNumberForSearch(it.number).toIntOrNull() ?: Int.MAX_VALUE }
        }

        val prefix = cards.filter { normalizeSearchName(it.name).startsWith("$normalizedQuery ") }
        if (prefix.isNotEmpty()) return prefix

        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        return cards.filter { card ->
            val normalizedName = normalizeSearchName(card.name)
            queryTokens.isNotEmpty() && queryTokens.all { token -> normalizedName.contains(token) }
        }
    }

    private fun normalizeSearchName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
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
