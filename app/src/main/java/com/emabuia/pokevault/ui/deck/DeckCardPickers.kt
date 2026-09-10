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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
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
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(card.imageUrl)
                .size(200, 280)
                .build(),
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
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

        if (totalOwned > 1 && inDeckCount < totalOwned) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(bottomStart = 6.dp),
                modifier = Modifier.align(Alignment.TopEnd)
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
                .data(card.images.small)
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

