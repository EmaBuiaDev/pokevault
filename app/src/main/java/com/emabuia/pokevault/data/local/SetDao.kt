package com.emabuia.pokevault.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SetDao {

    @Upsert
    suspend fun upsertSets(sets: List<CachedSetEntity>)

    @Query("SELECT * FROM sets")
    suspend fun getAll(): List<CachedSetEntity>

    @Query("SELECT MAX(cachedAt) FROM sets")
    suspend fun getLastCacheTime(): Long?

    @Query("DELETE FROM sets WHERE cachedAt < :threshold")
    suspend fun deleteExpired(threshold: Long)
}
