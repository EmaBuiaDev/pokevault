package com.emabuia.pokevault.data.italian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La sigla di /v1/expansions (schema/016 del Worker) deve arrivare al modello.
 * La risposta qui sotto ha la forma di quella vera dell'11/10/2026.
 */
class ItalianExpansionAbbreviationTest {

    private val response = """
        {"expansions":[
          {"id":"sv4pt5","name":"Destino di Paldea","cardCount":245,"officialCount":91,"sortOrder":100,
           "logoKey":null,"series":"Scarlet & Violet","abbreviation":"PAF","baseSetCode":"SV4PT5","releaseDate":"2024-01-26"},
          {"id":"sma","name":"Destino Sfuggente Tesoro Lucente","cardCount":94,"officialCount":94,"sortOrder":100,
           "logoKey":null,"series":"Sun & Moon","abbreviation":null,"baseSetCode":"SMA","releaseDate":"2019-08-23"}
        ]}
    """.trimIndent()

    @Test
    fun `la sigla arriva dove c'e', null dove manca`() {
        val byId = ItalianCatalogNormalizer.parseExpansionsResponse(response).associateBy { it.id }
        assertEquals("PAF", byId.getValue("sv4pt5").abbreviation)
        assertNull(byId.getValue("sma").abbreviation)
    }

    @Test
    fun `una risposta di prima, senza il campo, si legge lo stesso`() {
        val old = """{"expansions":[{"id":"swsh1","name":"Spada e Scudo","cardCount":216}]}"""
        val summary = ItalianCatalogNormalizer.parseExpansionsResponse(old).single()
        assertEquals("swsh1", summary.id)
        assertNull(summary.abbreviation)
    }
}
