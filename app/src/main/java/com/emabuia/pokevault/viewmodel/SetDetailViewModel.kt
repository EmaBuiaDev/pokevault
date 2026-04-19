package com.emabuia.pokevault.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import com.emabuia.pokevault.BuildConfig
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.PokeWalletPriceData
import com.emabuia.pokevault.data.remote.PokeWalletRepository
import com.emabuia.pokevault.data.remote.PokeTcgRepository
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.data.remote.CardMarket
import com.emabuia.pokevault.data.remote.CardMarketPrices
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgSet
import com.emabuia.pokevault.data.remote.TranslationService
import com.emabuia.pokevault.util.AppLocale
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class SetDetailUiState(
    val set: TcgSet? = null,
    val cards: List<TcgCard> = emptyList(),
    val ownedCardIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isLoadingCards: Boolean = true,
    val isAddingCard: String? = null,
    val viewMode: String = "grid",
    val searchQuery: String = "",
    val translatedQuery: String = "",
    val showOnlyMissing: Boolean = false,
    val showOnlyOwned: Boolean = false,
    val selectedType: String? = null,
    val selectedSupertype: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val selectedCardPokeWalletPrices: PokeWalletPriceData? = null,
    val isLoadingPokeWalletPrices: Boolean = false
) {
    val ownedCount: Int get() = cards.count { it.id in ownedCardIds }
    val totalCount: Int get() = cards.size
    val displayTotal: Int get() = if (cards.isNotEmpty()) cards.size else (set?.total ?: 0)
    val completionPercent: Int get() =
        if (displayTotal > 0) (ownedCount * 100 / displayTotal) else 0

    val availableTypes: List<String> get() = cards.flatMap { it.types ?: emptyList() }.distinct().sorted()
    val availableSupertypes: List<String> get() = cards.map { it.supertype }.distinct().sorted()
}

class SetDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val tcgRepository = RepositoryProvider.tcgRepository
    private val firestoreRepository = FirestoreRepository()
    private val pokeWalletRepository = RepositoryProvider.pokeWalletRepository

    var uiState by mutableStateOf(SetDetailUiState())
        private set

    private var currentSetId: String? = null
    private var translationJob: Job? = null
    private var lastPricedCardId: String? = null
    private val hydratedPriceSetIds = mutableSetOf<String>()
    private val hydrationPrefs = application.applicationContext.getSharedPreferences("price_hydration", Application.MODE_PRIVATE)

    companion object {
        private const val PRICE_HYDRATION_WINDOW_MS = 24L * 60 * 60 * 1000
        private const val MAX_PRICE_HYDRATION_REQUESTS_PER_SET = 20
    }

    init {
        TranslationService.loadCache(application.applicationContext)
    }

    fun loadSet(setId: String) {
        if (currentSetId == setId) return
        currentSetId = setId

        val context = getApplication<Application>().applicationContext
        uiState = uiState.copy(isLoading = true, isLoadingCards = true)

        viewModelScope.launch {
            val cardsDeferred = async { tcgRepository.getCardsBySet(setId, context = context) }
            val setDeferred = async { tcgRepository.getSetInfo(setId) }

            val cardsResult = cardsDeferred.await()
            val setResult = setDeferred.await()

            cardsResult
                .onSuccess { cards ->
                    val setInfo = setResult.getOrNull()
                    val resolvedSet = setInfo ?: TcgSet(
                        id = setId,
                        name = cards.firstOrNull()?.set?.name ?: setId,
                        series = cards.firstOrNull()?.set?.series ?: ""
                    )

                    uiState = uiState.copy(
                        set = resolvedSet,
                        cards = cards,
                        isLoading = false,
                        isLoadingCards = false
                    )
                    observeOwnedCards(resolvedSet.name)
                    if (shouldHydratePrices(setId) && hydratedPriceSetIds.add(setId)) {
                        launch { enrichMissingCardPrices(setId, cards) }
                    }
                }
                .onFailure { error ->
                    uiState = uiState.copy(isLoading = false, isLoadingCards = false, errorMessage = "Errore: ${error.message}")
                }
        }
    }

    private fun observeOwnedCards(setName: String) {
        viewModelScope.launch {
            firestoreRepository.getOwnedCardsBySet(setName)
                .catch { e ->
                    if (BuildConfig.DEBUG) {
                        android.util.Log.w("SetDetailVM", "Errore osservazione carte possedute", e)
                    }
                }
                .collectLatest { ownedCards ->
                    val ownedIds = ownedCards
                        .filter { it.apiCardId.isNotBlank() }
                        .map { it.apiCardId }
                        .toSet()
                    uiState = uiState.copy(ownedCardIds = ownedIds)
                }
        }
    }

    fun updateSearchQuery(query: String) {
        // Check cached translation first for instant results
        val cached = TranslationService.getCached(query)
        uiState = uiState.copy(searchQuery = query, translatedQuery = cached ?: "")

        // Translate async if no cache and query is long enough
        translationJob?.cancel()
        if (query.length >= 3 && cached == null) {
            translationJob = viewModelScope.launch {
                delay(400)
                val context = getApplication<Application>().applicationContext
                val translated = TranslationService.translateItToEn(query, context)
                if (translated != null && uiState.searchQuery == query) {
                    uiState = uiState.copy(translatedQuery = translated)
                }
            }
        }
    }

    fun toggleShowOnlyMissing() {
        uiState = uiState.copy(
            showOnlyMissing = !uiState.showOnlyMissing,
            showOnlyOwned = false
        )
    }

    fun toggleShowOnlyOwned() {
        uiState = uiState.copy(
            showOnlyOwned = !uiState.showOnlyOwned,
            showOnlyMissing = false
        )
    }

    fun selectType(type: String?) {
        uiState = uiState.copy(selectedType = type)
    }

    fun selectSupertype(supertype: String?) {
        uiState = uiState.copy(selectedSupertype = supertype)
    }

    fun addCardWithDetails(tcgCard: TcgCard, variant: String, quantity: Int, condition: String, language: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isAddingCard = tcgCard.id)
            val variantKey = CardOptions.getVariantApiKey(variant)
            val price = tcgCard.tcgplayer?.prices?.get(variantKey)?.market
                ?: tcgCard.cardmarket?.prices?.lowPrice
                ?: tcgCard.cardmarket?.prices?.averageSellPrice ?: 0.0

            val card = PokemonCard(
                name = tcgCard.name, imageUrl = tcgCard.images.small,
                set = tcgCard.set?.name ?: uiState.set?.name ?: "", 
                rarity = tcgCard.rarity ?: "Unknown",
                type = tcgCard.types?.firstOrNull() ?: "Colorless",
                hp = tcgCard.hp?.toIntOrNull() ?: 0, estimatedValue = price,
                apiCardId = tcgCard.id, cardNumber = tcgCard.number,
                variant = variant, quantity = quantity, condition = condition, language = language
            )
            firestoreRepository.addCard(card)
                .onSuccess {
                    uiState = uiState.copy(
                        successMessage = "${tcgCard.name} aggiunta!",
                        ownedCardIds = uiState.ownedCardIds + tcgCard.id
                    )
                    // Keep highlight visible briefly so feedback is noticeable.
                    delay(350)
                    uiState = uiState.copy(isAddingCard = null)
                }
                .onFailure {
                    uiState = uiState.copy(errorMessage = "Errore")
                    delay(350)
                    uiState = uiState.copy(isAddingCard = null)
                }
        }
    }

    fun addMultipleCards(cards: List<TcgCard>, preferredVariant: String) {
        viewModelScope.launch {
            var addedCount = 0
            val addedIds = mutableSetOf<String>()
            cards.forEach { tcgCard ->
                val availableVariants = CardOptions.getVariantsForCard(
                    tcgCard.tcgplayer?.prices?.keys ?: emptySet(), tcgCard.rarity
                )
                val actualVariant = if (preferredVariant in availableVariants) preferredVariant
                    else availableVariants.firstOrNull() ?: "Holo"

                val variantKey = CardOptions.getVariantApiKey(actualVariant)
                val price = tcgCard.tcgplayer?.prices?.get(variantKey)?.market
                    ?: tcgCard.cardmarket?.prices?.lowPrice
                    ?: tcgCard.cardmarket?.prices?.averageSellPrice ?: 0.0

                val card = PokemonCard(
                    name = tcgCard.name, imageUrl = tcgCard.images.small,
                    set = tcgCard.set?.name ?: uiState.set?.name ?: "",
                    rarity = tcgCard.rarity ?: "Unknown",
                    type = tcgCard.types?.firstOrNull() ?: "Colorless",
                    hp = tcgCard.hp?.toIntOrNull() ?: 0, estimatedValue = price,
                    apiCardId = tcgCard.id, cardNumber = tcgCard.number,
                    variant = actualVariant, quantity = 1, condition = "Near Mint", language = "🇮🇹 Italiano"
                )
                firestoreRepository.addCard(card).onSuccess {
                    addedCount++
                    addedIds += tcgCard.id
                }
            }
            uiState = uiState.copy(
                ownedCardIds = uiState.ownedCardIds + addedIds,
                successMessage = if (AppLocale.isItalian) "$addedCount carte aggiunte!" else "$addedCount cards added!"
            )
        }
    }

    fun removeCard(tcgCard: TcgCard) {
        viewModelScope.launch {
            firestoreRepository.deleteCardByApiId(tcgCard.id)
                .onSuccess { uiState = uiState.copy(successMessage = "${tcgCard.name} rimossa") }
                .onFailure { uiState = uiState.copy(errorMessage = "Errore") }
        }
    }

    fun setViewMode(mode: String) { uiState = uiState.copy(viewMode = mode) }
    fun clearMessages() { uiState = uiState.copy(errorMessage = null, successMessage = null) }

    fun loadPokeWalletPrices(card: TcgCard) {
        if (lastPricedCardId == card.id && uiState.selectedCardPokeWalletPrices != null) return
        lastPricedCardId = card.id
        uiState = uiState.copy(isLoadingPokeWalletPrices = true, selectedCardPokeWalletPrices = null)
        viewModelScope.launch {
            val setCode = card.set?.id ?: ""
            val cardNumber = card.number
            pokeWalletRepository.getCardPrices(card.name, setCode, cardNumber)
                .onSuccess { prices ->
                    uiState = uiState.copy(selectedCardPokeWalletPrices = prices, isLoadingPokeWalletPrices = false)
                }
                .onFailure {
                    uiState = uiState.copy(isLoadingPokeWalletPrices = false)
                }
        }
    }

    fun clearPokeWalletPrices() {
        lastPricedCardId = null
        uiState = uiState.copy(selectedCardPokeWalletPrices = null, isLoadingPokeWalletPrices = false)
    }

    private suspend fun enrichMissingCardPrices(setId: String, cards: List<TcgCard>) {
        val needingPrices = cards.filter { card ->
            val cm = card.cardmarket?.prices
            val hasApiEurPrice = (cm?.averageSellPrice ?: 0.0) > 0.0 || (cm?.lowPrice ?: 0.0) > 0.0
            !hasApiEurPrice
        }.take(MAX_PRICE_HYDRATION_REQUESTS_PER_SET)

        if (needingPrices.isEmpty()) return

        val updatedById = HashMap<String, TcgCard>()
        val lastIndex = needingPrices.lastIndex

        needingPrices.forEachIndexed { index, card ->
            val priceData = pokeWalletRepository
                .getCardPrices(card.name, card.set?.id.orEmpty(), card.number)
                .getOrNull()

            if (priceData != null && ((priceData.eurAvg ?: 0.0) > 0.0 || (priceData.eurLow ?: 0.0) > 0.0)) {
                val cmPrices = CardMarketPrices(
                    averageSellPrice = priceData.eurAvg,
                    lowPrice = priceData.eurLow,
                    trendPrice = priceData.eurTrend,
                    avg1 = priceData.eurAvg1,
                    avg7 = priceData.eurAvg7,
                    avg30 = priceData.eurAvg30
                )
                updatedById[card.id] = card.copy(
                    cardmarket = CardMarket(
                        url = priceData.cardMarketUrl.orEmpty(),
                        prices = cmPrices
                    )
                )
            }

            // Apply in small batches to keep UI responsive while prices stream in.
            if ((index + 1) % 8 == 0 || index == lastIndex) {
                if (updatedById.isNotEmpty()) {
                    val merged = uiState.cards.map { current -> updatedById[current.id] ?: current }
                    uiState = uiState.copy(cards = merged)
                }
            }
        }

        // Mark hydration window only if at least one EUR API price was resolved.
        if (updatedById.isNotEmpty()) {
            hydratedPriceSetIds.add(setId)
            markHydration(setId)
        } else {
            // Allow retries in the same session if no prices were resolved.
            hydratedPriceSetIds.remove(setId)
        }
    }

    private fun shouldHydratePrices(setId: String): Boolean {
        val lastHydration = hydrationPrefs.getLong("set_$setId", 0L)
        return System.currentTimeMillis() - lastHydration > PRICE_HYDRATION_WINDOW_MS
    }

    private fun markHydration(setId: String) {
        hydrationPrefs.edit().putLong("set_$setId", System.currentTimeMillis()).apply()
    }
}
