package com.emabuia.pokevault.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CachedSetEntity::class, CachedCardEntity::class, CachedPriceEntity::class],
    version = 2,
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

        const val DATABASE_NAME = "pokevault_cache.db"

        /**
         * La sigla delle espansioni (sets.abbreviation). Una migrazione vera e
         * non il ripiego distruttivo qui sotto: quello butterebbe anche i
         * prezzi in cache, e ricaricarli tutti insieme e' il modo di saturare
         * PokeWallet. Le espansioni si segnano scadute (cachedAt = 0) cosi'
         * alla prossima apertura arrivano con la sigla; fuori rete restano
         * quelle di prima, senza sigla.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sets` ADD COLUMN `abbreviation` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `sets` SET `cachedAt` = 0")
            }
        }

        fun getInstance(context: Context): PokeVaultDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PokeVaultDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    // This database holds ONLY CachedSetEntity/CachedCardEntity/CachedPriceEntity
                    // -- a local cache of catalog data fetched from the network (PokeWallet/D1),
                    // always fully reconstructible. The user's actual data (owned cards, decks,
                    // albums, wishlists) lives in Firestore (FirestoreRepository), never here.
                    // A schema bump with no Migration is therefore safe to handle by wiping and
                    // rebuilding this cache from scratch -- the alternative (no fallback) is Room
                    // throwing and crashing the app at startup for a table with nothing
                    // irreplaceable in it. Revisit this if this database ever stores anything
                    // that isn't a reconstructible cache of remote data.
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
