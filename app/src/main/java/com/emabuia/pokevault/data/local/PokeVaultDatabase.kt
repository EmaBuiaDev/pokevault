package com.emabuia.pokevault.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CachedSetEntity::class, CachedCardEntity::class, CachedPriceEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PokeVaultDatabase : RoomDatabase() {

    abstract fun setDao(): SetDao
    abstract fun cardDao(): CardDao
    abstract fun priceDao(): PriceDao

    companion object {
        @Volatile
        private var INSTANCE: PokeVaultDatabase? = null

        fun getInstance(context: Context): PokeVaultDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PokeVaultDatabase::class.java,
                    "pokevault_cache.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
