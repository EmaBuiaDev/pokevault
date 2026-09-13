package com.emabuia.pokevault.data.remote

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * L'API Limitless concede 50 richieste ogni 5 minuti e poi risponde 429.
 *
 * Il limitatore e' l'unica cosa che sta fra l'utente e quella risposta, per cui
 * i suoi conti vanno verificati: se sbaglia per eccesso le sezioni meta
 * smettono di caricare, se sbaglia per difetto arriva il 429 e smettono lo
 * stesso, ma per un quarto d'ora.
 */
class LimitlessRateLimiterTest {

    @Before
    fun setUp() = LimitlessRateLimiter.reset()

    @After
    fun tearDown() = LimitlessRateLimiter.reset()

    @Test
    fun `a finestra vuota le richieste passano`() {
        assertTrue(LimitlessRateLimiter.tryAcquire())
        assertEquals(0L, LimitlessRateLimiter.retryAfterSeconds())
    }

    @Test
    fun `la finestra si chiude prima della quota, lasciando la riserva`() {
        // 50 di quota meno 6 di riserva: la 45esima e' l'ultima che passa.
        repeat(44) { index ->
            assertTrue("la richiesta ${index + 1} doveva passare", LimitlessRateLimiter.tryAcquire())
        }

        assertFalse(LimitlessRateLimiter.tryAcquire())
        assertTrue(LimitlessRateLimiter.retryAfterSeconds() > 0)
    }

    @Test
    fun `availableNow scende man mano che si spendono richieste`() {
        val start = LimitlessRateLimiter.availableNow()
        assertEquals(44, start)

        repeat(10) { LimitlessRateLimiter.tryAcquire() }

        assertEquals(34, LimitlessRateLimiter.availableNow())
    }

    @Test
    fun `il contatore del server ha la meglio quando e' piu' prudente`() {
        // Il server dice che ne restano 8: la nostra finestra locale sarebbe
        // ancora quasi intatta, ma vale il numero piu' basso.
        LimitlessRateLimiter.onHeaders("\"50-in-5min\"; r=8; t=300")

        assertEquals(2, LimitlessRateLimiter.availableNow())
    }

    @Test
    fun `sotto la riserva il server chiude la finestra`() {
        LimitlessRateLimiter.onHeaders("\"50-in-5min\"; r=5; t=240")

        assertFalse(LimitlessRateLimiter.tryAcquire())
        assertEquals(0, LimitlessRateLimiter.availableNow())
    }

    @Test
    fun `un header senza i campi attesi non cambia niente`() {
        LimitlessRateLimiter.onHeaders("qualcosa-che-non-conosciamo")

        assertEquals(44, LimitlessRateLimiter.availableNow())
        assertTrue(LimitlessRateLimiter.tryAcquire())
    }

    @Test
    fun `un header nullo non cambia niente`() {
        LimitlessRateLimiter.onHeaders(null)

        assertTrue(LimitlessRateLimiter.tryAcquire())
    }

    @Test
    fun `dopo un 429 tutto si ferma per il tempo indicato`() {
        LimitlessRateLimiter.onTooManyRequests(retryAfterSeconds = 120)

        assertFalse(LimitlessRateLimiter.tryAcquire())
        assertEquals(0, LimitlessRateLimiter.availableNow())

        val wait = LimitlessRateLimiter.retryAfterSeconds()
        assertTrue("attesa attesa ~120s, trovata ${wait}s", wait in 119..122)
    }

    @Test
    fun `un 429 senza Retry-After usa la finestra dichiarata dal server`() {
        LimitlessRateLimiter.onHeaders("\"50-in-5min\"; r=0; t=90")
        LimitlessRateLimiter.onTooManyRequests(retryAfterSeconds = null)

        val wait = LimitlessRateLimiter.retryAfterSeconds()
        assertTrue("attesa attesa ~90s, trovata ${wait}s", wait in 88..93)
    }

    @Test
    fun `un 429 senza informazioni aspetta l'intera finestra`() {
        LimitlessRateLimiter.onTooManyRequests(retryAfterSeconds = null)

        val wait = LimitlessRateLimiter.retryAfterSeconds()
        assertTrue("attesa attesa ~300s, trovata ${wait}s", wait in 299..302)
    }

    @Test
    fun `il contatore del server scaduto viene ignorato`() {
        // t=0: la finestra a cui si riferisce r=1 e' gia' finita, quindi quel
        // numero non descrive piu' niente e non deve bloccare le richieste.
        LimitlessRateLimiter.onHeaders("\"50-in-5min\"; r=1; t=0")

        assertTrue(LimitlessRateLimiter.tryAcquire())
    }

    @Test
    fun `due 429 di seguito non accorciano l'attesa`() {
        LimitlessRateLimiter.onTooManyRequests(retryAfterSeconds = 200)
        LimitlessRateLimiter.onTooManyRequests(retryAfterSeconds = 10)

        val wait = LimitlessRateLimiter.retryAfterSeconds()
        assertTrue("l'attesa doveva restare ~200s, trovata ${wait}s", wait in 199..202)
    }
}
