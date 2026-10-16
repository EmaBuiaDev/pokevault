package com.emabuia.pokevault.util

import android.app.Application
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WhatsNewTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        app.getSharedPreferences("pokevault_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        AppLocale.setLanguage(AppLocale.Language.IT, app)
    }

    private fun installTimes(first: Long, lastUpdate: Long) {
        val info = shadowOf(app.packageManager).getInternalMutablePackageInfo(app.packageName)
        info.firstInstallTime = first
        info.lastUpdateTime = lastUpdate
    }

    // ── Regola ──

    @Test
    fun `chi aggiorna da una versione precedente lo vede`() {
        assertTrue(WhatsNew.shouldShow(lastSeenVersion = 0, currentVersion = 41, freshInstall = false, hasNotes = true))
        assertTrue(WhatsNew.shouldShow(lastSeenVersion = 41, currentVersion = 42, freshInstall = false, hasNotes = true))
    }

    @Test
    fun `non si vede su installazione nuova, versione gia' vista o senza note`() {
        assertFalse(WhatsNew.shouldShow(lastSeenVersion = 0, currentVersion = 41, freshInstall = true, hasNotes = true))
        assertFalse(WhatsNew.shouldShow(lastSeenVersion = 41, currentVersion = 41, freshInstall = false, hasNotes = true))
        assertFalse(WhatsNew.shouldShow(lastSeenVersion = 0, currentVersion = 42, freshInstall = false, hasNotes = false))
    }

    // ── Sul telefono ──

    @Test
    fun `dopo l'aggiornamento compare una volta sola`() {
        installTimes(first = 1_000L, lastUpdate = 2_000L)
        assertTrue(WhatsNew.consumeOnLaunch(app, 41))
        assertFalse(WhatsNew.consumeOnLaunch(app, 41))
    }

    @Test
    fun `installazione nuova non lo mostra, ma lo mostra all'aggiornamento dopo`() {
        installTimes(first = 1_000L, lastUpdate = 1_000L)
        assertFalse(WhatsNew.consumeOnLaunch(app, 41))
        installTimes(first = 1_000L, lastUpdate = 5_000L)
        // 44 non ha note: niente pop-up vuoto, ma la versione resta segnata.
        assertFalse(WhatsNew.consumeOnLaunch(app, 44))
        assertEquals(44, app.getSharedPreferences("pokevault_prefs", Context.MODE_PRIVATE).getInt("whats_new_seen_version", 0))
    }

    // ── Note ──

    @Test
    fun `le note della 3_1_8 ci sono in italiano e in inglese`() {
        for (lang in AppLocale.Language.values()) {
            AppLocale.setLanguage(lang, app)
            val notes = WhatsNew.notesFor(41)
            assertEquals(4, notes.size)
            assertTrue(notes.first().title.contains("PoketCards"))
            assertTrue(notes.all { it.title.isNotBlank() && it.body.isNotBlank() })
            assertTrue(WhatsNew.title("3.1.8").endsWith("3.1.8"))
        }
    }

    @Test
    fun `le versioni senza note non hanno pop-up`() {
        assertTrue(WhatsNew.notesFor(40).isEmpty())
        assertTrue(WhatsNew.notesFor(44).isEmpty())
    }

    @Test
    fun `le note della 3_1_9 ci sono in italiano e in inglese`() {
        for (lang in AppLocale.Language.values()) {
            AppLocale.setLanguage(lang, app)
            val notes = WhatsNew.notesFor(42)
            assertEquals(4, notes.size)
            assertEquals(
                listOf(WhatsNew.Icon.ALBUM, WhatsNew.Icon.HAND, WhatsNew.Icon.RATING, WhatsNew.Icon.META),
                notes.map { it.icon }
            )
            assertTrue(notes.all { it.title.isNotBlank() && it.body.isNotBlank() })
        }
    }

    @Test
    fun `chi aggiorna dalla 3_1_8 vede le note della 3_1_9`() {
        installTimes(first = 1_000L, lastUpdate = 2_000L)
        assertTrue(WhatsNew.consumeOnLaunch(app, 41))
        installTimes(first = 1_000L, lastUpdate = 9_000L)
        assertTrue(WhatsNew.consumeOnLaunch(app, 42))
    }

    @Test
    fun `le note della 3_1_10 ci sono in italiano e in inglese`() {
        for (lang in AppLocale.Language.values()) {
            AppLocale.setLanguage(lang, app)
            val notes = WhatsNew.notesFor(43)
            assertEquals(
                listOf(WhatsNew.Icon.IMPORT, WhatsNew.Icon.HAND, WhatsNew.Icon.THEME),
                notes.map { it.icon }
            )
            assertTrue(notes.all { it.title.isNotBlank() && it.body.isNotBlank() })
        }
    }

    @Test
    fun `chi aggiorna dalla 3_1_9 vede le note della 3_1_10`() {
        installTimes(first = 1_000L, lastUpdate = 2_000L)
        assertTrue(WhatsNew.consumeOnLaunch(app, 42))
        installTimes(first = 1_000L, lastUpdate = 9_000L)
        assertTrue(WhatsNew.consumeOnLaunch(app, 43))
    }
}
