package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.util.GradeBucket
import com.emabuia.pokevault.util.GradeTier
import com.emabuia.pokevault.util.GradedCompany
import com.emabuia.pokevault.util.GradedLab
import com.emabuia.pokevault.util.GradedSort
import com.emabuia.pokevault.util.GradedSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Lo stato della sezione Gradate.
 *
 * I conti non stanno qui: li fa [GradedLab], che e' testato. Questo oggetto
 * tiene le carte, cosa l'utente ha chiesto di vedere, e il risultato pronto per
 * la griglia.
 */
data class GradedCardsUiState(
    val allCards: List<PokemonCard> = emptyList(),
    val visibleCards: List<PokemonCard> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val selectedCompany: String? = null,
    val selectedTier: GradeTier? = null,
    val sort: GradedSort = GradedSort.GRADE_DESC,
    val summary: GradedSummary = GradedLab.summary(emptyList()),
    val companies: List<GradedCompany> = emptyList(),
    val spread: List<GradeBucket> = emptyList()
) {
    /** Vuoto vero: non ha gradate, non "i filtri non pescano niente". */
    val isEmpty: Boolean get() = allCards.isEmpty()

    /** I filtri sono al loro posto di partenza. */
    val hasFilters: Boolean
        get() = searchQuery.isNotBlank() || selectedCompany != null || selectedTier != null
}

class GradedCardsViewModel : ViewModel() {

    private val repository = FirestoreRepository()

    var uiState by mutableStateOf(GradedCardsUiState())
        private set

    /**
     * Il collect in corso.
     *
     * Serve a [retry]: senza cancellare il precedente, un secondo tentativo
     * lascerebbe due listener Firestore attaccati alla stessa collezione.
     */
    private var loadJob: Job? = null

    init {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        loadJob = viewModelScope.launch {
            repository.getCards()
                .catch { e ->
                    // Il messaggio tecnico non finisce a schermo: la schermata
                    // mostra una frase sua e un pulsante per riprovare.
                    uiState = uiState.copy(isLoading = false, errorMessage = e.message ?: "")
                }
                .collect { cards ->
                    val graded = cards.filter { it.isGraded }
                    uiState = uiState.copy(
                        allCards = graded,
                        visibleCards = visible(graded, uiState),
                        isLoading = false,
                        errorMessage = null,
                        summary = GradedLab.summary(graded),
                        companies = GradedLab.companyCounts(graded),
                        spread = GradedLab.tierCounts(graded)
                    )
                }
        }
    }

    /** Dopo un errore. Ricomincia da zero, listener compreso. */
    fun retry() = load()

    fun updateSearch(query: String) {
        uiState = uiState.copy(searchQuery = query).withVisible()
    }

    /** Ripassare l'ente gia' selezionato lo deseleziona: il chip e' un toggle. */
    fun filterByCompany(company: String?) {
        val next = if (company == uiState.selectedCompany) null else company
        uiState = uiState.copy(selectedCompany = next).withVisible()
    }

    fun filterByTier(tier: GradeTier?) {
        val next = if (tier == uiState.selectedTier) null else tier
        uiState = uiState.copy(selectedTier = next).withVisible()
    }

    fun setSort(sort: GradedSort) {
        if (sort == uiState.sort) return
        uiState = uiState.copy(sort = sort).withVisible()
    }

    fun clearFilters() {
        uiState = uiState.copy(
            searchQuery = "",
            selectedCompany = null,
            selectedTier = null
        ).withVisible()
    }

    private fun GradedCardsUiState.withVisible(): GradedCardsUiState =
        copy(visibleCards = visible(allCards, this))

    private fun visible(cards: List<PokemonCard>, state: GradedCardsUiState) =
        GradedLab.visible(
            cards = cards,
            query = state.searchQuery,
            company = state.selectedCompany,
            tier = state.selectedTier,
            sort = state.sort
        )
}
