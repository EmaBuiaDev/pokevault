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
import com.emabuia.pokevault.data.remote.PokeTcgRepository
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
    val selectedLanguageMacro: String = "ITA",
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

    private val languageMacros = listOf("ITA", "ENG", "JAP", "CHN")
    private val macrosAllowedWithZeroTotals = setOf("ENG")
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

    /**
     * Known sets whose language is mis-tagged in the source data.
     * Key: lowercase set name fragment (partial match) → correct macro.
     * These overrides take priority over the raw language field.
     */
    private val languageNameOverrides: List<Pair<String, String>> = listOf(
        // Japanese branded sets incorrectly tagged as ENG in PokeWallet
        "mega evolution all-stars" to "JAP",
        "mega evolution all stars" to "JAP",
        "pokémon card game classic" to "JAP",
        "pokemon card game classic" to "JAP",
        "special deck set" to "JAP",
        "gym special" to "JAP",
        "vmax climax" to "JAP",
        "eevee heroes" to "JAP",
        "25th anniversary collection" to "JAP",
        "mega evolution deck" to "JAP",
        // Ensure latest ENG expansions remain visible even with partial source metadata.
        "chaos rising" to "ENG",
        "abyss eye" to "ENG",
        "abyss eyes" to "ENG",
        "abiss eye" to "ENG",
        "abiss eyes" to "ENG"
    )

    /**
     * Display order inside a single series group:
     *   1. Sets WITH a usable logo come first.
     *   2. Within each logo bucket, sort by release date DESC.
     *   3. Sets without a parseable release date go to the bottom of their
     *      logo bucket (LocalDate.MIN under DESC).
     *   4. Stable tiebreaker by lowercase name.
     */
    private val setDisplayComparator: Comparator<TcgSet> =
        compareBy<TcgSet> { if (hasPrioritizedLogo(it)) 0 else 1 }
            .thenComparator { a, b ->
                val da = parseReleaseDate(a.releaseDate)
                val db = parseReleaseDate(b.releaseDate)
                val aMissing = da == LocalDate.MIN
                val bMissing = db == LocalDate.MIN
                when {
                    aMissing && !bMissing -> 1
                    !aMissing && bMissing -> -1
                    else -> db.compareTo(da) // DESC by release date
                }
            }
            .thenBy { it.name.lowercase(Locale.ROOT) }

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
            uiState = uiState.copy(searchedCards = emptyList(), isSearchingCards = false)
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

    fun onSetLogoLoadFailed(logoUrl: String) {
        val normalized = logoUrl.trim()
        if (normalized.isBlank()) return
        val wasAdded = knownMissingLogoUrls.add(normalized)
        if (!wasAdded) return

        logoCachePrefs.edit()
            .putStringSet(LOGO_CACHE_MISSING_URLS_KEY, knownMissingLogoUrls.toSet())
            .apply()
    }

    private fun applyFilters() {
        val displayableSets = uiState.allSets.filter(::isDisplayableExpansion)

        val languageCountByMacro = languageMacros.associateWith { macro ->
            displayableSets.count { macro in resolveMacroMemberships(it) }
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
        // Pre-bucket once per macro using defensive language normalization.
        // Pilot ITA duplicates targeted sets without removing them from source macros.
        val setsByMacro: Map<String, List<TcgSet>> = allSets
            .flatMap { set ->
                resolveMacroMemberships(set).map { macro -> macro to set }
            }
            .groupBy({ it.first }, { it.second })

        return languageMacros.map { macro ->
            val setsInMacro = setsByMacro[macro].orEmpty()
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
                    val orderedSets = sortSetsForDisplay(
                        sets = groupedByCanonicalSeries[canonicalSeries].orEmpty(),
                        seriesKey = canonicalSeries
                    )
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

    private fun sortSetsForDisplay(sets: List<TcgSet>, seriesKey: String? = null): List<TcgSet> {
        if (seriesKey == "Mega Evolutions") {
            val filtered = sets.filter { set ->
                megaEvolutionPriorityBucket(set) in 0..2
            }

            return filtered.sortedWith(
                compareBy<TcgSet> { megaEvolutionPriorityBucket(it) }
                    .then(setDisplayComparator)
            )
        }

        return sets.sortedWith(setDisplayComparator)
    }

    private fun megaEvolutionPriorityBucket(set: TcgSet): Int {
        val normalizedName = set.name.trim().lowercase(Locale.ROOT)
        val isPromo = normalizedName.contains("promo")
        val isEnergy = normalizedName.contains("energie") || normalizedName.contains("energies") || normalizedName.contains("energy")

        return when {
            hasPrioritizedLogo(set) -> 0
            isPromo -> 1
            isEnergy -> 2
            else -> 3
        }
    }

    /**
     * Resolves the correct language macro for a set, applying name-based overrides
     * for sets that are known to be mis-tagged in the source data.
     */
    private fun resolveLanguageMacro(set: TcgSet): String? {
        val lowerName = set.name.trim().lowercase(Locale.ROOT)
        for ((fragment, macro) in languageNameOverrides) {
            if (lowerName.contains(fragment)) return macro
        }
        return normalizeLanguageMacro(set.language)
    }

    private fun resolveMacroMemberships(set: TcgSet): Set<String> {
        val memberships = linkedSetOf<String>()
        resolveLanguageMacro(set)?.let { memberships += it }
        return memberships
    }

    /**
     * Defensive language normalization. The repository already maps to
     * ENG/JAP/CHN, but we guard against any raw value leaking through.
     */
    private fun normalizeLanguageMacro(raw: String?): String? {
        val normalized = raw?.trim()?.lowercase(Locale.ROOT)?.replace('_', ' ') ?: return null
        if (normalized.isBlank()) return null
        return when {
            normalized in setOf("it", "ita", "italian", "italiano") ||
                normalized.contains("ital") -> "ITA"
            normalized in setOf("en", "eng", "english", "inglese") ||
                normalized.contains("engl") || normalized.contains("ingl") -> "ENG"
            normalized in setOf("jp", "jap", "ja", "japanese", "giapponese") ||
                normalized.contains("jap") || normalized.contains("giapp") -> "JAP"
            normalized in setOf("zh", "zhs", "zht", "cn", "chn", "chi", "chinese") ||
                normalized.contains("chinese") ||
                normalized.contains("mandarin") ||
                normalized.contains("cinese") -> "CHN"
            else -> null
        }
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
            // Mega Evolutions (current SV-era branded sub-line)
            normalized.contains("mega evolution") ||
                normalized.contains("mega evoluzion") -> "Mega Evolutions"
            // Scarlet & Violet
            normalized in setOf(
                "scarlet and violet", "scarlet violet", "sv",
                "scarlatto e violetto", "scarlatto violetto"
            ) || normalized.startsWith("scarlet and violet") ||
                normalized.startsWith("scarlatto e violetto") -> "Scarlet & Violet"
            // Sword & Shield
            normalized in setOf("sword and shield", "sword shield", "swsh", "spada e scudo") ||
                normalized.startsWith("sword and shield") ||
                normalized.startsWith("spada e scudo") -> "Sword & Shield"
            // Sun & Moon
            normalized in setOf("sun and moon", "sun moon", "sm", "sole e luna") ||
                normalized.startsWith("sun and moon") ||
                normalized.startsWith("sole e luna") -> "Sun & Moon"
            // XY
            normalized == "xy" || normalized.startsWith("xy ") -> "XY"
            // Black & White
            normalized in setOf("black and white", "black white", "bw", "nero e bianco") ||
                normalized.startsWith("black and white") ||
                normalized.startsWith("nero e bianco") -> "Black & White"
            // HeartGold & SoulSilver
            normalized == "heartgold and soulsilver" ||
                normalized == "heartgold soulsilver" ||
                normalized == "hgss" -> "HeartGold & SoulSilver"
            // Platinum
            normalized == "platinum" || normalized == "platino" -> "Platinum"
            // Diamond & Pearl
            normalized in setOf("diamond and pearl", "diamond pearl", "dp", "diamante e perla") ||
                normalized.startsWith("diamond and pearl") ||
                normalized.startsWith("diamante e perla") -> "Diamond & Pearl"
            // EX (block, not Scarlet & Violet ex)
            normalized == "ex" || normalized == "ex series" -> "EX"
            // e-Card
            normalized in setOf("e card", "ecard", "e card series") -> "e-Card"
            // Neo
            normalized == "neo" || normalized.startsWith("neo ") -> "Neo"
            // Gym
            normalized == "gym" ||
                normalized == "gym heroes" ||
                normalized == "gym challenge" -> "Gym"
            // Base / Classic
            normalized in setOf(
                "base", "base set", "base set 2", "jungle", "fossil",
                "team rocket", "legendary collection"
            ) -> "Base"
            // Explicit Other
            normalized == "other" || normalized == "altro" -> OTHER_SERIES_KEY
            else -> OTHER_SERIES_KEY
        }
    }

    private fun isDisplayableExpansion(set: TcgSet): Boolean {
        val normalizedName = set.name.trim().lowercase(Locale.ROOT)
        if (normalizedName == "gym yeld" || normalizedName == "gym yield") {
            return false
        }
        if (set.printedTotal > 0 || set.total > 0) {
            return true
        }

        // Keep ENG expansions visible even when totals are missing from source payload.
        return resolveMacroMemberships(set).any { it in macrosAllowedWithZeroTotals }
    }

    private fun hasPrioritizedLogo(set: TcgSet): Boolean {
        val logoUrl = set.images.logo.trim()
        return logoUrl.isNotBlank() && !knownMissingLogoUrls.contains(logoUrl)
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
