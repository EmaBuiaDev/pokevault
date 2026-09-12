package com.emabuia.pokevault.data.model

import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ocr.CardSupertype
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

/**
 * Decide quale carta del catalogo spiega meglio cio' che l'OCR ha letto.
 *
 * E' la parte piu' delicata dello scanner: da qui dipende se all'utente
 * compare una carta da confermare, la rosa delle tre piu' probabili, oppure
 * niente. Sta fuori dal ViewModel proprio per poter essere messa sotto test
 * senza una camera e senza Firestore.
 *
 * Il numero da collezione e' gia' un filtro a monte (lo applica il catalogo),
 * quindi qui pesano solo i campi che distinguono una carta dall'omonima di
 * un'altra espansione: il totale stampato e il nome.
 */
object ScannerMatcher {

    /** Cio' che l'OCR ha letto, dopo che i frame hanno trovato un accordo. */
    data class Signals(
        val name: String? = null,
        val number: String? = null,
        val setTotal: String? = null,
        val hp: Int? = null,
        val supertype: CardSupertype = CardSupertype.POKEMON
    )

    /** Un candidato con il dettaglio del punteggio, utile anche nei log. */
    data class Candidate(
        val card: TcgCard,
        val score: Double,
        val nameSimilarity: Double,
        val totalExact: Boolean
    )

    /** Quante carte proporre quando il verdetto non e' netto. */
    const val MAX_CANDIDATES = 3

    // ═══════════════════════════════════════════
    // CLASSIFICA
    // ═══════════════════════════════════════════

    fun rank(cards: List<TcgCard>, signals: Signals): List<Candidate> {
        if (cards.isEmpty()) return emptyList()

        val pool = narrowBySupertype(cards, signals.supertype)
        val totalValue = signals.setTotal?.toIntOrNull()
        val hasUsableName = isUsableName(signals.name)

        return pool.map { card ->
            val similarity = if (hasUsableName) nameSimilarity(signals.name, card.name) else 0.0
            val printedTotal = card.set?.printedTotal?.takeIf { it > 0 }
            val totalExact = totalValue != null && printedTotal == totalValue

            val totalScore = when {
                totalValue == null || printedTotal == null -> 0.0
                totalExact -> TOTAL_EXACT_BONUS
                abs(printedTotal - totalValue) <= SET_TOTAL_TOLERANCE -> TOTAL_CLOSE_BONUS
                // Totale letto e totale del set incompatibili: e' un'altra
                // espansione. Penalizza senza escludere, perche' la cifra puo'
                // sempre essere stata letta male.
                else -> TOTAL_MISMATCH_PENALTY
            }

            val hpScore = if (signals.hp != null && card.hp?.toIntOrNull() == signals.hp) HP_MATCH_BONUS else 0.0

            Candidate(
                card = card,
                score = similarity * NAME_WEIGHT + totalScore + hpScore,
                nameSimilarity = similarity,
                totalExact = totalExact
            )
        }.sortedWith(
            compareByDescending<Candidate> { it.score }
                .thenByDescending { it.nameSimilarity }
        )
    }

    /**
     * Preferenza morbida sul supertype letto: TRAINER ed ENERGY sono segnali
     * rari e affidabili, ma non devono mai svuotare il pool, perche' POKEMON e'
     * anche il valore di ripiego quando l'OCR non ha capito.
     */
    private fun narrowBySupertype(cards: List<TcgCard>, supertype: CardSupertype): List<TcgCard> {
        return when (supertype) {
            CardSupertype.TRAINER -> cards.filter { it.supertype.equals("trainer", ignoreCase = true) }.ifEmpty { cards }
            CardSupertype.ENERGY -> cards.filter { it.supertype.equals("energy", ignoreCase = true) }.ifEmpty { cards }
            CardSupertype.POKEMON -> cards
        }
    }

    // ═══════════════════════════════════════════
    // VERDETTO
    // ═══════════════════════════════════════════

    /**
     * true quando la carta in testa si puo' proporre da sola.
     *
     * Servono due cose insieme: che sia credibile (totale esatto o nome molto
     * simile) e che stacchi la seconda. Tutto il resto e' un dubbio, e un
     * dubbio si risolve mostrando la rosa.
     */
    fun hasClearWinner(ranked: List<Candidate>): Boolean {
        val top = ranked.firstOrNull() ?: return false
        val second = ranked.getOrNull(1)

        val credible = top.totalExact ||
            top.nameSimilarity >= STRONG_NAME_SIMILARITY ||
            // Con un solo candidato il numero ha gia' fatto tutto il lavoro:
            // basta che il nome non smentisca, non serve che confermi.
            (ranked.size == 1 && top.nameSimilarity >= WEAK_NAME_SIMILARITY)

        val clear = second == null || (top.score - second.score) >= DECISION_MARGIN
        return credible && clear
    }

    /**
     * true quando il verdetto regge anche senza un tocco di conferma, cioe' in
     * modalita' continua. La soglia sul nome e' piu' alta di quella normale:
     * senza una mano a fare da rete, un nome "abbastanza simile" non basta.
     *
     * Non pretende il totale esatto, e la prima versione lo faceva: il totale
     * stampato non e' noto per tutte le espansioni ITA (dove manca si ripiega
     * sul conteggio del catalogo, che include le segrete e quindi non combacia
     * mai), quindi la modalita' continua non sarebbe scattata quasi mai. Non
     * serve: [hasClearWinner] garantisce gia' che il candidato sia solo oppure
     * molto staccato, e il numero e' un filtro esatto a monte.
     */
    fun isCertain(ranked: List<Candidate>): Boolean {
        val top = ranked.firstOrNull() ?: return false
        return hasClearWinner(ranked) && top.nameSimilarity >= AUTO_ADD_NAME_SIMILARITY
    }

    // ═══════════════════════════════════════════
    // NOMI
    // ═══════════════════════════════════════════

    /** Un nome troppo corto o fatto di sole parole di gioco non vale una ricerca. */
    fun isUsableName(name: String?): Boolean {
        val normalized = normalizeName(name)
        return normalized.length >= MIN_USABLE_NAME_LENGTH && normalized.any { it.isLetter() }
    }

    /**
     * Somiglianza 0..1 fra il nome letto e quello di una carta, confrontando
     * anche le versioni senza accenti: i nomi dei Pokemon sono identici in ogni
     * lingua ma l'OCR non sempre prende i segni diacritici.
     */
    fun nameSimilarity(readName: String?, cardName: String?): Double {
        return max(
            similarity(readName, cardName),
            similarity(stripAccents(readName), stripAccents(cardName))
        )
    }

    private fun similarity(expectedName: String?, actualName: String?): Double {
        val expected = normalizeName(expectedName)
        val actual = normalizeName(actualName)

        if (expected.isBlank() || actual.isBlank()) return 0.0
        if (expected == actual) return 1.0
        if (actual.startsWith(expected) || expected.startsWith(actual)) return PREFIX_SIMILARITY

        val distance = levenshtein(expected, actual)
        val maxLength = max(expected.length, actual.length)
        val charSimilarity = (1.0 - distance.toDouble() / maxLength.toDouble()).coerceIn(0.0, 1.0)

        val expectedTokens = expected.split(" ").filter { it.isNotBlank() }.toSet()
        val actualTokens = actual.split(" ").filter { it.isNotBlank() }.toSet()
        val tokenSimilarity = if (expectedTokens.isNotEmpty() && actualTokens.isNotEmpty()) {
            expectedTokens.intersect(actualTokens).size.toDouble() /
                max(expectedTokens.size, actualTokens.size).toDouble()
        } else {
            0.0
        }

        return (charSimilarity * CHAR_WEIGHT) + (tokenSimilarity * TOKEN_WEIGHT)
    }

    /**
     * Normalizza per il confronto togliendo le parole che descrivono la
     * meccanica di gioco: l'OCR le raccoglie dalla zona alta della carta
     * insieme al nome, e confrontarle farebbe somigliare fra loro carte che non
     * hanno niente in comune.
     */
    fun normalizeName(name: String?): String {
        if (name.isNullOrBlank()) return ""

        return name
            .lowercase()
            .replace(NAME_INVALID_CHARS, " ")
            .split(WHITESPACE)
            .filter { token -> token.length >= 2 && token !in GAME_WORDS }
            .joinToString(" ")
            .trim()
    }

    private fun stripAccents(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return COMBINING_MARKS.replace(decomposed, "")
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)

        for (leftIndex in left.indices) {
            current[0] = leftIndex + 1
            for (rightIndex in right.indices) {
                val substitutionCost = if (left[leftIndex] == right[rightIndex]) 0 else 1
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + substitutionCost
                )
            }
            previous.indices.forEach { index -> previous[index] = current[index] }
        }

        return previous[right.length]
    }

    // ═══════════════════════════════════════════
    // TARATURA
    // ═══════════════════════════════════════════

    /** Peso del nome: 0..1 di somiglianza diventano 0..60 punti. */
    private const val NAME_WEIGHT = 60.0

    /**
     * Il totale stampato arriva dalla stessa striscia dell'ID e identifica
     * l'espansione quasi da solo: vale piu' di un nome quasi perfetto.
     */
    private const val TOTAL_EXACT_BONUS = 55.0
    private const val TOTAL_CLOSE_BONUS = 18.0
    private const val TOTAL_MISMATCH_PENALTY = -30.0
    private const val SET_TOTAL_TOLERANCE = 2

    private const val HP_MATCH_BONUS = 8.0

    /** Distacco dal secondo sotto il quale la scelta passa all'utente. */
    private const val DECISION_MARGIN = 25.0

    private const val STRONG_NAME_SIMILARITY = 0.72
    private const val WEAK_NAME_SIMILARITY = 0.42
    private const val AUTO_ADD_NAME_SIMILARITY = 0.80

    private const val MIN_USABLE_NAME_LENGTH = 3
    private const val PREFIX_SIMILARITY = 0.92
    private const val CHAR_WEIGHT = 0.75
    private const val TOKEN_WEIGHT = 0.25

    private val NAME_INVALID_CHARS = Regex("""[^a-z0-9à-ÿ\s'-]""")
    private val WHITESPACE = Regex("""\s+""")

    /** Unicode combining diacritical marks (residui della decomposizione NFD). */
    private val COMBINING_MARKS = Regex("""\p{InCombiningDiacriticalMarks}""")

    private val GAME_WORDS = setOf(
        "trainer", "allenatore", "supporter", "aiuto", "item", "strumento",
        "stadium", "stadio", "tool", "energy", "energia", "pokemon", "pokmon",
        "basic", "base", "lotta", "fight", "fighting", "ability", "abilita",
        "attack", "attacco"
    )
}
