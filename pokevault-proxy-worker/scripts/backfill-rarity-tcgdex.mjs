#!/usr/bin/env node
// Backfill di cards.rarity in D1, sourced from TCGdex (MIT-licensed,
// api.tcgdex.net). TCGdex is touched only here, at backfill time -- never by
// the Worker or the app at runtime. Once written, rarity is ours in D1 like
// the rest of the catalog. See MIGRATION_PLAN.md M4.6 for the source
// analysis and the full id-mapping table this script encodes below.
//
// Usage:
//   node scripts/backfill-rarity-tcgdex.mjs <expansionId>              (dry-run: report only)
//   node scripts/backfill-rarity-tcgdex.mjs <expansionId> --apply       (write to D1)
//   node scripts/backfill-rarity-tcgdex.mjs --all [--apply]             (every expansion in D1)
//   ... --only-missing                                                  (solo le carte con rarity vuota)
//
// `--only-missing` esiste perche' questo non e' piu' un backfill unico: ogni
// import nuovo (topup, set storici, promo) entra senza rarita' e va ripreso.
// Senza il flag il giro riscarica da TCGdex *tutte* le carte del set per
// riscrivere valori che gia' abbiamo -- con --all sono 18.815 chiamate per
// coprirne 3.050 -- e ogni riscrittura e' un'occasione in piu' di sovrascrivere
// un valore giusto con uno sbagliato. Col flag si leggono da D1 solo le carte
// bucate, e con --all si visitano solo le espansioni che ne hanno.

import { writeFile, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { TCGDEX_ID_OVERRIDES } from './lib/tcgdex-set-id-map.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.rarity-tmp');
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

// Una rarita' vuota e una NULL sono lo stesso buco: l'app le manda tutte e due
// nel ramo "Altro" di RarityUtils, quello con le due stelle verdi.
const MISSING_RARITY_SQL = "(rarity IS NULL OR TRIM(rarity) = '')";

async function fetchD1CardNumbers(expansionId, onlyMissing) {
  const where = onlyMissing
    ? `expansion_id = ${sqlString(expansionId)} AND ${MISSING_RARITY_SQL}`
    : `expansion_id = ${sqlString(expansionId)}`;
  const parsed = await runD1Query(`SELECT card_id, card_number FROM cards WHERE ${where}`);
  return parsed[0]?.results ?? [];
}

async function fetchAllExpansionIds(onlyMissing) {
  const sql = onlyMissing
    ? `SELECT DISTINCT expansion_id AS id FROM cards WHERE ${MISSING_RARITY_SQL} ORDER BY id`
    : 'SELECT id FROM expansions ORDER BY id';
  const parsed = await runD1Query(sql);
  return (parsed[0]?.results ?? []).map((r) => r.id);
}

async function backfillExpansion(expansionId, apply, onlyMissing) {
  const tcgdexId = TCGDEX_ID_OVERRIDES[expansionId] ?? expansionId;
  const rows = await fetchD1CardNumbers(expansionId, onlyMissing);
  if (rows.length === 0) {
    console.log(`[${expansionId}] ${onlyMissing ? "nessuna carta senza rarita'" : 'nessuna carta in D1'}, salto`);
    return { expansionId, matched: 0, total: 0 };
  }

  const setSummary = await fetchJson(`https://api.tcgdex.net/v2/en/sets/${tcgdexId}`);
  if (!setSummary) {
    console.warn(`[${expansionId}] set TCGdex "${tcgdexId}" non trovato -- salto (${rows.length} carte senza rarita')`);
    return { expansionId, matched: 0, total: rows.length, error: 'set-not-found' };
  }

  const byNormalizedLocalId = new Map();
  for (const c of setSummary.cards ?? []) {
    byNormalizedLocalId.set(normalizeCardNumber(c.localId), c);
  }

  const results = await mapWithConcurrency(rows, CONCURRENCY, async (row) => {
    const ref = byNormalizedLocalId.get(normalizeCardNumber(row.card_number));
    if (!ref) return { row, rarity: null };
    const detail = await fetchJson(`https://api.tcgdex.net/v2/en/cards/${ref.id}`);
    return { row, rarity: detail?.rarity?.trim() || null };
  });

  const withRarity = results.filter((r) => r.rarity);
  // I numeri rimasti scoperti sono il dato che serve per scegliere la seconda
  // fonte: senza stamparli il riepilogo dice solo "ne mancano 11" e tocca
  // ricercarseli a mano.
  const unmatched = results.filter((r) => !r.rarity).map((r) => r.row.card_number);
  console.log(
    `[${expansionId}] ${withRarity.length}/${rows.length} carte con rarita' trovata (TCGdex: ${tcgdexId})` +
      (unmatched.length > 0
        ? ` -- scoperte: ${unmatched.slice(0, 15).join(', ')}${unmatched.length > 15 ? ` ...+${unmatched.length - 15}` : ''}`
        : '')
  );

  if (apply && withRarity.length > 0) {
    await mkdir(tmpDir, { recursive: true });
    const statements = withRarity.map(
      (r) => `UPDATE cards SET rarity = ${sqlString(r.rarity)} WHERE card_id = ${sqlString(r.row.card_id)};`
    );
    const sqlFile = path.join(tmpDir, `${expansionId}.sql`);
    await writeFile(sqlFile, statements.join('\n'), 'utf8');
    await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--file', sqlFile]);
    await rm(sqlFile, { force: true });
  }

  return { expansionId, matched: withRarity.length, total: rows.length };
}

async function main() {
  const args = process.argv.slice(2);
  const apply = args.includes('--apply');
  const all = args.includes('--all');
  const onlyMissing = args.includes('--only-missing');
  const flags = new Set(['--apply', '--all', '--only-missing']);
  const targets = all ? await fetchAllExpansionIds(onlyMissing) : args.filter((a) => !flags.has(a));

  if (targets.length === 0) {
    throw new Error('Usage: node backfill-rarity-tcgdex.mjs <expansionId> [--apply]  OR  --all [--apply]');
  }

  console.log(`${apply ? 'APPLY' : 'DRY RUN'}${onlyMissing ? " (solo carte senza rarita')" : ''} su ${targets.length} espansione/i\n`);

  const summary = [];
  for (const expansionId of targets) {
    try {
      summary.push(await backfillExpansion(expansionId, apply, onlyMissing));
    } catch (err) {
      console.error(`[${expansionId}] errore fatale:`, err.message);
      summary.push({ expansionId, matched: 0, total: 0, error: err.message });
    }
  }

  const totalCards = summary.reduce((s, r) => s + r.total, 0);
  const totalMatched = summary.reduce((s, r) => s + r.matched, 0);
  const failed = summary.filter((r) => r.error);
  console.log('\n=== Riepilogo ===');
  console.log(`Carte totali: ${totalCards}, con rarita' trovata: ${totalMatched} (${totalCards ? ((totalMatched / totalCards) * 100).toFixed(1) : 0}%)`);
  if (failed.length > 0) {
    console.log(`Espansioni con errore/set non trovato (${failed.length}):`, failed.map((f) => f.expansionId).join(', '));
  }
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
