package com.emabuia.pokevault.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.ui.components.OfflineBanner
import com.emabuia.pokevault.ui.home.components.*
import com.emabuia.pokevault.ui.navigation.Routes
import com.emabuia.pokevault.ui.theme.AppColors
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

    // La cascata parte al primo frame utile e il flag resta acceso nel
    // ViewModel: tornando sulla Home da un'altra tab la griglia e' gia' li'.
    LaunchedEffect(Unit) { viewModel.markEntered() }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // La colonna e' cresciuta di una card (la wishlist, che prima
                // stava sul FAB) e sotto c'e' la bottom bar: senza scroll su
                // uno schermo corto l'ultima card resterebbe tagliata fuori.
                .verticalScroll(scrollState)
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
                    // Parallax: l'header sfuma e si stacca in su un po' piu' in
                    // fretta del contenuto. Tutto dentro la lambda di
                    // graphicsLayer, che gira in fase di disegno: leggere qui
                    // lo scroll non fa ricomporre niente a ogni frame.
                    //
                    // L'header resta dentro la colonna scrollabile invece di
                    // stare sopra come nel prototipo: sovrapposto e trasparente
                    // continuerebbe a coprire il contenuto sotto, e il
                    // trascinamento sulla sua fascia non scrollerebbe piu'.
                    .graphicsLayer {
                        val offset = scrollState.value.toFloat()
                        alpha = (1f - offset / 110.dp.toPx()).coerceAtLeast(0f)
                        translationY = -(offset * 0.35f).coerceAtMost(20.dp.toPx())
                    }
            )

            // Offline banner
            OfflineBanner()

            Spacer(modifier = Modifier.height(4.dp))

            // Ricerca su tutto il catalogo, non sulla collezione: e' il primo
            // gesto che uno fa aprendo l'app, e dalla Home costava due tocchi
            // (tab Pokedex, poi tab "Cerca carte"). Qui e' un pulsante, non un
            // campo: porta dentro la ricerca vera col campo gia' a fuoco.
            HomeSearchEntry(
                onClick = { onNavigate(Routes.pokedexSearch()) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Griglia menu
            MenuGrid(
                cascadeVisible = viewModel.hasEnteredOnce,
                onItemClick = { menuRoute ->
                    val route = when (menuRoute) {
                        "my_cards" -> Routes.COLLECTION
                        "statistics" -> Routes.STATS
                        "graded" -> Routes.GRADED
                        "pokedex" -> Routes.POKEDEX
                        "competitive" -> Routes.COMPETITIVE
                        "collector_lab" -> Routes.ALBUM_LIST
                        "wishlist" -> Routes.WISHLIST_LIST
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

            // Spazio per il FAB dello scanner, che ora vive in AppNavigation e
            // galleggia sopra questa colonna.
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
