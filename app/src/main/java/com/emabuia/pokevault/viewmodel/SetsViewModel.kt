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
import com.emabuia.pokevault.data.remote.PokeTcgRepository
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgSet
import com.emabuia.pokevault.data.remote.TranslationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDate

data class SetsUiState(
    val allSets: List<TcgSet> = emptyList(),
    val filteredSets: List<TcgSet> = emptyList(),
    val cachedSetCardTotals: Map<String, Int> = emptyMap(),
    val selectedLanguageMacro: String = "ENG",
    val seriesList: List<String> = emptyList(),
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

    companion object {
        @Volatile
        private var hasValidatedSetsCacheThisSession: Boolean = false
    }

    private val repository = RepositoryProvider.tcgRepository
    private val firestoreRepository = FirestoreRepository()
    private var searchJob: Job? = null
    private var setSearchJob: Job? = null

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
            performOneTimeCacheClearIfNeeded(context)
            val cachedTotals = repository.getCachedSetCardCounts()
            repository.getSets(context = context)
                .onSuccess { sets ->
                    val shouldValidateLegacyCache = !hasValidatedSetsCacheThisSession && hasSuspiciousTotals(sets)
                    val resolvedSets = if (shouldValidateLegacyCache) {
                        repository.getSets(context = context, forceRefresh = true).getOrDefault(sets)
                    } else {
                        sets
                    }
                    hasValidatedSetsCacheThisSession = true
                    uiState = uiState.copy(
                        allSets = resolvedSets,
                        cachedSetCardTotals = cachedTotals,
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
            val cachedTotals = repository.getCachedSetCardCounts()
            repository.getSets(context = context)
                .onSuccess { sets ->
                    uiState = uiState.copy(
                        allSets = sets,
                        cachedSetCardTotals = cachedTotals,
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
            val cachedTotals = repository.getCachedSetCardCounts()
            repository.getSets(context = context, forceRefresh = true)
                .onSuccess { sets ->
                    hasValidatedSetsCacheThisSession = true
                    uiState = uiState.copy(
                        allSets = sets,
                        cachedSetCardTotals = cachedTotals,
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
            val price = tcgCard.cardmarket?.prices?.averageSellPrice
                ?: tcgCard.cardmarket?.prices?.lowPrice ?: 0.0

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
        val scopedSets = uiState.allSets.filter { set ->
            set.language == uiState.selectedLanguageMacro
        }
        val scopedSeries = buildSeriesListByLatestRelease(scopedSets)
        val selectedSeries = uiState.selectedSeries?.takeIf { it in scopedSeries }

        val filtered = scopedSets.filter { set ->
            val matchesSearch = uiState.searchQuery.isBlank() ||
                set.name.contains(uiState.searchQuery, ignoreCase = true) ||
                set.series.contains(uiState.searchQuery, ignoreCase = true)
            val matchesSeries = selectedSeries == null ||
                set.series == selectedSeries
            matchesSearch && matchesSeries
        }
        uiState = uiState.copy(
            seriesList = scopedSeries,
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

    private suspend fun performOneTimeCacheClearIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences("pokevault_cache_flags", Context.MODE_PRIVATE)
        val key = "sets_cache_proxy_v2"
        if (!prefs.contains(key)) {
            repository.clearSetsCache()
            prefs.edit().putBoolean(key, true).apply()
        }
    }

    private fun hasSuspiciousTotals(sets: List<TcgSet>): Boolean {
        // Cached legacy data may contain inflated totals (e.g. > 400 for standard sets).
        // Also trigger a refresh when un-enriched sets (printedTotal=0) are present,
        // so newly KV-backfilled values from the proxy are picked up each session.
        return sets.any { set ->
            val hasHugeLegacyCount = (set.printedTotal > 400) || (set.total > 400)
            val isPerfectOrderLegacyCount =
                set.name.contains("Perfect Order", ignoreCase = true) &&
                    (set.printedTotal > 124 || set.total > 124)
            val isUnEnriched = set.id.isNotBlank() && set.printedTotal == 0 && set.total == 0
            hasHugeLegacyCount || isPerfectOrderLegacyCount || isUnEnriched
        }
    }

    private fun buildSeriesListByLatestRelease(sets: List<TcgSet>): List<String> {
        return sets
            .groupBy { it.series }
            .entries
            .sortedWith(
                compareBy<Map.Entry<String, List<TcgSet>>> { it.key.equals("Altro", ignoreCase = true) }
                    .thenByDescending { (_, groupedSets) ->
                        groupedSets.maxOfOrNull { parseReleaseDate(it.releaseDate) } ?: LocalDate.MIN
                    }
            )
            .map { it.key }
    }

    private fun parseReleaseDate(raw: String): LocalDate {
        return runCatching { LocalDate.parse(raw) }.getOrDefault(LocalDate.MIN)
    }

}
