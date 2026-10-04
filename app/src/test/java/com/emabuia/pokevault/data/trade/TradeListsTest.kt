package com.emabuia.pokevault.data.trade

import com.emabuia.pokevault.data.model.GoalAlbum
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.Wishlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeCardKeyTest {

    @Test
    fun `l'id italiano del catalogo diventa codice e numero`() {
        assertEquals("me02:1", TradeCardKey.fromApiCardId("ita:me02:1"))
        assertEquals("me02:1", TradeCardKey.fromApiCardId("ita:ME02:001"))
        assertEquals("sv3pt5:199", TradeCardKey.fromApiCardId(" ita:SV3PT5:199 "))
    }

    @Test
    fun `promo a lettere e set col trattino restano validi`() {
        assertEquals("bwp:BW01", TradeCardKey.fromApiCardId("ita:bwp:BW01"))
        assertEquals("swshp:SWSH026", TradeCardKey.fromApiCardId("ita:swshp:SWSH026"))
        assertEquals("30th-c:12", TradeCardKey.fromApiCardId("ita:30TH-C:12"))
    }

    @Test
    fun `le carte senza id italiano restano fuori`() {
        assertNull(TradeCardKey.fromApiCardId("sv3pt5-199"))
        assertNull(TradeCardKey.fromApiCardId("pk_12345"))
        assertNull(TradeCardKey.fromApiCardId(""))
        assertNull(TradeCardKey.fromApiCardId(null))
        assertNull(TradeCardKey.fromApiCardId("ita:me02"))
        assertNull(TradeCardKey.fromApiCardId("ita:me 02:1"))
    }

    @Test
    fun `etichetta e immagine si ricavano dalla chiave`() {
        assertEquals("ME02 · 1", TradeCardKey.label("me02:1"))
        assertEquals(
            "https://x.dev/images/it/30TH-C/12?size=low",
            TradeCardKey.imageUrl("30th-c:12", "https://x.dev/")
        )
    }
}

class GeohashTest {

    @Test
    fun `vettori noti`() {
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, 11))
        // Stesso valore che il Worker usa nei suoi test (src/geohash.ts).
        assertEquals("u0nd9", Geohash.encode(45.4642, 9.19))
    }

    @Test
    fun `al server vanno solo 5 caratteri`() {
        assertEquals(5, Geohash.encode(41.9028, 12.4964).length)
    }
}

class TradeListsTest {

    private fun card(
        apiId: String,
        qty: Int,
        variant: String = "Normal",
        condition: String = "Near Mint",
        graded: Boolean = false,
        deckOnly: Boolean = false
    ) = PokemonCard(
        name = apiId,
        set = "Set",
        apiCardId = apiId,
        cardNumber = apiId.substringAfterLast(':'),
        quantity = qty,
        variant = variant,
        condition = condition,
        isGraded = graded,
        deckOnly = deckOnly
    )

    @Test
    fun `un doppione e' ogni copia oltre la prima`() {
        val duplicates = TradeLists.duplicates(listOf(card("ita:me02:1", 3), card("ita:me02:2", 1)))
        assertEquals(1, duplicates.size)
        assertEquals("me02:1", duplicates[0].key)
        assertEquals(2, duplicates[0].spare)
    }

    @Test
    fun `gradate, solo-deck e carte senza id italiano non si offrono`() {
        val duplicates = TradeLists.duplicates(
            listOf(
                card("ita:me02:1", 2, graded = true),
                card("ita:me02:2", 2, deckOnly = true),
                card("sv3pt5-199", 2)
            )
        )
        assertTrue(duplicates.isEmpty())
    }

    @Test
    fun `stampe e condizioni diverse sono offerte diverse`() {
        val duplicates = TradeLists.duplicates(
            listOf(
                card("ita:me02:1", 2, variant = "Normal"),
                card("ita:me02:1", 2, variant = "Reverse"),
                card("ita:me02:1", 3, variant = "Normal", condition = "Played")
            )
        )
        assertEquals(3, duplicates.size)
        assertEquals(3, duplicates.map { it.id }.toSet().size)
    }

    @Test
    fun `due documenti con una copia ciascuno fanno un doppione`() {
        val cards = listOf(card("ita:me02:1", 1), card("ita:me02:1", 1))
        assertEquals(1, TradeLists.duplicates(cards).single().spare)
        assertTrue(TradeLists.singles(cards).isEmpty())
    }

    @Test
    fun `le singole sono le carte in una copia, con le stesse esclusioni`() {
        val singles = TradeLists.singles(
            listOf(
                card("ita:me02:1", 1),
                card("ita:me02:2", 3),
                card("ita:me02:3", 1, graded = true),
                card("ita:me02:4", 1, deckOnly = true),
                card("sv3pt5-199", 1)
            )
        )
        assertEquals(listOf("me02:1"), singles.map { it.key })
        assertEquals(1, singles[0].spare)
    }

    @Test
    fun `le possedute escludono le solo-deck`() {
        val owned = TradeLists.ownedKeys(listOf(card("ita:me02:1", 1), card("ita:me02:2", 1, deckOnly = true)))
        assertEquals(setOf("me02:1"), owned)
    }

    @Test
    fun `le cercate sono wishlist e album meno le possedute, wishlist per prima`() {
        val wants = TradeLists.wants(
            wishlists = listOf(Wishlist(cardIds = listOf("ita:sv01:5", "ita:me02:1", "sv3pt5-199"))),
            goalAlbums = listOf(GoalAlbum(targetCardApiIds = listOf("ita:sv01:5", "ita:sv01:6"))),
            owned = setOf("me02:1")
        )
        assertEquals(listOf("sv01:5" to "wishlist", "sv01:6" to "album"), wants.map { it.key to it.source })
    }

    @Test
    fun `l'impronta non dipende dall'ordine e cambia con le carte`() {
        val a = TradeLists.ownedHash(setOf("me02:1", "sv01:5"))
        assertEquals(a, TradeLists.ownedHash(linkedSetOf("sv01:5", "me02:1")))
        assertNotEquals(a, TradeLists.ownedHash(setOf("me02:1")))
    }
}
