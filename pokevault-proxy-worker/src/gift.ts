/**
 * Codici regalo da 1 mese.
 *
 * Due forme dello stesso oggetto:
 *  - il codice AMICO, uno per account, che l'utente condivide per regalare un
 *    mese di premium a chi ancora non ce l'ha;
 *  - i codici promo monouso, creati a lotti dall'admin per giveaway e supporto.
 *
 * Il diritto lo decide questo Worker, mai il client: l'uid arriva dall'ID token
 * Firebase verificato e il mese concesso finisce in D1, dove
 * /v1/billing/entitlement lo somma all'abbonamento Play.
 *
 * Contro il multi-account (vedi schema/010_gift_codes.sql) valgono tre vincoli:
 * un riscatto per account, uno per dispositivo, e un tetto per codice.
 */

import { verifyFirebaseIdToken, type BillingEnv } from './billing';

export interface GiftEnv extends BillingEnv {
  /** Segreto con cui si derivano i codici AMICO e si anonimizzano i device id. */
  GIFT_CODE_SECRET?: string;
  /** Segreto che protegge la creazione dei lotti promo. */
  GIFT_ADMIN_SECRET?: string;
}

/** Giorni regalati da un riscatto, salvo diversa indicazione sulla riga. */
const DEFAULT_GRANT_DAYS = 30;

/**
 * Quanti amici puo' invitare un singolo account.
 *
 * Non e' illimitato di proposito: un codice AMICO finito su un gruppo Telegram
 * regalerebbe un mese a chiunque passi di li'.
 */
const FRIEND_CODE_MAX_REDEMPTIONS = 5;

/** Alfabeto senza caratteri confondibili: niente I/1, O/0, S/5, B/8. */
const CODE_ALPHABET = 'ACDEFGHJKLMNPQRTUVWXYZ2346789';

const FRIEND_CODE_PREFIX = 'AMICO';
const FRIEND_CODE_LENGTH = 8;

/** Tetto giornaliero di riscatti per indirizzo IP. */
const IP_RATE_LIMIT = 3;
const IP_RATE_WINDOW_SECONDS = 86_400;

/**
 * Motivi di rifiuto, in forma stabile.
 *
 * Il client li traduce: mandare qui la frase in italiano vorrebbe dire
 * rispedire una stringa italiana a un utente che ha l'app in inglese, e
 * ricompilare il Worker per correggere un refuso.
 */
export type GiftRejection =
  | 'code_missing'
  | 'code_not_found'
  | 'code_disabled'
  | 'code_expired'
  | 'code_exhausted'
  | 'own_code'
  | 'already_redeemed'
  | 'device_already_redeemed'
  | 'rate_limited';

// ── Utilita' ────────────────────────────────────────────────────────────────

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    // Un codice e uno stato di riscatto sono per utente: non vanno mai cachati.
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
  });
}

function bearerToken(request: Request): string {
  const header = request.headers.get('authorization') ?? '';
  return header.toLowerCase().startsWith('bearer ') ? header.slice(7).trim() : '';
}

/**
 * Porta il codice alla forma canonica.
 *
 * Chi lo riceve su WhatsApp lo incolla con spazi, trattini e minuscole: se la
 * ricerca fosse letterale il codice giusto risulterebbe inesistente.
 */
export function normalizeGiftCode(raw: string): string {
  return raw.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

async function hmacBytes(secret: string, message: string): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret) as BufferSource,
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signature = await crypto.subtle.sign(
    'HMAC',
    key,
    new TextEncoder().encode(message) as BufferSource
  );
  return new Uint8Array(signature);
}

function toHex(bytes: Uint8Array): string {
  let out = '';
  for (const b of bytes) out += b.toString(16).padStart(2, '0');
  return out;
}

/**
 * Codice AMICO di un account, derivato e non sorteggiato.
 *
 * Deterministico apposta: lo stesso uid da' sempre lo stesso codice, quindi
 * l'utente puo' averlo gia' condiviso e ritrovarlo identico dopo un
 * reinstallo, un cambio telefono o un ripristino del database.
 */
export async function friendCodeFor(uid: string, secret: string): Promise<string> {
  const digest = await hmacBytes(secret, `friend-code:${uid}`);
  let body = '';
  for (let i = 0; i < FRIEND_CODE_LENGTH; i++) {
    body += CODE_ALPHABET[digest[i] % CODE_ALPHABET.length];
  }
  return `${FRIEND_CODE_PREFIX}${body}`;
}

/**
 * Impronta del dispositivo, non il dispositivo.
 *
 * L'ANDROID_ID grezzo non viene mai salvato: serve soltanto a rispondere "da
 * questo telefono e' gia' partito un riscatto?", e un HMAC con segreto
 * risponde a quella domanda senza conservare un identificatore rintracciabile.
 */
async function deviceHash(deviceId: string, secret: string): Promise<string | null> {
  const trimmed = deviceId.trim();
  if (!trimmed) return null;
  return toHex(await hmacBytes(secret, `device:${trimmed}`));
}

// ── Rate limit per IP ───────────────────────────────────────────────────────

/**
 * Freno morbido sugli IP.
 *
 * Non e' un vincolo di correttezza (una rete mobile condivide un IP fra molti
 * utenti veri, e una VPN lo cambia a comando): serve solo a rendere lento chi
 * prova codici a tentativi, non a identificare qualcuno.
 */
async function overIpRateLimit(request: Request, env: GiftEnv): Promise<boolean> {
  const cache = env.CACHE;
  const ip = request.headers.get('cf-connecting-ip');
  if (!cache || !ip) return false;

  const key = `gift:ip:${ip}`;
  const current = Number((await cache.get(key)) ?? '0');
  if (current >= IP_RATE_LIMIT) return true;

  await cache.put(key, String(current + 1), { expirationTtl: IP_RATE_WINDOW_SECONDS });
  return false;
}

// ── Lettura e creazione del codice AMICO ────────────────────────────────────

interface GiftCodeRow {
  code: string;
  kind: string;
  owner_uid: string | null;
  grant_days: number;
  max_redemptions: number;
  redeemed_count: number;
  disabled: number;
  expires_at_ms: number | null;
}

const GIFT_CODE_COLUMNS =
  'code, kind, owner_uid, grant_days, max_redemptions, redeemed_count, disabled, expires_at_ms';

async function ensureFriendCode(
  db: D1Database,
  uid: string,
  secret: string
): Promise<GiftCodeRow> {
  const existing = await db
    .prepare(`SELECT ${GIFT_CODE_COLUMNS} FROM gift_codes WHERE owner_uid = ?1`)
    .bind(uid)
    .first<GiftCodeRow>();
  if (existing) return existing;

  const code = await friendCodeFor(uid, secret);
  // DO NOTHING e non REPLACE: se due aperture ravvicinate della schermata
  // arrivano insieme, la seconda non deve azzerare il contatore della prima.
  await db
    .prepare(
      `INSERT INTO gift_codes (code, kind, owner_uid, grant_days, max_redemptions)
       VALUES (?1, 'friend', ?2, ?3, ?4)
       ON CONFLICT(code) DO NOTHING`
    )
    .bind(code, uid, DEFAULT_GRANT_DAYS, FRIEND_CODE_MAX_REDEMPTIONS)
    .run();

  return {
    code,
    kind: 'friend',
    owner_uid: uid,
    grant_days: DEFAULT_GRANT_DAYS,
    max_redemptions: FRIEND_CODE_MAX_REDEMPTIONS,
    redeemed_count: 0,
    disabled: 0,
    expires_at_ms: null,
  };
}

// ── Rotte ───────────────────────────────────────────────────────────────────

/**
 * Gestisce /v1/gift/*. Restituisce null quando il path non e' di sua
 * competenza, cosi' il chiamante prosegue con le altre rotte.
 */
export async function handleGiftRequest(
  request: Request,
  pathname: string,
  env: GiftEnv
): Promise<Response | null> {
  if (!pathname.startsWith('/v1/gift/')) return null;

  const db = env.pokevault_catalog;
  if (!db) return json({ error: 'D1 binding non configurato' }, 500);

  const secret = env.GIFT_CODE_SECRET;
  if (!secret) return json({ error: 'GIFT_CODE_SECRET non configurato' }, 500);

  // POST /v1/gift/admin/create — lotti promo monouso. Non richiede un utente:
  // richiede il segreto di amministrazione.
  if (pathname === '/v1/gift/admin/create' && request.method === 'POST') {
    return handleAdminCreate(request, db, env);
  }

  const uid = await verifyFirebaseIdToken(bearerToken(request), env);
  if (!uid) return json({ error: 'ID token Firebase assente o non valido' }, 401);

  // GET /v1/gift/me — il mio codice AMICO, quante volte e' stato speso, e se
  // ho gia' usato un codice io.
  if (pathname === '/v1/gift/me' && request.method === 'GET') {
    const row = await ensureFriendCode(db, uid, secret);
    const mine = await db
      .prepare('SELECT code, granted_until_ms FROM gift_redemptions WHERE uid = ?1')
      .bind(uid)
      .first<{ code: string; granted_until_ms: number }>();

    return json({
      code: row.code,
      grantDays: row.grant_days,
      invitesUsed: row.redeemed_count,
      invitesMax: row.max_redemptions,
      alreadyRedeemed: mine !== null,
      giftUntilMs: mine && mine.granted_until_ms > Date.now() ? mine.granted_until_ms : null,
    });
  }

  // POST /v1/gift/redeem — riscatto vero e proprio.
  if (pathname === '/v1/gift/redeem' && request.method === 'POST') {
    return handleRedeem(request, db, env, uid, secret);
  }

  return json({ error: 'rotta /v1/gift sconosciuta' }, 404);
}

function reject(reason: GiftRejection, status = 409): Response {
  return json({ entitled: false, reason }, status);
}

async function handleRedeem(
  request: Request,
  db: D1Database,
  env: GiftEnv,
  uid: string,
  secret: string
): Promise<Response> {
  let body: { code?: string; deviceId?: string };
  try {
    body = (await request.json()) as { code?: string; deviceId?: string };
  } catch {
    return json({ error: 'corpo JSON non valido' }, 400);
  }

  const code = normalizeGiftCode(body.code ?? '');
  if (!code) return reject('code_missing', 400);

  // Il controllo sull'account viene prima del rate limit: chi ha gia' riscattato
  // deve leggere "l'hai gia' usato", non "riprova domani".
  const previous = await db
    .prepare('SELECT code FROM gift_redemptions WHERE uid = ?1')
    .bind(uid)
    .first<{ code: string }>();
  if (previous) return reject('already_redeemed');

  if (await overIpRateLimit(request, env)) return reject('rate_limited', 429);

  const row = await db
    .prepare(`SELECT ${GIFT_CODE_COLUMNS} FROM gift_codes WHERE code = ?1`)
    .bind(code)
    .first<GiftCodeRow>();

  if (!row) return reject('code_not_found', 404);
  if (row.disabled === 1) return reject('code_disabled');
  if (row.expires_at_ms !== null && row.expires_at_ms <= Date.now()) return reject('code_expired');
  if (row.redeemed_count >= row.max_redemptions) return reject('code_exhausted');
  // Regalarsi un mese da soli sarebbe il modo piu' breve di aggirare tutto.
  if (row.owner_uid === uid) return reject('own_code');

  const hash = await deviceHash(body.deviceId ?? '', secret);
  if (hash) {
    const sameDevice = await db
      .prepare('SELECT uid FROM gift_redemptions WHERE device_hash = ?1')
      .bind(hash)
      .first<{ uid: string }>();
    if (sameDevice) return reject('device_already_redeemed');
  }

  const grantedUntilMs = Date.now() + row.grant_days * 86_400_000;

  // Le due scritture stanno nello stesso batch, che D1 esegue come una
  // transazione: senza, un riscatto registrato e un contatore non incrementato
  // regalerebbero mesi oltre il tetto del codice.
  //
  // L'UPDATE ricontrolla il tetto nella WHERE perche' fra la SELECT di sopra e
  // qui puo' essersi infilato un altro riscatto dello stesso codice.
  let results: D1Result[];
  try {
    results = await db.batch([
      // INSERT ... SELECT e non VALUES: la capienza del codice va ricontrollata
      // DENTRO la transazione. Con un VALUES incondizionato, un codice esaurito
      // da un riscatto arrivato nel frattempo avrebbe lasciato la riga scritta
      // (il batch commette comunque) mentre la risposta diceva "esaurito":
      // l'utente si sarebbe visto negare un mese che il server gli aveva dato.
      db
        .prepare(
          `INSERT INTO gift_redemptions (uid, code, device_hash, granted_until_ms)
           SELECT ?1, ?2, ?3, ?4 FROM gift_codes
           WHERE code = ?2 AND disabled = 0 AND redeemed_count < max_redemptions`
        )
        .bind(uid, row.code, hash, grantedUntilMs),
      // Stessa condizione, stessa transazione: la UPDATE gira dopo la INSERT ma
      // su un'altra tabella, quindi le due guardie vedono lo stesso
      // redeemed_count e non possono dissentire.
      db
        .prepare(
          `UPDATE gift_codes SET redeemed_count = redeemed_count + 1
           WHERE code = ?1 AND disabled = 0 AND redeemed_count < max_redemptions`
        )
        .bind(row.code),
    ]);
  } catch (error) {
    // I controlli di sopra sono una fotografia: fra la SELECT e questa INSERT
    // puo' essersi infilato un altro riscatto dello stesso account o dello
    // stesso dispositivo. E' il vincolo che ha funzionato, non un guasto, e va
    // detto come tale: un 500 manderebbe l'utente a incolpare la connessione.
    const message = error instanceof Error ? error.message : String(error);
    if (/UNIQUE|constraint/i.test(message)) {
      return reject(hash ? 'device_already_redeemed' : 'already_redeemed');
    }
    throw error;
  }

  // Nessuna riga inserita significa che la guardia ha respinto entrambe le
  // istruzioni: il codice si e' esaurito fra la SELECT e la transazione.
  if ((results[0]?.meta?.changes ?? 0) === 0) return reject('code_exhausted');

  return json({
    entitled: true,
    grantDays: row.grant_days,
    giftUntilMs: grantedUntilMs,
  });
}

/** Confronto a tempo costante: un `===` su un segreto e' misurabile. */
function secretsMatch(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

async function handleAdminCreate(
  request: Request,
  db: D1Database,
  env: GiftEnv
): Promise<Response> {
  const expected = env.GIFT_ADMIN_SECRET;
  const provided = request.headers.get('x-gift-admin-secret') ?? '';
  // Senza segreto configurato si rifiuta: un endpoint che conia codici regalo
  // non puo' avere una modalita' "aperta perche' non e' stato configurato".
  if (!expected || !secretsMatch(expected, provided)) {
    return json({ error: 'non autorizzato' }, 403);
  }

  let body: { count?: number; grantDays?: number; expiresAtMs?: number; prefix?: string };
  try {
    body = (await request.json()) as {
      count?: number;
      grantDays?: number;
      expiresAtMs?: number;
      prefix?: string;
    };
  } catch {
    return json({ error: 'corpo JSON non valido' }, 400);
  }

  const count = Math.min(Math.max(Math.trunc(body.count ?? 1), 1), 200);
  const grantDays = Math.min(Math.max(Math.trunc(body.grantDays ?? DEFAULT_GRANT_DAYS), 1), 365);
  const expiresAtMs = Number.isFinite(body.expiresAtMs) ? Number(body.expiresAtMs) : null;
  const prefix = normalizeGiftCode(body.prefix ?? 'GIFT').slice(0, 8) || 'GIFT';

  const codes: string[] = [];
  const statements: D1PreparedStatement[] = [];
  for (let i = 0; i < count; i++) {
    const random = crypto.getRandomValues(new Uint8Array(8));
    let suffix = '';
    for (const b of random) suffix += CODE_ALPHABET[b % CODE_ALPHABET.length];
    const code = `${prefix}${suffix}`;
    codes.push(code);
    statements.push(
      db
        .prepare(
          `INSERT INTO gift_codes (code, kind, owner_uid, grant_days, max_redemptions, expires_at_ms)
           VALUES (?1, 'promo', NULL, ?2, 1, ?3)
           ON CONFLICT(code) DO NOTHING`
        )
        .bind(code, grantDays, expiresAtMs)
    );
  }

  await db.batch(statements);
  return json({ created: codes.length, grantDays, expiresAtMs, codes });
}
