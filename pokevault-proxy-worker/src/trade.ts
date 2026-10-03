/**
 * TradeRadar: scambi di carte fra utenti vicini.
 *
 * In sviluppo, e la produzione non deve accorgersene. Per questo il modulo si
 * accende solo con TRADE_ENABLED = "1", che oggi esiste soltanto nell'ambiente
 * staging di wrangler.toml (Worker pokevault-trade-staging, D1 suo). Sul
 * Worker di produzione la variabile non c'e': handleTradeRequest restituisce
 * null e /v1/trade/* finisce dove finiva prima, nel 404 di handleV1ApiRequest.
 *
 * Il database e' `trade_db`, separato dal catalogo: i dati degli scambi sono
 * dati di utenti. Il catalogo (`pokevault_catalog`) si legge soltanto, per
 * sapere quante carte ha ogni set. Le migrazioni stanno in schema-trade/.
 *
 * La carta si identifica con card_key = "<codice set>:<numero>" (me02:1):
 * vedi schema-trade/002_fondamenta.sql e TradeCardKey nell'app.
 */

import { romeNow, slotTime, romeToEpoch, addDays, type Slot } from './trade-time';
import { enqueue, deliver, prefsOf, type Outgoing, type PushEnv } from './trade-push';
import { activeGiftUntilMs, isEntitled, verifyFirebaseIdToken } from './billing';
import { GEOHASH5_REGEX, cellAndNeighbors, cellCenter, encode } from './geohash';

export interface TradeEnv extends PushEnv {
  /** "1" accende il modulo. Assente in produzione. */
  TRADE_ENABLED?: string;
  /** D1 degli scambi (staging: pokevault-trade-staging). */
  trade_db?: D1Database;
  /** Catalogo, in sola lettura: dimensione dei set. */
  pokevault_catalog?: D1Database;
  /** Progetto Firebase contro cui si verifica l'ID token. */
  FIREBASE_PROJECT_ID?: string;
  CACHE?: KVNamespace;
  /**
   * Minuti per cui la classifica calcolata al bisogno resta buona. Assente
   * (produzione) = 24 ore: la rifa' il cron ogni notte, e al bisogno si
   * ricalcola solo se quel giro e' saltato. Lo staging mette "10" per le prove.
   */
  TRADE_LEADERBOARD_TTL_MIN?: string;
  /**
   * "0" = il server non controlla l'abbonamento per gli avatar Premium. Solo
   * staging: i suoi account di prova non hanno mai un abbonamento vero.
   */
  TRADE_AVATAR_PREMIUM_CHECK?: string;
}

// ── Limiti ──────────────────────────────────────────────────────────────────

const MAX_HAVES = 2000;
const MAX_WANTS = 5000;
const MAX_OWNED = 20000;
/** Oltre questa quota di un set posseduta, le carte mancanti sono "cercate". */
const NEAR_COMPLETE_RATIO = 0.5;
/** Profili vicini esaminati per richiesta, i piu' recenti per primi. */
const MAX_CANDIDATES = 1000;
/** Persone restituite, le migliori per punteggio (vedi matchScore). */
const MAX_MATCHES = 100;
/** Righe della vista per carta, e persone elencate per ogni carta. */
const MAX_CARDS = 300;
const MAX_HOLDERS_PER_CARD = 50;
/** Carte per lato mostrate per ogni vicino. */
const MAX_ITEMS_PER_SIDE = 60;
/**
 * D1 accetta al massimo 100 parametri per istruzione: le righe per INSERT si
 * ricavano dalle colonne. Un numero fisso (14) andava bene con 7 colonne e ha
 * rotto trade_haves quando ne ha avute 9 (schema 3): 126 parametri, errore 500
 * appena si offrivano piu' di 11 carte.
 */
const MAX_PARAMS_PER_STATEMENT = 100;

const CARD_KEY_REGEX = /^[a-z0-9-]{1,20}:[A-Za-z0-9_]{1,20}$/;
const NICKNAME_REGEX = /^[\p{L}\p{N}_.\- ]{3,20}$/u;

type Level = 'wanted' | 'useful' | 'possible';
const LEVEL_RANK: Record<Level, number> = { wanted: 3, useful: 2, possible: 1 };

// ── Utilita' ────────────────────────────────────────────────────────────────

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    // Tutto qui e' per utente o cambia di continuo: mai in cache.
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
  });
}

function bearerToken(request: Request): string {
  const header = request.headers.get('authorization') ?? '';
  return header.toLowerCase().startsWith('bearer ') ? header.slice(7).trim() : '';
}

async function readJson<T>(request: Request): Promise<T | null> {
  try {
    return (await request.json()) as T;
  } catch {
    return null;
  }
}

function setCodeOf(cardKey: string): string {
  return cardKey.slice(0, cardKey.indexOf(':'));
}

function cleanText(value: unknown, max: number): string {
  return typeof value === 'string' ? value.trim().slice(0, max) : '';
}

/** Versione dello schema scritta dall'ultima migrazione di schema-trade/, o null. */
async function schemaVersion(db: D1Database): Promise<number | null> {
  try {
    const row = await db
      .prepare(`SELECT value FROM trade_meta WHERE key = 'schema_version'`)
      .first<{ value: string }>();
    return row ? Number(row.value) : null;
  } catch {
    return null;
  }
}

/**
 * Sostituisce in blocco le righe di un utente in una tabella: DELETE e INSERT
 * a gruppi nello stesso batch, cosi' chi legge non vede mai la lista a meta'.
 */
async function replaceRows(
  db: D1Database,
  table: string,
  uid: string,
  columns: string[],
  rows: unknown[][]
): Promise<void> {
  const statements: D1PreparedStatement[] = [db.prepare(`DELETE FROM ${table} WHERE uid = ?`).bind(uid)];
  const placeholders = `(${columns.map(() => '?').join(', ')})`;
  const rowsPerInsert = Math.floor(MAX_PARAMS_PER_STATEMENT / columns.length);
  for (let i = 0; i < rows.length; i += rowsPerInsert) {
    const chunk = rows.slice(i, i + rowsPerInsert);
    statements.push(
      db
        .prepare(`INSERT OR REPLACE INTO ${table} (${columns.join(', ')}) VALUES ${chunk.map(() => placeholders).join(', ')}`)
        .bind(...chunk.flat())
    );
  }
  await db.batch(statements);
}

// ── Dimensione dei set, dal catalogo ────────────────────────────────────────

let setSizesCache: { at: number; sizes: Map<string, number> } | null = null;
const SET_SIZES_TTL_MS = 6 * 60 * 60 * 1000;

/** Carte pubblicate per codice set (me02 -> 130), letta dal catalogo e tenuta 6 ore. */
async function catalogSetSizes(env: TradeEnv): Promise<Map<string, number>> {
  if (setSizesCache && Date.now() - setSizesCache.at < SET_SIZES_TTL_MS) return setSizesCache.sizes;
  const sizes = new Map<string, number>();
  const catalog = env.pokevault_catalog;
  if (!catalog) return sizes;
  const { results } = await catalog
    .prepare(
      `SELECT LOWER(SUBSTR(c.card_id, 1, INSTR(c.card_id, '_IT_') - 1)) AS code, COUNT(*) AS n
       FROM cards c JOIN expansions e ON e.id = c.expansion_id
       WHERE e.published = 1 AND INSTR(c.card_id, '_IT_') > 0
       GROUP BY code`
    )
    .all<{ code: string; n: number }>();
  for (const row of results) sizes.set(row.code, Number(row.n));
  setSizesCache = { at: Date.now(), sizes };
  return sizes;
}

interface CardLabel {
  name: string;
  setName: string;
}

let cardLabelsCache: { at: number; labels: Map<string, CardLabel> } | null = null;

/**
 * La chiave di una carta del catalogo, ricavata dal card_id come fa l'app
 * (ItalianCatalogNormalizer.toImageReference): ME02_IT_001.png -> me02:1.
 */
function cardKeyOfCatalogId(cardId: string): string | null {
  const match = /^([A-Za-z0-9-]+)_IT_([A-Za-z0-9_]+)\.(png|webp|jpe?g)$/i.exec(cardId.trim());
  if (!match) return null;
  const number = /^\d+$/.test(match[2]) ? String(parseInt(match[2], 10)) : match[2];
  return `${match[1].toLowerCase()}:${number}`;
}

/**
 * Nome italiano della carta e del suo set, per chiave, tenuti 6 ore come le
 * dimensioni dei set. Il server degli scambi conosce solo le chiavi: senza
 * questi l'app mostrerebbe "SWSH9 · 10" dove serve "Charizard".
 */
async function catalogCardLabels(env: TradeEnv): Promise<Map<string, CardLabel>> {
  if (cardLabelsCache && Date.now() - cardLabelsCache.at < SET_SIZES_TTL_MS) return cardLabelsCache.labels;
  const labels = new Map<string, CardLabel>();
  const catalog = env.pokevault_catalog;
  if (!catalog) return labels;
  const { results } = await catalog
    .prepare(
      `SELECT c.card_id AS card_id, c.nome AS nome, COALESCE(e.name, e.id) AS set_name
       FROM cards c JOIN expansions e ON e.id = c.expansion_id
       WHERE e.published = 1 AND INSTR(c.card_id, '_IT_') > 0`
    )
    .all<{ card_id: string; nome: string; set_name: string }>();
  for (const row of results) {
    const key = cardKeyOfCatalogId(row.card_id);
    if (key) labels.set(key, { name: row.nome, setName: row.set_name });
  }
  cardLabelsCache = { at: Date.now(), labels };
  return labels;
}

// ── Profilo ─────────────────────────────────────────────────────────────────

interface ProfileRow {
  uid: string;
  nickname: string;
  geohash5: string;
  paused: number;
  owned_hash: string | null;
  trades_done: number;
  created_at: number;
  /** 0 = no, 1 = si' (dallo schema 2, NOT NULL DEFAULT 0). */
  leaderboard_opt_in: number;
  /** Quando ha risposto alla domanda sulla classifica; NULL = mai chiesto (schema 8). */
  leaderboard_asked_at: number | null;
  /** Numero di Pokedex dell'avatar (1-1025), NULL = l'iniziale (schema 9). */
  avatar: number | null;
  /** 1 = sprite animato (Premium, fino al 649). */
  avatar_animated: number;
  /** Fino a quando e' fuori dai match (ms), e perche': no_show | reports | admin (schema 10). */
  suspended_until: number | null;
  suspension_reason: string | null;
  /** Le categorie di notifiche, in JSON (schema 11). */
  notify_prefs: string | null;
}

async function loadProfile(db: D1Database, uid: string): Promise<ProfileRow | null> {
  return db
    .prepare(
      `SELECT uid, nickname, geohash5, paused, owned_hash, trades_done, created_at, leaderboard_opt_in, leaderboard_asked_at,
              avatar, avatar_animated, suspended_until, suspension_reason, notify_prefs
       FROM trade_profiles WHERE uid = ?`
    )
    .bind(uid)
    .first<ProfileRow>();
}

function profileJson(row: ProfileRow, counts?: { haves: number; wants: number; owned: number }) {
  return {
    nickname: row.nickname,
    geohash5: row.geohash5,
    paused: row.paused === 1,
    ownedHash: row.owned_hash,
    tradesDone: row.trades_done,
    memberSince: row.created_at,
    avatar: row.avatar,
    avatarAnimated: row.avatar_animated === 1,
    // Chi e' sospeso lo deve sapere, e perche': altrimenti vede solo un radar vuoto.
    suspendedUntil: (row.suspended_until ?? 0) > Date.now() ? row.suspended_until : null,
    suspensionReason: (row.suspended_until ?? 0) > Date.now() ? row.suspension_reason : null,
    notify: prefsOf(row.notify_prefs),
    ...(counts ?? {}),
  };
}

async function putProfile(request: Request, db: D1Database, uid: string): Promise<Response> {
  const body = await readJson<{
    nickname?: string;
    geohash5?: string;
    adultConfirmed?: boolean;
    collectionConsent?: boolean;
    paused?: boolean;
  }>(request);
  if (!body) return json({ error: 'bad_json' }, 400);

  const nickname = cleanText(body.nickname, 20);
  const geohash5 = cleanText(body.geohash5, 5).toLowerCase();
  if (!NICKNAME_REGEX.test(nickname)) return json({ error: 'bad_nickname' }, 400);
  if (!GEOHASH5_REGEX.test(geohash5)) return json({ error: 'bad_geohash' }, 400);
  // Le due conferme sono condizione per esistere: senza, niente profilo.
  if (body.adultConfirmed !== true) return json({ error: 'adult_required' }, 400);
  if (body.collectionConsent !== true) return json({ error: 'consent_required' }, 400);

  const now = Date.now();
  await db
    .prepare(
      `INSERT INTO trade_profiles
         (uid, nickname, geohash5, adult_confirmed_at, collection_consent_at, paused, created_at, updated_at, public_id)
       VALUES (?1, ?2, ?3, ?4, ?4, ?5, ?4, ?4, lower(hex(randomblob(8))))
       ON CONFLICT(uid) DO UPDATE SET
         nickname = excluded.nickname,
         geohash5 = excluded.geohash5,
         paused = excluded.paused,
         updated_at = excluded.updated_at`
    )
    .bind(uid, nickname, geohash5, now, body.paused === true ? 1 : 0)
    .run();
  // Chi ha disattivato il profilo mentre era sospeso ritrova la sospensione.
  await db.batch([
    db
      .prepare(
        `UPDATE trade_profiles
         SET suspended_until = (SELECT suspended_until FROM trade_sanctions WHERE uid = ?1),
             suspension_reason = (SELECT reason FROM trade_sanctions WHERE uid = ?1)
         WHERE uid = ?1 AND EXISTS (SELECT 1 FROM trade_sanctions WHERE uid = ?1 AND suspended_until > ?2)`
      )
      .bind(uid, now),
    db.prepare(`DELETE FROM trade_sanctions WHERE uid = ?`).bind(uid),
  ]);

  const row = await loadProfile(db, uid);
  return json(row ? profileJson(row) : {});
}

async function getProfile(db: D1Database, uid: string, env: TradeEnv): Promise<Response> {
  const row = await loadProfile(db, uid);
  if (!row) return json({ error: 'no_profile' }, 404);
  const counts = await db
    .prepare(
      `SELECT (SELECT COALESCE(SUM(qty), 0) FROM trade_haves WHERE uid = ?1) AS haves,
              (SELECT COUNT(*) FROM trade_wants WHERE uid = ?1) AS wants,
              (SELECT COUNT(*) FROM trade_owned WHERE uid = ?1) AS owned`
    )
    .bind(uid)
    .first<{ haves: number; wants: number; owned: number }>();
  const reputation = (await reputationOf(db, '?', [uid])).get(uid) ?? null;
  await refreshLeaderboard(db, leaderboardTtlMs(env));
  const tier = (await tiersOf(db, '?', [uid])).get(uid) ?? null;
  return json({
    ...profileJson(row, counts ?? { haves: 0, wants: 0, owned: 0 }),
    reputation,
    tier,
    leaderboardOptIn: row.leaderboard_asked_at === null ? null : row.leaderboard_opt_in === 1,
  });
}

/**
 * Disattivazione: via tutto quello che il server sa dell'utente, tranne
 * quello che serve alla sicurezza degli altri: una sospensione in corso
 * (torna se riattiva, in trade_sanctions), le segnalazioni fatte e
 * ricevute, e i blocchi che altri hanno messo su di lui.
 */
async function deleteProfile(db: D1Database, uid: string): Promise<Response> {
  const now = Date.now();
  await db.batch([
    db
      .prepare(
        `INSERT OR REPLACE INTO trade_sanctions (uid, suspended_until, reason, created_at)
         SELECT uid, suspended_until, suspension_reason, ?2 FROM trade_profiles WHERE uid = ?1 AND suspended_until > ?2`
      )
      .bind(uid, now),
    db.prepare(`DELETE FROM trade_blocks WHERE blocker_uid = ?`).bind(uid),
    db.prepare(`DELETE FROM trade_push_tokens WHERE uid = ?`).bind(uid),
    db.prepare(`DELETE FROM trade_notifications WHERE uid = ?`).bind(uid),
    db.prepare(`DELETE FROM trade_wants_seen WHERE uid = ?1 OR holder_uid = ?1`).bind(uid),
    // I luoghi segnalati: quelli approvati restano (sono pubblici) ma senza
    // legame con chi li ha segnalati; quelli ancora in verifica se ne vanno.
    db.prepare(`DELETE FROM trade_spots WHERE added_by = ? AND approved = 0`).bind(uid),
    db.prepare(`UPDATE trade_spots SET added_by = NULL WHERE added_by = ?`).bind(uid),
    // Le proposte se ne vanno con il profilo, anche per l'altra persona.
    db.prepare(
      `DELETE FROM trade_proposal_items WHERE proposal_id IN
         (SELECT id FROM trade_proposals WHERE from_uid = ?1 OR to_uid = ?1)`
    ).bind(uid),
    db.prepare(`DELETE FROM trade_proposals WHERE from_uid = ?1 OR to_uid = ?1`).bind(uid),
    db.prepare(`DELETE FROM trade_haves WHERE uid = ?`).bind(uid),
    db.prepare(`DELETE FROM trade_wants WHERE uid = ?`).bind(uid),
    db.prepare(`DELETE FROM trade_owned WHERE uid = ?`).bind(uid),
    db.prepare(`DELETE FROM trade_profiles WHERE uid = ?`).bind(uid),
  ]);
  return json({ deleted: true });
}

// ── Liste ───────────────────────────────────────────────────────────────────

async function putHaves(request: Request, db: D1Database, uid: string): Promise<Response> {
  const body = await readJson<{ items?: Array<Record<string, unknown>> }>(request);
  const items = Array.isArray(body?.items) ? body!.items : null;
  if (!items) return json({ error: 'bad_json' }, 400);
  if (items.length > MAX_HAVES) return json({ error: 'too_many', max: MAX_HAVES }, 413);

  const rows: unknown[][] = [];
  for (const item of items) {
    const key = cleanText(item.key, 41);
    const qty = Number(item.qty);
    if (!CARD_KEY_REGEX.test(key) || !Number.isInteger(qty) || qty < 1 || qty > 99) {
      return json({ error: 'bad_item', key }, 400);
    }
    // Le carte a mano partono senza avvisi; i doppioni li hanno sempre (schema 3).
    const manual = item.manual === true;
    const notify = manual ? item.notify === true : true;
    rows.push([
      uid, key, setCodeOf(key),
      cleanText(item.variant, 30), cleanText(item.condition, 30), cleanText(item.language, 30),
      qty, manual ? 1 : 0, notify ? 1 : 0,
    ]);
  }
  await replaceRows(
    db, 'trade_haves', uid,
    ['uid', 'card_key', 'set_code', 'variant', 'condition', 'language', 'qty', 'manual', 'notify'],
    rows
  );
  return json({ haves: rows.length });
}

async function getHaves(db: D1Database, uid: string): Promise<Response> {
  const { results } = await db
    .prepare(`SELECT card_key, variant, condition, language, qty, manual, notify FROM trade_haves WHERE uid = ?`)
    .bind(uid)
    .all<{ card_key: string; variant: string; condition: string; language: string; qty: number; manual: number; notify: number }>();
  const reserved = await reservedOf(db, uid);
  return json({
    items: results.map((r) => ({
      key: r.card_key, variant: r.variant, condition: r.condition, language: r.language, qty: r.qty,
      manual: r.manual === 1, notify: r.notify === 1,
      // Copie promesse in un accordo: restano in lista, ma gli altri non le vedono.
      reserved: reserved.get(itemId({ key: r.card_key, ...r })) ?? 0,
    })),
  });
}

async function putWants(request: Request, db: D1Database, uid: string): Promise<Response> {
  const body = await readJson<{ items?: Array<Record<string, unknown>> }>(request);
  const items = Array.isArray(body?.items) ? body!.items : null;
  if (!items) return json({ error: 'bad_json' }, 400);
  if (items.length > MAX_WANTS) return json({ error: 'too_many', max: MAX_WANTS }, 413);

  const seen = new Set<string>();
  const rows: unknown[][] = [];
  for (const item of items) {
    const key = cleanText(item.key, 41);
    const source = item.source === 'album' ? 'album' : 'wishlist';
    const priority = item.priority === 'need' ? 'need' : 'nice';
    if (!CARD_KEY_REGEX.test(key)) return json({ error: 'bad_item', key }, 400);
    if (seen.has(key)) continue;
    seen.add(key);
    rows.push([uid, key, source, priority]);
  }
  await replaceRows(db, 'trade_wants', uid, ['uid', 'card_key', 'source', 'priority'], rows);
  return json({ wants: rows.length });
}

async function putOwned(request: Request, db: D1Database, uid: string): Promise<Response> {
  const body = await readJson<{ keys?: unknown[]; hash?: string }>(request);
  const keys = Array.isArray(body?.keys) ? body!.keys : null;
  if (!keys) return json({ error: 'bad_json' }, 400);
  if (keys.length > MAX_OWNED) return json({ error: 'too_many', max: MAX_OWNED }, 413);

  const unique = new Set<string>();
  for (const raw of keys) {
    const key = cleanText(raw, 41);
    if (!CARD_KEY_REGEX.test(key)) return json({ error: 'bad_item', key }, 400);
    unique.add(key);
  }
  const rows = [...unique].map((key) => [uid, key, setCodeOf(key)]);
  await replaceRows(db, 'trade_owned', uid, ['uid', 'card_key', 'set_code'], rows);
  await db
    .prepare(`UPDATE trade_profiles SET owned_hash = ?, updated_at = ? WHERE uid = ?`)
    .bind(cleanText(body?.hash, 64) || null, Date.now(), uid)
    .run();
  return json({ owned: rows.length });
}

// ── Match ───────────────────────────────────────────────────────────────────

interface MatchItem {
  key: string;
  variant: string;
  condition: string;
  language: string;
  qty: number;
  level: Level;
  /** Perche' e' cercata: 'wishlist' | 'album' | 'set', o null. */
  reason: string | null;
  /** Dal catalogo; assenti se la chiave non c'e'. */
  name?: string;
  setName?: string;
  /** Solo con reason 'set': quante carte del set ha chi la riceve, su quante. */
  setOwned?: number;
  setSize?: number;
}

interface Wishes {
  /** Carte cercate esplicitamente, con la fonte. */
  explicit: Map<string, string>;
  owned: Set<string>;
  /** Set con almeno una carta posseduta. */
  collecting: Set<string>;
  /** Set oltre NEAR_COMPLETE_RATIO: ogni loro carta mancante e' cercata. */
  nearComplete: Set<string>;
  /** Per i set di nearComplete: [possedute, totale]. */
  progress: Map<string, [number, number]>;
}

function classify(
  key: string,
  wishes: Wishes
): { level: Level; reason: string | null; setOwned?: number; setSize?: number } | null {
  if (wishes.owned.has(key)) return null;
  const explicit = wishes.explicit.get(key);
  if (explicit) return { level: 'wanted', reason: explicit };
  const set = setCodeOf(key);
  if (wishes.nearComplete.has(set)) {
    const [setOwned, setSize] = wishes.progress.get(set) ?? [0, 0];
    return { level: 'wanted', reason: 'set', setOwned, setSize };
  }
  if (wishes.collecting.has(set)) return { level: 'useful', reason: null };
  return { level: 'possible', reason: null };
}

function emptyWishes(): Wishes {
  return { explicit: new Map(), owned: new Set(), collecting: new Set(), nearComplete: new Set(), progress: new Map() };
}

/** Un set con [count] carte possedute: lo si colleziona, e oltre la soglia ogni mancante e' cercata. */
function addSetCount(wishes: Wishes, set: string, count: number, setSizes: Map<string, number>): void {
  wishes.collecting.add(set);
  const size = setSizes.get(set) ?? 0;
  if (size > 0 && count / size >= NEAR_COMPLETE_RATIO && count < size) {
    wishes.nearComplete.add(set);
    wishes.progress.set(set, [count, size]);
  }
}

/**
 * Cosa cerco io: wishlist, album e quante carte ho per set. Le singole carte
 * possedute qui non servono: quelle che ho gia' le scarta in SQL la query
 * delle offerte dei vicini.
 */
async function loadMyWishes(db: D1Database, uid: string, setSizes: Map<string, number>): Promise<Wishes> {
  const wishes = emptyWishes();
  const wants = await db
    .prepare(`SELECT card_key, source FROM trade_wants WHERE uid = ?`)
    .bind(uid)
    .all<{ card_key: string; source: string }>();
  for (const row of wants.results) wishes.explicit.set(row.card_key, row.source);
  const sets = await db
    .prepare(`SELECT set_code, COUNT(*) AS n FROM trade_owned WHERE uid = ? GROUP BY set_code`)
    .bind(uid)
    .all<{ set_code: string; n: number }>();
  for (const row of sets.results) addSetCount(wishes, row.set_code, Number(row.n), setSizes);
  return wishes;
}

/**
 * I vicini, come sottoquery da mettere dentro `uid IN (...)`. Cosi' i loro id
 * non passano mai come parametri: D1 ne accetta 100 per istruzione, e con
 * cento persone in zona un `IN (?, ?, ...)` non starebbe piu' in piedi.
 */
interface Nearby {
  sql: string;
  params: string[];
}

function nearbyOf(cells: string[], uid: string): Nearby {
  return {
    sql: `SELECT uid FROM trade_profiles
          WHERE geohash5 IN (${cells.map(() => '?').join(', ')}) AND uid <> ? AND paused = 0
            AND (suspended_until IS NULL OR suspended_until < ${Date.now()})
            AND uid NOT IN (SELECT blocked_uid FROM trade_blocks WHERE blocker_uid = ?)
            AND uid NOT IN (SELECT blocker_uid FROM trade_blocks WHERE blocked_uid = ?)
          ORDER BY updated_at DESC LIMIT ${MAX_CANDIDATES}`,
    params: [...cells, uid, uid, uid],
  };
}

/**
 * Quanto interessano ai vicini le MIE carte: per ognuno solo cio' che tocca
 * le chiavi che offro (cercate, gia' possedute, set collezionati). Le loro
 * liste intere non servono e con molte persone sarebbero troppe righe.
 */
async function loadTheirWishes(
  db: D1Database,
  nearby: Nearby,
  myKeys: string[],
  setSizes: Map<string, number>
): Promise<Map<string, Wishes>> {
  const byUid = new Map<string, Wishes>();
  const wishesOf = (uid: string): Wishes => {
    let wishes = byUid.get(uid);
    if (!wishes) {
      wishes = emptyWishes();
      byUid.set(uid, wishes);
    }
    return wishes;
  };
  if (myKeys.length === 0) return byUid;
  const keysJson = JSON.stringify(myKeys);
  const setsJson = JSON.stringify([...new Set(myKeys.map(setCodeOf))]);

  const wants = await db
    .prepare(
      `SELECT uid, card_key, source FROM trade_wants
       WHERE uid IN (${nearby.sql}) AND card_key IN (SELECT value FROM json_each(?))`
    )
    .bind(...nearby.params, keysJson)
    .all<{ uid: string; card_key: string; source: string }>();
  for (const row of wants.results) wishesOf(row.uid).explicit.set(row.card_key, row.source);

  const owned = await db
    .prepare(
      `SELECT uid, card_key FROM trade_owned
       WHERE uid IN (${nearby.sql}) AND card_key IN (SELECT value FROM json_each(?))`
    )
    .bind(...nearby.params, keysJson)
    .all<{ uid: string; card_key: string }>();
  for (const row of owned.results) wishesOf(row.uid).owned.add(row.card_key);

  const sets = await db
    .prepare(
      `SELECT uid, set_code, COUNT(*) AS n FROM trade_owned
       WHERE uid IN (${nearby.sql}) AND set_code IN (SELECT value FROM json_each(?))
       GROUP BY uid, set_code`
    )
    .bind(...nearby.params, setsJson)
    .all<{ uid: string; set_code: string; n: number }>();
  for (const row of sets.results) addSetCount(wishesOf(row.uid), row.set_code, Number(row.n), setSizes);

  return byUid;
}

function bestLevel(items: MatchItem[]): Level | null {
  let best: Level | null = null;
  for (const item of items) {
    if (!best || LEVEL_RANK[item.level] > LEVEL_RANK[best]) best = item.level;
  }
  return best;
}

function sortItems(items: MatchItem[]): MatchItem[] {
  return items.sort((a, b) => LEVEL_RANK[b.level] - LEVEL_RANK[a.level] || a.key.localeCompare(b.key));
}

const LEVEL_WEIGHT: Record<Level, number> = { wanted: 100, useful: 30, possible: 5 };

/**
 * Quanto vale uno scambio, per decidere chi mostrare per primo: la
 * reciprocita' prima di tutto, poi quanto interessano le carte (quelle che
 * ricevi pesano il doppio di quelle che dai), poi la vicinanza. Le carte di
 * un lato contano fino a MAX_ITEMS_PER_SIDE, come quelle che si mostrano.
 */
function matchScore(theyGive: MatchItem[], iGive: MatchItem[], near: boolean): number {
  const sum = (items: MatchItem[]) =>
    items.slice(0, MAX_ITEMS_PER_SIDE).reduce((total, item) => total + LEVEL_WEIGHT[item.level], 0);
  return (iGive.length > 0 ? 1000 : 0) + sum(theyGive) + sum(iGive) / 2 + (near ? 20 : 0);
}

interface CardHolder {
  /** Posizione nella lista `matches` della stessa risposta. */
  match: number;
  qty: number;
  variant: string;
  condition: string;
  language: string;
}

interface CardOffer {
  key: string;
  name?: string;
  setName?: string;
  level: Level;
  reason: string | null;
  setOwned?: number;
  setSize?: number;
  /** Quante persone ce l'hanno; holders ne elenca al massimo MAX_HOLDERS_PER_CARD. */
  holderCount: number;
  holders: CardHolder[];
}

/**
 * La vista per carta: ogni carta che posso ricevere, con chi ce l'ha. Le
 * persone restano nell'ordine dei match, quindi i reciproci e i migliori per
 * primi; le carte vanno per livello e poi per quante persone le hanno.
 */
function cardsView(matches: Array<{ theyGive: MatchItem[] }>): CardOffer[] {
  const byKey = new Map<string, CardOffer>();
  matches.forEach((match, index) => {
    for (const item of match.theyGive) {
      let card = byKey.get(item.key);
      if (!card) {
        card = {
          key: item.key, name: item.name, setName: item.setName,
          level: item.level, reason: item.reason, setOwned: item.setOwned, setSize: item.setSize,
          holderCount: 0, holders: [],
        };
        byKey.set(item.key, card);
      }
      // Due stampe della stessa carta dalla stessa persona: una riga sola.
      if (card.holders.some((holder) => holder.match === index)) continue;
      card.holderCount += 1;
      card.holders.push({ match: index, qty: item.qty, variant: item.variant, condition: item.condition, language: item.language });
    }
  });
  return [...byKey.values()]
    .sort((a, b) =>
      LEVEL_RANK[b.level] - LEVEL_RANK[a.level] ||
      b.holderCount - a.holderCount ||
      (a.name ?? a.key).localeCompare(b.name ?? b.key)
    )
    .slice(0, MAX_CARDS)
    .map((card) => ({ ...card, holders: card.holders.slice(0, MAX_HOLDERS_PER_CARD) }));
}

async function getMatches(db: D1Database, uid: string, env: TradeEnv): Promise<Response> {
  const me = await loadProfile(db, uid);
  if (!me) return json({ error: 'no_profile' }, 404);
  if (me.paused === 1) return json({ paused: true, matches: [], cards: [] });
  if ((me.suspended_until ?? 0) > Date.now()) return json({ suspended: true, matches: [], cards: [] });

  const cells = cellAndNeighbors(me.geohash5);
  const nearby = nearbyOf(cells, uid);
  const neighborRows = await db
    .prepare(`SELECT uid, public_id, nickname, geohash5, trades_done, created_at FROM trade_profiles WHERE uid IN (${nearby.sql})`)
    .bind(...nearby.params)
    .all<{ uid: string; public_id: string; nickname: string; geohash5: string; trades_done: number; created_at: number }>();
  const neighbors = neighborRows.results;
  if (neighbors.length === 0) return json({ cells: cells.length, nearby: 0, matches: [], cards: [] });

  // Le loro offerte, meno le carte che ho gia'.
  const theirHaves = await db
    .prepare(
      `SELECT h.uid, h.card_key, h.variant, h.condition, h.language, h.qty FROM trade_haves h
       WHERE h.uid IN (${nearby.sql})
         AND NOT EXISTS (SELECT 1 FROM trade_owned o WHERE o.uid = ? AND o.card_key = h.card_key)`
    )
    .bind(...nearby.params, uid)
    .all<{ uid: string; card_key: string; variant: string; condition: string; language: string; qty: number }>();
  const reservedNearby = await reservedByUid(db, nearby.sql, nearby.params);
  const havesByUid = new Map<string, typeof theirHaves.results>();
  for (const row of theirHaves.results) {
    const qty = row.qty - (reservedNearby.get(row.uid)?.get(itemId({ key: row.card_key, ...row })) ?? 0);
    if (qty <= 0) continue;
    const list = havesByUid.get(row.uid) ?? [];
    list.push({ ...row, qty });
    havesByUid.set(row.uid, list);
  }

  const myReserved = await reservedOf(db, uid);
  const myHaves = (
    await db
      .prepare(`SELECT card_key, variant, condition, language, qty FROM trade_haves WHERE uid = ?`)
      .bind(uid)
      .all<{ card_key: string; variant: string; condition: string; language: string; qty: number }>()
  ).results
    .map((row) => ({ ...row, qty: row.qty - (myReserved.get(itemId({ key: row.card_key, ...row })) ?? 0) }))
    .filter((row) => row.qty > 0);

  const setSizes = await catalogSetSizes(env);
  const myWishes = await loadMyWishes(db, uid, setSizes);
  const theirWishes = await loadTheirWishes(db, nearby, [...new Set(myHaves.map((h) => h.card_key))], setSizes);

  const reputation = await reputationOf(db, nearby.sql, nearby.params);
  const tiers = await tiersOf(db, nearby.sql, nearby.params);
  const scored = [];
  for (const neighbor of neighbors) {
    const theyGive: MatchItem[] = [];
    for (const have of havesByUid.get(neighbor.uid) ?? []) {
      const verdict = classify(have.card_key, myWishes);
      if (verdict) theyGive.push({ key: have.card_key, variant: have.variant, condition: have.condition, language: have.language, qty: have.qty, ...verdict });
    }
    if (theyGive.length === 0) continue;

    const wishes = theirWishes.get(neighbor.uid) ?? emptyWishes();
    const iGive: MatchItem[] = [];
    for (const have of myHaves) {
      const verdict = classify(have.card_key, wishes);
      if (verdict) iGive.push({ key: have.card_key, variant: have.variant, condition: have.condition, language: have.language, qty: have.qty, ...verdict });
    }

    // Stessa cella: meno di ~5 km; cella accanto: meno di ~15 km.
    const near = neighbor.geohash5 === me.geohash5;
    sortItems(theyGive);
    sortItems(iGive);
    scored.push({
      score: matchScore(theyGive, iGive, near),
      // L'id pubblico: con questo l'app manda una proposta o legge le sue offerte.
      id: neighbor.public_id,
      nickname: neighbor.nickname,
      distance: near ? 'lt5' : 'lt15',
      tradesDone: neighbor.trades_done,
      memberSince: neighbor.created_at,
      reputation: reputation.get(neighbor.uid) ?? null,
      tier: tiers.get(neighbor.uid) ?? null,
      level: bestLevel(theyGive),
      mutual: iGive.length > 0,
      theyGive,
      iGive,
    });
  }

  scored.sort((a, b) => b.score - a.score);
  const top = scored.slice(0, MAX_MATCHES);

  const labels = await catalogCardLabels(env);
  for (const match of top) {
    for (const item of [...match.theyGive, ...match.iGive]) {
      const label = labels.get(item.key);
      if (label) {
        item.name = label.name;
        item.setName = label.setName;
      }
    }
  }

  // La vista per carta si fa sulle liste intere; le schede ne mostrano un tetto.
  const cards = cardsView(top);
  const matches = top.map(({ score: _score, ...match }) => ({
    ...match,
    theyGiveCount: match.theyGive.length,
    iGiveCount: match.iGive.length,
    theyGive: match.theyGive.slice(0, MAX_ITEMS_PER_SIDE),
    iGive: match.iGive.slice(0, MAX_ITEMS_PER_SIDE),
  }));
  return json({ cells: cells.length, nearby: neighbors.length, matches, cards });
}

// ── Proposte (fase 2a) ──────────────────────────────────────────────────────

/** Carte per lato in una proposta. */
const MAX_PROPOSAL_ITEMS = 30;
/** Proposte aperte mandate da una persona: oltre, si aspetta qualche risposta. */
const MAX_OPEN_SENT = 20;
/** Le proposte chiuse restano in elenco per questo tempo. */
const CLOSED_VISIBLE_MS = 30 * 24 * 60 * 60 * 1000;

interface ProposalItem {
  key: string;
  variant: string;
  condition: string;
  language: string;
  qty: number;
}

function itemId(item: { key: string; variant: string; condition: string; language: string }): string {
  return [item.key, item.variant, item.condition, item.language].join('|');
}

/**
 * Le carte di un lato, ripulite: chiave valida, quantita' da 1 a 99, le
 * righe uguali sommate. Null se qualcosa non va.
 */
function parseItems(raw: unknown): ProposalItem[] | null {
  if (!Array.isArray(raw) || raw.length > MAX_PROPOSAL_ITEMS) return null;
  const byId = new Map<string, ProposalItem>();
  for (const value of raw as Array<Record<string, unknown>>) {
    const key = cleanText(value?.key, 41);
    const qty = Number(value?.qty);
    if (!CARD_KEY_REGEX.test(key) || !Number.isInteger(qty) || qty < 1 || qty > 99) return null;
    const item = {
      key,
      variant: cleanText(value.variant, 30),
      condition: cleanText(value.condition, 30),
      language: cleanText(value.language, 30),
      qty,
    };
    const existing = byId.get(itemId(item));
    if (existing) existing.qty += qty;
    else byId.set(itemId(item), item);
  }
  return [...byId.values()];
}

/** Ogni carta che [giverUid] dovrebbe dare e' fra le sue offerte, nella quantita' chiesta? */
async function itemsAvailable(db: D1Database, giverUid: string, items: ProposalItem[]): Promise<boolean> {
  if (items.length === 0) return true;
  const { results } = await db
    .prepare(`SELECT card_key, variant, condition, language, qty FROM trade_haves WHERE uid = ?`)
    .bind(giverUid)
    .all<{ card_key: string; variant: string; condition: string; language: string; qty: number }>();
  const offered = new Map(results.map((r) => [itemId({ key: r.card_key, ...r }), r.qty]));
  const reserved = await reservedOf(db, giverUid);
  return items.every((item) => (offered.get(itemId(item)) ?? 0) - (reserved.get(itemId(item)) ?? 0) >= item.qty);
}

async function uidOfPublicId(db: D1Database, publicId: string): Promise<{ uid: string; paused: number } | null> {
  if (!/^[0-9a-f]{16}$/.test(publicId)) return null;
  return db
    // Un profilo sospeso (due "non si e' presentato") conta come in pausa.
    .prepare(`SELECT uid, CASE WHEN suspended_until > ${Date.now()} THEN 1 ELSE paused END AS paused FROM trade_profiles WHERE public_id = ?`)
    .bind(publicId)
    .first<{ uid: string; paused: number }>();
}

function itemStatements(db: D1Database, proposalId: string, revision: number, giverUid: string, items: ProposalItem[]) {
  return items.map((item) =>
    db
      .prepare(
        `INSERT INTO trade_proposal_items (proposal_id, revision, giver_uid, card_key, variant, condition, language, qty)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
      )
      .bind(proposalId, revision, giverUid, item.key, item.variant, item.condition, item.language, item.qty)
  );
}

/** GET /v1/trade/users/:publicId/haves — le carte che una persona offre, per comporre una proposta. */
async function getUserHaves(db: D1Database, uid: string, publicId: string, env: TradeEnv): Promise<Response> {
  const user = await uidOfPublicId(db, publicId);
  if (!user || user.paused === 1 || (await blockedBetween(db, uid, user.uid))) return json({ error: 'no_user' }, 404);
  const { results } = await db
    .prepare(`SELECT card_key, variant, condition, language, qty FROM trade_haves WHERE uid = ?`)
    .bind(user.uid)
    .all<{ card_key: string; variant: string; condition: string; language: string; qty: number }>();
  const labels = await catalogCardLabels(env);
  const reserved = await reservedOf(db, user.uid);
  return json({
    items: results
      .map((r) => ({ ...r, qty: r.qty - (reserved.get(itemId({ key: r.card_key, ...r })) ?? 0) }))
      .filter((r) => r.qty > 0)
      .map((r) => ({
        key: r.card_key, variant: r.variant, condition: r.condition, language: r.language, qty: r.qty,
        name: labels.get(r.card_key)?.name, setName: labels.get(r.card_key)?.setName,
      })),
  });
}

/**
 * POST /v1/trade/proposals — { to, give, take }, con give e take dal punto di
 * vista di chi manda. Almeno una carta per parte, tutte fra le offerte di chi
 * le da'. Fra due persone c'e' una sola proposta aperta alla volta.
 */
async function createProposal(request: Request, db: D1Database, uid: string, push: Pusher): Promise<Response> {
  const body = await readJson<{ to?: string; give?: unknown; take?: unknown }>(request);
  if (!body) return json({ error: 'bad_json' }, 400);
  if (await isSuspended(db, uid)) return json({ error: 'suspended' }, 403);
  const target = await uidOfPublicId(db, cleanText(body.to, 16));
  if (!target || target.paused === 1 || target.uid === uid) return json({ error: 'no_user' }, 404);
  // Un blocco non si rivela: la risposta e' quella di chi non c'e'.
  if (await blockedBetween(db, uid, target.uid)) return json({ error: 'no_user' }, 404);
  const give = parseItems(body.give);
  const take = parseItems(body.take);
  if (!give || !take || give.length === 0 || take.length === 0) return json({ error: 'bad_items' }, 400);

  const open = await db
    .prepare(
      `SELECT id FROM trade_proposals
       WHERE status = 'open' AND ((from_uid = ?1 AND to_uid = ?2) OR (from_uid = ?2 AND to_uid = ?1))`
    )
    .bind(uid, target.uid)
    .first<{ id: string }>();
  if (open) return json({ error: 'already_open', id: open.id }, 409);

  const sent = await db
    .prepare(`SELECT COUNT(*) AS n FROM trade_proposals WHERE from_uid = ? AND status = 'open'`)
    .bind(uid)
    .first<{ n: number }>();
  if ((sent?.n ?? 0) >= MAX_OPEN_SENT) return json({ error: 'too_many_open', max: MAX_OPEN_SENT }, 429);

  if (!(await itemsAvailable(db, uid, give)) || !(await itemsAvailable(db, target.uid, take))) {
    return json({ error: 'not_offered' }, 409);
  }

  const id = crypto.randomUUID();
  const now = Date.now();
  await db.batch([
    db
      .prepare(
        `INSERT INTO trade_proposals (id, from_uid, to_uid, status, revision, turn_uid, created_at, updated_at)
         VALUES (?, ?, ?, 'open', 1, ?, ?, ?)`
      )
      .bind(id, uid, target.uid, target.uid, now, now),
    ...itemStatements(db, id, 1, uid, give),
    ...itemStatements(db, id, 1, target.uid, take),
  ]);
  // Per chi la riceve: give e take sono visti da chi manda.
  await push([{
    id: `proposal:${id}:1:${target.uid}`, uid: target.uid, kind: 'proposals', template: 'proposal_new',
    args: { nick: await nicknameOf(db, uid), give: quantity(give), take: quantity(take) },
    data: { screen: 'proposals', proposalId: id }, tag: `proposal:${id}`,
  }]);
  return json({ id }, 201);
}

interface ProposalRow {
  id: string;
  from_uid: string;
  to_uid: string;
  status: string;
  revision: number;
  turn_uid: string;
  closed_by: string | null;
  created_at: number;
  updated_at: number;
}

/**
 * POST /v1/trade/proposals/:id/{accept|decline|cancel|counter}.
 * Accetta, rifiuta o controproponi: solo chi deve rispondere. Ritira: chi
 * aspetta la risposta, oppure uno dei due dopo l'accordo. Accettando si
 * ricontrolla che le carte ci siano ancora.
 */
async function actOnProposal(request: Request, db: D1Database, uid: string, id: string, action: string, push: Pusher): Promise<Response> {
  const proposal = await db.prepare(`SELECT * FROM trade_proposals WHERE id = ?`).bind(id).first<ProposalRow>();
  if (!proposal || (proposal.from_uid !== uid && proposal.to_uid !== uid)) return json({ error: 'no_proposal' }, 404);
  const other = proposal.from_uid === uid ? proposal.to_uid : proposal.from_uid;
  const myTurn = proposal.status === 'open' && proposal.turn_uid === uid;
  const now = Date.now();

  const close = (status: string) =>
    db
      .prepare(`UPDATE trade_proposals SET status = ?, closed_by = ?, updated_at = ? WHERE id = ?`)
      .bind(status, uid, now, id)
      .run();

  if (action === 'decline') {
    if (!myTurn) return json({ error: 'not_your_turn' }, 409);
    await close('declined');
    return json({ status: 'declined' });
  }

  if (action === 'cancel') {
    const waiting = proposal.status === 'open' && proposal.turn_uid !== uid;
    if (!waiting && proposal.status !== 'accepted' && proposal.status !== 'scheduled') return json({ error: 'not_cancellable' }, 409);
    // Passata l'ora resta solo "Scambio fatto" o "Non si e' presentato": chi
    // non si e' presentato non deve poter far sparire l'appuntamento.
    if (proposal.status === 'scheduled' && meetingPassed(proposal as ProposalRow & Partial<MeetingColumns>)) {
      return json({ error: 'meeting_passed' }, 409);
    }
    await close('cancelled');
    // Ritirare una proposta ancora aperta non avvisa; annullare un accordo si'.
    if (proposal.status === 'accepted' || proposal.status === 'scheduled') {
      await push([{
        id: `cancelled:${id}:${other}`, uid: other, kind: 'meetings', template: 'deal_cancelled',
        args: { nick: await nicknameOf(db, uid) }, data: { screen: 'proposals', proposalId: id }, tag: `proposal:${id}`,
      }]);
    }
    return json({ status: 'cancelled' });
  }

  // Sospeso: si puo' rifiutare o ritirare, non prendere impegni nuovi.
  if ((action === 'accept' || action === 'counter') && (await isSuspended(db, uid))) return json({ error: 'suspended' }, 403);

  if (action === 'accept') {
    if (!myTurn) return json({ error: 'not_your_turn' }, 409);
    const { results } = await db
      .prepare(
        `SELECT giver_uid, card_key AS key, variant, condition, language, qty FROM trade_proposal_items
         WHERE proposal_id = ? AND revision = ?`
      )
      .bind(id, proposal.revision)
      .all<ProposalItem & { giver_uid: string }>();
    const mine = results.filter((r) => r.giver_uid === uid);
    const theirs = results.filter((r) => r.giver_uid === other);
    if (!(await itemsAvailable(db, uid, mine)) || !(await itemsAvailable(db, other, theirs))) {
      return json({ error: 'items_changed' }, 409);
    }
    await db
      .prepare(`UPDATE trade_proposals SET status = 'accepted', updated_at = ? WHERE id = ?`)
      .bind(now, id)
      .run();
    await push([{
      id: `accepted:${id}:${other}`, uid: other, kind: 'proposals', template: 'proposal_accepted',
      args: { nick: await nicknameOf(db, uid) }, data: { screen: 'proposals', proposalId: id }, tag: `proposal:${id}`,
    }]);
    return json({ status: 'accepted' });
  }

  if (action === 'counter') {
    if (!myTurn) return json({ error: 'not_your_turn' }, 409);
    const body = await readJson<{ give?: unknown; take?: unknown }>(request);
    const give = parseItems(body?.give);
    const take = parseItems(body?.take);
    if (!give || !take || give.length === 0 || take.length === 0) return json({ error: 'bad_items' }, 400);
    if (!(await itemsAvailable(db, uid, give)) || !(await itemsAvailable(db, other, take))) {
      return json({ error: 'not_offered' }, 409);
    }
    const revision = proposal.revision + 1;
    await db.batch([
      db
        .prepare(`UPDATE trade_proposals SET revision = ?, turn_uid = ?, updated_at = ? WHERE id = ?`)
        .bind(revision, other, now, id),
      ...itemStatements(db, id, revision, uid, give),
      ...itemStatements(db, id, revision, other, take),
    ]);
    await push([{
      id: `proposal:${id}:${revision}:${other}`, uid: other, kind: 'proposals', template: 'proposal_counter',
      args: { nick: await nicknameOf(db, uid) }, data: { screen: 'proposals', proposalId: id }, tag: `proposal:${id}`,
    }]);
    return json({ status: 'open', revision });
  }

  return json({ error: 'unknown_action' }, 404);
}

/**
 * GET /v1/trade/proposals — le mie, aperte e chiuse da poco, dal mio punto di
 * vista: give = cosa do io, take = cosa ricevo, myTurn = tocca a me.
 */
async function listProposals(db: D1Database, uid: string, env: TradeEnv): Promise<Response> {
  const me = await loadProfile(db, uid);
  const since = Date.now() - CLOSED_VISIBLE_MS;
  const { results: proposals } = await db
    .prepare(
      `SELECT p.*, o.public_id AS other_public_id, o.nickname AS other_nickname, o.geohash5 AS other_cell,
              o.trades_done AS other_trades
       FROM trade_proposals p
       JOIN trade_profiles o ON o.uid = CASE WHEN p.from_uid = ?1 THEN p.to_uid ELSE p.from_uid END
       WHERE (p.from_uid = ?1 OR p.to_uid = ?1) AND (p.status IN ('open', 'accepted', 'scheduled') OR p.updated_at > ?2)
       ORDER BY p.updated_at DESC LIMIT 100`
    )
    .bind(uid, since)
    .all<ProposalRow & MeetingColumns & ClosingColumns & { other_public_id: string; other_nickname: string; other_cell: string; other_trades: number }>();
  if (proposals.length === 0) return json({ proposals: [] });

  const ids = JSON.stringify(proposals.map((p) => p.id));
  const { results: ratings } = await db
    .prepare(`SELECT proposal_id, from_uid, mood, tags, created_at FROM trade_ratings WHERE proposal_id IN (SELECT value FROM json_each(?))`)
    .bind(ids)
    .all<{ proposal_id: string; from_uid: string; mood: string; tags: string; created_at: number }>();
  const { results: noShows } = await db
    .prepare(`SELECT proposal_id, target_uid, created_at, disputed_at FROM trade_no_shows WHERE proposal_id IN (SELECT value FROM json_each(?))`)
    .bind(ids)
    .all<{ proposal_id: string; target_uid: string; created_at: number; disputed_at: number | null }>();
  const { results: myVotes } = await db
    .prepare(`SELECT DISTINCT spot_id FROM trade_spot_votes WHERE uid = ?`)
    .bind(uid)
    .all<{ spot_id: string }>();
  const voted = new Set(myVotes.map((v) => v.spot_id));
  const otherUids = [...new Set(proposals.map((p) => (p.from_uid === uid ? p.to_uid : p.from_uid)))];
  const reputation = await reputationOf(db, 'SELECT value FROM json_each(?)', [JSON.stringify(otherUids)]);
  const tiers = await tiersOf(db, 'SELECT value FROM json_each(?)', [JSON.stringify(otherUids)]);

  const spotIds = [...new Set(proposals.map((p) => p.meet_spot_id).filter((id): id is string => !!id))];
  const spots = spotIds.length === 0
    ? []
    : (await db.prepare(`SELECT * FROM trade_spots WHERE id IN (SELECT value FROM json_each(?))`).bind(JSON.stringify(spotIds)).all<SpotRow>()).results;
  const stats = await spotStats(db, spotIds);

  // Le carte dell'ultima revisione di ognuna, in una query sola.
  const { results: items } = await db
    .prepare(
      `SELECT i.proposal_id, i.giver_uid, i.card_key, i.variant, i.condition, i.language, i.qty
       FROM trade_proposal_items i JOIN trade_proposals p ON p.id = i.proposal_id AND p.revision = i.revision
       WHERE p.id IN (SELECT value FROM json_each(?))`
    )
    .bind(JSON.stringify(proposals.map((p) => p.id)))
    .all<{ proposal_id: string; giver_uid: string; card_key: string; variant: string; condition: string; language: string; qty: number }>();
  const labels = await catalogCardLabels(env);
  const near = me ? cellAndNeighbors(me.geohash5) : [];

  return json({
    proposals: proposals.map((p) => {
      const own = items.filter((i) => i.proposal_id === p.id);
      const shape = (i: (typeof own)[number]) => ({
        key: i.card_key, variant: i.variant, condition: i.condition, language: i.language, qty: i.qty,
        name: labels.get(i.card_key)?.name, setName: labels.get(i.card_key)?.setName,
      });
      const spot = spots.find((s) => s.id === p.meet_spot_id);
      const center = me ? cellCenter(me.geohash5) : { lat: 0, lon: 0 };
      const meetingToConfirm = p.status === 'accepted' && p.meet_status === 'proposed' && p.meet_by !== uid;
      const iAmFrom = p.from_uid === uid;
      const doneByMe = !!(iAmFrom ? p.done_from : p.done_to);
      const doneByOther = !!(iAmFrom ? p.done_to : p.done_from);
      const mine = ratings.find((r) => r.proposal_id === p.id && r.from_uid === uid);
      const theirs = ratings.find((r) => r.proposal_id === p.id && r.from_uid !== uid);
      // Il voto dell'altro si vede quando ho votato anch'io, o dopo 7 giorni.
      const theirsVisible = theirs && (mine || theirs.created_at < Date.now() - RATING_REVEAL_MS);
      const rating = (r: { mood: string; tags: string }) => ({ mood: r.mood, tags: JSON.parse(r.tags || '[]') });
      // Segnalato come assente: posso rispondere "Io c'ero" per 48 ore.
      const noShow = p.status === 'no_show' ? noShows.find((n) => n.proposal_id === p.id) : undefined;
      const disputeUntil = noShow && noShow.target_uid === uid && !noShow.disputed_at ? noShow.created_at + NO_SHOW_DISPUTE_MS : null;
      const canDispute = disputeUntil !== null && disputeUntil > Date.now();
      return {
        id: p.id,
        status: p.status,
        revision: p.revision,
        myTurn: p.status === 'open' && p.turn_uid === uid,
        // Serve una mia mossa: rispondere, confermare l'appuntamento, confermare
        // lo scambio che l'altro ha gia' segnato, votare a scambio chiuso, o
        // rispondere a una segnalazione di assenza.
        actionNeeded: (p.status === 'open' && p.turn_uid === uid) || meetingToConfirm ||
          (p.status === 'scheduled' && doneByOther && !doneByMe) || (p.status === 'done' && !mine) || canDispute,
        doneByMe,
        doneByOther,
        // Chiuso dal cron dopo 7 giorni: uno solo dei due l'aveva segnato fatto.
        autoClosed: p.status === 'done' && doneByMe !== doneByOther,
        canDispute,
        disputeUntil: canDispute ? disputeUntil : null,
        noShowDisputed: !!noShow?.disputed_at,
        closedAt: p.closed_at,
        myRating: mine ? rating(mine) : null,
        theirRating: theirsVisible ? rating(theirs!) : null,
        // Il luogo l'ho gia' votato?
        spotVoted: p.meet_spot_id ? voted.has(p.meet_spot_id) : true,
        meeting: {
          status: p.meet_status,
          byMe: p.meet_by === uid,
          spot: spot ? spotJson(spot, center.lat, center.lon, stats.get(spot.id)) : null,
          slots: p.meet_slots ? JSON.parse(p.meet_slots) : [],
          slot: p.meet_slot ? JSON.parse(p.meet_slot) : null,
        },
        iStarted: p.from_uid === uid,
        closedByMe: p.closed_by === uid,
        createdAt: p.created_at,
        updatedAt: p.updated_at,
        counterpart: {
          id: p.other_public_id,
          nickname: p.other_nickname,
          distance: me && p.other_cell === me.geohash5 ? 'lt5' : near.includes(p.other_cell) ? 'lt15' : 'far',
          tradesDone: p.other_trades,
          reputation: reputation.get(iAmFrom ? p.to_uid : p.from_uid) ?? null,
          tier: tiers.get(iAmFrom ? p.to_uid : p.from_uid) ?? null,
        },
        give: own.filter((i) => i.giver_uid === uid).map(shape),
        take: own.filter((i) => i.giver_uid !== uid).map(shape),
      };
    }),
  });
}

// ── Carte riservate negli accordi (fase 2b) ─────────────────────────────────

/**
 * Le copie gia' promesse in un accordo (accettato o con appuntamento), per
 * persona e carta. Restano nella lista di chi le offre, ma per gli altri non
 * ci sono piu': non compaiono nei match, ne' nelle sue offerte, ne' si
 * possono mettere in un'altra proposta. Si liberano da sole se l'accordo
 * viene annullato.
 */
async function reservedByUid(db: D1Database, giverSql: string, params: unknown[]): Promise<Map<string, Map<string, number>>> {
  const { results } = await db
    .prepare(
      `SELECT i.giver_uid, i.card_key, i.variant, i.condition, i.language, SUM(i.qty) AS qty
       FROM trade_proposal_items i
       JOIN trade_proposals p ON p.id = i.proposal_id AND p.revision = i.revision
       WHERE p.status IN ('accepted', 'scheduled') AND i.giver_uid IN (${giverSql})
       GROUP BY i.giver_uid, i.card_key, i.variant, i.condition, i.language`
    )
    .bind(...params)
    .all<{ giver_uid: string; card_key: string; variant: string; condition: string; language: string; qty: number }>();
  const byUid = new Map<string, Map<string, number>>();
  for (const row of results) {
    const map = byUid.get(row.giver_uid) ?? new Map<string, number>();
    map.set(itemId({ key: row.card_key, ...row }), Number(row.qty));
    byUid.set(row.giver_uid, map);
  }
  return byUid;
}

async function reservedOf(db: D1Database, uid: string): Promise<Map<string, number>> {
  return (await reservedByUid(db, '?', [uid])).get(uid) ?? new Map();
}

// ── Luoghi (fase 2b) ────────────────────────────────────────────────────────

/** Una zona si riscarica da OpenStreetMap dopo questo tempo. */
const SPOT_CELL_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const SPOT_EMPTY_CELL_TTL_MS = 24 * 60 * 60 * 1000;
/** Raggio della ricerca attorno al centro di una cella. */
const SPOT_RADIUS_M = 8000;
/** Elementi di Overpass accettati per cella. */
const MAX_CELL_ELEMENTS = 400;
const PHOTON_URL = 'https://photon.komoot.io/api/';
/** I tipi di OpenStreetMap che "Manca un negozio?" accetta: niente citta', vie o confini. */
const SEARCH_MAX_KM = 30;
const SEARCH_OSM_KEYS = ['shop', 'amenity', 'leisure', 'tourism', 'craft', 'office', 'building'];
/** I servizi di OpenStreetMap chiedono un'identificazione. */
const OSM_USER_AGENT = 'PokeVault-TradeRadar/1.0';
const SPOT_KINDS = ['card_shop', 'comics', 'games', 'video_games', 'toys', 'mall', 'library', 'other'] as const;
type SpotKind = (typeof SPOT_KINDS)[number];
/** Quanto conviene un tipo di luogo: prima i negozi di carte, poi i luoghi pubblici. */
const SPOT_KIND_RANK: Record<SpotKind, number> = {
  card_shop: 6, comics: 5, games: 5, video_games: 3, toys: 3, mall: 2, library: 2, other: 1,
};
const SPOT_SLOT_PARTS = ['morning', 'afternoon', 'evening'];
/** Fin dove si puo' fissare un appuntamento. */
const MEETING_MAX_DAYS = 21;

/**
 * Il tipo di un luogo di OpenStreetMap, o null se non e' adatto. Il nome
 * conta solo per negozi di tipo compatibile: una ricerca larga per nome
 * prendeva macellerie ("Cardoncello"), parrucchieri e sale scommesse
 * ("Games Point"). Sale giochi e bingo (adult_gaming_centre) restano fuori.
 */
function spotKindOf(tags: Record<string, string>): SpotKind | null {
  const name = tags.name ?? '';
  if (!name) return null;
  const shop = tags.shop ?? '';
  const cards = /\b(tcg|cards?|card shop|carte da gioco|carte collezionabili)\b/i.test(name);
  const comics = /fumett|\bcomics?\b|\bmanga\b/i.test(name);
  const games = /\bgames?\b|ludoteca|\bnerd/i.test(name);
  if (shop === 'collector' || (cards && ['books', 'stationery', 'gift', 'variety_store', 'hobby', 'games', 'toys'].includes(shop))) return 'card_shop';
  if (shop === 'comics' || shop === 'anime' || (shop === 'books' && (/comic/i.test(tags.books ?? '') || comics))) return 'comics';
  if (shop === 'games' || shop === 'hobby' || (games && ['books', 'stationery', 'gift', 'variety_store', 'toys'].includes(shop))) return 'games';
  if (shop === 'video_games') return 'video_games';
  if (shop === 'toys') return 'toys';
  if (shop === 'mall') return 'mall';
  if (tags.amenity === 'library') return 'library';
  return null;
}

interface SpotRow {
  id: string;
  name: string;
  kind: SpotKind;
  lat: number;
  lon: number;
  geohash5: string;
  city: string | null;
  address: string | null;
  opening_hours: string | null;
  source: string;
  approved: number;
  added_by: string | null;
}

function distanceKm(aLat: number, aLon: number, bLat: number, bLon: number): number {
  const rad = Math.PI / 180;
  const dLat = (bLat - aLat) * rad;
  const dLon = (bLon - aLon) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(aLat * rad) * Math.cos(bLat * rad) * Math.sin(dLon / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(h));
}

/**
 * La richiesta Overpass per i luoghi attorno a una cella. La prepara il
 * server, ma la manda il telefono: da Cloudflare overpass-api.de risponde 521
 * (blocca i Worker), dai telefoni no. Cosi' le categorie restano decise qui,
 * e il telefono chiede solo il centro della zona, mai la sua posizione.
 */
function overpassQuery(cell: string): string {
  const { lat, lon } = cellCenter(cell);
  // Un rettangolo di SPOT_RADIUS_M per lato attorno al centro, non un cerchio,
  // e il nome con le maiuscole nelle classi invece di ",i": il 01/10, con
  // Overpass carico, (around:...) e ",i" andavano in timeout dopo 30 secondi
  // (risposta 200 con un remark e zero elementi), cosi' rispondono in 3.
  const dLat = SPOT_RADIUS_M / 111_320;
  const dLon = SPOT_RADIUS_M / (111_320 * Math.cos((lat * Math.PI) / 180));
  const box = `(${(lat - dLat).toFixed(4)},${(lon - dLon).toFixed(4)},${(lat + dLat).toFixed(4)},${(lon + dLon).toFixed(4)})`;
  return `[out:json][timeout:60];(` +
    `nwr["shop"~"^(collector|comics|anime|games|hobby|toys|video_games|mall)$"]${box};` +
    `nwr["shop"~"^(books|stationery|gift|variety_store)$"]["name"~"[Ff]umett|[Cc]omic|[Mm]anga|[Gg]ames|TCG|[Cc]ard|[Cc]arte|[Ll]udoteca|[Nn]erd"]${box};` +
    `nwr["amenity"="library"]${box};` +
    `);out center tags ${MAX_CELL_ELEMENTS};`;
}

/**
 * Le celle senza luoghi o scaricate da piu' di 30 giorni. Una cella risultata
 * vuota si riprova dopo un giorno: puo' essere una zona senza niente, ma
 * anche una risposta di Overpass andata storta.
 */
async function staleCells(db: D1Database, cells: string[]): Promise<string[]> {
  const { results } = await db
    .prepare(`SELECT geohash5, fetched_at, found FROM trade_spot_cells WHERE geohash5 IN (SELECT value FROM json_each(?))`)
    .bind(JSON.stringify(cells))
    .all<{ geohash5: string; fetched_at: number; found: number }>();
  const fresh = new Set(
    results
      .filter((r) => Date.now() - r.fetched_at < (r.found > 0 ? SPOT_CELL_TTL_MS : SPOT_EMPTY_CELL_TTL_MS))
      .map((r) => r.geohash5)
  );
  return cells.filter((cell) => !fresh.has(cell));
}

type OverpassElement = {
  type?: string;
  id?: number;
  lat?: number;
  lon?: number;
  center?: { lat?: number; lon?: number };
  tags?: Record<string, string>;
};

/**
 * POST /v1/trade/spots/cell — { cell, elements }: la risposta di Overpass
 * che il telefono ha ottenuto con overpassQuery. Il filtro lo rifa' il server
 * (spotKindOf), e accetta solo celle da aggiornare: una cella fresca non si
 * riscrive.
 */
async function putCellSpots(request: Request, db: D1Database): Promise<Response> {
  const body = await readJson<{ cell?: string; elements?: unknown }>(request);
  const cell = cleanText(body?.cell, 5).toLowerCase();
  if (!GEOHASH5_REGEX.test(cell) || !Array.isArray(body?.elements)) return json({ error: 'bad_cell' }, 400);
  if ((await staleCells(db, [cell])).length === 0) return json({ stored: 0, fresh: true });
  const { lat: centerLat, lon: centerLon } = cellCenter(cell);

  const now = Date.now();
  const statements: D1PreparedStatement[] = [];
  for (const element of (body!.elements as OverpassElement[]).slice(0, MAX_CELL_ELEMENTS)) {
    const tags: Record<string, string> = {};
    for (const [k, v] of Object.entries(element?.tags ?? {})) if (typeof v === 'string') tags[k] = v;
    const kind = spotKindOf(tags);
    const spotLat = Number(element?.lat ?? element?.center?.lat);
    const spotLon = Number(element?.lon ?? element?.center?.lon);
    const type = String(element?.type ?? '')[0];
    // Solo luoghi veri e davvero nella zona: niente di inventato lontano.
    if (!kind || !['n', 'w', 'r'].includes(type) || !Number.isInteger(element?.id)) continue;
    if (!Number.isFinite(spotLat) || !Number.isFinite(spotLon) || distanceKm(centerLat, centerLon, spotLat, spotLon) > (SPOT_RADIUS_M / 1000) * Math.SQRT2 + 1) continue;
    statements.push(
      db
        .prepare(
          `INSERT INTO trade_spots (id, name, kind, lat, lon, geohash5, city, opening_hours, source, approved, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'osm', 1, ?, ?)
           ON CONFLICT(id) DO UPDATE SET name = excluded.name, kind = excluded.kind, lat = excluded.lat, lon = excluded.lon,
             geohash5 = excluded.geohash5, city = excluded.city, opening_hours = excluded.opening_hours, updated_at = excluded.updated_at`
        )
        .bind(
          `osm:${type}${element.id}`, cleanText(tags.name, 80), kind, spotLat, spotLon, encode(spotLat, spotLon, 5),
          cleanText(tags['addr:city'], 60) || null, cleanText(tags.opening_hours, 120) || null, now, now
        )
    );
  }
  const stored = statements.length;
  statements.push(
    db.prepare(`INSERT OR REPLACE INTO trade_spot_cells (geohash5, fetched_at, found) VALUES (?, ?, ?)`).bind(cell, now, stored)
  );
  for (let i = 0; i < statements.length; i += 50) await db.batch(statements.slice(i, i + 50));
  return json({ stored });
}

function spotJson(spot: SpotRow, fromLat: number, fromLon: number, stats?: { trades: number; badges: string[] }) {
  return {
    id: spot.id,
    name: spot.name,
    kind: spot.kind,
    city: spot.city,
    address: spot.address,
    openingHours: spot.opening_hours,
    lat: spot.lat,
    lon: spot.lon,
    // Dal punto a meta' strada fra le due zone, non da una persona.
    distanceKm: Math.round(distanceKm(fromLat, fromLon, spot.lat, spot.lon) * 10) / 10,
    pending: spot.approved !== 1,
    // Scambi chiusi qui, e i badge votati da almeno tre persone (fase 2c).
    trades: stats?.trades ?? 0,
    badges: stats?.badges ?? [],
  };
}

/** Le due persone di una proposta, se chi chiede e' una di loro. */
async function proposalParties(db: D1Database, id: string, uid: string) {
  const proposal = await db.prepare(`SELECT * FROM trade_proposals WHERE id = ?`).bind(id).first<ProposalRow & MeetingColumns>();
  if (!proposal || (proposal.from_uid !== uid && proposal.to_uid !== uid)) return null;
  const other = proposal.from_uid === uid ? proposal.to_uid : proposal.from_uid;
  const cells = await db
    .prepare(`SELECT uid, geohash5 FROM trade_profiles WHERE uid IN (?, ?)`)
    .bind(uid, other)
    .all<{ uid: string; geohash5: string }>();
  const mine = cells.results.find((r) => r.uid === uid)?.geohash5;
  const theirs = cells.results.find((r) => r.uid === other)?.geohash5 ?? mine;
  if (!mine || !theirs) return null;
  const a = cellCenter(mine);
  const b = cellCenter(theirs);
  return { proposal, other, cells: [...new Set([mine, theirs])], mid: { lat: (a.lat + b.lat) / 2, lon: (a.lon + b.lon) / 2 } };
}

/**
 * GET /v1/trade/proposals/:id/spots — i luoghi per l'appuntamento, i migliori
 * per primi: il tipo conta (prima i negozi di carte), ma ogni 3 km dal punto
 * a meta' strada costano un gradino. missingCells elenca le zone ancora da
 * scaricare, con la richiesta Overpass gia' pronta.
 */
async function getProposalSpots(db: D1Database, uid: string, id: string): Promise<Response> {
  const parties = await proposalParties(db, id, uid);
  if (!parties) return json({ error: 'no_proposal' }, 404);
  // Le zone da aggiornare le scarica il telefono (vedi overpassQuery) e poi richiede.
  const missing = await staleCells(db, parties.cells);
  const { lat, lon } = parties.mid;
  const { results } = await db
    .prepare(
      `SELECT * FROM trade_spots
       WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? AND (approved = 1 OR added_by IN (?, ?))`
    )
    .bind(lat - 0.15, lat + 0.15, lon - 0.2, lon + 0.2, uid, parties.other)
    .all<SpotRow>();
  // Dove si scambia davvero sale: mezzo gradino ogni scambio chiuso (fino a
  // 5) e uno per badge, oltre a tipo e distanza.
  const stats = await spotStats(db, results.map((spot) => spot.id));
  const score = (spot: SpotRow) => {
    const extra = stats.get(spot.id);
    return (SPOT_KIND_RANK[spot.kind] ?? 1) - distanceKm(lat, lon, spot.lat, spot.lon) / 3 +
      Math.min(extra?.trades ?? 0, 10) * 0.5 + (extra?.badges.length ?? 0);
  };
  const spots = results.sort((a, b) => score(b) - score(a)).slice(0, 25).map((spot) => spotJson(spot, lat, lon, stats.get(spot.id)));
  return json({ spots, missingCells: missing.map((cell) => ({ cell, query: overpassQuery(cell) })) });
}

/** GET /v1/trade/spots/search?q=...&proposal=... — "Manca un negozio?", cercato su OpenStreetMap vicino. */
async function searchSpots(db: D1Database, uid: string, url: URL): Promise<Response> {
  const q = cleanText(url.searchParams.get('q'), 60);
  if (q.length < 2) return json({ results: [] });
  let center: { lat: number; lon: number } | null = null;
  const proposalId = url.searchParams.get('proposal');
  if (proposalId) center = (await proposalParties(db, proposalId, uid))?.mid ?? null;
  if (!center) {
    const me = await loadProfile(db, uid);
    if (me) center = cellCenter(me.geohash5);
  }
  // Se ne chiedono di piu': citta' e vie si scartano qui sotto.
  const params = new URLSearchParams({ q, limit: '40', lang: 'default', zoom: '11' });
  if (center) {
    params.set('lat', center.lat.toFixed(4));
    params.set('lon', center.lon.toFixed(4));
  }
  try {
    const response = await fetch(`${PHOTON_URL}?${params}`, { headers: { 'user-agent': OSM_USER_AGENT }, signal: AbortSignal.timeout(10000) });
    if (!response.ok) return json({ results: [] });
    const data = (await response.json()) as {
      features?: Array<{ geometry?: { coordinates?: [number, number] }; properties?: Record<string, string | number> }>;
    };
    const results = (data.features ?? [])
      // Solo luoghi dove si entra: negozi, locali, biblioteche, centri. Il 01/10
      // la ricerca ha restituito il comune di Portici, finito come luogo d'incontro.
      .filter((f) => f.properties?.name && f.geometry?.coordinates && SEARCH_OSM_KEYS.includes(String(f.properties.osm_key)))
      .map((f) => {
        const p = f.properties!;
        const [fLon, fLat] = f.geometry!.coordinates!;
        const tags: Record<string, string> = { name: String(p.name), [String(p.osm_key)]: String(p.osm_value) };
        return {
          osmId: `osm:${String(p.osm_type ?? 'n').toLowerCase()[0]}${p.osm_id}`,
          name: String(p.name),
          kind: spotKindOf(tags) ?? 'other',
          city: String(p.city ?? p.county ?? ''),
          lat: fLat,
          lon: fLon,
          distanceKm: center ? Math.round(distanceKm(center.lat, center.lon, fLat, fLon) * 10) / 10 : null,
        };
      });
    // Photon da' solo una precedenza ai vicini: "fumetteria" tornava Milano e Torino.
    // Si tengono quelli entro SEARCH_MAX_KM, dal piu' vicino.
    const near = results
      .filter((r) => r.distanceKm == null || r.distanceKm <= SEARCH_MAX_KM)
      .sort((x, y) => (x.distanceKm ?? 0) - (y.distanceKm ?? 0))
      .slice(0, 8);
    return json({ results: near });
  } catch {
    return json({ results: [] });
  }
}

/**
 * Coordinate di un indirizzo (o di una citta') con Photon, vicino a [near]:
 * per una segnalazione, cosi' le mappe portano nel posto giusto anche prima
 * della verifica. Null se non trova niente entro 40 km.
 */
async function geocode(
  query: string,
  near: { lat: number; lon: number },
  onlyStreets = false
): Promise<{ lat: number; lon: number } | null> {
  const params = new URLSearchParams({ q: query, limit: '5', lang: 'default', lat: near.lat.toFixed(4), lon: near.lon.toFixed(4) });
  // Senza civico si cerca una strada: "Gioacchino Rossini" da solo trovava altro con quel nome.
  if (onlyStreets) params.set('osm_tag', 'highway');
  try {
    const response = await fetch(`${PHOTON_URL}?${params}`, { headers: { 'user-agent': OSM_USER_AGENT }, signal: AbortSignal.timeout(10000) });
    if (!response.ok) return null;
    const data = (await response.json()) as { features?: Array<{ geometry?: { coordinates?: [number, number] } }> };
    for (const feature of data.features ?? []) {
      const coordinates = feature.geometry?.coordinates;
      if (!coordinates) continue;
      const [fLon, fLat] = coordinates;
      if (Number.isFinite(fLat) && Number.isFinite(fLon) && distanceKm(near.lat, near.lon, fLat, fLon) <= 40) return { lat: fLat, lon: fLon };
    }
  } catch {
    // Senza coordinate si resta al centro della zona: lo si sistema in verifica.
  }
  return null;
}

/** Le forme di un indirizzo da provare, dalla piu' precisa: com'e', senza civico, senza tipo di strada. */
function addressAttempts(address: string): string[] {
  if (!address) return [];
  const noNumber = address.replace(/[,\s]+\d+[a-z]?\s*$/i, '').trim();
  const noType = noNumber.replace(/^(via|viale|piazza|piazzale|corso|largo|vico|vicolo|strada|traversa)\s+/i, '').trim();
  return [...new Set([address, noNumber, noType].filter((a) => a.length >= 3))];
}

/**
 * POST /v1/trade/spots — un luogo che mancava. Con osmId e coordinate e' un
 * luogo vero di OpenStreetMap, trovato con la ricerca: entra subito. Senza,
 * e' una segnalazione: nome, citta' (obbligatoria), indirizzo facoltativo e
 * tipo. Le coordinate vengono dall'indirizzo, o dalla citta'. Finche' non la
 * verifichiamo la vedono solo chi l'ha fatta e chi scambia con lui (approved
 * = 0); poi tutti.
 */
async function addSpot(request: Request, db: D1Database, uid: string): Promise<Response> {
  const body = await readJson<{ osmId?: string; name?: string; kind?: string; city?: string; address?: string; lat?: number; lon?: number }>(request);
  const name = cleanText(body?.name, 80);
  if (name.length < 2) return json({ error: 'bad_name' }, 400);
  const kind = (SPOT_KINDS as readonly string[]).includes(body?.kind ?? '') ? (body!.kind as SpotKind) : 'other';
  const osmId = cleanText(body?.osmId, 30);
  const city = cleanText(body?.city, 60);
  const address = cleanText(body?.address, 120);
  const now = Date.now();
  let id: string;
  let lat = Number(body?.lat);
  let lon = Number(body?.lon);
  let source: string;
  let approved: number;
  if (/^osm:[nwr]\d+$/.test(osmId) && Number.isFinite(lat) && Number.isFinite(lon)) {
    id = osmId;
    source = 'osm';
    approved = 1;
  } else {
    if (city.length < 2) return json({ error: 'bad_city' }, 400);
    const me = await loadProfile(db, uid);
    if (!me) return json({ error: 'no_profile' }, 404);
    const center = cellCenter(me.geohash5);
    // Prima l'indirizzo esatto, poi senza civico, poi senza "Via/Viale": il 01/10
    // "Viale Gioacchino Rossini 27, Portici" in OpenStreetMap e' "Via Gioacchino Rossini".
    let found: { lat: number; lon: number } | null = null;
    for (const [index, attempt] of addressAttempts(address).entries()) {
      found = await geocode(`${attempt}, ${city}`, center, index > 0);
      if (found) break;
    }
    found = found ?? (await geocode(city, center));
    ({ lat, lon } = found ?? center);
    id = `user:${crypto.randomUUID()}`;
    source = 'user';
    approved = 0;
  }
  await db
    .prepare(
      `INSERT INTO trade_spots (id, name, kind, lat, lon, geohash5, city, address, opening_hours, source, approved, added_by, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO NOTHING`
    )
    .bind(id, name, kind, lat, lon, encode(lat, lon, 5), city || null, address || null, source, approved, uid, now, now)
    .run();
  const spot = await db.prepare(`SELECT * FROM trade_spots WHERE id = ?`).bind(id).first<SpotRow>();
  return json({ spot: spot ? spotJson(spot, lat, lon) : null }, 201);
}

// ── Appuntamento (fase 2b) ──────────────────────────────────────────────────

interface MeetingColumns {
  meet_status: string;
  meet_by: string | null;
  meet_spot_id: string | null;
  meet_slots: string | null;
  meet_slot: string | null;
}

/** La fascia di un orario, per chi la legge ancora: prima delle 13 mattina, dalle 19 sera. */
function partOfTime(time: string): string {
  const hour = Number(time.slice(0, 2));
  return hour < 13 ? 'morning' : hour < 19 ? 'afternoon' : 'evening';
}

/**
 * Gli orari proposti: da 1 a 3, giorno fra oggi e 21 giorni, ciascuno con
 * l'ora (richiesta dall'utente il 01/10: "mattina" non basta per vedersi).
 * Una fascia senza ora si accetta ancora, per le app di prima.
 */
function parseSlots(raw: unknown): Slot[] | null {
  if (!Array.isArray(raw) || raw.length < 1 || raw.length > 3) return null;
  const today = new Date().toISOString().slice(0, 10);
  const last = new Date(Date.now() + MEETING_MAX_DAYS * 86400000).toISOString().slice(0, 10);
  const seen = new Set<string>();
  const slots: Slot[] = [];
  for (const value of raw as Array<Record<string, unknown>>) {
    const day = cleanText(value?.day, 10);
    const time = cleanText(value?.time, 5);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(day) || day < today || day > last) return null;
    if (time) {
      if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(time) || time < '07:00' || time > '23:00') return null;
    } else if (!SPOT_SLOT_PARTS.includes(cleanText(value?.part, 10))) {
      return null;
    }
    const part = time ? partOfTime(time) : cleanText(value?.part, 10);
    const id = `${day}|${time || part}`;
    if (seen.has(id)) continue;
    seen.add(id);
    slots.push(time ? { day, time, part } : { day, part });
  }
  return slots;
}

/**
 * POST /v1/trade/proposals/:id/meeting — { spot, slots }: propone (o cambia)
 * luogo e fasce. Si puo' dopo l'accordo, anche ad appuntamento gia' fissato:
 * allora torna "da confermare" e tocca all'altro.
 * POST /v1/trade/proposals/:id/meeting/confirm — { slot }: l'altro sceglie
 * una delle fasce e l'appuntamento e' fissato.
 */
async function meetingAction(request: Request, db: D1Database, uid: string, id: string, confirm: boolean, push: Pusher): Promise<Response> {
  const proposal = await db.prepare(`SELECT * FROM trade_proposals WHERE id = ?`).bind(id).first<ProposalRow & MeetingColumns>();
  if (!proposal || (proposal.from_uid !== uid && proposal.to_uid !== uid)) return json({ error: 'no_proposal' }, 404);
  if (proposal.status !== 'accepted' && proposal.status !== 'scheduled') return json({ error: 'not_agreed' }, 409);
  const now = Date.now();

  if (confirm) {
    if (proposal.meet_status !== 'proposed' || proposal.meet_by === uid) return json({ error: 'not_your_turn' }, 409);
    const body = await readJson<{ slot?: number }>(request);
    const slots = JSON.parse(proposal.meet_slots ?? '[]') as Slot[];
    const chosen = slots[Number(body?.slot)];
    if (!chosen) return json({ error: 'bad_slot' }, 400);
    await db
      .prepare(`UPDATE trade_proposals SET meet_status = 'confirmed', meet_slot = ?, status = 'scheduled', updated_at = ? WHERE id = ?`)
      .bind(JSON.stringify(chosen), now, id)
      .run();
    if (proposal.meet_by) {
      const spot = await db.prepare(`SELECT name FROM trade_spots WHERE id = ?`).bind(proposal.meet_spot_id).first<{ name: string }>();
      const time = slotTime(chosen);
      await push([{
        id: `confirmed:${id}:${chosen.day}T${time}:${proposal.meet_by}`, uid: proposal.meet_by, kind: 'meetings', template: 'meeting_confirmed',
        args: { nick: await nicknameOf(db, uid), day: chosen.day, time, spot: spot?.name ?? '' },
        data: { screen: 'proposals', proposalId: id }, tag: `proposal:${id}`,
      }]);
    }
    return json({ status: 'scheduled', slot: chosen });
  }

  const body = await readJson<{ spot?: string; slots?: unknown }>(request);
  const slots = parseSlots(body?.slots);
  if (!slots) return json({ error: 'bad_slots' }, 400);
  const other = proposal.from_uid === uid ? proposal.to_uid : proposal.from_uid;
  const spot = await db
    .prepare(`SELECT id FROM trade_spots WHERE id = ? AND (approved = 1 OR added_by IN (?, ?))`)
    .bind(cleanText(body?.spot, 60), uid, other)
    .first<{ id: string }>();
  if (!spot) return json({ error: 'bad_spot' }, 400);
  await db
    .prepare(
      `UPDATE trade_proposals SET meet_status = 'proposed', meet_by = ?, meet_spot_id = ?, meet_slots = ?, meet_slot = NULL,
         status = 'accepted', updated_at = ? WHERE id = ?`
    )
    .bind(uid, spot.id, JSON.stringify(slots), now, id)
    .run();
  await push([{
    id: `meeting:${id}:${now}:${other}`, uid: other, kind: 'meetings', template: 'meeting_proposed',
    args: { nick: await nicknameOf(db, uid), change: proposal.meet_status !== 'none' },
    data: { screen: 'proposals', proposalId: id }, tag: `proposal:${id}`,
  }]);
  return json({ status: 'accepted', meeting: 'proposed' });
}

// ── Chiusura e feedback (fase 2c) ───────────────────────────────────────────

/** Un voto si vede comunque dopo questo tempo, anche se l'altro non ha votato. */
const RATING_REVEAL_MS = 7 * 24 * 60 * 60 * 1000;
/** Due "non si e' presentato" da persone diverse in questo periodo... */
const NO_SHOW_WINDOW_MS = 60 * 24 * 60 * 60 * 1000;
/** ...tolgono il profilo dai match per questo tempo. */
const NO_SHOW_SUSPENSION_MS = 30 * 24 * 60 * 60 * 1000;
/** Chi e' segnalato puo' rispondere "Io c'ero" entro questo tempo (schema 12). */
const NO_SHOW_DISPUTE_MS = 48 * 60 * 60 * 1000;
/** Persone diverse che sospendono: con segnalazioni non contestate, o comunque. */
const NO_SHOW_REPORTERS = 2;
const NO_SHOW_REPORTERS_ANYWAY = 3;
/**
 * Passato questo tempo dall'appuntamento lo scambio si chiude da solo: fatto
 * se uno dei due l'aveva segnato, altrimenti scaduto (schema 12).
 */
const MEETING_AUTO_CLOSE_MS = 7 * 24 * 60 * 60 * 1000;
const RATING_MOODS = ['good', 'ok', 'bad'];
/** I chip: i primi dopo 😊, gli altri dopo 😐 o 😞. */
const GOOD_TAGS = ['punctual', 'as_described', 'kind'];
const BAD_TAGS = ['late', 'worse_condition', 'different_card', 'rude'];
const SPOT_VOTE_TAGS = ['tournaments', 'comics', 'card_shop'];
/** Persone diverse che servono perche' un luogo prenda un badge. */
const SPOT_BADGE_VOTES = 3;

interface ClosingColumns {
  done_from: number | null;
  done_to: number | null;
  closed_at: number | null;
}

/**
 * POST /v1/trade/proposals/:id/done — "Scambio fatto". Solo ad appuntamento
 * fissato e dal suo giorno in poi (ora italiana). Quando l'hanno segnato
 * tutti e due lo scambio e' chiuso: status 'done' e uno scambio in piu' a
 * testa. La collezione la aggiorna l'app, con il riepilogo carta per carta.
 */
async function markDone(db: D1Database, uid: string, id: string, push: Pusher): Promise<Response> {
  const proposal = await db.prepare(`SELECT * FROM trade_proposals WHERE id = ?`).bind(id).first<ProposalRow & MeetingColumns & ClosingColumns>();
  if (!proposal || (proposal.from_uid !== uid && proposal.to_uid !== uid)) return json({ error: 'no_proposal' }, 404);
  if (proposal.status !== 'scheduled' || !proposal.meet_slot) return json({ error: 'not_scheduled' }, 409);
  const slot = JSON.parse(proposal.meet_slot) as Slot;
  if (romeNow().day < slot.day) return json({ error: 'too_early', day: slot.day }, 409);

  const now = Date.now();
  const column = proposal.from_uid === uid ? 'done_from' : 'done_to';
  const otherDone = proposal.from_uid === uid ? proposal.done_to : proposal.done_from;
  if (!otherDone) {
    // Lo status nel WHERE: nel frattempo il cron puo' averlo chiuso.
    const marked = await db.prepare(`UPDATE trade_proposals SET ${column} = ?, updated_at = ? WHERE id = ? AND status = 'scheduled'`).bind(now, now, id).run();
    if (!marked.meta.changes) return json({ error: 'not_scheduled' }, 409);
    const other = proposal.from_uid === uid ? proposal.to_uid : proposal.from_uid;
    await push([{
      id: `done:${id}:${other}`, uid: other, kind: 'after', template: 'done_by_other',
      args: { nick: await nicknameOf(db, uid) }, data: { screen: 'proposals', proposalId: id }, tag: `proposal:${id}`,
    }]);
    return json({ status: 'scheduled', waitingOther: true });
  }
  // Lo scambio in piu' a testa solo se l'ha chiuso questa chiamata, non il cron.
  const [closed] = await db.batch([
    db
      .prepare(`UPDATE trade_proposals SET ${column} = ?, status = 'done', closed_at = ?, updated_at = ? WHERE id = ? AND status = 'scheduled'`)
      .bind(now, now, now, id),
    db
      .prepare(
        `UPDATE trade_profiles SET trades_done = trades_done + 1 WHERE uid IN (?1, ?2)
           AND EXISTS (SELECT 1 FROM trade_proposals WHERE id = ?3 AND status = 'done' AND ${column} = ?4 AND closed_at = ?4)`
      )
      .bind(proposal.from_uid, proposal.to_uid, id, now),
  ]);
  if (!closed.meta.changes) return json({ error: 'not_scheduled' }, 409);
  // Chi aveva segnato per primo lo scopre qui: e' il momento di aggiornare la collezione e votare.
  const first = proposal.from_uid === uid ? proposal.to_uid : proposal.from_uid;
  await push([{
    id: `closed:${id}:${first}`, uid: first, kind: 'after', template: 'trade_closed',
    args: { nick: await nicknameOf(db, uid) }, data: { screen: 'proposals', proposalId: id }, tag: `proposal:${id}`,
  }]);
  return json({ status: 'done' });
}

/**
 * Le segnalazioni di assenza ricevute da [since] in poi bastano a sospendere?
 * Due persone diverse non contestate, o tre comunque: una contestazione e'
 * una parola contro l'altra, tre persone non sono piu' un caso.
 */
async function noShowSuspends(db: D1Database, target: string, since: number): Promise<boolean> {
  const counts = await db
    .prepare(
      `SELECT COUNT(DISTINCT reporter_uid) AS reporters,
              COUNT(DISTINCT CASE WHEN disputed_at IS NULL THEN reporter_uid END) AS undisputed
       FROM trade_no_shows WHERE target_uid = ? AND created_at > ?`
    )
    .bind(target, since)
    .first<{ reporters: number; undisputed: number }>();
  return (counts?.undisputed ?? 0) >= NO_SHOW_REPORTERS || (counts?.reporters ?? 0) >= NO_SHOW_REPORTERS_ANYWAY;
}

/**
 * POST /v1/trade/proposals/:id/noshow — "Non si e' presentato", solo dopo
 * l'ora dell'appuntamento. Lo scambio non c'e' stato: niente percentuale, ma
 * due segnalazioni da persone diverse in 60 giorni sospendono il profilo
 * dai match per 30 giorni. Chi e' segnalato lo sa subito e ha 48 ore per
 * rispondere "Io c'ero" (disputeNoShow).
 */
async function markNoShow(db: D1Database, uid: string, id: string, push: Pusher): Promise<Response> {
  const proposal = await db.prepare(`SELECT * FROM trade_proposals WHERE id = ?`).bind(id).first<ProposalRow & MeetingColumns & ClosingColumns>();
  if (!proposal || (proposal.from_uid !== uid && proposal.to_uid !== uid)) return json({ error: 'no_proposal' }, 404);
  if (proposal.status !== 'scheduled' || !proposal.meet_slot) return json({ error: 'not_scheduled' }, 409);
  const slot = JSON.parse(proposal.meet_slot) as Slot;
  const now = romeNow();
  if (now.day < slot.day || (now.day === slot.day && now.time < slotTime(slot))) return json({ error: 'too_early' }, 409);

  const target = proposal.from_uid === uid ? proposal.to_uid : proposal.from_uid;
  const ts = Date.now();
  // Prima si chiude, poi si registra solo se l'ha chiusa questa chiamata: se
  // l'altro ha segnalato nello stesso istante, o il cron l'ha chiusa, niente.
  const [closed] = await db.batch([
    db
      .prepare(`UPDATE trade_proposals SET status = 'no_show', closed_by = ?, closed_at = ?, updated_at = ? WHERE id = ? AND status = 'scheduled'`)
      .bind(uid, ts, ts, id),
    db
      .prepare(
        `INSERT OR IGNORE INTO trade_no_shows (proposal_id, reporter_uid, target_uid, created_at)
         SELECT ?1, ?2, ?3, ?4 WHERE EXISTS
           (SELECT 1 FROM trade_proposals WHERE id = ?1 AND status = 'no_show' AND closed_by = ?2 AND closed_at = ?4)`
      )
      .bind(id, uid, target, ts),
  ]);
  if (!closed.meta.changes) return json({ error: 'not_scheduled' }, 409);
  if (await noShowSuspends(db, target, ts - NO_SHOW_WINDOW_MS)) {
    await suspend(db, target, ts + NO_SHOW_SUSPENSION_MS, 'no_show');
  }
  await push([{
    id: `noshow:${id}:${target}`, uid: target, kind: 'meetings', template: 'no_show_reported',
    args: { nick: await nicknameOf(db, uid) }, data: { screen: 'proposals', proposalId: id }, tag: `proposal:${id}`,
  }]);
  return json({ status: 'no_show' });
}

/**
 * POST /v1/trade/proposals/:id/dispute — "Io c'ero": chi e' stato segnalato
 * come assente, entro 48 ore. La segnalazione resta, ma da sola non conta
 * piu': se la sospensione dipendeva da lei, si toglie.
 */
async function disputeNoShow(db: D1Database, uid: string, id: string): Promise<Response> {
  const report = await db
    .prepare(
      `SELECT n.created_at, n.disputed_at FROM trade_no_shows n JOIN trade_proposals p ON p.id = n.proposal_id
       WHERE n.proposal_id = ? AND n.target_uid = ? AND p.status = 'no_show'`
    )
    .bind(id, uid)
    .first<{ created_at: number; disputed_at: number | null }>();
  if (!report) return json({ error: 'no_proposal' }, 404);
  if (report.disputed_at) return json({ status: 'disputed' });
  const now = Date.now();
  if (now - report.created_at > NO_SHOW_DISPUTE_MS) return json({ error: 'too_late' }, 409);

  await db.batch([
    db.prepare(`UPDATE trade_no_shows SET disputed_at = ? WHERE proposal_id = ? AND target_uid = ? AND disputed_at IS NULL`).bind(now, id, uid),
    db.prepare(`UPDATE trade_proposals SET updated_at = ? WHERE id = ?`).bind(now, id),
  ]);
  const profile = await db
    .prepare(`SELECT suspended_until, suspension_reason FROM trade_profiles WHERE uid = ?`)
    .bind(uid)
    .first<{ suspended_until: number | null; suspension_reason: string | null }>();
  if (profile?.suspension_reason === 'no_show' && (profile.suspended_until ?? 0) > now) {
    // Si ricontano le segnalazioni che l'avevano fatta scattare, e quelle venute dopo.
    const since = profile.suspended_until! - NO_SHOW_SUSPENSION_MS - NO_SHOW_WINDOW_MS;
    if (!(await noShowSuspends(db, uid, since))) {
      await db
        .prepare(`UPDATE trade_profiles SET suspended_until = NULL, suspension_reason = NULL WHERE uid = ? AND suspension_reason = 'no_show'`)
        .bind(uid)
        .run();
    }
  }
  return json({ status: 'disputed' });
}

/**
 * POST /v1/trade/proposals/:id/rate — { mood, tags }: il voto sull'altro,
 * una volta sola e solo a scambio chiuso. I chip devono andare con la
 * faccina: quelli positivi solo con 😊, gli altri con 😐 o 😞.
 */
async function rateTrade(request: Request, db: D1Database, uid: string, id: string): Promise<Response> {
  const proposal = await db.prepare(`SELECT * FROM trade_proposals WHERE id = ?`).bind(id).first<ProposalRow>();
  if (!proposal || (proposal.from_uid !== uid && proposal.to_uid !== uid)) return json({ error: 'no_proposal' }, 404);
  if (proposal.status !== 'done') return json({ error: 'not_done' }, 409);
  const body = await readJson<{ mood?: string; tags?: unknown }>(request);
  const mood = cleanText(body?.mood, 10);
  if (!RATING_MOODS.includes(mood)) return json({ error: 'bad_mood' }, 400);
  const allowed = mood === 'good' ? GOOD_TAGS : BAD_TAGS;
  const tags = [...new Set(Array.isArray(body?.tags) ? (body!.tags as unknown[]).map((t) => cleanText(t, 30)) : [])];
  if (tags.some((tag) => !allowed.includes(tag))) return json({ error: 'bad_tags' }, 400);
  const other = proposal.from_uid === uid ? proposal.to_uid : proposal.from_uid;
  const result = await db
    .prepare(`INSERT OR IGNORE INTO trade_ratings (proposal_id, from_uid, to_uid, mood, tags, created_at) VALUES (?, ?, ?, ?, ?, ?)`)
    .bind(id, uid, other, mood, JSON.stringify(tags), Date.now())
    .run();
  if ((result.meta?.changes ?? 0) === 0) return json({ error: 'already_rated' }, 409);
  return json({ rated: true });
}

/**
 * POST /v1/trade/proposals/:id/spotvote — { tags }: dopo uno scambio chiuso,
 * cosa e' il luogo dove ci si e' visti. Solo chi ci ha scambiato vota, una
 * volta per tipo.
 */
async function voteSpot(request: Request, db: D1Database, uid: string, id: string): Promise<Response> {
  const proposal = await db.prepare(`SELECT * FROM trade_proposals WHERE id = ?`).bind(id).first<ProposalRow & MeetingColumns>();
  if (!proposal || (proposal.from_uid !== uid && proposal.to_uid !== uid)) return json({ error: 'no_proposal' }, 404);
  if (proposal.status !== 'done' || !proposal.meet_spot_id) return json({ error: 'not_done' }, 409);
  const body = await readJson<{ tags?: unknown }>(request);
  const tags = [...new Set(Array.isArray(body?.tags) ? (body!.tags as unknown[]).map((t) => cleanText(t, 20)) : [])];
  if (tags.some((tag) => !SPOT_VOTE_TAGS.includes(tag))) return json({ error: 'bad_tags' }, 400);
  const now = Date.now();
  if (tags.length > 0) {
    await db.batch(
      tags.map((tag) =>
        db
          .prepare(`INSERT OR IGNORE INTO trade_spot_votes (spot_id, uid, tag, created_at) VALUES (?, ?, ?, ?)`)
          .bind(proposal.meet_spot_id, uid, tag, now)
      )
    );
  }
  return json({ voted: tags.length });
}

interface Reputation {
  good: number;
  ok: number;
  bad: number;
  /** I chip positivi piu' ricevuti, al massimo tre. I negativi non si mostrano. */
  topTags: Array<{ tag: string; count: number }>;
}

/**
 * La reputazione delle persone in [uidSql]: i voti ricevuti che si possono
 * gia' vedere (hanno votato entrambi, o sono passati 7 giorni).
 */
async function reputationOf(db: D1Database, uidSql: string, params: unknown[]): Promise<Map<string, Reputation>> {
  const { results } = await db
    .prepare(
      `SELECT r.to_uid, r.mood, r.tags FROM trade_ratings r
       WHERE r.to_uid IN (${uidSql})
         AND (r.created_at < ? OR EXISTS (SELECT 1 FROM trade_ratings o WHERE o.proposal_id = r.proposal_id AND o.from_uid = r.to_uid))`
    )
    .bind(...params, Date.now() - RATING_REVEAL_MS)
    .all<{ to_uid: string; mood: string; tags: string }>();
  const byUid = new Map<string, { good: number; ok: number; bad: number; tags: Map<string, number> }>();
  for (const row of results) {
    const entry = byUid.get(row.to_uid) ?? { good: 0, ok: 0, bad: 0, tags: new Map<string, number>() };
    if (row.mood === 'good' || row.mood === 'ok' || row.mood === 'bad') entry[row.mood] += 1;
    for (const tag of JSON.parse(row.tags || '[]') as string[]) {
      if (GOOD_TAGS.includes(tag)) entry.tags.set(tag, (entry.tags.get(tag) ?? 0) + 1);
    }
    byUid.set(row.to_uid, entry);
  }
  const out = new Map<string, Reputation>();
  for (const [uid, entry] of byUid) {
    out.set(uid, {
      good: entry.good,
      ok: entry.ok,
      bad: entry.bad,
      topTags: [...entry.tags.entries()].sort((a, b) => b[1] - a[1]).slice(0, 3).map(([tag, count]) => ({ tag, count })),
    });
  }
  return out;
}

/** Per i luoghi: quanti scambi chiusi ci sono stati e quali badge hanno (3 voti da persone diverse). */
async function spotStats(db: D1Database, spotIds: string[]): Promise<Map<string, { trades: number; badges: string[] }>> {
  const stats = new Map<string, { trades: number; badges: string[] }>();
  if (spotIds.length === 0) return stats;
  const ids = JSON.stringify(spotIds);
  const trades = await db
    .prepare(`SELECT meet_spot_id AS id, COUNT(*) AS n FROM trade_proposals WHERE status = 'done' AND meet_spot_id IN (SELECT value FROM json_each(?)) GROUP BY meet_spot_id`)
    .bind(ids)
    .all<{ id: string; n: number }>();
  const votes = await db
    .prepare(`SELECT spot_id AS id, tag, COUNT(DISTINCT uid) AS n FROM trade_spot_votes WHERE spot_id IN (SELECT value FROM json_each(?)) GROUP BY spot_id, tag`)
    .bind(ids)
    .all<{ id: string; tag: string; n: number }>();
  for (const row of trades.results) stats.set(row.id, { trades: Number(row.n), badges: [] });
  for (const row of votes.results) {
    if (Number(row.n) < SPOT_BADGE_VOTES) continue;
    const entry = stats.get(row.id) ?? { trades: 0, badges: [] };
    entry.badges.push(row.tag);
    stats.set(row.id, entry);
  }
  return stats;
}

// ── Classifica e livelli (fase 2e) ──────────────────────────────────────────

/**
 * Quanto resta buona la classifica calcolata al bisogno. In produzione la rifa'
 * il cron ogni notte (tradeScheduled), e qui si ricalcola solo se quel giro e'
 * saltato; lo staging la vuole fresca per le prove (TRADE_LEADERBOARD_TTL_MIN).
 */
const LEADERBOARD_DEFAULT_TTL_MS = 24 * 60 * 60 * 1000;
/** Ora (UTC) del ricalcolo notturno: le 4 in Italia d'estate, le 3 d'inverno. */
const LEADERBOARD_NIGHTLY_HOUR_UTC = 2;

function leaderboardTtlMs(env: TradeEnv): number {
  const minutes = Number(env.TRADE_LEADERBOARD_TTL_MIN);
  return Number.isFinite(minutes) && minutes > 0 ? minutes * 60 * 1000 : LEADERBOARD_DEFAULT_TTL_MS;
}
/** La stessa coppia conta al massimo una volta in questo periodo: due amici non si gonfiano a vicenda. */
const PAIR_WINDOW_MS = 30 * 24 * 60 * 60 * 1000;
/** Per entrare in classifica: tanti scambi validi, con almeno tante persone diverse. */
const LEADERBOARD_MIN_TRADES = 3;
const LEADERBOARD_MIN_PARTNERS = 3;
const LEADERBOARD_SIZE = 50;
/** I livelli: scambi validi minimi, con almeno il 90% di 😊 fra 😊 e 😞. */
const TIERS: Array<{ tier: string; trades: number }> = [
  { tier: 'platinum', trades: 100 },
  { tier: 'gold', trades: 40 },
  { tier: 'silver', trades: 15 },
  { tier: 'bronze', trades: 5 },
];
const TIER_MIN_POSITIVE = 0.9;

/**
 * Limite inferiore dell'intervallo di Wilson (95%) per [good] su [total]:
 * chi ha 3 voti tutti buoni sta sotto chi ne ha 30 al 97%. Pochi voti non
 * bastano per salire.
 */
function wilsonLower(good: number, total: number): number {
  if (total === 0) return 0;
  const z = 1.96;
  const p = good / total;
  const denominator = 1 + (z * z) / total;
  const center = p + (z * z) / (2 * total);
  const margin = z * Math.sqrt((p * (1 - p)) / total + (z * z) / (4 * total * total));
  return (center - margin) / denominator;
}

/**
 * Quello che resta dopo una disattivazione non resta per sempre: la privacy
 * policy promette al massimo 12 mesi. Segnalazioni e "non si e' presentato"
 * dopo 12 mesi se ne vanno per tutti; i voti solo se uno dei due non ha
 * piu' il profilo (fra profili attivi sono la reputazione). Le sospensioni
 * messe da parte spariscono quando scadono. Gira insieme alla classifica:
 * ogni notte dal cron (tradeScheduled).
 */
const RETENTION_MS = 365 * 24 * 60 * 60 * 1000;

async function purgeExpired(db: D1Database): Promise<void> {
  const now = Date.now();
  const before = now - RETENTION_MS;
  await db.batch([
    db.prepare(`DELETE FROM trade_reports WHERE created_at < ?`).bind(before),
    db.prepare(`DELETE FROM trade_no_shows WHERE created_at < ?`).bind(before),
    db.prepare(`DELETE FROM trade_sanctions WHERE suspended_until < ?`).bind(now),
    // Le notifiche spedite servono solo a non rimandarle: un mese basta.
    db.prepare(`DELETE FROM trade_notifications WHERE created_at < ?`).bind(now - 30 * 24 * 60 * 60 * 1000),
    db.prepare(`DELETE FROM trade_wants_seen WHERE seen_at < ?`).bind(now - 90 * 24 * 60 * 60 * 1000),
    db
      .prepare(
        `DELETE FROM trade_ratings WHERE created_at < ?
           AND (from_uid NOT IN (SELECT uid FROM trade_profiles) OR to_uid NOT IN (SELECT uid FROM trade_profiles))`
      )
      .bind(before),
  ]);
}

/**
 * Ricalcola trade_leaderboard per tutti, se e' piu' vecchia di `ttlMs`
 * (vedi leaderboardTtlMs). Conta solo scambi chiusi da entrambi e voti gia'
 * visibili; per ogni coppia, uno ogni 30 giorni.
 */
async function refreshLeaderboard(db: D1Database, ttlMs: number, force = false): Promise<void> {
  if (!force) {
    const last = await db.prepare(`SELECT MAX(computed_at) AS at FROM trade_leaderboard`).first<{ at: number | null }>();
    if (last?.at && Date.now() - last.at < ttlMs) return;
  }
  await purgeExpired(db);
  const now = Date.now();
  const { results: done } = await db
    // Chiusi da tutti e due: non quelli chiusi dal cron dopo 7 giorni (schema 12).
    .prepare(
      `SELECT from_uid, to_uid, COALESCE(closed_at, updated_at) AS at FROM trade_proposals
       WHERE status = 'done' AND done_from IS NOT NULL AND done_to IS NOT NULL ORDER BY at`
    )
    .all<{ from_uid: string; to_uid: string; at: number }>();
  const { results: ratings } = await db
    .prepare(
      `SELECT r.from_uid, r.to_uid, r.mood, r.created_at AS at FROM trade_ratings r
       WHERE r.created_at < ? OR EXISTS (SELECT 1 FROM trade_ratings o WHERE o.proposal_id = r.proposal_id AND o.from_uid = r.to_uid)
       ORDER BY r.created_at`
    )
    .bind(now - RATING_REVEAL_MS)
    .all<{ from_uid: string; to_uid: string; mood: string; at: number }>();
  const { results: profiles } = await db
    .prepare(`SELECT uid, suspended_until FROM trade_profiles`)
    .all<{ uid: string; suspended_until: number | null }>();

  type Stats = { trades: number; partners: Set<string>; good: number; ok: number; bad: number };
  const stats = new Map<string, Stats>();
  const of = (uid: string) => {
    let entry = stats.get(uid);
    if (!entry) {
      entry = { trades: 0, partners: new Set(), good: 0, ok: 0, bad: 0 };
      stats.set(uid, entry);
    }
    return entry;
  };

  // Scambi: per ogni coppia, uno ogni 30 giorni (in ordine di tempo).
  const lastPairTrade = new Map<string, number>();
  for (const trade of done) {
    const pair = [trade.from_uid, trade.to_uid].sort().join('|');
    const last = lastPairTrade.get(pair);
    if (last !== undefined && trade.at - last < PAIR_WINDOW_MS) continue;
    lastPairTrade.set(pair, trade.at);
    for (const [me, other] of [[trade.from_uid, trade.to_uid], [trade.to_uid, trade.from_uid]]) {
      const entry = of(me);
      entry.trades += 1;
      entry.partners.add(other);
    }
  }
  // Voti: la stessa regola, per chi vota e chi riceve.
  const lastPairRating = new Map<string, number>();
  for (const rating of ratings) {
    const pair = `${rating.from_uid}>${rating.to_uid}`;
    const last = lastPairRating.get(pair);
    if (last !== undefined && rating.at - last < PAIR_WINDOW_MS) continue;
    lastPairRating.set(pair, rating.at);
    const entry = of(rating.to_uid);
    if (rating.mood === 'good' || rating.mood === 'ok' || rating.mood === 'bad') entry[rating.mood] += 1;
  }

  const suspended = new Set(profiles.filter((p) => (p.suspended_until ?? 0) > now).map((p) => p.uid));
  const rows: unknown[][] = [];
  for (const [uid, entry] of stats) {
    const judged = entry.good + entry.bad;
    const positive = judged === 0 ? 1 : entry.good / judged;
    const tier = positive >= TIER_MIN_POSITIVE ? TIERS.find((t) => entry.trades >= t.trades)?.tier ?? null : null;
    const eligible = entry.trades >= LEADERBOARD_MIN_TRADES && entry.partners.size >= LEADERBOARD_MIN_PARTNERS && !suspended.has(uid);
    rows.push([uid, entry.trades, entry.partners.size, entry.good, entry.ok, entry.bad, wilsonLower(entry.good, judged), tier, eligible ? 1 : 0, now]);
  }
  const columns = ['uid', 'trades', 'partners', 'good', 'ok', 'bad', 'score', 'tier', 'eligible', 'computed_at'];
  const statements: D1PreparedStatement[] = [db.prepare(`DELETE FROM trade_leaderboard`)];
  const perInsert = Math.floor(MAX_PARAMS_PER_STATEMENT / columns.length);
  for (let i = 0; i < rows.length; i += perInsert) {
    const chunk = rows.slice(i, i + perInsert);
    statements.push(
      db
        .prepare(`INSERT INTO trade_leaderboard (${columns.join(', ')}) VALUES ${chunk.map(() => `(${columns.map(() => '?').join(', ')})`).join(', ')}`)
        .bind(...chunk.flat())
    );
  }
  // Una riga segnaposto se non c'e' ancora nessuno, cosi' MAX(computed_at) vale e non si ricalcola a ogni richiesta.
  if (rows.length === 0) {
    statements.push(db.prepare(`INSERT INTO trade_leaderboard (${columns.join(', ')}) VALUES ('', 0, 0, 0, 0, 0, 0, NULL, 0, ?)`).bind(now));
  }
  await db.batch(statements);
}

/** Il livello delle persone in [uidSql], dall'ultima classifica calcolata. */
async function tiersOf(db: D1Database, uidSql: string, params: unknown[]): Promise<Map<string, string>> {
  const { results } = await db
    .prepare(`SELECT uid, tier FROM trade_leaderboard WHERE tier IS NOT NULL AND uid IN (${uidSql})`)
    .bind(...params)
    .all<{ uid: string; tier: string }>();
  return new Map(results.map((r) => [r.uid, r.tier]));
}

/**
 * GET /v1/trade/leaderboard?scope=zone|italy — le prime 50 posizioni fra chi
 * ha scelto di comparire e ne ha diritto, e sempre la mia, anche fuori dai
 * 50 o fuori classifica (con cosa manca). La zona e' quella dei match.
 */
async function getLeaderboard(db: D1Database, uid: string, url: URL, env: TradeEnv): Promise<Response> {
  await refreshLeaderboard(db, leaderboardTtlMs(env));
  const me = await loadProfile(db, uid);
  if (!me) return json({ error: 'no_profile' }, 404);
  const scope = url.searchParams.get('scope') === 'italy' ? 'italy' : 'zone';
  const cells = cellAndNeighbors(me.geohash5);
  const zoneFilter = scope === 'zone' ? `AND p.geohash5 IN (${cells.map(() => '?').join(', ')})` : '';
  const { results } = await db
    .prepare(
      `SELECT p.uid, p.public_id, p.nickname, p.created_at, p.avatar, p.avatar_animated, l.trades, l.partners, l.good, l.ok, l.bad, l.score, l.tier
       FROM trade_leaderboard l JOIN trade_profiles p ON p.uid = l.uid
       WHERE l.eligible = 1 AND p.leaderboard_opt_in = 1 ${zoneFilter}
       ORDER BY l.score DESC, l.trades DESC, p.created_at ASC`
    )
    .bind(...(scope === 'zone' ? cells : []))
    .all<{ uid: string; public_id: string; nickname: string; created_at: number; avatar: number | null; avatar_animated: number; trades: number; partners: number; good: number; ok: number; bad: number; score: number; tier: string | null }>();

  // I chip piu' ricevuti dei primi 50, per il mini profilo che si apre toccandoli.
  const shown = results.slice(0, LEADERBOARD_SIZE);
  const reputation = await reputationOf(db, 'SELECT value FROM json_each(?)', [JSON.stringify(shown.map((row) => row.uid))]);
  const entry = (row: (typeof results)[number], rank: number) => ({
    rank,
    id: row.public_id,
    nickname: row.nickname,
    tier: row.tier,
    trades: row.trades,
    partners: row.partners,
    positivePct: row.good + row.bad > 0 ? Math.round((row.good * 100) / (row.good + row.bad)) : null,
    good: row.good,
    ok: row.ok,
    bad: row.bad,
    topTags: reputation.get(row.uid)?.topTags ?? [],
    memberSince: row.created_at,
    avatar: row.avatar,
    avatarAnimated: row.avatar_animated === 1,
    isMe: row.uid === uid,
  });
  const myIndex = results.findIndex((row) => row.uid === uid);
  const mine = await db.prepare(`SELECT * FROM trade_leaderboard WHERE uid = ?`).bind(uid).first<{
    trades: number; partners: number; good: number; bad: number; tier: string | null; eligible: number;
  }>();
  // null finche' non ha risposto: l'app chiede quando si entra in classifica.
  const optIn = me.leaderboard_asked_at === null ? null : me.leaderboard_opt_in;
  return json({
    scope,
    entries: shown.map((row, index) => entry(row, index + 1)),
    total: results.length,
    me: {
      rank: myIndex >= 0 ? myIndex + 1 : null,
      optIn: optIn === null ? null : optIn === 1,
      eligible: mine?.eligible === 1,
      trades: mine?.trades ?? 0,
      partners: mine?.partners ?? 0,
      positivePct: mine && mine.good + mine.bad > 0 ? Math.round((mine.good * 100) / (mine.good + mine.bad)) : null,
      tier: mine?.tier ?? null,
      // Quanto manca per entrare: scambi validi e persone diverse.
      missingTrades: Math.max(0, LEADERBOARD_MIN_TRADES - (mine?.trades ?? 0)),
      missingPartners: Math.max(0, LEADERBOARD_MIN_PARTNERS - (mine?.partners ?? 0)),
    },
  });
}

/** PUT /v1/trade/leaderboard/optin — { optIn }: comparire in classifica o no. Si cambia quando si vuole. */
async function putLeaderboardOptIn(request: Request, db: D1Database, uid: string): Promise<Response> {
  const body = await readJson<{ optIn?: boolean }>(request);
  if (typeof body?.optIn !== 'boolean') return json({ error: 'bad_json' }, 400);
  await db
    .prepare(`UPDATE trade_profiles SET leaderboard_opt_in = ?, leaderboard_asked_at = ? WHERE uid = ?`)
    .bind(body.optIn ? 1 : 0, Date.now(), uid)
    .run();
  return json({ optIn: body.optIn });
}

/** Gli sprite animati (Nero e Bianco) arrivano fino a Genesect. */
const AVATAR_MAX = 1025;
const AVATAR_ANIMATED_MAX = 649;

/**
 * Gli avatar di tutti: Pikachu, Eevee, Snorlax e gli starter di ogni
 * generazione. DEVE restare uguale a TradeAvatars.FREE nell'app: un id che
 * l'app mostra gratis e il server no darebbe "premium_required" a chi non paga.
 */
const FREE_AVATARS = new Set([
  25, 133, 143,
  1, 4, 7, 152, 155, 158, 252, 255, 258, 387, 390, 393,
  495, 498, 501, 650, 653, 656, 722, 725, 728, 810, 813, 816, 906, 909, 912,
]);

/**
 * Premium = abbonamento Play valido o mese regalo attivo: le stesse due fonti
 * di /v1/billing/entitlement (src/billing.ts), lette dal catalogo D1, dove le
 * scrive il billing. Senza catalogo collegato, nessuno e' Premium.
 */
async function hasPremium(env: TradeEnv, uid: string): Promise<boolean> {
  const catalog = env.pokevault_catalog;
  if (!catalog) return false;
  if (await activeGiftUntilMs(catalog, uid)) return true;
  const row = await catalog
    .prepare(`SELECT state, expiry_time_ms FROM entitlements WHERE uid = ?1`)
    .bind(uid)
    .first<{ state: string; expiry_time_ms: number | null }>();
  return !!row && isEntitled(row.state, row.expiry_time_ms);
}

/**
 * PUT /v1/trade/avatar — { avatar: 1..1025 | null, animated }: il Pokemon
 * che compare sul podio al posto dell'iniziale. I 30 di FREE_AVATARS sono di
 * tutti; gli altri e gli sprite animati sono Premium, e qui lo si controlla
 * (prima lo decideva solo l'app). Un avatar gia' scelto non si toglie a chi
 * smette di pagare: vale per le scelte nuove.
 */
async function putAvatar(request: Request, db: D1Database, uid: string, env: TradeEnv): Promise<Response> {
  const body = await readJson<{ avatar?: number | null; animated?: boolean }>(request);
  if (!body) return json({ error: 'bad_json' }, 400);
  const avatar = body.avatar ?? null;
  if (avatar !== null && (!Number.isInteger(avatar) || avatar < 1 || avatar > AVATAR_MAX)) return json({ error: 'bad_avatar' }, 400);
  const animated = avatar !== null && avatar <= AVATAR_ANIMATED_MAX && body.animated === true;
  const needsPremium = avatar !== null && (!FREE_AVATARS.has(avatar) || animated);
  if (needsPremium && env.TRADE_AVATAR_PREMIUM_CHECK !== '0' && !(await hasPremium(env, uid))) {
    return json({ error: 'premium_required' }, 403);
  }
  const result = await db
    .prepare(`UPDATE trade_profiles SET avatar = ?, avatar_animated = ?, updated_at = ? WHERE uid = ?`)
    .bind(avatar, animated ? 1 : 0, Date.now(), uid)
    .run();
  if (!result.meta.changes) return json({ error: 'no_profile' }, 404);
  return json({ avatar, avatarAnimated: animated });
}

// ── Segnala e blocca (fase 2f) ──────────────────────────────────────────────
//
// Pensato per chi lo usa male, non solo per chi lo usa bene:
// - bloccare e' privato e vale nei due sensi; all'altro non si dice nulla;
// - segnalare e' anonimo, ma una segnalazione conta per la sospensione
//   automatica solo se il motivo e' grave, fra i due c'e' stato un accordo
//   vero, chi segnala non ha gia' segnalazioni archiviate come infondate e
//   non sta rispondendo a una segnalazione appena ricevuta da quella persona;
// - servono tre persone diverse cosi' in 90 giorni, e la sospensione
//   automatica dura 14 giorni: se non la controlliamo, finisce da sola;
// - le altre restano da guardare (script trade-segnalazioni-staging.mjs);
// - chi segnala non sa se la sua ha contato, ne' se l'altro e' stato sospeso.

const DAY_MS = 24 * 60 * 60 * 1000;
const REPORT_REASONS = new Set(['behavior', 'scam', 'fake_cards', 'nickname', 'other']);
/** I motivi che possono portare alla sospensione automatica. Il nickname e "altro" li guardiamo noi. */
const SERIOUS_REASONS = new Set(['behavior', 'scam', 'fake_cards']);
const REPORTS_PER_DAY = 5;
const REPORT_REPEAT_MS = 30 * DAY_MS;
const REPORT_WINDOW_MS = 90 * DAY_MS;
const REPORT_THRESHOLD = 3;
const REPORT_SUSPENSION_MS = 14 * DAY_MS;
/** Chi e' stato segnalato da qualcuno, se lo segnala a sua volta entro questo tempo, non conta. */
const RETALIATION_MS = 30 * DAY_MS;
/** Con due segnalazioni archiviate come infondate, le nuove non contano piu' da sole. */
const MAX_DISMISSED = 2;
const MAX_BLOCKS = 500;

async function isSuspended(db: D1Database, uid: string): Promise<boolean> {
  const row = await db.prepare(`SELECT suspended_until FROM trade_profiles WHERE uid = ?`).bind(uid).first<{ suspended_until: number | null }>();
  return (row?.suspended_until ?? 0) > Date.now();
}

/** Uno dei due ha bloccato l'altro. */
async function blockedBetween(db: D1Database, a: string, b: string): Promise<boolean> {
  const row = await db
    .prepare(
      `SELECT 1 AS x FROM trade_blocks
       WHERE (blocker_uid = ?1 AND blocked_uid = ?2) OR (blocker_uid = ?2 AND blocked_uid = ?1) LIMIT 1`
    )
    .bind(a, b)
    .first<{ x: number }>();
  return row !== null;
}

/** L'ora dell'appuntamento e' passata (giorno e ora in Italia). */
function meetingPassed(row: Partial<MeetingColumns>): boolean {
  if (!row.meet_slot) return false;
  const slot = JSON.parse(row.meet_slot) as Slot;
  const now = romeNow();
  return now.day > slot.day || (now.day === slot.day && now.time >= slotTime(slot));
}

/** Fuori dai match fino a [until]; una sospensione gia' piu' lunga resta quella. */
async function suspend(db: D1Database, uid: string, until: number, reason: string): Promise<void> {
  await db
    .prepare(
      `UPDATE trade_profiles SET suspended_until = ?2, suspension_reason = ?3
       WHERE uid = ?1 AND (suspended_until IS NULL OR suspended_until < ?2)`
    )
    .bind(uid, until, reason)
    .run();
}

async function profileOfPublicId(db: D1Database, publicId: string): Promise<{ uid: string; nickname: string } | null> {
  if (!/^[0-9a-f]{16}$/.test(publicId)) return null;
  return db.prepare(`SELECT uid, nickname FROM trade_profiles WHERE public_id = ?`).bind(publicId).first();
}

/**
 * Blocca: e le proposte in corso fra i due si annullano. Non quelle con
 * l'appuntamento gia' passato: restano per "Scambio fatto" o "Non si e'
 * presentato", altrimenti bloccare servirebbe a sfuggire al giudizio.
 */
async function applyBlock(db: D1Database, uid: string, target: string): Promise<number> {
  const now = Date.now();
  const { results } = await db
    .prepare(
      `SELECT id, status, meet_slot FROM trade_proposals
       WHERE ((from_uid = ?1 AND to_uid = ?2) OR (from_uid = ?2 AND to_uid = ?1))
         AND status IN ('open', 'accepted', 'scheduled')`
    )
    .bind(uid, target)
    .all<{ id: string; status: string; meet_slot: string | null }>();
  const cancel = results.filter((r) => !(r.status === 'scheduled' && meetingPassed(r)));
  await db.batch([
    db.prepare(`INSERT OR IGNORE INTO trade_blocks (blocker_uid, blocked_uid, created_at) VALUES (?, ?, ?)`).bind(uid, target, now),
    ...cancel.map((r) =>
      db
        .prepare(`UPDATE trade_proposals SET status = 'cancelled', closed_by = ?, updated_at = ? WHERE id = ?`)
        .bind(uid, now, r.id)
    ),
  ]);
  return cancel.length;
}

/** POST /v1/trade/users/:publicId/block */
async function blockUser(db: D1Database, uid: string, publicId: string): Promise<Response> {
  const target = await profileOfPublicId(db, publicId);
  if (!target || target.uid === uid) return json({ error: 'no_user' }, 404);
  const count = await db.prepare(`SELECT COUNT(*) AS n FROM trade_blocks WHERE blocker_uid = ?`).bind(uid).first<{ n: number }>();
  if ((count?.n ?? 0) >= MAX_BLOCKS) return json({ error: 'too_many_blocks', max: MAX_BLOCKS }, 429);
  const cancelled = await applyBlock(db, uid, target.uid);
  return json({ blocked: true, cancelled });
}

/** DELETE /v1/trade/users/:publicId/block */
async function unblockUser(db: D1Database, uid: string, publicId: string): Promise<Response> {
  const target = await profileOfPublicId(db, publicId);
  if (!target) return json({ error: 'no_user' }, 404);
  await db.prepare(`DELETE FROM trade_blocks WHERE blocker_uid = ? AND blocked_uid = ?`).bind(uid, target.uid).run();
  return json({ blocked: false });
}

/** GET /v1/trade/blocks — le persone che ho bloccato (non chi ha bloccato me: quello non si dice). */
async function listBlocks(db: D1Database, uid: string): Promise<Response> {
  const { results } = await db
    .prepare(
      `SELECT p.public_id AS id, p.nickname, b.created_at AS blockedAt
       FROM trade_blocks b JOIN trade_profiles p ON p.uid = b.blocked_uid
       WHERE b.blocker_uid = ? ORDER BY b.created_at DESC`
    )
    .bind(uid)
    .all<{ id: string; nickname: string; blockedAt: number }>();
  return json({ items: results });
}

/**
 * POST /v1/trade/users/:publicId/report — { reason, note?, proposalId?, block? }.
 * Una segnalazione per persona ogni 30 giorni, cinque al giorno in tutto.
 * block (di default si') blocca anche. La risposta non dice se ha contato.
 */
async function reportUser(request: Request, db: D1Database, uid: string, publicId: string): Promise<Response> {
  const body = await readJson<{ reason?: string; note?: string; proposalId?: string; block?: boolean }>(request);
  if (!body) return json({ error: 'bad_json' }, 400);
  const reason = cleanText(body.reason, 20);
  if (!REPORT_REASONS.has(reason)) return json({ error: 'bad_reason' }, 400);
  const note = cleanText(body.note, 300);
  const target = await profileOfPublicId(db, publicId);
  if (!target || target.uid === uid) return json({ error: 'no_user' }, 404);
  const now = Date.now();

  const recent = await db
    .prepare(
      `SELECT (SELECT COUNT(*) FROM trade_reports WHERE reporter_uid = ?1 AND created_at > ?3) AS today,
              (SELECT COUNT(*) FROM trade_reports WHERE reporter_uid = ?1 AND target_uid = ?2 AND created_at > ?4) AS again,
              (SELECT COUNT(*) FROM trade_reports WHERE reporter_uid = ?1 AND status = 'dismissed') AS dismissed,
              (SELECT COUNT(*) FROM trade_reports WHERE reporter_uid = ?2 AND target_uid = ?1 AND created_at > ?5) AS retaliation,
              (SELECT COUNT(*) FROM trade_proposals
                 WHERE ((from_uid = ?1 AND to_uid = ?2) OR (from_uid = ?2 AND to_uid = ?1))
                   AND (status IN ('accepted', 'scheduled', 'done', 'no_show') OR meet_slots IS NOT NULL OR meet_slot IS NOT NULL)) AS deals,
              (SELECT COALESCE(suspended_until, 0) FROM trade_profiles WHERE uid = ?1) AS mySuspension`
    )
    .bind(uid, target.uid, now - DAY_MS, now - REPORT_REPEAT_MS, now - RETALIATION_MS)
    .first<{ today: number; again: number; dismissed: number; retaliation: number; deals: number; mySuspension: number }>();
  if (!recent) return json({ error: 'no_profile' }, 404);
  if (recent.again > 0) return json({ error: 'already_reported' }, 409);
  if (recent.today >= REPORTS_PER_DAY) return json({ error: 'too_many_reports', max: REPORTS_PER_DAY }, 429);

  // La proposta di riferimento, se c'e', deve essere fra loro due.
  let proposalId: string | null = null;
  if (typeof body.proposalId === 'string' && /^[0-9a-f-]{36}$/.test(body.proposalId)) {
    const own = await db
      .prepare(`SELECT id FROM trade_proposals WHERE id = ?3 AND ((from_uid = ?1 AND to_uid = ?2) OR (from_uid = ?2 AND to_uid = ?1))`)
      .bind(uid, target.uid, body.proposalId)
      .first<{ id: string }>();
    proposalId = own?.id ?? null;
  }

  const weight =
    SERIOUS_REASONS.has(reason) &&
    recent.deals > 0 &&
    recent.dismissed < MAX_DISMISSED &&
    recent.retaliation === 0 &&
    recent.mySuspension <= now;

  // Prima la segnalazione, poi il blocco: il blocco annulla le proposte, e
  // l'accordo che da' peso alla segnalazione e' gia' stato contato.
  await db
    .prepare(
      `INSERT INTO trade_reports (id, reporter_uid, target_uid, reason, note, proposal_id, weight, status, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, 'open', ?)`
    )
    .bind(crypto.randomUUID(), uid, target.uid, reason, note, proposalId, weight ? 1 : 0, now)
    .run();
  const block = body.block !== false;
  if (block) await applyBlock(db, uid, target.uid);

  if (weight) {
    const reporters = await db
      .prepare(
        `SELECT COUNT(DISTINCT reporter_uid) AS n FROM trade_reports
         WHERE target_uid = ? AND weight = 1 AND status <> 'dismissed' AND created_at > ?`
      )
      .bind(target.uid, now - REPORT_WINDOW_MS)
      .first<{ n: number }>();
    if ((reporters?.n ?? 0) >= REPORT_THRESHOLD) await suspend(db, target.uid, now + REPORT_SUSPENSION_MS, 'reports');
  }
  return json({ reported: true, blocked: block });
}

// ── Notifiche (fase 3) ──────────────────────────────────────────────────────
//
// Qui le regole: chi avvisare e quando. La consegna (coda, notte, testi,
// Firebase) sta in trade-push.ts.

/** Mette in coda e, se puo' partire subito, spedisce dopo la risposta: la richiesta non aspetta Firebase. */
type Pusher = (items: Outgoing[]) => Promise<void>;

function pusherFor(db: D1Database, env: TradeEnv, ctx?: ExecutionContext): Pusher {
  return async (items) => {
    const work = enqueue(db, items)
      .then((ready) => deliver(db, env, ready))
      .then(() => undefined)
      .catch((error) => console.warn('traderadar push', error));
    if (ctx) ctx.waitUntil(work);
    else await work;
  };
}

async function nicknameOf(db: D1Database, uid: string): Promise<string> {
  const row = await db.prepare(`SELECT nickname FROM trade_profiles WHERE uid = ?`).bind(uid).first<{ nickname: string }>();
  return row?.nickname ?? '';
}

function quantity(items: ProposalItem[]): number {
  return items.reduce((sum, item) => sum + item.qty, 0);
}

const MAX_PUSH_TOKENS = 5;
const NOTIFY_KINDS = ['proposals', 'meetings', 'reminders', 'after', 'wants'] as const;

/** PUT /v1/trade/push — { token, lang }: questo telefono riceve le notifiche di questo utente. */
async function putPushToken(request: Request, db: D1Database, uid: string): Promise<Response> {
  const body = await readJson<{ token?: string; lang?: string }>(request);
  const token = typeof body?.token === 'string' ? body.token.trim() : '';
  if (!/^[A-Za-z0-9:_-]{20,4096}$/.test(token)) return json({ error: 'bad_token' }, 400);
  const now = Date.now();
  await db.batch([
    // Un token e' di un telefono: se ci entra un altro account, passa a lui.
    db
      .prepare(
        `INSERT INTO trade_push_tokens (token, uid, lang, created_at, updated_at) VALUES (?1, ?2, ?3, ?4, ?4)
         ON CONFLICT(token) DO UPDATE SET uid = excluded.uid, lang = excluded.lang, updated_at = excluded.updated_at`
      )
      .bind(token, uid, body?.lang === 'en' ? 'en' : 'it', now),
    db
      .prepare(
        `DELETE FROM trade_push_tokens WHERE uid = ?1 AND token NOT IN
           (SELECT token FROM trade_push_tokens WHERE uid = ?1 ORDER BY updated_at DESC LIMIT ${MAX_PUSH_TOKENS})`
      )
      .bind(uid),
  ]);
  return json({ ok: true });
}

/** DELETE /v1/trade/push — { token }: questo telefono non riceve piu'. */
async function deletePushToken(request: Request, db: D1Database, uid: string): Promise<Response> {
  const body = await readJson<{ token?: string }>(request);
  if (typeof body?.token !== 'string') return json({ error: 'bad_token' }, 400);
  await db.prepare(`DELETE FROM trade_push_tokens WHERE token = ? AND uid = ?`).bind(body.token.trim(), uid).run();
  return json({ ok: true });
}

/** PUT /v1/trade/notify — { proposals?, meetings?, reminders?, after?, wants? }: solo i campi presenti cambiano. */
async function putNotifyPrefs(request: Request, db: D1Database, uid: string): Promise<Response> {
  const body = await readJson<Record<string, unknown>>(request);
  if (!body) return json({ error: 'bad_json' }, 400);
  const me = await loadProfile(db, uid);
  if (!me) return json({ error: 'no_profile' }, 404);
  const prefs = prefsOf(me.notify_prefs);
  for (const kind of NOTIFY_KINDS) {
    if (typeof body[kind] === 'boolean') prefs[kind] = body[kind] as boolean;
  }
  await db.prepare(`UPDATE trade_profiles SET notify_prefs = ? WHERE uid = ?`).bind(JSON.stringify(prefs), uid).run();
  return json(prefs);
}

/** Il promemoria: 2 ore prima; per gli appuntamenti del mattino presto, la sera prima alle 21. */
const REMINDER_BEFORE_MS = 2 * 60 * 60 * 1000;
const EARLY_MEETING = '10:00';
const EVENING_BEFORE = '21:00';
/** "Com'e' andata?": 2 ore dopo l'ora fissata, finche' lo scambio non si chiude da solo. */
const AFTER_MS = 2 * 60 * 60 * 1000;
const AFTER_UNTIL_MS = MEETING_AUTO_CLOSE_MS;

/**
 * Gli appuntamenti passati da 7 giorni senza un esito: se uno dei due aveva
 * segnato "Scambio fatto" lo scambio si chiude come fatto (l'altro ha avuto
 * una settimana di "com'e' andata?" senza dire niente); se nessuno dei due
 * aveva segnato niente scade, senza penalita' per nessuno, e le carte
 * riservate tornano libere.
 */
async function closeStaleMeetings(db: D1Database): Promise<void> {
  const { results } = await db
    .prepare(
      `SELECT p.id, p.from_uid, p.to_uid, p.meet_slot, p.done_from, p.done_to,
              f.nickname AS from_nick, t.nickname AS to_nick
       FROM trade_proposals p
       JOIN trade_profiles f ON f.uid = p.from_uid
       JOIN trade_profiles t ON t.uid = p.to_uid
       WHERE p.status = 'scheduled' AND p.meet_slot IS NOT NULL`
    )
    .all<{ id: string; from_uid: string; to_uid: string; meet_slot: string; done_from: number | null; done_to: number | null; from_nick: string; to_nick: string }>();
  const items: Outgoing[] = [];
  for (const p of results) {
    const slot = JSON.parse(p.meet_slot) as Slot;
    const now = Date.now();
    if (now < romeToEpoch(slot.day, slotTime(slot)) + MEETING_AUTO_CLOSE_MS) continue;
    const data = { screen: 'proposals', proposalId: p.id };
    const tag = `proposal:${p.id}`;

    if (!p.done_from && !p.done_to) {
      const expired = await db
        .prepare(
          `UPDATE trade_proposals SET status = 'expired', closed_at = ?1, updated_at = ?1
           WHERE id = ?2 AND status = 'scheduled' AND done_from IS NULL AND done_to IS NULL`
        )
        .bind(now, p.id)
        .run();
      if (!expired.meta.changes) continue;
      items.push(
        { id: `expired:${p.id}:${p.from_uid}`, uid: p.from_uid, kind: 'meetings', template: 'meeting_expired', args: { nick: p.to_nick }, data, tag },
        { id: `expired:${p.id}:${p.to_uid}`, uid: p.to_uid, kind: 'meetings', template: 'meeting_expired', args: { nick: p.from_nick }, data, tag }
      );
      continue;
    }

    // Uno solo l'aveva segnato (con tutti e due sarebbe gia' 'done'). Si
    // aggiorna la collezione e si vota, ma non e' uno scambio "chiuso da
    // tutti e due": niente trades_done, e la classifica non lo conta.
    const closed = await db
      .prepare(
        `UPDATE trade_proposals SET status = 'done', closed_at = ?1, updated_at = ?1
         WHERE id = ?2 AND status = 'scheduled' AND (done_from IS NULL) <> (done_to IS NULL)`
      )
      .bind(now, p.id)
      .run();
    if (!closed.meta.changes) continue;
    const marked = p.done_from ? { uid: p.from_uid, other: p.to_nick } : { uid: p.to_uid, other: p.from_nick };
    const silent = p.done_from ? { uid: p.to_uid, other: p.from_nick } : { uid: p.from_uid, other: p.to_nick };
    items.push(
      { id: `closed:${p.id}:${marked.uid}`, uid: marked.uid, kind: 'after', template: 'trade_closed', args: { nick: marked.other }, data, tag },
      { id: `autoclosed:${p.id}:${silent.uid}`, uid: silent.uid, kind: 'after', template: 'trade_auto_closed', args: { nick: silent.other }, data, tag }
    );
  }
  await enqueue(db, items);
}

async function enqueueMeetingNotifications(db: D1Database): Promise<void> {
  const today = romeNow().day;
  const { results } = await db
    .prepare(
      `SELECT p.id, p.from_uid, p.to_uid, p.meet_slot, p.done_from, p.done_to,
              f.nickname AS from_nick, t.nickname AS to_nick, s.name AS spot
       FROM trade_proposals p
       JOIN trade_profiles f ON f.uid = p.from_uid
       JOIN trade_profiles t ON t.uid = p.to_uid
       LEFT JOIN trade_spots s ON s.id = p.meet_spot_id
       WHERE p.status = 'scheduled' AND p.meet_slot IS NOT NULL`
    )
    .all<{ id: string; from_uid: string; to_uid: string; meet_slot: string; done_from: number | null; done_to: number | null; from_nick: string; to_nick: string; spot: string | null }>();
  const now = Date.now();
  const items: Outgoing[] = [];
  for (const p of results) {
    const slot = JSON.parse(p.meet_slot) as Slot;
    if (slot.day < addDays(today, -8) || slot.day > addDays(today, 1)) continue;
    const time = slotTime(slot);
    const at = romeToEpoch(slot.day, time);
    const remindAt = time < EARLY_MEETING ? romeToEpoch(addDays(slot.day, -1), EVENING_BEFORE) : at - REMINDER_BEFORE_MS;
    const data = { screen: 'proposals', proposalId: p.id };
    const parties = [
      { uid: p.from_uid, nick: p.to_nick, done: p.done_from },
      { uid: p.to_uid, nick: p.from_nick, done: p.done_to },
    ];
    for (const party of parties) {
      if (now >= remindAt && now < at) {
        items.push({
          // Con l'ora nella chiave: se l'appuntamento cambia, il promemoria nuovo parte.
          id: `reminder:${p.id}:${slot.day}T${time}:${party.uid}`, uid: party.uid, kind: 'reminders', template: 'reminder',
          args: { nick: party.nick, day: slot.day, time, spot: p.spot ?? '' }, data, tag: `proposal:${p.id}`,
        });
      }
      if (!party.done && now >= at + AFTER_MS && now < at + AFTER_UNTIL_MS) {
        items.push({
          id: `after:${p.id}:${party.uid}`, uid: party.uid, kind: 'after', template: 'after',
          args: { nick: party.nick }, data, tag: `proposal:${p.id}`,
        });
      }
    }
  }
  await enqueue(db, items);
}

/** Le carte cercate: una volta al giorno, la sera (18-21), solo a chi l'ha chiesto. */
const WANTS_FROM = '18:00';
const WANTS_TO = '21:00';
const WANTS_USERS_PER_RUN = 100;

async function enqueueWantsDigest(db: D1Database, env: TradeEnv): Promise<void> {
  const { day, time } = romeNow();
  if (time < WANTS_FROM || time >= WANTS_TO) return;
  const now = Date.now();
  const { results: users } = await db
    .prepare(
      `SELECT p.uid, p.geohash5 FROM trade_profiles p
       WHERE p.paused = 0 AND (p.suspended_until IS NULL OR p.suspended_until < ?1)
         AND json_extract(p.notify_prefs, '$.wants') = 1
         AND NOT EXISTS (SELECT 1 FROM trade_notifications n WHERE n.id = 'wants:' || p.uid || ':' || ?2)
       LIMIT ${WANTS_USERS_PER_RUN}`
    )
    .bind(now, day)
    .all<{ uid: string; geohash5: string }>();
  if (users.length === 0) return;
  const labels = await catalogCardLabels(env);
  for (const user of users) {
    const cells = cellAndNeighbors(user.geohash5);
    // Le carte cercate che qualcuno vicino offre con la campanella accesa, non
    // gia' possedute, non gia' segnalate da quella persona, fra chi non si e' bloccato.
    const { results: found } = await db
      .prepare(
        `SELECT DISTINCT h.card_key, h.uid AS holder FROM trade_haves h JOIN trade_profiles o ON o.uid = h.uid
         WHERE o.geohash5 IN (${cells.map(() => '?').join(', ')}) AND o.uid <> ? AND o.paused = 0
           AND (o.suspended_until IS NULL OR o.suspended_until < ?)
           AND h.notify = 1
           AND h.card_key IN (SELECT card_key FROM trade_wants WHERE uid = ?)
           AND NOT EXISTS (SELECT 1 FROM trade_owned w WHERE w.uid = ? AND w.card_key = h.card_key)
           AND NOT EXISTS (SELECT 1 FROM trade_wants_seen s WHERE s.uid = ? AND s.card_key = h.card_key AND s.holder_uid = h.uid)
           AND o.uid NOT IN (SELECT blocked_uid FROM trade_blocks WHERE blocker_uid = ?)
           AND o.uid NOT IN (SELECT blocker_uid FROM trade_blocks WHERE blocked_uid = ?)
         LIMIT 200`
      )
      .bind(...cells, user.uid, now, user.uid, user.uid, user.uid, user.uid, user.uid)
      .all<{ card_key: string; holder: string }>();
    const id = `wants:${user.uid}:${day}`;
    if (found.length === 0) {
      // Segnato lo stesso: oggi questa persona e' gia' stata controllata.
      await db
        .prepare(`INSERT OR IGNORE INTO trade_notifications (id, uid, kind, payload, created_at, send_after, sent_at, status) VALUES (?, ?, 'wants', '{}', ?, ?, ?, 'empty')`)
        .bind(id, user.uid, now, now, now)
        .run();
      continue;
    }
    const keys = [...new Set(found.map((f) => f.card_key))];
    await enqueue(db, [{
      id, uid: user.uid, kind: 'wants', template: 'wants',
      args: { count: keys.length, names: keys.slice(0, 3).map((k) => labels.get(k)?.name ?? k) },
      data: { screen: 'matches' }, tag: 'wants',
    }]);
    await db.batch(
      found.map((f) =>
        db.prepare(`INSERT OR IGNORE INTO trade_wants_seen (uid, card_key, holder_uid, seen_at) VALUES (?, ?, ?, ?)`).bind(user.uid, f.card_key, f.holder, now)
      )
    );
  }
}

/**
 * Il cron di TradeRadar (ogni 15 minuti): chiude gli appuntamenti rimasti
 * senza esito, promemoria e "com'e' andata", carte cercate la sera, poi
 * spedisce quello che e' pronto (anche cio' che di notte era stato rimandato
 * al mattino). Il giro delle 02:00 UTC rifa' anche classifica e pulizia.
 */
export async function tradeScheduled(env: TradeEnv, scheduledTime = Date.now()): Promise<void> {
  const db = env.trade_db;
  if (!db || env.TRADE_ENABLED !== '1') return;
  const at = new Date(scheduledTime);
  if (at.getUTCHours() === LEADERBOARD_NIGHTLY_HOUR_UTC && at.getUTCMinutes() < 15) {
    // Un solo giro su quattro dell'ora, e un errore qui non ferma il resto.
    await refreshLeaderboard(db, 0, true).catch((error) => console.warn('traderadar classifica notturna', error));
  }
  try {
    // Un errore qui non deve fermare promemoria e notifiche.
    await closeStaleMeetings(db).catch((error) => console.warn('traderadar chiusura automatica', error));
    await enqueueMeetingNotifications(db);
    await enqueueWantsDigest(db, env);
  } finally {
    await deliver(db, env);
  }
}

// ── Router ──────────────────────────────────────────────────────────────────

/**
 * Risponde alle rotte /v1/trade/*, o restituisce null se non sono di sua
 * competenza o se il modulo e' spento: il chiamante prosegue come prima.
 */
export async function handleTradeRequest(
  request: Request,
  pathname: string,
  env: TradeEnv,
  ctx?: ExecutionContext
): Promise<Response | null> {
  if (!pathname.startsWith('/v1/trade/')) return null;
  if (env.TRADE_ENABLED !== '1') return null;

  const db = env.trade_db;
  if (!db) return json({ error: 'trade_db non configurato' }, 500);

  // GET /v1/trade/health — senza utente: dice se il modulo e il suo database
  // rispondono. Serve a verificare un deploy senza avere un account.
  if (pathname === '/v1/trade/health' && request.method === 'GET') {
    const version = await schemaVersion(db);
    return json({ ok: version !== null, service: 'traderadar', schemaVersion: version }, version !== null ? 200 : 503);
  }

  const uid = await verifyFirebaseIdToken(bearerToken(request), env);
  if (!uid) return json({ error: 'ID token Firebase assente o non valido' }, 401);

  const method = request.method;

  if (pathname === '/v1/trade/me' && method === 'GET') {
    return json({ uid, schemaVersion: await schemaVersion(db) });
  }

  if (pathname === '/v1/trade/profile') {
    if (method === 'GET') return getProfile(db, uid, env);
    if (method === 'PUT') return putProfile(request, db, uid);
    if (method === 'DELETE') return deleteProfile(db, uid);
  }

  // Tutto il resto richiede un profilo attivato.
  if (!(await loadProfile(db, uid))) return json({ error: 'no_profile' }, 404);
  const push = pusherFor(db, env, ctx);

  if (pathname === '/v1/trade/push') {
    if (method === 'PUT') return putPushToken(request, db, uid);
    if (method === 'DELETE') return deletePushToken(request, db, uid);
  }
  if (pathname === '/v1/trade/notify' && method === 'PUT') return putNotifyPrefs(request, db, uid);

  if (pathname === '/v1/trade/haves') {
    if (method === 'GET') return getHaves(db, uid);
    if (method === 'PUT') return putHaves(request, db, uid);
  }
  if (pathname === '/v1/trade/wants' && method === 'PUT') return putWants(request, db, uid);
  if (pathname === '/v1/trade/owned' && method === 'PUT') return putOwned(request, db, uid);
  if (pathname === '/v1/trade/matches' && method === 'GET') return getMatches(db, uid, env);

  if (pathname === '/v1/trade/proposals') {
    if (method === 'GET') return listProposals(db, uid, env);
    if (method === 'POST') return createProposal(request, db, uid, push);
  }
  const action = /^\/v1\/trade\/proposals\/([0-9a-f-]{36})\/(accept|decline|cancel|counter)$/.exec(pathname);
  if (action && method === 'POST') return actOnProposal(request, db, uid, action[1], action[2], push);
  const meeting = /^\/v1\/trade\/proposals\/([0-9a-f-]{36})\/(spots|meeting|meeting\/confirm)$/.exec(pathname);
  if (meeting) {
    if (meeting[2] === 'spots' && method === 'GET') return getProposalSpots(db, uid, meeting[1]);
    if (meeting[2] === 'meeting' && method === 'POST') return meetingAction(request, db, uid, meeting[1], false, push);
    if (meeting[2] === 'meeting/confirm' && method === 'POST') return meetingAction(request, db, uid, meeting[1], true, push);
  }
  const closing = /^\/v1\/trade\/proposals\/([0-9a-f-]{36})\/(done|noshow|dispute|rate|spotvote)$/.exec(pathname);
  if (closing && method === 'POST') {
    if (closing[2] === 'done') return markDone(db, uid, closing[1], push);
    if (closing[2] === 'noshow') return markNoShow(db, uid, closing[1], push);
    if (closing[2] === 'dispute') return disputeNoShow(db, uid, closing[1]);
    if (closing[2] === 'rate') return rateTrade(request, db, uid, closing[1]);
    return voteSpot(request, db, uid, closing[1]);
  }
  if (pathname === '/v1/trade/leaderboard' && method === 'GET') return getLeaderboard(db, uid, new URL(request.url), env);
  if (pathname === '/v1/trade/leaderboard/optin' && method === 'PUT') return putLeaderboardOptIn(request, db, uid);
  if (pathname === '/v1/trade/avatar' && method === 'PUT') return putAvatar(request, db, uid, env);
  if (pathname === '/v1/trade/spots/search' && method === 'GET') return searchSpots(db, uid, new URL(request.url));
  if (pathname === '/v1/trade/spots' && method === 'POST') return addSpot(request, db, uid);
  if (pathname === '/v1/trade/spots/cell' && method === 'POST') return putCellSpots(request, db);
  if (pathname === '/v1/trade/blocks' && method === 'GET') return listBlocks(db, uid);
  const userAction = /^\/v1\/trade\/users\/([0-9a-f]{16})\/(block|report)$/.exec(pathname);
  if (userAction) {
    if (userAction[2] === 'block' && method === 'POST') return blockUser(db, uid, userAction[1]);
    if (userAction[2] === 'block' && method === 'DELETE') return unblockUser(db, uid, userAction[1]);
    if (userAction[2] === 'report' && method === 'POST') return reportUser(request, db, uid, userAction[1]);
  }
  const userHaves = /^\/v1\/trade\/users\/([0-9a-f]{16})\/haves$/.exec(pathname);
  if (userHaves && method === 'GET') return getUserHaves(db, uid, userHaves[1], env);

  return json({ error: 'unknown /v1/trade route' }, 404);
}
