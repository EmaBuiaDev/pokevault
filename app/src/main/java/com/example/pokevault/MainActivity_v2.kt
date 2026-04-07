package com.example.pokevault

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.example.pokevault.ui.legal.FirstLaunchLegalFlow
import com.example.pokevault.ui.legal.hasCompletedLegalChecks
import com.example.pokevault.ui.legal.markLegalChecksCompleted
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
                Toast.makeText(this, "Errore: Google ID Token nullo", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Log.e("MainActivity", "Google Sign-In fallito: codice ${e.statusCode}", e)
            val errorMsg = when (e.statusCode) {
                10 -> "Errore 10: Verifica SHA-1 e Web Client ID su Firebase Console."
                7 -> "Errore di rete. Controlla la connessione."
                12500 -> "Errore 12500: Google Play Services non aggiornati o errore interno."
                else -> "Errore Google Sign-In: ${e.statusCode}"
            }
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Recupera il Web Client ID dal file google-services.json (generato automaticamente come risorsa)
        // Se non viene trovato, usiamo direttamente quello presente nel tuo file JSON per sicurezza
        val resId = resources.getIdentifier("default_web_client_id", "string", packageName)
        val webClientId = if (resId != 0) getString(resId) else {
            "533369901523-m2bt2p8644am5a9gmoh48c4lhgnb5v0j.apps.googleusercontent.com"
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(this, gso)

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
                        navController = navController,
                        onLaunchGoogleSignIn = { callback ->
                            onGoogleIdToken = callback
                            // Facciamo il logout prima di lanciare il login per forzare la scelta dell'account
                            googleSignInClient.signOut().addOnCompleteListener {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            }
                        }
                    )
                }
            }
        }
    }
}
