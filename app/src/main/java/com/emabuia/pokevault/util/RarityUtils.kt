package com.emabuia.pokevault.util

import androidx.compose.ui.graphics.Color

/**
 * Le forme che le carte portano stampate in basso a destra.
 *
 * `SPARKLE` e' la stella a quattro punte delle shiny e delle radiose;
 * `DASH` e' il trattino per le carte che un simbolo di rarita' non ce
 * l'hanno (i mazzi introduttivi) e per quelle di cui non sappiamo la rarita'.
 */
enum class RarityShape { CIRCLE, DIAMOND, STAR, SPARKLE, DASH }

/**
 * Il segno completo: forma, quante volte si ripete, piena o contornata.
 *
 * E' cosi' che le rarita' si distinguono sul cartoncino vero -- una stella
 * nera e' Rara, due nere Doppia Rara, due argentate contornate Ultra Rara,
 * tre oro Iper Rara -- e cosi' si distinguono anche qui, invece che con le
 * stringhe unicode di prima ("★★", "☆", "✧"), dove cinque rarita' diverse
 * finivano sulle stesse due stelle e la larghezza cambiava col font di
 * sistema.
 *
 * Ogni rarita' ne ha uno: nel riepilogo di un'espansione le voci stanno in
 * fila sotto la barra dei posseduti, e meta' simboli e meta' scritte era
 * proprio quello che non andava.
 */
data class RaritySymbol(
    val shape: RarityShape,
    val count: Int = 1,
    val filled: Boolean = true
)

data class RarityInfo(
    val symbol: RaritySymbol,
    val color: Color,
    val label: String,
    val sortOrder: Int,
    /**
     * Nome cortissimo per il riepilogo dell'espansione, dove le voci stanno
     * tutte su una riga sola e si spartiscono la larghezza: un set moderno ne
     * ha fino a dieci, cioe' meno di quaranta punti a testa, e "Illustr.
     * Spec." li' non ci sta. Vuoto significa "usa [label]".
     */
    val short: String = "",
    /** Se merita l'effetto holo-foil sull'immagine. */
    val foil: Boolean = false,
    /**
     * Il segno stampato e' nero: sulla carta vera si legge perche' il
     * cartoncino e' bianco. In app deve seguire il tema, altrimenti in scuro
     * e' nero su `#1A1A2E` e sparisce. Chi disegna usa `AppColors.textPrimary`
     * al posto di `color`.
     */
    val adaptive: Boolean = false,
    /** Non abbiamo il dato. Diverso da "il dato c'e' ma non lo mappiamo". */
    val isUnknown: Boolean = false
) {
    /** Il nome da mettere sotto al simbolo quando lo spazio e' poco. */
    val shortLabel: String get() = short.ifEmpty { label }
}

object RarityUtils {
    // Le rarita' che nessun ramo riconosce si mostrano col loro nome vero, in
    // coda e senza colore: "Pikachu Rare" e' un dato giusto che non abbiamo
    // ancora mappato, e chiamarlo "Altro" lo buttava via.
    private const val ORDER_UNMAPPED = 90
    private const val ORDER_NO_RARITY = 95
    private const val ORDER_UNKNOWN = 99

    private val NEUTRAL = Color(0xFF8A94A6)
    private val GOLD = Color(0xFFEAB308)
    private val SILVER = Color(0xFF94A3B8)
    private val SHINY = Color(0xFF60A5FA)
    private val PURPLE = Color(0xFF7C3AED)

    private val CIRCLE = RaritySymbol(RarityShape.CIRCLE)
    private val CIRCLE_OUTLINE = RaritySymbol(RarityShape.CIRCLE, filled = false)
    private val DIAMOND = RaritySymbol(RarityShape.DIAMOND)
    private val DIAMOND_OUTLINE = RaritySymbol(RarityShape.DIAMOND, filled = false)
    private val STAR = RaritySymbol(RarityShape.STAR)
    private val STAR_OUTLINE = RaritySymbol(RarityShape.STAR, filled = false)
    private val STAR2 = RaritySymbol(RarityShape.STAR, count = 2)
    private val STAR2_OUTLINE = RaritySymbol(RarityShape.STAR, count = 2, filled = false)
    private val STAR3 = RaritySymbol(RarityShape.STAR, count = 3)
    private val STAR3_OUTLINE = RaritySymbol(RarityShape.STAR, count = 3, filled = false)
    private val SPARKLE = RaritySymbol(RarityShape.SPARKLE)
    private val SPARKLE2 = RaritySymbol(RarityShape.SPARKLE, count = 2)
    private val DASH = RaritySymbol(RarityShape.DASH)

    /**
     * Se la rarita' merita l'effetto holo-foil sull'immagine della carta.
     *
     * Filtro stretto di proposito: la foil e' un'animazione continua per ogni
     * carta che la porta, e in una griglia da sessanta carte accenderla su
     * tutte vuol dire sessanta gradienti animati per niente. Passano solo le
     * rarita' che *in mano* sono davvero lucide, dall'Illustration Rare in su.
     *
     * Era dedotto dal `sortOrder` (`>= 5`, con Promo e "Altro" tolti a mano):
     * bastava aggiungere una rarita' in coda alla scala per accenderle la foil
     * senza volerlo. Ora ogni ramo lo dichiara.
     */
    fun hasFoilFinish(rarity: String?): Boolean = getRarityInfo(rarity).foil

    fun getRarityInfo(rarity: String?): RarityInfo {
        val raw = rarity?.trim().orEmpty()
        val r = raw.lowercase()
        val isIt = AppLocale.isItalian

        // "Unknown" e' rimasto congelato nelle collezioni salvate prima che
        // smettessimo di scriverlo (vedi i `rarity.orEmpty()` nei ViewModel):
        // quelle carte vanno lette come sconosciute, non come una rarita' di
        // nome "Unknown".
        if (raw.isBlank() || r == "unknown" || r == "sconosciuta" || r == "sconosciuto") {
            return RarityInfo(
                symbol = DASH,
                color = NEUTRAL,
                label = if (isIt) "Sconosciuta" else "Unknown",
                sortOrder = ORDER_UNKNOWN,
                short = if (isIt) "Sconosc." else "Unknown",
                isUnknown = true
            )
        }

        return when {
            // Priorità alle stringhe più specifiche per evitare raggruppamenti errati

            // Carte senza simbolo di rarita' stampato: i mazzi introduttivi
            // (xy0) sono cosi' davvero, TCGdex risponde letteralmente "None".
            // Non e' un buco: la carta la rarita' non ce l'ha. Cerchio vuoto,
            // non trattino, per non confonderla con la sconosciuta.
            r == "none" || r == "nessuna" ->
                RarityInfo(CIRCLE_OUTLINE, NEUTRAL, if (isIt) "Nessuna" else "None", ORDER_NO_RARITY)

            // 4. ACE SPEC
            r.contains("ace spec") ->
                RarityInfo(DIAMOND_OUTLINE, Color(0xFFD300C5), "Ace Spec", 4, short = "ACE")

            // 14. FUTURISTIC RARE (stella singola iridescente)
            // Debutta con l'espansione 30° Anniversario: le due full art
            // opalescenti di YOSHIROTTEN (Mewtwo-ex 157 e Mew-ex 158), che
            // sulla carta portano una stella colorata invece delle due oro
            // della Special Illustration Rare.
            // Nessun ramo piu' in basso intercetta "futuristic rare" -- non
            // contiene "ex", ne' "rare holo", e il ramo Rara e' un `==` -- per
            // cui senza questo caso finirebbe fra le non mappate: nessuna foil
            // e nessun colore suo.
            r.contains("futuristic rare") || r.contains("rara futuristica") ->
                RarityInfo(
                    SPARKLE,
                    Color(0xFFA855F7),
                    if (isIt) "Futuristica" else "Futuristic",
                    14,
                    short = if (isIt) "Futur." else "Futur.",
                    foil = true
                )

            // Pikachu Rare: rarita' propria del 30° Anniversario, 30 carte.
            // Niente foil finche' non e' verificata su una carta in mano.
            r.contains("pikachu rare") ->
                RarityInfo(STAR, Color(0xFFFACC15), "Pikachu Rare", 11, short = "Pikachu")

            // Classic Collection: le 25 ristampe olografiche di Celebrations,
            // tutte con la stessa rarita' e un simbolo tutto loro.
            r.contains("classic collection") ->
                RarityInfo(
                    DIAMOND_OUTLINE,
                    Color(0xFFD97706),
                    if (isIt) "Coll. Classica" else "Classic Coll.",
                    12,
                    short = if (isIt) "Classica" else "Classic",
                    foil = true
                )

            // 7. SPECIAL ILLUSTRATION RARE (Due stelle oro)
            // Deve stare sopra Illustration Rare perché ne contiene il nome
            r.contains("special illustration rare") || r.contains("special art rare") ->
                RarityInfo(
                    STAR2,
                    GOLD,
                    if (isIt) "Illustr. Spec." else "S. Illustration",
                    7,
                    short = if (isIt) "Ill. Sp." else "S. Ill.",
                    foil = true
                )

            // 5. ILLUSTRATION RARE (Stella singola oro/bianca)
            r.contains("illustration rare") || r.contains("rare art") || r.contains("illustrazione rara") ->
                RarityInfo(
                    STAR,
                    GOLD,
                    if (isIt) "Illustr. Rara" else "Illustration Rare",
                    5,
                    short = if (isIt) "Illustr." else "Illus.",
                    foil = true
                )

            // 10. SHINY ULTRA RARE (Due stelle shiny)
            // Deve stare sopra Ultra Rare
            r.contains("shiny ultra") || r.contains("ultra rara shiny") ->
                RarityInfo(
                    SPARKLE2,
                    SHINY,
                    if (isIt) "Ultra Shiny" else "Shiny Ultra Rare",
                    10,
                    short = if (isIt) "U. Shiny" else "S. Ultra",
                    foil = true
                )

            // 6. ULTRA RARE (Due stelle bianche/argento)
            // "ultrarara" tutto attaccato arriva dai topup che hanno letto
            // TCGdex in italiano invece che in inglese.
            r.contains("ultra rare") || r.contains("ultra rara") || r.contains("ultrarara") || r.contains("full art") ->
                RarityInfo(
                    STAR2_OUTLINE,
                    SILVER,
                    if (isIt) "Ultra Rara" else "Ultra Rare",
                    6,
                    short = "Ultra",
                    foil = true
                )

            // 8. HYPER RARE (Tre stelle oro)
            r.contains("hyper rare") || r.contains("iper rara") || r.contains("gold") ->
                RarityInfo(
                    STAR3,
                    GOLD,
                    if (isIt) "Iper Rara" else "Hyper Rare",
                    8,
                    short = if (isIt) "Iper" else "Hyper",
                    foil = true
                )

            // 9. SHINY RARE (Stella singola shiny)
            r.contains("shiny rare") || r.contains("rara shiny") ->
                RarityInfo(
                    SPARKLE,
                    SHINY,
                    if (isIt) "Rara Shiny" else "Shiny Rare",
                    9,
                    short = "Shiny",
                    foil = true
                )

            // 3. DOUBLE RARE (V, VMAX, VSTAR, ex)
            // Deve stare sopra Rare perché "rare holo v"/"holo rare v" verrebbe preso da "rare"/"holo rare"
            r.contains("double rare") || r.contains("doppia rara") || r.contains("ex") ||
                    r.contains("vmax") || r.contains("vstar") ||
                    r == "rare holo v" || r == "holo rare v" ->
                RarityInfo(
                    STAR2,
                    NEUTRAL,
                    if (isIt) "Doppia Rara" else "Double Rare",
                    3,
                    short = if (isIt) "Doppia" else "Double",
                    adaptive = true
                )

            // 13. SECRET RARE (numerata oltre il totale stampato del set; TCGdex, non copriva PokeWallet)
            // "segreto rara" e' l'italiano storto lasciato dai topup XY.
            r.contains("secret rare") || r.contains("rara segreta") || r.contains("segret") ->
                RarityInfo(
                    STAR3_OUTLINE,
                    GOLD,
                    if (isIt) "Rara Segreta" else "Secret Rare",
                    13,
                    short = if (isIt) "Segreta" else "Secret",
                    foil = true
                )

            // Rarita' storiche distinte (ere HGSS/BW/SM), assenti dal vocabolario PokeWallet originale
            r.contains("legend") ->
                RarityInfo(STAR2, PURPLE, "LEGEND", 8, short = "LEGEND", foil = true)
            r.contains("prime") ->
                RarityInfo(STAR, PURPLE, if (isIt) "Rara Prime" else "Rare PRIME", 6, short = "Prime", foil = true)
            r.contains("radiant") || r.contains("radiosa") ->
                RarityInfo(SPARKLE, GOLD, if (isIt) "Radiosa Rara" else "Radiant Rare", 6, short = if (isIt) "Radiosa" else "Radiant", foil = true)
            r.contains("amazing") ->
                RarityInfo(SPARKLE, Color(0xFFEC4899), if (isIt) "Rara Amazing" else "Amazing Rare", 6, short = "Amazing", foil = true)
            r.contains("black white rare") ->
                RarityInfo(STAR2_OUTLINE, NEUTRAL, if (isIt) "Rara B/W" else "Black White Rare", 3, short = "B/W", adaptive = true)

            // LV.X: la stella nera la porta, ma il nome in piu' conta -- sono
            // le carte di punta dell'era DP e sono olografiche.
            r.contains("lv.x") ->
                RarityInfo(STAR_OUTLINE, PURPLE, "LV.X", 6, short = "LV.X", foil = true)

            // 2. RARE (Holo o Standard) -- entrambi gli ordini di parole visti nei dati reali
            // ("Rare Holo" da PokeWallet, "Holo Rare" da TCGdex). La stella nera
            // stampata e' la stessa per tutte e due.
            r == "rare" || r == "rara" ->
                RarityInfo(STAR, NEUTRAL, if (isIt) "Rara" else "Rare", 2, short = if (isIt) "Rara" else "Rare", adaptive = true)
            r.contains("rare holo") || r.contains("holo rare") ->
                RarityInfo(STAR, NEUTRAL, if (isIt) "Rara Holo" else "Holo Rare", 2, short = "Holo", adaptive = true)

            // 11. PROMO
            r.contains("promo") ->
                RarityInfo(STAR_OUTLINE, Color(0xFFEF4444), "Promo", 11, short = "Promo")

            // 1. UNCOMMON
            r.contains("uncommon") || r == "non comune" ->
                RarityInfo(DIAMOND, SILVER, if (isIt) "Non Comune" else "Uncommon", 1, short = if (isIt) "N. Com." else "Uncom.")

            // 0. COMMON
            r.contains("common") && !r.contains("uncommon") || r == "comune" ->
                RarityInfo(CIRCLE, SILVER, if (isIt) "Comune" else "Common", 0, short = if (isIt) "Comune" else "Common")

            // Rarita' vera che non abbiamo mappato: si mostra com'e' scritta,
            // neutra e in fondo alla scala. Non e' "sconosciuta" -- il dato
            // c'e' ed e' giusto, manca solo il ramo qui sopra.
            else -> RarityInfo(CIRCLE_OUTLINE, NEUTRAL, raw, ORDER_UNMAPPED)
        }
    }
}
