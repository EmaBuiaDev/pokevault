package com.emabuia.pokevault.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromCode(code: String?): ThemeMode =
            entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/**
 * Preferenza di tema, con lo stesso schema gia' usato da AppLocale.
 *
 * Serve perche' l'app finora era scura per costruzione: senza un modo per
 * forzare chiaro o scuro, il tema chiaro sarebbe verificabile solo cambiando
 * l'impostazione di sistema del dispositivo.
 */
object ThemePreference {

    private const val PREFS_NAME = "pokevault_prefs"
    private const val KEY_THEME = "app_theme"

    var current by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        current = ThemeMode.fromCode(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.code))
    }

    fun set(mode: ThemeMode, context: Context) {
        current = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, mode.code).apply()
    }
}
