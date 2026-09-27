package com.emabuia.pokevault.data.model

import com.emabuia.pokevault.data.remote.SetCodeMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 120 decklist vere da tornei Limitless (settembre 2026), 3065 righe, in due
 * forme: il testo di PTCG Live e lo stesso contenuto come CSV con intestazione.
 *
 * Il parser e' il primo anello dell'import: una riga divisa male cerca la
 * carta sbagliata e nessun passo dopo se ne accorge. Qui ogni riga deve dare
 * esattamente quantita', nome, set e numero di partenza -- anche per i codici
 * che cominciano con una cifra (30C) e per quelli nuovi (PBL).
 *
 * I file stanno in src/test/resources/decklists e sono generati dagli
 * standings di play.limitlesstcg.com: per aggiornarli basta rigenerarli.
 */
class DecklistTorneiTest {

    private data class Expected(
        val deck: Int,
        val section: String,
        val qty: Int,
        val name: String,
        val set: String,
        val number: String
    )

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("decklists/$name")) { "manca $name" }
            .readText(Charsets.UTF_8)

    private val expected: List<Expected> = resource("limitless-expected.tsv")
        .lines()
        .filter { it.isNotBlank() }
        .map { line ->
            val f = line.split('\t')
            Expected(f[0].toInt(), f[1], f[2].toInt(), f[3], f[4], f[5])
        }

    /** I deck di un file, per indice: il separatore e' "===== N". */
    private fun decks(fileName: String): Map<Int, String> =
        resource(fileName)
            .split(Regex("""(?m)^===== """))
            .filter { it.isNotBlank() }
            .associate { chunk ->
                val index = chunk.lineSequence().first().trim().toInt()
                index to chunk.substringAfter('\n')
            }

    private fun assertEveryLineParses(fileName: String, checkSection: Boolean) {
        val byDeck = decks(fileName)
        val failures = mutableListOf<String>()

        expected.groupBy { it.deck }.forEach { (deckIndex, rows) ->
            val result = DeckImportParser.parse(byDeck.getValue(deckIndex))
            if (result.errors.isNotEmpty()) failures += "deck $deckIndex errori: ${result.errors}"
            if (result.cards.size != rows.size) {
                failures += "deck $deckIndex: ${result.cards.size} carte invece di ${rows.size}"
                return@forEach
            }
            rows.zip(result.cards).forEach { (want, got) ->
                val wantSet = SetCodeMapper.normalizeDecklistSetCode(want.set)
                val sectionOk = !checkSection || got.type == want.section
                if (got.qty != want.qty || got.name != want.name || got.set != wantSet ||
                    got.number != want.number || !sectionOk
                ) {
                    failures += "deck $deckIndex: atteso $want, letto $got"
                }
            }
        }

        assertTrue(
            "${failures.size} righe sbagliate su ${expected.size}:\n" + failures.take(20).joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun `ogni riga del formato PTCG Live si legge com'era`() {
        assertEveryLineParses("limitless-ptcgl.txt", checkSection = true)
    }

    @Test
    fun `ogni riga CSV si legge come la stessa in PTCG Live`() {
        // Il CSV non ha sezioni: il tipo si inferisce dal nome, quindi qui
        // si controllano solo quantita', nome, set e numero.
        assertEveryLineParses("limitless-csv.txt", checkSection = false)
    }

    /**
     * Il parser normalizza il codice, e poi findExactItalianCard lo normalizza
     * di nuovo. Se il secondo passaggio lo cambiasse, l'import da testo
     * cercherebbe in un'altra espansione rispetto a quello da Limitless.
     */
    @Test
    fun `normalizzare due volte un codice non lo cambia`() {
        val codes = expected.mapTo(sortedSetOf()) { it.set } + listOf("30C", "PBL", "ME05", "MEP", "BLK", "WHT")
        codes.forEach { code ->
            val once = SetCodeMapper.normalizeDecklistSetCode(code)
            assertEquals("$code non e' stabile", once, SetCodeMapper.normalizeDecklistSetCode(once))
        }
    }

    @Test
    fun `il campione copre i codici che l'import deve riconoscere`() {
        val codes = expected.mapTo(mutableSetOf()) { it.set }
        assertTrue("manca 30C nel campione", "30C" in codes)
        assertTrue("manca PBL nel campione", "PBL" in codes)
        assertEquals(3065, expected.size)
    }
}
