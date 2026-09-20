package com.emabuia.pokevault.ui.deck

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.PokemonSpriteResolver
@Composable
fun DeckItem(
    deck: Deck,
    onClick: () -> Unit,
    ownedById: Map<String, PokemonCard>
) {
    val cardCounts = remember(deck.cards) { deck.cards.groupingBy { it }.eachCount() }
    // Indice precalcolato dal chiamante: prima ogni riga della lista filtrava
    // l'intera collezione posseduta, quindi il costo cresceva con
    // (numero di mazzi x carte possedute) a ogni ricomposizione.
    val uniqueDeckCards = remember(cardCounts, ownedById) {
        cardCounts.keys.mapNotNull { ownedById[it] }
    }

    fun classifyForDeckSections(card: PokemonCard): String {
        val supertype = card.supertype.lowercase()
        val type = card.type.lowercase()
        val name = card.name.lowercase()
        val subtypes = card.subtypes.map { it.lowercase() }

        val hasEnergyMarker =
            supertype.contains("energy") ||
                supertype.contains("energ") ||
                type.contains("energy") ||
                type.contains("energia") ||
                subtypes.any { it.contains("energy") || it.contains("energia") } ||
                name.contains("energy") ||
                name.contains("energia")
        if (hasEnergyMarker) return "Energy"

        val hasTrainerMarker =
            supertype.contains("trainer") ||
                supertype.contains("allenat") ||
                supertype.contains("aiuto") ||
                type.contains("trainer") ||
                type.contains("supporter") ||
                type.contains("item") ||
                type.contains("stadium") ||
                type.contains("tool") ||
                type.contains("allenat") ||
                type.contains("aiuto") ||
                type.contains("stadio") ||
                type.contains("strumento") ||
                subtypes.any {
                    it == "item" ||
                        it == "stadium" ||
                        it == "supporter" ||
                        it == "tool" ||
                        it == "strumento" ||
                        it == "stadio" ||
                        it == "aiuto"
                }

        val hasPokemonSubtypeMarker = subtypes.any {
            it == "basic" ||
                it == "stage 1" ||
                it == "stage 2" ||
                it == "baby" ||
                it == "ex" ||
                it == "v" ||
                it == "vmax" ||
                it == "vstar"
        }
        val hasPokemonTypeMarker =
            type in listOf(
                "grass", "fire", "water", "lightning", "electric", "fighting",
                "psychic", "darkness", "metal", "dragon", "fairy"
            )
        val hasStrongPokemonMarker =
            card.hp > 0 ||
                hasPokemonSubtypeMarker ||
                hasPokemonTypeMarker
        val hasExplicitPokemonSupertype = supertype.contains("pok")

        if (hasTrainerMarker && !hasStrongPokemonMarker) return "Trainer"
        if (hasStrongPokemonMarker) return "Pokémon"
        if (hasExplicitPokemonSupertype && !hasTrainerMarker && type != "colorless") return "Pokémon"

        return "Trainer"
    }
    
    val pokemonCount = remember(uniqueDeckCards, cardCounts) { 
        uniqueDeckCards.filter { classifyForDeckSections(it) == "Pokémon" }.sumOf { cardCounts[it.id] ?: 0 } 
    }
    val trainerCount = remember(uniqueDeckCards, cardCounts) { 
        uniqueDeckCards.filter { classifyForDeckSections(it) == "Trainer" }.sumOf { cardCounts[it.id] ?: 0 } 
    }
    val energyCount = remember(uniqueDeckCards, cardCounts) {
        uniqueDeckCards.filter { classifyForDeckSections(it) == "Energy" }.sumOf { cardCounts[it.id] ?: 0 }
    }

    // I due Pokemon che danno il nome al mazzo, come si usa fare altrove. Il
    // criterio e' headlineScore, non il numero di copie: vedi il commento li'
    // sopra, contare le copie mostrava lo sprite della carta base.
    val context = LocalContext.current
    // isReady fra le chiavi: la tabella arriva da un thread di I/O, e senza
    // questa dipendenza le righe gia' composte resterebbero senza sprite.
    val spriteUrls = remember(uniqueDeckCards, cardCounts, deck.coverImageUrls, PokemonSpriteResolver.isReady) {
        // Tutti gli sprite che questo mazzo puo' mostrare. Serve intero anche
        // quando la scelta e' manuale: una copertina che punta a un Pokemon
        // tolto dal mazzo va ignorata, non disegnata.
        val available = uniqueDeckCards
            .filter { classifyForDeckSections(it) == "Pokémon" }
            .sortedByDescending { headlineScore(it, cardCounts[it.id] ?: 0) }
            .mapNotNull { PokemonSpriteResolver.spriteUrlForCardName(context, it.name) }
            .distinct()

        deck.chosenSpriteCovers().filter { it in available }.ifEmpty { available }.take(2)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            // Lo sfondo prende il colore del tipo principale del mazzo: fermo,
            // ma non uguale per tutti. Prima qui c'erano una scia luminosa e
            // un cerchio blu che traslavano in continuazione -- due animazioni
            // infinite per ogni riga visibile, che ridisegnavano a ogni frame
            // finche' l'elenco era a schermo, e in cambio davano un movimento
            // che non raccontava niente del mazzo.
            val accent = TypeColors.of(normalizeTypeKey(deck.mainTypes.firstOrNull().orEmpty()))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF10151F),
                                Color(0xFF1C2D44),
                                accent.copy(alpha = 0.30f)
                            )
                        )
                    )
            )

            // Il fondo si schiarisce verso destra, dove stanno gli sprite,
            // cosi' si staccano invece di galleggiare. Gradiente orizzontale e
            // non radiale: il radiale vuole centro e raggio in pixel, e qui
            // servirebbe conoscere la dimensione per scriverli.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.55f to accent.copy(alpha = 0.10f),
                            1f to accent.copy(alpha = 0.26f)
                        )
                    )
            )

            // Una banda sottile sul bordo sinistro nel colore del tipo: da'
            // alla riga un punto fermo da cui inizia a leggersi.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .align(Alignment.CenterStart)
                    .background(accent.copy(alpha = 0.85f))
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.16f),
                                Color.Black.copy(alpha = 0.62f)
                            )
                        )
                    )
            )

            // Gli sprite dei Pokemon piu' giocati, a destra e dietro al
            // riquadro delle informazioni: sono l'identita' del mazzo, e
            // devono farsi riconoscere prima di essere letti.
            if (spriteUrls.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy((-12).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    spriteUrls.forEach { url ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(url)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(70.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.30f),
                                Color.Black.copy(alpha = 0.62f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // "Deck di prova" sta accanto al nome, non sospeso in un
                // angolo: e' una cosa che si dice del mazzo, e si legge
                // insieme a come si chiama.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = deck.name,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (deck.deckOnly) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = AppColors.purple.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(5.dp)
                        ) {
                            Text(
                                text = AppLocale.deckTestBadge,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "$pokemonCount Pokémon • $trainerCount Trainer • $energyCount Energy",
                    color = AppColors.blue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                // Quante carte ha il mazzo rispetto alle 60 che ne fanno uno
                // legale. Era un'informazione che l'elenco non dava affatto:
                // per sapere se un deck era finito bisognava aprirlo.
                Spacer(modifier = Modifier.height(6.dp))
                DeckSizeBar(cardCount = deck.cards.size)
            }
        }
    }
}

/**
 * Il riempimento del mazzo verso le 60 carte.
 *
 * Verde a 60 e non a "il piu' possibile": 60 non e' un massimo da avvicinare
 * ma il numero esatto che rende un mazzo giocabile, e un deck da 59 e' rotto
 * quanto uno da 61.
 */
@Composable
private fun DeckSizeBar(cardCount: Int) {
    val legal = cardCount == LEGAL_DECK_SIZE
    val accent = if (legal) AppColors.green else AppColors.textMuted

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(
                        (cardCount.toFloat() / LEGAL_DECK_SIZE).coerceIn(0f, 1f)
                    )
                    .clip(CircleShape)
                    .background(accent)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "$cardCount/$LEGAL_DECK_SIZE",
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Le carte di un mazzo legale. Vedi anche HandSimulatorDeckMapping. */
private const val LEGAL_DECK_SIZE = 60

/** Le carte con una "rule box": ex, V, VMAX, VSTAR, GX. */
private val RULE_BOX = Regex("\\b(ex|v|vmax|vstar|gx)\\b", RegexOption.IGNORE_CASE)

/**
 * Quanto una carta rappresenta il mazzo.
 *
 * Non basta contare le copie: una linea evolutiva ne ha quattro di base e due
 * o tre dello stadio finale, quindi ordinando per quantita' il mazzo
 * "Charizard ex" si presentava con lo sprite di Charmander. Quello che da' il
 * nome al deck e' la carta con la rule box, e a parita' lo stadio piu' alto --
 * che e' anche il criterio con cui questi mazzi vengono chiamati in giro.
 *
 * Lo stadio arriva da `subtypes`, riempito dal catalogo: puo' essere in
 * inglese o in italiano a seconda di quando la carta e' entrata in collezione,
 * quindi si guardano tutte e due le forme.
 */
internal fun headlineScore(card: PokemonCard, copies: Int): Int {
    var score = copies
    if (RULE_BOX.containsMatchIn(card.name)) score += 100

    val stage = card.subtypes.joinToString(" ").lowercase()
    score += when {
        "stage 2" in stage || "fase 2" in stage -> 30
        "stage 1" in stage || "fase 1" in stage -> 15
        else -> 0
    }
    return score
}

/**
 * TypeColors ragiona in inglese, le carte non sempre.
 *
 * Una carta importata da una decklist inglese ha "Darkness", una presa dal
 * catalogo italiano puo' avere "Oscurita'": senza questa traduzione la seconda
 * cadeva sul colore di ripiego, cioe' grigio, e due mazzi diversi finivano con
 * la stessa pastiglia.
 */
internal fun normalizeTypeKey(type: String): String = when (type.lowercase().trim()) {
    "fuoco" -> "fire"
    "acqua" -> "water"
    "erba" -> "grass"
    "lampo", "elettro" -> "lightning"
    "psico" -> "psychic"
    "lotta" -> "fighting"
    "oscurità", "oscurita" -> "darkness"
    "metallo" -> "metal"
    "drago" -> "dragon"
    "folletto" -> "fairy"
    "incolore", "normale" -> "colorless"
    else -> type.lowercase().trim()
}

