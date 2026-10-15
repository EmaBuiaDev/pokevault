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
    @SerializedName(value = "createdAtMillis", alternate = ["g"]) val createdAtMillis: Long,
    // Dalla 3.1.9. Nullabili per forza: Gson non passa dal costruttore, e una
    // mano salvata prima non ha queste chiavi -- un tipo non nullo arriverebbe
    // null lo stesso, e il primo accesso andrebbe in crash.
    /** Gli id delle carte, per mostrarne le immagini. Le mani vecchie hanno solo i nomi. */
    @SerializedName("cardIds") val cardIds: List<String>? = null,
    /** Il voto dato dall'utente nella Prova, vedi [HandRating.code]. Null per le mani salvate dall'Analisi. */
    @SerializedName("rating") val rating: String? = null,
    /** Il giudizio dell'app sulla stessa mano, vedi [HandRating.code]. */
    @SerializedName("appRating") val appRating: String? = null,
    /** Chi iniziava: "first" o "second". */
    @SerializedName("turnOrder") val turnOrder: String? = null
) {
    val userRating: HandRating? get() = HandRating.fromCode(rating)
    val appHandRating: HandRating? get() = HandRating.fromCode(appRating)
    val wentFirst: Boolean? get() = when (turnOrder) {
        "first" -> true
        "second" -> false
        else -> null
    }
}

/**
 * Il voto rapido che l'utente da' a una mano della Prova.
 *
 * Tre livelli e non i quattro del verdetto dell'app: al tavolo la domanda e'
 * "questa mano parte bene?", e tre risposte bastano. Si salva con un codice
 * scritto a mano e non con il nome della costante, che R8 puo' cambiare.
 */
enum class HandRating(val code: String) {
    BAD("bad"),
    RISKY("risky"),
    GOOD("good");

    /** Parte bene, si' o no: e' su questo che si confronta il voto con l'app. */
    val startsWell: Boolean get() = this == GOOD

    companion object {
        fun fromCode(code: String?): HandRating? = entries.firstOrNull { it.code == code }

        /**
         * Il verdetto dell'app tradotto nei tre livelli dell'utente. "Giocabile"
         * va con "Buona": per l'app e' una mano che parte e ha una risorsa.
         */
        fun fromVerdict(verdict: HandVerdict): HandRating = when (verdict) {
            HandVerdict.GREAT, HandVerdict.PLAYABLE -> GOOD
            HandVerdict.RISKY -> RISKY
            HandVerdict.MULLIGAN -> BAD
        }
    }
}

/**
 * Le carte di una mano salvata, da disegnare.
 *
 * Prima per id, che e' esatto; poi per nome, che e' tutto cio' che hanno le
 * mani salvate fino alla 3.1.8. Una carta tolta dal mazzo nel frattempo resta
 * col solo nome: SimCardFace lo scrive al posto dell'immagine.
 */
fun SavedProblemHand.resolveCards(pool: List<SimulatorCard>): List<SimulatorCard> {
    val byId = pool.filter { it.id.isNotBlank() }.associateBy { it.id }
    val byName = pool.groupBy { it.name.lowercase().trim() }
    return cards.mapIndexed { index, name ->
        cardIds?.getOrNull(index)?.let(byId::get)
            ?: byName[name.lowercase().trim()]?.firstOrNull()
            ?: SimulatorCard(name = name, isBasic = false, isEnergy = false, isSupporter = false, isOutCard = false)
    }
}

/** Null = tutte; altrimenti solo quelle con quel voto (le mani dell'Analisi non ne hanno). */
fun List<SavedProblemHand>.filterByRating(rating: HandRating?): List<SavedProblemHand> =
    if (rating == null) this else filter { it.userRating == rating }

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

    /** Salva una mano e ne restituisce l'id, o null se non c'era niente da salvare. */
    fun saveProblemHand(
        deckId: String,
        deckName: String,
        cards: List<String>,
        tags: List<String>,
        note: String = "",
        cardIds: List<String>? = null,
        rating: HandRating? = null,
        appRating: HandRating? = null,
        turnOrder: TurnOrder? = null
    ): String? {
        if (deckId.isBlank() || cards.isEmpty()) return null

        val id = UUID.randomUUID().toString()
        val current = getSavedHandsRaw().toMutableList()
        current += SavedProblemHand(
            id = id,
            deckId = deckId,
            deckName = deckName,
            cards = cards,
            tags = tags.distinct(),
            note = note,
            createdAtMillis = System.currentTimeMillis(),
            cardIds = cardIds?.takeIf { it.any(String::isNotBlank) },
            rating = rating?.code,
            appRating = appRating?.code,
            turnOrder = turnOrder?.let { if (it == TurnOrder.FIRST) "first" else "second" }
        )

        // Keep only latest 200 entries to avoid unbounded local growth.
        val bounded = current
            .sortedByDescending { it.createdAtMillis }
            .take(200)

        prefs.edit().putString(KEY_SAVED_HANDS, gson.toJson(bounded)).apply()
        return id
    }

    /** Cambia il voto di una mano gia' salvata: cambiare idea non la duplica. */
    fun updateRating(id: String, rating: HandRating) {
        if (id.isBlank()) return
        val updated = getSavedHandsRaw().map { if (it.id == id) it.copy(rating = rating.code) else it }
        prefs.edit().putString(KEY_SAVED_HANDS, gson.toJson(updated)).apply()
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
