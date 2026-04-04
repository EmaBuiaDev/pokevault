package com.example.pokevault.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.ui.theme.*
import com.example.pokevault.util.AppLocale

@Composable
fun CollectionSection(
    cards: List<PokemonCard>,
    onCardClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(top = 24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppLocale.collection,
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = AppLocale.cardsCount(cards.size),
                color = TextMuted,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (cards.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = AppLocale.noCardsFound,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(cards.take(20)) { card ->
                    PokemonCardItem(
                        card = card,
                        onClick = { onCardClick(card.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun PokemonCardItem(
    card: PokemonCard,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (card.imageUrl.isNotBlank()) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .width(120.dp)
                    .height(168.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
            // Fallback emoji se non c'è immagine
            val typeColor = when (card.type.lowercase()) {
                "fuoco", "fire" -> RedCard
                "acqua", "water" -> BlueCard
                "elettro", "lightning" -> YellowCard
                "psico", "psychic" -> PurpleCard
                "erba", "grass" -> GreenCard
                else -> DarkCard
            }
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(168.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(typeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (card.type.lowercase()) {
                        "fuoco", "fire" -> "🔥"
                        "acqua", "water" -> "💧"
                        "elettro", "lightning" -> "⚡"
                        "psico", "psychic" -> "🔮"
                        "erba", "grass" -> "🌿"
                        else -> "🎴"
                    },
                    fontSize = 40.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = card.name,
            color = TextWhite,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
