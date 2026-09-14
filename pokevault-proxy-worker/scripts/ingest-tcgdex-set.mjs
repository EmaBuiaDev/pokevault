#!/usr/bin/env node
// Ingest a single set from TCGdex (api.tcgdex.net, MIT-licensed cards-database,
// verified free/no-rate-limit/no-API-key on 2026-09-06) into our own D1 +
// R2 catalog. This is the prototype for the scheduled "new expansion"
// automation: TCGdex is touched only here, at ingest time, never by the
// Worker or the app at runtime (see plan sez. "Come applicarlo ai set futuri").
//
// Coverage rule (plan sez. 2.2): a set is only marked `published = 1` in D1
// if at least COVERAGE_THRESHOLD of its cards have a real Italian image.
// Below threshold, the set (and its cards) are still written so a card a
// user already owns stays resolvable, but the set stays hidden from the
// Pokedex listing (/v1/expansions only returns published=1) until a later
// re-run finds better coverage.
//
// Usage:
//   node scripts/ingest-tcgdex-set.mjs <setId>                 (dry-run: report only)
//   node scripts/ingest-tcgdex-set.mjs <setId> --apply          (write to R2 + D1)

import { writeFile, readFile, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import sharp from 'sharp';
import { canonicalStage } from './lib/tcgdex-stage.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.ingest-tmp');
const BUCKET = 'pokevault-images';
// Ripiego quando TCGdex non espone l'immagine: l'archivio ufficiale e' una
// sorgente indipendente, quindi non condivide ne' i buchi di catalogo ne' i
// falsi negativi della HEAD (vedi headOk piu' sotto). Vuole il numero senza
// zero iniziali, al contrario delle nostre chiavi R2.
const OFFICIAL_BASE = 'https://assets.pokemon.com/static-assets/content-assets/cms2-it-it/img/cards/web';
// Stessa qualita' di recompress-webp.mjs: le immagini ufficiali arrivano in PNG
// e vanno convertite, quelle TCGdex sono gia' webp e si caricano intatte.
const WEBP_QUALITY = 82;
const COVERAGE_THRESHOLD = 0.8; // lowered from 0.9 on 2026-09-07 (user decision, after seeing me05 sit at 85% unpublished)
const CONCURRENCY = 6;

const wranglerBin = path.join(workerRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'wrangler.cmd' : 'wrangler');

function runWrangler(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(wranglerBin, args, { cwd: workerRoot, shell: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let stderr = '';
    child.stderr.on('data', (d) => { stderr += d.toString(); });
    child.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`wrangler ${args.join(' ')} exited ${code}: ${stderr.slice(0, 400)}`))));
  });
}

async function fetchJson(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${url} -> HTTP ${res.status}`);
  return res.json();
}

// A bare HEAD check under concurrency produced false negatives in practice
// (52/120 "missing" on one run, 102/120 "missing" -- i.e. present -- moments
// later on the exact same set, later confirmed all genuinely present via a
// separate curl check): likely connection-pool contention between the
// per-card detail fetches (api.tcgdex.net) and these HEAD checks
// (assets.tcgdex.net) at concurrency=6. Retina retries + an explicit
// timeout instead of trusting a single fetch attempt.
async function headOk(url, attempts = 3) {
  for (let i = 0; i < attempts; i += 1) {
    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 8000);
      try {
        const res = await fetch(url, { method: 'HEAD', signal: controller.signal });
        if (res.ok) return true;
        if (res.status === 404) return false; // genuine miss, no point retrying
      } finally {
        clearTimeout(timeout);
      }
    } catch {
      // transient (timeout/network) -- fall through to retry
    }
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
  const setId = args[0];
  const apply = args.includes('--apply');
  if (!setId) throw new Error('Usage: node ingest-tcgdex-set.mjs <setId> [--apply]');

  console.log(`Recupero set "${setId}" da TCGdex...`);
  const setSummary = await fetchJson(`https://api.tcgdex.net/v2/it/sets/${setId}`);
  const uniqueByLocalId = new Map();
  for (const c of setSummary.cards) {
    if (!uniqueByLocalId.has(c.localId)) uniqueByLocalId.set(c.localId, c);
  }
  const cardRefs = [...uniqueByLocalId.values()];
  console.log(`Set: ${setSummary.name} (${setSummary.releaseDate}) - ${cardRefs.length} carte distinte`);

  // Abbreviazione ufficiale inglese (me05 -> "PBL"): e' il codice con cui
  // PokeWallet indicizza il set, diverso dal nostro setId maiuscolo ("ME05")
  // che finisce in base_set_code/dominant_set_code. Senza questo campo il set
  // entra nel Pokedex ma resta senza prezzi finche' qualcuno non modifica a
  // mano le tabelle nel Worker (e' quello che e' successo a Buio Pesto).
  // Verificato il 2026-09-11 sulla directory /sets di PokeWallet: risolve a un
  // unico set inglese per 18/18 dei set gia' mappati a mano, zero collisioni.
  // Presente anche nel payload italiano, quindi non costa una chiamata in piu'.
  const upstreamSetCode = (setSummary.abbreviation?.official ?? '').trim().toUpperCase() || null;
  console.log(`Codice upstream (abbreviation.official): ${upstreamSetCode ?? "-- assente, il Worker usera' i fallback"}`);

  console.log('Recupero dettaglio carte + verifica immagini...');
  const setCodeForOfficial = setId.toUpperCase();
  const enriched = await mapWithConcurrency(cardRefs, CONCURRENCY, async (ref) => {
    const detail = await fetchJson(`https://api.tcgdex.net/v2/it/cards/${ref.id}`);
    if (detail.image && await headOk(`${detail.image}/low.webp`)) {
      return { ref, detail, hasImage: true, imageSource: 'tcgdex' };
    }
    // Prima di dichiarare la carta senza immagine, chiedilo all'archivio
    // ufficiale: il 2026-09-13 diciassette carte di me05 erano finite a
    // 'missing' pur essendo tutte e diciassette regolarmente pubblicate li'.
    // Arrendersi qui significa un placeholder in app finche' qualcuno non
    // lancia repair-missing-images.mjs a mano.
    const officialUrl = `${OFFICIAL_BASE}/${setCodeForOfficial}/${setCodeForOfficial}_IT_${parseInt(ref.localId, 10)}.png`;
    if (/^\d+$/.test(ref.localId) && await headOk(officialUrl)) {
      return { ref, detail, hasImage: true, imageSource: 'official', officialUrl };
    }
    return { ref, detail, hasImage: false, imageSource: null };
  });

  const withImage = enriched.filter((e) => e.hasImage).length;
  const coverage = withImage / enriched.length;
  console.log(`Copertura immagini IT: ${withImage}/${enriched.length} (${(coverage * 100).toFixed(1)}%)`);
  const published = coverage >= COVERAGE_THRESHOLD;
  console.log(`Soglia ${(COVERAGE_THRESHOLD * 100).toFixed(0)}% -> published = ${published ? 1 : 0}`);

  const missing = enriched.filter((e) => !e.hasImage).map((e) => e.ref.localId);
  if (missing.length > 0) {
    console.log(`Carte senza immagine IT (${missing.length}):`, missing.slice(0, 20).join(', ') + (missing.length > 20 ? '...' : ''));
  }

  if (!apply) {
    console.log('\nDRY RUN: nessuna scrittura eseguita. Rilancia con --apply per scrivere su R2 + D1.');
    return;
  }

  await mkdir(tmpDir, { recursive: true });
  const setCodeUpper = setId.toUpperCase();
  let uploaded = 0;
  let uploadFailed = 0;

  console.log('\nCaricamento immagini su R2...');
  await mapWithConcurrency(enriched.filter((e) => e.hasImage), CONCURRENCY, async (e) => {
    const localFile = path.join(tmpDir, `${e.ref.localId}.webp`);
    const destKey = `it/${setCodeUpper}/${setCodeUpper}_IT_${e.ref.localId}.webp`;
    try {
      const sourceUrl = e.imageSource === 'official' ? e.officialUrl : `${e.detail.image}/low.webp`;
      const res = await fetch(sourceUrl);
      if (!res.ok) throw new Error(`${sourceUrl} -> HTTP ${res.status}`);
      const raw = Buffer.from(await res.arrayBuffer());
      // L'archivio ufficiale serve PNG da ~200 KB: convertiti, o il set pesa
      // dieci volte tanto rispetto al resto della libreria.
      const buf = e.imageSource === 'official'
        ? await sharp(raw).webp({ quality: WEBP_QUALITY }).toBuffer()
        : raw;
      await writeFile(localFile, buf);
      await runWrangler(['r2', 'object', 'put', `${BUCKET}/${destKey}`, '--file', localFile, '--remote', '--content-type', 'image/webp']);
      uploaded += 1;
    } catch (err) {
      uploadFailed += 1;
      // Un upload fallito lasciava la carta scritta come 'ok' su D1 pur senza
      // oggetto su R2: il Worker rispondeva 404 e il catalogo mentiva. Segnarla
      // qui la fa ricadere in 'missing', cosi' repair-missing-images.mjs la
      // ritrova.
      e.hasImage = false;
      console.error(`  fallita ${e.ref.localId}: ${err.message}`);
    } finally {
      await rm(localFile, { force: true });
    }
  });
  console.log(`Upload completato: ${uploaded} ok, ${uploadFailed} falliti`);

  console.log('\nGenerazione SQL per D1...');
  // I promo non hanno un totale stampato (le loro carte portano "SVP 001", non
  // un "x/y"): li' TCGdex non espone cardCount.official e la colonna resta
  // NULL, che l'app tratta come "non lo so", mai come "non e' questa".
  const officialCount = Number(setSummary.cardCount?.official) > 0
    ? Number(setSummary.cardCount.official)
    : null;
  const lines = [];
  lines.push(
    // base_set_code and dominant_set_code hold the same value here (schema/003 was
    // added twice under different names by two branches worked in parallel) --
    // written together so neither column goes stale for newly-ingested sets.
    //
    // official_count e' il conteggio delle sole carte base -- il numero stampato
    // sulle carte dopo la barra ("066/217") -- mentre card_count conta anche le
    // segrete e i fuori serie, che si numerano superandolo ("219/217"). Va scritto
    // qui perche' la ricerca per ID nell'app confronta quel numero (schema/008):
    // un set ingerito senza sarebbe irraggiungibile digitandone un ID.
    `INSERT INTO expansions (id, card_count, official_count, sort_order, logo_key, published, coverage_pct, release_date, dominant_set_code, base_set_code, upstream_set_code) VALUES (` +
    `${sqlString(setId)}, ${enriched.length}, ${officialCount ?? 'NULL'}, 100, NULL, ${published ? 1 : 0}, ${coverage.toFixed(4)}, ${sqlString(setSummary.releaseDate)}, ${sqlString(setCodeUpper)}, ${sqlString(setCodeUpper)}, ${sqlString(upstreamSetCode)}) ` +
    `ON CONFLICT(id) DO UPDATE SET card_count = excluded.card_count, official_count = COALESCE(excluded.official_count, expansions.official_count), published = excluded.published, coverage_pct = excluded.coverage_pct, release_date = excluded.release_date, dominant_set_code = excluded.dominant_set_code, base_set_code = excluded.base_set_code, upstream_set_code = COALESCE(excluded.upstream_set_code, expansions.upstream_set_code);`
  );

  const cardRows = enriched.map((e) => {
    const cardId = `${setCodeUpper}_IT_${e.ref.localId}.webp`;
    const attacchi = (e.detail.attacks ?? []).map((a) => ({
      nome: a.name ?? '',
      danno: a.damage != null ? String(a.damage) : '',
      descrizione: a.effect ?? '',
    }));
    // Lo stadio arriva dal payload italiano gia' scaricato ("Livello 1"),
    // canonicalStage() lo riporta alla grafia inglese con cui lo scrivono i
    // backfill: una sola grafia per stadio in tutta la colonna.
    return `(${sqlString(cardId)}, ${sqlString(setId)}, ${sqlString(e.ref.localId)}, ${sqlString(e.detail.name)}, ` +
      `${sqlString((e.detail.types ?? []).join(', ') || null)}, ${sqlString(e.detail.hp ?? null)}, NULL, ` +
      `${sqlString(JSON.stringify(attacchi))}, ${sqlString(canonicalStage(e.detail.stage))}, ` +
      `${sqlString(e.hasImage ? 'ok' : 'missing')}, ${e.hasImage ? 1 : 0})`;
  });
  for (let i = 0; i < cardRows.length; i += 50) {
    lines.push(
      'INSERT INTO cards (card_id, expansion_id, card_number, nome, tipo, ps, regola_speciale, attacchi_json, stage, image_status, image_webp) VALUES\n' +
      cardRows.slice(i, i + 50).join(',\n') +
      // image_status sale e non scende: un rilancio con una rete ballerina non
      // deve declassare a 'missing' una carta la cui immagine e' gia' su R2 da
      // una run precedente. A ripulire i falsi 'ok' ci pensa comunque il
      // fallback 404 del Worker, che mostra il placeholder.
      '\nON CONFLICT(card_id) DO UPDATE SET nome=excluded.nome, tipo=excluded.tipo, ps=excluded.ps, attacchi_json=excluded.attacchi_json, ' +
      // Lo stadio non si azzera da solo: se una run senza stage (o TCGdex che
      // smette di esporlo) tornasse NULL, COALESCE tiene quello gia' scritto.
      'stage=COALESCE(excluded.stage, cards.stage), ' +
      "image_status=CASE WHEN excluded.image_status='ok' THEN 'ok' ELSE cards.image_status END, " +
      'image_webp=MAX(excluded.image_webp, cards.image_webp);'
    );
  }

  const sqlFile = path.join(workerRoot, `ingest-${setId}.sql`);
  await writeFile(sqlFile, lines.join('\n\n'), 'utf8');
  console.log(`SQL scritto in ${sqlFile}, esecuzione su D1...`);
  await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--file', sqlFile]);

  console.log('\n=== Riepilogo ===');
  console.log(`Set: ${setId} (${setSummary.name})`);
  const fromOfficial = enriched.filter((e) => e.imageSource === 'official').length;
  console.log(`Carte: ${enriched.length}, immagini caricate: ${uploaded}, copertura: ${(coverage * 100).toFixed(1)}%, published: ${published}`);
  if (fromOfficial > 0) {
    console.log(`Di queste, ${fromOfficial} recuperate dall'archivio ufficiale perche' TCGdex non le esponeva.`);
  }
  const stillMissing = enriched.filter((e) => !e.hasImage).length;
  if (stillMissing > 0) {
    console.log(`Restano ${stillMissing} carte senza immagine: rilancia 'node scripts/repair-missing-images.mjs ${setId}' piu' avanti.`);
  }

  await rm(tmpDir, { recursive: true, force: true });
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
