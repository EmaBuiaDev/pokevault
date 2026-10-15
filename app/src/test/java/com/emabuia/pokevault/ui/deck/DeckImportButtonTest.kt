package com.emabuia.pokevault.ui.deck

import android.app.Application
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.emabuia.pokevault.ui.theme.PokeVaultTheme
import com.emabuia.pokevault.util.AppLocale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Il tasto che apre l'import nel Deck Lab.
 *
 * Era solo un'icona a freccia in giu', che si leggeva come "scarica": ora porta
 * anche la scritta. M3 toglie la scritta dell'ExtendedFAB dall'accessibilita'
 * e legge solo la descrizione dell'icona: se quella e' null, TalkBack trova un
 * pulsante muto. Per questo i test cercano il tasto per descrizione.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DeckImportButtonTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(darkTheme: Boolean, onClick: () -> Unit = {}) {
        compose.setContent {
            PokeVaultTheme(darkTheme = darkTheme) {
                DeckImportButton(onClick = onClick)
            }
        }
    }

    @Test
    fun testTalkBackSenteImportaDeckColTemaChiaro() {
        show(darkTheme = false)
        compose.onNodeWithContentDescription(AppLocale.importDeck)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun testTalkBackSenteImportaDeckColTemaScuro() {
        show(darkTheme = true)
        compose.onNodeWithContentDescription(AppLocale.importDeck)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun testLaScrittaImportaCe() {
        show(darkTheme = false)
        compose.onNodeWithText(AppLocale.import, useUnmergedTree = true).assertExists()
    }

    @Test
    fun testIlToccoApreLImport() {
        var clicks = 0
        show(darkTheme = false) { clicks++ }
        compose.onNodeWithContentDescription(AppLocale.importDeck).performClick()
        assertEquals(1, clicks)
    }
}
