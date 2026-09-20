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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.Tournament
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.components.DeckSpriteRow
import com.emabuia.pokevault.ui.components.hasChosenSprites
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
    // isPremium NON viene raccolto qui: tutti i gate di questo schermo stanno
    // dentro lambda di click, quindi leggono _isPremium.value al momento del
    // tocco, che e' gia' il comportamento corretto. Raccoglierlo senza usarlo
    // faceva solo ricomporre l'intero schermo a ogni cambio di stato premium.
    var showDeleteDialog by remember { mutableStateOf<Tournament?>(null) }
    var showPremiumDialog by remember { mutableStateOf(false) }

    // Contati una volta per tutta la lista. Prima ogni riga scorreva l'intero
    // archivio dei match per contare i suoi, quindi il costo cresceva con
    // (tornei x match) a ogni ricomposizione -- cioe' a ogni frame di
    // scorrimento.
    val matchCountByTournament = remember(viewModel.allMatches) {
        viewModel.allMatches.groupingBy { it.tournamentId }.eachCount()
    }

    // Il mazzo di un torneo serve solo per le sue copertine: l'indice evita di
    // cercarlo riga per riga.
    val decksById = remember(viewModel.userDecks) {
        viewModel.userDecks.associateBy { it.id }
    }

    // Due domande diverse sullo stesso archivio: "cosa ho giocato" e "come sto
    // andando". Prima c'era solo la prima, e la seconda si riduceva a due
    // riquadri con record e percentuale in cima alla lista.
    var showStats by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        AppLocale.tournamentListTitle,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = AppColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        },
        floatingActionButton = {
            // Il FAB crea tornei: nella vista statistiche non avrebbe niente da
            // fare, e coprirebbe l'ultima riga della tabella dei matchup.
            if (!showStats) {
                FloatingActionButton(
                    onClick = {
                        if (premiumManager.canCreateTournament(viewModel.tournaments.size)) {
                            onAddTournament(null)
                        } else {
                            showPremiumDialog = true
                        }
                    },
                    containerColor = AppColors.orange,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = AppLocale.addTournament, tint = AppColors.textPrimary)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ModeSwitch(
                options = listOf(
                    AppLocale.matchLogTabTournaments,
                    AppLocale.matchLogTabStats
                ),
                selectedIndex = if (showStats) 1 else 0,
                onSelect = { showStats = it == 1 },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (showStats) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    matchStatsSection(summary = viewModel.summary)
                }
                return@Column
            }

            if (viewModel.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.orange)
                }
            } else if (viewModel.tournaments.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EmojiEvents, null, tint = AppColors.textMuted, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(AppLocale.tournamentEmpty, color = AppColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(AppLocale.tournamentEmptySubtitle, color = AppColors.textMuted, fontSize = 14.sp)
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
                            matchCount = matchCountByTournament[tournament.id] ?: 0,
                            deck = decksById[tournament.deckId],
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
            containerColor = AppColors.surface,
            title = { Text(AppLocale.tournamentDeleteTitle, color = AppColors.textPrimary) },
            text = { Text(AppLocale.tournamentDeleteMessage, color = AppColors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTournament(tournament.id)
                    showDeleteDialog = null
                }) { Text(AppLocale.delete, color = AppColors.red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(AppLocale.cancel, color = AppColors.textSecondary)
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
    deck: Deck?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val dateStr = tournament.date?.toDate()?.let { dateFormat.format(it) } ?: ""

    val typeColor = when (tournament.type) {
        "Cup" -> AppColors.gold
        "Challenge" -> AppColors.blue
        "Local" -> AppColors.green
        else -> AppColors.textMuted
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.card)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Il mazzo con cui hai giocato, quando ha delle copertine: e' la
            // cosa per cui si riconosce un torneo passato. Il tipo resta
            // scritto nella targhetta qui accanto, quindi l'icona a cerchio
            // ripeteva un'informazione gia' presente: la teniamo solo per i
            // tornei senza copertine, cosi' la riga non perde il suo inizio.
            if (deck.hasChosenSprites()) {
                DeckSpriteRow(deck = deck, size = 40.dp)
            } else {
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
                            color = AppColors.lavender.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = tournament.format,
                                color = AppColors.lavender,
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
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dateStr.isNotBlank()) {
                        Text(dateStr, color = AppColors.textMuted, fontSize = 11.sp)
                    }
                    if (tournament.location.isNotBlank()) {
                        if (dateStr.isNotBlank()) Text("  •  ", color = AppColors.textMuted, fontSize = 11.sp)
                        Text(tournament.location, color = AppColors.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        AppLocale.tournamentMatches(matchCount),
                        color = AppColors.textSecondary,
                        fontSize = 11.sp
                    )
                    if (tournament.participants > 0) {
                        Text("•", color = AppColors.textMuted, fontSize = 11.sp)
                        Text(AppLocale.playersCount(tournament.participants), color = AppColors.textSecondary, fontSize = 11.sp)
                    }
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, AppLocale.delete, tint = AppColors.textMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}
