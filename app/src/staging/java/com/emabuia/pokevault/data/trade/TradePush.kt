package com.emabuia.pokevault.data.trade

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.MainActivity
import com.emabuia.pokevault.R
import com.emabuia.pokevault.util.AppLocale
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Le notifiche di TradeRadar dal lato del telefono (fase 3).
 *
 * Firebase Messaging parte spento (manifest: auto-init disattivato): nessun
 * identificativo viene creato finche' l'utente non apre TradeRadar. Si
 * accende con [register] e si rispegne con [forget] (disattivazione, logout,
 * eliminazione dell'account), cosi' su un telefono condiviso le notifiche di
 * un account non arrivano al successivo. In prod TradeRadar non c'e' e
 * niente di qui viene chiamato.
 */
object TradePush {

    const val CHANNEL_ID = "traderadar"

    /** Dove portare chi tocca una notifica: screen = matches | proposals. */
    data class Open(val screen: String, val proposalId: String?)

    /** Una notifica toccata e non ancora gestita dal pannello di TradeRadar. */
    var pendingOpen by mutableStateOf<Open?>(null)
        private set

    fun consumeOpen(): Open? = pendingOpen.also { pendingOpen = null }

    /** Gli extra che il server mette nelle notifiche (data: kind, screen, proposalId). */
    fun handleIntent(intent: Intent?) {
        if (!BuildConfig.TRADE_ENABLED || intent == null) return
        val screen = intent.getStringExtra("screen") ?: return
        if (intent.getStringExtra("kind") == null) return
        pendingOpen = Open(screen, intent.getStringExtra("proposalId"))
        intent.removeExtra("screen")
    }

    /** Questo telefono riceve le notifiche dell'utente di adesso. */
    suspend fun register(context: Context): Boolean {
        if (!TradeApi.isEnabled) return false
        return runCatching {
            ensureChannel(context)
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true
            val token = messaging.token.await()
            TradeApi.putPushToken(token, if (AppLocale.isItalian) "it" else "en") is TradeApi.Result.Ok
        }.getOrElse {
            Timber.w(it, "TradeRadar: registrazione notifiche fallita")
            false
        }
    }

    /** Il token nuovo che Firebase ha dato a questo telefono (dal servizio). */
    suspend fun updateToken(token: String) {
        if (!TradeApi.isEnabled) return
        TradeApi.putPushToken(token, if (AppLocale.isItalian) "it" else "en")
    }

    /**
     * Niente piu' notifiche su questo telefono: il token si butta (il server
     * lo scopre al primo invio e lo cancella) e Firebase si rispegne.
     */
    fun forget() {
        if (!TradeApi.isEnabled) return
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = false
            messaging.deleteToken()
        }.onFailure { Timber.w(it, "TradeRadar: deleteToken fallito") }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, AppLocale.tradeRadarChannelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = AppLocale.tradeRadarChannelDescription
            }
        )
    }

    /**
     * Mostra una notifica arrivata con l'app aperta (in background la mostra
     * il sistema). Il tag e' quello del server: le novita' della stessa
     * proposta si sostituiscono invece di accumularsi.
     */
    fun show(context: Context, title: String, body: String, data: Map<String, String>, tag: String?) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val pending = PendingIntent.getActivity(
            context,
            (tag ?: title).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_traderadar)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(tag, 0, notification) }
            .onFailure { Timber.w(it, "TradeRadar: notifica non mostrata") }
    }
}
