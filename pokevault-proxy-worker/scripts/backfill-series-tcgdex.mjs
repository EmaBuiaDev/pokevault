#!/usr/bin/env node
// One-time backfill of expansions.series in D1, sourced from TCGdex's own
// `serie.name` taxonomy field (same source/id-mapping as the rarity,
// release-date and logo backfills -- see MIGRATION_PLAN.md M4.6). Set-level,
// one request per expansion.
//
// Replaces the ~150 lines of fuzzy name-matching (deriveSeriesName /
// canonicalSeries in PokeTcgRepository.kt) that classified ITA sets by
// guessing from a name that was often empty or wrong for cards borrowed
// from a PokeWallet link that failed to resolve.
//
// Usage:
//   node scripts/backfill-series-tcgdex.mjs <expansionId> [--apply]
//   node scripts/backfill-series-tcgdex.mjs --all [--apply]

import { writeFile, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { TCGDEX_ID_OVERRIDES } from './lib/tcgdex-set-id-map.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.series-tmp');

const wranglerBin = path.join(workerRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'wrangler.cmd' : 'wrangler');

// TCGdex gap found on 2026-09-08: sv03.5 ("151") is the only expansion of the
// 107 whose set summary has no `serie` field at all. Everything else -- 106
// of 107 -- resolved directly from TCGdex, including every other ".5"/promo/
// sub-collection set. Hardcoded because it's a genuine one-off upstream gap,
// not a pattern worth a second override table.
const SERIES_FALLBACK = {
  sv3pt5: 'Scarlet & Violet',
};

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

async function fetchJson(url, attempts = 3) {
  for (let i = 0; i < attempts; i += 1) {
    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 10000);
      try {
        const res = await fetch(url, { signal: controller.signal });
        if (res.status === 404) return null;
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return await res.json();
      } finally {
        clearTimeout(timeout);
      }
    } catch (err) {
      if (i === attempts - 1) throw err;
      await new Promise((r) => setTimeout(r, 500 * (i + 1)));
    }
  }
  return null;
}

function sqlString(value) {
  if (value === null || value === undefined) return 'NULL';
  return `'${String(value).replace(/'/g, "''")}'`;
}

async function runD1Query(sql) {
  const out = await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--json', '--command', `"${sql.replace(/"/g, '\\"')}"`]);
  const jsonStart = out.indexOf('[');
  return JSON.parse(jsonStart >= 0 ? out.slice(jsonStart) : out);
}

async function fetchAllExpansionIds() {
  const parsed = await runD1Query('SELECT id FROM expansions ORDER BY id');
  return (parsed[0]?.results ?? []).map((r) => r.id);
}

async function backfillExpansion(expansionId, apply) {
  const tcgdexId = TCGDEX_ID_OVERRIDES[expansionId] ?? expansionId;
  const setSummary = await fetchJson(`https://api.tcgdex.net/v2/en/sets/${tcgdexId}`);
  if (!setSummary) {
    console.warn(`[${expansionId}] set TCGdex "${tcgdexId}" non trovato -- salto`);
    return { expansionId, ok: false, error: 'set-not-found' };
  }

  const series = setSummary.serie?.name ?? SERIES_FALLBACK[expansionId] ?? null;
  if (!series) {
    console.warn(`[${expansionId}] TCGdex non ha "serie" per "${tcgdexId}" e nessun fallback -- salto`);
    return { expansionId, ok: false, error: 'no-series' };
  }

  console.log(`[${expansionId}] ${series} (TCGdex: ${tcgdexId}, "${setSummary.name}")`);

  if (apply) {
    await mkdir(tmpDir, { recursive: true });
    const sqlFile = path.join(tmpDir, `${expansionId}.sql`);
    await writeFile(sqlFile, `UPDATE expansions SET series = ${sqlString(series)} WHERE id = ${sqlString(expansionId)};`, 'utf8');
    await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--file', sqlFile]);
    await rm(sqlFile, { force: true });
  }

  return { expansionId, ok: true, series };
}

async function main() {
  const args = process.argv.slice(2);
  const apply = args.includes('--apply');
  const all = args.includes('--all');
  const targets = all ? await fetchAllExpansionIds() : args.filter((a) => a !== '--apply' && a !== '--all');

  if (targets.length === 0) {
    throw new Error('Usage: node backfill-series-tcgdex.mjs <expansionId> [--apply]  OR  --all [--apply]');
  }

  console.log(`${apply ? 'APPLY' : 'DRY RUN'} su ${targets.length} espansione/i\n`);

  const summary = [];
  for (const expansionId of targets) {
    try {
      summary.push(await backfillExpansion(expansionId, apply));
    } catch (err) {
      console.error(`[${expansionId}] errore fatale:`, err.message);
      summary.push({ expansionId, ok: false, error: err.message });
    }
  }

  const ok = summary.filter((r) => r.ok).length;
  const failed = summary.filter((r) => !r.ok);
  console.log('\n=== Riepilogo ===');
  console.log(`${ok}/${summary.length} espansioni con serie trovata`);
  if (failed.length > 0) {
    console.log(`Fallite (${failed.length}):`, failed.map((f) => f.expansionId).join(', '));
  }
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
