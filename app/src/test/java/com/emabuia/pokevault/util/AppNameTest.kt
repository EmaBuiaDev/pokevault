package com.emabuia.pokevault.util

import android.app.Application
import com.emabuia.pokevault.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Il nome visibile dell'app e' PoketCards (prima PokeVault). Gli identificativi
 * tecnici restano "pokevault", ma niente di quello che l'utente legge deve
 * ancora dire PokeVault, in nessuna delle due lingue.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AppNameTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        AppLocale.setLanguage(AppLocale.Language.IT, app)
    }

    @Test
    fun `il nome e' PoketCards`() {
        assertEquals("PoketCards", AppLocale.APP_NAME)
    }

    @Test
    fun `app_name e etichetta del launcher usano il nome nuovo`() {
        // Nello staging app_name e' "PoketCards Staging": basta che inizi col nome.
        val appName = app.getString(R.string.app_name)
        assertTrue(appName, appName.startsWith(AppLocale.APP_NAME))
        // L'etichetta del manifest punta a @string/app_name, non a un testo fisso.
        assertEquals(appName, app.applicationInfo.loadLabel(app.packageManager).toString())
    }

    @Test
    fun `i testi che nominano l'app dicono PoketCards in italiano e in inglese`() {
        for (lang in AppLocale.Language.values()) {
            AppLocale.setLanguage(lang, app)
            val testi = listOf(
                AppLocale.billingClaimedBody,
                AppLocale.tradeRadarNotifyBlocked,
                AppLocale.tradeRadarInviteMessage,
                AppLocale.ratingPromptTagline,
                AppLocale.privacyConsentSummary,
                AppLocale.premiumSubtitle,
                AppLocale.premiumSettingsLabel,
                AppLocale.giftShareMessage("AMICOK7P2QX4M"),
            )
            for (testo in testi) {
                assertTrue("$lang: $testo", testo.contains("PoketCards"))
                // Maiuscole contano: "com.emabuia.pokevault" nel link Play e' il package.
                assertFalse("$lang: $testo", testo.contains("PokeVault"))
            }
        }
    }

    @Test
    fun `l'invito TradeRadar porta ancora alla scheda Play di sempre`() {
        // Il package non cambia: il link deve restare valido.
        AppLocale.setLanguage(AppLocale.Language.IT, app)
        assertTrue(AppLocale.tradeRadarInviteMessage.endsWith("details?id=com.emabuia.pokevault"))
    }
}
