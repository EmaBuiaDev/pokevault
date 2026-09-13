@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.emabuia.pokevault.ui.competitive

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
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.simulator.HandEvaluation
import com.emabuia.pokevault.data.simulator.HandTrait
import com.emabuia.pokevault.data.simulator.HandVerdict
import com.emabuia.pokevault.data.simulator.PracticeHand
import com.emabuia.pokevault.data.simulator.PracticeTally
import com.emabuia.pokevault.data.simulator.SavedProblemHand
import com.emabuia.pokevault.data.simulator.SimulatorCard
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modalita' Prova: una mano alla volta, come al tavolo.
 *
 * Esiste perche' la domanda che il giocatore si fa non e' "qual e' la mia
 * percentuale di setup entro T2" ma "questa mano la tengo?". La simulazione
 * statistica non sa rispondere a quella: mostra medie, e una media non e' una
 * mano. Qui le sette carte si vedono davvero, e la decisione e' dell'utente —
 * il conteggio in fondo gli dice poi quanto spesso l'ha dovuta rimescolare.
 */
internal fun LazyListScope.practiceSection(
    hasDeck: Boolean,
    /** Mazzo scelto ma con meno carte di quante ne serva scoprire. */
    deckTooSmall: Boolean,
    hand: PracticeHand?,
    tally: PracticeTally,
    dealKey: Int,
    savedHands: List<SavedProblemHand>,
    onDeal: () -> Unit,
    onKeep: () -> Unit,
    onMulligan: () -> Unit,
    onDraw: () -> Unit,
    onSaveHand: () -> Unit,
    onDeleteSaved: (String) -> Unit,
    onCardClick: (SimulatorCard) -> Unit
) {
    if (!hasDeck) {
        item(key = "practice-empty") {
            PracticeEmptyState(
                title = AppLocale.handSimulatorPracticeEmptyTitle,
                body = AppLocale.handSimulatorPracticeEmptyBody
            )
        }
        return
    }

    if (deckTooSmall) {
        item(key = "practice-too-small") {
            PracticeEmptyState(
                title = AppLocale.handSimulatorInvalidDeck,
                body = AppLocale.handSimulatorNoDecksSubtitle
            )
        }
        return
    }

    if (hand == null) {
        item(key = "practice-start") {
            PracticeStartCard(onDeal = onDeal)
        }
    } else {
        item(key = "practice-table") {
            PracticeTable(
                hand = hand,
                dealKey = dealKey,
                onKeep = onKeep,
                onMulligan = onMulligan,
                onDraw = onDraw,
                onSaveHand = onSaveHand,
                onCardClick = onCardClick
            )
        }

        item(key = "practice-tally") {
            PracticeTallyRow(tally = tally)
        }
    }

    item(key = "practice-vault") {
        SavedHandsCard(
            savedHands = savedHands,
            onDelete = onDeleteSaved
        )
    }
}

@Composable
private fun PracticeEmptyState(title: String, body: String) {
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
                imageVector = Icons.Default.Style,
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
private fun PracticeStartCard(onDeal: () -> Unit) {
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
            Text(
                text = AppLocale.handSimulatorPracticeEmptyBody,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDeal,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Casino, contentDescription = null)
                Text(
                    text = AppLocale.handSimulatorDealButton,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PracticeTable(
    hand: PracticeHand,
    dealKey: Int,
    onKeep: () -> Unit,
    onMulligan: () -> Unit,
    onDraw: () -> Unit,
    onSaveHand: () -> Unit,
    onCardClick: (SimulatorCard) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hand.turn == 0) {
                        AppLocale.handSimulatorOpeningLabel
                    } else {
                        AppLocale.handSimulatorTurnLabel(hand.turn)
                    },
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (hand.mulligans > 0) {
                    SimChip(
                        label = AppLocale.handSimulatorMulligansTaken(hand.mulligans),
                        tint = AppColors.orange
                    )
                }
            }

            HandFan(
                cards = hand.cards,
                openingSize = hand.opening.size,
                dealKey = dealKey,
                onCardClick = onCardClick
            )

            Text(
                text = AppLocale.handSimulatorTapCardHint,
                color = AppColors.textMuted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            VerdictBanner(
                verdict = hand.evaluation.verdict,
                composition = hand.evaluation.compositionLine()
            )

            val gaps = hand.evaluation.gapLabels()
            if (gaps.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    gaps.forEach { gap ->
                        SimChip(label = gap, tint = AppColors.red)
                    }
                }
            }

            Text(
                text = hand.evaluation.verdict.why(),
                color = AppColors.textSecondary,
                fontSize = 12.sp
            )

            // La pescata del turno viene prima della decisione: e' il modo in
            // cui si scopre se la mano si sblocca da sola, e va provato mentre
            // le carte sono ancora sul tavolo.
            OutlinedButton(
                onClick = onDraw,
                enabled = hand.canDraw,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (hand.canDraw) {
                        AppLocale.handSimulatorDrawTurn(hand.turn + 1)
                    } else {
                        AppLocale.handSimulatorDeckEmpty
                    }
                )
            }

            Text(
                text = AppLocale.handSimulatorDecideHint,
                color = AppColors.textMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onMulligan,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hand.evaluation.verdict == HandVerdict.MULLIGAN) {
                            AppColors.red
                        } else {
                            AppColors.surface
                        },
                        contentColor = if (hand.evaluation.verdict == HandVerdict.MULLIGAN) {
                            AppColors.onAccent
                        } else {
                            AppColors.textPrimary
                        }
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = AppLocale.handSimulatorMulliganButton,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                Button(
                    onClick = onKeep,
                    enabled = hand.evaluation.verdict != HandVerdict.MULLIGAN,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.green),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = AppLocale.handSimulatorKeepButton,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            TextButton(
                onClick = onSaveHand,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = AppLocale.handSimulatorSaveProblem,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun PracticeTallyRow(tally: PracticeTally) {
    if (tally.dealt == 0) return

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AppLocale.handSimulatorTally(tally.kept, tally.mulliganed),
                    color = AppColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                tally.keepRate?.let { rate ->
                    Text(
                        text = AppLocale.handSimulatorKeepRate(rate),
                        color = AppColors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            tally.keepRate?.let { rate ->
                SimChip(
                    label = "$rate%",
                    tint = if (rate >= 70) AppColors.green else AppColors.yellow
                )
            }
        }
    }
}

@Composable
private fun SavedHandsCard(
    savedHands: List<SavedProblemHand>,
    onDelete: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = AppLocale.handSimulatorSavedTitle,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            if (savedHands.isEmpty()) {
                Text(
                    text = AppLocale.handSimulatorSavedEmpty,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp
                )
            } else {
                savedHands.take(12).forEach { saved ->
                    SavedHandRow(hand = saved, onDelete = { onDelete(saved.id) })
                }
            }
        }
    }
}

@Composable
private fun SavedHandRow(
    hand: SavedProblemHand,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val dateLabel = remember(hand.createdAtMillis) { formatter.format(Date(hand.createdAtMillis)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.background),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hand.deckName,
                        color = AppColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateLabel,
                        color = AppColors.textSecondary,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = AppLocale.delete,
                        tint = AppColors.textSecondary
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                hand.tags.forEach { tag ->
                    SimChip(label = translateProblemTag(tag))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                hand.cards.forEach { cardName ->
                    Text(
                        text = cardName,
                        color = AppColors.textMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Come si legge una mano
// ══════════════════════════════════════════════════════════════════════════

/**
 * "2 Basic · 1 energia · 2 out": cosa c'e' in mano, nell'ordine in cui conta.
 *
 * I supporter compaiono solo quando ci sono: elencare gli zeri allungherebbe
 * la riga proprio nei casi in cui il verdetto sopra dice gia' tutto.
 */
private fun HandEvaluation.compositionLine(): String {
    val parts = mutableListOf(AppLocale.handCountBasics(basics))
    if (energies > 0) parts += AppLocale.handCountEnergy(energies)
    if (outs > 0) parts += AppLocale.handCountOuts(outs)
    if (supporters > 0) parts += AppLocale.handCountSupporters(supporters)
    if (keyCardsFound.isNotEmpty()) parts += AppLocale.handHasKeyCard
    return parts.joinToString(" · ")
}

/** Cosa manca alla mano, come etichette rosse sotto al verdetto. */
private fun HandEvaluation.gapLabels(): List<String> {
    val gaps = mutableListOf<String>()
    if (energies == 0) gaps += AppLocale.handMissingEnergy
    if (outs == 0) gaps += AppLocale.handMissingOut
    if (weaknesses.contains(HandTrait.MISS_KEY_CARD)) gaps += AppLocale.handMissingKeyCard
    return gaps
}
