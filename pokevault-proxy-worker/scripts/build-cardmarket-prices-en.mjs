#!/usr/bin/env node
// Listino Cardmarket -> prezzi delle carte del catalogo EN -> R2
// (prezzi/cardmarket-en-v1.json). Piano carte EN, Fase 2, 13/10/2026.
//
// E' il gemello inglese di build-cardmarket-prices.mjs, che resta com'e': legge
// il database inglese (card_cardmarket e card_variants, riempiti da
// map-cardmarket-en.mjs) e scrive un file suo, che solo le rotte /en/prices
// del Worker leggono. Stesso formato, quindi l'app lo legge con lo stesso
// codice dei prezzi italiani. Nessuna chiamata PokeWallet: solo il listino.
//
//   node scripts/build-cardmarket-prices-en.mjs              (prova: file locale)
//   node scripts/build-cardmarket-prices-en.mjs --upload     (carica su R2 se il listino e' nuovo)
//   opzioni: --guide FILE, --out FILE, --force (come lo script italiano)
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { CARDMARKET_R2_BUCKET, EUR_FIELDS, downloadPriceGuide, eurPrice, priceKey, runWrangler, workerRoot } from './lib/cardmarket.mjs';

// Deve restare uguale a CARDMARKET_EN_R2_KEY in src/catalog-en.ts.
export const CARDMARKET_EN_R2_KEY = 'prezzi/cardmarket-en-v1.json';
const EN_DB = 'pokevault-catalog-en';

const args = process.argv.slice(2);
const value = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : undefined; };
const UPLOAD = args.includes('--upload');
const FORCE = args.includes('--force');
const GUIDE_FILE = value('--guide');
const OUT = value('--out') ?? path.join(workerRoot, 'scripts', '.cardmarket-prices-en.json');
// Piu' bassa dell'italiano (90%): i collegamenti dei set solo EN vengono da
// TCGdex senza arbitro, e qualche prodotto vecchio non ha piu' un prezzo.
const MIN_FOUND_RATIO = 0.8;

const summary = [];
const say = (line) => { console.log(line); summary.push(line); };
function writeSummary() {
  if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, `### Listino Cardmarket EN\n\n${summary.map((l) => `- ${l}`).join('\n')}\n`);
}
function fail(message) {
  say(`ERRORE: ${message}`);
  writeSummary();
  process.exit(1);
}

async function enSelect(sql) {
  const oneLine = sql.replace(/\s+/g, ' ').trim();
  const out = await runWrangler(['d1', 'execute', EN_DB, '--remote', '--json', '--command', `"${oneLine.replace(/"/g, '\\"')}"`]);
  const start = out.indexOf('[');
  return JSON.parse(start >= 0 ? out.slice(start) : out)[0]?.results ?? [];
}

/** Prefisso del card_id EN ("SV08_EN_7.webp" -> "sv08"): l'app chiede i prezzi anche cosi'. */
function rawSetCodeEn(cardId) {
  const i = String(cardId).indexOf('_EN_');
  return i > 0 ? cardId.slice(0, i).toLowerCase() : null;
}

// ── 1. listino ──
const guide = GUIDE_FILE
  ? JSON.parse(GUIDE_FILE.endsWith('.gz') ? zlib.gunzipSync(fs.readFileSync(GUIDE_FILE)) : fs.readFileSync(GUIDE_FILE))
  : await downloadPriceGuide();
const createdMs = Date.parse(guide.createdAt);
if (!Number.isFinite(createdMs)) fail(`createdAt del listino illeggibile: ${guide.createdAt}`);
const createdAt = new Date(createdMs).toISOString();
say(`Listino del ${createdAt} (${guide.priceGuides.length} prodotti)`);
const byProduct = new Map(guide.priceGuides.map((p) => [p.idProduct, p]));

// ── 2. collegamenti e stampe dal catalogo EN ──
const rows = await enSelect(
  `SELECT m.card_id, m.id_product, c.expansion_id, c.card_number
   FROM card_cardmarket m JOIN cards c ON c.card_id = m.card_id JOIN expansions e ON e.id = c.expansion_id
   WHERE e.published = 1`,
);
if (rows.length === 0) fail('card_cardmarket del catalogo EN e\' vuota: lancia prima map-cardmarket-en.mjs');
const variantsByCard = new Map();
for (const v of await enSelect(
  `SELECT v.card_id, v.variant, v.kind, v.type, v.id_product
   FROM card_variants v JOIN cards c ON c.card_id = v.card_id JOIN expansions e ON e.id = c.expansion_id
   WHERE e.published = 1`,
)) {
  if (!variantsByCard.has(v.card_id)) variantsByCard.set(v.card_id, []);
  variantsByCard.get(v.card_id).push(v);
}

// Stesse regole di build-cardmarket-prices.mjs (vedi i commenti li').
function holoPrice(entry) {
  if (!entry) return null;
  const mapped = {};
  for (const f of EUR_FIELDS) mapped[f] = entry[`${f}-holo`];
  return eurPrice(mapped);
}
function variantPrices(cardProduct, main, variants) {
  const out = {};
  const sameProductNormal = variants.some((v) => v.kind === 'base' && (v.variant === 'Normal' || v.variant === '1st Edition') && (v.id_product ?? cardProduct) === cardProduct);
  for (const v of variants) {
    let price = null;
    const product = v.id_product ?? cardProduct;
    if (v.kind === 'special') {
      if (!v.id_product) continue;
      const entry = byProduct.get(v.id_product);
      price = v.type === 'reverse' ? (holoPrice(entry) ?? eurPrice(entry)) : (eurPrice(entry) ?? holoPrice(entry));
    } else {
      const entry = byProduct.get(product);
      if (v.type === 'reverse') price = holoPrice(entry);
      else if (v.type === 'holo' && sameProductNormal) price = holoPrice(entry);
      else price = eurPrice(entry);
    }
    if (!price) continue;
    if (v.kind === 'base' && product === cardProduct && JSON.stringify(price) === JSON.stringify(main)) continue;
    out[v.variant] = product !== cardProduct || v.kind === 'special' ? { ...price, idProduct: product } : price;
  }
  return Object.keys(out).length ? out : null;
}

// ── 3. prezzi per espansione ──
const expansions = {};
let found = 0;
for (const row of rows) {
  const key = priceKey(row.card_number);
  const price = eurPrice(byProduct.get(row.id_product));
  if (!key || !price) continue;
  const exp = (expansions[row.expansion_id] ??= { aliases: [], prices: {} });
  exp.prices[key] = { ...price, idProduct: row.id_product };
  const v = variantPrices(row.id_product, price, variantsByCard.get(row.card_id) ?? []);
  if (v) exp.prices[key].v = v;
  const alias = rawSetCodeEn(row.card_id);
  if (alias && alias !== row.expansion_id && !exp.aliases.includes(alias)) exp.aliases.push(alias);
  found += 1;
}
const ratio = found / rows.length;
say(`Carte EN collegate: ${rows.length}; col prezzo nel listino: ${found} (${(ratio * 100).toFixed(1)}%); espansioni: ${Object.keys(expansions).length}`);
if (ratio < MIN_FOUND_RATIO) fail(`solo il ${(ratio * 100).toFixed(1)}% delle carte ritrova il prezzo: non carico niente`);

const blob = { version: 1, lang: 'en', createdAt, sourceCreatedAt: guide.createdAt, builtAt: Date.now(), expansions };
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(blob));
say(`File: ${OUT} (${Math.round(fs.statSync(OUT).size / 1024)} KB)`);

if (!UPLOAD) {
  say('Prova: niente caricato su R2 (aggiungi --upload).');
  writeSummary();
  process.exit(0);
}

// ── 4. caricamento (solo se il listino e' piu' nuovo di quello su R2) ──
const objectPath = `${CARDMARKET_R2_BUCKET}/${CARDMARKET_EN_R2_KEY}`;
let current = null;
try {
  current = JSON.parse((await runWrangler(['r2', 'object', 'get', objectPath, '--remote', '--pipe'], { binary: true }, 1)).toString());
} catch {
  say('Su R2 non c\'e\' ancora un listino EN.');
}
const currentMs = current ? Date.parse(current.createdAt) : NaN;
const sameCoverage = current && Object.keys(current.expansions ?? {}).length === Object.keys(expansions).length;
if (Number.isFinite(currentMs) && currentMs >= createdMs && sameCoverage && !FORCE) {
  say(`Su R2 c'e' gia' il listino EN del ${current.createdAt} con le stesse espansioni: niente da caricare.`);
  writeSummary();
  process.exit(0);
}
await runWrangler(['r2', 'object', 'put', objectPath, `--file="${OUT}"`, '--content-type', 'application/json', '--remote'], {}, 2);
const check = JSON.parse((await runWrangler(['r2', 'object', 'get', objectPath, '--remote', '--pipe'], { binary: true }, 2)).toString());
if (check.createdAt !== createdAt || Object.keys(check.expansions ?? {}).length !== Object.keys(expansions).length) {
  fail('il file riletto da R2 non corrisponde a quello caricato');
}
say(`Caricato su R2 (${CARDMARKET_EN_R2_KEY}).`);
writeSummary();
