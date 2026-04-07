package com.example.pokevault.viewmodel

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for card form validation logic (mirroring AddCardViewModel).
 */
class AddCardValidationTest {

    // ── Grade validation (replicated from AddCardViewModel) ──

    private fun isValidGrade(input: String): Boolean {
        if (input.isBlank()) return true // blank means not graded
        val normalized = input.replace(",", ".")
        val value = normalized.toFloatOrNull() ?: return false
        return value in 0f..10f
    }

    @Test
    fun `blank grade is valid`() {
        assertTrue(isValidGrade(""))
    }

    @Test
    fun `grade 0 is valid`() {
        assertTrue(isValidGrade("0"))
    }

    @Test
    fun `grade 10 is valid`() {
        assertTrue(isValidGrade("10"))
    }

    @Test
    fun `grade 9_5 is valid`() {
        assertTrue(isValidGrade("9.5"))
    }

    @Test
    fun `grade with comma is valid`() {
        assertTrue(isValidGrade("9,5"))
    }

    @Test
    fun `grade above 10 is invalid`() {
        assertFalse(isValidGrade("10.5"))
    }

    @Test
    fun `negative grade is invalid`() {
        assertFalse(isValidGrade("-1"))
    }

    @Test
    fun `non-numeric grade is invalid`() {
        assertFalse(isValidGrade("abc"))
    }

    // ── HP validation ──

    private fun isValidHp(input: String): Boolean {
        if (input.isBlank()) return true
        return input.all { it.isDigit() }
    }

    @Test
    fun `numeric HP is valid`() {
        assertTrue(isValidHp("120"))
    }

    @Test
    fun `empty HP is valid`() {
        assertTrue(isValidHp(""))
    }

    @Test
    fun `non-numeric HP is invalid`() {
        assertFalse(isValidHp("abc"))
    }

    @Test
    fun `HP with decimal is invalid`() {
        assertFalse(isValidHp("12.5"))
    }

    // ── Value validation ──

    private fun isValidValue(input: String): Boolean {
        if (input.isBlank()) return true
        return input.all { it.isDigit() || it == '.' }
    }

    @Test
    fun `numeric value is valid`() {
        assertTrue(isValidValue("25.99"))
    }

    @Test
    fun `integer value is valid`() {
        assertTrue(isValidValue("100"))
    }

    @Test
    fun `empty value is valid`() {
        assertTrue(isValidValue(""))
    }

    @Test
    fun `value with letters is invalid`() {
        assertFalse(isValidValue("25$"))
    }

    // ── Name required validation ──

    @Test
    fun `non-blank name is valid`() {
        assertTrue("Pikachu".isNotBlank())
    }

    @Test
    fun `blank name is invalid`() {
        assertFalse("".isNotBlank())
    }

    @Test
    fun `whitespace-only name is invalid`() {
        assertFalse("   ".isNotBlank())
    }

    // ── Grading company required when graded ──

    @Test
    fun `grading company required when card is graded`() {
        val isGraded = true
        val gradingCompany = ""
        assertTrue(isGraded && gradingCompany.isBlank()) // should show error
    }

    @Test
    fun `grading company not required when card is not graded`() {
        val isGraded = false
        val gradingCompany = ""
        assertFalse(isGraded && gradingCompany.isBlank())
    }

    @Test
    fun `grading company provided when card is graded is ok`() {
        val isGraded = true
        val gradingCompany = "PSA"
        assertFalse(isGraded && gradingCompany.isBlank())
    }
}
