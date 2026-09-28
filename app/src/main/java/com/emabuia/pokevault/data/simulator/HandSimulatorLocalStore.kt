package com.emabuia.pokevault.data.simulator

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Una mano problematica salvata, scritta in JSON con Gson nelle preferenze.
 *
 * Fino alla 3.1.5 questa classe era fuori dai package tenuti da
 * proguard-rules.pro, e R8 ne rinominava i campi in `a`..`g`: il JSON sul
 * telefono ha quelle chiavi (verificato sul mapping della 3.1.4, vc37, e
 * della 3.1.5). Ora la classe e' tenuta e i campi si chiamano col loro nome;
 * gli `alternate` servono a rileggere le mani salvate dalle versioni vecchie.
 * Non toglierli finche' qualcuno puo' aggiornare da una di quelle.
 */
data class SavedProblemHand(
    @SerializedName(value = "id", alternate = ["a"]) val id: String,
    @SerializedName(value = "deckId", alternate = ["b"]) val deckId: String,
    @SerializedName(value = "deckName", alternate = ["c"]) val deckName: String,
    @SerializedName(value = "cards", alternate = ["d"]) val cards: List<String>,
    @SerializedName(value = "tags", alternate = ["e"]) val tags: List<String>,
    @SerializedName(value = "note", alternate = ["f"]) val note: String,
    @SerializedName(value = "createdAtMillis", alternate = ["g"]) val createdAtMillis: Long
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
