#!/usr/bin/env node
// One-time backfill of expansions.name in D1, sourced from TCGdex's Italian
// locale (`GET /v2/it/sets/{id}` -- same id mapping as the rarity/release-
// date/series/logo backfills, see MIGRATION_PLAN.md M4.6). Falls back to the
// English locale name only for the rare set TCGdex has no Italian entry for.
//
// Fixes a real production bug: the Pokedex previously showed a set's name
// borrowed at runtime from a linked PokeWallet set, which silently fell back
// to a Japanese/Chinese set's name when no English match existed for that
// raw set code -- reported by the user as "molte hanno nomi giapponesi/
// cinesi" even though the cards inside are correctly Italian.
//
// Usage:
//   node scripts/backfill-set-name-tcgdex.mjs <expansionId> [--apply]
//   node scripts/backfill-set-name-tcgdex.mjs --all [--apply]

import { writeFile, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { TCGDEX_ID_OVERRIDES } from './lib/tcgdex-set-id-map.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.set-name-tmp');

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

  let name = (await fetchJson(`https://api.tcgdex.net/v2/it/sets/${tcgdexId}`))?.name;
  let source = 'it';
  if (!name) {
    name = (await fetchJson(`https://api.tcgdex.net/v2/en/sets/${tcgdexId}`))?.name;
    source = 'en (fallback, nessuna voce IT su TCGdex)';
  }

  if (!name) {
    console.warn(`[${expansionId}] nessun nome trovato su TCGdex (${tcgdexId}) -- salto`);
    return { expansionId, ok: false, error: 'no-name' };
  }

  console.log(`[${expansionId}] "${name}" (TCGdex: ${tcgdexId}, ${source})`);

  if (apply) {
    await mkdir(tmpDir, { recursive: true });
    const sqlFile = path.join(tmpDir, `${expansionId}.sql`);
    await writeFile(sqlFile, `UPDATE expansions SET name = ${sqlString(name)} WHERE id = ${sqlString(expansionId)};`, 'utf8');
    await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--file', sqlFile]);
    await rm(sqlFile, { force: true });
  }

  return { expansionId, ok: true, name };
}

async function main() {
  const args = process.argv.slice(2);
  const apply = args.includes('--apply');
  const all = args.includes('--all');
  const targets = all ? await fetchAllExpansionIds() : args.filter((a) => a !== '--apply' && a !== '--all');

  if (targets.length === 0) {
    throw new Error('Usage: node backfill-set-name-tcgdex.mjs <expansionId> [--apply]  OR  --all [--apply]');
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
  console.log(`${ok}/${summary.length} espansioni con nome trovato`);
  if (failed.length > 0) {
    console.log(`Fallite (${failed.length}):`, failed.map((f) => f.expansionId).join(', '));
  }
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
