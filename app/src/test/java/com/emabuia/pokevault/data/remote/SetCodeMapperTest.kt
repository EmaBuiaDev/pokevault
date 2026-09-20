package com.emabuia.pokevault.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetCodeMapperTest {

    @Test
    fun `normalize decklist set code maps known aliases`() {
        assertEquals("sv6", SetCodeMapper.normalizeDecklistSetCode("TWM"))
        assertEquals("sv1", SetCodeMapper.normalizeDecklistSetCode("svi"))
        assertEquals("sv10", SetCodeMapper.normalizeDecklistSetCode("DRI"))
        assertEquals("me03", SetCodeMapper.normalizeDecklistSetCode("POR"))
        assertEquals("dcr", SetCodeMapper.normalizeDecklistSetCode("ME04"))
        assertEquals("dcr", SetCodeMapper.normalizeDecklistSetCode("CRI"))
        assertEquals("MEG", SetCodeMapper.normalizeDecklistSetCode("ME01"))
        assertEquals("PFL", SetCodeMapper.normalizeDecklistSetCode("ME02"))
        assertEquals("asc", SetCodeMapper.normalizeDecklistSetCode("ME2PT5"))
        assertEquals("me03", SetCodeMapper.normalizeDecklistSetCode("ME03"))

        // Questi tre prima puntavano al set sbagliato, e il test lo fissava:
        // MEP valeva "MEG", BLK e WHT valevano tutti e due "sv11". Due set
        // diversi con lo stesso codice normalizzato sono indistinguibili per
        // chi cerca una carta per set+numero, che finiva per prendere la prima
        // del catalogo: "Kadabra MEG 55" tornava una carta di MEP. Vedi
        // SetCodeCollisionTest, che ora vieta la classe di errore invece dei
        // singoli casi.
        assertEquals("mep", SetCodeMapper.normalizeDecklistSetCode("MEP"))
        // BLK e WHT erano stati tolti da "sv11" ma scambiati fra loro, e qui
        // lo scambio era fissato: Black Bolt e' "Luce Nera" (rsv10pt5) e White
        // Flare e' "Fuoco Bianco" (zsv10pt5).
        assertEquals("rsv10pt5", SetCodeMapper.normalizeDecklistSetCode("BLK"))
        assertEquals("zsv10pt5", SetCodeMapper.normalizeDecklistSetCode("wht"))
    }

    @Test
    fun `matches imported set with api set id and set name acronym`() {
        assertTrue(
            SetCodeMapper.matchesImportedSet(
                importedSet = "TWM",
                cardSetName = "Twilight Masquerade",
                cardApiSetId = "sv6",
                cardApiId = "sv6-128"
            )
        )
    }

    @Test
    fun `does not match different set alias`() {
        assertFalse(
            SetCodeMapper.matchesImportedSet(
                importedSet = "TWM",
                cardSetName = "Paldean Fates",
                cardApiSetId = "sv4pt5",
                cardApiId = "sv4pt5-128"
            )
        )
    }

    @Test
    fun `matches imported por alias against me03 api set`() {
        assertTrue(
            SetCodeMapper.matchesImportedSet(
                importedSet = "POR",
                cardSetName = "Perfect Order",
                cardApiSetId = "ME03",
                cardApiId = "pk_test"
            )
        )
    }

    @Test
    fun `matches imported me04 alias against chaos rising set`() {
        assertTrue(
            SetCodeMapper.matchesImportedSet(
                importedSet = "ME04",
                cardSetName = "Chaos Rising",
                cardApiSetId = "dcr",
                cardApiId = "pk_test"
            )
        )
    }
}