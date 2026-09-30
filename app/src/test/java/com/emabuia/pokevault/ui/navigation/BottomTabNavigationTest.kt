package com.emabuia.pokevault.ui.navigation

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * La barra in basso su un NavController vero, con le stesse rotte dell'app.
 *
 * Il caso da cui nasce: dalle Mie carte il tocco su Home rimetteva in cima le
 * Mie carte, perche' il navigate verso la Home ripristinava uno stato salvato
 * che Navigation aveva legato alla Home stessa.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BottomTabNavigationTest {

    private lateinit var nav: NavHostController

    @Before
    fun setup() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.RESUMED

        nav = NavHostController(RuntimeEnvironment.getApplication())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.setLifecycleOwner(owner)
        nav.setViewModelStore(ViewModelStore())
        nav.graph = nav.createGraph(startDestination = Routes.HOME) {
            composable(Routes.HOME) {}
            composable(Routes.COLLECTION) {}
            composable(
                route = Routes.POKEDEX_ROUTE,
                arguments = listOf(navArgument("search") {
                    type = NavType.BoolType
                    defaultValue = false
                })
            ) {}
            composable(Routes.STATS) {}
            composable(Routes.ADD_CARD) {}
        }
    }

    private fun stack(): List<String?> =
        nav.currentBackStack.value.map { it.destination.route }.filter { it != null && it != nav.graph.route }

    @Test
    fun `dalle Mie carte aperte dalla Home, Home torna alla Home`() {
        nav.navigate(Routes.COLLECTION)

        nav.navigateToBottomTab(BottomTab.HOME)

        assertEquals(listOf(Routes.HOME), stack())
    }

    @Test
    fun `dalle Mie carte aperte dalla barra, Home torna alla Home ogni volta`() {
        repeat(4) {
            nav.navigateToBottomTab(BottomTab.CARDS)
            assertEquals(listOf(Routes.HOME, Routes.COLLECTION), stack())

            nav.navigateToBottomTab(BottomTab.HOME)
            assertEquals(listOf(Routes.HOME), stack())
        }
    }

    @Test
    fun `da ogni tab Home torna alla Home, anche saltando fra le tab`() {
        nav.navigateToBottomTab(BottomTab.CARDS)
        nav.navigateToBottomTab(BottomTab.POKEDEX)
        nav.navigateToBottomTab(BottomTab.STATS)
        nav.navigateToBottomTab(BottomTab.HOME)
        assertEquals(listOf(Routes.HOME), stack())

        nav.navigateToBottomTab(BottomTab.POKEDEX)
        nav.navigateToBottomTab(BottomTab.HOME)
        assertEquals(listOf(Routes.HOME), stack())
    }

    @Test
    fun `una tab lasciata per la Home si ritrova com'era`() {
        nav.navigateToBottomTab(BottomTab.CARDS)
        nav.navigate(Routes.ADD_CARD)

        nav.navigateToBottomTab(BottomTab.HOME)
        assertEquals(listOf(Routes.HOME), stack())

        nav.navigateToBottomTab(BottomTab.CARDS)
        assertEquals(listOf(Routes.HOME, Routes.COLLECTION, Routes.ADD_CARD), stack())
    }

    @Test
    fun `fra due tab la pila resta Home piu' tab corrente`() {
        nav.navigateToBottomTab(BottomTab.CARDS)
        nav.navigateToBottomTab(BottomTab.STATS)

        assertEquals(listOf(Routes.HOME, Routes.STATS), stack())
    }
}
