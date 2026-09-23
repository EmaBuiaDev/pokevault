package com.emabuia.pokevault.ui.wishlist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emabuia.pokevault.data.model.WishlistAccents
import com.emabuia.pokevault.data.model.WishlistIcons
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale

/**
 * I segni delle wishlist.
 *
 * Le quattro ball sono disegnate qui e non prese da un set di icone: non esiste
 * un'icona Material che sia una Great Ball, e la soluzione precedente — una
 * stella al posto della Master Ball, un fulmine al posto di Pikachu — chiedeva
 * all'utente di ricordare una convenzione invece di guardare un disegno. Una
 * ball disegnata si riconosce senza spiegazioni, e le quattro insieme sono una
 * scala che i giocatori conoscono gia': Poke, Great, Ultra, Master.
 *
 * Le altre sei sono icone Material scelte perche' *sono* la cosa che dicono —
 * il salvadanaio per le occasioni, le frecce per lo scambio, il pacco per il
 * regalo — e si tingono del colore della lista.
 */

// I colori delle ball sono fissi e non seguono il tema: una Master Ball verde
// non e' una Master Ball. Il bianco non e' puro per non bucare il fondo chiaro.
private val BallShell = Color(0xFFF4F4F7)
private val BallBand = Color(0xFF1B1B22)
private val PokeBallRed = Color(0xFFE3350D)
private val GreatBallBlue = Color(0xFF2E6FC4)
private val GreatBallStripe = Color(0xFFD9443C)
private val UltraBallBlack = Color(0xFF2A2A31)
private val UltraBallYellow = Color(0xFFF2C300)
private val MasterBallPurple = Color(0xFF6B34C4)
private val MasterBallStud = Color(0xFFE861A8)

private enum class BallMark { NONE, STRIPES, STUDS }

private data class BallStyle(
    val top: Color,
    val mark: BallMark,
    val markColor: Color = Color.Transparent
)

private fun ballStyle(iconKey: String): BallStyle? = when (iconKey) {
    WishlistIcons.POKE_BALL -> BallStyle(PokeBallRed, BallMark.NONE)
    WishlistIcons.GREAT_BALL -> BallStyle(GreatBallBlue, BallMark.STRIPES, GreatBallStripe)
    WishlistIcons.ULTRA_BALL -> BallStyle(UltraBallBlack, BallMark.STRIPES, UltraBallYellow)
    WishlistIcons.MASTER_BALL -> BallStyle(MasterBallPurple, BallMark.STUDS, MasterBallStud)
    else -> null
}

private fun materialGlyph(iconKey: String): ImageVector = when (iconKey) {
    WishlistIcons.BUDGET -> Icons.Default.Savings
    WishlistIcons.TRADE -> Icons.Default.SwapHoriz
    WishlistIcons.GIFT -> Icons.Default.CardGiftcard
    WishlistIcons.GRADED -> Icons.Default.WorkspacePremium
    WishlistIcons.DECK -> Icons.Default.Style
    WishlistIcons.SET -> Icons.Default.CollectionsBookmark
    else -> Icons.Default.Savings
}

/** L'etichetta dell'icona: dice l'uso della lista, non un Pokemon. */
fun wishlistIconLabel(iconKey: String): String = when (WishlistIcons.normalize(iconKey)) {
    WishlistIcons.POKE_BALL -> AppLocale.wishlistIconPokeBall
    WishlistIcons.GREAT_BALL -> AppLocale.wishlistIconGreatBall
    WishlistIcons.ULTRA_BALL -> AppLocale.wishlistIconUltraBall
    WishlistIcons.MASTER_BALL -> AppLocale.wishlistIconMasterBall
    WishlistIcons.BUDGET -> AppLocale.wishlistIconBudget
    WishlistIcons.TRADE -> AppLocale.wishlistIconTrade
    WishlistIcons.GIFT -> AppLocale.wishlistIconGift
    WishlistIcons.GRADED -> AppLocale.wishlistIconGraded
    WishlistIcons.DECK -> AppLocale.wishlistIconDeck
    else -> AppLocale.wishlistIconSet
}

/** Il colore della lista, risolto sul tema corrente. */
@Composable
@ReadOnlyComposable
fun wishlistAccentColor(accentKey: String): Color = when (accentKey) {
    WishlistAccents.ORANGE -> AppColors.orange
    WishlistAccents.GOLD -> AppColors.gold
    WishlistAccents.GREEN -> AppColors.green
    WishlistAccents.BLUE -> AppColors.blue
    WishlistAccents.PURPLE -> AppColors.purple
    else -> AppColors.red
}

/**
 * Il segno da solo, senza contorno.
 *
 * [accent] tinge solo le icone Material: le ball hanno colori propri, e
 * ricolorarle le renderebbe di nuovo dei simboli da imparare invece che degli
 * oggetti da riconoscere.
 */
@Composable
fun WishlistGlyph(
    iconKey: String,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    val key = WishlistIcons.normalize(iconKey)
    val ball = ballStyle(key)
    if (ball != null) {
        Canvas(modifier = modifier.size(size)) { drawBall(ball) }
    } else {
        Icon(
            imageVector = materialGlyph(key),
            contentDescription = null,
            tint = accent,
            modifier = modifier.size(size)
        )
    }
}

/**
 * Il segno dentro la sua pastiglia colorata: e' cosi' che una lista si
 * riconosce in fila con le altre.
 */
@Composable
fun WishlistBadge(
    iconKey: String,
    accentKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val accent = wishlistAccentColor(accentKey)
    Box(
        modifier = modifier
            .size(size)
            .background(accent.copy(alpha = 0.18f), CircleShape)
            .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        WishlistGlyph(iconKey = iconKey, accent = accent, size = size * 0.52f)
    }
}

/**
 * La ball.
 *
 * Tutto e' in frazioni del raggio, cosi' lo stesso disegno regge sia a 16dp in
 * un chip sia a 40dp nell'intestazione del dettaglio. Il contorno si disegna per
 * ultimo perche' e' quello che tiene insieme la forma sul fondo chiaro, dove la
 * meta' inferiore e' quasi dello stesso colore dello sfondo.
 */
private fun DrawScope.drawBall(style: BallStyle) {
    val diameter = size.minDimension
    val radius = diameter / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val topLeft = Offset(center.x - radius, center.y - radius)
    val box = Size(diameter, diameter)

    drawArc(
        color = style.top,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = topLeft,
        size = box
    )
    drawArc(
        color = BallShell,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = topLeft,
        size = box
    )

    when (style.mark) {
        BallMark.STRIPES -> {
            val inset = radius * 0.28f
            val stripe = Stroke(width = radius * 0.30f, cap = StrokeCap.Round)
            listOf(196f, 306f).forEach { start ->
                drawArc(
                    color = style.markColor,
                    startAngle = start,
                    sweepAngle = 38f,
                    useCenter = false,
                    topLeft = Offset(topLeft.x + inset, topLeft.y + inset),
                    size = Size(diameter - inset * 2f, diameter - inset * 2f),
                    style = stripe
                )
            }
        }

        BallMark.STUDS -> {
            listOf(-1f, 1f).forEach { side ->
                drawCircle(
                    color = style.markColor,
                    radius = radius * 0.17f,
                    center = Offset(center.x + side * radius * 0.46f, center.y - radius * 0.44f)
                )
            }
        }

        BallMark.NONE -> Unit
    }

    drawRect(
        color = BallBand,
        topLeft = Offset(center.x - radius, center.y - radius * 0.13f),
        size = Size(diameter, radius * 0.26f)
    )
    drawCircle(
        color = BallBand,
        radius = radius * 0.92f,
        center = center,
        style = Stroke(width = radius * 0.16f)
    )
    drawCircle(color = BallBand, radius = radius * 0.30f, center = center)
    drawCircle(color = BallShell, radius = radius * 0.17f, center = center)
}
