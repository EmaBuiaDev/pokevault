package com.emabuia.pokevault.ui.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.ui.components.CascadeIn
import com.emabuia.pokevault.ui.components.CollectorSearchField
import com.emabuia.pokevault.ui.components.EmptyStateView
import com.emabuia.pokevault.ui.components.FillBar
import com.emabuia.pokevault.ui.components.SkeletonBlock
import com.emabuia.pokevault.ui.components.SortChipRow
import com.emabuia.pokevault.ui.components.StatTile
import com.emabuia.pokevault.ui.components.formatEurCompact
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.WishlistLab
import com.emabuia.pokevault.util.WishlistListSort
import com.emabuia.pokevault.util.WishlistRow
import com.emabuia.pokevault.viewmodel.WishlistViewModel

/**
 * L'elenco delle liste.
 *
 * Prima era un elenco di nomi con accanto un numero: due liste da dodici carte
 * erano indistinguibili anche se di una ne avevi gia' prese undici. Adesso ogni
 * riga dice a che punto sei — il confronto con la collezione e' locale e non
 * costa una chiamata — e in cima c'e' quello che serve per decidere dove
 * entrare.
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
    var sort by remember { mutableStateOf(WishlistListSort.RECENT) }

    val rows = remember(viewModel.wishlists, viewModel.ownedCardIds) {
        WishlistLab.rows(viewModel.wishlists, viewModel.ownedCardIds)
    }
    val visibleRows = remember(rows, query, sort) {
        WishlistLab.sortRows(WishlistLab.filterRows(rows, query), sort)
    }
    val overview = remember(rows) { WishlistLab.overview(rows) }

    // Il possesso si legge dalla collezione: l'ascolto parte qui, non nel
    // ViewModel, che vive anche dove il possesso non serve.
    LaunchedEffect(Unit) { viewModel.observeOwnedCards() }

    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.isLoading) { if (!viewModel.isLoading) revealed = true }

    val sortLabels = listOf(
        AppLocale.wishlistSortRecent,
        AppLocale.wishlistSortName,
        AppLocale.wishlistSortSize,
        AppLocale.wishlistSortProgress
    )
    val sortOptions = listOf(
        WishlistListSort.RECENT,
        WishlistListSort.NAME,
        WishlistListSort.SIZE,
        WishlistListSort.PROGRESS
    )

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
                                text = AppLocale.wishlistListsCount(overview.lists) +
                                    " · " + AppLocale.wishlistCardsCount(overview.cards) +
                                    " · " + AppLocale.wishlistOwnedCount(overview.owned),
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
                onClick = {
                    if (viewModel.canCreateWishlist(isPremium)) {
                        showCreateDialog = true
                    } else {
                        showPremiumDialog = true
                    }
                },
                containerColor = AppColors.purple,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = AppLocale.wishlistCreate, tint = AppColors.textPrimary)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        index = index
                    )
                }
            }
            return@Scaffold
        }

        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    icon = Icons.Default.Favorite,
                    title = AppLocale.wishlistEmpty,
                    subtitle = AppLocale.wishlistEmptySubtitle
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
        ) {
            item(key = "summary") {
                CascadeIn(index = 0, visible = revealed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            label = AppLocale.wishlistTitle,
                            value = overview.lists.toString(),
                            icon = Icons.Default.Style,
                            accent = AppColors.purple,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = AppLocale.wishlistFilterMissing,
                            value = overview.missing.toString(),
                            icon = Icons.Default.Favorite,
                            accent = AppColors.red,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = AppLocale.wishlistFilterHigh,
                            value = overview.highPriority.toString(),
                            icon = Icons.Default.PriorityHigh,
                            accent = AppColors.orange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // La ricerca compare solo quando c'e' davvero qualcosa da cercare:
            // sopra due liste e' una riga di interfaccia che non serve a nulla.
            if (rows.size >= 3) {
                item(key = "search") {
                    CascadeIn(index = 1, visible = revealed) {
                        CollectorSearchField(
                            value = query,
                            onValueChange = { query = it },
                            hint = AppLocale.wishlistSearchLists
                        )
                    }
                }
            }

            item(key = "sort") {
                CascadeIn(index = 2, visible = revealed) {
                    SortChipRow(
                        labels = sortLabels,
                        selectedIndex = sortOptions.indexOf(sort),
                        onSelect = { index -> sort = sortOptions[index] },
                        accent = AppColors.purple
                    )
                }
            }

            if (visibleRows.isEmpty()) {
                item(key = "no-results") {
                    EmptyStateView(
                        icon = Icons.Default.Favorite,
                        title = AppLocale.wishlistNoListResults,
                        subtitle = ""
                    )
                }
            }

            items(visibleRows, key = { it.id }) { row ->
                WishlistListRow(
                    row = row,
                    onClick = { onWishlistClick(row.id) },
                    onEdit = { wishlistToEdit = viewModel.getWishlistById(row.id) },
                    onDelete = { wishlistToDelete = viewModel.getWishlistById(row.id) }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateWishlistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, iconKey, budget ->
                viewModel.createWishlist(name, iconKey, isPremium, budget) { success ->
                    if (success) showCreateDialog = false
                }
            },
            isSaving = viewModel.isSaving
        )
    }

    wishlistToEdit?.let { wishlist ->
        CreateWishlistDialog(
            onDismiss = { wishlistToEdit = null },
            onConfirm = { name, iconKey, budget ->
                viewModel.updateWishlistDetails(wishlist.id, name, iconKey, budget) { success ->
                    if (success) wishlistToEdit = null
                }
            },
            isSaving = viewModel.isSaving,
            initialName = wishlist.name,
            initialIconKey = wishlist.iconKey,
            initialBudget = wishlist.budget,
            titleText = AppLocale.wishlistEdit,
            confirmText = AppLocale.save
        )
    }

    wishlistToDelete?.let { wishlist ->
        AlertDialog(
            onDismissRequest = { wishlistToDelete = null },
            containerColor = AppColors.surface,
            title = { Text(AppLocale.wishlistDeleteTitle, color = AppColors.textPrimary) },
            text = { Text(AppLocale.wishlistDeleteMessage, color = AppColors.textSecondary) },
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

@Composable
private fun WishlistListRow(
    row: WishlistRow,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.card.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .border(1.dp, AppColors.textMuted.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .pressScale { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WishlistIconBadge(iconKey = row.iconKey, size = 40)

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = row.name,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = AppLocale.wishlistCardsCount(row.total) +
                    " · " + AppLocale.wishlistOwnedCount(row.owned),
                color = AppColors.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (row.total > 0) {
                FillBar(
                    percent = row.percent,
                    accent = AppColors.purple,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (row.highPriority > 0 || row.hasBudget) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (row.highPriority > 0) {
                        WishlistTag(
                            text = AppLocale.wishlistHighPriorityCount(row.highPriority),
                            color = AppColors.orange,
                            icon = Icons.Default.PriorityHigh
                        )
                    }
                    if (row.hasBudget) {
                        WishlistTag(
                            text = formatEurCompact(row.budget),
                            color = AppColors.green,
                            icon = Icons.Default.Savings
                        )
                    }
                }
            }
        }

        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = AppLocale.wishlistEdit, tint = AppColors.textMuted)
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.DeleteOutline, contentDescription = AppLocale.delete, tint = AppColors.textMuted)
        }
    }
}
