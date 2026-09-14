package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.data.model.WishlistAccents
import com.emabuia.pokevault.data.model.WishlistIcons
import com.emabuia.pokevault.data.remote.CardImages
import com.emabuia.pokevault.data.remote.CardMarket
import com.emabuia.pokevault.data.remote.CardMarketPrices
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgCardSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I conti della Wishlist.
 *
 * Due cose in particolare sono facili da sbagliare senza accorgersene: contare
 * due volte una carta che sta in due liste, e sommare come zero le carte di cui
 * non si conosce il prezzo facendo sembrare una lista piu' economica di quanto
 * sia.
 */
class WishlistLabTest {

    private fun card(
        id: String,
        number: String = "1",
        name: String = id,
        setName: String = "Base",
        low: Double? = null,
        image: String = "https://img/$id.png"
    ) = TcgCard(
        id = id,
        name = name,
        number = number,
        set = TcgCardSet(id = setName.lowercase(), name = setName),
        images = CardImages(small = image),
        cardmarket = low?.let { CardMarket(prices = CardMarketPrices(lowPrice = it)) }
    )

    private fun wishlist(
        id: String,
        name: String = id,
        cardIds: List<String> = emptyList(),
        budget: Double = 0.0,
        iconKey: String = WishlistIcons.POKE_BALL,
        accentKey: String = ""
    ) = Wishlist(
        id = id,
        name = name,
        iconKey = iconKey,
        accentKey = accentKey,
        budgetEur = budget,
        cardIds = cardIds,
        createdAt = null
    )

    private fun row(
        id: String,
        name: String = id,
        total: Int = 0,
        owned: Int = 0,
        cost: Double = 0.0,
        budget: Double = 0.0,
        createdAt: Long = 0L
    ) = WishlistRow(
        id = id,
        name = name,
        iconKey = WishlistIcons.POKE_BALL,
        accentKey = WishlistAccents.RED,
        budgetEur = budget,
        total = total,
        owned = owned,
        cost = cost,
        pricedMissing = total - owned,
        previewUrls = emptyList(),
        createdAtSeconds = createdAt,
        isResolved = true
    )

    // ── Collezione ────────────────────────────────────────────────────────

    @Test
    fun `owned ids ignore cards without an api id`() {
        val owned = listOf(
            PokemonCard(id = "1", apiCardId = "base1-4"),
            PokemonCard(id = "2", apiCardId = "  "),
            PokemonCard(id = "3", apiCardId = " base1-5 ")
        )
        assertEquals(setOf("base1-4", "base1-5"), WishlistLab.ownedCardIds(owned))
    }

    // ── Riga ──────────────────────────────────────────────────────────────

    @Test
    fun `a row counts what is already in the collection`() {
        val cards = mapOf(
            "a" to card("a", low = 10.0),
            "b" to card("b", low = 5.0)
        )
        val result = WishlistLab.row(wishlist("w", cardIds = listOf("a", "b")), cards, setOf("a"))

        assertEquals(2, result.total)
        assertEquals(1, result.owned)
        assertEquals(1, result.missing)
        // Solo la mancante entra nel costo: quello che hai gia' comprato non e'
        // una spesa da fare.
        assertEquals(5.0, result.cost, 0.001)
    }

    @Test
    fun `a row is not resolved while its cards are still loading`() {
        val partial = mapOf("a" to card("a", low = 1.0))
        val result = WishlistLab.row(wishlist("w", cardIds = listOf("a", "b")), partial, emptySet())

        assertFalse(result.isResolved)
        assertEquals(2, result.total)
    }

    @Test
    fun `cards without a price do not lower the total silently`() {
        val cards = mapOf(
            "a" to card("a", low = 12.0),
            "b" to card("b")
        )
        val result = WishlistLab.row(wishlist("w", cardIds = listOf("a", "b")), cards, emptySet())

        assertEquals(12.0, result.cost, 0.001)
        assertEquals(1, result.pricedMissing)
        assertEquals(1, result.unpricedMissing)
    }

    @Test
    fun `a row keeps the first three covers`() {
        val ids = listOf("a", "b", "c", "d")
        val cards = ids.associateWith { card(it) }
        val result = WishlistLab.row(wishlist("w", cardIds = ids), cards, emptySet())

        assertEquals(listOf("https://img/a.png", "https://img/b.png", "https://img/c.png"), result.previewUrls)
    }

    // ── Riassunto ─────────────────────────────────────────────────────────

    @Test
    fun `the summary counts a card in two lists only once`() {
        val cards = mapOf("a" to card("a", low = 20.0), "b" to card("b", low = 5.0))
        val lists = listOf(
            wishlist("w1", cardIds = listOf("a", "b")),
            wishlist("w2", cardIds = listOf("a"))
        )

        val summary = WishlistLab.summary(lists, cards, emptySet())

        assertEquals(2, summary.lists)
        assertEquals(2, summary.cards)
        assertEquals(25.0, summary.cost, 0.001)
    }

    @Test
    fun `the summary knows what is already owned`() {
        val cards = mapOf("a" to card("a", low = 20.0), "b" to card("b", low = 5.0))
        val lists = listOf(wishlist("w1", cardIds = listOf("a", "b")))

        val summary = WishlistLab.summary(lists, cards, setOf("a"))

        assertEquals(1, summary.owned)
        assertEquals(1, summary.missing)
        assertEquals(5.0, summary.cost, 0.001)
    }

    @Test
    fun `the summary counts the lists over budget`() {
        val cards = mapOf("a" to card("a", low = 50.0))
        val lists = listOf(
            wishlist("w1", cardIds = listOf("a"), budget = 10.0),
            wishlist("w2", cardIds = listOf("a"), budget = 100.0)
        )

        assertEquals(1, WishlistLab.summary(lists, cards, emptySet()).listsOverBudget)
    }

    // ── Ordinamenti ───────────────────────────────────────────────────────

    @Test
    fun `completed lists sink to the bottom`() {
        val rows = listOf(
            row("done", total = 3, owned = 3),
            row("open", total = 4, owned = 1)
        )
        val sorted = WishlistLab.sortWishlists(rows, WishlistSort.CLOSEST)

        assertEquals("open", sorted.first().id)
        assertEquals("done", sorted.last().id)
    }

    @Test
    fun `an empty list is not the closest to be finished`() {
        val rows = listOf(
            row("empty", total = 0, owned = 0),
            row("open", total = 4, owned = 3)
        )
        assertEquals("open", WishlistLab.sortWishlists(rows, WishlistSort.CLOSEST).first().id)
    }

    @Test
    fun `sorting by cost puts the most expensive list first`() {
        val rows = listOf(row("cheap", cost = 3.0), row("dear", cost = 120.0))
        assertEquals("dear", WishlistLab.sortWishlists(rows, WishlistSort.COST).first().id)
    }

    @Test
    fun `filtering a list by name ignores case and spaces`() {
        val rows = listOf(row("1", name = "Chase Kanto"), row("2", name = "Regali"))
        assertEquals(1, WishlistLab.filterWishlists(rows, "  kanto ").size)
    }

    // ── Carte ─────────────────────────────────────────────────────────────

    @Test
    fun `the missing filter hides what is already in the collection`() {
        val cards = listOf(card("a"), card("b"))
        val missing = WishlistLab.filterCards(cards, "", WishlistCardFilter.MISSING, setOf("a"))
        val owned = WishlistLab.filterCards(cards, "", WishlistCardFilter.OWNED, setOf("a"))

        assertEquals(listOf("b"), missing.map { it.id })
        assertEquals(listOf("a"), owned.map { it.id })
    }

    @Test
    fun `cards can be searched by set name`() {
        val cards = listOf(card("a", setName = "Evolving Skies"), card("b", setName = "Base"))
        val found = WishlistLab.filterCards(cards, "evolving", WishlistCardFilter.ALL, emptySet())

        assertEquals(listOf("a"), found.map { it.id })
    }

    @Test
    fun `sorting by set keeps each expansion together and in card order`() {
        val cards = listOf(
            card("b10", number = "10", setName = "Base"),
            card("e2", number = "2", setName = "Evolving Skies"),
            card("b2", number = "2", setName = "Base")
        )
        val sorted = WishlistLab.sortCards(cards, WishlistCardSort.SET)

        assertEquals(listOf("b2", "b10", "e2"), sorted.map { it.id })
    }

    // ── Budget ────────────────────────────────────────────────────────────

    @Test
    fun `budget percent can go past one hundred`() {
        assertEquals(140f, WishlistLab.budgetPercent(70.0, 50.0), 0.001f)
        assertEquals(0f, WishlistLab.budgetPercent(70.0, 0.0), 0.001f)
    }

    @Test
    fun `what fits in the budget starts from the cheapest`() {
        val missing = listOf(
            card("a", low = 20.0),
            card("b", low = 5.0),
            card("c", low = 8.0),
            card("noprice")
        )
        val affordable = WishlistLab.affordableWithin(missing, 15.0)

        assertEquals(listOf("b", "c"), affordable.map { it.id })
    }

    @Test
    fun `without a budget nothing is affordable by definition`() {
        assertTrue(WishlistLab.affordableWithin(listOf(card("a", low = 1.0)), 0.0).isEmpty())
    }
}
