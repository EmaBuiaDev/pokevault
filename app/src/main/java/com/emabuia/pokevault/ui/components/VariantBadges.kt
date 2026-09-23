package com.emabuia.pokevault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.util.AppLocale

/**
 * Quale stampa di una carta si ha in collezione.
 *
 * Prima la griglia diceva solo "ce l'hai" con una spunta verde, e per sapere
 * se fosse la normale o la reverse bisognava aprire la carta. Qui la stessa
 * informazione sta in una lettera: N normale, R reverse, H holo.
 */
object CardVariants {

    /** Una lettera, due per le prime edizioni: sulla miniatura non c'e' spazio. */
    fun shortLabel(variant: String): String = when (variant) {
        "Normal" -> "N"
        "Reverse" -> "R"
        "Holo" -> "H"
        "1st Edition" -> "1ª"
        "1st Edition Holo" -> "1ªH"
        "Unlimited Holo" -> "UH"
        else -> variant.take(1).uppercase()
    }

    /** Il nome per esteso, tradotto, per i posti dove lo spazio c'e'. */
    fun label(variant: String): String {
        val isIt = AppLocale.isItalian
        return when (variant) {
            "Normal" -> if (isIt) "Normale" else "Normal"
            "Reverse" -> "Reverse"
            "Holo" -> "Holo"
            "1st Edition" -> if (isIt) "1ª Edizione" else "1st Edition"
            "1st Edition Holo" -> if (isIt) "1ª Ed. Holo" else "1st Ed. Holo"
            "Unlimited Holo" -> if (isIt) "Unlimited Holo" else "Unlimited Holo"
            else -> variant
        }
    }

    /**
     * Un colore per stampa, cosi' la lettera si riconosce senza leggerla:
     * la reverse tira all'argento-azzurro e l'holo all'oro, come in mano.
     */
    fun color(variant: String): Color = when (variant) {
        "Normal" -> Color(0xFF94A3B8)
        "Reverse" -> Color(0xFF60A5FA)
        "Holo" -> Color(0xFFEAB308)
        "1st Edition", "1st Edition Holo" -> Color(0xFFA855F7)
        else -> Color(0xFF94A3B8)
    }

    /** L'ordine in cui si mostrano, sempre lo stesso a prescindere dai dati. */
    private val ORDER = listOf("Normal", "Reverse", "Holo", "1st Edition", "1st Edition Holo", "Unlimited Holo")

    /** Posizione di una stampa nell'ordine; le sconosciute vanno in fondo. */
    fun order(variant: String): Int = ORDER.indexOf(variant).takeIf { it >= 0 } ?: ORDER.size

    fun sorted(variants: Collection<String>): List<String> =
        variants.distinct().sortedBy(::order)
}

/**
 * Le stampe possedute, in fila sulla miniatura.
 *
 * Una pastiglia piena per stampa, con la lettera in negativo e un bordo chiaro:
 * sta sopra un'illustrazione qualunque, e il solo colore del testo non bastava
 * a garantirne la lettura.
 */
@Composable
fun OwnedVariantBadges(
    variants: Set<String>,
    modifier: Modifier = Modifier,
    size: Int = 16,
    fontSize: Int = 9
) {
    if (variants.isEmpty()) return
    // L'ordinamento alloca una lista: senza `remember` lo rifarebbe ogni volta
    // che la cella si ricompone -- e in una griglia da sessanta carte le celle
    // si ricompongono a ogni prezzo che arriva.
    val ordered = remember(variants) { CardVariants.sorted(variants) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ordered.forEach { variant ->
            val tint = CardVariants.color(variant)
            // Pastiglia piena del colore della stampa, lettera scura sopra e
            // un filo di bordo chiaro: le lettere colorate su fondo scuro si
            // perdevano sulle illustrazioni chiare, e a otto punti erano
            // piccole da leggere di sfuggita.
            Box(
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape)
                    .background(tint)
                    .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = CardVariants.shortLabel(variant),
                    color = Color.Black.copy(alpha = 0.82f),
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    // Senza questo la lettera resta alta nel cerchio: il Text
                    // porta con se' lo spazio sopra e sotto previsto dal font,
                    // che il centraggio del Box conta come parte del testo.
                    // Stesso rimedio del contatore delle copie in collezione.
                    style = TextStyle(
                        lineHeight = fontSize.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}

/**
 * Una stampa fra cui scegliere nel quick add.
 *
 * Quella gia' in collezione si vede: resta scelibile -- una seconda copia e'
 * legittima -- ma si presenta spenta e col segno di spunta, invece di
 * sembrare identica a una che manca.
 */
@Composable
fun VariantChoiceChip(
    variant: String,
    alreadyOwned: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = CardVariants.color(variant)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (alreadyOwned) Color.Black.copy(alpha = 0.45f) else tint.copy(alpha = 0.9f))
            .border(1.dp, tint.copy(alpha = if (alreadyOwned) 0.5f else 0f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (alreadyOwned) "✓ ${CardVariants.label(variant)}" else CardVariants.label(variant),
            color = if (alreadyOwned) tint else Color.Black.copy(alpha = 0.85f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
