package com.emabuia.pokevault.data.firebase

import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.GoalAlbum
import com.emabuia.pokevault.data.model.GoalCriteriaType
import com.emabuia.pokevault.data.model.MatchLog
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.Tournament
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.data.model.collectionGroupKey
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlin.math.abs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val userId: String
        get() = auth.currentUser?.uid ?: throw Exception("Utente non autenticato")

    private val userDoc
        get() = firestore.collection("users").document(userId)

    private val cardsCollection
        get() = userDoc.collection("cards")

    private val decksCollection
        get() = userDoc.collection("decks")

    private val albumsCollection
        get() = userDoc.collection("albums")

    private val wishlistsCollection
        get() = userDoc.collection("wishlists")

    private val matchLogsCollection
        get() = userDoc.collection("match_logs")

    private val tournamentsCollection
        get() = userDoc.collection("tournaments")

    private val goalAlbumsCollection
        get() = userDoc.collection("goal_albums")

    /**
     * Le carte possedute.
     *
     * Le carte segnate [PokemonCard.deckOnly] non sono possedute: vivono nella
     * stessa collection solo perche' un deck referenzia id di documenti, ma
     * qui non escono. Chiunque voglia anche quelle -- cioe' il solo Deck Lab --
     * usa [getCardsIncludingDeckOnly].
     *
     * Il filtro e' client-side di proposito: `whereEqualTo("deckOnly", false)`
     * non matcherebbe i documenti scritti prima che il campo esistesse, che
     * sono tutti quelli gia' in circolazione.
     */
    fun getCards(): Flow<List<PokemonCard>> =
        getCardsIncludingDeckOnly().map { cards -> cards.filter { !it.deckOnly } }

    /** Collezione + carte solo-deck. Vedi [getCards]. */
    fun getCardsIncludingDeckOnly(): Flow<List<PokemonCard>> = callbackFlow {
        val col = try { cardsCollection } catch (e: Exception) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val listener = col.addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val cards = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(PokemonCard::class.java)?.copy(id = doc.id)
            } ?: emptyList()
            trySend(cards)
        }
        awaitClose { listener.remove() }
    }

    fun getOwnedCardsBySet(setName: String): Flow<List<PokemonCard>> = callbackFlow {
        val col = try { cardsCollection } catch (e: Exception) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val listener = col.whereEqualTo("set", setName)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val cards = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(PokemonCard::class.java)?.copy(id = doc.id)
                }?.filter { !it.deckOnly } ?: emptyList()
                trySend(cards)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Backfill one-shot dei metadati di classificazione per carte legacy.
     * Aggiorna SOLO `supertype`/`subtypes` su documenti mancanti o incoerenti.
     * Non usa API esterne, quindi non impatta proxy o cache remote.
     */
    suspend fun backfillLegacyCardClassificationMetadata(): Result<Int> {
        return try {
            val snapshot = cardsCollection.get().await()
            var updated = 0

            for (doc in snapshot.documents) {
                val card = doc.toObject(PokemonCard::class.java) ?: continue
                val updates = mutableMapOf<String, Any>()

                val inferredSupertype = inferSupertype(card)
                val currentSupertype = card.supertype.trim()
                val normalizedCurrent = normalizeCategory(currentSupertype)

                val shouldUpdateSupertype =
                    currentSupertype.isBlank() ||
                        normalizedCurrent == null ||
                        (normalizedCurrent == "Pokémon" && inferredSupertype != "Pokémon")

                if (shouldUpdateSupertype) {
                    updates["supertype"] = inferredSupertype
                }

                // Assicura il campo esistente nei documenti legacy senza sovrascrivere valori reali.
                if (doc.get("subtypes") == null) {
                    updates["subtypes"] = card.subtypes
                }

                if (updates.isNotEmpty()) {
                    doc.reference.update(updates).await()
                    updated++
                }
            }

            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun inferSupertype(card: PokemonCard): String {
        val supertype = card.supertype.lowercase()
        val type = card.type.lowercase()
        val name = card.name.lowercase()
        val subtypes = card.subtypes.map { it.lowercase() }

        val hasEnergyMarker =
            supertype.contains("energy") ||
                supertype.contains("energ") ||
                type.contains("energy") ||
                type.contains("energia") ||
                subtypes.any { it.contains("energy") || it.contains("energia") } ||
                name.contains("energy") ||
                name.contains("energia")
        if (hasEnergyMarker) return "Energy"

        val hasTrainerMarker =
            supertype.contains("trainer") ||
                supertype.contains("allenat") ||
                supertype.contains("aiuto") ||
                type.contains("trainer") ||
                type.contains("supporter") ||
                type.contains("item") ||
                type.contains("stadium") ||
                type.contains("tool") ||
                type.contains("allenat") ||
                type.contains("aiuto") ||
                type.contains("stadio") ||
                type.contains("strumento") ||
                subtypes.any {
                    it == "item" ||
                        it == "stadium" ||
                        it == "supporter" ||
                        it == "tool" ||
                        it == "strumento" ||
                        it == "stadio" ||
                        it == "aiuto"
                }

        val hasPokemonSubtypeMarker = subtypes.any {
            it == "basic" ||
                it == "stage 1" ||
                it == "stage 2" ||
                it == "baby" ||
                it == "ex" ||
                it == "v" ||
                it == "vmax" ||
                it == "vstar"
        }
        val hasPokemonTypeMarker =
            type in listOf(
                "grass", "fire", "water", "lightning", "electric", "fighting",
                "psychic", "darkness", "metal", "dragon", "fairy"
            )
        val hasStrongPokemonMarker =
            card.hp > 0 ||
                hasPokemonSubtypeMarker ||
                hasPokemonTypeMarker
        val hasExplicitPokemonSupertype = supertype.contains("pok")

        if (hasTrainerMarker && !hasStrongPokemonMarker) return "Trainer"
        if (hasStrongPokemonMarker) return "Pokémon"
        if (hasExplicitPokemonSupertype && !hasTrainerMarker && type != "colorless") return "Pokémon"

        return "Trainer"
    }

    private fun normalizeCategory(raw: String): String? {
        val v = raw.lowercase().trim()
        return when {
            v.contains("pok") -> "Pokémon"
            v.contains("train") || v.contains("allenat") || v.contains("aiuto") -> "Trainer"
            v.contains("energ") -> "Energy"
            else -> null
        }
    }

    /**
     * Aggiunge una carta in modo local-first:
     * - la verifica di esistenza (per incrementare la quantità di una carta già
     *   posseduta) colpisce la cache locale, quindi è istantanea
     * - la scrittura usa direttamente set()/update() senza runTransaction, così
     *   finisce subito nella cache locale e gli snapshot listener emettono
     *   l'aggiornamento all'istante (con hasPendingWrites=true)
     * - l'aggiornamento dei totali utente è fire-and-forget: la UI ricalcola
     *   comunque i totali lato client da [CollectionViewModel]
     * La sincronizzazione con il server Firestore avviene in background.
     */
    suspend fun addCard(card: PokemonCard): Result<String> {
        return try {
            var effectiveEstimatedValue = card.estimatedValue
            val canonicalLanguage = canonicalDisplayLanguage(card.language)

            val data = hashMapOf<String, Any?>(
                "name" to card.name,
                "imageUrl" to card.imageUrl,
                "set" to card.set,
                "rarity" to card.rarity,
                "type" to card.type,
                "hp" to card.hp,
                "supertype" to card.supertype,
                "subtypes" to card.subtypes,
                "isGraded" to card.isGraded,
                "grade" to card.grade,
                "gradingCompany" to card.gradingCompany,
                "estimatedValue" to card.estimatedValue,
                "quantity" to card.quantity,
                "condition" to card.condition,
                "notes" to card.notes,
                "apiCardId" to card.apiCardId,
                "cardNumber" to card.cardNumber,
                "variant" to card.variant,
                "language" to canonicalLanguage,
                "deckOnly" to card.deckOnly,
                "addedAt" to com.google.firebase.Timestamp.now()
            )

            val docId: String = if (card.apiCardId.isNotBlank()) {
                val existing: QuerySnapshot? = try {
                    cardsCollection
                        .whereEqualTo("apiCardId", card.apiCardId)
                        .whereEqualTo("variant", card.variant)
                        .get(Source.CACHE).await()
                } catch (_: Exception) {
                    null // cache miss: trattiamo come carta nuova
                }

                // Una carta solo-deck e una posseduta non si fondono mai, anche
                // a parita' di stampa: sommarle vorrebbe dire far crescere la
                // collezione per una carta che l'utente non ha comprato.
                val existingForLanguage = existing?.documents?.firstOrNull { doc ->
                    normalizeLanguageKey(doc.getString("language")) == normalizeLanguageKey(canonicalLanguage) &&
                        (doc.getBoolean("deckOnly") ?: false) == card.deckOnly
                }

                if (existingForLanguage != null) {
                    val doc = existingForLanguage
                    val docRef = doc.reference
                    val currentQty = doc.getLong("quantity")?.toInt() ?: 1
                    val currentEstimatedValue = doc.getDouble("estimatedValue") ?: 0.0
                    val currentSupertype = normalizeCategory(doc.getString("supertype").orEmpty())
                    val incomingSupertype = normalizeCategory(card.supertype)
                    val currentType = doc.getString("type").orEmpty()
                    val currentHp = doc.getLong("hp")?.toInt() ?: 0
                    val currentSubtypes = (doc.get("subtypes") as? List<*>)
                    if (effectiveEstimatedValue <= 0.0) {
                        effectiveEstimatedValue = currentEstimatedValue
                    }

                    val updates = mutableMapOf<String, Any>(
                        "quantity" to (currentQty + card.quantity),
                        "estimatedValue" to effectiveEstimatedValue,
                        "language" to canonicalLanguage
                    )

                    // Heal legacy docs that were saved without proper classification fields.
                    if (incomingSupertype != null &&
                        (currentSupertype == null ||
                            (currentSupertype == "Pokémon" && incomingSupertype != "Pokémon"))
                    ) {
                        updates["supertype"] = card.supertype
                    }
                    if ((currentSubtypes == null || currentSubtypes.isEmpty()) && card.subtypes.isNotEmpty()) {
                        updates["subtypes"] = card.subtypes
                    }
                    if ((currentType.isBlank() || currentType.equals("Colorless", ignoreCase = true)) &&
                        card.type.isNotBlank() &&
                        !card.type.equals("Colorless", ignoreCase = true)
                    ) {
                        updates["type"] = card.type
                    }
                    if (currentHp <= 0 && card.hp > 0) {
                        updates["hp"] = card.hp
                    }

                    // Fire-and-forget: la scrittura colpisce la cache locale
                    // all'istante; lo snapshot listener emette subito l'update.
                    docRef.update(updates)
                    doc.id
                } else {
                    // Usiamo un DocumentReference generato localmente così
                    // otteniamo subito l'ID senza aspettare la rete.
                    val newDocRef = cardsCollection.document()
                    newDocRef.set(data)
                    newDocRef.id
                }
            } else {
                val newDocRef = cardsCollection.document()
                newDocRef.set(data)
                newDocRef.id
            }

            // Aggiornamento dei totali utente fire-and-forget (i totali vengono
            // comunque ricalcolati client-side dalla lista delle carte).
            // Una carta solo-deck non e' posseduta: non conta ne' nel numero di
            // carte ne' nel valore della collezione.
            if (!card.deckOnly) {
                userDoc.update(
                    "totalCards", FieldValue.increment(card.quantity.toLong()),
                    "totalValue", FieldValue.increment(effectiveEstimatedValue * card.quantity)
                )
            }

            Result.success(docId)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun updateCard(cardId: String, card: PokemonCard): Result<Unit> {
        return try {
            // Lettura dalla cache locale: istantanea.
            val oldCardDoc = try {
                cardsCollection.document(cardId).get(Source.CACHE).await()
            } catch (_: Exception) { null }

            val oldQty = oldCardDoc?.getLong("quantity")?.toInt() ?: 0
            val oldValue = oldCardDoc?.getDouble("estimatedValue") ?: 0.0

            val qtyDiff = card.quantity - oldQty
            val valueDiff = (card.estimatedValue * card.quantity) - (oldValue * oldQty)

            val data = mutableMapOf<String, Any?>(
                "isGraded" to card.isGraded,
                "grade" to card.grade,
                "gradingCompany" to card.gradingCompany,
                "quantity" to card.quantity,
                "condition" to card.condition,
                "notes" to card.notes,
                "estimatedValue" to card.estimatedValue
            )

            // Write diretto: finisce immediatamente nella cache locale, lo
            // snapshot listener emette l'aggiornamento all'istante.
            cardsCollection.document(cardId).update(data)

            // Una carta solo-deck non ha mai contribuito ai totali: se si
            // arriva qui dal dettaglio di una carta aperta da un deck di prova,
            // muovere i contatori inventerebbe carte possedute.
            val isDeckOnly = oldCardDoc?.getBoolean("deckOnly") ?: false
            if (isDeckOnly) return Result.success(Unit)

            // Totali fire-and-forget.
            if (qtyDiff != 0) {
                userDoc.update("totalCards", FieldValue.increment(qtyDiff.toLong()))
            }
            if (valueDiff != 0.0) {
                userDoc.update("totalValue", FieldValue.increment(valueDiff))
            }

            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Aggiorna SOLO il nome dell'espansione di una carta.
     *
     * Deliberatamente separata da updateCard(): quella scrive un insieme fisso di
     * campi che non include `set`, e ricalcola i totali dell'utente da un vecchio
     * valore letto dalla cache locale -- se il documento non e' in cache assume
     * quantita' e valore a zero e i delta risultano positivi, gonfiando
     * `totalCards`/`totalValue`. Per una correzione di massa sarebbe stato un
     * effetto collaterale grave: qui si scrive un solo campo e nessun contatore.
     */
    suspend fun updateCardSetName(cardId: String, setName: String): Result<Unit> {
        return try {
            cardsCollection.document(cardId).update("set", setName).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Scrive lo stadio evolutivo su una carta gia' in collezione.
     *
     * Un solo campo, `subtypes`, come updateCardSetName: niente updateCard, che
     * riscrive il documento intero e ricalcola i contatori dell'utente da una
     * lettura di cache (vedi il commento li' sopra). Qui non cambia ne'
     * quantita' ne' valore, solo un metadato che mancava.
     */
    suspend fun updateCardSubtypes(cardId: String, subtypes: List<String>): Result<Unit> {
        return try {
            cardsCollection.document(cardId).update("subtypes", subtypes).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCard(cardId: String): Result<Unit> {
        return try {
            // Leggiamo quantità e valore dalla cache locale (istantaneo) per
            // decrementare i totali utente; se la cache non ha nulla passiamo
            // comunque all'eliminazione.
            val cardDoc = try {
                cardsCollection.document(cardId).get(Source.CACHE).await()
            } catch (_: Exception) { null }

            val isDeckOnly = cardDoc?.getBoolean("deckOnly") ?: false
            val quantity = if (isDeckOnly) 0 else cardDoc?.getLong("quantity")?.toInt() ?: 0
            val value = if (isDeckOnly) 0.0 else cardDoc?.getDouble("estimatedValue") ?: 0.0
            val totalCardValue = value * quantity

            // Delete diretto: la carta sparisce subito dalla cache locale e
            // lo snapshot listener aggiorna la UI all'istante.
            cardsCollection.document(cardId).delete()

            // Totali fire-and-forget: una carta solo-deck non li ha mai toccati
            // entrando, quindi non li tocca nemmeno uscendo.
            if (quantity != 0) {
                userDoc.update("totalCards", FieldValue.increment(-quantity.toLong()))
            }
            if (totalCardValue != 0.0) {
                userDoc.update("totalValue", FieldValue.increment(-totalCardValue))
            }

            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteCardByApiId(apiCardId: String): Result<Unit> {
        return try {
            // Query locale: le carte da cancellare sono già in cache perché
            // le abbiamo appena caricate dallo snapshot listener.
            val snapshot = try {
                cardsCollection
                    .whereEqualTo("apiCardId", apiCardId)
                    .get(Source.CACHE).await()
            } catch (_: Exception) { null }

            if (snapshot != null) {
                // Le copie solo-deck restano: chi cancella una carta dalla
                // collezione non sta cancellando i deck di prova che la usano.
                deleteCards(snapshot.documents.mapNotNull { doc ->
                    doc.toObject(PokemonCard::class.java)?.copy(id = doc.id)
                }.filter { !it.deckOnly })
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addCards(cards: List<PokemonCard>): Result<List<String>> {
        return try {
            if (cards.isEmpty()) return Result.success(emptyList())

            val batch = firestore.batch()
            val addedIds = mutableListOf<String>()
            var totalCardsDelta = 0L
            var totalValueDelta = 0.0

            for (card in cards) {
                var effectiveEstimatedValue = card.estimatedValue
                val canonicalLanguage = canonicalDisplayLanguage(card.language)

                val data = hashMapOf<String, Any?>(
                    "name" to card.name,
                    "imageUrl" to card.imageUrl,
                    "set" to card.set,
                    "rarity" to card.rarity,
                    "type" to card.type,
                    "hp" to card.hp,
                    "supertype" to card.supertype,
                    "subtypes" to card.subtypes,
                    "isGraded" to card.isGraded,
                    "grade" to card.grade,
                    "gradingCompany" to card.gradingCompany,
                    "estimatedValue" to card.estimatedValue,
                    "quantity" to card.quantity,
                    "condition" to card.condition,
                    "notes" to card.notes,
                    "apiCardId" to card.apiCardId,
                    "cardNumber" to card.cardNumber,
                    "variant" to card.variant,
                    "language" to canonicalLanguage,
                    "deckOnly" to card.deckOnly,
                    "addedAt" to com.google.firebase.Timestamp.now()
                )

                if (card.apiCardId.isNotBlank()) {
                    val existing: QuerySnapshot? = try {
                        cardsCollection
                            .whereEqualTo("apiCardId", card.apiCardId)
                            .whereEqualTo("variant", card.variant)
                            .get(Source.CACHE).await()
                    } catch (_: Exception) {
                        null
                    }

                    // Stessa regola di addCard: le copie solo-deck sono un
                    // insieme a parte e non assorbono quantita' di collezione.
                    val existingForLanguage = existing?.documents?.firstOrNull { doc ->
                        normalizeLanguageKey(doc.getString("language")) == normalizeLanguageKey(canonicalLanguage) &&
                            (doc.getBoolean("deckOnly") ?: false) == card.deckOnly
                    }

                    if (existingForLanguage != null) {
                        val doc = existingForLanguage
                        val docRef = doc.reference
                        val currentQty = doc.getLong("quantity")?.toInt() ?: 1
                        val currentEstimatedValue = doc.getDouble("estimatedValue") ?: 0.0
                        val currentSupertype = normalizeCategory(doc.getString("supertype").orEmpty())
                        val incomingSupertype = normalizeCategory(card.supertype)
                        val currentType = doc.getString("type").orEmpty()
                        val currentHp = doc.getLong("hp")?.toInt() ?: 0
                        val currentSubtypes = (doc.get("subtypes") as? List<*>)

                        if (effectiveEstimatedValue <= 0.0) {
                            effectiveEstimatedValue = currentEstimatedValue
                        }

                        val updates = mutableMapOf<String, Any>(
                            "quantity" to (currentQty + card.quantity),
                            "estimatedValue" to effectiveEstimatedValue,
                            "language" to canonicalLanguage
                        )

                        if (incomingSupertype != null &&
                            (currentSupertype == null ||
                                (currentSupertype == "Pokémon" && incomingSupertype != "Pokémon"))
                        ) {
                            updates["supertype"] = card.supertype
                        }
                        if ((currentSubtypes == null || currentSubtypes.isEmpty()) && card.subtypes.isNotEmpty()) {
                            updates["subtypes"] = card.subtypes
                        }
                        if ((currentType.isBlank() || currentType.equals("Colorless", ignoreCase = true)) &&
                            card.type.isNotBlank() &&
                            !card.type.equals("Colorless", ignoreCase = true)
                        ) {
                            updates["type"] = card.type
                        }
                        if (currentHp <= 0 && card.hp > 0) {
                            updates["hp"] = card.hp
                        }

                        batch.update(docRef, updates)
                        addedIds += doc.id
                    } else {
                        val newDocRef = cardsCollection.document()
                        batch.set(newDocRef, data)
                        addedIds += newDocRef.id
                    }
                } else {
                    val newDocRef = cardsCollection.document()
                    batch.set(newDocRef, data)
                    addedIds += newDocRef.id
                }

                if (!card.deckOnly) {
                    totalCardsDelta += card.quantity.toLong()
                    totalValueDelta += effectiveEstimatedValue * card.quantity
                }
            }

            batch.update(
                userDoc,
                mapOf(
                    "totalCards" to FieldValue.increment(totalCardsDelta),
                    "totalValue" to FieldValue.increment(totalValueDelta)
                )
            )

            batch.commit().await()
            Result.success(addedIds)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCards(cards: List<PokemonCard>): Result<Int> {
        return try {
            if (cards.isEmpty()) return Result.success(0)

            val batch = firestore.batch()
            var totalCardsDelta = 0L
            var totalValueDelta = 0.0

            cards.forEach { card ->
                batch.delete(cardsCollection.document(card.id))
                if (!card.deckOnly) {
                    totalCardsDelta += card.quantity.toLong()
                    totalValueDelta += card.estimatedValue * card.quantity
                }
            }

            batch.update(
                userDoc,
                mapOf(
                    "totalCards" to FieldValue.increment(-totalCardsDelta),
                    "totalValue" to FieldValue.increment(-totalValueDelta)
                )
            )

            batch.commit().await()
            Result.success(cards.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCard(cardId: String): Result<PokemonCard> {
        return try {
            val doc = cardsCollection.document(cardId).get().await()
            val card = doc.toObject(PokemonCard::class.java)?.copy(id = doc.id)
                ?: throw Exception("Carta non trovata")
            Result.success(card)
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun canonicalDisplayLanguage(language: String?): String {
        return when (normalizeLanguageKey(language)) {
            "ITA" -> "🇮🇹 Italiano"
            "ENG" -> "🇬🇧 English"
            "JAP" -> "🇯🇵 Giapponese"
            "CHN" -> "🇨🇳 Cinese"
            else -> language?.trim()?.takeIf { it.isNotBlank() } ?: "🇬🇧 English"
        }
    }

    private fun normalizeLanguageKey(language: String?): String {
        val normalized = language.orEmpty().trim().lowercase()
        return when {
            normalized.isBlank() -> ""
            "ital" in normalized -> "ITA"
            "eng" in normalized -> "ENG"
            "jap" in normalized || "giapp" in normalized -> "JAP"
            "chn" in normalized || "chin" in normalized -> "CHN"
            else -> normalized.uppercase()
        }
    }

    suspend fun getCollectionStats(): CollectionStats {
        return try {
            // Leggiamo il profilo utente e tutte le carte in parallelo (un solo round-trip extra)
            val userSnapshot = userDoc.get().await()
            val cachedTotal = userSnapshot.getLong("totalCards")?.toInt() ?: 0
            val cachedValue = userSnapshot.getDouble("totalValue") ?: 0.0

            // Le carte solo-deck non sono possedute: se finissero qui dentro
            // riallineerebbero totalCards/totalValue a un valore gonfiato, e
            // questa funzione quel valore lo riscrive sul profilo.
            val cards = cardsCollection.get().await()
                .documents.mapNotNull { it.toObject(PokemonCard::class.java) }
                .filter { !it.deckOnly }

            val uniqueKey: (PokemonCard) -> String = { c -> c.collectionGroupKey() }

            val totalCards = cards.sumOf { it.quantity }
            val totalValue = cards.sumOf { it.estimatedValue * it.quantity }

            // Aggiorna il profilo quando i totali cache risultano disallineati dal dato reale.
            if (cachedTotal != totalCards || abs(cachedValue - totalValue) > 0.0001) {
                userDoc.update(mapOf("totalCards" to totalCards, "totalValue" to totalValue))
            }

            CollectionStats(
                totalCards = totalCards,
                totalValue = totalValue,
                uniqueCards = cards.map(uniqueKey).toSet().size,
                mostValuable = cards.maxByOrNull { it.estimatedValue }?.name ?: "-"
            )
        } catch (e: Exception) { CollectionStats() }
    }

    // --- DECK METHODS ---

    fun getDecks(): Flow<List<Deck>> = callbackFlow {
        val col = try { decksCollection } catch (e: Exception) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val listener = col.orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val decks = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Deck::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(decks)
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveDeck(deck: Deck): Result<String> {
        return try {
            val data = hashMapOf(
                "name" to deck.name,
                "cards" to deck.cards,
                "mainTypes" to deck.mainTypes,
                "averageHp" to deck.averageHp,
                "totalCards" to deck.totalCards,
                "recommendedEnergy" to deck.recommendedEnergy,
                "coverImageUrl" to deck.coverImageUrl,
                "coverImageUrls" to deck.displayCoverImageUrls(),
                // set() riscrive il documento intero: se questo campo non c'e',
                // modificare un deck di prova lo farebbe tornare un deck normale.
                "deckOnly" to deck.deckOnly,
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            val docRef = if (deck.id.isEmpty()) {
                decksCollection.add(data).await()
            } else {
                decksCollection.document(deck.id).set(data).await()
                decksCollection.document(deck.id)
            }
            Result.success(docRef.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteDeck(deckId: String): Result<Unit> {
        return try {
            decksCollection.document(deckId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // --- ALBUM METHODS ---

    fun getAlbums(): Flow<List<Album>> = callbackFlow {
        val col = try { albumsCollection } catch (e: Exception) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val listener = col.orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val albums = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Album::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(albums)
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveAlbum(album: Album): Result<String> {
        return try {
            val data = hashMapOf(
                "name" to album.name,
                "description" to album.description,
                "pokemonType" to album.pokemonType,
                "expansion" to album.expansion,
                "supertype" to album.supertype,
                "size" to album.size,
                "theme" to album.theme,
                "cardIds" to album.cardIds,
                "coverImageUrl" to album.coverImageUrl,
                "createdAt" to (album.createdAt ?: com.google.firebase.Timestamp.now())
            )
            val docRef = if (album.id.isEmpty()) {
                albumsCollection.add(data).await()
            } else {
                albumsCollection.document(album.id).set(data).await()
                albumsCollection.document(album.id)
            }
            Result.success(docRef.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteAlbum(albumId: String): Result<Unit> {
        return try {
            albumsCollection.document(albumId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addCardToAlbum(albumId: String, cardId: String): Result<Unit> {
        return try {
            albumsCollection.document(albumId)
                .update("cardIds", FieldValue.arrayUnion(cardId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun removeCardFromAlbum(albumId: String, cardId: String): Result<Unit> {
        return try {
            albumsCollection.document(albumId)
                .update("cardIds", FieldValue.arrayRemove(cardId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** Inserimento multiplo: una scrittura sola per tutte le carte scelte. */
    suspend fun addCardsToAlbum(albumId: String, cardIds: List<String>): Result<Unit> {
        if (cardIds.isEmpty()) return Result.success(Unit)
        return try {
            albumsCollection.document(albumId)
                .update("cardIds", FieldValue.arrayUnion(*cardIds.toTypedArray()))
                .await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Riscrive l'ordine delle carte.
     *
     * arrayUnion/arrayRemove non bastano per riordinare o per rimettere una
     * carta al posto da cui era stata togliata: l'ordine e' quello dell'array,
     * quindi va scritto per intero.
     */
    suspend fun setAlbumCardIds(albumId: String, cardIds: List<String>): Result<Unit> {
        return try {
            albumsCollection.document(albumId)
                .update("cardIds", cardIds)
                .await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** La copertina scelta a mano: prima il campo esisteva ma nessuno lo scriveva. */
    suspend fun updateAlbumCover(albumId: String, coverImageUrl: String): Result<Unit> {
        return try {
            albumsCollection.document(albumId)
                .update("coverImageUrl", coverImageUrl)
                .await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // --- WISHLIST METHODS ---

    fun getWishlists(): Flow<List<Wishlist>> = callbackFlow {
        val col = try { wishlistsCollection } catch (e: Exception) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val listener = col.orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val wishlists = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Wishlist::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(wishlists)
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveWishlist(wishlist: Wishlist): Result<String> {
        return try {
            val data = hashMapOf(
                "name" to wishlist.name,
                "iconKey" to wishlist.iconKey,
                "accentKey" to wishlist.accentKey,
                "budgetEur" to wishlist.budgetEur,
                "cardIds" to wishlist.cardIds,
                "createdAt" to (wishlist.createdAt ?: com.google.firebase.Timestamp.now())
            )
            val docRef = if (wishlist.id.isEmpty()) {
                wishlistsCollection.add(data).await()
            } else {
                wishlistsCollection.document(wishlist.id).set(data).await()
                wishlistsCollection.document(wishlist.id)
            }
            Result.success(docRef.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteWishlist(wishlistId: String): Result<Unit> {
        return try {
            wishlistsCollection.document(wishlistId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addCardToWishlist(wishlistId: String, cardId: String): Result<Unit> {
        return try {
            wishlistsCollection.document(wishlistId)
                .update("cardIds", FieldValue.arrayUnion(cardId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Piu' carte in una lista con una scrittura sola.
     *
     * Serve al Chase, che manda in wishlist tutte le mancanti in un colpo: una
     * chiamata per carta su un set da duecento sarebbero duecento scritture.
     */
    suspend fun addCardsToWishlist(wishlistId: String, cardIds: List<String>): Result<Unit> {
        if (cardIds.isEmpty()) return Result.success(Unit)
        return try {
            wishlistsCollection.document(wishlistId)
                .update("cardIds", FieldValue.arrayUnion(*cardIds.toTypedArray()))
                .await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun removeCardFromWishlist(wishlistId: String, cardId: String): Result<Unit> {
        return try {
            wishlistsCollection.document(wishlistId)
                .update("cardIds", FieldValue.arrayRemove(cardId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Piu' carte fuori da una lista con una scrittura sola.
     *
     * Serve alla pulizia delle carte gia' comprate: una lista che si e' riempita
     * per mesi puo' averne venti da togliere insieme, e venti scritture per un
     * gesto solo sono venti occasioni di fallire a meta'.
     */
    suspend fun removeCardsFromWishlist(wishlistId: String, cardIds: List<String>): Result<Unit> {
        if (cardIds.isEmpty()) return Result.success(Unit)
        return try {
            wishlistsCollection.document(wishlistId)
                .update("cardIds", FieldValue.arrayRemove(*cardIds.toTypedArray()))
                .await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun removeCardFromAllWishlists(cardId: String): Result<Unit> {
        return try {
            val snapshot = wishlistsCollection
                .whereArrayContains("cardIds", cardId)
                .get()
                .await()

            for (doc in snapshot.documents) {
                doc.reference.update("cardIds", FieldValue.arrayRemove(cardId)).await()
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // --- MATCH LOG METHODS ---

    fun getMatchLogs(): Flow<List<MatchLog>> = callbackFlow {
        val col = try { matchLogsCollection } catch (e: Exception) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        // Nessun orderBy sul server: un orderBy in Firestore *esclude* i
        // documenti privi del campo ordinato, e questa query ordinava per
        // "date", che nei match non esiste — saveMatchLog scrive "createdAt".
        // Il risultato era che getMatchLogs() tornava sempre una lista vuota:
        // le statistiche globali del Match Log e il record sulla card dell'hub
        // restavano a zero anche con decine di partite registrate.
        // L'ordinamento si fa qui: sono le partite di un utente solo, e cosi'
        // nessun documento resta fuori, nemmeno quelli di schemi precedenti.
        val listener = col.addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val logs = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(MatchLog::class.java)?.copy(id = doc.id)
            }?.sortedByDescending { it.createdAt?.seconds ?: Long.MIN_VALUE } ?: emptyList()
            trySend(logs)
        }
        awaitClose { listener.remove() }
    }

    suspend fun saveMatchLog(match: MatchLog): Result<String> {
        return try {
            val data = hashMapOf(
                "tournamentId" to match.tournamentId,
                "round" to match.round,
                "result" to match.result,
                "opponentName" to match.opponentName,
                "opponentDeck" to match.opponentDeck,
                "notes" to match.notes,
                "createdAt" to (match.createdAt ?: com.google.firebase.Timestamp.now())
            )
            val docRef = if (match.id.isEmpty()) {
                matchLogsCollection.add(data).await()
            } else {
                matchLogsCollection.document(match.id).set(data).await()
                matchLogsCollection.document(match.id)
            }
            Result.success(docRef.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteMatchLog(matchId: String): Result<Unit> {
        return try {
            matchLogsCollection.document(matchId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // --- TOURNAMENT METHODS ---

    fun getTournaments(): Flow<List<Tournament>> = callbackFlow {
        val col = try { tournamentsCollection } catch (e: Exception) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val listener = col.orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val tournaments = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Tournament::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(tournaments)
            }
        awaitClose { listener.remove() }
    }

    fun getMatchesForTournament(tournamentId: String): Flow<List<MatchLog>> = callbackFlow {
        val col = try { matchLogsCollection } catch (e: Exception) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val listener = col.whereEqualTo("tournamentId", tournamentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val logs = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(MatchLog::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(logs.sortedBy { it.round })
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveTournament(tournament: Tournament): Result<String> {
        return try {
            val data = hashMapOf(
                "location" to tournament.location,
                "date" to (tournament.date ?: com.google.firebase.Timestamp.now()),
                "participants" to tournament.participants,
                "registrationFee" to tournament.registrationFee,
                "type" to tournament.type,
                "format" to tournament.format,
                "deckName" to tournament.deckName,
                "deckId" to tournament.deckId,
                "createdAt" to (tournament.createdAt ?: com.google.firebase.Timestamp.now())
            )
            val docRef = if (tournament.id.isEmpty()) {
                tournamentsCollection.add(data).await()
            } else {
                tournamentsCollection.document(tournament.id).set(data).await()
                tournamentsCollection.document(tournament.id)
            }
            Result.success(docRef.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteTournament(tournamentId: String): Result<Unit> {
        return try {
            // Elimina anche tutte le partite del torneo
            val matches = matchLogsCollection
                .whereEqualTo("tournamentId", tournamentId)
                .get().await()
            for (doc in matches.documents) {
                doc.reference.delete().await()
            }
            tournamentsCollection.document(tournamentId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // --- GOAL ALBUM METHODS ---

    fun getGoalAlbums(): Flow<List<GoalAlbum>> = callbackFlow {
        val col = try { goalAlbumsCollection } catch (e: Exception) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val listener = col.orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val albums = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val criteriaTypeStr = doc.getString("criteriaType") ?: "SET"
                        val criteriaType = try { GoalCriteriaType.valueOf(criteriaTypeStr) } catch (_: Exception) { GoalCriteriaType.SET }
                        @Suppress("UNCHECKED_CAST")
                        GoalAlbum(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            criteriaType = criteriaType,
                            criteriaValue = doc.getString("criteriaValue") ?: "",
                            targetCardApiIds = (doc.get("targetCardApiIds") as? List<String>) ?: emptyList(),
                            createdAt = doc.getTimestamp("createdAt")
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(albums)
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveGoalAlbum(album: GoalAlbum): Result<String> {
        return try {
            val data = hashMapOf<String, Any?>(
                "name" to album.name,
                "criteriaType" to album.criteriaType.name,
                "criteriaValue" to album.criteriaValue,
                "targetCardApiIds" to album.targetCardApiIds,
                "createdAt" to (album.createdAt ?: com.google.firebase.Timestamp.now())
            )
            val docRef = if (album.id.isEmpty()) {
                goalAlbumsCollection.add(data).await()
            } else {
                goalAlbumsCollection.document(album.id).set(data).await()
                goalAlbumsCollection.document(album.id)
            }
            Result.success(docRef.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteGoalAlbum(goalAlbumId: String): Result<Unit> {
        return try {
            goalAlbumsCollection.document(goalAlbumId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}

data class CollectionStats(
    val totalCards: Int = 0,
    val uniqueCards: Int = 0,
    val totalValue: Double = 0.0,
    val mostValuable: String = "-"
)
