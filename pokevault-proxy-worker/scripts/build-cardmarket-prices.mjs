#!/usr/bin/env node
// Ogni mattina: listino pubblico Cardmarket -> prezzi per carta del catalogo -> R2.
//
//   node scripts/build-cardmarket-prices.mjs                 (prova: scrive solo il file locale)
//   node scripts/build-cardmarket-prices.mjs --upload        (carica su R2 se il listino e' nuovo)
//   opzioni: --guide FILE  listino da file (.json o .json.gz) invece che scaricato
//            --out FILE    dove scrivere il JSON (default scripts/.cardmarket-prices.json)
//            --force       carica anche se su R2 c'e' gia' un listino uguale o piu' recente
//
// Il collegamento carta -> idProduct sta in D1 (card_cardmarket, schema/013), lo
// fa scripts/map-cardmarket-tcgdex.mjs. Il file su R2 e' quello che il Worker
// fonde sopra i prezzi PokeWallet (src/cardmarket-prices.ts); stesso formato
// delle voci dello snapshot, quindi l'app non cambia.
//
// Non carica niente, e lo dice uscendo con errore, se il listino sembra rotto:
// meno del 90% delle carte collegate ritrova il suo prezzo. Se Cardmarket non ha
// rinnovato il listino non carica nulla; dopo 48 ore ferme esce con errore
// (la run di Actions fallita e' l'avviso). Il Worker per conto suo smette di
// usare un listino piu' vecchio di 72 ore e torna a PokeWallet.

import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {
  CARDMARKET_R2_BUCKET, CARDMARKET_R2_KEY, EUR_FIELDS, d1Select, downloadPriceGuide, eurPrice, priceKey, rawSetCode, runWrangler, workerRoot,
} from './lib/cardmarket.mjs';

const args = process.argv.slice(2);
const value = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : undefined; };
const UPLOAD = args.includes('--upload');
const FORCE = args.includes('--force');
const GUIDE_FILE = value('--guide');
const OUT = value('--out') ?? path.join(workerRoot, 'scripts', '.cardmarket-prices.json');
const MIN_FOUND_RATIO = 0.9;
const STALE_ALERT_MS = 48 * 60 * 60 * 1000;

const summary = [];
const say = (line) => { console.log(line); summary.push(line); };
function writeSummary() {
  if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, `### Listino Cardmarket\n\n${summary.map((l) => `- ${l}`).join('\n')}\n`);
}
function fail(message) {
  say(`ERRORE: ${message}`);
  writeSummary();
  process.exit(1);
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

// ── 2. collegamenti ──
const rows = await d1Select(
  `SELECT m.card_id, m.id_product, c.expansion_id, c.card_number
   FROM card_cardmarket m JOIN cards c ON c.card_id = m.card_id JOIN expansions e ON e.id = c.expansion_id
   WHERE e.published = 1`,
);
if (rows.length === 0) fail('card_cardmarket e\' vuota: nessuna carta collegata');

// ── 2b. stampe (schema/015) ──
// Se la tabella non c'e' ancora il listino esce come prima, senza prezzi per
// stampa: il Worker e l'app li trattano come facoltativi.
let variantsByCard = new Map();
try {
  const variantRows = await d1Select(
    `SELECT v.card_id, v.variant, v.kind, v.type, v.id_product
     FROM card_variants v JOIN cards c ON c.card_id = v.card_id JOIN expansions e ON e.id = c.expansion_id
     WHERE e.published = 1`,
  );
  for (const v of variantRows) {
    if (!variantsByCard.has(v.card_id)) variantsByCard.set(v.card_id, []);
    variantsByCard.get(v.card_id).push(v);
  }
  say(`Stampe in D1: ${variantRows.length} per ${variantsByCard.size} carte`);
} catch (err) {
  say(`Stampe non lette (${err.message.split('\n')[0]}): listino senza prezzi per stampa`);
}

/** I prezzi "-holo" di un prodotto (la reverse di una carta non olografica). */
function holoPrice(entry) {
  if (!entry) return null;
  const mapped = {};
  for (const f of EUR_FIELDS) mapped[f] = entry[`${f}-holo`];
  return eurPrice(mapped);
}

/**
 * Il prezzo di ogni stampa della carta, solo dove e' diverso da quello della
 * voce principale (che e' la Normale, o la Holo per le carte olografiche).
 *  - Normale, 1a Edizione: il prezzo del prodotto;
 *  - Reverse: le colonne "-holo" del prodotto;
 *  - Holo: il prezzo del prodotto, che per una carta olografica e' gia' lei;
 *    se la carta esce anche Normale sullo stesso prodotto, le colonne "-holo";
 *  - speciali: il loro prodotto. Senza un prodotto loro NON prendono il
 *    prezzo della carta: una Master Ball valutata come la comune e' falso, e
 *    l'app mostra "nessun prezzo".
 */
function variantPrices(cardProduct, main, variants) {
  const out = {};
  const sameProductNormal = variants.some((v) => v.kind === 'base' && (v.variant === 'Normal' || v.variant === '1st Edition') && (v.id_product ?? cardProduct) === cardProduct);
  for (const v of variants) {
    let price = null;
    let product = v.id_product ?? cardProduct;
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
let withVariants = 0;
const missing = [];
for (const row of rows) {
  const key = priceKey(row.card_number);
  const price = eurPrice(byProduct.get(row.id_product));
  if (!key || !price) { missing.push(row.card_id); continue; }
  const exp = (expansions[row.expansion_id] ??= { aliases: [], prices: {} });
  // idProduct: il Worker ne fa il link diretto alla versione esatta.
  exp.prices[key] = { ...price, idProduct: row.id_product };
  const v = variantPrices(row.id_product, price, variantsByCard.get(row.card_id) ?? []);
  if (v) { exp.prices[key].v = v; withVariants += 1; }
  const alias = rawSetCode(row.card_id);
  if (alias && !exp.aliases.includes(alias)) exp.aliases.push(alias);
  found += 1;
}
const ratio = found / rows.length;
say(`Carte collegate: ${rows.length}; col prezzo nel listino: ${found} (${(ratio * 100).toFixed(1)}%); espansioni: ${Object.keys(expansions).length}; con prezzi per stampa: ${withVariants}`);
if (missing.length) console.log(`Senza prezzo nel listino (prime 20): ${missing.slice(0, 20).join(', ')}`);
if (ratio < MIN_FOUND_RATIO) fail(`solo il ${(ratio * 100).toFixed(1)}% delle carte ritrova il prezzo: il listino sembra rotto, non carico niente`);

const blob = { version: 1, createdAt, sourceCreatedAt: guide.createdAt, builtAt: Date.now(), expansions };
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(blob));
say(`File: ${OUT} (${Math.round(fs.statSync(OUT).size / 1024)} KB)`);

if (!UPLOAD) {
  say('Prova: niente caricato su R2 (aggiungi --upload).');
  writeSummary();
  process.exit(0);
}

// ── 4. confronto con quello gia' su R2 ──
const objectPath = `${CARDMARKET_R2_BUCKET}/${CARDMARKET_R2_KEY}`;
let current = null;
try {
  const raw = await runWrangler(['r2', 'object', 'get', objectPath, '--remote', '--pipe'], { binary: true }, 1);
  current = JSON.parse(raw.toString());
} catch {
  say('Su R2 non c\'e\' ancora un listino.');
}
const currentMs = current ? Date.parse(current.createdAt) : NaN;
if (Number.isFinite(currentMs) && currentMs >= createdMs && !FORCE) {
  const ageMs = Date.now() - createdMs;
  say(`Su R2 c'e' gia' il listino del ${current.createdAt}: niente da caricare.`);
  if (ageMs > STALE_ALERT_MS) fail(`Cardmarket non rinnova il listino da ${Math.round(ageMs / 3_600_000)} ore`);
  writeSummary();
  process.exit(0);
}

// ── 5. caricamento e verifica ──
await runWrangler(['r2', 'object', 'put', objectPath, `--file="${OUT}"`, '--content-type', 'application/json', '--remote'], {}, 2);
const check = JSON.parse((await runWrangler(['r2', 'object', 'get', objectPath, '--remote', '--pipe'], { binary: true }, 2)).toString());
if (check.createdAt !== createdAt || Object.keys(check.expansions ?? {}).length !== Object.keys(expansions).length) {
  fail('il file riletto da R2 non corrisponde a quello caricato');
}
say(`Caricato su R2 (${CARDMARKET_R2_KEY}), sostituisce il listino del ${current?.createdAt ?? 'nessuno'}.`);
writeSummary();
