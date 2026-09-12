package com.emabuia.pokevault.ocr

/** Una singola lettura, come esce dal parsing di un frame. */
data class CardReading(
    val number: String? = null,
    val setTotal: String? = null,
    val name: String? = null,
    val setHint: String? = null,
    val hp: Int? = null,
    val supertype: CardSupertype = CardSupertype.POKEMON
) {
    fun isEmpty(): Boolean = number == null && name == null
}

/** Quello su cui i frame recenti sono d'accordo. */
data class ScanConsensus(
    val number: String? = null,
    val setTotal: String? = null,
    val name: String? = null,
    val setHint: String? = null,
    val hp: Int? = null,
    val supertype: CardSupertype = CardSupertype.POKEMON,
    /** Quanti frame recenti hanno letto questo numero. */
    val numberVotes: Int = 0,
    /** Quanti frame recenti hanno letto questo nome. */
    val nameVotes: Int = 0,
    /** true quando c'e' abbastanza accordo per lanciare una ricerca. */
    val isReady: Boolean = false
) {
    /** Etichetta mostrata nel preview: "67/87" oppure "67". */
    val displayId: String
        get() = when {
            number == null -> ""
            setTotal == null -> number
            else -> "$number/$setTotal"
        }

    /** Chiave con cui evitare di rilanciare due volte la stessa ricerca. */
    val searchKey: String
        get() = listOf(number.orEmpty(), setTotal.orEmpty(), ScanAggregator.normalizeName(name))
            .joinToString("|")
}

/**
 * Decide quando i frame letti concordano abbastanza da valere una ricerca.
 *
 * La prima versione contava frame *consecutivi* con la stessa lettura, e non
 * funzionava: la striscia con l'ID e' testo alto pochi pixel, quindi capita
 * spesso che un frame non produca niente, e quel singolo frame azzerava tutto
 * il conteggio. In mano all'utente significava dover tenere la carta immobile
 * e non vedere mai una conferma.
 *
 * Qui ogni lettura e' un voto che vale per una finestra di tempo: i frame
 * sporchi semplicemente non votano, ma non cancellano quelli buoni. Vince la
 * maggioranza, campo per campo, cosi' il nome smette anche di ballare a ogni
 * fotogramma.
 */
class ScanAggregator(
    private val windowMs: Long = WINDOW_MS,
    private val minNumberVotes: Int = MIN_NUMBER_VOTES,
    private val minNameOnlyVotes: Int = MIN_NAME_ONLY_VOTES
) {

    private val readings = ArrayDeque<Timed>()

    private data class Timed(val atMs: Long, val reading: CardReading)

    fun record(reading: CardReading, nowMs: Long) {
        if (reading.isEmpty()) return
        readings.addLast(Timed(nowMs, reading))
        prune(nowMs)
    }

    fun reset() = readings.clear()

    /** true quando nella finestra nessun frame ha prodotto un numero. */
    fun hasNoIdInWindow(nowMs: Long): Boolean {
        prune(nowMs)
        return readings.isNotEmpty() && readings.none { it.reading.number != null }
    }

    /**
     * Il consenso corrente, o null se nella finestra non c'e' niente.
     * [ScanConsensus.isReady] dice se basta per cercare: serve lo stesso numero
     * letto piu' volte, oppure - quando l'ID proprio non si legge - lo stesso
     * nome letto abbastanza volte da non essere un abbaglio.
     */
    fun consensus(nowMs: Long): ScanConsensus? {
        prune(nowMs)
        if (readings.isEmpty()) return null

        val values = readings.map { it.reading }

        val number = majorityOf(values.mapNotNull { it.number })
        val numberVotes = if (number == null) 0 else values.count { it.number == number }

        // Totale, nome e resto si contano solo tra i frame che non contraddicono
        // il numero vincente: altrimenti la lettura di un'altra carta inquadrata
        // di sfuggita entrerebbe nel consenso.
        val agreeing = if (number == null) values else values.filter { it.number == null || it.number == number }

        val name = majorityOf(agreeing.mapNotNull { it.name }, ::normalizeName)
        val nameVotes = if (name == null) 0 else {
            val key = normalizeName(name)
            agreeing.count { normalizeName(it.name) == key }
        }

        // Due letture dello stesso numero bastano. Ne basta una sola se il nome
        // e' stato letto piu' volte uguale: la ricerca incrocia comunque i due
        // campi, e sulla striscia l'ID esce pulito solo ogni tanto.
        val hasNumber = number != null &&
            (numberVotes >= minNumberVotes || (name != null && nameVotes >= minNameOnlyVotes - 1))
        val hasNameOnly = number == null && name != null && nameVotes >= minNameOnlyVotes

        return ScanConsensus(
            number = number,
            setTotal = majorityOf(agreeing.mapNotNull { it.setTotal }),
            name = name,
            setHint = majorityOf(agreeing.mapNotNull { it.setHint }),
            hp = majorityOf(agreeing.mapNotNull { it.hp?.toString() })?.toIntOrNull(),
            // TRAINER/ENERGY sono segnali forti e rari: se un frame li ha visti,
            // contano piu' del POKEMON che e' anche il valore di default.
            supertype = agreeing.map { it.supertype }.firstOrNull { it != CardSupertype.POKEMON }
                ?: CardSupertype.POKEMON,
            numberVotes = numberVotes,
            nameVotes = nameVotes,
            isReady = hasNumber || hasNameOnly
        )
    }

    private fun prune(nowMs: Long) {
        while (readings.isNotEmpty() && nowMs - readings.first().atMs > windowMs) {
            readings.removeFirst()
        }
        while (readings.size > MAX_READINGS) {
            readings.removeFirst()
        }
    }

    /**
     * Il valore piu' votato; a pari voti vince l'ultimo letto, che e' quello
     * con l'inquadratura piu' recente.
     */
    private fun <T> majorityOf(values: List<T>, key: (T) -> Any? = { it }): T? {
        if (values.isEmpty()) return null

        val counts = mutableMapOf<Any?, Int>()
        values.forEach { value -> counts[key(value)] = (counts[key(value)] ?: 0) + 1 }
        val best = counts.maxOf { it.value }

        return values.last { counts[key(it)] == best }
    }

    companion object {
        /** Quanto vale un voto. Due frame utili entrano comodamente in questo arco. */
        private const val WINDOW_MS = 3_500L

        /** Voti sul numero richiesti per cercare: uno solo puo' essere un abbaglio. */
        private const val MIN_NUMBER_VOTES = 2

        /** Senza ID serve piu' accordo sul nome, che l'OCR sbaglia molto di piu'. */
        private const val MIN_NAME_ONLY_VOTES = 4

        private const val MAX_READINGS = 16

        private val NON_LETTERS = Regex("""[^a-z0-9à-ÿ]+""")

        /** Normalizzazione minima, solo per raggruppare letture dello stesso nome. */
        fun normalizeName(name: String?): String {
            if (name.isNullOrBlank()) return ""
            return name.lowercase().replace(NON_LETTERS, " ").trim()
        }
    }
}
