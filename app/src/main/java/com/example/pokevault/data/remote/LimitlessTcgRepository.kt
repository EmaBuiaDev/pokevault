package com.example.pokevault.data.remote

import android.util.Log
import com.example.pokevault.data.model.MetaDeck
import com.example.pokevault.data.model.MetaDeckCard
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
            val tournaments = api.getTournaments(
                game = "PTCG",
                format = apiFormat,
                limit = 10
            )

            if (tournaments.isEmpty()) {
                return Result.success(emptyList())
            }

            val allMetaDecks = mutableListOf<MetaDeck>()

            // 2. Per ogni torneo, recupera i top player con decklist
            for (tournament in tournaments) {
                if (allMetaDecks.size >= limit) break

                try {
                    val players = api.getTournamentPlayers(tournament.id)

                    // Prendi solo i top player con decklist
                    val topPlayers = players
                        .filter { it.decklist != null && it.decklist.isNotEmpty() }
                        .sortedBy { it.placing }
                        .take(8) // Top 8 per torneo

                    for (player in topPlayers) {
                        if (allMetaDecks.size >= limit) break

                        val metaDeck = mapToMetaDeck(
                            player = player,
                            tournament = tournament
                        )
                        allMetaDecks.add(metaDeck)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Errore caricamento players per torneo ${tournament.id}", e)
                    // Continua con il prossimo torneo
                }
            }

            // Ordina per placement e data
            val sorted = allMetaDecks
                .sortedWith(compareBy<MetaDeck> { it.placement ?: Int.MAX_VALUE }
                    .thenByDescending { it.date })
                .take(limit)

            // Salva in cache
            metaDecksCache[cacheKey] = CachedResult(sorted, System.currentTimeMillis())

            Result.success(sorted)
        } catch (e: Exception) {
            Log.e(TAG, "Errore fetch meta decks", e)

            // Fallback su cache scaduta
            metaDecksCache[cacheKey]?.let {
                return Result.success(it.decks)
            }

            Result.failure(e)
        }
    }

    private fun mapToMetaDeck(
        player: LimitlessPlayer,
        tournament: LimitlessTournament
    ): MetaDeck {
        val cards = player.decklist?.map { card ->
            MetaDeckCard(
                name = card.name,
                set = card.set.ifEmpty { null },
                number = card.number.ifEmpty { null },
                qty = card.count,
                type = classifyCardType(card)
            )
        } ?: emptyList()

        // Calcola winrate dal record
        val record = player.record
        val winrate = if (record != null) {
            val totalGames = record.wins + record.losses + record.ties
            if (totalGames > 0) record.wins.toDouble() / totalGames else null
        } else null

        // Determina l'archetipo dal deck info o dalle carte principali
        val archetype = player.deck?.name
            ?: inferArchetype(cards)

        val deckId = "${tournament.id}_${player.name}_${player.placing}"

        return MetaDeck(
            id = deckId,
            archetype = archetype,
            player = player.name.ifEmpty { null },
            tournament = tournament.name.ifEmpty { null },
            tournamentId = tournament.id.ifEmpty { null },
            date = tournament.date.ifEmpty { null },
            placement = if (player.placing > 0) player.placing else null,
            winrate = winrate,
            link = if (tournament.id.isNotEmpty())
                "https://play.limitlesstcg.com/tournament/${tournament.id}/player/${player.name}"
            else null,
            cards = cards
        )
    }

    private fun classifyCardType(card: LimitlessDeckCard): String {
        // Usa il tipo se fornito dall'API
        card.type?.let {
            return when (it.lowercase()) {
                "pokémon", "pokemon" -> "pokemon"
                "trainer" -> "trainer"
                "energy", "energia" -> "energy"
                else -> it.lowercase()
            }
        }

        // Fallback: inferisci dal nome della carta
        val nameLower = card.name.lowercase()
        return when {
            nameLower.contains("energy") || nameLower.contains("energia") -> "energy"
            nameLower.contains("professor") ||
                nameLower.contains("boss") ||
                nameLower.contains("judge") ||
                nameLower.contains("research") ||
                nameLower.contains("nest ball") ||
                nameLower.contains("ultra ball") ||
                nameLower.contains("rare candy") ||
                nameLower.contains("switch") ||
                nameLower.contains("catcher") ||
                nameLower.contains("pal pad") ||
                nameLower.contains("tool") ||
                nameLower.contains("stadium") -> "trainer"
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
