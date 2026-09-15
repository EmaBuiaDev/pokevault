#!/usr/bin/env node
// Completa un set gia' presente in D1 aggiungendo SOLO le carte che gli
// mancano rispetto a TCGdex, senza toccare le righe gia' scritte.
//
// Perche' non basta ingest-tcgdex-set.mjs: quello scrive ogni carta come
// "{SET}_IT_{localId}.webp" con il localId zero-padded di TCGdex ("048").
// I set arrivati dal catalogo storico (import-catalog-to-d1.mjs) hanno invece
// id senza padding e in .png ("MEP_IT_48.png"). Le due grafie non collidono
// sulla ON CONFLICT(card_id), quindi un --apply su uno di quei set non
// aggiorna: duplica. Su mep avrebbe prodotto 135 righe per 88 carte, con 47
// doppioni -- e le collezioni degli utenti puntano agli id storici, quindi
// non si possono nemmeno rinominare.
//
// Qui la grafia del set viene dedotta dalle righe che ci sono gia' (padding,
// estensione, layout della chiave R2) e le carte nuove la seguono: un set
// resta scritto in un modo solo.
//
// Uso:
//   node scripts/topup-set-from-tcgdex.mjs <setId>            (dry-run)
//   node scripts/topup-set-from-tcgdex.mjs <setId> --apply    (scrive R2 + D1)

import { writeFile, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import sharp from 'sharp';
import { canonicalStage } from './lib/tcgdex-stage.mjs';
import { normalizeNumber, detectConvention, detectR2Layout, r2KeyFor } from './lib/set-convention.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.topup-tmp');
const BUCKET = 'pokevault-images';
const OFFICIAL_BASE = 'https://assets.pokemon.com/static-assets/content-assets/cms2-it-it/img/cards/web';
const WEBP_QUALITY = 82;
const CONCURRENCY = 6;

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

// wrangler stampa banner e log attorno al JSON: si ritaglia l'array.
async function d1Query(sql) {
  const raw = await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--json', '--command', JSON.stringify(sql)], { capture: true });
  const start = raw.indexOf('[');
  const end = raw.lastIndexOf(']');
  if (start < 0 || end < 0) throw new Error(`Risposta D1 non interpretabile: ${raw.slice(0, 200)}`);
  return JSON.parse(raw.slice(start, end + 1))[0]?.results ?? [];
}

async function fetchJson(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${url} -> HTTP ${res.status}`);
  return res.json();
}

// Stessa cautela di ingest-tcgdex-set.mjs: una HEAD secca sotto concorrenza
// produce falsi negativi, e un falso negativo qui significa una carta nuova
// scritta 'missing' pur avendo l'immagine.
async function headOk(url, attempts = 3) {
  for (let i = 0; i < attempts; i += 1) {
    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 8000);
      try {
        const res = await fetch(url, { method: 'HEAD', signal: controller.signal });
        if (res.ok) return true;
        if (res.status === 404) return false;
      } finally {
        clearTimeout(timeout);
      }
    } catch { /* transitorio: si riprova */ }
    if (i < attempts - 1) await new Promise((r) => setTimeout(r, 500 * (i + 1)));
  }
  return false;
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
  if (!setId) throw new Error('Uso: node scripts/topup-set-from-tcgdex.mjs <setId> [--apply]');
  const setCodeUpper = setId.toUpperCase();

  console.log(`Righe gia' in D1 per "${setId}"...`);
  const existing = await d1Query(`SELECT card_id, card_number, image_status FROM cards WHERE expansion_id = '${setId.replace(/'/g, "''")}'`);
  if (existing.length === 0) {
    throw new Error(`Il set "${setId}" non esiste in D1: per un set nuovo si usa ingest-tcgdex-set.mjs.`);
  }
  const haveNumbers = new Set(existing.map((r) => normalizeNumber(r.card_number)));
  const convention = detectConvention(existing, setCodeUpper);
  console.log(`In D1: ${existing.length} carte. Grafia del set: numero ${convention.padWidth > 0 ? `con padding a ${convention.padWidth}` : 'senza padding'}, cardId .${convention.cardIdExt}`);

  const setSummary = await fetchJson(`https://api.tcgdex.net/v2/it/sets/${setId}`);
  const uniqueByLocalId = new Map();
  for (const c of setSummary.cards ?? []) {
    if (!uniqueByLocalId.has(c.localId)) uniqueByLocalId.set(c.localId, c);
  }
  const missingRefs = [...uniqueByLocalId.values()].filter((c) => !haveNumbers.has(normalizeNumber(c.localId)));
  console.log(`TCGdex: ${uniqueByLocalId.size} carte, di cui ${missingRefs.length} non nostre.`);
  if (missingRefs.length === 0) {
    console.log('Niente da aggiungere.');
    return;
  }
  console.log(`Mancanti: ${missingRefs.map((c) => normalizeNumber(c.localId)).join(', ')}`);

  console.log('\nDettaglio carte + ricerca immagine...');
  const enriched = await mapWithConcurrency(missingRefs, CONCURRENCY, async (ref) => {
    const detail = await fetchJson(`https://api.tcgdex.net/v2/it/cards/${ref.id}`);
    if (detail.image && await headOk(`${detail.image}/low.webp`)) {
      return { ref, detail, hasImage: true, imageSource: 'tcgdex', sourceUrl: `${detail.image}/low.webp` };
    }
    // Numerico: l'archivio vuole il numero senza zeri iniziali (048 -> 48).
    // Non numerico: vuole la stringa tale e quale (SMP_IT_SM01.png), e
    // saltarlo perche' "non e' un numero" lasciava a secco interi set promo.
    const officialNumber = /^\d+$/.test(ref.localId) ? String(parseInt(ref.localId, 10)) : ref.localId;
    const officialUrl = `${OFFICIAL_BASE}/${setCodeUpper}/${setCodeUpper}_IT_${officialNumber}.png`;
    if (await headOk(officialUrl)) {
      return { ref, detail, hasImage: true, imageSource: 'official', sourceUrl: officialUrl };
    }
    return { ref, detail, hasImage: false, imageSource: null, sourceUrl: null };
  });

  const senzaImmagine = enriched.filter((e) => !e.hasImage);
  console.log(`Immagini trovate: ${enriched.length - senzaImmagine.length}/${enriched.length}` +
    (senzaImmagine.length > 0 ? ` (senza: ${senzaImmagine.map((e) => e.ref.localId).join(', ')})` : ''));

  // Va bene anche una carta dal numero non numerico: i set promo (smp, swshp)
  // non ne hanno altre, e senza campione si finirebbe a indovinare il layout.
  const sampleOk = existing.find((r) => r.image_status === 'ok' && String(r.card_number ?? '').trim() !== '');
  const layout = sampleOk
    ? await detectR2Layout({ setId, sampleNumber: String(sampleOk.card_number).trim(), bucket: BUCKET, tmpDir, runWrangler })
    : null;
  if (layout) {
    console.log(`Layout R2 del set: ${layout.sampleKey}`);
  } else {
    console.log('Layout R2 non riconosciuto dalle carte esistenti: si usa it/<SET>/<SET>_IT_<n>.webp');
  }
  const r2Key = (number) => r2KeyFor(layout, setCodeUpper, number);

  console.log('\nAnteprima:');
  for (const e of enriched.slice(0, 5)) {
    const n = convention.formatNumber(e.ref.localId);
    console.log(`  ${convention.formatCardId(e.ref.localId)}  ${e.detail.name}  -> ${r2Key(n)} (${e.imageSource ?? 'nessuna immagine'})`);
  }
  if (enriched.length > 5) console.log(`  ... e altre ${enriched.length - 5}`);

  if (!apply) {
    console.log('\nDRY RUN: nessuna scrittura. Rilancia con --apply.');
    await rm(tmpDir, { recursive: true, force: true });
    return;
  }

  await mkdir(tmpDir, { recursive: true });
  let uploaded = 0;
  console.log('\nCaricamento immagini su R2...');
  await mapWithConcurrency(enriched.filter((e) => e.hasImage), CONCURRENCY, async (e) => {
    const number = convention.formatNumber(e.ref.localId);
    const localFile = path.join(tmpDir, `${number}.webp`);
    try {
      const res = await fetch(e.sourceUrl);
      if (!res.ok) throw new Error(`${e.sourceUrl} -> HTTP ${res.status}`);
      const raw = Buffer.from(await res.arrayBuffer());
      // L'archivio ufficiale serve PNG da ~200 KB: convertiti, o il set pesa
      // dieci volte il resto della libreria. TCGdex e' gia' webp.
      const buf = e.imageSource === 'official' ? await sharp(raw).webp({ quality: WEBP_QUALITY }).toBuffer() : raw;
      await writeFile(localFile, buf);
      await runWrangler(['r2', 'object', 'put', `${BUCKET}/${r2Key(number)}`, '--file', localFile, '--remote', '--content-type', 'image/webp']);
      uploaded += 1;
    } catch (err) {
      // Scritta 'ok' senza oggetto su R2 = catalogo che mente e 404 in app:
      // meglio 'missing', cosi' repair-missing-images.mjs la ritrova.
      e.hasImage = false;
      console.error(`  fallita ${e.ref.localId}: ${err.message}`);
    } finally {
      await rm(localFile, { force: true });
    }
  });
  console.log(`Upload: ${uploaded} ok, ${enriched.filter((e) => !e.hasImage).length} senza immagine`);

  const rows = enriched.map((e) => {
    const number = convention.formatNumber(e.ref.localId);
    const attacchi = (e.detail.attacks ?? []).map((a) => ({
      nome: a.name ?? '',
      danno: a.damage != null ? String(a.damage) : '',
      descrizione: a.effect ?? '',
    }));
    return `(${sqlString(convention.formatCardId(e.ref.localId))}, ${sqlString(setId)}, ${sqlString(number)}, ` +
      `${sqlString(e.detail.name)}, ${sqlString((e.detail.types ?? []).join(', ') || null)}, ` +
      `${sqlString(e.detail.hp != null ? String(e.detail.hp) : null)}, NULL, ${sqlString(JSON.stringify(attacchi))}, ` +
      `${sqlString(e.detail.rarity ?? null)}, ${sqlString(canonicalStage(e.detail.stage))}, ` +
      `${sqlString(e.hasImage ? 'ok' : 'missing')}, ${e.hasImage ? 1 : 0})`;
  });

  const total = existing.length + enriched.length;
  const withImage = existing.filter((r) => r.image_status === 'ok').length + enriched.filter((e) => e.hasImage).length;
  const lines = [];
  for (let i = 0; i < rows.length; i += 50) {
    lines.push(
      'INSERT INTO cards (card_id, expansion_id, card_number, nome, tipo, ps, regola_speciale, attacchi_json, rarity, stage, image_status, image_webp) VALUES\n' +
      rows.slice(i, i + 50).join(',\n') +
      '\nON CONFLICT(card_id) DO UPDATE SET nome=excluded.nome, tipo=excluded.tipo, ps=excluded.ps, attacchi_json=excluded.attacchi_json, ' +
      'rarity=COALESCE(excluded.rarity, cards.rarity), stage=COALESCE(excluded.stage, cards.stage), ' +
      "image_status=CASE WHEN excluded.image_status='ok' THEN 'ok' ELSE cards.image_status END, " +
      'image_webp=MAX(excluded.image_webp, cards.image_webp);'
    );
  }
  // official_count resta com'e': sui promo non esiste un totale stampato
  // ("MEP 048", non "048/088") e NULL in app vuol dire "non lo so".
  lines.push(`UPDATE expansions SET card_count = ${total}, coverage_pct = ${(withImage / total).toFixed(4)} WHERE id = ${sqlString(setId)};`);

  const sqlFile = path.join(workerRoot, `topup-${setId}.sql`);
  await writeFile(sqlFile, lines.join('\n\n'), 'utf8');
  console.log(`\nSQL in ${sqlFile}, esecuzione su D1...`);
  await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--file', sqlFile]);

  console.log('\n=== Riepilogo ===');
  console.log(`${setId}: ${existing.length} -> ${total} carte, copertura ${(withImage / total * 100).toFixed(1)}%`);
  const stillMissing = enriched.filter((e) => !e.hasImage).map((e) => e.ref.localId);
  if (stillMissing.length > 0) {
    console.log(`Senza immagine (placeholder in app): ${stillMissing.join(', ')} - ripassare con repair-missing-images.mjs`);
  }
  console.log("Se quelle carte erano gia' state richieste, purgare la cache KV di /images/it/<SET>/ (404 cachati 24h).");
  await rm(tmpDir, { recursive: true, force: true });
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
