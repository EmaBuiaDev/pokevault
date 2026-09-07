#!/usr/bin/env node
// Recompress the ITA card image library from PNG to WebP, same resolution,
// uploaded ALONGSIDE the existing PNGs (additive, non-destructive).
//
// Source of truth for which keys exist: the catalog JSON (it/catalog/cards.cleaned.json),
// not an R2 bucket listing — `wrangler r2 object list` doesn't exist as a CLI command,
// and the catalog already gives us the exact cardId -> R2 key mapping used in production
// (see ItalianCatalog.kt: cardId is literally the filename, e.g. "DP1_IT_1.png").
//
// Usage:
//   node scripts/recompress-webp.mjs --catalog ./catalog-live.json --limit 20   (validation run)
//   node scripts/recompress-webp.mjs --catalog ./catalog-live.json             (full run)
//
// Resumable: successes are appended to progress.ndjson; re-running skips cardIds
// already recorded there. Safe to Ctrl+C and restart.

import { readFile, appendFile, mkdir, rm, stat } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import sharp from 'sharp';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.recompress-tmp');
const progressFile = path.join(__dirname, 'recompress-progress.ndjson');

const BUCKET = 'pokevault-images';
const CONCURRENCY = 6;
const WEBP_QUALITY = 82;

function parseArgs(argv) {
  const args = { catalog: null, limit: null, concurrency: CONCURRENCY };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--catalog') args.catalog = argv[++i];
    else if (argv[i] === '--limit') args.limit = parseInt(argv[++i], 10);
    else if (argv[i] === '--concurrency') args.concurrency = parseInt(argv[++i], 10);
  }
  if (!args.catalog) {
    throw new Error('Usage: node recompress-webp.mjs --catalog <path> [--limit N] [--concurrency N]');
  }
  return args;
}

// Mirrors ItalianCatalogNormalizer.toImageReference (Kotlin): cardId is the
// filename itself, e.g. "DP1_IT_1.png" -> setCode "DP1", base "DP1_IT_1".
const CARD_ID_RE = /^([A-Za-z0-9]+)_IT_([A-Za-z0-9_]+)\.(png|webp|jpe?g)$/i;

// Multiple naming conventions coexist in the real bucket (confirmed by
// probing R2 directly, one set at a time): most sets use uppercase folder +
// "{SET}_IT_{number}.png" (matches cardId as-is); SVP uses uppercase folder +
// bare "{number}.png"; MEP uses LOWERCASE folder + bare "{number}.png".
// Mirrors the Worker's own `buildItalianCardKeyCandidates`, which tries both
// folder cases and both filename shapes for exactly this reason.
function deriveKeyCandidates(cardId) {
  const match = CARD_ID_RE.exec(cardId.trim());
  if (!match) return [];
  const setUpper = match[1].toUpperCase();
  const setLower = match[1].toLowerCase();
  const number = match[2];
  const seen = new Set();
  const candidates = [];
  const push = (folder, base) => {
    const srcKey = `it/${folder}/${base}.png`;
    if (seen.has(srcKey)) return;
    seen.add(srcKey);
    candidates.push({ srcKey, destKey: `it/${folder}/${base}.webp` });
  };
  for (const folder of [setUpper, setLower]) {
    push(folder, `${setUpper}_IT_${number}`);
    push(folder, number);
  }
  return candidates;
}

async function loadProgress() {
  const done = new Set();
  if (!existsSync(progressFile)) return done;
  const text = await readFile(progressFile, 'utf8');
  for (const line of text.split('\n')) {
    if (!line.trim()) continue;
    try {
      const entry = JSON.parse(line);
      if (entry.ok) done.add(entry.cardId);
    } catch { /* ignore malformed line */ }
  }
  return done;
}

const wranglerBin = path.join(workerRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'wrangler.cmd' : 'wrangler');

// Transient wrangler/workerd failures worth retrying: native crashes (exit
// codes outside normal range), SQLite lock contention under concurrency,
// and generic network blips. "key does not exist" is permanent -> no retry.
function isTransientWranglerError(message) {
  return /SQLITE_BUSY|database is locked|ECONNRESET|ETIMEDOUT|fetch failed|network|kj::Exception|Fatal uncaught|5\d\d:|gateway timeout|internal error|failed to fetch/i.test(message)
    && !/specified key does not exist/i.test(message);
}

const MAX_ATTEMPTS = 3;
const RETRY_DELAY_MS = 1500;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function runWrangler(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(wranglerBin, args, {
      cwd: workerRoot,
      shell: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let stderr = '';
    child.stderr.on('data', (d) => { stderr += d.toString(); });
    child.on('close', (code) => {
      if (code === 0) resolve();
      else reject(new Error(`wrangler ${args.join(' ')} exited ${code}: ${stderr.slice(0, 500)}`));
    });
  });
}

const KEY_NOT_FOUND_RE = /specified key does not exist/i;

async function processCardOnce(cardId) {
  const candidates = deriveKeyCandidates(cardId);
  if (candidates.length === 0) return { cardId, ok: false, permanent: true, error: 'unparseable cardId' };

  const base = cardId.replace(/\.(png|webp|jpe?g)$/i, '');
  const localPng = path.join(tmpDir, `${base}.png`);
  const localWebp = path.join(tmpDir, `${base}.webp`);

  try {
    let downloaded = null;
    let lastNotFoundError = null;
    for (const candidate of candidates) {
      try {
        await runWrangler(['r2', 'object', 'get', `${BUCKET}/${candidate.srcKey}`, '--file', localPng, '--remote']);
        downloaded = candidate;
        break;
      } catch (error) {
        const message = String(error?.message ?? error);
        if (KEY_NOT_FOUND_RE.test(message)) {
          lastNotFoundError = message;
          continue; // try next naming convention
        }
        throw error; // transient/unexpected error: surface for retry logic
      }
    }
    if (!downloaded) {
      return {
        cardId,
        ok: false,
        permanent: true,
        error: `no candidate key found (tried: ${candidates.map((c) => c.srcKey).join(', ')}): ${lastNotFoundError}`,
      };
    }

    const beforeStat = await stat(localPng);
    if (beforeStat.size === 0) throw new Error('downloaded PNG is empty');

    await sharp(localPng).webp({ quality: WEBP_QUALITY }).toFile(localWebp);
    const afterStat = await stat(localWebp);
    if (afterStat.size === 0) throw new Error('encoded WebP is empty');

    await runWrangler(['r2', 'object', 'put', `${BUCKET}/${downloaded.destKey}`, '--file', localWebp, '--remote']);

    return {
      cardId,
      ok: true,
      srcKey: downloaded.srcKey,
      destKey: downloaded.destKey,
      beforeBytes: beforeStat.size,
      afterBytes: afterStat.size,
    };
  } catch (error) {
    const message = String(error?.message ?? error);
    return { cardId, ok: false, permanent: !isTransientWranglerError(message), error: message };
  } finally {
    await rm(localPng, { force: true });
    await rm(localWebp, { force: true });
  }
}

async function processCard(cardId) {
  let last = null;
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
    last = await processCardOnce(cardId);
    if (last.ok || last.permanent) return last;
    if (attempt < MAX_ATTEMPTS) await sleep(RETRY_DELAY_MS * attempt);
  }
  return last;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  await mkdir(tmpDir, { recursive: true });

  const raw = (await readFile(args.catalog, 'utf8')).replace(/^﻿/, '');
  const catalog = JSON.parse(raw);
  const cards = Array.isArray(catalog) ? catalog : catalog.cards;

  const uniqueCardIds = [...new Set(cards.map((c) => c.cardId).filter(Boolean))];
  const alreadyDone = await loadProgress();
  let pending = uniqueCardIds.filter((id) => !alreadyDone.has(id));
  if (args.limit) pending = pending.slice(0, args.limit);

  console.log(`Carte totali nel catalogo: ${uniqueCardIds.length}`);
  console.log(`Gia' completate (da run precedenti): ${alreadyDone.size}`);
  console.log(`Da processare in questa run: ${pending.length} (concorrenza=${args.concurrency})`);

  let completed = 0;
  let failed = 0;
  let bytesBefore = 0;
  let bytesAfter = 0;
  const failures = [];

  let cursor = 0;
  async function worker() {
    while (cursor < pending.length) {
      const cardId = pending[cursor++];
      const result = await processCard(cardId);
      await appendFile(progressFile, JSON.stringify(result) + '\n', 'utf8');
      if (result.ok) {
        completed += 1;
        bytesBefore += result.beforeBytes;
        bytesAfter += result.afterBytes;
      } else {
        failed += 1;
        failures.push(result);
      }
      const done = completed + failed;
      if (done % 25 === 0 || done === pending.length) {
        console.log(`[${done}/${pending.length}] ok=${completed} fail=${failed}`);
      }
    }
  }

  await Promise.all(Array.from({ length: args.concurrency }, () => worker()));

  console.log('\n=== Riepilogo ===');
  console.log(`Convertite con successo: ${completed}`);
  console.log(`Fallite: ${failed}`);
  if (completed > 0) {
    const mb = (n) => (n / 1024 / 1024).toFixed(2);
    console.log(`Peso prima (PNG): ${mb(bytesBefore)} MB`);
    console.log(`Peso dopo (WebP): ${mb(bytesAfter)} MB`);
    console.log(`Risparmio: ${(100 * (1 - bytesAfter / bytesBefore)).toFixed(1)}%`);
  }
  if (failures.length > 0) {
    console.log('\nCarte fallite (prime 20):');
    for (const f of failures.slice(0, 20)) console.log(`  ${f.cardId}: ${f.error}`);
  }

  await rm(tmpDir, { recursive: true, force: true });
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
