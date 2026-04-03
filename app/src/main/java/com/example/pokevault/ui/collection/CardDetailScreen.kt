package com.example.pokevault.ui.collection

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
    var editedVariants by remember { mutableStateOf<List<PokemonCard>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var selectedVariantIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cardId) {
        repository.getCard(cardId).onSuccess { initialCard ->
            val apiId = initialCard.apiCardId
            if (apiId.isNotBlank()) {
                repository.getCards().first().let { allCards ->
                    val found = allCards.filter { it.apiCardId == apiId }
                    variants = found
                    editedVariants = found
                }
            } else {
                variants = listOf(initialCard)
                editedVariants = listOf(initialCard)
            }
        }.onFailure {
            repository.getCards().first().let { allCards ->
                val found = allCards.filter { it.apiCardId == cardId || it.id == cardId }
                variants = found
                editedVariants = found
            }
        }
        isLoading = false
    }

    fun saveChanges() {
        scope.launch {
            isSaving = true
            editedVariants.forEach { card ->
                repository.updateCard(card.id, card)
            }
            isSaving = false
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(editedVariants.firstOrNull()?.name ?: "Dettaglio", fontWeight = FontWeight.Bold, color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        bottomBar = {
            if (editedVariants != variants && !isLoading) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = DarkSurface,
                    tonalElevation = 8.dp
                ) {
                    Button(
                        onClick = { saveChanges() },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueCard),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("CONFERMA MODIFICHE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BlueCard)
            }
        } else if (editedVariants.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Carta non trovata", color = TextGray)
            }
        } else {
            val currentCard = editedVariants[selectedVariantIndex]
            val totalQty = editedVariants.sumOf { it.quantity }

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

                editedVariants.forEachIndexed { index, variant ->
                    VariantRow(
                        variant = variant,
                        isSelected = selectedVariantIndex == index,
                        onClick = { selectedVariantIndex = index },
                        onIncrement = {
                            editedVariants = editedVariants.toMutableList().apply {
                                this[index] = variant.copy(quantity = variant.quantity + 1)
                            }
                        },
                        onDecrement = {
                            if (variant.quantity > 1) {
                                editedVariants = editedVariants.toMutableList().apply {
                                    this[index] = variant.copy(quantity = variant.quantity - 1)
                                }
                            }
                        }
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
    isSelected: Boolean,
    onClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
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
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onDecrement, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Remove, null, tint = if (variant.quantity > 1) TextWhite else TextMuted, modifier = Modifier.size(18.dp))
            }
            Text(
                text = "x${variant.quantity}",
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.widthIn(min = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = onIncrement, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Add, null, tint = BlueCard, modifier = Modifier.size(18.dp))
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
