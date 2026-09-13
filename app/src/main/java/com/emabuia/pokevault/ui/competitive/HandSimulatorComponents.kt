package com.emabuia.pokevault.ui.competitive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.emabuia.pokevault.data.simulator.HandVerdict
import com.emabuia.pokevault.data.simulator.SimulatorCard
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.AppMotion
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.ImageUrlUtils

/** Proporzioni di una carta Pokémon: 63 x 88 mm. */
private const val CARD_ASPECT_RATIO = 63f / 88f

/** Quanto una carta del ventaglio copre la precedente. */
private const val FAN_OVERLAP = 0.38f

/** Larghezza massima di una carta nel ventaglio: oltre, sette carte sfondano. */
private val FAN_MAX_CARD_WIDTH = 86.dp

/** Gradi di rotazione fra una carta e la successiva. */
private const val FAN_STEP_DEGREES = 4.5f

/**
 * Perno della rotazione, in unita' di altezza della carta.
 *
 * Sta sotto la carta (>1) perche' e' il punto attorno a cui ruota una mano
 * tenuta in mano davvero: cosi' il ventaglio si apre ad arco senza che serva
 * calcolare a mano lo scostamento verticale di ogni carta.
 */
private val FAN_PIVOT = TransformOrigin(0.5f, 2.6f)

// ══════════════════════════════════════════════════════════════════════════
// Carta
// ══════════════════════════════════════════════════════════════════════════

/**
 * L'immagine di una carta, con ripiego sul nome quando l'URL manca.
 *
 * Il ripiego non e' un dettaglio: i mazzi importati dal Deck Lab possono avere
 * carte senza immagine, e un rettangolo vuoto in mezzo al ventaglio si legge
 * come un errore di caricamento invece che come "questa carta non ha foto".
 */
@Composable
internal fun SimCardFace(
    card: SimulatorCard,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp
) {
    val imageUrl = remember(card.imageUrl) {
        if (card.imageUrl.isBlank()) "" else ImageUrlUtils.safeProxiedImageUrl(card.imageUrl)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(AppColors.surface),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Style,
                    contentDescription = null,
                    tint = AppColors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = card.name,
                    color = AppColors.textSecondary,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Ventaglio
// ══════════════════════════════════════════════════════════════════════════

/**
 * La mano, disposta a ventaglio.
 *
 * [dealKey] cambia a ogni nuova mano e fa ripartire l'entrata a cascata: e' un
 * parametro e non un `remember` interno perche' rimescolare senza che le carte
 * si rialzino renderebbe il mulligan indistinguibile da una ricomposizione.
 *
 * Le carte da [openingSize] in poi sono le pescate dei turni successivi e
 * vengono marcate: nel ventaglio non si distinguerebbero dall'apertura, ed e'
 * esattamente la distinzione che conta quando si valuta se la mano si e'
 * sbloccata da sola.
 */
@Composable
internal fun HandFan(
    cards: List<SimulatorCard>,
    openingSize: Int,
    dealKey: Int,
    modifier: Modifier = Modifier,
    onCardClick: (SimulatorCard) -> Unit = {}
) {
    if (cards.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val count = cards.size
        val span = count - (count - 1) * FAN_OVERLAP
        val cardWidth = minOf(FAN_MAX_CARD_WIDTH, maxWidth / span)
        val cardHeight = cardWidth / CARD_ASPECT_RATIO
        val center = (count - 1) / 2f

        // La rotazione attorno a un perno sotto la carta alza le carte esterne:
        // senza questo margine il ventaglio verrebbe tagliato in alto.
        val fanRise = cardHeight * 0.22f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight + fanRise),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(-(cardWidth * FAN_OVERLAP)),
                verticalAlignment = Alignment.Bottom
            ) {
                cards.forEachIndexed { index, card ->
                    val offsetFromCenter = index - center
                    FannedCard(
                        card = card,
                        isDrawn = index >= openingSize,
                        turnLabel = if (index >= openingSize) "T${index - openingSize + 1}" else null,
                        angle = offsetFromCenter * FAN_STEP_DEGREES,
                        entryIndex = index,
                        dealKey = dealKey,
                        modifier = Modifier
                            .width(cardWidth)
                            .height(cardHeight)
                            // Le carte a destra stanno sopra: e' l'ordine con
                            // cui si apre un ventaglio tenuto nella sinistra.
                            .zIndex(index.toFloat()),
                        onClick = { onCardClick(card) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FannedCard(
    card: SimulatorCard,
    isDrawn: Boolean,
    turnLabel: String?,
    angle: Float,
    entryIndex: Int,
    dealKey: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val motion = AppMotion.current
    val entry = remember(dealKey, entryIndex) { Animatable(if (motion.enabled) 0f else 1f) }

    LaunchedEffect(dealKey, entryIndex) {
        if (!motion.enabled) {
            entry.snapTo(1f)
            return@LaunchedEffect
        }
        entry.snapTo(0f)
        entry.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = motion.content,
                delayMillis = motion.cascade(entryIndex),
                easing = AppMotion.easing
            )
        )
    }

    val progress = entry.value
    val borderColor = if (isDrawn) AppColors.blue else Color.Transparent

    Box(
        modifier = modifier.graphicsLayer {
            transformOrigin = FAN_PIVOT
            rotationZ = angle * progress
            // Le carte entrano dal mazzo: partono accatastate al centro, in
            // basso, e si aprono verso la propria posizione.
            translationY = (1f - progress) * size.height * 0.35f
            alpha = progress
        }
    ) {
        SimCardFace(
            card = card,
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (isDrawn) 2.dp else 0.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onClick)
        )

        if (turnLabel != null) {
            Surface(
                color = AppColors.blue,
                shape = RoundedCornerShape(bottomStart = 6.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = turnLabel,
                    color = AppColors.onAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Fila compatta di carte, per le mani problematiche dell'analisi.
 *
 * Qui il ventaglio sarebbe fuori posto: sono sette esempi in colonna, e ogni
 * mano deve occupare una riga sola.
 */
@Composable
internal fun HandStrip(
    cards: List<SimulatorCard>,
    modifier: Modifier = Modifier,
    onCardClick: (SimulatorCard) -> Unit = {}
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val count = cards.size.coerceAtLeast(1)
        val cardWidth = minOf(44.dp, (maxWidth - 4.dp * (count - 1)) / count)

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            cards.forEach { card ->
                SimCardFace(
                    card = card,
                    cornerRadius = 5.dp,
                    modifier = Modifier
                        .width(cardWidth)
                        .aspectRatio(CARD_ASPECT_RATIO)
                        .clickable { onCardClick(card) }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Verdetto
// ══════════════════════════════════════════════════════════════════════════

@Composable
internal fun HandVerdict.color(): Color = when (this) {
    HandVerdict.GREAT -> AppColors.green
    HandVerdict.PLAYABLE -> AppColors.blue
    HandVerdict.RISKY -> AppColors.yellow
    HandVerdict.MULLIGAN -> AppColors.red
}

internal fun HandVerdict.label(): String = when (this) {
    HandVerdict.GREAT -> AppLocale.handVerdictGreat
    HandVerdict.PLAYABLE -> AppLocale.handVerdictPlayable
    HandVerdict.RISKY -> AppLocale.handVerdictRisky
    HandVerdict.MULLIGAN -> AppLocale.handVerdictMulligan
}

internal fun HandVerdict.why(): String = when (this) {
    HandVerdict.GREAT -> AppLocale.handVerdictGreatWhy
    HandVerdict.PLAYABLE -> AppLocale.handVerdictPlayableWhy
    HandVerdict.RISKY -> AppLocale.handVerdictRiskyWhy
    HandVerdict.MULLIGAN -> AppLocale.handVerdictMulliganWhy
}

/**
 * Il verdetto, in una riga sola.
 *
 * Il colore e' animato perche' fra una pescata e l'altra il verdetto cambia in
 * corsa: un salto secco di colore si legge come un altro elemento apparso,
 * non come lo stesso elemento che ha cambiato giudizio.
 */
@Composable
internal fun VerdictBanner(
    verdict: HandVerdict,
    composition: String,
    modifier: Modifier = Modifier
) {
    val target = verdict.color()
    val tint by animateColorAsState(
        targetValue = target,
        animationSpec = tween(AppMotion.state),
        label = "verdictColor"
    )

    Surface(
        color = tint.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = verdict.label(),
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = composition,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Punteggio e metriche
// ══════════════════════════════════════════════════════════════════════════

internal fun scoreColorIndex(score: Int): Int = when {
    score >= 80 -> 0
    score >= 65 -> 1
    score >= 50 -> 2
    else -> 3
}

@Composable
internal fun scoreColor(score: Int): Color = when (scoreColorIndex(score)) {
    0 -> AppColors.green
    1 -> AppColors.blue
    2 -> AppColors.yellow
    else -> AppColors.red
}

/**
 * Anello del punteggio di consistenza.
 *
 * Un anello e non una barra: il punteggio e' un voto complessivo e non una
 * delle percentuali: se avesse la stessa forma delle metriche sotto
 * sembrerebbe la quinta metrica invece della loro sintesi.
 */
@Composable
internal fun ConsistencyRing(
    score: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 132.dp,
    /** Cosa misura l'anello. Il Match Log lo riusa per il tasso di vittorie. */
    label: String = AppLocale.handSimulatorScoreLabel,
    /** Il colore del riempimento; di default segue le soglie del punteggio. */
    ringColor: Color? = null
) {
    val motion = AppMotion.current
    val color = ringColor ?: scoreColor(score)
    val trackColor = AppColors.textMuted.copy(alpha = 0.22f)

    val sweep by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(
            durationMillis = motion.bar,
            easing = AppMotion.easing
        ),
        label = "consistencySweep"
    )

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.09f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)

            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = 270f * sweep.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp
            )
            Text(
                text = label.uppercase(),
                color = AppColors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Una metrica come barra, con la soglia segnata sopra.
 *
 * La soglia e' il pezzo che mancava: "energia entro T1: 71%" non dice niente
 * finche' non si sa che sotto il 72% il mazzo resta senza attacco. Il segno
 * sulla barra lo dice senza una riga di testo in piu'.
 */
@Composable
internal fun MetricBar(
    label: String,
    percent: Double,
    target: Int,
    index: Int,
    modifier: Modifier = Modifier,
    /** Cosa fare quando la metrica sta sotto soglia. Mostrato solo in quel caso. */
    advice: String? = null
) {
    val motion = AppMotion.current
    val value = percent.coerceIn(0.0, 100.0).toFloat()
    val belowTarget = value < target

    val fillColor = if (belowTarget) AppColors.yellow else AppColors.green
    val fill by animateFloatAsState(
        targetValue = value / 100f,
        animationSpec = tween(
            durationMillis = motion.bar,
            delayMillis = motion.barCascade(index),
            easing = AppMotion.easing
        ),
        label = "metricFill"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = AppColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${value.toInt()}%",
                color = if (belowTarget) AppColors.yellow else AppColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(AppColors.textMuted.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(5.dp))
                    .background(fillColor)
            )

            // Il segno della soglia: una tacca verticale sulla barra.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .padding(start = maxWidth * (target / 100f))
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(AppColors.textPrimary.copy(alpha = 0.55f))
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (belowTarget) {
                "${AppLocale.handSimulatorBelowTarget} · ${AppLocale.handSimulatorTarget(target)}"
            } else {
                AppLocale.handSimulatorTarget(target)
            },
            color = if (belowTarget) AppColors.yellow else AppColors.textMuted,
            fontSize = 11.sp
        )

        // Il consiglio sta attaccato alla barra che lo ha generato: prima era
        // in una card di insight separata, e l'utente doveva ricostruire da
        // solo a quale percentuale si riferisse ognuno.
        if (belowTarget && advice != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = AppColors.yellow.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = advice,
                    color = AppColors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Interruttore a due posizioni fra Prova e Analisi.
 *
 * Material3 ha `SingleChoiceSegmentedButtonRow`, ma i suoi colori non
 * discendono dai token dell'app e su due sole voci risulta piu' pesante di
 * quello che serve.
 */
@Composable
internal fun ModeSwitch(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.card)
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val background by animateColorAsState(
                targetValue = if (selected) AppColors.blue else Color.Transparent,
                animationSpec = tween(AppMotion.state),
                label = "modeBackground"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) AppColors.onAccent else AppColors.textSecondary,
                animationSpec = tween(AppMotion.state),
                label = "modeText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(background)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/** Etichetta neutra: conteggi, tag, nomi di carta. */
@Composable
internal fun SimChip(
    label: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val accent = tint ?: AppColors.textSecondary

    Surface(
        color = if (tint != null) accent.copy(alpha = 0.14f) else AppColors.background,
        shape = RoundedCornerShape(8.dp),
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    ) {
        Text(
            text = label,
            color = if (tint != null) accent else AppColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}
