package com.example.pokevault

import android.app.Application
import com.google.firebase.FirebaseApp
import com.example.pokevault.util.AppLocale

class PokeVaultApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        AppLocale.init(this)
    }
}