package com.emabuia.pokevault.data.italian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il filtro della ricerca e il vocabolario del pannello passano entrambi da
 * qui: se questa derivazione cambia senza accorgersene, il pannello offre voci
 * che poi non pescano niente. Questi test tengono ferme le regole.
 */
class ItalianCardFacetsTest {

    private fun record(
        nome: String,
        ps: String? = null,
        tipo: String? = null,
        rarity: String? = null,
        espansione: String = "sv08"
    ) = ItalianCardRecord(
        cardId = "${espansione.uppercase()}_IT_001.png",
        espansioneId = espansione,
        nome = nome,
        tipo = tipo,
        ps = ps,
        rarity = rarity
    )

    // ── Categoria ──

    @Test
    fun `una carta con ps e' un Pokemon`() {
        assertEquals("Pokémon", ItalianCardFacets.supertypeOf(record("Charizard ex", ps = "330", tipo = "Fuoco")))
    }

    @Test
    fun `una carta senza ps che inizia per Energia e' una Energia`() {
        assertEquals("Energy", ItalianCardFacets.supertypeOf(record("Energia Fuoco")))
    }

    @Test
    fun `una macchina che nomina l'energia resta un Allenatore`() {
        // Il caso che la vecchia euristica sul campo tipo sbagliava.
        assertEquals("Trainer", ItalianCardFacets.supertypeOf(record("Recupero di Energia Plus")))
    }

    // ── Meccanica ──

    @Test
    fun `riconosce i suffissi di meccanica a prescindere dalle maiuscole`() {
        assertEquals("ex", ItalianCardFacets.variantOf(record("Charizard ex")))
        assertEquals("ex", ItalianCardFacets.variantOf(record("Charizard-EX")))
        assertEquals("VMAX", ItalianCardFacets.variantOf(record("Mewtwo VMAX")))
        assertEquals("VSTAR", ItalianCardFacets.variantOf(record("Arceus VSTAR")))
        assertEquals("V", ItalianCardFacets.variantOf(record("Pikachu V")))
        assertEquals("GX", ItalianCardFacets.variantOf(record("Lucario-GX")))
    }

    @Test
    fun `una carta senza suffisso e' Base`() {
        assertEquals(ItalianCardFacets.VARIANT_BASE, ItalianCardFacets.variantOf(record("Bulbasaur")))
        assertEquals(ItalianCardFacets.VARIANT_BASE, ItalianCardFacets.variantOf(record("Energia Lotta Speciale")))
    }

    // ── Tipi ──

    @Test
    fun `il doppio tipo arriva da D1 in un campo solo e va risplittato`() {
        assertEquals(listOf("Metallo", "Lotta"), ItalianCardFacets.typesOf(record("Tizio", tipo = "Metallo, Lotta")))
    }

    @Test
    fun `una carta senza tipo non ne ha nessuno`() {
        assertTrue(ItalianCardFacets.typesOf(record("Professor Oak")).isEmpty())
    }

    // ── Punti salute ──

    @Test
    fun `le fasce ps coprono la scala senza buchi ne' sovrapposizioni`() {
        assertEquals(ItalianHpBucket.UP_TO_60, ItalianHpBucket.forHp(60))
        assertEquals(ItalianHpBucket.FROM_70_TO_90, ItalianHpBucket.forHp(70))
        assertEquals(ItalianHpBucket.FROM_70_TO_90, ItalianHpBucket.forHp(90))
        assertEquals(ItalianHpBucket.FROM_100_TO_130, ItalianHpBucket.forHp(100))
        assertEquals(ItalianHpBucket.FROM_140_TO_200, ItalianHpBucket.forHp(200))
        assertEquals(ItalianHpBucket.OVER_200, ItalianHpBucket.forHp(330))
    }

    @Test
    fun `una carta senza ps non ha fascia`() {
        assertNull(ItalianCardFacets.hpBucketOf(record("Professor Oak")))
    }

    // ── Fasce di prezzo ──

    @Test
    fun `le fasce di prezzo non si sovrappongono sugli estremi`() {
        assertEquals(ItalianPriceBucket.UNDER_1, ItalianPriceBucket.forPrice(0.99))
        assertEquals(ItalianPriceBucket.FROM_1_TO_5, ItalianPriceBucket.forPrice(1.0))
        assertEquals(ItalianPriceBucket.FROM_5_TO_20, ItalianPriceBucket.forPrice(5.0))
        assertEquals(ItalianPriceBucket.OVER_50, ItalianPriceBucket.forPrice(1200.0))
    }

}
