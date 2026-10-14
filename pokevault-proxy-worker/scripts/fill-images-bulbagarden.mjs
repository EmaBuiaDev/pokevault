#!/usr/bin/env node
// Immagini mancanti del catalogo EN da Bulbagarden Archives (14/10/2026).
//
// Per i set che nessun'altra fonte ha (i McDonald's 2014-2018 e 2024):
// Bulbagarden li cataloga con le immagini delle carte ORIGINALI da cui sono
// ristampati -- stesso disegno, senza il simbolo McDonald's. Deciso con
// l'utente di usarle lo stesso; si segnano con image_source 'original-print',
// cosi' una fonte con le scansioni vere le puo' sostituire.
//
// Le carte si abbinano per nome: i file si chiamano "<Nome><SetOriginale><n>"
// ("WeedleKalosStarterSet1.jpg", "AlolanMeowthSunMoon78.jpg"). A parita' di
// nome vince l'ordine della categoria, che e' quello del set. Riempie SOLO
// le carte senza immagine, poi ricalcola copertura e pubblicazione (80%).
//
//   node scripts/fill-images-bulbagarden.mjs <expansionId> "<Categoria>"           (a secco)
//   node scripts/fill-images-bulbagarden.mjs <expansionId> "<Categoria>" --apply [--esatte]
//   (--esatte: la categoria ha le scansioni del set stesso, non delle carte originali)
//
// Esempio: node scripts/fill-images-bulbagarden.mjs 2014xy "McDonald's Collection 2014"
import { mkdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import sharp from 'sharp';
import { runWrangler, sqlString, workerRoot } from './lib/cardmarket.mjs';

const EN_DB = 'pokevault-catalog-en';
const BUCKET = 'pokevault-images';
const COVERAGE_THRESHOLD = 0.8;
const API = 'https://archives.bulbagarden.net/w/api.php';
const UA = 'PokeVault-catalog/1.0 (catalogo carte; GitHub EmaBuiaDev/pokevault)';
// Le scansioni del set vero si segnano 'bulbagarden' (--esatte); le carte
// originali al posto delle ristampe 'original-print', sostituibili.
const SOURCE = process.argv.includes('--esatte') ? 'bulbagarden' : 'original-print';
const execFileAsync = promisify(execFile);

const [expansionId, category, ...rest] = process.argv.slice(2);
const APPLY = rest.includes('--apply');
// --riusa: la stessa immagine per piu' carte uguali (un Trainer Kit ha dieci
// Water Energy identiche).
const REUSE = rest.includes('--riusa');
if (!expansionId || !category) {
  console.error('Uso: node scripts/fill-images-bulbagarden.mjs <expansionId> "<Categoria Bulbagarden>" [--apply]');
  process.exit(2);
}

async function enSelect(sql) {
  const oneLine = sql.replace(/\s+/g, ' ').trim();
  const out = await runWrangler(['d1', 'execute', EN_DB, '--remote', '--json', '--command', `"${oneLine.replace(/"/g, '\\"')}"`]);
  const start = out.indexOf('[');
  return JSON.parse(start >= 0 ? out.slice(start) : out)[0]?.results ?? [];
}

/**
 * Scarica con curl: l'archivio sta dietro una protezione che risponde 403 al
 * fetch di Node e lascia passare curl. Riprova con attese crescenti.
 */
async function curlGet(url, binary) {
  for (let i = 1; ; i += 1) {
    try {
      const { stdout } = await execFileAsync('curl', ['-s', '-f', '-L', '--max-time', '60', '-A', UA, url], {
        encoding: binary ? 'buffer' : 'utf8',
        maxBuffer: 50 * 1024 * 1024,
      });
      return stdout;
    } catch (err) {
      if (i >= 5) throw new Error(`${url}: curl ${err.code ?? err.message}`);
      await new Promise((r) => setTimeout(r, 4000 * i));
    }
  }
}

async function api(params) {
  return JSON.parse(await curlGet(`${API}?${new URLSearchParams({ format: 'json', ...params })}`, false));
}

/** "Alolan Meowth" -> "alolanmeowth": come il nome sta all'inizio del file. */
const squash = (s) => String(s ?? '').normalize('NFD').replace(/\p{Mn}+/gu, '').toLowerCase().replace(/[^a-z0-9]/g, '');

// ── il nostro set ──
const cards = await enSelect(`SELECT card_id, card_number, nome, image_status FROM cards WHERE expansion_id = ${sqlString(expansionId)}`);
if (cards.length === 0) { console.error(`Nessuna carta per "${expansionId}".`); process.exit(1); }
cards.sort((a, b) => (parseInt(a.card_number, 10) || 0) - (parseInt(b.card_number, 10) || 0));
const missing = cards.filter((c) => c.image_status !== 'ok');
console.log(`${expansionId}: ${cards.length} carte, senza immagine ${missing.length}`);

// ── la categoria: solo le immagini delle carte (niente simbolo, logo, busta) ──
const members = (await api({ action: 'query', list: 'categorymembers', cmtitle: `Category:${category}`, cmtype: 'file', cmlimit: '200' }))
  ?.query?.categorymembers ?? [];
const files = members.map((m) => m.title.replace(/^File:/, ''))
  .filter((t) => /\.(jpe?g|png)$/i.test(t) && !/setsymbol|logo|booster/i.test(t));
console.log(`Bulbagarden "${category}": ${files.length} immagini di carte`);

// Abbinamento per nome; a parita' di nome (due Eevee) l'ordine della categoria.
const used = new Set();
const matched = [];
const unmatched = [];
for (const c of missing) {
  // Anche senza la "s" del possessivo: "Professor Elm's Training Method"
  // nell'archivio e' "ProfessorElmTrainingMethod...".
  const wantedForms = [squash(c.nome), squash(String(c.nome).replace(/['’]s/g, ''))];
  // Prima i file nel formato delle scansioni ("EeveeSunMoon101.jpg"): quelli
  // con spazi sono altro (illustrazioni, "Eevee Shibuzoh.png").
  const candidates = files.filter((f) => (REUSE || !used.has(f)) && wantedForms.some((w) => squash(f).startsWith(w)));
  const hit = candidates.find((f) => !/\s/.test(f)) ?? candidates[0];
  if (hit) { used.add(hit); matched.push({ card: c, file: hit }); } else unmatched.push(`${c.card_number} ${c.nome}`);
}
console.log(`Abbinate ${matched.length}; senza file ${unmatched.length}${unmatched.length ? ': ' + unmatched.join(', ') : ''}`);
for (const m of matched) console.log(`  ${m.card.card_number} ${m.card.nome} <- ${m.file}`);

if (!APPLY) { console.log('A SECCO: niente scritto. Rilancia con --apply.'); }
else {
  // Gli indirizzi veri dei file, a gruppi di 40 titoli.
  const urlByFile = new Map();
  for (let i = 0; i < matched.length; i += 40) {
    const titles = matched.slice(i, i + 40).map((m) => `File:${m.file}`).join('|');
    const pages = (await api({ action: 'query', titles, prop: 'imageinfo', iiprop: 'url' }))?.query?.pages ?? {};
    for (const p of Object.values(pages)) {
      const url = p.imageinfo?.[0]?.url;
      if (url) urlByFile.set(p.title.replace(/^File:/, ''), url);
    }
  }

  const tmpDir = path.join(workerRoot, 'scripts', '.bulbagarden-tmp', expansionId);
  await mkdir(tmpDir, { recursive: true });
  const done = [];
  for (const { card, file } of matched) {
    const url = urlByFile.get(file);
    if (!url) { console.warn(`  ${card.card_number}: indirizzo di ${file} non trovato`); continue; }
    try {
      const webp = await sharp(await curlGet(url, true)).resize({ width: 600, withoutEnlargement: true }).webp({ quality: 82 }).toBuffer();
      const folder = card.card_id.split('_EN_')[0];
      const local = path.join(tmpDir, card.card_id);
      await writeFile(local, webp);
      await runWrangler(['r2', 'object', 'put', `${BUCKET}/en/${folder}/${card.card_id}`, '--file', `"${local}"`, '--remote', '--content-type', 'image/webp']);
      await rm(local, { force: true });
      done.push(card.card_id);
    } catch (err) {
      console.error(`  ${card.card_number}: ${err.message.slice(0, 200)}`);
    }
  }
  await rm(tmpDir, { recursive: true, force: true });

  if (done.length) {
    await enSelect(`UPDATE cards SET image_status = 'ok', image_source = '${SOURCE}', updated_at = unixepoch() WHERE card_id IN (${done.map(sqlString).join(',')})`);
  }
  const [{ ok, total }] = await enSelect(`SELECT SUM(CASE WHEN image_status = 'ok' THEN 1 ELSE 0 END) AS ok, COUNT(*) AS total FROM cards WHERE expansion_id = ${sqlString(expansionId)}`);
  const coverage = total ? ok / total : 0;
  const published = coverage >= COVERAGE_THRESHOLD ? 1 : 0;
  await enSelect(`UPDATE expansions SET coverage_pct = ${(coverage * 100).toFixed(1)}, published = ${published} WHERE id = ${sqlString(expansionId)}`);
  console.log(`Caricate ${done.length}. ${expansionId}: con immagine ${ok}/${total} (${(coverage * 100).toFixed(1)}%), published = ${published}`);
}
