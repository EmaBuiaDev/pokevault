package com.emabuia.pokevault.data.english

import android.content.Context
import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.data.catalog.CardNameMatch
import com.emabuia.pokevault.data.catalog.CardRef
import com.emabuia.pokevault.data.catalog.CatalogLang
import com.emabuia.pokevault.data.catalog.CatalogSetId
import com.emabuia.pokevault.data.italian.ItalianCardRecord
import com.emabuia.pokevault.data.italian.ItalianCatalogNormalizer
import com.emabuia.pokevault.data.italian.ItalianExpansionSummary
import com.emabuia.pokevault.data.italian.ItalianIllustratorsNormalizer
import com.emabuia.pokevault.data.italian.ItalianIllustratorsResponse
import com.emabuia.pokevault.data.italian.ItalianImageReference
import com.emabuia.pokevault.data.italian.ItalianPrintedTotals
import com.emabuia.pokevault.data.local.ItalianTranslations
import com.emabuia.pokevault.data.remote.CardImages
import com.emabuia.pokevault.data.remote.SetImages
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgCardSet
import com.emabuia.pokevault.data.remote.TcgSet
import com.emabuia.pokevault.util.IllustratorCardRef
import com.emabuia.pokevault.util.IllustratorEntry
import com.emabuia.pokevault.util.Illustrators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Le carte del catalogo INGLESE: id, immagini e loghi (piano carte EN).
 *
 * Il Worker serve le carte inglesi nello stesso formato delle italiane (stesso
 * mapCardRow), quindi i record sono [ItalianCardRecord]; cambiano solo il
 * cardId ("SV08_EN_12.webp") e i percorsi (/images/en/, ?source=en).
 */
object EnglishCatalogIds {

    private val imageIdRegex = Regex(
        "^([A-Za-z0-9-]+)_EN_([A-Za-z0-9_]+)\\.(png|webp|jpe?g)$",
        RegexOption.IGNORE_CASE
    )

    /** "SV08_EN_012.webp" -> cartella SV08, numero 12 (i promo a lettere restano come sono). */
    fun reference(cardId: String): ItalianImageReference? {
        val match = imageIdRegex.matchEntire(cardId.trim()) ?: return null
        val rawNumber = match.groupValues[2].trim()
        return ItalianImageReference(
            setCode = match.groupValues[1].trim(),
            cardNumber = rawNumber.toIntOrNull()?.toString() ?: rawNumber
        )
    }

    /** L'apiCardId della carta: "eng:sv08:12". Per i set gemelli e' quello italiano con eng: davanti. */
    fun apiCardId(reference: ItalianImageReference): String =
        CardRef.of(CatalogLang.EN, reference.setCode, reference.cardNumber).apiCardId

    /** Una carta EN per gli album degli illustratori: id in collezione, espansione e miniatura. */
    fun illustratorRef(baseUrl: String, cardId: String): IllustratorCardRef? =
        reference(cardId)?.let { reference ->
            IllustratorCardRef(
                apiCardId = apiCardId(reference),
                expansion = reference.setCode.lowercase(Locale.ROOT),
                previewUrl = cardImageUrl(baseUrl, reference, "low")
            )
        }

    fun cardImageUrl(baseUrl: String, reference: ItalianImageReference, size: String): String =
        "${baseUrl.trim().trimEnd('/')}/images/en/${reference.folderName}/${reference.cardNumber}?size=$size"

    /** Logo o simbolo del set, dalla cartella en/ di R2. */
    fun setImageUrl(baseUrl: String, folder: String, symbol: Boolean): String =
        "${baseUrl.trim().trimEnd('/')}/sets/${folder.trim().uppercase(Locale.ROOT)}/${if (symbol) "symbol" else "image"}?source=en"

    /**
     * Pokemon con i PS; fra le carte senza, le Energie sono quelle il cui nome
     * FINISCE per "Energy" ("Fire Energy", "Double Turbo Energy"). In inglese
     * l'inizio non basta: "Energy Retrieval" e "Energy Switch" sono Allenatori.
     */
    fun supertypeOf(record: ItalianCardRecord): String {
        if ((record.ps?.toIntOrNull() ?: 0) > 0) return "Pokémon"
        return if (record.nome.trim().endsWith("Energy", ignoreCase = true)) "Energy" else "Trainer"
    }
}

/** La ricerca sul catalogo EN completo: parti pure, senza rete. */
internal object EnglishCatalogSearch {

    fun byName(records: List<ItalianCardRecord>, query: String, limit: Int): List<ItalianCardRecord> {
        val normalizedQuery = CardNameMatch.normalize(CardNameMatch.sanitize(query))
        if (normalizedQuery.isBlank()) return emptyList()
        val tokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        return records.asSequence()
            .mapNotNull { record ->
                val score = CardNameMatch.score(CardNameMatch.normalize(record.nome), normalizedQuery, tokens)
                if (score <= 0) null else record to score
            }
            .sortedWith(
                compareByDescending<Pair<ItalianCardRecord, Int>> { it.second }
                    .thenBy { numberOf(it.first).toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.first.espansioneId }
            )
            .take(limit.coerceIn(1, 300))
            .map { it.first }
            .toList()
    }

    /**
     * Le carte con quel numero. Col totale stampato si tengono le espansioni
     * che lo stampano (o quasi, vedi ItalianPrintedTotals); se nessuna lo
     * stampa si tiene tutto, come nella ricerca italiana.
     */
    fun byNumber(
        records: List<ItalianCardRecord>,
        number: String,
        printedTotal: Int?,
        totalsByExpansion: Map<String, List<Int>>,
        limit: Int
    ): List<ItalianCardRecord> {
        val target = number.trim().trimStart('0').ifBlank { number.trim() }
        if (target.isBlank()) return emptyList()
        val matching = records.filter { numberOf(it).equals(target, ignoreCase = true) }
        val scored = matching.map { record ->
            record to ItalianPrintedTotals.matchScore(
                totalsByExpansion[record.espansioneId.trim().lowercase(Locale.ROOT)].orEmpty(),
                printedTotal
            )
        }
        return ItalianPrintedTotals.keepBestMatches(scored)
            .sortedBy { it.espansioneId }
            .take(limit.coerceIn(1, 300))
    }

    private fun numberOf(record: ItalianCardRecord): String =
        EnglishCatalogIds.reference(record.cardId)?.cardNumber.orEmpty()
}

/**
 * Il catalogo inglese, dal Worker indicato in [BuildConfig.EN_CATALOG_URL]
 * (/v1/en/expansions e /v1/en/expansions/{id}/cards).
 *
 * Tutto a parte dal catalogo italiano: cache sue (memoria e la preferenza
 * `en_catalog_cache_v1` per l'elenco dei set), nessuna scrittura nelle cache
 * italiane. Si carica solo quando qualcuno apre la sezione inglese.
 */
class EnglishCatalogRepository(
    private val baseUrl: () -> String = { BuildConfig.EN_CATALOG_URL },
    private val enabled: () -> Boolean = { BuildConfig.EN_CATALOG_ENABLED }
) {

    companion object {
        private const val PREFS_NAME = "en_catalog_cache_v1"
        private const val KEY_EXPANSIONS_JSON = "expansions_json"
        private const val KEY_EXPANSIONS_AT = "expansions_at"
        private const val CACHE_TTL_MS = 5L * 60L * 1000L
        private const val LANGUAGE = "ENG"
        private const val CATALOG_DIR = "en_catalog"
        private const val CATALOG_FILE = "catalog.json"
        /** Il Worker lo rifa' al massimo ogni 6 ore: tenerlo di piu' non serve, di meno nemmeno. */
        private const val CATALOG_TTL_MS = 6L * 60L * 60L * 1000L
        private const val ILLUSTRATORS_TTL_MS = 30L * 60L * 1000L
    }

    val isEnabled: Boolean get() = enabled() && baseUrl().isNotBlank()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val expansionsMutex = Mutex()
    @Volatile private var memoryExpansions: List<ItalianExpansionSummary>? = null
    @Volatile private var memoryExpansionsAt: Long = 0L

    private val cardsMutex = Mutex()
    private val memoryCards = java.util.concurrent.ConcurrentHashMap<String, Pair<List<TcgCard>, Long>>()

    suspend fun getExpansions(context: Context?, forceRefresh: Boolean = false): Result<List<ItalianExpansionSummary>> =
        expansionsMutex.withLock {
            if (!isEnabled) return@withLock Result.success(emptyList())
            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                memoryExpansions?.takeIf { now - memoryExpansionsAt <= CACHE_TTL_MS }?.let { return@withLock Result.success(it) }
            }
            val network = runCatching {
                val raw = fetch("${baseUrl().trim().trimEnd('/')}/v1/en/expansions")
                withContext(Dispatchers.Default) { ItalianCatalogNormalizer.parseExpansionsResponse(raw) to raw }
            }
            network.onSuccess { (list, raw) ->
                memoryExpansions = list
                memoryExpansionsAt = now
                context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
                    ?.putString(KEY_EXPANSIONS_JSON, raw)?.putLong(KEY_EXPANSIONS_AT, now)?.apply()
            }
            if (network.isSuccess) return@withLock Result.success(network.getOrThrow().first)

            // Senza rete: l'ultimo elenco salvato, anche se vecchio.
            val stale = memoryExpansions ?: context
                ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.getString(KEY_EXPANSIONS_JSON, null)
                ?.let { runCatching { ItalianCatalogNormalizer.parseExpansionsResponse(it) }.getOrNull() }
            if (stale != null) Result.success(stale) else Result.failure(network.exceptionOrNull() ?: IllegalStateException("catalogo EN non raggiungibile"))
        }

    suspend fun getSets(context: Context?, forceRefresh: Boolean = false): Result<List<TcgSet>> =
        getExpansions(context, forceRefresh).map { list -> list.map(::toTcgSet) }

    suspend fun getSet(context: Context?, setId: String): TcgSet? {
        val expansionId = CatalogSetId.expansionOf(setId, CatalogLang.EN) ?: return null
        return getExpansions(context).getOrNull()?.firstOrNull { it.id.equals(expansionId, ignoreCase = true) }?.let(::toTcgSet)
    }

    suspend fun getCardsBySet(context: Context?, setId: String, forceRefresh: Boolean = false): Result<List<TcgCard>> {
        if (!isEnabled) return Result.success(emptyList())
        val expansionId = CatalogSetId.expansionOf(setId, CatalogLang.EN) ?: return Result.success(emptyList())
        return cardsMutex.withLock {
            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                memoryCards[expansionId]?.takeIf { now - it.second <= CACHE_TTL_MS }?.let { return@withLock Result.success(it.first) }
            }
            val summary = getExpansions(context).getOrNull()?.firstOrNull { it.id.equals(expansionId, ignoreCase = true) }
            val set = summary?.let(::toTcgSet) ?: TcgSet(id = setId, name = expansionId.uppercase(Locale.ROOT), language = LANGUAGE)
            runCatching {
                val raw = fetch("${baseUrl().trim().trimEnd('/')}/v1/en/expansions/$expansionId/cards")
                val records = withContext(Dispatchers.Default) { ItalianCatalogNormalizer.parseExpansionCardsResponse(raw) }
                records.mapNotNull { toTcgCard(it, set, collectionSetName = summary?.itName) }
                    .sortedBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }
            }.onSuccess { memoryCards[expansionId] = it to now }
        }
    }

    // ── Catalogo completo, per la ricerca ───────────────────────────────────

    private val catalogMutex = Mutex()
    @Volatile private var memoryCatalog: List<ItalianCardRecord>? = null
    @Volatile private var memoryCatalogAt: Long = 0L

    private fun catalogFile(context: Context): File =
        File(File(context.filesDir, CATALOG_DIR).apply { mkdirs() }, CATALOG_FILE)

    /**
     * Tutte le carte EN pubblicate (/en/catalog.json, una decina di MB, meno di
     * uno compresso). Si scarica solo alla prima ricerca in inglese e si tiene
     * su file: chi cerca solo in italiano non lo scarica mai.
     */
    suspend fun getAllCards(context: Context): Result<List<ItalianCardRecord>> = catalogMutex.withLock {
        if (!isEnabled) return@withLock Result.success(emptyList())
        val now = System.currentTimeMillis()
        memoryCatalog?.takeIf { now - memoryCatalogAt <= CATALOG_TTL_MS }?.let { return@withLock Result.success(it) }

        val file = catalogFile(context)
        if (file.exists() && now - file.lastModified() <= CATALOG_TTL_MS) {
            readCatalogFile(file)?.let { records ->
                memoryCatalog = records
                memoryCatalogAt = file.lastModified()
                return@withLock Result.success(records)
            }
        }

        val network = runCatching {
            val raw = fetch("${baseUrl().trim().trimEnd('/')}/en/catalog.json")
            val records = withContext(Dispatchers.Default) { ItalianCatalogNormalizer.parseExpansionCardsResponse(raw) }
            if (records.isEmpty()) error("catalogo EN vuoto")
            withContext(Dispatchers.IO) { runCatching { file.writeText(raw) } }
            records
        }
        network.onSuccess {
            memoryCatalog = it
            memoryCatalogAt = now
        }
        if (network.isSuccess) return@withLock network

        // Senza rete: la copia che c'e', anche se vecchia.
        val stale = memoryCatalog ?: file.takeIf { it.exists() }?.let { readCatalogFile(it) }
        if (stale != null) Result.success(stale) else Result.failure(network.exceptionOrNull() ?: IllegalStateException("catalogo EN non raggiungibile"))
    }

    private suspend fun readCatalogFile(file: File): List<ItalianCardRecord>? = withContext(Dispatchers.IO) {
        runCatching { ItalianCatalogNormalizer.parseExpansionCardsResponse(file.readText()) }
            .getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Le carte EN col nome piu' vicino a [query], stessa scala dell'italiano (CardNameMatch). */
    suspend fun searchByName(context: Context, query: String, limit: Int = 300): Result<List<TcgCard>> {
        if (!isEnabled) return Result.success(emptyList())
        val records = getAllCards(context).getOrElse { return Result.failure(it) }
        val summaries = getExpansions(context).getOrNull().orEmpty()
        return runCatching {
            withContext(Dispatchers.Default) {
                EnglishCatalogSearch.byName(records, query, limit).mapNotNull { toSearchCard(it, summaries) }
            }
        }
    }

    /** Le carte EN con quel numero; col totale ("25/191") tiene le espansioni che lo stampano. */
    suspend fun searchByNumber(context: Context, number: String, printedTotal: Int?, limit: Int = 300): Result<List<TcgCard>> {
        if (!isEnabled) return Result.success(emptyList())
        val records = getAllCards(context).getOrElse { return Result.failure(it) }
        val summaries = getExpansions(context).getOrNull().orEmpty()
        val totals = summaries.associate { it.id.lowercase(Locale.ROOT) to listOfNotNull(it.officialCount, it.cardCount) }
        return runCatching {
            withContext(Dispatchers.Default) {
                EnglishCatalogSearch.byNumber(records, number, printedTotal, totals, limit).mapNotNull { toSearchCard(it, summaries) }
            }
        }
    }

    private fun toSearchCard(record: ItalianCardRecord, summaries: List<ItalianExpansionSummary>): TcgCard? {
        val summary = summaries.firstOrNull { it.id.equals(record.espansioneId.trim(), ignoreCase = true) } ?: return null
        return toTcgCard(record, toTcgSet(summary), collectionSetName = summary.itName)
    }

    // ── Illustratori ────────────────────────────────────────────────────────

    private val illustratorsMutex = Mutex()
    @Volatile private var memoryIllustrators: ItalianIllustratorsResponse? = null
    @Volatile private var memoryIllustratorsAt: Long = 0L

    /** /v1/en/illustrators, stessa forma della rotta italiana. */
    suspend fun getIllustrators(forceRefresh: Boolean = false): Result<ItalianIllustratorsResponse> = illustratorsMutex.withLock {
        if (!isEnabled) return@withLock Result.success(ItalianIllustratorsResponse())
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            memoryIllustrators?.takeIf { now - memoryIllustratorsAt <= ILLUSTRATORS_TTL_MS }?.let { return@withLock Result.success(it) }
        }
        runCatching {
            val raw = fetch("${baseUrl().trim().trimEnd('/')}/v1/en/illustrators")
            withContext(Dispatchers.Default) { ItalianIllustratorsNormalizer.parseIllustrators(raw) }
        }.onSuccess {
            memoryIllustrators = it
            memoryIllustratorsAt = now
        }.recoverCatching { error -> memoryIllustrators ?: throw error }
    }

    /** Le voci della sezione illustratori per il catalogo inglese: stesse regole dell'italiano. */
    suspend fun illustratorIndex(forceRefresh: Boolean = false): List<IllustratorEntry> {
        val response = getIllustrators(forceRefresh).getOrNull() ?: return emptyList()
        val base = baseUrl()
        return withContext(Dispatchers.Default) {
            Illustrators.entries(response.illustrators.map { it.name to it.cardIds }) { cardId ->
                EnglishCatalogIds.illustratorRef(base, cardId)
            }
        }
    }

    suspend fun cardsWithoutIllustrator(): Int = getIllustrators().getOrNull()?.cardsWithoutIllustrator ?: 0

    /** Le carte EN di un illustratore, una chiamata per nome grezzo (quasi sempre uno). */
    suspend fun cardsByIllustrator(context: Context, entry: IllustratorEntry): List<TcgCard> {
        if (!isEnabled) return emptyList()
        val summaries = getExpansions(context).getOrNull().orEmpty()
        val records = entry.rawNames.flatMap { rawName ->
            // Spazi, punti e accenti nei nomi: senza encode la rotta risponde 404.
            val encoded = java.net.URLEncoder.encode(rawName.trim(), "UTF-8").replace("+", "%20")
            runCatching {
                val raw = fetch("${baseUrl().trim().trimEnd('/')}/v1/en/illustrators/$encoded/cards")
                withContext(Dispatchers.Default) { ItalianIllustratorsNormalizer.parseIllustratorCards(raw) }
            }.getOrDefault(emptyList())
        }
        return withContext(Dispatchers.Default) {
            records.mapNotNull { toSearchCard(it, summaries) }.distinctBy { it.id }
        }
    }

    /** Una carta EN gia' caricata (set aperti in questa sessione), o null. */
    fun findCachedCard(apiCardId: String): TcgCard? {
        return memoryCards.values.asSequence().flatMap { it.first.asSequence() }.firstOrNull { it.id == apiCardId }
    }

    fun toTcgSet(summary: ItalianExpansionSummary): TcgSet {
        val folder = (summary.baseSetCode ?: summary.id).trim().uppercase(Locale.ROOT)
        val base = baseUrl()
        return TcgSet(
            id = CatalogSetId.of(CatalogLang.EN, summary.id),
            name = summary.name?.takeIf { it.isNotBlank() } ?: folder,
            // Intestazioni della lista nella lingua dell'app, come per l'italiano.
            series = summary.series?.takeIf { it.isNotBlank() }?.let { ItalianTranslations.translateSeriesName(it) }.orEmpty(),
            language = LANGUAGE,
            printedTotal = summary.officialCount?.takeIf { it > 0 } ?: summary.cardCount,
            total = summary.cardCount,
            releaseDate = summary.releaseDate.orEmpty(),
            images = SetImages(
                symbol = EnglishCatalogIds.setImageUrl(base, folder, symbol = true),
                logo = EnglishCatalogIds.setImageUrl(base, folder, symbol = false)
            ),
            abbreviation = summary.abbreviation?.trim().orEmpty()
        )
    }

    /**
     * @param collectionSetName il nome del set italiano gemello: la carta lo
     *   porta come nome di set, cosi' in Collezione finisce nella tessera della
     *   carta italiana (collectionCardKey usa il nome del set). Null per i set
     *   solo inglesi, che tengono il loro.
     */
    fun toTcgCard(record: ItalianCardRecord, set: TcgSet, collectionSetName: String? = null): TcgCard? {
        val reference = EnglishCatalogIds.reference(record.cardId) ?: return null
        val base = baseUrl()
        return TcgCard(
            id = EnglishCatalogIds.apiCardId(reference),
            name = record.nome,
            supertype = EnglishCatalogIds.supertypeOf(record),
            subtypes = record.stage?.trim()?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList(),
            hp = record.ps?.takeIf { it.isNotBlank() },
            types = record.tipo?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() },
            set = TcgCardSet(
                id = set.id,
                name = collectionSetName?.takeIf { it.isNotBlank() } ?: set.name,
                series = set.series,
                printedTotal = set.printedTotal,
                releaseDate = set.releaseDate
            ),
            number = reference.cardNumber,
            rarity = record.rarity?.takeIf { it.isNotBlank() },
            images = CardImages(
                small = EnglishCatalogIds.cardImageUrl(base, reference, "low"),
                large = EnglishCatalogIds.cardImageUrl(base, reference, "high")
            ),
            artist = record.illustratore?.takeIf { it.isNotBlank() },
            variants = record.stampe?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
        )
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} su $url")
            response.body?.string().orEmpty()
        }
    }
}
