package com.emabuia.pokevault.data.remote

import android.content.Context
import com.emabuia.pokevault.data.italian.ItalianCardRecord
import com.emabuia.pokevault.data.italian.ItalianCatalog
import com.emabuia.pokevault.data.italian.ItalianCatalogRemoteRepository
import com.emabuia.pokevault.data.local.toEntity
import com.emabuia.pokevault.data.local.toTcgCard
import com.emabuia.pokevault.data.local.toTcgSet
import com.emabuia.pokevault.data.local.ItalianTranslations
import com.emabuia.pokevault.util.AppLocale
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.text.Normalizer
import java.util.Locale
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class PokeTcgRepository {

    private val api = PokeWalletRetrofitClient.create(com.emabuia.pokevault.BuildConfig.POKEWALLET_API_KEY)
    private val db get() = RepositoryProvider.database
    private val italianCatalogRepository = ItalianCatalogRemoteRepository()
    private val gson = Gson()

    @Volatile
    private var memorySets: List<TcgSet>? = null
    private val memoryCards = ConcurrentHashMap<String, List<TcgCard>>()
    private val memoryItalianCards = ConcurrentHashMap<String, List<TcgCard>>()
    private val memorySearch = ConcurrentHashMap<String, Pair<List<TcgCard>, Long>>()

    // Concurrency control to avoid duplicated requests when multiple screens ask the same data.
    private val setsMutex = Mutex()
    private val cardsMutex = Mutex()
    private val searchMutex = Mutex()
    private val setInfoMutex = Mutex()
    private val cardByIdMutex = Mutex()
    private val setLanguageMapMutex = Mutex()

    // Anti-spike protection to reduce credit usage during repeated bursts.
    private val lastNetworkAttempt = ConcurrentHashMap<String, Long>()
    @Volatile
    private var globalRateLimitUntil: Long = 0L
    private val setLanguageById = ConcurrentHashMap<String, String?>()
    @Volatile
    private var cacheHitCount: Long = 0
    @Volatile
    private var cacheMissCount: Long = 0
    @Volatile
    private var networkCallCount: Long = 0

    data class CacheDiagnostics(
        val hits: Long,
        val misses: Long,
        val networkCalls: Long
    )

    fun getDiagnostics(): CacheDiagnostics {
        return CacheDiagnostics(
            hits = cacheHitCount,
            misses = cacheMissCount,
            networkCalls = networkCallCount
        )
    }

    suspend fun clearSetsCache() {
        try {
            db.setDao().deleteAll()
        } catch (e: Exception) {
            Timber.w(e, "clearSetsCache: errore pulizia tabella sets")
        }
        memorySets = null
    }

    companion object {
        private const val SET_IMAGE_CACHE_VERSION = "setimg-v4"
        private const val SETS_CACHE_DURATION = 7 * 24 * 60 * 60 * 1000L   // 7 days
        private const val CARDS_CACHE_DURATION = 30 * 24 * 60 * 60 * 1000L  // 30 days
        private const val SEARCH_CACHE_DURATION = 60 * 60 * 1000L           // 1 hour
        private const val SCANNER_MIN_NAME_SCORE = 36
        private val ALLOWED_LANGUAGES = setOf("ITA", "ENG", "JAP", "CHN")
        private const val ITALIAN_SET_SUFFIX = "__ita"
        private val ITALIAN_PRINTED_TOTAL_BY_EXPANSION = mapOf(
            "me04" to 87
        )

        private const val RATE_LIMIT_COOLDOWN_MS = 60 * 1000L

        private val SANITIZE_MULTI_SPACE = Regex("\\s+")
        private val LEGACY_ID_REGEX = Regex("^([A-Za-z0-9]+)-(.+)$")
        private val HASH_ID_REGEX = Regex("^[a-f0-9]{32,}$", RegexOption.IGNORE_CASE)
        private val FULL_NUMBER_REGEX = Regex("""^(\d+)/(\d+)$""")
        private val FLEX_FULL_NUMBER_REGEX = Regex("""^\s*0*(\d+)\s*/\s*0*(\d+)\s*$""")
        private val FLEX_SET_NUMBER_REGEX = Regex("""^\s*([A-Za-z0-9]{2,16})\s*[-/\s]\s*([A-Za-z0-9]+)\s*$""")

        private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE
        private val HUMAN_DATE_LONG = DateTimeFormatter.ofPattern("d MMMM, uuuu", Locale.ENGLISH)
        private val HUMAN_DATE_SHORT = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)

        private val MEGA_EVOLUTION_SET_CODES = setOf("MEG", "PFL", "ASC", "POR", "CRI", "M5")
        // Accept canonical Mega codes (ME01, ME03, ...) and short wave codes (M5, M6, ...).
        // Avoid broad matches like M23/M24 (non Mega Evolution expansions).
        private val MEGA_EVOLUTION_CODE_PATTERN = Regex("^(?:ME\\d+|M\\d)$")
        private val SCARLET_VIOLET_SET_CODES = setOf("BLK", "WHT")
        private data class MissingSetSeed(
            val setId: String,
            val setCode: String,
            val searchQuery: String
        )
        private val MISSING_MEGA_SET_SEEDS = listOf(
            MissingSetSeed(setId = "24655", setCode = "CRI", searchQuery = "Chaos Rising"),
            MissingSetSeed(setId = "24711", setCode = "m5", searchQuery = "Abyss Eye")
        )
        // Name fragments (lowercase) used to infer ENG language when the source
        // metadata is incomplete for brand-new expansions just released by PokeWallet.
        private val ENG_NAME_HINTS = setOf(
            "chaos rising",
            "abyss eye",
            "abyss eyes",
            "ascending heroes",
            "perfect order"
        )
        private val LEGACY_CODE_TO_SERIES = mapOf(
            "BS" to "Base",
            "JU" to "Base",
            "FO" to "Base",
            "B2" to "Base",
            "TR" to "Base",
            "G1" to "Gym",
            "G2" to "Gym",
            "N1" to "Neo",
            "N2" to "Neo",
            "N3" to "Neo",
            "N4" to "Neo",
            "LC" to "Base",
            "EX" to "e-Card",
            "AQ" to "e-Card",
            "SK" to "e-Card",
            "RS" to "EX",
            "SS" to "EX",
            "DR" to "EX",
            "MA" to "EX",
            "HL" to "EX",
            "FG" to "EX",
            "TRR" to "EX",
            "DX" to "EX",
            "EM" to "EX",
            "UF" to "EX",
            "DS" to "EX",
            "LM" to "EX",
            "HP" to "EX",
            "CG" to "EX",
            "DF" to "EX",
            "PK" to "EX",
            "MT" to "Diamond & Pearl",
            "SW" to "Diamond & Pearl",
            "GE" to "Diamond & Pearl",
            "MD" to "Diamond & Pearl",
            "LA" to "Diamond & Pearl",
            "SF" to "Diamond & Pearl",
            "PL" to "Platinum",
            "RR" to "Platinum",
            "SV" to "Platinum",
            "AR" to "Platinum",
            "UL" to "HeartGold & SoulSilver",
            "UD" to "HeartGold & SoulSilver",
            "TM" to "HeartGold & SoulSilver",
            "CL" to "HeartGold & SoulSilver",
            "BLW" to "Black & White",
            "EPO" to "Black & White",
            "NVI" to "Black & White",
            "NXD" to "Black & White",
            "DEX" to "Black & White",
            "DRX" to "Black & White",
            "DRV" to "Black & White",
            "BCR" to "Black & White",
            "PLS" to "Black & White",
            "PLF" to "Black & White",
            "PLB" to "Black & White",
            "LTR" to "Black & White",
            "FLF" to "XY",
            "FFI" to "XY",
            "PHF" to "XY",
            "PRC" to "XY",
            "DCR" to "XY",
            "ROS" to "XY",
            "AOR" to "XY",
            "BKT" to "XY",
            "BKP" to "XY",
            "GEN" to "XY",
            "FCO" to "XY",
            "STS" to "XY",
            "EVO" to "XY",
            "SUM" to "Sun & Moon",
            "GRI" to "Sun & Moon",
            "BUS" to "Sun & Moon",
            "SLG" to "Sun & Moon",
            "CIN" to "Sun & Moon",
            "UPR" to "Sun & Moon",
            "FLI" to "Sun & Moon",
            "CES" to "Sun & Moon",
            "DRM" to "Sun & Moon",
            "LOT" to "Sun & Moon",
            "TEU" to "Sun & Moon",
            "DET" to "Sun & Moon",
            "UNB" to "Sun & Moon",
            "UNM" to "Sun & Moon",
            "HIF" to "Sun & Moon",
            "CEC" to "Sun & Moon",
            "SSH" to "Sword & Shield",
            "RCL" to "Sword & Shield",
            "DAA" to "Sword & Shield",
            "CPA" to "Sword & Shield",
            "VIV" to "Sword & Shield",
            "SHF" to "Sword & Shield",
            "BST" to "Sword & Shield",
            "CRE" to "Sword & Shield",
            "EVS" to "Sword & Shield",
            "FST" to "Sword & Shield",
            "BRS" to "Sword & Shield",
            "ASR" to "Sword & Shield",
            "LOR" to "Sword & Shield",
            "SIT" to "Sword & Shield",
            "CRZ" to "Sword & Shield"
        )
    }

    suspend fun getSets(context: Context? = null, forceRefresh: Boolean = false): Result<List<TcgSet>> =
        setsMutex.withLock {
            if (!forceRefresh && memorySets != null) {
                recordCacheHit("getSets:memory")
                val merged = mergeItalianSets(memorySets!!, context, forceRefresh = false)
                memorySets = merged
                return Result.success(merged)
            }

            // L2: Room DB
            if (!forceRefresh) {
                val roomSets = loadSetsFromRoom()
                if (roomSets != null) {
                    val merged = mergeItalianSets(roomSets, context, forceRefresh = false)
                    memorySets = merged
                    recordCacheHit("getSets:room")
                    return Result.success(merged)
                }
            }

            // Stale fallback for network errors
            val staleCache = loadSetsFromRoom(ignoreExpiry = true)

            recordCacheMiss("getSets")

            val networkResult = guardedApiCall(resourceKey = "sets") {
                val response = api.getSets()
                val enrichedSets = enrichMissingMegaSetsFromSearch(response.data)
                setLanguageById.clear()
                enrichedSets
                    .mapNotNull { remoteSet ->
                        runCatching {
                            val mapped = remoteSet.toTcgSet()
                            if (mapped.id.isBlank() || mapped.name.isBlank()) return@runCatching null
                            setLanguageById[mapped.id] = mapped.language
                            mapped
                        }.onFailure { err ->
                            Timber.w(err, "Skip invalid set record while loading expansions")
                        }.getOrNull()
                    }
                    .filter { it.language in ALLOWED_LANGUAGES }
                    .sortedWith(
                        compareByDescending<TcgSet> { parseReleaseDateToEpoch(it.releaseDate) }
                            .thenByDescending { it.id }
                            .thenBy { it.name }
                    )
            }

            networkResult.onSuccess { sets ->
                val merged = mergeItalianSets(sets, context, forceRefresh = forceRefresh)
                memorySets = merged
                refreshLanguageMapFromSets(merged)
                saveSetsToRoom(merged)
            }

            if (networkResult.isSuccess) {
                Result.success(memorySets!!)
            } else {
                staleCache?.let {
                    val merged = mergeItalianSets(it, context, forceRefresh = false)
                    memorySets = merged
                    refreshLanguageMapFromSets(merged)
                    recordCacheHit("getSets:stale")
                    Result.success(merged)
                } ?: networkResult
            }
        }

    suspend fun getCardsBySet(
        setId: String,
        context: Context? = null,
        forceRefresh: Boolean = false,
        preferredImageMacro: String? = null
    ): Result<List<TcgCard>> =
        cardsMutex.withLock {
            val mustBypassCache = MISSING_MEGA_SET_SEEDS.any {
                it.setId.equals(setId, ignoreCase = true) || it.setCode.equals(setId, ignoreCase = true)
            }
            val effectiveForceRefresh = forceRefresh || mustBypassCache

            if (isItalianSetId(setId)) {
                return getCardsByItalianSet(
                    setId = setId,
                    context = context,
                    forceRefresh = forceRefresh
                )
            }

            val normalizedMacro = preferredImageMacro?.trim()?.uppercase(Locale.ROOT)
            if (normalizedMacro == "ITA") {
                val italianCards = getItalianOverlayCards(
                    setId = setId,
                    context = context,
                    forceRefresh = forceRefresh
                )
                return Result.success(italianCards ?: emptyList())
            }

            // L1: Memory
            if (!effectiveForceRefresh) {
                memoryCards[setId]?.let {
                    if (it.isNotEmpty()) {
                        val localizedCards = adaptPilotImagesForCurrentLocale(setId, it, preferredImageMacro)
                        memoryCards[setId] = localizedCards
                        updateSetTotalsFromKnownCards(setId = setId, cardsCount = it.size)
                        recordCacheHit("getCardsBySet:memory:$setId")
                        return Result.success(localizedCards)
                    }
                }
            }

            // L2: Room DB
            if (!effectiveForceRefresh) {
                val roomCards = loadCardsFromRoom(setId)
                if (roomCards != null) {
                    val localizedCards = adaptPilotImagesForCurrentLocale(setId, roomCards, preferredImageMacro)
                    memoryCards[setId] = localizedCards
                    updateSetTotalsFromKnownCards(setId = setId, cardsCount = roomCards.size)
                    recordCacheHit("getCardsBySet:room:$setId")
                    return Result.success(localizedCards)
                }
            }

            ensureSetLanguageMapReady()
            if (!isAllowedSetLanguage(setId)) {
                return Result.success(emptyList())
            }

            recordCacheMiss("getCardsBySet:$setId")

            val staleCache = loadCardsFromRoom(setId, ignoreExpiry = true)
            val networkResult = guardedApiCall(resourceKey = "cards:$setId") {
                fetchAllCardsForSet(setId)
            }

            if (networkResult.isSuccess) {
                val networkCards = networkResult.getOrThrow().cards
                val localizedCards = adaptPilotImagesForCurrentLocale(setId, networkCards, preferredImageMacro)
                memoryCards[setId] = localizedCards
                saveCardsToRoom(networkCards)
                updateSetTotalsFromKnownCards(setId = setId, cardsCount = localizedCards.size)
                Result.success(localizedCards)
            } else {
                val fallback = memoryCards[setId] ?: staleCache?.let {
                    adaptPilotImagesForCurrentLocale(setId, it, preferredImageMacro)
                }
                fallback?.let {
                    recordCacheHit("getCardsBySet:stale:$setId")
                    Result.success(it)
                } ?: Result.failure(networkResult.exceptionOrNull()!!)
            }
        }

    suspend fun getSetInfo(setId: String): Result<TcgSet> = setInfoMutex.withLock {
        memorySets?.firstOrNull { it.id == setId }?.let { return Result.success(it) }

        // Prefer Room cache to avoid unnecessary credit consumption when opening set details.
        val roomSets = loadSetsFromRoom()
        roomSets?.firstOrNull { it.id == setId }?.let {
            memorySets = roomSets
            return Result.success(it)
        }

        // Fallback to stale Room cache if fresh cache is expired.
        loadSetsFromRoom(ignoreExpiry = true)?.firstOrNull { it.id == setId }?.let {
            return Result.success(it)
        }

        val networkResult = guardedApiCall(resourceKey = "set:$setId") {
            val response = api.getSet(setId, page = 1, limit = 1)
            when {
                response.set != null -> response.set.toTcgSet()
                response.matches.isNotEmpty() -> response.matches.first().toTcgSet()
                else -> throw NoSuchElementException("Set non trovato")
            }
        }

        if (networkResult.isSuccess) return networkResult

        memorySets?.firstOrNull { it.id == setId }?.let { return Result.success(it) }
        networkResult
    }
                    suspend fun getEnglishBaseCardForItalianOverlay(
                        italianCardId: String,
                        italianSetId: String,
                        context: Context? = null,
                        forceRefresh: Boolean = false
                    ): Result<TcgCard?> = cardsMutex.withLock {
                        if (!isItalianOverlayCardId(italianCardId)) return@withLock Result.success(null)

                        val expansionId = parseItalianExpansionId(italianSetId) ?: return@withLock Result.success(null)
                        val imageRef = italianCardId.removePrefix("ita:").split(':')
                        val overlayNumber = imageRef.getOrNull(2)?.takeIf { it.isNotBlank() }
                            ?: return@withLock Result.success(null)

                        val setInfo = memorySets?.firstOrNull { it.id == italianSetId }
                            ?: loadSetsFromRoom(ignoreExpiry = true)?.firstOrNull { it.id == italianSetId }
                            ?: TcgSet(
                                id = italianSetId,
                                name = expansionId.uppercase(Locale.ROOT),
                                series = deriveSeriesName(setCode = expansionId, language = "ENG", setName = expansionId),
                                language = "ITA"
                            )

                        val catalog = context?.let {
                            italianCatalogRepository.getCatalog(it, forceRefresh = forceRefresh).getOrNull()
                        }
                        val expansionCards = catalog?.cardsByExpansion()?.get(expansionId).orEmpty()
                        val preferredBaseSetCode = preferredBaseSetCodeForItalianExpansion(expansionId)
                        val dominantRawSetCode = expansionCards
                            .asSequence()
                            .mapNotNull { record -> record.imageReference()?.setCode }
                            .map { code -> code.trim().uppercase(Locale.ROOT) }
                            .groupingBy { it }
                            .eachCount()
                            .maxByOrNull { it.value }
                            ?.key

                        val baseSetId = resolveEnglishBaseSetIdForItalianSet(
                            italianSet = setInfo,
                            dominantRawSetCode = preferredBaseSetCode ?: dominantRawSetCode
                        ) ?: return@withLock Result.success(null)

                        val baseCards = loadStandardCardsForSet(
                            setId = baseSetId,
                            context = context,
                            forceRefresh = forceRefresh
                        ).getOrDefault(emptyList())

                        Result.success(
                            baseCards.firstOrNull { extractCardNumber(it.number) == extractCardNumber(overlayNumber) }
                        )
                    }

    suspend fun searchCards(query: String, page: Int = 1): Result<List<TcgCard>> = searchMutex.withLock {
        if (query.isBlank()) return Result.success(emptyList())

        val normalized = sanitizeQuery(query)
        val setAndNumber = parseSetNumberQuery(normalized)
        val normalizedQuery = setAndNumber?.let { (setId, number) -> "$setId $number" } ?: normalized
        val fullNumber = parseFullNumberQuery(normalizedQuery)
        val cacheKey = "search::$normalizedQuery::$page"
        memorySearch[cacheKey]?.let { (cards, timestamp) ->
            if (System.currentTimeMillis() - timestamp < SEARCH_CACHE_DURATION) {
                recordCacheHit("search:$normalizedQuery:$page")
                return Result.success(cards)
            }
        }

        if (page == 1) {
            val localCards = searchCardsFromLocalCache(normalizedQuery)
            if (localCards.isNotEmpty()) {
                val rankedLocal = rankSearchResults(normalizedQuery, localCards, fullNumber)
                memorySearch[cacheKey] = rankedLocal to System.currentTimeMillis()
                recordCacheHit("search:local:$normalizedQuery:$page")
                return Result.success(rankedLocal)
            }
        }

        recordCacheMiss("search:$normalizedQuery:$page")

        val networkResult = guardedApiCall(resourceKey = "search:$normalizedQuery:$page") {
            val cards = when (fullNumber) {
                null -> {
                    if (setAndNumber != null) {
                        performSetNumberSearch(setAndNumber.first, setAndNumber.second, page)
                    } else {
                        performGenericSearch(normalizedQuery, page)
                    }
                }
                else -> performPreciseNumberSearch(number = fullNumber.first, total = fullNumber.second)
            }
            val deduped = cards.distinctBy { it.id }
            rankSearchResults(normalizedQuery, deduped, fullNumber)
        }

        networkResult.onSuccess { cards ->
            memorySearch[cacheKey] = cards to System.currentTimeMillis()
        }

        networkResult
    }

    suspend fun searchCardsFuzzy(name: String, page: Int = 1): Result<List<TcgCard>> {
        return searchCardsFuzzy(name = name, page = page, targetSetId = null)
    }

    suspend fun searchItalianCardsByName(
        query: String,
        context: Context,
        exactMode: Boolean = false,
        limit: Int = 60,
        targetSetId: String? = null
    ): Result<List<TcgCard>> {
        val cleanQuery = sanitizeQuery(query)
        val normalizedQuery = normalizeNameForLookup(cleanQuery)
        if (normalizedQuery.isBlank()) return Result.success(emptyList())

        val safeLimit = limit.coerceIn(1, 100)
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        val normalizedTargetSet = targetSetId
            ?.let(SetCodeMapper::normalizeDecklistSetCode)
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }

        val catalog = italianCatalogRepository.getCatalog(context, forceRefresh = false)
            .getOrElse { return Result.success(emptyList()) }

        return runCatching {
            withContext(Dispatchers.Default) {
                val scoredRecords = catalog.cards.asSequence()
                    .filter { record ->
                        normalizedTargetSet == null ||
                            matchesItalianExpansionHint(
                                record.espansioneId.trim().lowercase(Locale.ROOT),
                                normalizedTargetSet
                            )
                    }
                    .mapNotNull { record ->
                        val normalizedName = normalizeNameForLookup(record.nome)
                        if (normalizedName.isBlank()) return@mapNotNull null

                        val score = scoreItalianNameMatch(
                            normalizedName = normalizedName,
                            normalizedQuery = normalizedQuery,
                            queryTokens = queryTokens,
                            exactMode = exactMode
                        )

                        if (score <= 0) null else record to score
                    }
                    .sortedWith(
                        compareByDescending<Pair<ItalianCardRecord, Int>> { it.second }
                            .thenBy { extractCardNumber(it.first.cardId).toIntOrNull() ?: Int.MAX_VALUE }
                            .thenBy { normalizeItalianSetCode(it.first.espansioneId) }
                    )
                    .take(safeLimit)
                    .toList()

                if (scoredRecords.isEmpty()) {
                    return@withContext emptyList()
                }

                scoredRecords.map { (record, _) ->
                    val expansionId = record.espansioneId.trim().lowercase(Locale.ROOT)
                    val italianSetId = buildItalianSetId(expansionId)
                    val setInfo = TcgSet(
                        id = italianSetId,
                        name = expansionId.uppercase(Locale.ROOT),
                        series = deriveSeriesName(
                            setCode = expansionId,
                            language = "ITA",
                            setName = expansionId
                        ),
                        language = "ITA"
                    )
                    toItalianTcgCard(record = record, setInfo = setInfo)
                }.distinctBy { it.id }
            }
        }
    }

    suspend fun searchItalianScannerCandidates(
        name: String?,
        number: String?,
        setTotal: String?,
        targetSetId: String?,
        context: Context,
        limit: Int = 6
    ): Result<List<TcgCard>> {
        val normalizedNumber = number?.trim()?.trimStart('0')?.ifBlank { "0" }
        val normalizedName = normalizeNameForLookup(name)
        val normalizedTargetSet = targetSetId
            ?.let(SetCodeMapper::normalizeDecklistSetCode)
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
        val targetTotal = setTotal?.toIntOrNull()

        if (normalizedNumber.isNullOrBlank() && normalizedName.isBlank()) {
            return Result.success(emptyList())
        }

        val catalog = italianCatalogRepository.getCatalog(context, forceRefresh = false)
            .getOrElse { return Result.success(emptyList()) }
        val expansionManifests = catalog.expansions.associateBy { it.espansioneId.trim().lowercase(Locale.ROOT) }

        return runCatching {
            withContext(Dispatchers.Default) {
                val baseCandidates = catalog.cards.filter { record ->
                    val numberOk = if (normalizedNumber.isNullOrBlank()) {
                        true
                    } else {
                        val ref = record.imageReference()
                        val cardNum = (ref?.cardNumber ?: extractCardNumber(record.cardId))
                            .trimStart('0')
                            .ifBlank { "0" }
                        cardNum == normalizedNumber
                    }
                    numberOk && (
                        normalizedTargetSet == null ||
                            matchesItalianExpansionHint(
                                record.espansioneId.trim().lowercase(Locale.ROOT),
                                normalizedTargetSet
                            )
                        )
                }

                fun rank(applyNameGate: Boolean): List<Pair<ItalianCardRecord, Int>> =
                    baseCandidates.mapNotNull { record ->
                        val expansionId = record.espansioneId.trim().lowercase(Locale.ROOT)
                        val cardName = normalizeNameForLookup(record.nome)
                        val nameScore = scannerNameScore(cardName, normalizedName)
                        if (applyNameGate && normalizedName.isNotBlank() && nameScore < SCANNER_MIN_NAME_SCORE) {
                            return@mapNotNull null
                        }

                        var score = 0
                        if (!normalizedNumber.isNullOrBlank()) score += 120
                        score += nameScore
                        if (normalizedTargetSet != null) score += 120

                        val printedTotal = ITALIAN_PRINTED_TOTAL_BY_EXPANSION[expansionId]
                            ?: expansionManifests[expansionId]?.cardCount?.takeIf { it > 0 }
                        if (targetTotal != null && printedTotal != null) {
                            score += when {
                                printedTotal == targetTotal -> 45
                                abs(printedTotal - targetTotal) <= 2 -> 18
                                else -> 0
                            }
                        }

                        record to score
                    }.sortedWith(
                        compareByDescending<Pair<ItalianCardRecord, Int>> { it.second }
                            .thenBy { it.first.espansioneId }
                            .thenBy { extractCardNumber(it.first.cardId).toIntOrNull() ?: Int.MAX_VALUE }
                    ).take(limit.coerceIn(1, 20))

                // Il nome OCR e spesso sporco: se il gate sul nome azzera i risultati
                // ma il numero carta e disponibile, riprova senza gate cosi il chiamante
                // puo comunque proporre i candidati per numero/totale set.
                val gated = rank(applyNameGate = true)
                val ranked = if (gated.isEmpty() && normalizedName.isNotBlank() && !normalizedNumber.isNullOrBlank()) {
                    rank(applyNameGate = false)
                } else {
                    gated
                }

                ranked.map { (record, _) ->
                    val expansionId = record.espansioneId.trim().lowercase(Locale.ROOT)
                    val printedTotal = ITALIAN_PRINTED_TOTAL_BY_EXPANSION[expansionId]
                        ?: expansionManifests[expansionId]?.cardCount?.takeIf { it > 0 }
                    toItalianTcgCard(
                        record = record,
                        setInfo = TcgSet(
                            id = buildItalianSetId(expansionId),
                            name = expansionId.uppercase(Locale.ROOT),
                            series = deriveSeriesName(setCode = expansionId, language = "ITA", setName = expansionId),
                            printedTotal = printedTotal ?: 0,
                            language = "ITA"
                        )
                    )
                }.distinctBy { it.id }
            }
        }
    }

    /**
     * Cerca le carte ITA nel catalogo locale filtrando per numero carta e, se fornito, per totale stampato del set.
     * Il filtro totale usa i set ITA gia mergiati in memorySets, cosi eredita il printedTotal reale dal set base ENG.
     */
    suspend fun searchItalianCardsByNumber(
        number: String,
        context: Context,
        setTotal: String? = null,
        targetSetId: String? = null
    ): Result<List<TcgCard>> {
        val normalizedTarget = number.trimStart('0').ifBlank { number }
        if (normalizedTarget.isBlank()) return Result.success(emptyList())

        val catalog = italianCatalogRepository.getCatalog(context, forceRefresh = false)
            .getOrElse { return Result.success(emptyList()) }

        return runCatching {
            withContext(Dispatchers.Default) {
                val normalizedTargetSet = targetSetId
                    ?.let(SetCodeMapper::normalizeDecklistSetCode)
                    ?.lowercase(Locale.ROOT)
                    ?.takeIf { it.isNotBlank() }

                // Hard-scope only by explicit set hint. Do not hard-filter by setTotal:
                // printed totals can differ from catalog card counts when secret cards are present.
                val allowedExpansions: Set<String>? = if (normalizedTargetSet != null) {
                    catalog.expansions
                        .asSequence()
                        .map { it.espansioneId.trim().lowercase(Locale.ROOT) }
                        .filter { it.isNotBlank() }
                        .filter { expansionId -> matchesItalianExpansionHint(expansionId, normalizedTargetSet) }
                        .toSet()
                        .takeIf { it.isNotEmpty() }
                } else null

                val matchingRecords = catalog.cards.filter { record ->
                    if (allowedExpansions != null &&
                        record.espansioneId.trim().lowercase(Locale.ROOT) !in allowedExpansions
                    ) return@filter false
                    val ref = record.imageReference()
                    val cardNum = (ref?.cardNumber ?: extractCardNumber(record.cardId))
                        .trimStart('0').ifBlank { "0" }
                    cardNum.equals(normalizedTarget, ignoreCase = true)
                }

                if (matchingRecords.isEmpty()) return@withContext emptyList()

                matchingRecords.map { record ->
                    val expansionId = record.espansioneId.trim().lowercase(Locale.ROOT)
                    val italianSetId = buildItalianSetId(expansionId)
                    val setInfo = TcgSet(
                        id = italianSetId,
                        name = expansionId.uppercase(Locale.ROOT),
                        series = deriveSeriesName(
                            setCode = expansionId,
                            language = "ITA",
                            setName = expansionId
                        ),
                        language = "ITA"
                    )
                    toItalianTcgCard(record = record, setInfo = setInfo)
                }.distinctBy { it.id }
            }
        }
    }

    suspend fun searchCardsFuzzy(name: String, page: Int = 1, targetSetId: String? = null): Result<List<TcgCard>> {
        val clean = sanitizeQuery(name)
        if (clean.isBlank()) return Result.success(emptyList())

        val normalizedTargetSet = targetSetId
            ?.let(SetCodeMapper::normalizeDecklistSetCode)
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }

        fun applyStrictSetScope(cards: List<TcgCard>): List<TcgCard> {
            val scoped = if (normalizedTargetSet == null) {
                cards
            } else {
                cards.filter { matchesSearchSet(it.set?.id, normalizedTargetSet) }
            }
            return scoped.distinctBy { it.id }
        }

        val direct = searchCards(clean, page).getOrDefault(emptyList())
        val directScoped = applyStrictSetScope(direct)
        if (directScoped.isNotEmpty()) return Result.success(directScoped)

        val parsedSetNumber = parseSetNumberQuery(clean)
        if (parsedSetNumber != null) {
            val setScoped = performSetNumberSearch(parsedSetNumber.first, parsedSetNumber.second, page)
            val scoped = applyStrictSetScope(setScoped)
            if (scoped.isNotEmpty()) return Result.success(scoped)
        }

        val tokens = normalizeNameForLookup(clean)
            .split(" ")
            .filter { it.length >= 4 }
            .distinct()

        for (token in tokens) {
            if (token == clean) continue
            val tokenResults = searchCards(token, page).getOrDefault(emptyList())
            val scoped = applyStrictSetScope(tokenResults)
            if (scoped.isNotEmpty()) return Result.success(scoped)
        }

        return Result.success(emptyList())
    }

    suspend fun searchByNameAndNumber(name: String?, number: String?, setId: String? = null): Result<List<TcgCard>> {
        val cleanName = name?.let(::sanitizeQuery)?.takeIf { it.isNotBlank() }
        val cleanNumber = number?.trim()?.trimStart('0')?.takeIf { it.isNotBlank() }
        val cleanSetId = setId?.trim()?.takeIf { it.isNotBlank() }

        if (cleanName == null && cleanNumber == null) return Result.success(emptyList())

        if (cleanSetId != null && cleanNumber != null) {
            val setScoped = searchCards("$cleanSetId $cleanNumber", page = 1).getOrDefault(emptyList())
            val filtered = filterByName(setScoped, cleanName)
            if (filtered.isNotEmpty()) return Result.success(filtered)
        }

        if (cleanName != null && cleanNumber != null) {
            val combined = searchCards("$cleanName $cleanNumber", page = 1).getOrDefault(emptyList())
            val filtered = combined.filter { extractCardNumber(it.number) == cleanNumber }.ifEmpty {
                filterByName(combined, cleanName)
            }
            if (filtered.isNotEmpty()) return Result.success(filtered.distinctBy { it.id })
        }

        if (cleanName != null) {
            val byName = searchCardsFuzzy(cleanName).getOrDefault(emptyList())
            if (byName.isNotEmpty()) {
                val filtered = if (cleanNumber != null) {
                    byName.filter { extractCardNumber(it.number) == cleanNumber }
                } else byName
                if (filtered.isNotEmpty()) return Result.success(filtered.distinctBy { it.id })
                return Result.success(byName.distinctBy { it.id })
            }
        }

        if (cleanNumber != null) {
            return searchCards(cleanNumber, page = 1)
        }

        return Result.success(emptyList())
    }

    suspend fun getPokewalletCardBySetAndNumber(setCode: String?, number: String?): Result<TcgCard?> {
        val normalizedSet = SetCodeMapper.normalizeDecklistSetCode(setCode)
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success(null)
        val normalizedNumber = extractCardNumber(number.orEmpty())
            .takeIf { it.isNotBlank() }
            ?: return Result.success(null)

        val exactMatches = mutableListOf<PokeWalletCard>()
        for (query in buildStrictSetNumberQueries(normalizedSet, number)) {
            val networkResult = guardedApiCall(resourceKey = "cards:strict:$query") {
                api.search(query = query, page = 1, limit = 30)
            }
            if (networkResult.isFailure) {
                return Result.failure(networkResult.exceptionOrNull()!!)
            }

            exactMatches += networkResult.getOrThrow().results.filter { card ->
                matchesExactSetAndNumber(card, normalizedSet, normalizedNumber)
            }
        }

        val remoteCard = rankStrictMatches(exactMatches.distinctBy { it.id })
            .firstOrNull()
            ?: return Result.success(null)

        return Result.success(remoteCard.toTcgCard())
    }

    suspend fun findExactCardInCatalog(
        name: String?,
        setCode: String?,
        number: String?
    ): Result<TcgCard?> {
        val normalizedSet = SetCodeMapper.normalizeDecklistSetCode(setCode)
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success(null)
        val normalizedNumber = extractCardNumber(number.orEmpty())
            .takeIf { it.isNotBlank() }
            ?: return Result.success(null)
        val normalizedName = normalizeNameForLookup(name).takeIf { it.isNotBlank() }

        val candidates = resolveImportedSetCandidates(normalizedSet)
        if (candidates.isEmpty()) return Result.success(null)

        for (candidate in candidates) {
            val cachedCards = linkedSetOf<TcgCard>()
            memoryCards[candidate.id]?.let { cards ->
                cachedCards += cards.filter { extractCardNumber(it.number) == normalizedNumber }
            }

            val roomCards = runCatching {
                db.cardDao().getBySetIdAndNumber(candidate.id, normalizedNumber)
            }.onFailure { err ->
                Timber.w(err, "findExactCardInCatalog: errore Room set+numero=%s %s", candidate.id, normalizedNumber)
            }.getOrDefault(emptyList())
                .map { it.toTcgCard() }
            cachedCards += roomCards

            val catalogHit = selectCatalogMatch(cachedCards.toList(), normalizedName)
            if (catalogHit != null) return Result.success(catalogHit)

            val setCards = getCardsBySet(candidate.id, forceRefresh = false).getOrDefault(emptyList())
            val exactCards = setCards.filter { extractCardNumber(it.number) == normalizedNumber }
            val setHit = selectCatalogMatch(exactCards, normalizedName)
            if (setHit != null) return Result.success(setHit)
        }

        return Result.success(null)
    }

    suspend fun searchPokewalletCardByNameSetAndNumber(
        name: String,
        setCode: String?,
        number: String?
    ): Result<TcgCard?> {
        val cleanName = sanitizeQuery(name)
        val normalizedSet = SetCodeMapper.normalizeDecklistSetCode(setCode)
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success(null)
        val normalizedNumber = extractCardNumber(number.orEmpty())
            .takeIf { it.isNotBlank() }
            ?: return Result.success(null)
        if (cleanName.isBlank()) return Result.success(null)

        val exactMatches = mutableListOf<PokeWalletCard>()
        for (query in buildStrictNameSetNumberQueries(name, normalizedSet, number)) {
            val networkResult = guardedApiCall(resourceKey = "cards:search:strict:$query") {
                api.search(query = query, page = 1, limit = 30)
            }

            if (networkResult.isFailure) {
                return Result.failure(networkResult.exceptionOrNull()!!)
            }

            exactMatches += networkResult.getOrThrow().results.filter { card ->
                matchesExactNameSetAndNumber(
                    card = card,
                    expectedName = name,
                    expectedSet = normalizedSet,
                    expectedNumber = normalizedNumber
                )
            }
        }

        if (exactMatches.isNotEmpty()) {
            return Result.success(rankStrictMatches(exactMatches.distinctBy { it.id }).firstOrNull()?.toTcgCard())
        }

        val fallbackMatches = mutableListOf<PokeWalletCard>()
        for (page in 1..3) {
            val networkResult = guardedApiCall(resourceKey = "search:name:$cleanName:$page") {
                api.search(query = preferredNameQuery(name), page = page, limit = 50)
            }

            if (networkResult.isFailure) {
                val exception = networkResult.exceptionOrNull()
                if (exception is HttpException && exception.code() == 404) {
                    break
                }
                return Result.failure(exception ?: IllegalStateException("Unknown cards/search error"))
            }

            val pageMatches = networkResult.getOrThrow().results.filter { card ->
                matchesExactNameSetAndNumber(
                    card = card,
                    expectedName = name,
                    expectedSet = normalizedSet,
                    expectedNumber = normalizedNumber
                )
            }
            fallbackMatches += pageMatches
            if (pageMatches.isNotEmpty()) break
        }

        return Result.success(rankStrictMatches(fallbackMatches.distinctBy { it.id }).firstOrNull()?.toTcgCard())
    }

    fun getCandidateSetIdsByPrintedTotal(total: String?, tolerance: Int = 1): List<String> {
        return getCandidateSetsByPrintedTotal(total, tolerance).map { it.id }
    }

    fun getPrintedTotalForSet(setId: String?): Int? {
        val safeSetId = setId?.takeIf { it.isNotBlank() } ?: return null
        return memorySets?.firstOrNull { it.id == safeSetId }?.printedTotal
    }

    suspend fun getCard(cardId: String, preferNetwork: Boolean = false): Result<TcgCard> = cardByIdMutex.withLock {
        fun findMemoryCard(): TcgCard? {
            return memoryCards.values
                .asSequence()
                .flatMap { it.asSequence() }
                .firstOrNull { it.id == cardId }
                ?: memoryItalianCards.values
                    .asSequence()
                    .flatMap { it.asSequence() }
                    .firstOrNull { it.id == cardId }
        }

        suspend fun findRoomCard(): TcgCard? {
            return runCatching { db.cardDao().getById(cardId) }
                .onFailure { Timber.w(it, "getCard: errore lettura Room per id=%s", cardId) }
                .getOrNull()
                ?.toTcgCard()
        }

        suspend fun loadFromNetwork(): Result<TcgCard> = guardedApiCall(resourceKey = "card:$cardId") {
            when {
                isPokeWalletCardId(cardId) -> api.getCard(cardId).toTcgCard()
                isItalianOverlayCardId(cardId) -> {
                    findMemoryCard() ?: throw NoSuchElementException("Carta italiana non trovata: $cardId")
                }
                else -> {
                    val legacy = LEGACY_ID_REGEX.matchEntire(cardId)
                    if (legacy == null) {
                        throw NoSuchElementException("Carta non trovata: $cardId")
                    }
                    val setRef = legacy.groupValues[1]
                    val number = legacy.groupValues[2]
                    val hits = performApiSearch("$setRef $number", page = 1, limit = 20)
                    hits.firstOrNull() ?: throw NoSuchElementException("Carta non trovata per id legacy $cardId")
                }
            }
        }

        if (!preferNetwork) {
            val memoryCard = findMemoryCard()
            if (memoryCard != null) {
                recordCacheHit("getCard:memory:$cardId")
                return Result.success(memoryCard)
            }

            val roomCard = findRoomCard()
            if (roomCard != null) {
                recordCacheHit("getCard:room:$cardId")
                return Result.success(roomCard)
            }
        }

        val networkResult = loadFromNetwork()

        if (networkResult.isSuccess) return networkResult

        val memoryCard = findMemoryCard()
        if (memoryCard != null) {
            recordCacheHit("getCard:memory:$cardId")
            return Result.success(memoryCard)
        }

        val roomCard = findRoomCard()
        if (roomCard != null) {
            recordCacheHit("getCard:room:$cardId")
            return Result.success(roomCard)
        }

        val fallback = memorySearch.values
            .asSequence()
            .flatMap { it.first.asSequence() }
            .firstOrNull { it.id == cardId }

        fallback?.let {
            recordCacheHit("getCard:searchIndex:$cardId")
            Result.success(it)
        } ?: networkResult
    }

    private suspend fun fetchAllCardsForSet(setId: String): SetCardsResult {
        val cards = linkedMapOf<String, TcgCard>()
        var page = 1
        var apiTotalCount = 0

        while (true) {
            val response = api.getSet(setId, page = page, limit = 200)
            val set = response.set ?: response.matches.firstOrNull()
                ?: throw NoSuchElementException("Set PokeWallet non trovato: $setId")
            val setLanguage = mapLanguageMacro(set.language)
            if (setLanguage !in ALLOWED_LANGUAGES) {
                return SetCardsResult(emptyList(), 0)
            }

            val batch = response.cards
                .filter { isActualCard(it) }
                .map { it.toTcgCard(set) }
            if (page == 1) {
                // Prefer cardCount: totalCards may include extra records that inflate set size.
                apiTotalCount = set.cardCount.takeIf { it > 0 } ?: set.totalCards.takeIf { it > 0 } ?: batch.size
            }
            batch.forEach { cards[it.id] = it }

            if (batch.isEmpty()) break
            if (cards.size >= apiTotalCount) break
            if (response.cards.size < 200) break
            page++
        }

        // Return actual filtered card count, not API totalCount (which includes non-card products)
        return SetCardsResult(cards.values.toList(), cards.size)
    }

    private fun isActualCard(card: PokeWalletCard): Boolean {
        val info = card.cardInfo ?: return false
        // Must have a card number
        if (info.cardNumber.isNullOrBlank()) return false
        // Filter out non-card products by name
        val name = info.name.lowercase()
        val productPatterns = listOf(
            Regex("\\bmini tin\\b"),
            Regex("\\bbooster box\\b"),
            Regex("\\bbooster bundle\\b"),
            Regex("\\bbooster pack\\b"),
            Regex("\\bcollection box\\b"),
            Regex("\\belite trainer box\\b"),
            Regex("\\betb\\b"),
            Regex("\\bblister\\b"),
            Regex("\\bdisplay\\b"),
            Regex("\\btheme deck\\b"),
            Regex("\\bstarter set\\b"),
            Regex("\\bbuild\\s*&\\s*battle\\b"),
            Regex("\\bbuild and battle\\b")
        )
        if (productPatterns.any { it.containsMatchIn(name) }) return false
        return true
    }

    private suspend fun performGenericSearch(query: String, page: Int): List<TcgCard> {
        val translated = when (query.lowercase()) {
            "fuoco" -> "fire"
            "acqua" -> "water"
            "erba" -> "grass"
            "elettro" -> "lightning"
            "psico" -> "psychic"
            "lotta" -> "fighting"
            "buio" -> "darkness"
            "metallo" -> "metal"
            "drago" -> "dragon"
            "folletto" -> "fairy"
            "incolore" -> "colorless"
            "energia" -> "energy"
            "allenatore" -> "trainer"
            else -> query
        }
        return performAdaptiveApiSearch(translated, page = page)
    }

    private suspend fun performPreciseNumberSearch(number: String, total: String): List<TcgCard> {
        val exactSetIds = getCandidateSetIdsByPrintedTotal(total, tolerance = 0).toSet()
        val nearSetIds = getCandidateSetIdsByPrintedTotal(total, tolerance = 1).toSet()

        val totalVariants = linkedSetOf(total, total.padStart(3, '0'))

        val primaryNumberVariants = linkedSetOf(
            "$number/$total",
            "${number.padStart(3, '0')}/$total",
            "$number/${total.padStart(3, '0')}",
            "${number.padStart(3, '0')}/${total.padStart(3, '0')}"
        )

        val fetched = linkedSetOf<TcgCard>()
        for (variant in primaryNumberVariants) {
            fetched += performApiSearch(query = variant, page = 1, limit = 30)
            if (fetched.count { card -> extractCardNumber(card.number) == number } >= 12) break
        }

        if (fetched.none { card -> extractCardNumber(card.number) == number }) {
            fetched += performApiSearch(query = number, page = 1, limit = 30)
            fetched += performApiSearch(query = number.padStart(3, '0'), page = 1, limit = 30)
        }

        val filteredByNumber = fetched.filter { card -> extractCardNumber(card.number) == number }
        val candidates = if (filteredByNumber.isNotEmpty()) {
            filteredByNumber
        } else {
            performAdaptiveApiSearch(number, page = 1)
                .filter { card -> extractCardNumber(card.number) == number }
        }

        return candidates.distinctBy { it.id }.sortedWith(
            compareByDescending<TcgCard> { card ->
                val setId = card.set?.id.orEmpty()
                when {
                    setId in exactSetIds -> 3
                    setId in nearSetIds -> 2
                    else -> 0
                }
            }.thenByDescending { card ->
                if (extractPrintedTotalForSearch(card.number) in totalVariants) 1 else 0
            }.thenByDescending { card ->
                parseReleaseDateToEpoch(setReleaseDateById(card.set?.id).orEmpty())
            }.thenBy { card ->
                normalizeNameForLookup(card.name)
            }.thenBy { card ->
                card.id
            }
        )
    }

    private suspend fun performSetNumberSearch(setId: String, number: String, page: Int): List<TcgCard> {
        val normalizedSet = SetCodeMapper.normalizeDecklistSetCode(setId)
            ?.lowercase(Locale.ROOT)
            ?: return emptyList()
        val normalizedNumber = extractCardNumber(number)
        if (normalizedNumber.isBlank()) return emptyList()

        val setTokens = SetCodeMapper.searchTokensForSetQuery(normalizedSet)
        val numberVariants = buildNumberVariants(normalizedNumber)
        val queries = linkedSetOf<String>()
        setTokens.forEach { token ->
            numberVariants.forEach { variant ->
                queries += "$token $variant"
            }
        }

        val candidates = linkedSetOf<TcgCard>()
        for (searchQuery in queries) {
            candidates += performApiSearch(query = searchQuery, page = page, limit = 30)
            if (candidates.size >= 30) break
        }

        return candidates
            .filter { card ->
                matchesSearchSet(card.set?.id, normalizedSet) &&
                    extractCardNumber(card.number) == normalizedNumber
            }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<TcgCard> { card -> parseReleaseDateToEpoch(setReleaseDateById(card.set?.id).orEmpty()) }
                    .thenBy { card -> extractCardNumber(card.number).toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { card -> normalizeNameForLookup(card.name) }
                    .thenBy { card -> card.id }
            )
    }

    private suspend fun performApiSearch(query: String, page: Int, limit: Int): List<TcgCard> {
        ensureSetLanguageMapReady()
        val response = api.search(query = query, page = page, limit = limit)
        return response.results
            .map { it.toTcgCard() }
            .filter { card ->
                val setId = card.set?.id.orEmpty()
                if (setId.isBlank()) return@filter false
                val language = setLanguageById[setId]
                language in ALLOWED_LANGUAGES
            }
    }

    private suspend fun enrichMissingMegaSetsFromSearch(baseSets: List<PokeWalletSet>): List<PokeWalletSet> {
        if (baseSets.isEmpty()) return baseSets

        val presentIds = baseSets.map { it.setId.trim() }.toMutableSet()
        val presentCodes = baseSets
            .mapNotNull { it.setCode?.trim()?.uppercase(Locale.ROOT) }
            .toMutableSet()
        val fallbackAdds = mutableListOf<PokeWalletSet>()

        for (seed in MISSING_MEGA_SET_SEEDS) {
            if (seed.setId in presentIds || seed.setCode.uppercase(Locale.ROOT) in presentCodes) continue

            val lookup = runCatching {
                api.search(query = seed.searchQuery, page = 1, limit = 25)
            }.getOrNull() ?: continue

            val matchingCard = lookup.results.firstOrNull { card ->
                val info = card.cardInfo
                val idMatch = info?.setId?.trim() == seed.setId
                val codeMatch = info?.setCode?.trim()?.equals(seed.setCode, ignoreCase = true) == true
                idMatch || codeMatch
            } ?: continue

            val info = matchingCard.cardInfo ?: continue
            val detailSet = resolveMissingMegaSetDetail(seed, info)
            val setId = detailSet?.setId?.trim().orEmpty()
                .ifBlank { info.setId?.trim().orEmpty() }
                .ifBlank { seed.setId }
            val setCode = detailSet?.setCode?.trim().orEmpty()
                .ifBlank { info.setCode?.trim().orEmpty() }
                .ifBlank { seed.setCode }
            val setName = detailSet?.name?.trim().orEmpty()
                .ifBlank { info.setName?.trim().orEmpty() }
                .ifBlank { seed.searchQuery }
            val releaseDate = detailSet?.releaseDate?.trim().orEmpty().ifBlank { null }
            val language = detailSet?.language?.trim().orEmpty().ifBlank { null }

            fallbackAdds += PokeWalletSet(
                name = setName,
                setCode = setCode,
                setId = setId,
                cardCount = 0,
                totalCards = 0,
                language = language,
                releaseDate = releaseDate
            )
            presentIds += setId
            presentCodes += setCode.uppercase(Locale.ROOT)
        }

        if (fallbackAdds.isEmpty()) return baseSets
        return baseSets + fallbackAdds
    }

    private suspend fun resolveMissingMegaSetDetail(
        seed: MissingSetSeed,
        cardInfo: PokeWalletCardInfo
    ): PokeWalletSet? {
        val response = runCatching {
            api.getSet(seed.setCode, page = 1, limit = 1)
        }.getOrNull() ?: return null

        val expectedId = cardInfo.setId?.trim().orEmpty().ifBlank { seed.setId }
        val expectedCode = cardInfo.setCode?.trim().orEmpty().ifBlank { seed.setCode }
        val candidates = buildList {
            response.set?.let(::add)
            addAll(response.matches)
        }

        return candidates.firstOrNull { candidate ->
            candidate.setId.trim() == expectedId ||
                candidate.setCode?.trim()?.equals(expectedCode, ignoreCase = true) == true
        }
    }

    private suspend fun performAdaptiveApiSearch(query: String, page: Int): List<TcgCard> {
        val first = performApiSearch(query = query, page = page, limit = 15)
        if (first.size >= 8) return first

        val second = performApiSearch(query = query, page = page, limit = 30)
        return (first + second).distinctBy { it.id }
    }

    private fun filterByName(cards: List<TcgCard>, expectedName: String?): List<TcgCard> {
        if (expectedName == null) return cards.distinctBy { it.id }
        val normalizedQuery = normalizeNameForLookup(expectedName)
        if (normalizedQuery.isBlank()) return cards.distinctBy { it.id }
        val queryTokens = normalizedQuery.split(" ").filter { it.length >= 2 }

        return cards.filter { card ->
            val normalizedName = normalizeNameForLookup(card.name)
            normalizedName == normalizedQuery ||
                normalizedName.startsWith("$normalizedQuery ") ||
                (queryTokens.isNotEmpty() && queryTokens.all { token ->
                    " $normalizedName ".contains(" $token ")
                })
        }.distinctBy { it.id }
    }

    private fun scoreItalianNameMatch(
        normalizedName: String,
        normalizedQuery: String,
        queryTokens: List<String>,
        exactMode: Boolean
    ): Int {
        if (normalizedName == normalizedQuery) return 1000

        val startsWith = normalizedName.startsWith(normalizedQuery)
        val wordContains = " $normalizedName ".contains(" $normalizedQuery ")

        if (exactMode) {
            return when {
                startsWith -> 850
                wordContains -> 700
                else -> 0
            }
        }

        if (startsWith) return 850
        if (wordContains) return 700
        if (normalizedName.contains(normalizedQuery)) return 560

        val longTokens = queryTokens.filter { it.length >= 2 }
        if (longTokens.isNotEmpty() && longTokens.all { token -> normalizedName.contains(token) }) {
            return 420
        }
        if (longTokens.any { token -> token.length >= 3 && " $normalizedName ".contains(" $token ") }) {
            return 260
        }

        return 0
    }

    private fun scannerNameScore(normalizedName: String, normalizedQuery: String): Int {
        if (normalizedQuery.isBlank()) return 0
        if (normalizedName == normalizedQuery) return 120
        if (normalizedName.startsWith(normalizedQuery) || normalizedQuery.startsWith(normalizedName)) return 105
        if (normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName)) return 88

        val queryTokens = normalizedQuery.split(" ").filter { it.length >= 2 }
        val nameTokens = normalizedName.split(" ").filter { it.length >= 2 }
        if (queryTokens.isNotEmpty() && queryTokens.all { token -> nameTokens.any { it.contains(token) || token.contains(it) } }) {
            return 72
        }

        val distance = levenshteinDistance(normalizedName, normalizedQuery)
        val maxLength = maxOf(normalizedName.length, normalizedQuery.length).coerceAtLeast(1)
        val similarity = 1.0 - distance.toDouble() / maxLength.toDouble()
        return when {
            similarity >= 0.82 -> 68
            similarity >= 0.72 -> 52
            similarity >= 0.62 -> 36
            else -> 0
        }
    }

    private fun levenshteinDistance(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)

        for (leftIndex in left.indices) {
            current[0] = leftIndex + 1
            for (rightIndex in right.indices) {
                val substitutionCost = if (left[leftIndex] == right[rightIndex]) 0 else 1
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + substitutionCost
                )
            }
            previous.indices.forEach { index -> previous[index] = current[index] }
        }

        return previous[right.length]
    }

    private suspend fun searchCardsFromLocalCache(query: String): List<TcgCard> {
        if (query.isBlank()) return emptyList()

        val memoryPool = memoryCards.values
            .asSequence()
            .flatMap { it.asSequence() }
            .toList()

        val fullNumber = parseFullNumberQuery(query)
        if (fullNumber != null) {
            val normalizedNumber = fullNumber.first
            val total = fullNumber.second
            val totalVariants = linkedSetOf(total, total.padStart(3, '0'))
            val exactSetIds = getCandidateSetIdsByPrintedTotal(total, tolerance = 0).toSet()
            val nearSetIds = getCandidateSetIdsByPrintedTotal(total, tolerance = 1).toSet()

            val memoryMatches = memoryPool.filter { card ->
                extractCardNumber(card.number) == normalizedNumber
            }
            if (memoryMatches.isNotEmpty()) {
                return memoryMatches
                    .distinctBy { it.id }
                    .sortedWith(
                        compareByDescending<TcgCard> { card ->
                            val setId = card.set?.id.orEmpty()
                            when {
                                setId in exactSetIds -> 3
                                setId in nearSetIds -> 2
                                else -> 0
                            }
                        }.thenByDescending { card ->
                            if (extractPrintedTotalForSearch(card.number) in totalVariants) 1 else 0
                        }.thenByDescending { card ->
                            parseReleaseDateToEpoch(setReleaseDateById(card.set?.id).orEmpty())
                        }
                    )
            }

            val roomMatches = runCatching {
                db.cardDao().getByNumber(normalizedNumber)
            }.onFailure { err ->
                Timber.w(err, "searchCardsFromLocalCache: errore Room numero=%s", normalizedNumber)
            }.getOrDefault(emptyList())
                .map { it.toTcgCard() }

            return roomMatches
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<TcgCard> { card ->
                        val setId = card.set?.id.orEmpty()
                        when {
                            setId in exactSetIds -> 3
                            setId in nearSetIds -> 2
                            else -> 0
                        }
                    }.thenByDescending { card ->
                        if (extractPrintedTotalForSearch(card.number) in totalVariants) 1 else 0
                    }.thenByDescending { card ->
                        parseReleaseDateToEpoch(setReleaseDateById(card.set?.id).orEmpty())
                    }
                )
        }

        val parts = query.split(" ").filter { it.isNotBlank() }
        if (parts.size == 2 && parts[1].all { it.isDigit() }) {
            val setId = parts[0]
            val normalizedNumber = parts[1].trimStart('0').ifEmpty { "0" }

            val memoryMatches = memoryPool.filter { card ->
                card.set?.id.equals(setId, ignoreCase = true) &&
                    extractCardNumber(card.number) == normalizedNumber
            }
            if (memoryMatches.isNotEmpty()) return memoryMatches.distinctBy { it.id }

            val roomMatches = runCatching {
                db.cardDao().getBySetIdAndNumber(setId, normalizedNumber)
            }.onFailure { err ->
                Timber.w(err, "searchCardsFromLocalCache: errore Room set+numero=%s %s", setId, normalizedNumber)
            }.getOrDefault(emptyList())
                .map { it.toTcgCard() }

            if (roomMatches.isNotEmpty()) return roomMatches.distinctBy { it.id }
        }

        if (parts.size == 1 && parts[0].all { it.isDigit() }) {
            val normalizedNumber = parts[0].trimStart('0').ifEmpty { "0" }

            val memoryMatches = memoryPool.filter { extractCardNumber(it.number) == normalizedNumber }
            if (memoryMatches.isNotEmpty()) return memoryMatches.distinctBy { it.id }

            val roomMatches = runCatching {
                db.cardDao().getByNumber(normalizedNumber)
            }.onFailure { err ->
                Timber.w(err, "searchCardsFromLocalCache: errore Room solo numero=%s", normalizedNumber)
            }.getOrDefault(emptyList())
                .map { it.toTcgCard() }

            return roomMatches.distinctBy { it.id }
        }

        val namePattern = "%$query%"
        val memoryMatches = memoryPool
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(60)
        if (memoryMatches.isNotEmpty()) return memoryMatches.distinctBy { it.id }

        return runCatching {
            db.cardDao().searchByName(namePattern, 60)
                .map { it.toTcgCard() }
                .distinctBy { it.id }
        }.onFailure { err ->
            Timber.w(err, "searchCardsFromLocalCache: errore Room nome=%s", query)
        }.getOrDefault(emptyList())
    }

    private suspend fun rankSearchResults(
        query: String,
        cards: List<TcgCard>,
        fullNumber: Pair<String, String>?
    ): List<TcgCard> {
        if (cards.isEmpty()) return cards
        if (fullNumber != null) {
            // Number searches are already sorted by precision in performPreciseNumberSearch.
            return cards
        }

        val normalizedQuery = normalizeNameForLookup(query)
        if (normalizedQuery.isBlank()) return cards
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }

        return cards.sortedWith(
            compareByDescending<TcgCard> { card -> scoreNameMatch(card.name, normalizedQuery, queryTokens) }
                .thenByDescending { card -> parseReleaseDateToEpoch(setReleaseDateById(card.set?.id).orEmpty()) }
                .thenBy { card -> extractCardNumber(card.number).toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { card -> normalizeNameForLookup(card.name) }
                .thenBy { card -> card.id }
        )
    }

    private fun scoreNameMatch(cardName: String, normalizedQuery: String, queryTokens: List<String>): Int {
        val normalizedName = normalizeNameForLookup(cardName)
        return when {
            normalizedName == normalizedQuery -> 500
            normalizedName.startsWith(normalizedQuery) -> 420
            " $normalizedName ".contains(" $normalizedQuery ") -> 350
            queryTokens.isNotEmpty() && queryTokens.all { token -> normalizedName.contains(token) } -> 280
            queryTokens.any { token -> token.length >= 4 } &&
                queryTokens.filter { it.length >= 4 }.any { token ->
                    " $normalizedName ".contains(" $token ")
                } -> 140
            else -> 0
        }
    }

    private fun setReleaseDateById(setId: String?): String? {
        val safeSetId = setId?.takeIf { it.isNotBlank() } ?: return null
        return memorySets?.firstOrNull { it.id == safeSetId }?.releaseDate
    }

    private fun extractPrintedTotalForSearch(number: String): String {
        val total = number.substringAfter("/", "").trim()
        if (total.isBlank()) return ""
        return total.trimStart('0').ifEmpty { "0" }
    }

    private fun parseFullNumberQuery(raw: String): Pair<String, String>? {
        val strict = FULL_NUMBER_REGEX.matchEntire(raw)
        if (strict != null) {
            val number = strict.groupValues[1].trimStart('0').ifEmpty { "0" }
            val total = strict.groupValues[2].trimStart('0').ifEmpty { "0" }
            return number to total
        }
        val flex = FLEX_FULL_NUMBER_REGEX.matchEntire(raw)
        if (flex != null) {
            val number = flex.groupValues[1].trimStart('0').ifEmpty { "0" }
            val total = flex.groupValues[2].trimStart('0').ifEmpty { "0" }
            return number to total
        }
        return null
    }

    private fun parseSetNumberQuery(raw: String): Pair<String, String>? {
        val match = FLEX_SET_NUMBER_REGEX.matchEntire(raw) ?: return null
        val rawSet = match.groupValues[1]
        val rawNumber = match.groupValues[2]

        if (rawSet.all { it.isDigit() }) return null
        if (rawNumber.contains("/")) return null

        val normalizedSet = SetCodeMapper.normalizeDecklistSetCode(rawSet)
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val normalizedNumber = extractCardNumber(rawNumber)
        if (normalizedNumber.isBlank()) return null

        return normalizedSet to normalizedNumber
    }

    private fun matchesSearchSet(cardSetId: String?, expectedSetId: String): Boolean {
        val normalizedCardSet = SetCodeMapper.normalizeDecklistSetCode(cardSetId)
            ?.lowercase(Locale.ROOT)
            ?: return false
        return normalizedCardSet == expectedSetId
    }

    private fun getCandidateSetsByPrintedTotal(total: String?, tolerance: Int): List<TcgSet> {
        val parsedTotal = total?.toIntOrNull() ?: return emptyList()
        val sets = memorySets ?: return emptyList()

        val exactMatches = sets.filter { it.printedTotal == parsedTotal }
        if (exactMatches.isNotEmpty()) return exactMatches.sortedByDescending { parseReleaseDateToEpoch(it.releaseDate) }
        if (tolerance <= 0) return emptyList()

        return sets
            .map { it to abs(it.printedTotal - parsedTotal) }
            .filter { (_, diff) -> diff in 1..tolerance }
            .sortedWith(compareBy<Pair<TcgSet, Int>> { it.second }.thenByDescending { parseReleaseDateToEpoch(it.first.releaseDate) })
            .map { it.first }
    }

    private suspend fun buildStrictSetNumberQueries(setCode: String, rawNumber: String?): List<String> {
        val setTokens = SetCodeMapper.searchTokensForSetQuery(setCode)
        val numberVariants = buildNumberVariants(rawNumber)
        val queries = linkedSetOf<String>()
        setTokens.forEach { token ->
            numberVariants.forEach { number ->
                queries += "$token $number"
            }
        }
        return queries.toList()
    }

    private suspend fun buildStrictNameSetNumberQueries(name: String, setCode: String, rawNumber: String?): List<String> {
        val setTokens = SetCodeMapper.searchTokensForSetQuery(setCode)
        val nameVariants = buildNameQueryVariants(name)
        val numberVariants = buildNumberVariants(rawNumber)
        val queries = linkedSetOf<String>()
        nameVariants.forEach { variant ->
            setTokens.forEach { token ->
                numberVariants.forEach { number ->
                    queries += "$variant $token $number"
                    queries += "$token $number $variant"
                    queries += "$token $variant $number"
                }
            }
        }
        return queries.toList()
    }

    private fun buildNameQueryVariants(rawName: String): List<String> {
        val original = sanitizeQuery(rawName)
        val normalized = normalizeNameForLookup(rawName)
        val titleCased = normalized
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }

        return linkedSetOf(original, titleCased)
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun buildNumberVariants(rawNumber: String?): List<String> {
        val raw = rawNumber?.substringBefore('/')?.trim().orEmpty()
        val normalized = extractCardNumber(raw)
        if (normalized.isBlank()) return emptyList()

        val variants = linkedSetOf<String>()
        if (raw.isNotBlank()) variants += raw
        variants += normalized
        if (normalized.all { it.isDigit() }) {
            variants += normalized.padStart(2, '0')
            variants += normalized.padStart(3, '0')
        }
        return variants.toList()
    }

    private fun matchesExactSetAndNumber(card: PokeWalletCard, expectedSet: String, expectedNumber: String): Boolean {
        if (!isActualCard(card)) return false
        val cardSet = SetCodeMapper.normalizeDecklistSetCode(card.cardInfo?.setCode)?.lowercase()
        val cardNumber = extractCardNumber(card.cardInfo?.cardNumber.orEmpty())
        return cardSet == expectedSet && cardNumber == expectedNumber
    }

    private fun matchesExactNameSetAndNumber(
        card: PokeWalletCard,
        expectedName: String,
        expectedSet: String,
        expectedNumber: String
    ): Boolean {
        if (!matchesExactSetAndNumber(card, expectedSet, expectedNumber)) return false
        return normalizeNameForLookup(card.cardInfo?.name.orEmpty()) == normalizeNameForLookup(expectedName)
    }

    private suspend fun rankStrictMatches(cards: List<PokeWalletCard>): List<PokeWalletCard> {
        val releaseBySet = getReleaseEpochByCanonicalSetCode()
        return cards.sortedWith(
            compareByDescending<PokeWalletCard> { card ->
                val setCode = SetCodeMapper.normalizeDecklistSetCode(card.cardInfo?.setCode)?.lowercase()
                releaseBySet[setCode] ?: Long.MIN_VALUE
            }.thenBy { card ->
                extractCardNumber(card.cardInfo?.cardNumber.orEmpty()).toIntOrNull() ?: Int.MAX_VALUE
            }
        )
    }

    private suspend fun getReleaseEpochByCanonicalSetCode(): Map<String, Long> {
        val sets = memorySets ?: loadSetsFromRoom(ignoreExpiry = true).orEmpty()
        if (sets.isEmpty()) return emptyMap()

        return sets.groupBy { set ->
            val imageRef = extractSetRefFromImageUrl(set.images.symbol)
                ?: extractSetRefFromImageUrl(set.images.logo)
                ?: set.id
            SetCodeMapper.normalizeDecklistSetCode(imageRef)?.lowercase() ?: imageRef.lowercase()
        }.mapValues { (_, values) ->
            values.maxOfOrNull { set -> parseReleaseDateToEpoch(set.releaseDate) } ?: Long.MIN_VALUE
        }
    }

    private fun extractSetRefFromImageUrl(url: String): String? {
        return Regex("""/sets/([^/?]+)/image(?:\?.*)?$""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun resolveImportedSetCandidates(canonicalSetCode: String): List<TcgSet> {
        val sets = memorySets
            ?: loadSetsFromRoom(ignoreExpiry = true)
            ?: getSets(forceRefresh = false).getOrDefault(emptyList())

        return sets.filter { set ->
            val setRef = extractSetRefFromImageUrl(set.images.symbol)
                ?: extractSetRefFromImageUrl(set.images.logo)
                ?: return@filter false
            SetCodeMapper.normalizeDecklistSetCode(setRef)?.lowercase() == canonicalSetCode
        }.sortedByDescending { parseReleaseDateToEpoch(it.releaseDate) }
    }

    private fun isItalianSetId(setId: String): Boolean {
        return setId.trim().lowercase(Locale.ROOT).endsWith(ITALIAN_SET_SUFFIX)
    }

    private fun isItalianOverlayCardId(cardId: String): Boolean {
        return cardId.trim().lowercase(Locale.ROOT).startsWith("ita:")
    }

    private fun buildItalianSetId(expansionId: String): String {
        return "${expansionId.trim().lowercase(Locale.ROOT)}$ITALIAN_SET_SUFFIX"
    }

    private fun parseItalianExpansionId(setId: String): String? {
        val normalized = setId.trim().lowercase(Locale.ROOT)
        if (!normalized.endsWith(ITALIAN_SET_SUFFIX)) return null
        return normalized.removeSuffix(ITALIAN_SET_SUFFIX).ifBlank { null }
    }

    private fun normalizeItalianSetCode(raw: String): String {
        return SetCodeMapper.normalizeDecklistSetCode(raw)
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?: raw.trim().uppercase(Locale.ROOT)
    }

    private fun matchesItalianSetHint(setId: String?, expectedSetId: String): Boolean {
        val expansionId = setId?.let(::parseItalianExpansionId) ?: return false
        return matchesItalianExpansionHint(expansionId, expectedSetId)
    }

    private fun matchesItalianExpansionHint(expansionId: String, expectedSetId: String): Boolean {
        val preferredBase = preferredBaseSetCodeForItalianExpansion(expansionId)
        val candidates = linkedSetOf<String>()
        preferredBase?.let { code ->
            SetCodeMapper.normalizeDecklistSetCode(code)
                ?.lowercase(Locale.ROOT)
                ?.let(candidates::add)
        }
        SetCodeMapper.normalizeDecklistSetCode(expansionId)
            ?.lowercase(Locale.ROOT)
            ?.let(candidates::add)
        return expectedSetId in candidates
    }

    private fun preferredBaseSetCodeForItalianExpansion(expansionId: String): String? {
        return when (expansionId.trim().lowercase(Locale.ROOT)) {
            "me01" -> "MEG"
            "me02" -> "PFL"
            "me03" -> "ME03"
            "me04" -> "CRI"
            "me2pt5" -> "ASC"
            "mep" -> "MEP"
            "sv01" -> "SVI"
            "sv02" -> "PAL"
            "sv03" -> "OBF"
            "sv04" -> "PAR"
            "sv05" -> "TEF"
            "sv06" -> "TWM"
            "sv07" -> "SCR"
            "sv08" -> "SSP"
            "sv09" -> "JTG"
            "sv10" -> "DRI"
                "zsv10pt5" -> "BLK"
                "rsv10pt5" -> "WHT"
            "sv3pt5" -> "MEW"
            "sv4pt5" -> "PAF"
            "sv6pt5" -> "SFA"
            "sv8pt5" -> "PRE"
            else -> null
        }
    }

    private fun buildItalianCardId(record: ItalianCardRecord): String {
        val imageRef = record.imageReference()
        val normalizedSetCode = imageRef?.setCode ?: normalizeItalianSetCode(record.espansioneId)
        val normalizedNumber = imageRef?.cardNumber ?: extractCardNumber(record.cardId)
        return "ita:${normalizedSetCode.lowercase(Locale.ROOT)}:$normalizedNumber"
    }

    private fun findExactRawSetMatch(sets: List<TcgSet>, rawSetCode: String?): TcgSet? {
        val target = rawSetCode?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return sets
            .asSequence()
            .filter { set ->
                if (isItalianSetId(set.id)) return@filter false
                val setRef = extractSetRefFromImageUrl(set.images.symbol)
                    ?: extractSetRefFromImageUrl(set.images.logo)
                    ?: return@filter false
                setRef.equals(target, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<TcgSet> { set ->
                    set.language?.trim()?.uppercase(Locale.ROOT) == "ENG"
                }.thenByDescending { set ->
                    parseReleaseDateToEpoch(set.releaseDate)
                }
            )
            .firstOrNull()
    }

    private suspend fun resolveItalianExpansionIdForSet(
        setId: String,
        catalog: ItalianCatalog
    ): String? {
        val safeSetId = setId.trim()
        if (safeSetId.isBlank()) return null

        val direct = safeSetId.lowercase(Locale.ROOT)
        if (catalog.cardsByExpansion().containsKey(direct)) {
            return direct
        }

        val set = memorySets?.firstOrNull { it.id == safeSetId }
            ?: loadSetsFromRoom(ignoreExpiry = true)?.firstOrNull { it.id == safeSetId }

        val setRef = set?.let {
            extractSetRefFromImageUrl(it.images.symbol)
                ?: extractSetRefFromImageUrl(it.images.logo)
        }
        val setName = set?.name.orEmpty()

        val italianExpansionId = parseItalianExpansionId(safeSetId)

        val preferredBaseSetCode = italianExpansionId?.let(::preferredBaseSetCodeForItalianExpansion)
        val targetRaw = (preferredBaseSetCode ?: setRef ?: safeSetId).trim().uppercase(Locale.ROOT)
        val targetCanonical = normalizeItalianSetCode(preferredBaseSetCode ?: setRef ?: safeSetId)
        val cardsByExpansion = catalog.cardsByExpansion()

        val preferredExpansionId = preferredItalianExpansionIdHint(
            setName = setName,
            targetRawSetCode = targetRaw,
            targetCanonicalSetCode = targetCanonical
        )
        if (preferredExpansionId != null && cardsByExpansion.containsKey(preferredExpansionId)) {
            return preferredExpansionId
        }

        val byRawCardCode = catalog.expansions.firstOrNull { manifest ->
            val rawFromCards = cardsByExpansion[manifest.espansioneId.lowercase(Locale.ROOT)]
                .orEmpty()
                .asSequence()
                .mapNotNull { record -> record.imageReference()?.setCode }
                .map { code -> code.trim().uppercase(Locale.ROOT) }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
            rawFromCards == targetRaw
        }

        if (byRawCardCode != null) {
            return byRawCardCode.espansioneId.trim().lowercase(Locale.ROOT)
        }

        val byCardCode = catalog.expansions.firstOrNull { manifest ->
            val canonicalFromCards = cardsByExpansion[manifest.espansioneId.lowercase(Locale.ROOT)]
                .orEmpty()
                .asSequence()
                .mapNotNull { record -> record.imageReference()?.setCode }
                .map { code -> normalizeItalianSetCode(code) }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
            canonicalFromCards == targetCanonical
        }

        if (byCardCode != null) {
            return byCardCode.espansioneId.trim().lowercase(Locale.ROOT)
        }

        return catalog.expansions
            .firstOrNull { manifest ->
                normalizeItalianSetCode(manifest.espansioneId) == targetCanonical
            }
            ?.espansioneId
            ?.trim()
            ?.lowercase(Locale.ROOT)
    }

    private fun preferredItalianExpansionIdHint(
        setName: String,
        targetRawSetCode: String,
        targetCanonicalSetCode: String
    ): String? {
        val normalizedName = setName.trim().lowercase(Locale.ROOT)

        if (
            normalizedName.contains("ascesa eroica") ||
            normalizedName.contains("ascended heroes") ||
            targetRawSetCode == "ASC" ||
            targetCanonicalSetCode == "ASC"
        ) {
            return "me2pt5"
        }

        if (
            normalizedName.contains("caos nascente") ||
            normalizedName.contains("chaos rising") ||
            targetRawSetCode == "CRI" ||
            targetCanonicalSetCode == "DCR"
        ) {
            return "me04"
        }

        if (
            normalizedName.contains("fiamme spettrali") ||
            normalizedName.contains("phantasmal flames") ||
            targetRawSetCode == "PFL" ||
            targetCanonicalSetCode == "PFL"
        ) {
            return "mep"
        }

        return null
    }

    private suspend fun mergeItalianSets(
        baseSets: List<TcgSet>,
        context: Context?,
        forceRefresh: Boolean
    ): List<TcgSet> {
        val nonItalianSets = baseSets.filterNot { isItalianSetId(it.id) }
        if (context == null) return nonItalianSets

        val catalog = italianCatalogRepository.getCatalog(context, forceRefresh = forceRefresh)
            .getOrElse {
                Timber.w(it, "mergeItalianSets: catalogo ITA non disponibile")
                return nonItalianSets
            }

        val cardsByExpansion = catalog.cardsByExpansion()
        if (cardsByExpansion.isEmpty()) return nonItalianSets

        // Pre-index nonItalianSets once: raw setRef (uppercase) → newest set, and
        // canonical (normalized) setRef → first set. Avoids O(N*M) linear scans per expansion.
        // We prefer ENG sets so that ITA expansions always inherit ENG logos/series (never JAP/CHN);
        // a non-ENG fallback is still allowed if no ENG match exists.
        fun TcgSet.langPriority(): Int = when (language?.trim()?.uppercase(Locale.ROOT).orEmpty()) {
            "ENG" -> 0
            "" -> 1
            else -> 2
        }
        val setsByRawRef: Map<String, TcgSet> = run {
            val grouped = HashMap<String, MutableList<TcgSet>>(nonItalianSets.size)
            for (set in nonItalianSets) {
                val ref = (extractSetRefFromImageUrl(set.images.symbol)
                    ?: extractSetRefFromImageUrl(set.images.logo))
                    ?.trim()?.uppercase(Locale.ROOT)
                    ?: continue
                grouped.getOrPut(ref) { mutableListOf() }.add(set)
            }
            grouped.mapValues { (_, list) ->
                list.sortedWith(
                    compareBy<TcgSet> { it.langPriority() }
                        .thenByDescending { parseReleaseDateToEpoch(it.releaseDate) }
                ).first()
            }
        }
        val setsByCanonicalRef: Map<String, TcgSet> = run {
            val map = HashMap<String, TcgSet>(setsByRawRef.size)
            // Iterate ENG-first so canonical key resolves to the ENG variant when present.
            val ordered = setsByRawRef.values.sortedBy { it.langPriority() }
            for (set in ordered) {
                val ref = extractSetRefFromImageUrl(set.images.symbol)
                    ?: extractSetRefFromImageUrl(set.images.logo)
                    ?: continue
                val canonical = normalizeItalianSetCode(ref)
                map.putIfAbsent(canonical, set)
            }
            map
        }

        val italianSets = catalog.expansions.mapNotNull { manifest ->
            val expansionId = manifest.espansioneId.trim().lowercase(Locale.ROOT)
            if (expansionId.isBlank()) return@mapNotNull null

            val expansionCards = cardsByExpansion[expansionId].orEmpty()
            val preferredBaseSetCode = preferredBaseSetCodeForItalianExpansion(expansionId)
            val dominantRawSetCode = expansionCards
                .asSequence()
                .mapNotNull { record -> record.imageReference()?.setCode }
                .map { code -> code.trim().uppercase(Locale.ROOT) }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key

            val baseRawSetCode = preferredBaseSetCode ?: dominantRawSetCode ?: expansionId.uppercase(Locale.ROOT)
            val dominantCanonicalSetCode = normalizeItalianSetCode(baseRawSetCode)

            val linkedBase = setsByRawRef[baseRawSetCode.trim().uppercase(Locale.ROOT)]
                ?: setsByCanonicalRef[dominantCanonicalSetCode]

            val linkedPrintedTotal = linkedBase?.printedTotal?.takeIf { it > 0 }
            val cardCount = linkedPrintedTotal ?: manifest.cardCount.takeIf { it > 0 } ?: expansionCards.size
            val setName = linkedBase?.name?.takeIf { it.isNotBlank() }
                ?: baseRawSetCode
                ?: expansionId.uppercase(Locale.ROOT)
            val setSeries = linkedBase?.series?.takeIf { it.isNotBlank() }
                ?: deriveSeriesName(
                    setCode = baseRawSetCode,
                    language = "ITA",
                    setName = setName
                )
            val setImages = linkedBase?.images ?: SetImages(
                symbol = buildSetImageUrl(baseRawSetCode),
                logo = buildSetImageUrl(baseRawSetCode)
            )

            TcgSet(
                id = buildItalianSetId(expansionId),
                name = setName,
                series = setSeries,
                language = "ITA",
                printedTotal = cardCount,
                total = cardCount,
                releaseDate = linkedBase?.releaseDate.orEmpty(),
                images = setImages
            )
        }

        return (nonItalianSets + italianSets)
            .distinctBy { it.id }
    }

    private suspend fun getCardsByItalianSet(
        setId: String,
        context: Context?,
        forceRefresh: Boolean
    ): Result<List<TcgCard>> {
        val safeContext = context ?: return Result.failure(
            IllegalStateException("Context richiesto per caricare catalogo ITA")
        )

        val expansionId = parseItalianExpansionId(setId) ?: return Result.success(emptyList())

        // Fast path: fetch just this expansion's cards (~100-200) instead of the whole
        // ~15k-card catalog blob. Falls back to the full catalog on any failure (network,
        // endpoint not yet deployed, etc.) so opening a set can never become less reliable.
        val expansionCards = italianCatalogRepository
            .getExpansionCards(
                baseUrl = PokeWalletRetrofitClient.imageBaseUrl,
                expansionId = expansionId,
                forceRefresh = forceRefresh
            )
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: italianCatalogRepository.getCatalog(safeContext, forceRefresh = forceRefresh)
                .getOrElse { return Result.failure(it) }
                .cardsByExpansion()[expansionId].orEmpty()
        val cacheKey = setId.trim().lowercase(Locale.ROOT)

        if (!forceRefresh) {
            val expectedCardIds = expansionCards.mapTo(linkedSetOf()) { record -> buildItalianCardId(record) }
            val cachedCards = memoryItalianCards[cacheKey] ?: memoryCards[setId]
            if (!cachedCards.isNullOrEmpty()) {
                val cachedIds = cachedCards.mapTo(linkedSetOf()) { it.id }
                if (cachedIds == expectedCardIds) {
                    return Result.success(cachedCards)
                }
            }
        }

        val setInfo = memorySets?.firstOrNull { it.id == setId } ?: loadSetsFromRoom(ignoreExpiry = true)
            ?.firstOrNull { it.id == setId }
            ?: TcgSet(
                id = setId,
                name = expansionId.uppercase(Locale.ROOT),
                series = deriveSeriesName(setCode = expansionId, language = "ENG", setName = expansionId),
                language = "ENG"
            )

        val preferredBaseSetCode = preferredBaseSetCodeForItalianExpansion(expansionId)
        val dominantRawSetCode = expansionCards
            .asSequence()
            .mapNotNull { record -> record.imageReference()?.setCode }
            .map { code -> code.trim().uppercase(Locale.ROOT) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val baseSetId = resolveEnglishBaseSetIdForItalianSet(
            italianSet = setInfo,
            dominantRawSetCode = preferredBaseSetCode ?: dominantRawSetCode
        )

        val baseCards = baseSetId?.let { resolvedBaseSetId ->
            loadStandardCardsForSet(resolvedBaseSetId, context = safeContext, forceRefresh = forceRefresh)
                .getOrDefault(emptyList())
        } ?: emptyList()
        val baseCardsByNumber = baseCards.associateBy { extractCardNumber(it.number) }

        val cards = expansionCards
            .map { record ->
                val baseCard = baseCardsByNumber[record.imageReference()?.cardNumber ?: extractCardNumber(record.cardId)]
                toItalianTcgCard(record, setInfo, baseCard)
            }
            .sortedBy { card -> extractCardNumber(card.number).toIntOrNull() ?: Int.MAX_VALUE }

        memoryItalianCards[cacheKey] = cards
        memoryCards[setId] = cards
        updateSetTotalsFromKnownCards(setId = setId, cardsCount = cards.size)
        return Result.success(cards)
    }

    private suspend fun resolveEnglishBaseSetIdForItalianSet(
        italianSet: TcgSet,
        dominantRawSetCode: String?
    ): String? {
        val setRef = extractSetRefFromImageUrl(italianSet.images.symbol)
            ?: extractSetRefFromImageUrl(italianSet.images.logo)
        val candidateCodes = linkedSetOf<String>()

        findExactRawSetMatch(
            sets = memorySets ?: loadSetsFromRoom(ignoreExpiry = true) ?: getSets(forceRefresh = false).getOrDefault(emptyList()),
            rawSetCode = dominantRawSetCode
        )?.let { return it.id }

        findExactRawSetMatch(
            sets = memorySets ?: loadSetsFromRoom(ignoreExpiry = true) ?: getSets(forceRefresh = false).getOrDefault(emptyList()),
            rawSetCode = setRef
        )?.let { return it.id }

        dominantRawSetCode?.takeIf { it.isNotBlank() }?.let { candidateCodes += normalizeItalianSetCode(it).lowercase(Locale.ROOT) }
        setRef?.takeIf { it.isNotBlank() }?.let { candidateCodes += normalizeItalianSetCode(it).lowercase(Locale.ROOT) }

        for (candidateCode in candidateCodes) {
            val baseSet = resolveImportedSetCandidates(candidateCode)
                .firstOrNull { !isItalianSetId(it.id) }
            if (baseSet != null) {
                return baseSet.id
            }
        }

        return null
    }

    private suspend fun toItalianTcgCard(record: ItalianCardRecord, setInfo: TcgSet, baseCard: TcgCard? = null): TcgCard {
        val imageRef = record.imageReference()
        val normalizedSetCode = imageRef?.setCode ?: normalizeItalianSetCode(record.espansioneId)
        val normalizedNumber = imageRef?.cardNumber ?: extractCardNumber(record.cardId)
        val smallImage = record.imageUrl(PokeWalletRetrofitClient.imageBaseUrl, size = "low")
            ?: "${PokeWalletRetrofitClient.imageBaseUrl}images/it/$normalizedSetCode/$normalizedNumber?size=low"
        val largeImage = record.imageUrl(PokeWalletRetrofitClient.imageBaseUrl, size = "high")
            ?: "${PokeWalletRetrofitClient.imageBaseUrl}images/it/$normalizedSetCode/$normalizedNumber?size=high"
        val smallImageWithBust = appendItalianImageCacheBuster(smallImage)
        val largeImageWithBust = appendItalianImageCacheBuster(largeImage)
        val cardSet = TcgCardSet(
            id = setInfo.id,
            name = setInfo.name,
            series = setInfo.series,
            printedTotal = setInfo.printedTotal
        )

        return TcgCard(
            id = buildItalianCardId(record),
            name = record.nome.ifBlank { baseCard?.name.orEmpty() },
            supertype = baseCard?.supertype?.takeIf { it.isNotBlank() } ?: deriveItalianSupertype(record),
            subtypes = baseCard?.subtypes ?: emptyList(),
            hp = record.ps?.takeIf { it.isNotBlank() } ?: baseCard?.hp,
            types = record.tipo?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: baseCard?.types,
            set = cardSet,
            number = normalizedNumber,
            rarity = baseCard?.rarity?.takeIf { it.isNotBlank() },
            images = CardImages(
                small = smallImageWithBust,
                large = largeImageWithBust
            ),
            tcgplayer = baseCard?.tcgplayer,
            cardmarket = baseCard?.cardmarket
        )
    }

    private fun appendItalianImageCacheBuster(url: String): String {
        if (url.isBlank()) return url
        val separator = if (url.contains('?')) '&' else '?'
        return "$url${separator}itv=r2v3"
    }

    private suspend fun getItalianOverlayCards(
        setId: String,
        context: Context?,
        forceRefresh: Boolean
    ): List<TcgCard>? {
        val safeContext = context ?: return null
        val cacheKey = setId.trim().lowercase(Locale.ROOT)
        val refreshItalianCatalog = forceRefresh

        val catalog = italianCatalogRepository.getCatalog(safeContext, forceRefresh = refreshItalianCatalog)
            .getOrElse { return null }

        val expansionId = resolveItalianExpansionIdForSet(setId = setId, catalog = catalog) ?: cacheKey
        val records = catalog.cardsByExpansion()[expansionId].orEmpty()
        if (records.isEmpty()) return null

        val baseCards = loadCachedStandardCardsForSet(setId)
        val baseCardsByNumber = baseCards.associateBy { extractCardNumber(it.number) }
        val setInfo = memorySets?.firstOrNull { it.id == setId }
            ?: loadSetsFromRoom(ignoreExpiry = true)?.firstOrNull { it.id == setId }
            ?: TcgSet(id = setId, name = setId, series = deriveSeriesName(setCode = setId, language = "ENG", setName = setId), language = "ENG")

        val cards = records.map { record ->
            val key = record.imageReference()?.cardNumber ?: extractCardNumber(record.cardId)
            toItalianTcgCard(record, setInfo, baseCardsByNumber[key])
        }.sortedBy { card -> extractCardNumber(card.number).toIntOrNull() ?: Int.MAX_VALUE }

        memoryItalianCards[cacheKey] = cards
        return cards
    }

    private suspend fun loadCachedStandardCardsForSet(setId: String): List<TcgCard> {
        memoryCards[setId]?.let { cached ->
            if (cached.isNotEmpty()) return cached
        }

        val roomCards = loadCardsFromRoom(setId, ignoreExpiry = true)
        if (roomCards != null && roomCards.isNotEmpty()) {
            memoryCards[setId] = roomCards
            return roomCards
        }

        return emptyList()
    }

    private suspend fun loadStandardCardsForSet(
        setId: String,
        context: Context?,
        forceRefresh: Boolean,
        preferredImageMacro: String? = null
    ): Result<List<TcgCard>> {
        if (context == null) {
            return Result.failure(IllegalStateException("Context richiesto per caricare carte"))
        }

        if (!forceRefresh) {
            memoryCards[setId]?.let {
                if (it.isNotEmpty()) {
                    val localizedCards = adaptPilotImagesForCurrentLocale(setId, it, preferredImageMacro)
                    memoryCards[setId] = localizedCards
                    updateSetTotalsFromKnownCards(setId = setId, cardsCount = it.size)
                    recordCacheHit("getCardsBySet:memory:$setId")
                    return Result.success(localizedCards)
                }
            }
        }

        if (!forceRefresh) {
            val roomCards = loadCardsFromRoom(setId)
            if (roomCards != null) {
                val localizedCards = adaptPilotImagesForCurrentLocale(setId, roomCards, preferredImageMacro)
                memoryCards[setId] = localizedCards
                updateSetTotalsFromKnownCards(setId = setId, cardsCount = roomCards.size)
                recordCacheHit("getCardsBySet:room:$setId")
                return Result.success(localizedCards)
            }
        }

        ensureSetLanguageMapReady()
        if (!isAllowedSetLanguage(setId)) {
            return Result.success(emptyList())
        }

        recordCacheMiss("getCardsBySet:$setId")

        val staleCache = loadCardsFromRoom(setId, ignoreExpiry = true)
        val networkResult = guardedApiCall(resourceKey = "cards:$setId") {
            fetchAllCardsForSet(setId)
        }

        return if (networkResult.isSuccess) {
            val networkCards = networkResult.getOrThrow().cards
            val localizedCards = adaptPilotImagesForCurrentLocale(setId, networkCards, preferredImageMacro)
            memoryCards[setId] = localizedCards
            saveCardsToRoom(networkCards)
            updateSetTotalsFromKnownCards(setId = setId, cardsCount = localizedCards.size)
            Result.success(localizedCards)
        } else {
            val fallback = memoryCards[setId] ?: staleCache?.let {
                adaptPilotImagesForCurrentLocale(setId, it, preferredImageMacro)
            }
            fallback?.let {
                recordCacheHit("getCardsBySet:stale:$setId")
                Result.success(it)
            } ?: Result.failure(networkResult.exceptionOrNull()!!)
        }
    }

    private fun deriveItalianSupertype(record: ItalianCardRecord): String {
        val tipo = record.tipo?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            tipo.contains("allenator") || tipo.contains("trainer") -> "Trainer"
            tipo.contains("energ") -> "Energy"
            else -> if (record.ps?.toIntOrNull() ?: 0 > 0) "Pokémon" else "Trainer"
        }
    }

    private suspend fun resolveItalianCardRarity(canonicalSetCode: String, normalizedNumber: String): String? {
        val normalizedSet = SetCodeMapper.normalizeDecklistSetCode(canonicalSetCode)
            ?.lowercase(Locale.ROOT)
            ?: return null

        val candidates = resolveImportedSetCandidates(normalizedSet)
            .filterNot { isItalianSetId(it.id) }

        for (candidate in candidates) {
            memoryCards[candidate.id]
                ?.firstOrNull { card -> extractCardNumber(card.number) == normalizedNumber }
                ?.rarity
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }

            val roomRarity = runCatching {
                db.cardDao().getBySetIdAndNumber(candidate.id, normalizedNumber)
                    .firstOrNull()
                    ?.rarity
            }.getOrNull()

            if (!roomRarity.isNullOrBlank()) {
                return roomRarity
            }
        }

        return null
    }

    private fun selectCatalogMatch(cards: List<TcgCard>, normalizedName: String?): TcgCard? {
        if (cards.isEmpty()) return null
        if (normalizedName == null) return cards.firstOrNull()

        val queryTokens = normalizedName.split(" ").filter { it.length >= 2 }
        return cards
            .sortedWith(
                compareByDescending<TcgCard> { card ->
                    val normalizedCardName = normalizeNameForLookup(card.name)
                    when {
                        normalizedCardName == normalizedName -> 3
                        normalizedCardName.startsWith("$normalizedName ") -> 2
                        queryTokens.isNotEmpty() && queryTokens.all { token ->
                            " $normalizedCardName ".contains(" $token ")
                        } -> 1
                        else -> 0
                    }
                }.thenByDescending { card -> parseReleaseDateToEpoch(setReleaseDateById(card.set?.id).orEmpty()) }
                    .thenBy { card -> normalizeNameForLookup(card.name) }
                    .thenBy { card -> card.id }
            )
            .firstOrNull { card ->
                val normalizedCardName = normalizeNameForLookup(card.name)
                normalizedCardName == normalizedName ||
                    normalizedCardName.startsWith("$normalizedName ") ||
                    (queryTokens.isNotEmpty() && queryTokens.all { token ->
                        " $normalizedCardName ".contains(" $token ")
                    })
            }
    }

    private fun preferredNameQuery(rawName: String): String {
        return buildNameQueryVariants(rawName).firstOrNull().orEmpty()
    }

    // ── Room Cache Layer ──────────────────────────────────────────────

    private suspend fun saveSetsToRoom(sets: List<TcgSet>) {
        try {
            db.setDao().upsertSets(sets.map { it.toEntity() })
        } catch (e: Exception) {
            Timber.w(e, "Errore salvataggio cache set Room")
        }
    }

    private suspend fun loadSetsFromRoom(ignoreExpiry: Boolean = false): List<TcgSet>? {
        return try {
            val lastTime = db.setDao().getLastCacheTime() ?: return null
            if (!ignoreExpiry && System.currentTimeMillis() - lastTime > SETS_CACHE_DURATION) return null
            val entities = db.setDao().getAll()
            if (entities.isEmpty()) return null
            if (entities.any { isLegacyNumericSetImageUrl(it.symbolUrl) || isLegacyNumericSetImageUrl(it.logoUrl) }) {
                return null
            }
            entities.map { it.toTcgSet() }.also { refreshLanguageMapFromSets(it) }
        } catch (e: Exception) {
            Timber.w(e, "Errore lettura cache set Room")
            null
        }
    }

    private fun isLegacyNumericSetImageUrl(url: String): Boolean {
        return Regex("""/sets/\d+/image(?:\?.*)?$""").containsMatchIn(url)
    }

    private suspend fun saveCardsToRoom(cards: List<TcgCard>) {
        try {
            db.cardDao().upsertCards(cards.map { it.toEntity() })
        } catch (e: Exception) {
            Timber.w(e, "Errore salvataggio cache carte Room")
        }
    }

    private suspend fun loadCardsFromRoom(setId: String, ignoreExpiry: Boolean = false): List<TcgCard>? {
        return try {
            val lastTime = db.cardDao().getLastCacheTimeForSet(setId) ?: return null
            if (!ignoreExpiry && System.currentTimeMillis() - lastTime > CARDS_CACHE_DURATION) return null
            val entities = db.cardDao().getBySetId(setId)
            if (entities.isEmpty()) return null
            entities.map { it.toTcgCard() }
        } catch (e: Exception) {
            Timber.w(e, "Errore lettura cache carte Room per set %s", setId)
            null
        }
    }

    private suspend fun updateSetTotalsFromKnownCards(setId: String, cardsCount: Int) {
        if (setId.isBlank() || cardsCount <= 0) return

        val baseSets = memorySets ?: loadSetsFromRoom(ignoreExpiry = true) ?: return
        var hasChanged = false
        val updatedSets = baseSets.map { set ->
            if (set.id == setId && (set.total != cardsCount || set.printedTotal != cardsCount)) {
                hasChanged = true
                set.copy(printedTotal = cardsCount, total = cardsCount)
            } else {
                set
            }
        }

        if (hasChanged) {
            memorySets = updatedSets
            refreshLanguageMapFromSets(updatedSets)
            saveSetsToRoom(updatedSets)
        }
    }

    private fun sanitizeQuery(raw: String): String {
        return raw.replace(SANITIZE_MULTI_SPACE, " ").trim()
    }

    private fun normalizeNameForLookup(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val deCamel = raw.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        val normalized = Normalizer.normalize(deCamel, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        return sanitizeQuery(normalized)
    }

    private fun extractCardNumber(number: String): String {
        return number.substringBefore("/").trim().trimStart('0').ifEmpty { "0" }
    }

    private fun isPokeWalletCardId(cardId: String): Boolean {
        return cardId.startsWith("pk_") || HASH_ID_REGEX.matches(cardId)
    }

    private fun PokeWalletSet.toTcgSet(): TcgSet {
        // totalCards = proxy-enriched real count (only actual cards, highest accuracy).
        // cardCount = raw API value, accurate for most sets but may be inflated on sets
        // with non-card products (e.g. ME03). The proxy corrects those via KV or forced overrides.
        val printedCount = when {
            cardCount > 0 && totalCards > 0 -> minOf(cardCount, totalCards)
            cardCount > 0 -> cardCount
            totalCards > 0 -> totalCards
            else -> 0
        }
        val totalCount = printedCount
        // Remove set code prefix if present (e.g., "ME03:Perfect Order" → "Perfect Order")
        val cleanName = name.substringAfterLast(":").trim().takeIf { it.isNotBlank() } ?: name
        val imageSetRef = setCode?.takeIf { it.isNotBlank() } ?: setId
        val resolvedLanguage = mapLanguageMacro(language)
            ?: inferEnglishLanguageFallback(setCode = setCode, setName = cleanName)
        return TcgSet(
            id = setId,
            name = ItalianTranslations.translateExpansionName(cleanName),
            series = deriveSeriesName(setCode = setCode, language = language, setName = cleanName),
            language = resolvedLanguage,
            printedTotal = printedCount,
            total = totalCount,
            releaseDate = releaseDate.orEmpty(),
            images = SetImages(
                symbol = buildSetImageUrl(imageSetRef),
                logo = buildSetImageUrl(imageSetRef)
            )
        )
    }

    /**
     * Best-effort ENG language inference for newly published expansions that
     * may arrive from PokeWallet with a missing/blank `language` field.
     * Strict: only triggers on well-known Mega Evolution codes/patterns or on
     * curated English name fragments to avoid mislabelling unrelated sets.
     */
    internal fun inferEnglishLanguageFallback(setCode: String?, setName: String?): String? {
        val rawCode = setCode?.trim().orEmpty().uppercase(Locale.ROOT)
        val normalizedCode = SetCodeMapper.normalizeDecklistSetCode(rawCode)?.uppercase(Locale.ROOT)
            ?: rawCode
        if (rawCode.isNotEmpty()) {
            if (rawCode in MEGA_EVOLUTION_SET_CODES || MEGA_EVOLUTION_CODE_PATTERN.matches(rawCode)) {
                return "ENG"
            }
            if (normalizedCode in MEGA_EVOLUTION_SET_CODES || MEGA_EVOLUTION_CODE_PATTERN.matches(normalizedCode)) {
                return "ENG"
            }
        }
        val lowerName = setName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (lowerName.isNotEmpty() && ENG_NAME_HINTS.any { lowerName.contains(it) }) {
            return "ENG"
        }
        return null
    }

    private fun PokeWalletCard.toTcgCard(setOverride: PokeWalletSet? = null): TcgCard {
        val info = cardInfo
        val setId = setOverride?.setId ?: info?.setId.orEmpty()
        val rawSetName = setOverride?.name ?: info?.setName.orEmpty()
        val setName = ItalianTranslations.translateExpansionName(
            rawSetName.substringAfterLast(":").trim().ifBlank { rawSetName }
        )
        val setCode = setOverride?.setCode ?: info?.setCode
        val rawNumber = info?.cardNumber.orEmpty()
        val tcgSet = TcgCardSet(
            id = setId,
            name = setName,
            series = deriveSeriesName(setCode = setCode, language = setOverride?.language, setName = setName)
        )

        return TcgCard(
            id = id,
            name = info?.name.orEmpty(),
            supertype = deriveSupertype(info?.cardType),
            subtypes = info?.stage?.let { listOf(it) },
            hp = info?.hp?.substringBefore('.'),
            types = info?.cardType?.let(::deriveTypes),
            set = tcgSet,
            number = rawNumber.substringBefore('/').trim().ifBlank { rawNumber },
            rarity = normalizeRarity(info?.rarity),
            images = CardImages(
                small = buildCardImageUrl(
                    cardId = id,
                    size = "low",
                    setCode = setCode,
                    cardNumber = rawNumber
                ),
                large = buildCardImageUrl(
                    cardId = id,
                    size = "high",
                    setCode = setCode,
                    cardNumber = rawNumber
                )
            ),
            tcgplayer = tcgplayer?.toLegacyTcgPlayer(),
            cardmarket = cardmarket?.toLegacyCardMarket()
        )
    }

    private fun PokeWalletTcgPlayer.toLegacyTcgPlayer(): TcgPlayer {
        val mappedPrices = prices.associate { price ->
            mapSubTypeNameToKey(price.subTypeName) to TcgPriceInfo(
                low = price.lowPrice,
                mid = price.midPrice,
                high = price.highPrice,
                market = price.marketPrice
            )
        }
        return TcgPlayer(url = url.orEmpty(), prices = mappedPrices)
    }

    private fun PokeWalletCardMarket.toLegacyCardMarket(): CardMarket {
        val preferred = prices
            .firstOrNull {
                it.variantType.equals("normal", ignoreCase = true) &&
                    ((it.avg ?: 0.0) > 0.0 || (it.low ?: 0.0) > 0.0)
            }
            ?: prices.firstOrNull { (it.avg ?: 0.0) > 0.0 || (it.low ?: 0.0) > 0.0 }
            ?: prices.firstOrNull { it.variantType.equals("normal", ignoreCase = true) }
            ?: prices.firstOrNull()
        return CardMarket(
            url = productUrl.orEmpty(),
            prices = CardMarketPrices(
                averageSellPrice = preferred?.avg,
                lowPrice = preferred?.low,
                trendPrice = preferred?.trend,
                avg1 = preferred?.avg1,
                avg7 = preferred?.avg7,
                avg30 = preferred?.avg30
            )
        )
    }

    private fun buildCardImageUrl(
        cardId: String,
        size: String,
        setCode: String? = null,
        cardNumber: String? = null
    ): String {
        val encodedCardId = encodeUrlPathSegment(cardId)
        return "${PokeWalletRetrofitClient.imageBaseUrl}images/$encodedCardId?size=$size"
    }

    private suspend fun adaptPilotImagesForCurrentLocale(
        setId: String,
        cards: List<TcgCard>,
        preferredImageMacro: String? = null
    ): List<TcgCard> {
        if (cards.isEmpty()) return cards
        if (!isItalianPilotSet(setId)) return cards

        val normalizedMacro = preferredImageMacro?.trim()?.uppercase(Locale.ROOT)
        val useItalianPilotImages = when (normalizedMacro) {
            "ITA" -> true
            "ENG", "JAP", "CHN" -> false
            else -> AppLocale.isItalian
        }

        return cards.map { card ->
            val normalizedNumber = extractCardNumber(card.number)
            val smallUrl = if (useItalianPilotImages && normalizedNumber.isNotBlank()) {
                "${PokeWalletRetrofitClient.imageBaseUrl}images/it/ME03/$normalizedNumber?size=low"
            } else {
                buildCardImageUrl(card.id, "low")
            }
            val largeUrl = if (useItalianPilotImages && normalizedNumber.isNotBlank()) {
                "${PokeWalletRetrofitClient.imageBaseUrl}images/it/ME03/$normalizedNumber?size=high"
            } else {
                buildCardImageUrl(card.id, "high")
            }

            if (card.images.small == smallUrl && card.images.large == largeUrl) {
                card
            } else {
                card.copy(images = CardImages(small = smallUrl, large = largeUrl))
            }
        }
    }

    private suspend fun isItalianPilotSet(setId: String): Boolean {
        if (setId.isBlank()) return false

        val sets = memorySets ?: loadSetsFromRoom(ignoreExpiry = true).orEmpty()
        val set = sets.firstOrNull { it.id == setId } ?: return false
        val setRef = extractSetRefFromImageUrl(set.images.symbol)
            ?: extractSetRefFromImageUrl(set.images.logo)
            ?: return false
        val normalized = SetCodeMapper.normalizeDecklistSetCode(setRef)?.uppercase(Locale.ROOT)
            ?: setRef.uppercase(Locale.ROOT)
        return normalized == "ME03"
    }

    private fun encodeUrlPathSegment(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
    }

    private fun buildSetImageUrl(setRef: String): String {
        return "${PokeWalletRetrofitClient.imageBaseUrl}sets/$setRef/image?v=$SET_IMAGE_CACHE_VERSION"
    }

    private fun mapSubTypeNameToKey(subTypeName: String?): String {
        return when (subTypeName?.trim()?.lowercase()) {
            "normal" -> "normal"
            "holofoil", "holo" -> "holofoil"
            "reverse holofoil", "reverse holo" -> "reverseHolofoil"
            "1st edition holofoil" -> "1stEditionHolofoil"
            "1st edition" -> "1stEditionNormal"
            "unlimited" -> "unlimited"
            "shadowless" -> "shadowless"
            else -> subTypeName?.replace(" ", "")?.replaceFirstChar { it.lowercase() } ?: "normal"
        }
    }

    private fun deriveTypes(cardType: String): List<String> {
        val normalized = cardType.trim()
        return when (normalized.lowercase()) {
            "fire", "water", "grass", "lightning", "psychic", "fighting", "darkness", "metal", "dragon", "fairy", "colorless" -> listOf(normalized.replaceFirstChar { it.uppercase() })
            else -> emptyList()
        }
    }

    private fun deriveSupertype(cardType: String?): String {
        return when (cardType?.trim()?.lowercase()) {
            "trainer" -> "Trainer"
            "energy" -> "Energy"
            else -> "Pokémon"
        }
    }

    internal fun deriveSeriesName(setCode: String?, language: String?, setName: String?): String {
        val code = setCode?.trim()?.uppercase().orEmpty()
        val name = setName?.trim()?.lowercase().orEmpty()

        val normalizedCode = SetCodeMapper.normalizeDecklistSetCode(code)?.uppercase() ?: code
        val isMegaByName =
            name.contains("megaevoluzione") ||
                name.contains("mega evoluzione") ||
                name.contains("mega evolution") ||
                name.contains("fiamme spettrali") ||
                name.contains("phantasmal flames") ||
                name.contains("ascesa eroica") ||
                name.contains("heroic rise") ||
                name.contains("ascending heroes") ||
                name.contains("perfect order") ||
                name.contains("equilibrio perfetto") ||
                name.contains("caos nascente") ||
                name.contains("chaos rising") ||
                name.contains("abyss eye")
        val isScarletVioletByName =
            name.contains("scarlatto e violetto") ||
                name.contains("scarlet & violet") ||
                name.contains("white flare") ||
                name.contains("fuoco bianco") ||
                name.contains("black bolt") ||
                name.contains("luce nera")
        val isGymByName =
            name.contains("gym heroes") ||
                name.contains("gym challenge") ||
                name.startsWith("gym ")
        val isNeoByName =
            name.contains("neo genesis") ||
                name.contains("neo discovery") ||
                name.contains("neo revelation") ||
                name.contains("neo destiny")
        val isLegendaryCollectionByName = name.contains("legendary collection")
        val isECardByName =
            name.contains("expedition") ||
                name.contains("aquapolis") ||
                name.contains("skyridge")
        val isBaseByName =
            name.contains("base set") ||
                name == "jungle" ||
                name == "fossil" ||
                name.contains("base set 2") ||
                name.contains("team rocket") ||
                name.contains("legendary collection")
        val exactLegacySeries = LEGACY_CODE_TO_SERIES[code]

        val series = when {
            code in MEGA_EVOLUTION_SET_CODES || MEGA_EVOLUTION_CODE_PATTERN.matches(code) -> "Mega Evolutions"
            isMegaByName -> "Mega Evolutions"
            exactLegacySeries != null -> exactLegacySeries
            isGymByName -> "Gym"
            isNeoByName -> "Neo"
            isECardByName -> "e-Card"
            isLegendaryCollectionByName || isBaseByName -> "Base"
            normalizedCode in SCARLET_VIOLET_SET_CODES -> "Scarlet & Violet"
            isScarletVioletByName -> "Scarlet & Violet"
            normalizedCode.startsWith("SV") -> "Scarlet & Violet"
            normalizedCode.startsWith("SWSH") -> "Sword & Shield"
            normalizedCode.startsWith("SM") -> "Sun & Moon"
            normalizedCode.startsWith("XY") -> "XY"
            normalizedCode.startsWith("BW") -> "Black & White"
            normalizedCode.startsWith("HGSS") || normalizedCode.startsWith("HS") -> "HeartGold & SoulSilver"
            normalizedCode.startsWith("DP") -> "Diamond & Pearl"
            normalizedCode.startsWith("EX") -> "EX"
            else -> "Other"
        }
        return ItalianTranslations.translateSeriesName(series)
    }

    private fun isAllowedSetLanguage(setId: String): Boolean {
        val language = setLanguageById[setId]
        // If language is unknown at this stage, allow card retrieval and let
        // set-level filtering decide after first successful response.
        return language == null || language in ALLOWED_LANGUAGES
    }

    private fun refreshLanguageMapFromSets(sets: List<TcgSet>) {
        sets.forEach { set ->
            setLanguageById[set.id] = set.language
        }
    }

    private suspend fun ensureSetLanguageMapReady() {
        if (setLanguageById.isNotEmpty()) return
        setLanguageMapMutex.withLock {
            if (setLanguageById.isNotEmpty()) return@withLock

            // Try existing in-memory sets first (0 credits).
            memorySets?.takeIf { it.isNotEmpty() }?.let {
                refreshLanguageMapFromSets(it)
                return@withLock
            }

            val response = api.getSets()
            response.data.forEach { remoteSet ->
                setLanguageById[remoteSet.setId] = mapLanguageMacro(remoteSet.language)
            }
            recordNetworkCall("bootstrap:setLanguageMap")
        }
    }

    private fun mapLanguageMacro(raw: String?): String? {
        val normalized = raw?.trim()?.lowercase()?.replace('_', ' ') ?: return null
        return when {
            normalized in setOf("it", "ita", "italian", "italiano") || normalized.contains("ital") -> "ITA"
            normalized in setOf("en", "eng", "english", "inglese") || normalized.contains("engl") || normalized.contains("ingl") -> "ENG"
            normalized in setOf("jp", "jap", "ja", "japanese", "giapponese") || normalized.contains("jap") || normalized.contains("giapp") -> "JAP"
            normalized in setOf("zh", "zhs", "zht", "cn", "chn", "chi", "chinese") ||
                normalized.contains("chinese") ||
                normalized.contains("mandarin") ||
                normalized.contains("simplified chinese") ||
                normalized.contains("traditional chinese") ||
                normalized.contains("cinese") -> "CHN"
            else -> null
        }
    }

    private fun normalizeRarity(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        val key = value.lowercase()
        return when {
            "special illustration rare" in key || "sir" == key -> "Special Illustration Rare"
            "illustration rare" in key || key == "ir" -> "Illustration Rare"
            "hyper rare" in key -> "Hyper Rare"
            "ultra rare" in key -> "Ultra Rare"
            "double rare" in key -> "Double Rare"
            "ace spec" in key -> "ACE SPEC Rare"
            "radiant" in key -> "Radiant Rare"
            "amazing" in key -> "Amazing Rare"
            "secret" in key -> "Secret Rare"
            "trainer gallery" in key -> "Trainer Gallery Rare"
            "holo" in key && "rare" in key -> "Rare Holo"
            "rare" in key -> "Rare"
            "uncommon" in key -> "Uncommon"
            "common" in key -> "Common"
            else -> value
        }
    }

    private fun parseReleaseDateToEpoch(value: String?): Long {
        val source = value?.trim().orEmpty()
        if (source.isBlank()) return 0L

        parseDate(source, ISO_DATE)?.let { return it }

        val cleaned = source
            .replace(Regex("(\\d+)(st|nd|rd|th)"), "$1")
            .replace("_", " ")
            .trim()

        parseDate(cleaned, HUMAN_DATE_LONG)?.let { return it }
        parseDate(cleaned, HUMAN_DATE_SHORT)?.let { return it }
        return 0L
    }

    private fun parseDate(value: String, formatter: DateTimeFormatter): Long? {
        return try {
            LocalDate.parse(value, formatter).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private suspend fun <T> guardedApiCall(resourceKey: String, call: suspend () -> T): Result<T> {
        val now = System.currentTimeMillis()
        if (now < globalRateLimitUntil) {
            return Result.failure(IllegalStateException("Rate limit cooldown active"))
        }

        lastNetworkAttempt[resourceKey] = now

        return try {
            val result = call()
            recordNetworkCall(resourceKey)
            Result.success(result)
        } catch (e: HttpException) {
            if (e.code() == 429) {
                globalRateLimitUntil = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
            }
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun recordCacheHit(tag: String) {
        cacheHitCount++
        if (com.emabuia.pokevault.BuildConfig.DEBUG) {
            Timber.d("CACHE_HIT[%s] hit=%d miss=%d net=%d", tag, cacheHitCount, cacheMissCount, networkCallCount)
        }
    }

    private fun recordCacheMiss(tag: String) {
        cacheMissCount++
        if (com.emabuia.pokevault.BuildConfig.DEBUG) {
            Timber.d("CACHE_MISS[%s] hit=%d miss=%d net=%d", tag, cacheHitCount, cacheMissCount, networkCallCount)
        }
    }

    private fun recordNetworkCall(tag: String) {
        networkCallCount++
        if (com.emabuia.pokevault.BuildConfig.DEBUG) {
            Timber.d("NETWORK_CALL[%s] hit=%d miss=%d net=%d", tag, cacheHitCount, cacheMissCount, networkCallCount)
        }
    }

    private data class SetCardsResult(
        val cards: List<TcgCard>,
        val totalCount: Int
    )
}
