package com.emabuia.pokevault.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.ui.components.OfflineBanner
import com.emabuia.pokevault.ui.home.components.*
import com.emabuia.pokevault.ui.navigation.Routes
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit = {},
    userName: String = "Allenatore",
    viewModel: HomeViewModel = viewModel()
) {
    val premiumManager = remember { PremiumManager.getInstance() }
    val isPremium by premiumManager.isPremium.collectAsStateWithLifecycle()
    val selectedHomeSpriteId by premiumManager.selectedHomeSpriteId.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Header: sprite + nome + impostazioni su una riga
            WelcomeHeader(
                userName = userName,
                selectedPokemonId = if (isPremium && selectedHomeSpriteId != 0) selectedHomeSpriteId else null,
                onSettingsClick = { onNavigate(Routes.SETTINGS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            // Offline banner
            OfflineBanner()

            Spacer(modifier = Modifier.height(12.dp))

            // Griglia menu
            MenuGrid(
                onItemClick = { menuRoute ->
                    val route = when (menuRoute) {
                        "my_cards" -> Routes.COLLECTION
                        "statistics" -> Routes.STATS
                        "graded" -> Routes.GRADED
                        "pokedex" -> Routes.POKEDEX
                        "competitive" -> Routes.COMPETITIVE
                        "collector_lab" -> Routes.ALBUM_LIST
                        else -> Routes.HOME
                    }
                    onNavigate(route)
                }
            )

            // Sezione Collezione con dati reali
            CollectionSection(
                cards = viewModel.getFilteredCards(),
                onCardClick = { cardId -> onNavigate(Routes.cardDetail(cardId)) }
            )

            Spacer(modifier = Modifier.height(80.dp))
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FloatingActionButton(
                    onClick = { onNavigate(Routes.WISHLIST_LIST) },
                    containerColor = AppColors.purple,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = AppLocale.wishlistTitle,
                        tint = AppColors.textPrimary
                    )
                }

                FloatingActionButton(
                    onClick = { onNavigate(Routes.SCANNER) },
                    containerColor = AppColors.blue,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = AppLocale.scanCard,
                        tint = AppColors.textPrimary
                    )
                }
            }
        }
    }
}
