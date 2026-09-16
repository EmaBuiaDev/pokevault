#!/usr/bin/env node
// Ingests an Italian expansion exposed by the official Pokemon card archive.
// This is intentionally a separate path from TCGdex: newly published sets can
// appear on pokemon.com before third-party catalogs have them.
//
// Usage:
//   node scripts/ingest-pokemon-official-set.mjs 30th --name "30 Anniversario" --release-date 2026-09-16 --official-count 128
//   node scripts/ingest-pokemon-official-set.mjs 30th ... --apply
//   node scripts/ingest-pokemon-official-set.mjs <setCode> <cardCount>   (scraping, senza manifest)

import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.official-ingest-tmp');
const manifestDir = path.join(__dirname, 'manifests');
const bucket = 'pokevault-images';
const imageBase = 'https://assets.pokemon.com/static-assets/content-assets/cms2-it-it/img/cards/web';
const archiveBase = 'https://www.pokemon.com/it/gcc/archivio-carte/series';

// The archive protects automated HTML requests with an interstitial, while the
// public card image URLs remain stable, so the card list comes from
// `manifests/<setCode>.json` -- the same hand-verified format
// topup-set-from-official.mjs already reads ({numero, nome, fonte}). `numero`
// is authoritative and deliberately NOT a 1..N range: the archive publishes a
// set card by card, so a freshly released set has holes (30th went live with
// 143, 151, 152 and 154 still unpublished) and the secret rares run past the
// printed denominator (158 over a 128-card set).
async function loadManifest(setCode) {
  const file = path.join(manifestDir, `${setCode}.json`);
  let raw;
  try {
    raw = await readFile(file, 'utf8');
  } catch (error) {
    if (error.code === 'ENOENT') return null;
    throw error;
  }
  const entries = JSON.parse(raw);
  for (const entry of entries) {
    if (!entry?.numero || !entry?.nome) {
      throw new Error(`Manifest ${file}: voce senza numero o nome (${JSON.stringify(entry)})`);
    }
  }
  return entries;
}

const wranglerBin = path.join(
  workerRoot,
  'node_modules',
  '.bin',
  process.platform === 'win32' ? 'wrangler.cmd' : 'wrangler'
);

function runWrangler(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(wranglerBin, args, {
      cwd: workerRoot,
      shell: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let stderr = '';
    child.stderr.on('data', (data) => { stderr += data.toString(); });
    child.on('close', (code) => {
      if (code === 0) resolve();
      else reject(new Error(`wrangler ${args.join(' ')} exited ${code}: ${stderr.slice(0, 500)}`));
    });
  });
}

async function fetchText(url) {
  const response = await fetch(url, { headers: { 'user-agent': 'PokeVault catalog ingest' } });
  if (!response.ok) throw new Error(`${url} -> HTTP ${response.status}`);
  return response.text();
}

function htmlText(value) {
  return value
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&#x27;/gi, "'")
    .replace(/&quot;/g, '"')
    .replace(/\s+/g, ' ')
    .trim();
}

function firstMatch(html, pattern, label) {
  const match = pattern.exec(html);
  if (!match?.[1]) throw new Error(`Campo ufficiale mancante: ${label}`);
  return htmlText(match[1]);
}

function sqlString(value) {
  if (value === null || value === undefined) return 'NULL';
  return `'${String(value).replace(/'/g, "''")}'`;
}

function parseCard(html, setCode, number) {
  const title = firstMatch(html, /<h1[^>]*>([\s\S]*?)<\/h1>/i, 'nome');
  const hpMatch = /(?:>\s*|\b)PS\s*(\d+)/i.exec(html);
  const series = firstMatch(html, /<h3[^>]*>\s*([^<]+?)\s*<\/h3>/i, 'espansione');
  const setNumber = firstMatch(html, /(\d+)\s*\/\s*(\d+)\s*([^<]+)</i, 'numero e rarita');
  const numberMatch = /^(\d+)\s*\/\s*(\d+)\s*(.*)$/i.exec(setNumber);
  const imageMatch = html.match(new RegExp(`https:\\/\\/assets\\.pokemon\\.com[^"']+\\/${setCode.toUpperCase()}\\/${setCode.toUpperCase()}_IT_${number}\\.png`, 'i'));
  if (!imageMatch) throw new Error(`Immagine ufficiale mancante per ${setCode}/${number}`);

  return {
    number: numberMatch?.[1] ?? String(number),
    total: numberMatch?.[2] ?? null,
    rarity: numberMatch?.[3]?.trim() || null,
    nome: title,
    espansioneNome: series,
    ps: hpMatch?.[1] ?? null,
    imageUrl: imageMatch[0],
    sourceUrl: `${archiveBase}/${setCode}/${number}/`,
  };
}

function flagValue(args, name) {
  const index = args.indexOf(name);
  if (index === -1) return null;
  const value = args[index + 1];
  if (!value || value.startsWith('--')) throw new Error(`${name} richiede un valore`);
  return value;
}

async function main() {
  const args = process.argv.slice(2);
  const setCode = (args[0] ?? '').trim().toLowerCase();
  const apply = args.includes('--apply');
  const setName = flagValue(args, '--name');
  const releaseDate = flagValue(args, '--release-date');
  const series = flagValue(args, '--series');
  const officialCountArg = flagValue(args, '--official-count');
  if (!setCode) {
    throw new Error('Uso: node scripts/ingest-pokemon-official-set.mjs <setCode> [--name N] [--release-date YYYY-MM-DD] [--series S] [--official-count N] [--apply]');
  }
  if (releaseDate && !/^\d{4}-\d{2}-\d{2}$/.test(releaseDate)) {
    // /v1/expansions ordina per release_date come stringa: un formato diverso
    // non fallisce, ordina sbagliato -- che e' peggio.
    throw new Error(`--release-date deve essere YYYY-MM-DD, ricevuto "${releaseDate}"`);
  }
  const officialCount = officialCountArg === null ? null : Number(officialCountArg);
  if (officialCount !== null && (!Number.isInteger(officialCount) || officialCount < 1)) {
    throw new Error('--official-count deve essere un intero positivo');
  }

  const manifest = await loadManifest(setCode);
  const scrapeCount = Number(args[1]);
  if (!manifest && (!Number.isInteger(scrapeCount) || scrapeCount < 1)) {
    throw new Error(`Nessun manifest in scripts/manifests/${setCode}.json e nessun <cardCount> per lo scraping`);
  }

  const entries = manifest ?? Array.from({ length: scrapeCount }, (_, i) => ({ numero: String(i + 1) }));
  const cards = [];
  for (const entry of entries) {
    const number = entry.numero;
    const card = manifest
      ? {
          number,
          total: officialCount === null ? null : String(officialCount),
          // Opzionale nel manifest: la rarita' si legge dal simbolo stampato
          // in fondo alla carta e non tutte sono distinguibili con certezza,
          // quindi si annota solo dove lo e' -- una assente resta NULL e non
          // sovrascrive quello che c'e' gia' in D1 (vedi COALESCE sotto).
          rarity: entry.rarita ?? null,
          nome: entry.nome,
          ps: null,
          imageUrl: `${imageBase}/${setCode.toUpperCase()}/${setCode.toUpperCase()}_IT_${number}.png`,
          sourceUrl: `${archiveBase}/${setCode}/${number}/`,
        }
      : parseCard(await fetchText(`${archiveBase}/${setCode}/${number}/`), setCode, number);
    if (!card.nome) throw new Error(`Carta ${setCode}/${number} senza nome`);
    const imageResponse = await fetch(card.imageUrl, { method: 'HEAD' });
    if (!imageResponse.ok) throw new Error(`${card.imageUrl} -> HTTP ${imageResponse.status}`);
    cards.push(card);
    console.log(`${card.number}/${card.total ?? '?'} ${card.nome} (${card.rarity ?? 'rarita non indicata'})`);
  }

  // The archive currently exposes only the published cards. The denominator
  // in labels such as "1/128" is the planned series total, not our catalog
  // coverage, so it must not inflate the Pokedex count: it goes to
  // official_count (schema/008), card_count stays the number of cards we have.
  const total = cards.length;
  const expansionId = setCode;
  // name/series/release_date sono i tre campi che per ogni altra espansione
  // arrivano dai backfill TCGdex. Un set che TCGdex non ha ancora resterebbe
  // senza: senza `name` il Pokedex mostra il codice grezzo ("30TH"), e senza
  // `release_date` l'ORDER BY di /v1/expansions lo manda in fondo alla lista
  // (`(e.release_date IS NULL)` ordina per primo) invece che in cima, che e'
  // l'esatto contrario di quello che ci si aspetta da un set appena uscito.
  const expansionColumns = 'id, card_count, official_count, name, series, sort_order, logo_key, published, coverage_pct, release_date, dominant_set_code, base_set_code, upstream_set_code';
  const expansionValues = [
    sqlString(expansionId),
    String(total),
    officialCount === null ? 'NULL' : String(officialCount),
    sqlString(setName),
    sqlString(series),
    '100',
    'NULL',
    '1',
    '1',
    sqlString(releaseDate),
    sqlString(setCode.toUpperCase()),
    sqlString(setCode.toUpperCase()),
    'NULL',
  ].join(', ');
  // COALESCE su excluded: una seconda passata senza --name (per esempio per
  // aggiungere solo le carte pubblicate nel frattempo) non deve cancellare i
  // metadati gia' scritti.
  const expansionSql = `INSERT INTO expansions (${expansionColumns}) VALUES (${expansionValues}) ON CONFLICT(id) DO UPDATE SET card_count = excluded.card_count, official_count = COALESCE(excluded.official_count, official_count), name = COALESCE(excluded.name, name), series = COALESCE(excluded.series, series), release_date = COALESCE(excluded.release_date, release_date), published = 1, coverage_pct = 1, dominant_set_code = excluded.dominant_set_code, base_set_code = excluded.base_set_code;`;
  const cardRows = cards.map((card) => {
    const cardId = `${setCode.toUpperCase()}_IT_${card.number}.png`;
    return `(${sqlString(cardId)}, ${sqlString(expansionId)}, ${sqlString(card.number)}, ${sqlString(card.nome)}, NULL, ${sqlString(card.ps)}, NULL, '[]', 'ok', 0, ${sqlString(card.rarity)})`;
  });
  // Batch da 50 righe come import-catalog-to-d1.mjs: oltre quella soglia D1
  // rifiuta la singola statement con SQLITE_TOOBIG.
  const cardStatements = [];
  for (let i = 0; i < cardRows.length; i += 50) {
    const chunk = cardRows.slice(i, i + 50);
    cardStatements.push(`INSERT INTO cards (card_id, expansion_id, card_number, nome, tipo, ps, regola_speciale, attacchi_json, image_status, image_webp, rarity) VALUES\n${chunk.join(',\n')}\nON CONFLICT(card_id) DO UPDATE SET nome = excluded.nome, ps = excluded.ps, image_status = excluded.image_status, rarity = COALESCE(excluded.rarity, rarity);`);
  }
  const sqlFile = path.join(workerRoot, `ingest-${setCode}-official.sql`);
  await writeFile(sqlFile, `${expansionSql}\n\n${cardStatements.join('\n\n')}\n`, 'utf8');

  console.log(`\nDRY RUN: ${cards.length} carte valide, SQL: ${sqlFile}`);
  if (!apply) return;

  await mkdir(tmpDir, { recursive: true });
  for (const card of cards) {
    const localFile = path.join(tmpDir, `${card.number}.png`);
    const response = await fetch(card.imageUrl);
    if (!response.ok) throw new Error(`${card.imageUrl} -> HTTP ${response.status}`);
    await writeFile(localFile, Buffer.from(await response.arrayBuffer()));
    const destination = `${bucket}/it/${setCode.toUpperCase()}/${setCode.toUpperCase()}_IT_${card.number}.png`;
    await runWrangler(['r2', 'object', 'put', destination, '--file', localFile, '--remote', '--content-type', 'image/png']);
    await rm(localFile, { force: true });
  }
  await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--file', sqlFile]);
  await rm(tmpDir, { recursive: true, force: true });
  console.log(`Import completato: ${cards.length} carte, set ${expansionId}.`);
}

main().catch((error) => {
  console.error('Errore fatale:', error);
  process.exit(1);
});