package com.emabuia.pokevault.data.catalog

import com.emabuia.pokevault.data.trade.TradeCardKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il punto unico che riconosce le carte del nostro catalogo. Con l'italiano
 * deve dare esattamente quello che davano i vecchi controlli a mano
 * (`startsWith("ita:")`, `endsWith("__ita")`): e' la Fase 1 del piano carte EN.
 */
class CardRefTest {

    @Test
    fun `l'id italiano si scrive come prima`() {
        assertEquals("ita:sv08:12", CardRef.of(CatalogLang.IT, "SV08", "12").apiCardId)
        assertEquals("ita:30th-c:1", CardRef.of(CatalogLang.IT, " 30TH-C ", " 1 ").apiCardId)
        // I promo a lettere restano come sono: vedi promo numerati a lettere.
        assertEquals("ita:swshp:SWSH026", CardRef.of(CatalogLang.IT, "swshp", "SWSH026").apiCardId)
    }

    @Test
    fun `parse legge set e numero`() {
        assertEquals(CardRef(CatalogLang.IT, "sv08", "12"), CardRef.parse("ita:sv08:12"))
        assertEquals(CardRef(CatalogLang.IT, "sv08", "12"), CardRef.parse("  ITA:SV08:12 "))
        assertEquals(CardRef(CatalogLang.EN, "sv08", "12"), CardRef.parse("eng:sv08:12"))
        assertEquals(CardRef(CatalogLang.IT, "bwp", "BW01"), CardRef.parse("ita:bwp:BW01"))
    }

    @Test
    fun `parse rifiuta cio' che non e' una carta del catalogo`() {
        assertNull(CardRef.parse(null))
        assertNull(CardRef.parse(""))
        assertNull(CardRef.parse("sv8-12")) // id PokeWallet
        assertNull(CardRef.parse("jpn:sv8:12"))
        assertNull(CardRef.parse("ita:sv08")) // manca il numero
        assertNull(CardRef.parse("ita:sv08:12:x"))
        assertNull(CardRef.parse("ita::12"))
        assertNull(CardRef.parse("ita:sv08: "))
        assertNull(CardRef.parse("italia:sv08:12"))
    }

    @Test
    fun `langOf guarda solo il prefisso`() {
        assertEquals(CatalogLang.IT, CardRef.langOf("ita:sv08:12"))
        assertEquals(CatalogLang.IT, CardRef.langOf("Ita:qualcosa"))
        assertEquals(CatalogLang.EN, CardRef.langOf("eng:sv08:12"))
        assertNull(CardRef.langOf("sv8-12"))
        assertNull(CardRef.langOf("ita"))
        assertNull(CardRef.langOf(":sv08:12"))
        assertTrue(CardRef.isCatalogCard("ita:sv08:12", CatalogLang.IT))
        assertFalse(CardRef.isCatalogCard("eng:sv08:12", CatalogLang.IT))
    }

    @Test
    fun `setCodeOf basta anche senza numero, come i vecchi split`() {
        assertEquals("sv08", CardRef.setCodeOf("ita:sv08:12", CatalogLang.IT))
        assertEquals("sv08", CardRef.setCodeOf("ita:sv08", CatalogLang.IT))
        assertEquals("30th-c", CardRef.setCodeOf("ita:30th-c:7", CatalogLang.IT))
        assertNull(CardRef.setCodeOf("ita::12", CatalogLang.IT))
        assertNull(CardRef.setCodeOf("eng:sv08:12", CatalogLang.IT))
        assertNull(CardRef.setCodeOf("sv8-12", CatalogLang.IT))
        assertNull(CardRef.setCodeOf(null, CatalogLang.IT))
    }

    @Test
    fun `id dei set del catalogo`() {
        assertEquals("sv08__ita", CatalogSetId.of(CatalogLang.IT, " SV08 "))
        assertEquals("sv08__eng", CatalogSetId.of(CatalogLang.EN, "sv08"))
        assertEquals(CatalogLang.IT, CatalogSetId.langOf("SV08__ITA"))
        assertEquals(CatalogLang.EN, CatalogSetId.langOf("sv08__eng"))
        assertNull(CatalogSetId.langOf("sv8"))
        assertNull(CatalogSetId.langOf(null))
        assertEquals("sv08", CatalogSetId.expansionOf("sv08__ita", CatalogLang.IT))
        assertNull(CatalogSetId.expansionOf("sv08__eng", CatalogLang.IT))
        assertNull(CatalogSetId.expansionOf("__ita", CatalogLang.IT))
        assertTrue(CatalogSetId.isCatalogSet("me02__ita", CatalogLang.IT))
        assertFalse(CatalogSetId.isCatalogSet("me02", CatalogLang.IT))
    }

    @Test
    fun `lingua predefinita della copia`() {
        assertEquals("🇮🇹 Italiano", CatalogLang.IT.copyLanguageLabel)
        assertEquals("🇬🇧 English", CatalogLang.EN.copyLanguageLabel)
    }

    @Test
    fun `negli scambi per ora conta solo l'italiano`() {
        assertEquals("sv08:12", TradeCardKey.fromApiCardId("ita:sv08:012"))
        assertEquals("bwp:BW01", TradeCardKey.fromApiCardId("ita:bwp:BW01"))
        assertNull(TradeCardKey.fromApiCardId("eng:sv08:12"))
        assertEquals("ita:sv08:12", TradeCardKey.toApiCardId("sv08:12"))
    }
}
