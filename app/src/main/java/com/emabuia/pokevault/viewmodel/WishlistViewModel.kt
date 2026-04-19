package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.data.model.WishlistIcons
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.util.AppLocale
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class WishlistViewModel : ViewModel() {

    companion object {
        const val FREE_WISHLIST_LIMIT = 1

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

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var successMessage by mutableStateOf<String?>(null)
        private set

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

    fun isCardWishlisted(cardId: String): Boolean {
        return wishlists.any { cardId in it.cardIds }
    }

    fun canCreateWishlist(isPremium: Boolean): Boolean {
        return canCreateWishlistCount(isPremium, wishlists.size)
    }

    fun getWishlistById(wishlistId: String): Wishlist? {
        return wishlists.firstOrNull { it.id == wishlistId }
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

        val cards = coroutineScope {
            wishlist.cardIds.map { cardId ->
                async {
                    tcgRepository.getCard(cardId).getOrNull()
                }
            }.awaitAll().filterNotNull()
        }

        return cards.sortedBy { it.number.replace(Regex("[^0-9]"), "").toIntOrNull() ?: Int.MAX_VALUE }
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

    fun createWishlistAndAddCard(
        name: String,
        iconKey: String,
        cardId: String,
        onResult: (Boolean) -> Unit = {}
    ) {
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
                iconKey = normalizeIconKey(iconKey)
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

    fun createWishlist(name: String, iconKey: String, onResult: (Boolean) -> Unit = {}) {
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
                iconKey = normalizeIconKey(iconKey)
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
