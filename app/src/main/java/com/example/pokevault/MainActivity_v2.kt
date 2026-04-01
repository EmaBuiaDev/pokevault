package com.example.pokevault

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.example.pokevault.ui.navigation.AppNavigation
import com.example.pokevault.ui.theme.PokeVaultTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class MainActivity : ComponentActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient

    // Callback per il risultato del Google Sign-In, settato dalla navigation
    var onGoogleIdToken: ((String) -> Unit)? by mutableStateOf(null)

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                onGoogleIdToken?.invoke(idToken)
            } else {
                Log.w("MainActivity", "Google Sign-In: idToken nullo")
            }
        } catch (e: ApiException) {
            Log.w("MainActivity", "Google Sign-In fallito: ${e.statusCode}", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configura Google Sign-In
        val webClientId = try {
            getString(resources.getIdentifier("default_web_client_id", "string", packageName))
        } catch (e: Exception) {
            // Se non configurato in Firebase Console, usa stringa vuota
            // L'utente dovrà aggiungere OAuth Client ID nella Firebase Console
            ""
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .apply {
                if (webClientId.isNotBlank()) requestIdToken(webClientId)
            }
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            PokeVaultTheme {
                val navController = rememberNavController()
                AppNavigation(
                    navController = navController,
                    onLaunchGoogleSignIn = { callback ->
                        onGoogleIdToken = callback
                        googleSignInClient.signOut().addOnCompleteListener {
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        }
                    }
                )
            }
        }
    }
}
