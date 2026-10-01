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

import { verifyFirebaseIdToken } from './billing';
import { GEOHASH5_REGEX, cellAndNeighbors, cellCenter, encode } from './geohash';

export interface TradeEnv {
  /** "1" accende il modulo. Assente in produzione. */
  TRADE_ENABLED?: string;
  /** D1 degli scambi (staging: pokevault-trade-staging). */
  trade_db?: D1Database;
  /** Catalogo, in sola lettura: dimensione dei set. */
  pokevault_catalog?: D1Database;
  /** Progetto Firebase contro cui si verifica l'ID token. */
  FIREBASE_PROJECT_ID?: string;
  CACHE?: KVNamespace;
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
}

async function loadProfile(db: D1Database, uid: string): Promise<ProfileRow | null> {
  return db
    .prepare(
      `SELECT uid, nickname, geohash5, paused, owned_hash, trades_done, created_at
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

  const row = await loadProfile(db, uid);
  return json(row ? profileJson(row) : {});
}

async function getProfile(db: D1Database, uid: string): Promise<Response> {
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
  return json(profileJson(row, counts ?? { haves: 0, wants: 0, owned: 0 }));
}

/** Disattivazione: via tutto quello che il server sa dell'utente. */
async function deleteProfile(db: D1Database, uid: string): Promise<Response> {
  await db.batch([
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
          ORDER BY updated_at DESC LIMIT ${MAX_CANDIDATES}`,
    params: [...cells, uid],
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
    .prepare(`SELECT uid, paused FROM trade_profiles WHERE public_id = ?`)
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
async function getUserHaves(db: D1Database, publicId: string, env: TradeEnv): Promise<Response> {
  const user = await uidOfPublicId(db, publicId);
  if (!user || user.paused === 1) return json({ error: 'no_user' }, 404);
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
async function createProposal(request: Request, db: D1Database, uid: string): Promise<Response> {
  const body = await readJson<{ to?: string; give?: unknown; take?: unknown }>(request);
  if (!body) return json({ error: 'bad_json' }, 400);
  const target = await uidOfPublicId(db, cleanText(body.to, 16));
  if (!target || target.paused === 1 || target.uid === uid) return json({ error: 'no_user' }, 404);
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
async function actOnProposal(request: Request, db: D1Database, uid: string, id: string, action: string): Promise<Response> {
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
    await close('cancelled');
    return json({ status: 'cancelled' });
  }

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
    .all<ProposalRow & MeetingColumns & { other_public_id: string; other_nickname: string; other_cell: string; other_trades: number }>();
  if (proposals.length === 0) return json({ proposals: [] });

  const spotIds = [...new Set(proposals.map((p) => p.meet_spot_id).filter((id): id is string => !!id))];
  const spots = spotIds.length === 0
    ? []
    : (await db.prepare(`SELECT * FROM trade_spots WHERE id IN (SELECT value FROM json_each(?))`).bind(JSON.stringify(spotIds)).all<SpotRow>()).results;

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
      return {
        id: p.id,
        status: p.status,
        revision: p.revision,
        myTurn: p.status === 'open' && p.turn_uid === uid,
        // Serve una mia mossa: rispondere alla proposta, o confermare l'appuntamento.
        actionNeeded: (p.status === 'open' && p.turn_uid === uid) || meetingToConfirm,
        meeting: {
          status: p.meet_status,
          byMe: p.meet_by === uid,
          spot: spot ? spotJson(spot, center.lat, center.lon) : null,
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

function spotJson(spot: SpotRow, fromLat: number, fromLon: number) {
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
  const score = (spot: SpotRow) => (SPOT_KIND_RANK[spot.kind] ?? 1) - distanceKm(lat, lon, spot.lat, spot.lon) / 3;
  const spots = results.sort((a, b) => score(b) - score(a)).slice(0, 25).map((spot) => spotJson(spot, lat, lon));
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
async function geocode(query: string, near: { lat: number; lon: number }): Promise<{ lat: number; lon: number } | null> {
  const params = new URLSearchParams({ q: query, limit: '5', lang: 'default', lat: near.lat.toFixed(4), lon: near.lon.toFixed(4) });
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
    const found = (address ? await geocode(`${address}, ${city}`, center) : null) ?? (await geocode(city, center));
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

interface Slot {
  day: string;
  /** "HH:mm", dalle 07:00 alle 23:00. Le prime prove (01/10) avevano solo la fascia. */
  time?: string;
  part: string;
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
async function meetingAction(request: Request, db: D1Database, uid: string, id: string, confirm: boolean): Promise<Response> {
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
  return json({ status: 'accepted', meeting: 'proposed' });
}

// ── Router ──────────────────────────────────────────────────────────────────

/**
 * Risponde alle rotte /v1/trade/*, o restituisce null se non sono di sua
 * competenza o se il modulo e' spento: il chiamante prosegue come prima.
 */
export async function handleTradeRequest(
  request: Request,
  pathname: string,
  env: TradeEnv
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
    if (method === 'GET') return getProfile(db, uid);
    if (method === 'PUT') return putProfile(request, db, uid);
    if (method === 'DELETE') return deleteProfile(db, uid);
  }

  // Tutto il resto richiede un profilo attivato.
  if (!(await loadProfile(db, uid))) return json({ error: 'no_profile' }, 404);

  if (pathname === '/v1/trade/haves') {
    if (method === 'GET') return getHaves(db, uid);
    if (method === 'PUT') return putHaves(request, db, uid);
  }
  if (pathname === '/v1/trade/wants' && method === 'PUT') return putWants(request, db, uid);
  if (pathname === '/v1/trade/owned' && method === 'PUT') return putOwned(request, db, uid);
  if (pathname === '/v1/trade/matches' && method === 'GET') return getMatches(db, uid, env);

  if (pathname === '/v1/trade/proposals') {
    if (method === 'GET') return listProposals(db, uid, env);
    if (method === 'POST') return createProposal(request, db, uid);
  }
  const action = /^\/v1\/trade\/proposals\/([0-9a-f-]{36})\/(accept|decline|cancel|counter)$/.exec(pathname);
  if (action && method === 'POST') return actOnProposal(request, db, uid, action[1], action[2]);
  const meeting = /^\/v1\/trade\/proposals\/([0-9a-f-]{36})\/(spots|meeting|meeting\/confirm)$/.exec(pathname);
  if (meeting) {
    if (meeting[2] === 'spots' && method === 'GET') return getProposalSpots(db, uid, meeting[1]);
    if (meeting[2] === 'meeting' && method === 'POST') return meetingAction(request, db, uid, meeting[1], false);
    if (meeting[2] === 'meeting/confirm' && method === 'POST') return meetingAction(request, db, uid, meeting[1], true);
  }
  if (pathname === '/v1/trade/spots/search' && method === 'GET') return searchSpots(db, uid, new URL(request.url));
  if (pathname === '/v1/trade/spots' && method === 'POST') return addSpot(request, db, uid);
  if (pathname === '/v1/trade/spots/cell' && method === 'POST') return putCellSpots(request, db);
  const userHaves = /^\/v1\/trade\/users\/([0-9a-f]{16})\/haves$/.exec(pathname);
  if (userHaves && method === 'GET') return getUserHaves(db, userHaves[1], env);

  return json({ error: 'unknown /v1/trade route' }, 404);
}
