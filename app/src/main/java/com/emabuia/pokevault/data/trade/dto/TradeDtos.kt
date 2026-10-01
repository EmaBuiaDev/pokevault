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
    @SerializedName("qty") val qty: Int? = null,
    /** Carta singola messa in lista a mano (schema 3). */
    @SerializedName("manual") val manual: Boolean? = null,
    /** Partecipa agli avvisi: sempre per i doppioni, a scelta per le carte a mano. */
    @SerializedName("notify") val notify: Boolean? = null,
    /** Copie promesse in un accordo: restano mie, ma gli altri non le vedono (solo in GET). */
    @SerializedName("reserved") val reserved: Int? = null
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
    @SerializedName("reason") val reason: String? = null,
    /** Nome italiano della carta e del set, dal catalogo; null se il server non li trova. */
    @SerializedName("name") val name: String? = null,
    @SerializedName("setName") val setName: String? = null,
    /** Solo con reason "set": carte del set possedute da chi la riceve, su quante. */
    @SerializedName("setOwned") val setOwned: Int? = null,
    @SerializedName("setSize") val setSize: Int? = null
)

data class TradeMatch(
    /** Id pubblico della persona: per proporle uno scambio o leggere le sue offerte. */
    @SerializedName("id") val id: String? = null,
    @SerializedName("nickname") val nickname: String? = null,
    /** "lt5" | "lt15" */
    @SerializedName("distance") val distance: String? = null,
    @SerializedName("tradesDone") val tradesDone: Int? = null,
    @SerializedName("memberSince") val memberSince: Long? = null,
    @SerializedName("level") val level: String? = null,
    @SerializedName("mutual") val mutual: Boolean? = null,
    @SerializedName("theyGive") val theyGive: List<TradeMatchItem>? = null,
    @SerializedName("iGive") val iGive: List<TradeMatchItem>? = null,
    /** Quante carte in tutto per lato: le liste sopra ne portano al massimo 60. */
    @SerializedName("theyGiveCount") val theyGiveCount: Int? = null,
    @SerializedName("iGiveCount") val iGiveCount: Int? = null
)

/** Chi ha una carta, nella vista per carta. */
data class TradeCardHolder(
    /** Posizione del match in [TradeMatchesPayload.matches]. */
    @SerializedName("match") val match: Int? = null,
    @SerializedName("qty") val qty: Int? = null,
    @SerializedName("variant") val variant: String? = null,
    @SerializedName("condition") val condition: String? = null,
    @SerializedName("language") val language: String? = null
)

/** Una carta che posso ricevere e chi ce l'ha: la vista per carta. */
data class TradeCardOffer(
    @SerializedName("key") val key: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("setName") val setName: String? = null,
    @SerializedName("level") val level: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("setOwned") val setOwned: Int? = null,
    @SerializedName("setSize") val setSize: Int? = null,
    /** Persone che ce l'hanno; [holders] ne elenca al massimo 50. */
    @SerializedName("holderCount") val holderCount: Int? = null,
    @SerializedName("holders") val holders: List<TradeCardHolder>? = null
)

/** GET /v1/trade/matches */
data class TradeMatchesPayload(
    @SerializedName("paused") val paused: Boolean? = null,
    /** Persone attive nella zona, anche senza carte per te. */
    @SerializedName("nearby") val nearby: Int? = null,
    /** Le migliori 100, gia' in ordine di punteggio. */
    @SerializedName("matches") val matches: List<TradeMatch>? = null,
    @SerializedName("cards") val cards: List<TradeCardOffer>? = null
)

/** Corpo delle risposte d'errore: { "error": "no_profile" } e simili. */
data class TradeErrorPayload(
    @SerializedName("error") val error: String? = null
)

// ── Proposte (fase 2a) ──────────────────────────────────────────────────────

/** Una carta in una proposta, o fra le offerte di un'altra persona. */
data class TradeOfferItem(
    @SerializedName("key") val key: String? = null,
    @SerializedName("variant") val variant: String? = null,
    @SerializedName("condition") val condition: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("qty") val qty: Int? = null,
    /** Dal catalogo, solo nelle risposte: il server li ignora nelle richieste. */
    @SerializedName("name") val name: String? = null,
    @SerializedName("setName") val setName: String? = null
)

/** GET /v1/trade/users/:id/haves */
data class TradeUserHavesPayload(
    @SerializedName("items") val items: List<TradeOfferItem>? = null
)

/** POST /v1/trade/proposals: give e take dal punto di vista di chi manda. */
data class TradeProposalRequest(
    @SerializedName("to") val to: String,
    @SerializedName("give") val give: List<TradeOfferItem>,
    @SerializedName("take") val take: List<TradeOfferItem>
)

/** POST /v1/trade/proposals/:id/counter */
data class TradeCounterRequest(
    @SerializedName("give") val give: List<TradeOfferItem>,
    @SerializedName("take") val take: List<TradeOfferItem>
)

data class TradeCreatedPayload(
    @SerializedName("id") val id: String? = null
)

data class TradeCounterpart(
    /** L'id pubblico, lo stesso di [TradeMatch.id]. */
    @SerializedName("id") val id: String? = null,
    @SerializedName("nickname") val nickname: String? = null,
    /** "lt5" | "lt15" | "far" */
    @SerializedName("distance") val distance: String? = null,
    @SerializedName("tradesDone") val tradesDone: Int? = null
)

/** Una proposta vista da me: [give] e' cio' che do, [take] cio' che ricevo. */
data class TradeProposal(
    @SerializedName("id") val id: String? = null,
    /** "open" | "accepted" | "declined" | "cancelled" */
    @SerializedName("status") val status: String? = null,
    /** Da 2 in su e' una controproposta. */
    @SerializedName("revision") val revision: Int? = null,
    @SerializedName("myTurn") val myTurn: Boolean? = null,
    /** Serve una mia mossa: rispondere, o confermare l'appuntamento. */
    @SerializedName("actionNeeded") val actionNeeded: Boolean? = null,
    @SerializedName("meeting") val meeting: TradeMeeting? = null,
    @SerializedName("iStarted") val iStarted: Boolean? = null,
    @SerializedName("closedByMe") val closedByMe: Boolean? = null,
    @SerializedName("createdAt") val createdAt: Long? = null,
    @SerializedName("updatedAt") val updatedAt: Long? = null,
    @SerializedName("counterpart") val counterpart: TradeCounterpart? = null,
    @SerializedName("give") val give: List<TradeOfferItem>? = null,
    @SerializedName("take") val take: List<TradeOfferItem>? = null
)

/** GET /v1/trade/proposals */
data class TradeProposalsPayload(
    @SerializedName("proposals") val proposals: List<TradeProposal>? = null
)

// ── Luoghi e appuntamento (fase 2b) ─────────────────────────────────────────

data class TradeSpot(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    /** card_shop | comics | games | video_games | toys | mall | library | other */
    @SerializedName("kind") val kind: String? = null,
    @SerializedName("city") val city: String? = null,
    /** Solo per i luoghi segnalati: l'indirizzo scritto da chi l'ha segnalato. */
    @SerializedName("address") val address: String? = null,
    @SerializedName("openingHours") val openingHours: String? = null,
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lon") val lon: Double? = null,
    /** Dal punto a meta' strada fra le due zone. */
    @SerializedName("distanceKm") val distanceKm: Double? = null,
    /** Segnalato da un utente e non ancora approvato. */
    @SerializedName("pending") val pending: Boolean? = null
)

data class TradeSlot(
    /** yyyy-MM-dd */
    @SerializedName("day") val day: String,
    /** "HH:mm": l'ora dell'appuntamento (dal 01/10; le prime prove avevano solo la fascia). */
    @SerializedName("time") val time: String? = null,
    /** morning | afternoon | evening: la parte della giornata, ricavata dall'ora. */
    @SerializedName("part") val part: String? = null
)

data class TradeMeeting(
    /** none | proposed | confirmed */
    @SerializedName("status") val status: String? = null,
    @SerializedName("byMe") val byMe: Boolean? = null,
    @SerializedName("spot") val spot: TradeSpot? = null,
    @SerializedName("slots") val slots: List<TradeSlot>? = null,
    @SerializedName("slot") val slot: TradeSlot? = null
)

/** Una zona che il server non ha ancora: il telefono la scarica da Overpass con [query]. */
data class TradeMissingCell(
    @SerializedName("cell") val cell: String? = null,
    @SerializedName("query") val query: String? = null
)

/** GET /v1/trade/proposals/:id/spots */
data class TradeSpotsPayload(
    @SerializedName("spots") val spots: List<TradeSpot>? = null,
    @SerializedName("missingCells") val missingCells: List<TradeMissingCell>? = null
)

/** POST /v1/trade/spots/cell: la risposta grezza di Overpass, il filtro lo fa il server. */
data class TradeCellUpload(
    @SerializedName("cell") val cell: String,
    @SerializedName("elements") val elements: com.google.gson.JsonArray
)

data class TradeSpotCandidate(
    @SerializedName("osmId") val osmId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("kind") val kind: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lon") val lon: Double? = null,
    @SerializedName("distanceKm") val distanceKm: Double? = null
)

/** GET /v1/trade/spots/search */
data class TradeSpotSearchPayload(
    @SerializedName("results") val results: List<TradeSpotCandidate>? = null
)

/** POST /v1/trade/spots: un luogo trovato con la ricerca, o una segnalazione (solo nome e citta'). */
data class TradeAddSpotRequest(
    @SerializedName("osmId") val osmId: String? = null,
    @SerializedName("name") val name: String,
    @SerializedName("kind") val kind: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lon") val lon: Double? = null,
    /** Per una segnalazione: facoltativo, da li' il server ricava le coordinate. */
    @SerializedName("address") val address: String? = null
)

data class TradeAddSpotPayload(
    @SerializedName("spot") val spot: TradeSpot? = null
)

/** POST /v1/trade/proposals/:id/meeting */
data class TradeMeetingRequest(
    @SerializedName("spot") val spot: String,
    @SerializedName("slots") val slots: List<TradeSlot>
)

/** POST /v1/trade/proposals/:id/meeting/confirm */
data class TradeConfirmRequest(
    @SerializedName("slot") val slot: Int
)
