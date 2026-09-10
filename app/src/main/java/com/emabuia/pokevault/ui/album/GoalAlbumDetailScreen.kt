package com.emabuia.pokevault.ui.album

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.emabuia.pokevault.data.model.GoalAlbum
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.GoalAlbumViewModel
import com.emabuia.pokevault.viewmodel.GoalProgress
import kotlinx.coroutines.launch

private enum class ChaseTab { ALL, OWNED, MISSING, DUPLICATES }

private fun safeImageUrl(url: String): String {
    return url
        .replace(" ", "%20")
        .replace("(", "%28")
        .replace(")", "%29")
}

@Composable
private fun CardImageFallback(card: TcgCard) {
    val series = card.set?.series?.takeIf { it.isNotBlank() } ?: "-"
    val setName = card.set?.name?.takeIf { it.isNotBlank() } ?: "-"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .background(AppColors.surface)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = card.name,
                color = AppColors.textPrimary,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = series,
                color = AppColors.textMuted,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = setName,
                color = AppColors.textMuted,
                fontSize = 8.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalAlbumDetailScreen(
    goalAlbumId: String,
    onBack: () -> Unit,
    viewModel: GoalAlbumViewModel = viewModel()
) {
    val album = viewModel.getGoalAlbumById(goalAlbumId)
    var selectedTab by remember { mutableStateOf(ChaseTab.ALL) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Carichiamo le TcgCard per il dettaglio (da cache/api progressivamente)
    var targetCards by remember { mutableStateOf<List<TcgCard>>(emptyList()) }
    var isLoadingCards by remember { mutableStateOf(false) }
    val tcgRepo = remember { com.emabuia.pokevault.data.remote.RepositoryProvider.tcgRepository }

    LaunchedEffect(album) {
        if (album == null) return@LaunchedEffect
        isLoadingCards = true
        targetCards = when (album.criteriaType) {
            com.emabuia.pokevault.data.model.GoalCriteriaType.SET ->
                tcgRepo.getCardsBySet(album.criteriaValue).getOrElse { emptyList() }
            else ->
                tcgRepo.searchCards(buildTcgQuery(album)).getOrElse { emptyList() }
        }
        isLoadingCards = false
    }

    if (album == null) {
        // Vedi AlbumDetailScreen: prima era uno spinner incondizionato, quindi
        // infinito per un chase cancellato o con id non valido.
        com.emabuia.pokevault.ui.components.NotFoundOrLoadingView(
            isLoading = viewModel.isLoading,
            message = AppLocale.chaseNotFound,
            onBack = onBack,
            accentColor = AppColors.orange
        )
        return
    }

    val progress = remember(album, viewModel.ownedCards, targetCards) {
        viewModel.getProgress(album, targetCards)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress.percentage / 100f,
        animationSpec = tween(800),
        label = "progress"
    )

    Scaffold(
        containerColor = AppColors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(album.name, color = AppColors.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppLocale.back, tint = AppColors.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = AppLocale.delete, tint = AppColors.textMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Header progresso ──────────────────────────────────────────
            ChaseProgressHeader(progress = progress, animatedProgress = animatedProgress)

            // ── Tab row ───────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = AppColors.surface,
                contentColor = AppColors.orange,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = AppColors.orange
                    )
                }
            ) {
                ChaseTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = tab.toLabel(progress),
                                fontSize = 12.sp,
                                color = if (selectedTab == tab) AppColors.orange else AppColors.textSecondary,
                                fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (isLoadingCards) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.orange)
                }
            } else {
                // ── Grid carte ────────────────────────────────────────────
                // Insieme degli id posseduti, costruito una volta sola.
                // Prima ogni cella faceva ownedCards.any { ... trim() == ... trim() }:
                // una scansione lineare dell'intera collezione piu' due
                // allocazioni di stringa per confronto, per cella, per frame.
                val ownedApiIds = remember(viewModel.ownedCards) {
                    viewModel.ownedCards
                        .asSequence()
                        .filter { it.quantity >= 1 }
                        .map { it.apiCardId.trim() }
                        .toHashSet()
                }

                val displayCards: List<TcgCard> = remember(selectedTab, targetCards, ownedApiIds, progress) {
                    when (selectedTab) {
                        ChaseTab.ALL -> targetCards
                        ChaseTab.OWNED -> targetCards.filter { it.id.trim() in ownedApiIds }
                        ChaseTab.MISSING -> progress.missing
                        ChaseTab.DUPLICATES -> {
                            val dupIds = progress.duplicates.map { it.apiCardId.trim() }.toSet()
                            targetCards.filter { it.id.trim() in dupIds }
                        }
                    }
                }

                if (displayCards.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedTab.emptyMessage(),
                            color = AppColors.textMuted,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
                    ) {
                        itemsIndexed(displayCards, key = { _, c -> c.id }) { _, card ->
                            val isOwned = card.id.trim() in ownedApiIds
                            ChaseCardItem(
                                card = card,
                                isOwned = isOwned,
                                onAddTap = {
                                    viewModel.addMissingCardToCollection(card) { success ->
                                        scope.launch {
                                            val message = if (success) {
                                                if (AppLocale.isItalian) "Carta aggiunta alla collezione" else "Card added to collection"
                                            } else {
                                                if (AppLocale.isItalian) "Errore durante l'aggiunta" else "Error while adding card"
                                            }
                                            snackbarHostState.showSnackbar(message)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = AppColors.surface,
            title = { Text(AppLocale.chaseDeleteTitle, color = AppColors.textPrimary) },
            text = { Text(AppLocale.chaseDeleteMessage, color = AppColors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGoalAlbum(goalAlbumId)
                    showDeleteDialog = false
                    onBack()
                }) { Text(AppLocale.delete, color = com.emabuia.pokevault.ui.theme.AppColors.red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(AppLocale.cancel, color = AppColors.textSecondary)
                }
            }
        )
    }
}

// ── Progress Header ────────────────────────────────────────────────────────────

@Composable
private fun ChaseProgressHeader(progress: GoalProgress, animatedProgress: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.surface)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Anello circolare
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(72.dp),
                color = AppColors.background,
                strokeWidth = 7.dp,
                strokeCap = StrokeCap.Round
            )
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(72.dp),
                color = AppColors.orange,
                strokeWidth = 7.dp,
                strokeCap = StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${progress.percentage.toInt()}%",
                    color = AppColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            StatRow(label = AppLocale.chaseStatTotal, value = "${progress.total}")
            StatRow(label = AppLocale.chaseStatOwned, value = "${progress.owned}", color = com.emabuia.pokevault.ui.theme.AppColors.green)
            StatRow(label = AppLocale.chaseStatMissing, value = "${progress.missing.size}", color = com.emabuia.pokevault.ui.theme.AppColors.red)
            StatRow(label = AppLocale.chaseStatDuplicates, value = "${progress.duplicates.size}", color = AppColors.blue)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color = AppColors.textPrimary) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AppColors.textSecondary, fontSize = 12.sp)
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Card Item ──────────────────────────────────────────────────────────────────

@Composable
private fun ChaseCardItem(card: TcgCard, isOwned: Boolean, onAddTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !isOwned, onClick = onAddTap)
    ) {
        SubcomposeAsyncImage(
            model = safeImageUrl(card.images.small),
            contentDescription = card.name,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isOwned) 1f else 0.35f),
            error = { CardImageFallback(card) }
        )
        if (!isOwned) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(AppColors.background.copy(alpha = 0.65f))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = AppLocale.chaseCardMissingLabel,
                    color = AppColors.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun buildTcgQuery(album: GoalAlbum): String = when (album.criteriaType) {
    com.emabuia.pokevault.data.model.GoalCriteriaType.RARITY -> "rarity:\"${album.criteriaValue}\""
    com.emabuia.pokevault.data.model.GoalCriteriaType.SUPERTYPE -> "supertype:\"${album.criteriaValue}\""
    com.emabuia.pokevault.data.model.GoalCriteriaType.TYPE -> "types:\"${album.criteriaValue}\""
    else -> ""
}

private fun ChaseTab.toLabel(progress: GoalProgress): String = when (this) {
    ChaseTab.ALL -> "Tutte (${progress.total})"
    ChaseTab.OWNED -> "Possedute (${progress.owned})"
    ChaseTab.MISSING -> "Mancanti (${progress.missing.size})"
    ChaseTab.DUPLICATES -> "Doppie (${progress.duplicates.size})"
}

private fun ChaseTab.emptyMessage(): String = when (this) {
    ChaseTab.ALL -> "Nessuna carta disponibile"
    ChaseTab.OWNED -> "Non possiedi ancora nessuna carta di questo Chase"
    ChaseTab.MISSING -> "🎉 Hai completato questo Chase!"
    ChaseTab.DUPLICATES -> "Nessun duplicato"
}
