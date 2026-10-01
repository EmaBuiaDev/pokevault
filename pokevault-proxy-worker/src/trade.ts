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
import { GEOHASH5_REGEX, cellAndNeighbors } from './geohash';

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
         (uid, nickname, geohash5, adult_confirmed_at, collection_consent_at, paused, created_at, updated_at)
       VALUES (?1, ?2, ?3, ?4, ?4, ?5, ?4, ?4)
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
  return json({
    items: results.map((r) => ({
      key: r.card_key, variant: r.variant, condition: r.condition, language: r.language, qty: r.qty,
      manual: r.manual === 1, notify: r.notify === 1,
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
    .prepare(`SELECT uid, nickname, geohash5, trades_done, created_at FROM trade_profiles WHERE uid IN (${nearby.sql})`)
    .bind(...nearby.params)
    .all<{ uid: string; nickname: string; geohash5: string; trades_done: number; created_at: number }>();
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
  const havesByUid = new Map<string, typeof theirHaves.results>();
  for (const row of theirHaves.results) {
    const list = havesByUid.get(row.uid) ?? [];
    list.push(row);
    havesByUid.set(row.uid, list);
  }

  const myHaves = (
    await db
      .prepare(`SELECT card_key, variant, condition, language, qty FROM trade_haves WHERE uid = ?`)
      .bind(uid)
      .all<{ card_key: string; variant: string; condition: string; language: string; qty: number }>()
  ).results;

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

  return json({ error: 'unknown /v1/trade route' }, 404);
}
