package com.emabuia.pokevault.ui.pokedex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.wishlist.WishlistEditorDialog
import com.emabuia.pokevault.ui.wishlist.WishlistPickerDialog
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.WishlistViewModel

/**
 * Il cuore di una tessera del catalogo: quale dialogo e' aperto, e per che
 * carta. Stessa logica del dettaglio espansione (SetDetailScreen): senza
 * wishlist si crea la prima, altrimenti si sceglie in quali mettere la carta.
 */
@Stable
class CatalogWishlistState {
    var pickerCard by mutableStateOf<TcgCard?>(null)
    var createCard by mutableStateOf<TcgCard?>(null)
    var showPremium by mutableStateOf(false)

    fun open(card: TcgCard, wishlistViewModel: WishlistViewModel) {
        if (wishlistViewModel.wishlists.isEmpty()) createCard = card else pickerCard = card
    }
}

@Composable
fun rememberCatalogWishlistState(): CatalogWishlistState = remember { CatalogWishlistState() }

@Composable
fun CatalogWishlistDialogs(
    state: CatalogWishlistState,
    wishlistViewModel: WishlistViewModel,
    onPremiumRequired: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val premiumManager = remember { PremiumManager.getInstance() }
    val isPremium by premiumManager.isPremium.collectAsStateWithLifecycle()

    state.pickerCard?.let { card ->
        // Da isPremium e non direttamente: canCreateWishlist() legge un valore
        // che Compose non osserva, e all'attivazione del Premium non si ricomporrebbe.
        val canCreateWishlist = remember(isPremium, wishlistViewModel.wishlists.size) {
            premiumManager.canCreateWishlist(wishlistViewModel.wishlists.size)
        }
        WishlistPickerDialog(
            wishlists = wishlistViewModel.wishlists,
            selectedWishlistIds = wishlistViewModel.getWishlistIdsForCard(card.id),
            canCreateNew = canCreateWishlist,
            onDismiss = { state.pickerCard = null },
            onCreateNewRequested = {
                if (canCreateWishlist) {
                    state.createCard = card
                    state.pickerCard = null
                } else {
                    state.showPremium = true
                }
            },
            onConfirmSelection = { selectedIds ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                wishlistViewModel.updateCardWishlists(card.id, selectedIds)
                state.pickerCard = null
            }
        )
    }

    state.createCard?.let { card ->
        WishlistEditorDialog(
            onDismiss = { state.createCard = null },
            onConfirm = { draft ->
                wishlistViewModel.createWishlistAndAddCard(draft, card.id, isPremium) { success ->
                    if (success) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.createCard = null
                    }
                }
            },
            isSaving = wishlistViewModel.isSaving
        )
    }

    if (state.showPremium) {
        PremiumRequiredDialog(
            title = AppLocale.premiumWishlistLimitTitle,
            message = AppLocale.premiumWishlistLimitMessage,
            onDismiss = { state.showPremium = false },
            onUpgrade = {
                state.showPremium = false
                onPremiumRequired()
            }
        )
    }
}
