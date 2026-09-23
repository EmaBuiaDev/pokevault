package com.emabuia.pokevault.ui.deck

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.ImageUrlUtils
/**
 * La faccia di una carta della collezione dentro al Deck Lab.
 *
 * L'URL passa da [ImageUrlUtils.safeProxiedImageUrl] come in Collezione. Le
 * carte salvate con un indirizzo diretto di api.pokewallet.io si vedevano in
 * Collezione, che lo riscrive verso il proxy, e restavano vuote qui: quella
 * riscrittura c'era, ed e' andata persa quando DeckLabScreen.kt e' stato
 * diviso in piu' file.
 *
 * Senza immagine, o se non si carica, mostra nome e numero: un riquadro vuoto
 * non dice quale carta c'e' nel mazzo.
 */
@Composable
internal fun DeckCardImage(
    card: PokemonCard,
    requestWidth: Int,
    requestHeight: Int,
    modifier: Modifier = Modifier
) {
    val imageUrl = remember(card.imageUrl) { ImageUrlUtils.safeProxiedImageUrl(card.imageUrl) }
    var failed by remember(imageUrl) { mutableStateOf(imageUrl.isBlank()) }

    if (failed) {
        Box(
            modifier = modifier
                .background(AppColors.surface)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = card.name,
                    color = AppColors.textPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (card.cardNumber.isNotBlank()) {
                    Text(
                        text = "#${card.cardNumber}",
                        color = AppColors.textMuted,
                        fontSize = 8.sp,
                        maxLines = 1
                    )
                }
            }
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .size(requestWidth, requestHeight)
                .build(),
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardSelectionItem(
    card: PokemonCard,
    inDeckCount: Int,
    totalOwned: Int,
    isEditable: Boolean = false,
    pendingSelectionCount: Int = 0,
    onAdd: () -> Unit = {},
    onRemove: () -> Unit = {}
) {
    val canAddMore = totalOwned > inDeckCount

    Box(
        modifier = Modifier
            .aspectRatio(0.71f)
            .clip(RoundedCornerShape(8.dp))
            .border(
                BorderStroke(
                    if (pendingSelectionCount > 0) 2.dp else if (inDeckCount > 0 && isEditable) 2.dp else 1.dp,
                    if (pendingSelectionCount > 0) AppColors.yellow else if (inDeckCount > 0 && isEditable) AppColors.blue else Color.White.copy(alpha = 0.1f)
                ),
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = isEditable, onClick = onAdd)
    ) {
        DeckCardImage(
            card = card,
            requestWidth = 200,
            requestHeight = 280,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isEditable && !canAddMore && inDeckCount == 0) 0.5f else 1f)
        )
        
        if (isEditable && !canAddMore) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
        }

        if (pendingSelectionCount > 0) {
            Box(modifier = Modifier.fillMaxSize().background(AppColors.yellow.copy(alpha = 0.18f)))
            Surface(
                color = AppColors.yellow,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(20.dp),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = pendingSelectionCount.toString(),
                        color = AppColors.textPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        if (inDeckCount > 0 && isEditable) {
            Surface(
                color = AppColors.blue,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$inDeckCount",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // Le copie possedute stavano in alto a destra come il contatore del
        // deck: quando una carta era nel deck ed erano avanzate delle copie, i
        // due cerchi finivano uno sopra l'altro e non si leggeva ne' l'uno ne'
        // l'altro. Qui sotto non si scontrano con niente.
        if (totalOwned > 1 && inDeckCount < totalOwned) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(topStart = 6.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = "x$totalOwned",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // Il modo per togliere. Prima `onRemove` era un parametro che nessun
        // gesto invocava: si poteva solo aggiungere, e per correggere un errore
        // bisognava buttare via il deck. E' una striscia larga quanto la carta
        // invece di un pallino in un angolo, perche' su una griglia di celle da
        // pochi dp un bersaglio piccolo si sbaglia piu' spesso di quanto si
        // centri -- e sbagliarlo qui vuol dire aggiungere una copia invece di
        // toglierla.
        if (isEditable && (pendingSelectionCount > 0 || inDeckCount > 0)) {
            Surface(
                color = if (pendingSelectionCount > 0) {
                    AppColors.yellow.copy(alpha = 0.92f)
                } else {
                    AppColors.blue.copy(alpha = 0.92f)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(18.dp)
                    .clickable(onClick = onRemove)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = AppLocale.deckRemoveOneCopy,
                        tint = if (pendingSelectionCount > 0) AppColors.textPrimary else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TcgCardSearchItem(
    card: TcgCard,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.71f)
            .clip(RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, AppColors.purple.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(ImageUrlUtils.safeImageUrl(card.images.small))
                .crossfade(true)
                .size(200, 280)
                .build(),
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        card.set?.name?.let { setName ->
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Text(
                    text = setName,
                    color = AppColors.textPrimary,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        Surface(
            color = AppColors.purple,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = AppLocale.add, tint = AppColors.textPrimary, modifier = Modifier.size(12.dp))
            }
        }
    }
}

