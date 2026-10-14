package com.emabuia.pokevault.viewmodel

import com.emabuia.pokevault.data.remote.TcgSet
import com.emabuia.pokevault.ui.album.goalSetLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * I set fra cui scegliere un chase: solo i nostri cataloghi. Con i set
 * PokeWallet il chase non contava le copie italiane (id diversi) e ogni
 * apertura costava chiamate.
 */
class GoalSetChoicesTest {

    private val itSv08 = TcgSet(id = "sv08__ita", name = "Scintille Folgoranti")
    private val pokeWallet = TcgSet(id = "sv8", name = "Surging Sparks", language = "ENG")
    private val japanese = TcgSet(id = "sv8jp", name = "Super Electric Breaker", language = "JAP")
    private val enSv08 = TcgSet(id = "sv08__eng", name = "Surging Sparks", language = "ENG")

    @Test
    fun `restano solo i set italiani e inglesi del catalogo, prima gli italiani`() {
        assertEquals(listOf(itSv08, enSv08), goalSetChoices(listOf(pokeWallet, itSv08, japanese), listOf(enSv08)))
    }

    @Test
    fun `dove l'inglese e' spento restano gli italiani`() {
        assertEquals(listOf(itSv08), goalSetChoices(listOf(pokeWallet, itSv08), emptyList()))
    }

    @Test
    fun `la bandiera compare solo quando ci sono due lingue`() {
        assertEquals("Scintille Folgoranti", goalSetLabel(itSv08, showFlags = false))
        assertEquals("🇮🇹 Scintille Folgoranti", goalSetLabel(itSv08, showFlags = true))
        assertEquals("🇬🇧 Surging Sparks", goalSetLabel(enSv08, showFlags = true))
    }
}
