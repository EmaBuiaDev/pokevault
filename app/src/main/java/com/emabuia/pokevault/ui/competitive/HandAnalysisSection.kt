@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.emabuia.pokevault.ui.competitive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.simulator.HandSimulationSummary
import com.emabuia.pokevault.data.simulator.ProblemHandSample
import com.emabuia.pokevault.data.simulator.SimulatorCard
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.AppMotion
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
 * Modalita' Analisi: migliaia di mani, riassunte in un voto.
 *
 * La struttura e' deliberatamente a imbuto — punteggio, poi le quattro
 * metriche che lo compongono, poi il resto dietro a un espandibile — perche'
 * prima le nove percentuali erano affiancate tutte allo stesso peso e non
 * dicevano da dove cominciare a guardare.
 */
internal fun LazyListScope.analysisSection(
    hasDeck: Boolean,
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

    item(key = "analysis-metrics") {
        MetricsCard(summary = summary)
    }

    item(key = "analysis-details") {
        DetailsCard(summary = summary)
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

@Composable
private fun MetricsCard(summary: HandSimulationSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            MetricBar(
                label = AppLocale.handSimulatorMetricStarterShort,
                percent = summary.starterRate,
                target = TARGET_STARTER,
                index = 0,
                advice = AppLocale.handSimulatorInsightBrickMessage(summary.mulliganRate.roundPercent())
            )
            MetricBar(
                label = AppLocale.handSimulatorMetricEnergyShort,
                percent = summary.energyByT1Rate,
                target = TARGET_ENERGY_T1,
                index = 1,
                advice = AppLocale.handSimulatorInsightEnergyMessage(summary.energyByT1Rate.roundPercent())
            )
            MetricBar(
                label = AppLocale.handSimulatorMetricOutShort,
                percent = summary.outByT1Rate,
                target = TARGET_OUT_T1,
                index = 2,
                advice = AppLocale.handSimulatorInsightOutMessage(summary.outByT1Rate.roundPercent())
            )
            MetricBar(
                label = AppLocale.handSimulatorMetricSetupShort,
                percent = summary.setupByT2Rate,
                target = TARGET_SETUP_T2,
                index = 3,
                advice = AppLocale.handSimulatorInsightSetupMessage(summary.setupByT2Rate.roundPercent())
            )
            summary.keyCardByT2Rate?.let { keyRate ->
                MetricBar(
                    label = AppLocale.handSimulatorMetricKeyShort,
                    percent = keyRate,
                    target = TARGET_KEY_T2,
                    index = 4
                )
            }
        }
    }
}

/**
 * Le metriche grezze, chiuse di default.
 *
 * Restano raggiungibili perche' a chi costruisce liste servono davvero (la
 * media di Basic in apertura dice se la linea di starter e' troppo sottile),
 * ma non competono piu' con il punteggio per l'attenzione di chi apre la
 * schermata la prima volta.
 */
@Composable
private fun DetailsCard(summary: HandSimulationSummary) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(AppMotion.chevron),
        label = "detailsChevron"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = AppLocale.handSimulatorAllMetrics,
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = AppColors.textSecondary,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow(AppLocale.handSimulatorTotalRuns, summary.totalRuns.toString())
                    DetailRow(
                        AppLocale.handSimulatorMulliganRate,
                        "${summary.mulliganRate.roundPercent()}%"
                    )
                    DetailRow(
                        AppLocale.handSimulatorAvgBasics,
                        summary.averageBasics.roundTo2Decimals()
                    )
                    DetailRow(
                        AppLocale.handSimulatorAvgMulligans,
                        summary.averageMulligans.roundTo2Decimals()
                    )
                    DetailRow(
                        AppLocale.handSimulatorStarterRate,
                        "${summary.starterRate.roundPercent()}%"
                    )
                    DetailRow(
                        AppLocale.handSimulatorEnergyT1,
                        "${summary.energyByT1Rate.roundPercent()}%"
                    )
                    DetailRow(
                        AppLocale.handSimulatorOutT1,
                        "${summary.outByT1Rate.roundPercent()}%"
                    )
                    DetailRow(
                        AppLocale.handSimulatorSetupT2,
                        "${summary.setupByT2Rate.roundPercent()}%"
                    )
                    summary.keyCardByT2Rate?.let { keyRate ->
                        DetailRow(
                            AppLocale.handSimulatorKeyByT2,
                            "${keyRate.roundPercent()}%"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AppColors.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = AppColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
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
