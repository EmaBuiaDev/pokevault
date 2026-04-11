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
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.model.Tournament
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.CompetitiveLogViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchLogScreen(
    onBack: () -> Unit,
    onAddTournament: (String?) -> Unit,
    onTournamentClick: (String) -> Unit,
    onNavigateToPremium: () -> Unit = {},
    viewModel: CompetitiveLogViewModel = viewModel()
) {
    val premiumManager = remember { PremiumManager.getInstance() }
    val isPremium by premiumManager.isPremium.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Tournament?>(null) }
    var showPremiumDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        AppLocale.tournamentListTitle,
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
                onClick = {
                    if (premiumManager.canCreateTournament(viewModel.tournaments.size)) {
                        onAddTournament(null)
                    } else {
                        showPremiumDialog = true
                    }
                },
                containerColor = OrangeCard,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = AppLocale.addTournament, tint = TextWhite)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Global stats
            if (viewModel.allMatches.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMini(
                        label = AppLocale.matchRecordLabel,
                        value = AppLocale.matchRecord(viewModel.globalWins, viewModel.globalLosses, viewModel.globalTies),
                        modifier = Modifier.weight(1f)
                    )
                    StatMini(
                        label = AppLocale.matchWinRate,
                        value = "${viewModel.globalWinRate.toInt()}%",
                        modifier = Modifier.weight(0.5f)
                    )
                }
            }

            if (viewModel.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrangeCard)
                }
            } else if (viewModel.tournaments.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EmojiEvents, null, tint = TextMuted, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(AppLocale.tournamentEmpty, color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(AppLocale.tournamentEmptySubtitle, color = TextMuted, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(viewModel.tournaments, key = { it.id }) { tournament ->
                        TournamentCard(
                            tournament = tournament,
                            matchCount = viewModel.allMatches.count { it.tournamentId == tournament.id },
                            onClick = { onTournamentClick(tournament.id) },
                            onDelete = { showDeleteDialog = tournament }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    showDeleteDialog?.let { tournament ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = DarkSurface,
            title = { Text(AppLocale.tournamentDeleteTitle, color = TextWhite) },
            text = { Text(AppLocale.tournamentDeleteMessage, color = TextGray) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTournament(tournament.id)
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

    if (showPremiumDialog) {
        PremiumRequiredDialog(
            title = AppLocale.premiumTournamentLimitTitle,
            message = AppLocale.premiumTournamentLimitMessage,
            onDismiss = { showPremiumDialog = false },
            onUpgrade = {
                showPremiumDialog = false
                onNavigateToPremium()
            }
        )
    }
}

@Composable
private fun TournamentCard(
    tournament: Tournament,
    matchCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val dateStr = tournament.date?.toDate()?.let { dateFormat.format(it) } ?: ""

    val typeColor = when (tournament.type) {
        "Cup" -> StarGold
        "Challenge" -> BlueCard
        "Local" -> GreenCard
        else -> TextMuted
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (tournament.type) {
                        "Cup" -> Icons.Default.EmojiEvents
                        "Challenge" -> Icons.Default.Star
                        else -> Icons.Default.Group
                    },
                    contentDescription = null,
                    tint = typeColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = typeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = tournament.type,
                            color = typeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (tournament.format.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = LavenderCard.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = tournament.format,
                                color = LavenderCard,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = tournament.deckName.ifBlank { "—" },
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dateStr.isNotBlank()) {
                        Text(dateStr, color = TextMuted, fontSize = 11.sp)
                    }
                    if (tournament.location.isNotBlank()) {
                        if (dateStr.isNotBlank()) Text("  •  ", color = TextMuted, fontSize = 11.sp)
                        Text(tournament.location, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        AppLocale.tournamentMatches(matchCount),
                        color = TextGray,
                        fontSize = 11.sp
                    )
                    if (tournament.participants > 0) {
                        Text("•", color = TextMuted, fontSize = 11.sp)
                        Text("${tournament.participants} players", color = TextGray, fontSize = 11.sp)
                    }
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, AppLocale.delete, tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun StatMini(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = DarkCard)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = TextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
