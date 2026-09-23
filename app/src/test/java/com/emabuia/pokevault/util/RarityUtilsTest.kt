package com.emabuia.pokevault.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * getRarityInfo e' una catena di `when` con rami `contains`, quindi l'ordine
 * conta piu' del contenuto: un ramo generico messo troppo in alto si mangia in
 * silenzio le rarita' piu' specifiche, e una stringa che nessun ramo riconosce
 * esce col suo nome e basta, senza che niente fallisca. Questi test fissano
 * l'esito delle rarita' che si somigliano di piu'.
 */
class RarityUtilsTest {

    @Test
    fun `futuristic rare e' riconosciuta e non finisce fra le non mappate`() {
        val info = RarityUtils.getRarityInfo("Futuristic rare")
        assertEquals(14, info.sortOrder)
        assertEquals(RarityShape.SPARKLE, info.symbol.shape)
        assertFalse(info.isUnknown)
    }

    @Test
    fun `futuristic rare regge maiuscole spazi e la forma italiana`() {
        assertEquals(14, RarityUtils.getRarityInfo("FUTURISTIC RARE").sortOrder)
        assertEquals(14, RarityUtils.getRarityInfo("  Futuristic Rare  ").sortOrder)
        assertEquals(14, RarityUtils.getRarityInfo("Rara Futuristica").sortOrder)
    }

    @Test
    fun `futuristic rare e' lucida`() {
        assertTrue(RarityUtils.hasFoilFinish("Futuristic rare"))
    }

    @Test
    fun `futuristic rare non ruba le altre rarita' che contengono rare`() {
        assertEquals(7, RarityUtils.getRarityInfo("Special illustration rare").sortOrder)
        assertEquals(5, RarityUtils.getRarityInfo("Illustration rare").sortOrder)
        assertEquals(6, RarityUtils.getRarityInfo("Ultra Rare").sortOrder)
        assertEquals(3, RarityUtils.getRarityInfo("Double rare").sortOrder)
        assertEquals(13, RarityUtils.getRarityInfo("Secret Rare").sortOrder)
        assertEquals(2, RarityUtils.getRarityInfo("Rare").sortOrder)
        assertEquals(0, RarityUtils.getRarityInfo("Common").sortOrder)
        assertEquals(1, RarityUtils.getRarityInfo("Uncommon").sortOrder)
    }

    // ── Il segno stampato ──────────────────────────────────────────────────

    @Test
    fun `le tre rarita' classiche hanno la forma stampata sulla carta`() {
        assertEquals(RaritySymbol(RarityShape.CIRCLE), RarityUtils.getRarityInfo("Common").symbol)
        assertEquals(RaritySymbol(RarityShape.DIAMOND), RarityUtils.getRarityInfo("Uncommon").symbol)
        assertEquals(RaritySymbol(RarityShape.STAR), RarityUtils.getRarityInfo("Rare").symbol)
        assertEquals(RaritySymbol(RarityShape.STAR), RarityUtils.getRarityInfo("Holo Rare").symbol)
        assertEquals(RaritySymbol(RarityShape.STAR), RarityUtils.getRarityInfo("Rare Holo").symbol)
    }

    @Test
    fun `ogni rarita' ha un simbolo, comprese le moderne e le sconosciute`() {
        // Nel riepilogo di un'espansione le voci stanno in fila: se meta'
        // fossero simboli e meta' scritte, la riga tornerebbe quella di prima.
        val stringhe = listOf(
            "Common", "Uncommon", "Rare", "Holo Rare", "Double rare", "Ultra Rare",
            "Illustration rare", "Special illustration rare", "Hyper rare", "Secret Rare",
            "Shiny rare", "Shiny Ultra Rare", "ACE SPEC Rare", "Promo", "LEGEND",
            "Rare PRIME", "Radiant Rare", "Amazing Rare", "Black White Rare",
            "Rare Holo LV.X", "Futuristic rare", "Pikachu Rare", "Classic Collection",
            "None", "Mythical Rare", null
        )
        for (s in stringhe) {
            val info = RarityUtils.getRarityInfo(s)
            assertTrue("nessun simbolo per $s", info.symbol.count >= 1)
        }
    }

    @Test
    fun `le rarita' che si somigliano non finiscono sullo stesso simbolo`() {
        // Due stelle ce l'hanno in tante: a distinguerle sono riempimento e
        // colore, che e' come funziona sul cartoncino vero.
        val doppia = RarityUtils.getRarityInfo("Double rare")
        val ultra = RarityUtils.getRarityInfo("Ultra Rare")
        val illSpec = RarityUtils.getRarityInfo("Special illustration rare")
        assertEquals(2, doppia.symbol.count)
        assertEquals(2, ultra.symbol.count)
        assertEquals(2, illSpec.symbol.count)
        assertTrue(doppia.symbol.filled)
        assertFalse(ultra.symbol.filled)   // contornate, non piene
        assertTrue(illSpec.symbol.filled)
        assertTrue(doppia.color != illSpec.color)
        // Tre stelle: Iper Rara piene, Rara Segreta contornate.
        assertEquals(3, RarityUtils.getRarityInfo("Hyper rare").symbol.count)
        assertEquals(3, RarityUtils.getRarityInfo("Secret Rare").symbol.count)
        assertFalse(RarityUtils.getRarityInfo("Secret Rare").symbol.filled)
    }

    @Test
    fun `le etichette corte restano corte`() {
        // Il riepilogo dell'espansione tiene tutte le rarita' su una riga
        // sola: con dieci voci ogni nome ha si' e no trentacinque punti, e una
        // parola piu' lunga di cosi' torna a mandare l'ultima voce sotto --
        // com'era per Iper Rara in Buio Pesto e Futuristica nel 30°.
        val stringhe = listOf(
            "Common", "Uncommon", "Rare", "Holo Rare", "Double rare", "Ultra Rare",
            "Illustration rare", "Special illustration rare", "Hyper rare", "Secret Rare",
            "Shiny rare", "Shiny Ultra Rare", "ACE SPEC Rare", "Promo", "LEGEND",
            "Rare PRIME", "Radiant Rare", "Amazing Rare", "Black White Rare",
            "Rare Holo LV.X", "Futuristic rare", "Pikachu Rare", "Classic Collection",
            "None", null
        )
        // Solo in italiano: `AppLocale.current` ha il setter privato e si
        // cambia lingua solo con un Context, che qui non c'e'. Le forme
        // inglesi vanno quindi controllate a mano quando si toccano -- e' cosi'
        // che "S. Illustration" era finito su "S. Illus.", un carattere di
        // troppo. Se la riga si rompe non e' un disastro comunque: il layout
        // spartisce la larghezza e tronca, non manda a capo.
        for (s in stringhe) {
            val short = RarityUtils.getRarityInfo(s).shortLabel
            assertTrue("etichetta troppo lunga per $s: \"$short\"", short.length <= 8)
        }
    }

    @Test
    fun `le shiny usano il luccichio a quattro punte, non la stella`() {
        assertEquals(RarityShape.SPARKLE, RarityUtils.getRarityInfo("Shiny rare").symbol.shape)
        assertEquals(RarityShape.SPARKLE, RarityUtils.getRarityInfo("Radiant Rare").symbol.shape)
        assertEquals(RarityShape.STAR, RarityUtils.getRarityInfo("Rare").symbol.shape)
    }

    @Test
    fun `le rarita' col segno nero seguono il tema invece di sparire`() {
        assertTrue(RarityUtils.getRarityInfo("Rare").adaptive)
        assertTrue(RarityUtils.getRarityInfo("Double rare").adaptive)
        assertFalse(RarityUtils.getRarityInfo("Common").adaptive)
        assertFalse(RarityUtils.getRarityInfo("Hyper rare").adaptive)
    }

    // ── Sconosciute e non mappate: due cose diverse ────────────────────────

    @Test
    fun `senza dato la rarita' e' sconosciuta e va in fondo`() {
        val vuota = RarityUtils.getRarityInfo(null)
        assertTrue(vuota.isUnknown)
        assertEquals(99, vuota.sortOrder)
        assertFalse(RarityUtils.hasFoilFinish(null))
        assertTrue(RarityUtils.getRarityInfo("   ").isUnknown)
    }

    @Test
    fun `l'Unknown congelato nelle vecchie collezioni vale come sconosciuta`() {
        val vecchia = RarityUtils.getRarityInfo("Unknown")
        assertTrue(vecchia.isUnknown)
        assertEquals(99, vecchia.sortOrder)
        assertFalse(RarityUtils.hasFoilFinish("Unknown"))
    }

    @Test
    fun `una rarita' vera che non mappiamo tiene il suo nome e non e' sconosciuta`() {
        val info = RarityUtils.getRarityInfo("Mythical Rare")
        assertEquals("Mythical Rare", info.label)
        assertFalse(info.isUnknown)
        assertEquals(90, info.sortOrder)
        // In coda alla scala, ma senza guadagnare la foil per il solo fatto
        // di avere un sortOrder alto -- era il difetto del vecchio `>= 5`.
        assertFalse(RarityUtils.hasFoilFinish("Mythical Rare"))
    }

    @Test
    fun `None e Nessuna sono carte senza rarita' stampata, non buchi`() {
        val none = RarityUtils.getRarityInfo("None")
        assertFalse(none.isUnknown)
        assertEquals(95, none.sortOrder)
        assertEquals(RarityUtils.getRarityInfo("Nessuna").label, none.label)
    }

    // ── Vocabolario storto lasciato dai topup XY ───────────────────────────

    @Test
    fun `le rarita' scritte in italiano storto cadono nel ramo giusto`() {
        assertEquals(13, RarityUtils.getRarityInfo("Segreto rara").sortOrder)
        assertEquals(6, RarityUtils.getRarityInfo("Ultrarara").sortOrder)
        assertEquals(1, RarityUtils.getRarityInfo("Non comune").sortOrder)
        assertEquals(2, RarityUtils.getRarityInfo("Rara").sortOrder)
    }

    @Test
    fun `le rarita' nuove del catalogo sono riconosciute`() {
        assertEquals("Pikachu Rare", RarityUtils.getRarityInfo("Pikachu Rare").label)
        assertFalse(RarityUtils.getRarityInfo("Pikachu Rare").isUnknown)
        assertFalse(RarityUtils.getRarityInfo("Classic Collection").isUnknown)
        assertTrue(RarityUtils.hasFoilFinish("Classic Collection"))
        assertEquals(6, RarityUtils.getRarityInfo("Rare Holo LV.X").sortOrder)
    }

    @Test
    fun `la foil resta accesa solo dall'Illustration Rare in su`() {
        assertFalse(RarityUtils.hasFoilFinish("Common"))
        assertFalse(RarityUtils.hasFoilFinish("Uncommon"))
        assertFalse(RarityUtils.hasFoilFinish("Rare"))
        assertFalse(RarityUtils.hasFoilFinish("Double rare"))
        assertFalse(RarityUtils.hasFoilFinish("Promo"))
        assertTrue(RarityUtils.hasFoilFinish("Illustration rare"))
        assertTrue(RarityUtils.hasFoilFinish("Ultra Rare"))
        assertTrue(RarityUtils.hasFoilFinish("Secret Rare"))
    }
}
