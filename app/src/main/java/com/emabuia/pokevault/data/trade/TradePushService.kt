package com.emabuia.pokevault.data.trade

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Riceve le notifiche di TradeRadar (dichiarato nel manifest principale dalla
 * 3.1.6, quando TradeRadar e' uscito; prima solo nello staging).
 *
 * Con l'app in background la notifica la mostra il sistema e questo servizio
 * non viene chiamato; con l'app aperta arriva qui e la mostra [TradePush.show].
 */
class TradePushService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // Solo con qualcuno dentro: il server lega il token all'utente.
        if (FirebaseAuth.getInstance().currentUser == null) return
        scope.launch { TradePush.updateToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Il numero sul tasto Radar della barra: una proposta nuova lo cambia.
        scope.launch { TradeBadge.refresh(applicationContext) }
        val notification = message.notification ?: return
        TradePush.show(
            context = this,
            title = notification.title.orEmpty(),
            body = notification.body.orEmpty(),
            data = message.data,
            tag = notification.tag
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
