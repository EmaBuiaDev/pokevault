package com.emabuia.pokevault.viewmodel

import android.app.Application
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
        searchJob = viewModelScope.launch {
            delay(500)
            uiState = uiState.copy(isSearchingCards = true)
            val context = getApplication<Application>().applicationContext

            // Search with original query + translate in parallel
            val directDeferred = async { repository.searchCards(query) }
            val translatedDeferred = async {
                val translated = TranslationService.translateItToEn(query, context)
                if (translated != null && translated.lowercase() != query.lowercase()) {
                    repository.searchCards(translated)
                } else null
            }

            val directCards = directDeferred.await().getOrDefault(emptyList())
            val translatedCards = translatedDeferred.await()?.getOrDefault(emptyList()) ?: emptyList()

            val allCards = (directCards + translatedCards).distinctBy { it.id }
            uiState = uiState.copy(searchedCards = allCards, isSearchingCards = false)
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
            scopedSeriesGroups.flatMap { it.sets }
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
            val orderedSeriesGroups = officialSeriesOrder.map { canonicalSeries ->
                val orderedSets = groupedByCanonicalSeries[canonicalSeries]
                    .orEmpty()
                    .sortedByDescending { parseReleaseDate(it.releaseDate) }
                SeriesSetsGroup(
                    seriesKey = canonicalSeries,
                    seriesLabel = ItalianTranslations.translateSeriesName(canonicalSeries),
                    sets = orderedSets
                )
            }
            LanguageMacroGroup(macro = macro, seriesGroups = orderedSeriesGroups)
        }
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
            else -> "Other"
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

    private fun parseReleaseDate(raw: String): LocalDate {
        return runCatching { LocalDate.parse(raw) }.getOrDefault(LocalDate.MIN)
    }

}
