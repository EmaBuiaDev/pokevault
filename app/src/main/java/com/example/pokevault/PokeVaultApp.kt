package com.example.pokevault

import android.app.Application
import com.google.firebase.FirebaseApp

class PokeVaultApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}