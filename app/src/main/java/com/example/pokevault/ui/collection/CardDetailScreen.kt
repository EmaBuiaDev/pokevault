package com.example.pokevault.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.ui.theme.*
import kotlinx.coroutines.launch
import com.example.pokevault.ui.collection.getTypeEmojiForCollection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    cardId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val repository = remember { FirestoreRepository() }
    var card by remember { mutableStateOf<PokemonCard?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Carica carta
    LaunchedEffect(cardId) {
        repository.getCard(cardId)
            .onSuccess { card = it }
            .onFailure { /* gestisci errore */ }
        isLoading = false
    }

    // Dialog elimina
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Elimina carta", color = TextWhite) },
            text = { Text("Sei sicuro di voler eliminare ${card?.name}?", color = TextGray) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.deleteCard(cardId)
                        showDeleteDialog = false
                        onBack()
                    }
                }) { Text("Elimina", color = RedCard) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annulla", color = TextGray)
                }
            },
            containerColor = DarkSurface
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = { Text(card?.name ?: "Dettaglio", fontWeight = FontWeight.SemiBold, color = TextWhite) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Indietro", tint = TextWhite)
                }
            },
            actions = {
                IconButton(onClick = { onEdit(cardId) }) {
                    Icon(Icons.Default.Edit, "Modifica", tint = BlueCard)
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, "Elimina", tint = RedCard)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BlueCard)
            }
        } else if (card == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Carta non trovata", color = TextGray)
            }
        } else {
            val c = card!!
            val typeColor = when (c.type.lowercase()) {
                "fuoco", "fire" -> RedCard
                "acqua", "water" -> BlueCard
                "erba", "grass" -> GreenCard
                "elettro", "lightning" -> YellowCard
                "psico", "psychic" -> PurpleCard
                else -> PurpleCard
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                // ── Card Preview Grande ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(typeColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = getTypeEmojiForCollection(c.type), fontSize = 72.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = c.name,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                        Text(
                            text = "${c.hp} HP",
                            color = typeColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Badges ──
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailBadge(text = c.type, color = typeColor)
                    DetailBadge(text = c.rarity, color = PurpleCard)
                    DetailBadge(text = c.condition, color = BlueCard)
                    if (c.isGraded) {
                        DetailBadge(text = "⭐ ${c.grade}", color = StarGold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Info Dettagliate ──
                DetailSection(title = "Informazioni") {
                    DetailRow("Set / Espansione", c.set.ifBlank { "-" })
                    DetailRow("Rarità", c.rarity)
                    DetailRow("Tipo", c.type)
                    DetailRow("HP", "${c.hp}")
                    DetailRow("Condizione", c.condition)
                    DetailRow("Quantità", "${c.quantity}")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Valore ──
                DetailSection(title = "Valore") {
                    DetailRow("Valore stimato", "€${"%.2f".format(c.estimatedValue)}")
                    if (c.quantity > 1) {
                        DetailRow(
                            "Valore totale",
                            "€${"%.2f".format(c.estimatedValue * c.quantity)}"
                        )
                    }
                }

                // ── Grading ──
                if (c.isGraded) {
                    Spacer(modifier = Modifier.height(16.dp))
                    DetailSection(title = "Certificazione") {
                        DetailRow("Gradata", "Sì")
                        DetailRow("Voto", "${c.grade ?: "-"}")
                    }
                }

                // ── Note ──
                if (c.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    DetailSection(title = "Note") {
                        Text(
                            text = c.notes,
                            color = TextGray,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun DetailBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = TextWhite,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextMuted, fontSize = 14.sp)
        Text(text = value, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
    fun getTypeColorForDetail(type: String): androidx.compose.ui.graphics.Color {
        return when (type.lowercase()) {
            "fuoco", "fire" -> RedCard
            "acqua", "water" -> BlueCard
            "erba", "grass" -> GreenCard
            "elettro", "lightning" -> YellowCard
            "psico", "psychic" -> PurpleCard
            "lotta", "fighting" -> androidx.compose.ui.graphics.Color(0xFFF97316)
            "buio", "darkness" -> androidx.compose.ui.graphics.Color(0xFF6366F1)
            "metallo", "metal" -> androidx.compose.ui.graphics.Color(0xFF6B7280)
            "drago", "dragon" -> androidx.compose.ui.graphics.Color(0xFF7C3AED)
            "folletto", "fairy" -> androidx.compose.ui.graphics.Color(0xFFEC4899)
            else -> androidx.compose.ui.graphics.Color(0xFF9CA3AF)
        }
    }
}
