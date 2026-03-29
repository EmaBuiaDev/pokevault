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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.ui.theme.*

@Composable
fun CollectionSection(
    cards: List<PokemonCard>,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(top = 24.dp)) {
        // Header con toggle Lista/Griglia
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Collezione",
                style = MaterialTheme.typography.headlineMedium
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCard)
            ) {
                ViewToggleButton(
                    text = "Lista",
                    isSelected = !isGridView,
                    onClick = { if (isGridView) onToggleView() }
                )
                ViewToggleButton(
                    text = "Griglia",
                    isSelected = isGridView,
                    onClick = { if (!isGridView) onToggleView() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cards orizzontali
        if (cards.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nessuna carta trovata.\nAggiungi la tua prima carta!",
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(cards) { card ->
                    PokemonCardItem(card = card)
                }
            }
        }
    }
}

@Composable
fun ViewToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = if (isSelected) TextWhite else TextMuted,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 13.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                if (isSelected) BlueCard.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun PokemonCardItem(
    card: PokemonCard,
    modifier: Modifier = Modifier
) {
    // Colore basato sul tipo
    val typeColor = when (card.type.lowercase()) {
        "fuoco" -> RedCard
        "acqua" -> BlueCard
        "elettro" -> YellowCard
        "psico" -> PurpleCard
        "erba" -> GreenCard
        else -> DarkCard
    }

    Box(
        modifier = modifier
            .width(120.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(typeColor.copy(alpha = 0.15f))
            .clickable { /* TODO: apri dettaglio carta */ }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Emoji placeholder (poi sostituire con immagine reale)
            Text(
                text = when (card.type.lowercase()) {
                    "fuoco" -> "🔥"
                    "acqua" -> "💧"
                    "elettro" -> "⚡"
                    "psico" -> "🔮"
                    "erba" -> "🌿"
                    else -> "🎴"
                },
                fontSize = 40.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = card.name,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Text(
                text = "${card.hp} HP",
                color = typeColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = card.rarity,
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}
