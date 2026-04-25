package com.emabuia.pokevault.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthManager {

    enum class ReauthProvider {
        PASSWORD,
        GOOGLE,
        UNKNOWN
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Utente corrente
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isLoggedIn: Boolean
        get() = currentUser != null

    // Osserva stato autenticazione in tempo reale
    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // ── Registrazione con email/password ──
    suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("Registrazione fallita")

            // Crea profilo utente su Firestore
            val profile = hashMapOf(
                "name" to displayName,
                "email" to email,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "totalCards" to 0
            )
            firestore.collection("users")
                .document(user.uid)
                .set(profile)
                .await()

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Login con email/password ──
    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("Login fallito")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Login con Google ──
    suspend fun loginWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: throw Exception("Login Google fallito")

            // Crea profilo se primo accesso
            val doc = firestore.collection("users").document(user.uid).get().await()
            if (!doc.exists()) {
                val profile = hashMapOf(
                    "name" to (user.displayName ?: "Allenatore"),
                    "email" to (user.email ?: ""),
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "totalCards" to 0
                )
                firestore.collection("users")
                    .document(user.uid)
                    .set(profile)
                    .await()
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Reset password ──
    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getReauthProvider(): ReauthProvider {
        val user = currentUser ?: return ReauthProvider.UNKNOWN
        val providers = user.providerData
            .mapNotNull { it.providerId }
            .filter { it != "firebase" }

        return when {
            providers.contains("password") -> ReauthProvider.PASSWORD
            providers.contains("google.com") -> ReauthProvider.GOOGLE
            else -> ReauthProvider.UNKNOWN
        }
    }

    suspend fun reauthenticateWithPassword(password: String): Result<Unit> {
        return try {
            val user = currentUser ?: throw Exception("Nessun utente autenticato")
            val email = user.email ?: throw Exception("Email utente non disponibile")
            val credential = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reauthenticateWithGoogle(idToken: String): Result<Unit> {
        return try {
            val user = currentUser ?: throw Exception("Nessun utente autenticato")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Eliminazione account e dati ──
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = currentUser ?: throw Exception("Nessun utente autenticato")
            val uid = user.uid

            val userDoc = firestore.collection("users").document(uid)
            val subcollections = listOf(
                "cards",
                "decks",
                "albums",
                "wishlists",
                "match_logs",
                "tournaments",
                "goal_albums"
            )

            for (collectionName in subcollections) {
                deleteSubcollection(userDoc, collectionName)
            }

            // Elimina documento utente
            userDoc.delete().await()

            // Elimina account Firebase Auth
            user.delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun deleteSubcollection(
        userDoc: com.google.firebase.firestore.DocumentReference,
        collectionName: String
    ) {
        val snapshot = userDoc.collection(collectionName).get().await()
        for (doc in snapshot.documents) {
            doc.reference.delete().await()
        }
    }

    // ── Logout ──
    fun logout() {
        auth.signOut()
    }
}
