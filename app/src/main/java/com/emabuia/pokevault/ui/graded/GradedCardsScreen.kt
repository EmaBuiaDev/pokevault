package com.emabuia.pokevault.ui.graded

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.viewmodel.GradedCardsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradedCardsScreen(
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    viewModel: GradedCardsViewModel = viewModel()
) {
    val state = viewModel.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = { Text("Carte Graduate", fontWeight = FontWeight.SemiBold, color = TextWhite) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Indietro", tint = TextWhite)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BlueCard)
            }
        } else if (state.totalGraded == 0) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⭐", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Nessuna carta gradata", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Aggiungi carte con certificazione PSA, BGS o CGC dalla sezione Collezione.",
                        color = TextMuted, fontSize = 14.sp, textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // ── Stat Cards ──
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradedStatCard("Totale", "${state.totalGraded}", StarGold, Modifier.weight(1f))
                    GradedStatCard("Grade Medio", "${"%.1f".format(state.averageGrade)}", GreenCard, Modifier.weight(1f))
                    GradedStatCard("Valore", "\u20AC${"%.0f".format(state.totalValue)}", BlueCard, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Company filter chips ──
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompanyChip("Tutte", state.selectedCompany == null) { viewModel.filterByCompany(null) }
                    state.companyCounts.forEach { (company, count) ->
                        CompanyChip("$company ($count)", state.selectedCompany == company) {
                            viewModel.filterByCompany(if (state.selectedCompany == company) null else company)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Search ──
                SearchField(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.updateSearch(it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── Cards Grid ──
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(state.filteredCards) { card ->
                        GradedCardItem(card = card, onClick = { onCardClick(card.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun GradedStatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
        Text(label, color = TextMuted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
fun CompanyChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (isSelected) TextWhite else TextMuted,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) StarGold.copy(alpha = 0.4f) else DarkCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = androidx.compose.ui.text.TextStyle(color = TextWhite, fontSize = 14.sp),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(BlueCard),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) Text("Cerca carta gradata...", color = TextMuted, fontSize = 14.sp)
                    innerTextField()
                }
            )
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Default.Close, contentDescription = "Cancella",
                    tint = TextMuted, modifier = Modifier.size(18.dp).clickable { onQueryChange("") }
                )
            }
        }
    }
}

@Composable
fun GradedCardItem(card: PokemonCard, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .clickable(onClick = onClick)
    ) {
        // Card image
        Box {
            if (card.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.name,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                        .background(PurpleCard.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎴", fontSize = 48.sp)
                }
            }

            // Grade badge overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StarGold)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${card.grade ?: "-"}",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }

        // Card info
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                card.name, color = TextWhite, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    card.gradingCompany.ifBlank { "N/D" },
                    color = StarGold, fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
                if (card.estimatedValue > 0) {
                    Text(
                        "\u20AC${"%.2f".format(card.estimatedValue)}",
                        color = GreenCard, fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
            Text(
                card.set.ifBlank { "-" },
                color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
