package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.data.model.WishlistIcons
import com.emabuia.pokevault.data.model.WishlistItem
import com.emabuia.pokevault.data.model.WishlistPriority
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.WishlistLab
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope

class WishlistViewModel : ViewModel() {

    companion object {
        /**
         * Il limite e' uno solo, quello di PremiumManager.
         *
         * Prima era duplicato qui, e l'unico test esistente sui limiti free
         * verificava QUESTA copia, non quella che governa davvero i gate:
         * potevano divergere senza che nulla se ne accorgesse.
         */
        const val FREE_WISHLIST_LIMIT = PremiumManager.FREE_WISHLIST_LIMIT

        fun isValidWishlistName(name: String): Boolean {
            val normalized = name.trim()
            return normalized.isNotEmpty() && normalized.length <= 40
        }

        fun normalizeIconKey(iconKey: String): String {
            return if (iconKey in WishlistIcons.all) iconKey else WishlistIcons.POKEBALL
        }

        fun canCreateWishlistCount(isPremium: Boolean, currentCount: Int): Boolean {
            return isPremium || currentCount < FREE_WISHLIST_LIMIT
        }
    }

    private val repository = FirestoreRepository()
    private val tcgRepository = RepositoryProvider.tcgRepository

    var wishlists by mutableStateOf<List<Wishlist>>(emptyList())
        private set

    /**
     * Le carte gia' in collezione.
     *
     * Servono a dire quali carte della wishlist sono gia' state prese: il
     * confronto e' locale, quindi l'elenco puo' mostrare l'avanzamento di ogni
     * lista senza scaricare nemmeno una carta.
     */
    var ownedCards by mutableStateOf<List<PokemonCard>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var successMessage by mutableStateOf<String?>(null)
        private set

    /**
     * Le TcgCard gia' risolte, per id.
     *
     * Il dettaglio ricarica le carte a ogni cambio di `cardIds` (una modifica
     * di priorita' non lo fa, ma un'aggiunta si'): senza cache ogni ritocco
     * rifaceva una chiamata per ogni carta della lista.
     */
    private val cardCache = ConcurrentHashMap<String, TcgCard>()

    private var isObservingOwnedCards = false

    init {
        loadWishlists()
    }

    private fun loadWishlists() {
        viewModelScope.launch {
            isLoading = true
            repository.getWishlists()
                .catch {
                    isLoading = false
                    errorMessage = if (AppLocale.isItalian) "Errore nel caricamento wishlist" else "Error loading wishlists"
                }
                .collectLatest { list ->
                    wishlists = list
                    isLoading = false
                }
        }
    }

    /**
     * Avvia l'ascolto della collezione, una volta sola.
     *
     * Non parte da `init` perche' questo ViewModel vive anche dentro il
     * dettaglio di un set e dentro il Chase, dove serve solo a sapere in che
     * liste sta una carta: li' un listener sull'intera collezione dell'utente
     * sarebbe un ascolto aperto per un dato che nessuno guarda. Lo chiamano le
     * due schermate della Wishlist, che invece il possesso lo mostrano.
     */
    fun observeOwnedCards() {
        if (isObservingOwnedCards) return
        isObservingOwnedCards = true
        viewModelScope.launch {
            repository.getCards()
                .catch { }
                .collectLatest { cards -> ownedCards = cards }
        }
    }

    /**
     * Indice di tutti i cardId in wishlist, ricalcolato solo quando le wishlist
     * cambiano.
     *
     * isCardWishlisted viene chiamata per OGNI carta visibile della griglia di
     * un set (~120 celle) a ogni ricomposizione, e prima scorreva tutte le
     * wishlist facendo `cardId in it.cardIds` su una List: una scansione
     * lineare dentro un ciclo, cioe' lavoro quadratico durante lo scroll.
     */
    private val wishlistedCardIds: Set<String> by derivedStateOf {
        wishlists.flatMapTo(HashSet()) { it.cardIds }
    }

    /** Gli id catalogo posseduti, indicizzati una volta sola. */
    val ownedCardIds: Set<String> by derivedStateOf {
        ownedCards.asSequence()
            .map { it.apiCardId.trim() }
            .filter { it.isNotEmpty() }
            .toHashSet()
    }

    fun isCardWishlisted(cardId: String): Boolean = cardId in wishlistedCardIds

    fun canCreateWishlist(isPremium: Boolean): Boolean {
        return canCreateWishlistCount(isPremium, wishlists.size)
    }

    fun getWishlistById(wishlistId: String): Wishlist? {
        return wishlists.firstOrNull { it.id == wishlistId }
    }

    fun getWishlistIdsForCard(cardId: String): Set<String> {
        return wishlists.asSequence()
            .filter { cardId in it.cardIds }
            .map { it.id }
            .toSet()
    }

    fun removeCardFromWishlist(wishlistId: String, cardId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repository.removeCardFromWishlist(wishlistId, cardId)
                .onSuccess {
                    successMessage = if (AppLocale.isItalian) "Carta rimossa dalla lista" else "Card removed from list"
                    onResult(true)
                }
                .onFailure {
                    errorMessage = if (AppLocale.isItalian) "Impossibile rimuovere la carta" else "Could not remove the card"
                    onResult(false)
                }
        }
    }

    suspend fun loadCardsForWishlist(wishlist: Wishlist): List<TcgCard> {
        if (wishlist.cardIds.isEmpty()) return emptyList()

        val missingIds = wishlist.cardIds.filter { it !in cardCache }
        if (missingIds.isNotEmpty()) {
            coroutineScope {
                missingIds.map { cardId ->
                    async { tcgRepository.getCard(cardId).getOrNull()?.also { cardCache[cardId] = it } }
                }.awaitAll()
            }
        }

        return wishlist.cardIds.mapNotNull { cardCache[it] }
    }

    fun addCardToWishlist(
        wishlistId: String,
        cardId: String,
        priority: WishlistPriority = WishlistPriority.MEDIUM,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val item = if (priority == WishlistPriority.MEDIUM) null else WishlistItem(priority = priority.key)
            repository.addCardToWishlist(wishlistId, cardId, item)
                .onSuccess {
                    successMessage = if (AppLocale.isItalian) "Carta aggiunta alla wishlist" else "Card added to wishlist"
                    onResult(true)
                }
                .onFailure {
                    errorMessage = if (AppLocale.isItalian) "Impossibile aggiungere la carta" else "Could not add the card"
                    onResult(false)
                }
        }
    }

    /**
     * Aggiunge lo stesso blocco di carte a piu' liste.
     *
     * Una scrittura per lista, non per carta: e' quello che serve al Chase per
     * spedire in wishlist tutte le mancanti di un set.
     */
    fun addCardsToWishlists(
        wishlistIds: Set<String>,
        cardIds: List<String>,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (wishlistIds.isEmpty() || cardIds.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val failed = supervisorScope {
                wishlistIds
                    .map { id -> async { repository.addCardsToWishlist(id, cardIds).isFailure } }
                    .awaitAll()
                    .any { it }
            }
            if (failed) {
                errorMessage = AppLocale.chaseWishlistError
                onResult(false)
            } else {
                successMessage = AppLocale.chaseWishlistAdded(cardIds.size)
                onResult(true)
            }
        }
    }

    /**
     * [priority] vale solo per le liste in cui la carta entra adesso: su quelle
     * dove era gia' dentro la priorita' e' gia' stata decisa, e sovrascriverla
     * riaprendo il picker per aggiungerla a una lista in piu' cancellerebbe una
     * scelta fatta apposta.
     */
    fun updateCardWishlists(
        cardId: String,
        targetWishlistIds: Set<String>,
        priority: WishlistPriority = WishlistPriority.MEDIUM,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val currentWishlistIds = getWishlistIdsForCard(cardId)
            val toAdd = targetWishlistIds - currentWishlistIds
            val toRemove = currentWishlistIds - targetWishlistIds

            if (toAdd.isEmpty() && toRemove.isEmpty()) {
                onResult(true)
                return@launch
            }

            val newItem = WishlistItem(priority = priority.key)
            val failures = supervisorScope {
                val addResults = toAdd.map { wishlistId ->
                    async { repository.addCardToWishlist(wishlistId, cardId, newItem).isFailure }
                }
                val removeResults = toRemove.map { wishlistId ->
                    async { repository.removeCardFromWishlist(wishlistId, cardId).isFailure }
                }
                (addResults + removeResults).awaitAll().count { it }
            }

            if (failures == 0) {
                successMessage = if (AppLocale.isItalian) "Wishlist aggiornate" else "Wishlists updated"
                onResult(true)
            } else {
                errorMessage = if (AppLocale.isItalian) {
                    "Alcune wishlist non sono state aggiornate"
                } else {
                    "Some wishlists could not be updated"
                }
                onResult(false)
            }
        }
    }

    fun removeCardFromAllWishlists(cardId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repository.removeCardFromAllWishlists(cardId)
                .onSuccess {
                    successMessage = if (AppLocale.isItalian) "Carta rimossa dalla wishlist" else "Card removed from wishlist"
                    onResult(true)
                }
                .onFailure {
                    errorMessage = if (AppLocale.isItalian) "Impossibile rimuovere la carta" else "Could not remove the card"
                    onResult(false)
                }
        }
    }

    // ── Metadati per carta ────────────────────────────────────────────────

    /**
     * Priorita', nota e tetto di prezzo di una carta dentro una lista.
     *
     * La voce viene riscritta per intero e conserva l'`addedAt` esistente: e'
     * un documento solo, e il merge parziale su una mappa annidata costerebbe
     * una lettura in piu' per nulla.
     */
    fun updateCardMeta(
        wishlistId: String,
        cardId: String,
        priority: WishlistPriority,
        note: String,
        targetPrice: Double,
        onResult: (Boolean) -> Unit = {}
    ) {
        val wishlist = getWishlistById(wishlistId)
        if (wishlist == null) {
            errorMessage = AppLocale.wishlistNotFound
            onResult(false)
            return
        }

        val existing = wishlist.itemFor(cardId)
        val updated = WishlistItem(
            priority = priority.key,
            note = WishlistLab.normalizeNote(note),
            targetPrice = targetPrice.coerceIn(0.0, WishlistLab.MAX_PRICE),
            addedAt = existing.addedAt
        )

        viewModelScope.launch {
            isSaving = true
            repository.updateWishlistItem(wishlistId, cardId, updated)
                .onSuccess {
                    successMessage = AppLocale.wishlistCardUpdated
                    onResult(true)
                }
                .onFailure {
                    errorMessage = AppLocale.wishlistUpdateFailed
                    onResult(false)
                }
            isSaving = false
        }
    }

    fun updateBudget(wishlistId: String, budget: Double, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            isSaving = true
            repository.updateWishlistBudget(wishlistId, budget.coerceIn(0.0, WishlistLab.MAX_PRICE))
                .onSuccess {
                    successMessage = AppLocale.wishlistBudgetUpdated
                    onResult(true)
                }
                .onFailure {
                    errorMessage = AppLocale.wishlistUpdateFailed
                    onResult(false)
                }
            isSaving = false
        }
    }

    /**
     * "L'ho presa": la carta entra in collezione ed esce dalla lista.
     *
     * E' il passaggio che mancava del tutto — una wishlist senza uscita si
     * riempie e basta — ed e' anche il solo punto in cui le due parti dell'app
     * si parlano davvero.
     */
    fun markAsPurchased(
        wishlistId: String,
        card: TcgCard,
        keepInList: Boolean = false,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            isSaving = true
            val variants = CardOptions.getVariantsForCard(
                card.tcgplayer?.prices?.keys ?: emptySet(),
                card.rarity
            )
            val market = card.cardmarket?.prices
            val estimatedValue = market?.avg30
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
                variant = variants.firstOrNull() ?: "Holo",
                quantity = 1,
                condition = "Near Mint",
                language = "🇮🇹 Italiano"
            )

            repository.addCard(pokemonCard)
                .onSuccess {
                    if (!keepInList) repository.removeCardFromWishlist(wishlistId, card.id)
                    successMessage = AppLocale.wishlistCardPurchased
                    onResult(true)
                }
                .onFailure {
                    errorMessage = AppLocale.wishlistPurchaseFailed
                    onResult(false)
                }
            isSaving = false
        }
    }

    fun createWishlistAndAddCard(
        name: String,
        iconKey: String,
        cardId: String,
        isPremium: Boolean,
        budget: Double = 0.0,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (!canCreateWishlistCount(isPremium, wishlists.size)) {
            errorMessage = AppLocale.premiumWishlistLimitMessage
            onResult(false)
            return
        }

        val normalizedName = name.trim()
        if (!isValidWishlistName(normalizedName)) {
            errorMessage = if (AppLocale.isItalian) "Nome lista non valido" else "Invalid list name"
            onResult(false)
            return
        }

        viewModelScope.launch {
            isSaving = true
            val wishlist = Wishlist(
                name = normalizedName,
                iconKey = normalizeIconKey(iconKey),
                budget = budget.coerceIn(0.0, WishlistLab.MAX_PRICE)
            )

            repository.saveWishlist(wishlist)
                .onSuccess { wishlistId ->
                    repository.addCardToWishlist(wishlistId, cardId)
                        .onSuccess {
                            successMessage = if (AppLocale.isItalian) "Wishlist creata e carta aggiunta" else "Wishlist created and card added"
                            onResult(true)
                        }
                        .onFailure {
                            errorMessage = if (AppLocale.isItalian) "Wishlist creata, ma aggiunta carta fallita" else "Wishlist created, but card add failed"
                            onResult(false)
                        }
                }
                .onFailure {
                    errorMessage = if (AppLocale.isItalian) "Impossibile creare wishlist" else "Could not create wishlist"
                    onResult(false)
                }

            isSaving = false
        }
    }

    fun createWishlist(
        name: String,
        iconKey: String,
        isPremium: Boolean,
        budget: Double = 0.0,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (!canCreateWishlistCount(isPremium, wishlists.size)) {
            errorMessage = AppLocale.premiumWishlistLimitMessage
            onResult(false)
            return
        }

        val normalizedName = name.trim()
        if (!isValidWishlistName(normalizedName)) {
            errorMessage = if (AppLocale.isItalian) "Nome lista non valido" else "Invalid list name"
            onResult(false)
            return
        }

        viewModelScope.launch {
            isSaving = true
            val wishlist = Wishlist(
                name = normalizedName,
                iconKey = normalizeIconKey(iconKey),
                budget = budget.coerceIn(0.0, WishlistLab.MAX_PRICE)
            )
            repository.saveWishlist(wishlist)
                .onSuccess {
                    successMessage = if (AppLocale.isItalian) "Wishlist creata" else "Wishlist created"
                    onResult(true)
                }
                .onFailure {
                    errorMessage = if (AppLocale.isItalian) "Impossibile creare wishlist" else "Could not create wishlist"
                    onResult(false)
                }
            isSaving = false
        }
    }

    fun updateWishlistDetails(
        wishlistId: String,
        name: String,
        iconKey: String,
        budget: Double = 0.0,
        onResult: (Boolean) -> Unit = {}
    ) {
        val normalizedName = name.trim()
        if (!isValidWishlistName(normalizedName)) {
            errorMessage = if (AppLocale.isItalian) "Nome lista non valido" else "Invalid list name"
            onResult(false)
            return
        }

        val existing = getWishlistById(wishlistId)
        if (existing == null) {
            errorMessage = AppLocale.wishlistNotFound
            onResult(false)
            return
        }

        viewModelScope.launch {
            isSaving = true
            val updated = existing.copy(
                name = normalizedName,
                iconKey = normalizeIconKey(iconKey),
                budget = budget.coerceIn(0.0, WishlistLab.MAX_PRICE)
            )
            repository.saveWishlist(updated)
                .onSuccess {
                    successMessage = AppLocale.wishlistUpdated
                    onResult(true)
                }
                .onFailure {
                    errorMessage = AppLocale.wishlistUpdateFailed
                    onResult(false)
                }
            isSaving = false
        }
    }

    fun deleteWishlist(wishlistId: String) {
        viewModelScope.launch {
            repository.deleteWishlist(wishlistId)
                .onFailure {
                    errorMessage = if (AppLocale.isItalian) "Impossibile eliminare la wishlist" else "Could not delete wishlist"
                }
        }
    }

    fun clearMessages() {
        errorMessage = null
        successMessage = null
    }
}
