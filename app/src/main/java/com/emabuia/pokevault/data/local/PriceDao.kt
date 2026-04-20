package com.emabuia.pokevault.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PriceDao {

    @Upsert
    suspend fun upsertPrice(price: CachedPriceEntity)

    @Upsert
    suspend fun upsertPrices(prices: List<CachedPriceEntity>)

    @Query("SELECT * FROM prices WHERE cardId = :cardId AND setCode = :setCode")
    suspend fun getPrice(cardId: String, setCode: String): CachedPriceEntity?

    @Query("SELECT * FROM prices WHERE cachedAt < :threshold ORDER BY cachedAt ASC LIMIT :limit")
    suspend fun getExpiredPrices(threshold: Long, limit: Int): List<CachedPriceEntity>

    @Query("DELETE FROM prices WHERE cachedAt < :threshold")
    suspend fun deleteExpired(threshold: Long)
}
