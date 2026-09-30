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
 * dati di utenti, e non devono poter toccare `pokevault_catalog` nemmeno per
 * sbaglio. Le migrazioni stanno in schema-trade/.
 */

import { verifyFirebaseIdToken } from './billing';

export interface TradeEnv {
  /** "1" accende il modulo. Assente in produzione. */
  TRADE_ENABLED?: string;
  /** D1 degli scambi (staging: pokevault-trade-staging). */
  trade_db?: D1Database;
  /** Progetto Firebase contro cui si verifica l'ID token. */
  FIREBASE_PROJECT_ID?: string;
  CACHE?: KVNamespace;
}

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

  // GET /v1/trade/me — chi sono per il server. Nella fase 0 serve solo a
  // dimostrare che l'app di staging arriva fin qui autenticata.
  if (pathname === '/v1/trade/me' && request.method === 'GET') {
    return json({ uid, schemaVersion: await schemaVersion(db) });
  }

  return json({ error: 'unknown /v1/trade route' }, 404);
}
