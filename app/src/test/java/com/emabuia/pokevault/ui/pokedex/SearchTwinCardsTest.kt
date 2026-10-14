package com.emabuia.pokevault.ui.pokedex

import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgCardSet
import org.junit.Assert.assertEquals
import org.junit.Test

/** La tessera unica della ricerca "Tutte": bandiere e carta da aggiungere. */
class SearchTwinCardsTest {

    private val it = TcgCard(id = "ita:sv08:57", set = TcgCardSet(id = "sv08__ita"))
    private val en = TcgCard(id = "eng:sv08:57", set = TcgCardSet(id = "sv08__eng"))

    @Test
    fun `bandiere - prima l'italiano, una sola se la carta esiste in una lingua`() {
        assertEquals("🇮🇹🇬🇧", twinFlags(en, it))
        assertEquals("🇮🇹🇬🇧", twinFlags(it, en))
        assertEquals("🇬🇧", twinFlags(en, null))
        assertEquals("🇮🇹", twinFlags(it, null))
    }

    @Test
    fun `scelta l'altra lingua nella scheda si aggiunge la gemella`() {
        assertEquals("🇮🇹 Italiano", copyLanguageOf(it))
        assertEquals("🇬🇧 English", copyLanguageOf(en))
        assertEquals(en, cardForLanguage(it, en, "🇬🇧 English"))
        assertEquals(it, cardForLanguage(it, en, "🇮🇹 Italiano"))
        // Senza gemella, o con una lingua che non e' del catalogo, la carta mostrata.
        assertEquals(it, cardForLanguage(it, null, "🇬🇧 English"))
        assertEquals(it, cardForLanguage(it, en, "🇯🇵 Giapponese"))
    }
}
