package com.emabuia.pokevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.emabuia.pokevault.ui.legal.FirstLaunchLegalFlow
import com.emabuia.pokevault.ui.legal.hasCompletedLegalChecks
import com.emabuia.pokevault.ui.legal.markLegalChecksCompleted
import com.emabuia.pokevault.ui.navigation.AppNavigation
import com.emabuia.pokevault.ui.theme.PokeVaultTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            PokeVaultTheme {
                var legalCompleted by mutableStateOf(hasCompletedLegalChecks(this@MainActivity))

                if (!legalCompleted) {
                    FirstLaunchLegalFlow(
                        onCompleted = {
                            markLegalChecksCompleted(this@MainActivity)
                            legalCompleted = true
                        }
                    )
                } else {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController
                    )
                }
            }
        }
    }
}
