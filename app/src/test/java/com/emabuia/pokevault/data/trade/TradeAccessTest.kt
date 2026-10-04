package com.emabuia.pokevault.data.trade

import com.emabuia.pokevault.data.trade.dto.TradeAccess
import com.emabuia.pokevault.data.trade.dto.TradeProfilePayload
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La prova gratuita di 30 giorni (schema 13) come la legge l'app. */
class TradeAccessTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_790_000_000_000L

    @Test
    fun `i giorni rimasti si arrotondano in su e l'ultimo giorno e' 1`() {
        assertEquals(30, TradeAccess("trial", now + 30 * day).trialDaysLeft(now))
        assertEquals(30, TradeAccess("trial", now + 29 * day + 1).trialDaysLeft(now))
        assertEquals(1, TradeAccess("trial", now + 60_000).trialDaysLeft(now))
        assertEquals(0, TradeAccess("trial", now).trialDaysLeft(now))
        assertEquals(0, TradeAccess("receive_only", now - day).trialDaysLeft(now))
        assertEquals(0, TradeAccess("trial", null).trialDaysLeft(now))
    }

    @Test
    fun `i tre stati`() {
        assertTrue(TradeAccess("trial").isTrial)
        assertTrue(TradeAccess("receive_only").isReceiveOnly)
        assertFalse(TradeAccess("premium").isTrial)
        assertFalse(TradeAccess("premium").isReceiveOnly)
        assertFalse(TradeAccess(null).isReceiveOnly)
    }

    @Test
    fun `il profilo del server si legge con e senza access`() {
        val gson = Gson()
        val nuovo = gson.fromJson(
            """{"nickname":"ema994","access":{"mode":"trial","trialEndsAt":${now + 5 * day},"enforced":false}}""",
            TradeProfilePayload::class.java
        )
        assertEquals("trial", nuovo.access?.mode)
        assertEquals(5, nuovo.access?.trialDaysLeft(now))
        assertEquals(false, nuovo.access?.enforced)
        // Un server di prima dello schema 13: nessun access, nessun crash.
        assertNull(gson.fromJson("""{"nickname":"ema994"}""", TradeProfilePayload::class.java).access)
    }
}
