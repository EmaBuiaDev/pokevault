package com.emabuia.pokevault.ui.home

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.emabuia.pokevault.ui.home.components.WelcomeHeader
import com.emabuia.pokevault.ui.theme.DarkPokeVaultColors
import com.emabuia.pokevault.ui.theme.LightPokeVaultColors
import com.emabuia.pokevault.ui.theme.PokeVaultTheme
import com.emabuia.pokevault.util.AppLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Il saluto in cima alla Home.
 *
 * Il nome dell'utente era bianco fisso: col tema chiaro stava su uno sfondo
 * quasi bianco e non si leggeva. Ora segue il colore del testo del tema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WelcomeHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun greetingColor(darkTheme: Boolean): Color {
        compose.setContent {
            PokeVaultTheme(darkTheme = darkTheme) {
                WelcomeHeader(userName = "Ash")
            }
        }
        val node = compose.onNodeWithText(AppLocale.helloUser("Ash")).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first().layoutInput.style.color
    }

    @Test
    fun `col tema chiaro il nome usa il testo scuro del tema`() {
        val color = greetingColor(darkTheme = false)
        assertEquals(LightPokeVaultColors.textPrimary, color)
        assertNotEquals(Color.White, color)
    }

    @Test
    fun `col tema scuro resta bianco come prima`() {
        assertEquals(DarkPokeVaultColors.textPrimary, greetingColor(darkTheme = true))
        assertEquals(Color.White, DarkPokeVaultColors.textPrimary)
    }
}
