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
    @SerializedName("owned") val owned: Int? = null,
    /** I miei voti ricevuti (fase 2c). */
    @SerializedName("reputation") val reputation: TradeReputation? = null,
    /** Il mio livello e se compaio in classifica (null = mai chiesto) (fase 2e). */
    @SerializedName("tier") val tier: String? = null,
    @SerializedName("leaderboardOptIn") val leaderboardOptIn: Boolean? = null,
    /** Il Pokemon del podio (numero di Pokedex), null = l'iniziale. */
    @SerializedName("avatar") val avatar: Int? = null,
    @SerializedName("avatarAnimated") val avatarAnimated: Boolean? = null,
    /** Sospeso fino a (ms), e perche': no_show | reports | admin. Null se non lo e' (fase 2f). */
    @SerializedName("suspendedUntil") val suspendedUntil: Long? = null,
    @SerializedName("suspensionReason") val suspensionReason: String? = null,
    /** Le categorie di notifiche (fase 3). */
    @SerializedName("notify") val notify: TradeNotifyPrefs? = null,
    /** Prova gratuita / Premium / solo ricevere (schema 13). Null da un server piu' vecchio. */
    @SerializedName("access") val access: TradeAccess? = null
)

/**
 * Cosa puo' fare l'utente in TradeRadar: 30 giorni di prova dall'attivazione,
 * poi Premium. Senza Premium, finita la prova, "solo ricevere": risponde alle
 * proposte e finisce gli scambi avviati, ma non sfoglia i match ne' ne manda
 * di nuove. [enforced] dice se il server lo fa gia' rispettare.
 */
data class TradeAccess(
    /** trial | premium | receive_only */
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("trialEndsAt") val trialEndsAt: Long? = null,
    @SerializedName("enforced") val enforced: Boolean? = null
) {
    val isTrial: Boolean get() = mode == "trial"
    val isReceiveOnly: Boolean get() = mode == "receive_only"

    /** Giorni di prova rimasti, arrotondati in su (l'ultimo giorno e' "1"). */
    fun trialDaysLeft(now: Long = System.currentTimeMillis()): Int {
        val end = trialEndsAt ?: return 0
        val left = end - now
        return if (left <= 0) 0 else ((left + DAY_MS - 1) / DAY_MS).toInt()
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

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
    @SerializedName("reputation") val reputation: TradeReputation? = null,
    /** Il livello: bronze | silver | gold | platinum, o null (fase 2e). */
    @SerializedName("tier") val tier: String? = null,
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
    /** Sospeso: niente match finche' dura (fase 2f). */
    @SerializedName("suspended") val suspended: Boolean? = null,
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
    @SerializedName("tradesDone") val tradesDone: Int? = null,
    @SerializedName("reputation") val reputation: TradeReputation? = null,
    @SerializedName("tier") val tier: String? = null
)

/** Una proposta vista da me: [give] e' cio' che do, [take] cio' che ricevo. */
data class TradeProposal(
    @SerializedName("id") val id: String? = null,
    /** "open" | "accepted" | "scheduled" | "done" | "no_show" | "expired" | "declined" | "cancelled" */
    @SerializedName("status") val status: String? = null,
    /** Da 2 in su e' una controproposta. */
    @SerializedName("revision") val revision: Int? = null,
    @SerializedName("myTurn") val myTurn: Boolean? = null,
    /** Serve una mia mossa: rispondere, o confermare l'appuntamento. */
    @SerializedName("actionNeeded") val actionNeeded: Boolean? = null,
    /** Chi ha gia' segnato "Scambio fatto" (fase 2c). */
    @SerializedName("doneByMe") val doneByMe: Boolean? = null,
    @SerializedName("doneByOther") val doneByOther: Boolean? = null,
    /** Chiuso da solo 7 giorni dopo l'appuntamento: uno solo dei due l'aveva segnato fatto. */
    @SerializedName("autoClosed") val autoClosed: Boolean? = null,
    /** Mi hanno segnalato come assente e posso ancora rispondere "Io c'ero" (fino a [disputeUntil], ms). */
    @SerializedName("canDispute") val canDispute: Boolean? = null,
    @SerializedName("disputeUntil") val disputeUntil: Long? = null,
    /** La segnalazione di assenza e' stata contestata. */
    @SerializedName("noShowDisputed") val noShowDisputed: Boolean? = null,
    @SerializedName("closedAt") val closedAt: Long? = null,
    @SerializedName("myRating") val myRating: TradeRating? = null,
    /** Il voto dell'altro: null finche' non ho votato anch'io (o non passano 7 giorni). */
    @SerializedName("theirRating") val theirRating: TradeRating? = null,
    /** Il luogo dell'appuntamento l'ho gia' votato. */
    @SerializedName("spotVoted") val spotVoted: Boolean? = null,
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
    @SerializedName("pending") val pending: Boolean? = null,
    /** Scambi chiusi qui, e i badge votati da almeno tre persone (tournaments | comics | card_shop). */
    @SerializedName("trades") val trades: Int? = null,
    @SerializedName("badges") val badges: List<String>? = null
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

// ── Chiusura e feedback (fase 2c) ───────────────────────────────────────────

/** Un voto: mood good | ok | bad, chip come "punctual" o "late". */
data class TradeRating(
    @SerializedName("mood") val mood: String? = null,
    @SerializedName("tags") val tags: List<String>? = null
)

data class TradeTagCount(
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("count") val count: Int? = null
)

/** I voti ricevuti gia' visibili, e i chip positivi piu' ricevuti (i negativi non si mostrano). */
data class TradeReputation(
    @SerializedName("good") val good: Int? = null,
    @SerializedName("ok") val ok: Int? = null,
    @SerializedName("bad") val bad: Int? = null,
    @SerializedName("topTags") val topTags: List<TradeTagCount>? = null
)

/** POST /v1/trade/proposals/:id/rate */
data class TradeRateRequest(
    @SerializedName("mood") val mood: String,
    @SerializedName("tags") val tags: List<String>
)

/** POST /v1/trade/proposals/:id/spotvote: tournaments | comics | card_shop */
data class TradeSpotVoteRequest(
    @SerializedName("tags") val tags: List<String>
)

// ── Classifica (fase 2e) ────────────────────────────────────────────────────

data class TradeLeaderboardEntry(
    @SerializedName("rank") val rank: Int? = null,
    /** L'id pubblico, per segnalare o bloccare dal mini profilo. */
    @SerializedName("id") val id: String? = null,
    @SerializedName("nickname") val nickname: String? = null,
    /** bronze | silver | gold | platinum, o null */
    @SerializedName("tier") val tier: String? = null,
    @SerializedName("trades") val trades: Int? = null,
    @SerializedName("positivePct") val positivePct: Int? = null,
    @SerializedName("memberSince") val memberSince: Long? = null,
    @SerializedName("isMe") val isMe: Boolean? = null,
    /** Per il mini profilo: persone diverse, voti visibili, chip piu' ricevuti. */
    @SerializedName("partners") val partners: Int? = null,
    @SerializedName("good") val good: Int? = null,
    @SerializedName("ok") val ok: Int? = null,
    @SerializedName("bad") val bad: Int? = null,
    @SerializedName("topTags") val topTags: List<TradeTagCount>? = null,
    /** Il Pokemon scelto per il podio, null = l'iniziale. */
    @SerializedName("avatar") val avatar: Int? = null,
    @SerializedName("avatarAnimated") val avatarAnimated: Boolean? = null
)

/** Io: posizione (null se fuori), adesione (null = mai chiesto) e cosa manca per entrare. */
data class TradeLeaderboardMe(
    @SerializedName("rank") val rank: Int? = null,
    @SerializedName("optIn") val optIn: Boolean? = null,
    @SerializedName("eligible") val eligible: Boolean? = null,
    @SerializedName("trades") val trades: Int? = null,
    @SerializedName("partners") val partners: Int? = null,
    @SerializedName("positivePct") val positivePct: Int? = null,
    @SerializedName("tier") val tier: String? = null,
    @SerializedName("missingTrades") val missingTrades: Int? = null,
    @SerializedName("missingPartners") val missingPartners: Int? = null
)

/** GET /v1/trade/leaderboard?scope=zone|italy */
data class TradeLeaderboardPayload(
    @SerializedName("scope") val scope: String? = null,
    @SerializedName("entries") val entries: List<TradeLeaderboardEntry>? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("me") val me: TradeLeaderboardMe? = null
)

/** PUT /v1/trade/avatar: avatar null = torna l'iniziale. */
data class TradeAvatarRequest(
    @SerializedName("avatar") val avatar: Int?,
    @SerializedName("animated") val animated: Boolean
)

/** PUT /v1/trade/leaderboard/optin */
data class TradeOptInRequest(
    @SerializedName("optIn") val optIn: Boolean
)

// ── Segnala e blocca (fase 2f) ──────────────────────────────────────────────

/** POST /v1/trade/users/:id/report. reason: behavior | scam | fake_cards | nickname | other */
data class TradeReportRequest(
    @SerializedName("reason") val reason: String,
    @SerializedName("note") val note: String,
    @SerializedName("proposalId") val proposalId: String?,
    @SerializedName("block") val block: Boolean
)

data class TradeBlockedUser(
    @SerializedName("id") val id: String? = null,
    @SerializedName("nickname") val nickname: String? = null,
    @SerializedName("blockedAt") val blockedAt: Long? = null
)

/** GET /v1/trade/blocks */
data class TradeBlocksPayload(
    @SerializedName("items") val items: List<TradeBlockedUser>? = null
)

// ── Notifiche (fase 3) ──────────────────────────────────────────────────────

/** wants null = mai chiesto: niente avvisi sulle carte cercate finche' non si risponde. */
data class TradeNotifyPrefs(
    @SerializedName("proposals") val proposals: Boolean? = null,
    @SerializedName("meetings") val meetings: Boolean? = null,
    @SerializedName("reminders") val reminders: Boolean? = null,
    @SerializedName("after") val after: Boolean? = null,
    @SerializedName("wants") val wants: Boolean? = null
)

/** PUT /v1/trade/push */
data class TradePushRequest(
    @SerializedName("token") val token: String,
    @SerializedName("lang") val lang: String
)
