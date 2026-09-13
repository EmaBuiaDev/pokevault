package com.emabuia.pokevault.data.remote

import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.data.model.MetaArchetype
import com.emabuia.pokevault.data.model.MetaDeck
import com.emabuia.pokevault.data.model.MetaDeckCard
import com.emabuia.pokevault.data.model.TournamentKind
import com.emabuia.pokevault.data.model.TournamentResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit

object LimitlessRetrofitClient {
    private const val BASE_URL = "https://play.limitlesstcg.com/api/"

    private val okHttpClient = OkHttpClient.Builder()
        // Primo della catena: deve poter fermare la richiesta prima che
        // qualunque altro interceptor la tocchi. Vedi LimitlessRateLimiter.
        .addInterceptor(LimitlessRateLimitInterceptor())
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        })
        // Una sola connessione per volta verso Limitless: con sei chiamate in
        // parallelo il server vede una raffica, e la raffica e' esattamente
        // cio' che fa scattare il 429.
        .dispatcher(okhttp3.Dispatcher().apply { maxRequestsPerHost = 3 })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: LimitlessTcgApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LimitlessTcgApiService::class.java)
    }
}

class LimitlessTcgRepository {

    private val api = LimitlessRetrofitClient.apiService
    private val gson = Gson()

    private data class CachedArchetypes(
        val archetypes: List<MetaArchetype>,
        val timestamp: Long
    )

    companion object {
        private const val CACHE_DURATION = 30 * 60 * 1000L // 30 minuti

        /**
         * Sotto questa soglia un evento non e' un risultato competitivo.
         *
         * L'API restituisce ogni torneo gestito con Limitless, comprese le
         * serate da quattro giocatori in un negozio: comparivano in cima alla
         * sezione Win Tournament accanto ai Regional, e sono la ragione per cui
         * la lista sembrava casuale.
         */
        private const val MIN_TOURNAMENT_PLAYERS = 8

        /** Quanti tornei recenti scandire alla ricerca di quelli richiesti. */
        private const val TOURNAMENT_CANDIDATE_WINDOW = 60

        /** Quanti dettagli chiedere insieme, per non martellare l'API. */
        private const val DETAILS_CONCURRENCY = 3

        /**
         * Quanti dettagli **nuovi** scaricare in un singolo caricamento.
         *
         * Con il filtro "Dal vivo" si potrebbe voler sapere il tipo di tutti e
         * sessanta i candidati, ma sessanta richieste sono piu' della meta'
         * della finestra di rate limit: si arriverebbe a 429 per una lista di
         * dieci tornei. Il tetto vale solo per i dettagli che mancano — quelli
         * gia' su disco non costano niente — per cui la finestra dei candidati
         * si copre comunque nel giro di due o tre aperture, e da li' in poi il
         * filtro e' immediato.
         */
        private const val MAX_NEW_DETAILS_PER_LOAD = 14

        /** Quanti standings chiedere per aggregare gli archetipi. */
        private const val ARCHETYPE_TOURNAMENTS = 12

        // Cache in memoria condivisa a livello di processo: sopravvive alla
        // navigazione tra le schermate, così tornando su DeckLab non rifacciamo
        // decine di richieste. Viene invalidata solo dopo CACHE_DURATION
        // oppure esplicitamente via [clearCache] o [refresh].
        // ConcurrentHashMap perché letto/scritto da più coroutine su Dispatchers.IO.
        private val archetypeCache = java.util.concurrent.ConcurrentHashMap<String, CachedArchetypes>()

        private data class CachedTournamentResults(
            val results: List<TournamentResult>,
            val timestamp: Long
        )
        private val tournamentResultsCache = java.util.concurrent.ConcurrentHashMap<String, CachedTournamentResults>()

        private data class CachedTournamentList(
            val tournaments: List<LimitlessTournament>,
            val timestamp: Long
        )
        private val tournamentListCache = java.util.concurrent.ConcurrentHashMap<String, CachedTournamentList>()

        /** Dettagli per id, senza scadenza: vedi [LimitlessTcgRepository.tournamentDetails]. */
        private val detailsCache = java.util.concurrent.ConcurrentHashMap<String, LimitlessTournamentDetails>()

        /**
         * Standings per id di torneo, condivisi fra archetipi e Win Tournament.
         *
         * Le due sezioni guardano la stessa finestra di tornei recenti e
         * chiedevano gli stessi standings ciascuna per conto suo: aprire
         * entrambe le tab costava il doppio delle richieste per gli stessi
         * dati. Gli standings di un torneo concluso non cambiano, quindi una
         * volta letti valgono per tutti e due.
         *
         * Restano in memoria e non su disco: con le decklist di trentadue
         * giocatori sono la cosa piu' pesante che l'API restituisce, e non
         * vale la pena riempirci le SharedPreferences.
         */
        private val standingsCache = java.util.concurrent.ConcurrentHashMap<String, List<LimitlessStanding>>()

        /**
         * Restituisce il timestamp più recente di una voce valida in cache
         * per il formato richiesto, o null se non c'è niente.
         */
        fun lastCacheTimestamp(format: String): Long? {
            val inMemory = listOfNotNull(
                archetypeCache["archetypes_$format"]?.timestamp,
                tournamentResultsCache.entries
                    .filter { it.key.startsWith("results_${format}_") }
                    .maxOfOrNull { it.value.timestamp }
            ).maxOrNull()

            // Al primo avvio la memoria e' vuota ma il disco no: senza questo
            // la UI direbbe "mai aggiornato" mostrando dati di dieci minuti fa.
            val onDisk = LimitlessLocalCache.timestampOf(archetypesKey(format))
            return listOfNotNull(inMemory, onDisk).maxOrNull()
        }

        private fun archetypesKey(format: String) = "archetypes_$format"
    }

    /**
     * Aggrega gli archetipi dal meta competitivo.
     * Recupera tornei + standings, raggruppa per archetipo e calcola meta share.
     * Simile a limitlesstcg.com/decks.
     */
    suspend fun getMetaArchetypes(
        format: String = "standard"
    ): Result<List<MetaArchetype>> = withContext(Dispatchers.IO) {
        loadMetaArchetypes(format)
    }

    /**
     * Il corpo vero, sempre su [Dispatchers.IO].
     *
     * Non e' una suddivisione estetica: qui dentro si leggono e si scrivono le
     * SharedPreferences della cache e si deserializzano decklist di trentadue
     * giocatori per torneo. Sul thread principale — dove `viewModelScope`
     * esegue di default — sarebbero scatti visibili durante lo scorrimento.
     */
    private suspend fun loadMetaArchetypes(format: String): Result<List<MetaArchetype>> {
        val cacheKey = archetypesKey(format)
        archetypeCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_DURATION) {
                return Result.success(cached.archetypes)
            }
        }

        // Cache su disco: al primo avvio evita di rispendere l'intera finestra
        // di rate limit per dati calcolati dieci minuti prima.
        readArchetypesFromDisk(cacheKey, CACHE_DURATION)?.let { fresh ->
            archetypeCache[cacheKey] = CachedArchetypes(fresh, LimitlessLocalCache.timestampOf(cacheKey) ?: 0L)
            return Result.success(fresh)
        }

        return try {
            val apiFormat = when (format.lowercase()) {
                "standard" -> "standard"
                "expanded" -> "expanded"
                else -> "standard"
            }

            // Stessa finestra di candidati della sezione Win Tournament: e' la
            // stessa chiamata allo stesso endpoint, e farla due volte era
            // spendere due richieste per ricevere due liste identiche.
            val tournaments = getTournamentCandidates(apiFormat).take(ARCHETYPE_TOURNAMENTS)
            if (tournaments.isEmpty()) return Result.success(emptyList())

            // Raccogli tutti gli standings con deck info
            data class DeckEntry(
                val archetype: String,
                val placement: Int,
                val winrate: Double?,
                val metaDeck: MetaDeck
            )

            // Fetch in parallelo: per 15 tornei sequenziali avremmo sommato
            // 15x la latenza di rete; con coroutineScope+async partono insieme.
            val allEntries = coroutineScope {
                tournaments.map { tournament ->
                    async(Dispatchers.IO) {
                        try {
                            val standings = standingsOf(tournament.id)
                            // Prendi top 32 (o tutti quelli con decklist)
                            val withDeck = standings
                                .filter { it.deck?.name != null || it.decklist != null }
                                .sortedBy { it.placing }
                                .take(32)

                            withDeck.mapNotNull { standing ->
                                val archName = standing.deck?.name
                                    ?: inferArchetype(parseDecklistCards(standing.decklist))
                                if (archName.isBlank() || archName == "Unknown") return@mapNotNull null

                                val record = standing.record
                                val wr = if (record != null) {
                                    val total = record.wins + record.losses + record.ties
                                    if (total > 0) record.wins.toDouble() / total else null
                                } else null

                                val metaDeck = mapToMetaDeck(standing, tournament)

                                DeckEntry(
                                    archetype = archName,
                                    placement = standing.placing,
                                    winrate = wr,
                                    metaDeck = metaDeck
                                )
                            }
                        } catch (e: Exception) {
                            Timber.w("Errore standings per archetypes: ${e.message}")
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }

            if (allEntries.isEmpty()) return Result.success(emptyList())

            // Raggruppa per archetipo
            val totalDecks = allEntries.size
            val grouped = allEntries.groupBy { it.archetype.lowercase().trim() }

            val archetypes = grouped.map { (_, entries) ->
                val displayName = entries.first().archetype
                val count = entries.size
                val metaShare = (count.toDouble() / totalDecks) * 100.0
                val winrates = entries.mapNotNull { it.winrate }
                val avgWr = if (winrates.isNotEmpty()) winrates.average() else 0.0
                val topPlace = entries.minOf { it.placement }
                val recent = entries.sortedBy { it.placement }.take(5).map { it.placement }
                // Usa il deck con miglior piazzamento come sample
                val bestDeck = entries.minByOrNull { it.placement }?.metaDeck

                MetaArchetype(
                    name = displayName,
                    count = count,
                    metaShare = metaShare,
                    avgWinrate = avgWr,
                    topPlacement = topPlace,
                    recentResults = recent,
                    sampleDeck = bestDeck
                )
            }.sortedByDescending { it.metaShare }

            archetypeCache[cacheKey] = CachedArchetypes(archetypes, System.currentTimeMillis())
            LimitlessLocalCache.write(cacheKey, archetypes)
            Result.success(archetypes)
        } catch (e: Exception) {
            Timber.e(e, "Errore fetch archetypes: ${e.message}")
            archetypeCache[cacheKey]?.let { return Result.success(it.archetypes) }
            readArchetypesFromDisk(cacheKey, maxAgeMs = null)?.let { return Result.success(it) }
            Result.failure(e)
        }
    }

    /**
     * Parsing flessibile della decklist.
     * L'API potrebbe restituire la decklist in diversi formati:
     *
     * Formato 1 - Mappa per categoria:
     * {"pokemon": [{"count":4,"name":"...","set":"...","number":"..."}], "trainer": [...], "energy": [...]}
     *
     * Formato 2 - Lista piatta:
     * [{"count":4,"name":"...","set":"...","number":"..."}]
     *
     * Formato 3 - Mappa con card IDs:
     * {"deck": [{"id":"OBF_125","count":4}]}
     */
    private fun parseDecklistCards(decklist: Any?): List<MetaDeckCard> {
        if (decklist == null) return emptyList()

        val cards = mutableListOf<MetaDeckCard>()

        try {
            val json = gson.toJson(decklist)
            Timber.d("Decklist raw JSON (troncato): ${json.take(500)}")

            // Prova Formato 1: Mappa con chiavi "pokemon", "trainer", "energy"
            try {
                val mapType = TypeToken.getParameterized(Map::class.java, String::class.java, Any::class.java).type
                val map: Map<String, Any> = gson.fromJson(json, mapType)

                val categoryKeys = mapOf(
                    "pokemon" to "pokemon",
                    "pokémon" to "pokemon",
                    "trainer" to "trainer",
                    "energy" to "energy"
                )

                for ((key, type) in categoryKeys) {
                    val categoryData = map[key] ?: continue
                    val categoryJson = gson.toJson(categoryData)
                    val categoryCards = parseCardList(categoryJson, type)
                    cards.addAll(categoryCards)
                }

                if (cards.isNotEmpty()) {
                    Timber.d("Parsed ${cards.size} carte (formato mappa per categoria)")
                    return cards
                }

                // Potrebbe essere formato {"deck": [...]}
                val deckData = map["deck"]
                if (deckData != null) {
                    val deckJson = gson.toJson(deckData)
                    val deckCards = parseCardList(deckJson, null)
                    if (deckCards.isNotEmpty()) {
                        Timber.d("Parsed ${deckCards.size} carte (formato deck array)")
                        return deckCards
                    }
                }
            } catch (_: Exception) {
                // Non è una mappa, prova come lista
            }

            // Prova Formato 2: Lista piatta di carte
            try {
                val listCards = parseCardList(json, null)
                if (listCards.isNotEmpty()) {
                    Timber.d("Parsed ${listCards.size} carte (formato lista piatta)")
                    return listCards
                }
            } catch (_: Exception) {
                // Non è una lista
            }

        } catch (e: Exception) {
            Timber.w("Errore parsing decklist: ${e.message}")
        }

        return cards
    }

    /**
     * Parsa una lista JSON di carte. Gestisce sia il formato con name/set/number
     * che il formato con solo id (es. "OBF_125").
     */
    private fun parseCardList(json: String, forcedType: String?): List<MetaDeckCard> {
        val cards = mutableListOf<MetaDeckCard>()

        try {
            val innerMapType = TypeToken.getParameterized(Map::class.java, String::class.java, Any::class.java).type
            val listType = TypeToken.getParameterized(List::class.java, innerMapType).type
            val rawList: List<Map<String, Any>> = gson.fromJson(json, listType)

            for (item in rawList) {
                val count = when (val c = item["count"]) {
                    is Number -> c.toInt()
                    is String -> c.toIntOrNull() ?: 1
                    else -> {
                        // Potrebbe essere "amount" invece di "count"
                        when (val a = item["amount"]) {
                            is Number -> a.toInt()
                            is String -> a.toIntOrNull() ?: 1
                            else -> 1
                        }
                    }
                }

                val name = (item["name"] as? String) ?: ""
                val set = (item["set"] as? String) ?: ""
                val number = (item["number"] as? String) ?: ""
                val cardId = (item["id"] as? String) ?: ""

                // Se abbiamo un ID ma non un nome, usa l'ID come nome e prova a estrarre set/number
                val finalName: String
                val finalSet: String
                val finalNumber: String

                if (name.isNotEmpty()) {
                    finalName = name
                    finalSet = SetCodeMapper.normalizeDecklistSetCode(set) ?: set
                    finalNumber = number
                } else if (cardId.isNotEmpty()) {
                    // Formato ID tipo "OBF_125" → set="OBF", number="125"
                    val parts = cardId.split("_", limit = 2)
                    val parsedSet = parts.getOrElse(0) { "" }
                    finalSet = SetCodeMapper.normalizeDecklistSetCode(parsedSet) ?: parsedSet
                    finalNumber = parts.getOrElse(1) { "" }
                    finalName = cardId // Usa l'ID come nome fallback
                } else {
                    continue // Salta carte senza nome né ID
                }

                val type = forcedType ?: classifyCardByName(finalName)

                cards.add(
                    MetaDeckCard(
                        name = finalName,
                        set = finalSet.ifEmpty { null },
                        number = finalNumber.ifEmpty { null },
                        qty = count,
                        type = type
                    )
                )
            }
        } catch (e: Exception) {
            Timber.w("Errore parseCardList: ${e.message}")
        }

        return cards
    }

    private fun mapToMetaDeck(
        standing: LimitlessStanding,
        tournament: LimitlessTournament
    ): MetaDeck {
        val cards = parseDecklistCards(standing.decklist)

        // Calcola winrate dal record
        val record = standing.record
        val winrate = if (record != null) {
            val totalGames = record.wins + record.losses + record.ties
            if (totalGames > 0) record.wins.toDouble() / totalGames else null
        } else null

        // Il display name è "name", lo username è "player"
        val displayName = standing.name.ifEmpty { standing.player }

        // Determina l'archetipo dal deck info o dalle carte principali
        val archetype = standing.deck?.name
            ?: inferArchetype(cards)

        val deckId = "${tournament.id}_${standing.player.ifEmpty { standing.name }}_${standing.placing}"

        return MetaDeck(
            id = deckId,
            archetype = archetype,
            player = displayName.ifEmpty { null },
            tournament = tournament.name.ifEmpty { null },
            tournamentId = tournament.id.ifEmpty { null },
            date = tournament.date.ifEmpty { null },
            placement = if (standing.placing > 0) standing.placing else null,
            winrate = winrate,
            link = if (tournament.id.isNotEmpty() && standing.player.isNotEmpty())
                "https://play.limitlesstcg.com/tournament/${tournament.id}/player/${standing.player}"
            else null,
            cards = cards
        )
    }

    private fun classifyCardByName(name: String): String {
        val nameLower = name.lowercase()
        return when {
            nameLower.contains("energy") || nameLower.contains("energia") -> "energy"
            nameLower.contains("professor") ||
                nameLower.contains("boss") ||
                nameLower.contains("judge") ||
                nameLower.contains("research") ||
                nameLower.contains("iono") ||
                nameLower.contains("nest ball") ||
                nameLower.contains("ultra ball") ||
                nameLower.contains("rare candy") ||
                nameLower.contains("switch") ||
                nameLower.contains("catcher") ||
                nameLower.contains("pal pad") ||
                nameLower.contains("battle vip pass") ||
                nameLower.contains("tool") ||
                nameLower.contains("stadium") ||
                nameLower.contains("supporter") ||
                nameLower.contains("item") -> "trainer"
            else -> "pokemon"
        }
    }

    private fun inferArchetype(cards: List<MetaDeckCard>): String {
        // Trova i Pokémon con più copie come indicatore dell'archetipo
        val pokemonCards = cards
            .filter { it.type == "pokemon" }
            .sortedByDescending { it.qty }

        return when {
            pokemonCards.size >= 2 -> {
                val top = pokemonCards.take(2).joinToString(" / ") {
                    it.name.split(" ").first()
                }
                top
            }
            pokemonCards.size == 1 -> pokemonCards.first().name
            else -> "Unknown"
        }
    }

    /**
     * Recupera gli ultimi [limit] tornei competitivi con i top 3 piazzati per
     * ciascuno, filtrati per [kind]. Usato nella sezione "Win Tournament".
     *
     * L'endpoint `/tournaments` non dice se un torneo si e' giocato di persona:
     * quel campo (`isOnline`) sta solo in `/tournaments/{id}/details`, uno per
     * torneo. Per questo i dettagli si risolvono a gruppi di
     * [DETAILS_CONCURRENCY] e ci si ferma appena si sono trovati abbastanza
     * tornei del tipo richiesto, invece di scaricare il dettaglio di tutti i
     * candidati: per "Tutti" bastano le prime due ondate, ed e' l'unico modo di
     * offrire il filtro senza moltiplicare per sei le richieste all'API.
     */
    suspend fun getTournamentResults(
        format: String = "standard",
        limit: Int = 10,
        kind: TournamentKind = TournamentKind.ALL
    ): Result<List<TournamentResult>> = withContext(Dispatchers.IO) {
        loadTournamentResults(format, limit, kind)
    }

    /** Il corpo vero, sempre su [Dispatchers.IO]. Vedi [loadMetaArchetypes]. */
    private suspend fun loadTournamentResults(
        format: String,
        limit: Int,
        kind: TournamentKind
    ): Result<List<TournamentResult>> {
        val cacheKey = "results_${format}_${limit}_${kind.name}"
        tournamentResultsCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_DURATION) {
                return Result.success(cached.results)
            }
        }

        readResultsFromDisk(cacheKey, CACHE_DURATION)?.let { fresh ->
            tournamentResultsCache[cacheKey] =
                CachedTournamentResults(fresh, LimitlessLocalCache.timestampOf(cacheKey) ?: 0L)
            return Result.success(fresh)
        }

        return try {
            val apiFormat = when (format.lowercase()) {
                "expanded" -> "expanded"
                else -> "standard"
            }

            val candidates = getTournamentCandidates(apiFormat)
                // Un "torneo" da quattro giocatori non e' un risultato
                // competitivo, e' una serata fra amici: finiva in cima alla
                // lista accanto ai Regional solo perche' era piu' recente.
                .filter { it.players >= MIN_TOURNAMENT_PLAYERS }

            Timber.d("TournamentResults: ${candidates.size} candidati per kind=$kind")
            if (candidates.isEmpty()) return Result.success(emptyList())

            // Il budget conta solo i dettagli che mancano: quelli gia' su disco
            // sono gratis, e sono la ragione per cui il filtro "Dal vivo"
            // diventa istantaneo dopo le prime aperture.
            var newDetailsBudget = MAX_NEW_DETAILS_PER_LOAD
            val matched = mutableListOf<Pair<LimitlessTournament, LimitlessTournamentDetails?>>()

            for (chunk in candidates.chunked(DETAILS_CONCURRENCY)) {
                val missing = chunk.filter { cachedDetails(it.id) == null }

                if (missing.size > newDetailsBudget) {
                    // Questo gruppo costa piu' di quanto resti da spendere.
                    // Si prende solo cio' che e' gia' noto e si tira dritto,
                    // invece di fermarsi: piu' avanti nella finestra possono
                    // esserci gruppi interamente in cache, che non costano
                    // niente e sarebbe assurdo saltare.
                    matched += chunk
                        .mapNotNull { tournament ->
                            cachedDetails(tournament.id)?.let { tournament to it }
                        }
                        .filter { (_, details) -> kind.accepts(details) }
                } else {
                    newDetailsBudget -= missing.size

                    val resolved = coroutineScope {
                        chunk.map { tournament ->
                            async(Dispatchers.IO) { tournament to tournamentDetails(tournament.id) }
                        }.awaitAll()
                    }
                    matched += resolved.filter { (_, details) -> kind.accepts(details) }
                }

                if (matched.size >= limit) break
            }

            if (newDetailsBudget <= 0) {
                Timber.d("Limitless: budget dettagli esaurito, ${matched.size} tornei trovati")
            }

            val selected = matched.take(limit)
            if (selected.isEmpty()) {
                val empty = emptyList<TournamentResult>()
                tournamentResultsCache[cacheKey] = CachedTournamentResults(empty, System.currentTimeMillis())
                return Result.success(empty)
            }

            val results = coroutineScope {
                selected.map { (tournament, details) ->
                    async(Dispatchers.IO) {
                        try {
                            val standings = standingsOf(tournament.id)

                            // Prendi i top piazzati con decklist, filtrando placement 1-3
                            val withDecklist = standings
                                .filter { it.decklist != null }
                                .sortedBy { it.placing }

                            // Prima tenta esattamente top 3 (placing 1, 2, 3)
                            val top3Exact = withDecklist.filter { it.placing in 1..3 }.take(3)

                            // Fallback: prendi i primi 3 con decklist (potrebbero partire da placing > 3)
                            val top3 = if (top3Exact.isNotEmpty()) top3Exact
                            else withDecklist.take(3)

                            val mappedDecks = top3
                                .map { mapToMetaDeck(it, tournament) }
                                .filter { it.cards.isNotEmpty() }

                            Timber.d("TournamentResults ${tournament.name}: ${mappedDecks.size} top placings")

                            TournamentResult(
                                tournamentId = tournament.id,
                                tournamentName = tournament.name.ifEmpty { tournament.id },
                                date = tournament.date.ifEmpty { null },
                                players = tournament.players,
                                top3 = mappedDecks,
                                isOnline = details?.isOnline,
                                organizerName = details?.organizer?.name?.ifBlank { null },
                                organizerLogo = details?.organizer?.logo?.ifBlank { null }
                            )
                        } catch (e: Exception) {
                            Timber.w("Errore standings torneo ${tournament.id}: ${e.message}")
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
                    // Esclude tornei senza risultati utili
                    .filter { it.top3.isNotEmpty() }
            }

            // Ordina per data discendente (più recente prima)
            val sorted = results.sortedByDescending { it.date }

            tournamentResultsCache[cacheKey] = CachedTournamentResults(sorted, System.currentTimeMillis())
            LimitlessLocalCache.write(cacheKey, sorted)
            Result.success(sorted)
        } catch (e: Exception) {
            Timber.e(e, "Errore fetch tournament results: ${e.message}")

            // Dati vecchi invece di una schermata di errore: se siamo in pausa
            // per il rate limit, la lista di mezz'ora fa e' comunque piu' utile
            // di un messaggio rosso, e il vero rimedio e' solo aspettare.
            tournamentResultsCache[cacheKey]?.let { return Result.success(it.results) }
            readResultsFromDisk(cacheKey, maxAgeMs = null)?.let { return Result.success(it) }
            Result.failure(e)
        }
    }

    /**
     * La finestra di tornei recenti su cui lavorare, condivisa fra i tre
     * filtri: cambiare da "Tutti" a "Dal vivo" non deve richiamare l'endpoint
     * della lista, che restituirebbe le stesse identiche voci.
     */
    private suspend fun getTournamentCandidates(apiFormat: String): List<LimitlessTournament> {
        val cacheKey = "candidates_$apiFormat"
        tournamentListCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_DURATION) {
                return cached.tournaments
            }
        }

        readCandidatesFromDisk(cacheKey, CACHE_DURATION)?.let { fresh ->
            tournamentListCache[cacheKey] =
                CachedTournamentList(fresh, LimitlessLocalCache.timestampOf(cacheKey) ?: 0L)
            return fresh
        }

        return try {
            val tournaments = api.getTournaments(
                game = "PTCG",
                format = apiFormat,
                limit = TOURNAMENT_CANDIDATE_WINDOW
            )
            tournamentListCache[cacheKey] = CachedTournamentList(tournaments, System.currentTimeMillis())
            LimitlessLocalCache.write(cacheKey, tournaments)
            tournaments
        } catch (e: LimitlessRateLimitException) {
            // La finestra dei candidati scaduta vale comunque piu' di niente:
            // sono gli stessi tornei, solo senza gli ultimissimi.
            readCandidatesFromDisk(cacheKey, maxAgeMs = null) ?: throw e
        }
    }

    private fun readCandidatesFromDisk(cacheKey: String, maxAgeMs: Long?): List<LimitlessTournament>? =
        LimitlessLocalCache.read<List<LimitlessTournament>>(
            key = cacheKey,
            type = object : TypeToken<List<LimitlessTournament>>() {}.type,
            maxAgeMs = maxAgeMs
        )?.takeIf { it.isNotEmpty() }

    /**
     * Il dettaglio di un torneo, memorizzato per sempre e anche su disco.
     *
     * Un torneo concluso non cambia piu': ne' la sede, ne' l'organizzatore, ne'
     * il fatto che si sia giocato online. Tenere questa cache fuori dalla
     * scadenza dei trenta minuti — e fuori dalla vita del processo — fa si' che
     * un refresh manuale, o un riavvio dell'app, non ripaghino il costo di
     * informazioni gia' note.
     */
    private suspend fun tournamentDetails(tournamentId: String): LimitlessTournamentDetails? {
        cachedDetails(tournamentId)?.let { return it }

        return try {
            val details = api.getTournamentDetails(tournamentId)
            detailsCache[tournamentId] = details
            LimitlessLocalCache.putTournamentDetails(tournamentId, details)
            details
        } catch (e: LimitlessRateLimitException) {
            // Non e' un errore del torneo: e' la finestra chiusa. Chi chiama
            // deve fermarsi, non provare il prossimo id.
            throw e
        } catch (e: Exception) {
            Timber.w("Errore dettagli torneo $tournamentId: ${e.message}")
            null
        }
    }

    /** Il dettaglio gia' noto, da memoria o da disco. Nessuna richiesta. */
    private fun cachedDetails(tournamentId: String): LimitlessTournamentDetails? {
        detailsCache[tournamentId]?.let { return it }
        return LimitlessLocalCache.tournamentDetails(tournamentId)?.also {
            detailsCache[tournamentId] = it
        }
    }

    /**
     * Gli standings di un torneo, una volta sola per sessione.
     *
     * Archetipi e Win Tournament guardano la stessa finestra di tornei: senza
     * questa cache, aprire la seconda tab richiedeva di nuovo gli stessi
     * standings gia' scaricati dalla prima.
     */
    private suspend fun standingsOf(tournamentId: String): List<LimitlessStanding> {
        standingsCache[tournamentId]?.let { return it }

        val standings = api.getTournamentStandings(tournamentId)
        standingsCache[tournamentId] = standings
        return standings
    }

    private fun readArchetypesFromDisk(cacheKey: String, maxAgeMs: Long?): List<MetaArchetype>? =
        LimitlessLocalCache.read<List<MetaArchetype>>(
            key = cacheKey,
            type = object : TypeToken<List<MetaArchetype>>() {}.type,
            maxAgeMs = maxAgeMs
        )?.takeIf { it.isNotEmpty() }

    private fun readResultsFromDisk(cacheKey: String, maxAgeMs: Long?): List<TournamentResult>? =
        LimitlessLocalCache.read<List<TournamentResult>>(
            key = cacheKey,
            type = object : TypeToken<List<TournamentResult>>() {}.type,
            maxAgeMs = maxAgeMs
        )?.takeIf { it.isNotEmpty() }

    fun clearCache() {
        archetypeCache.clear()
        tournamentResultsCache.clear()
        tournamentListCache.clear()
        LimitlessLocalCache.clearComputed()
        // detailsCache e standingsCache no: vedi [tournamentDetails] e
        // [standingsOf]. Sono dati di eventi conclusi, che non invecchiano, e
        // riscaricarli a ogni refresh manuale e' il modo piu' veloce di
        // arrivare a 429 senza averci guadagnato niente.
    }

    /**
     * Fra quanti secondi l'API tornera' disponibile, o 0 se lo e' gia'.
     *
     * Serve alla UI per dire "riprova fra due minuti" invece di mostrare un
     * errore di rete che suggerisce, sbagliando, di riprovare subito.
     */
    fun rateLimitRetryAfterSeconds(): Long = LimitlessRateLimiter.retryAfterSeconds()
}

/**
 * Vero se il dettaglio del torneo corrisponde al filtro.
 *
 * Un dettaglio mancante (chiamata fallita) passa solo sotto "Tutti": meglio un
 * torneo senza etichetta in una lista che non promette niente, che un torneo
 * dal vivo elencato fra quelli online per una richiesta andata male.
 */
private fun TournamentKind.accepts(details: LimitlessTournamentDetails?): Boolean = when (this) {
    TournamentKind.ALL -> true
    TournamentKind.LIVE -> details?.isOnline == false
    TournamentKind.ONLINE -> details?.isOnline == true
}
