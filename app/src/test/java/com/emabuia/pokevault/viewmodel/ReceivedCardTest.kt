package com.emabuia.pokevault.viewmodel

import com.emabuia.pokevault.data.catalog.CatalogLang
import com.emabuia.pokevault.data.remote.TcgCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** TradeRadar: con quale carta entra in collezione una copia ricevuta. */
class ReceivedCardTest {

    private val it = TcgCard(id = "ita:sv08:25")
    private val en = TcgCard(id = "eng:sv08:25")

    @Test
    fun `la lingua della copia, con o senza bandiera`() {
        assertEquals(CatalogLang.EN, receivedCardLanguage("🇬🇧 English"))
        assertEquals(CatalogLang.EN, receivedCardLanguage("English"))
        assertEquals(CatalogLang.IT, receivedCardLanguage("🇮🇹 Italiano"))
        assertNull(receivedCardLanguage("🇯🇵 Giapponese"))
        assertNull(receivedCardLanguage(null))
    }

    @Test
    fun `una copia inglese entra come carta inglese, una italiana come italiana`() {
        assertEquals(en, pickReceivedCard(CatalogLang.EN, it, en))
        assertEquals(it, pickReceivedCard(CatalogLang.IT, it, en))
        assertEquals(it, pickReceivedCard(null, it, en))
    }

    @Test
    fun `se manca la carta della sua lingua si prende l'altra`() {
        // Set solo inglese: non c'e' la carta italiana.
        assertEquals(en, pickReceivedCard(CatalogLang.IT, null, en))
        // Inglese spento: c'e' solo l'italiana.
        assertEquals(it, pickReceivedCard(CatalogLang.EN, it, null))
        assertNull(pickReceivedCard(CatalogLang.EN, null, null))
    }
}
