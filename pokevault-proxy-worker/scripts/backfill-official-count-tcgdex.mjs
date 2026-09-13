#!/usr/bin/env node
// Backfill di expansions.official_count in D1: quante carte ha la parte BASE
// di una espansione, cioe' il numero stampato sulle carte dopo la barra.
//
// Non e' card_count, che conta anche segrete e fuori serie: i due differiscono
// su 84 espansioni su 105, e la ricerca per ID nell'app confronta proprio il
// numero stampato (vedi schema/008_add_official_count.sql). Le carte oltre il
// conteggio ufficiale si numerano superandolo -- "219/217".
//
// Stessa fonte e stesso id-mapping degli altri backfill TCGdex (rarita',
// release date, serie, nome del set): set-level, una richiesta per espansione.
//
// Usage:
//   node scripts/backfill-official-count-tcgdex.mjs <expansionId> [--apply]
//   node scripts/backfill-official-count-tcgdex.mjs --all [--apply]
//   node scripts/backfill-official-count-tcgdex.mjs --missing [--apply]

import { writeFile, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { TCGDEX_ID_OVERRIDES } from './lib/tcgdex-set-id-map.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.official-count-tmp');

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

async function fetchExpansionIds({ onlyMissing }) {
  const where = onlyMissing ? 'WHERE official_count IS NULL' : '';
  const parsed = await runD1Query(`SELECT id FROM expansions ${where} ORDER BY id`);
  return (parsed[0]?.results ?? []).map((r) => r.id);
}

async function backfillExpansion(expansionId, apply) {
  const tcgdexId = TCGDEX_ID_OVERRIDES[expansionId] ?? expansionId;
  const setSummary = await fetchJson(`https://api.tcgdex.net/v2/en/sets/${tcgdexId}`);
  if (!setSummary) {
    console.warn(`[${expansionId}] set TCGdex "${tcgdexId}" non trovato -- salto`);
    return { expansionId, ok: false, error: 'set-not-found' };
  }

  const official = setSummary.cardCount?.official;
  if (!(official > 0)) {
    // I set promo non hanno un totale stampato: le loro carte portano un codice
    // ("SVP 001"), non un "x/y". NULL e' la risposta giusta, e l'app lo tratta
    // come "non lo so", mai come "non e' questa espansione".
    console.warn(`[${expansionId}] TCGdex non ha cardCount.official per "${tcgdexId}" -- resta NULL`);
    return { expansionId, ok: false, error: 'no-official-count' };
  }

  const total = setSummary.cardCount?.total ?? null;
  console.log(`[${expansionId}] ufficiale ${official}${total ? ` (totale ${total})` : ''} -- TCGdex: ${tcgdexId}, "${setSummary.name}"`);

  if (apply) {
    await mkdir(tmpDir, { recursive: true });
    const sqlFile = path.join(tmpDir, `${expansionId}.sql`);
    await writeFile(
      sqlFile,
      `UPDATE expansions SET official_count = ${Number(official)} WHERE id = ${sqlString(expansionId)};`,
      'utf8'
    );
    await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--file', sqlFile]);
    await rm(sqlFile, { force: true });
  }

  return { expansionId, ok: true, official };
}

async function main() {
  const args = process.argv.slice(2);
  const apply = args.includes('--apply');
  const all = args.includes('--all');
  const onlyMissing = args.includes('--missing');
  const flags = new Set(['--apply', '--all', '--missing']);
  const targets = (all || onlyMissing)
    ? await fetchExpansionIds({ onlyMissing })
    : args.filter((a) => !flags.has(a));

  if (targets.length === 0) {
    throw new Error('Usage: node backfill-official-count-tcgdex.mjs <expansionId> [--apply]  OR  --all|--missing [--apply]');
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
  console.log(`${ok}/${summary.length} espansioni col conteggio ufficiale`);
  if (failed.length > 0) {
    console.log(`Senza (${failed.length}):`, failed.map((f) => `${f.expansionId} (${f.error})`).join(', '));
  }
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
