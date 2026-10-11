package com.emabuia.pokevault.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.emabuia.pokevault.data.remote.SetImages
import com.emabuia.pokevault.data.remote.TcgSet

@Entity(tableName = "sets")
data class CachedSetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val series: String,
    val language: String?,
    val printedTotal: Int,
    val total: Int,
    val releaseDate: String,
    val symbolUrl: String,
    val logoUrl: String,
    val cachedAt: Long = System.currentTimeMillis(),
    // Il default sta anche nello schema: e' quello che scrive la migrazione
    // 1 -> 2 sulle righe gia' in cache (vedi PokeVaultDatabase.MIGRATION_1_2).
    @ColumnInfo(defaultValue = "")
    val abbreviation: String = ""
)

fun CachedSetEntity.toTcgSet(): TcgSet = TcgSet(
    id = id,
    name = name,
    series = series,
    language = language,
    printedTotal = printedTotal,
    total = total,
    releaseDate = releaseDate,
    images = SetImages(symbol = symbolUrl, logo = logoUrl),
    abbreviation = abbreviation
)

fun TcgSet.toEntity(): CachedSetEntity = CachedSetEntity(
    id = id,
    name = name,
    series = series,
    language = language,
    printedTotal = printedTotal,
    total = total,
    releaseDate = releaseDate,
    symbolUrl = images.symbol,
    logoUrl = images.logo,
    abbreviation = abbreviation
)
