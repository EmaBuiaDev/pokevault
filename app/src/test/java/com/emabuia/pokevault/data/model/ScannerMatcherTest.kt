package com.emabuia.pokevault.data.model

import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgCardSet
import com.emabuia.pokevault.ocr.CardSupertype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qui si decide cosa vede l'utente: una carta da confermare, la rosa delle tre
 * piu' probabili, o l'aggiunta automatica in modalita' continua. Sono le soglie
 * piu' facili da rompere per sbaglio, quindi vanno fissate dai test.
 */
class ScannerMatcherTest {

    private fun card(
        id: String = "c1",
        name: String,
        printedTotal: Int = 0,
        hp: String? = null,
        supertype: String = "Pokémon"
    ) = TcgCard(
        id = id,
        name = name,
        supertype = supertype,
        hp = hp,
        set = TcgCardSet(id = "me04__ita", name = "Caos Nascente", series = "Mega", printedTotal = printedTotal),
        number = "67"
    )

    private fun signals(
        name: String? = null,
        total: String? = null,
        hp: Int? = null,
        supertype: CardSupertype = CardSupertype.POKEMON
    ) = ScannerMatcher.Signals(name = name, number = "67", setTotal = total, hp = hp, supertype = supertype)

    // ── Classifica ──────────────────────────────────────────────────

    @Test
    fun `il totale esatto batte lo stesso nome in un altra espansione`() {
        val ranked = ScannerMatcher.rank(
            cards = listOf(
                card(id = "sbagliata", name = "Charizard ex", printedTotal = 191),
                card(id = "giusta", name = "Charizard ex", printedTotal = 87)
            ),
            signals = signals(name = "Charizard ex", total = "87")
        )

        assertEquals("giusta", ranked.first().card.id)
        assertTrue(ranked.first().totalExact)
    }

    @Test
    fun `a pari totale ordina per somiglianza del nome`() {
        val ranked = ScannerMatcher.rank(
            cards = listOf(
                card(id = "pikachu", name = "Pikachu", printedTotal = 87),
                card(id = "charizard", name = "Charizard ex", printedTotal = 87)
            ),
            signals = signals(name = "Charizard ex", total = "87")
        )

        assertEquals("charizard", ranked.first().card.id)
    }

    @Test
    fun `gli HP letti fanno da spareggio`() {
        val ranked = ScannerMatcher.rank(
            cards = listOf(
                card(id = "senza-hp", name = "Charizard ex", printedTotal = 87, hp = "180"),
                card(id = "con-hp", name = "Charizard ex", printedTotal = 87, hp = "330")
            ),
            signals = signals(name = "Charizard ex", total = "87", hp = 330)
        )

        assertEquals("con-hp", ranked.first().card.id)
    }

    @Test
    fun `il totale incompatibile penalizza senza escludere`() {
        val ranked = ScannerMatcher.rank(
            cards = listOf(card(name = "Charizard ex", printedTotal = 191)),
            signals = signals(name = "Charizard ex", total = "87")
        )

        assertEquals("la carta resta in lista: il totale puo' essere letto male", 1, ranked.size)
        assertFalse(ranked.first().totalExact)
    }

    @Test
    fun `un totale vicino non vale come esatto`() {
        val ranked = ScannerMatcher.rank(
            cards = listOf(card(name = "Charizard ex", printedTotal = 88)),
            signals = signals(name = "Charizard ex", total = "87")
        )

        assertFalse(ranked.first().totalExact)
    }

    @Test
    fun `senza carte non c e classifica`() {
        assertTrue(ScannerMatcher.rank(emptyList(), signals(name = "Charizard ex")).isEmpty())
    }

    // ── Supertype ───────────────────────────────────────────────────

    @Test
    fun `un allenatore letto restringe il pool`() {
        val ranked = ScannerMatcher.rank(
            cards = listOf(
                card(id = "pokemon", name = "Charizard ex"),
                card(id = "allenatore", name = "Ricerca Insolita", supertype = "Trainer")
            ),
            signals = signals(supertype = CardSupertype.TRAINER)
        )

        assertEquals(1, ranked.size)
        assertEquals("allenatore", ranked.first().card.id)
    }

    @Test
    fun `il supertype non svuota mai il pool`() {
        // Se nessun candidato e un allenatore, la lettura era sbagliata: meglio
        // proporre le carte che ci sono che non proporre niente.
        val ranked = ScannerMatcher.rank(
            cards = listOf(card(name = "Charizard ex")),
            signals = signals(supertype = CardSupertype.TRAINER)
        )

        assertEquals(1, ranked.size)
    }

    // ── Verdetto: proporre una carta o la rosa ───────────────────────

    @Test
    fun `un solo candidato si propone anche con nome debole`() {
        // Il numero ha gia' fatto il lavoro: basta che il nome non smentisca.
        val ranked = ScannerMatcher.rank(
            cards = listOf(card(name = "Charizard ex")),
            signals = signals(name = "Charizurd ex")
        )

        assertTrue(ScannerMatcher.hasClearWinner(ranked))
    }

    @Test
    fun `un solo candidato col nome di un altra carta non si propone`() {
        val ranked = ScannerMatcher.rank(
            cards = listOf(card(name = "Charizard ex")),
            signals = signals(name = "Pikachu")
        )

        assertFalse(ScannerMatcher.hasClearWinner(ranked))
    }

    @Test
    fun `due candidati troppo vicini vanno alla rosa`() {
        val ranked = ScannerMatcher.rank(
            cards = listOf(
                card(id = "a", name = "Charizard ex", printedTotal = 87),
                card(id = "b", name = "Charizard", printedTotal = 87)
            ),
            signals = signals(name = "Charizard ex", total = "87")
        )

        assertFalse("nomi quasi identici: decide l'utente", ScannerMatcher.hasClearWinner(ranked))
    }

    @Test
    fun `un candidato staccato si propone da solo`() {
        val ranked = ScannerMatcher.rank(
            cards = listOf(
                card(id = "giusta", name = "Charizard ex", printedTotal = 87),
                card(id = "altra", name = "Pikachu", printedTotal = 191)
            ),
            signals = signals(name = "Charizard ex", total = "87")
        )

        assertTrue(ScannerMatcher.hasClearWinner(ranked))
        assertEquals("giusta", ranked.first().card.id)
    }

    // ── Verdetto: aggiungere senza conferma ─────────────────────────

    @Test
    fun `in continuo il totale esatto non e obbligatorio`() {
        // Il totale stampato non e' noto per tutte le espansioni ITA: pretenderlo
        // qui vorrebbe dire una modalita' continua che non scatta quasi mai.
        // Il numero e' comunque un filtro esatto a monte e il candidato e' solo.
        val ranked = ScannerMatcher.rank(
            cards = listOf(card(name = "Charizard ex", printedTotal = 191)),
            signals = signals(name = "Charizard ex", total = "87")
        )

        assertTrue(ScannerMatcher.isCertain(ranked))
    }

    @Test
    fun `in continuo due candidati vicini non bastano`() {
        // Qui sta la vera protezione: se due carte si somigliano, nessuna entra
        // da sola, per quanto i nomi siano letti bene.
        val ranked = ScannerMatcher.rank(
            cards = listOf(
                card(id = "a", name = "Charizard ex", printedTotal = 87),
                card(id = "b", name = "Charizard", printedTotal = 87)
            ),
            signals = signals(name = "Charizard ex", total = "87")
        )

        assertFalse(ScannerMatcher.isCertain(ranked))
    }

    @Test
    fun `in continuo serve anche il nome quasi identico`() {
        val ranked = ScannerMatcher.rank(
            cards = listOf(card(name = "Charizard ex", printedTotal = 87)),
            signals = signals(name = "Chzrizurd ux", total = "87")
        )

        assertTrue("col numero giusto si propone", ScannerMatcher.hasClearWinner(ranked))
        assertFalse("ma senza un tocco di conferma no", ScannerMatcher.isCertain(ranked))
    }

    @Test
    fun `verdetto pieno e la carta entra da sola`() {
        val ranked = ScannerMatcher.rank(
            cards = listOf(card(name = "Charizard ex", printedTotal = 87)),
            signals = signals(name = "Charizard ex", total = "87")
        )

        assertTrue(ScannerMatcher.isCertain(ranked))
    }

    @Test
    fun `senza candidati non c e niente da aggiungere`() {
        assertFalse(ScannerMatcher.isCertain(emptyList()))
        assertFalse(ScannerMatcher.hasClearWinner(emptyList()))
    }

    // ── Nomi ────────────────────────────────────────────────────────

    @Test
    fun `gli accenti non contano nel confronto`() {
        // I nomi dei Pokemon sono identici in ogni lingua, ma l'OCR non sempre
        // prende i segni diacritici.
        assertEquals(1.0, ScannerMatcher.nameSimilarity("Flabebe", "Flabébé"), 0.001)
    }

    @Test
    fun `un nome contenuto nell altro vale molto`() {
        assertTrue(ScannerMatcher.nameSimilarity("Charizard", "Charizard ex") > 0.9)
    }

    @Test
    fun `due carte diverse non si somigliano`() {
        assertTrue(ScannerMatcher.nameSimilarity("Pikachu", "Charizard ex") < 0.42)
    }

    @Test
    fun `le parole di gioco non sono un nome`() {
        assertTrue(ScannerMatcher.isUsableName("Charizard"))
        assertFalse("e' una parola di meccanica, non un nome", ScannerMatcher.isUsableName("Energia"))
        assertFalse("troppo corto per cercare", ScannerMatcher.isUsableName("PV"))
        assertFalse(ScannerMatcher.isUsableName(null))
        assertFalse(ScannerMatcher.isUsableName("  "))
    }

    @Test
    fun `la normalizzazione butta via la meccanica e tiene il nome`() {
        assertEquals("charizard ex", ScannerMatcher.normalizeName("Basic Charizard ex"))
    }
}
