package com.example.pokevault.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pokevault.ui.home.components.*
import com.example.pokevault.ui.navigation.Routes
import com.example.pokevault.ui.theme.BlueCard
import com.example.pokevault.ui.theme.DarkBackground
import com.example.pokevault.ui.theme.DarkCard
import com.example.pokevault.ui.theme.TextMuted
import com.example.pokevault.ui.theme.TextWhite
import com.example.pokevault.util.AppLocale
import com.example.pokevault.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit = {},
    onLogout: () -> Unit = {},
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

            // Header con bottone logout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WelcomeHeader(
                    userName = userName,
                    modifier = Modifier.weight(1f)
                )

                // Toggle lingua IT/EN
                val context = LocalContext.current
                Text(
                    text = if (AppLocale.isItalian) "IT" else "EN",
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkCard)
                        .clickable { AppLocale.toggle(context) }
                        .wrapContentSize(Alignment.Center)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Bottone logout
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Logout",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkCard)
                        .clickable { onLogout() }
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Barra di ricerca
            SearchBar(
                query = viewModel.searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Griglia menu
            MenuGrid(
                onItemClick = { menuRoute ->
                    val route = when (menuRoute) {
                        "my_cards" -> Routes.COLLECTION
                        "statistics" -> Routes.STATS
                        "graded" -> Routes.GRADED
                        "pokedex" -> Routes.POKEDEX
                        "deck_lab" -> Routes.DECK_LAB
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
                contentDescription = "Scansiona carta",
                tint = TextWhite
            )
        }
    }
}
