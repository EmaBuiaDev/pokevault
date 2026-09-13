package com.emabuia.pokevault.ui.competitive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.competitive.CompetitiveSummary
import com.emabuia.pokevault.data.competitive.DeckStat
import com.emabuia.pokevault.data.competitive.MatchupAnalyzer
import com.emabuia.pokevault.data.competitive.MatchupStat
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.AppMotion
import com.emabuia.pokevault.util.AppLocale

/**
 * Le statistiche del match log.
 *
 * Esiste perche' l'app registrava il mazzo avversario a ogni partita e non lo
 * rileggeva mai: si accumulavano decine di righe che nessuna schermata
 * trasformava in qualcosa di utile. Eppure e' proprio da li' che nasce il
 * valore di un match log — sapere che contro Charizard si e' 2-7 e contro
 * Gardevoir 8-1 e' quello che cambia il mazzo che si porta la domenica dopo.
 */
internal fun LazyListScope.matchStatsSection(summary: CompetitiveSummary) {
    if (summary.isEmpty) {
        item(key = "stats-empty") {
            StatsPlaceholder(
                title = AppLocale.matchStatsEmptyTitle,
                body = AppLocale.matchStatsEmptyBody
            )
        }
        return
    }

    item(key = "stats-header") { RecordCard(summary = summary) }

    if (summary.recentForm.isNotEmpty()) {
        item(key = "stats-form") { RecentFormCard(results = summary.recentForm) }
    }

    val best = summary.bestMatchup
    val worst = summary.worstMatchup
    if (best != null || worst != null) {
        item(key = "stats-highlights") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                best?.let {
                    HighlightCard(
                        label = AppLocale.matchStatsBestMatchup,
                        matchup = it,
                        accent = AppColors.green,
                        icon = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                worst?.let {
                    HighlightCard(
                        label = AppLocale.matchStatsWorstMatchup,
                        matchup = it,
                        accent = AppColors.red,
                        icon = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    item(key = "stats-matchups") { MatchupsCard(summary = summary) }

    if (summary.deckStats.isNotEmpty()) {
        item(key = "stats-decks") { DecksCard(decks = summary.deckStats) }
    }

    item(key = "stats-bottom") { Spacer(modifier = Modifier.height(80.dp)) }
}

@Composable
private fun StatsPlaceholder(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Insights,
                contentDescription = null,
                tint = AppColors.textMuted,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Il bilancio complessivo.
 *
 * L'anello e' lo stesso dell'Hand Simulator: nella stessa sezione dell'app,
 * "quanto sei messo bene" deve avere sempre la stessa forma.
 */
@Composable
private fun RecordCard(summary: CompetitiveSummary) {
    val rate = summary.winRate

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ConsistencyRing(
                score = rate ?: 0,
                label = AppLocale.matchStatsWinRateLabel,
                ringColor = winRateColor(rate)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = AppLocale.matchRecord(summary.wins, summary.losses, summary.ties),
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = AppLocale.matchStatsPlayed(summary.played),
                color = AppColors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Gli ultimi risultati come striscia di quadrati.
 *
 * E' il modo in cui si legge la forma di una squadra: dieci colori in fila
 * dicono in un colpo d'occhio se il periodo e' buono, cosa che una percentuale
 * complessiva — che media tutto l'anno — non puo' dire.
 */
@Composable
private fun RecentFormCard(results: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = AppLocale.matchStatsFormTitle,
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = AppLocale.matchStatsFormHint,
                    color = AppColors.textMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            ResultStrip(results = results)
        }
    }
}

/**
 * Una fila di risultati come quadrati colorati.
 *
 * Sta qui e non dentro la sua card perche' la usa anche il dettaglio del
 * torneo: leggere "come sta andando" deve avere la stessa forma che si tratti
 * di una giornata o di tutta la stagione.
 */
@Composable
internal fun ResultStrip(
    results: List<String>,
    modifier: Modifier = Modifier,
    square: androidx.compose.ui.unit.Dp = 30.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        results.forEach { result ->
            val color = resultColor(result)
            Box(
                modifier = Modifier
                    .size(square)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = result,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun HighlightCard(
    label: String,
    matchup: MatchupStat,
    accent: Color,
    icon: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (icon) {
                        Icons.AutoMirrored.Filled.TrendingUp
                    } else {
                        Icons.AutoMirrored.Filled.TrendingDown
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = label,
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = matchup.opponentDeck,
                color = AppColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${matchup.winRate}% · ${AppLocale.matchRecord(matchup.wins, matchup.losses, matchup.ties)}",
                color = AppColors.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun MatchupsCard(summary: CompetitiveSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = AppLocale.matchStatsMatchupsTitle,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = AppLocale.matchStatsMatchupsHint,
                color = AppColors.textMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (summary.matchups.isEmpty()) {
                Text(
                    text = AppLocale.matchStatsNoMatchupsTitle,
                    color = AppColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = AppLocale.matchStatsNoMatchupsBody,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                return@Column
            }

            summary.matchups.forEachIndexed { index, matchup ->
                if (index > 0) Spacer(modifier = Modifier.height(14.dp))
                MatchupRow(matchup = matchup, index = index)
            }

            if (summary.matchesWithoutOpponentDeck > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = AppLocale.matchStatsMissingOpponentDeck(summary.matchesWithoutOpponentDeck),
                    color = AppColors.textMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Una riga di matchup: nome, record, e la barra che li mostra in proporzione.
 *
 * La barra e' divisa in vittorie, pari e sconfitte invece di riempirsi fino
 * alla percentuale: un 6-4 e un 3-2 hanno lo stesso 60%, ma il primo e' un dato
 * e il secondo e' quasi un caso. Con la barra a segmenti la differenza si vede,
 * perche' e' lunga il doppio.
 */
@Composable
private fun MatchupRow(matchup: MatchupStat, index: Int) {
    val rate = matchup.winRate

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = matchup.opponentDeck,
                color = AppColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = AppLocale.matchRecord(matchup.wins, matchup.losses, matchup.ties),
                color = AppColors.textSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = rate?.let { "$it%" } ?: AppLocale.matchStatsNoWinRate,
                color = if (rate == null) AppColors.textMuted else winRateColor(rate),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        SegmentedRecordBar(
            wins = matchup.wins,
            ties = matchup.ties,
            losses = matchup.losses,
            index = index
        )

        if (matchup.played < MatchupAnalyzer.MIN_GAMES_FOR_VERDICT) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = AppLocale.matchStatsFewGames(MatchupAnalyzer.MIN_GAMES_FOR_VERDICT),
                color = AppColors.textMuted,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Vittorie, pari e sconfitte in proporzione, su una barra sola.
 *
 * La larghezza totale e' proporzionale alle partite giocate rispetto al
 * matchup piu' frequente, per cui un matchup da due partite si vede subito che
 * pesa poco senza bisogno di leggerne il conteggio.
 */
@Composable
private fun SegmentedRecordBar(
    wins: Int,
    ties: Int,
    losses: Int,
    index: Int
) {
    val total = (wins + ties + losses).coerceAtLeast(1)
    val motion = AppMotion.current

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = motion.bar,
            delayMillis = motion.barCascade(index),
            easing = AppMotion.easing
        ),
        label = "recordBar"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.textMuted.copy(alpha = 0.15f))
    ) {
        // I segmenti a zero non ricevono peso: un weight(0f) occuperebbe
        // comunque lo spazio minimo e lascerebbe una scheggia di colore per
        // un risultato che non e' mai successo.
        if (wins > 0) {
            Box(
                modifier = Modifier
                    .weight(wins.toFloat() / total * progress)
                    .fillMaxHeight()
                    .background(AppColors.green)
            )
        }
        if (ties > 0) {
            Box(
                modifier = Modifier
                    .weight(ties.toFloat() / total * progress)
                    .fillMaxHeight()
                    .background(AppColors.yellow)
            )
        }
        if (losses > 0) {
            Box(
                modifier = Modifier
                    .weight(losses.toFloat() / total * progress)
                    .fillMaxHeight()
                    .background(AppColors.red)
            )
        }
        // Lo spazio che resta finche' l'animazione non e' finita.
        if (progress < 1f) {
            Box(modifier = Modifier.weight(1f - progress))
        }
    }
}

@Composable
private fun DecksCard(decks: List<DeckStat>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = AppLocale.matchStatsDecksTitle,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = AppLocale.matchStatsDecksHint,
                color = AppColors.textMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            decks.forEachIndexed { index, deck ->
                if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                DeckStatRow(deck = deck, index = index)
            }
        }
    }
}

@Composable
private fun DeckStatRow(deck: DeckStat, index: Int) {
    val rate = deck.winRate

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                tint = AppColors.lavender,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deck.deckName,
                    color = AppColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = AppLocale.matchStatsDeckTournaments(deck.tournaments),
                    color = AppColors.textMuted,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = AppLocale.matchRecord(deck.wins, deck.losses, deck.ties),
                color = AppColors.textSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = rate?.let { "$it%" } ?: AppLocale.matchStatsNoWinRate,
                color = if (rate == null) AppColors.textMuted else winRateColor(rate),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        SegmentedRecordBar(
            wins = deck.wins,
            ties = deck.ties,
            losses = deck.losses,
            index = index
        )
    }
}

/** Il colore di un risultato singolo, condiviso da striscia e barre. */
@Composable
private fun resultColor(result: String): Color = when (result) {
    "W" -> AppColors.green
    "L" -> AppColors.red
    "T" -> AppColors.yellow
    else -> AppColors.textMuted
}

/**
 * Il colore di un tasso di vittorie.
 *
 * La soglia e' il 50%: sopra si sta vincendo, sotto no. Il grigio sotto i 40
 * non serve — un matchup brutto deve leggersi come brutto.
 */
@Composable
internal fun winRateColor(rate: Int?): Color = when {
    rate == null -> AppColors.textMuted
    rate >= 60 -> AppColors.green
    rate >= 45 -> AppColors.yellow
    else -> AppColors.red
}
