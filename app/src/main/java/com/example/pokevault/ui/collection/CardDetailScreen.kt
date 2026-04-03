package com.example.pokevault.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.ui.theme.*
import kotlinx.coroutines.launch

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

    LaunchedEffect(cardId) {
        repository.getCard(cardId)
            .onSuccess { card = it }
            .onFailure { /* gestisci errore */ }
        isLoading = false
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card?.name ?: "Dettaglio", fontWeight = FontWeight.Bold, color = TextWhite) },
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
        },
        containerColor = DarkBackground
    ) { padding ->
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Visualizzazione Carta Reale ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(0.71f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                ) {
                    if (c.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = c.imageUrl,
                            contentDescription = c.name,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(getTypeEmojiForCollection(c.type), fontSize = 64.sp)
                                Text("Immagine non disponibile", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }

                    // Badge Quantità Overlay
                    if (c.quantity > 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BlueCard)
                                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("x${c.quantity}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Info Principali (Variante e Tipo) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VariantBadge(
                        label = "Variante",
                        value = c.variant,
                        color = PurpleCard,
                        modifier = Modifier.weight(1f)
                    )
                    VariantBadge(
                        label = "Quantità",
                        value = "${c.quantity} pz",
                        color = BlueCard,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Dettagli Rapidi ──
                DetailSection(title = "Caratteristiche") {
                    DetailRow("Set", c.set)
                    DetailRow("Rarità", c.rarity)
                    DetailRow("Lingua", c.language)
                    DetailRow("Condizione", c.condition)
                    if (c.isGraded) {
                        DetailRow("Voto Grading", "⭐ ${c.grade}")
                        DetailRow("Ente", c.gradingCompany)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Valore di Mercato ──
                DetailSection(title = "Valutazione") {
                    DetailRow("Valore unitario", "€${"%.2f".format(c.estimatedValue)}")
                    if (c.quantity > 1) {
                        DetailRow("Valore totale", "€${"%.2f".format(c.estimatedValue * c.quantity)}")
                    }
                }

                if (c.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    DetailSection(title = "Note") {
                        Text(text = c.notes, color = TextGray, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun VariantBadge(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(text = value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(16.dp)
    ) {
        Text(text = title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextMuted, fontSize = 14.sp)
        Text(text = value, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
