/**
 * Verifica lato server degli abbonamenti Google Play.
 *
 * Prima l'entitlement premium era interamente dichiarato dal client: l'app
 * scriveva users/{uid}.isPremium su Firestore e nessuno lo rileggeva. Qui la
 * fonte di verita' e' la Google Play Developer API, interrogata dal Worker con
 * un service account. Il client puo' solo chiedere una verifica passando il
 * purchase token; l'uid viene sempre ricavato dall'ID token Firebase, mai dal
 * corpo della richiesta.
 */

export interface BillingEnv {
  pokevault_catalog?: D1Database;
  CACHE?: KVNamespace;
  /** JSON completo del service account con accesso alla Play Developer API. */
  PLAY_SERVICE_ACCOUNT_JSON?: string;
  /** Package name dell'app, es. com.emabuia.pokevault. */
  PLAY_PACKAGE_NAME?: string;
  /** Project id Firebase, per validare aud/iss dell'ID token. */
  FIREBASE_PROJECT_ID?: string;
  /** Segreto condiviso che protegge l'endpoint RTDN. */
  RTDN_SHARED_SECRET?: string;
}

/** Stati che danno effettivamente accesso alle funzioni premium. */
const ENTITLED_STATES = new Set(['active', 'in_grace']);

// Le stesse chiavi del securetoken, ma in formato JWK: WebCrypto le importa
// direttamente, mentre l'endpoint x509 costringerebbe a estrarre a mano lo
// SubjectPublicKeyInfo da un certificato DER.
const GOOGLE_JWK_URL =
  'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
const ANDROID_PUBLISHER = 'https://androidpublisher.googleapis.com/androidpublisher/v3';
const PLAY_SCOPE = 'https://www.googleapis.com/auth/androidpublisher';

const ACCESS_TOKEN_CACHE_KEY = 'billing:play_access_token';
const JWK_CACHE_KEY = 'billing:firebase_jwks';

// ── Utilita' di base ────────────────────────────────────────────────────────

function base64UrlToBytes(input: string): Uint8Array {
  const padded = input.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(padded + '='.repeat((4 - (padded.length % 4)) % 4));
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function pemToPkcs8(pem: string): Uint8Array {
  const body = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\s+/g, '');
  return base64UrlToBytes(body.replace(/\+/g, '-').replace(/\//g, '_'));
}

// ── Verifica dell'ID token Firebase ─────────────────────────────────────────

interface FirebaseClaims {
  sub: string;
  aud: string;
  iss: string;
  exp: number;
  iat: number;
}

/**
 * Verifica la firma RS256 di un ID token Firebase e ne restituisce l'uid.
 *
 * Restituisce null quando il token e' assente, scaduto, mal formato o firmato
 * da una chiave che non e' di Google: chiamare questa funzione e' l'unico modo
 * accettato per stabilire chi sia l'utente.
 */
export async function verifyFirebaseIdToken(
  idToken: string,
  env: BillingEnv
): Promise<string | null> {
  const projectId = env.FIREBASE_PROJECT_ID;
  if (!projectId || !idToken) return null;

  const parts = idToken.split('.');
  if (parts.length !== 3) return null;

  let header: { kid?: string; alg?: string };
  let claims: FirebaseClaims;
  try {
    header = JSON.parse(new TextDecoder().decode(base64UrlToBytes(parts[0])));
    claims = JSON.parse(new TextDecoder().decode(base64UrlToBytes(parts[1])));
  } catch {
    return null;
  }

  if (header.alg !== 'RS256' || !header.kid) return null;

  const now = Math.floor(Date.now() / 1000);
  if (claims.exp <= now) return null;
  if (claims.iat > now + 300) return null;
  if (claims.aud !== projectId) return null;
  if (claims.iss !== `https://securetoken.google.com/${projectId}`) return null;
  if (!claims.sub) return null;

  const jwks = await fetchFirebaseJwks(env);
  const jwk = jwks[header.kid];
  if (!jwk) return null;

  const ok = await verifyRs256(jwk, `${parts[0]}.${parts[1]}`, parts[2]);
  return ok ? claims.sub : null;
}

interface JsonWebKey_RSA {
  kid: string;
  kty: string;
  n: string;
  e: string;
  alg?: string;
}

async function fetchFirebaseJwks(env: BillingEnv): Promise<Record<string, JsonWebKey_RSA>> {
  const cached = await env.CACHE?.get(JWK_CACHE_KEY, 'json');
  if (cached) return cached as Record<string, JsonWebKey_RSA>;

  const response = await fetch(GOOGLE_JWK_URL);
  if (!response.ok) return {};
  const body = (await response.json()) as { keys?: JsonWebKey_RSA[] };

  const byKid: Record<string, JsonWebKey_RSA> = {};
  for (const key of body.keys ?? []) {
    if (key.kid) byKid[key.kid] = key;
  }

  // Google ruota queste chiavi; il max-age della risposta dice per quanto
  // restano valide. In mancanza si tiene un'ora, ben dentro la finestra.
  const maxAge = Number(/max-age=(\d+)/.exec(response.headers.get('cache-control') ?? '')?.[1]);
  await env.CACHE?.put(JWK_CACHE_KEY, JSON.stringify(byKid), {
    expirationTtl: Number.isFinite(maxAge) && maxAge > 60 ? Math.min(maxAge, 86400) : 3600,
  });
  return byKid;
}

async function verifyRs256(
  jwk: JsonWebKey_RSA,
  signedPart: string,
  signature: string
): Promise<boolean> {
  try {
    const key = await crypto.subtle.importKey(
      'jwk',
      { kty: jwk.kty, n: jwk.n, e: jwk.e, alg: 'RS256', ext: true },
      { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
      false,
      ['verify']
    );
    return await crypto.subtle.verify(
      'RSASSA-PKCS1-v1_5',
      key,
      base64UrlToBytes(signature) as BufferSource,
      new TextEncoder().encode(signedPart) as BufferSource
    );
  } catch {
    return false;
  }
}

// ── Access token per la Play Developer API ──────────────────────────────────

interface ServiceAccount {
  client_email: string;
  private_key: string;
}

async function getPlayAccessToken(env: BillingEnv): Promise<string | null> {
  const cached = await env.CACHE?.get(ACCESS_TOKEN_CACHE_KEY);
  if (cached) return cached;

  const raw = env.PLAY_SERVICE_ACCOUNT_JSON;
  if (!raw) return null;

  let account: ServiceAccount;
  try {
    account = JSON.parse(raw);
  } catch {
    return null;
  }
  if (!account.client_email || !account.private_key) return null;

  const now = Math.floor(Date.now() / 1000);
  const header = bytesToBase64Url(new TextEncoder().encode(JSON.stringify({ alg: 'RS256', typ: 'JWT' })));
  const payload = bytesToBase64Url(
    new TextEncoder().encode(
      JSON.stringify({
        iss: account.client_email,
        scope: PLAY_SCOPE,
        aud: TOKEN_ENDPOINT,
        iat: now,
        exp: now + 3600,
      })
    )
  );

  const key = await crypto.subtle.importKey(
    'pkcs8',
    pemToPkcs8(account.private_key.replace(/\\n/g, '\n')) as BufferSource,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signature = new Uint8Array(
    await crypto.subtle.sign(
      'RSASSA-PKCS1-v1_5',
      key,
      new TextEncoder().encode(`${header}.${payload}`) as BufferSource
    )
  );
  const assertion = `${header}.${payload}.${bytesToBase64Url(signature)}`;

  const response = await fetch(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    }),
  });
  if (!response.ok) return null;

  const token = (await response.json()) as { access_token?: string; expires_in?: number };
  if (!token.access_token) return null;

  // Scade 5 minuti prima del dovuto, per non usarne uno appena scaduto.
  const ttl = Math.max(60, (token.expires_in ?? 3600) - 300);
  await env.CACHE?.put(ACCESS_TOKEN_CACHE_KEY, token.access_token, { expirationTtl: ttl });
  return token.access_token;
}

// ── Interrogazione dello stato dell'abbonamento ─────────────────────────────

export interface SubscriptionStatus {
  state: string;
  expiryTimeMs: number | null;
  autoRenewing: boolean;
  productId: string;
}

/** Mappa subscriptionState della API v2 sui nostri stati normalizzati. */
function normalizeState(apiState: string | undefined): string {
  switch (apiState) {
    case 'SUBSCRIPTION_STATE_ACTIVE':
      return 'active';
    case 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD':
      return 'in_grace';
    case 'SUBSCRIPTION_STATE_ON_HOLD':
      return 'on_hold';
    case 'SUBSCRIPTION_STATE_PAUSED':
      return 'paused';
    case 'SUBSCRIPTION_STATE_CANCELED':
      return 'canceled';
    case 'SUBSCRIPTION_STATE_EXPIRED':
      return 'expired';
    default:
      return 'revoked';
  }
}

export async function fetchSubscriptionStatus(
  purchaseToken: string,
  env: BillingEnv
): Promise<SubscriptionStatus | null> {
  const accessToken = await getPlayAccessToken(env);
  const packageName = env.PLAY_PACKAGE_NAME;
  if (!accessToken || !packageName) return null;

  const url =
    `${ANDROID_PUBLISHER}/applications/${encodeURIComponent(packageName)}` +
    `/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;

  const response = await fetch(url, { headers: { authorization: `Bearer ${accessToken}` } });
  if (!response.ok) return null;

  const body = (await response.json()) as {
    subscriptionState?: string;
    lineItems?: Array<{
      expiryTime?: string;
      productId?: string;
      autoRenewingPlan?: { autoRenewEnabled?: boolean };
    }>;
  };

  // Un abbonamento ha un solo line item nei casi che ci interessano; si prende
  // quello con scadenza piu' lontana, che e' il periodo effettivamente pagato.
  const line = (body.lineItems ?? [])
    .slice()
    .sort((a, b) => Date.parse(b.expiryTime ?? '') - Date.parse(a.expiryTime ?? ''))[0];

  const expiry = line?.expiryTime ? Date.parse(line.expiryTime) : NaN;

  return {
    state: normalizeState(body.subscriptionState),
    expiryTimeMs: Number.isFinite(expiry) ? expiry : null,
    autoRenewing: line?.autoRenewingPlan?.autoRenewEnabled === true,
    productId: line?.productId ?? '',
  };
}

// ── Persistenza ─────────────────────────────────────────────────────────────

async function saveEntitlement(
  db: D1Database,
  uid: string,
  purchaseToken: string,
  status: SubscriptionStatus
): Promise<void> {
  await db
    .prepare(
      `INSERT INTO entitlements (uid, product_id, purchase_token, state, expiry_time_ms, auto_renewing, updated_at)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, unixepoch())
       ON CONFLICT(uid) DO UPDATE SET
         product_id = excluded.product_id,
         purchase_token = excluded.purchase_token,
         state = excluded.state,
         expiry_time_ms = excluded.expiry_time_ms,
         auto_renewing = excluded.auto_renewing,
         updated_at = excluded.updated_at`
    )
    .bind(
      uid,
      status.productId,
      purchaseToken,
      status.state,
      status.expiryTimeMs,
      status.autoRenewing ? 1 : 0
    )
    .run();
}

/**
 * L'utente ha diritto al premium?
 *
 * Uno stato 'canceled' resta valido fino alla scadenza: l'utente ha disdetto il
 * rinnovo ma ha pagato fino a quella data.
 */
export function isEntitled(state: string, expiryTimeMs: number | null): boolean {
  if (ENTITLED_STATES.has(state)) return true;
  if (state === 'canceled' && expiryTimeMs !== null) return expiryTimeMs > Date.now();
  return false;
}

// ── Rotte ───────────────────────────────────────────────────────────────────

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    // Un entitlement non va mai cachato: e' per utente e cambia nel tempo.
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
  });
}

function bearerToken(request: Request): string {
  const header = request.headers.get('authorization') ?? '';
  return header.toLowerCase().startsWith('bearer ') ? header.slice(7).trim() : '';
}

/**
 * Gestisce /v1/billing/*. Restituisce null quando il path non e' di sua
 * competenza, cosi' il chiamante prosegue con le altre rotte.
 */
export async function handleBillingRequest(
  request: Request,
  pathname: string,
  env: BillingEnv
): Promise<Response | null> {
  if (!pathname.startsWith('/v1/billing/')) return null;

  const db = env.pokevault_catalog;
  if (!db) return json({ error: 'D1 binding non configurato' }, 500);

  // POST /v1/billing/verify — il client manda il purchase token, il server
  // chiede a Google qual e' lo stato reale e lo memorizza.
  if (pathname === '/v1/billing/verify' && request.method === 'POST') {
    const uid = await verifyFirebaseIdToken(bearerToken(request), env);
    if (!uid) return json({ error: 'ID token Firebase assente o non valido' }, 401);

    let body: { purchaseToken?: string };
    try {
      body = (await request.json()) as { purchaseToken?: string };
    } catch {
      return json({ error: 'corpo JSON non valido' }, 400);
    }

    const purchaseToken = body.purchaseToken?.trim();
    if (!purchaseToken) return json({ error: 'purchaseToken mancante' }, 400);

    const status = await fetchSubscriptionStatus(purchaseToken, env);
    if (!status) return json({ error: 'verifica presso Google non riuscita' }, 502);

    await saveEntitlement(db, uid, purchaseToken, status);

    return json({
      entitled: isEntitled(status.state, status.expiryTimeMs),
      state: status.state,
      expiryTimeMs: status.expiryTimeMs,
      autoRenewing: status.autoRenewing,
      productId: status.productId,
    });
  }

  // GET /v1/billing/entitlement — stato corrente dell'utente autenticato.
  if (pathname === '/v1/billing/entitlement' && request.method === 'GET') {
    const uid = await verifyFirebaseIdToken(bearerToken(request), env);
    if (!uid) return json({ error: 'ID token Firebase assente o non valido' }, 401);

    const row = await db
      .prepare(
        `SELECT product_id, state, expiry_time_ms, auto_renewing
         FROM entitlements WHERE uid = ?1`
      )
      .bind(uid)
      .first<{
        product_id: string;
        state: string;
        expiry_time_ms: number | null;
        auto_renewing: number;
      }>();

    if (!row) return json({ entitled: false, state: 'none' });

    return json({
      entitled: isEntitled(row.state, row.expiry_time_ms),
      state: row.state,
      expiryTimeMs: row.expiry_time_ms,
      autoRenewing: row.auto_renewing === 1,
      productId: row.product_id,
    });
  }

  // POST /v1/billing/rtdn — notifiche Real-time Developer di Google, inoltrate
  // da Pub/Sub. Sono cio' che rende visibili disdette, rimborsi e sospensioni
  // senza aspettare che l'utente riapra l'app.
  if (pathname === '/v1/billing/rtdn' && request.method === 'POST') {
    const expected = env.RTDN_SHARED_SECRET;
    const provided = new URL(request.url).searchParams.get('key') ?? '';
    // L'endpoint e' pubblico per forza (lo chiama Pub/Sub): senza segreto
    // configurato si rifiuta, invece di accettare qualunque chiamante.
    if (!expected || provided !== expected) return json({ error: 'non autorizzato' }, 403);

    let envelope: { message?: { data?: string } };
    try {
      envelope = (await request.json()) as { message?: { data?: string } };
    } catch {
      return json({ error: 'corpo JSON non valido' }, 400);
    }

    const encoded = envelope.message?.data;
    // Senza payload si risponde comunque 200: un non-2xx fa ritentare Pub/Sub
    // all'infinito su un messaggio che non diventera' mai valido.
    if (!encoded) return json({ ok: true, skipped: 'nessun payload' });

    let notification: {
      subscriptionNotification?: { purchaseToken?: string; notificationType?: number };
    };
    try {
      notification = JSON.parse(new TextDecoder().decode(base64UrlToBytes(encoded)));
    } catch {
      return json({ ok: true, skipped: 'payload non decodificabile' });
    }

    const purchaseToken = notification.subscriptionNotification?.purchaseToken;
    if (!purchaseToken) return json({ ok: true, skipped: 'non e\' una notifica di abbonamento' });

    // La notifica dice SOLO che qualcosa e' cambiato: lo stato autorevole si
    // rilegge sempre dalla Developer API, mai dal contenuto del messaggio.
    const status = await fetchSubscriptionStatus(purchaseToken, env);
    if (!status) return json({ error: 'verifica presso Google non riuscita' }, 502);

    const owner = await db
      .prepare('SELECT uid FROM entitlements WHERE purchase_token = ?1')
      .bind(purchaseToken)
      .first<{ uid: string }>();

    // Token sconosciuto: l'utente non ha ancora chiamato /verify. Non e' un
    // errore, e ritentare non aiuterebbe.
    if (!owner) return json({ ok: true, skipped: 'purchase token non associato' });

    await saveEntitlement(db, owner.uid, purchaseToken, status);
    return json({ ok: true, state: status.state });
  }

  return json({ error: 'rotta /v1/billing sconosciuta' }, 404);
}
