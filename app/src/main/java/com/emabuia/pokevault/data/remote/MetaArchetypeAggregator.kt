package com.emabuia.pokevault.data.remote

import com.emabuia.pokevault.data.model.MetaArchetype
import com.emabuia.pokevault.data.model.MetaDeck

/** Un deck dei top 32 di un torneo, con quello che serve per aggregarlo. */
internal data class ArchetypeEntry(
    val archetype: String,
    val placement: Int,
    val wins: Int,
    val losses: Int,
    val ties: Int,
    /** Giocatori del torneo da cui viene. */
    val players: Int,
    /** Data del torneo, ISO: serve a dividere la finestra in vecchi e recenti. */
    val tournamentDate: String,
    val tournamentId: String,
    val metaDeck: MetaDeck
)

/**
 * Dai deck dei tornei agli archetipi della classifica.
 *
 * Fuori dal repository perche' e' qui che stavano i due errori della 3.1.8, e
 * vanno tenuti fermi da test che non passino dalla rete:
 * - il "trend" confrontava i piazzamenti ordinati dal migliore al peggiore,
 *   quindi diceva "in calo" quasi sempre: ora confronta la quota nei tornei
 *   piu' recenti con quella nei piu' vecchi;
 * - il win rate era la media dei win rate dei singoli deck, per cui un
 *   archetipo con un solo deck vincente mostrava 100%: ora e' vittorie totali
 *   su partite totali, e la schermata dice su quante partite.
 */
internal object MetaArchetypeAggregator {

    /** Un piazzamento da top 8: il taglio delle fasi finali nei tornei grandi. */
    private const val TOP_CUT = 8

    fun aggregate(entries: List<ArchetypeEntry>): List<MetaArchetype> {
        if (entries.isEmpty()) return emptyList()

        val totalDecks = entries.size
        val tournaments = entries.map { it.tournamentId }.distinct()

        // Meta' recente e meta' vecchia della finestra, per data. Con un solo
        // torneo non c'e' un prima e un dopo: niente trend.
        val byDate = entries.distinctBy { it.tournamentId }
            .sortedByDescending { it.tournamentDate }
            .map { it.tournamentId }
        val recentIds = byDate.take(byDate.size / 2).toSet()
        val olderIds = byDate.drop(byDate.size / 2).toSet()
        val recentTotal = entries.count { it.tournamentId in recentIds }
        val olderTotal = entries.count { it.tournamentId in olderIds }

        return entries
            .groupBy { it.archetype.lowercase().trim() }
            .map { (_, group) ->
                val best = LimitlessPlacing.ranked(group) { it.placement }.first()
                val trend = if (recentTotal > 0 && olderTotal > 0) {
                    val recentShare = group.count { it.tournamentId in recentIds } * 100.0 / recentTotal
                    val olderShare = group.count { it.tournamentId in olderIds } * 100.0 / olderTotal
                    recentShare - olderShare
                } else null
                val wins = group.sumOf { it.wins }
                val losses = group.sumOf { it.losses }
                val ties = group.sumOf { it.ties }
                val games = wins + losses + ties

                MetaArchetype(
                    name = group.first().archetype,
                    count = group.size,
                    metaShare = group.size * 100.0 / totalDecks,
                    // I pareggi contano come partite non vinte, come prima.
                    avgWinrate = if (games > 0) wins.toDouble() / games else 0.0,
                    topPlacement = LimitlessPlacing.best(group.map { it.placement }),
                    recentResults = LimitlessPlacing.ranked(group) { it.placement }
                        .map { it.placement }
                        .filter(LimitlessPlacing::isKnown)
                        .take(5),
                    sampleDeck = best.metaDeck,
                    wins = wins,
                    losses = losses,
                    ties = ties,
                    topPlacementPlayers = if (LimitlessPlacing.isKnown(best.placement)) best.players else 0,
                    top8Count = group.count { it.placement in 1..TOP_CUT },
                    trend = trend,
                    sampleTournaments = tournaments.size,
                    sampleDecks = totalDecks
                )
            }
            .sortedByDescending { it.metaShare }
    }
}
