package com.emabuia.pokevault.ui.illustrator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.components.CompletionBanner
import com.emabuia.pokevault.ui.components.EmptyStateView
import com.emabuia.pokevault.ui.components.LabSearchField
import com.emabuia.pokevault.ui.components.ProgressRing
import com.emabuia.pokevault.ui.components.SkeletonBlock
import com.emabuia.pokevault.ui.components.StatTile
import com.emabuia.pokevault.ui.pokedex.CardDetailBottomSheet
import com.emabuia.pokevault.ui.pokedex.TcgCardCompactItem
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.IllustratorViewModel

/**
 * La pagina di un illustratore.
 *
 * E' deliberatamente **una pagina espansione**: stessa griglia a tre colonne,
 * stessa tessera [TcgCardCompactItem], quindi stesso linguaggio -- bordo e
 * spunta verdi su cio' che si possiede, velo scuro su cio' che manca. Chi sa
 * gia' sfogliare un set sa gia' sfogliare un artista, e non c'e' niente di
 * nuovo da imparare.
 *
 * L'unica differenza vera: le carte di un illustratore attraversano decine di
 * espansioni, quindi sono raggruppate per set e i gruppi si chiudono.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IllustratorDetailScreen(
    illustratorKey: String,
    onBack: () -> Unit,
    viewModel: IllustratorViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.loadIndex(context) }
    // Il dettaglio si carica quando l'indice c'e': arrivando qui da un link
    // diretto, la voce puo' non esserci ancora.
    LaunchedEffect(illustratorKey, viewModel.entries.size) {
        viewModel.loadDetail(context, illustratorKey)
    }

    val row = viewModel.rowFor(illustratorKey)
    val displayName = row?.displayName ?: illustratorKey
    val cards = viewModel.detailCards

    var query by remember { mutableStateOf("") }
    var showOnlyMissing by remember { mutableStateOf(false) }
    var showOnlyOwned by remember { mutableStateOf(false) }
    var collapsedSets by remember { mutableStateOf(setOf<String>()) }
    var selectedCard by remember { mutableStateOf<TcgCard?>(null) }
    var quickAddCardId by remember { mutableStateOf<String?>(null) }

    val filtered = remember(cards, query, showOnlyMissing, showOnlyOwned, viewModel.ownedCards) {
        val q = query.trim().lowercase()
        cards.filter { card ->
            val owned = viewModel.isOwned(card.id)
            val matchesQuery = q.isEmpty() ||
                card.name.lowercase().contains(q) ||
                card.number.lowercase().contains(q)
            val matchesOwnership = when {
                showOnlyMissing -> !owned
                showOnlyOwned -> owned
                else -> true
            }
            matchesQuery && matchesOwnership
        }
    }

    // I gruppi in ordine cronologico, non alfabetico: i set di un artista
    // raccontano la sua carriera solo se messi in fila per data. La data arriva
    // dal manifest D1 attraverso il TcgSet sintetico.
    val groups = remember(filtered) {
        filtered
            .groupBy { it.set?.id.orEmpty() }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<TcgCard>>> {
                    it.value.firstOrNull()?.set?.releaseDate.orEmpty()
                }.thenBy { it.key }
            )
            .map { (setId, setCards) ->
                IllustratorSetGroup(
                    setId = setId,
                    setName = setCards.firstOrNull()?.set?.name.orEmpty().ifBlank { setId },
                    cards = setCards.sortedBy {
                        it.number.replace(Regex("[^0-9]"), "").toIntOrNull() ?: Int.MAX_VALUE
                    }
                )
            }
    }

    selectedCard?.let { sheetCard ->
        CardDetailBottomSheet(
            card = sheetCard,
            isOwned = viewModel.isOwned(sheetCard.id),
            isLoading = viewModel.isAddingCard == sheetCard.id,
            onAddCard = { variant, quantity, condition, language ->
                viewModel.addCard(sheetCard, variant, quantity, condition, language)
            },
            onRemoveCard = { viewModel.removeCard(sheetCard); selectedCard = null },
            onDismiss = { selectedCard = null },
            cardList = filtered,
            onCardChange = { selectedCard = it },
            ownedVariants = viewModel.ownedVariants[sheetCard.id].orEmpty()
        )
    }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            displayName,
                            color = AppColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (row != null) {
                            Text(
                                AppLocale.illustratorCardsAndSets(row.total, row.expansionCount),
                                color = AppColors.textMuted,
                                fontSize = 11.sp
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
                actions = {
                    IconButton(onClick = { viewModel.toggleFollow(illustratorKey) }) {
                        val followed = row?.isFollowed == true
                        Icon(
                            imageVector = if (followed) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (followed) AppLocale.illustratorUnfollow
                            else AppLocale.illustratorFollow,
                            tint = if (followed) AppColors.gold else AppColors.textMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (row != null) {
                item(span = { GridItemSpan(3) }) {
                    IllustratorHeader(
                        key = illustratorKey,
                        displayName = displayName,
                        owned = row.owned,
                        total = row.total,
                        percent = row.percent,
                        expansions = row.expansionCount
                    )
                }

                if (row.isComplete) {
                    item(span = { GridItemSpan(3) }) {
                        CompletionBanner(
                            title = AppLocale.illustratorCompleteTitle,
                            subtitle = AppLocale.illustratorCompleteSubtitle(displayName)
                        )
                    }
                }
            }

            item(span = { GridItemSpan(3) }) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    LabSearchField(
                        value = query,
                        onValueChange = { query = it },
                        hint = AppLocale.searchCards,
                        accent = AppColors.purple
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // I due filtri si escludono a vicenda, come nel set:
                        // "mancanti e possedute" insieme non vuol dire niente.
                        OwnershipChip(
                            label = AppLocale.illustratorFilterMissing,
                            selected = showOnlyMissing,
                            onClick = {
                                showOnlyMissing = !showOnlyMissing
                                showOnlyOwned = false
                            }
                        )
                        OwnershipChip(
                            label = AppLocale.illustratorFilterOwned,
                            selected = showOnlyOwned,
                            onClick = {
                                showOnlyOwned = !showOnlyOwned
                                showOnlyMissing = false
                            }
                        )
                    }
                }
            }

            // Indice arrivato ma la voce non c'e': un link a un artista che il
            // catalogo non conosce (piu'). Senza questo ramo la pagina restava
            // vuota per sempre, con solo la ricerca sopra.
            if (!viewModel.isLoading && row == null) {
                item(span = { GridItemSpan(3) }) {
                    EmptyStateView(
                        icon = Icons.Default.Brush,
                        title = AppLocale.illustratorsNoMatch,
                        subtitle = AppLocale.illustratorsEmptySubtitle
                    )
                }
                return@LazyVerticalGrid
            }

            // Anche mentre arriva l'indice, non solo le carte: prima di quel
            // momento il dettaglio non e' nemmeno partito, e la griglia vuota
            // sembrava un artista senza carte.
            if ((viewModel.isLoading || viewModel.isDetailLoading) && cards.isEmpty()) {
                items(9) { index ->
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                        shape = RoundedCornerShape(10.dp),
                        index = index
                    )
                }
                return@LazyVerticalGrid
            }

            for (group in groups) {
                val isCollapsed = group.setId in collapsedSets
                val ownedInGroup = group.cards.count { viewModel.isOwned(it.id) }

                item(span = { GridItemSpan(3) }, key = "header::${group.setId}") {
                    SetGroupHeader(
                        name = group.setName,
                        owned = ownedInGroup,
                        total = group.cards.size,
                        isCollapsed = isCollapsed,
                        onToggle = {
                            collapsedSets = if (isCollapsed) collapsedSets - group.setId
                            else collapsedSets + group.setId
                        }
                    )
                }

                if (!isCollapsed) {
                    items(group.cards, key = { "${group.setId}::${it.id}" }) { card ->
                        TcgCardCompactItem(
                            card = card,
                            isOwned = viewModel.isOwned(card.id),
                            isWishlisted = false,
                            isAdding = viewModel.isAddingCard == card.id,
                            ownedVariants = viewModel.ownedVariants[card.id].orEmpty(),
                            isPopupOpen = quickAddCardId == card.id,
                            onClick = { selectedCard = card },
                            // Come nella pagina espansione: una stampa sola si
                            // aggiunge subito, piu' stampe aprono la scelta
                            // sulla tessera. Le stampe possibili dipendono dalla
                            // data del set, che qui arriva dal manifest D1.
                            onQuickAddClick = {
                                val variants = CardOptions.getVariantsForCard(
                                    card.tcgplayer?.prices?.keys ?: emptySet(),
                                    card.rarity,
                                    card.set?.releaseDate
                                )
                                if (variants.size <= 1) {
                                    viewModel.addCard(card, variants.firstOrNull() ?: "Holo", 1, "Near Mint", "")
                                } else {
                                    quickAddCardId = if (quickAddCardId == card.id) null else card.id
                                }
                            },
                            onVariantSelected = { variant ->
                                viewModel.addCard(card, variant, 1, "Near Mint", "")
                                quickAddCardId = null
                            },
                            onWishlistClick = { }
                        )
                    }
                }
            }
        }
    }
}

private data class IllustratorSetGroup(
    val setId: String,
    val setName: String,
    val cards: List<TcgCard>
)

@Composable
private fun IllustratorHeader(
    key: String,
    displayName: String,
    owned: Int,
    total: Int,
    percent: Float,
    expansions: Int
) {
    // `card` sopra `background` col filo di bordo: nel tema chiaro `surface` e
    // `card` sono lo stesso bianco, e un riquadro su surface sparirebbe.
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.card, RoundedCornerShape(18.dp))
            .border(1.dp, AppColors.textMuted.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IllustratorAvatar(key = key, displayName = displayName, size = 52.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    color = AppColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$owned/$total",
                    color = AppColors.textSecondary,
                    fontSize = 13.sp
                )
            }
            ProgressRing(percent = percent, accent = AppColors.purple, size = 54.dp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = AppLocale.chaseStatOwned,
                value = owned.toString(),
                icon = Icons.Default.Style,
                accent = AppColors.green,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = AppLocale.chaseStatMissing,
                value = (total - owned).coerceAtLeast(0).toString(),
                icon = Icons.Default.Style,
                accent = AppColors.red,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = AppLocale.illustratorsSets(expansions),
                value = expansions.toString(),
                icon = Icons.Default.PhotoLibrary,
                accent = AppColors.purple,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SetGroupHeader(
    name: String,
    owned: Int,
    total: Int,
    isCollapsed: Boolean,
    onToggle: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp)
    ) {
        Text(
            name,
            color = AppColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$owned/$total",
            color = if (owned >= total) AppColors.green else AppColors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = null,
            tint = AppColors.textMuted,
            modifier = Modifier.padding(start = 6.dp).size(18.dp)
        )
    }
}

@Composable
private fun OwnershipChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = AppColors.purple
    Box(
        modifier = Modifier
            .background(
                if (selected) accent.copy(alpha = 0.18f) else AppColors.card,
                RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.55f) else AppColors.textMuted.copy(alpha = 0.18f),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) accent else AppColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
