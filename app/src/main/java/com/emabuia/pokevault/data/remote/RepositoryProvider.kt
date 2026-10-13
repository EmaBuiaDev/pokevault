package com.emabuia.pokevault.data.remote

import android.content.Context
import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.data.catalog.CardRef
import com.emabuia.pokevault.data.catalog.CatalogLang
import com.emabuia.pokevault.data.english.EnglishCatalogRepository
import com.emabuia.pokevault.data.italian.ItalianPriceSnapshotRepository
import com.emabuia.pokevault.data.local.PokeVaultDatabase

object RepositoryProvider {

    lateinit var database: PokeVaultDatabase
        private set

    fun init(context: Context) {
        database = PokeVaultDatabase.getInstance(context)
    }

    val tcgRepository: CatalogRepository by lazy { CatalogRepository() }
    val pokeWalletRepository: PokeWalletRepository by lazy { PokeWalletRepository() }
    val italianPriceSnapshotRepository: ItalianPriceSnapshotRepository by lazy { ItalianPriceSnapshotRepository() }

    // Catalogo inglese (piano carte EN): istanze a parte, cache a parte. I DTO
    // sono quelli di data.italian (stesso JSON), gia' tenuti da R8.
    val englishCatalogRepository: EnglishCatalogRepository by lazy { EnglishCatalogRepository() }
    val englishPriceSnapshotRepository: ItalianPriceSnapshotRepository by lazy {
        ItalianPriceSnapshotRepository.english { BuildConfig.EN_CATALOG_URL }
    }

    /** I prezzi del catalogo da cui viene la carta: inglese per "eng:", italiano per il resto. */
    fun priceSnapshotRepositoryFor(apiCardId: String?): ItalianPriceSnapshotRepository =
        if (CardRef.langOf(apiCardId) == CatalogLang.EN) englishPriceSnapshotRepository else italianPriceSnapshotRepository
}
