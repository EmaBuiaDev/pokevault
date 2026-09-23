#!/usr/bin/env node
// Ripara le carte che su D1 risultano senza immagine (image_status <> 'ok').
//
// Perche' esiste: ingest-tcgdex-set.mjs verifica ogni immagine con una HEAD e,
// quando quella fallisce, scrive 'missing' senza caricare niente su R2. La HEAD
// da' falsi negativi sotto concorrenza (vedi il commento in cima a quello
// script), quindi restano buchi anche per carte che l'archivio ufficiale espone
// regolarmente. Il 2026-09-13 erano 17, tutte in me05 (la 42 e le 80-95).
//
// Sorgente di riparazione: assets.pokemon.com, indipendente da TCGdex.
//
//   node scripts/repair-missing-images.mjs                 (tutto il catalogo, dry-run)
//   node scripts/repair-missing-images.mjs me05            (un solo set, dry-run)
//   node scripts/repair-missing-images.mjs me05 --apply    (scrive su R2 + D1 + purga la cache)

import { mkdir, writeFile, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import sharp from 'sharp';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.repair-tmp');
const BUCKET = 'pokevault-images';
const DATABASE = 'pokevault-catalog';
// Stesso namespace di wrangler.toml, [[kv_namespaces]] binding CACHE.
const KV_NAMESPACE_ID = '14e664fe935345578443564f353685b9';
const OFFICIAL_BASE = 'https://assets.pokemon.com/static-assets/content-assets/cms2-it-it/img/cards/web';
// Stessa qualita' di recompress-webp.mjs, cosi' le riparate restano
// indistinguibili dal resto della libreria.
const WEBP_QUALITY = 82;

const wranglerBin = path.join(workerRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'wrangler.cmd' : 'wrangler');

function runWrangler(args, { capture = false } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(wranglerBin, args, { cwd: workerRoot, shell: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (d) => { stdout += d.toString(); });
    child.stderr.on('data', (d) => { stderr += d.toString(); });
    child.on('close', (code) => (code === 0
      ? resolve(capture ? stdout : undefined)
      : reject(new Error(`wrangler ${args.slice(0, 3).join(' ')} exited ${code}: ${stderr.slice(0, 400)}`))));
  });
}

// wrangler stampa il suo banner prima del JSON, quindi il parse parte dalla
// prima parentesi quadra invece che dall'inizio dell'output.
function parseWranglerJson(raw) {
  const start = raw.indexOf('[');
  if (start < 0) throw new Error(`risposta wrangler inattesa: ${raw.slice(0, 200)}`);
  return JSON.parse(raw.slice(start));
}

async function d1Query(sql) {
  const out = await runWrangler(
    ['d1', 'execute', DATABASE, '--remote', '--json', '--command', `"${sql.replace(/"/g, '\\"')}"`],
    { capture: true }
  );
  return parseWranglerJson(out)[0].results;
}

function sqlString(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

// L'URL ufficiale vuole il numero SENZA zero iniziali (ME05_IT_42.png), la
// chiave R2 e il card_id su D1 lo vogliono paddato a tre cifre
// (ME05_IT_042.webp). Sbagliare verso costa un 404 silenzioso, quindi le due
// forme restano separate e non intercambiabili.
const unpad = (n) => String(parseInt(n, 10));
const pad = (n) => String(parseInt(n, 10)).padStart(3, '0');

async function main() {
  const args = process.argv.slice(2);
  const apply = args.includes('--apply');
  const expansionFilter = args.find((a) => !a.startsWith('--'))?.trim().toLowerCase() ?? null;

  const where = ["image_status <> 'ok'"];
  if (expansionFilter) where.push(`expansion_id = ${sqlString(expansionFilter)}`);
  const broken = await d1Query(
    `SELECT expansion_id, card_id, card_number, nome FROM cards WHERE ${where.join(' AND ')} ORDER BY expansion_id, CAST(card_number AS INTEGER)`
  );

  if (broken.length === 0) {
    console.log(expansionFilter
      ? `Nessuna carta senza immagine in ${expansionFilter}.`
      : 'Nessuna carta senza immagine in tutto il catalogo.');
    return;
  }
  console.log(`Carte senza immagine: ${broken.length}\n`);

  await mkdir(tmpDir, { recursive: true });
  const prepared = [];
  const unavailable = [];

  for (const card of broken) {
    // "ME05_IT_042.webp" -> "ME05", la stessa estrazione che fa il Worker con
    // ITALIAN_CARD_ID_PREFIX_REGEX.
    const setCode = (card.card_id.match(/^([A-Za-z0-9]+)_IT_/)?.[1] ?? '').toUpperCase();
    const rawNumber = String(card.card_number ?? '').trim();
    if (!setCode || rawNumber === '') {
      unavailable.push({ ...card, reason: 'card_id o numero non interpretabile' });
      continue;
    }

    // I promo non numerano a cifre ("SM01", "SWSH026"): li' il pad/unpad non
    // si applica, e la stringa va usata tale e quale -- e' anche quella che
    // usa l'archivio ufficiale (SMP_IT_SM01.png; SMP_IT_SM1.png da' 404).
    // Scartarli come "non interpretabili" lasciava 111 carte di smp senza
    // immagine per sempre, di cui 64 pubblicate regolarmente a monte
    // (verificato il 15/09/2026).
    const isNumeric = /^\d+$/.test(rawNumber);
    const officialNumber = isNumeric ? unpad(rawNumber) : rawNumber;
    const keyNumber = isNumeric ? pad(rawNumber) : rawNumber;

    const url = `${OFFICIAL_BASE}/${setCode}/${setCode}_IT_${officialNumber}.png`;
    const res = await fetch(url);
    if (!res.ok) {
      unavailable.push({ ...card, reason: `archivio ufficiale HTTP ${res.status}` });
      console.log(`  ${setCode}/${keyNumber} ${card.nome} -- non disponibile a monte (HTTP ${res.status})`);
      continue;
    }

    const png = Buffer.from(await res.arrayBuffer());
    const webp = await sharp(png).webp({ quality: WEBP_QUALITY }).toBuffer();
    const localFile = path.join(tmpDir, `${setCode}_${keyNumber}.webp`);
    await writeFile(localFile, webp);
    prepared.push({
      setCode,
      number: rawNumber,
      cardId: card.card_id,
      key: `it/${setCode}/${setCode}_IT_${keyNumber}.webp`,
      localFile,
    });
    console.log(`  ${setCode}/${keyNumber} ${card.nome}  png ${(png.length / 1024).toFixed(0)} KB -> webp ${(webp.length / 1024).toFixed(0)} KB`);
  }

  console.log(`\nRiparabili: ${prepared.length}, non disponibili a monte: ${unavailable.length}`);

  if (!apply) {
    console.log('\nDRY RUN: nessuna scrittura. Rilancia con --apply.');
    await rm(tmpDir, { recursive: true, force: true });
    return;
  }
  if (prepared.length === 0) {
    await rm(tmpDir, { recursive: true, force: true });
    return;
  }

  console.log('\nCaricamento su R2...');
  for (const item of prepared) {
    await runWrangler(['r2', 'object', 'put', `${BUCKET}/${item.key}`, '--file', item.localFile, '--remote', '--content-type', 'image/webp']);
    console.log(`  ${item.key}`);
  }

  console.log('\nAggiornamento D1...');
  const ids = prepared.map((p) => sqlString(p.cardId)).join(', ');
  const sqlFile = path.join(tmpDir, 'repair.sql');
  await writeFile(sqlFile, `UPDATE cards SET image_status='ok', image_webp=1 WHERE card_id IN (${ids});\n`, 'utf8');
  await runWrangler(['d1', 'execute', DATABASE, '--remote', '--file', sqlFile]);

  // Senza questo passaggio la riparazione non si vede: il Worker cacha anche i
  // 404 delle immagini per 24 ore (getTtlSeconds in src/index.ts), quindi
  // continuerebbe a servire il placeholder per un giorno intero nonostante
  // l'oggetto sia gia' su R2. Verificato il 2026-09-13.
  console.log('\nPurga della cache KV...');
  await purgeKvCache(prepared);

  await rm(tmpDir, { recursive: true, force: true });
  console.log(`\nFatto: ${prepared.length} immagini riparate.`);
  if (unavailable.length > 0) {
    console.log(`Restano ${unavailable.length} carte non disponibili nemmeno sull'archivio ufficiale:`);
    unavailable.forEach((c) => console.log(`  ${c.card_id} ${c.nome} -- ${c.reason}`));
  }
}

async function purgeKvCache(prepared) {
  const bySet = new Map();
  // Confronto per stringa normalizzata e non per intero: "SM01" non e' un
  // numero, e parseInt lo ridurrebbe a NaN facendo saltare la purga proprio
  // alle carte dei set promo.
  const numberKey = (value) => {
    const raw = String(value).trim();
    return /^\d+$/.test(raw) ? String(parseInt(raw, 10)) : raw.toUpperCase();
  };
  for (const item of prepared) {
    if (!bySet.has(item.setCode)) bySet.set(item.setCode, new Set());
    bySet.get(item.setCode).add(numberKey(item.number));
  }

  const toDelete = [];
  for (const [setCode, numbers] of bySet) {
    const raw = await runWrangler(
      ['kv', 'key', 'list', '--namespace-id', KV_NAMESPACE_ID, '--prefix', `"pokewallet:/images/it/${setCode}/"`, '--remote'],
      { capture: true }
    );
    for (const entry of parseWranglerJson(raw)) {
      // Una stessa carta ha piu' chiavi (nuda, ?size=low, ?size=high, piu' le
      // varianti col cache-buster dell'app): vanno via tutte, o l'app continua
      // a pescare quella vecchia.
      const seg = entry.name.match(new RegExp(`^pokewallet:/images/it/${setCode}/([^?]+)`, 'i'))?.[1];
      if (!seg) continue;
      const bare = seg.replace(/\.(webp|png|jpe?g)$/i, '');
      if (numbers.has(numberKey(bare))) toDelete.push(entry.name);
    }
  }

  if (toDelete.length === 0) {
    console.log('  nessuna chiave da cancellare');
    return;
  }
  const listFile = path.join(tmpDir, 'kv-delete.json');
  await writeFile(listFile, JSON.stringify(toDelete, null, 1), 'utf8');
  await runWrangler(['kv', 'bulk', 'delete', `"${listFile}"`, '--namespace-id', KV_NAMESPACE_ID, '--remote', '--force']);
  console.log(`  cancellate ${toDelete.length} chiavi`);
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
