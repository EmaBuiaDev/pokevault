package com.emabuia.pokevault.data.trade

import com.emabuia.pokevault.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * TradeRadar esce con la 3.1.6: nel flavor prod e' acceso e parla col Worker
 * di produzione, nello staging col Worker di prova.
 *
 * Fino alla 3.1.5 questo test pretendeva il contrario (flag spento in prod):
 * si e' cambiato insieme al flag, apposta e non per sbaglio. Un indirizzo
 * sbagliato qui manderebbe gli utenti veri sullo staging, o viceversa.
 */
class TradeRadarFlagTest {

    @Test
    fun `in prod TradeRadar e' acceso sul Worker di produzione`() {
        assumeTrue(BuildConfig.FLAVOR == "prod")

        assertTrue(BuildConfig.TRADE_ENABLED)
        assertEquals("https://pokevault-proxy.pokevault-emanu.workers.dev", BuildConfig.TRADE_API_URL)
        assertTrue(TradeApi.isEnabled)
    }

    @Test
    fun `nello staging TradeRadar parla col Worker di prova`() {
        assumeTrue(BuildConfig.FLAVOR == "staging")

        assertTrue(BuildConfig.TRADE_ENABLED)
        assertEquals("https://pokevault-trade-staging.pokevault-emanu.workers.dev", BuildConfig.TRADE_API_URL)
    }
}
