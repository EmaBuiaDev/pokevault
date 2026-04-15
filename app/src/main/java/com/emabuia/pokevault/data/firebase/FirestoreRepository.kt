package com.emabuia.pokevault.data.firebase

import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.MatchLog
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.Tournament
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    private val matchLogsCollection
        get() = userDoc.collection("match_logs")

    private val tournamentsCollection
        get() = userDoc.collection("tournaments")

    fun getCards(): Flow<List<PokemonCard>> = callbackFlow {
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
                } ?: emptyList()
                trySend(cards)
            }
        awaitClose { listener.remove() }
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
            val data = hashMapOf<String, Any?>(
                "name" to card.name,
                "imageUrl" to card.imageUrl,
                "set" to card.set,
                "rarity" to card.rarity,
                "type" to card.type,
                "hp" to card.hp,
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
                "language" to card.language,
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

                if (existing != null && existing.documents.isNotEmpty()) {
                    val doc = existing.documents.first()
                    val docRef = doc.reference
                    val currentQty = doc.getLong("quantity")?.toInt() ?: 1
                    // Fire-and-forget: la scrittura colpisce la cache locale
                    // all'istante; lo snapshot listener emette subito l'update.
                    docRef.update(
                        mapOf(
                            "quantity" to (currentQty + card.quantity),
                            "estimatedValue" to card.estimatedValue
                        )
                    )
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
            userDoc.update(
                "totalCards", FieldValue.increment(card.quantity.toLong()),
                "totalValue", FieldValue.increment(card.estimatedValue * card.quantity)
            )

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

    suspend fun deleteCard(cardId: String): Result<Unit> {
        return try {
            // Leggiamo quantità e valore dalla cache locale (istantaneo) per
            // decrementare i totali utente; se la cache non ha nulla passiamo
            // comunque all'eliminazione.
            val cardDoc = try {
                cardsCollection.document(cardId).get(Source.CACHE).await()
            } catch (_: Exception) { null }

            val quantity = cardDoc?.getLong("quantity")?.toInt() ?: 0
            val value = cardDoc?.getDouble("estimatedValue") ?: 0.0
            val totalCardValue = value * quantity

            // Delete diretto: la carta sparisce subito dalla cache locale e
            // lo snapshot listener aggiorna la UI all'istante.
            cardsCollection.document(cardId).delete()

            // Totali fire-and-forget.
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
                for (doc in snapshot.documents) {
                    deleteCard(doc.id)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getCard(cardId: String): Result<PokemonCard> {
        return try {
            val doc = cardsCollection.document(cardId).get().await()
            val card = doc.toObject(PokemonCard::class.java)?.copy(id = doc.id)
                ?: throw Exception("Carta non trovata")
            Result.success(card)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getCollectionStats(): CollectionStats {
        return try {
            // Leggiamo il profilo utente e tutte le carte in parallelo (un solo round-trip extra)
            val userSnapshot = userDoc.get().await()
            val cachedTotal = userSnapshot.getLong("totalCards")?.toInt() ?: 0
            val cachedValue = userSnapshot.getDouble("totalValue") ?: 0.0

            val cards = cardsCollection.get().await()
                .documents.mapNotNull { it.toObject(PokemonCard::class.java) }

            val uniqueKey: (PokemonCard) -> String = { c ->
                c.apiCardId.ifBlank { "${c.name}_${c.set}_${c.cardNumber}" }
            }

            val totalCards = if (cachedTotal > 0) cachedTotal else cards.sumOf { it.quantity }
            val totalValue = if (cachedTotal > 0) cachedValue else cards.sumOf { it.estimatedValue * it.quantity }

            // Aggiorna il profilo se i totali aggregati mancavano (vecchio utente / primo accesso)
            if (cachedTotal == 0 && cards.isNotEmpty()) {
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

    // --- MATCH LOG METHODS ---

    fun getMatchLogs(): Flow<List<MatchLog>> = callbackFlow {
        val col = try { matchLogsCollection } catch (e: Exception) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val listener = col.orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val logs = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(MatchLog::class.java)?.copy(id = doc.id)
                } ?: emptyList()
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
}

data class CollectionStats(
    val totalCards: Int = 0,
    val uniqueCards: Int = 0,
    val totalValue: Double = 0.0,
    val mostValuable: String = "-"
)
