@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.MatchLog
import com.emabuia.pokevault.data.model.Tournament
import com.emabuia.pokevault.ui.components.DeckSpriteCompact
import com.emabuia.pokevault.ui.components.DeckSpriteRow
import com.emabuia.pokevault.ui.components.hasChosenSprites
import com.emabuia.pokevault.ui.components.ArchetypeSpriteRow
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
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        tournament?.type ?: "",
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
            FloatingActionButton(
                onClick = { onAddMatch(tournamentId) },
                containerColor = AppColors.orange,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, AppLocale.addMatch, tint = AppColors.textPrimary)
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
            // Una scheda sola invece di tre impilate.
            //
            // Prima c'erano l'anagrafica del torneo, due riquadri con record e
            // percentuale, e un terzo con l'andamento: tre schede identiche per
            // colore e forma, che occupavano mezzo schermo e non dicevano quale
            // fosse l'informazione importante. Qui l'esito sta in cima e in
            // grande, il resto gli fa da contorno.
            if (tournament != null) {
                item {
                    // Il mazzo serve solo per le sue copertine: si cerca qui,
                    // dove il ViewModel c'e', e si passa alla scheda.
                    val deck = remember(tournament.deckId, viewModel.userDecks) {
                        viewModel.userDecks.firstOrNull { it.id == tournament.deckId }
                    }
                    val results = remember(viewModel.tournamentMatches) {
                        viewModel.tournamentMatches.sortedBy { it.round }.map { it.result }
                    }
                    TournamentInfoCard(
                        tournament = tournament,
                        deck = deck,
                        wins = viewModel.wins,
                        losses = viewModel.losses,
                        ties = viewModel.ties,
                        winRate = viewModel.winRate,
                        results = results
                    )
                }
            }

            // Section title
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    AppLocale.matchLogTitle,
                    color = AppColors.textPrimary,
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
                            Icon(Icons.Default.SportsEsports, null, tint = AppColors.textMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(AppLocale.matchLogEmpty, color = AppColors.textMuted, fontSize = 14.sp)
                            Text(AppLocale.matchLogEmptySubtitle, color = AppColors.textMuted, fontSize = 12.sp)
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
            containerColor = AppColors.surface,
            title = { Text(AppLocale.matchDeleteTitle, color = AppColors.textPrimary) },
            text = { Text(AppLocale.matchDeleteMessage, color = AppColors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMatch(match.id)
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
}

@Composable
private fun TournamentInfoCard(
    tournament: Tournament,
    deck: Deck?,
    wins: Int,
    losses: Int,
    ties: Int,
    winRate: Float,
    results: List<String>
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val dateStr = tournament.date?.toDate()?.let { dateFormat.format(it) } ?: ""

    val typeColor = when (tournament.type) {
        "Cup" -> AppColors.gold
        "Challenge" -> AppColors.blue
        "Local" -> AppColors.green
        else -> AppColors.textMuted
    }

    // Com'e' andata, in un colore. E' la prima cosa che si vuole sapere
    // riaprendo un torneo, e finora bisognava leggere tre numeri per dedurla.
    val outcomeColor = when {
        results.isEmpty() -> AppColors.textMuted
        wins > losses -> AppColors.green
        losses > wins -> AppColors.red
        else -> AppColors.yellow
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.card)
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
                    Surface(color = AppColors.lavender.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                        Text(tournament.format, color = AppColors.lavender, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            // L'esito, in grande. Il bilancio e' il numero che conta, la
            // percentuale gli sta accanto piu' piccola, e la striscia dei
            // turni sotto racconta come ci si e' arrivati.
            if (results.isNotEmpty()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = AppLocale.matchRecord(wins, losses, ties),
                        color = outcomeColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 34.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.padding(bottom = 4.dp)) {
                        Text(
                            text = "${winRate.toInt()}%",
                            color = AppColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = AppLocale.matchWinRate,
                            color = AppColors.textMuted,
                            fontSize = 10.sp
                        )
                    }
                }
                ResultStrip(results = results, square = 24.dp)
            }

            if (tournament.deckName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Gli sprite al posto dell'icona a strati quando il mazzo
                    // ne ha: dicono quale mazzo era, non che c'era un mazzo.
                    if (deck.hasChosenSprites()) {
                        DeckSpriteRow(deck = deck, size = DeckSpriteCompact)
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Default.Layers, null, tint = AppColors.orange, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(tournament.deckName, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            // Data, luogo, partecipanti e quota su una riga sola che va a capo
            // da se'. Erano due righe fisse, spesso mezze vuote, e occupavano
            // quanto l'informazione principale pur essendo il contorno.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (dateStr.isNotBlank()) {
                    MetaItem(Icons.Default.CalendarToday, dateStr)
                }
                if (tournament.location.isNotBlank()) {
                    MetaItem(Icons.Default.LocationOn, tournament.location)
                }
                if (tournament.participants > 0) {
                    MetaItem(Icons.Default.Group, "${tournament.participants}")
                }
                if (tournament.registrationFee > 0) {
                    MetaItem(Icons.Default.Euro, "${"%.2f".format(tournament.registrationFee)} €")
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
        "W" -> AppColors.green; "L" -> AppColors.red; "T" -> AppColors.yellow; else -> AppColors.textMuted
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        // Le sconfitte hanno un fondo appena tinto: scorrendo i turni si vede
        // subito dove si e' perso, senza leggere la lettera dentro al cerchio.
        colors = CardDefaults.cardColors(
            containerColor = if (match.result == "L") {
                AppColors.red.copy(alpha = 0.07f)
            } else {
                AppColors.card
            }
        )
    ) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // La banda colorata sul bordo: e' quella che rende la colonna dei
            // risultati leggibile di scorcio.
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(resultColor)
            )

            // Il respiro verticale sta qui e non sul cerchio: e' la colonna di
            // destra a poter crescere fino a tre righe -- turno, avversario,
            // note -- e con il padding sul cerchio toccherebbe i bordi.
            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(resultColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(match.result, color = resultColor, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (match.round > 0) {
                        Text(
                            "${AppLocale.matchRound} ${match.round}",
                            color = AppColors.textPrimary,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Il mazzo dell'avversario e' scritto a mano, quindi
                        // gli sprite si ricavano dal nome dell'archetipo:
                        // scorrendo i turni si riconosce contro cosa hai
                        // giocato senza rileggere ogni riga.
                        if (match.opponentDeck.isNotBlank()) {
                            ArchetypeSpriteRow(archetype = match.opponentDeck, size = 26.dp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(vsText, color = AppColors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (match.notes.isNotBlank()) {
                    Text(match.notes, color = AppColors.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, AppLocale.delete, tint = AppColors.textMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Un dato di contorno del torneo: icona piccola e testo smorzato. */
@Composable
private fun MetaItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AppColors.textMuted, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = AppColors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
