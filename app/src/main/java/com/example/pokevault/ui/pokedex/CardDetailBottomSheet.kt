package com.example.pokevault.ui.pokedex

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.pokevault.data.remote.TcgCard
import com.example.pokevault.ui.theme.*

@Composable
fun CardDetailBottomSheet(
    card: TcgCard,
    isOwned: Boolean,
    isLoading: Boolean,
    onToggleOwned: () -> Unit,
    onDismiss: () -> Unit
) {
    val rarityInfo = getRarityInfo(card.rarity)
    val price = card.cardmarket?.prices?.averageSellPrice
        ?: card.tcgplayer?.prices?.values?.firstOrNull()?.market

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(DarkBackground)
                    .clickable(enabled = false, onClick = {}) // Blocca click passthrough
                    .padding(top = 12.dp)
            ) {
                // ── Handle ──
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextMuted.copy(alpha = 0.3f))
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    // ── Header: nome, numero, set ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.name,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Tipo badge
                                if (card.types != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(getTypeColorForTcg(card.types.firstOrNull()))
                                    )
                                }
                                Text(
                                    text = "#${card.number}",
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                                if (card.set != null) {
                                    Text(
                                        text = "·",
                                        color = TextMuted,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = card.set.name,
                                        color = TextGray,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        // Azioni: preferiti, salva
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(DarkCard)
                            ) {
                                Icon(
                                    Icons.Default.FavoriteBorder,
                                    contentDescription = "Preferita",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(DarkCard)
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Condividi",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Immagine carta grande ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = card.images.large,
                            contentDescription = card.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Info pills ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Rarità
                        InfoPill(
                            icon = rarityInfo.emoji,
                            text = card.rarity ?: "Sconosciuto",
                            color = rarityInfo.color
                        )

                        // Prezzo
                        if (price != null && price > 0) {
                            InfoPill(
                                icon = "ℹ",
                                text = "${"%.2f".format(price)} €",
                                color = GreenCard
                            )
                        }

                        // Cardmarket link
                        if (card.cardmarket != null) {
                            InfoPill(
                                icon = "🛒",
                                text = "cardmarket",
                                color = BlueCard
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Sezione Collezione ──
                    Text(
                        text = "Collezione",
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Status box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkCard)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Info stato
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Bandiera (placeholder IT)
                            Text(text = "🇮🇹", fontSize = 20.sp)

                            Column {
                                Text(
                                    text = if (isOwned) "Normal" else "Non posseduta",
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isOwned) "Mint" else "Tocca + per aggiungere",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Azioni
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isOwned) {
                                // Bottone elimina
                                IconButton(
                                    onClick = onToggleOwned,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RedCard.copy(alpha = 0.15f))
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = RedCard,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Rimuovi",
                                            tint = RedCard,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Quantità
                                Text(
                                    text = "1",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }

                            // Bottone aggiungi
                            IconButton(
                                onClick = { if (!isOwned) onToggleOwned() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isOwned) GreenCard.copy(alpha = 0.15f)
                                        else BlueCard
                                    )
                            ) {
                                if (isLoading && !isOwned) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        if (isOwned) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = "Aggiungi",
                                        tint = if (isOwned) GreenCard else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ── Dettagli carta ──
                    if (card.hp != null || card.types != null || card.subtypes != null) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Dettagli",
                            color = TextWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkCard)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (card.supertype.isNotBlank()) {
                                DetailInfoRow("Supertipo", card.supertype)
                            }
                            if (card.subtypes != null) {
                                DetailInfoRow("Sottotipo", card.subtypes.joinToString(", "))
                            }
                            if (card.hp != null) {
                                DetailInfoRow("HP", card.hp)
                            }
                            if (card.types != null) {
                                DetailInfoRow("Tipo", card.types.joinToString(", "))
                            }
                            if (card.rarity != null) {
                                DetailInfoRow("Rarità", card.rarity)
                            }
                            DetailInfoRow("Numero", "#${card.number}")
                            if (card.set != null) {
                                DetailInfoRow("Set", card.set.name)
                                DetailInfoRow("Serie", card.set.series)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun InfoPill(
    icon: String,
    text: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = icon, fontSize = 12.sp)
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextMuted, fontSize = 13.sp)
        Text(
            text = value,
            color = TextWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

fun getTypeColorForTcg(type: String?): Color {
    return when (type?.lowercase()) {
        "fire" -> Color(0xFFEF4444)
        "water" -> Color(0xFF3B82F6)
        "grass" -> Color(0xFF22C55E)
        "lightning" -> Color(0xFFEAB308)
        "psychic" -> Color(0xFF8B5CF6)
        "fighting" -> Color(0xFFF97316)
        "darkness" -> Color(0xFF6366F1)
        "metal" -> Color(0xFF6B7280)
        "dragon" -> Color(0xFF7C3AED)
        "fairy" -> Color(0xFFEC4899)
        "colorless" -> Color(0xFF9CA3AF)
        else -> Color(0xFF6B7280)
    }
}
