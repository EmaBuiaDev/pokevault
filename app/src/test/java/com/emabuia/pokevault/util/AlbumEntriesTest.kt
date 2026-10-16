package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.data.model.AlbumCatalogCard
import com.emabuia.pokevault.data.model.BinderLayout
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.toFirestoreMap
import com.emabuia.pokevault.data.remote.CardImages
import com.emabuia.pokevault.data.remote.CardMarket
import com.emabuia.pokevault.data.remote.CardMarketPrices
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgCardSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tasche dell'album: carte della collezione e carte cercate nel catalogo.
 *
 * Il rischio vero e' rompere gli album gia' salvati, fatti solo di id della
 * collezione: per quelli tutto deve restare com'era. Poi i doppioni (la stessa
 * carta da due schede, o gia' presente) e le tasche oltre la grandezza.
 */
class AlbumEntriesTest {

    private fun owned(
        id: String,
        apiId: String = "",
        quantity: Int = 1,
        value: Double = 0.0,
        name: String = "Carta $id"
    ) = PokemonCard(
        id = id,
        name = name,
        apiCardId = apiId,
        quantity = quantity,
        estimatedValue = value,
        imageUrl = "https://img/$id.png",
        set = "Set della collezione"
    )

    private fun catalog(apiId: String, name: String = "Catalogo $apiId", set: String = "Fiamme Ossidiana") =
        AlbumCatalogCard(apiId = apiId, name = name, imageUrl = "https://img/$apiId.png", set = set, number = "1")

    private fun album(
        cardIds: List<String> = emptyList(),
        catalogCards: List<AlbumCatalogCard> = emptyList(),
        size: Int = 9
    ) = Album(id = "a1", name = "Album", cardIds = cardIds, catalogCards = catalogCards, size = size)

    private fun resolve(album: Album, cards: List<PokemonCard>) =
        AlbumEntries.resolve(album, cards.associateBy { it.id }, AlbumEntries.ownedByApiId(cards))

    // ── Voci ──────────────────────────────────────────────────────────────

    @Test
    fun `la voce del catalogo tiene l'apiCardId intero, anche coi due punti`() {
        val entry = AlbumEntries.catalogEntry("ita:sv08:12")
        assertEquals("cat:ita:sv08:12", entry)
        assertEquals("ita:sv08:12", AlbumEntries.apiIdOf(entry))
    }

    @Test
    fun `l'id di una carta della collezione non e' una voce del catalogo`() {
        assertNull(AlbumEntries.apiIdOf("Xy7Gq2LmP0aBcDeFgHiJ"))
        assertNull(AlbumEntries.apiIdOf("cat:"))
    }

    @Test
    fun `per apiCardId vince la copia con piu' esemplari, e le righe vuote non contano`() {
        val map = AlbumEntries.ownedByApiId(
            listOf(
                owned("a", apiId = "ita:sv08:12", quantity = 1),
                owned("b", apiId = "ita:sv08:12", quantity = 3),
                owned("c", apiId = "ita:sv08:13", quantity = 0),
                owned("d", apiId = "")
            )
        )
        assertEquals(setOf("ita:sv08:12"), map.keys)
        assertEquals("b", map.getValue("ita:sv08:12").id)
    }

    // ── Album di prima: niente deve cambiare ──────────────────────────────

    @Test
    fun `un album di sole carte della collezione si risolve come prima`() {
        val cards = listOf(owned("x", value = 2.0), owned("y", value = 3.0), owned("z"))
        val slots = resolve(album(cardIds = listOf("z", "x", "sparita", "y")), cards)

        // Ordine dell'album, la carta tolta dalla collezione saltata.
        assertEquals(listOf("z", "x", "y"), slots.map { it.key })
        assertTrue(slots.all { it.isOwned })
        assertEquals(5.0, slots.sumOf { it.value }, 0.0001)
    }

    @Test
    fun `un album salvato prima dei campi nuovi e' un raccoglitore da nove`() {
        assertEquals(BinderLayout.POCKET_9, BinderLayout.of(Album().layout))
        assertEquals(BinderLayout.POCKET_9, BinderLayout.of(null))
        assertEquals(BinderLayout.POCKET_9, BinderLayout.of("5x5"))
        assertEquals(BinderLayout.POCKET_12, BinderLayout.of("4x3"))
        assertEquals(listOf(4, 9, 12), BinderLayout.entries.map { it.perPage })
    }

    // ── Carte da trovare ──────────────────────────────────────────────────

    @Test
    fun `una carta del catalogo che non hai e' da trovare e non vale niente`() {
        val ghost = catalog("ita:sv08:12").copy(price = 40.0)
        val slots = resolve(album(cardIds = listOf("cat:ita:sv08:12"), catalogCards = listOf(ghost)), emptyList())

        val slot = slots.single()
        assertFalse(slot.isOwned)
        assertEquals("Catalogo ita:sv08:12", slot.name)
        assertEquals("https://img/ita:sv08:12.png", slot.imageUrl)
        assertEquals(0.0, slot.value, 0.0)
    }

    @Test
    fun `quando la carta entra in collezione la tasca si accende da sola`() {
        val a = album(cardIds = listOf("cat:ita:sv08:12"), catalogCards = listOf(catalog("ita:sv08:12")))
        val slot = resolve(a, listOf(owned("doc1", apiId = "ita:sv08:12", value = 7.5))).single()

        assertTrue(slot.isOwned)
        assertEquals("cat:ita:sv08:12", slot.key)
        assertEquals("doc1", slot.owned?.id)
        assertEquals(7.5, slot.value, 0.0)
        // L'immagine e' quella della tua copia.
        assertEquals("https://img/doc1.png", slot.imageUrl)
    }

    @Test
    fun `una voce del catalogo senza dati e senza copia si salta`() {
        assertTrue(resolve(album(cardIds = listOf("cat:ita:sv08:99")), emptyList()).isEmpty())
    }

    // ── Aggiungere dal catalogo ───────────────────────────────────────────

    @Test
    fun `dal catalogo entra la tua copia se ce l'hai, se no la carta da trovare`() {
        val cards = listOf(owned("doc1", apiId = "ita:sv08:12"))
        val plan = AlbumEntries.planCatalogAdditions(
            album(),
            listOf(catalog("ita:sv08:12"), catalog("ita:sv08:13")),
            AlbumEntries.ownedByApiId(cards)
        )
        assertEquals(listOf("doc1", "cat:ita:sv08:13"), plan.entries)
        assertEquals(listOf("ita:sv08:13"), plan.catalogCards.map { it.apiId })
    }

    @Test
    fun `una carta gia' nell'album non entra una seconda volta`() {
        val cards = listOf(owned("doc1", apiId = "ita:sv08:12"))
        val a = album(
            cardIds = listOf("doc1", "cat:ita:sv08:13"),
            catalogCards = listOf(catalog("ita:sv08:13"))
        )
        val plan = AlbumEntries.planCatalogAdditions(
            a,
            listOf(catalog("ita:sv08:12"), catalog("ita:sv08:13"), catalog("ita:sv08:13")),
            AlbumEntries.ownedByApiId(cards)
        )
        assertTrue(plan.isEmpty)
        assertTrue(plan.catalogCards.isEmpty())
    }

    @Test
    fun `non si va oltre le tasche libere`() {
        val a = album(cardIds = listOf("x", "y"), size = 3)
        val plan = AlbumEntries.planCatalogAdditions(
            a,
            listOf(catalog("eng:sv1:1"), catalog("eng:sv1:2"), catalog("eng:sv1:3")),
            emptyMap()
        )
        assertEquals(listOf("cat:eng:sv1:1"), plan.entries)
        assertEquals(1, plan.catalogCards.size)
    }

    @Test
    fun `un album pieno non prende niente`() {
        val plan = AlbumEntries.planCatalogAdditions(album(cardIds = listOf("x"), size = 1), listOf(catalog("eng:sv1:1")), emptyMap())
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `le due schede insieme stanno nelle tasche libere, prima la collezione`() {
        val a = album(cardIds = listOf("gia"), size = 3)
        val plan = AlbumEntries.planAdditions(
            a,
            ownedIds = listOf("mia1", "mia1", "gia"),
            picked = listOf(catalog("ita:sv08:1"), catalog("ita:sv08:2")),
            ownedByApiId = emptyMap()
        )
        assertEquals(listOf("mia1", "cat:ita:sv08:1"), plan.entries)
        assertEquals(listOf("ita:sv08:1"), plan.catalogCards.map { it.apiId })
    }

    @Test
    fun `la stessa carta scelta nelle due schede entra una volta sola`() {
        val cards = listOf(owned("doc1", apiId = "ita:sv08:12"))
        val plan = AlbumEntries.planAdditions(
            album(),
            ownedIds = listOf("doc1"),
            picked = listOf(catalog("ita:sv08:12")),
            ownedByApiId = AlbumEntries.ownedByApiId(cards)
        )
        assertEquals(listOf("doc1"), plan.entries)
    }

    // ── Togliere e rimettere ──────────────────────────────────────────────

    @Test
    fun `togliere una carta da trovare toglie anche i suoi dati`() {
        val a = album(
            cardIds = listOf("x", "cat:ita:sv08:12", "cat:ita:sv08:13"),
            catalogCards = listOf(catalog("ita:sv08:12"), catalog("ita:sv08:13"))
        )
        val content = AlbumEntries.withoutEntry(a, "cat:ita:sv08:12")
        assertEquals(listOf("x", "cat:ita:sv08:13"), content.cardIds)
        assertEquals(listOf("ita:sv08:13"), content.catalogCards.map { it.apiId })
    }

    @Test
    fun `annulla rimette la carta da trovare al suo posto, coi suoi dati`() {
        val removed = catalog("ita:sv08:12")
        val a = album(cardIds = listOf("x", "y"))
        val content = AlbumEntries.withEntryAt(a, "cat:ita:sv08:12", 1, removed)
        assertEquals(listOf("x", "cat:ita:sv08:12", "y"), content.cardIds)
        assertEquals(listOf(removed), content.catalogCards)

        // Due volte "annulla" non la sdoppiano.
        val again = AlbumEntries.withEntryAt(a.copy(cardIds = content.cardIds, catalogCards = content.catalogCards), "cat:ita:sv08:12", 0, removed)
        assertEquals(content.cardIds, again.cardIds)
        assertEquals(1, again.catalogCards.size)
    }

    @Test
    fun `restano solo le carte del catalogo che qualche tasca usa, una per carta`() {
        val pruned = AlbumEntries.prune(
            listOf("cat:a", "x"),
            listOf(catalog("a"), catalog("a"), catalog("b"))
        )
        assertEquals(listOf("a"), pruned.map { it.apiId })
    }

    // ── Regole ────────────────────────────────────────────────────────────

    @Test
    fun `senza regole passa tutto`() {
        assertFalse(AlbumEntries.hasRules(album()))
        assertTrue(AlbumEntries.matchesRules(album(), catalog("x")))
    }

    @Test
    fun `il tipo vale in italiano e in inglese`() {
        val fuoco = album().copy(pokemonType = "Fuoco")
        assertTrue(AlbumEntries.matchesRules(fuoco, catalog("x").copy(type = "Fire")))
        assertTrue(AlbumEntries.matchesRules(fuoco, catalog("x").copy(type = "Fuoco")))
        assertFalse(AlbumEntries.matchesRules(fuoco, catalog("x").copy(type = "Water")))
    }

    @Test
    fun `espansione e categoria filtrano come per la collezione`() {
        val a = album().copy(expansion = "Fiamme Ossidiana", supertype = "Trainer")
        val aiuto = catalog("x").copy(supertype = "Trainer", set = "Fiamme Ossidiana")
        assertTrue(AlbumEntries.matchesRules(a, aiuto))
        assertFalse(AlbumEntries.matchesRules(a, aiuto.copy(set = "Scarlatto e Violetto")))
        assertFalse(AlbumEntries.matchesRules(a, aiuto.copy(supertype = "Pokémon", hp = 120, type = "Fire")))
    }

    // ── Dal catalogo alla tasca ───────────────────────────────────────────

    @Test
    fun `la fotografia della carta prende il prezzo minimo e l'immagine piccola`() {
        val card = TcgCard(
            id = " ita:sv08:12 ",
            name = "Charizard ex",
            supertype = "Pokémon",
            hp = "330",
            types = listOf("Fire"),
            set = TcgCardSet(id = "sv08__ita", name = "Scintille Folgoranti"),
            number = "12",
            rarity = "Double Rare",
            images = CardImages(small = "", large = "https://img/large.png"),
            cardmarket = CardMarket(prices = CardMarketPrices(lowPrice = 3.0, trendPrice = 9.0, avg30 = 8.0))
        )
        val snap = AlbumEntries.fromTcgCard(card)
        assertEquals("ita:sv08:12", snap.apiId)
        assertEquals("https://img/large.png", snap.imageUrl)
        assertEquals("Scintille Folgoranti", snap.set)
        assertEquals("sv08__ita", snap.setId)
        assertEquals("Fire", snap.type)
        assertEquals(330, snap.hp)
        assertEquals(3.0, snap.price, 0.0001)
    }

    @Test
    fun `la mappa per Firestore ha tutti i campi ed e' la stessa per la stessa carta`() {
        val card = catalog("ita:sv08:12").copy(hp = 70, price = 1.5)
        val map = card.toFirestoreMap()
        assertEquals(
            setOf("apiId", "name", "imageUrl", "set", "setId", "number", "rarity", "type", "supertype", "hp", "price"),
            map.keys
        )
        assertEquals(map, card.copy().toFirestoreMap())
    }

    // ── Pagine ────────────────────────────────────────────────────────────

    @Test
    fun `le pagine arrotondano per eccesso`() {
        assertEquals(1, AlbumEntries.pagesFor(9, 9))
        assertEquals(2, AlbumEntries.pagesFor(10, 9))
        assertEquals(14, AlbumEntries.pagesFor(120, 9))
        assertEquals(1, AlbumEntries.pagesFor(0, 12))
    }

    @Test
    fun `rimpicciolire non butta fuori le carte gia' dentro`() {
        assertEquals(24, AlbumEntries.sizeFor(pages = 2, perPage = 12, cardsInside = 5))
        assertEquals(30, AlbumEntries.sizeFor(pages = 1, perPage = 12, cardsInside = 30))
        assertEquals(999, AlbumEntries.sizeFor(pages = 500, perPage = 9, cardsInside = 0))
    }
}
