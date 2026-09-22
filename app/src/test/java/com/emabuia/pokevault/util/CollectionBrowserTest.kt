package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.model.PokemonCard
import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test

/**
 * Raggruppamento, filtri e ordinamenti di "Le mie carte".
 *
 * Due famiglie di test: i difetti corretti (rarita' doppie nel filtro, prezzo
 * di una carta preso dalla stampa sbagliata) e i comportamenti di prima che
 * non devono cambiare (una tessera per carta, doppio tipo, espansione
 * sconosciuta, carte senza data in fondo).
 */
class CollectionBrowserTest {

    private val unknown = "Espansione sconosciuta"

    private fun card(
        id: String,
        name: String = id,
        set: String = "Scintille Folgoranti",
        number: String = "1",
        variant: String = "Normal",
        quantity: Int = 1,
        value: Double = 0.0,
        rarity: String = "Common",
        type: String = "Fire",
        supertype: String = "Pokémon",
        language: String = "Italiano",
        addedAt: Long? = null
    ) = PokemonCard(
        id = id,
        name = name,
        set = set,
        cardNumber = number,
        variant = variant,
        quantity = quantity,
        estimatedValue = value,
        rarity = rarity,
        type = type,
        supertype = supertype,
        language = language,
        addedAt = addedAt?.let { Timestamp(it, 0) }
    )

    private fun groups(vararg cards: PokemonCard) = CollectionBrowser.group(cards.toList(), unknown)

    // ── Raggruppamento (comportamento di prima) ───────────────────────────

    @Test
    fun `due stampe della stessa carta sono una tessera sola con le copie sommate`() {
        val g = groups(
            card("a", number = "5", variant = "Normal", quantity = 2),
            card("b", number = "5", variant = "Reverse", quantity = 1)
        )
        assertEquals(1, g.size)
        assertEquals(3, g.single().totalQuantity)
        assertEquals(3, g.single().representative.quantity)
        assertEquals(setOf("Normal", "Reverse"), g.single().variants)
        // La stampa mostrata e' la Normale, qualunque sia l'ordine dei documenti.
        assertEquals("Normal", g.single().representative.variant)
    }

    @Test
    fun `senza set la carta finisce sotto espansione sconosciuta`() {
        val g = groups(card("a", set = "", number = "1")).single()
        assertEquals(unknown, g.expansion)
        assertEquals(unknown, g.expansionLabel)
    }

    // ── Il prezzo di una carta ────────────────────────────────────────────

    @Test
    fun `il prezzo di una carta e' quello della stampa piu' cara`() {
        val g = groups(
            card("n", number = "7", variant = "Normal", value = 1.0),
            card("r", number = "7", variant = "Reverse", value = 30.0)
        ).single()
        assertEquals(30.0, g.topValue, 0.001)
    }

    @Test
    fun `ordinando per prezzo conta la stampa piu' cara, non la prima`() {
        val sorted = CollectionBrowser.sort(
            groups(
                card("cara-n", name = "Cara", number = "1", variant = "Normal", value = 1.0),
                card("cara-r", name = "Cara", number = "1", variant = "Reverse", value = 30.0),
                card("media", name = "Media", number = "2", value = 10.0)
            ),
            CollectionSort.PRICE_DESC
        )
        assertEquals(listOf("Cara", "Media"), sorted.map { it.representative.name })
    }

    @Test
    fun `in ordine crescente le carte senza prezzo vanno in fondo`() {
        val sorted = CollectionBrowser.sort(
            groups(
                card("zero", name = "Zero", number = "1", value = 0.0),
                card("uno", name = "Uno", number = "2", value = 1.0)
            ),
            CollectionSort.PRICE_ASC
        )
        assertEquals(listOf("Uno", "Zero"), sorted.map { it.representative.name })
    }

    // ── Ordinamenti ───────────────────────────────────────────────────────

    @Test
    fun `per numero il 2 viene prima del 10`() {
        val sorted = CollectionBrowser.sort(
            groups(card("x", number = "10"), card("y", number = "2")),
            CollectionSort.NUMBER
        )
        assertEquals(listOf("2", "10"), sorted.map { it.number })
    }

    @Test
    fun `per data le carte senza data finiscono in fondo`() {
        val sorted = CollectionBrowser.sort(
            groups(
                card("vecchia", number = "1", addedAt = null),
                card("nuova", number = "2", addedAt = 2_000),
                card("media", number = "3", addedAt = 1_000)
            ),
            CollectionSort.NEWEST
        )
        assertEquals(listOf("nuova", "media", "vecchia"), sorted.map { it.representative.id })
    }

    // ── Filtri ────────────────────────────────────────────────────────────

    /**
     * Il difetto che si vedeva: il catalogo ha sia "Rare Holo" sia "Holo
     * Rare", tutte e due tradotte "Rara Holo". Filtrando la stringa grezza
     * uscivano due chip uguali che trovavano carte diverse.
     */
    @Test
    fun `i sinonimi di rarita' sono una voce sola e il filtro le trova entrambe`() {
        val g = groups(
            card("a", number = "1", rarity = "Rare Holo"),
            card("b", number = "2", rarity = "Holo Rare")
        )
        val facets = CollectionBrowser.facets(g)
        assertEquals(listOf(FacetCount("Rara Holo", 2)), facets.rarities)

        val found = CollectionBrowser.filter(g, CollectionFilter(rarities = setOf("Rara Holo")))
        assertEquals(2, found.size)
    }

    @Test
    fun `una carta a doppio tipo si trova da tutti e due i tipi`() {
        val g = groups(card("a", type = "Metal, Fighting"))
        assertEquals(1, CollectionBrowser.filter(g, CollectionFilter(types = setOf("Metallo"))).size)
        assertEquals(1, CollectionBrowser.filter(g, CollectionFilter(types = setOf("Lotta"))).size)
        assertEquals(0, CollectionBrowser.filter(g, CollectionFilter(types = setOf("Fuoco"))).size)
    }

    @Test
    fun `dentro un filtro le scelte si sommano, fra filtri diversi si restringono`() {
        val g = groups(
            card("fuoco", number = "1", type = "Fire", variant = "Reverse"),
            card("acqua", number = "2", type = "Water", variant = "Normal"),
            card("erba", number = "3", type = "Grass", variant = "Reverse")
        )
        val oneOrOther = CollectionBrowser.filter(g, CollectionFilter(types = setOf("Fuoco", "Acqua")))
        assertEquals(setOf("fuoco", "acqua"), oneOrOther.map { it.representative.id }.toSet())

        val both = CollectionBrowser.filter(g, CollectionFilter(types = setOf("Fuoco", "Acqua"), variants = setOf("Reverse")))
        assertEquals(listOf("fuoco"), both.map { it.representative.id })
    }

    @Test
    fun `scegliendo un tipo non tornano gli Allenatori`() {
        val g = groups(
            card("poke", number = "1", type = "Colorless"),
            card("trainer", number = "2", type = "Colorless", supertype = "Trainer", name = "Ricerca Professionale")
        )
        val found = CollectionBrowser.filter(g, CollectionFilter(types = setOf("Incolore")))
        assertEquals(listOf("poke"), found.map { it.representative.id })
    }

    @Test
    fun `la stampa trova la carta anche se ne hai pure un'altra`() {
        val g = groups(
            card("n", number = "4", variant = "Normal"),
            card("r", number = "4", variant = "Reverse")
        )
        assertEquals(1, CollectionBrowser.filter(g, CollectionFilter(variants = setOf("Reverse"))).size)
    }

    @Test
    fun `solo doppioni conta le copie di tutte le stampe`() {
        val g = groups(
            card("n", number = "4", variant = "Normal", quantity = 1),
            card("r", number = "4", variant = "Reverse", quantity = 1),
            card("sola", number = "9", quantity = 1)
        )
        val found = CollectionBrowser.filter(g, CollectionFilter(onlyDuplicates = true))
        assertEquals(1, found.size)
        assertEquals("4", found.single().number)
    }

    @Test
    fun `fasce di valore, con i bordi al posto giusto`() {
        assertTrue(ValueBucket.NO_PRICE.matches(0.0))
        assertFalse(ValueBucket.UNDER_1.matches(0.0))
        assertTrue(ValueBucket.UNDER_1.matches(0.5))
        assertTrue(ValueBucket.FROM_1_TO_10.matches(1.0))
        assertFalse(ValueBucket.FROM_1_TO_10.matches(10.0))
        assertTrue(ValueBucket.FROM_10_TO_50.matches(10.0))
        assertTrue(ValueBucket.OVER_50.matches(50.0))
    }

    @Test
    fun `la lingua con e senza bandiera e' la stessa`() {
        val g = groups(
            card("a", number = "1", language = "🇮🇹 Italiano"),
            card("b", number = "2", language = "Italiano")
        )
        assertEquals(listOf(FacetCount("Italiano", 2)), CollectionBrowser.facets(g).languages)
        assertEquals(2, CollectionBrowser.filter(g, CollectionFilter(languages = setOf("Italiano"))).size)
    }

    @Test
    fun `la ricerca trova per nome, per set e per numero, senza maiuscole`() {
        val g = groups(card("a", name = "Pikachu ex", number = "57"))
        assertEquals(1, CollectionBrowser.filter(g, CollectionFilter(query = "PIKA")).size)
        assertEquals(1, CollectionBrowser.filter(g, CollectionFilter(query = "scintille")).size)
        assertEquals(1, CollectionBrowser.filter(g, CollectionFilter(query = "57")).size)
        assertEquals(0, CollectionBrowser.filter(g, CollectionFilter(query = "charizard")).size)
    }

    @Test
    fun `il nome dell'espansione con spazi doppi non diventa un'espansione a parte`() {
        val g = groups(
            card("a", number = "1", set = "Scintille  Folgoranti"),
            card("b", number = "2", set = "Scintille Folgoranti")
        )
        assertEquals(1, CollectionBrowser.facets(g).expansions.size)
    }

    @Test
    fun `nessun filtro attivo conta zero, la ricerca non conta come filtro`() {
        assertEquals(0, CollectionFilter(query = "pika").activeCount)
        assertEquals(3, CollectionFilter(types = setOf("Fuoco", "Acqua"), onlyDuplicates = true).activeCount)
    }

    // ── Sezioni per espansione ────────────────────────────────────────────

    @Test
    fun `le sezioni sommano copie e valore di tutte le stampe`() {
        val sections = CollectionBrowser.sections(
            groups(
                card("a", number = "1", quantity = 2, value = 3.0),
                card("b", number = "2", set = "Evoluzioni a Paldea", quantity = 1, value = 10.0)
            ),
            ExpansionOrder.MOST_VALUE
        )
        assertEquals(listOf("Evoluzioni a Paldea", "Scintille Folgoranti"), sections.map { it.label })
        assertEquals(2, sections.last().totalQuantity)
        assertEquals(6.0, sections.last().totalValue, 0.001)
    }
}
