package com.emabuia.pokevault.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Token di motion dell'app.
 *
 * Stessa impostazione dei colori (vedi [PokeVaultColors]): le durate non sono
 * costanti sparse nei composable ma un valore fornito via CompositionLocal, che
 * si legge da [AppMotion]. Cosi' il ritmo dell'interfaccia si ritocca in un
 * punto solo, e soprattutto puo' esistere una seconda palette — quella ridotta
 * — che il tema fornisce quando l'utente ha spento le animazioni di sistema.
 *
 * I nomi sono per *ruolo* (micro-stato, contenuto, schermata) e non per luogo:
 * la durata `chevron` vale per ogni chevron dell'app, non per un accordion in
 * particolare.
 */
@Immutable
data class PokeVaultMotion(
    /**
     * false quando `ANIMATOR_DURATION_SCALE` di sistema e' 0.
     *
     * Con le durate a zero le transizioni sono gia' istantanee, ma le
     * animazioni *decorative* e infinite (foil, coriandoli, shimmer, anelli
     * dello scanner) non hanno una durata da azzerare: girerebbero comunque.
     * Chi le disegna deve leggere questo flag e saltarle del tutto.
     */
    val enabled: Boolean,

    // ── Micro-stato: press, colore, rotazione di un chevron ────────────────
    /** Press-scale e feedback immediati. */
    val press: Int,
    /** Cambio di colore di icone e testi (tab attiva, chip selezionato). */
    val state: Int,
    /** Rotazioni brevi: chevron degli accordion, frecce. */
    val chevron: Int,

    // ── Contenuto: fade, cascate, barre ────────────────────────────────────
    /** Comparsa di un blocco di contenuto. */
    val content: Int,
    /** Ritardo fra un elemento e il successivo in una cascata. */
    val cascadeStagger: Int,
    /** Crossfade fra scheletro e contenuto vero. */
    val crossfade: Int,
    /** Riempimento delle barre delle statistiche. */
    val bar: Int,
    /** Ritardo fra una barra e la successiva. */
    val barStagger: Int,

    // ── Transizione di schermata ───────────────────────────────────────────
    val screenEnter: Int,
    val screenExit: Int,

    // ── Momenti ────────────────────────────────────────────────────────────
    /** Flip 3D della carta nel dettaglio. */
    val flip: Int,
    /** Giro completo dello shimmer su uno scheletro. */
    val shimmer: Int,
    /** Ritardo fra una riga di scheletro e la successiva. */
    val shimmerStagger: Int,
    /** Passata della holo-foil sulle carte rare. */
    val foil: Int,
    /** Entrata del badge di set completato. */
    val celebration: Int,
    /** Volo di un coriandolo. */
    val confetti: Int,
    /** Ritardo fra un coriandolo e il successivo. */
    val confettiStagger: Int,
    /** Respiro dell'alone attorno al badge. */
    val glow: Int,

    // ── Scanner (quattro stati) ────────────────────────────────────────────
    /** Anello che si espande e svanisce durante l'inquadratura. */
    val scanRing: Int,
    /** Spazzata della banda sul riquadro, andata o ritorno. */
    val scanSweep: Int,
    /** Pulsazione delle barre placeholder durante la lettura. */
    val scanPulse: Int,
    /** Flash bianco al riconoscimento. */
    val scanFlash: Int,
    /** Pop della spunta verde. */
    val scanCheck: Int,

    // ── Pull-to-refresh ────────────────────────────────────────────────────
    /** Giro della pokeball mentre il Worker risponde. */
    val pullSpin: Int
) {
    /** Ritardo di cascata per l'elemento in posizione [index]. */
    fun cascade(index: Int): Int = if (enabled) index * cascadeStagger else 0

    /** Ritardo di cascata per la riga [index] di uno scheletro. */
    fun shimmerCascade(index: Int): Int = if (enabled) index * shimmerStagger else 0

    /** Ritardo per la barra in posizione [index]. */
    fun barCascade(index: Int): Int = if (enabled) index * barStagger else 0

    /** Ritardo per il coriandolo in posizione [index]. */
    fun confettiCascade(index: Int): Int = if (enabled) index * confettiStagger else 0
}

/**
 * Ritmo normale.
 *
 * Tre fasce: micro-stato 180–320 ms, contenuto 400–900 ms, schermata 220 ms in
 * entrata e 180 in uscita. Le animazioni continue (foil, shimmer, anelli) stanno
 * sopra il secondo perche' devono restare in sottofondo e non tirare l'occhio.
 */
val StandardPokeVaultMotion = PokeVaultMotion(
    enabled = true,

    press = 180,
    state = 250,
    chevron = 320,

    content = 420,
    cascadeStagger = 60,
    crossfade = 350,
    bar = 900,
    barStagger = 90,

    screenEnter = 220,
    screenExit = 180,

    flip = 720,
    shimmer = 1300,
    shimmerStagger = 120,
    foil = 2800,
    celebration = 450,
    confetti = 1000,
    confettiStagger = 70,
    glow = 2200,

    scanRing = 2400,
    scanSweep = 1600,
    scanPulse = 1100,
    scanFlash = 550,
    scanCheck = 500,

    pullSpin = 800
)

/**
 * Ritmo azzerato, per `ANIMATOR_DURATION_SCALE == 0`.
 *
 * Tutto a zero e non "piu' veloce": chi ha spento le animazioni di sistema lo ha
 * fatto per non vederle, non per vederle di corsa. Gli stati cambiano senza
 * interpolazione e [PokeVaultMotion.enabled] spegne le animazioni decorative.
 */
val ReducedPokeVaultMotion = PokeVaultMotion(
    enabled = false,

    press = 0,
    state = 0,
    chevron = 0,

    content = 0,
    cascadeStagger = 0,
    crossfade = 0,
    bar = 0,
    barStagger = 0,

    screenEnter = 0,
    screenExit = 0,

    flip = 0,
    shimmer = 0,
    shimmerStagger = 0,
    foil = 0,
    celebration = 0,
    confetti = 0,
    confettiStagger = 0,
    glow = 0,

    scanRing = 0,
    scanSweep = 0,
    scanPulse = 0,
    scanFlash = 0,
    scanCheck = 0,

    pullSpin = 0
)

/**
 * Ritmo corrente. Si legge tramite [AppMotion].
 *
 * staticCompositionLocalOf come per i colori: cambia solo quando l'utente tocca
 * l'impostazione di sistema delle animazioni, e in quel caso ricomporre tutto e'
 * esattamente cio' che serve.
 */
val LocalPokeVaultMotion = staticCompositionLocalOf { StandardPokeVaultMotion }

/**
 * Impostazione di sistema che scala (o azzera) le animazioni.
 *
 * Compose applica gia' questo fattore alle durate dei `tween` attraverso il suo
 * frame clock; quello che non sa fare da solo e' *saltare* le animazioni
 * decorative, ed e' il motivo per cui il tema legge il valore anche qui.
 */
internal const val ANIMATOR_DURATION_SCALE_SETTING: String =
    Settings.Global.ANIMATOR_DURATION_SCALE

private fun readAnimatorScale(context: Context): Float =
    Settings.Global.getFloat(context.contentResolver, ANIMATOR_DURATION_SCALE_SETTING, 1f)

/**
 * Palette di motion da fornire al tema: [StandardPokeVaultMotion], oppure
 * [ReducedPokeVaultMotion] se l'utente ha azzerato le animazioni di sistema.
 *
 * L'impostazione e' osservata e non letta una volta sola: si cambia dalle
 * Opzioni sviluppatore senza che l'app venga ricreata, e senza observer l'app
 * continuerebbe ad animare fino al riavvio del processo.
 */
@Composable
fun rememberAppMotion(): PokeVaultMotion {
    // Le anteprime non hanno un contentResolver di sistema da interrogare.
    if (LocalInspectionMode.current) return StandardPokeVaultMotion

    val context = LocalContext.current
    var scale by remember(context) { mutableFloatStateOf(readAnimatorScale(context)) }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = readAnimatorScale(context)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(ANIMATOR_DURATION_SCALE_SETTING),
            false,
            observer
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return if (scale == 0f) ReducedPokeVaultMotion else StandardPokeVaultMotion
}

/** Easing di riferimento del prototipo: `cubic-bezier(.2, 0, 0, 1)`. */
private val PrototypeEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Punto di accesso ai token di motion.
 *
 * Da usare al posto di durate scritte a mano nei composable: quelle sono valori
 * fissi, quindi non seguono ne' un ritocco globale del ritmo ne' le impostazioni
 * di accessibilita'. Dove serve passare le durate a codice non composable (le
 * lambda di transizione di `NavHost`, per esempio) si legge [current] una volta
 * sola e si passa in giro l'oggetto.
 */
object AppMotion {
    /** L'intera palette, per chi deve passarla fuori dalla composizione. */
    val current: PokeVaultMotion
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current

    val enabled: Boolean
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.enabled

    val press: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.press
    val state: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.state
    val chevron: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.chevron

    val content: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.content
    val cascadeStagger: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.cascadeStagger
    val crossfade: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.crossfade
    val bar: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.bar
    val barStagger: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.barStagger

    val screenEnter: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.screenEnter
    val screenExit: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.screenExit

    val flip: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.flip
    val shimmer: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.shimmer
    val shimmerStagger: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.shimmerStagger
    val foil: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.foil
    val celebration: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.celebration
    val confetti: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.confetti
    val glow: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.glow

    val scanRing: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.scanRing
    val scanSweep: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.scanSweep
    val scanPulse: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.scanPulse
    val scanFlash: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.scanFlash
    val scanCheck: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.scanCheck

    val pullSpin: Int
        @Composable @ReadOnlyComposable get() = LocalPokeVaultMotion.current.pullSpin

    /** Easing di default: quello che il prototipo usa ovunque. */
    val easing: Easing get() = PrototypeEasing

    /** Easing di sistema, dove conviene restare in famiglia Material. */
    val standardEasing: Easing get() = FastOutSlowInEasing

    /** Easing delle animazioni continue: nessuna accelerazione, o si notano. */
    val linearEasing: Easing get() = LinearEasing

    /**
     * Spec degli elementi che "atterrano": indicatore della bottom bar, badge
     * di set completato, transizione condivisa della carta.
     */
    fun <T> landing(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 380f)

    /** Spec del press-scale: piu' rigido, deve stare dietro al dito. */
    fun <T> pressSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 600f
    )
}
