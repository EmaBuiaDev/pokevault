package com.example.pokevault.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthManager {

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

    // ── Logout ──
    fun logout() {
        auth.signOut()
    }
}
