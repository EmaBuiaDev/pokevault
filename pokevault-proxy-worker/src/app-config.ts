// Gli interruttori dell'app che si cambiano senza una release (14/10/2026).
//
// Per ora uno solo: il catalogo inglese. L'app lo chiede all'avvio
// (GET /v1/app-config) e lo usa dall'avvio dopo; se non risponde tiene
// l'ultima risposta, e senza nessuna risposta resta spento.
//
// Il valore sta su R2 (app/config.json), non nella KV della cache: gli script
// che puliscono la cache non devono poterlo cancellare. Si cambia con
// scripts/interruttore-inglese.mjs, senza ripubblicare il Worker.

export const APP_CONFIG_R2_KEY = 'app/config.json';

export interface AppConfig {
  englishCatalog: boolean;
}

/** Spento finche' qualcuno non lo accende: e' il valore se manca il file o e' rotto. */
export const DEFAULT_APP_CONFIG: AppConfig = { englishCatalog: false };

/** Il file su R2 letto con prudenza: solo un `true` vero accende qualcosa. */
export function parseAppConfig(raw: string | null | undefined): AppConfig {
  if (!raw) return { ...DEFAULT_APP_CONFIG };
  try {
    const parsed = JSON.parse(raw) as Partial<Record<keyof AppConfig, unknown>>;
    return { englishCatalog: parsed?.englishCatalog === true };
  } catch {
    return { ...DEFAULT_APP_CONFIG };
  }
}

// Tenuto in memoria dall'isolate per un minuto: R2 a ogni avvio di ogni app
// costerebbe poco, ma non serve.
const MEMO_MS = 60 * 1000;
let memo: { at: number; config: AppConfig } | null = null;

export async function handleAppConfigRequest(request: Request, pathname: string, bucket: R2Bucket | undefined): Promise<Response | null> {
  if (pathname !== '/v1/app-config' || request.method !== 'GET') return null;
  const now = Date.now();
  if (!memo || now - memo.at > MEMO_MS) {
    let raw: string | null = null;
    try {
      raw = bucket ? (await (await bucket.get(APP_CONFIG_R2_KEY))?.text()) ?? null : null;
    } catch (error) {
      console.error('app-config: file illeggibile, vale il predefinito:', error);
    }
    memo = { at: now, config: parseAppConfig(raw) };
  }
  return new Response(JSON.stringify(memo.config), {
    status: 200,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'public, max-age=300',
      'Access-Control-Allow-Origin': '*',
    },
  });
}
