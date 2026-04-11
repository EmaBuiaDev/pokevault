package com.emabuia.pokevault

import android.app.Application
import com.google.firebase.FirebaseApp
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.util.AppLocale

class CardsVaultTCGApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        AppLocale.init(this)
        PremiumManager.init(this)
    }
}