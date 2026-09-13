package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.remote.CardMarket
import com.emabuia.pokevault.data.remote.CardMarketPrices
import com.emabuia.pokevault.data.remote.TcgCard
import org.junit.Assert.*
import org.junit.Test

/**
 * Conti e ordinamenti del Collector Lab.
 *
 * Erano la parte piu' facile da sbagliare senza accorgersene: percentuali
 * calcolate dentro la UI, numeri di carta ordinati come stringhe, prezzi
 * mancanti sommati come zero.
 */
class CollectorLabTest {

    private fun album(
        id: String,
        name: String = id,
        used: Int = 0,
        size: Int = 9,
        value: Double = 0.0,
        createdAt: Long = 0L,
        description: String = "",
        type: String = ""
    ) = AlbumRow(
        id = id,
        name = name,
        description = description,
        theme = "classic",
        pokemonType = type,
        coverUrl = "",
        previewUrls = emptyList(),
        used = used,
        size = size,
        value = value,
        createdAtSeconds = createdAt
    )

    private fun chase(
        id: String,
        name: String = id,
        owned: Int = 0,
        total: Int = 10,
        createdAt: Long = 0L,
        criteria: String = "Set"
    ) = ChaseRow(
        id = id,
        name = name,
        criteriaLabel = criteria,
        owned = owned,
        total = total,
        createdAtSeconds = createdAt
    )

    private fun card(
        id: String,
        number: String = "1",
        name: String = id,
        low: Double? = null,
        avg: Double? = null
    ) = TcgCard(
        id = id,
        name = name,
        number = number,
        cardmarket = if (low == null && avg == null) null
        else CardMarket(prices = CardMarketPrices(lowPrice = low, averageSellPrice = avg))
    )

    // ── fillPercent ───────────────────────────────────────────────────────

    @Test
    fun `fillPercent is zero when there is nothing to fill`() {
        assertEquals(0f, CollectorLab.fillPercent(0, 0), 0.001f)
        assertEquals(0f, CollectorLab.fillPercent(5, 0), 0.001f)
    }

    @Test
    fun `fillPercent is a percentage`() {
        assertEquals(50f, CollectorLab.fillPercent(9, 18), 0.001f)
        assertEquals(100f, CollectorLab.fillPercent(9, 9), 0.001f)
    }

    @Test
    fun `fillPercent never exceeds one hundred`() {
        assertEquals(100f, CollectorLab.fillPercent(12, 9), 0.001f)
    }

    // ── Raccoglitore ──────────────────────────────────────────────────────

    @Test
    fun `binder pages round up`() {
        assertEquals(1, CollectorLab.binderPageCount(9))
        assertEquals(2, CollectorLab.binderPageCount(10))
        assertEquals(4, CollectorLab.binderPageCount(36))
        assertEquals(14, CollectorLab.binderPageCount(120))
    }

    @Test
    fun `binder pages of an empty album are none`() {
        assertEquals(0, CollectorLab.binderPageCount(0))
    }

    @Test
    fun `a binder page always has nine slots`() {
        val cards = listOf("a", "b", "c", "d")
        val page = CollectorLab.binderPage(0, cards)
        assertEquals(9, page.size)
        assertEquals("a", page[0])
        assertNull(page[4])
    }

    @Test
    fun `the second binder page starts from the tenth card`() {
        val cards = (1..12).map { "c$it" }
        val page = CollectorLab.binderPage(1, cards)
        assertEquals("c10", page[0])
        assertEquals("c12", page[2])
        assertNull(page[3])
    }

    @Test
    fun `a page past the end is all empty slots`() {
        val page = CollectorLab.binderPage(5, listOf("a"))
        assertEquals(9, page.size)
        assertTrue(page.all { it == null })
    }

    // ── Album ─────────────────────────────────────────────────────────────

    @Test
    fun `albums sort by name ignoring case`() {
        val rows = listOf(album("1", "zapdos"), album("2", "Articuno"))
        assertEquals(
            listOf("2", "1"),
            CollectorLab.sortAlbums(rows, AlbumSort.NAME).map { it.id }
        )
    }

    @Test
    fun `albums sort by fill percentage not by absolute count`() {
        val nearlyFull = album("full", used = 8, size = 9)
        val bigButEmpty = album("big", used = 20, size = 120)
        val sorted = CollectorLab.sortAlbums(listOf(bigButEmpty, nearlyFull), AlbumSort.FILL)
        assertEquals("full", sorted.first().id)
    }

    @Test
    fun `albums sort by value descending`() {
        val rows = listOf(album("cheap", value = 3.0), album("rich", value = 120.0))
        assertEquals("rich", CollectorLab.sortAlbums(rows, AlbumSort.VALUE).first().id)
    }

    @Test
    fun `albums sort by most recent first`() {
        val rows = listOf(album("old", createdAt = 100L), album("new", createdAt = 900L))
        assertEquals("new", CollectorLab.sortAlbums(rows, AlbumSort.RECENT).first().id)
    }

    @Test
    fun `album filter looks at name description and type`() {
        val rows = listOf(
            album("1", name = "Fuoco puro"),
            album("2", name = "Kanto", description = "solo starter"),
            album("3", name = "Misto", type = "Water")
        )
        assertEquals(listOf("1"), CollectorLab.filterAlbums(rows, "fuoco").map { it.id })
        assertEquals(listOf("2"), CollectorLab.filterAlbums(rows, "starter").map { it.id })
        assertEquals(listOf("3"), CollectorLab.filterAlbums(rows, "water").map { it.id })
    }

    @Test
    fun `an empty query keeps every album`() {
        val rows = listOf(album("1"), album("2"))
        assertEquals(2, CollectorLab.filterAlbums(rows, "   ").size)
    }

    // ── Chase ─────────────────────────────────────────────────────────────

    @Test
    fun `completed chases go last even if they are at one hundred percent`() {
        val done = chase("done", owned = 10, total = 10)
        val almost = chase("almost", owned = 9, total = 10)
        val started = chase("started", owned = 1, total = 10)
        val sorted = CollectorLab.sortChases(listOf(done, started, almost), ChaseSort.CLOSEST)
        assertEquals(listOf("almost", "started", "done"), sorted.map { it.id })
    }

    @Test
    fun `the spotlight skips completed and empty chases`() {
        val done = chase("done", owned = 10, total = 10)
        val empty = chase("empty", owned = 0, total = 0)
        val open = chase("open", owned = 4, total = 10)
        assertEquals("open", CollectorLab.spotlightChase(listOf(done, empty, open))?.id)
    }

    @Test
    fun `there is no spotlight when everything is done`() {
        val done = chase("done", owned = 10, total = 10)
        assertNull(CollectorLab.spotlightChase(listOf(done)))
    }

    @Test
    fun `chase missing count never goes negative`() {
        // Le doppie possono far salire il conteggio sopra il totale.
        assertEquals(0, chase("x", owned = 12, total = 10).missing)
    }

    @Test
    fun `chase filter matches the criteria label too`() {
        val rows = listOf(
            chase("1", name = "Caccia grossa", criteria = "Set · Paldea"),
            chase("2", name = "Kanto", criteria = "Set · Base")
        )
        assertEquals(listOf("1"), CollectorLab.filterChases(rows, "paldea").map { it.id })
    }

    // ── Riassunto ─────────────────────────────────────────────────────────

    @Test
    fun `summary adds up albums and chases`() {
        val albums = listOf(album("1", used = 5, value = 10.0), album("2", used = 3, value = 2.5))
        val chases = listOf(
            chase("a", owned = 10, total = 10),
            chase("b", owned = 2, total = 10)
        )
        val summary = CollectorLab.summary(albums, chases)
        assertEquals(2, summary.albums)
        assertEquals(8, summary.cardsInAlbums)
        assertEquals(12.5, summary.albumValue, 0.001)
        assertEquals(1, summary.chasesCompleted)
        assertEquals(8, summary.missingCards)
        assertEquals(60f, summary.averageChasePercent, 0.001f)
    }

    @Test
    fun `chases without target cards do not drag the average down`() {
        val chases = listOf(chase("a", owned = 5, total = 10), chase("empty", owned = 0, total = 0))
        assertEquals(50f, CollectorLab.summary(emptyList(), chases).averageChasePercent, 0.001f)
    }

    @Test
    fun `a lab with nothing in it summarises to zero`() {
        val summary = CollectorLab.summary(emptyList(), emptyList())
        assertEquals(0, summary.albums)
        assertEquals(0f, summary.averageChasePercent, 0.001f)
    }

    // ── Prezzi delle mancanti ─────────────────────────────────────────────

    @Test
    fun `completion cost sums the minimum price of each missing card`() {
        val missing = listOf(card("a", low = 2.0), card("b", low = 3.5))
        assertEquals(5.5, CollectorLab.completionCost(missing), 0.001)
    }

    @Test
    fun `cards without a price count as zero and are declared`() {
        val missing = listOf(card("a", low = 4.0), card("b"))
        assertEquals(4.0, CollectorLab.completionCost(missing), 0.001)
        assertEquals(1, CollectorLab.pricedCount(missing))
    }

    @Test
    fun `average sell price stands in when the low price is missing`() {
        val missing = listOf(card("a", avg = 7.0))
        assertEquals(7.0, CollectorLab.completionCost(missing), 0.001)
    }

    @Test
    fun `cheapest and priciest ignore cards without a price`() {
        val missing = listOf(card("none"), card("mid", low = 5.0), card("top", low = 40.0))
        assertEquals("mid", CollectorLab.cheapestMissing(missing)?.id)
        assertEquals("top", CollectorLab.mostExpensiveMissing(missing)?.id)
    }

    @Test
    fun `with no priced card there is no suggestion`() {
        assertNull(CollectorLab.cheapestMissing(listOf(card("a"))))
        assertNull(CollectorLab.mostExpensiveMissing(listOf(card("a"))))
    }

    // ── Ordinamento delle carte ───────────────────────────────────────────

    @Test
    fun `card numbers sort like a binder not like strings`() {
        val cards = listOf(card("c", "10"), card("a", "2"), card("b", "9"), card("d", "100"))
        assertEquals(
            listOf("a", "b", "c", "d"),
            CollectorLab.sortChaseCards(cards, ChaseCardSort.NUMBER).map { it.id }
        )
    }

    @Test
    fun `prefixed numbers stay grouped after the plain ones`() {
        val cards = listOf(card("tg", "TG12"), card("plain", "7"), card("sv", "SV001"))
        val sorted = CollectorLab.sortChaseCards(cards, ChaseCardSort.NUMBER).map { it.id }
        assertEquals("plain", sorted.first())
        assertEquals(listOf("sv", "tg"), sorted.drop(1))
    }

    @Test
    fun `unpriced cards sit at the bottom of the ascending price sort`() {
        val cards = listOf(card("none"), card("expensive", low = 30.0), card("cheap", low = 1.0))
        assertEquals(
            listOf("cheap", "expensive", "none"),
            CollectorLab.sortChaseCards(cards, ChaseCardSort.PRICE_ASC).map { it.id }
        )
    }

    @Test
    fun `descending price sort starts from the most expensive`() {
        val cards = listOf(card("cheap", low = 1.0), card("expensive", low = 30.0))
        assertEquals(
            listOf("expensive", "cheap"),
            CollectorLab.sortChaseCards(cards, ChaseCardSort.PRICE_DESC).map { it.id }
        )
    }

    @Test
    fun `card search matches name number and rarity`() {
        val cards = listOf(
            card("a", number = "12", name = "Charizard"),
            card("b", number = "99", name = "Pikachu").copy(rarity = "Illustration Rare")
        )
        assertEquals(listOf("a"), CollectorLab.filterChaseCards(cards, "chari").map { it.id })
        assertEquals(listOf("b"), CollectorLab.filterChaseCards(cards, "99").map { it.id })
        assertEquals(listOf("b"), CollectorLab.filterChaseCards(cards, "illustration").map { it.id })
    }

    @Test
    fun `card number key splits prefix and digits`() {
        assertEquals(Pair("", 7), CollectorLab.cardNumberKey("007"))
        assertEquals(Pair("TG", 12), CollectorLab.cardNumberKey("TG12"))
        assertEquals(Pair("", 45), CollectorLab.cardNumberKey("45/102"))
        assertEquals(Pair("SWSH", 1), CollectorLab.cardNumberKey("swsh001"))
    }
}
