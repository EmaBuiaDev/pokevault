package com.emabuia.pokevault.viewmodel

import com.emabuia.pokevault.data.model.WishlistAccents
import com.emabuia.pokevault.data.model.WishlistDraft
import com.emabuia.pokevault.data.model.WishlistIcons
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
        assertEquals(WishlistIcons.POKE_BALL, WishlistViewModel.normalizeIconKey("unknown"))
    }

    /**
     * Le liste create prima del nuovo catalogo non devono diventare tutte
     * uguali: ogni vecchia chiave finisce su un'icona diversa, e si porta
     * dietro il colore che aveva.
     */
    @Test
    fun `legacy icon keys keep telling lists apart`() {
        val migrated = listOf("pokeball", "master_ball", "pikachu", "charizard", "eevee")
            .map { WishlistIcons.normalize(it) }

        assertEquals(migrated.size, migrated.toSet().size)
        assertTrue(migrated.all { it in WishlistIcons.all })
        assertEquals(WishlistIcons.MASTER_BALL, WishlistIcons.normalize("master_ball"))
    }

    @Test
    fun `legacy icon keys keep their colour`() {
        assertEquals(WishlistAccents.GOLD, WishlistIcons.defaultAccentFor("pikachu"))
        assertEquals(WishlistAccents.ORANGE, WishlistIcons.defaultAccentFor("charizard"))
        assertEquals(WishlistAccents.PURPLE, WishlistIcons.defaultAccentFor("master_ball"))
    }

    @Test
    fun `an accent outside the palette falls back to the one of the icon`() {
        assertEquals(
            WishlistAccents.GREEN,
            WishlistAccents.normalize("fucsia", WishlistIcons.BUDGET)
        )
        assertEquals(
            WishlistAccents.BLUE,
            WishlistAccents.normalize(WishlistAccents.BLUE, WishlistIcons.BUDGET)
        )
    }

    @Test
    fun `free user can create only one wishlist`() {
        assertTrue(WishlistViewModel.canCreateWishlistCount(isPremium = false, currentCount = 0))
        assertFalse(WishlistViewModel.canCreateWishlistCount(isPremium = false, currentCount = 1))
        assertTrue(WishlistViewModel.canCreateWishlistCount(isPremium = true, currentCount = 50))
    }

    /** Il budget si scrive come viene: con la virgola, col punto, o non si scrive. */
    @Test
    fun `budget accepts both separators and refuses nonsense`() {
        assertEquals(12.5, WishlistViewModel.parseBudget("12,50"), 0.001)
        assertEquals(12.5, WishlistViewModel.parseBudget(" 12.50 "), 0.001)
        assertEquals(30.0, WishlistViewModel.parseBudget("€30"), 0.001)
        assertEquals(0.0, WishlistViewModel.parseBudget(""), 0.001)
        assertEquals(0.0, WishlistViewModel.parseBudget("tanti"), 0.001)
    }

    @Test
    fun `a draft is normalized before it is saved`() {
        val normalized = WishlistViewModel.normalizeDraft(
            WishlistDraft(
                name = "  Regali  ",
                iconKey = "eevee",
                accentKey = "",
                budgetEur = -5.0
            )
        )

        assertEquals("Regali", normalized.name)
        assertEquals(WishlistIcons.GIFT, normalized.iconKey)
        assertEquals(WishlistAccents.BLUE, normalized.accentKey)
        assertEquals(0.0, normalized.budgetEur, 0.001)
    }
}
