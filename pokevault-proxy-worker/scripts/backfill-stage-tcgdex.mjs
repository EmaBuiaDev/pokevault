#!/usr/bin/env node
// One-time backfill of cards.stage in D1, sourced from TCGdex (MIT-licensed,
// api.tcgdex.net). TCGdex is touched only here, at backfill time -- never by
// the Worker or the app at runtime. Once written, the stage is ours in D1 like
// the rest of the catalog. Gemello di backfill-rarity-tcgdex.mjs: stessa
// meccanica, stessa mappa di set id, stesso modo di parlare con wrangler.
//
// Perche': senza stadio l'app non sa se un Pokemon si puo' calare in campo
// dalla mano, e l'Hand-Simulator contava come Base anche le Fase 1 (vedi
// schema/009_add_stage.sql).
//
// Legge l'endpoint INGLESE: i valori entrano in D1 gia' canonici (Basic,
// Stage1, Stage2, ...) senza passare da canonicalStage(), che resta comunque
// applicata per difesa e per i set dove TCGdex localizza comunque il campo.
//
// Usage:
//   node scripts/backfill-stage-tcgdex.mjs <expansionId>              (dry-run: report only)
//   node scripts/backfill-stage-tcgdex.mjs <expansionId> --apply       (write to D1)
//   node scripts/backfill-stage-tcgdex.mjs --all [--apply]             (every expansion in D1)

import { writeFile, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { TCGDEX_ID_OVERRIDES } from './lib/tcgdex-set-id-map.mjs';
import { canonicalStage } from './lib/tcgdex-stage.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.stage-tmp');
const CONCURRENCY = 8;

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

// wrangler's CLI has occasional transient crashes on Windows (seen and
// documented in earlier sessions, e.g. native UV_HANDLE_CLOSING assertions)
// unrelated to the query itself -- retried here rather than trusting a
// single attempt, same reasoning as headOk() in ingest-tcgdex-set.mjs.
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

function sqlString(value) {
  if (value === null || value === undefined) return 'NULL';
  return `'${String(value).replace(/'/g, "''")}'`;
}

// Matches our D1 card_number against TCGdex's localId. Pure-digit numbers
// compare by integer value (strips padding either side -- our "1" vs
// TCGdex's zero-padded "001"); anything with letters (SV001, TG22, GG01,
// CC001...) compares case-insensitively as-is.
function normalizeCardNumber(raw) {
  const trimmed = String(raw ?? '').trim();
  if (/^\d+$/.test(trimmed)) return String(parseInt(trimmed, 10));
  return trimmed.toUpperCase();
}

// Reads (SELECT) must go through --command, not --file: wrangler routes
// --file through its bulk "import" pipeline regardless of the SQL inside,
// which returns execution stats ("Rows read"/"Rows written"), not the actual
// row data -- confirmed by testing a plain SELECT through --file directly.
// --command with spawn(shell: true) on Windows re-tokenizes on spaces (no
// automatic quoting across the args array), so the SQL string itself is
// wrapped in literal double quotes here to survive that.
async function runD1Query(sql) {
  const out = await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--json', '--command', `"${sql.replace(/"/g, '\\"')}"`]);
  // wrangler sometimes prints progress lines to stdout before the JSON payload.
  const jsonStart = out.indexOf('[');
  return JSON.parse(jsonStart >= 0 ? out.slice(jsonStart) : out);
}

async function fetchD1CardNumbers(expansionId) {
  const parsed = await runD1Query(`SELECT card_id, card_number FROM cards WHERE expansion_id = ${sqlString(expansionId)}`);
  return parsed[0]?.results ?? [];
}

async function fetchAllExpansionIds() {
  const parsed = await runD1Query('SELECT id FROM expansions ORDER BY id');
  return (parsed[0]?.results ?? []).map((r) => r.id);
}

async function backfillExpansion(expansionId, apply) {
  const tcgdexId = TCGDEX_ID_OVERRIDES[expansionId] ?? expansionId;
  const rows = await fetchD1CardNumbers(expansionId);
  if (rows.length === 0) {
    console.log(`[${expansionId}] nessuna carta in D1, salto`);
    return { expansionId, matched: 0, total: 0 };
  }

  const setSummary = await fetchJson(`https://api.tcgdex.net/v2/en/sets/${tcgdexId}`);
  if (!setSummary) {
    console.warn(`[${expansionId}] set TCGdex "${tcgdexId}" non trovato -- salto (${rows.length} carte senza stadio)`);
    return { expansionId, matched: 0, total: rows.length, error: 'set-not-found' };
  }

  const byNormalizedLocalId = new Map();
  for (const c of setSummary.cards ?? []) {
    byNormalizedLocalId.set(normalizeCardNumber(c.localId), c);
  }

  const results = await mapWithConcurrency(rows, CONCURRENCY, async (row) => {
    const ref = byNormalizedLocalId.get(normalizeCardNumber(row.card_number));
    if (!ref) return { row, stage: null };
    const detail = await fetchJson(`https://api.tcgdex.net/v2/en/cards/${ref.id}`);
    return { row, stage: canonicalStage(detail?.stage) };
  });

  // Trainer ed Energie non hanno stadio: NULL li' e' il valore giusto, non un
  // buco. Il conteggio percentuale sotto va letto tenendone conto -- un set
  // sano si ferma intorno al 55-65%, non al 100%.
  const withStage = results.filter((r) => r.stage);
  console.log(`[${expansionId}] ${withStage.length}/${rows.length} carte con stadio trovato (TCGdex: ${tcgdexId})`);

  if (apply && withStage.length > 0) {
    await mkdir(tmpDir, { recursive: true });
    const statements = withStage.map(
      (r) => `UPDATE cards SET stage = ${sqlString(r.stage)} WHERE card_id = ${sqlString(r.row.card_id)};`
    );
    const sqlFile = path.join(tmpDir, `${expansionId}.sql`);
    await writeFile(sqlFile, statements.join('\n'), 'utf8');
    await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--file', sqlFile]);
    await rm(sqlFile, { force: true });
  }

  return { expansionId, matched: withStage.length, total: rows.length };
}

async function main() {
  const args = process.argv.slice(2);
  const apply = args.includes('--apply');
  const all = args.includes('--all');
  const targets = all ? await fetchAllExpansionIds() : args.filter((a) => a !== '--apply' && a !== '--all');

  if (targets.length === 0) {
    throw new Error('Usage: node backfill-stage-tcgdex.mjs <expansionId> [--apply]  OR  --all [--apply]');
  }

  console.log(`${apply ? 'APPLY' : 'DRY RUN'} su ${targets.length} espansione/i\n`);

  const summary = [];
  for (const expansionId of targets) {
    try {
      summary.push(await backfillExpansion(expansionId, apply));
    } catch (err) {
      console.error(`[${expansionId}] errore fatale:`, err.message);
      summary.push({ expansionId, matched: 0, total: 0, error: err.message });
    }
  }

  const totalCards = summary.reduce((s, r) => s + r.total, 0);
  const totalMatched = summary.reduce((s, r) => s + r.matched, 0);
  const failed = summary.filter((r) => r.error);
  console.log('\n=== Riepilogo ===');
  console.log(`Carte totali: ${totalCards}, con stadio trovato: ${totalMatched} (${totalCards ? ((totalMatched / totalCards) * 100).toFixed(1) : 0}%)`);
  if (failed.length > 0) {
    console.log(`Espansioni con errore/set non trovato (${failed.length}):`, failed.map((f) => f.expansionId).join(', '));
  }
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
