package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.data.model.WishlistAccents
import com.emabuia.pokevault.data.model.WishlistDraft
import com.emabuia.pokevault.data.model.WishlistIcons
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.WishlistLab
import com.emabuia.pokevault.util.WishlistRow
import com.emabuia.pokevault.util.WishlistSummary
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

        /**
         * Quante carte si chiedono insieme al catalogo.
         *
         * Una wishlist puo' pescare da venti espansioni diverse: lanciare una
         * `async` per ogni id vorrebbe dire aprire duecento richieste in un
         * colpo e far scattare il rate limit del Worker. A blocchi il tempo di
         * attesa e' quasi lo stesso e le richieste restano contate.
         */
        private const val CARD_FETCH_BATCH = 8

        fun isValidWishlistName(name: String): Boolean {
            val normalized = name.trim()
            return normalized.isNotEmpty() && normalized.length <= 40
        }

        fun normalizeIconKey(iconKey: String): String = WishlistIcons.normalize(iconKey)

        fun canCreateWishlistCount(isPremium: Boolean, currentCount: Int): Boolean {
            return isPremium || currentCount < FREE_WISHLIST_LIMIT
        }

        /** Il budget scritto a mano: accetta sia "12,50" sia "12.50", zero se vuoto. */
        fun parseBudget(raw: String): Double {
            val normalized = raw.trim().replace(',', '.').replace("€", "").trim()
            if (normalized.isEmpty()) return 0.0
            return normalized.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        }

        fun normalizeDraft(draft: WishlistDraft): WishlistDraft {
            // L'accento si risolve sulla chiave *originale*: cosi' una lista creata
            // col vecchio catalogo, modificata senza toccare il colore, tiene il
            // colore che aveva.
            return draft.copy(
                name = draft.name.trim(),
                iconKey = WishlistIcons.normalize(draft.iconKey),
                accentKey = WishlistAccents.normalize(draft.accentKey, draft.iconKey),
                budgetEur = draft.budgetEur.coerceAtLeast(0.0)
            )
        }
    }

    private val repository = FirestoreRepository()
    private val tcgRepository = RepositoryProvider.tcgRepository

    var wishlists by mutableStateOf<List<Wishlist>>(emptyList())
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
     * Le carte delle wishlist, man mano che arrivano dal catalogo.
     *
     * Senza questa cache la lista delle wishlist non poteva dire niente di piu'
     * di "12 carte": nessun prezzo, nessuna copertina, nessun modo di sapere
     * quali erano gia' in collezione. E' una mappa di stato perche' la riga si
     * deve completare da sola quando la carta arriva.
     */
    private val cardCache = mutableStateMapOf<String, TcgCard>()

    /** Gli id che il catalogo non sa risolvere: non si richiedono all'infinito. */
    private val unresolvedCardIds = mutableSetOf<String>()
    private val pendingCardIds = mutableSetOf<String>()

    var isLoadingCards by mutableStateOf(false)
        private set

    /** Gli id delle carte gia' in collezione, per sapere cosa e' gia' stato preso. */
    var ownedCardIds by mutableStateOf<Set<String>>(emptySet())
        private set

    val cardsById: Map<String, TcgCard> get() = cardCache

    init {
        loadWishlists()
        loadOwnedCards()
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
                    ensureCardsLoaded(list.flatMap { it.cardIds })
                }
        }
    }

    private fun loadOwnedCards() {
        viewModelScope.launch {
            repository.getCards()
                .catch { }
                .collectLatest { cards ->
                    ownedCardIds = WishlistLab.ownedCardIds(cards)
                }
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

    // ── Carte ─────────────────────────────────────────────────────────────

    /**
     * Chiede al catalogo le carte che non sono gia' in cache.
     *
     * Gli id gia' in arrivo e quelli che il catalogo ha gia' dichiarato
     * sconosciuti non si richiedono: la lista delle wishlist si ricompone a ogni
     * snapshot di Firestore, e senza questi due filtri ogni ricomposizione
     * rilancerebbe tutte le richieste da capo.
     */
    private fun ensureCardsLoaded(cardIds: List<String>) {
        val toLoad = cardIds.asSequence()
            .distinct()
            .filter { it.isNotBlank() && it !in cardCache && it !in pendingCardIds && it !in unresolvedCardIds }
            .toList()
        if (toLoad.isEmpty()) return

        pendingCardIds += toLoad
        isLoadingCards = true

        viewModelScope.launch {
            toLoad.chunked(CARD_FETCH_BATCH).forEach { batch ->
                val results = supervisorScope {
                    batch.map { id ->
                        async { id to tcgRepository.getCard(id).getOrNull() }
                    }.awaitAll()
                }
                results.forEach { (id, card) ->
                    if (card != null) cardCache[id] = card else unresolvedCardIds += id
                    pendingCardIds -= id
                }
            }
            isLoadingCards = pendingCardIds.isNotEmpty()
        }
    }

    /** Le carte di una lista, quelle gia' arrivate. */
    fun cardsOf(wishlistId: String): List<TcgCard> {
        val wishlist = getWishlistById(wishlistId) ?: return emptyList()
        return wishlist.cardIds.mapNotNull { cardCache[it] }
    }

    /** true quando di quella lista manca ancora qualche carta da caricare. */
    fun isLoadingCardsOf(wishlistId: String): Boolean {
        val wishlist = getWishlistById(wishlistId) ?: return false
        return wishlist.cardIds.any { it in pendingCardIds }
    }

    fun rows(): List<WishlistRow> = WishlistLab.rows(wishlists, cardCache, ownedCardIds)

    fun summary(): WishlistSummary = WishlistLab.summary(wishlists, cardCache, ownedCardIds)

    // ── Carte dentro le liste ─────────────────────────────────────────────

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

    /**
     * Toglie dalla lista le carte che nel frattempo sono entrate in collezione.
     *
     * E' il gesto che chiude il giro: una wishlist che non si svuota mai smette
     * di essere una lista della spesa e diventa un archivio di cose gia' fatte.
     */
    fun removeOwnedCards(wishlistId: String, onResult: (Boolean) -> Unit = {}) {
        val wishlist = getWishlistById(wishlistId)
        if (wishlist == null) {
            errorMessage = AppLocale.wishlistNotFound
            onResult(false)
            return
        }
        val owned = wishlist.cardIds.filter { it in ownedCardIds }
        if (owned.isEmpty()) {
            onResult(true)
            return
        }
        viewModelScope.launch {
            repository.removeCardsFromWishlist(wishlistId, owned)
                .onSuccess {
                    successMessage = AppLocale.wishlistCleanupDone(owned.size)
                    onResult(true)
                }
                .onFailure {
                    errorMessage = if (AppLocale.isItalian) "Impossibile rimuovere le carte" else "Could not remove the cards"
                    onResult(false)
                }
        }
    }

    fun addCardToWishlist(wishlistId: String, cardId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repository.addCardToWishlist(wishlistId, cardId)
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

    fun updateCardWishlists(cardId: String, targetWishlistIds: Set<String>, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val currentWishlistIds = getWishlistIdsForCard(cardId)
            val toAdd = targetWishlistIds - currentWishlistIds
            val toRemove = currentWishlistIds - targetWishlistIds

            if (toAdd.isEmpty() && toRemove.isEmpty()) {
                onResult(true)
                return@launch
            }

            val failures = supervisorScope {
                val addResults = toAdd.map { wishlistId ->
                    async { repository.addCardToWishlist(wishlistId, cardId).isFailure }
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

    // ── Liste ─────────────────────────────────────────────────────────────

    fun createWishlistAndAddCard(
        draft: WishlistDraft,
        cardId: String,
        isPremium: Boolean,
        onResult: (Boolean) -> Unit = {}
    ) {
        val normalized = validatedDraft(draft, isPremium) ?: run { onResult(false); return }

        viewModelScope.launch {
            isSaving = true
            repository.saveWishlist(normalized.toWishlist())
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

    fun createWishlist(draft: WishlistDraft, isPremium: Boolean, onResult: (Boolean) -> Unit = {}) {
        val normalized = validatedDraft(draft, isPremium) ?: run { onResult(false); return }

        viewModelScope.launch {
            isSaving = true
            repository.saveWishlist(normalized.toWishlist())
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
        draft: WishlistDraft,
        onResult: (Boolean) -> Unit = {}
    ) {
        val normalized = normalizeDraft(draft)
        if (!isValidWishlistName(normalized.name)) {
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
                name = normalized.name,
                iconKey = normalized.iconKey,
                accentKey = normalized.accentKey,
                budgetEur = normalized.budgetEur
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

    /** Normalizza e controlla limite free e nome; null se non si puo' procedere. */
    private fun validatedDraft(draft: WishlistDraft, isPremium: Boolean): WishlistDraft? {
        if (!canCreateWishlistCount(isPremium, wishlists.size)) {
            errorMessage = AppLocale.premiumWishlistLimitMessage
            return null
        }
        val normalized = normalizeDraft(draft)
        if (!isValidWishlistName(normalized.name)) {
            errorMessage = if (AppLocale.isItalian) "Nome lista non valido" else "Invalid list name"
            return null
        }
        return normalized
    }

    private fun WishlistDraft.toWishlist() = Wishlist(
        name = name,
        iconKey = iconKey,
        accentKey = accentKey,
        budgetEur = budgetEur
    )
}
