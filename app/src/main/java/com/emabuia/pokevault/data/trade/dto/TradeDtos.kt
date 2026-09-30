package com.emabuia.pokevault.data.trade.dto

import com.google.gson.annotations.SerializedName

/*
 * Risposte del Worker di TradeRadar, lette con Gson.
 *
 * Stanno in un package a parte perche' proguard-rules.pro tiene per intero
 * questo e nient'altro di data.trade: senza la regola R8 le considera sempre
 * null (le scrive solo Gson, per reflection) e in release ogni risposta
 * sembrerebbe vuota. Tenere l'intero data.trade invece impedirebbe a R8 di
 * togliere TradeRadar dalla build prod, dove e' spento.
 */

/** GET /v1/trade/me */
data class TradeMePayload(
    @SerializedName("uid") val uid: String? = null,
    @SerializedName("schemaVersion") val schemaVersion: Int? = null
)
