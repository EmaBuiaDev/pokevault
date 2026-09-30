package com.emabuia.pokevault.data.trade

import com.emabuia.pokevault.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * TradeRadar e' in sviluppo e la produzione non deve vederlo.
 *
 * Nel flavor prod il flag deve restare spento e l'URL vuoto: finche' e' cosi'
 * la tile in Home e la rotta non esistono. Quando verra' il lancio, questo test
 * si cambia insieme al flag, apposta e non per sbaglio.
 */
class TradeRadarFlagTest {

    @Test
    fun `in prod TradeRadar e' spento`() {
        assumeTrue(BuildConfig.FLAVOR == "prod")

        assertFalse(BuildConfig.TRADE_ENABLED)
        assertEquals("", BuildConfig.TRADE_API_URL)
        assertFalse(TradeApi.isEnabled)
    }
}
