package com.emabuia.pokevault.data.firebase

import com.emabuia.pokevault.data.trade.TradeApi
import com.emabuia.pokevault.data.trade.TradePush
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.Date

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

            // Crea profilo utente su Firestore. In merge: isPremium o lastSeen
            // possono essere gia' arrivati (vedi UserProfileFields), e set()
            // pieno li cancellerebbe.
            val profile = hashMapOf(
                "name" to displayName,
                "email" to email,
                "createdAt" to Timestamp.now(),
                "totalCards" to 0
            )
            firestore.collection("users")
                .document(user.uid)
                .set(profile, SetOptions.merge())
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

            // Completa il profilo dove manca: il documento puo' gia' esistere
            // senza (lo crea la sync di isPremium), vedi UserProfileFields.
            completeProfile(user)

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Scrive in merge i campi del profilo che mancano nel documento utente.
     * Lancia se Firestore non risponde: al login l'errore risale come prima.
     *
     * Solo dal server: offline la cache puo' avere il documento a meta' (la
     * sola scrittura di isPremium in coda), e "mancante" li' vorrebbe dire
     * rimettere totalCards a 0 e il nome dell'account sopra quello scelto.
     */
    suspend fun completeProfile(user: FirebaseUser) {
        val ref = firestore.collection("users").document(user.uid)
        val missing = UserProfileFields.missing(
            existing = ref.get(Source.SERVER).await().data,
            name = user.displayName,
            email = user.email,
            createdAtMs = user.metadata?.creationTimestamp ?: System.currentTimeMillis()
        )
        if (missing.isEmpty()) return
        val fields = missing.mapValues { (key, value) ->
            if (key == "createdAt") Timestamp(Date(value as Long)) else value
        }
        ref.set(fields, SetOptions.merge()).await()
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

            // Prima il profilo TradeRadar, finche' c'e' l'account per autenticarsi:
            // dopo non ci sarebbe piu' modo di cancellarlo. Se il server non
            // risponde ci si ferma, e l'utente riprova.
            if (TradeApi.isEnabled) {
                when (TradeApi.deleteProfile()) {
                    is TradeApi.Result.Ok, TradeApi.Result.NoProfile -> Unit
                    else -> throw Exception("TradeRadar non raggiungibile: riprova tra poco")
                }
                TradePush.forget()
            }

            val userDoc = firestore.collection("users").document(uid)
            val subcollections = listOf(
                "cards",
                "decks",
                "albums",
                "wishlists",
                "match_logs",
                "tournaments",
                "goal_albums",
                "followed_illustrators"
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
        // Sullo stesso telefono il prossimo account non deve ricevere le notifiche di questo.
        TradePush.forget()
        auth.signOut()
    }
}
