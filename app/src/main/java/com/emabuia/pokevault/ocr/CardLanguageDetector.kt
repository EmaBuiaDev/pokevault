package com.emabuia.pokevault.ocr

import com.emabuia.pokevault.data.catalog.CatalogLang
import java.text.Normalizer
import java.util.Locale

/**
 * In che lingua e' stampata la carta inquadrata (piano carte EN, Fase 4).
 *
 * Serve per i set gemelli: numero e totale sono gli stessi in italiano e in
 * inglese, quindi solo il testo dice quale delle due carte si ha in mano.
 *
 * Si contano le parole che esistono in una lingua sola. I nomi dei Pokemon
 * non aiutano (Pikachu e' Pikachu ovunque), ma il testo di regole lo fanno:
 * "Weakness"/"Debolezza", "Retreat"/"Ritirata", "Stage"/"Fase". Una parola
 * sola non basta, perche' l'OCR sbaglia: serve un vantaggio di [MIN_MARGIN].
 */
object CardLanguageDetector {

    /** Quante parole in piu' deve avere una lingua sull'altra per decidere. */
    const val MIN_MARGIN = 2

    // Parole intere, gia' senza accenti e in minuscolo. Niente parole che
    // esistono in tutte e due le lingue: ne' "pokemon" ne' "illus", che e'
    // stampato anche sulle carte italiane ("Illus. Atsuko Nishida").
    private val ENGLISH = setOf(
        "weakness", "resistance", "retreat", "basic", "stage", "evolves", "from",
        "ability", "trainer", "supporter", "item", "stadium", "tool", "energy",
        "your", "opponent", "opponents", "damage", "this", "attack", "attacks",
        "flip", "coin", "heads", "tails", "discard", "draw", "cards", "card",
        "bench", "active", "turn", "each", "the", "of", "and", "to", "may",
        "rule", "when", "knocked", "out", "prize", "search", "deck",
        "shuffle", "hand", "put", "into", "onto", "attach"
    )

    private val ITALIAN = setOf(
        "debolezza", "resistenza", "ritirata", "base", "fase", "evoluzione",
        "evolve", "abilita", "allenatore", "aiuto", "strumento", "stadio",
        "energia", "tuo", "tua", "tuoi", "tue", "avversario", "danni", "danno",
        "questo", "questa", "attacco", "attacchi", "lancia", "moneta", "testa",
        "croce", "scarta", "pesca", "carte", "carta", "panchina", "attivo",
        "turno", "ogni", "il", "lo", "la", "gli", "le", "di", "del", "della",
        "dell", "all", "nel", "puoi", "regola", "quando", "messo", "ko", "premio", "cerca",
        "mazzo", "mischia", "mano", "metti", "assegna"
    )

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^a-z]+")

    /** Quante parole di ciascuna lingua ci sono nel testo. */
    fun counts(text: String): Pair<Int, Int> {
        val words = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.ROOT)
            .split(NON_WORD)
            .filter { it.length >= 2 }
        var english = 0
        var italian = 0
        for (word in words) {
            if (word in ENGLISH) english++
            if (word in ITALIAN) italian++
        }
        return english to italian
    }

    /** La lingua del testo, o null se non si capisce (troppo poco testo, o pari). */
    fun detect(text: String): CatalogLang? {
        val (english, italian) = counts(text)
        return when {
            english - italian >= MIN_MARGIN -> CatalogLang.EN
            italian - english >= MIN_MARGIN -> CatalogLang.IT
            else -> null
        }
    }
}

/**
 * I voti dei fotogrammi recenti sulla lingua: un fotogramma letto male non
 * deve far cambiare catalogo. Vince la lingua con piu' voti, se ne ha almeno
 * [minVotes] e piu' dell'altra. Contano solo gli ultimi [windowMs]: i voti di
 * una carta tolta poco fa non devono pesare su quella che si inquadra adesso.
 */
class LanguageVotes(private val minVotes: Int = 2, private val windowMs: Long = 3_500L) {
    private val votes = ArrayDeque<Pair<Long, CatalogLang>>()

    fun record(lang: CatalogLang?, nowMs: Long) {
        prune(nowMs)
        if (lang != null) votes.addLast(nowMs to lang)
    }

    fun winner(nowMs: Long): CatalogLang? {
        prune(nowMs)
        val counts = votes.groupingBy { it.second }.eachCount()
        val best = counts.maxByOrNull { it.value } ?: return null
        val others = counts.filterKeys { it != best.key }.values.maxOrNull() ?: 0
        return best.key.takeIf { best.value >= minVotes && best.value > others }
    }

    fun reset() = votes.clear()

    private fun prune(nowMs: Long) {
        while (votes.isNotEmpty() && nowMs - votes.first().first > windowMs) votes.removeFirst()
    }
}
