package com.emabuia.pokevault.data.competitive

import com.emabuia.pokevault.data.model.MatchLog
import com.emabuia.pokevault.data.model.Tournament
import java.text.Normalizer

/**
 * Il bilancio contro un archetipo avversario.
 *
 * E' la ragione per cui si tiene un match log: sapere che contro Charizard si
 * e' 2-7 e contro Gardevoir 8-1 cambia cosa si porta al torneo successivo.
 * Finora il campo "mazzo avversario" veniva scritto a ogni partita e non
 * riletto mai.
 */
data class MatchupStat(
    /** La grafia piu' usata dall'utente per questo archetipo. */
    val opponentDeck: String,
    val wins: Int,
    val losses: Int,
    val ties: Int
) {
    val played: Int get() = wins + losses + ties

    /** Partite che hanno prodotto un vincitore: i pari non dicono chi e' favorito. */
    val decided: Int get() = wins + losses

    /**
     * Percentuale di vittorie sulle partite decise, o null se sono tutte pari.
     *
     * Si ignorano i pareggi invece di contarli come mezza vittoria: in un
     * matchup 1-1-8 la lettura utile e' "50% sulle due partite finite", non
     * "15% su dieci".
     */
    val winRate: Int? get() = if (decided == 0) null else (wins * 100) / decided
}

/** Il bilancio di un mazzo dell'utente, sommando i tornei in cui lo ha portato. */
data class DeckStat(
    val deckName: String,
    val wins: Int,
    val losses: Int,
    val ties: Int,
    val tournaments: Int
) {
    val played: Int get() = wins + losses + ties
    val decided: Int get() = wins + losses
    val winRate: Int? get() = if (decided == 0) null else (wins * 100) / decided
}

/**
 * Tutto quello che si puo' dire sulle partite registrate.
 *
 * [bestMatchup] e [worstMatchup] sono null finche' non c'e' abbastanza
 * campione: vedi [MatchupAnalyzer.MIN_GAMES_FOR_VERDICT].
 */
data class CompetitiveSummary(
    val wins: Int,
    val losses: Int,
    val ties: Int,
    val matchups: List<MatchupStat>,
    val deckStats: List<DeckStat>,
    /** Gli ultimi risultati, dal piu' recente. Solo "W", "L" o "T". */
    val recentForm: List<String>,
    val bestMatchup: MatchupStat?,
    val worstMatchup: MatchupStat?,
    /** Partite salvate senza indicare il mazzo avversario. */
    val matchesWithoutOpponentDeck: Int
) {
    val played: Int get() = wins + losses + ties
    val decided: Int get() = wins + losses
    val winRate: Int? get() = if (decided == 0) null else (wins * 100) / decided
    val isEmpty: Boolean get() = played == 0
}

object MatchupAnalyzer {

    /**
     * Partite sotto le quali un matchup non riceve un giudizio.
     *
     * Con due partite giocate un 2-0 direbbe "100%", che non e'
     * un'informazione: e' rumore con l'aria di un dato. Il matchup resta in
     * tabella — il conteggio e' comunque vero — ma non viene eletto ne' a
     * migliore ne' a peggiore.
     */
    const val MIN_GAMES_FOR_VERDICT = 4

    /** Quanti risultati recenti tenere per la striscia di forma. */
    const val RECENT_FORM_SIZE = 10

    /**
     * Aggrega le partite.
     *
     * [matchesNewestFirst] deve arrivare gia' ordinata dalla piu' recente:
     * l'ordine e' quello che serve alla striscia di forma, e il repository lo
     * garantisce gia'. L'ordinamento interno usa `createdAt` quando c'e' ed e'
     * stabile quando manca, quindi l'ordine ricevuto viene comunque rispettato.
     */
    fun analyze(
        matchesNewestFirst: List<MatchLog>,
        tournaments: List<Tournament>
    ): CompetitiveSummary {
        val ordered = matchesNewestFirst.sortedByDescending { it.createdAt?.seconds ?: Long.MIN_VALUE }
        val valid = ordered.filter { it.result in VALID_RESULTS }

        val matchups = buildMatchups(valid)
        val eligible = matchups.filter { it.played >= MIN_GAMES_FOR_VERDICT && it.winRate != null }

        return CompetitiveSummary(
            wins = valid.count { it.result == "W" },
            losses = valid.count { it.result == "L" },
            ties = valid.count { it.result == "T" },
            matchups = matchups,
            deckStats = buildDeckStats(valid, tournaments),
            recentForm = valid.take(RECENT_FORM_SIZE).map { it.result },
            // A parita' di percentuale vince chi ha giocato di piu': e' il
            // campione piu' solido, non quello che ha avuto piu' fortuna.
            bestMatchup = eligible.maxWithOrNull(
                compareBy<MatchupStat> { it.winRate ?: 0 }.thenBy { it.played }
            ),
            worstMatchup = eligible.minWithOrNull(
                compareBy<MatchupStat> { it.winRate ?: 0 }.thenByDescending { it.played }
            ),
            matchesWithoutOpponentDeck = valid.count { normalizeDeckName(it.opponentDeck).isBlank() }
        )
    }

    /**
     * I nomi di mazzo avversario gia' usati, dal piu' frequente.
     *
     * Servono a proporli quando si registra una partita nuova: il campo e'
     * libero, e "Charizard ex", "charizard" e "Zard" scritti in tre momenti
     * diversi diventano tre matchup separati che non dicono niente.
     */
    fun knownOpponentDecks(matches: List<MatchLog>): List<String> =
        buildMatchups(matches.filter { it.result in VALID_RESULTS }).map { it.opponentDeck }

    private fun buildMatchups(matches: List<MatchLog>): List<MatchupStat> {
        return matches
            .filter { normalizeDeckName(it.opponentDeck).isNotBlank() }
            .groupBy { normalizeDeckName(it.opponentDeck) }
            .map { (_, group) ->
                MatchupStat(
                    opponentDeck = mostCommonSpelling(group.map { it.opponentDeck }),
                    wins = group.count { it.result == "W" },
                    losses = group.count { it.result == "L" },
                    ties = group.count { it.result == "T" }
                )
            }
            .sortedWith(
                compareByDescending<MatchupStat> { it.played }
                    .thenBy { it.opponentDeck.lowercase() }
            )
    }

    /**
     * Il bilancio per mazzo dell'utente.
     *
     * Il mazzo non sta sulla partita ma sul torneo: si uniscono per
     * `tournamentId`. Le partite di un torneo senza mazzo indicato restano
     * fuori, perche' attribuirle a "—" mescolerebbe tornei diversi in una riga
     * sola che non significa niente.
     */
    private fun buildDeckStats(
        matches: List<MatchLog>,
        tournaments: List<Tournament>
    ): List<DeckStat> {
        val deckByTournament = tournaments.associate { it.id to it.deckName.trim() }

        return matches
            .mapNotNull { match ->
                val deck = deckByTournament[match.tournamentId]?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                deck to match
            }
            .groupBy({ it.first.lowercase() }, { it.second })
            .map { (_, group) ->
                val label = group.firstNotNullOfOrNull { match ->
                    deckByTournament[match.tournamentId]?.takeIf { it.isNotBlank() }
                }.orEmpty()

                DeckStat(
                    deckName = label,
                    wins = group.count { it.result == "W" },
                    losses = group.count { it.result == "L" },
                    ties = group.count { it.result == "T" },
                    tournaments = group.map { it.tournamentId }.distinct().size
                )
            }
            .sortedWith(
                compareByDescending<DeckStat> { it.played }
                    .thenBy { it.deckName.lowercase() }
            )
    }

    /**
     * Fra piu' grafie dello stesso archetipo vince la piu' usata.
     *
     * A parita' si prende la piu' lunga: fra "Zard" e "Charizard ex" la seconda
     * si riconosce anche a distanza di mesi.
     */
    private fun mostCommonSpelling(spellings: List<String>): String {
        return spellings
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { it.key.length }
            )
            .firstOrNull()?.key
            .orEmpty()
    }

    /**
     * La chiave con cui due nomi di mazzo sono lo stesso mazzo.
     *
     * Volutamente prudente: minuscole, niente accenti, niente punteggiatura,
     * spazi normalizzati. Non prova a capire che "Zard" e "Charizard" sono la
     * stessa cosa — accorpare due archetipi diversi falserebbe il bilancio in
     * un modo che l'utente non potrebbe nemmeno accorgersi.
     */
    fun normalizeDeckName(raw: String): String {
        val decomposed = Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private val VALID_RESULTS = setOf("W", "L", "T")
}
