package com.emabuia.pokevault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emabuia.pokevault.ui.theme.AppMotion

/**
 * Riquadro cliccabile che si stringe sotto al dito.
 *
 * Sostituisce `Modifier.clickable { }` sulle card e sui chip. Il ripple resta —
 * e' il segnale che dice *dove* si e' toccato; la scala aggiunge il peso, cioe'
 * il segnale che dice che l'elemento e' un oggetto e non un disegno.
 *
 * La molla e' rigida di proposito (vedi [AppMotion.pressSpring]): deve stare
 * dietro al dito, non rimbalzare quando lo si alza.
 */
@Composable
fun Modifier.pressScale(
    scaleDown: Float = 0.96f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = AppMotion.pressSpring(),
        label = "pressScale"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick
        )
}

/**
 * Variante per gli elementi larghi quanto lo schermo.
 *
 * Su una card che occupa tutta la larghezza la scala si legge come un
 * tremolio dei bordi; uno scorrimento laterale di pochi dp dice la stessa cosa
 * senza deformare niente. E' il caso delle FeaturedCard della Home.
 */
@Composable
fun Modifier.pressSlide(
    slide: Dp = 4.dp,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val offset by animateFloatAsState(
        targetValue = if (pressed) slide.value else 0f,
        animationSpec = AppMotion.pressSpring(),
        label = "pressSlide"
    )

    return this
        .graphicsLayer { translationX = offset * density }
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick
        )
}

/** Di quanto l'elemento parte piu' in basso del posto che gia' occupa. */
private val CascadeSlide = 40.dp

/**
 * Ingresso a cascata: l'elemento in posizione [index] entra dal basso con un
 * ritardo proporzionale alla sua posizione.
 *
 * [visible] va tenuto da chi chiama e messo a true una volta sola (di solito da
 * un flag nel ViewModel): rigiocare la cascata a ogni ricomposizione o a ogni
 * ritorno sulla schermata la trasforma da benvenuto in inciampo.
 *
 * Il contenuto e' sempre composto e misurato, anche mentre e' ancora invisibile,
 * e il movimento vive tutto dentro `graphicsLayer`: alpha e traslazione girano
 * in fase di disegno e non toccano il layout. Con `AnimatedVisibility` cio' che
 * non era ancora entrato era alto zero, quindi la pagina si apriva schiacciata e
 * tutto quello che stava sotto la cascata — sulla Home, la riga della collezione
 * — compariva a meta' schermo e scendeva mano a mano che gli elementi entravano.
 * La cascata si vede uguale; sotto di lei non si muove piu' niente.
 */
@Composable
fun CascadeIn(
    index: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val motion = AppMotion.current
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = motion.content,
            delayMillis = motion.cascade(index),
            easing = AppMotion.standardEasing
        ),
        label = "cascadeIn"
    )

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * CascadeSlide.toPx()
        }
    ) {
        content()
    }
}
