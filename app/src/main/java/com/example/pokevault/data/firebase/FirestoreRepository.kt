package com.example.pokevault.data.firebase

import com.example.pokevault.data.model.Deck
import com.example.pokevault.data.model.PokemonCard
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val userId: String
        get() = auth.currentUser?.uid ?: throw Exception("Utente non autenticato")

    private val cardsCollection
        get() = firestore.collection("users").document(userId).collection("cards")

    private val decksCollection
        get() = firestore.collection("users").document(userId).collection("decks")

    fun getCards(): Flow<List<PokemonCard>> = callbackFlow {
        val listener = cardsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val cards = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(PokemonCard::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(cards)
            }
        awaitClose { listener.remove() }
    }

    fun getOwnedCardsBySet(setName: String): Flow<List<PokemonCard>> = callbackFlow {
        val listener = cardsCollection
            .whereEqualTo("set", setName)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val cards = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(PokemonCard::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(cards)
            }
        awaitClose { listener.remove() }
    }

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

            // Se esiste gia' una carta con stesso apiCardId e variante, incrementa la quantita' con transaction
            if (card.apiCardId.isNotBlank()) {
                val existing = cardsCollection
                    .whereEqualTo("apiCardId", card.apiCardId)
                    .whereEqualTo("variant", card.variant)
                    .get().await()

                if (existing.documents.isNotEmpty()) {
                    val docRef = existing.documents.first().reference
                    firestore.runTransaction { transaction ->
                        val snapshot = transaction.get(docRef)
                        val currentQty = snapshot.getLong("quantity")?.toInt() ?: 1
                        transaction.update(docRef, mapOf(
                            "quantity" to (currentQty + card.quantity),
                            "estimatedValue" to card.estimatedValue
                        ))
                    }.await()
                    return Result.success(existing.documents.first().id)
                }
            }

            val docRef = cardsCollection.add(data).await()
            Result.success(docRef.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun updateCard(cardId: String, card: PokemonCard): Result<Unit> {
        return try {
            val data = mutableMapOf<String, Any?>(
                "isGraded" to card.isGraded,
                "grade" to card.grade,
                "gradingCompany" to card.gradingCompany,
                "quantity" to card.quantity,
                "condition" to card.condition,
                "notes" to card.notes,
                "estimatedValue" to card.estimatedValue
            )
            cardsCollection.document(cardId).update(data).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteCard(cardId: String): Result<Unit> {
        return try {
            cardsCollection.document(cardId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteCardByApiId(apiCardId: String): Result<Unit> {
        return try {
            val snapshot = cardsCollection
                .whereEqualTo("apiCardId", apiCardId)
                .get().await()
            for (doc in snapshot.documents) {
                doc.reference.delete().await()
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
            val snapshot = cardsCollection.get().await()
            val cards = snapshot.documents.mapNotNull { it.toObject(PokemonCard::class.java) }
            val mostValuable = cards.maxByOrNull { it.estimatedValue }?.name ?: "-"

            CollectionStats(
                totalCards = cards.sumOf { it.quantity },
                uniqueCards = cards.map { it.apiCardId.ifBlank { "${it.name}_${it.set}_${it.cardNumber}" } }.toSet().size,
                totalValue = cards.sumOf { it.estimatedValue * it.quantity },
                mostValuable = mostValuable
            )
        } catch (e: Exception) { CollectionStats() }
    }

    // --- DECK METHODS ---

    fun getDecks(): Flow<List<Deck>> = callbackFlow {
        val listener = decksCollection
            .orderBy("createdAt", Query.Direction.DESCENDING)
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
}

data class CollectionStats(
    val totalCards: Int = 0,
    val uniqueCards: Int = 0,
    val totalValue: Double = 0.0,
    val mostValuable: String = "-"
)
