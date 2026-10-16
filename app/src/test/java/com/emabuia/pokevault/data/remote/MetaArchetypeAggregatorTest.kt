package com.emabuia.pokevault.data.remote

import com.emabuia.pokevault.data.model.MetaArchetype
import com.emabuia.pokevault.data.model.MetaDeck
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La classifica degli archetipi del Meta Deck.
 *
 * Fino alla 3.1.8 il trend confrontava piazzamenti ordinati per posizione (e
 * diceva "in calo" quasi sempre) e il win rate era una media dei singoli deck
 * (un deck da 5-1 bastava per un 83% in cima alla classifica).
 */
class MetaArchetypeAggregatorTest {

    private fun deck(id: String) = MetaDeck(
        id = id, archetype = null, player = null, tournament = null, tournamentId = null,
        date = null, placement = null, winrate = null, link = null, cards = emptyList()
    )

    private fun entry(
        archetype: String,
        placement: Int,
        wins: Int = 5,
        losses: Int = 2,
        ties: Int = 0,
        players: Int = 64,
        tournament: String = "t1",
        date: String = "2026-10-01",
        deckId: String = "$archetype-$tournament-$placement"
    ) = ArchetypeEntry(
        archetype = archetype,
        placement = placement,
        wins = wins,
        losses = losses,
        ties = ties,
        players = players,
        tournamentDate = date,
        tournamentId = tournament,
        metaDeck = deck(deckId)
    )

    @Test
    fun `la presenza e' la quota dei deck nei top 32`() {
        val result = MetaArchetypeAggregator.aggregate(
            listOf(entry("Dragapult ex", 1), entry("Dragapult ex", 2), entry("Gardevoir ex", 3), entry("Charizard ex", 4))
        )

        assertEquals("Dragapult ex", result.first().name)
        assertEquals(50.0, result.first().metaShare, 1e-9)
        assertEquals(100.0, result.sumOf { it.metaShare }, 1e-9)
        assertEquals(4, result.first().sampleDecks)
        assertEquals(1, result.first().sampleTournaments)
    }

    @Test
    fun `il win rate somma vittorie e partite di tutti i deck`() {
        // 1-0 e 2-7: la media dei due win rate sarebbe (100% + 22%) / 2 = 61%,
        // le partite dicono 3 vinte su 10.
        val result = MetaArchetypeAggregator.aggregate(
            listOf(
                entry("Lugia VSTAR", 1, wins = 1, losses = 0),
                entry("Lugia VSTAR", 2, wins = 2, losses = 7)
            )
        ).single()

        assertEquals(3, result.wins)
        assertEquals(10, result.games)
        assertEquals(0.3, result.avgWinrate, 1e-9)
    }

    @Test
    fun `i pareggi contano come partite non vinte`() {
        val result = MetaArchetypeAggregator.aggregate(
            listOf(entry("Raging Bolt ex", 1, wins = 4, losses = 2, ties = 2))
        ).single()

        assertEquals(8, result.games)
        assertEquals(0.5, result.avgWinrate, 1e-9)
    }

    @Test
    fun `il trend guarda i tornei recenti contro i vecchi, non i piazzamenti`() {
        // Due tornei vecchi in cui Gholdengo e' meta' del campo, due recenti in
        // cui non c'e' piu'. I suoi piazzamenti sono ottimi: il vecchio calcolo
        // non poteva accorgersene.
        val entries = listOf(
            entry("Gholdengo ex", 1, tournament = "old1", date = "2026-09-01"),
            entry("Other", 2, tournament = "old1", date = "2026-09-01"),
            entry("Gholdengo ex", 1, tournament = "old2", date = "2026-09-08"),
            entry("Other", 2, tournament = "old2", date = "2026-09-08"),
            entry("Other", 1, tournament = "new1", date = "2026-10-01"),
            entry("Other", 2, tournament = "new1", date = "2026-10-01"),
            entry("Other", 1, tournament = "new2", date = "2026-10-08"),
            entry("Rising", 2, tournament = "new2", date = "2026-10-08")
        )

        val byName = MetaArchetypeAggregator.aggregate(entries).associateBy { it.name }

        assertEquals(-50.0, byName.getValue("Gholdengo ex").trend!!, 1e-9)
        assertEquals(25.0, byName.getValue("Rising").trend!!, 1e-9)
    }

    @Test
    fun `con un torneo solo il trend non c'e'`() {
        val result = MetaArchetypeAggregator.aggregate(listOf(entry("A", 1), entry("B", 2)))
        result.forEach { assertNull(it.trend) }
    }

    @Test
    fun `il miglior piazzamento porta i giocatori del suo torneo`() {
        val result = MetaArchetypeAggregator.aggregate(
            listOf(
                entry("Miraidon ex", 3, players = 40, tournament = "small"),
                entry("Miraidon ex", 1, players = 512, tournament = "regional"),
                entry("Miraidon ex", 12, players = 512, tournament = "regional", deckId = "other")
            )
        ).single()

        assertEquals(1, result.topPlacement)
        assertEquals(512, result.topPlacementPlayers)
        assertEquals(2, result.top8Count)
    }

    @Test
    fun `il deck da importare e' quello col miglior piazzamento`() {
        // L'import dal Meta Deck usa sampleDeck: deve restare il deck che ha
        // fatto meglio, come prima della 3.1.9.
        val result = MetaArchetypeAggregator.aggregate(
            listOf(
                entry("Charizard ex", 9, deckId = "ninth"),
                entry("Charizard ex", 2, deckId = "second"),
                entry("Charizard ex", 0, deckId = "dropped")
            )
        ).single()

        assertEquals("second", result.sampleDeck?.id)
    }

    @Test
    fun `i ritirati non diventano il miglior piazzamento`() {
        val result = MetaArchetypeAggregator.aggregate(
            listOf(entry("A", 0, players = 999), entry("A", 0))
        ).single()

        assertEquals(0, result.topPlacement)
        assertEquals(0, result.topPlacementPlayers)
        assertNotNull(result.sampleDeck)
    }

    @Test
    fun `una cache scritta dalla 3_1_8 si rilegge senza i campi nuovi`() {
        val json = """
            [{"name":"Charizard ex","count":3,"metaShare":12.5,"avgWinrate":0.61,"topPlacement":1,
              "recentResults":[1,4],"sampleDeck":null}]
        """.trimIndent()

        val read: List<MetaArchetype> = Gson().fromJson(json, object : TypeToken<List<MetaArchetype>>() {}.type)

        val archetype = read.single()
        assertEquals("Charizard ex", archetype.name)
        assertEquals(0, archetype.games)
        assertEquals(0, archetype.topPlacementPlayers)
        assertNull(archetype.trend)
    }

    @Test
    fun `senza deck non c'e' classifica`() {
        assertTrue(MetaArchetypeAggregator.aggregate(emptyList()).isEmpty())
    }
}
