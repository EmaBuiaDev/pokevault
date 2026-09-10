package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.GoalAlbum
import com.emabuia.pokevault.data.model.GoalCriteriaType
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgSet
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Progresso calcolato on-the-fly, mai persistito. */
data class GoalProgress(
    val total: Int,
    val owned: Int,
    val missing: List<TcgCard>,
    val duplicates: List<PokemonCard>,
    val percentage: Float
)

class GoalAlbumViewModel : ViewModel() {

    private val repository = FirestoreRepository()
    private val tcgRepository = RepositoryProvider.tcgRepository
    private val premiumManager = PremiumManager.getInstance()

    // ── State ──────────────────────────────────────────────────────────────

    var goalAlbums by mutableStateOf<List<GoalAlbum>>(emptyList())
        private set

    var ownedCards by mutableStateOf<List<PokemonCard>>(emptyList())
        private set

    // Parte a true: il loader viene avviato in init, quindi al primo frame
    // stiamo gia' caricando. Con false, un dettaglio lampeggiava "non trovato".
    var isLoading by mutableStateOf(true)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)

    // ── Create form state ──────────────────────────────────────────────────

    var formName by mutableStateOf("")
    var formCriteriaType by mutableStateOf(GoalCriteriaType.SET)
    var formCriteriaValue by mutableStateOf("")

    // Dati di preview durante la creazione (caricati dalla TCG API)
    var previewCards by mutableStateOf<List<TcgCard>>(emptyList())
        private set
    var isPreviewLoading by mutableStateOf(false)
        private set

    // Sets e suggerimenti per picker
    var availableSets by mutableStateOf<List<TcgSet>>(emptyList())
        private set

    init {
        loadGoalAlbums()
        loadOwnedCards()
    }

    // ── Loaders ────────────────────────────────────────────────────────────

    private fun loadGoalAlbums() {
        viewModelScope.launch {
            isLoading = true
            repository.getGoalAlbums()
                .catch { isLoading = false }
                .collectLatest { list ->
                    goalAlbums = list
                    isLoading = false
                }
        }
    }

    private fun loadOwnedCards() {
        viewModelScope.launch {
            repository.getCards()
                .catch { }
                .collectLatest { cards ->
                    ownedCards = cards
                }
        }
    }

    fun loadAvailableSets() {
        if (availableSets.isNotEmpty()) return
        viewModelScope.launch {
            tcgRepository.getSets().onSuccess { availableSets = it }
        }
    }

    // ── Preview ────────────────────────────────────────────────────────────

    /** Aggiorna la lista di carte preview quando il criterio cambia. */
    fun loadPreview() {
        if (formCriteriaValue.isBlank()) {
            previewCards = emptyList()
            return
        }
        viewModelScope.launch {
            isPreviewLoading = true
            previewCards = fetchTargetCards(GoalCriteriaType.SET, formCriteriaValue)
            isPreviewLoading = false
        }
    }

    // ── Progress ───────────────────────────────────────────────────────────

    /**
     * Calcola il progresso on-the-fly confrontando targetCardApiIds con
     * le carte dell'utente. Non fa rete, non altera lo stato persistito.
     */
    fun getProgress(album: GoalAlbum, targetCards: List<TcgCard>): GoalProgress {
        val ownedApiIds = ownedCards.groupBy { it.apiCardId.trim() }

        val owned = album.targetCardApiIds.count { apiId ->
            ownedApiIds.containsKey(apiId) && (ownedApiIds[apiId]?.sumOf { it.quantity } ?: 0) >= 1
        }

        val missing = targetCards.filter { tc ->
            val qty = ownedApiIds[tc.id.trim()]?.sumOf { it.quantity } ?: 0
            qty == 0
        }

        val duplicates = ownedCards.filter { pc ->
            pc.apiCardId.trim() in album.targetCardApiIds && pc.quantity > 1
        }

        val percentage = if (album.targetCardApiIds.isEmpty()) 0f
        else (owned.toFloat() / album.targetCardApiIds.size * 100f).coerceIn(0f, 100f)

        return GoalProgress(
            total = album.targetCardApiIds.size,
            owned = owned,
            missing = missing,
            duplicates = duplicates,
            percentage = percentage
        )
    }

    // ── Premium gate ───────────────────────────────────────────────────────

    fun canCreate(): Boolean = premiumManager.canCreateGoalAlbum(goalAlbums.size)

    // ── CRUD ───────────────────────────────────────────────────────────────

    fun saveGoalAlbum(onSuccess: () -> Unit) {
        if (formName.isBlank() || formCriteriaValue.isBlank()) return
        viewModelScope.launch {
            isSaving = true
            val criteriaType = GoalCriteriaType.SET
            val targetApiIds = fetchTargetCards(criteriaType, formCriteriaValue).map { it.id }
            val album = GoalAlbum(
                name = formName.trim(),
                criteriaType = criteriaType,
                criteriaValue = formCriteriaValue.trim(),
                targetCardApiIds = targetApiIds
            )
            repository.saveGoalAlbum(album).onSuccess {
                resetForm()
                isSaving = false
                onSuccess()
            }.onFailure { e ->
                errorMessage = e.message
                isSaving = false
            }
        }
    }

    fun deleteGoalAlbum(albumId: String) {
        viewModelScope.launch {
            repository.deleteGoalAlbum(albumId)
        }
    }

    fun addMissingCardToCollection(card: TcgCard, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val variants = CardOptions.getVariantsForCard(
                card.tcgplayer?.prices?.keys ?: emptySet(),
                card.rarity
            )
            val selectedVariant = variants.firstOrNull() ?: "Holo"

            val market = card.cardmarket?.prices
            val estimatedValue =
                market?.avg30
                    ?: market?.trendPrice
                    ?: market?.averageSellPrice
                    ?: market?.lowPrice
                    ?: 0.0

            val pokemonCard = PokemonCard(
                name = card.name,
                imageUrl = card.images.small,
                set = card.set?.name ?: "",
                rarity = card.rarity ?: "Unknown",
                type = card.types?.firstOrNull() ?: "Colorless",
                hp = card.hp?.toIntOrNull() ?: 0,
                supertype = card.supertype.ifBlank { "Pokémon" },
                subtypes = card.subtypes ?: emptyList(),
                estimatedValue = estimatedValue,
                apiCardId = card.id,
                cardNumber = card.number,
                variant = selectedVariant,
                quantity = 1,
                condition = "Near Mint",
                language = "🇮🇹 Italiano"
            )

            repository.addCard(pokemonCard)
                .onSuccess { onResult(true) }
                .onFailure {
                    errorMessage = it.message
                    onResult(false)
                }
        }
    }

    fun getGoalAlbumById(id: String): GoalAlbum? = goalAlbums.find { it.id == id }

    fun resetForm() {
        formName = ""
        formCriteriaType = GoalCriteriaType.SET
        formCriteriaValue = ""
        previewCards = emptyList()
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private suspend fun fetchTargetCards(
        type: GoalCriteriaType,
        value: String
    ): List<TcgCard> = when (type) {
        GoalCriteriaType.SET -> {
            tcgRepository.getCardsBySet(value).getOrElse { emptyList() }
        }
        GoalCriteriaType.RARITY -> {
            tcgRepository.searchCards("rarity:\"$value\"").getOrElse { emptyList() }
        }
        GoalCriteriaType.SUPERTYPE -> {
            tcgRepository.searchCards("supertype:\"$value\"").getOrElse { emptyList() }
        }
        GoalCriteriaType.TYPE -> {
            tcgRepository.searchCards("types:\"$value\"").getOrElse { emptyList() }
        }
        GoalCriteriaType.CUSTOM -> {
            // Per CUSTOM value è una lista di apiIds separata da virgola
            value.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .mapNotNull { apiId -> tcgRepository.getCard(apiId).getOrNull() }
        }
    }
}
