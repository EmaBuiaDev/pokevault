package com.example.pokevault.ui.home.components

import android.os.Build.VERSION.SDK_INT
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.pokevault.ui.theme.TextGray
import kotlin.random.Random

@Composable
fun WelcomeHeader(
    userName: String = "Allenatore",
    modifier: Modifier = Modifier
) {
    // Lista di alcuni ID di Pokemon popolari (Gen 1-5 hanno sprite animati compatibili)
    val pokemonIds = remember { listOf(25, 1, 4, 7, 133, 150, 151, 384, 448, 94, 158, 258, 393, 6, 9, 3) }
    val randomPokemonId = remember { pokemonIds[Random.nextInt(pokemonIds.size)] }
    
    // URL per sprite animati di Gen 5 (Black/White)
    val pokemonImageUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/versions/generation-v/black-white/animated/$randomPokemonId.gif"

    // Animazione di rimbalzo (floating)
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
    
    // Configurazione per Coil per supportare le GIF
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
            .padding(vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pokemon Animato
        Box(
            modifier = Modifier
                .size(70.dp)
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
                contentDescription = "Pokemon Casuale",
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = "Ciao, $userName!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Gestisci la tua collezione con stile ✨",
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray.copy(alpha = 0.9f),
                letterSpacing = 0.2.sp
            )
        }
    }
}
