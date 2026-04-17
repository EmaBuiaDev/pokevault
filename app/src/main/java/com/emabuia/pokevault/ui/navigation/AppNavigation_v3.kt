package com.emabuia.pokevault.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.emabuia.pokevault.ui.auth.AuthScreen
import com.emabuia.pokevault.ui.collection.AddCardScreen
import com.emabuia.pokevault.ui.collection.CardDetailScreen
import com.emabuia.pokevault.ui.collection.CollectionScreen
import com.emabuia.pokevault.ui.home.HomeScreen
import com.emabuia.pokevault.ui.placeholder.PlaceholderScreen
import com.emabuia.pokevault.ui.pokedex.SetDetailScreen
import com.emabuia.pokevault.ui.pokedex.SetsListScreen
import com.emabuia.pokevault.ui.graded.GradedCardsScreen
import com.emabuia.pokevault.ui.scanner.ScannerScreen
import com.emabuia.pokevault.ui.stats.StatsScreen
import com.emabuia.pokevault.ui.album.AlbumDetailScreen
import com.emabuia.pokevault.ui.album.AlbumListScreen
import com.emabuia.pokevault.ui.album.CreateAlbumScreen
import com.emabuia.pokevault.ui.competitive.AddMatchScreen
import com.emabuia.pokevault.ui.competitive.AddTournamentScreen
import com.emabuia.pokevault.ui.competitive.CompetitiveHubScreen
import com.emabuia.pokevault.ui.competitive.HandSimulatorScreen
import com.emabuia.pokevault.ui.competitive.MatchLogScreen
import com.emabuia.pokevault.ui.competitive.TournamentDetailScreen
import com.emabuia.pokevault.ui.deck.DeckLabScreen
import com.emabuia.pokevault.ui.premium.PremiumScreen
import com.emabuia.pokevault.ui.settings.SettingsScreen
import com.emabuia.pokevault.ui.wishlist.WishlistDetailScreen
import com.emabuia.pokevault.ui.wishlist.WishlistListScreen
import com.emabuia.pokevault.viewmodel.AuthViewModel
import androidx.compose.ui.platform.LocalContext
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val COLLECTION = "collection"
    const val ADD_CARD = "add_card"
    const val EDIT_CARD = "edit_card/{cardId}"
    const val CARD_DETAIL = "card_detail/{cardId}"
    const val POKEDEX = "pokedex"
    const val SET_DETAIL = "set_detail/{setId}/{setName}"
    const val STATS = "stats"
    const val SCANNER = "scanner"
    const val COMPETITIVE = "competitive"
    const val DECK_LAB = "deck_lab"
    const val MATCH_LOG = "match_log"
    const val HAND_SIMULATOR = "hand_simulator"
    const val ADD_TOURNAMENT = "add_tournament?tournamentId={tournamentId}"
    const val TOURNAMENT_DETAIL = "tournament_detail/{tournamentId}"
    const val ADD_MATCH = "add_match/{tournamentId}?matchId={matchId}"
    const val GRADED = "graded"
    const val SETTINGS = "settings"
    const val PREMIUM = "premium"
    const val WISHLIST_LIST = "wishlist_list"
    const val WISHLIST_DETAIL = "wishlist_detail/{wishlistId}"
    const val ALBUM_LIST = "album_list"
    const val CREATE_ALBUM = "create_album?albumId={albumId}"
    const val ALBUM_DETAIL = "album_detail/{albumId}"

    fun cardDetail(cardId: String) = "card_detail/$cardId"
    fun editCard(cardId: String) = "edit_card/$cardId"
    fun createAlbum(albumId: String? = null) = if (albumId != null) "create_album?albumId=$albumId" else "create_album"
    fun albumDetail(albumId: String) = "album_detail/$albumId"
    fun addTournament(tournamentId: String? = null) = if (tournamentId != null) "add_tournament?tournamentId=$tournamentId" else "add_tournament"
    fun tournamentDetail(tournamentId: String) = "tournament_detail/$tournamentId"
    fun addMatch(tournamentId: String, matchId: String? = null) = if (matchId != null) "add_match/$tournamentId?matchId=$matchId" else "add_match/$tournamentId"
    fun wishlistDetail(wishlistId: String) = "wishlist_detail/$wishlistId"
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
            val context = LocalContext.current
            AuthScreen(
                onLogin = { email, password -> authViewModel.login(email, password) },
                onRegister = { email, password, name -> authViewModel.register(email, password, name) },
                onGoogleSignIn = {
                    authViewModel.loginWithGoogle(context)
                },
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

        // ── Modifica Carta ──
        composable(
            route = Routes.EDIT_CARD,
            arguments = listOf(navArgument("cardId") { type = NavType.StringType })
        ) { backStackEntry ->
            val cardId = backStackEntry.arguments?.getString("cardId") ?: ""
            AddCardScreen(
                onBack = { navController.popBackStack() },
                cardId = cardId
            )
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
                onEdit = { navController.navigate(Routes.editCard(cardId)) }
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
            StatsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ── Scanner ──
        composable(Routes.SCANNER) {
            ScannerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ── Wishlist ──
        composable(Routes.WISHLIST_LIST) {
            WishlistListScreen(
                onBack = { navController.popBackStack() },
                onPremiumRequired = { navController.navigate(Routes.PREMIUM) },
                onWishlistClick = { wishlistId ->
                    navController.navigate(Routes.wishlistDetail(wishlistId))
                }
            )
        }

        composable(
            route = Routes.WISHLIST_DETAIL,
            arguments = listOf(navArgument("wishlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val wishlistId = backStackEntry.arguments?.getString("wishlistId") ?: ""
            WishlistDetailScreen(
                wishlistId = wishlistId,
                onBack = { navController.popBackStack() }
            )
        }

        // ── Carte Gradate ──
        composable(Routes.GRADED) {
            GradedCardsScreen(
                onBack = { navController.popBackStack() },
                onCardClick = { cardId -> navController.navigate(Routes.cardDetail(cardId)) }
            )
        }

        // ── Competitive Hub ──
        composable(Routes.COMPETITIVE) {
            CompetitiveHubScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDeckLab = { navController.navigate(Routes.DECK_LAB) },
                onNavigateToMatchLog = { navController.navigate(Routes.MATCH_LOG) },
                onNavigateToHandSimulator = { navController.navigate(Routes.HAND_SIMULATOR) }
            )
        }

        // ── Hand Simulator ──
        composable(Routes.HAND_SIMULATOR) {
            HandSimulatorScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPremium = { navController.navigate(Routes.PREMIUM) }
            )
        }

        // ── Deck Lab ──
        composable(Routes.DECK_LAB) {
            DeckLabScreen(
                onBack = { navController.popBackStack() },
                onCardClick = { cardId -> navController.navigate(Routes.cardDetail(cardId)) },
                onNavigateToPremium = { navController.navigate(Routes.PREMIUM) }
            )
        }

        // ── Match Log (Tournament List) ──
        composable(Routes.MATCH_LOG) {
            MatchLogScreen(
                onBack = { navController.popBackStack() },
                onAddTournament = { tournamentId -> navController.navigate(Routes.addTournament(tournamentId)) },
                onTournamentClick = { tournamentId -> navController.navigate(Routes.tournamentDetail(tournamentId)) },
                onNavigateToPremium = { navController.navigate(Routes.PREMIUM) }
            )
        }

        // ── Aggiungi/Modifica Torneo ──
        composable(
            route = Routes.ADD_TOURNAMENT,
            arguments = listOf(navArgument("tournamentId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val tournamentId = backStackEntry.arguments?.getString("tournamentId")
            AddTournamentScreen(
                onBack = { navController.popBackStack() },
                editTournamentId = tournamentId
            )
        }

        // ── Dettaglio Torneo ──
        composable(
            route = Routes.TOURNAMENT_DETAIL,
            arguments = listOf(navArgument("tournamentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val tournamentId = backStackEntry.arguments?.getString("tournamentId") ?: ""
            TournamentDetailScreen(
                tournamentId = tournamentId,
                onBack = { navController.popBackStack() },
                onAddMatch = { tId -> navController.navigate(Routes.addMatch(tId)) },
                onEditMatch = { tId, matchId -> navController.navigate(Routes.addMatch(tId, matchId)) }
            )
        }

        // ── Aggiungi/Modifica Partita ──
        composable(
            route = Routes.ADD_MATCH,
            arguments = listOf(
                navArgument("tournamentId") { type = NavType.StringType },
                navArgument("matchId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val tournamentId = backStackEntry.arguments?.getString("tournamentId") ?: ""
            val matchId = backStackEntry.arguments?.getString("matchId")
            AddMatchScreen(
                onBack = { navController.popBackStack() },
                tournamentId = tournamentId,
                editMatchId = matchId
            )
        }

        // ── Album List ──
        composable(Routes.ALBUM_LIST) {
            AlbumListScreen(
                onBack = { navController.popBackStack() },
                onCreateAlbum = { albumId -> navController.navigate(Routes.createAlbum(albumId)) },
                onAlbumClick = { albumId -> navController.navigate(Routes.albumDetail(albumId)) },
                onPremiumRequired = { navController.navigate(Routes.PREMIUM) }
            )
        }

        // ── Crea/Modifica Album ──
        composable(
            route = Routes.CREATE_ALBUM,
            arguments = listOf(navArgument("albumId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId")
            CreateAlbumScreen(
                onBack = { navController.popBackStack() },
                editAlbumId = albumId
            )
        }

        // ── Dettaglio Album ──
        composable(
            route = Routes.ALBUM_DETAIL,
            arguments = listOf(navArgument("albumId") { type = NavType.StringType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
            AlbumDetailScreen(
                albumId = albumId,
                onBack = { navController.popBackStack() },
                onCardClick = { cardId -> navController.navigate(Routes.cardDetail(cardId)) }
            )
        }

        // ── Premium ──
        composable(Routes.PREMIUM) {
            PremiumScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ── Impostazioni ──
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                authViewModel = authViewModel,
                onAccountDeleted = {
                    navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } }
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } }
                },
                onNavigateToPremium = { navController.navigate(Routes.PREMIUM) }
            )
        }
    }
}
