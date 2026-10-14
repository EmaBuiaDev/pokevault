// Quante righe D1 ha scritto OGGI (UTC) tutto l'account Cloudflare.
//
// Il limite del piano gratuito D1 (100.000 righe scritte al giorno) vale per
// l'account intero: un import grosso che lo esaurisce fa fallire fino a
// mezzanotte UTC anche le scritture della produzione (scambi, import italiano).
// Gli script lunghi del catalogo EN lo controllano prima di ogni passo e si
// fermano sopra la soglia (vedi ingest-en-tutti.mjs).
//
// Token: quello di wrangler (`wrangler auth token`), chiesto ogni volta perche'
// quello OAuth scade dopo un'ora e wrangler lo rinnova da se'.
import { runWrangler } from './cardmarket.mjs';

export const ACCOUNT_ID = 'e6c4d1ff864abf6dbcb4ec1e0de6b34a';
/** Soglia per i lavori di catalogo: ne lascia 30.000 alla produzione. */
export const BATCH_WRITE_LIMIT = 70000;

/** Righe scritte oggi da tutto l'account, o null se non si riesce a saperlo. */
export async function d1RowsWrittenToday() {
  try {
    const raw = await runWrangler(['auth', 'token'], {}, 2);
    const token = raw.trim().split(/\s+/).pop();
    if (!token) return null;
    const today = new Date().toISOString().slice(0, 10);
    const query = `{ viewer { accounts(filter:{accountTag:"${ACCOUNT_ID}"}) { d1AnalyticsAdaptiveGroups(limit:50, filter:{date_geq:"${today}"}) { sum { rowsWritten } } } } }`;
    const res = await fetch('https://api.cloudflare.com/client/v4/graphql', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ query }),
    });
    const json = await res.json();
    const groups = json?.data?.viewer?.accounts?.[0]?.d1AnalyticsAdaptiveGroups;
    if (!Array.isArray(groups)) return null;
    return groups.reduce((sum, g) => sum + (g.sum?.rowsWritten ?? 0), 0);
  } catch {
    return null;
  }
}

/** Millisecondi fino a 10 minuti dopo la prossima mezzanotte UTC. */
export function msUntilNextUtcDay() {
  const now = new Date();
  const next = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1, 0, 10, 0);
  return next - now.getTime();
}

/** Quante letture fallite di fila si lasciano passare prima di fermarsi. */
export const UNREADABLE_TOLERATED = 2;
/** La pausa quando la quota non si legge da troppe volte di fila. */
const UNREADABLE_PAUSE_MS = 15 * 60 * 1000;
let unreadableStreak = 0;

/**
 * Cosa fare con una lettura della quota: 'go' sotto la soglia o con un errore
 * isolato (la rete che singhiozza), 'pause' sopra la soglia o quando la quota
 * non si legge da piu' di [tolerated] volte di fila. Prima si proseguiva
 * sempre a quota illeggibile: con il login scaduto l'import correva alla cieca
 * e poteva finire la quota che serve alla produzione.
 */
export function quotaDecision(written, streak, limit = BATCH_WRITE_LIMIT, tolerated = UNREADABLE_TOLERATED) {
  if (written === null) return streak <= tolerated ? 'go' : 'pause';
  return written < limit ? 'go' : 'pause';
}

/**
 * Aspetta finche' l'account e' sotto la soglia. Un errore di lettura isolato
 * passa (e lo dice); di fila, l'import si ferma e riprova ogni 15 minuti.
 */
export async function waitForD1Quota(log, limit = BATCH_WRITE_LIMIT) {
  for (;;) {
    const written = await d1RowsWrittenToday();
    unreadableStreak = written === null ? unreadableStreak + 1 : 0;
    if (written === null) {
      if (quotaDecision(null, unreadableStreak, limit) === 'go') {
        log?.(`quota D1: non leggibile (${unreadableStreak} di fila), si prosegue`);
        return null;
      }
      log?.(`quota D1: non leggibile ${unreadableStreak} volte di fila (login scaduto?), pausa di 15 minuti`);
      await new Promise(r => setTimeout(r, UNREADABLE_PAUSE_MS));
      continue;
    }
    if (quotaDecision(written, 0, limit) === 'go') return written;
    const wait = msUntilNextUtcDay();
    log?.(`quota D1: ${written} righe scritte oggi (soglia ${limit}), pausa di ${Math.round(wait / 60000)} minuti fino al nuovo giorno UTC`);
    await new Promise(r => setTimeout(r, wait));
  }
}
