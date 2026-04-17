package com.emabuia.pokevault.data.simulator

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class SavedProblemHand(
    val id: String,
    val deckId: String,
    val deckName: String,
    val cards: List<String>,
    val tags: List<String>,
    val note: String,
    val createdAtMillis: Long
)

class HandSimulatorLocalStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "hand_simulator_local"
        private const val KEY_SAVED_HANDS = "saved_problem_hands"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getSavedHands(deckId: String? = null): List<SavedProblemHand> {
        val json = prefs.getString(KEY_SAVED_HANDS, null).orEmpty()
        if (json.isBlank()) return emptyList()

        return runCatching {
            val type = object : TypeToken<List<SavedProblemHand>>() {}.type
            val allHands: List<SavedProblemHand> = gson.fromJson(json, type) ?: emptyList()
            allHands
                .asSequence()
                .filter { hand -> deckId.isNullOrBlank() || hand.deckId == deckId }
                .sortedByDescending { it.createdAtMillis }
                .toList()
        }.getOrDefault(emptyList())
    }

    fun saveProblemHand(
        deckId: String,
        deckName: String,
        cards: List<String>,
        tags: List<String>,
        note: String = ""
    ) {
        if (deckId.isBlank() || cards.isEmpty()) return

        val current = getSavedHandsRaw().toMutableList()
        current += SavedProblemHand(
            id = UUID.randomUUID().toString(),
            deckId = deckId,
            deckName = deckName,
            cards = cards,
            tags = tags.distinct(),
            note = note,
            createdAtMillis = System.currentTimeMillis()
        )

        // Keep only latest 200 entries to avoid unbounded local growth.
        val bounded = current
            .sortedByDescending { it.createdAtMillis }
            .take(200)

        prefs.edit().putString(KEY_SAVED_HANDS, gson.toJson(bounded)).apply()
    }

    fun deleteProblemHand(id: String) {
        if (id.isBlank()) return

        val updated = getSavedHandsRaw().filterNot { it.id == id }
        prefs.edit().putString(KEY_SAVED_HANDS, gson.toJson(updated)).apply()
    }

    private fun getSavedHandsRaw(): List<SavedProblemHand> {
        val json = prefs.getString(KEY_SAVED_HANDS, null).orEmpty()
        if (json.isBlank()) return emptyList()

        return runCatching {
            val type = object : TypeToken<List<SavedProblemHand>>() {}.type
            gson.fromJson<List<SavedProblemHand>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
