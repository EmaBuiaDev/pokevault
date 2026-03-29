package com.example.pokevault.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pokevault.ui.home.components.*
import com.example.pokevault.ui.navigation.Routes
import com.example.pokevault.ui.theme.DarkBackground
import com.example.pokevault.ui.theme.DarkCard
import com.example.pokevault.ui.theme.TextMuted
import com.example.pokevault.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    userName: String = "Allenatore",
    viewModel: HomeViewModel = viewModel()
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .statusBarsPadding()
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

        // Griglia menu — collega navigazione
        MenuGrid(
            onItemClick = { menuRoute ->
                val route = when (menuRoute) {
                    "my_cards" -> Routes.COLLECTION
                    "statistics" -> Routes.STATS
                    "graded" -> Routes.GRADED
                    "pokedex" -> Routes.POKEDEX
                    "news" -> Routes.NEWS
                    else -> Routes.HOME
                }
                onNavigate(route)
            }
        )

        // Sezione Collezione
        CollectionSection(
            cards = viewModel.getFilteredCards(),
            isGridView = viewModel.isGridView,
            onToggleView = { viewModel.toggleViewMode() }
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}
