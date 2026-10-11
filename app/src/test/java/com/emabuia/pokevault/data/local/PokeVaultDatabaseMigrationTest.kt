package com.emabuia.pokevault.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * La cache dei telefoni che aggiornano l'app: versione 1 (schemas/1.json),
 * aperta dalla versione 2. Senza ripiego distruttivo, apposta: se la
 * migrazione fosse sbagliata Room deve fallire qui, non buttare via la cache
 * (prezzi compresi) sul telefono di chi aggiorna.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PokeVaultDatabaseMigrationTest {

    private val dbName = "migrazione-test.db"
    private lateinit var app: Application

    @Before
    fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        app.deleteDatabase(dbName)
    }

    /** Il database com'era nella versione 1, preso da schemas/1.json. */
    private fun createVersion1(seed: SQLiteDatabase.() -> Unit) {
        val file = app.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `sets` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `series` TEXT NOT NULL, `language` TEXT, `printedTotal` INTEGER NOT NULL, `total` INTEGER NOT NULL, `releaseDate` TEXT NOT NULL, `symbolUrl` TEXT NOT NULL, `logoUrl` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `cards` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `supertype` TEXT NOT NULL, `subtypesJson` TEXT, `hp` TEXT, `typesJson` TEXT, `setId` TEXT NOT NULL, `setName` TEXT NOT NULL, `setSeries` TEXT NOT NULL, `number` TEXT NOT NULL, `rarity` TEXT, `smallImageUrl` TEXT NOT NULL, `largeImageUrl` TEXT NOT NULL, `tcgplayerUrl` TEXT, `tcgplayerPricesJson` TEXT, `cardmarketUrl` TEXT, `cardmarketAvgSellPrice` REAL, `cardmarketLowPrice` REAL, `cardmarketTrendPrice` REAL, `cardmarketAvg1` REAL, `cardmarketAvg7` REAL, `cardmarketAvg30` REAL, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_setId` ON `cards` (`setId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `prices` (`cardId` TEXT NOT NULL, `setCode` TEXT NOT NULL, `eurAvg` REAL, `eurLow` REAL, `eurTrend` REAL, `eurAvg1` REAL, `eurAvg7` REAL, `eurAvg30` REAL, `eurVariantType` TEXT, `usdMarket` REAL, `usdLow` REAL, `cardMarketUrl` TEXT, `tcgPlayerUrl` TEXT, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`cardId`, `setCode`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'bcea7f2a39b8f506cc2858791bfb8f26')")
            db.seed()
            db.version = 1
        }
    }

    private fun openVersion2(): PokeVaultDatabase =
        Room.databaseBuilder(app, PokeVaultDatabase::class.java, dbName)
            .addMigrations(PokeVaultDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `le espansioni in cache restano, senza sigla e da riaggiornare`() = runBlocking {
        createVersion1 {
            execSQL(
                "INSERT INTO sets VALUES ('ita-sv4pt5', 'Destino di Paldea', 'Scarlatto e Violetto', 'ITA', " +
                    "91, 91, '2024-01-26', 'https://x/sets/PAF/image', 'https://x/sets/PAF/image', 1760000000000)"
            )
        }

        val db = openVersion2()
        try {
            val sets = db.setDao().getAll()
            assertEquals(1, sets.size)
            val set = sets.single()
            assertEquals("Destino di Paldea", set.name)
            assertEquals("", set.abbreviation)
            assertEquals(0L, set.cachedAt)
            assertEquals("", set.toTcgSet().abbreviation)
        } finally {
            db.close()
        }
    }

    @Test
    fun `i prezzi in cache sopravvivono alla migrazione`() = runBlocking {
        createVersion1 {
            execSQL(
                "INSERT INTO prices (cardId, setCode, eurLow, cachedAt) VALUES ('sv4pt5-1', 'PAF', 0.25, 1760000000000)"
            )
        }

        val db = openVersion2()
        try {
            val cursor = db.openHelper.readableDatabase.query("SELECT eurLow FROM prices WHERE cardId = 'sv4pt5-1'")
            cursor.use {
                assertEquals(1, it.count)
                it.moveToFirst()
                assertEquals(0.25, it.getDouble(0), 0.0001)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `dopo la migrazione la sigla si salva e si rilegge`() = runBlocking {
        createVersion1 { }

        val db = openVersion2()
        try {
            val entity = CachedSetEntity(
                id = "ita-swsh1",
                name = "Spada e Scudo",
                series = "Spada e Scudo",
                language = "ITA",
                printedTotal = 202,
                total = 202,
                releaseDate = "2020-02-07",
                symbolUrl = "",
                logoUrl = "",
                abbreviation = "SSH"
            )
            db.setDao().upsertSets(listOf(entity))
            assertEquals("SSH", db.setDao().getAll().single().toTcgSet().abbreviation)
        } finally {
            db.close()
        }
    }
}
