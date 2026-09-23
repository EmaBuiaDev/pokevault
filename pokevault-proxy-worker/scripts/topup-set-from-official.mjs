#!/usr/bin/env node
// Completa un set gia' presente in D1 con le carte che ha SOLO l'archivio
// ufficiale, partendo da un manifest verificato a mano.
//
// Perche' esiste, accanto a topup-set-from-tcgdex.mjs: quattro set storici
// (xyp, bwp, dp1, col1) su TCGdex in italiano tornano `cards: []` -- il totale
// nei metadati e' quello globale, ma le carte italiane li' non ci sono. Le
// immagini pero' le ha assets.pokemon.com, e i nomi si leggono dalla carta
// stessa: da qui il manifest, che e' l'unico pezzo non automatizzabile perche'
// l'archivio HTML di pokemon.com risponde con un interstiziale (~1 KB, nessun
// <h1>) alle richieste non interattive.
//
// Il manifest vive in scripts/manifests/<setId>.json ed e' una lista di
// { numero, nome, fonte }, dove `fonte` dice da dove viene il nome ("carta" =
// letto dall'immagine ufficiale, "tcgdex-en" = nome inglese, identico in
// italiano perche' le specie Pokemon non si traducono; Allenatori ed Energie
// NON possono usare questa scorciatoia).
//
// Scrive solo numero, nome e immagine: tipo, PS e attacchi restano NULL, come
// gia' fa ingest-pokemon-official-set.mjs, perche' l'archivio non li espone in
// modo affidabile.
//
// Uso:
//   node scripts/topup-set-from-official.mjs <setId>            (dry-run)
//   node scripts/topup-set-from-official.mjs <setId> --apply    (scrive R2 + D1)

import { writeFile, readFile, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import sharp from 'sharp';
import { normalizeNumber, detectConvention, detectR2Layout, r2KeyFor } from './lib/set-convention.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.topup-official-tmp');
const manifestDir = path.join(__dirname, 'manifests');
const BUCKET = 'pokevault-images';
const OFFICIAL_BASE = 'https://assets.pokemon.com/static-assets/content-assets/cms2-it-it/img/cards/web';
const WEBP_QUALITY = 82;
const CONCURRENCY = 4;

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

async function d1Query(sql) {
  const raw = await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--json', '--command', JSON.stringify(sql)], { capture: true });
  const start = raw.indexOf('[');
  const end = raw.lastIndexOf(']');
  if (start < 0 || end < 0) throw new Error(`Risposta D1 non interpretabile: ${raw.slice(0, 200)}`);
  return JSON.parse(raw.slice(start, end + 1))[0]?.results ?? [];
}

function sqlString(value) {
  if (value === null || value === undefined) return 'NULL';
  return `'${String(value).replace(/'/g, "''")}'`;
}

async function mapWithConcurrency(items, limit, fn) {
  const results = new Array(items.length);
  let cursor = 0;
  async function worker() {
    while (cursor < items.length) {
      const i = cursor++;
      results[i] = await fn(items[i], i);
    }
  }
  await Promise.all(Array.from({ length: limit }, worker));
  return results;
}

async function main() {
  const args = process.argv.slice(2);
  const setId = args.find((a) => !a.startsWith('--'));
  const apply = args.includes('--apply');
  if (!setId) throw new Error('Uso: node scripts/topup-set-from-official.mjs <setId> [--apply]');
  const setCodeUpper = setId.toUpperCase();

  const manifestFile = path.join(manifestDir, `${setId}.json`);
  const manifest = JSON.parse(await readFile(manifestFile, 'utf8'));
  console.log(`Manifest ${path.relative(workerRoot, manifestFile)}: ${manifest.length} carte`);
  const senzaNome = manifest.filter((c) => !c.nome || !String(c.numero ?? '').trim());
  if (senzaNome.length > 0) throw new Error(`Manifest incompleto: ${JSON.stringify(senzaNome.slice(0, 3))}`);

  const existing = await d1Query(`SELECT card_id, card_number, image_status FROM cards WHERE expansion_id = '${setId.replace(/'/g, "''")}'`);
  if (existing.length === 0) {
    throw new Error(`Il set "${setId}" non esiste in D1: per un set nuovo si usa ingest-pokemon-official-set.mjs.`);
  }
  const haveNumbers = new Set(existing.map((r) => normalizeNumber(r.card_number)));
  const convention = detectConvention(existing, setCodeUpper);
  console.log(`In D1: ${existing.length} carte. Grafia: numero ${convention.padWidth > 0 ? `paddato a ${convention.padWidth}` : 'senza padding'}, cardId .${convention.cardIdExt}`);

  // Una carta gia' nostra non si tocca: il manifest aggiunge, non riscrive.
  const daAggiungere = manifest.filter((c) => !haveNumbers.has(normalizeNumber(c.numero)));
  const gia = manifest.length - daAggiungere.length;
  if (gia > 0) console.log(`${gia} carte del manifest sono gia' in D1: saltate.`);
  if (daAggiungere.length === 0) {
    console.log('Niente da aggiungere.');
    return;
  }

  await mkdir(tmpDir, { recursive: true });
  const sampleOk = existing.find((r) => r.image_status === 'ok' && String(r.card_number ?? '').trim() !== '');
  const layout = sampleOk
    ? await detectR2Layout({ setId, sampleNumber: String(sampleOk.card_number).trim(), bucket: BUCKET, tmpDir, runWrangler })
    : null;
  console.log(layout ? `Layout R2 del set: ${layout.sampleKey}` : 'Layout R2 non riconosciuto: si usa it/<SET>/<SET>_IT_<n>.webp');

  console.log('\nVerifica immagini sull archivio ufficiale...');
  const enriched = await mapWithConcurrency(daAggiungere, CONCURRENCY, async (card) => {
    // Numerico: l'archivio vuole il numero senza zeri iniziali. Non numerico:
    // la stringa tale e quale (XYP_IT_XY173.png).
    const raw = String(card.numero).trim();
    const officialNumber = /^\d+$/.test(raw) ? String(parseInt(raw, 10)) : raw;
    const url = `${OFFICIAL_BASE}/${setCodeUpper}/${setCodeUpper}_IT_${officialNumber}.png`;
    let ok = false;
    try {
      const res = await fetch(url, { method: 'HEAD' });
      ok = res.ok;
    } catch { /* trattata come assente */ }
    return { ...card, url, hasImage: ok };
  });

  const senzaImmagine = enriched.filter((e) => !e.hasImage);
  console.log(`Immagini disponibili: ${enriched.length - senzaImmagine.length}/${enriched.length}` +
    (senzaImmagine.length > 0 ? ` (assenti: ${senzaImmagine.map((e) => e.numero).join(', ')})` : ''));

  console.log('\nAnteprima:');
  for (const e of enriched.slice(0, 5)) {
    const n = convention.formatNumber(e.numero);
    console.log(`  ${convention.formatCardId(e.numero)}  ${e.nome}  [${e.fonte ?? 'manifest'}] -> ${r2KeyFor(layout, setCodeUpper, n)}`);
  }
  if (enriched.length > 5) console.log(`  ... e altre ${enriched.length - 5}`);

  if (!apply) {
    console.log('\nDRY RUN: nessuna scrittura. Rilancia con --apply.');
    await rm(tmpDir, { recursive: true, force: true });
    return;
  }

  let uploaded = 0;
  console.log('\nCaricamento immagini su R2...');
  await mapWithConcurrency(enriched.filter((e) => e.hasImage), CONCURRENCY, async (e) => {
    const number = convention.formatNumber(e.numero);
    const localFile = path.join(tmpDir, `${number}.webp`);
    try {
      const res = await fetch(e.url);
      if (!res.ok) throw new Error(`${e.url} -> HTTP ${res.status}`);
      // L'archivio serve PNG da ~200 KB: convertiti, o il set pesa dieci volte
      // il resto della libreria.
      const webp = await sharp(Buffer.from(await res.arrayBuffer())).webp({ quality: WEBP_QUALITY }).toBuffer();
      await writeFile(localFile, webp);
      await runWrangler(['r2', 'object', 'put', `${BUCKET}/${r2KeyFor(layout, setCodeUpper, number)}`, '--file', localFile, '--remote', '--content-type', 'image/webp']);
      uploaded += 1;
    } catch (err) {
      e.hasImage = false;
      console.error(`  fallita ${e.numero}: ${err.message}`);
    } finally {
      await rm(localFile, { force: true });
    }
  });
  console.log(`Upload: ${uploaded} ok, ${enriched.filter((e) => !e.hasImage).length} senza immagine`);

  const rows = enriched.map((e) => {
    const number = convention.formatNumber(e.numero);
    // tipo, ps, regola_speciale, stage: NULL. L'archivio non li espone e
    // inventarli sarebbe peggio che lasciarli vuoti -- l'app li tratta gia'
    // come "non lo so".
    return `(${sqlString(convention.formatCardId(e.numero))}, ${sqlString(setId)}, ${sqlString(number)}, ` +
      `${sqlString(e.nome)}, NULL, NULL, NULL, '[]', ${sqlString(e.hasImage ? 'ok' : 'missing')}, ${e.hasImage ? 1 : 0})`;
  });

  const total = existing.length + enriched.length;
  const withImage = existing.filter((r) => r.image_status === 'ok').length + enriched.filter((e) => e.hasImage).length;
  const lines = [];
  for (let i = 0; i < rows.length; i += 50) {
    lines.push(
      'INSERT INTO cards (card_id, expansion_id, card_number, nome, tipo, ps, regola_speciale, attacchi_json, image_status, image_webp) VALUES\n' +
      rows.slice(i, i + 50).join(',\n') +
      '\nON CONFLICT(card_id) DO UPDATE SET nome=excluded.nome, ' +
      "image_status=CASE WHEN excluded.image_status='ok' THEN 'ok' ELSE cards.image_status END, " +
      'image_webp=MAX(excluded.image_webp, cards.image_webp);'
    );
  }
  lines.push(`UPDATE expansions SET card_count = ${total}, coverage_pct = ${(withImage / total).toFixed(4)} WHERE id = ${sqlString(setId)};`);

  const sqlFile = path.join(workerRoot, `topup-official-${setId}.sql`);
  await writeFile(sqlFile, lines.join('\n\n'), 'utf8');
  console.log(`\nSQL in ${sqlFile}, esecuzione su D1...`);
  await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--file', sqlFile]);

  console.log('\n=== Riepilogo ===');
  console.log(`${setId}: ${existing.length} -> ${total} carte, copertura ${(withImage / total * 100).toFixed(1)}%`);
  console.log("Se quelle carte erano gia' state richieste, purgare la cache KV di /images/it/<SET>/ (404 cachati 24h).");
  await rm(tmpDir, { recursive: true, force: true });
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
