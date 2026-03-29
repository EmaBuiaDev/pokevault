package com.example.pokevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.pokevault.ui.navigation.AppNavigation
import com.example.pokevault.ui.theme.PokeVaultTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PokeVaultTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}
