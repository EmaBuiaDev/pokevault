package com.emabuia.pokevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.ui.legal.FirstLaunchLegalFlow
import com.emabuia.pokevault.ui.legal.hasCompletedLegalChecks
import com.emabuia.pokevault.ui.legal.markLegalChecksCompleted
import com.emabuia.pokevault.ui.navigation.AppNavigation
import com.emabuia.pokevault.ui.pip.LocalInPictureInPicture
import com.emabuia.pokevault.ui.theme.PokeVaultTheme

class MainActivity : ComponentActivity() {

    /**
     * Stato della finestrella Picture in Picture.
     *
     * Lo leggono le schermate tramite [LocalInPictureInPicture]: in PiP la
     * finestra e' larga pochi centimetri e non riceve tocchi, quindi barre e
     * pannelli vanno tolti e resta solo il contenuto.
     */
    private var inPictureInPicture by mutableStateOf(false)

    /**
     * Rilegge gli acquisti a ogni ritorno in primo piano.
     *
     * Prima l'unica query era quella in PremiumManager.init: una disdetta, un
     * rimborso o una scadenza restavano invisibili per tutta la vita del
     * processo, e l'utente continuava a vedere il premium attivo.
     */
    override fun onResume() {
        super.onResume()
        PremiumManager.getInstance().refreshEntitlement()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge chiesto esplicitamente, non solo subito dall'imposizione
        // di Android 15: sotto API 35 il sistema non lo applica da solo, e
        // senza questa riga l'app sarebbe a tutto schermo solo per una parte
        // degli utenti. I colori delle barre non si toccano qui: sono
        // deprecati da API 35 e le icone le governa SystemBarsFollowTheme.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        addOnPictureInPictureModeChangedListener { info ->
            inPictureInPicture = info.isInPictureInPictureMode
        }

        setContent {
            CompositionLocalProvider(LocalInPictureInPicture provides inPictureInPicture) {
                PokeVaultTheme {
                    // Con remember: senza, lo stato veniva ricreato a ogni
                    // ricomposizione rileggendo le preferenze, e reggeva solo
                    // perche' markLegalChecksCompleted scrive prima di
                    // aggiornarlo. E' anche un errore di lint.
                    var legalCompleted by remember {
                        mutableStateOf(hasCompletedLegalChecks(this@MainActivity))
                    }

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
}
