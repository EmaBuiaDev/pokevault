package com.emabuia.pokevault.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.CardGroup
import com.emabuia.pokevault.util.CollectionBrowser
import com.emabuia.pokevault.util.CollectionSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Quante carte mostra la riga "Aggiunte di recente". */
private const val RECENT_CARDS = 10

class HomeViewModel : ViewModel() {

    private val repository = FirestoreRepository()

    /**
     * Le ultime carte aggiunte, una tessera per carta, dalla piu' nuova.
     *
     * Prima la Home mostrava le prime venti carte nell'ordine dei documenti
     * Firestore, cioe' per id: un ordine che non vuol dire niente, con la
     * stessa carta due volte se ne avevi Normale e Reverse. Accanto c'era un
     * conteggio di documenti, un terzo numero diverso sia dalle carte sia
     * dalle uniche di Collezione e Statistiche.
     */
    var recentGroups by mutableStateOf<List<CardGroup>>(emptyList())
        private set

    /** Se la collezione ha almeno una carta: serve a scegliere l'invito giusto. */
    var hasCards by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(true)
        private set

    /**
     * Se la cascata d'ingresso della griglia e' gia' stata giocata.
     *
     * Sta nel ViewModel e non in un `remember` della schermata perche' deve
     * sopravvivere al ritorno sulla Home da un'altra tab: rigiocare
     * l'animazione a ogni rientro la trasforma da benvenuto in attesa.
     */
    var hasEnteredOnce by mutableStateOf(false)
        private set

    fun markEntered() {
        hasEnteredOnce = true
    }

    init {
        loadCards()
    }

    // Niente piu' getCollectionStats(): la Home le caricava da Firestore a ogni
    // apertura e non le mostrava da nessuna parte.
    private fun loadCards() {
        viewModelScope.launch {
            repository.getCards()
                .catch { error ->
                    isLoading = false
                    Timber.w(error, "Errore caricamento carte")
                }
                .collect { cards ->
                    val unknown = AppLocale.unknownExpansion
                    recentGroups = withContext(Dispatchers.Default) {
                        CollectionBrowser.sort(CollectionBrowser.group(cards, unknown), CollectionSort.NEWEST)
                            .take(RECENT_CARDS)
                    }
                    hasCards = cards.isNotEmpty()
                    isLoading = false
                }
        }
    }
}
