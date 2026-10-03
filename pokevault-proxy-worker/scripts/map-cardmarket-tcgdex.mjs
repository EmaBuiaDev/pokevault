#!/usr/bin/env node
// Collega ogni carta del catalogo al suo prodotto Cardmarket (idProduct) e lo
// scrive in card_cardmarket (schema/013). Da qui build-cardmarket-prices.mjs
// sa quale riga del listino pubblico e' il prezzo di quale carta.
//
//   node scripts/map-cardmarket-tcgdex.mjs <expansionId...>        (prova: solo report)
//   node scripts/map-cardmarket-tcgdex.mjs --all --apply           (scrive su D1)
//   opzioni: --only-missing   salta le carte gia' collegate
//            --cache FILE     cache su disco delle risposte TCGdex (ndjson)
//            --guide FILE     listino da file invece che scaricato (ripetibile)
//            --report FILE    salva il dettaglio carta per carta in JSON
//
// Due fonti per ogni carta, perche' nessuna delle due basta da sola:
//  T = l'idProduct che TCGdex associa alla carta (pricing.cardmarket.idProduct);
//  P = il prodotto che PokeWallet usa OGGI per quella carta, ricostruito dai
//      suoi prezzi: PokeWallet riprende il listino Cardmarket, e le medie
//      (avg, avg1, avg7, avg30) identiche indicano lo stesso prodotto.
// Misurato il 03/10/2026 sull'intero catalogo: T e P concordano quasi sempre.
// Quando no, quasi sempre sbaglia TCGdex: per full art e rare segrete (XY,
// SM) indica il prodotto della versione normale -- la Yveltal-EX full art
// prenderebbe 0,02 EUR invece di ~10. Da qui le regole:
//  - T e P uguali                     -> quello ('both');
//  - T e P diversi                    -> decide il prezzo TCGplayer della carta
//                                        (fonte indipendente), se manca vale P
//                                        ('arbiter' / 'pokewallet');
//  - solo T                           -> si accetta solo se nessun'altra carta
//                                        dello stesso set ha lo stesso T, e se
//                                        il prezzo non e' lontanissimo da
//                                        TCGplayer ('tcgdex'); se no niente;
//  - solo P                           -> P ('pokewallet').
// Una carta senza collegamento resta coi prezzi PokeWallet, come oggi.

import fs from 'node:fs';
import zlib from 'node:zlib';
import { TCGDEX_ID_OVERRIDES } from './lib/tcgdex-set-id-map.mjs';
import {
  WORKER_URL, d1Select, d1ExecuteFile, downloadPriceGuide, eurPrice, priceKey, sqlString, workerRoot,
} from './lib/cardmarket.mjs';
import path from 'node:path';

const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const values = (name) => args.flatMap((a, i) => (a === name && args[i + 1] ? [args[i + 1]] : []));
const APPLY = flag('--apply');
const ALL = flag('--all');
const ONLY_MISSING = flag('--only-missing');
const CACHE_FILE = values('--cache')[0] ?? null;
const GUIDE_FILES = values('--guide');
const REPORT_FILE = values('--report')[0] ?? null;
const optionValues = new Set([...values('--cache'), ...values('--guide'), ...values('--report')]);
const EXPANSIONS = args.filter((a) => !a.startsWith('--') && !optionValues.has(a));
const CONCURRENCY = 8;
const USD_TO_EUR = 0.86; // solo per confrontare ordini di grandezza, non per mostrare prezzi

if (!ALL && EXPANSIONS.length === 0) {
  console.error('Uso: node scripts/map-cardmarket-tcgdex.mjs <expansionId...> | --all  [--apply] [--only-missing] [--cache FILE] [--guide FILE] [--report FILE]');
  process.exit(2);
}

// ── cache TCGdex ──
const cache = new Map();
if (CACHE_FILE && fs.existsSync(CACHE_FILE)) {
  for (const line of fs.readFileSync(CACHE_FILE, 'utf8').split('\n')) {
    if (line) { const o = JSON.parse(line); cache.set(o.url, o.value); }
  }
}
const cacheOut = CACHE_FILE ? fs.createWriteStream(CACHE_FILE, { flags: 'a' }) : null;

async function tcgdex(url, slim) {
  if (cache.has(url)) return cache.get(url);
  let lastErr;
  for (let i = 0; i < 4; i += 1) {
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(15000) });
      if (res.status === 404) { remember(url, null); return null; }
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const value = slim(await res.json());
      remember(url, value);
      return value;
    } catch (err) {
      lastErr = err;
      await new Promise((r) => setTimeout(r, 1000 * (i + 1)));
    }
  }
  throw new Error(`TCGdex ${url}: ${lastErr?.message}`);
}
function remember(url, value) {
  cache.set(url, value);
  cacheOut?.write(JSON.stringify({ url, value }) + '\n');
}
const slimSet = (j) => ({ cards: (j.cards ?? []).map((c) => ({ id: c.id, localId: c.localId })) });
function slimCard(j) {
  const tp = j.pricing?.tcgplayer;
  const pick = tp ? (tp.normal ?? tp.holofoil ?? tp['1st-edition'] ?? tp.unlimited ?? tp['reverse-holofoil'] ?? null) : null;
  return { id: j.id, idProduct: j.pricing?.cardmarket?.idProduct ?? null, usd: pick?.marketPrice ?? pick?.midPrice ?? null };
}
const localKey = (raw) => { const s = String(raw ?? '').trim(); return /^\d+$/.test(s) ? String(parseInt(s, 10)) : s.toUpperCase(); };

// ── 1. carte dal catalogo ──
const scope = ALL ? '' : `AND c.expansion_id IN (${EXPANSIONS.map(sqlString).join(',')})`;
const cards = await d1Select(
  `SELECT c.card_id, c.expansion_id, c.card_number FROM cards c WHERE 1=1 ${scope} ORDER BY c.expansion_id, c.card_id`,
);
const already = ONLY_MISSING ? new Set((await d1Select('SELECT card_id FROM card_cardmarket')).map((r) => r.card_id)) : new Set();
const todo = cards.filter((c) => !already.has(c.card_id));
console.log(`Carte nel catalogo: ${cards.length}${ONLY_MISSING ? `, gia' collegate ${already.size}, da fare ${todo.length}` : ''}`);
if (todo.length === 0) process.exit(0);

// ── 2. listino e prezzi di oggi (PokeWallet) ──
const guides = GUIDE_FILES.length > 0
  ? GUIDE_FILES.map((f) => JSON.parse(f.endsWith('.gz') ? zlib.gunzipSync(fs.readFileSync(f)) : fs.readFileSync(f)))
  : [await downloadPriceGuide()];
const guideById = new Map();
for (const g of guides) for (const p of g.priceGuides) guideById.set(p.idProduct, p); // l'ultimo file vince
console.log(`Listino: ${guides.map((g) => g.createdAt).join(', ')} (${guideById.size} prodotti)`);

const r2 = (x) => (typeof x === 'number' ? Math.round(x * 100) : 'n');
const tupleIndex = new Map();
const addTuple = (k, id) => { if (!tupleIndex.has(k)) tupleIndex.set(k, new Set()); tupleIndex.get(k).add(id); };
for (const g of guides) {
  for (const p of g.priceGuides) {
    addTuple(`A${r2(p.avg)}|${r2(p.avg1)}|${r2(p.avg7)}|${r2(p.avg30)}`, p.idProduct);
    addTuple(`M${r2(p.avg1)}|${r2(p.avg7)}|${r2(p.avg30)}`, p.idProduct);
  }
}
const snapRes = await fetch(`${WORKER_URL}/ita/prices.json`);
if (!snapRes.ok) throw new Error(`Snapshot prezzi non leggibile: HTTP ${snapRes.status}`);
const snapshot = await snapRes.json();

/** Il prodotto che PokeWallet usa per questa carta, o null se non si capisce. */
function pokewalletProduct(entry, hintT) {
  if (!entry || entry.avg1 == null || entry.avg7 == null || entry.avg30 == null) return null;
  for (const key of [`A${r2(entry.avg)}|${r2(entry.avg1)}|${r2(entry.avg7)}|${r2(entry.avg30)}`, `M${r2(entry.avg1)}|${r2(entry.avg7)}|${r2(entry.avg30)}`]) {
    const ids = tupleIndex.get(key);
    if (!ids || ids.size === 0) continue;
    if (hintT != null && ids.has(hintT)) return hintT; // compatibile con TCGdex: e' lui
    if (ids.size === 1) return [...ids][0];
    return null; // piu' prodotti con gli stessi valori: non si sa
  }
  return null;
}

// ── 3. TCGdex ──
const byExpansion = new Map();
for (const c of todo) {
  if (!byExpansion.has(c.expansion_id)) byExpansion.set(c.expansion_id, []);
  byExpansion.get(c.expansion_id).push(c);
}
const jobs = [];
const setsMissing = [];
for (const [expansionId, list] of byExpansion) {
  const tid = TCGDEX_ID_OVERRIDES[expansionId] ?? expansionId;
  const set = await tcgdex(`https://api.tcgdex.net/v2/en/sets/${encodeURIComponent(tid)}`, slimSet);
  if (!set) { setsMissing.push(expansionId); continue; }
  const byLocal = new Map(set.cards.map((c) => [localKey(c.localId), c.id]));
  for (const c of list) jobs.push({ card: c, tcgdexId: byLocal.get(localKey(c.card_number)) ?? null });
}
let next = 0;
let done = 0;
await Promise.all(Array.from({ length: CONCURRENCY }, async () => {
  while (next < jobs.length) {
    const job = jobs[next++];
    if (job.tcgdexId) job.detail = await tcgdex(`https://api.tcgdex.net/v2/en/cards/${encodeURIComponent(job.tcgdexId)}`, slimCard);
    done += 1;
    if (done % 2000 === 0) console.log(`  TCGdex ${done}/${jobs.length}`);
  }
}));
cacheOut?.end();

// ── 4. decisione ──
const priceOf = (id) => { const e = eurPrice(guideById.get(id)); return e ? (e.low ?? e.avg ?? e.trend) : null; };
function plausible(eur, usd) {
  if (usd == null || eur == null) return true;
  const ref = Math.max(usd * USD_TO_EUR, 0.01);
  const p = Math.max(eur, 0.01);
  return Math.abs(p - ref) <= 3 || (p / ref <= 8 && ref / p <= 8);
}
// `a` (TCGdex) batte `b` (il prezzo di oggi) solo se e' piu' vicino a TCGplayer
// di almeno una volta e mezza: nel dubbio resta quello che l'utente vede gia'.
// Senza margine il Mew di Evoluzioni passava da 5 a 130 EUR con TCGplayer a 28.
const SWITCH_MARGIN = Math.log(1.5);
const closer = (a, b, usd) => {
  const ref = Math.max(usd * USD_TO_EUR, 0.01);
  const d = (x) => Math.abs(Math.log(Math.max(x, 0.01) / ref));
  return d(a) + SWITCH_MARGIN < d(b);
};

const tCount = new Map();
for (const j of jobs) {
  const t = j.detail?.idProduct;
  if (t) { const k = `${j.card.expansion_id}|${t}`; tCount.set(k, (tCount.get(k) ?? 0) + 1); }
}

const decisions = [];
const tally = {};
const bump = (k) => { tally[k] = (tally[k] ?? 0) + 1; };
for (const j of jobs) {
  const { card } = j;
  const key = priceKey(card.card_number);
  const today = key ? snapshot.expansions?.[card.expansion_id]?.prices?.[key] : null;
  const T = j.detail?.idProduct && guideById.has(j.detail.idProduct) ? j.detail.idProduct : null;
  const usd = j.detail?.usd ?? null;
  const P = pokewalletProduct(today, T);
  let chosen = null;
  let source = null;
  let reason = null;

  if (T && P && T === P) { chosen = T; source = 'both'; }
  else if (T && P) {
    const pt = priceOf(T); const pp = priceOf(P);
    if (usd != null && pt != null && pp != null && plausible(pt, usd) && closer(pt, pp, usd)) { chosen = T; source = 'arbiter'; }
    else { chosen = P; source = 'pokewallet'; }
  } else if (T) {
    const pt = priceOf(T);
    const oggiEur = today ? (today.low ?? today.avg ?? today.trend ?? null) : null;
    const vicinoAOggi = oggiEur == null || pt == null || Math.abs(pt - oggiEur) <= Math.max(1, 0.5 * Math.min(pt, oggiEur));
    if ((tCount.get(`${card.expansion_id}|${T}`) ?? 0) > 1) reason = 'prodotto condiviso con un altra carta del set';
    else if (pt == null) reason = 'nel listino senza prezzo';
    else if (!plausible(pt, usd)) reason = 'prezzo lontano da TCGplayer';
    // Oggi ha un prezzo, ma non si capisce da quale prodotto: e' come un
    // disaccordo con PokeWallet, e si cambia solo con l'arbitro a favore.
    else if (!vicinoAOggi && !(usd != null && closer(pt, oggiEur, usd))) reason = 'diverso da oggi senza arbitro a favore';
    else { chosen = T; source = 'tcgdex'; }
  } else if (P) { chosen = P; source = 'pokewallet'; }
  else reason = j.tcgdexId ? (j.detail?.idProduct ? 'idProduct TCGdex non nel listino' : 'TCGdex senza idProduct') : 'carta non su TCGdex';

  if (chosen && priceOf(chosen) == null) { reason = 'nel listino senza prezzo'; chosen = null; source = null; }
  bump(source ?? `scartata: ${reason}`);

  const oggi = today ? (today.low ?? today.avg ?? null) : null;
  const nuovo = chosen ? priceOf(chosen) : null;
  decisions.push({ card_id: card.card_id, expansion_id: card.expansion_id, number: key, T, P, usd, chosen, source, reason, oggi, nuovo });
}

// ── 5. confronto con i prezzi di oggi ──
const linked = decisions.filter((d) => d.chosen);
const withToday = linked.filter((d) => d.oggi != null);
const same = withToday.filter((d) => Math.abs(d.oggi - d.nuovo) < 0.005).length;
const near = withToday.filter((d) => Math.abs(d.oggi - d.nuovo) <= Math.max(0.05, 0.1 * d.oggi)).length;
const far = withToday.filter((d) => Math.abs(d.oggi - d.nuovo) > Math.max(1, 0.5 * Math.min(d.oggi, d.nuovo)));
console.log('\nEsito per carta:', tally);
if (setsMissing.length) console.log(`Set non trovati su TCGdex: ${setsMissing.join(', ')}`);
console.log(`Collegate: ${linked.length}/${decisions.length}; con prezzo anche oggi: ${withToday.length} (uguali ${same}, entro 10% ${near}, molto diversi ${far.length}); senza prezzo oggi: ${linked.length - withToday.length}`);
for (const d of far.slice(0, 40)) console.log(`  ${d.expansion_id} ${d.number}: oggi ${d.oggi} -> ${d.nuovo} (${d.source}, usd ${d.usd})`);
if (REPORT_FILE) fs.writeFileSync(REPORT_FILE, JSON.stringify(decisions, null, 1));

// ── 6. scrittura ──
if (!APPLY) {
  console.log('\nProva: niente scritto. Aggiungi --apply per salvare su D1.');
  process.exit(0);
}
const tmpDir = path.join(workerRoot, 'scripts', '.cardmarket-tmp');
fs.mkdirSync(tmpDir, { recursive: true });
const now = Math.floor(Date.now() / 1000);
const rows = linked.map((d) => `(${sqlString(d.card_id)},${d.chosen},${sqlString(d.source)},${now})`);
const BATCH = 500;
for (let i = 0; i < rows.length; i += BATCH) {
  const file = path.join(tmpDir, `batch-${i / BATCH}.sql`);
  fs.writeFileSync(file, `INSERT OR REPLACE INTO card_cardmarket (card_id, id_product, source, updated_at) VALUES\n${rows.slice(i, i + BATCH).join(',\n')};\n`);
  await d1ExecuteFile(file);
  console.log(`  scritte ${Math.min(i + BATCH, rows.length)}/${rows.length}`);
}
// Una carta che prima era collegata e ora no (fonte cambiata) non deve restare col vecchio prodotto.
const unlinked = decisions.filter((d) => !d.chosen).map((d) => d.card_id);
for (let i = 0; i < unlinked.length; i += BATCH) {
  const file = path.join(tmpDir, `del-${i / BATCH}.sql`);
  fs.writeFileSync(file, `DELETE FROM card_cardmarket WHERE card_id IN (${unlinked.slice(i, i + BATCH).map(sqlString).join(',')});\n`);
  await d1ExecuteFile(file);
}
fs.rmSync(tmpDir, { recursive: true, force: true });
const [{ n }] = await d1Select('SELECT COUNT(*) AS n FROM card_cardmarket');
console.log(`\nFatto. card_cardmarket ora ha ${n} righe.`);
