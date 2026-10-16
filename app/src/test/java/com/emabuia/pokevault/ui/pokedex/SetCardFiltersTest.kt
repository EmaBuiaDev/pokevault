package com.emabuia.pokevault.ui.pokedex

import com.emabuia.pokevault.data.italian.ItalianHpBucket
import com.emabuia.pokevault.data.italian.ItalianPriceBucket
import com.emabuia.pokevault.data.remote.TcgCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I filtri e l'ordine dentro un'espansione. La griglia, lo swipe della scheda
 * e la selezione multipla leggono tutti questa lista: un errore qui li
 * sposterebbe tutti insieme.
 */
class SetCardFiltersTest {

    private fun card(
        id: String,
        number: String = id,
        name: String = "Carta $id",
        supertype: String = "Pokémon",
        types: List<String>? = null,
        rarity: String? = null,
        hp: String? = null,
        artist: String? = null,
        stage: String? = null,
        variants: List<String>? = null
    ) = TcgCard(
        id = id, name = name, supertype = supertype, types = types, rarity = rarity,
        hp = hp, artist = artist, number = number, subtypes = stage?.let { listOf(it) }, variants = variants
    )

    private fun ctx(
        owned: Set<String> = emptySet(),
        ownedVariants: Map<String, Set<String>> = emptyMap(),
        wishlisted: Set<String> = emptySet(),
        prices: Map<String, Double> = emptyMap()
    ) = SetCardContext(owned, ownedVariants, wishlisted) { prices[it.id] }

    private fun List<TcgCard>.ids() = map { it.id }

    private fun List<TcgCard>.filtered(filter: SetCardFilter, context: SetCardContext = ctx()) =
        filter { matchesSetCardFilter(it, filter, context) }.ids()

    // ── Regola speciale dal nome ──

    @Test
    fun `la regola speciale si legge dal nome`() {
        assertEquals(CardMechanic.EX, cardMechanicOf("Dragapult-ex"))
        assertEquals(CardMechanic.EX, cardMechanicOf("Teal Mask Ogerpon ex"))
        assertEquals(CardMechanic.EX_CLASSIC, cardMechanicOf("Mewtwo-EX"))
        assertEquals(CardMechanic.GX, cardMechanicOf("Pikachu & Zekrom-GX"))
        assertEquals(CardMechanic.V, cardMechanicOf("Pikachu V"))
        assertEquals(CardMechanic.VMAX, cardMechanicOf("Charizard VMAX"))
        assertEquals(CardMechanic.VSTAR, cardMechanicOf("Arceus V ASTRO"))
        assertEquals(CardMechanic.VSTAR, cardMechanicOf("Arceus VSTAR"))
        assertEquals(CardMechanic.BREAK, cardMechanicOf("Greninja TURBO"))
        assertEquals(CardMechanic.LVX, cardMechanicOf("Dialga LV.X"))
    }

    @Test
    fun `le carte normali non hanno regola`() {
        assertNull(cardMechanicOf("Pikachu"))
        assertNull(cardMechanicOf("Vulpix"))
        assertNull(cardMechanicOf("Exeggcute"))
        assertNull(cardMechanicOf("Ricerca Professionale"))
        assertNull(cardMechanicOf("Energia Turbo Gemma"))
    }

    // ── Filtri ──

    @Test
    fun `filtro vuoto lascia passare tutto`() {
        val cards = listOf(card("1"), card("2"))
        assertEquals(listOf("1", "2"), cards.filtered(SetCardFilter()))
        assertTrue(SetCardFilter().isEmpty)
    }

    @Test
    fun `mancanti e possedute`() {
        val cards = listOf(card("1"), card("2"), card("3"))
        val c = ctx(owned = setOf("2"))
        assertEquals(listOf("1", "3"), cards.filtered(SetCardFilter(ownership = SetOwnership.MISSING), c))
        assertEquals(listOf("2"), cards.filtered(SetCardFilter(ownership = SetOwnership.OWNED), c))
    }

    @Test
    fun `dentro un gruppo vale l'una o l'altra, fra gruppi tutte e due`() {
        val cards = listOf(
            card("1", types = listOf("Fire"), rarity = "Common"),
            card("2", types = listOf("Water"), rarity = "Rare"),
            card("3", types = listOf("Grass"), rarity = "Rare")
        )
        assertEquals(listOf("1", "2"), cards.filtered(SetCardFilter(types = setOf("Fire", "Water"))))
        assertEquals(listOf("2"), cards.filtered(SetCardFilter(types = setOf("Fire", "Water"), rarities = setOf("Rare"))))
    }

    @Test
    fun `fase, regola e illustratore`() {
        val cards = listOf(
            card("1", name = "Charmander", stage = "Base", artist = "Mitsuhiro Arita"),
            card("2", name = "Charizard-ex", stage = "Fase 2", artist = "5ban Graphics "),
            card("3", name = "Ricerca", supertype = "Trainer")
        )
        assertEquals(listOf("1"), cards.filtered(SetCardFilter(stages = setOf("Base"))))
        assertEquals(listOf("2"), cards.filtered(SetCardFilter(mechanics = setOf(CardMechanic.EX))))
        // L'illustratore si confronta senza gli spazi in coda del catalogo.
        assertEquals(listOf("2"), cards.filtered(SetCardFilter(artists = setOf("5ban Graphics"))))
        assertEquals(listOf("3"), cards.filtered(SetCardFilter(supertypes = setOf("Trainer"))))
    }

    @Test
    fun `fasce di PS, le carte senza PS restano fuori`() {
        val cards = listOf(card("1", hp = "60"), card("2", hp = "220"), card("3"))
        assertEquals(listOf("1"), cards.filtered(SetCardFilter(hpBuckets = setOf(ItalianHpBucket.UP_TO_60))))
        assertEquals(listOf("2"), cards.filtered(SetCardFilter(hpBuckets = setOf(ItalianHpBucket.OVER_200))))
    }

    @Test
    fun `fasce di prezzo, senza prezzo noto la carta resta fuori`() {
        val cards = listOf(card("1"), card("2"), card("3"))
        val c = ctx(prices = mapOf("1" to 0.5, "2" to 30.0))
        assertEquals(listOf("1"), cards.filtered(SetCardFilter(priceBuckets = setOf(ItalianPriceBucket.UNDER_1)), c))
        assertEquals(
            listOf("1", "2"),
            cards.filtered(SetCardFilter(priceBuckets = setOf(ItalianPriceBucket.UNDER_1, ItalianPriceBucket.FROM_20_TO_50)), c)
        )
    }

    @Test
    fun `solo quelle nella wishlist`() {
        val cards = listOf(card("1"), card("2"))
        assertEquals(listOf("2"), cards.filtered(SetCardFilter(onlyWishlist = true), ctx(wishlisted = setOf("2"))))
    }

    @Test
    fun `ti manca una stampa - posseduta ma non in tutte le stampe base`() {
        val both = listOf("Normal", "Reverse")
        val cards = listOf(
            card("1", variants = both), // Normale si', Reverse no -> passa
            card("2", variants = both), // tutte e due -> no
            card("3", variants = both), // non posseduta -> no (e' "Mancanti")
            card("4", variants = listOf("Holo")) // l'unica stampa ce l'ha -> no
        )
        val c = ctx(
            owned = setOf("1", "2", "4"),
            ownedVariants = mapOf(
                "1" to setOf("Normal"),
                "2" to setOf("Normal", "Reverse"),
                "4" to setOf("Holo")
            )
        )
        assertEquals(listOf("1"), cards.filtered(SetCardFilter(missingVariant = true), c))
    }

    @Test
    fun `una stampa speciale non conta come stampa base mancante`() {
        // Le timbrate stanno nelle stampe della carta ma non fra le base:
        // chi ha Normale e Reverse non deve vederla come incompleta.
        val c = card("1", variants = listOf("Normal", "Reverse", "Reverse|stamp=pokeball"))
        val context = ctx(owned = setOf("1"), ownedVariants = mapOf("1" to setOf("Normal", "Reverse")))
        assertFalse(hasMissingVariant(c, context))
    }

    @Test
    fun `azzerare il pannello lascia Mancanti e Possedute`() {
        val filter = SetCardFilter(ownership = SetOwnership.MISSING, types = setOf("Fire"), onlyWishlist = true)
        assertEquals(2, filter.panelCount)
        assertEquals(SetCardFilter(ownership = SetOwnership.MISSING), filter.clearPanel())
    }

    // ── Ordine ──

    @Test
    fun `per numero come sempre, le carte senza numero in fondo`() {
        val cards = listOf(card("a", number = "10"), card("b", number = "2"), card("c", number = "SV"), card("d", number = "1"))
        assertEquals(listOf("d", "b", "a", "c"), sortSetCards(cards, SetCardSort.NUMBER) { null }.ids())
    }

    @Test
    fun `per prezzo, le carte senza prezzo in fondo in entrambi i versi`() {
        val cards = listOf(card("1"), card("2"), card("3"), card("4"))
        val prices = mapOf("1" to 5.0, "2" to 50.0, "4" to 0.3)
        val priceOf: (TcgCard) -> Double? = { prices[it.id] }
        assertEquals(listOf("2", "1", "4", "3"), sortSetCards(cards, SetCardSort.PRICE_DESC, priceOf).ids())
        assertEquals(listOf("4", "1", "2", "3"), sortSetCards(cards, SetCardSort.PRICE_ASC, priceOf).ids())
    }

    @Test
    fun `per PS e per nome, a parita' vince il numero`() {
        val cards = listOf(
            card("1", name = "Pikachu", hp = "60"),
            card("2", name = "Arcanine", hp = "130"),
            card("3", name = "Pikachu", hp = "60"),
            card("4", name = "Ricerca", supertype = "Trainer")
        )
        assertEquals(listOf("2", "1", "3", "4"), sortSetCards(cards, SetCardSort.HP_DESC) { null }.ids())
        assertEquals(listOf("2", "1", "3", "4"), sortSetCards(cards, SetCardSort.NAME) { null }.ids())
    }

    // ── Voci del pannello ──

    @Test
    fun `il pannello offre solo quello che c'e' nel set, coi conteggi`() {
        val cards = listOf(
            card("1", name = "Dragapult-ex", types = listOf("Dragon"), artist = "A"),
            card("2", name = "Dreepy", types = listOf("Dragon"), artist = "B"),
            card("3", name = "Drakloak", types = listOf("Dragon"), artist = "B")
        )
        val facets = SetCardFacets.from(cards)
        assertEquals(listOf(FacetCount("Dragon", 3)), facets.types)
        assertEquals(listOf(FacetCount(CardMechanic.EX, 1)), facets.mechanics)
        // Gli illustratori piu' presenti prima.
        assertEquals(listOf(FacetCount("B", 2), FacetCount("A", 1)), facets.artists)
    }
}
