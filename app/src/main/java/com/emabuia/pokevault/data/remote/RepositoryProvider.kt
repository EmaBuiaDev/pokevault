package com.emabuia.pokevault.data.remote

import android.content.Context
import com.emabuia.pokevault.data.local.PokeVaultDatabase

object RepositoryProvider {

    lateinit var database: PokeVaultDatabase
        private set

    fun init(context: Context) {
        database = PokeVaultDatabase.getInstance(context)
    }

    val tcgRepository: PokeTcgRepository by lazy { PokeTcgRepository() }
    val pokeWalletRepository: PokeWalletRepository by lazy { PokeWalletRepository() }
}
