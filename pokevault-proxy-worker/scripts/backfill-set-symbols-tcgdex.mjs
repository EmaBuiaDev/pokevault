#!/usr/bin/env node
// Simbolo e sigla di ogni espansione, da TCGdex (stessa fonte e stessa mappa
// degli id dei backfill di loghi, serie e rarita').
//
// - Il simbolo (il disegnino stampato sulle carte) va su R2 in
//   it/{SETCODE}/symbol.png, accanto al logo. Il Worker lo serve su
//   GET /sets/{SETCODE}/symbol.
// - La sigla ufficiale ("PAF", "SSH") va in expansions.abbreviation
//   (schema/016) e arriva all'app con /v1/expansions.
//
// SETCODE e' lo stesso codice con cui l'app chiede il logo: override del
// client, poi base_set_code di D1, poi l'id in maiuscolo -- come
// buildItalianTcgSet in CatalogRepository.kt.
//
// Attenzione alla cache del Worker: un 404 su /sets/{SETCODE}/symbol resta in
// KV per 24 ore. Lo script va lanciato con --apply PRIMA di pubblicare il
// Worker con la rotta nuova, o le espansioni chieste nel frattempo restano
// senza simbolo per un giorno.
//
// Usage:
//   node scripts/backfill-set-symbols-tcgdex.mjs <expansionId> [--apply]
//   node scripts/backfill-set-symbols-tcgdex.mjs --all [--apply]

import { writeFile, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { TCGDEX_ID_OVERRIDES } from './lib/tcgdex-set-id-map.mjs';
import { CLIENT_SET_CODE_OVERRIDES } from './lib/client-set-codes.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.symbol-tmp');
const BUCKET = 'pokevault-images';
const CONCURRENCY = 6;

const wranglerBin = path.join(workerRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'wrangler.cmd' : 'wrangler');

function runWranglerOnce(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(wranglerBin, args, { cwd: workerRoot, shell: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (d) => { stdout += d.toString(); });
    child.stderr.on('data', (d) => { stderr += d.toString(); });
    child.on('close', (code) => {
      if (code === 0) resolve(stdout);
      else reject(new Error(`wrangler ${args.join(' ')} exited ${code}: ${stderr.slice(0, 500)}`));
    });
  });
}

async function runWrangler(args, attempts = 3) {
  let lastErr;
  for (let i = 0; i < attempts; i += 1) {
    try {
      return await runWranglerOnce(args);
    } catch (err) {
      lastErr = err;
      if (i < attempts - 1) await new Promise((r) => setTimeout(r, 1000 * (i + 1)));
    }
  }
  throw lastErr;
}

async function runD1Query(sql) {
  const out = await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--json', '--command', `"${sql.replace(/"/g, '\\"')}"`]);
  const jsonStart = out.indexOf('[');
  return JSON.parse(jsonStart >= 0 ? out.slice(jsonStart) : out);
}

function sqlString(value) {
  if (value === null || value === undefined) return 'NULL';
  return `'${String(value).replace(/'/g, "''")}'`;
}

async function fetchJson(url) {
  for (let i = 0; i < 3; i += 1) {
    try {
      const res = await fetch(url);
      if (res.status === 404) return null;
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return await res.json();
    } catch (err) {
      if (i === 2) throw err;
      await new Promise((r) => setTimeout(r, 500 * (i + 1)));
    }
  }
  return null;
}

async function fetchExpansions(targetIds) {
  const parsed = await runD1Query('SELECT id, base_set_code FROM expansions ORDER BY id');
  const all = parsed[0]?.results ?? [];
  if (!targetIds) return all;
  const wanted = new Set(targetIds);
  return all.filter((r) => wanted.has(r.id));
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
  await Promise.all(Array.from({ length: Math.min(limit, items.length) || 1 }, worker));
  return results;
}

// Dove TCGdex non ha il simbolo, quello del set inglese su pokemontcg.io
// (id loro). Verificati uno per uno l'11/10/2026: tutti 200.
const SYMBOL_FALLBACK_POKEMONTCG_ID = {
  sv07: 'sv7',
  sv08: 'sv8',
  sv8pt5: 'sv8pt5',
  svp: 'svp',
  sve: 'sve',
  base1: 'base1',
  cel25c: 'cel25c',
  sm35: 'sm35',
  sm75: 'sm75',
  me02: 'me2',
  swsh12pt5gg: 'swsh12pt5gg',
};

export function clientSetCode(row) {
  return String(CLIENT_SET_CODE_OVERRIDES[row.id] ?? row.base_set_code ?? row.id).trim().toUpperCase();
}

export function officialAbbreviation(setSummary) {
  const raw = setSummary?.abbreviation?.official ?? setSummary?.tcgOnline ?? null;
  const clean = typeof raw === 'string' ? raw.trim() : '';
  return clean || null;
}

async function main() {
  const args = process.argv.slice(2);
  const apply = args.includes('--apply');
  const all = args.includes('--all');
  const targetIds = all ? null : args.filter((a) => a !== '--apply' && a !== '--all');

  if (!all && targetIds.length === 0) {
    throw new Error('Usage: node backfill-set-symbols-tcgdex.mjs <expansionId> [--apply]  OR  --all [--apply]');
  }

  const rows = await fetchExpansions(targetIds);
  console.log(`${apply ? 'APPLY' : 'DRY RUN'} su ${rows.length} espansione/i\n`);
  await mkdir(tmpDir, { recursive: true });

  // Le sotto-collezioni (swsh10tg, swsh45sv...) stanno nella cartella del set
  // base: il simbolo lo scrive solo il set base (l'id piu' corto), o l'ultimo
  // arrivato sovrascriverebbe l'altro a caso. Si decide su tutte le
  // espansioni di D1, non solo su quelle chieste.
  const allRows = targetIds ? await fetchExpansions(null) : rows;
  const folderOwner = new Map();
  for (const r of allRows) {
    const code = clientSetCode(r);
    const current = folderOwner.get(code);
    if (!current || r.id.length < current.length) folderOwner.set(code, r.id);
  }

  const results = await mapWithConcurrency(rows, CONCURRENCY, async (row) => {
    const setCode = clientSetCode(row);
    const tcgdexId = TCGDEX_ID_OVERRIDES[row.id] ?? row.id;
    try {
      const setSummary = await fetchJson(`https://api.tcgdex.net/v2/en/sets/${tcgdexId}`);
      if (!setSummary) {
        console.warn(`[${row.id}] set TCGdex "${tcgdexId}" non trovato`);
        return { id: row.id, setCode, symbol: false, abbreviation: null, error: 'set-not-found' };
      }
      const abbreviation = officialAbbreviation(setSummary);

      // Il percorso "univ" che TCGdex mette nei metadati l'11/10/2026
      // rispondeva 400 (bucket non valido) per tutti i set; quello "en" della
      // stessa espansione invece funziona. Si provano tutti e due.
      let buf = null;
      const fallbackId = SYMBOL_FALLBACK_POKEMONTCG_ID[row.id];
      const candidates = [...new Set([
        ...(setSummary.symbol ? [`${setSummary.symbol}.png`, `${setSummary.symbol.replace('/univ/', '/en/')}.png`] : []),
        ...(fallbackId ? [`https://images.pokemontcg.io/${fallbackId}/symbol.png`] : []),
      ])];
      if (candidates.length > 0) {
        for (const url of candidates) {
          const res = await fetch(url);
          if (res.ok) {
            buf = Buffer.from(await res.arrayBuffer());
            break;
          }
        }
        if (!buf) console.warn(`[${row.id}] simbolo non scaricabile: ${candidates.join(' | ')}`);
      }

      console.log(`[${row.id}] ${setCode}  sigla=${abbreviation ?? '-'}  simbolo=${buf ? `${buf.length} byte` : 'NO'}`);

      const ownsFolder = folderOwner.get(setCode) === row.id;
      if (buf && !ownsFolder) console.log(`[${row.id}] cartella ${setCode} di ${folderOwner.get(setCode)}: simbolo non scritto`);

      if (apply) {
        if (buf && ownsFolder) {
          const localFile = path.join(tmpDir, `${row.id}.png`);
          await writeFile(localFile, buf);
          await runWrangler(['r2', 'object', 'put', `${BUCKET}/it/${setCode}/symbol.png`, '--file', localFile, '--content-type', 'image/png', '--remote']);
          await rm(localFile, { force: true });
        }
        if (abbreviation) {
          const sqlFile = path.join(tmpDir, `${row.id}.sql`);
          await writeFile(sqlFile, `UPDATE expansions SET abbreviation = ${sqlString(abbreviation)} WHERE id = ${sqlString(row.id)};`, 'utf8');
          await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--file', sqlFile]);
          await rm(sqlFile, { force: true });
        }
      }
      return { id: row.id, setCode, symbol: Boolean(buf), abbreviation };
    } catch (err) {
      console.error(`[${row.id}] errore:`, err.message);
      return { id: row.id, setCode, symbol: false, abbreviation: null, error: err.message };
    }
  });

  const withSymbol = results.filter((r) => r.symbol).length;
  const withAbbr = results.filter((r) => r.abbreviation).length;
  console.log('\n=== Riepilogo ===');
  console.log(`simbolo: ${withSymbol}/${results.length}   sigla: ${withAbbr}/${results.length}`);
  const noSymbol = results.filter((r) => !r.symbol).map((r) => r.id);
  const noAbbr = results.filter((r) => !r.abbreviation).map((r) => r.id);
  if (noSymbol.length) console.log(`Senza simbolo (${noSymbol.length}): ${noSymbol.join(', ')}`);
  if (noAbbr.length) console.log(`Senza sigla (${noAbbr.length}): ${noAbbr.join(', ')}`);

  // Due espansioni nella stessa cartella si sovrascriverebbero il simbolo.
  const byCode = new Map();
  for (const r of results) byCode.set(r.setCode, [...(byCode.get(r.setCode) ?? []), r.id]);
  const clashes = [...byCode.entries()].filter(([, ids]) => ids.length > 1);
  if (clashes.length) console.log(`ATTENZIONE cartelle condivise: ${clashes.map(([c, ids]) => `${c}=${ids.join('+')}`).join(', ')}`);

  await rm(tmpDir, { recursive: true, force: true });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((err) => {
    console.error('Errore fatale:', err);
    process.exit(1);
  });
}
