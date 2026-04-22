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
        assertEquals("dcr", SetCodeMapper.normalizeDecklistSetCode("CRI"))
        assertEquals("MEG", SetCodeMapper.normalizeDecklistSetCode("ME01"))
        assertEquals("PFL", SetCodeMapper.normalizeDecklistSetCode("ME02"))
        assertEquals("sv11", SetCodeMapper.normalizeDecklistSetCode("BLK"))
        assertEquals("sv11", SetCodeMapper.normalizeDecklistSetCode("wht"))
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
}