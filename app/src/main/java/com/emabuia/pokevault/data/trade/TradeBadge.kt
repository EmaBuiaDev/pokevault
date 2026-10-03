package com.emabuia.pokevault.data.trade

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.data.trade.dto.TradeProposal

/**
 * Il numero sul tasto TradeRadar della barra in basso: le proposte che
 * aspettano te. E' lo stesso della tab Proposte, contato con la stessa regola
 * ([countOf], che usa anche TradeRadarViewModel.proposalsToAnswer).
 *
 * Lo aggiornano il ViewModel quando rilegge le proposte e la barra quando
 * l'app torna in primo piano ([refresh]): una richiesta, e solo con TradeRadar
 * acceso nel build. Senza profilo o senza accesso il numero e' 0.
 */
object TradeBadge {

    var pending by mutableIntStateOf(0)
        private set

    /** Prefs e chiave dove il ViewModel ricorda gli scambi gia' applicati alla collezione. */
    const val PREFS = "trade_radar"
    const val KEY_APPLIED_CLOSINGS = "applied_closings"

    /** Le proposte in cui tocca a me, o chiuse senza ancora il riepilogo della collezione. */
    fun countOf(proposals: List<TradeProposal>, appliedClosings: Set<String>): Int =
        proposals.count { proposal ->
            proposal.actionNeeded == true ||
                proposal.myTurn == true ||
                (proposal.status == "done" && proposal.id != null && proposal.id !in appliedClosings)
        }

    fun update(count: Int) {
        pending = count.coerceAtLeast(0)
    }

    suspend fun refresh(context: Context) {
        if (!BuildConfig.TRADE_ENABLED) return
        when (val result = TradeApi.proposals()) {
            is TradeApi.Result.Ok -> {
                val applied = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getStringSet(KEY_APPLIED_CLOSINGS, emptySet())
                    .orEmpty()
                update(countOf(result.value.proposals.orEmpty(), applied))
            }
            TradeApi.Result.NoProfile, TradeApi.Result.Unauthorized -> update(0)
            // Rete o server giu': si tiene l'ultimo numero visto.
            else -> Unit
        }
    }
}
