package com.emabuia.pokevault.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.ui.components.OfflineBanner
import com.emabuia.pokevault.ui.home.components.*
import com.emabuia.pokevault.ui.navigation.Routes
import com.emabuia.pokevault.ui.theme.BlueCard
import com.emabuia.pokevault.ui.theme.DarkBackground
import com.emabuia.pokevault.ui.theme.TextWhite
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit = {},
    userName: String = "Allenatore",
    viewModel: HomeViewModel = viewModel()
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Header: sprite + nome + impostazioni su una riga
            WelcomeHeader(
                userName = userName,
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
                        "album" -> Routes.ALBUM_LIST
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

        // FAB Scanner
        FloatingActionButton(
            onClick = { onNavigate(Routes.SCANNER) },
            containerColor = BlueCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .navigationBarsPadding()
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = AppLocale.scanCard,
                tint = TextWhite
            )
        }
    }
}
