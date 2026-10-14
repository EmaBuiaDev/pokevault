#!/usr/bin/env node
// Immagini mancanti del catalogo EN da pkmncards.com (14/10/2026).
//
// Quarta fonte, dopo TCGdex, pokemontcg.io e Limitless: copre i set che le
// altre non hanno (Trainer Kit, McDonald's, promo recenti). Riempie SOLO le
// carte senza immagine ('missing'); quelle gia' 'ok' non si toccano.
//
// Le carte si abbinano per numero E nome: pkmncards scrive "Fletchling ·
// XY Trainer Kit—Bisharp (TK7A) #1", e una carta col numero giusto ma il nome
// diverso (numerazioni che non coincidono) resta fuori e finisce nel rapporto.
// Dopo, copertura e pubblicazione del set si ricalcolano con la regola
// dell'import (80%).
//
//   node scripts/fill-images-pkmncards.mjs <expansionId> <slug pkmncards>          (a secco)
//   node scripts/fill-images-pkmncards.mjs <expansionId> <slug pkmncards> --apply
//
// Lo slug e' quello della pagina del set: pkmncards.com/set/<slug>/.
import { mkdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import { runWrangler, sqlString, workerRoot } from './lib/cardmarket.mjs';

const EN_DB = 'pokevault-catalog-en';
const BUCKET = 'pokevault-images';
const COVERAGE_THRESHOLD = 0.8;
const UA = 'PokeVault-catalog/1.0 (catalogo carte; contatto: pagina GitHub EmaBuiaDev/pokevault)';

const [expansionId, slug, ...rest] = process.argv.slice(2);
const APPLY = rest.includes('--apply');
// --per-nome N: le ultime N carte della pagina sono un sottoinsieme col suo
// numero originale (le Classic Collection: "Blastoise #2" e' la nostra CC001).
// Si guardano solo quelle e si abbinano per nome, ignorando il numero.
// --riusa: la stessa immagine per piu' carte uguali (energie base dei kit).
const REUSE = rest.includes('--riusa');
const byNameTail = (() => { const i = rest.indexOf('--per-nome'); return i >= 0 ? parseInt(rest[i + 1], 10) : null; })();
if (!expansionId || !slug) {
  console.error('Uso: node scripts/fill-images-pkmncards.mjs <expansionId> <slug pkmncards> [--apply]');
  process.exit(2);
}

async function enSelect(sql) {
  const oneLine = sql.replace(/\s+/g, ' ').trim();
  const out = await runWrangler(['d1', 'execute', EN_DB, '--remote', '--json', '--command', `"${oneLine.replace(/"/g, '\\"')}"`]);
  const start = out.indexOf('[');
  return JSON.parse(start >= 0 ? out.slice(start) : out)[0]?.results ?? [];
}

/** "007" -> "7"; i numeri con lettere restano come sono (in maiuscolo). */
export function normNumber(raw) {
  const s = String(raw ?? '').trim();
  if (/^\d+$/.test(s)) return String(parseInt(s, 10));
  // "H01" e "H1" sono la stessa carta (le olografiche di Aquapolis e Skyridge).
  const prefixed = /^([A-Za-z]+)0*(\d+)$/.exec(s);
  return prefixed ? `${prefixed[1].toUpperCase()}${prefixed[2]}` : s.toUpperCase();
}

const ENERGY_SYMBOLS = { G: 'grass', R: 'fire', W: 'water', L: 'lightning', P: 'psychic', F: 'fighting', D: 'darkness', M: 'metal', Y: 'fairy', C: 'colorless' };

/** Per confrontare due nomi: minuscolo, senza accenti, senza segni, senza "basic"; "{G} Energy" e' "grass energy". */
export function normName(raw) {
  return String(raw ?? '').replace(/\{([A-Z])\}/g, (m, k) => ENERGY_SYMBOLS[k] ?? m).normalize('NFD').replace(/\p{Mn}+/gu, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ').replace(/^basic /, '').trim();
}

/** Le carte di una pagina set di pkmncards: numero, nome, immagine a piena risoluzione. */
export function parseSetPage(html) {
  const out = [];
  // Il numero puo' mancare: le energie base senza numero ("Grass Energy · Sun & Moon Energy (SME)").
  const re = /title="([^"]+?) · [^"]*?(?: #([0-9A-Za-z-]+))?" class="card-image-link"><img[^>]*?\ssrc="([^"]+)"/g;
  for (const m of html.matchAll(re)) {
    out.push({ name: m[1].replace(/&#0?39;|&#8217;/g, "'").replace(/&amp;|&#0?38;/g, '&'), number: normNumber(m[2]), url: m[3] });
  }
  return out;
}

async function fetchWithRetry(url, asBuffer) {
  for (let i = 1; ; i += 1) {
    try {
      const res = await fetch(url, { headers: { 'User-Agent': UA }, signal: AbortSignal.timeout(30000) });
      if (res.status === 404) return null;
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return asBuffer ? Buffer.from(await res.arrayBuffer()) : await res.text();
    } catch (err) {
      if (i >= 4) throw new Error(`${url}: ${err.message}`);
      await new Promise((r) => setTimeout(r, 1500 * i));
    }
  }
}

// ── il nostro set ──
const cards = await enSelect(`SELECT card_id, card_number, nome, image_status FROM cards WHERE expansion_id = ${sqlString(expansionId)}`);
if (cards.length === 0) { console.error(`Nessuna carta per "${expansionId}" nel catalogo EN.`); process.exit(1); }
const missing = cards.filter((c) => c.image_status !== 'ok');
console.log(`${expansionId}: ${cards.length} carte, senza immagine ${missing.length}`);

// ── pkmncards ──
const html = await fetchWithRetry(`https://pkmncards.com/set/${encodeURIComponent(slug)}/?display=images`, false);
if (!html) { console.error(`pkmncards non ha la pagina /set/${slug}/`); process.exit(1); }
const remote = parseSetPage(html);
console.log(`pkmncards ${slug}: ${remote.length} carte`);
const byNumber = new Map();
for (const r of remote) { if (!byNumber.has(r.number)) byNumber.set(r.number, []); byNumber.get(r.number).push(r); }

const matched = [];
const wrongName = [];
const notFound = [];
if (byNameTail) {
  const pool = remote.slice(-byNameTail);
  const used = new Set();
  for (const c of missing) {
    const wanted = normName(c.nome);
    const free = REUSE ? pool : pool.filter((r) => !used.has(r));
    const hit = free.find((r) => normName(r.name) === wanted)
      ?? free.find((r) => normName(r.name).includes(wanted) || wanted.includes(normName(r.name)));
    if (hit) { used.add(hit); matched.push({ card: c, remote: hit }); } else notFound.push(`${c.card_number} ${c.nome}`);
  }
}
for (const c of byNameTail ? [] : missing) {
  const candidates = byNumber.get(normNumber(c.card_number)) ?? [];
  if (candidates.length === 0) { notFound.push(c.card_number); continue; }
  const wanted = normName(c.nome);
  const hit = candidates.find((r) => normName(r.name) === wanted)
    ?? candidates.find((r) => normName(r.name).includes(wanted) || wanted.includes(normName(r.name)));
  if (hit) matched.push({ card: c, remote: hit });
  else wrongName.push(`${c.card_number} "${c.nome}" vs "${candidates[0].name}"`);
}
console.log(`Abbinate ${matched.length}; numero senza carta su pkmncards ${notFound.length}; nome diverso ${wrongName.length}`);
if (byNameTail) for (const m of matched) console.log(`  ${m.card.card_number} ${m.card.nome} <- ${m.remote.name} #${m.remote.number}`);
if (wrongName.length) console.log(`  nome diverso: ${wrongName.slice(0, 15).join('; ')}${wrongName.length > 15 ? '...' : ''}`);
if (notFound.length) console.log(`  non trovate: ${notFound.slice(0, 30).join(', ')}${notFound.length > 30 ? '...' : ''}`);

if (!APPLY) { console.log('A SECCO: niente scritto. Rilancia con --apply.'); process.exit(0); }

// ── caricamento ──
const tmpDir = path.join(workerRoot, 'scripts', '.pkmncards-tmp', expansionId);
await mkdir(tmpDir, { recursive: true });
const done = [];
for (const { card, remote: r } of matched) {
  try {
    const raw = await fetchWithRetry(r.url, true);
    if (!raw) { console.warn(`  ${card.card_number}: immagine sparita (${r.url})`); continue; }
    const webp = await sharp(raw).resize({ width: 600, withoutEnlargement: true }).webp({ quality: 82 }).toBuffer();
    const folder = card.card_id.split('_EN_')[0];
    const file = path.join(tmpDir, card.card_id);
    await writeFile(file, webp);
    await runWrangler(['r2', 'object', 'put', `${BUCKET}/en/${folder}/${card.card_id}`, '--file', `"${file}"`, '--remote', '--content-type', 'image/webp']);
    await rm(file, { force: true });
    done.push(card.card_id);
  } catch (err) {
    console.error(`  ${card.card_number}: ${err.message.slice(0, 200)}`);
  }
}
await rm(tmpDir, { recursive: true, force: true });

if (done.length) {
  const list = done.map(sqlString).join(',');
  await enSelect(`UPDATE cards SET image_status = 'ok', image_source = 'pkmncards', updated_at = unixepoch() WHERE card_id IN (${list})`);
}
const [{ ok, total }] = await enSelect(`SELECT SUM(CASE WHEN image_status = 'ok' THEN 1 ELSE 0 END) AS ok, COUNT(*) AS total FROM cards WHERE expansion_id = ${sqlString(expansionId)}`);
const coverage = total ? ok / total : 0;
const published = coverage >= COVERAGE_THRESHOLD ? 1 : 0;
await enSelect(`UPDATE expansions SET coverage_pct = ${(coverage * 100).toFixed(1)}, published = ${published} WHERE id = ${sqlString(expansionId)}`);
console.log(`Caricate ${done.length}. ${expansionId}: con immagine ${ok}/${total} (${(coverage * 100).toFixed(1)}%), published = ${published}`);
