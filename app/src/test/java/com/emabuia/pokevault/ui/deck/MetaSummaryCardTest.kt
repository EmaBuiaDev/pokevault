package com.emabuia.pokevault.ui.deck

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.emabuia.pokevault.data.model.MetaArchetype
import com.emabuia.pokevault.ui.theme.PokeVaultTheme
import com.emabuia.pokevault.util.AppLocale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "I più giocati" in cima al Meta Deck: aperta prendeva troppo spazio prima
 * della classifica, quindi parte chiusa, e deve dire che si apre.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MetaSummaryCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val archetypes = listOf(
        MetaArchetype(
            name = "Dragapult ex", count = 4, metaShare = 40.0, avgWinrate = 0.6, topPlacement = 1,
            recentResults = emptyList(), sampleDeck = null, sampleTournaments = 12, sampleDecks = 10
        ),
        MetaArchetype(
            name = "Gardevoir ex", count = 6, metaShare = 60.0, avgWinrate = 0.5, topPlacement = 2,
            recentResults = emptyList(), sampleDeck = null, sampleTournaments = 12, sampleDecks = 10
        )
    )

    private fun show() {
        compose.setContent {
            PokeVaultTheme(darkTheme = false) {
                MetaSummaryCard(archetypes = archetypes)
            }
        }
    }

    @Test
    fun `parte chiusa e dice che si puo' aprire`() {
        show()

        compose.onNodeWithText(AppLocale.metaTopEightTitle, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(AppLocale.metaShowBreakdown, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Dragapult ex", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `un tocco la apre e un altro la richiude`() {
        show()

        compose.onNodeWithText(AppLocale.metaShowBreakdown, useUnmergedTree = true).performClick()
        compose.onNodeWithText("Dragapult ex", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(AppLocale.metaHideBreakdown, useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithText(AppLocale.metaHideBreakdown, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Dragapult ex", useUnmergedTree = true).assertDoesNotExist()
    }
}
