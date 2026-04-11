package com.emabuia.pokevault.ui.competitive

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
import com.emabuia.pokevault.data.model.MatchLog
import com.emabuia.pokevault.data.model.Tournament
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.CompetitiveLogViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentDetailScreen(
    tournamentId: String,
    onBack: () -> Unit,
    onAddMatch: (String) -> Unit,
    onEditMatch: (String, String) -> Unit,
    viewModel: CompetitiveLogViewModel = viewModel()
) {
    val tournament = viewModel.getTournamentById(tournamentId)

    LaunchedEffect(tournamentId) {
        viewModel.loadMatchesForTournament(tournamentId)
    }

    var showDeleteDialog by remember { mutableStateOf<MatchLog?>(null) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        tournament?.type ?: "",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddMatch(tournamentId) },
                containerColor = OrangeCard,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, AppLocale.addMatch, tint = TextWhite)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Tournament info card
            if (tournament != null) {
                item { TournamentInfoCard(tournament) }
            }

            // Stats
            if (viewModel.tournamentMatches.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(AppLocale.matchRecordLabel, AppLocale.matchRecord(viewModel.wins, viewModel.losses, viewModel.ties), Modifier.weight(1f))
                        StatCard(AppLocale.matchWinRate, "${viewModel.winRate.toInt()}%", Modifier.weight(0.5f))
                    }
                }
            }

            // Section title
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    AppLocale.matchLogTitle,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            if (viewModel.tournamentMatches.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SportsEsports, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(AppLocale.matchLogEmpty, color = TextMuted, fontSize = 14.sp)
                            Text(AppLocale.matchLogEmptySubtitle, color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(viewModel.tournamentMatches, key = { it.id }) { match ->
                    MatchCard(
                        match = match,
                        onClick = { onEditMatch(tournamentId, match.id) },
                        onDelete = { showDeleteDialog = match }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

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
                }) { Text(AppLocale.delete, color = RedCard) }
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
private fun TournamentInfoCard(tournament: Tournament) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val dateStr = tournament.date?.toDate()?.let { dateFormat.format(it) } ?: ""

    val typeColor = when (tournament.type) {
        "Cup" -> StarGold
        "Challenge" -> BlueCard
        "Local" -> GreenCard
        else -> TextMuted
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = typeColor.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                    Text(tournament.type, color = typeColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                if (tournament.format.isNotBlank()) {
                    Surface(color = LavenderCard.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                        Text(tournament.format, color = LavenderCard, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            if (tournament.deckName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Layers, null, tint = OrangeCard, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tournament.deckName, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (dateStr.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, null, tint = TextMuted, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(dateStr, color = TextGray, fontSize = 12.sp)
                    }
                }
                if (tournament.location.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = TextMuted, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tournament.location, color = TextGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (tournament.participants > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, null, tint = TextMuted, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${tournament.participants}", color = TextGray, fontSize = 12.sp)
                    }
                }
                if (tournament.registrationFee > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Euro, null, tint = TextMuted, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${"%.2f".format(tournament.registrationFee)} €", color = TextGray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchCard(
    match: MatchLog,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val resultColor = when (match.result) {
        "W" -> GreenCard; "L" -> RedCard; "T" -> YellowCard; else -> TextMuted
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(resultColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(match.result, color = resultColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (match.round > 0) {
                        Text(
                            "${AppLocale.matchRound} ${match.round}",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

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
                    Text(vsText, color = TextGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                if (match.notes.isNotBlank()) {
                    Text(match.notes, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, AppLocale.delete, tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = TextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
