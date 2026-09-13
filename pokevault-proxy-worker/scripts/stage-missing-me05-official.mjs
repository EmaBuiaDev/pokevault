#!/usr/bin/env node
// Stages the fifteen Buio Pesto cards published by the official archive after
// the first 105 cards. The upload phase never contacts pokemon.com.
//
//   node scripts/stage-missing-me05-official.mjs --download-only
//   node scripts/stage-missing-me05-official.mjs --upload-staged

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const workerRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const stageDir = path.join(workerRoot, 'official-staging', 'ME05');
const sqlFile = path.join(workerRoot, 'ingest-me05-official-missing.sql');
const bucket = 'pokevault-images';
const imageBase = 'https://assets.pokemon.com/static-assets/content-assets/cms2-it-it/img/cards/web/ME05/';
const cards = [
  [106, 'Campanella Oscura'], [107, 'Scambio di Energia'], [108, 'Lotta Finale di Iridio'],
  [109, 'Imma'], [110, 'Difensore Ferreo'], [111, 'Vitalit\u00e0 di Misty'],
  [112, 'Recluta del Clan Ruggine'], [113, 'Bomba Assurda'], [114, 'Mega Zeraora-ex'],
  [115, 'Mega Chandelure-ex'], [116, 'Mega Darkrai-ex'], [117, 'Morpeko-ex'],
  [118, 'Lotta Finale di Iridio'], [119, 'Imma'], [120, 'Mega Darkrai-ex'],
].map(([number, nome]) => ({ number: String(number), nome }));

const wranglerBin = path.join(workerRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'wrangler.cmd' : 'wrangler');

function sqlString(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

function runWrangler(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(wranglerBin, args, { cwd: workerRoot, shell: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let stderr = '';
    child.stderr.on('data', (data) => { stderr += data.toString(); });
    child.on('close', (code) => code === 0 ? resolve() : reject(new Error(`wrangler exited ${code}: ${stderr.slice(0, 500)}`)));
  });
}

async function writeSql() {
  const rows = cards.map((card) => `(${sqlString(`ME05_IT_${card.number}.webp`)}, 'me05', ${sqlString(card.number)}, ${sqlString(card.nome)}, NULL, NULL, NULL, '[]', 'ok', 0)`);
  const sql = [
    "UPDATE expansions SET card_count = MAX(card_count, 120), published = 1, coverage_pct = 1 WHERE id = 'me05';",
    'INSERT INTO cards (card_id, expansion_id, card_number, nome, tipo, ps, regola_speciale, attacchi_json, image_status, image_webp) VALUES',
    `${rows.join(',\n')}\nON CONFLICT(card_id) DO UPDATE SET nome = excluded.nome, image_status = 'ok';`,
  ].join('\n');
  await writeFile(sqlFile, `${sql}\n`, 'utf8');
}

async function main() {
  const args = process.argv.slice(2);
  const downloadOnly = args.includes('--download-only');
  const uploadStaged = args.includes('--upload-staged');
  if (downloadOnly === uploadStaged) throw new Error('Usare esattamente uno tra --download-only e --upload-staged');

  await writeSql();
  await mkdir(stageDir, { recursive: true });

  if (downloadOnly) {
    for (const card of cards) {
      const url = `${imageBase}ME05_IT_${card.number}.png`;
      const response = await fetch(url);
      if (!response.ok) throw new Error(`${url} -> HTTP ${response.status}`);
      await writeFile(path.join(stageDir, `${card.number}.png`), Buffer.from(await response.arrayBuffer()));
      console.log(`Scaricata localmente ME05/${card.number} ${card.nome}`);
    }
    await writeFile(path.join(stageDir, 'manifest.json'), JSON.stringify(cards, null, 2), 'utf8');
    console.log(`Staging completato: ${stageDir}`);
    console.log(`SQL pronto: ${sqlFile}`);
    return;
  }

  const manifest = JSON.parse(await readFile(path.join(stageDir, 'manifest.json'), 'utf8'));
  for (const card of manifest) {
    const localFile = path.join(stageDir, `${card.number}.png`);
    await readFile(localFile);
    await runWrangler([
      'r2', 'object', 'put', `${bucket}/it/ME05/ME05_IT_${card.number}.png`,
      '--file', localFile, '--remote', '--content-type', 'image/png',
    ]);
    console.log(`Caricata da staging locale ME05/${card.number}`);
  }
  await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--file', sqlFile]);
  console.log(`Import Cloudflare completato: ${manifest.length} carte.`);
}

main().catch((error) => {
  console.error('Errore fatale:', error);
  process.exit(1);
});