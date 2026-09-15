package com.emabuia.pokevault.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.AppMotion

/**
 * Scheletri di caricamento.
 *
 * Sostituiscono i `CircularProgressIndicator`: uno spinner dice solo "aspetta",
 * uno scheletro dice anche *cosa* stai aspettando e quanto sara' lungo, e la
 * comparsa del contenuto vero non e' piu' un salto ma una sostituzione.
 */

/** Riquadro grigio che luccica. Il [index] sfasa la luce riga per riga. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    index: Int = 0
) {
    Box(modifier = modifier.clip(shape).shimmerBackground(index))
}

/**
 * Sfondo animato di uno scheletro.
 *
 * La luce e' una banda chiara che attraversa il riquadro. I colori sono risolti
 * qui, fuori dal blocco di disegno: dentro `drawWithCache`/`Canvas` i token non
 * si possono leggere (convenzione del repo).
 */
@Composable
fun Modifier.shimmerBackground(index: Int = 0): Modifier {
    val base = AppColors.card
    // "Piu' chiaro" su tema scuro e "piu' scuro" su tema chiaro sono lo stesso
    // gesto: un velo del colore del testo sopra la card.
    val highlight = AppColors.textPrimary.copy(alpha = 0.08f).compositeOver(base)
    val motion = AppMotion.current

    if (!motion.enabled) return this.background(base)

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = motion.shimmer,
                delayMillis = motion.shimmerCascade(index),
                easing = AppMotion.linearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    return this.drawBehind { drawShimmer(progress, base, highlight) }
}

/**
 * La banda parte fuori dal riquadro a sinistra e finisce fuori a destra, cosi'
 * il ciclo non ha un istante in cui la luce resta ferma su un bordo.
 */
private fun DrawScope.drawShimmer(
    progress: Float,
    base: Color,
    highlight: Color
) {
    val bandWidth = size.width * 0.6f
    val travel = size.width + bandWidth * 2f
    val start = -bandWidth + travel * progress

    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(start, 0f),
            end = Offset(start + bandWidth, size.height)
        )
    )
}

/**
 * Scheletro della collezione: due espansioni chiuse, una aperta con una riga di
 * carte, altre due chiuse. Riproduce la forma piu' comune della schermata, non
 * un caso limite.
 */
@Composable
fun CollectionSkeleton(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SkeletonBlock(
            modifier = Modifier.fillMaxWidth().height(58.dp),
            index = 0
        )
        SkeletonBlock(
            modifier = Modifier.fillMaxWidth().height(58.dp),
            index = 1
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { column ->
                SkeletonBlock(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.72f),
                    shape = RoundedCornerShape(10.dp),
                    index = column + 2
                )
            }
        }

        SkeletonBlock(
            modifier = Modifier.fillMaxWidth().height(58.dp),
            index = 5
        )
        SkeletonBlock(
            modifier = Modifier.fillMaxWidth().height(58.dp),
            index = 6
        )
    }
}

/** Scheletro delle statistiche: sei StatCard 2x3 e tre barre. */
@Composable
fun StatsSkeleton(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { column ->
                    SkeletonBlock(
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp),
                        shape = RoundedCornerShape(16.dp),
                        index = row * 2 + column
                    )
                }
            }
        }

        repeat(3) { index ->
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                index = 6 + index
            )
        }
    }
}

/**
 * Scheletro della sezione Gradate: tre numeri, la barra della distribuzione, la
 * ricerca coi suoi filtri e quattro slab.
 *
 * Le proporzioni sono quelle vere della griglia — l'etichetta dell'ente sopra,
 * la carta, il piede — cosi' quando il contenuto arriva non c'e' nessun salto.
 */
@Composable
fun GradedSkeleton(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { column ->
                SkeletonBlock(
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp),
                    shape = RoundedCornerShape(14.dp),
                    index = column
                )
            }
        }

        SkeletonBlock(
            modifier = Modifier.fillMaxWidth().height(10.dp),
            shape = RoundedCornerShape(5.dp),
            index = 3
        )

        SkeletonBlock(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            index = 4
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(88, 70, 70, 58).forEachIndexed { index, width ->
                SkeletonBlock(
                    modifier = Modifier.width(width.dp).height(30.dp),
                    shape = RoundedCornerShape(15.dp),
                    index = 5 + index
                )
            }
        }

        repeat(2) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { column ->
                    SkeletonBlock(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.52f),
                        shape = RoundedCornerShape(16.dp),
                        index = 9 + row * 2 + column
                    )
                }
            }
        }
    }
}
