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
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.AlbumViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumListScreen(
    onBack: () -> Unit,
    onCreateAlbum: (String?) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPremiumRequired: () -> Unit = {},
    viewModel: AlbumViewModel = viewModel()
) {
    val premiumManager = remember { PremiumManager.getInstance() }
    var showDeleteDialog by remember { mutableStateOf<Album?>(null) }
    var showPremiumDialog by remember { mutableStateOf(false) }

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
        } else if (viewModel.albums.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PhotoAlbum,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        AppLocale.albumEmpty,
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        AppLocale.albumEmptySubtitle,
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
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
