package com.emabuia.pokevault.ui.album

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
    onCreateChase: () -> Unit = {},
    onChaseClick: (String) -> Unit = {},
    onPremiumRequired: () -> Unit = {},
    viewModel: AlbumViewModel = viewModel(),
    goalViewModel: GoalAlbumViewModel = viewModel()
) {
    val premiumManager = remember { PremiumManager.getInstance() }
    var showDeleteDialog by remember { mutableStateOf<Album?>(null) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    var showChasePremiumDialog by remember { mutableStateOf(false) }
    var showDeleteChaseDialog by remember { mutableStateOf<GoalAlbum?>(null) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        AppLocale.myAlbums,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppLocale.back,
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (premiumManager.canCreateAlbum(viewModel.albums.size)) {
                        onCreateAlbum(null)
                    } else {
                        showPremiumDialog = true
                    }
                },
                containerColor = OrangeCard,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = AppLocale.createAlbum, tint = TextWhite)
            }
        }
    ) { padding ->
        if (viewModel.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = OrangeCard)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // ── Sezione Espositore ────────────────────────────────────
                item {
                    SectionHeader(
                        title = AppLocale.albumSectionEspositore,
                        subtitle = AppLocale.albumSectionEspositoreSubtitle
                    )
                }
                if (viewModel.albums.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PhotoAlbum, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(AppLocale.albumEmpty, color = TextMuted, fontSize = 14.sp)
                            }
                        }
                    }
                } else {
                    items(viewModel.albums, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            cardsCount = album.cardIds.size,
                            coverUrl = album.coverImageUrl.ifBlank {
                                val cards = viewModel.getCardsForAlbum(album)
                                cards.firstOrNull()?.imageUrl ?: ""
                            },
                            onClick = { onAlbumClick(album.id) },
                            onDelete = { showDeleteDialog = album },
                            onEdit = { onCreateAlbum(album.id) }
                        )
                    }
                }

                // ── Sezione Chase ─────────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    SectionHeader(
                        title = AppLocale.albumSectionChase,
                        subtitle = AppLocale.albumSectionChaseSubtitle
                    )
                }
                item {
                    // Card "Nuovo Chase" sempre visibile come primo elemento
                    NewChaseCard(
                        onClick = {
                            if (goalViewModel.canCreate()) {
                                onCreateChase()
                            } else {
                                showChasePremiumDialog = true
                            }
                        }
                    )
                }
                items(goalViewModel.goalAlbums, key = { it.id }) { goalAlbum ->
                    ChaseCard(
                        goalAlbum = goalAlbum,
                        ownedCount = goalAlbum.targetCardApiIds.count { apiId ->
                            goalViewModel.ownedCards.any { pc -> pc.apiCardId.trim() == apiId }
                        },
                        onClick = { onChaseClick(goalAlbum.id) },
                        onDelete = { showDeleteChaseDialog = goalAlbum }
                    )
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { album ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = DarkSurface,
            title = {
                Text(AppLocale.albumDeleteTitle, color = TextWhite)
            },
            text = {
                Text(AppLocale.albumDeleteMessage, color = TextGray)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAlbum(album.id)
                    showDeleteDialog = null
                }) {
                    Text(AppLocale.delete, color = RedCard)
                }
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
            title = AppLocale.premiumAlbumLimitTitle,
            message = AppLocale.premiumAlbumLimitMessage,
            onDismiss = { showPremiumDialog = false },
            onUpgrade = {
                showPremiumDialog = false
                onPremiumRequired()
            }
        )
    }

    // Chase delete dialog
    showDeleteChaseDialog?.let { goalAlbum ->
        AlertDialog(
            onDismissRequest = { showDeleteChaseDialog = null },
            containerColor = DarkSurface,
            title = { Text(AppLocale.chaseDeleteTitle, color = TextWhite) },
            text = { Text(AppLocale.chaseDeleteMessage, color = TextGray) },
            confirmButton = {
                TextButton(onClick = {
                    goalViewModel.deleteGoalAlbum(goalAlbum.id)
                    showDeleteChaseDialog = null
                }) { Text(AppLocale.delete, color = RedCard) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChaseDialog = null }) {
                    Text(AppLocale.cancel, color = TextGray)
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

// ── Section Header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(title, color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextMuted, fontSize = 12.sp)
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
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
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
                    .background(OrangeCard.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = OrangeCard, modifier = Modifier.size(26.dp))
            }
            Column {
                Text(AppLocale.newChaseLabel, color = OrangeCard, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(AppLocale.newChaseSubtitle, color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

// ── Chase Card ────────────────────────────────────────────────────────────────

@Composable
private fun ChaseCard(
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
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
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
                    color = DarkBackground,
                    strokeWidth = 4.dp
                )
                CircularProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier.size(52.dp),
                    color = OrangeCard,
                    strokeWidth = 4.dp
                )
                Text(
                    "${pct.toInt()}%",
                    color = TextWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    goalAlbum.name,
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$ownedCount / ${goalAlbum.targetCardApiIds.size} carte",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Text(
                    goalAlbum.criteriaType.displayName() + (if (goalAlbum.criteriaValue.isNotBlank()) " · ${goalAlbum.criteriaValue}" else ""),
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = AppLocale.delete, tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun com.emabuia.pokevault.data.model.GoalCriteriaType.displayName(): String = when (this) {
    com.emabuia.pokevault.data.model.GoalCriteriaType.SET -> "Set"
    com.emabuia.pokevault.data.model.GoalCriteriaType.RARITY -> "Rarità"
    com.emabuia.pokevault.data.model.GoalCriteriaType.SUPERTYPE -> "Categoria"
    com.emabuia.pokevault.data.model.GoalCriteriaType.TYPE -> "Tipo"
    com.emabuia.pokevault.data.model.GoalCriteriaType.CUSTOM -> "Personalizzato"
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
        colors = CardDefaults.cardColors(containerColor = DarkCard)
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
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (album.description.isNotBlank()) {
                    Text(
                        text = album.description,
                        color = TextGray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = AppLocale.albumSlots(cardsCount, album.size),
                        color = TextMuted,
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
                        tint = TextMuted,
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
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

fun getThemeColors(theme: String): List<Color> {
    return when (theme) {
        "fire" -> listOf(Color(0xFFEF4444), Color(0xFFFF8C00))
        "water" -> listOf(Color(0xFF3B82F6), Color(0xFF06B6D4))
        "grass" -> listOf(Color(0xFF22C55E), Color(0xFF84CC16))
        "electric" -> listOf(Color(0xFFEAB308), Color(0xFFFBBF24))
        "dark" -> listOf(Color(0xFF6B21A8), Color(0xFF4C1D95))
        "psychic" -> listOf(Color(0xFFD946EF), Color(0xFF8B5CF6))
        else -> listOf(OrangeCard, OrangeCard.copy(alpha = 0.7f)) // classic
    }
}
