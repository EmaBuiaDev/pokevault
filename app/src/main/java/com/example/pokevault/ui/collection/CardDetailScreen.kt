package com.example.pokevault.ui.collection

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pokevault.data.firebase.FirestoreRepository
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    cardId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val repository = remember { FirestoreRepository() }
    var variants by remember { mutableStateOf<List<PokemonCard>>(emptyList()) }
    var editedQuantities by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedVariantIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun loadData() {
        scope.launch {
            repository.getCard(cardId).onSuccess { initialCard ->
                val apiId = initialCard.apiCardId
                if (apiId.isNotBlank()) {
                    repository.getCards().first().let { allCards ->
                        val found = allCards.filter { it.apiCardId == apiId }
                        variants = found
                        editedQuantities = found.associate { it.id to it.quantity }
                    }
                } else {
                    variants = listOf(initialCard)
                    editedQuantities = mapOf(initialCard.id to initialCard.quantity)
                }
            }.onFailure {
                repository.getCards().first().let { allCards ->
                    val found = allCards.filter { it.apiCardId == cardId || it.id == cardId }
                    variants = found
                    editedQuantities = found.associate { it.id to it.quantity }
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(cardId) {
        loadData()
    }

    fun confirmVariantChange(card: PokemonCard, newQty: Int) {
        scope.launch {
            if (newQty <= 0) {
                repository.deleteCard(card.id).onSuccess {
                    val remaining = variants.filter { it.id != card.id }
                    if (remaining.isEmpty()) onBack() else loadData()
                }
            } else {
                repository.updateCard(card.id, card.copy(quantity = newQty)).onSuccess {
                    loadData()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(variants.firstOrNull()?.name ?: "Dettaglio", fontWeight = FontWeight.Bold, color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = TextWhite)
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
        } else if (variants.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Carta non trovata", color = TextGray)
            }
        } else {
            val currentCard = variants.getOrNull(selectedVariantIndex) ?: variants.first()
            val totalQty = variants.sumOf { editedQuantities[it.id] ?: it.quantity }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Immagine Carta ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .aspectRatio(0.71f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                ) {
                    AsyncImage(
                        model = currentCard.imageUrl,
                        contentDescription = currentCard.name,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(BlueCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("x$totalQty", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Varianti in tuo possesso:",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )

                variants.forEachIndexed { index, variant ->
                    val editedQty = editedQuantities[variant.id] ?: variant.quantity
                    VariantRow(
                        variant = variant,
                        editedQuantity = editedQty,
                        isSelected = selectedVariantIndex == index,
                        onClick = { selectedVariantIndex = index },
                        onQtyChange = { newQty ->
                            if (newQty >= 0) {
                                editedQuantities = editedQuantities.toMutableMap().apply { put(variant.id, newQty) }
                            }
                        },
                        onConfirm = { confirmVariantChange(variant, editedQty) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                DetailSection(title = "Dettagli ${currentCard.variant}") {
                    DetailRow("Condizione", currentCard.condition)
                    DetailRow("Lingua", currentCard.language)
                    DetailRow("Valore stimato", "€${"%.2f".format(currentCard.estimatedValue)}")
                    if (currentCard.isGraded) {
                        DetailRow("Grading", "${currentCard.gradingCompany} ${currentCard.grade}")
                    }
                    if (currentCard.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(currentCard.notes, color = TextGray, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun VariantRow(
    variant: PokemonCard,
    editedQuantity: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onQtyChange: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    val isChanged = editedQuantity != variant.quantity

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) BlueCard.copy(alpha = 0.2f) else DarkCard)
            .border(
                width = 1.dp,
                color = if (isSelected) BlueCard else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = variant.variant,
                color = if (isSelected) BlueCard else TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = "${variant.condition} · ${variant.language}",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = { if (editedQuantity > 0) onQtyChange(editedQuantity - 1) }, 
                modifier = Modifier.size(28.dp),
                enabled = editedQuantity > 0
            ) {
                Icon(
                    imageVector = if (editedQuantity <= 1) Icons.Default.Delete else Icons.Default.Remove, 
                    contentDescription = null, 
                    tint = if (editedQuantity <= 1) RedCard.copy(alpha = if(editedQuantity > 0) 1f else 0.3f) else TextWhite, 
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Text(
                text = "x$editedQuantity",
                color = if (editedQuantity == 0) RedCard else TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.widthIn(min = 20.dp),
                textAlign = TextAlign.Center
            )
            
            IconButton(onClick = { onQtyChange(editedQuantity + 1) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, null, tint = BlueCard, modifier = Modifier.size(16.dp))
            }

            // Tasto Conferma Piccolo e Moderno
            AnimatedVisibility(
                visible = isChanged,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                IconButton(
                    onClick = onConfirm,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(26.dp)
                        .background(if (editedQuantity == 0) RedCard else GreenCard, CircleShape)
                ) {
                    Icon(
                        imageVector = if (editedQuantity == 0) Icons.Default.DeleteForever else Icons.Default.Check,
                        contentDescription = "Conferma",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DarkCard).padding(16.dp)
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

fun getTypeEmojiForCollection(type: String): String {
    return when (type.lowercase()) {
        "fire", "fuoco" -> "🔥"
        "water", "acqua" -> "💧"
        "grass", "erba" -> "🌿"
        "lightning", "elettro" -> "⚡"
        "psychic", "psico" -> "🔮"
        "fighting", "lotta" -> "👊"
        "darkness", "buio" -> "🌑"
        "metal", "metallo" -> "⚙️"
        "dragon", "drago" -> "🐉"
        "fairy", "folletto" -> "🧚"
        "colorless" -> "⭐"
        else -> "🎴"
    }
}
