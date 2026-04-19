package com.emabuia.pokevault.data.remote

import android.content.Context
import com.emabuia.pokevault.data.local.toEntity
import com.emabuia.pokevault.data.local.toTcgCard
import com.emabuia.pokevault.data.local.toTcgSet
import com.emabuia.pokevault.data.local.ItalianTranslations
import com.google.gson.Gson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class PokeTcgRepository {

    private val api = PokeWalletRetrofitClient.create(com.emabuia.pokevault.BuildConfig.POKEWALLET_API_KEY)
    private val db get() = RepositoryProvider.database
    private val gson = Gson()

    @Volatile
    private var memorySets: List<TcgSet>? = null
    private val memoryCards = ConcurrentHashMap<String, List<TcgCard>>()
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

    companion object {
        private const val SETS_CACHE_DURATION = 7 * 24 * 60 * 60 * 1000L   // 7 days
        private const val CARDS_CACHE_DURATION = 30 * 24 * 60 * 60 * 1000L  // 30 days
        private const val SEARCH_CACHE_DURATION = 60 * 60 * 1000L           // 1 hour
        private val ALLOWED_LANGUAGES = setOf("ENG")

        private const val RATE_LIMIT_COOLDOWN_MS = 60 * 1000L

        private val SANITIZE_MULTI_SPACE = Regex("\\s+")
        private val LEGACY_ID_REGEX = Regex("^([A-Za-z0-9]+)-(.+)$")
        private val HASH_ID_REGEX = Regex("^[a-f0-9]{32,}$", RegexOption.IGNORE_CASE)
        private val FULL_NUMBER_REGEX = Regex("""^(\\d+)/(\\d+)$""")

        private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE
        private val HUMAN_DATE_LONG = DateTimeFormatter.ofPattern("d MMMM, uuuu", Locale.ENGLISH)
        private val HUMAN_DATE_SHORT = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)
    }

    suspend fun getSets(context: Context? = null, forceRefresh: Boolean = false): Result<List<TcgSet>> =
        setsMutex.withLock {
            if (!forceRefresh && memorySets != null) {
                recordCacheHit("getSets:memory")
                return Result.success(memorySets!!)
            }

            // L2: Room DB
            if (!forceRefresh) {
                val roomSets = loadSetsFromRoom()
                if (roomSets != null) {
                    memorySets = roomSets
                    recordCacheHit("getSets:room")
                    return Result.success(roomSets)
                }
            }

            // Stale fallback for network errors
            val staleCache = loadSetsFromRoom(ignoreExpiry = true)

            recordCacheMiss("getSets")

            val networkResult = guardedApiCall(resourceKey = "sets") {
                val response = api.getSets()
                setLanguageById.clear()
                response.data
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
                memorySets = sets
                refreshLanguageMapFromSets(sets)
                saveSetsToRoom(sets)
            }

            if (networkResult.isSuccess) {
                networkResult
            } else {
                staleCache?.let {
                    memorySets = it
                    refreshLanguageMapFromSets(it)
                    recordCacheHit("getSets:stale")
                    Result.success(it)
                } ?: networkResult
            }
        }

    suspend fun getCardsBySet(setId: String, context: Context? = null, forceRefresh: Boolean = false): Result<List<TcgCard>> =
        cardsMutex.withLock {
            // L1: Memory
            if (!forceRefresh) {
                memoryCards[setId]?.let {
                    if (it.isNotEmpty()) {
                        recordCacheHit("getCardsBySet:memory:$setId")
                        return Result.success(it)
                    }
                }
            }

            // L2: Room DB
            if (!forceRefresh) {
                val roomCards = loadCardsFromRoom(setId)
                if (roomCards != null) {
                    memoryCards[setId] = roomCards
                    recordCacheHit("getCardsBySet:room:$setId")
                    return Result.success(roomCards)
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

            networkResult.onSuccess { result ->
                memoryCards[setId] = result.cards
                saveCardsToRoom(result.cards)
            }

            if (networkResult.isSuccess) {
                Result.success(networkResult.getOrThrow().cards)
            } else {
                val fallback = memoryCards[setId] ?: staleCache
                fallback?.let {
                    recordCacheHit("getCardsBySet:stale:$setId")
                    Result.success(it)
                } ?: Result.failure(networkResult.exceptionOrNull()!!)
            }
        }

    suspend fun getSetInfo(setId: String): Result<TcgSet> = setInfoMutex.withLock {
        memorySets?.firstOrNull { it.id == setId }?.let { return Result.success(it) }

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

    suspend fun searchCards(query: String, page: Int = 1): Result<List<TcgCard>> = searchMutex.withLock {
        if (query.isBlank()) return Result.success(emptyList())

        val normalized = sanitizeQuery(query)
        val cacheKey = "search::$normalized::$page"
        memorySearch[cacheKey]?.let { (cards, timestamp) ->
            if (System.currentTimeMillis() - timestamp < SEARCH_CACHE_DURATION) {
                recordCacheHit("search:$normalized:$page")
                return Result.success(cards)
            }
        }
        recordCacheMiss("search:$normalized:$page")

        val networkResult = guardedApiCall(resourceKey = "search:$normalized:$page") {
            val cards = when (val match = FULL_NUMBER_REGEX.matchEntire(normalized)) {
                null -> performGenericSearch(normalized, page)
                else -> {
                    val number = match.groupValues[1].trimStart('0').ifEmpty { "0" }
                    val total = match.groupValues[2].trimStart('0').ifEmpty { "0" }
                    val candidateSetIds = getCandidateSetIdsByPrintedTotal(total, tolerance = 1).toSet()
                    val broad = performAdaptiveApiSearch(number, page = 1)
                    if (candidateSetIds.isEmpty()) {
                        broad.filter { extractCardNumber(it.number) == number }
                    } else {
                        broad.filter {
                            extractCardNumber(it.number) == number && it.set?.id in candidateSetIds
                        }
                    }
                }
            }
            cards.distinctBy { it.id }
        }

        networkResult.onSuccess { cards ->
            memorySearch[cacheKey] = cards to System.currentTimeMillis()
        }

        networkResult
    }

    suspend fun searchCardsFuzzy(name: String, page: Int = 1): Result<List<TcgCard>> {
        val clean = sanitizeQuery(name)
        if (clean.isBlank()) return Result.success(emptyList())

        val direct = searchCards(clean, page).getOrDefault(emptyList())
        if (direct.isNotEmpty()) return Result.success(direct)

        val firstWord = clean.split(" ").firstOrNull().orEmpty()
        if (firstWord.length >= 3 && firstWord != clean) {
            return searchCards(firstWord, page)
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

    fun getCandidateSetIdsByPrintedTotal(total: String?, tolerance: Int = 1): List<String> {
        return getCandidateSetsByPrintedTotal(total, tolerance).map { it.id }
    }

    fun getPrintedTotalForSet(setId: String?): Int? {
        val safeSetId = setId?.takeIf { it.isNotBlank() } ?: return null
        return memorySets?.firstOrNull { it.id == safeSetId }?.printedTotal
    }

    suspend fun getCard(cardId: String): Result<TcgCard> = cardByIdMutex.withLock {
        val networkResult = guardedApiCall(resourceKey = "card:$cardId") {
            when {
                isPokeWalletCardId(cardId) -> api.getCard(cardId).toTcgCard()
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

        if (networkResult.isSuccess) return networkResult

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
                // Store API total count to know when to stop fetching
                apiTotalCount = set.totalCards.takeIf { it > 0 } ?: set.cardCount.takeIf { it > 0 } ?: batch.size
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

    private suspend fun performAdaptiveApiSearch(query: String, page: Int): List<TcgCard> {
        val first = performApiSearch(query = query, page = page, limit = 15)
        if (first.size >= 8) return first

        val second = performApiSearch(query = query, page = page, limit = 30)
        return (first + second).distinctBy { it.id }
    }

    private fun filterByName(cards: List<TcgCard>, expectedName: String?): List<TcgCard> {
        if (expectedName == null) return cards.distinctBy { it.id }
        return cards.filter { it.name.contains(expectedName, ignoreCase = true) }.distinctBy { it.id }
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
            entities.map { it.toTcgSet() }.also { refreshLanguageMapFromSets(it) }
        } catch (e: Exception) {
            Timber.w(e, "Errore lettura cache set Room")
            null
        }
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

    private fun sanitizeQuery(raw: String): String {
        return raw.replace(SANITIZE_MULTI_SPACE, " ").trim()
    }

    private fun extractCardNumber(number: String): String {
        return number.substringBefore("/").trim().trimStart('0').ifEmpty { "0" }
    }

    private fun isPokeWalletCardId(cardId: String): Boolean {
        return cardId.startsWith("pk_") || HASH_ID_REGEX.matches(cardId)
    }

    private fun PokeWalletSet.toTcgSet(): TcgSet {
        val totalCount = totalCards.takeIf { it > 0 } ?: cardCount
        // Remove set code prefix if present (e.g., "ME03:Perfect Order" → "Perfect Order")
        val cleanName = name.substringAfterLast(":").trim().takeIf { it.isNotBlank() } ?: name
        return TcgSet(
            id = setId,
            name = ItalianTranslations.translateExpansionName(cleanName),
            series = deriveSeriesName(setCode = setCode, language = language, setName = cleanName),
            language = mapLanguageMacro(language),
            printedTotal = totalCount,
            total = totalCount,
            releaseDate = releaseDate.orEmpty(),
            images = SetImages(
                symbol = buildSetImageUrl(setId),
                logo = buildSetImageUrl(setId)
            )
        )
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
                small = buildCardImageUrl(id, "low"),
                large = buildCardImageUrl(id, "high")
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

    private fun buildCardImageUrl(cardId: String, size: String): String {
        return "${PokeWalletRetrofitClient.imageBaseUrl}images/$cardId?size=$size"
    }

    private fun buildSetImageUrl(setId: String): String {
        return "${PokeWalletRetrofitClient.imageBaseUrl}sets/$setId/image"
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

    private fun deriveSeriesName(setCode: String?, language: String?, setName: String?): String {
        val code = setCode?.uppercase().orEmpty()
        val name = setName?.lowercase().orEmpty()

        if (name.contains("mega")) return ItalianTranslations.translateSeriesName("Mega Evolutions")
        if (name.contains("world championship")) return ItalianTranslations.translateSeriesName("World Championships")
        if (name.contains("black star") || name.contains("promo")) return ItalianTranslations.translateSeriesName("Promos")

        val series = when {
            code.startsWith("ME") || code.startsWith("MEX") || code.startsWith("MEGA") -> "Mega Evolutions"
            code.startsWith("SV") -> "Scarlet & Violet"
            code.startsWith("SWSH") -> "Sword & Shield"
            code.startsWith("SM") -> "Sun & Moon"
            code.startsWith("XY") -> "XY"
            code.startsWith("BW") -> "Black & White"
            code.startsWith("HGSS") -> "HeartGold & SoulSilver"
            code.startsWith("DP") -> "Diamond & Pearl"
            code.startsWith("EX") -> "EX"
            code.isNotBlank() -> code.takeWhile { !it.isDigit() }.ifBlank { code }
            else -> language?.uppercase().orEmpty()
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
            normalized in setOf("it", "ita", "italian", "italiano") || normalized.contains("ital") -> "IT"
            normalized in setOf("en", "eng", "english", "inglese") || normalized.contains("engl") || normalized.contains("ingl") -> "ENG"
            normalized in setOf("jp", "jap", "ja", "japanese", "giapponese") || normalized.contains("jap") || normalized.contains("giapp") -> "JAP"
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
