@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.emabuia.pokevault.ui.competitive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.simulator.HandSimulationSummary
import com.emabuia.pokevault.data.simulator.ProblemHandSample
import com.emabuia.pokevault.data.simulator.SimulatorCard
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale

/**
 * Soglie di riferimento delle metriche.
 *
 * Sono le stesse che gia' facevano scattare gli insight rule-based: prima
 * esistevano solo dentro `buildInsights` e l'utente non le vedeva mai, per cui
 * una percentuale restava un numero senza metro. Qui diventano la tacca sulla
 * barra.
 */
private const val TARGET_STARTER = 88
private const val TARGET_ENERGY_T1 = 72
private const val TARGET_OUT_T1 = 65
private const val TARGET_SETUP_T2 = 60
private const val TARGET_KEY_T2 = 70

/**
 * Almeno un Aiuto in mano entro il 2° turno. E' una soglia nostra, come le
 * altre: segna il punto sotto cui una mano su quattro resta senza Aiuto nel
 * primo turno in cui tutti possono giocarlo.
 */
private const val TARGET_SUPPORTER_T2 = 75

/**
 * Modalita' Analisi: migliaia di mani, riassunte in un voto.
 *
 * La struttura e' deliberatamente a imbuto — prima il voto, poi una sezione
 * per ogni domanda (mulligan, Pokemon Base, Energia, Aiuti, come si parte),
 * infine le mani peggiori — perche' nove percentuali affiancate allo stesso
 * peso non dicevano da dove cominciare a guardare.
 */
internal fun LazyListScope.analysisSection(
    hasDeck: Boolean,
    /** Carte del mazzo simulato: le sezioni dicono su quante sono calcolate. */
    deckSize: Int,
    summary: HandSimulationSummary?,
    accuracyWarnings: List<String>,
    isSimulating: Boolean,
    runCount: Int,
    freeLimitNote: String?,
    onRun: () -> Unit,
    onSaveProblemHand: (ProblemHandSample) -> Unit,
    onCardClick: (SimulatorCard) -> Unit
) {
    if (!hasDeck) {
        item(key = "analysis-no-deck") {
            AnalysisPlaceholder(
                title = AppLocale.handSimulatorNoDecks,
                body = AppLocale.handSimulatorNoDecksSubtitle
            )
        }
        return
    }

    if (summary == null) {
        item(key = "analysis-intro") {
            AnalysisIntroCard(
                isSimulating = isSimulating,
                runCount = runCount,
                freeLimitNote = freeLimitNote,
                onRun = onRun
            )
        }
        return
    }

    item(key = "analysis-score") {
        ScoreCard(
            summary = summary,
            isSimulating = isSimulating,
            freeLimitNote = freeLimitNote,
            onRun = onRun
        )
    }

    if (accuracyWarnings.isNotEmpty()) {
        item(key = "analysis-accuracy") {
            AccuracyCard(warnings = accuracyWarnings)
        }
    }

    // Una sezione per domanda, nell'ordine in cui si pongono al tavolo:
    // la partita comincia? con quanti Pokemon? posso attaccare? posso
    // pescare? Fino alla 3.1.8 c'erano sette barre e un elenco "Tutte le
    // metriche" con le stesse sigle: niente sulle Energie oltre al primo
    // turno, niente sulle carte Aiuto.
    item(key = "analysis-mulligan") {
        MulliganCard(summary = summary, deckSize = deckSize)
    }

    item(key = "analysis-basics") {
        BasicsCard(summary = summary)
    }

    item(key = "analysis-energy") {
        EnergyCard(summary = summary, deckSize = deckSize)
    }

    item(key = "analysis-supporters") {
        SupportersCard(summary = summary, deckSize = deckSize)
    }

    item(key = "analysis-start") {
        StartCard(summary = summary)
    }

    if (summary.problemHands.isNotEmpty()) {
        item(key = "analysis-problems") {
            ProblemHandsCard(
                hands = summary.problemHands,
                onSave = onSaveProblemHand,
                onCardClick = onCardClick
            )
        }
    }
}

@Composable
private fun AnalysisPlaceholder(title: String, body: String) {
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
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AnalysisIntroCard(
    isSimulating: Boolean,
    runCount: Int,
    freeLimitNote: String?,
    onRun: () -> Unit
) {
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
                imageVector = Icons.Default.Science,
                contentDescription = null,
                tint = AppColors.blue,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = AppLocale.handSimulatorNotRunTitle,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = AppLocale.handSimulatorNotRunBody,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            RunButton(
                isSimulating = isSimulating,
                label = AppLocale.handSimulatorRunButton,
                onRun = onRun
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = AppLocale.handSimulatorRunsRecap(runCount),
                color = AppColors.textMuted,
                fontSize = 11.sp
            )
            freeLimitNote?.let { note ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note,
                    color = AppColors.textSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RunButton(
    isSimulating: Boolean,
    label: String,
    onRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onRun,
        enabled = !isSimulating,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
        modifier = modifier.fillMaxWidth()
    ) {
        if (isSimulating) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = AppColors.onAccent
            )
        } else {
            Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            text = if (isSimulating) AppLocale.handSimulatorRunning else label,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun ScoreCard(
    summary: HandSimulationSummary,
    isSimulating: Boolean,
    freeLimitNote: String?,
    onRun: () -> Unit
) {
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
            ConsistencyRing(score = summary.consistencyScore)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = AppLocale.handSimulatorScoreVerdict(summary.consistencyScore),
                color = scoreColor(summary.consistencyScore),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = AppLocale.handSimulatorOrderScores(summary.goingFirst.score, summary.goingSecond.score),
                color = AppColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = AppLocale.handSimulatorRunsRecap(summary.totalRuns),
                color = AppColors.textSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = AppLocale.handSimulatorScoreExplain,
                color = AppColors.textMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            RunButton(
                isSimulating = isSimulating,
                label = AppLocale.handSimulatorRunAgain,
                onRun = onRun
            )

            freeLimitNote?.let { note ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note,
                    color = AppColors.textSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AccuracyCard(warnings: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.orange.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = AppColors.orange,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = AppLocale.handSimulatorAccuracyTitle,
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                warnings.forEach { warning ->
                    Text(text = warning, color = AppColors.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Una sezione dell'analisi: titolo, una riga che dice su cosa e' calcolata, e
 * i numeri. Le sezioni sono una per domanda che il giocatore si fa quando
 * taglia una lista, nell'ordine in cui se la fa.
 */
@Composable
private fun StatCard(
    title: String,
    subtitle: String?,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                subtitle?.let {
                    Text(text = it, color = AppColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            content()
        }
    }
}

/** Una percentuale con la sua barra sottile, senza soglia. */
@Composable
private fun StatLine(label: String, percent: Double, color: Color = AppColors.blue) {
    val value = percent.coerceIn(0.0, 100.0)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${value.roundPercent()}%",
                color = AppColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppColors.textMuted.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((value / 100.0).toFloat())
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

/** Un numero che non e' una percentuale: medie, conteggi. */
@Composable
private fun StatValue(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = AppColors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, color = AppColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Un avvertimento sul gioco, non un consiglio di lista. */
@Composable
private fun StatNote(text: String, tint: Color = AppColors.textMuted) {
    Text(text = text, color = tint, fontSize = 11.sp, lineHeight = 15.sp)
}

/**
 * Quante mani su cento finiscono in ciascun gruppo: una colonna per gruppo,
 * alta quanto la sua quota. Si legge come la forma della distribuzione, che
 * e' quello che una media da sola nasconde.
 */
@Composable
private fun DistributionBars(
    labels: List<String>,
    values: List<Double>,
    highlight: Int? = null
) {
    val max = values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        labels.forEachIndexed { index, label ->
            val value = values.getOrElse(index) { 0.0 }
            val color = if (index == highlight) AppColors.orange else AppColors.blue
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${value.roundPercent()}%",
                    color = AppColors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DISTRIBUTION_HEIGHT * (value / max).toFloat().coerceIn(0.02f, 1f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(color)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    color = AppColors.textSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val DISTRIBUTION_HEIGHT = 56.dp

/**
 * Il mulligan, per primo: e' il dato che decide se la partita comincia.
 * Numeri esatti, non simulati (vedi HandSimulationEngine.exactMulligan).
 */
@Composable
private fun MulliganCard(summary: HandSimulationSummary, deckSize: Int) {
    val mulligan = summary.mulligan
    StatCard(
        title = AppLocale.handSimulatorMulliganTitle,
        subtitle = AppLocale.handSimulatorMulliganSubtitle(summary.basics.inDeck, deckSize)
    ) {
        MetricBar(
            label = AppLocale.handSimulatorFirstHandLegal,
            percent = 100.0 - mulligan.firstHandRate,
            target = TARGET_STARTER,
            index = 0,
            advice = AppLocale.handSimulatorInsightBrickMessage(mulligan.firstHandRate.roundPercent())
        )
        StatLine(AppLocale.handSimulatorMulliganFirst, mulligan.firstHandRate, AppColors.orange)
        StatLine(AppLocale.handSimulatorMulliganTwice, mulligan.twoOrMoreRate, AppColors.red)
        StatValue(AppLocale.handSimulatorMulliganAverage, mulligan.averagePerGame.roundTo2Decimals())
        StatNote(AppLocale.handSimulatorMulliganNote)
    }
}

@Composable
private fun BasicsCard(summary: HandSimulationSummary) {
    val basics = summary.basics
    StatCard(
        title = AppLocale.handSimulatorBasicsTitle,
        subtitle = AppLocale.handSimulatorBasicsSubtitle
    ) {
        DistributionBars(
            labels = List(4) { AppLocale.handSimulatorBasicsBucket(it) },
            values = basics.distribution,
            // Il Base solitario e' un rischio di regolamento, non solo di
            // ritmo: lo si colora diverso dagli altri gruppi.
            highlight = 0
        )
        StatNote(AppLocale.handSimulatorBasicsAverage(basics.average.roundTo2Decimals()), AppColors.textSecondary)
        if (basics.loneBasicRate.roundPercent() > 0) {
            StatNote(AppLocale.handSimulatorLoneBasicWarning(basics.loneBasicRate.roundPercent()), AppColors.orange)
        }
    }
}

@Composable
private fun EnergyCard(summary: HandSimulationSummary, deckSize: Int) {
    val energy = summary.energy
    StatCard(
        title = AppLocale.handSimulatorEnergyTitle,
        subtitle = AppLocale.handSimulatorEnergySubtitle(energy.inDeck, deckSize)
    ) {
        StatLine(AppLocale.handSimulatorEnergyInOpening, energy.inOpeningRate)
        MetricBar(
            label = AppLocale.handSimulatorEnergyByTurn1,
            percent = energy.byTurn1Rate,
            target = TARGET_ENERGY_T1,
            index = 1,
            advice = AppLocale.handSimulatorInsightEnergyMessage(energy.byTurn1Rate.roundPercent())
        )
        StatLine(AppLocale.handSimulatorEnergyByTurn2, energy.byTurn2Rate)
        StatNote(AppLocale.handSimulatorEnergyAverage(energy.averageInOpening.roundTo2Decimals()), AppColors.textSecondary)
    }
}

@Composable
private fun SupportersCard(summary: HandSimulationSummary, deckSize: Int) {
    val supporters = summary.supporters
    StatCard(
        title = AppLocale.handSimulatorSupportersTitle,
        subtitle = AppLocale.handSimulatorSupportersSubtitle(supporters.inDeck, deckSize)
    ) {
        MetricBar(
            label = AppLocale.handSimulatorSupportersByTurn2,
            percent = supporters.byTurn2Rate,
            target = TARGET_SUPPORTER_T2,
            index = 2,
            advice = AppLocale.handSimulatorInsightSupporterMessage((100.0 - supporters.byTurn2Rate).roundPercent())
        )
        DistributionBars(
            labels = List(3) { AppLocale.handSimulatorSupportersBucket(it) },
            values = supporters.distributionByTurn2,
            highlight = 0
        )
        StatNote(AppLocale.handSimulatorSupportersAverage(supporters.averageByTurn2.roundTo2Decimals()), AppColors.textSecondary)
        StatLine(AppLocale.handSimulatorSupportersByTurn1, supporters.byTurn1Rate)
    }
}

/**
 * Come parte il mazzo nei due casi. Dipende da chi inizia, perche' chi
 * inizia non gioca carte Aiuto al primo turno.
 */
@Composable
private fun StartCard(summary: HandSimulationSummary) {
    StatCard(
        title = AppLocale.handSimulatorStartTitle,
        subtitle = AppLocale.handSimulatorStartSubtitle
    ) {
        MetricBar(
            label = AppLocale.handSimulatorDrawSearchFirst,
            percent = summary.goingFirst.outByT1Rate,
            target = TARGET_OUT_T1,
            index = 3,
            advice = AppLocale.handSimulatorInsightOutFirstMessage(summary.goingFirst.outByT1Rate.roundPercent())
        )
        MetricBar(
            label = AppLocale.handSimulatorDrawSearchSecond,
            percent = summary.goingSecond.outByT1Rate,
            target = TARGET_OUT_T1,
            index = 4,
            advice = AppLocale.handSimulatorInsightOutMessage(summary.goingSecond.outByT1Rate.roundPercent())
        )
        MetricBar(
            label = AppLocale.handSimulatorFullStartFirst,
            percent = summary.goingFirst.setupByT2Rate,
            target = TARGET_SETUP_T2,
            index = 5,
            advice = AppLocale.handSimulatorInsightSetupMessage(summary.goingFirst.setupByT2Rate.roundPercent())
        )
        MetricBar(
            label = AppLocale.handSimulatorFullStartSecond,
            percent = summary.goingSecond.setupByT2Rate,
            target = TARGET_SETUP_T2,
            index = 6,
            advice = AppLocale.handSimulatorInsightSetupMessage(summary.goingSecond.setupByT2Rate.roundPercent())
        )
        summary.keyCardByT2Rate?.let { keyRate ->
            MetricBar(
                label = AppLocale.handSimulatorKeyCardByTurn2,
                percent = keyRate,
                target = TARGET_KEY_T2,
                index = 7,
                advice = summary.keyCardPrizedRate?.let { prized ->
                    AppLocale.handSimulatorInsightPrizedMessage(prized.roundPercent())
                }
            )
        }
    }
}

/**
 * Le mani peggiori incontrate, con le carte vere.
 *
 * Prima erano elenchi di nomi: leggibili solo da chi gia' sapeva a memoria
 * cosa fa ogni carta del proprio mazzo, cioe' da nessuno alle due di notte
 * mentre si taglia una lista.
 */
@Composable
private fun ProblemHandsCard(
    hands: List<ProblemHandSample>,
    onSave: (ProblemHandSample) -> Unit,
    onCardClick: (SimulatorCard) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = AppLocale.handSimulatorProblemsTitle,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = AppLocale.handSimulatorProblemsSubtitle,
                color = AppColors.textSecondary,
                fontSize = 12.sp
            )

            hands.forEach { hand ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.background),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HandStrip(cards = hand.cards, onCardClick = onCardClick)

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            hand.tags.forEach { tag ->
                                SimChip(label = translateProblemTag(tag), tint = AppColors.red)
                            }
                        }

                        TextButton(onClick = { onSave(hand) }) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                tint = AppColors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = AppLocale.handSimulatorSaveProblem,
                                color = AppColors.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
