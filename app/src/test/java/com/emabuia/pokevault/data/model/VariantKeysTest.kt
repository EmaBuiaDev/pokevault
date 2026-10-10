package com.emabuia.pokevault.data.model

import com.emabuia.pokevault.data.remote.PokeWalletPriceData
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgCardSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VariantKeysTest {

    @Test
    fun `le stampe base restano le stringhe di sempre e non sono speciali`() {
        listOf("Normal", "Reverse", "Holo", "1st Edition", "1st Edition Holo").forEach {
            assertFalse(it, VariantKeys.isSpecial(it))
            assertEquals(it, VariantKeys.baseOf(it))
        }
    }

    @Test
    fun `una speciale si scompone in base, lavorazione e timbri`() {
        val parsed = VariantKeys.parse("Reverse|foil=league|stamp=30th-pokeday")
        assertEquals("Reverse", parsed.base)
        assertEquals("league", parsed.foil)
        assertEquals(listOf("30th-pokeday"), parsed.stamps)
        assertTrue(VariantKeys.isSpecial("Reverse|foil=league|stamp=30th-pokeday"))
    }

    @Test
    fun `i nomi delle speciali sono leggibili in italiano`() {
        assertEquals("Reverse Poké Ball", VariantKeys.label("Reverse|foil=pokeball", italian = true))
        assertEquals("Normale · Pokémon Day", VariantKeys.label("Normal|stamp=pokemon-day", italian = true))
        assertEquals("Reverse Lega · Pokémon Day 30°", VariantKeys.label("Reverse|foil=league|stamp=30th-pokeday", italian = true))
        assertEquals("1ª Edizione", VariantKeys.label("1st Edition", italian = true))
        // Un timbro che ancora non conosciamo non sparisce: si legge lo stesso.
        assertEquals("Normale · Nuovo Timbro", VariantKeys.label("Normal|stamp=nuovo-timbro", italian = true))
        assertEquals("Holo · Worlds 2027", VariantKeys.label("Holo|stamp=worlds-2027", italian = true))
    }

    @Test
    fun `prima le base nell'ordine di sempre, poi le speciali`() {
        val sorted = VariantKeys.sorted(
            listOf("Reverse|foil=masterball", "Holo", "Normal|stamp=set-logo", "Reverse", "Normal", "Reverse|foil=pokeball")
        )
        assertEquals(
            listOf("Normal", "Reverse", "Holo", "Normal|stamp=set-logo", "Reverse|foil=masterball", "Reverse|foil=pokeball"),
            sorted
        )
    }

    private fun card(rarity: String, variants: List<String>?, date: String = "2026-09-16") = TcgCard(
        id = "ita:30th:1",
        rarity = rarity,
        set = TcgCardSet(id = "30th__ita", releaseDate = date),
        variants = variants
    )

    @Test
    fun `le stampe del catalogo vincono sulla rarita' -- il 30 non ha reverse`() {
        // Prima: una Comune del 2026 si prendeva Normale e Reverse.
        assertEquals(listOf("Normal", "Reverse"), CardOptions.getVariantsForCard(card("Common", null)))
        // Col catalogo: solo quella che esiste.
        assertEquals(listOf("Normal"), CardOptions.getVariantsForCard(card("Common", listOf("Normal"))))
    }

    @Test
    fun `l'aggiunta veloce propone solo le stampe base`() {
        val c = card("Common", listOf("Reverse|foil=pokeball", "Normal", "Reverse", "Reverse|foil=masterball"))
        assertEquals(listOf("Normal", "Reverse", "Reverse|foil=masterball", "Reverse|foil=pokeball"), CardOptions.getVariantsForCard(c))
        assertEquals(listOf("Normal", "Reverse"), CardOptions.getBaseVariantsForCard(c))
    }

    @Test
    fun `una carta con le sole speciali ne propone comunque una`() {
        val c = card("Promo", listOf("Holo|stamp=staff"))
        assertEquals(listOf("Holo|stamp=staff"), CardOptions.getBaseVariantsForCard(c))
    }

    @Test
    fun `il prezzo di una stampa -- suo, della carta, o nessuno per una speciale`() {
        val reverse = PokeWalletPriceData(eurLow = 0.4)
        val stamped = PokeWalletPriceData(eurLow = 75.0)
        val main = PokeWalletPriceData(
            eurLow = 0.02,
            variantPrices = mapOf("Reverse" to reverse, "Normal|stamp=set-logo" to stamped)
        )
        assertEquals(reverse, main.forVariant("Reverse"))
        assertEquals(stamped, main.forVariant("Normal|stamp=set-logo"))
        // Una base senza prezzo suo costa come la carta (il worker scrive solo i diversi).
        assertEquals(main, main.forVariant("Normal"))
        // Una speciale senza prezzo suo non prende quello della comune.
        assertNull(main.forVariant("Reverse|foil=masterball"))
    }
}
