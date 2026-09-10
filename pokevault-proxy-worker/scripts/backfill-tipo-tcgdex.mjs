#!/usr/bin/env node
// One-time backfill of cards.tipo in D1, sourced from TCGdex (MIT-licensed,
// api.tcgdex.net). TCGdex is touched only here, at backfill time -- never by
// the Worker or the app at runtime. Same pattern as backfill-rarity-tcgdex.mjs
// and backfill-release-date-tcgdex.mjs -- see MIGRATION_PLAN.md M4.6.
//
// Why this exists: only expansion me05 (ingested via scripts/ingest-tcgdex-set.mjs,
// which sets tipo directly at ingest time) has cards.tipo populated -- confirmed
// via `SELECT expansion_id, COUNT(*) FROM cards WHERE tipo IS NOT NULL GROUP BY
// expansion_id` returning a single row (me05, 95). The other 106 expansions
// (bulk-imported via import-catalog-to-d1.mjs from the original R2 catalog blob,
// which never carried a type field) have tipo = NULL for all ~15.4k cards. The
// app's fallback (PokeTcgRepository.toItalianTcgCard: types = record.tipo ?:
// baseCard?.types) silently substitutes the linked ENGLISH PokeWallet card's
// type when tipo is null -- inconsistent/wrong results in DeckLab's "Analisi
// Lab" type breakdown, reported by the user on 2026-09-09.
//
// Uses the IT locale (not EN, unlike the rarity backfill): D1 already has real
// Italian type names ("Fuoco", "Acqua", ...) for me05, and the Kotlin side
// (TypeBadge's `when` block) matches on Italian names -- EN values would be a
// second inconsistency layered on top of the one this script fixes.
//
// Usage:
//   node scripts/backfill-tipo-tcgdex.mjs <expansionId>              (dry-run: report only)
//   node scripts/backfill-tipo-tcgdex.mjs <expansionId> --apply       (write to D1)
//   node scripts/backfill-tipo-tcgdex.mjs --all [--apply]             (every expansion in D1)

import { writeFile, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { TCGDEX_ID_OVERRIDES } from './lib/tcgdex-set-id-map.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.tipo-tmp');
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
// documented in earlier sessions) unrelated to the query itself -- retried
// rather than trusting a single attempt, same reasoning as headOk() in
// ingest-tcgdex-set.mjs.
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
// which returns execution stats, not the actual row data. --command with
// spawn(shell: true) on Windows re-tokenizes on spaces, so the SQL string
// is wrapped in literal double quotes to survive that.
async function runD1Query(sql) {
  const out = await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--json', '--command', `"${sql.replace(/"/g, '\\"')}"`]);
  const jsonStart = out.indexOf('[');
  return JSON.parse(jsonStart >= 0 ? out.slice(jsonStart) : out);
}

async function fetchD1CardNumbers(expansionId) {
  // Only cards still missing tipo -- skips me05 automatically and makes --all
  // safely re-runnable (already-backfilled cards are never re-fetched).
  const parsed = await runD1Query(
    `SELECT card_id, card_number FROM cards WHERE expansion_id = ${sqlString(expansionId)} AND (tipo IS NULL OR tipo = '')`
  );
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
    console.log(`[${expansionId}] nessuna carta da aggiornare, salto`);
    return { expansionId, matched: 0, total: 0 };
  }

  const setSummary = await fetchJson(`https://api.tcgdex.net/v2/it/sets/${tcgdexId}`);
  if (!setSummary) {
    console.warn(`[${expansionId}] set TCGdex "${tcgdexId}" non trovato -- salto (${rows.length} carte senza tipo)`);
    return { expansionId, matched: 0, total: rows.length, error: 'set-not-found' };
  }

  const byNormalizedLocalId = new Map();
  for (const c of setSummary.cards ?? []) {
    byNormalizedLocalId.set(normalizeCardNumber(c.localId), c);
  }

  const results = await mapWithConcurrency(rows, CONCURRENCY, async (row) => {
    const ref = byNormalizedLocalId.get(normalizeCardNumber(row.card_number));
    if (!ref) return { row, tipo: null };
    const detail = await fetchJson(`https://api.tcgdex.net/v2/it/cards/${ref.id}`);
    // Trainer/Energy cards have no `types` (only Pokemon do) -- expected null,
    // not an error. Joined the same way as ingest-tcgdex-set.mjs for the rare
    // dual-type card, so the format matches what's already in D1 for me05.
    const types = detail?.types;
    const tipo = Array.isArray(types) && types.length > 0 ? types.join(', ') : null;
    return { row, tipo };
  });

  const withTipo = results.filter((r) => r.tipo);
  console.log(`[${expansionId}] ${withTipo.length}/${rows.length} carte con tipo trovato (TCGdex: ${tcgdexId})`);

  if (apply && withTipo.length > 0) {
    await mkdir(tmpDir, { recursive: true });
    const statements = withTipo.map(
      (r) => `UPDATE cards SET tipo = ${sqlString(r.tipo)} WHERE card_id = ${sqlString(r.row.card_id)};`
    );
    const sqlFile = path.join(tmpDir, `${expansionId}.sql`);
    await writeFile(sqlFile, statements.join('\n'), 'utf8');
    await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--file', sqlFile]);
    await rm(sqlFile, { force: true });
  }

  return { expansionId, matched: withTipo.length, total: rows.length };
}

async function main() {
  const args = process.argv.slice(2);
  const apply = args.includes('--apply');
  const all = args.includes('--all');
  const targets = all ? await fetchAllExpansionIds() : args.filter((a) => a !== '--apply' && a !== '--all');

  if (targets.length === 0) {
    throw new Error('Usage: node backfill-tipo-tcgdex.mjs <expansionId> [--apply]  OR  --all [--apply]');
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
  console.log(`Carte totali: ${totalCards}, con tipo trovato: ${totalMatched} (${totalCards ? ((totalMatched / totalCards) * 100).toFixed(1) : 0}%)`);
  if (failed.length > 0) {
    console.log(`Espansioni con errore/set non trovato (${failed.length}):`, failed.map((f) => f.expansionId).join(', '));
  }
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
