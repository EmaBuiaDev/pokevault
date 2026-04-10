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
import com.example.pokevault.data.model.CardOptions
import com.example.pokevault.data.remote.TcgCard
import com.example.pokevault.ui.theme.*
import com.example.pokevault.util.RarityUtils.getRarityInfo

@Composable
fun CardDetailBottomSheet(
    card: TcgCard,
    isOwned: Boolean,
    isLoading: Boolean,
    onAddCard: (variant: String, quantity: Int, condition: String, language: String) -> Unit,
    onRemoveCard: () -> Unit,
    onDismiss: () -> Unit
) {
    val rarityInfo = getRarityInfo(card.rarity)

    // Varianti disponibili (API + fallback per rarità)
    val availableVariants = remember(card) {
        CardOptions.getVariantsForCard(card.tcgplayer?.prices?.keys ?: emptySet(), card.rarity)
    }

    // State form
    var selectedVariant by remember { mutableStateOf(availableVariants.firstOrNull() ?: "Normal") }
    var quantity by remember { mutableIntStateOf(1) }
    var selectedCondition by remember { mutableStateOf("Near Mint") }
    var selectedLanguage by remember { mutableStateOf("🇮🇹 Italiano") }
    var showAddForm by remember { mutableStateOf(!isOwned) }

    // Prezzo per variante selezionata
    val variantKey = CardOptions.getVariantApiKey(selectedVariant)
    val price = card.tcgplayer?.prices?.get(variantKey)?.market
        ?: card.cardmarket?.prices?.lowPrice
        ?: card.cardmarket?.prices?.averageSellPrice

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
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(DarkBackground)
                    .clickable(enabled = false, onClick = {})
            ) {
                // ── Handle ──
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextMuted.copy(alpha = 0.3f))
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    // ── Header: nome, numero, set ──
                    Text(
                        text = card.name,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (card.types != null) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(getTypeColorForTcg(card.types.firstOrNull()))
                            )
                        }
                        Text("#${card.number}", color = TextMuted, fontSize = 13.sp)
                        if (card.set != null) {
                            Text("·", color = TextMuted, fontSize = 13.sp)
                            Text(card.set.name, color = TextGray, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Immagine carta ──
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        InfoPill(icon = "✦", text = card.rarity ?: "Sconosciuto", color = rarityInfo.color)
                        if (price != null && price > 0) {
                            InfoPill(icon = "💰", text = "${"%.2f".format(price)} €", color = GreenCard)
                        }
                        if (card.hp != null) {
                            InfoPill(icon = "❤️", text = "${card.hp} HP", color = RedCard)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Sezione stato possesso ──
                    if (isOwned && !showAddForm) {
                        // Carta già posseduta
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(GreenCard.copy(alpha = 0.1f))
                                .border(1.dp, GreenCard.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(GreenCard),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    Column {
                                        Text("Nella tua collezione", color = GreenCard, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        Text("Tocca per aggiungere un'altra copia", color = TextMuted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Aggiungi altra copia
                            Button(
                                onClick = { showAddForm = true },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BlueCard)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aggiungi copia", fontSize = 13.sp)
                            }
                            // Rimuovi
                            OutlinedButton(
                                onClick = { onRemoveCard(); onDismiss() },
                                modifier = Modifier.height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RedCard.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Delete, null, tint = RedCard, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // ── Form aggiunta ──
                    if (!isOwned || showAddForm) {
                        Text(
                            text = if (isOwned) "Aggiungi un'altra copia" else "Aggiungi alla collezione",
                            color = TextWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // ── Riga 1: Quantità + Condizione ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Quantità
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Quantità", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DarkCard)
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { if (quantity > 1) quantity-- },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurface)
                                    ) {
                                        Icon(Icons.Default.Remove, null, tint = TextWhite, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        text = "$quantity",
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    IconButton(
                                        onClick = { quantity++ },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(BlueCard)
                                    ) {
                                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            // Condizione
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Condizione", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                                OptionSelector(
                                    options = CardOptions.CONDITIONS,
                                    selected = selectedCondition,
                                    onSelect = { selectedCondition = it }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ── Riga 2: Lingua + Versione ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Lingua
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Lingua", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                                OptionSelector(
                                    options = CardOptions.LANGUAGES,
                                    selected = selectedLanguage,
                                    onSelect = { selectedLanguage = it }
                                )
                            }

                            // Versione/Variante
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Versione", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                                OptionSelector(
                                    options = availableVariants,
                                    selected = selectedVariant,
                                    onSelect = { selectedVariant = it }
                                )
                            }
                        }

                    }

                    // ── Dettagli carta ──
                    Text("Dettagli", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkCard)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (card.supertype.isNotBlank()) DetailInfoRow("Supertipo", card.supertype)
                        if (card.subtypes != null) DetailInfoRow("Sottotipo", card.subtypes.joinToString(", "))
                        if (card.hp != null) DetailInfoRow("HP", card.hp)
                        if (card.types != null) DetailInfoRow("Tipo", card.types.joinToString(", "))
                        if (card.rarity != null) DetailInfoRow("Rarità", card.rarity)
                        DetailInfoRow("Numero", "#${card.number}")
                        if (card.set != null) {
                            DetailInfoRow("Set", card.set.name)
                            DetailInfoRow("Serie", card.set.series)
                        }

                        // Varianti disponibili con prezzi
                        val variants = card.tcgplayer?.prices
                        if (variants != null && variants.isNotEmpty()) {
                            HorizontalDivider(color = TextMuted.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))
                            Text("Prezzi per variante", color = TextWhite, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            variants.forEach { (key, priceInfo) ->
                                val variantName = when (key) {
                                    "normal" -> "Normal"
                                    "holofoil" -> "Holofoil"
                                    "reverseHolofoil" -> "Reverse Holo"
                                    "1stEditionHolofoil" -> "1st Ed. Holo"
                                    "1stEditionNormal" -> "1st Edition"
                                    else -> key
                                }
                                val mkt = priceInfo.market
                                if (mkt != null && mkt > 0) {
                                    DetailInfoRow(variantName, "${"%.2f".format(mkt)} €")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }

                // ── Bottoni fissi in basso ──
                if (!isOwned || showAddForm) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBackground)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Annulla
                        OutlinedButton(
                            onClick = {
                                if (isOwned) showAddForm = false
                                else onDismiss()
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TextMuted.copy(alpha = 0.3f))
                        ) {
                            Text("Annulla", color = TextGray, fontWeight = FontWeight.Medium)
                        }

                        // Aggiungi
                        Button(
                            onClick = {
                                onAddCard(selectedVariant, quantity, selectedCondition, selectedLanguage)
                                onDismiss()
                            },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueCard)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aggiungi", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Dropdown compatto ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkCard)
                .menuAnchor()
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selected,
                    color = TextWhite,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DarkSurface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            color = if (option == selected) BlueCard else TextWhite,
                            fontSize = 13.sp,
                            fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = { onSelect(option); expanded = false },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun InfoPill(icon: String, text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = icon, fontSize = 12.sp)
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextMuted, fontSize = 13.sp)
        Text(text = value, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
