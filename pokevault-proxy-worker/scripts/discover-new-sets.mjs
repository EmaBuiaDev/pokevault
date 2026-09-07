#!/usr/bin/env node
// Compares TCGdex's full set list against what's already in D1, reporting
// sets that are genuinely new. Exact-id matching is enough GOING FORWARD:
// the historical alias mess (TCGdex "sv03.5" vs our "sv3pt5", "swsh10.5" vs
// "pgo") only exists because the pre-existing dataset (curated by hand,
// source unknown) used different naming conventions than TCGdex. Every set
// THIS pipeline ingests uses the TCGdex id as-is as the D1 expansion id, so
// future runs never hit that ambiguity again.
//
// Deliberately excludes non-physical-TCG product lines that are out of
// scope for this catalog: Pokemon TCG Pocket (digital-only game, ids like
// "A1", "A2a", "B2"), Trainer Kits ("tk-*", 2-player starter box exclusives),
// and McDonald's yearly promo tie-ins ("20XXxx") -- all niche side-products,
// not the main card pool the app tracks.
//
// Usage:
//   node scripts/discover-new-sets.mjs                (list only)
//   node scripts/discover-new-sets.mjs --ingest        (list, then run
//                                                        ingest-tcgdex-set.mjs
//                                                        --apply on each)

import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const wranglerBin = path.join(workerRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'wrangler.cmd' : 'wrangler');

// Pokemon TCG Pocket sets use "A" or "B" followed by a digit (A1, A2a, B2a, ...).
// Every physical-TCG id used by TCGdex either has no leading letter block
// matching this shape or is a well-known era prefix (ex, sm, swsh, sv, me, ...)
// that never collides with it -- this pattern is intentionally narrow.
const POCKET_RE = /^[AB]\d/i;
const OUT_OF_SCOPE_RE = /^(tk-|20\d\d[a-z]+$)/i;

function isInScope(id) {
  return !POCKET_RE.test(id) && !OUT_OF_SCOPE_RE.test(id);
}

// spawn's shell:true on Windows just space-joins the args array into one
// command line and lets cmd.exe re-split it -- any argument containing a
// space (e.g. a SQL --command string) must be quoted here first, or it
// silently gets split into multiple CLI arguments.
function quoteForShell(arg) {
  return /\s/.test(arg) ? `"${arg.replace(/"/g, '\\"')}"` : arg;
}

function runWrangler(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(wranglerBin, args.map(quoteForShell), { cwd: workerRoot, shell: true, stdio: ['ignore', 'pipe', 'inherit'] });
    let stdout = '';
    child.stdout.on('data', (d) => { stdout += d.toString(); });
    child.on('close', (code) => (code === 0 ? resolve(stdout) : reject(new Error(`wrangler ${args.join(' ')} exited ${code}`))));
  });
}

function runNode(scriptArgs) {
  return new Promise((resolve, reject) => {
    const child = spawn('node', scriptArgs, { cwd: workerRoot, stdio: 'inherit' });
    child.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`node ${scriptArgs.join(' ')} exited ${code}`))));
  });
}

async function getD1ExpansionIds() {
  const raw = (await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--command', 'SELECT id FROM expansions', '--json'])).replace(/^﻿/, '');
  const data = JSON.parse(raw);
  return new Set(data[0].results.map((r) => r.id));
}

// The precise cutoff that makes date-filtering work: any TCGdex set released
// on or before this date is, by construction, something our existing
// catalog already covers (possibly under a different id -- see the alias
// note above) -- so it is never a genuinely new set, even if it slipped
// past the id-exclusion filter.
async function getD1LatestReleaseDate() {
  const raw = (await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--command', 'SELECT MAX(release_date) as d FROM expansions', '--json'])).replace(/^﻿/, '');
  const data = JSON.parse(raw);
  return data[0].results[0].d; // 'YYYY-MM-DD' or null if no expansion has release_date set yet
}

async function getTcgdexSetIds() {
  const res = await fetch('https://api.tcgdex.net/v2/it/sets');
  if (!res.ok) throw new Error(`TCGdex /sets -> HTTP ${res.status}`);
  const sets = await res.json();
  return sets.map((s) => s.id);
}

async function getTcgdexReleaseDate(setId) {
  const res = await fetch(`https://api.tcgdex.net/v2/it/sets/${setId}`);
  if (!res.ok) return null;
  const set = await res.json();
  return set.releaseDate ?? null;
}

async function main() {
  const ingest = process.argv.includes('--ingest');

  console.log('Recupero elenco set da D1...');
  const d1Ids = await getD1ExpansionIds();
  const latestKnownDate = await getD1LatestReleaseDate();
  console.log(`D1: ${d1Ids.size} espansioni presenti, data di uscita piu' recente conosciuta: ${latestKnownDate ?? 'nessuna (release_date non ancora popolata)'}`);

  console.log('Recupero elenco set da TCGdex...');
  const tcgdexIds = await getTcgdexSetIds();
  console.log(`TCGdex: ${tcgdexIds.length} espansioni totali`);

  const idCandidates = tcgdexIds.filter((id) => isInScope(id) && !d1Ids.has(id));
  console.log(`\nCandidati per id (esclusi TCG Pocket, Trainer Kit, McDonald's, id gia' presenti): ${idCandidates.length}`);

  // Second, stricter filter: a candidate counts as genuinely new only if its
  // TCGdex releaseDate is AFTER the latest release we already have in D1.
  // This is what actually eliminates the historical-alias false positives
  // (old sets under a TCGdex id different from our existing one) without
  // needing to enumerate every alias by hand.
  let newSets = idCandidates;
  if (latestKnownDate) {
    console.log('Verifica data di uscita di ogni candidato...');
    const withDates = await Promise.all(idCandidates.map(async (id) => ({ id, releaseDate: await getTcgdexReleaseDate(id) })));
    newSets = withDates.filter((s) => s.releaseDate && s.releaseDate > latestKnownDate).map((s) => s.id);
    const rejected = withDates.filter((s) => !(s.releaseDate && s.releaseDate > latestKnownDate));
    if (rejected.length > 0) {
      console.log(`Scartati perche' non piu' recenti di ${latestKnownDate} (probabili alias storici, non set nuovi): ${rejected.length}`);
    }
  }

  console.log(`\nSet DAVVERO nuovi (usciti dopo ${latestKnownDate ?? 'N/D'}): ${newSets.length}`);
  if (newSets.length > 0) console.log(newSets.join(', '));

  if (!ingest || newSets.length === 0) {
    if (!ingest) console.log('\n(modalita solo elenco: rilancia con --ingest per importarli automaticamente)');
    return;
  }

  console.log('\n=== Ingest automatico dei set nuovi ===');
  for (const setId of newSets) {
    console.log(`\n--- ${setId} ---`);
    try {
      await runNode(['scripts/ingest-tcgdex-set.mjs', setId, '--apply']);
    } catch (err) {
      console.error(`Ingest fallito per ${setId}: ${err.message}`);
    }
  }
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
