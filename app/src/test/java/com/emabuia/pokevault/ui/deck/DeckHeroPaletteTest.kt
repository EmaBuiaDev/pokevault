package com.emabuia.pokevault.ui.deck

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.emabuia.pokevault.ui.theme.DarkPokeVaultColors
import com.emabuia.pokevault.ui.theme.LightPokeVaultColors
import org.junit.Assert.*
import org.junit.Test

/**
 * I colori della card di un mazzo e della testata del dettaglio.
 *
 * Fino alla 3.1.8 erano un blu notte e veli neri fissi, con il nome in bianco,
 * anche col tema chiaro: il mazzo era un blocco scuro in una pagina chiara.
 * Il tema scuro invece andava bene, e questi test lo tengono com'era.
 */
class DeckHeroPaletteTest {

    private val fire = Color(0xFFC62828)

    private val light = deckHeroPalette(LightPokeVaultColors, fire, accentAlpha = 0.30f)
    private val dark = deckHeroPalette(DarkPokeVaultColors, fire, accentAlpha = 0.30f)

    /** Rapporto di contrasto WCAG fra due colori opachi. */
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }

    @Test
    fun testSulChiaroIlFondoPartedalBiancoDellaCard() {
        assertEquals(LightPokeVaultColors.card, light.background.first())
        light.background.drop(1).forEach {
            assertTrue("la velatura del tipo deve restare leggera, alpha ${it.alpha}", it.alpha <= 0.2f)
        }
    }

    @Test
    fun testSulChiaroNienteVeliScuri() {
        assertNull("il velo scuro in basso sporcava la card di grigio", light.bottomShade)
        assertEquals(
            "la testata non deve avere la sfumatura scura in alto",
            0f, light.headerShade.first().alpha, 0f
        )
        light.infoBox.forEach {
            assertTrue("il riquadro del nome deve essere chiaro", it.luminance() > 0.9f)
        }
    }

    @Test
    fun testSulChiaroLeScritteSonoScureELeggibili() {
        assertEquals(LightPokeVaultColors.textPrimary, light.title)
        assertNotEquals(Color.White, light.title)
        assertTrue(contrast(light.title, LightPokeVaultColors.surface) >= 4.5f)
        assertTrue(contrast(light.onButton, LightPokeVaultColors.surface) >= 4.5f)
        assertTrue("i pulsanti tondi devono essere chiari", light.button.luminance() > 0.9f)
    }

    @Test
    fun testSulChiaroBinarioEDivisoreSiVedono() {
        // Erano bianchi semitrasparenti: sul bianco sparivano.
        assertNotEquals(Color.White, light.track.copy(alpha = 1f))
        assertNotEquals(Color.White, light.divider.copy(alpha = 1f))
    }

    @Test
    fun testIlTemaScuroRestaComEra() {
        assertEquals(
            listOf(Color(0xFF10151F), Color(0xFF1C2D44), fire.copy(alpha = 0.30f)),
            dark.background
        )
        assertEquals(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.62f)),
            dark.bottomShade
        )
        assertEquals(
            listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, DarkPokeVaultColors.background),
            dark.headerShade
        )
        assertEquals(listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.62f)), dark.infoBox)
        assertEquals(Color.White.copy(alpha = 0.12f), dark.infoBorder)
        assertEquals(Color.White, dark.title)
        assertEquals(Color.White.copy(alpha = 0.16f), dark.track)
        assertEquals(Color.Black.copy(alpha = 0.5f), dark.button)
        assertEquals(Color.White, dark.onButton)
        assertEquals(Color.White.copy(alpha = 0.1f), dark.divider)
    }

    @Test
    fun testLaTestataUsaLaSuaIntensita() {
        val header = deckHeroPalette(DarkPokeVaultColors, fire, accentAlpha = 0.32f)
        assertEquals(fire.copy(alpha = 0.32f), header.background.last())
    }
}
