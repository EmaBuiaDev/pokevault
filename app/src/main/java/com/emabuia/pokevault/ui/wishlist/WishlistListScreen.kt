package com.emabuia.pokevault.ui.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.data.model.WishlistIcons
import com.emabuia.pokevault.ui.components.FillBar
import com.emabuia.pokevault.ui.components.LabSearchField
import com.emabuia.pokevault.ui.components.SkeletonBlock
import com.emabuia.pokevault.ui.components.SortChipRow
import com.emabuia.pokevault.ui.components.StatTile
import com.emabuia.pokevault.ui.components.formatEurCompact
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.WishlistLab
import com.emabuia.pokevault.util.WishlistRow
import com.emabuia.pokevault.util.WishlistSort
import com.emabuia.pokevault.viewmodel.WishlistViewModel

/**
 * Le wishlist.
 *
 * Prima era un elenco di nomi con un'icona a caso e il numero di carte: non
 * diceva quanto costa quello che manca, non sapeva che meta' di quelle carte
 * erano gia' in collezione, non si poteva cercare ne' ordinare. Adesso ogni
 * riga risponde alle tre domande di chi tiene una lista della spesa — quanto
 * manca, quanto costa, quanto ne ho gia' preso — e in cima c'e' lo stesso conto
 * fatto su tutte le liste insieme.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WishlistListScreen(
    onBack: () -> Unit,
    onPremiumRequired: () -> Unit,
    onWishlistClick: (String) -> Unit,
    viewModel: WishlistViewModel = viewModel()
) {
    val premiumManager = remember { PremiumManager.getInstance() }
    val isPremium by premiumManager.isPremium.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    var wishlistToDelete by remember { mutableStateOf<Wishlist?>(null) }
    var wishlistToEdit by remember { mutableStateOf<Wishlist?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(WishlistSort.CLOSEST) }

    // derivedStateOf e non un calcolo nel corpo: le righe dipendono da tre stati
    // (liste, carte arrivate, collezione) e si rifanno solo quando uno cambia,
    // non a ogni ricomposizione dello schermo.
    val rows by remember { derivedStateOf { viewModel.rows() } }
    val summary by remember { derivedStateOf { viewModel.summary() } }
    val visibleRows = remember(rows, query, sort) {
        WishlistLab.sortWishlists(WishlistLab.filterWishlists(rows, query), sort)
    }

    fun requestCreate() {
        if (viewModel.canCreateWishlist(isPremium)) showCreateDialog = true else showPremiumDialog = true
    }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = AppLocale.wishlistTitle,
                            color = AppColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (rows.isNotEmpty()) {
                            Text(
                                text = AppLocale.wishlistTakenCount(summary.owned, summary.cards),
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
                            Icons.AutoMirrored.Filled.ArrowBack,
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
                onClick = { requestCreate() },
                containerColor = AppColors.purple,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = AppLocale.wishlistCreate, tint = AppColors.onAccent)
            }
        }
    ) { padding ->
        when {
            viewModel.isLoading && rows.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth().height(86.dp),
                        shape = RoundedCornerShape(16.dp)
                    )
                    repeat(3) { index ->
                        SkeletonBlock(
                            modifier = Modifier.fillMaxWidth().height(104.dp),
                            shape = RoundedCornerShape(16.dp),
                            index = index + 1
                        )
                    }
                }
            }

            rows.isEmpty() -> {
                WishlistEmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onCreate = { requestCreate() }
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
                ) {
                    item(key = "summary") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(
                                label = AppLocale.wishlistStatCards,
                                value = summary.cards.toString(),
                                icon = Icons.Default.Style,
                                accent = AppColors.purple,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = AppLocale.wishlistStatTaken,
                                value = "${summary.owned}",
                                icon = Icons.Default.ShoppingBag,
                                accent = AppColors.green,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = AppLocale.wishlistStatCost,
                                value = formatEurCompact(summary.cost),
                                icon = Icons.Default.Savings,
                                accent = AppColors.gold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Il totale in cima e' una stima al ribasso finche' le carte stanno
                    // arrivando, o finche' di qualcuna non si conosce il prezzo: e'
                    // la cifra su cui si decide una spesa, non puo' fingersi esatta.
                    val partialNote = when {
                        viewModel.isLoadingCards -> AppLocale.wishlistPartialTotal
                        summary.unpricedMissing > 0 -> AppLocale.wishlistUnpricedNote(summary.unpricedMissing)
                        else -> null
                    }
                    if (partialNote != null) {
                        item(key = "partial-note") {
                            Text(
                                text = partialNote,
                                color = AppColors.textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    item(key = "controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            LabSearchField(
                                value = query,
                                onValueChange = { query = it },
                                hint = AppLocale.wishlistSearchHint,
                                accent = AppColors.purple
                            )
                            SortChipRow(
                                labels = listOf(
                                    AppLocale.wishlistSortClosest,
                                    AppLocale.wishlistSortRecent,
                                    AppLocale.wishlistSortName,
                                    AppLocale.wishlistSortCost,
                                    AppLocale.wishlistSortCards
                                ),
                                selectedIndex = sort.ordinal,
                                onSelect = { index -> sort = WishlistSort.entries[index] },
                                accent = AppColors.purple
                            )
                        }
                    }

                    if (visibleRows.isEmpty()) {
                        item(key = "no-results") {
                            Text(
                                text = AppLocale.wishlistNoResults,
                                color = AppColors.textMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    }

                    items(visibleRows, key = { it.id }) { row ->
                        WishlistCard(
                            row = row,
                            onClick = { onWishlistClick(row.id) },
                            onEdit = { wishlistToEdit = viewModel.getWishlistById(row.id) },
                            onDelete = { wishlistToDelete = viewModel.getWishlistById(row.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        WishlistEditorDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { draft ->
                viewModel.createWishlist(draft, isPremium) { success ->
                    if (success) showCreateDialog = false
                }
            },
            isSaving = viewModel.isSaving
        )
    }

    wishlistToEdit?.let { wishlist ->
        WishlistEditorDialog(
            onDismiss = { wishlistToEdit = null },
            onConfirm = { draft ->
                viewModel.updateWishlistDetails(wishlist.id, draft) { success ->
                    if (success) wishlistToEdit = null
                }
            },
            isSaving = viewModel.isSaving,
            initial = wishlist,
            titleText = AppLocale.wishlistEdit,
            confirmText = AppLocale.save
        )
    }

    wishlistToDelete?.let { wishlist ->
        AlertDialog(
            onDismissRequest = { wishlistToDelete = null },
            containerColor = AppColors.surface,
            title = { Text(AppLocale.wishlistDeleteTitle, color = AppColors.textPrimary) },
            text = {
                Column {
                    Text(wishlist.name, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(AppLocale.wishlistDeleteMessage, color = AppColors.textSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWishlist(wishlist.id)
                        wishlistToDelete = null
                    }
                ) {
                    Text(AppLocale.delete, color = AppColors.red)
                }
            },
            dismissButton = {
                TextButton(onClick = { wishlistToDelete = null }) {
                    Text(AppLocale.cancel, color = AppColors.textMuted)
                }
            }
        )
    }

    if (showPremiumDialog) {
        PremiumRequiredDialog(
            title = AppLocale.premiumWishlistLimitTitle,
            message = AppLocale.premiumWishlistLimitMessage,
            onDismiss = { showPremiumDialog = false },
            onUpgrade = {
                showPremiumDialog = false
                onPremiumRequired()
            }
        )
    }

    LaunchedEffect(viewModel.successMessage, viewModel.errorMessage) {
        if (viewModel.successMessage != null || viewModel.errorMessage != null) {
            viewModel.clearMessages()
        }
    }
}

/**
 * La riga di una lista.
 *
 * L'icona sta nello slot grande perche' e' quello che distingue una lista
 * dall'altra a colpo d'occhio; il prezzo sta a destra perche' e' il numero che
 * si confronta fra righe diverse. Modifica ed elimina sono finite in un menu:
 * due cestini per riga trasformavano una lista di desideri in una barra
 * strumenti.
 */
@Composable
private fun WishlistCard(
    row: WishlistRow,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = wishlistAccentColor(row.accentKey)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.surface, RoundedCornerShape(16.dp))
            .pressScale(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WishlistBadge(iconKey = row.iconKey, accentKey = row.accentKey, size = 46.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.name,
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
                                .background(AppColors.gold.copy(alpha = 0.2f), CircleShape)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = AppLocale.wishlistCompleteBadge,
                                color = AppColors.gold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = "${wishlistIconLabel(row.iconKey)} · ${AppLocale.wishlistCardsCount(row.total)}",
                    color = AppColors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatEurCompact(row.cost),
                    color = if (row.isOverBudget) AppColors.red else accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AppLocale.wishlistMissingCount(row.missing),
                    color = AppColors.textMuted,
                    fontSize = 10.sp
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = AppLocale.wishlistEdit,
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(AppLocale.wishlistEdit, color = AppColors.textPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = AppColors.textMuted)
                        },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(AppLocale.delete, color = AppColors.red) },
                        leadingIcon = {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AppColors.red)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }

        if (row.total > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FillBar(percent = row.ownedPercent, modifier = Modifier.weight(1f), accent = accent)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${row.owned}/${row.total}",
                    color = if (row.isComplete) AppColors.gold else AppColors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (row.hasBudget) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FillBar(
                    percent = row.budgetPercent.coerceAtMost(100f),
                    modifier = Modifier.weight(1f),
                    accent = if (row.isOverBudget) AppColors.red else AppColors.green
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (row.isOverBudget) {
                        AppLocale.wishlistBudgetOver(formatEurCompact(row.cost - row.budgetEur))
                    } else {
                        AppLocale.wishlistBudgetLeft(formatEurCompact(row.budgetLeft))
                    },
                    color = if (row.isOverBudget) AppColors.red else AppColors.textMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/** Il vuoto: una ball vera, non un cuore generico, e il pulsante che serve. */
@Composable
private fun WishlistEmptyState(
    modifier: Modifier = Modifier,
    onCreate: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        WishlistGlyph(
            iconKey = WishlistIcons.POKE_BALL,
            accent = AppColors.red,
            size = 64.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = AppLocale.wishlistEmpty,
            color = AppColors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = AppLocale.wishlistEmptySubtitle,
            color = AppColors.textMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onCreate,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.purple),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = AppLocale.wishlistCreate,
                color = AppColors.onAccent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
