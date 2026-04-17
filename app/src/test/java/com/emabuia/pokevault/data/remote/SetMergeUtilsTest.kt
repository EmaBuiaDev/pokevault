package com.emabuia.pokevault.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetMergeUtilsTest {

    @Test
    fun `normalize set text collapses punctuation accents and whitespace`() {
        val normalized = normalizeSetText("  Scarlet & Víolet\nPromo  ")

        assertEquals("scarlet violet promo", normalized)
    }

    @Test
    fun `merge skips duplicate extra set when canonical key matches primary`() {
        val primary = listOf(
            TcgSet(
                id = "sv1",
                name = "Scarlet & Violet",
                series = "Scarlet & Violet",
                printedTotal = 198,
                total = 258
            )
        )
        val extras = listOf(
            TcgSet(
                id = encodeTcgdexSetId("sv01"),
                name = "Scarlet Violet",
                series = "Scarlet   Violet",
                printedTotal = 198,
                total = 258
            )
        )

        val merged = mergePrimaryWithExtras(primary, extras)

        assertEquals(1, merged.size)
        assertEquals("sv1", merged.first().id)
    }

    @Test
    fun `merge keeps extra set when series changes canonical key`() {
        val primary = listOf(
            TcgSet(
                id = "swshp",
                name = "Black Star Promos",
                series = "Sword & Shield",
                printedTotal = 107,
                total = 288
            )
        )
        val extras = listOf(
            TcgSet(
                id = encodeTcgdexSetId("svp"),
                name = "Black Star Promos",
                series = "Scarlet & Violet",
                printedTotal = 225,
                total = 226
            )
        )

        val merged = mergePrimaryWithExtras(primary, extras)

        assertEquals(2, merged.size)
        assertTrue(merged.any { it.id == encodeTcgdexSetId("svp") })
    }

    @Test
    fun `tcgdex summary requires logo and positive count`() {
        val valid = TcgdexSetSummary(
            id = "base1",
            name = "Base Set",
            logo = "https://assets.tcgdex.net/en/base/base1/logo",
            cardCount = TcgdexCardCount(total = 102, official = 102)
        )
        val missingLogo = valid.copy(logo = "")
        val missingCount = valid.copy(cardCount = TcgdexCardCount())

        assertTrue(hasTcgdexSummaryAssets(valid))
        assertFalse(hasTcgdexSummaryAssets(missingLogo))
        assertFalse(hasTcgdexSummaryAssets(missingCount))
    }

    @Test
    fun `tcgdex detail requires logo and images for all cards`() {
        val complete = TcgdexSetDetail(
            id = "base1",
            name = "Base Set",
            logo = "https://assets.tcgdex.net/en/base/base1/logo",
            serie = TcgdexSerie(id = "base", name = "Base"),
            cardCount = TcgdexCardCount(total = 102, official = 102),
            cards = listOf(
                TcgdexCardSummary(id = "base1-1", localId = "1", name = "Alakazam", image = "https://assets.tcgdex.net/en/base/base1/1"),
                TcgdexCardSummary(id = "base1-2", localId = "2", name = "Blastoise", image = "https://assets.tcgdex.net/en/base/base1/2")
            )
        )

        assertTrue(hasCompleteTcgdexAssets(complete))
        assertFalse(hasCompleteTcgdexAssets(complete.copy(logo = "")))
        assertFalse(
            hasCompleteTcgdexAssets(
                complete.copy(
                    cards = complete.cards + TcgdexCardSummary(
                        id = "base1-3",
                        localId = "3",
                        name = "Chansey",
                        image = ""
                    )
                )
            )
        )
    }
}