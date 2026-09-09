package com.emabuia.pokevault.ui.deck

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
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
@Composable
fun DeckItem(
    deck: Deck,
    onClick: () -> Unit,
    allOwnedCards: List<PokemonCard>
) {
    val deckCardAnimation = rememberInfiniteTransition(label = "deckCardBackgroundAnimation")
    val sheenOffset by deckCardAnimation.animateFloat(
        initialValue = -220f,
        targetValue = 420f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "deckCardSheenOffset"
    )
    val glowAlpha by deckCardAnimation.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "deckCardGlowAlpha"
    )

    val coverUrls = remember(deck) { deck.displayCoverImageUrls() }
    val cardCounts = remember(deck.cards) { deck.cards.groupingBy { it }.eachCount() }
    val uniqueDeckCards = remember(deck.cards, allOwnedCards) {
        allOwnedCards.filter { it.id in cardCounts.keys }
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            if (coverUrls.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.16f))
                )
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverUrls.first())
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF10151F),
                                    Color(0xFF1C2D44),
                                    BlueCard.copy(alpha = 0.28f)
                                )
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF0E1A29).copy(alpha = 0.14f),
                                BlueCard.copy(alpha = 0.08f),
                                Color(0xFF0D131E).copy(alpha = 0.16f)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(translationX = sheenOffset)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .size(130.dp)
                    .align(Alignment.TopStart)
                    .graphicsLayer(
                        translationX = sheenOffset * 0.45f,
                        translationY = 12f
                    )
                    .background(
                        color = BlueCard.copy(alpha = glowAlpha),
                        shape = CircleShape
                    )
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

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                deck.mainTypes.take(2).forEach { type ->
                    TypeBadge(type, small = true)
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
                                Color.Black.copy(alpha = 0.18f),
                                Color.Black.copy(alpha = 0.46f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (coverUrls.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        coverUrls.forEach { coverUrl ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(coverUrl)
                                    .size(120, 168)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(30.dp, 42.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.65f)), RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
                Text(
                    text = deck.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$pokemonCount Pokémon • $trainerCount Trainer • $energyCount Energy",
                    color = BlueCard,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TypeBadge(type: String, small: Boolean = false) {
    val emoji = when (type.lowercase()) {
        "fuoco", "fire" -> "🔥"
        "acqua", "water" -> "💧"
        "lampo", "elettro", "lightning" -> "⚡"
        "psico", "psychic" -> "🔮"
        "erba", "grass" -> "🌿"
        "lotta", "fighting" -> "👊"
        "oscurità", "darkness" -> "🌙"
        "metallo", "metal" -> "⚙️"
        "folletto", "fairy" -> "✨"
        "drago", "dragon" -> "🐲"
        "incolore", "normale", "colorless" -> "⚪"
        else -> "🔘"
    }
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier.size(if (small) 24.dp else 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = if (small) 12.sp else 14.sp)
        }
    }
}

