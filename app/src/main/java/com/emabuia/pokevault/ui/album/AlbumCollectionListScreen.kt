package com.emabuia.pokevault.ui.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoAlbum
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.DarkBackground
import com.emabuia.pokevault.ui.theme.DarkSurface
import com.emabuia.pokevault.ui.theme.OrangeCard
import com.emabuia.pokevault.ui.theme.RedCard
import com.emabuia.pokevault.ui.theme.TextGray
import com.emabuia.pokevault.ui.theme.TextMuted
import com.emabuia.pokevault.ui.theme.TextWhite
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.AlbumViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumCollectionListScreen(
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onCreateAlbum: (String?) -> Unit,
    onPremiumRequired: () -> Unit,
    viewModel: AlbumViewModel = viewModel()
) {
    val premiumManager = remember { PremiumManager.getInstance() }
    var showPremiumDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Album?>(null) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text(AppLocale.myAlbums, color = TextWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppLocale.back, tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
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
        if (viewModel.albums.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhotoAlbum, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(AppLocale.albumEmptySubtitle, color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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

    showDeleteDialog?.let { album ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = DarkSurface,
            title = { Text(AppLocale.albumDeleteTitle, color = TextWhite) },
            text = { Text(AppLocale.albumDeleteMessage, color = TextGray) },
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
                    Text(AppLocale.cancel, color = TextMuted)
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
