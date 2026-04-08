package com.example.pokevault.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pokevault.data.model.MetaArchetype
import com.example.pokevault.data.model.MetaDeck
import com.example.pokevault.ui.theme.*
import com.example.pokevault.util.AppLocale
import com.example.pokevault.viewmodel.MetaDeckViewModel

@Composable
fun MetaArchetypeSection(
    viewModel: MetaDeckViewModel,
    onImportDeck: ((MetaDeck) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Format selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FormatChip(
                label = "Standard",
                selected = viewModel.selectedFormat == "standard",
                onClick = { viewModel.selectFormat("standard") }
            )
            FormatChip(
                label = "Expanded",
                selected = viewModel.selectedFormat == "expanded",
                onClick = { viewModel.selectFormat("expanded") }
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = { viewModel.refresh() },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(DarkCard)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        when {
            viewModel.isLoadingArchetypes -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BlueCard)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (AppLocale.isItalian) "Caricamento meta deck..." else "Loading meta decks...",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            viewModel.archetypeError != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(viewModel.archetypeError ?: "", color = TextGray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.loadArchetypes() }) {
                            Text(AppLocale.retry, color = BlueCard)
                        }
                    }
                }
            }

            viewModel.archetypes.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(AppLocale.metaNoArchetypes, color = TextGray, fontSize = 14.sp)
                    }
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(viewModel.archetypes) { index, archetype ->
                        ArchetypeCard(
                            rank = index + 1,
                            archetype = archetype,
                            onImport = if (onImportDeck != null && archetype.sampleDeck != null) {
                                { onImportDeck(archetype.sampleDeck!!) }
                            } else null
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ArchetypeCard(
    rank: Int,
    archetype: MetaArchetype,
    onImport: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700)  // Gold
        2 -> Color(0xFFC0C0C0)  // Silver
        3 -> Color(0xFFCD7F32)  // Bronze
        else -> TextMuted
    }

    val metaShareColor = when {
        archetype.metaShare >= 15 -> Color(0xFFEF4444) // Alto
        archetype.metaShare >= 8 -> Color(0xFFEAB308)   // Medio
        archetype.metaShare >= 3 -> Color(0xFF22C55E)   // Basso
        else -> TextMuted
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Rank badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(rankColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$rank",
                        color = rankColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name and meta share
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = archetype.name,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${archetype.count} deck",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        if (archetype.topPlacement <= 3) {
                            Spacer(modifier = Modifier.width(8.dp))
                            val trophyColor = when (archetype.topPlacement) {
                                1 -> Color(0xFFFFD700)
                                2 -> Color(0xFFC0C0C0)
                                else -> Color(0xFFCD7F32)
                            }
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = trophyColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = " Top ${archetype.topPlacement}",
                                color = trophyColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Meta share badge
                Surface(
                    color = metaShareColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${"%.1f".format(archetype.metaShare)}%",
                            color = metaShareColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "meta",
                            color = metaShareColor.copy(alpha = 0.8f),
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Win Rate
                val wrColor = when {
                    archetype.avgWinrate >= 0.65 -> GreenCard
                    archetype.avgWinrate >= 0.50 -> YellowCard
                    else -> RedCard
                }
                ArchetypeStat(
                    label = "Win Rate",
                    value = "${(archetype.avgWinrate * 100).toInt()}%",
                    color = wrColor,
                    modifier = Modifier.weight(1f)
                )

                // Best Placement
                ArchetypeStat(
                    label = if (AppLocale.isItalian) "Miglior" else "Best",
                    value = "Top ${archetype.topPlacement}",
                    color = BlueCard,
                    modifier = Modifier.weight(1f)
                )

                // Import button
                if (onImport != null) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onImport),
                        color = PurpleCard.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = PurpleCard,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Import",
                                color = PurpleCard,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchetypeStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(40.dp),
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = label,
                color = color.copy(alpha = 0.7f),
                fontSize = 9.sp
            )
        }
    }
}
