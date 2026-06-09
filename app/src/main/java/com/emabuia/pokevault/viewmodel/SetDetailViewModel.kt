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
import com.emabuia.pokevault.data.remote.SetCodeMapper
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgSet
import com.emabuia.pokevault.data.remote.TranslationService
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.hasPositiveEurPrice
import com.emabuia.pokevault.util.minimumEurPriceOrZero
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

    private data class PriceLookupRequest(
        val cardName: String,
        val setCode: String,
        val cardNumber: String
    )

    private val tcgRepository = RepositoryProvider.tcgRepository
    private val firestoreRepository = FirestoreRepository()
    private val pokeWalletRepository = RepositoryProvider.pokeWalletRepository

    var uiState by mutableStateOf(SetDetailUiState())
        private set

    private var currentSetId: String? = null
    private var currentSourceMacro: String? = null
    private var translationJob: Job? = null
    private var italianSetPriceWarmupJob: Job? = null
    private var lastPricedCardId: String? = null
    private val requestedCardPriceIds = mutableSetOf<String>()
    private var italianSetPriceMap: Map<String, PokeWalletPriceData> = emptyMap()
    private var italianSetPriceMapSetId: String? = null
    private var italianSetPriceMapAttemptedSetId: String? = null
    private var italianSetPriceMapAttemptedAtMs: Long = 0L
    private val italianMirrorCounterpartCache = mutableMapOf<String, TcgCard?>()

    companion object {
        private const val ITALIAN_SET_MAP_RETRY_COOLDOWN_MS = 20_000L
    }

    private fun isItalianSection(set: TcgSet? = uiState.set): Boolean {
        val sectionMacro = currentSourceMacro?.trim()?.uppercase()
        if (sectionMacro == "ITA") return true
        return set?.language?.trim()?.uppercase() == "ITA"
    }

    private fun defaultCollectionLanguage(): String {
        return CardOptions.languageLabelForMacro(currentSourceMacro ?: uiState.set?.language)
            ?: CardOptions.LANGUAGES.first()
    }

    private fun resolvePriceLookup(card: TcgCard): PriceLookupRequest {
        val overlaySetCode = card.id
            .takeIf { it.startsWith("ita:", ignoreCase = true) }
            ?.split(':')
            ?.getOrNull(1)
            .orEmpty()

        val fallbackSetCode = card.set?.id
            ?.substringBefore("__")
            .orEmpty()

        return PriceLookupRequest(
            cardName = card.name,
            setCode = overlaySetCode.ifBlank { fallbackSetCode },
            cardNumber = card.number
        )
    }

    private fun normalizeCardNumberKey(raw: String): String? {
        val clean = raw.split("/").firstOrNull()?.trim().orEmpty()
        if (clean.isBlank()) return null
        return clean.toIntOrNull()?.toString() ?: clean.uppercase()
    }

    private fun withPriceData(card: TcgCard, priceData: PokeWalletPriceData): TcgCard {
        val cmPrices = CardMarketPrices(
            averageSellPrice = priceData.eurAvg,
            lowPrice = priceData.eurLow,
            trendPrice = priceData.eurTrend,
            avg1 = priceData.eurAvg1,
            avg7 = priceData.eurAvg7,
            avg30 = priceData.eurAvg30
        )
        return card.copy(
            cardmarket = CardMarket(
                url = priceData.cardMarketUrl.orEmpty(),
                prices = cmPrices
            )
        )
    }

    private fun priceDataFromCard(card: TcgCard): PokeWalletPriceData? {
        val prices = card.cardmarket?.prices ?: return null
        if (!prices.hasPositiveEurPrice()) return null
        return PokeWalletPriceData(
            eurAvg = prices.averageSellPrice,
            eurLow = prices.lowPrice,
            eurTrend = prices.trendPrice,
            eurAvg1 = prices.avg1,
            eurAvg7 = prices.avg7,
            eurAvg30 = prices.avg30,
            cardMarketUrl = card.cardmarket?.url,
            tcgPlayerUrl = card.tcgplayer?.url
        )
    }

    private suspend fun resolveItalianSetPriceMap(
        cards: List<TcgCard>,
        forceRefresh: Boolean = false
    ): Map<String, PokeWalletPriceData> {
        if (!isItalianSection()) return emptyMap()

        val activeSetId = currentSetId
        if (!forceRefresh && activeSetId != null && italianSetPriceMapSetId == activeSetId && italianSetPriceMap.isNotEmpty()) {
            return italianSetPriceMap
        }
        if (!forceRefresh && activeSetId != null && italianSetPriceMapAttemptedSetId == activeSetId) {
            val elapsed = System.currentTimeMillis() - italianSetPriceMapAttemptedAtMs
            if (elapsed < ITALIAN_SET_MAP_RETRY_COOLDOWN_MS) {
                return emptyMap()
            }
        }

        val setCode = cards.asSequence()
            .mapNotNull { card ->
                card.id.takeIf { it.startsWith("ita:", ignoreCase = true) }
                    ?.split(':')
                    ?.getOrNull(1)
                    ?.takeIf { it.isNotBlank() }
            }
            .firstOrNull()
            ?: currentSetId
                ?.substringBefore("__")
                ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()

        val canonicalSetCode = SetCodeMapper.normalizeDecklistSetCode(setCode) ?: setCode
        val resolved = pokeWalletRepository.getSetPriceMap(
            setCode = canonicalSetCode,
            forceRefresh = forceRefresh
        ).getOrDefault(emptyMap())

        if (activeSetId != null) {
            italianSetPriceMapAttemptedSetId = activeSetId
            italianSetPriceMapAttemptedAtMs = System.currentTimeMillis()
        }

        if (activeSetId != null && resolved.isNotEmpty()) {
            italianSetPriceMapSetId = activeSetId
            italianSetPriceMap = resolved
        }

        return resolved
    }

    private suspend fun resolveItalianMirrorPrices(
        card: TcgCard,
        forceRefreshRemote: Boolean = false
    ): PokeWalletPriceData? {
        if (!isItalianSection()) return null

        val italianSetId = currentSetId
            ?.takeIf { it.endsWith("__ita", ignoreCase = true) }
            ?: card.id
                .takeIf { it.startsWith("ita:", ignoreCase = true) }
                ?.split(':')
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?.let { "${it}__ita" }
            ?: return null

        val counterpartCacheKey = "$italianSetId:${card.number}"
        val counterpart = if (counterpartCacheKey in italianMirrorCounterpartCache) {
            italianMirrorCounterpartCache[counterpartCacheKey]
        } else {
            tcgRepository.getEnglishBaseCardForItalianOverlay(
                italianCardId = card.id,
                italianSetId = italianSetId,
                context = getApplication<Application>().applicationContext,
                forceRefresh = false
            ).getOrNull().also { resolved ->
                italianMirrorCounterpartCache[counterpartCacheKey] = resolved
            }
        } ?: return null

        val counterpartLookup = resolvePriceLookup(counterpart)
        val mirroredFromPokeWallet = pokeWalletRepository.getCardPrices(
            cardName = counterpartLookup.cardName,
            setCode = counterpartLookup.setCode,
            cardNumber = counterpartLookup.cardNumber,
            forceRefresh = forceRefreshRemote
        ).getOrNull()
        if (mirroredFromPokeWallet?.hasEurPrices == true) {
            return mirroredFromPokeWallet
        }

        val firstUsdPrice = counterpart.tcgplayer?.prices?.values?.firstOrNull {
            it.market != null || it.low != null
        }

        val counterpartCardMarket = counterpart.cardmarket?.prices
        val hasMirrorPrice = counterpartCardMarket?.hasPositiveEurPrice() == true
        if (!hasMirrorPrice) return null

        return PokeWalletPriceData(
            eurAvg = counterpartCardMarket?.averageSellPrice,
            eurLow = counterpartCardMarket?.lowPrice,
            eurTrend = counterpartCardMarket?.trendPrice,
            eurAvg1 = counterpartCardMarket?.avg1,
            eurAvg7 = counterpartCardMarket?.avg7,
            eurAvg30 = counterpartCardMarket?.avg30,
            cardMarketUrl = counterpart.cardmarket?.url,
            usdMarket = firstUsdPrice?.market,
            usdLow = firstUsdPrice?.low,
            tcgPlayerUrl = counterpart.tcgplayer?.url
        )
    }

    init {
        TranslationService.loadCache(application.applicationContext)
    }

    fun loadSet(setId: String, sourceMacro: String? = null) {
        val normalizedMacro = sourceMacro?.trim()?.uppercase()
        if (currentSetId == setId && currentSourceMacro == normalizedMacro) return
        italianSetPriceWarmupJob?.cancel()
        currentSetId = setId
        currentSourceMacro = normalizedMacro
        requestedCardPriceIds.clear()
        lastPricedCardId = null
        italianSetPriceMap = emptyMap()
        italianSetPriceMapSetId = null
        italianSetPriceMapAttemptedSetId = null
        italianSetPriceMapAttemptedAtMs = 0L
        italianMirrorCounterpartCache.clear()

        val context = getApplication<Application>().applicationContext
        uiState = uiState.copy(
            isLoading = true,
            isLoadingCards = true,
            searchQuery = "",
            translatedQuery = "",
            showOnlyMissing = false,
            showOnlyOwned = false,
            selectedType = null,
            selectedSupertype = null,
            errorMessage = null
        )

        viewModelScope.launch {
            val cardsDeferred = async {
                tcgRepository.getCardsBySet(
                    setId = setId,
                    context = context,
                    preferredImageMacro = normalizedMacro
                )
            }
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
                    if (normalizedMacro == "ITA" || resolvedSet.language?.trim()?.uppercase() == "ITA") {
                        italianSetPriceWarmupJob = viewModelScope.launch {
                            resolveItalianSetPriceMap(cards, forceRefresh = false)
                        }
                    }
                    observeOwnedCards(
                        setName = resolvedSet.name,
                        currentCards = cards
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(isLoading = false, isLoadingCards = false, errorMessage = "Errore: ${error.message}")
                }
        }
    }

    private fun observeOwnedCards(setName: String, currentCards: List<TcgCard>) {
        viewModelScope.launch {
            firestoreRepository.getOwnedCardsBySet(setName)
                .catch { e ->
                    if (BuildConfig.DEBUG) {
                        android.util.Log.w("SetDetailVM", "Errore osservazione carte possedute", e)
                    }
                }
                .collectLatest { ownedCards ->
                    val currentCardIds = currentCards
                        .asSequence()
                        .map { it.id }
                        .filter { it.isNotBlank() }
                        .toSet()

                    val ownedIds = ownedCards
                        .asSequence()
                        .map { it.apiCardId }
                        .filter { it.isNotBlank() && it in currentCardIds }
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
            val price = tcgCard.cardmarket?.prices.minimumEurPriceOrZero()
            val resolvedLanguage = language.ifBlank { defaultCollectionLanguage() }

            val card = PokemonCard(
                name = tcgCard.name, imageUrl = tcgCard.images.small,
                set = tcgCard.set?.name ?: uiState.set?.name ?: "", 
                rarity = tcgCard.rarity ?: "Unknown",
                type = tcgCard.types?.firstOrNull() ?: "Colorless",
                hp = tcgCard.hp?.toIntOrNull() ?: 0,
                supertype = tcgCard.supertype.ifBlank { "Pokémon" },
                subtypes = tcgCard.subtypes ?: emptyList(),
                estimatedValue = price,
                apiCardId = tcgCard.id, cardNumber = tcgCard.number,
                variant = variant, quantity = quantity, condition = condition, language = resolvedLanguage
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
            val preparedCards = cards.map { tcgCard ->
                val availableVariants = CardOptions.getVariantsForCard(
                    tcgCard.tcgplayer?.prices?.keys ?: emptySet(), tcgCard.rarity
                )
                val actualVariant = if (preferredVariant in availableVariants) preferredVariant
                    else availableVariants.firstOrNull() ?: "Holo"
                val price = tcgCard.cardmarket?.prices.minimumEurPriceOrZero()

                PokemonCard(
                    name = tcgCard.name, imageUrl = tcgCard.images.small,
                    set = tcgCard.set?.name ?: uiState.set?.name ?: "",
                    rarity = tcgCard.rarity ?: "Unknown",
                    type = tcgCard.types?.firstOrNull() ?: "Colorless",
                    hp = tcgCard.hp?.toIntOrNull() ?: 0,
                    supertype = tcgCard.supertype.ifBlank { "Pokémon" },
                    subtypes = tcgCard.subtypes ?: emptyList(),
                    estimatedValue = price,
                    apiCardId = tcgCard.id, cardNumber = tcgCard.number,
                    variant = actualVariant, quantity = 1, condition = "Near Mint", language = defaultCollectionLanguage()
                )
            }

            val addedIds = preparedCards.map { it.apiCardId }.toSet()
            val originalOwnedIds = uiState.ownedCardIds
            uiState = uiState.copy(ownedCardIds = originalOwnedIds + addedIds)

            firestoreRepository.addCards(preparedCards)
                .onSuccess {
                    uiState = uiState.copy(
                        successMessage = if (AppLocale.isItalian) "${preparedCards.size} carte aggiunte!" else "${preparedCards.size} cards added!"
                    )
                }
                .onFailure {
                    uiState = uiState.copy(
                        ownedCardIds = originalOwnedIds,
                        errorMessage = "Errore"
                    )
                }
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
        viewModelScope.launch {
            val lookup = resolvePriceLookup(card)
            val isStale = pokeWalletRepository.isCardPriceStale(
                setCode = lookup.setCode,
                cardNumber = lookup.cardNumber
            )
            if (lastPricedCardId == card.id && uiState.selectedCardPokeWalletPrices != null && !isStale) {
                return@launch
            }

            lastPricedCardId = card.id
            uiState = uiState.copy(
                isLoadingPokeWalletPrices = true,
                selectedCardPokeWalletPrices = priceDataFromCard(card)
            )

            val setPrices = resolveItalianSetPriceMap(uiState.cards, forceRefresh = false)
            val setPrice = normalizeCardNumberKey(card.number)?.let(setPrices::get)
            if (setPrice?.hasEurPrices == true) {
                uiState = uiState.copy(selectedCardPokeWalletPrices = setPrice, isLoadingPokeWalletPrices = false)
                return@launch
            }

            val mirroredPrices = resolveItalianMirrorPrices(card)
            if (mirroredPrices != null) {
                uiState = uiState.copy(selectedCardPokeWalletPrices = mirroredPrices, isLoadingPokeWalletPrices = false)
                return@launch
            }

            pokeWalletRepository.getCardPrices(
                cardName = lookup.cardName,
                setCode = lookup.setCode,
                cardNumber = lookup.cardNumber,
                forceRefresh = false
            )
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

    fun ensureCardPrice(card: TcgCard) {
        val current = uiState.cards.firstOrNull { it.id == card.id } ?: card
        if (!requestedCardPriceIds.add(card.id)) return

        viewModelScope.launch {
            val cm = current.cardmarket?.prices
            val hasApiEurPrice = cm.hasPositiveEurPrice()
            val lookup = resolvePriceLookup(current)
            val shouldRefreshExistingPrice = hasApiEurPrice && pokeWalletRepository.isCardPriceStale(
                setCode = lookup.setCode,
                cardNumber = lookup.cardNumber
            )
            if (hasApiEurPrice && !shouldRefreshExistingPrice) return@launch

            val setPrices = resolveItalianSetPriceMap(uiState.cards, forceRefresh = false)
            val setPrice = normalizeCardNumberKey(current.number)?.let(setPrices::get)

            if (setPrice?.hasEurPrices == true) {
                val merged = uiState.cards.map { listCard ->
                    if (listCard.id == current.id) withPriceData(listCard, setPrice) else listCard
                }
                uiState = uiState.copy(cards = merged)
            }

            val mirroredPrices = resolveItalianMirrorPrices(
                card = current,
                forceRefreshRemote = false
            )
            if (mirroredPrices != null) {
                val merged = uiState.cards.map { listCard ->
                    if (listCard.id == current.id) {
                        withPriceData(listCard, mirroredPrices)
                    } else {
                        listCard
                    }
                }
                uiState = uiState.copy(cards = merged)
                return@launch
            }

            val result = pokeWalletRepository.getCardPrices(
                cardName = lookup.cardName,
                setCode = lookup.setCode,
                cardNumber = lookup.cardNumber,
                forceRefresh = false
            )

            val priceData = result.getOrNull()
            if (priceData != null && ((priceData.eurAvg ?: 0.0) > 0.0 || (priceData.eurLow ?: 0.0) > 0.0)) {
                val merged = uiState.cards.map { listCard ->
                    if (listCard.id == current.id) {
                        withPriceData(listCard, priceData)
                    } else {
                        listCard
                    }
                }
                uiState = uiState.copy(cards = merged)
            }
        }
    }
}
