package com.emabuia.pokevault.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.emabuia.pokevault.ui.theme.AppMotion

/**
 * Transizione condivisa della carta: la miniatura della griglia e l'immagine
 * grande del dettaglio sono lo stesso oggetto che cambia posto.
 *
 * I due scope necessari viaggiano su CompositionLocal invece che come parametri.
 * L'alternativa era aggiungere due argomenti a ogni composable fra il NavHost e
 * l'immagine — nelle schermate piu' grandi sono cinque o sei livelli — per una
 * cosa che riguarda solo l'ultimo di quei livelli.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Lo scope della destinazione corrente del NavHost.
 *
 * Va fornito dentro ogni `composable { }` che partecipa a una transizione
 * condivisa: e' quello che dice all'animazione quando la schermata entra e
 * quando esce.
 */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marca un'immagine di carta come estremo di una transizione condivisa.
 *
 * Se uno dei due scope manca (schermata fuori dal NavHost, anteprima, test) il
 * modificatore non fa niente: meglio nessuna animazione che un crash.
 *
 * @param cardId la stessa chiave alle due estremita'. Nella collezione e' la
 *   chiave di gruppo, che e' anche cio' che viene passato alla rotta di
 *   dettaglio.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCardImage(cardId: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this

    return with(sharedScope) {
        this@sharedCardImage.sharedElement(
            sharedContentState = rememberSharedContentState(key = "card-$cardId"),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = { _, _ -> AppMotion.landing<Rect>() }
        )
    }
}
