package com.emabuia.pokevault.ui.deck

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.DeckAnalysis
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
@Composable
fun DeckDetailView(
    deck: Deck,
    allOwnedCards: List<PokemonCard>,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit
) {
    fun getCardKey(card: PokemonCard): String =
        card.apiCardId.ifEmpty { "${card.name}-${card.set}-${card.cardNumber}-${card.variant}" }

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

    val idToCard = remember(allOwnedCards) { allOwnedCards.associateBy { it.id } }
    
    val groupedCards = remember(deck.cards, idToCard) {
        deck.cards.mapNotNull { idToCard[it] }
            .groupBy { getCardKey(it) }
            .map { (_, instances) -> instances.first() to instances.size }
    }
    
    val cardsByCategory = remember(groupedCards) {
        listOf("Pokémon", "Trainer", "Energy").map { cat ->
            val filtered = groupedCards.filter { (card, _) ->
                classifyForDeckSections(card) == cat
            }
            cat to filtered
        }.filter { it.second.isNotEmpty() }
    }

    val deckAnalysis = remember(groupedCards) {
        val expanded = groupedCards.flatMap { (card, qty) -> List(qty) { card } }
        val typesCount = expanded.flatMap { it.type.split(",").map { t -> t.trim() } }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
        val supertypesCount = expanded.groupingBy { classifyForDeckSections(it) }.eachCount()
        val avgHp = expanded.filter { it.hp > 0 }.map { it.hp }
            .let { hpValues -> if (hpValues.isNotEmpty()) hpValues.average() else 0.0 }
        DeckAnalysis(
            typesCount = typesCount,
            averageHp = avgHp,
            supertypesCount = supertypesCount
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(AppColors.background)) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            if (deck.coverImageUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(deck.coverImageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent,
                                AppColors.background
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = AppColors.blue, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AppColors.green, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AppColors.red, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = AppColors.gold, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = deck.name,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Totale: ${deck.cards.size} carte",
                    color = AppColors.textMuted,
                    fontSize = 13.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { AnalysisSection(deckAnalysis) }

            items(cardsByCategory, key = { (category, _) -> category }) { (category, cardsList) ->
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val displayTitle = when(category) {
                            "Energy" -> "ENERGIA"
                            else -> category.uppercase()
                        }
                        Text(
                            text = displayTitle,
                            color = AppColors.lavender,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${cardsList.sumOf { it.second }}",
                            color = AppColors.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    cardsList.chunked(4).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (card, quantity) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.71f)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(card.imageUrl)
                                            .crossfade(true)
                                            .size(250, 350)
                                            .build(),
                                        contentDescription = card.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onCardClick(card.id) }
                                    )
                                    if (quantity > 1) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            Text(
                                                text = "x$quantity",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            repeat(4 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun DeckExportDialog(
    deckName: String,
    decklistText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        title = {
            Text(
                text = AppLocale.deckExportTitle,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = decklistText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 360.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.blue,
                        unfocusedBorderColor = AppColors.textMuted.copy(alpha = 0.4f),
                        focusedTextColor = AppColors.textPrimary,
                        unfocusedTextColor = AppColors.textPrimary,
                        focusedContainerColor = AppColors.card,
                        unfocusedContainerColor = AppColors.card
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("PTCG Decklist", decklistText))
                            Toast.makeText(context, AppLocale.deckExportCopied, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.textPrimary)
                    ) {
                        Text(AppLocale.deckExportCopy)
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "$deckName - PTCG Decklist")
                                putExtra(Intent.EXTRA_TEXT, decklistText)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, AppLocale.deckExportShareChooser)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
                    ) {
                        Text(AppLocale.deckExportShare)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(AppLocale.cancel, color = AppColors.textMuted)
            }
        }
    )
}

@Composable
fun AnalysisSection(analysis: DeckAnalysis) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.card)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Analytics, contentDescription = null, tint = AppColors.lavender, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Analisi Lab", color = AppColors.lavender, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AnalysisInfoItem("Media HP", analysis.averageHp.toInt().toString())
            AnalysisInfoItem("Tipi", analysis.typesCount.size.toString())
            
            val p = analysis.supertypesCount["Pokémon"] ?: 0
            val t = (analysis.supertypesCount["Trainer"] ?: 0)
            val e = analysis.supertypesCount["Energy"] ?: 0
            
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Ripartizione", color = AppColors.textMuted, fontSize = 10.sp)
                Text(text = "$p Pokémon / $t Trainer / $e Energy", color = AppColors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AnalysisInfoItem(label: String, value: String) {
    Column {
        Text(text = label, color = AppColors.textMuted, fontSize = 10.sp)
        Text(text = value, color = AppColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

