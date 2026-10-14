package com.emabuia.pokevault.data.english

import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.data.catalog.CardRef
import com.emabuia.pokevault.data.catalog.CatalogLang
import com.emabuia.pokevault.data.italian.ItalianCardRecord
import com.emabuia.pokevault.data.italian.ItalianExpansionSummary
import com.emabuia.pokevault.data.trade.TradeCardKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Il catalogo inglese in app (piano carte EN, Fase 3). */
class EnglishCatalogTest {

    private val base = "https://worker.example/"
    private val repo = EnglishCatalogRepository(baseUrl = { base }, enabled = { true })

    @Test
    fun `il cardId EN da cartella e numero, come l'italiano`() {
        val ref = EnglishCatalogIds.reference("SV08_EN_012.webp")!!
        assertEquals("SV08", ref.folderName)
        assertEquals("12", ref.cardNumber)
        assertEquals("SWSH026", EnglishCatalogIds.reference("SWSHP_EN_SWSH026.webp")!!.cardNumber)
        assertEquals("30TH-C", EnglishCatalogIds.reference("30TH-C_EN_7.webp")!!.folderName)
        assertNull(EnglishCatalogIds.reference("SV08_IT_12.png"))
    }

    @Test
    fun `la carta EN di un set gemello ha la chiave degli scambi della carta italiana`() {
        val api = EnglishCatalogIds.apiCardId(EnglishCatalogIds.reference("SV08_EN_25.webp")!!)
        assertEquals("eng:sv08:25", api)
        assertEquals(TradeCardKey.fromApiCardId("ita:sv08:25"), TradeCardKey.fromApiCardId(api))
        assertEquals(CatalogLang.EN, CardRef.langOf(api))
    }

    @Test
    fun `immagini e loghi dalla cartella en`() {
        val ref = EnglishCatalogIds.reference("PL3_EN_1.webp")!!
        assertEquals("https://worker.example/images/en/PL3/1?size=low", EnglishCatalogIds.cardImageUrl(base, ref, "low"))
        assertEquals("https://worker.example/sets/PL3/image?source=en", EnglishCatalogIds.setImageUrl(base, "pl3", symbol = false))
        assertEquals("https://worker.example/sets/PL3/symbol?source=en", EnglishCatalogIds.setImageUrl(base, "pl3", symbol = true))
    }

    @Test
    fun `supertipo dai PS e dal nome`() {
        assertEquals("Pokémon", EnglishCatalogIds.supertypeOf(ItalianCardRecord(nome = "Pikachu", ps = "60")))
        assertEquals("Energy", EnglishCatalogIds.supertypeOf(ItalianCardRecord(nome = "Basic Fire Energy")))
        assertEquals("Energy", EnglishCatalogIds.supertypeOf(ItalianCardRecord(nome = "Double Turbo Energy")))
        assertEquals("Trainer", EnglishCatalogIds.supertypeOf(ItalianCardRecord(nome = "Energy Retrieval")))
        assertEquals("Trainer", EnglishCatalogIds.supertypeOf(ItalianCardRecord(nome = "Energy Switch")))
        assertEquals("Trainer", EnglishCatalogIds.supertypeOf(ItalianCardRecord(nome = "Professor's Research")))
    }

    @Test
    fun `set EN in app`() {
        val set = repo.toTcgSet(
            ItalianExpansionSummary(id = "sv08", name = "Surging Sparks", cardCount = 252, officialCount = 191, baseSetCode = "SV08", releaseDate = "2024-11-08", series = "Scarlet & Violet", abbreviation = "SSP")
        )
        assertEquals("sv08__eng", set.id)
        assertEquals("ENG", set.language)
        assertEquals("Surging Sparks", set.name)
        assertEquals(191, set.printedTotal)
        assertEquals(252, set.total)
        assertEquals("SSP", set.abbreviation)
        assertTrue(set.images.logo.endsWith("/sets/SV08/image?source=en"))
    }

    @Test
    fun `carta EN in app`() {
        val set = repo.toTcgSet(ItalianExpansionSummary(id = "sv08", name = "Surging Sparks", cardCount = 252, baseSetCode = "SV08"))
        val card = repo.toTcgCard(
            ItalianCardRecord(cardId = "SV08_EN_25.webp", espansioneId = "sv08", nome = "Volcarona", tipo = "Fire", ps = "140", rarity = "Common", stage = "Stage1", illustratore = "Saki Hayashiro", stampe = listOf("Normal", "Reverse")),
            set
        )!!
        assertEquals("eng:sv08:25", card.id)
        assertEquals("25", card.number)
        assertEquals("Pokémon", card.supertype)
        assertEquals(listOf("Fire"), card.types)
        assertEquals(listOf("Stage1"), card.subtypes)
        assertEquals("sv08__eng", card.set?.id)
        assertEquals(listOf("Normal", "Reverse"), card.variants)
        assertTrue(card.images.large.endsWith("/images/en/SV08/25?size=high"))
        assertNull(repo.toTcgCard(ItalianCardRecord(cardId = "SV08_IT_25.png", espansioneId = "sv08", nome = "X"), set))
    }

    @Test
    fun `in prod il catalogo inglese e' acceso e parla col Worker di produzione`() {
        // Dalla 3.1.8: acceso nella build, senza aspettare /v1/app-config.
        assumeTrue(BuildConfig.FLAVOR == "prod")
        assertTrue(BuildConfig.EN_CATALOG_ENABLED)
        assertEquals("https://pokevault-proxy.pokevault-emanu.workers.dev", BuildConfig.EN_CATALOG_URL)
        assertTrue(EnglishCatalogRepository().isEnabled)
    }

    @Test
    fun `nello staging il catalogo inglese parla col Worker di staging del catalogo`() {
        assumeTrue(BuildConfig.FLAVOR == "staging")
        assertTrue(BuildConfig.EN_CATALOG_ENABLED)
        assertEquals("https://pokevault-catalog-staging.pokevault-emanu.workers.dev", BuildConfig.EN_CATALOG_URL)
    }
    private fun rec(id: String, exp: String, nome: String) = ItalianCardRecord(cardId = id, espansioneId = exp, nome = nome)

    @Test
    fun `ricerca EN per nome - prima il nome identico, poi chi comincia cosi'`() {
        val records = listOf(
            rec("SV08_EN_57.webp", "sv08", "Pikachu ex"),
            rec("SV01_EN_63.webp", "sv01", "Pikachu"),
            rec("SV03_EN_1.webp", "sv03", "Raichu"),
            rec("SV05_EN_9.webp", "sv05", "Pikachu's Friend")
        )
        val found = EnglishCatalogSearch.byName(records, "pikachu", 300).map { it.cardId }
        assertEquals("SV01_EN_63.webp", found.first())
        assertFalse("SV03_EN_1.webp" in found)
        assertEquals(3, found.size)
        assertTrue(EnglishCatalogSearch.byName(records, "  ", 300).isEmpty())
    }

    @Test
    fun `ricerca EN per numero - il totale stampato sceglie l'espansione`() {
        val records = listOf(
            rec("SV08_EN_25.webp", "sv08", "Volcarona"),
            rec("SV01_EN_25.webp", "sv01", "Sprigatito"),
            rec("SV01_EN_26.webp", "sv01", "Floragato")
        )
        val totals = mapOf("sv08" to listOf(191, 252), "sv01" to listOf(198, 258))
        assertEquals(listOf("SV08_EN_25.webp"), EnglishCatalogSearch.byNumber(records, "025", 191, totals, 300).map { it.cardId })
        // Senza totale, o con un totale che nessuno stampa, tutte le carte con quel numero.
        assertEquals(2, EnglishCatalogSearch.byNumber(records, "25", null, totals, 300).size)
        assertEquals(2, EnglishCatalogSearch.byNumber(records, "25", 999, totals, 300).size)
    }

    @Test
    fun `album illustratori EN - la carta porta l'id eng e la miniatura inglese`() {
        val ref = EnglishCatalogIds.illustratorRef(base, "SV08_EN_025.webp")!!
        assertEquals("eng:sv08:25", ref.apiCardId)
        assertEquals("sv08", ref.expansion)
        assertTrue(ref.previewUrl.endsWith("/images/en/SV08/25?size=low"))
        assertNull(EnglishCatalogIds.illustratorRef(base, "SV08_IT_25.png"))
    }

    @Test
    fun `la cartella R2 porta all'espansione EN, anche quando sono diverse`() {
        val summaries = listOf(
            ItalianExpansionSummary(id = "sv08", baseSetCode = "SV08"),
            ItalianExpansionSummary(id = "sv8pt5", baseSetCode = "SV08.5")
        )
        assertEquals("sv08", repo.expansionIdForFolder(summaries, "sv08"))
        assertEquals("sv8pt5", repo.expansionIdForFolder(summaries, "sv08.5"))
        assertNull(repo.expansionIdForFolder(summaries, "xy1"))
    }

    @Test
    fun `ricerca EN coi nomi gia' normalizzati, stesso risultato`() {
        val records = listOf(
            ItalianCardRecord(cardId = "SV01_EN_63.webp", espansioneId = "sv01", nome = "Pikachu"),
            ItalianCardRecord(cardId = "SV08_EN_57.webp", espansioneId = "sv08", nome = "Pikachu ex")
        )
        val names = EnglishCatalogSearch.normalizedNames(records)
        assertEquals(EnglishCatalogSearch.byName(records, "pika", 300), EnglishCatalogSearch.byName(records, "pika", 300, names))
    }

    private val scanRecords = listOf(
        ItalianCardRecord(cardId = "SV08_EN_25.webp", espansioneId = "sv08", nome = "Pikachu"),
        ItalianCardRecord(cardId = "SV01_EN_25.webp", espansioneId = "sv01", nome = "Sprigatito"),
        ItalianCardRecord(cardId = "SV03_EN_25.webp", espansioneId = "sv03", nome = "Pikachu"),
        ItalianCardRecord(cardId = "SV08_EN_26.webp", espansioneId = "sv08", nome = "Raichu")
    )
    private val scanNames = EnglishCatalogSearch.normalizedNames(scanRecords)
    private val scanTotals = mapOf("sv08" to listOf(191), "sv01" to listOf(198), "sv03" to listOf(197))

    private fun scan(name: String?, number: String?, total: String?, targets: Set<String>? = null) =
        EnglishCatalogSearch.scannerCandidates(scanRecords, scanNames, name, number, total, targets, scanTotals, 10).map { it.cardId }

    @Test
    fun `scanner EN - numero e totale scelgono l'espansione`() {
        assertEquals("SV08_EN_25.webp", scan("Pikachu", "025", "191").first())
        assertEquals("SV03_EN_25.webp", scan("Pikachu", "25", "197").first())
    }

    @Test
    fun `scanner EN - il nome scarta le carte sbagliate, ma se scarta tutto vale il numero`() {
        assertEquals(listOf("SV08_EN_25.webp", "SV03_EN_25.webp").toSet(), scan("Pikachu", "25", null).toSet())
        // Nome illeggibile: restano le carte col numero giusto.
        assertEquals(3, scan("Xqzzv", "25", null).size)
    }

    @Test
    fun `scanner EN - il set letto sulla carta filtra`() {
        assertEquals(listOf("SV01_EN_25.webp"), scan(null, "25", null, targets = setOf("sv01")))
        assertTrue(scan(null, null, null).isEmpty())
    }

    @Test
    fun `scanner EN - il set letto si riconosce da id, sigla o forma normalizzata`() {
        val summaries = listOf(
            ItalianExpansionSummary(id = "sv08", abbreviation = "SSP", baseSetCode = "SV08"),
            ItalianExpansionSummary(id = "sv01", abbreviation = "SVI")
        )
        assertEquals(setOf("sv08"), repo.scannerTargetExpansions(summaries, listOf("ssp")))
        assertEquals(setOf("sv08"), repo.scannerTargetExpansions(summaries, listOf("sv8", "SSP")))
        assertEquals(setOf("sv08"), repo.scannerTargetExpansions(summaries, listOf("SV08")))
        assertNull(repo.scannerTargetExpansions(summaries, listOf("zzz")))
        assertNull(repo.scannerTargetExpansions(summaries, emptyList()))
    }

    @Test
    fun `scanner EN - il totale stampato e' l'ufficiale, non il conteggio con le segrete`() {
        assertEquals(listOf(191), repo.printedTotalsOf(ItalianExpansionSummary(id = "sv08", cardCount = 252, officialCount = 191)))
        assertEquals(listOf(30), repo.printedTotalsOf(ItalianExpansionSummary(id = "x", cardCount = 30)))
    }

    private fun tcg(id: String, number: String, name: String) =
        com.emabuia.pokevault.data.remote.TcgCard(id = id, number = number, name = name)

    @Test
    fun `decklist EN - le energie base si cercano anche senza Basic`() {
        assertEquals(listOf("basic fire energy", "fire energy"), EnglishCatalogSearch.decklistNames("Basic Fire Energy"))
        assertEquals(listOf("dragapult ex"), EnglishCatalogSearch.decklistNames("Dragapult ex"))
        // "Basic" davanti a qualcosa che non e' un'energia resta com'e'.
        assertEquals(listOf("basic ball"), EnglishCatalogSearch.decklistNames("Basic Ball"))
    }

    @Test
    fun `decklist EN - stesso numero e, per i Pokemon, stesso nome`() {
        val set = listOf(tcg("eng:sv06:130", "130", "Dragapult ex"), tcg("eng:sv06:131", "131", "Drakloak"))
        assertEquals("eng:sv06:130", EnglishCatalogSearch.decklistMatch(set, "130", "Dragapult ex", requireNameMatch = true)?.id)
        // Numero giusto, nome sbagliato: un Pokemon no, un Allenatore si'.
        assertNull(EnglishCatalogSearch.decklistMatch(set, "130", "Pikachu", requireNameMatch = true))
        assertEquals("eng:sv06:130", EnglishCatalogSearch.decklistMatch(set, "130", "Pikachu", requireNameMatch = false)?.id)
        assertEquals("eng:sv06:131", EnglishCatalogSearch.decklistMatch(set, "0131", null, requireNameMatch = true)?.id)
        assertNull(EnglishCatalogSearch.decklistMatch(set, null, "Drakloak", requireNameMatch = true))
    }

    @Test
    fun `decklist EN - per nome il nome coincide e vince lo stesso numero`() {
        val records = listOf(
            ItalianCardRecord(cardId = "SV01_EN_5.webp", espansioneId = "sv01", nome = "Toucannon ex"),
            ItalianCardRecord(cardId = "SV03_EN_9.webp", espansioneId = "sv03", nome = "Toucannon"),
            ItalianCardRecord(cardId = "SV05_EN_12.webp", espansioneId = "sv05", nome = "Toucannon")
        )
        val names = EnglishCatalogSearch.normalizedNames(records)
        val found = EnglishCatalogSearch.decklistByName(records, listOf("toucannon"), names, "12").map { it.cardId }
        assertEquals(listOf("SV05_EN_12.webp", "SV03_EN_9.webp"), found)
    }
}
