#!/usr/bin/env node
// One-time backfill of set logos into R2, sourced from TCGdex (same source/id
// mapping as the rarity and release-date backfills -- see MIGRATION_PLAN.md
// M4.6). No Worker or D1 changes needed: the Worker already serves
// `GET /sets/{SETCODE}/image` by checking R2 for `it/{SETCODE}/logo.png`
// (among a few legacy filename variants) before falling back to PokeWallet
// (see buildItalianSetLogoCandidates / handleItalianR2AssetRequest in
// src/index.ts) -- this script just needed to actually populate that R2 path.
//
// The SETCODE used here must match exactly what the Android client requests
// (PokeTcgRepository.buildSetImageUrl(baseRawSetCode)): the small manual
// override map (preferredBaseSetCodeForItalianExpansion in
// PokeTcgRepository.kt) takes priority over D1's expansions.base_set_code
// (schema/003) -- duplicated below since it's tiny and Kotlin-only.
//
// Usage:
//   node scripts/backfill-set-logos-tcgdex.mjs <expansionId> [--apply]
//   node scripts/backfill-set-logos-tcgdex.mjs --all [--apply]

import { writeFile, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { TCGDEX_ID_OVERRIDES } from './lib/tcgdex-set-id-map.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.logo-tmp');
const BUCKET = 'pokevault-images';
const CONCURRENCY = 6;

const wranglerBin = path.join(workerRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'wrangler.cmd' : 'wrangler');

// Undici espansioni non hanno logo su TCGdex (promo, sotto-collezioni e
// qualche set recente): senza ripiego restavano col placeholder in app, ed e'
// quello che l'utente vedeva il 15/09/2026. Il ripiego e' il set INGLESE
// corrispondente su PokeWallet, chiesto al Worker per codice.
//
// Il codice va scelto a mano e verificato guardando l'immagine, perche' quella
// che PokeWallet serve per un codice e' nella lingua in cui quel prodotto e'
// uscito: chiedendo "XY10" si ottiene il logo giapponese (めざめる超王), mentre
// il set inglese "XY - Fates Collide" sta sotto "FCO". Tutti quelli qui sotto
// sono stati aperti e controllati uno per uno.
const WORKER_BASE = 'https://pokevault-proxy.pokevault-emanu.workers.dev';
const LOGO_FALLBACK_POKEWALLET_CODE = {
  sm35: 'SHL',             // Shining Legends
  sm75: 'DRM',             // Dragon Majesty
  sma: 'HIF:SV',           // Hidden Fates: Shiny Vault
  xy10: 'FCO',             // XY - Fates Collide
  cel25c: 'CCC',           // Celebrations: Classic Collection
  swsh12tg: 'SWSH12: TG',  // Silver Tempest Trainer Gallery
  swsh12pt5gg: 'CRZ:GG',   // Crown Zenith: Galarian Gallery
  sv05: 'TEF',             // Temporal Forces
  mep: 'MEP',              // Mega Evolution Promos
  svp: 'SVP',              // Scarlet & Violet Promos
  // "Carta Alternatica A Gialla": sei carte alternative dell'era XY, senza un
  // logo proprio. PokeWallet sotto XYA ha un mazzo giapponese (メガバトルデッキ60)
  // e il codice "XY" da solo non risolve (409), quindi si riusa il marchio XY
  // che abbiamo gia' su R2 per il set base.
  xya: `${WORKER_BASE}/sets/XY1/image?source=ita`,
};

// Mirrors PokeTcgRepository.kt's preferredBaseSetCodeForItalianExpansion --
// keep in sync if that map changes.
const CLIENT_SET_CODE_OVERRIDES = {
  me01: 'MEG',
  me02: 'PFL',
  me03: 'ME03',
  me04: 'CRI',
  me2pt5: 'ASC',
  mep: 'MEP',
  sv01: 'SVI',
  sv02: 'PAL',
  sv03: 'OBF',
  sv04: 'PAR',
  sv05: 'TEF',
  sv06: 'TWM',
  sv07: 'SCR',
  sv08: 'SSP',
  sv09: 'JTG',
  sv10: 'DRI',
  zsv10pt5: 'BLK',
  rsv10pt5: 'WHT',
  sv3pt5: 'MEW',
  sv4pt5: 'PAF',
  sv6pt5: 'SFA',
  sv8pt5: 'PRE',
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

async function runD1Query(sql) {
  const out = await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--json', '--command', `"${sql.replace(/"/g, '\\"')}"`]);
  const jsonStart = out.indexOf('[');
  return JSON.parse(jsonStart >= 0 ? out.slice(jsonStart) : out);
}

async function fetchExpansions(targetIds) {
  const parsed = await runD1Query('SELECT id, base_set_code FROM expansions ORDER BY id');
  const all = parsed[0]?.results ?? [];
  if (!targetIds) return all;
  const set = new Set(targetIds);
  return all.filter((r) => set.has(r.id));
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

async function main() {
  const args = process.argv.slice(2);
  const apply = args.includes('--apply');
  const all = args.includes('--all');
  const targetIds = all ? null : args.filter((a) => a !== '--apply' && a !== '--all');

  if (!all && targetIds.length === 0) {
    throw new Error('Usage: node backfill-set-logos-tcgdex.mjs <expansionId> [--apply]  OR  --all [--apply]');
  }

  const rows = await fetchExpansions(targetIds);
  console.log(`${apply ? 'APPLY' : 'DRY RUN'} su ${rows.length} espansione/i\n`);

  await mkdir(tmpDir, { recursive: true });

  const results = await mapWithConcurrency(rows, CONCURRENCY, async (row) => {
    const setCode = CLIENT_SET_CODE_OVERRIDES[row.id] ?? row.base_set_code;
    if (!setCode) {
      console.warn(`[${row.id}] nessun base_set_code -- salto`);
      return { id: row.id, ok: false, error: 'no-set-code' };
    }

    const tcgdexId = TCGDEX_ID_OVERRIDES[row.id] ?? row.id;
    try {
      const setSummary = await fetch(`https://api.tcgdex.net/v2/en/sets/${tcgdexId}`).then((r) => (r.ok ? r.json() : null));
      const fallbackCode = LOGO_FALLBACK_POKEWALLET_CODE[row.id];
      const fallbackUrl = !fallbackCode
        ? null
        : (fallbackCode.startsWith('http')
          ? fallbackCode
          : `${WORKER_BASE}/sets/${encodeURIComponent(fallbackCode)}/image?v=logo-backfill`);
      // TCGdex per primo; il ripiego vale sia quando il logo non c'e' sia
      // quando c'e' nei metadati ma l'asset risponde 404 (capita: xy10).
      const candidati = [
        setSummary?.logo ? { url: `${setSummary.logo}.png`, label: `TCGdex ${tcgdexId}` } : null,
        fallbackUrl ? { url: fallbackUrl, label: `ripiego ${fallbackCode}` } : null,
      ].filter(Boolean);
      if (candidati.length === 0) {
        console.warn(`[${row.id}] nessun logo su TCGdex (${tcgdexId}) e nessun ripiego -- salto`);
        return { id: row.id, ok: false, error: 'no-logo' };
      }

      let buf = null;
      let sourceLabel = null;
      let lastStatus = null;
      for (const candidato of candidati) {
        const imgRes = await fetch(candidato.url);
        if (!imgRes.ok) {
          lastStatus = imgRes.status;
          console.warn(`[${row.id}] ${candidato.label} -> HTTP ${imgRes.status}`);
          continue;
        }
        buf = Buffer.from(await imgRes.arrayBuffer());
        sourceLabel = candidato.label;
        break;
      }
      if (!buf) {
        return { id: row.id, ok: false, error: `http-${lastStatus}` };
      }

      console.log(`[${row.id}] setCode=${setCode} <- ${sourceLabel} (${buf.length} byte)`);

      if (apply) {
        const localFile = path.join(tmpDir, `${row.id}.png`);
        const destKey = `it/${setCode}/logo.png`;
        await writeFile(localFile, buf);
        await runWrangler(['r2', 'object', 'put', `${BUCKET}/${destKey}`, '--file', localFile, '--remote']);
        await rm(localFile, { force: true });
      }

      return { id: row.id, ok: true, setCode };
    } catch (err) {
      console.error(`[${row.id}] errore:`, err.message);
      return { id: row.id, ok: false, error: err.message };
    }
  });

  const ok = results.filter((r) => r.ok).length;
  const failed = results.filter((r) => !r.ok);
  console.log('\n=== Riepilogo ===');
  console.log(`${ok}/${results.length} loghi ${apply ? 'caricati' : 'trovati'}`);
  if (failed.length > 0) {
    console.log(`Falliti (${failed.length}):`, failed.map((f) => `${f.id}(${f.error})`).join(', '));
  }

  await rm(tmpDir, { recursive: true, force: true });
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
