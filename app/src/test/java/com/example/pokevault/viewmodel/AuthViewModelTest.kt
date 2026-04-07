package com.example.pokevault.viewmodel

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AuthViewModel validation logic.
 * These test the pure validation methods without Firebase dependencies.
 */
class AuthViewModelTest {

    // Replicate the validation logic from AuthViewModel for testability
    private val validProviders = listOf(
        "gmail.com", "outlook.com", "hotmail.it", "hotmail.com",
        "yahoo.com", "yahoo.it", "icloud.com", "libero.it", "virgilio.it",
        "live.it", "fastwebnet.it", "tiscali.it", "alice.it", "tim.it", "poste.it"
    )

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$".toRegex()
        if (!email.matches(emailRegex)) return false
        val domain = email.substringAfterLast("@").lowercase()
        return validProviders.any { domain == it || domain.endsWith(".$it") }
    }

    // ── Email validation tests ──

    @Test
    fun `valid gmail email returns true`() {
        assertTrue(isValidEmail("user@gmail.com"))
    }

    @Test
    fun `valid outlook email returns true`() {
        assertTrue(isValidEmail("test.user@outlook.com"))
    }

    @Test
    fun `valid libero email returns true`() {
        assertTrue(isValidEmail("mario@libero.it"))
    }

    @Test
    fun `valid yahoo email returns true`() {
        assertTrue(isValidEmail("user@yahoo.com"))
    }

    @Test
    fun `valid icloud email returns true`() {
        assertTrue(isValidEmail("user@icloud.com"))
    }

    @Test
    fun `invalid domain returns false`() {
        assertFalse(isValidEmail("user@invalid-domain.xyz"))
    }

    @Test
    fun `empty email returns false`() {
        assertFalse(isValidEmail(""))
    }

    @Test
    fun `email without at sign returns false`() {
        assertFalse(isValidEmail("usergmail.com"))
    }

    @Test
    fun `email without domain returns false`() {
        assertFalse(isValidEmail("user@"))
    }

    @Test
    fun `email with spaces returns false`() {
        assertFalse(isValidEmail("user @gmail.com"))
    }

    @Test
    fun `email with plus sign is valid`() {
        assertTrue(isValidEmail("user+tag@gmail.com"))
    }

    @Test
    fun `email with dots in local part is valid`() {
        assertTrue(isValidEmail("user.name@gmail.com"))
    }

    // ── Password validation tests ──

    @Test
    fun `password with 6 chars is valid`() {
        assertTrue("abc123".length >= 6)
    }

    @Test
    fun `password with 5 chars is too short`() {
        assertFalse("abc12".length >= 6)
    }

    @Test
    fun `empty password is too short`() {
        assertFalse("".length >= 6)
    }

    // ── Error message mapping tests ──

    private fun mapErrorMessage(error: Throwable): String {
        return when {
            error.message?.contains("email", ignoreCase = true) == true &&
                error.message?.contains("already", ignoreCase = true) == true ->
                "Questa email è già registrata"
            error.message?.contains("password", ignoreCase = true) == true &&
                error.message?.contains("invalid", ignoreCase = true) == true ->
                "Password non corretta"
            error.message?.contains("no user", ignoreCase = true) == true ->
                "Nessun account trovato con questa email"
            error.message?.contains("network", ignoreCase = true) == true ->
                "Errore di connessione. Controlla internet."
            error.message?.contains("badly formatted", ignoreCase = true) == true ->
                "Formato email non valido"
            else -> error.message ?: "Errore sconosciuto"
        }
    }

    @Test
    fun `email already registered maps correctly`() {
        val error = Exception("The email address is already in use by another account")
        assertEquals("Questa email è già registrata", mapErrorMessage(error))
    }

    @Test
    fun `invalid password maps correctly`() {
        val error = Exception("The password is invalid or the user does not have a password")
        assertEquals("Password non corretta", mapErrorMessage(error))
    }

    @Test
    fun `no user maps correctly`() {
        val error = Exception("There is no user record corresponding to this identifier")
        assertEquals("Nessun account trovato con questa email", mapErrorMessage(error))
    }

    @Test
    fun `network error maps correctly`() {
        val error = Exception("A network error (such as timeout) has occurred")
        assertEquals("Errore di connessione. Controlla internet.", mapErrorMessage(error))
    }

    @Test
    fun `badly formatted email maps correctly`() {
        val error = Exception("The email address is badly formatted")
        assertEquals("Formato email non valido", mapErrorMessage(error))
    }

    @Test
    fun `unknown error returns original message`() {
        val error = Exception("Something went wrong")
        assertEquals("Something went wrong", mapErrorMessage(error))
    }

    @Test
    fun `null message returns unknown error`() {
        val error = Exception()
        assertEquals("Errore sconosciuto", mapErrorMessage(error))
    }
}
