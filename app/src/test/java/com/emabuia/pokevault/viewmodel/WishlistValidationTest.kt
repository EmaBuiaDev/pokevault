package com.emabuia.pokevault.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WishlistValidationTest {

    @Test
    fun `blank name is invalid`() {
        assertFalse(WishlistViewModel.isValidWishlistName(""))
        assertFalse(WishlistViewModel.isValidWishlistName("   "))
    }

    @Test
    fun `name over forty chars is invalid`() {
        assertFalse(WishlistViewModel.isValidWishlistName("a".repeat(41)))
    }

    @Test
    fun `name in range is valid`() {
        assertTrue(WishlistViewModel.isValidWishlistName("Wishlist Fuoco"))
    }

    @Test
    fun `invalid icon key falls back`() {
        assertEquals("pokeball", WishlistViewModel.normalizeIconKey("unknown"))
    }

    @Test
    fun `free user can create only one wishlist`() {
        assertTrue(WishlistViewModel.canCreateWishlistCount(isPremium = false, currentCount = 0))
        assertFalse(WishlistViewModel.canCreateWishlistCount(isPremium = false, currentCount = 1))
        assertTrue(WishlistViewModel.canCreateWishlistCount(isPremium = true, currentCount = 50))
    }
}
