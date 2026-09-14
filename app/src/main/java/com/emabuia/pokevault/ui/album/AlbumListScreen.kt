package com.emabuia.pokevault.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.data.model.GoalAlbum
import com.emabuia.pokevault.ui.components.CascadeIn
import com.emabuia.pokevault.ui.components.FillBar
import com.emabuia.pokevault.ui.components.SkeletonBlock
import com.emabuia.pokevault.ui.components.StatTile
import com.emabuia.pokevault.ui.components.formatEurCompact
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.ChaseRow
import com.emabuia.pokevault.util.CollectorLab
import com.emabuia.pokevault.util.CollectorSummary
import com.emabuia.pokevault.viewmodel.AlbumViewModel
import com.emabuia.pokevault.viewmodel.GoalAlbumViewModel

/**
 * L'ingresso del Collector Lab.
 *
 * Prima erano due riquadri in cima a una pagina per il resto vuota: aprire la
 * sezione non diceva niente di quello che c'era dentro. Ora il riassunto e la
 * vetrina stanno qui, e le due liste restano a un tocco.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumListScreen(
    onBack: () -> Unit,
    onCreateAlbum: (String?) -> Unit,
    onAlbumClick: (String) -> Unit,
    onOpenAlbumList: () -> Unit = {},
    onOpenChaseList: () -> Unit = {},
    onCreateChase: () -> Unit = {},
    onChaseClick: (String) -> Unit = {},
    onPremiumRequired: () -> Unit = {},
    viewModel: AlbumViewModel = viewModel(),
    goalViewModel: GoalAlbumViewModel = viewModel()
) {
    var showChasePremiumDialog by remember { mutableStateOf(false) }
    var showAlbumPremiumDialog by remember { mutableStateOf(false) }
    val premiumManager = remember { com.emabuia.pokevault.data.billing.PremiumManager.getInstance() }

    val albumRows = viewModel.albumRows
    val chaseRows = remember(goalViewModel.goalAlbums, goalViewModel.ownedCards) {
        goalViewModel.chaseRows { it.criteriaSummary() }
    }
    val summary = remember(albumRows, chaseRows) { CollectorLab.summary(albumRows, chaseRows) }
    val spotlight = remember(chaseRows) { CollectorLab.spotlightChase(chaseRows) }
    val recentAlbums = remember(albumRows) {
        CollectorLab.sortAlbums(albumRows, com.emabuia.pokevault.util.AlbumSort.RECENT).take(6)
    }
    val isLoading = viewModel.isLoading || goalViewModel.isLoading
    val isEmpty = albumRows.isEmpty() && chaseRows.isEmpty()

    // La cascata si gioca una volta sola, al primo arrivo dei dati: rigiocarla a
    // ogni ricomposizione la trasformerebbe in un inciampo.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) { if (!isLoading) revealed = true }

    fun createAlbum() {
        if (premiumManager.canCreateAlbum(albumRows.size)) onCreateAlbum(null)
        else showAlbumPremiumDialog = true
    }

    fun createChase() {
        if (goalViewModel.canCreate()) onCreateChase() else showChasePremiumDialog = true
    }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            AppLocale.albumTitle,
                            color = AppColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            AppLocale.collectorLabSubtitle,
                            color = AppColors.textMuted,
                            fontSize = 11.sp
                        )
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.background
                )
            )
        }
    ) { padding ->
        if (isLoading && isEmpty) {
            CollectorLabSkeleton(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)
        ) {
            item(key = "summary") {
                CascadeIn(index = 0, visible = revealed) {
                    CollectorSummaryCard(summary = summary)
                }
            }

            item(key = "entries") {
                CascadeIn(index = 1, visible = revealed) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CollectorEntryCard(
                            title = AppLocale.collectorAlbumTitle,
                            subtitle = if (albumRows.isEmpty()) AppLocale.collectorAlbumSubtitle
                            else AppLocale.collectorAlbumCount(albumRows.size),
                            icon = Icons.Default.PhotoLibrary,
                            accent = AppColors.orange,
                            previewUrls = recentAlbums.flatMap { it.previewUrls }.take(3),
                            footer = if (albumRows.isEmpty()) null
                            else AppLocale.albumSlots(summary.cardsInAlbums, albumRows.sumOf { it.size }),
                            onClick = { if (albumRows.isEmpty()) createAlbum() else onOpenAlbumList() },
                            modifier = Modifier.weight(1f)
                        )
                        CollectorEntryCard(
                            title = AppLocale.collectorChaseTitle,
                            subtitle = if (chaseRows.isEmpty()) AppLocale.collectorChaseSubtitle
                            else AppLocale.collectorChaseCount(chaseRows.size),
                            icon = Icons.Default.TrackChanges,
                            accent = AppColors.red,
                            previewUrls = emptyList(),
                            footer = if (chaseRows.isEmpty()) null
                            else AppLocale.collectorChasesDone(summary.chasesCompleted, chaseRows.size),
                            ringPercent = if (chaseRows.isEmpty()) null else summary.averageChasePercent,
                            onClick = { if (chaseRows.isEmpty()) createChase() else onOpenChaseList() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (spotlight != null) {
                item(key = "spotlight") {
                    CascadeIn(index = 2, visible = revealed) {
                        ChaseSpotlightCard(
                            row = spotlight,
                            onClick = { onChaseClick(spotlight.id) }
                        )
                    }
                }
            }

            if (recentAlbums.isNotEmpty()) {
                item(key = "recent-header") {
                    CascadeIn(index = 3, visible = revealed) {
                        SectionHeader(
                            title = AppLocale.collectorRecentAlbums,
                            actionLabel = AppLocale.collectorSeeAll,
                            onAction = onOpenAlbumList
                        )
                    }
                }
                item(key = "recent-row") {
                    CascadeIn(index = 4, visible = revealed) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(recentAlbums, key = { it.id }) { row ->
                                RecentAlbumCard(row = row, onClick = { onAlbumClick(row.id) })
                            }
                        }
                    }
                }
            }

            if (isEmpty) {
                item(key = "empty") {
                    CollectorLabEmptyState(
                        onCreateAlbum = { createAlbum() },
                        onCreateChase = { createChase() }
                    )
                }
            } else {
                item(key = "quick-actions") {
                    CascadeIn(index = 5, visible = revealed) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            QuickActionButton(
                                label = AppLocale.albumCreateCta,
                                icon = Icons.Default.PhotoAlbum,
                                accent = AppColors.orange,
                                onClick = { createAlbum() },
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionButton(
                                label = AppLocale.chaseCreateCta,
                                icon = Icons.Default.TrackChanges,
                                accent = AppColors.red,
                                onClick = { createChase() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
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

    if (showAlbumPremiumDialog) {
        PremiumRequiredDialog(
            title = AppLocale.premiumAlbumLimitTitle,
            message = AppLocale.premiumAlbumLimitMessage,
            onDismiss = { showAlbumPremiumDialog = false },
            onUpgrade = {
                showAlbumPremiumDialog = false
                onPremiumRequired()
            }
        )
    }
}

// ── Riassunto ─────────────────────────────────────────────────────────────────

@Composable
private fun CollectorSummaryCard(summary: CollectorSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        AppColors.orange.copy(alpha = 0.18f),
                        AppColors.surface
                    )
                )
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    AppLocale.albumsSummary(summary.albums, summary.cardsInAlbums),
                    color = AppColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (summary.missingCards > 0) {
                        AppLocale.collectorMissingCards(summary.missingCards)
                    } else {
                        AppLocale.collectorChasesDone(summary.chasesCompleted, summary.chases)
                    },
                    color = AppColors.textMuted,
                    fontSize = 12.sp
                )
            }
            if (summary.chases > 0) {
                ProgressRing(
                    percent = summary.averageChasePercent,
                    size = 56.dp,
                    stroke = 5.dp,
                    accent = AppColors.red,
                    labelSize = 12
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = AppLocale.collectorStatCards,
                value = "${summary.cardsInAlbums}",
                icon = Icons.Default.Style,
                accent = AppColors.blue,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = AppLocale.collectorStatValue,
                value = formatEurCompact(summary.albumValue),
                icon = Icons.Default.Savings,
                accent = AppColors.green,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = AppLocale.collectorStatChaseAvg,
                value = "${summary.averageChasePercent.toInt()}%",
                icon = Icons.Default.TrackChanges,
                accent = AppColors.red,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── Le due porte: Album e Chase ───────────────────────────────────────────────

@Composable
private fun CollectorEntryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    previewUrls: List<String>,
    footer: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ringPercent: Float? = null
) {
    Column(
        modifier = modifier
            .height(158.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.surface)
            .pressScale(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            when {
                ringPercent != null -> ProgressRing(
                    percent = ringPercent,
                    size = 38.dp,
                    stroke = 3.dp,
                    accent = accent,
                    labelSize = 9
                )
                previewUrls.isNotEmpty() -> CoverCollage(
                    urls = previewUrls,
                    gradient = TypeColors.gradientFor("classic"),
                    slotSize = 34.dp
                )
            }
        }

        Column {
            Text(
                title,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                subtitle,
                color = AppColors.textMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (footer != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    footer,
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Vetrina: il chase piu' vicino al traguardo ────────────────────────────────

@Composable
private fun ChaseSpotlightCard(row: ChaseRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.surface)
            .pressScale(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.red.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    AppLocale.collectorSpotlightTitle,
                    color = AppColors.red,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = AppColors.textMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(percent = row.percent, size = 54.dp, accent = AppColors.red)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.name,
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    AppLocale.chaseCardsProgress(row.owned, row.total),
                    color = AppColors.textSecondary,
                    fontSize = 12.sp
                )
                Text(
                    AppLocale.collectorMissingCards(row.missing),
                    color = AppColors.textMuted,
                    fontSize = 11.sp
                )
            }
        }

        FillBar(percent = row.percent, modifier = Modifier.fillMaxWidth(), accent = AppColors.red)
    }
}

// ── Album recenti ─────────────────────────────────────────────────────────────

@Composable
private fun RecentAlbumCard(
    row: com.emabuia.pokevault.util.AlbumRow,
    onClick: () -> Unit
) {
    val gradient = getThemeColors(row.theme)
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.surface)
            .pressScale(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CoverCollage(
            urls = row.previewUrls,
            gradient = gradient,
            slotSize = 56.dp
        )
        Text(
            row.name,
            color = AppColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            AppLocale.albumSlots(row.used, row.size),
            color = AppColors.textMuted,
            fontSize = 11.sp
        )
        FillBar(percent = row.fillPercent, modifier = Modifier.fillMaxWidth(), accent = gradient.first())
    }
}

// ── Azioni rapide ─────────────────────────────────────────────────────────────

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.14f))
            .pressScale(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Stati vuoto e caricamento ─────────────────────────────────────────────────

@Composable
private fun CollectorLabEmptyState(
    onCreateAlbum: () -> Unit,
    onCreateChase: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.AutoAwesomeMotion,
            contentDescription = null,
            tint = AppColors.textMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(52.dp)
        )
        Text(
            AppLocale.collectorEmptyTitle,
            color = AppColors.textSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            AppLocale.collectorEmptySubtitle,
            color = AppColors.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionButton(
                label = AppLocale.albumCreateCta,
                icon = Icons.Default.PhotoAlbum,
                accent = AppColors.orange,
                onClick = onCreateAlbum,
                modifier = Modifier.weight(1f)
            )
            QuickActionButton(
                label = AppLocale.chaseCreateCta,
                icon = Icons.Default.TrackChanges,
                accent = AppColors.red,
                onClick = onCreateChase,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CollectorLabSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SkeletonBlock(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            shape = RoundedCornerShape(20.dp),
            index = 0
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(
                modifier = Modifier.weight(1f).height(158.dp),
                shape = RoundedCornerShape(18.dp),
                index = 1
            )
            SkeletonBlock(
                modifier = Modifier.weight(1f).height(158.dp),
                shape = RoundedCornerShape(18.dp),
                index = 2
            )
        }
        SkeletonBlock(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(18.dp),
            index = 3
        )
    }
}

// ── Helpers condivisi con le altre schermate della sezione ────────────────────

/** "Set · Paldea Evolved": il criterio del chase in una riga. */
internal fun GoalAlbum.criteriaSummary(): String =
    criteriaType.displayName() + (if (criteriaValue.isNotBlank()) " · $criteriaValue" else "")

internal fun com.emabuia.pokevault.data.model.GoalCriteriaType.displayName(): String = when (this) {
    com.emabuia.pokevault.data.model.GoalCriteriaType.SET -> AppLocale.criteriaSet
    com.emabuia.pokevault.data.model.GoalCriteriaType.RARITY -> AppLocale.criteriaRarity
    com.emabuia.pokevault.data.model.GoalCriteriaType.SUPERTYPE -> AppLocale.criteriaSupertype
    com.emabuia.pokevault.data.model.GoalCriteriaType.TYPE -> AppLocale.criteriaType
    com.emabuia.pokevault.data.model.GoalCriteriaType.CUSTOM -> AppLocale.criteriaCustom
}

/** Vedi [TypeColors]: i colori dei tipi stanno tutti in un punto solo. */
@Composable
fun getThemeColors(theme: String): List<Color> = TypeColors.gradientFor(theme)
