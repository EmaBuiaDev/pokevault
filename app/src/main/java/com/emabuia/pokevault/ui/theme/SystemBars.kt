package com.emabuia.pokevault.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Colore delle icone delle barre di sistema.
 *
 * Con l'edge-to-edge le barre sono trasparenti e sopra ci passa il contenuto
 * dell'app: chi decide se le icone (ora, batteria, gesture bar) vanno disegnate
 * chiare o scure e' l'app, non piu' il tema XML.
 *
 * enableEdgeToEdge() lo fa una volta sola in onCreate e guarda la modalita'
 * scura *di sistema*. L'app pero' ha una sua preferenza (ThemePreference):
 * chi tiene il telefono in scuro e sceglie il tema chiaro si ritrovava le
 * icone bianche su fondo chiaro, cioe' invisibili, fino al riavvio dell'app.
 * Qui le icone seguono il tema effettivamente in uso, e cambiano insieme a lui.
 *
 * isAppearanceLightStatusBars = true significa "icone scure", cioe' fondo
 * chiaro: e' il valore giusto quando il tema dell'app e' chiaro.
 */
@Composable
internal fun SystemBarsFollowTheme(lightAppTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightAppTheme
            isAppearanceLightNavigationBars = lightAppTheme
        }
    }
}

/**
 * Forza le icone di sistema chiare finche' la schermata e' in composizione, e
 * le rimette com'erano quando esce.
 *
 * Serve alle schermate che disegnano un fondo scuro loro, indipendente dal
 * tema: lo scanner e' nero perche' sotto c'e' l'anteprima della fotocamera, e
 * con il tema chiaro si sarebbe ritrovato icone scure su nero.
 */
@Composable
fun LightSystemBarsOverlay() {
    val view = LocalView.current
    if (view.isInEditMode) return

    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars

        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false

        onDispose {
            previousStatus?.let { controller?.isAppearanceLightStatusBars = it }
            previousNavigation?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }
}

/**
 * Il Context di una composable non e' sempre l'Activity: dentro un Dialog o una
 * ComposeView e' un ContextWrapper che la avvolge.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
