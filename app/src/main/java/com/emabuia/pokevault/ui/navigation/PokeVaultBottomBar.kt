package com.emabuia.pokevault.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.AppMotion
import com.emabuia.pokevault.util.AppLocale

/**
 * Le quattro sezioni raggiungibili dalla bottom bar.
 *
 * Sono un sottoinsieme di [Routes], non un elenco a parte: le rotte restano
 * quelle di sempre. Le altre sezioni (Competitivo, Album, Gradate, Wishlist)
 * continuano a passare dalla Home — in una barra da quattro voci non ci
 * stanno, e metterle dietro un "Altro" le renderebbe meno visibili di adesso.
 */
enum class BottomTab(val route: String, val icon: ImageVector) {
    HOME(Routes.HOME, Icons.Default.Home),
    CARDS(Routes.COLLECTION, Icons.Default.Style),
    POKEDEX(Routes.POKEDEX, Icons.Default.CatchingPokemon),
    STATS(Routes.STATS, Icons.Default.BarChart);

    val label: String
        get() = when (this) {
            HOME -> AppLocale.navHome
            CARDS -> AppLocale.navCards
            POKEDEX -> AppLocale.navPokedex
            STATS -> AppLocale.navStats
        }

    companion object {
        /** La voce che corrisponde a [route], o null se la barra non va mostrata. */
        fun forRoute(route: String?): BottomTab? = entries.firstOrNull { it.route == route }
    }
}

/**
 * Barra di navigazione principale.
 *
 * Non e' la `NavigationBar` di Material3: quella disegna un suo indicatore a
 * pillola dietro l'icona e non lascia spazio alla barretta che scivola da una
 * voce all'altra. Qui la struttura e' una Row di quattro voci a peso uguale con
 * l'indicatore in un livello sopra, posizionato per offset.
 */
@Composable
fun PokeVaultBottomBar(
    selected: BottomTab?,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = BottomTab.entries
    // Il divisorio segue il testo invece di essere bianco fisso: su tema chiaro
    // un bianco al 7% sopra una surface bianca non si vedrebbe.
    val hairline = AppColors.textPrimary.copy(alpha = 0.07f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.surface)
    ) {
        val tabWidth = maxWidth / tabs.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * (selected?.ordinal ?: 0),
            animationSpec = AppMotion.landing(),
            label = "bottomBarIndicatorOffset"
        )

        // Gli insets stanno qui dentro e non sul Box: cosi' la surface continua
        // a dipingere dietro la barra di sistema, e sono i contenuti a
        // scansarsi.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(hairline)
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                if (selected != null) {
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .width(tabWidth)
                            .height(3.dp)
                            .background(AppColors.blue)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                ) {
                    tabs.forEach { tab ->
                        BottomBarItem(
                            tab = tab,
                            isSelected = tab == selected,
                            onClick = { onSelect(tab) },
                            modifier = Modifier.width(tabWidth)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: BottomTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = AppMotion.pressSpring(),
        label = "bottomBarItemScale"
    )
    val tint by animateColorAsState(
        targetValue = if (isSelected) AppColors.blue else AppColors.textMuted,
        animationSpec = tween(AppMotion.state),
        label = "bottomBarItemTint"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = tab.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1
        )
    }
}

/**
 * FAB dello scanner, l'unico rimasto.
 *
 * Prima la Home aveva due FAB impilati (wishlist e scanner) e nessuna barra;
 * ora la wishlist si raggiunge dalla Home e qui resta la sola azione che ha
 * senso da qualunque sezione: inquadrare una carta.
 */
@Composable
fun ScannerFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = AppMotion.pressSpring(),
        label = "scannerFabScale"
    )
    val shape = RoundedCornerShape(16.dp)
    val glow = AppColors.blue.copy(alpha = 0.45f)

    FloatingActionButton(
        onClick = onClick,
        containerColor = AppColors.blue,
        contentColor = AppColors.onAccent,
        shape = shape,
        interactionSource = interaction,
        // L'ombra la mette il modifier, colorata di blu: quella di default e'
        // nera e sotto un FAB blu sporca invece di staccarlo.
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp
        ),
        modifier = modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = glow,
                spotColor = glow
            )
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = AppLocale.scanCard
        )
    }
}
