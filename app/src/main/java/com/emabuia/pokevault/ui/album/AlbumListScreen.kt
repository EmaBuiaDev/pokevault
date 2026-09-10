package com.emabuia.pokevault.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.data.model.GoalAlbum
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.AlbumViewModel
import com.emabuia.pokevault.viewmodel.GoalAlbumViewModel

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

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        AppLocale.albumTitle,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CollectorLabCardsRow(
                albumCount = viewModel.albums.size,
                chaseCount = goalViewModel.goalAlbums.size,
                onAlbumClick = {
                    onOpenAlbumList()
                },
                onChaseClick = {
                    if (goalViewModel.goalAlbums.isEmpty()) {
                        if (goalViewModel.canCreate()) {
                            onCreateChase()
                        } else {
                            showChasePremiumDialog = true
                        }
                    } else {
                        onOpenChaseList()
                    }
                }
            )
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
}

@Composable
private fun CollectorLabCardsRow(
    albumCount: Int,
    chaseCount: Int,
    onAlbumClick: () -> Unit,
    onChaseClick: () -> Unit
) {
    val albumSubtitle = if (albumCount == 0) {
        AppLocale.collectorAlbumSubtitle
    } else {
        AppLocale.collectorAlbumCount(albumCount)
    }
    val chaseSubtitle = if (chaseCount == 0) {
        AppLocale.collectorChaseSubtitle
    } else {
        AppLocale.collectorChaseCount(chaseCount)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CollectorLabCard(
            title = AppLocale.collectorAlbumTitle,
            subtitle = albumSubtitle,
            icon = Icons.Default.PhotoLibrary,
            accent = AppColors.orange,
            onClick = onAlbumClick,
            modifier = Modifier.weight(1f)
        )
        CollectorLabCard(
            title = AppLocale.collectorChaseTitle,
            subtitle = chaseSubtitle,
            icon = Icons.Default.TrackChanges,
            accent = AppColors.red,
            onClick = onChaseClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CollectorLabCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(108.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = AppColors.textMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── New Chase Card (CTA) ──────────────────────────────────────────────────────

@Composable
private fun NewChaseCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.orange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = AppColors.orange, modifier = Modifier.size(26.dp))
            }
            Column {
                Text(AppLocale.newChaseLabel, color = AppColors.orange, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(AppLocale.newChaseSubtitle, color = AppColors.textMuted, fontSize = 12.sp)
            }
        }
    }
}

// ── Chase Card ────────────────────────────────────────────────────────────────

@Composable
internal fun ChaseCard(
    goalAlbum: GoalAlbum,
    ownedCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val pct = if (goalAlbum.targetCardApiIds.isEmpty()) 0f
    else (ownedCount.toFloat() / goalAlbum.targetCardApiIds.size * 100f).coerceIn(0f, 100f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini progress ring
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(52.dp),
                    color = AppColors.background,
                    strokeWidth = 4.dp
                )
                CircularProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier.size(52.dp),
                    color = AppColors.orange,
                    strokeWidth = 4.dp
                )
                Text(
                    "${pct.toInt()}%",
                    color = AppColors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    goalAlbum.name,
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    AppLocale.chaseCardsProgress(ownedCount, goalAlbum.targetCardApiIds.size),
                    color = AppColors.textMuted,
                    fontSize = 12.sp
                )
                Text(
                    goalAlbum.criteriaType.displayName() + (if (goalAlbum.criteriaValue.isNotBlank()) " · ${goalAlbum.criteriaValue}" else ""),
                    color = AppColors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = AppLocale.delete, tint = AppColors.textMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

internal fun com.emabuia.pokevault.data.model.GoalCriteriaType.displayName(): String = when (this) {
    com.emabuia.pokevault.data.model.GoalCriteriaType.SET -> AppLocale.criteriaSet
    com.emabuia.pokevault.data.model.GoalCriteriaType.RARITY -> AppLocale.criteriaRarity
    com.emabuia.pokevault.data.model.GoalCriteriaType.SUPERTYPE -> AppLocale.criteriaSupertype
    com.emabuia.pokevault.data.model.GoalCriteriaType.TYPE -> AppLocale.criteriaType
    com.emabuia.pokevault.data.model.GoalCriteriaType.CUSTOM -> AppLocale.criteriaCustom
}

@Composable
fun AlbumCard(
    album: Album,
    cardsCount: Int,
    coverUrl: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = getThemeColors(album.theme)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover image or placeholder
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(themeColors)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.PhotoAlbum,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (album.description.isNotBlank()) {
                    Text(
                        text = album.description,
                        color = AppColors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = AppLocale.albumSlots(cardsCount, album.size),
                        color = AppColors.textMuted,
                        fontSize = 12.sp
                    )
                    if (album.pokemonType.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppLocale.translateType(album.pokemonType),
                            color = themeColors.first(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Actions
            Column {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = AppLocale.editAlbum,
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = AppLocale.delete,
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** Vedi [TypeColors]: i colori dei tipi stanno tutti in un punto solo. */
@Composable
fun getThemeColors(theme: String): List<Color> = TypeColors.gradientFor(theme)
