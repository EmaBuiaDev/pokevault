package com.example.pokevault.ui.competitive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pokevault.data.model.MatchLog
import com.example.pokevault.ui.theme.*
import com.example.pokevault.util.AppLocale
import com.example.pokevault.viewmodel.CompetitiveLogViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchLogScreen(
    onBack: () -> Unit,
    onAddMatch: (String?) -> Unit,
    viewModel: CompetitiveLogViewModel = viewModel()
) {
    var showDeleteDialog by remember { mutableStateOf<MatchLog?>(null) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        AppLocale.matchLogTitle,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppLocale.back,
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddMatch(null) },
                containerColor = OrangeCard,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = AppLocale.addMatch, tint = TextWhite)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Stats bar
            if (viewModel.matchLogs.isNotEmpty()) {
                StatsBar(viewModel)
            }

            if (viewModel.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = OrangeCard)
                }
            } else if (viewModel.matchLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            AppLocale.matchLogEmpty,
                            color = TextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            AppLocale.matchLogEmptySubtitle,
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(viewModel.matchLogs, key = { it.id }) { match ->
                        MatchLogCard(
                            match = match,
                            onClick = { onAddMatch(match.id) },
                            onDelete = { showDeleteDialog = match }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Delete dialog
    showDeleteDialog?.let { match ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = DarkSurface,
            title = { Text(AppLocale.matchDeleteTitle, color = TextWhite) },
            text = { Text(AppLocale.matchDeleteMessage, color = TextGray) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMatch(match.id)
                    showDeleteDialog = null
                }) {
                    Text(AppLocale.delete, color = RedCard)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(AppLocale.cancel, color = TextGray)
                }
            }
        )
    }
}

@Composable
private fun StatsBar(viewModel: CompetitiveLogViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatMini(
            label = AppLocale.matchRecordLabel,
            value = AppLocale.matchRecord(viewModel.wins, viewModel.losses, viewModel.ties),
            modifier = Modifier.weight(1f)
        )
        StatMini(
            label = AppLocale.matchWinRate,
            value = "${viewModel.winRate.toInt()}%",
            modifier = Modifier.weight(0.5f)
        )
    }
}

@Composable
private fun StatMini(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = TextMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun MatchLogCard(
    match: MatchLog,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val resultColor = when (match.result) {
        "W" -> GreenCard
        "L" -> RedCard
        "T" -> YellowCard
        else -> TextMuted
    }
    val resultLabel = when (match.result) {
        "W" -> AppLocale.matchWin
        "L" -> AppLocale.matchLoss
        "T" -> AppLocale.matchTie
        else -> ""
    }
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val dateStr = match.date?.toDate()?.let { dateFormat.format(it) } ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Result badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(resultColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = match.result,
                    color = resultColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = match.deckName,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (match.format.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = LavenderCard.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = match.format,
                                color = LavenderCard,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                if (match.opponentName.isNotBlank() || match.opponentDeck.isNotBlank()) {
                    val vsText = buildString {
                        append("vs ")
                        if (match.opponentName.isNotBlank()) append(match.opponentName)
                        if (match.opponentDeck.isNotBlank()) {
                            if (match.opponentName.isNotBlank()) append(" (")
                            append(match.opponentDeck)
                            if (match.opponentName.isNotBlank()) append(")")
                        }
                    }
                    Text(
                        text = vsText,
                        color = TextGray,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dateStr.isNotBlank()) {
                        Text(text = dateStr, color = TextMuted, fontSize = 11.sp)
                    }
                    if (match.location.isNotBlank()) {
                        if (dateStr.isNotBlank()) {
                            Text(text = "  •  ", color = TextMuted, fontSize = 11.sp)
                        }
                        Text(
                            text = match.location,
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = AppLocale.delete,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
