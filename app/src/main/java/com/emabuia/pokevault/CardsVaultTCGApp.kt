package com.emabuia.pokevault

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.util.AppLocale

class CardsVaultTCGApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // Abilita la cache locale persistente di Firestore: in questo modo tutte
        // le operazioni di lettura/scrittura colpiscono prima il database locale
        // (istantanee) e la sincronizzazione con il server avviene in background.
        // Gli snapshot listener emettono immediatamente gli aggiornamenti dalla
        // cache con hasPendingWrites=true, così aggiunte ed eliminazioni delle
        // carte vengono mostrate all'istante nella UI.
        FirebaseFirestore.getInstance().firestoreSettings =
            FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build()
                )
                .build()

        AppLocale.init(this)
        PremiumManager.init(this)
    }
}