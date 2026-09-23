package com.emabuia.pokevault.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quali stampe di una carta offriamo quando si tocca "aggiungi".
 *
 * Le carte italiane non hanno prezzi TCGplayer, quindi passano tutte dal
 * fallback sulla rarita': se quello sbaglia, il quick add propone stampe che
 * non esistono -- era il caso di ogni Rara, che si prendeva Normale, Reverse
 * *e* Holo.
 */
class CardVariantsTest {

    private fun variants(rarity: String?, date: String? = "2024-01-01") =
        CardOptions.getVariantsForCard(emptySet(), rarity, date)

    @Test
    fun `una rara non holo non propone anche la holo`() {
        // La versione olografica di una carta e' un'altra rarita' ("Rare
        // Holo"), non una variante di questa.
        assertEquals(listOf("Normal", "Reverse"), variants("Rare"))
        assertFalse("Holo" in variants("Rare"))
    }

    @Test
    fun `le rarita' moderne escono in una stampa sola`() {
        for (rarity in listOf(
            "Ultra Rare", "Illustration rare", "Special illustration rare",
            "Hyper rare", "Secret Rare", "Double rare", "Shiny rare",
            "ACE SPEC Rare", "Promo", "Classic Collection", "Pikachu Rare",
            "Futuristic rare", "Radiant Rare", "LEGEND", "Rare Holo LV.X"
        )) {
            assertEquals("stampe sbagliate per $rarity", listOf("Holo"), variants(rarity))
        }
    }

    @Test
    fun `le due forme di holo rare sono trattate allo stesso modo`() {
        // Il catalogo porta tutte e due: "Rare Holo" dall'era DP e "Holo Rare"
        // da TCGdex. Prima solo la prima veniva riconosciuta, e la seconda
        // finiva nel ramo generico.
        assertEquals(variants("Rare Holo"), variants("Holo Rare"))
        assertEquals(listOf("Holo", "Reverse"), variants("Holo Rare"))
    }

    @Test
    fun `comuni e non comuni escono normali e reverse`() {
        assertEquals(listOf("Normal", "Reverse"), variants("Common"))
        assertEquals(listOf("Normal", "Reverse"), variants("Uncommon"))
    }

    @Test
    fun `sui set dove il reverse non esisteva non viene proposto`() {
        // Il reverse holo debutta nel maggio 2002: sulle carte del Set Base
        // (1999) e di Neo quella stampa non e' mai stata fatta.
        assertEquals(listOf("Normal"), variants("Common", "1999-01-09"))
        assertEquals(listOf("Normal"), variants("Rare", "2000-12-16"))
        assertEquals(listOf("Holo"), variants("Rare Holo", "1999-01-09"))

        // Expedition (settembre 2002) il reverse ce l'ha.
        assertEquals(listOf("Normal", "Reverse"), variants("Common", "2002-09-15"))
    }

    @Test
    fun `senza data del set si resta larghi invece che togliere stampe`() {
        assertEquals(listOf("Normal", "Reverse"), variants("Common", null))
        assertEquals(listOf("Normal", "Reverse"), variants("Common", ""))
    }

    @Test
    fun `una rarita' sconosciuta non si inventa tre stampe`() {
        assertEquals(listOf("Holo"), variants("Mythical Rare"))
        assertEquals(listOf("Holo"), variants(null))
    }

    @Test
    fun `i prezzi veri hanno la precedenza sulla rarita'`() {
        // Quando TCGplayer elenca le stampe, quelle sono: la rarita' non
        // c'entra piu'.
        val fromApi = CardOptions.getVariantsForCard(setOf("normal", "reverseHolofoil"), "Ultra Rare")
        assertTrue("Normal" in fromApi)
        assertTrue("Reverse" in fromApi)
        assertEquals(2, fromApi.size)
    }

    /**
     * Il prezzo in dollari delle carte italiane viaggia dentro la mappa dei
     * prezzi TCGplayer, che e' lo stesso posto da cui questa funzione ricava
     * le stampe esistenti. Stava sotto la chiave "normal", che e' anche il
     * nome di una stampa: bastava quello per far credere alla funzione di
     * avere davanti un listino vero, e da li' in poi la rarita' non veniva
     * nemmeno guardata. Su un set di sole Holo -- il 30 Anniversario -- si
     * finiva a proporre "Normale", e a salvarla cosi'.
     */
    @Test
    fun `il prezzo in dollari non vale come elenco delle stampe`() {
        val soloDollari = setOf(CardOptions.USD_ONLY_PRICE_KEY)

        // Si ricade sulla rarita', che e' l'unica fonte vera per le italiane.
        assertEquals(listOf("Holo"), CardOptions.getVariantsForCard(soloDollari, "Ultra Rare", "2024-01-01"))
        assertEquals(listOf("Holo"), CardOptions.getVariantsForCard(soloDollari, "Classic Collection", "2024-01-01"))
        assertEquals(listOf("Normal", "Reverse"), CardOptions.getVariantsForCard(soloDollari, "Common", "2024-01-01"))

        // La forma del difetto, per confronto: con la vecchia chiave "normal"
        // la stessa carta si prendeva la sola stampa normale, che su una Ultra
        // Rara non esiste nemmeno.
        assertEquals(listOf("Normal"), CardOptions.getVariantsForCard(setOf("normal"), "Ultra Rare", "2024-01-01"))

        // E se accanto al prezzo finto c'e' anche un listino vero, quello vale.
        val misto = setOf(CardOptions.USD_ONLY_PRICE_KEY, "holofoil", "reverseHolofoil")
        val stampe = CardOptions.getVariantsForCard(misto, "Common")
        assertEquals(setOf("Holo", "Reverse"), stampe.toSet())
    }
}
