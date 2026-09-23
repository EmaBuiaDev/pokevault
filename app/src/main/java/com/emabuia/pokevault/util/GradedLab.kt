package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.model.PokemonCard

/**
 * Conti, filtri e ordinamenti della sezione Gradate.
 *
 * Stessa impostazione di [CollectorLab] e [WishlistLab], e per la stessa
 * ragione: prima la schermata faceva i suoi conti dentro il ViewModel a colpi di
 * `sumOf` e `groupBy` scritti sul posto, quindi non erano verificabili — e uno
 * di quei conti era sbagliato in modo invisibile (vedi [GradedLab.companyKey]).
 *
 * Una carta gradata non e' una carta qualsiasi: e' un oggetto unico chiuso in un
 * blocco di plastica, con un ente che l'ha valutata e un voto che ne decide il
 * prezzo. Tutto quello che c'e' qui gira attorno a queste due cose.
 */

/**
 * Fascia di voto.
 *
 * Le soglie sono quelle che usano i collezionisti, non intervalli regolari: il 10
 * sta da solo perche' vale un multiplo del 9, e sotto il 6 le differenze non
 * contano piu' per il prezzo.
 */
enum class GradeTier {
    /** Voto 10: la vetrina. */
    GEM,

    /** 9 e 9.5. */
    MINT,

    /** 8 e 8.5. */
    NEAR_MINT,

    /** Da 6 a 7.5. */
    EXCELLENT,

    /** Sotto il 6. */
    PLAYED,

    /** Marcata come gradata ma senza voto: succede, e va vista. */
    UNGRADED
}

enum class GradedSort { GRADE_DESC, VALUE_DESC, NAME, RECENT }

/** Una voce del filtro per ente, col suo conto. */
data class GradedCompany(val key: String, val count: Int)

/** Quante slab ci sono in una fascia di voto. */
data class GradeBucket(val tier: GradeTier, val count: Int)

/**
 * Il riassunto in cima alla sezione.
 *
 * I conti sono per *slab* e non per riga di collezione: due copie della stessa
 * carta gradata sono due blocchi di plastica, due voti e due prezzi. Per questo
 * anche la media e' pesata sulla quantita'.
 */
data class GradedSummary(
    /** Pezzi, quantita' inclusa. */
    val slabs: Int,
    /** Righe di collezione, che possono valere piu' di un pezzo. */
    val entries: Int,
    /** Slab di cui si conosce il voto: il denominatore di [averageGrade]. */
    val graded: Int,
    /** Slab con voto 10. */
    val gems: Int,
    /** Media dei voti noti, 0 se non ce n'e' nessuno. */
    val averageGrade: Float,
    val totalValue: Double,
    /** Slab con un valore stimato: dice quanto fidarsi di [totalValue]. */
    val pricedSlabs: Int
) {
    /** Slab senza prezzo. Sopra lo zero, [totalValue] e' una stima al ribasso. */
    val unpricedSlabs: Int get() = (slabs - pricedSlabs).coerceAtLeast(0)

    /** Slab marcate gradate ma senza voto. */
    val ungraded: Int get() = (slabs - graded).coerceAtLeast(0)
}

object GradedLab {

    /**
     * Segnaposto dell'ente sconosciuto.
     *
     * Esiste perche' prima il chip si costruiva su "N/D" ma il filtro
     * confrontava la stringa vuota: si poteva selezionare un gruppo che non
     * conteneva mai niente, e la griglia restava vuota senza spiegare perche'.
     */
    const val UNKNOWN_COMPANY: String = "?"

    /**
     * Ente di una carta, normalizzato.
     *
     * Il maiuscolo unisce "psa" e "PSA", che altrimenti diventano due filtri
     * distinti sullo stesso ente.
     */
    fun companyKey(card: PokemonCard): String =
        card.gradingCompany.trim().uppercase().ifBlank { UNKNOWN_COMPANY }

    /** La fascia di [grade]. Null vuol dire gradata ma senza voto. */
    fun tierOf(grade: Float?): GradeTier = when {
        grade == null || grade <= 0f -> GradeTier.UNGRADED
        grade >= 10f -> GradeTier.GEM
        grade >= 9f -> GradeTier.MINT
        grade >= 8f -> GradeTier.NEAR_MINT
        grade >= 6f -> GradeTier.EXCELLENT
        else -> GradeTier.PLAYED
    }

    /**
     * Il voto come lo stampa l'ente sull'etichetta: "10" e non "10.0".
     *
     * I voti sono mezzi punti, quindi il decimale serve solo quando c'e'
     * davvero. L'interpolazione di un Float lo scriveva sempre, e un PSA 10 —
     * l'etichetta piu' importante di tutta la sezione — si leggeva "10.0".
     *
     * Il decimale non viene arrotondato a mezzo punto: la stessa funzione scrive
     * anche la media di [GradedSummary.averageGrade], che non e' un voto e puo'
     * legittimamente valere 9.3. Fingere un 9.5 li' sarebbe un numero inventato.
     */
    fun formatGrade(grade: Float?): String {
        if (grade == null || grade <= 0f) return "—"
        // Formattato prima e ripulito dopo, non il contrario: cosi' un 9.96
        // finisce su "10" e non su "10.0".
        return String.format(java.util.Locale.US, "%.1f", grade).removeSuffix(".0")
    }

    // ── Riassunto ─────────────────────────────────────────────────────────

    fun summary(cards: List<PokemonCard>): GradedSummary {
        var slabs = 0
        var graded = 0
        var gems = 0
        var gradeSum = 0.0
        var value = 0.0
        var priced = 0

        cards.forEach { card ->
            val pieces = card.quantity.coerceAtLeast(1)
            slabs += pieces
            val grade = card.grade
            if (grade != null && grade > 0f) {
                graded += pieces
                gradeSum += grade.toDouble() * pieces
                if (grade >= 10f) gems += pieces
            }
            if (card.estimatedValue > 0.0) {
                priced += pieces
                value += card.estimatedValue * pieces
            }
        }

        return GradedSummary(
            slabs = slabs,
            entries = cards.size,
            graded = graded,
            gems = gems,
            averageGrade = if (graded > 0) (gradeSum / graded).toFloat() else 0f,
            totalValue = value,
            pricedSlabs = priced
        )
    }

    /**
     * Gli enti presenti, dal piu' rappresentato al meno.
     *
     * L'ordine e' stabilito e non quello in cui Firestore restituisce i
     * documenti: con `groupBy` i chip si riordinavano da soli a ogni arrivo di
     * uno snapshot, e il filtro che si stava per toccare si spostava sotto il
     * dito. A pari conto decide il nome, cosi' l'ordine e' sempre lo stesso.
     */
    fun companyCounts(cards: List<PokemonCard>): List<GradedCompany> =
        cards.groupingBy { companyKey(it) }
            .eachCount()
            .map { (key, count) -> GradedCompany(key, count) }
            .sortedWith(compareByDescending<GradedCompany> { it.count }.thenBy { it.key })

    /**
     * La distribuzione dei voti, nell'ordine delle fasce e senza i buchi.
     *
     * Le fasce vuote non compaiono: servono a disegnare una barra e a offrire un
     * filtro, e un filtro che non seleziona niente e' solo rumore.
     */
    fun tierCounts(cards: List<PokemonCard>): List<GradeBucket> {
        val counts = cards.groupingBy { tierOf(it.grade) }.eachCount()
        return GradeTier.entries.mapNotNull { tier ->
            counts[tier]?.let { GradeBucket(tier, it) }
        }
    }

    // ── Filtri e ordinamento ──────────────────────────────────────────────

    /**
     * [company] e [tier] a null vogliono dire "tutti". La ricerca guarda nome,
     * espansione, numero ed ente: sono i quattro modi in cui si cerca una slab
     * che si sa di avere.
     */
    fun filter(
        cards: List<PokemonCard>,
        query: String = "",
        company: String? = null,
        tier: GradeTier? = null
    ): List<PokemonCard> {
        val needle = query.trim()
        return cards.filter { card ->
            val matchesQuery = needle.isEmpty() ||
                card.name.contains(needle, ignoreCase = true) ||
                card.set.contains(needle, ignoreCase = true) ||
                card.cardNumber.contains(needle, ignoreCase = true) ||
                card.gradingCompany.contains(needle, ignoreCase = true)
            val matchesCompany = company == null || companyKey(card) == company
            val matchesTier = tier == null || tierOf(card.grade) == tier
            matchesQuery && matchesCompany && matchesTier
        }
    }

    /**
     * Ogni ordinamento ha i suoi spareggi, perche' i pareggi sono la norma qui:
     * in una collezione di slab meta' delle carte ha lo stesso voto.
     */
    fun sort(cards: List<PokemonCard>, sort: GradedSort): List<PokemonCard> = when (sort) {
        GradedSort.GRADE_DESC -> cards.sortedWith(
            compareByDescending<PokemonCard> { it.grade ?: -1f }
                .thenByDescending { slabValue(it) }
                .thenBy { it.name.lowercase() }
        )
        GradedSort.VALUE_DESC -> cards.sortedWith(
            compareByDescending<PokemonCard> { slabValue(it) }
                .thenByDescending { it.grade ?: -1f }
                .thenBy { it.name.lowercase() }
        )
        GradedSort.NAME -> cards.sortedWith(
            compareBy<PokemonCard> { it.name.lowercase() }
                .thenByDescending { it.grade ?: -1f }
        )
        GradedSort.RECENT -> cards.sortedWith(
            compareByDescending<PokemonCard> { it.addedAt?.seconds ?: Long.MIN_VALUE }
                .thenByDescending { it.grade ?: -1f }
                .thenBy { it.name.lowercase() }
        )
    }

    /** Quanto vale la riga: il prezzo di una slab per quante ce ne sono. */
    fun slabValue(card: PokemonCard): Double =
        card.estimatedValue * card.quantity.coerceAtLeast(1)

    /** Filtro e ordinamento insieme: quello che la griglia mostra davvero. */
    fun visible(
        cards: List<PokemonCard>,
        query: String = "",
        company: String? = null,
        tier: GradeTier? = null,
        sort: GradedSort = GradedSort.GRADE_DESC
    ): List<PokemonCard> = sort(filter(cards, query, company, tier), sort)
}
