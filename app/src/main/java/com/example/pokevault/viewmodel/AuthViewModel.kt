package com.example.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokevault.data.firebase.FirebaseAuthManager
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

    fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

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
