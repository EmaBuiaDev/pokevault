package com.emabuia.pokevault.data.billing

import com.emabuia.pokevault.viewmodel.WishlistViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Copertura dei limiti free.
 *
 * Prima non esisteva alcun test su PremiumManager: le funzioni di gate
 * leggevano _isPremium.value direttamente, quindi non erano verificabili senza
 * un BillingClient e un Context. L'unico test adiacente
 * (WishlistValidationTest) verificava una COPIA duplicata del limite dentro
 * WishlistViewModel, non quella che governa davvero i gate.
 */
class PremiumLimitsTest {

    @Test
    fun `un utente free e' entro il limite finche' non lo raggiunge`() {
        assertTrue(PremiumManager.isWithinFreeLimit(isPremium = false, currentCount = 0, freeLimit = 1))
        assertFalse(PremiumManager.isWithinFreeLimit(isPremium = false, currentCount = 1, freeLimit = 1))
        assertFalse(PremiumManager.isWithinFreeLimit(isPremium = false, currentCount = 5, freeLimit = 1))
    }

    @Test
    fun `un utente premium non ha limiti`() {
        assertTrue(PremiumManager.isWithinFreeLimit(isPremium = true, currentCount = 0, freeLimit = 1))
        assertTrue(PremiumManager.isWithinFreeLimit(isPremium = true, currentCount = 999, freeLimit = 1))
    }

    @Test
    fun `un limite a zero blocca subito gli utenti free`() {
        assertFalse(PremiumManager.isWithinFreeLimit(isPremium = false, currentCount = 0, freeLimit = 0))
        assertTrue(PremiumManager.isWithinFreeLimit(isPremium = true, currentCount = 0, freeLimit = 0))
    }

    @Test
    fun `la quota meta deck si esaurisce alla decima visualizzazione`() {
        val limit = PremiumManager.FREE_META_DECK_VIEWS
        assertTrue(PremiumManager.isWithinFreeLimit(false, limit - 1, limit))
        assertFalse(PremiumManager.isWithinFreeLimit(false, limit, limit))
    }

    @Test
    fun `il limite wishlist non e' piu' duplicato`() {
        // Le due costanti divergevano senza che nulla se ne accorgesse: il test
        // esistente verificava quella di WishlistViewModel, i gate usavano
        // quella di PremiumManager.
        assertEquals(PremiumManager.FREE_WISHLIST_LIMIT, WishlistViewModel.FREE_WISHLIST_LIMIT)
    }

    @Test
    fun `WishlistViewModel applica la stessa regola dei gate`() {
        val limit = PremiumManager.FREE_WISHLIST_LIMIT
        assertEquals(
            PremiumManager.isWithinFreeLimit(false, limit, limit),
            WishlistViewModel.canCreateWishlistCount(isPremium = false, currentCount = limit)
        )
        assertEquals(
            PremiumManager.isWithinFreeLimit(false, limit - 1, limit),
            WishlistViewModel.canCreateWishlistCount(isPremium = false, currentCount = limit - 1)
        )
    }
}
