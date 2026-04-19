package com.emabuia.pokevault.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? =
        value?.let { gson.toJson(it) }

    @TypeConverter
    fun toStringList(value: String?): List<String>? =
        value?.let { gson.fromJson(it, object : TypeToken<List<String>>() {}.type) }

    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? =
        value?.let { gson.toJson(it) }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? =
        value?.let { gson.fromJson(it, object : TypeToken<Map<String, String>>() {}.type) }
}
