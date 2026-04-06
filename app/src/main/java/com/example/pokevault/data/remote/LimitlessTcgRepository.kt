package com.example.pokevault.data.remote

import android.util.Log
import com.example.pokevault.data.model.MetaDeck
import com.example.pokevault.data.model.MetaDeckCard
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object LimitlessRetrofitClient {
    private const val BASE_URL = "https://play.limitlesstcg.com/api/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
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

    // Cache in memoria con chiave format
    private val metaDecksCache = mutableMapOf<String, CachedResult>()

    private data class CachedResult(
        val decks: List<MetaDeck>,
        val timestamp: Long
    )

    companion object {
        private const val TAG = "LimitlessTcgRepo"
        private const val CACHE_DURATION = 30 * 60 * 1000L // 30 minuti
    }

    suspend fun getMetaDecks(
        format: String = "standard",
        limit: Int = 50
    ): Result<List<MetaDeck>> {
        // Controlla cache
        val cacheKey = "${format}_$limit"
        metaDecksCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_DURATION) {
                return Result.success(cached.decks)
            }
        }

        return try {
            val apiFormat = when (format.lowercase()) {
                "standard" -> "standard"
                "expanded" -> "expanded"
                else -> "standard"
            }

            // 1. Recupera tornei recenti
            Log.d(TAG, "Fetching tournaments format=$apiFormat")
            val tournaments = api.getTournaments(
                game = "PTCG",
                format = apiFormat,
                limit = 10
            )
            Log.d(TAG, "Trovati ${tournaments.size} tornei")

            if (tournaments.isEmpty()) {
                return Result.success(emptyList())
            }

            val allMetaDecks = mutableListOf<MetaDeck>()

            // 2. Per ogni torneo, recupera i top player con decklist
            for (tournament in tournaments) {
                if (allMetaDecks.size >= limit) break

                try {
                    Log.d(TAG, "Fetching standings per torneo: ${tournament.name} (${tournament.id})")
                    val standings = api.getTournamentStandings(tournament.id)
                    Log.d(TAG, "Trovati ${standings.size} giocatori")

                    // Prendi solo i top player con decklist
                    val topPlayers = standings
                        .filter { it.decklist != null }
                        .sortedBy { it.placing }
                        .take(8) // Top 8 per torneo

                    Log.d(TAG, "Giocatori con decklist: ${topPlayers.size}")

                    for (player in topPlayers) {
                        if (allMetaDecks.size >= limit) break

                        val metaDeck = mapToMetaDeck(
                            standing = player,
                            tournament = tournament
                        )
                        if (metaDeck.cards.isNotEmpty()) {
                            allMetaDecks.add(metaDeck)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Errore caricamento standings per torneo ${tournament.id}: ${e.message}", e)
                    // Continua con il prossimo torneo
                }
            }

            Log.d(TAG, "Totale meta decks trovati: ${allMetaDecks.size}")

            // Ordina per placement e data
            val sorted = allMetaDecks
                .sortedWith(compareBy<MetaDeck> { it.placement ?: Int.MAX_VALUE }
                    .thenByDescending { it.date })
                .take(limit)

            // Salva in cache
            metaDecksCache[cacheKey] = CachedResult(sorted, System.currentTimeMillis())

            Result.success(sorted)
        } catch (e: Exception) {
            Log.e(TAG, "Errore fetch meta decks: ${e.message}", e)

            // Fallback su cache scaduta
            metaDecksCache[cacheKey]?.let {
                return Result.success(it.decks)
            }

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
            Log.d(TAG, "Decklist raw JSON (troncato): ${json.take(500)}")

            // Prova Formato 1: Mappa con chiavi "pokemon", "trainer", "energy"
            try {
                val mapType = object : TypeToken<Map<String, Any>>() {}.type
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
                    Log.d(TAG, "Parsed ${cards.size} carte (formato mappa per categoria)")
                    return cards
                }

                // Potrebbe essere formato {"deck": [...]}
                val deckData = map["deck"]
                if (deckData != null) {
                    val deckJson = gson.toJson(deckData)
                    val deckCards = parseCardList(deckJson, null)
                    if (deckCards.isNotEmpty()) {
                        Log.d(TAG, "Parsed ${deckCards.size} carte (formato deck array)")
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
                    Log.d(TAG, "Parsed ${listCards.size} carte (formato lista piatta)")
                    return listCards
                }
            } catch (_: Exception) {
                // Non è una lista
            }

        } catch (e: Exception) {
            Log.w(TAG, "Errore parsing decklist: ${e.message}")
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
            val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
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
                    finalSet = set
                    finalNumber = number
                } else if (cardId.isNotEmpty()) {
                    // Formato ID tipo "OBF_125" → set="OBF", number="125"
                    val parts = cardId.split("_", limit = 2)
                    finalSet = parts.getOrElse(0) { "" }
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
            Log.w(TAG, "Errore parseCardList: ${e.message}")
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

    fun clearCache() {
        metaDecksCache.clear()
    }
}
