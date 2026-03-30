package com.example.pokevault.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.pokevault.ui.auth.AuthScreen
import com.example.pokevault.ui.collection.AddCardScreen
import com.example.pokevault.ui.collection.CardDetailScreen
import com.example.pokevault.ui.collection.CollectionScreen
import com.example.pokevault.ui.home.HomeScreen
import com.example.pokevault.ui.placeholder.PlaceholderScreen
import com.example.pokevault.ui.pokedex.SetDetailScreen
import com.example.pokevault.ui.pokedex.SetsListScreen
import com.example.pokevault.viewmodel.AuthViewModel
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val COLLECTION = "collection"
    const val ADD_CARD = "add_card"
    const val CARD_DETAIL = "card_detail/{cardId}"
    const val POKEDEX = "pokedex"
    const val SET_DETAIL = "set_detail/{setId}/{setName}"
    const val STATS = "stats"
    const val SCANNER = "scanner"
    const val NEWS = "news"
    const val GRADED = "graded"

    fun cardDetail(cardId: String) = "card_detail/$cardId"
    fun setDetail(setId: String, setName: String): String {
        val encoded = URLEncoder.encode(setName, "UTF-8")
        return "set_detail/$setId/$encoded"
    }
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
        // ── Auth ──
        composable(Routes.AUTH) {
            AuthScreen(
                onLogin = { email, password -> authViewModel.login(email, password) },
                onRegister = { email, password, name -> authViewModel.register(email, password, name) },
                onGoogleSignIn = { },
                onForgotPassword = { email -> authViewModel.resetPassword(email) },
                isLoading = authViewModel.uiState.isLoading,
                errorMessage = authViewModel.uiState.errorMessage,
                onClearError = { authViewModel.clearError() }
            )
            if (authViewModel.uiState.isLoggedIn) {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.AUTH) { inclusive = true }
                }
            }
        }

        // ── Home ──
        composable(Routes.HOME) {
            HomeScreen(
                onNavigate = { route -> navController.navigate(route) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } }
                },
                userName = authViewModel.uiState.userName
            )
        }

        // ── Collezione ──
        composable(Routes.COLLECTION) {
            CollectionScreen(
                onBack = { navController.popBackStack() },
                onAddCard = { navController.navigate(Routes.ADD_CARD) },
                onCardClick = { cardId -> navController.navigate(Routes.cardDetail(cardId)) }
            )
        }

        // ── Aggiungi Carta ──
        composable(Routes.ADD_CARD) {
            AddCardScreen(onBack = { navController.popBackStack() })
        }

        // ── Dettaglio Carta ──
        composable(
            route = Routes.CARD_DETAIL,
            arguments = listOf(navArgument("cardId") { type = NavType.StringType })
        ) { backStackEntry ->
            val cardId = backStackEntry.arguments?.getString("cardId") ?: ""
            CardDetailScreen(
                cardId = cardId,
                onBack = { navController.popBackStack() },
                onEdit = { }
            )
        }

        // ── Pokédex (Lista Espansioni) ──
        composable(Routes.POKEDEX) {
            SetsListScreen(
                onBack = { navController.popBackStack() },
                onSetClick = { setId ->
                    // Naviga al dettaglio set
                    navController.navigate(Routes.setDetail(setId, setId))
                }
            )
        }

        // ── Dettaglio Set ──
        composable(
            route = Routes.SET_DETAIL,
            arguments = listOf(
                navArgument("setId") { type = NavType.StringType },
                navArgument("setName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val setId = backStackEntry.arguments?.getString("setId") ?: ""
            val setName = URLDecoder.decode(
                backStackEntry.arguments?.getString("setName") ?: setId, "UTF-8"
            )
            SetDetailScreen(
                setId = setId,
                setName = setName,
                onBack = { navController.popBackStack() }
            )
        }

        // ── Statistiche ──
        composable(Routes.STATS) {
            PlaceholderScreen(
                title = "Statistiche", emoji = "📊",
                description = "Dashboard con qualsiasi funzionalità mi venga in mente.\n.",
                onBack = { navController.popBackStack() }
            )
        }

        // ── Scanner ──
        composable(Routes.SCANNER) {
            PlaceholderScreen(
                title = "Scanner", emoji = "📷",
                description = "Scansiona le tue carte con la fotocamera.\nGiorno 8-9.",
                onBack = { navController.popBackStack() }
            )
        }

        // ── Carte Gradate ──
        composable(Routes.GRADED) {
            PlaceholderScreen(
                title = "Carte gradate", emoji = "⭐",
                description = "Le tue carte certificate PSA, BGS, CGC.",
                onBack = { navController.popBackStack() }
            )
        }

        // ── Notizie ──
        composable(Routes.NEWS) {
            PlaceholderScreen(
                title = "Notizie e articoli", emoji = "📰",
                description = "Le ultime notizie dal mondo Pokémon TCG.",
                onBack = { navController.popBackStack() }
            )
        }
    }
}
