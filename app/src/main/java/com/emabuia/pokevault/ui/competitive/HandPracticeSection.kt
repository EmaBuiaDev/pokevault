@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.emabuia.pokevault.ui.competitive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.simulator.HandEvaluation
import com.emabuia.pokevault.data.simulator.HandTrait
import com.emabuia.pokevault.data.simulator.PracticeHand
import com.emabuia.pokevault.data.simulator.PracticeTally
import com.emabuia.pokevault.data.simulator.SavedProblemHand
import com.emabuia.pokevault.data.simulator.SimulatorCard
import com.emabuia.pokevault.data.simulator.TurnOrder
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modalita' Prova: una mano alla volta, come al tavolo.
 *
 * Esiste perche' la domanda che il giocatore si fa non e' "qual e' la mia
 * percentuale" ma "come parte questa mano?". Qui le sette carte si vedono
 * davvero, con i Premi, e si possono pescare i turni successivi.
 *
 * I pulsanti non stanno qui ma in [PracticeActionBar], fissa in fondo allo
 * schermo: dentro la lista finivano sotto mano, Premi e verdetto, e per
 * pescare un'altra mano bisognava scorrere ogni volta.
 */
internal fun LazyListScope.practiceSection(
    hasDeck: Boolean,
    /** Mazzo scelto ma con meno carte di quante ne serva scoprire. */
    deckTooSmall: Boolean,
    hand: PracticeHand?,
    tally: PracticeTally,
    dealKey: Int,
    savedHands: List<SavedProblemHand>,
    turnOrder: TurnOrder,
    onTurnOrderChange: (TurnOrder) -> Unit,
    onCoinFlip: () -> Unit,
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

    // Chi inizia viene prima della mano: decide cosa si puo' giocare al primo
    // turno, quindi come va letta la mano che esce.
    item(key = "practice-turn-order") {
        TurnOrderRow(
            turnOrder = turnOrder,
            onTurnOrderChange = onTurnOrderChange,
            onCoinFlip = onCoinFlip
        )
    }

    if (hand == null) {
        item(key = "practice-start") {
            PracticeEmptyState(
                title = AppLocale.handSimulatorDealButton,
                body = AppLocale.handSimulatorPracticeEmptyBody
            )
        }
    } else {
        item(key = "practice-table") {
            PracticeTable(
                hand = hand,
                dealKey = dealKey,
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

/**
 * I pulsanti della Prova, sempre visibili in fondo.
 *
 * Seguono il regolamento: con un Pokemon Base in mano la mano si tiene, e si
 * puo' solo andare avanti a pescare o cominciare un'altra partita; senza, il
 * mulligan e' obbligatorio e si fa prima di tutto il resto. Fino alla 3.1.8
 * c'erano "Tengo" e "Mulligan" su ogni mano, come se rimescolare una mano
 * valida fosse una scelta: nel gioco non lo e'.
 */
@Composable
internal fun PracticeActionBar(
    hand: PracticeHand?,
    onDeal: () -> Unit,
    onMulligan: () -> Unit,
    onDraw: () -> Unit
) {
    Surface(color = AppColors.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                hand == null -> Button(
                    onClick = onDeal,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = AppLocale.handSimulatorDealButton,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                hand.needsMulligan -> Button(
                    onClick = onMulligan,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.red,
                        contentColor = AppColors.onAccent
                    ),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = AppLocale.handSimulatorMulliganButton,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                else -> {
                    OutlinedButton(
                        onClick = onDraw,
                        enabled = hand.canDraw,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(
                            text = if (hand.canDraw) {
                                AppLocale.handSimulatorDrawTurn(hand.turn + 1)
                            } else {
                                AppLocale.handSimulatorDeckEmpty
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = onDeal,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = AppLocale.handSimulatorNewHand,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
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

/**
 * Il tavolo: mano, Premi, poi il giudizio.
 *
 * Mano e Premi stanno uno sotto l'altro, prima di ogni testo: sono le carte
 * che si guardano, e con i pulsanti in fondo allo schermo devono entrare
 * insieme nella prima schermata senza rimpicciolirle.
 */
@Composable
private fun PracticeTable(
    hand: PracticeHand,
    dealKey: Int,
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
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically
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
                    modifier = Modifier.padding(end = 4.dp)
                )
                if (hand.mulligans > 0) {
                    SimChip(
                        label = AppLocale.handSimulatorMulligansTaken(hand.mulligans),
                        tint = AppColors.orange
                    )
                    // Regolamento: l'avversario PUO' pescare una carta per
                    // ogni mulligan. E' il prezzo del mulligan, e va detto.
                    SimChip(
                        label = AppLocale.handSimulatorOpponentExtraDraws(hand.mulligans),
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

            if (hand.prizes.isNotEmpty()) {
                PrizesRow(prizes = hand.prizes, dealKey = dealKey, onCardClick = onCardClick)
            }

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
                    gaps.forEach { (label, severe) ->
                        SimChip(label = label, tint = if (severe) AppColors.red else AppColors.orange)
                    }
                }
            }

            Text(
                text = hand.evaluation.verdict.why(),
                color = AppColors.textSecondary,
                fontSize = 12.sp
            )

            if (!hand.needsMulligan) {
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
}

@Composable
private fun PracticeTallyRow(tally: PracticeTally) {
    if (tally.games == 0 && tally.mulligans == 0) return

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
                    text = AppLocale.handSimulatorTally(tally.games, tally.mulligans),
                    color = AppColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                tally.goodStartRate?.let { rate ->
                    Text(
                        text = AppLocale.handSimulatorGoodStartRate(rate),
                        color = AppColors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            tally.goodStartRate?.let { rate ->
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
 * "2 Pokémon Base · 1 Energia · 2 che pescano o cercano": cosa c'e' in mano,
 * nell'ordine in cui conta.
 *
 * Gli Aiuti compaiono solo quando ci sono: elencare gli zeri allungherebbe la
 * riga proprio nei casi in cui il verdetto sopra dice gia' tutto.
 */
internal fun HandEvaluation.compositionLine(): String {
    val parts = mutableListOf(AppLocale.handCountBasics(basics))
    if (energies > 0) parts += AppLocale.handCountEnergy(energies)
    if (outs > 0) parts += AppLocale.handCountOuts(outs)
    if (supporters > 0) parts += AppLocale.handCountSupporters(supporters)
    if (keyCardsFound.isNotEmpty()) parts += AppLocale.handHasKeyCard
    return parts.joinToString(" · ")
}

/**
 * Cosa manca alla mano, come etichette sotto al verdetto: rosse le mancanze,
 * arancioni i rischi (la mano parte, ma puo' costare la partita).
 */
internal fun HandEvaluation.gapLabels(): List<Pair<String, Boolean>> {
    val gaps = mutableListOf<Pair<String, Boolean>>()
    if (energies == 0) gaps += AppLocale.handMissingEnergy to true
    if (outs == 0) gaps += AppLocale.handMissingOut to true
    // Ci sono carte che pescano, ma sono Aiuti e si inizia per primi.
    if (weaknesses.contains(HandTrait.SUPPORTER_LOCKED_T1)) gaps += AppLocale.handMissingUsableOut to false
    if (weaknesses.contains(HandTrait.MISS_KEY_CARD)) gaps += AppLocale.handMissingKeyCard to true
    if (weaknesses.contains(HandTrait.KEY_CARD_PRIZED)) gaps += AppLocale.handKeyCardPrized to false
    // Regolamento: Attivo KO e panchina vuota, partita persa. Con un Base solo
    // succede al primo attacco se non ne arriva un altro.
    if (basics == 1) gaps += AppLocale.handVerdictLoneBasic to false
    return gaps
}

/**
 * Chi inizia, in una riga: i due bottoni e la moneta, sotto la regola.
 *
 * Era una card alta con il lancio della moneta su una riga sua: spingeva
 * giu' la mano e i Premi. La regola resta scritta per esteso perche' e'
 * proprio quella che si dimentica.
 */
@Composable
private fun TurnOrderRow(
    turnOrder: TurnOrder,
    onTurnOrderChange: (TurnOrder) -> Unit,
    onCoinFlip: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModeSwitch(
                options = listOf(AppLocale.handSimulatorGoingFirst, AppLocale.handSimulatorGoingSecond),
                selectedIndex = turnOrder.ordinal,
                onSelect = { index -> onTurnOrderChange(TurnOrder.entries[index]) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onCoinFlip,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.card)
            ) {
                Icon(
                    Icons.Default.Casino,
                    contentDescription = AppLocale.handSimulatorCoinFlip,
                    tint = AppColors.textSecondary
                )
            }
        }
        Text(
            text = when (turnOrder) {
                TurnOrder.FIRST -> AppLocale.handSimulatorRuleGoingFirst
                TurnOrder.SECOND -> AppLocale.handSimulatorRuleGoingSecond
            },
            color = AppColors.textMuted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/**
 * I 6 Premi: coperti, come al tavolo, e scopribili per capire perche' una
 * carta non arriva. Si ricoprono a ogni mano nuova ([dealKey]).
 */
@Composable
private fun PrizesRow(
    prizes: List<SimulatorCard>,
    dealKey: Int,
    onCardClick: (SimulatorCard) -> Unit
) {
    var revealed by remember(dealKey) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = AppLocale.handSimulatorPrizesTitle,
                color = AppColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { revealed = !revealed }) {
                Text(
                    text = if (revealed) AppLocale.handSimulatorHidePrizes else AppLocale.handSimulatorShowPrizes,
                    fontSize = 12.sp
                )
            }
        }
        if (revealed) {
            HandStrip(cards = prizes, onCardClick = onCardClick)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                prizes.forEach { _ ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(44.dp)
                            .aspectRatio(PRIZE_CARD_ASPECT_RATIO)
                            .clip(RoundedCornerShape(5.dp))
                            .background(AppColors.blue.copy(alpha = 0.25f))
                            .border(1.dp, AppColors.blue.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                    ) {
                        Icon(
                            Icons.Default.Style,
                            contentDescription = null,
                            tint = AppColors.blue.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Proporzioni di una carta, come in HandStrip. */
private const val PRIZE_CARD_ASPECT_RATIO = 63f / 88f
