package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.data.model.WishlistItem
import com.emabuia.pokevault.data.model.WishlistPriority
import com.emabuia.pokevault.data.remote.CardMarket
import com.emabuia.pokevault.data.remote.CardMarketPrices
import com.emabuia.pokevault.data.remote.CardImages
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgCardSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I conti della Wishlist.
 *
 * Sono le cose che l'utente legge come fossero certe — quanto manca, quanto
 * costa, cosa e' un'occasione — e prima nessuna di queste esisteva in un posto
 * verificabile.
 */
class WishlistLabTest {

    private fun card(
        id: String,
        name: String = "Card $id",
        number: String = "1",
        price: Double? = null,
        setName: String = "Scarlatto e Violetto"
    ) = TcgCard(
        id = id,
        name = name,
        number = number,
        images = CardImages(),
        set = TcgCardSet(id = "sv1", name = setName, series = "Scarlet & Violet"),
        cardmarket = price?.let { CardMarket(prices = CardMarketPrices(lowPrice = it)) }
    )

    private fun wishlist(
        cardIds: List<String>,
        items: Map<String, WishlistItem> = emptyMap(),
        budget: Double = 0.0
    ) = Wishlist(id = "w1", name = "Chase Kanto", cardIds = cardIds, items = items, budget = budget)

    private fun item(
        priority: WishlistPriority = WishlistPriority.MEDIUM,
        target: Double = 0.0,
        note: String = ""
    ) = WishlistItem(priority = priority.key, targetPrice = target, note = note)

    // ── Modello ───────────────────────────────────────────────────────────

    @Test
    fun `unknown priority key falls back to medium`() {
        assertEquals(WishlistPriority.MEDIUM, WishlistPriority.fromKey("urgentissima"))
        assertEquals(WishlistPriority.MEDIUM, WishlistPriority.fromKey(null))
        assertEquals(WishlistPriority.HIGH, WishlistPriority.fromKey("HIGH"))
    }

    @Test
    fun `card without metadata keeps the default item`() {
        val list = wishlist(listOf("a"))
        assertEquals(WishlistPriority.MEDIUM, list.priorityOf("a"))
        assertFalse(list.itemFor("a").hasTarget)
    }

    // ── Elenco delle liste ────────────────────────────────────────────────

    @Test
    fun `row counts owned cards against the collection`() {
        val list = wishlist(listOf("a", "b", "c"))
        val row = WishlistLab.rows(listOf(list), setOf("b")).first()

        assertEquals(3, row.total)
        assertEquals(1, row.owned)
        assertEquals(2, row.missing)
        assertFalse(row.isComplete)
    }

    @Test
    fun `owned cards do not count as high priority anymore`() {
        val list = wishlist(
            cardIds = listOf("a", "b"),
            items = mapOf(
                "a" to item(WishlistPriority.HIGH),
                "b" to item(WishlistPriority.HIGH)
            )
        )
        val row = WishlistLab.rows(listOf(list), setOf("a")).first()

        assertEquals(1, row.highPriority)
    }

    @Test
    fun `progress sort pushes finished lists to the bottom`() {
        val done = WishlistRow("done", "Finita", "pokeball", 2, 2, 0, 0.0, 10)
        val half = WishlistRow("half", "Meta", "pokeball", 4, 2, 0, 0.0, 20)
        val start = WishlistRow("start", "Appena nata", "pokeball", 4, 0, 0, 0.0, 30)

        val sorted = WishlistLab.sortRows(listOf(done, half, start), WishlistListSort.PROGRESS)

        assertEquals(listOf("half", "start", "done"), sorted.map { it.id })
    }

    @Test
    fun `overview sums every list`() {
        val lists = listOf(
            wishlist(listOf("a", "b")).copy(id = "w1"),
            wishlist(listOf("c")).copy(id = "w2")
        )
        val overview = WishlistLab.overview(WishlistLab.rows(lists, setOf("a")))

        assertEquals(2, overview.lists)
        assertEquals(3, overview.cards)
        assertEquals(1, overview.owned)
        assertEquals(2, overview.missing)
    }

    // ── Carte di una lista ────────────────────────────────────────────────

    @Test
    fun `card row carries metadata price and position`() {
        val list = wishlist(
            cardIds = listOf("a", "b"),
            items = mapOf("b" to item(WishlistPriority.HIGH, target = 10.0, note = "solo NM"))
        )
        val cards = listOf(card("a", price = 3.0), card("b", price = 8.0))

        val rows = WishlistLab.cardRows(list, cards, setOf("a"))
        val rowA = rows.first { it.cardId == "a" }
        val rowB = rows.first { it.cardId == "b" }

        assertEquals(0, rowA.position)
        assertTrue(rowA.isOwned)
        assertEquals(3.0, rowA.price, 0.001)
        assertEquals(WishlistPriority.MEDIUM, rowA.priority)

        assertEquals(1, rowB.position)
        assertFalse(rowB.isOwned)
        assertEquals("solo NM", rowB.item.note)
    }

    @Test
    fun `a card is a deal only under an explicit target`() {
        val list = wishlist(
            cardIds = listOf("a", "b", "c"),
            items = mapOf(
                "a" to item(target = 10.0),
                "b" to item(target = 5.0)
            )
        )
        val cards = listOf(card("a", price = 8.0), card("b", price = 9.0), card("c", price = 1.0))
        val rows = WishlistLab.cardRows(list, cards, emptySet()).associateBy { it.cardId }

        assertTrue(rows.getValue("a").isDeal)
        assertFalse(rows.getValue("b").isDeal)
        assertEquals(4.0, rows.getValue("b").overTargetBy, 0.001)
        // Senza tetto non c'e' occasione, per quanto la carta costi poco.
        assertFalse(rows.getValue("c").isDeal)
        assertEquals(0.0, rows.getValue("c").overTargetBy, 0.001)
    }

    @Test
    fun `a card without price is never a deal`() {
        val list = wishlist(listOf("a"), items = mapOf("a" to item(target = 10.0)))
        val row = WishlistLab.cardRows(list, listOf(card("a")), emptySet()).first()

        assertFalse(row.hasPrice)
        assertFalse(row.isDeal)
    }

    @Test
    fun `priority sort puts wanted first and owned last`() {
        val list = wishlist(
            cardIds = listOf("low", "high", "owned", "medium"),
            items = mapOf(
                "low" to item(WishlistPriority.LOW),
                "high" to item(WishlistPriority.HIGH),
                "owned" to item(WishlistPriority.HIGH),
                "medium" to item(WishlistPriority.MEDIUM)
            )
        )
        val cards = listOf(
            card("low", price = 1.0),
            card("high", price = 2.0),
            card("owned", price = 3.0),
            card("medium", price = 4.0)
        )

        val sorted = WishlistLab.sortCards(
            WishlistLab.cardRows(list, cards, setOf("owned")),
            WishlistCardSort.PRIORITY
        )

        assertEquals(listOf("high", "medium", "low", "owned"), sorted.map { it.cardId })
    }

    @Test
    fun `ascending price keeps unpriced cards at the bottom`() {
        val list = wishlist(listOf("a", "b", "c"))
        val cards = listOf(card("a", price = 12.0), card("b"), card("c", price = 4.0))

        val sorted = WishlistLab.sortCards(
            WishlistLab.cardRows(list, cards, emptySet()),
            WishlistCardSort.PRICE_ASC
        )

        assertEquals(listOf("c", "a", "b"), sorted.map { it.cardId })
    }

    @Test
    fun `recent sort follows insertion order backwards`() {
        val list = wishlist(listOf("first", "second", "third"))
        val cards = listOf(card("first"), card("second"), card("third"))

        val sorted = WishlistLab.sortCards(
            WishlistLab.cardRows(list, cards, emptySet()),
            WishlistCardSort.RECENT
        )

        assertEquals(listOf("third", "second", "first"), sorted.map { it.cardId })
    }

    @Test
    fun `filters select what the chips promise`() {
        val list = wishlist(
            cardIds = listOf("high", "deal", "owned", "plain"),
            items = mapOf(
                "high" to item(WishlistPriority.HIGH),
                "deal" to item(target = 10.0)
            )
        )
        val cards = listOf(
            card("high", price = 20.0),
            card("deal", price = 6.0),
            card("owned", price = 1.0),
            card("plain", price = 2.0)
        )
        val rows = WishlistLab.cardRows(list, cards, setOf("owned"))

        fun ids(filter: WishlistCardFilter) =
            WishlistLab.filterCards(rows, "", filter).map { it.cardId }.sorted()

        assertEquals(listOf("high"), ids(WishlistCardFilter.HIGH))
        assertEquals(listOf("deal"), ids(WishlistCardFilter.DEALS))
        assertEquals(listOf("owned"), ids(WishlistCardFilter.OWNED))
        assertEquals(listOf("deal", "high", "plain"), ids(WishlistCardFilter.MISSING))
        assertEquals(4, WishlistLab.filterCards(rows, "", WishlistCardFilter.ALL).size)
    }

    @Test
    fun `search looks into the note too`() {
        val list = wishlist(
            cardIds = listOf("a", "b"),
            items = mapOf("a" to item(note = "la vuole Marco"))
        )
        val cards = listOf(card("a", name = "Pikachu"), card("b", name = "Eevee"))
        val rows = WishlistLab.cardRows(list, cards, emptySet())

        assertEquals(listOf("a"), WishlistLab.filterCards(rows, "marco", WishlistCardFilter.ALL).map { it.cardId })
        assertEquals(listOf("b"), WishlistLab.filterCards(rows, "eevee", WishlistCardFilter.ALL).map { it.cardId })
    }

    // ── Conti e budget ────────────────────────────────────────────────────

    @Test
    fun `cost counts only the cards still missing`() {
        val list = wishlist(listOf("a", "b", "c"))
        val cards = listOf(card("a", price = 10.0), card("b", price = 5.0), card("c"))
        val stats = WishlistLab.stats(WishlistLab.cardRows(list, cards, setOf("a")), budget = 0.0)

        assertEquals(3, stats.cards)
        assertEquals(1, stats.owned)
        assertEquals(2, stats.missing)
        assertEquals(5.0, stats.cost, 0.001)
        assertEquals(15.0, stats.value, 0.001)
        // "c" non ha prezzo: il totale e' una stima al ribasso e lo dichiara.
        assertEquals(1, stats.pricedMissing)
    }

    @Test
    fun `budget says how much is left and when it is blown`() {
        val list = wishlist(listOf("a", "b"), budget = 20.0)
        val cards = listOf(card("a", price = 8.0), card("b", price = 4.0))
        val stats = WishlistLab.stats(WishlistLab.cardRows(list, cards, emptySet()), budget = 20.0)

        assertTrue(stats.hasBudget)
        assertFalse(stats.isOverBudget)
        assertEquals(8.0, stats.budgetLeft, 0.001)
        assertEquals(60f, stats.budgetPercent, 0.01f)

        val tight = WishlistLab.stats(WishlistLab.cardRows(list, cards, emptySet()), budget = 10.0)
        assertTrue(tight.isOverBudget)
        assertEquals(-2.0, tight.budgetLeft, 0.001)
        // Oltre il tetto la percentuale supera il 100: lo sforamento si vede.
        assertEquals(120f, tight.budgetPercent, 0.01f)
    }

    @Test
    fun `no budget means no budget math`() {
        val stats = WishlistLab.stats(emptyList(), budget = 0.0)
        assertFalse(stats.hasBudget)
        assertFalse(stats.isOverBudget)
        assertEquals(0f, stats.budgetPercent, 0.001f)
    }

    @Test
    fun `next pick prefers a deal over a cheaper card without target`() {
        val list = wishlist(
            cardIds = listOf("deal", "cheap"),
            items = mapOf("deal" to item(WishlistPriority.MEDIUM, target = 30.0))
        )
        val cards = listOf(card("deal", price = 25.0), card("cheap", price = 2.0))

        val pick = WishlistLab.nextPick(WishlistLab.cardRows(list, cards, emptySet()))

        assertEquals("deal", pick?.cardId)
    }

    @Test
    fun `without deals next pick is the cheapest of the most wanted`() {
        val list = wishlist(
            cardIds = listOf("highExpensive", "highCheap", "lowCheap"),
            items = mapOf(
                "highExpensive" to item(WishlistPriority.HIGH),
                "highCheap" to item(WishlistPriority.HIGH),
                "lowCheap" to item(WishlistPriority.LOW)
            )
        )
        val cards = listOf(
            card("highExpensive", price = 40.0),
            card("highCheap", price = 12.0),
            card("lowCheap", price = 1.0)
        )

        val pick = WishlistLab.nextPick(WishlistLab.cardRows(list, cards, emptySet()))

        assertEquals("highCheap", pick?.cardId)
    }

    @Test
    fun `next pick stays empty when nothing has a price`() {
        val list = wishlist(listOf("a"))
        assertNull(WishlistLab.nextPick(WishlistLab.cardRows(list, listOf(card("a")), emptySet())))
    }

    @Test
    fun `owned cards are never the next pick`() {
        val list = wishlist(listOf("a"))
        val rows = WishlistLab.cardRows(list, listOf(card("a", price = 5.0)), setOf("a"))
        assertNull(WishlistLab.nextPick(rows))
    }

    // ── Esportazione e input ──────────────────────────────────────────────

    @Test
    fun `share text lists the cards and totals the missing ones`() {
        val list = wishlist(
            cardIds = listOf("a", "b"),
            items = mapOf("a" to item(WishlistPriority.HIGH))
        )
        val cards = listOf(
            card("a", name = "Charizard", number = "6", price = 30.0),
            card("b", name = "Pikachu", number = "25", price = 2.0)
        )
        val rows = WishlistLab.cardRows(list, cards, setOf("b"))

        val text = WishlistLab.shareText(
            listName = "Chase Kanto",
            rows = rows,
            totalLabel = "Totale stimato",
            ownedLabel = "Già presa",
            priorityLabel = { it.key }
        )

        assertTrue(text.startsWith("Chase Kanto"))
        assertTrue(text.contains("Charizard #6"))
        assertTrue(text.contains("Già presa"))
        // Pikachu e' gia' in collezione: non entra nel totale.
        assertTrue(text.contains("Totale stimato: € 30,00"))
    }

    @Test
    fun `typed prices accept commas and refuse nonsense`() {
        assertEquals(12.5, WishlistLab.normalizePrice("12,50"), 0.001)
        assertEquals(12.5, WishlistLab.normalizePrice(" 12.50 € "), 0.001)
        assertEquals(0.0, WishlistLab.normalizePrice(""), 0.001)
        assertEquals(0.0, WishlistLab.normalizePrice("-3"), 0.001)
        assertEquals(0.0, WishlistLab.normalizePrice("tanto"), 0.001)
        assertEquals(WishlistLab.MAX_PRICE, WishlistLab.normalizePrice("9999999"), 0.001)
    }

    @Test
    fun `notes are trimmed and capped`() {
        assertEquals("solo NM", WishlistLab.normalizeNote("  solo NM  "))
        assertEquals(WishlistLab.MAX_NOTE_LENGTH, WishlistLab.normalizeNote("x".repeat(500)).length)
    }
}
