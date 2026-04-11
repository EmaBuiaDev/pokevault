package com.emabuia.pokevault.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.AlbumViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    viewModel: AlbumViewModel = viewModel()
) {
    val album = viewModel.getAlbumById(albumId)
    var showAddSheet by remember { mutableStateOf(false) }

    if (album == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = OrangeCard)
        }
        return
    }

    val albumCards = viewModel.getCardsForAlbum(album)
    val themeColors = getThemeColors(album.theme)
    val isFull = album.cardIds.size >= album.size

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            album.name,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            AppLocale.albumSlots(album.cardIds.size, album.size),
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
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
            if (!isFull) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = themeColors.first(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = AppLocale.albumAddCards, tint = Color.White)
                }
            }
        }
    ) { padding ->
        if (albumCards.isEmpty()) {
            // Empty album
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
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        AppLocale.albumAddCards,
                        color = TextGray,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { showAddSheet = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = themeColors.first().copy(alpha = 0.2f)
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = themeColors.first())
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(AppLocale.albumAddCards, color = themeColors.first())
                    }
                }
            }
        } else {
            // Card grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(albumCards, key = { it.id }) { card ->
                    AlbumCardItem(
                        card = card,
                        themeColors = themeColors,
                        onClick = { onCardClick(card.id) },
                        onRemove = { viewModel.removeCardFromAlbum(albumId, card.id) }
                    )
                }
                // Empty slots
                val emptySlots = album.size - albumCards.size
                if (emptySlots > 0) {
                    items(emptySlots) {
                        EmptySlot(themeColors = themeColors)
                    }
                }
            }
        }
    }

    // Add cards bottom sheet
    if (showAddSheet) {
        AddCardsBottomSheet(
            album = album,
            viewModel = viewModel,
            onDismiss = { showAddSheet = false },
            onCardClick = onCardClick
        )
    }
}

@Composable
private fun AlbumCardItem(
    card: PokemonCard,
    themeColors: List<Color>,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(card.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .padding(2.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = AppLocale.delete,
                tint = Color.White,
                modifier = Modifier
                    .size(18.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(9.dp)
                    )
                    .padding(2.dp)
            )
        }
    }
}

@Composable
private fun EmptySlot(themeColors: List<Color>) {
    Box(
        modifier = Modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = TextMuted.copy(alpha = 0.3f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCardsBottomSheet(
    album: Album,
    viewModel: AlbumViewModel,
    onDismiss: () -> Unit,
    onCardClick: (String) -> Unit
) {
    val filteredCards = viewModel.getFilteredCardsForAlbum(album)
    val isFull = album.cardIds.size >= album.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = AppLocale.albumAddCards,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            if (album.pokemonType.isNotBlank() || album.expansion.isNotBlank() || album.supertype.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (album.pokemonType.isNotBlank()) {
                        FilterTag(AppLocale.translateType(album.pokemonType))
                    }
                    if (album.expansion.isNotBlank()) {
                        FilterTag(album.expansion)
                    }
                    if (album.supertype.isNotBlank()) {
                        FilterTag(album.supertype)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isFull) {
                Text(
                    text = AppLocale.albumFull,
                    color = RedCard,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else if (filteredCards.isEmpty()) {
                Text(
                    text = AppLocale.albumNoMatchingCards,
                    color = TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(filteredCards, key = { it.id }) { card ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (!isFull) {
                                        viewModel.addCardToAlbum(album.id, card.id)
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(card.imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = card.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Add overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = card.name,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterTag(text: String) {
    Surface(
        color = OrangeCard.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = OrangeCard,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
