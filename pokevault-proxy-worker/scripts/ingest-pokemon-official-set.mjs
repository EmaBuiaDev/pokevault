#!/usr/bin/env node
// Ingests an Italian expansion exposed by the official Pokemon card archive.
// This is intentionally a separate path from TCGdex: newly published sets can
// appear on pokemon.com before third-party catalogs have them.
//
// Usage:
//   node scripts/ingest-pokemon-official-set.mjs 30th 12
//   node scripts/ingest-pokemon-official-set.mjs 30th 12 --apply

import { mkdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.official-ingest-tmp');
const bucket = 'pokevault-images';
const imageBase = 'https://assets.pokemon.com/static-assets/content-assets/cms2-it-it/img/cards/web';
const archiveBase = 'https://www.pokemon.com/it/gcc/archivio-carte/series';

// The archive protects automated HTML requests with an interstitial, while
// the public card image URLs remain stable. Keep verified archive results here
// so the ingest does not depend on bypassing that protection.
const VERIFIED_MANIFESTS = {
  '30th': {
    total: 128,
    cards: [
      'Exeggcute', 'Exeggutor di Alola', 'Volbeat', 'Illumise', 'Tropius',
      'Cherubi', 'Cherrim', 'Vivillon', 'Vulpix', 'Ninetales', 'Moltres', 'Ho-Oh',
    ],
  },
};

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

async function main() {
  const args = process.argv.slice(2);
  const setCode = (args[0] ?? '').trim().toLowerCase();
  const cardCount = Number(args[1]);
  const apply = args.includes('--apply');
  if (!setCode || !Number.isInteger(cardCount) || cardCount < 1) {
    throw new Error('Uso: node scripts/ingest-pokemon-official-set.mjs <setCode> <cardCount> [--apply]');
  }

  const verifiedManifest = VERIFIED_MANIFESTS[setCode];
  const cards = [];
  for (let number = 1; number <= cardCount; number += 1) {
    const card = verifiedManifest
      ? {
          number: String(number),
          total: String(verifiedManifest.total),
          rarity: null,
          nome: verifiedManifest.cards[number - 1],
          ps: null,
          imageUrl: `${imageBase}/${setCode.toUpperCase()}/${setCode.toUpperCase()}_IT_${number}.png`,
          sourceUrl: `${archiveBase}/${setCode}/${number}/`,
        }
      : parseCard(await fetchText(`${archiveBase}/${setCode}/${number}/`), setCode, number);
    if (!card.nome) throw new Error(`Carta ${setCode}/${number} non presente nel manifest verificato`);
    const imageResponse = await fetch(card.imageUrl, { method: 'HEAD' });
    if (!imageResponse.ok) throw new Error(`${card.imageUrl} -> HTTP ${imageResponse.status}`);
    cards.push(card);
    console.log(`${card.number}/${card.total ?? '?'} ${card.nome} (${card.rarity ?? 'rarita non indicata'})`);
  }

  // The archive currently exposes only the published cards. The denominator
  // in labels such as "1/128" is the planned series total, not our catalog
  // coverage, so it must not inflate the Pokedex count.
  const total = cards.length;
  const expansionId = setCode;
  const expansionSql = `INSERT INTO expansions (id, card_count, sort_order, logo_key, published, coverage_pct, release_date, dominant_set_code, base_set_code, upstream_set_code) VALUES (${sqlString(expansionId)}, ${total}, 100, NULL, 1, 1, NULL, ${sqlString(setCode.toUpperCase())}, ${sqlString(setCode.toUpperCase())}, NULL) ON CONFLICT(id) DO UPDATE SET card_count = excluded.card_count, published = 1, coverage_pct = 1, dominant_set_code = excluded.dominant_set_code, base_set_code = excluded.base_set_code;`;
  const cardRows = cards.map((card) => {
    const cardId = `${setCode.toUpperCase()}_IT_${card.number}.png`;
    return `(${sqlString(cardId)}, ${sqlString(expansionId)}, ${sqlString(card.number)}, ${sqlString(card.nome)}, NULL, ${sqlString(card.ps)}, NULL, '[]', 'ok', 0)`;
  });
  const cardSql = `INSERT INTO cards (card_id, expansion_id, card_number, nome, tipo, ps, regola_speciale, attacchi_json, image_status, image_webp) VALUES\n${cardRows.join(',\n')}\nON CONFLICT(card_id) DO UPDATE SET nome = excluded.nome, ps = excluded.ps, image_status = excluded.image_status;`;
  const sqlFile = path.join(workerRoot, `ingest-${setCode}-official.sql`);
  await writeFile(sqlFile, `${expansionSql}\n\n${cardSql}\n`, 'utf8');

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