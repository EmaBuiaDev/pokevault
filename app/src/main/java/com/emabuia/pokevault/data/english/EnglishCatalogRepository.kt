package com.emabuia.pokevault.data.english

import android.content.Context
import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.data.catalog.CardRef
import com.emabuia.pokevault.data.catalog.CatalogLang
import com.emabuia.pokevault.data.catalog.CatalogSetId
import com.emabuia.pokevault.data.italian.ItalianCardRecord
import com.emabuia.pokevault.data.italian.ItalianCatalogNormalizer
import com.emabuia.pokevault.data.italian.ItalianExpansionSummary
import com.emabuia.pokevault.data.italian.ItalianImageReference
import com.emabuia.pokevault.data.local.ItalianTranslations
import com.emabuia.pokevault.data.remote.CardImages
import com.emabuia.pokevault.data.remote.SetImages
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgCardSet
import com.emabuia.pokevault.data.remote.TcgSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
            val set = getSet(context, setId) ?: TcgSet(id = setId, name = expansionId.uppercase(Locale.ROOT), language = LANGUAGE)
            runCatching {
                val raw = fetch("${baseUrl().trim().trimEnd('/')}/v1/en/expansions/$expansionId/cards")
                val records = withContext(Dispatchers.Default) { ItalianCatalogNormalizer.parseExpansionCardsResponse(raw) }
                records.mapNotNull { toTcgCard(it, set) }
                    .sortedBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }
            }.onSuccess { memoryCards[expansionId] = it to now }
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

    fun toTcgCard(record: ItalianCardRecord, set: TcgSet): TcgCard? {
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
                name = set.name,
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
