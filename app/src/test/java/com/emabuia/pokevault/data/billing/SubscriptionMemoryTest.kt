package com.emabuia.pokevault.data.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** L'abbonamento senza rete: quando vale la copia salvata e quando no. */
class SubscriptionMemoryTest {

    private val now = 1_760_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun remembered(entitled: Boolean = true, uid: String = "u1", ageMs: Long = day) =
        RememberedSubscription(uid, entitled, now - ageMs)

    @Test
    fun `il server decide quando risponde, anche contro Google Play e la copia`() {
        assertFalse(SubscriptionMemory.isSubscribed(false, true, remembered(), "u1", now))
        assertTrue(SubscriptionMemory.isSubscribed(true, false, remembered(entitled = false), "u1", now))
    }

    @Test
    fun `senza server decide Google Play`() {
        assertTrue(SubscriptionMemory.isSubscribed(null, true, null, "u1", now))
        assertFalse(SubscriptionMemory.isSubscribed(null, false, remembered(), "u1", now))
    }

    @Test
    fun `offline, chi aveva l'abbonamento lo tiene per qualche giorno`() {
        assertTrue(SubscriptionMemory.isSubscribed(null, null, remembered(ageMs = 6 * day), "u1", now))
    }

    @Test
    fun `dopo la settimana di grazia la copia non vale piu`() {
        assertFalse(SubscriptionMemory.isSubscribed(null, null, remembered(ageMs = 8 * day), "u1", now))
    }

    @Test
    fun `la copia di un altro account non vale`() {
        assertFalse(SubscriptionMemory.isSubscribed(null, null, remembered(uid = "altro"), "u1", now))
        assertFalse(SubscriptionMemory.isSubscribed(null, null, remembered(), "", now))
    }

    @Test
    fun `una copia che dice no resta no`() {
        assertFalse(SubscriptionMemory.isSubscribed(null, null, remembered(entitled = false), "u1", now))
    }

    @Test
    fun `una data nel futuro, orologio spostato, non allunga la grazia`() {
        assertFalse(SubscriptionMemory.isSubscribed(null, null, remembered(ageMs = -2 * day), "u1", now))
    }

    @Test
    fun `senza copia e senza risposte non c'e' abbonamento`() {
        assertFalse(SubscriptionMemory.isSubscribed(null, null, null, "u1", now))
    }
}
