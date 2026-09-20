package com.emabuia.pokevault.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Una decklist vera, copiata da un import che era andato storto.
 *
 * Il parser e' il primo anello: se sbaglia a separare nome, set e numero,
 * tutto quello che viene dopo cerca la carta sbagliata senza avere modo di
 * accorgersene. Questi casi sono quelli che nel formato reale mettono in
 * difficolta': nomi con l'apostrofo, con il possessivo, con la rule box, e le
 * energie scritte col simbolo fra graffe.
 */
class DecklistRealeTest {

    private val decklist = """
        Pokémon: 13
        4 Kadabra MEG 55
        1 Genesect SFA 40
        1 Shaymin DRI 10
        1 Psyduck ASC 39
        2 Pikipek PBL 66
        1 Abra TWM 80
        3 Abra MEG 54
        1 Dunsparce PRE 79
        1 Fezandipiti ex ASC 142
        3 Alakazam MEG 56
        2 Dudunsparce TEF 129
        1 Toucannon PBL 68

        Carte Allenatore: 13
        1 Night Stretcher ASC 196
        1 Eri TEF 146
        4 Buddy-Buddy Poffin TEF 144
        1 Lana's Aid TWM 155
        3 Boss's Orders MEG 114
        1 Colress's Tenacity SFA 57
        4 Rare Candy MEG 125

        Energia: 3
        1 Basic {P} Energy MEE 5
        1 Enriching Energy SSP 191
        4 Telepathic {P} Energy POR 88

        Totale carte: 60
    """.trimIndent()

    private val parsed = DeckImportParser.parse(decklist)

    private fun card(nome: String) = parsed.cards.firstOrNull { it.name == nome }

    @Test
    fun testTutteLeRigheVengonoRiconosciute() {
        // 12 Pokemon + 7 allenatore + 3 energia
        assertEquals(22, parsed.cards.size)
        assertTrue("Righe non riconosciute: ${parsed.errors}", parsed.errors.isEmpty())
    }

    @Test
    fun testNomeSetNumeroSeparatiBene() {
        val kadabra = card("Kadabra")
        assertNotNull("Kadabra non riconosciuto", kadabra)
        assertEquals(4, kadabra!!.qty)
        assertEquals("55", kadabra.number)
        assertEquals("pokemon", kadabra.type)
    }

    /**
     * Il set della riga deve restare quello scritto: MEG e MEP sono espansioni
     * diverse, ed era proprio qui che "Kadabra MEG 55" finiva per pescare una
     * carta di MEP.
     */
    @Test
    fun testIlSetNonDiventaUnAltro() {
        val kadabra = card("Kadabra")!!
        val mep = SetCodeMapperBridge.normalize("MEP")
        assertNotEquals(
            "il set di Kadabra non deve confondersi con MEP",
            mep?.lowercase(),
            kadabra.set?.lowercase()
        )
    }

    @Test
    fun testNomiConApostrofoEPossessivo() {
        assertNotNull("Lana's Aid", card("Lana's Aid"))
        assertNotNull("Boss's Orders", card("Boss's Orders"))
        assertNotNull("Colress's Tenacity", card("Colress's Tenacity"))
        assertEquals("trainer", card("Boss's Orders")!!.type)
    }

    @Test
    fun testNomeConRuleBox() {
        val fezandipiti = card("Fezandipiti ex")
        assertNotNull("Fezandipiti ex non riconosciuto", fezandipiti)
        assertEquals("142", fezandipiti!!.number)
    }

    /** Le energie col simbolo fra graffe: "1 Basic {P} Energy MEE 5". */
    @Test
    fun testEnergieColSimbolo() {
        val basic = card("Basic {P} Energy")
        assertNotNull("energia base col simbolo non riconosciuta", basic)
        assertEquals("energy", basic!!.type)
        assertEquals("5", basic.number)

        val telepathic = card("Telepathic {P} Energy")
        assertNotNull("energia speciale col simbolo non riconosciuta", telepathic)
        assertEquals(4, telepathic!!.qty)
    }

    /** L'intestazione italiana "Carte Allenatore:" deve aprire la sezione. */
    @Test
    fun testIntestazioneAllenatoreInItaliano() {
        assertEquals("trainer", card("Night Stretcher")!!.type)
        assertEquals("trainer", card("Rare Candy")!!.type)
    }
}

/** Il mapper sta in un altro package: questo evita di importarlo nel test. */
private object SetCodeMapperBridge {
    fun normalize(raw: String): String? =
        com.emabuia.pokevault.data.remote.SetCodeMapper.normalizeDecklistSetCode(raw)
}
