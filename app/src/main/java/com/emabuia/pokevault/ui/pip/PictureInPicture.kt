package com.emabuia.pokevault.ui.pip

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureParamsCompat
import timber.log.Timber

/**
 * Vero mentre l'app sta girando nella finestrella Picture in Picture.
 *
 * Lo fornisce MainActivity. In PiP la finestra e' larga qualche centimetro e
 * non riceve tocchi: una schermata che lo legge deve mostrare solo il proprio
 * contenuto, senza barre, bottoni o pannelli.
 */
val LocalInPictureInPicture = staticCompositionLocalOf { false }

/**
 * Proporzioni della finestra PiP: quelle di una carta Pokemon (63x88mm).
 *
 * La finestra inquadra la stessa scena della cornice di scansione, quindi
 * tanto vale che abbia la sua forma.
 */
private val CardPipRatio = Rational(63, 88)

/**
 * Fa scivolare l'app in Picture in Picture quando l'utente la lascia, finche'
 * questa schermata resta in composizione e [enabled] e' vero.
 *
 * Lo usa lo scanner: e' l'unico punto dell'app con contenuto vivo (l'anteprima
 * della fotocamera e il riconoscimento in corso), cioe' l'unico che ha senso
 * continuare a vedere mentre si guarda altro. Uscendo dalla schermata i
 * parametri vengono azzerati, cosi' il resto dell'app non entra mai in PiP.
 *
 * Da API 31 ci pensa il sistema con l'auto-enter, che e' anche l'unico modo di
 * avere l'animazione fluida dalla schermata alla finestrella. Sotto, l'unico
 * aggancio disponibile e' onUserLeaveHint, che scatta sul tasto Home ma non
 * quando si passa a un'altra app da altre strade.
 */
@Composable
fun PictureInPictureWhenLeaving(
    enabled: Boolean,
    sourceRectHint: Rect? = null
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() } ?: return

    val supported = remember(activity) {
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    if (!supported) return

    // L'effetto si ricrea solo quando cambia `enabled`; il Runnable invece deve
    // leggere sempre il valore corrente.
    val currentlyEnabled by rememberUpdatedState(enabled)

    DisposableEffect(activity, enabled, sourceRectHint) {
        val params = PictureInPictureParamsCompat.Builder()
            .setAspectRatio(CardPipRatio)
            .setEnabled(enabled)
            .apply {
                // Dice al sistema da quale rettangolo dello schermo far partire
                // l'animazione: senza, la finestrella non nasce dall'anteprima
                // ma da tutta la schermata, e si vede un salto.
                sourceRectHint?.let { setSourceRectHint(it) }
            }
            .build()
        activity.setPictureInPictureParams(params)

        val onUserLeaveHint = Runnable {
            val needsManualEntry = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            if (currentlyEnabled && needsManualEntry && !activity.isInPictureInPictureMode) {
                // Il sistema puo' rifiutare (impostazione PiP negata all'app,
                // activity gia' in uscita): e' un extra, non deve buttare giu'
                // l'app mentre l'utente sta solo tornando alla home.
                runCatching { activity.enterPictureInPictureMode(params) }
                    .onFailure { Timber.w(it, "Ingresso in PiP rifiutato") }
            }
        }
        activity.addOnUserLeaveHintListener(onUserLeaveHint)

        onDispose {
            activity.removeOnUserLeaveHintListener(onUserLeaveHint)
            activity.setPictureInPictureParams(
                PictureInPictureParamsCompat.Builder().setEnabled(false).build()
            )
        }
    }
}

/**
 * Il Context di una composable e' un ContextWrapper attorno all'Activity, non
 * l'Activity stessa.
 */
private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
