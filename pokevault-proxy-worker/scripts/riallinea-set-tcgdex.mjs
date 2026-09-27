#!/usr/bin/env node
// Riscrive illustratore, rarita', tipo e stadio di un set prendendoli da
// TCGdex, anche dove il campo c'e' gia'.
//
// Perche' esiste, accanto ai backfill-*-tcgdex.mjs: quelli riempiono solo i
// buchi, e non servono quando il dato c'e' ma e' sbagliato. Nato il 27/09/2026
// per rsv10pt5 e zsv10pt5, che lib/tcgdex-set-id-map.mjs abbinava al set
// TCGdex dell'altro: i backfill avevano scritto su ogni carta i dati della
// carta con lo stesso numero nell'altro set.
//
// Protezione: una carta si tocca solo se il suo nome su TCGdex (it) coincide
// col nostro. Abbinare per numero e' esattamente cio' che ha prodotto il
// danno, quindi il nome fa da controprova e chi non la passa viene elencato e
// lasciato com'e'.
//
// Formati, gli stessi dei backfill: rarita' e stadio dal payload inglese
// (stadio via canonicalStage), tipo e illustratore da quello italiano.
//
// Uso:
//   node scripts/riallinea-set-tcgdex.mjs <setId>            (dry-run)
//   node scripts/riallinea-set-tcgdex.mjs <setId> --apply    (scrive su D1)

import { writeFile, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { TCGDEX_ID_OVERRIDES } from './lib/tcgdex-set-id-map.mjs';
import { canonicalStage } from './lib/tcgdex-stage.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.riallinea-tmp');
const CONCURRENCY = 6;
const FIELDS = ['illustratore', 'rarity', 'tipo', 'stage'];

const wranglerBin = path.join(workerRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'wrangler.cmd' : 'wrangler');

function runWrangler(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(wranglerBin, args, { cwd: workerRoot, shell: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (d) => { stdout += d.toString(); });
    child.stderr.on('data', (d) => { stderr += d.toString(); });
    child.on('close', (code) => (code === 0
      ? resolve(stdout)
      : reject(new Error(`wrangler ${args.slice(0, 3).join(' ')} exited ${code}: ${stderr.slice(0, 400)}`))));
  });
}

async function d1Query(sql) {
  const out = await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--json', '--command', `"${sql.replace(/"/g, '\\"')}"`]);
  return JSON.parse(out.slice(out.indexOf('[')))[0]?.results ?? [];
}

async function fetchJson(url, attempts = 3) {
  for (let i = 0; i < attempts; i += 1) {
    try {
      const res = await fetch(url);
      if (res.status === 404) return null;
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return await res.json();
    } catch (err) {
      if (i === attempts - 1) throw err;
      await new Promise((r) => setTimeout(r, 500 * (i + 1)));
    }
  }
  return null;
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

const sqlString = (v) => `'${String(v).replace(/'/g, "''")}'`;
const normalizeNumber = (raw) => {
  const t = String(raw ?? '').trim();
  return /^\d+$/.test(t) ? String(parseInt(t, 10)) : t.toUpperCase();
};
// "Reshiram-ex" e "Reshiram ex", "Poké Ball" e "Poke Ball": stessa carta.
const normalizeName = (s) => String(s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]/g, '');

async function main() {
  const args = process.argv.slice(2);
  const apply = args.includes('--apply');
  const setId = args.find((a) => !a.startsWith('--'));
  if (!setId) throw new Error('Uso: node scripts/riallinea-set-tcgdex.mjs <setId> [--apply]');
  const tcgdexId = TCGDEX_ID_OVERRIDES[setId] ?? setId;

  const rows = await d1Query(`SELECT card_id, card_number, nome, illustratore, rarity, tipo, stage FROM cards WHERE expansion_id = ${sqlString(setId)}`);
  const setIt = await fetchJson(`https://api.tcgdex.net/v2/it/sets/${tcgdexId}`);
  if (!setIt) throw new Error(`Set TCGdex "${tcgdexId}" non trovato in it`);
  const byNumber = new Map((setIt.cards ?? []).map((c) => [normalizeNumber(c.localId), c]));
  console.log(`${setId} -> TCGdex ${tcgdexId} (${setIt.name}): ${rows.length} carte in D1, ${byNumber.size} su TCGdex\n`);

  const results = await mapWithConcurrency(rows, CONCURRENCY, async (row) => {
    const ref = byNumber.get(normalizeNumber(row.card_number));
    if (!ref) return { row, skip: 'assente su TCGdex' };
    if (normalizeName(ref.name) !== normalizeName(row.nome)) return { row, skip: `nome diverso: TCGdex "${ref.name}"` };
    const [it, en] = await Promise.all([
      fetchJson(`https://api.tcgdex.net/v2/it/cards/${ref.id}`),
      fetchJson(`https://api.tcgdex.net/v2/en/cards/${ref.id}`),
    ]);
    const source = {
      illustratore: it?.illustrator?.trim() || null,
      rarity: en?.rarity?.trim() || null,
      tipo: Array.isArray(it?.types) && it.types.length > 0 ? it.types.join(', ') : null,
      stage: canonicalStage(en?.stage),
    };
    // Si corregge, non si svuota: dove TCGdex non ha il dato si tiene il nostro.
    const changes = {};
    for (const f of FIELDS) {
      if (source[f] && source[f] !== row[f]) changes[f] = source[f];
    }
    return { row, changes };
  });

  const skipped = results.filter((r) => r.skip);
  const changed = results.filter((r) => r.changes && Object.keys(r.changes).length > 0);
  const perField = Object.fromEntries(FIELDS.map((f) => [f, changed.filter((r) => f in r.changes).length]));
  console.log(`Da correggere: ${changed.length} carte ${JSON.stringify(perField)}; gia' giuste: ${results.length - skipped.length - changed.length}; saltate: ${skipped.length}`);
  for (const r of changed.slice(0, 8)) {
    const diff = Object.entries(r.changes).map(([f, v]) => `${f}: ${r.row[f] ?? 'NULL'} -> ${v}`).join('; ');
    console.log(`  ${r.row.card_number} ${r.row.nome}  ${diff}`);
  }
  if (changed.length > 8) console.log(`  ... e altre ${changed.length - 8}`);
  for (const r of skipped) console.log(`  SALTATA ${r.row.card_number} ${r.row.nome} -- ${r.skip}`);

  if (!apply) {
    console.log('\nDRY RUN: nessuna scrittura. Rilancia con --apply.');
    return;
  }
  if (changed.length === 0) return;

  await mkdir(tmpDir, { recursive: true });
  const statements = changed.map((r) => {
    const sets = Object.entries(r.changes).map(([f, v]) => `${f} = ${sqlString(v)}`).join(', ');
    return `UPDATE cards SET ${sets} WHERE card_id = ${sqlString(r.row.card_id)};`;
  });
  const sqlFile = path.join(tmpDir, `${setId}.sql`);
  await writeFile(sqlFile, statements.join('\n'), 'utf8');
  await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--file', sqlFile]);
  await rm(tmpDir, { recursive: true, force: true });
  console.log(`\nScritte ${changed.length} carte su D1.`);
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
