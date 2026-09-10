package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.CollectionStats
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.data.remote.TcgSet
import com.emabuia.pokevault.data.model.collectionGroupKey
import com.emabuia.pokevault.util.AppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SetCompletion(
    val setName: String,
    val ownedUnique: Int,
    val totalCards: Int,
    val symbolUrl: String? = null
) {
    /**
     * Limitata a 0..1: e' usata direttamente come frazione in
     * Modifier.fillMaxWidth(), che richiede quell'intervallo. Con le secret rare
     * ownedUnique puo' superare totalCards (il totale stampato del set), e senza
     * questo vincolo la barra di completamento faceva crashare lo schermo.
     */
    val percentage: Float
        get() = if (totalCards > 0) (ownedUnique.toFloat() / totalCards).coerceIn(0f, 1f) else 0f
}

data class StatsUiState(
    val stats: CollectionStats = CollectionStats(),
    val cards: List<PokemonCard> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    // Distribuzioni calcolate
    val cardsBySet: List<Pair<String, Int>> = emptyList(),
    val setCompletions: List<SetCompletion> = emptyList(),
    val cardsByRarity: List<Pair<String, Int>> = emptyList(),
    val cardsByType: List<Pair<String, Int>> = emptyList(),
    val gradedCount: Int = 0,
    val averageValue: Double = 0.0
)

class StatsViewModel : ViewModel() {

    private val repository = FirestoreRepository()
    private val tcgRepository = RepositoryProvider.tcgRepository

    var uiState by mutableStateOf(StatsUiState())
        private set

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getCards()
                .catch { e ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = e.message ?: AppLocale.unknownError
                    )
                }
                .collect { cards ->
                    // getSets() e' servito dalla cache del repository, ma resta una
                    // sospensione: va fatta fuori dal blocco di calcolo.
                    val allSets = tcgRepository.getSets().getOrNull().orEmpty()

                    val computed = withContext(Dispatchers.Default) {
                        buildStats(cards, allSets)
                    }

                    uiState = computed.copy(isLoading = false)
                }
        }
    }

    /**
     * Tutte le aggregazioni in un punto solo, fuori dal main thread.
     *
     * Prima questo blocco girava dentro il collector su viewModelScope (cioe'
     * Dispatchers.Main) e per di piu' chiamava repository.getCollectionStats(),
     * che rilegge da capo l'INTERA collezione e per giunta scrive su userDoc:
     * ogni carta aggiunta in qualsiasi punto dell'app costava due letture
     * complete piu' una scrittura. I totali si ricavano dallo stesso snapshot
     * che abbiamo gia' in mano.
     *
     * Ricavarli da un'unica fonte corregge anche la media: prima totalValue
     * veniva dalla seconda lettura e totalCards dalla prima, quindi il valore
     * medio poteva essere transitoriamente sbagliato.
     */
    private fun buildStats(cards: List<PokemonCard>, allSets: List<TcgSet>): StatsUiState {
        val totalCards = cards.sumOf { it.quantity }
        val totalValue = cards.sumOf { it.estimatedValue * it.quantity }

        val stats = CollectionStats(
            totalCards = totalCards,
            uniqueCards = cards.map { it.collectionGroupKey() }.toSet().size,
            totalValue = totalValue,
            mostValuable = cards.maxByOrNull { it.estimatedValue }?.name ?: "-"
        )

        val bySet = cards.groupBy { AppLocale.displaySetName(it.set).ifBlank { AppLocale.unknown } }
            .mapValues { (_, v) -> v.sumOf { it.quantity } }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }

        // Indice per nome invece di allSets.find{} per ogni gruppo: era
        // O(gruppi x set) su un catalogo di alcune centinaia di set.
        val setsByName = HashMap<String, TcgSet>(allSets.size * 2)
        allSets.forEach { setsByName.putIfAbsent(it.name, it) }

        val completions = cards.asSequence()
            .filter { it.set.isNotBlank() }
            .groupBy { it.set }
            .map { (rawSetName, setCards) ->
                val displayName = AppLocale.displaySetName(rawSetName)
                val uniqueOwned = setCards.asSequence()
                    .map { it.apiCardId }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .count()
                val tcgSet = setsByName[rawSetName] ?: setsByName[displayName]
                SetCompletion(displayName, uniqueOwned, tcgSet?.total ?: 0, tcgSet?.images?.symbol)
            }
            .filter { it.totalCards > 0 }
            .sortedByDescending { it.percentage }

        val byRarity = cards.groupBy {
                AppLocale.translateRarity(it.rarity).ifBlank { AppLocale.unknown }
            }
            .mapValues { (_, v) -> v.sumOf { it.quantity } }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }

        val byType = cards.groupBy {
                AppLocale.translateType(it.type).ifBlank { AppLocale.other }
            }
            .mapValues { (_, v) -> v.sumOf { it.quantity } }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }

        return StatsUiState(
            stats = stats,
            cards = cards,
            isLoading = false,
            cardsBySet = bySet,
            setCompletions = completions,
            cardsByRarity = byRarity,
            cardsByType = byType,
            gradedCount = cards.count { it.isGraded },
            averageValue = if (totalCards > 0) totalValue / totalCards else 0.0
        )
    }
}
