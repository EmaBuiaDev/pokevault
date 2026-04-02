package com.example.pokevault.data.firebase

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

    fun getCards(): Flow<List<PokemonCard>> = callbackFlow {
        val listener = cardsCollection
            .orderBy("addedAt", Query.Direction.DESCENDING)
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
            // Controlla se esiste già una carta identica (stesso apiCardId + variante + lingua + condizione)
            // Se sì, incrementa quantity invece di creare un duplicato
            val existingDoc = if (card.apiCardId.isNotBlank()) {
                val query = cardsCollection
                    .whereEqualTo("apiCardId", card.apiCardId)
                    .whereEqualTo("variant", card.variant)
                    .whereEqualTo("language", card.language)
                    .whereEqualTo("condition", card.condition)
                    .get().await()
                query.documents.firstOrNull()
            } else {
                // Per carte senza apiCardId, cerca per nome + set + variante + lingua + condizione
                val query = cardsCollection
                    .whereEqualTo("name", card.name)
                    .whereEqualTo("set", card.set)
                    .whereEqualTo("variant", card.variant)
                    .whereEqualTo("language", card.language)
                    .whereEqualTo("condition", card.condition)
                    .get().await()
                query.documents.firstOrNull()
            }

            if (existingDoc != null) {
                // Carta già presente: incrementa quantity
                val currentQty = (existingDoc.getLong("quantity") ?: 1).toInt()
                val newQty = currentQty + card.quantity
                existingDoc.reference.update("quantity", newQty).await()
                Result.success(existingDoc.id)
            } else {
                // Carta nuova: crea documento
                val data = hashMapOf(
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
                val docRef = cardsCollection.add(data).await()
                Result.success(docRef.id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCard(cardId: String, card: PokemonCard): Result<Unit> {
        return try {
            val data = hashMapOf<String, Any>(
                "name" to card.name,
                "imageUrl" to card.imageUrl,
                "set" to card.set,
                "rarity" to card.rarity,
                "type" to card.type,
                "hp" to card.hp,
                "isGraded" to card.isGraded,
                "gradingCompany" to card.gradingCompany,
                "estimatedValue" to card.estimatedValue,
                "quantity" to card.quantity,
                "condition" to card.condition,
                "notes" to card.notes,
                "variant" to card.variant,
                "language" to card.language
            )
            if (card.grade != null) data["grade"] = card.grade!!
            cardsCollection.document(cardId).update(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCard(cardId: String): Result<Unit> {
        return try {
            cardsCollection.document(cardId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun searchCards(query: String): Flow<List<PokemonCard>> = callbackFlow {
        val listener = cardsCollection.orderBy("name")
            .startAt(query).endAt(query + "\uf8ff")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val cards = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(PokemonCard::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(cards)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getCollectionStats(): CollectionStats {
        return try {
            val snapshot = cardsCollection.get().await()
            val cards = snapshot.documents.mapNotNull { it.toObject(PokemonCard::class.java) }
            CollectionStats(
                totalCards = cards.sumOf { it.quantity },
                uniqueCards = cards.size,
                totalValue = cards.sumOf { it.estimatedValue * it.quantity },
                mostValuable = cards.maxByOrNull { it.estimatedValue }?.name ?: "-"
            )
        } catch (e: Exception) { CollectionStats() }
    }
}

data class CollectionStats(
    val totalCards: Int = 0,
    val uniqueCards: Int = 0,
    val totalValue: Double = 0.0,
    val mostValuable: String = "-"
)
