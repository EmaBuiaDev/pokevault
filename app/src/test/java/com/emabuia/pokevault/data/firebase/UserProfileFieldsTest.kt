package com.emabuia.pokevault.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileFieldsTest {

    private val created = 1_791_000_000_000L

    @Test
    fun `documento che non esiste - profilo completo`() {
        val out = UserProfileFields.missing(null, "Ash", "ash@gmail.com", created)
        assertEquals(mapOf("name" to "Ash", "email" to "ash@gmail.com", "createdAt" to created, "totalCards" to 0), out)
    }

    @Test
    fun `documento creato dalla sync di isPremium - si completa il profilo`() {
        // Il caso visto in produzione l'08/10: solo isPremium, lastSeen, appVersion.
        val existing = mapOf<String, Any?>("isPremium" to false, "lastSeen" to "ts", "appVersion" to "3.1.6")
        val out = UserProfileFields.missing(existing, "Misty", "misty@gmail.com", created)
        assertEquals(setOf("name", "email", "createdAt", "totalCards"), out.keys)
        assertEquals("misty@gmail.com", out["email"])
    }

    @Test
    fun `profilo gia' completo - niente da scrivere`() {
        val existing = mapOf<String, Any?>("name" to "Brock", "email" to "b@x.it", "createdAt" to "ts", "totalCards" to 42L)
        assertTrue(UserProfileFields.missing(existing, "Altro Nome", "altra@x.it", created).isEmpty())
    }

    @Test
    fun `totalCards e nome esistenti non si toccano`() {
        val existing = mapOf<String, Any?>("name" to "Scelto da me", "totalCards" to 120L)
        val out = UserProfileFields.missing(existing, "Nome Google", "g@gmail.com", created)
        assertEquals(setOf("email", "createdAt"), out.keys)
    }

    @Test
    fun `email vuota si ripara, senza email dall'account non si scrive`() {
        val existing = mapOf<String, Any?>("name" to "A", "email" to "", "createdAt" to "ts", "totalCards" to 0L)
        assertEquals(mapOf("email" to "a@x.it"), UserProfileFields.missing(existing, "A", "a@x.it", created))
        assertTrue(UserProfileFields.missing(existing, "A", null, created).isEmpty())
    }

    @Test
    fun `senza nome nell'account - Allenatore`() {
        val out = UserProfileFields.missing(null, " ", "a@x.it", created)
        assertEquals(UserProfileFields.DEFAULT_NAME, out["name"])
    }
}
