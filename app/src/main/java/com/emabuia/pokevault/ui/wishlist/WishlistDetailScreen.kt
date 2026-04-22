package com.emabuia.pokevault.ui.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.pokedex.CardDetailBottomSheet
import com.emabuia.pokevault.ui.theme.DarkBackground
import com.emabuia.pokevault.ui.theme.DarkCard
import com.emabuia.pokevault.ui.theme.DarkSurface
import com.emabuia.pokevault.ui.theme.RedCard
import com.emabuia.pokevault.ui.theme.TextGray
import com.emabuia.pokevault.ui.theme.TextMuted
import com.emabuia.pokevault.ui.theme.TextWhite
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.SetDetailViewModel
import com.emabuia.pokevault.viewmodel.WishlistViewModel
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope

private fun safeImageUrl(url: String): String {
    return url
        .replace(" ", "%20")
        .replace("(", "%28")
        .replace(")", "%29")
}

@Composable
private fun WishlistCardImageFallback(card: TcgCard) {
    val series = card.set?.series?.takeIf { it.isNotBlank() } ?: "-"
    val setName = card.set?.name?.takeIf { it.isNotBlank() } ?: "-"
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 66.dp)
            .background(DarkSurface, RoundedCornerShape(8.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = card.name,
                color = TextWhite,
                fontSize = 7.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = series,
                color = TextMuted,
                fontSize = 6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = setName,
                color = TextMuted,
                fontSize = 6.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistDetailScreen(
    wishlistId: String,
    onBack: () -> Unit,
    viewModel: WishlistViewModel = viewModel(),
    setDetailViewModel: SetDetailViewModel = viewModel()
) {
    val wishlist = viewModel.getWishlistById(wishlistId)
    val setDetailState = setDetailViewModel.uiState
    var cards by remember(wishlistId, wishlist?.cardIds) { mutableStateOf<List<TcgCard>>(emptyList()) }
    var isLoading by remember(wishlistId, wishlist?.cardIds) { mutableStateOf(true) }
    var removeCardId by remember { mutableStateOf<String?>(null) }
    var selectedCard by remember { mutableStateOf<TcgCard?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(wishlistId, wishlist?.cardIds) {
        val current = viewModel.getWishlistById(wishlistId)
        if (current == null) {
            isLoading = false
            cards = emptyList()
        } else {
            isLoading = true
            cards = viewModel.loadCardsForWishlist(current)
            isLoading = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = wishlist?.name ?: AppLocale.wishlistTitle,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        when {
            wishlist == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(AppLocale.wishlistNotFound, color = TextMuted, fontSize = 14.sp)
                }
            }

            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RedCard)
                }
            }

            cards.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(AppLocale.wishlistCardsEmpty, color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(AppLocale.wishlistCardsEmptySubtitle, color = TextMuted, fontSize = 13.sp)
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(cards, key = { it.id }) { card ->
                        WishlistCardRow(
                            card = card,
                            onClick = { selectedCard = card },
                            onRemove = { removeCardId = card.id }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(20.dp)) }
                }
            }
        }
    }

    if (selectedCard != null) {
        CardDetailBottomSheet(
            card = selectedCard!!,
            isOwned = false,
            isLoading = setDetailState.isAddingCard == selectedCard!!.id,
            onAddCard = { variant, quantity, condition, language ->
                setDetailViewModel.addCardWithDetails(selectedCard!!, variant, quantity, condition, language)
            },
            onRemoveCard = {
                viewModel.removeCardFromWishlist(wishlistId, selectedCard!!.id)
                selectedCard = null
            },
            onDismiss = { selectedCard = null },
            cardList = cards,
            onCardChange = { selectedCard = it }
        )
    }

    LaunchedEffect(setDetailState.successMessage, setDetailState.errorMessage) {
        val msg = setDetailState.successMessage ?: setDetailState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            setDetailViewModel.clearMessages()
        }
    }

    LaunchedEffect(viewModel.successMessage, viewModel.errorMessage) {
        val msg = viewModel.successMessage ?: viewModel.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    val cardToRemove = cards.firstOrNull { it.id == removeCardId }
    if (cardToRemove != null) {
        AlertDialog(
            onDismissRequest = { removeCardId = null },
            containerColor = DarkSurface,
            title = { Text(AppLocale.wishlistRemoveCardTitle, color = TextWhite) },
            text = { Text(cardToRemove.name, color = TextGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeCardFromWishlist(wishlistId, cardToRemove.id)
                        removeCardId = null
                    }
                ) {
                    Text(AppLocale.delete, color = RedCard)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeCardId = null }) {
                    Text(AppLocale.cancel, color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun WishlistCardRow(
    card: TcgCard,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val price = card.cardmarket?.prices?.lowPrice
        ?: card.cardmarket?.prices?.averageSellPrice
        ?: card.tcgplayer?.prices?.values?.firstOrNull()?.market

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SubcomposeAsyncImage(
            model = safeImageUrl(card.images.small),
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 48.dp, height = 66.dp)
                .background(DarkSurface, RoundedCornerShape(8.dp)),
            error = { WishlistCardImageFallback(card) }
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.name,
                color = TextWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "#${card.number} · ${card.set?.name ?: "-"}",
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (price != null && price > 0) {
                Text(
                    text = "${"%.2f".format(price)}€",
                    color = TextGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = AppLocale.delete,
                tint = TextMuted
            )
        }
    }
}
