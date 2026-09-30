package com.emabuia.pokevault.data.trade.dto

import com.google.gson.annotations.SerializedName

/*
 * Richieste e risposte del Worker di TradeRadar, lette e scritte con Gson.
 *
 * Stanno in un package a parte perche' proguard-rules.pro tiene per intero
 * questo e nient'altro di data.trade: senza la regola R8 le considera sempre
 * null (le scrive solo Gson, per reflection) e in release ogni risposta
 * sembrerebbe vuota. Tenere l'intero data.trade invece impedirebbe a R8 di
 * togliere TradeRadar dalla build prod, dove e' spento.
 *
 * I nomi JSON sono fissati da @SerializedName: rinominare una proprieta'
 * Kotlin non deve rompere il contratto con src/trade.ts.
 */

/** GET /v1/trade/me */
data class TradeMePayload(
    @SerializedName("uid") val uid: String? = null,
    @SerializedName("schemaVersion") val schemaVersion: Int? = null
)

/** PUT /v1/trade/profile */
data class TradeProfileRequest(
    @SerializedName("nickname") val nickname: String,
    @SerializedName("geohash5") val geohash5: String,
    @SerializedName("adultConfirmed") val adultConfirmed: Boolean,
    @SerializedName("collectionConsent") val collectionConsent: Boolean,
    @SerializedName("paused") val paused: Boolean
)

/** GET e PUT /v1/trade/profile */
data class TradeProfilePayload(
    @SerializedName("nickname") val nickname: String? = null,
    @SerializedName("geohash5") val geohash5: String? = null,
    @SerializedName("paused") val paused: Boolean? = null,
    @SerializedName("ownedHash") val ownedHash: String? = null,
    @SerializedName("tradesDone") val tradesDone: Int? = null,
    @SerializedName("memberSince") val memberSince: Long? = null,
    @SerializedName("haves") val haves: Int? = null,
    @SerializedName("wants") val wants: Int? = null,
    @SerializedName("owned") val owned: Int? = null
)

data class TradeHaveItem(
    @SerializedName("key") val key: String? = null,
    @SerializedName("variant") val variant: String? = null,
    @SerializedName("condition") val condition: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("qty") val qty: Int? = null
)

/** GET e PUT /v1/trade/haves */
data class TradeHavesPayload(
    @SerializedName("items") val items: List<TradeHaveItem>? = null
)

data class TradeWantItem(
    @SerializedName("key") val key: String,
    @SerializedName("source") val source: String,
    @SerializedName("priority") val priority: String = "nice"
)

/** PUT /v1/trade/wants */
data class TradeWantsRequest(
    @SerializedName("items") val items: List<TradeWantItem>
)

/** PUT /v1/trade/owned */
data class TradeOwnedRequest(
    @SerializedName("keys") val keys: List<String>,
    @SerializedName("hash") val hash: String
)

data class TradeMatchItem(
    @SerializedName("key") val key: String? = null,
    @SerializedName("variant") val variant: String? = null,
    @SerializedName("condition") val condition: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("qty") val qty: Int? = null,
    /** "wanted" | "useful" | "possible" */
    @SerializedName("level") val level: String? = null,
    /** "wishlist" | "album" | "set", o null */
    @SerializedName("reason") val reason: String? = null
)

data class TradeMatch(
    @SerializedName("nickname") val nickname: String? = null,
    /** "lt5" | "lt15" */
    @SerializedName("distance") val distance: String? = null,
    @SerializedName("tradesDone") val tradesDone: Int? = null,
    @SerializedName("memberSince") val memberSince: Long? = null,
    @SerializedName("level") val level: String? = null,
    @SerializedName("mutual") val mutual: Boolean? = null,
    @SerializedName("theyGive") val theyGive: List<TradeMatchItem>? = null,
    @SerializedName("iGive") val iGive: List<TradeMatchItem>? = null
)

/** GET /v1/trade/matches */
data class TradeMatchesPayload(
    @SerializedName("paused") val paused: Boolean? = null,
    @SerializedName("matches") val matches: List<TradeMatch>? = null
)

/** Corpo delle risposte d'errore: { "error": "no_profile" } e simili. */
data class TradeErrorPayload(
    @SerializedName("error") val error: String? = null
)
