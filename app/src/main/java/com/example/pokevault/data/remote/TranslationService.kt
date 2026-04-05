package com.example.pokevault.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object TranslationService {

    private val memoryCache = ConcurrentHashMap<String, String>()
    @Volatile private var diskCacheLoaded = false

    private const val PREFS_NAME = "pokevault_translations"
    private const val CACHE_KEY = "it_en_translations"

    @Synchronized
    fun loadCache(context: Context) {
        if (diskCacheLoaded) return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(CACHE_KEY, null)
            if (json != null) {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val cached: Map<String, String> = Gson().fromJson(json, type)
                memoryCache.putAll(cached)
            }
        } catch (_: Exception) {}
        diskCacheLoaded = true
    }

    private fun saveCache(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(CACHE_KEY, Gson().toJson(memoryCache)).apply()
        } catch (_: Exception) {}
    }

    fun getCached(query: String): String? {
        return memoryCache[query.lowercase().trim()]
    }

    suspend fun translateItToEn(query: String, context: Context? = null): String? {
        val key = query.lowercase().trim()
        if (key.isBlank() || key.length < 3) return null

        memoryCache[key]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=it|en"
                val response = URL(url).readText()
                val json = JSONObject(response)
                val translated = json.getJSONObject("responseData")
                    .getString("translatedText")
                    .lowercase()
                    .trim()

                if (translated.isNotBlank() && translated != key) {
                    memoryCache[key] = translated
                    context?.let { saveCache(it) }
                    translated
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }
}
