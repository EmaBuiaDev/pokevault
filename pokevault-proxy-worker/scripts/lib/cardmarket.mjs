// Pezzi in comune fra map-cardmarket-tcgdex.mjs (carta -> idProduct, una volta
// per set) e build-cardmarket-prices.mjs (listino del giorno -> R2).

import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
export const workerRoot = path.resolve(__dirname, '..', '..');

export const PRICE_GUIDE_URL = 'https://downloads.s3.cardmarket.com/productCatalog/priceGuide/price_guide_6.json';
export const CARDMARKET_R2_BUCKET = 'pokevault-images';
// Deve restare uguale a CARDMARKET_PRICES_R2_KEY in src/index.ts.
export const CARDMARKET_R2_KEY = 'prezzi/cardmarket-it-v1.json';
export const WORKER_URL = (process.env.POKEVAULT_WORKER_URL || 'https://pokevault-proxy.pokevault-emanu.workers.dev').replace(/\/+$/, '');

const wranglerBin = path.join(workerRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'wrangler.cmd' : 'wrangler');

function runWranglerOnce(args, { binary = false } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(wranglerBin, args, { cwd: workerRoot, shell: true, stdio: ['ignore', 'pipe', 'pipe'] });
    const out = [];
    let stderr = '';
    child.stdout.on('data', (d) => { out.push(d); });
    child.stderr.on('data', (d) => { stderr += d.toString(); });
    child.on('close', (code) => {
      const buf = Buffer.concat(out);
      if (code === 0) resolve(binary ? buf : buf.toString());
      else reject(new Error(`wrangler ${args.join(' ')} uscito con ${code}: ${stderr.slice(0, 800)}`));
    });
  });
}

export async function runWrangler(args, options = {}, attempts = 3) {
  let lastErr;
  for (let i = 0; i < attempts; i += 1) {
    try {
      return await runWranglerOnce(args, options);
    } catch (err) {
      lastErr = err;
      if (i < attempts - 1) await new Promise((r) => setTimeout(r, 1500 * (i + 1)));
    }
  }
  throw lastErr;
}

/**
 * SELECT su D1 di produzione. Va con --command e non con --file: con --file
 * wrangler restituisce solo le statistiche, non le righe. Su Windows lo spawn
 * con shell ri-spezza sugli spazi, da cui le virgolette attorno all'SQL.
 */
export async function d1Select(sql) {
  // Su una riga: con la shell gli a capo spezzano il comando.
  const oneLine = sql.replace(/\s+/g, ' ').trim();
  const out = await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--json', '--command', `"${oneLine.replace(/"/g, '\\"')}"`]);
  const jsonStart = out.indexOf('[');
  const parsed = JSON.parse(jsonStart >= 0 ? out.slice(jsonStart) : out);
  return parsed[0]?.results ?? [];
}

export async function d1ExecuteFile(file) {
  return runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--yes', `--file="${file}"`], {}, 1);
}

export function sqlString(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

/** Il listino di oggi, come oggetto: { version, createdAt, priceGuides: [...] }. */
export async function downloadPriceGuide() {
  const res = await fetch(PRICE_GUIDE_URL);
  if (!res.ok) throw new Error(`Listino Cardmarket non scaricabile: HTTP ${res.status}`);
  const guide = await res.json();
  if (!guide?.createdAt || !Array.isArray(guide.priceGuides) || guide.priceGuides.length === 0) {
    throw new Error('Listino Cardmarket in un formato che non conosco');
  }
  return guide;
}

/** Numero di carta come chiave dei prezzi: stessa regola di normalizeCardNumberKeyForPrices nel Worker. */
export function priceKey(raw) {
  const clean = (String(raw ?? '').split('/')[0] ?? '').trim();
  if (!clean) return null;
  return /^\d+$/.test(clean) ? String(parseInt(clean, 10)) : clean.toUpperCase();
}

/** Prefisso del card_id ("SV10_IT_7.png" -> "sv10"): uno dei codici con cui l'app chiede i prezzi. */
export function rawSetCode(cardId) {
  const i = String(cardId).indexOf('_IT_');
  return i > 0 ? cardId.slice(0, i).toLowerCase() : null;
}

export const EUR_FIELDS = ['avg', 'low', 'trend', 'avg1', 'avg7', 'avg30'];

export function eurPrice(guideEntry) {
  if (!guideEntry) return null;
  const out = {};
  for (const f of EUR_FIELDS) {
    const v = guideEntry[f];
    if (typeof v === 'number' && Number.isFinite(v) && v > 0) out[f] = v;
  }
  return out.avg != null || out.low != null || out.trend != null ? out : null;
}
