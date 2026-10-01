#!/usr/bin/env node
// I luoghi segnalati in TradeRadar ("Manca un negozio?"), in STAGING.
//
//   node scripts/trade-luoghi-staging.mjs
//       elenca le segnalazioni in verifica
//   node scripts/trade-luoghi-staging.mjs --approva <id> [--lat N --lon N] [--nome "..."] [--citta "..."] [--indirizzo "..."] [--tipo card_shop|comics|games|...]
//       la approva: da quel momento la vedono tutti (si possono correggere
//       coordinate, nome, citta', indirizzo e tipo)
//   node scripts/trade-luoghi-staging.mjs --rifiuta <id>
//       la cancella, se nessun appuntamento la usa
//
// <id> basta che sia l'inizio (es. "user:3937"). Legge e scrive il D1 di
// staging con wrangler, quindi serve essere loggati a Cloudflare.

import { execFileSync } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';

const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const arg = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : null; };
const KINDS = ['card_shop', 'comics', 'games', 'video_games', 'toys', 'mall', 'library', 'other'];

/**
 * Una query sul D1 di staging. Wrangler si chiama con node, senza shell: cosi'
 * apici e virgolette del SQL arrivano intatti. (Con --file wrangler non
 * restituisce le righe, solo un riepilogo.)
 */
function sql(query) {
  const wrangler = path.join(here, '..', 'node_modules', 'wrangler', 'bin', 'wrangler.js');
  const out = execFileSync(process.execPath, [wrangler, 'd1', 'execute', 'pokevault-trade-staging', '--env', 'staging', '--remote', '--json', '--command', query], {
    cwd: path.join(here, '..'),
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'ignore'],
  });
  return JSON.parse(out.slice(out.indexOf('[')))[0].results ?? [];
}
/** La cella geohash di 5 caratteri, come encode() in src/geohash.ts. */
function geohash5(lat, lon) {
  const B32 = '0123456789bcdefghjkmnpqrstuvwxyz';
  let la = [-90, 90], lo = [-180, 180], even = true, bit = 0, ch = 0, out = '';
  while (out.length < 5) {
    const range = even ? lo : la;
    const v = even ? lon : lat;
    const mid = (range[0] + range[1]) / 2;
    if (v >= mid) { ch |= 1 << (4 - bit); range[0] = mid; } else range[1] = mid;
    even = !even;
    if (++bit === 5) { out += B32[ch]; bit = 0; ch = 0; }
  }
  return out;
}
const quote = (value) => `'${String(value).replace(/'/g, "''")}'`;

function find(prefix) {
  const rows = sql(`SELECT * FROM trade_spots WHERE source = 'user' AND id LIKE ${quote(`${prefix}%`)}`);
  if (rows.length !== 1) {
    console.log(rows.length === 0 ? `Nessuna segnalazione con id ${prefix}.` : `${rows.length} segnalazioni iniziano per ${prefix}: serve un id piu' lungo.`);
    process.exit(1);
  }
  return rows[0];
}

if (arg('--approva')) {
  const spot = find(arg('--approva'));
  const sets = ['approved = 1', `updated_at = ${Date.now()}`];
  if (arg('--lat') && arg('--lon')) {
    const lat = Number(arg('--lat'));
    const lon = Number(arg('--lon'));
    sets.push(`lat = ${lat}`, `lon = ${lon}`, `geohash5 = ${quote(geohash5(lat, lon))}`);
  }
  if (arg('--nome')) sets.push(`name = ${quote(arg('--nome'))}`);
  if (arg('--citta')) sets.push(`city = ${quote(arg('--citta'))}`);
  if (arg('--indirizzo')) sets.push(`address = ${quote(arg('--indirizzo'))}`);
  if (arg('--tipo')) {
    if (!KINDS.includes(arg('--tipo'))) { console.log(`Tipo sconosciuto. Validi: ${KINDS.join(', ')}`); process.exit(1); }
    sets.push(`kind = ${quote(arg('--tipo'))}`);
  }
  sql(`UPDATE trade_spots SET ${sets.join(', ')} WHERE id = ${quote(spot.id)}`);
  console.log(`Approvato: ${arg('--nome') ?? spot.name} (${spot.city ?? '-'}). Ora lo vedono tutti.`);
  process.exit(0);
}

if (arg('--rifiuta')) {
  const spot = find(arg('--rifiuta'));
  const used = sql(`SELECT COUNT(*) AS n FROM trade_proposals WHERE meet_spot_id = ${quote(spot.id)}`)[0]?.n ?? 0;
  if (used > 0) { console.log(`${spot.name} e' usato da ${used} appuntamenti: non lo cancello.`); process.exit(1); }
  sql(`DELETE FROM trade_spots WHERE id = ${quote(spot.id)}`);
  console.log(`Rifiutato e cancellato: ${spot.name}.`);
  process.exit(0);
}

const pending = sql(
  `SELECT s.id, s.name, s.kind, s.city, s.address, s.lat, s.lon, p.nickname AS da,
          datetime(s.created_at / 1000, 'unixepoch') AS quando,
          (SELECT COUNT(*) FROM trade_proposals WHERE meet_spot_id = s.id) AS appuntamenti
   FROM trade_spots s LEFT JOIN trade_profiles p ON p.uid = s.added_by
   WHERE s.source = 'user' AND s.approved = 0 ORDER BY s.created_at`
);
if (pending.length === 0) console.log('Nessuna segnalazione in verifica.');
for (const s of pending) {
  console.log(`${s.id}\n   ${s.name} · ${s.kind} · ${[s.address, s.city].filter(Boolean).join(', ') || 'senza citta\''}`);
  console.log(`   segnalato da ${s.da ?? '?'} il ${s.quando} · ${s.lat.toFixed(5)}, ${s.lon.toFixed(5)} · in ${s.appuntamenti} appuntamenti`);
}
