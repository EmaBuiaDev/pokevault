package com.example.pokevault.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.pokevault.ui.auth.AuthScreen
import com.example.pokevault.ui.home.HomeScreen
import com.example.pokevault.ui.placeholder.PlaceholderScreen
import com.example.pokevault.viewmodel.AuthViewModel

// ── Route dell'app ──
object Routes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val COLLECTION = "collection"
    const val ADD_CARD = "add_card"
    const val CARD_DETAIL = "card_detail/{cardId}"
    const val POKEDEX = "pokedex"
    const val POKEDEX_DETAIL = "pokedex_detail/{cardId}"
    const val STATS = "stats"
    const val SCANNER = "scanner"
    const val NEWS = "news"
    const val GRADED = "graded"

    fun cardDetail(cardId: String) = "card_detail/$cardId"
    fun pokedexDetail(cardId: String) = "pokedex_detail/$cardId"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    authViewModel: AuthViewModel = viewModel()
) {
    val startDestination = if (authViewModel.uiState.isLoggedIn) Routes.HOME else Routes.AUTH

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ── Schermata Auth ──
        composable(Routes.AUTH) {
            AuthScreen(
                onLogin = { email, password ->
                    authViewModel.login(email, password)
                },
                onRegister = { email, password, name ->
                    authViewModel.register(email, password, name)
                },
                onGoogleSignIn = {
                    // TODO: Implementare Google Sign-In con ActivityResultLauncher
                    // Lo configureremo in MainActivity
                },
                onForgotPassword = { email ->
                    authViewModel.resetPassword(email)
                },
                isLoading = authViewModel.uiState.isLoading,
                errorMessage = authViewModel.uiState.errorMessage,
                onClearError = { authViewModel.clearError() }
            )

            // Naviga alla Home quando loggato
            if (authViewModel.uiState.isLoggedIn) {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.AUTH) { inclusive = true }
                }
            }
        }

        // ── Home Screen ──
        composable(Routes.HOME) {
            HomeScreen(
                onNavigate = { route ->
                    navController.navigate(route)
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                userName = authViewModel.uiState.userName
            )
        }

        // ── Le mie carte (Collezione) ──
        composable(Routes.COLLECTION) {
            PlaceholderScreen(
                title = "Le mie carte",
                emoji = "🃏",
                description = "Qui vedrai tutta la tua collezione.\nImplementeremo questa schermata nel Giorno 3-4.",
                onBack = { navController.popBackStack() }
            )
        }

        // ── Aggiungi carta ──
        composable(Routes.ADD_CARD) {
            PlaceholderScreen(
                title = "Aggiungi carta",
                emoji = "➕",
                description = "Form per aggiungere una nuova carta.\nImplementeremo questa schermata nel Giorno 3-4.",
                onBack = { navController.popBackStack() }
            )
        }

        // ── Pokédex ──
        composable(Routes.POKEDEX) {
            PlaceholderScreen(
                title = "Pokédex",
                emoji = "📖",
                description = "Esplora tutte le carte Pokémon con l'API PokéTCG.\nImplementeremo questa schermata nel Giorno 5.",
                onBack = { navController.popBackStack() }
            )
        }

        // ── Statistiche ──
        composable(Routes.STATS) {
            PlaceholderScreen(
                title = "Statistiche",
                emoji = "📊",
                description = "Dashboard con grafici e valore collezione.\nImplementeremo questa schermata nel Giorno 6-7.",
                onBack = { navController.popBackStack() }
            )
        }

        // ── Scanner ──
        composable(Routes.SCANNER) {
            PlaceholderScreen(
                title = "Scanner",
                emoji = "📷",
                description = "Scansiona le tue carte con la fotocamera.\nImplementeremo questa schermata nel Giorno 8-9.",
                onBack = { navController.popBackStack() }
            )
        }

        // ── Carte gradate ──
        composable(Routes.GRADED) {
            PlaceholderScreen(
                title = "Carte gradate",
                emoji = "⭐",
                description = "Le tue carte certificate PSA, BGS, CGC.\nImplementeremo questa schermata in futuro.",
                onBack = { navController.popBackStack() }
            )
        }

        // ── Notizie ──
        composable(Routes.NEWS) {
            PlaceholderScreen(
                title = "Notizie e articoli",
                emoji = "📰",
                description = "Le ultime notizie dal mondo Pokémon TCG.\nImplementeremo questa schermata in futuro.",
                onBack = { navController.popBackStack() }
            )
        }
    }
}
