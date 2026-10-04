package com.emabuia.pokevault.data.italian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le carte della Galleria Allenatori di Astri Lucenti (swsh9tg) hanno card_id
 * "SWSH9_IT_TG01", e lo snapshot scrive l'alias swsh9 -> swsh9tg: chi chiedeva
 * "swsh9" riceveva le 30 carte della galleria al posto del set, e il Moltres di
 * Galar-V 183 restava senza prezzo. Stessa regola del Worker (price-lookup.ts).
 */
class ItalianPriceSnapshotLookupTest {

    private val snapshot = ItalianPriceSnapshot(
        version = 1,
        builtAt = 1L,
        expansions = mapOf(
            "swsh9" to ItalianPriceExpansionEntry("BRS", mapOf("183" to ItalianPriceEntry(low = 20.0), "TG24" to ItalianPriceEntry(low = 5.0))),
            "swsh9tg" to ItalianPriceExpansionEntry("BRS-TG", mapOf("TG01" to ItalianPriceEntry(low = 3.0), "TG24" to ItalianPriceEntry(low = 4.0))),
            "me05" to ItalianPriceExpansionEntry("PBL", mapOf("30" to ItalianPriceEntry(low = 0.02)))
        ),
        aliases = mapOf("swsh9" to "swsh9tg", "swsh9tg" to "swsh9tg", "me05" to "me05", "pbl" to "me05")
    )

    @Test
    fun `un set rubato da un alias da le sue carte piu quelle della galleria`() {
        val prices = snapshot.priceMapFor("SWSH9")
        assertEquals(20.0, prices["183"]?.low)
        assertEquals(3.0, prices["TG01"]?.low)
        assertEquals("un numero in tutte e due resta quello della galleria, come prima", 4.0, prices["TG24"]?.low)
    }

    @Test
    fun `la galleria chiesta per nome resta solo la galleria`() {
        assertEquals(setOf("TG01", "TG24"), snapshot.priceMapFor("swsh9tg").keys)
    }

    @Test
    fun `alias normali e set senza alias come prima`() {
        assertSame(snapshot.expansions.getValue("me05").prices, snapshot.priceMapFor("pbl"))
        assertSame(snapshot.expansions.getValue("me05").prices, snapshot.priceMapFor("me05"))
        assertTrue(snapshot.priceMapFor("boh").isEmpty())
        assertTrue(snapshot.priceMapFor(" ").isEmpty())
        assertNull(snapshot.priceMapFor("me05")["31"])
    }
}
