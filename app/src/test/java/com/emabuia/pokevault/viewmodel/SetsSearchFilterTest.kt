package com.emabuia.pokevault.viewmodel

import com.emabuia.pokevault.data.italian.ItalianCardSearchFilter
import com.emabuia.pokevault.data.italian.ItalianHpBucket
import com.emabuia.pokevault.data.italian.ItalianPriceBucket
import com.emabuia.pokevault.data.remote.CardMarket
import com.emabuia.pokevault.data.remote.CardMarketPrices
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgCardSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le regole con cui i filtri del pannello restringono i risultati di ricerca.
 *
 * Restringono: non producono risultati da soli. Questi test coprono i casi in
 * cui una svista si vedrebbe solo in esercizio -- la carta a doppio tipo che
 * non esce, la macchina che passa un filtro sui punti salute.
 */
class SetsSearchFilterTest {

    private fun card(
        name: String = "Charizard ex",
        supertype: String = "Pokémon",
        types: List<String>? = listOf("Fuoco"),
        hp: String? = "330",
        rarity: String? = "Double Rare",
        setId: String = "sv08__ita",
        series: String = "Scarlatto e Violetto",
        eur: Double? = null
    ) = TcgCard(
        id = "ita:sv08:001",
        name = name,
        supertype = supertype,
        types = types,
        hp = hp,
        rarity = rarity,
        set = TcgCardSet(id = setId, name = "Scintille Folgoranti", series = series),
        number = "001",
        cardmarket = eur?.let { CardMarket(prices = CardMarketPrices(lowPrice = it)) }
    )

    @Test
    fun `un filtro vuoto lascia passare tutto`() {
        assertTrue(cardMatchesFilter(card(), ItalianCardSearchFilter()))
    }

    @Test
    fun `le dimensioni diverse si sommano in AND`() {
        val fuocoDoppiaRara = ItalianCardSearchFilter(
            types = setOf("Fuoco"),
            rarities = setOf("Double Rare")
        )
        assertTrue(cardMatchesFilter(card(), fuocoDoppiaRara))
        assertFalse(cardMatchesFilter(card(), fuocoDoppiaRara.copy(rarities = setOf("Secret Rare"))))
    }

    @Test
    fun `piu' valori nella stessa dimensione si sommano in OR`() {
        val filter = ItalianCardSearchFilter(types = setOf("Acqua", "Fuoco"))
        assertTrue(cardMatchesFilter(card(), filter))
    }

    @Test
    fun `una carta a doppio tipo passa se e' selezionato uno dei due`() {
        val doppioTipo = card(types = listOf("Metallo", "Lotta"))
        assertTrue(cardMatchesFilter(doppioTipo, ItalianCardSearchFilter(types = setOf("Lotta"))))
        assertFalse(cardMatchesFilter(doppioTipo, ItalianCardSearchFilter(types = setOf("Acqua"))))
    }

    @Test
    fun `il filtro meccanica legge il suffisso del nome`() {
        assertTrue(cardMatchesFilter(card(name = "Charizard ex"), ItalianCardSearchFilter(variants = setOf("ex"))))
        assertFalse(cardMatchesFilter(card(name = "Charizard"), ItalianCardSearchFilter(variants = setOf("ex"))))
    }

    @Test
    fun `una carta senza ps non passa mai un filtro sui ps`() {
        val macchina = card(name = "Professor Oak", supertype = "Trainer", types = null, hp = null)
        val filter = ItalianCardSearchFilter(hpBuckets = setOf(ItalianHpBucket.UP_TO_60))
        assertFalse(cardMatchesFilter(macchina, filter))
    }

    @Test
    fun `il filtro espansione confronta il codice senza il suffisso ita`() {
        assertEquals("sv08", cardExpansionIdOf(card(setId = "sv08__ita")))
        assertTrue(cardMatchesFilter(card(), ItalianCardSearchFilter(expansionIds = setOf("sv08"))))
        assertFalse(cardMatchesFilter(card(), ItalianCardSearchFilter(expansionIds = setOf("sv09"))))
    }

    @Test
    fun `la fascia di prezzo esiste solo se lo snapshot copre la carta`() {
        assertNull(cardPriceBucketOf(card(eur = null)))
        assertEquals(ItalianPriceBucket.FROM_5_TO_20, cardPriceBucketOf(card(eur = 12.50)))
    }

    @Test
    fun `la fascia ps legge i punti salute della carta`() {
        assertEquals(ItalianHpBucket.OVER_200, cardHpBucketOf(card(hp = "330")))
        assertNull(cardHpBucketOf(card(hp = null)))
    }

    // ── Ricerca per numero di carta ──

    @Test
    fun `un id completo si legge come numero piu' totale`() {
        val parsed = parseCardNumberQuery("001/217")
        assertEquals("1", parsed?.number)
        assertEquals(217, parsed?.printedTotal)
    }

    @Test
    fun `gli zeri davanti e gli spazi non contano`() {
        assertEquals(parseCardNumberQuery("1/217"), parseCardNumberQuery(" 001 / 0217 "))
    }

    @Test
    fun `il solo numero vale come numero di carta, senza totale`() {
        val parsed = parseCardNumberQuery("067")
        assertEquals("67", parsed?.number)
        assertNull(parsed?.printedTotal)
    }

    @Test
    fun `un nome non e' una ricerca per numero`() {
        assertNull(parseCardNumberQuery("Charizard"))
        assertNull(parseCardNumberQuery("Charizard ex"))
        assertNull(parseCardNumberQuery("sv08 001"))
    }

    @Test
    fun `col totale indicato restano solo le espansioni che combaciano`() {
        val giusta = card(setId = "sv08__ita").copy(set = TcgCardSet(id = "sv08__ita", printedTotal = 217))
        val altra = card(setId = "sv09__ita").copy(set = TcgCardSet(id = "sv09__ita", printedTotal = 191))
        assertEquals(listOf(giusta), narrowToPrintedTotal(listOf(giusta, altra), 217))
    }

    @Test
    fun `se nessun totale combacia si tengono tutte, invece di svuotare lo schermo`() {
        val cards = listOf(card().copy(set = TcgCardSet(id = "sv08__ita", printedTotal = 217)))
        assertEquals(cards, narrowToPrintedTotal(cards, 999))
    }

    @Test
    fun `un id a meta-battitura vale come solo numero`() {
        val parsed = parseCardNumberQuery("001/")
        assertEquals("1", parsed?.number)
        assertNull(parsed?.printedTotal)
    }

    @Test
    fun `senza totale non si restringe niente`() {
        val cards = listOf(card(), card(setId = "sv09__ita"))
        assertEquals(cards, narrowToPrintedTotal(cards, null))
    }
}
