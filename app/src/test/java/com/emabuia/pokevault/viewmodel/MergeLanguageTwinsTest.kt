package com.emabuia.pokevault.viewmodel

import com.emabuia.pokevault.data.catalog.CatalogLang
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgCardSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Con "Tutte" la carta italiana e la gemella inglese diventano una tessera sola. */
class MergeLanguageTwinsTest {

    private fun card(id: String, setId: String) = TcgCard(id = id, name = id, set = TcgCardSet(id = setId))

    private val itPika = card("ita:sv08:57", "sv08__ita")
    private val enPika = card("eng:sv08:57", "sv08__eng")
    private val itRaichu = card("ita:sv08:58", "sv08__ita")
    private val enOnly = card("eng:cel25:5", "cel25__eng")

    @Test
    fun `le gemelle diventano una tessera, al posto della prima, nella lingua dell'app`() {
        val it = mergeLanguageTwins(listOf(itPika, itRaichu, enPika, enOnly), CatalogLang.IT)
        assertEquals(listOf(itPika, itRaichu, enOnly), it.cards)
        assertEquals(enPika, it.twinOf[itPika.id])

        val en = mergeLanguageTwins(listOf(itPika, itRaichu, enPika, enOnly), CatalogLang.EN)
        assertEquals(listOf(enPika, itRaichu, enOnly), en.cards)
        assertEquals(itPika, en.twinOf[enPika.id])
    }

    @Test
    fun `una carta che esiste in una lingua sola resta com'e', senza gemella`() {
        val merged = mergeLanguageTwins(listOf(itRaichu, enOnly), CatalogLang.IT)
        assertEquals(listOf(itRaichu, enOnly), merged.cards)
        assertNull(merged.twinOf[itRaichu.id])
        assertNull(merged.twinOf[enOnly.id])
    }

    @Test
    fun `stesso numero scritto diverso, stessa carta - set diversi, carte diverse`() {
        val itZero = card("ita:sv08:057", "sv08__ita")
        assertEquals(1, mergeLanguageTwins(listOf(itZero, enPika), CatalogLang.IT).cards.size)
        val otherSet = card("eng:sv07:57", "sv07__eng")
        assertEquals(2, mergeLanguageTwins(listOf(itPika, otherSet), CatalogLang.IT).cards.size)
    }

    @Test
    fun `i promo a lettere si accoppiano, le carte fuori catalogo non si toccano`() {
        val itPromo = card("ita:swshp:SWSH026", "swshp__ita")
        val enPromo = card("eng:swshp:swsh026", "swshp__eng")
        assertEquals(listOf(itPromo), mergeLanguageTwins(listOf(itPromo, enPromo), CatalogLang.IT).cards)

        val legacy = card("sv8-57", "sv8")
        val legacyTwo = card("sv8-57", "sv8")
        assertEquals(2, mergeLanguageTwins(listOf(legacy, legacyTwo), CatalogLang.IT).cards.size)
    }

    @Test
    fun `senza gemelle la lista resta identica, nello stesso ordine`() {
        val list = listOf(itRaichu, itPika, enOnly)
        assertEquals(list, mergeLanguageTwins(list, CatalogLang.IT).cards)
    }
}
