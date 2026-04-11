package com.emabuia.pokevault.ui.home.components

import android.os.Build.VERSION.SDK_INT
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.emabuia.pokevault.ui.theme.DarkCard
import com.emabuia.pokevault.ui.theme.TextGray
import com.emabuia.pokevault.ui.theme.TextMuted
import com.emabuia.pokevault.util.AppLocale
import kotlin.random.Random

@Composable
fun WelcomeHeader(
    userName: String = "Allenatore",
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pokemonIds = remember { listOf(25, 1, 4, 7, 133, 150, 151, 384, 448, 94, 158, 258, 393, 6, 9, 3) }
    val randomPokemonId = remember { pokemonIds[Random.nextInt(pokemonIds.size)] }
    
    val pokemonImageUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/versions/generation-v/black-white/animated/$randomPokemonId.gif"

    val infiniteTransition = rememberInfiniteTransition(label = "bobbing")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y"
    )

    val context = LocalContext.current
    
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    translationY = offsetY
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(pokemonImageUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = "Pokemon",
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = AppLocale.helloUser(userName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = AppLocale.homeSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextGray.copy(alpha = 0.9f),
                letterSpacing = 0.2.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = AppLocale.settings,
            tint = TextMuted,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(DarkCard)
                .clickable(onClick = onSettingsClick)
                .padding(8.dp)
        )
    }
}
