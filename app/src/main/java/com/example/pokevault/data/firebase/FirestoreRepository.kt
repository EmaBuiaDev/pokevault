package com.example.pokevault.data.firebase

import com.example.pokevault.data.model.Deck
import com.example.pokevault.data.model.PokemonCard
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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

    private val userDoc
        get() = firestore.collection("users").document(userId)

    private val cardsCollection
        get() = userDoc.collection("cards")

    private val decksCollection
        get() = userDoc.collection("decks")

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

            var finalCardId = ""
            
            firestore.runTransaction { transaction ->
                // 1. Controlla se esiste già
                val existingQuery = cardsCollection
                    .whereEqualTo("apiCardId", card.apiCardId)
                    .whereEqualTo("variant", card.variant)
                    .get()

                // Nota: In una transazione Firestore le query devono essere risolte prima o gestite con attenzione
                // Qui usiamo un approccio semplificato per l'aggiornamento dei totali
            }

            // Per semplicità e robustezza usiamo Batch o operazioni singole atomiche
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
                        
                        // Aggiorna totali utente
                        transaction.update(userDoc, "totalCards", FieldValue.increment(card.quantity.toLong()))
                        transaction.update(userDoc, "totalValue", FieldValue.increment(card.estimatedValue * card.quantity))
                    }.await()
                    return Result.success(existing.documents.first().id)
                }
            }

            // Nuova carta
            val docRef = cardsCollection.add(data).await()
            userDoc.update(
                "totalCards", FieldValue.increment(card.quantity.toLong()),
                "totalValue", FieldValue.increment(card.estimatedValue * card.quantity)
            ).await()
            
            Result.success(docRef.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun updateCard(cardId: String, card: PokemonCard): Result<Unit> {
        return try {
            val oldCardDoc = cardsCollection.document(cardId).get().await()
            val oldQty = oldCardDoc.getLong("quantity")?.toInt() ?: 0
            val oldValue = oldCardDoc.getDouble("estimatedValue") ?: 0.0
            
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
            
            firestore.runTransaction { transaction ->
                transaction.update(cardsCollection.document(cardId), data)
                if (qtyDiff != 0) transaction.update(userDoc, "totalCards", FieldValue.increment(qtyDiff.toLong()))
                if (valueDiff != 0.0) transaction.update(userDoc, "totalValue", FieldValue.increment(valueDiff))
            }.await()
            
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteCard(cardId: String): Result<Unit> {
        return try {
            val cardDoc = cardsCollection.document(cardId).get().await()
            val quantity = cardDoc.getLong("quantity")?.toInt() ?: 0
            val value = cardDoc.getDouble("estimatedValue") ?: 0.0
            val totalCardValue = value * quantity

            firestore.runTransaction { transaction ->
                transaction.delete(cardsCollection.document(cardId))
                transaction.update(userDoc, "totalCards", FieldValue.increment(-quantity.toLong()))
                transaction.update(userDoc, "totalValue", FieldValue.increment(-totalCardValue))
            }.await()
            
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteCardByApiId(apiCardId: String): Result<Unit> {
        return try {
            val snapshot = cardsCollection
                .whereEqualTo("apiCardId", apiCardId)
                .get().await()
            
            for (doc in snapshot.documents) {
                deleteCard(doc.id) // Riutilizziamo la logica di eliminazione con aggiornamento totali
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
            // Leggiamo i dati aggregati dal profilo utente (super veloce!)
            val userSnapshot = userDoc.get().await()
            val totalCards = userSnapshot.getLong("totalCards")?.toInt() ?: 0
            val totalValue = userSnapshot.getDouble("totalValue") ?: 0.0
            
            // Se totalCards è 0 ma sappiamo di avere carte (magari vecchio utente), 
            // facciamo il ricalcolo una tantum (fallback)
            if (totalCards == 0) {
                val snapshot = cardsCollection.get().await()
                val cards = snapshot.documents.mapNotNull { it.toObject(PokemonCard::class.java) }
                if (cards.isNotEmpty()) {
                    val recalculatedStats = CollectionStats(
                        totalCards = cards.sumOf { it.quantity },
                        uniqueCards = cards.map { it.apiCardId.ifBlank { "${it.name}_${it.set}_${it.cardNumber}" } }.toSet().size,
                        totalValue = cards.sumOf { it.estimatedValue * it.quantity },
                        mostValuable = cards.maxByOrNull { it.estimatedValue }?.name ?: "-"
                    )
                    // Aggiorniamo il profilo per la prossima volta
                    userDoc.update(mapOf(
                        "totalCards" to recalculatedStats.totalCards,
                        "totalValue" to recalculatedStats.totalValue
                    ))
                    return recalculatedStats
                }
            }

            CollectionStats(
                totalCards = totalCards,
                totalValue = totalValue,
                // uniqueCards e mostValuable richiedono ancora una scansione se non salvati, 
                // ma totalCards e totalValue sono i più pesanti
                uniqueCards = 0, // Opzionale: implementare logica simile se necessario
                mostValuable = "-"
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
