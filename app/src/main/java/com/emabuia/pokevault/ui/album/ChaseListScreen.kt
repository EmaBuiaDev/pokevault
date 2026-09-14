package com.emabuia.pokevault.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.ui.components.LabSearchField
import com.emabuia.pokevault.ui.components.EmptyStateView
import com.emabuia.pokevault.ui.components.FillBar
import com.emabuia.pokevault.ui.components.ProgressRing
import com.emabuia.pokevault.ui.components.SkeletonBlock
import com.emabuia.pokevault.ui.components.SortChipRow
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.ChaseRow
import com.emabuia.pokevault.util.ChaseSort
import com.emabuia.pokevault.util.CollectorLab
import com.emabuia.pokevault.viewmodel.GoalAlbumViewModel

/**
 * La lista dei chase.
 *
 * Ordinata per default sui "quasi fatti", cioe' su quelli dove conviene
 * spendere la prossima carta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaseListScreen(
    onBack: () -> Unit,
    onCreateChase: () -> Unit,
    onChaseClick: (String) -> Unit,
    onPremiumRequired: () -> Unit,
    viewModel: GoalAlbumViewModel = viewModel()
) {
    var showChasePremiumDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ChaseRow?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ChaseSort.CLOSEST) }

    val rows = remember(viewModel.goalAlbums, viewModel.ownedCards) {
        viewModel.chaseRows { it.criteriaSummary() }
    }
    val visibleRows = remember(rows, query, sort) {
        CollectorLab.sortChases(CollectorLab.filterChases(rows, query), sort)
    }
    val completed = remember(rows) { rows.count { it.isComplete } }
    val missing = remember(rows) { rows.sumOf { it.missing } }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Chase",
                            color = AppColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (rows.isNotEmpty()) {
                            Text(
                                text = AppLocale.collectorChasesDone(completed, rows.size) +
                                    " · " + AppLocale.collectorMissingCards(missing),
                                color = AppColors.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppLocale.back,
                            tint = AppColors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (viewModel.canCreate()) onCreateChase() else showChasePremiumDialog = true
                },
                containerColor = AppColors.orange,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = AppLocale.createChaseTitle, tint = AppColors.textPrimary)
            }
        }
    ) { padding ->
        if (viewModel.isLoading && rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(4) { index ->
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth().height(92.dp),
                        shape = RoundedCornerShape(16.dp),
                        index = index
                    )
                }
            }
            return@Scaffold
        }

        if (rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                EmptyStateView(
                    icon = Icons.Default.TrackChanges,
                    title = AppLocale.newChaseLabel,
                    subtitle = AppLocale.newChaseSubtitle
                )
                Button(
                    onClick = {
                        if (viewModel.canCreate()) onCreateChase() else showChasePremiumDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.orange),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        AppLocale.chaseCreateCta,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item(key = "controls") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabSearchField(
                        value = query,
                        onValueChange = { query = it },
                        hint = AppLocale.chaseSearchHint
                    )
                    SortChipRow(
                        labels = listOf(
                            AppLocale.chaseSortClosest,
                            AppLocale.chaseSortName,
                            AppLocale.chaseSortRecent
                        ),
                        selectedIndex = sort.ordinal,
                        onSelect = { index -> sort = ChaseSort.entries[index] }
                    )
                }
            }

            if (visibleRows.isEmpty()) {
                item(key = "no-results") {
                    Text(
                        AppLocale.chaseNoResults,
                        color = AppColors.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            items(visibleRows, key = { it.id }) { row ->
                ChaseCard(
                    row = row,
                    onClick = { onChaseClick(row.id) },
                    // La cancellazione passa da una conferma: prima il cestino
                    // della riga eliminava il chase al primo tocco, senza
                    // chiedere niente e senza modo di tornare indietro.
                    onDelete = { pendingDelete = row }
                )
            }

            item(key = "fab-space") { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = AppColors.surface,
            title = { Text(AppLocale.chaseDeleteTitle, color = AppColors.textPrimary) },
            text = {
                Column {
                    Text(row.name, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(AppLocale.chaseDeleteMessage, color = AppColors.textSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGoalAlbum(row.id)
                    pendingDelete = null
                }) {
                    Text(AppLocale.delete, color = AppColors.red)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(AppLocale.cancel, color = AppColors.textMuted)
                }
            }
        )
    }

    if (showChasePremiumDialog) {
        PremiumRequiredDialog(
            title = AppLocale.premiumChaseLimitTitle,
            message = AppLocale.premiumChaseLimitMessage,
            onDismiss = { showChasePremiumDialog = false },
            onUpgrade = {
                showChasePremiumDialog = false
                onPremiumRequired()
            }
        )
    }
}

/** La riga di un chase: anello, criterio, mancanti e — se e' chiuso — il sigillo. */
@Composable
internal fun ChaseCard(
    row: ChaseRow,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.surface)
            .pressScale(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(percent = row.percent, size = 52.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.name,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (row.isComplete) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AppColors.gold.copy(alpha = 0.2f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                AppLocale.chaseCompleteBadge,
                                color = AppColors.gold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    AppLocale.chaseCardsProgress(row.owned, row.total),
                    color = AppColors.textSecondary,
                    fontSize = 12.sp
                )
                Text(
                    row.criteriaLabel,
                    color = AppColors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = AppLocale.delete,
                    tint = AppColors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FillBar(percent = row.percent, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (row.isComplete) AppLocale.chaseCompleteBadge
                else AppLocale.chaseMissingCount(row.missing),
                color = if (row.isComplete) AppColors.gold else AppColors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
