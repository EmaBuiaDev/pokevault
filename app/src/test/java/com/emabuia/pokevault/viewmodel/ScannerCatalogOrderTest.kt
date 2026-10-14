package com.emabuia.pokevault.viewmodel

import com.emabuia.pokevault.data.catalog.CatalogLang
import org.junit.Assert.assertEquals
import org.junit.Test

/** In che cataloghi cerca lo scanner, e in che ordine. */
class ScannerCatalogOrderTest {

    @Test
    fun `inglese spento - solo l'italiano, lo scanner di sempre`() {
        ScanLanguage.entries.forEach { mode ->
            assertEquals(listOf(CatalogLang.IT), scannerCatalogOrder(mode, CatalogLang.EN, CatalogLang.EN, englishEnabled = false))
        }
    }

    @Test
    fun `auto - prima la lingua letta sulla carta, poi l'altra`() {
        assertEquals(listOf(CatalogLang.EN, CatalogLang.IT), scannerCatalogOrder(ScanLanguage.AUTO, CatalogLang.EN, CatalogLang.IT, true))
        assertEquals(listOf(CatalogLang.IT, CatalogLang.EN), scannerCatalogOrder(ScanLanguage.AUTO, CatalogLang.IT, CatalogLang.EN, true))
    }

    @Test
    fun `auto senza lingua letta - quella dell'app`() {
        assertEquals(listOf(CatalogLang.EN, CatalogLang.IT), scannerCatalogOrder(ScanLanguage.AUTO, null, CatalogLang.EN, true))
        assertEquals(listOf(CatalogLang.IT, CatalogLang.EN), scannerCatalogOrder(ScanLanguage.AUTO, null, CatalogLang.IT, true))
    }

    @Test
    fun `lingua fissata - solo quel catalogo, qualunque cosa si legga`() {
        assertEquals(listOf(CatalogLang.IT), scannerCatalogOrder(ScanLanguage.IT, CatalogLang.EN, CatalogLang.EN, true))
        assertEquals(listOf(CatalogLang.EN), scannerCatalogOrder(ScanLanguage.EN, CatalogLang.IT, CatalogLang.IT, true))
    }
}
