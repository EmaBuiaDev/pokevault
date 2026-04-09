package com.example.pokevault.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.BuildConfig
import com.example.pokevault.data.firebase.FirebaseAuthManager
import com.example.pokevault.util.AppLocale
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null,
    val userName: String = ""
)

class AuthViewModel : ViewModel() {

    private val authManager = FirebaseAuthManager()

    var uiState by mutableStateOf(AuthUiState())
        private set

    private val validProviders = listOf(
        "gmail.com", "outlook.com", "hotmail.it", "hotmail.com",
        "yahoo.com", "yahoo.it", "icloud.com", "libero.it", "virgilio.it",
        "live.it", "fastwebnet.it", "tiscali.it", "alice.it", "tim.it", "poste.it"
    )

    init {
        // Controlla se utente già loggato
        if (authManager.isLoggedIn) {
            uiState = uiState.copy(
                isLoggedIn = true,
                userName = authManager.currentUser?.displayName ?: "Allenatore"
            )
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$".toRegex()
        if (!email.matches(emailRegex)) return false

        val domain = email.substringAfterLast("@").lowercase()
        return validProviders.any { domain == it || domain.endsWith(".$it") }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            uiState = uiState.copy(errorMessage = "Compila tutti i campi")
            return
        }

        if (!isValidEmail(email)) {
            uiState = uiState.copy(errorMessage = "Inserisci un'email valida (es. Gmail, Outlook, Libero)")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            authManager.login(email, password)
                .onSuccess { user ->
                    uiState = uiState.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        userName = user.displayName ?: "Allenatore"
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = mapErrorMessage(error)
                    )
                }
        }
    }

    fun register(email: String, password: String, name: String) {
        if (email.isBlank() || password.isBlank() || name.isBlank()) {
            uiState = uiState.copy(errorMessage = "Compila tutti i campi")
            return
        }

        if (!isValidEmail(email)) {
            uiState = uiState.copy(errorMessage = "Provider email non supportato o non valido")
            return
        }

        if (password.length < 6) {
            uiState = uiState.copy(errorMessage = "La password deve avere almeno 6 caratteri")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            authManager.register(email, password, name)
                .onSuccess { user ->
                    uiState = uiState.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        userName = name
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = mapErrorMessage(error)
                    )
                }
        }
    }

    fun loginWithGoogle(context: Context) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId == 0) {
                uiState = uiState.copy(isLoading = false, errorMessage = "Google Sign-In non configurato correttamente.")
                return@launch
            }
            val webClientId = context.getString(resId)

            // Prova prima con GetGoogleIdOption (seamless, senza picker)
            // Se fallisce (NoCredentialException), usa GetSignInWithGoogleOption (mostra sempre il picker)
            val idToken = tryGetGoogleIdToken(context, webClientId)
                ?: tryGetSignInWithGoogleToken(context, webClientId)

            if (idToken == null) {
                // Assicura che il loading venga disattivato anche se nessun errore è stato impostato
                if (uiState.isLoading) {
                    uiState = uiState.copy(isLoading = false)
                }
                return@launch
            }

            authManager.loginWithGoogle(idToken)
                .onSuccess { user ->
                    uiState = uiState.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        userName = user.displayName ?: "Allenatore"
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = mapErrorMessage(error)
                    )
                }
        }
    }

    /**
     * Tenta il login silenzioso via GetGoogleIdOption (solo account già autorizzati).
     * Ritorna null se non ci sono credenziali precedentemente autorizzate.
     */
    private suspend fun tryGetGoogleIdToken(context: Context, webClientId: String): String? {
        return try {
            val credentialManager = CredentialManager.create(context)
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()
            val result = credentialManager.getCredential(
                context, GetCredentialRequest.Builder().addCredentialOption(option).build()
            )
            GoogleIdTokenCredential.createFrom(result.credential.data).idToken
        } catch (e: GetCredentialCancellationException) {
            uiState = uiState.copy(isLoading = false)
            null
        } catch (e: NoCredentialException) {
            null // Nessun account autorizzato: fallback al picker completo
        } catch (e: Exception) {
            Log.w("AuthViewModel", "GetGoogleIdOption fallito, provo fallback", e)
            null
        }
    }

    /** Fallback: mostra sempre il selettore account Google (funziona anche al primo accesso). */
    private suspend fun tryGetSignInWithGoogleToken(context: Context, webClientId: String): String? {
        return try {
            val credentialManager = CredentialManager.create(context)
            val option = GetSignInWithGoogleOption.Builder(webClientId).build()
            val result = credentialManager.getCredential(
                context, GetCredentialRequest.Builder().addCredentialOption(option).build()
            )
            GoogleIdTokenCredential.createFrom(result.credential.data).idToken
        } catch (e: GetCredentialCancellationException) {
            uiState = uiState.copy(isLoading = false)
            null
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Google Sign-In fallito", e)
            uiState = uiState.copy(
                isLoading = false,
                errorMessage = if (AppLocale.isItalian)
                    "Errore durante il login con Google: ${e.localizedMessage ?: "errore sconosciuto"}. Verifica di avere un account Google sul dispositivo."
                else
                    "Google Sign-In failed: ${e.localizedMessage ?: "unknown error"}. Make sure you have a Google account on your device."
            )
            null
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            uiState = uiState.copy(errorMessage = "Inserisci la tua email")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            authManager.resetPassword(email)
                .onSuccess {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "Email di reset inviata! Controlla la posta."
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = mapErrorMessage(error)
                    )
                }
        }
    }

    fun logout() {
        authManager.logout()
        uiState = AuthUiState()
    }

    fun deleteAccount(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            authManager.deleteAccount()
                .onSuccess {
                    uiState = AuthUiState()
                    onSuccess()
                }
                .onFailure { error ->
                    val message = when {
                        error.message?.contains("requires-recent-login", ignoreCase = true) == true ||
                        error.message?.contains("re-authenticate", ignoreCase = true) == true ->
                            if (AppLocale.isItalian)
                                "Per sicurezza, esegui il logout e accedi di nuovo prima di eliminare l'account."
                            else
                                "For security, please log out and log in again before deleting your account."
                        else -> error.message ?: "Errore sconosciuto"
                    }
                    onError(message)
                }
        }
    }

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    private fun mapErrorMessage(error: Throwable): String {
        return when {
            error.message?.contains("email", ignoreCase = true) == true &&
                error.message?.contains("already", ignoreCase = true) == true ->
                "Questa email è già registrata"
            error.message?.contains("password", ignoreCase = true) == true &&
                error.message?.contains("invalid", ignoreCase = true) == true ->
                "Password non corretta"
            error.message?.contains("no user", ignoreCase = true) == true ->
                "Nessun account trovato con questa email"
            error.message?.contains("network", ignoreCase = true) == true ->
                "Errore di connessione. Controlla internet."
            error.message?.contains("badly formatted", ignoreCase = true) == true ->
                "Formato email non valido"
            else -> error.message ?: "Errore sconosciuto"
        }
    }
}
