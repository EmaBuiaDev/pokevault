package com.emabuia.pokevault.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/**
 * Instrumented integration tests per SetDao
 *
 * Usa AndroidJUnit4Runner e il database vero in memoria
 * Testa:
 * - Inserimento/aggiornamento set
 * - Recupero set
 * - Cancellazione dati scaduti
 */
@RunWith(AndroidJUnit4::class)
class SetDaoInstrumentedTest {

    private lateinit var database: PokeVaultDatabase
    private lateinit var setDao: SetDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PokeVaultDatabase::class.java
        ).build()

        setDao = database.setDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Costruttore unico per i set di prova: quando [CachedSetEntity] cambia forma
     * c'e' un solo punto da aggiornare, invece di ogni caso di test.
     */
    private fun cachedSet(
        id: String,
        name: String = "Set $id",
        series: String = "Scarlet & Violet",
        releaseDate: String = "2023-01-01",
        total: Int = 100,
        cachedAt: Long = System.currentTimeMillis()
    ) = CachedSetEntity(
        id = id,
        name = name,
        series = series,
        language = "ENG",
        printedTotal = total,
        total = total,
        releaseDate = releaseDate,
        symbolUrl = "https://example.com/${id}_symbol.png",
        logoUrl = "https://example.com/$id.png",
        cachedAt = cachedAt
    )

    @Test
    fun testInsertAndRetrieveSets() {
        runBlocking {
            // Arrange
            val sets = listOf(
                cachedSet(id = "sv01", name = "Scarlet & Violet", releaseDate = "2023-04-14", total = 198),
                cachedSet(id = "sv04pt", name = "Paradox Rift", releaseDate = "2023-11-03", total = 182)
            )

            // Act
            setDao.upsertSets(sets)
            val retrieved = setDao.getAll()

            // Assert
            assertEquals(2, retrieved.size)
            assertTrue(retrieved.any { it.id == "sv01" })
            assertTrue(retrieved.any { it.id == "sv04pt" })
        }
    }

    @Test
    fun testUpsertReplacesExistingSet() {
        runBlocking {
            // Arrange
            setDao.upsertSets(listOf(cachedSet(id = "sv01", name = "Nome vecchio", total = 198)))

            // Act
            setDao.upsertSets(listOf(cachedSet(id = "sv01", name = "Nome nuovo", total = 200)))
            val retrieved = setDao.getAll()

            // Assert
            assertEquals("l'upsert aggiorna, non duplica", 1, retrieved.size)
            assertEquals("Nome nuovo", retrieved.first().name)
            assertEquals(200, retrieved.first().printedTotal)
        }
    }

    @Test
    fun testGetLastCacheTime() {
        runBlocking {
            // Arrange
            val now = System.currentTimeMillis()
            val sets = listOf(
                cachedSet(id = "sv01", total = 100, cachedAt = now - 10_000),
                cachedSet(id = "sv02", total = 120, cachedAt = now)
            )

            // Act
            setDao.upsertSets(sets)
            val lastTime = setDao.getLastCacheTime()

            // Assert
            assertNotNull(lastTime)
            assertEquals(now, lastTime)
        }
    }

    @Test
    fun testDeleteExpiredKeepsFreshSets() {
        runBlocking {
            // Arrange
            val now = System.currentTimeMillis()
            val sets = listOf(
                cachedSet(id = "scaduto", cachedAt = now - 60_000),
                cachedSet(id = "fresco", cachedAt = now)
            )
            setDao.upsertSets(sets)

            // Act
            setDao.deleteExpired(threshold = now - 30_000)
            val retrieved = setDao.getAll()

            // Assert
            assertEquals(1, retrieved.size)
            assertEquals("fresco", retrieved.first().id)
        }
    }

    @Test
    fun testDeleteAll() {
        runBlocking {
            // Arrange
            setDao.upsertSets(listOf(cachedSet(id = "sv01")))

            // Act
            var retrieved = setDao.getAll()
            assertEquals(1, retrieved.size)

            setDao.deleteAll()
            retrieved = setDao.getAll()

            // Assert
            assertEquals(0, retrieved.size)
        }
    }
}
