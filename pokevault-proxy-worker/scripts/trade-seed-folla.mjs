#!/usr/bin/env node
// Una folla di utenti finti attorno a una cella, in STAGING: serve a vedere
// come si comporta TradeRadar con tante persone vicine (vista per carta,
// scambi consigliati, ordinamento).
//
//   node scripts/trade-seed-folla.mjs --near <geohash5> [--count 30]
//                                       crea o aggiorna la folla e la lascia
//   node scripts/trade-seed-folla.mjs --clean [--count 30]
//                                       cancella profili e account
//
// Account email/password nel progetto Firebase **pokevault-staging**:
// traderadar.test.folla01..NN@pokevault.invalid, password TradeRadar-staging-1.
// Le carte sono vere (dal catalogo pubblico), scelte con un generatore a seme
// fisso: rilanciando si ottiene la stessa folla.
//
// Pensata per ema94 (cella sr60n): una parte della folla offre carte di
// swsh9 (set che colleziona), qualcuno me02:5 (nella sua wishlist), e una
// parte cerca 30th-c:1 e 30th-c:3 (che lui offre), cosi' ci sono reciproci.

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const here = path.dirname(fileURLToPath(import.meta.url));
const services = JSON.parse(fs.readFileSync(path.join(here, '../../app/src/staging/google-services.json'), 'utf8'));
if (services.project_info.project_id !== 'pokevault-staging') {
  throw new Error('google-services.json non e\' quello di staging: mi fermo.');
}
const API_KEY = services.client[0].api_key[0].current_key;
const BASE = 'https://pokevault-trade-staging.pokevault-emanu.workers.dev';
const CATALOG = 'https://pokevault-proxy.pokevault-emanu.workers.dev/ita/catalog.json';
const PASSWORD = 'TradeRadar-staging-1';

const args = process.argv.slice(2);
const arg = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : null; };
const clean = args.includes('--clean');
const near = arg('--near');
const count = Number(arg('--count') ?? 30);
if (!clean && !/^[0-9b-hjkmnp-z]{5}$/.test(near ?? '')) throw new Error('serve --near <geohash5>');

const NAMES = [
  'Marco', 'Sara', 'Luca B', 'Giulia R', 'Matteo', 'Chiara', 'Andrea', 'Francesca', 'Davide', 'Elena',
  'Simone', 'Martina', 'Federico', 'Alessia', 'Lorenzo', 'Valentina', 'Riccardo', 'Giorgia', 'Stefano', 'Ilaria',
  'Paolo', 'Silvia', 'Nicola', 'Roberta', 'Fabio', 'Laura', 'Gabriele', 'Marta', 'Tommaso', 'Beatrice',
];
const label = (i) => `folla${String(i + 1).padStart(2, '0')}`;

// ── Generatore a seme fisso (mulberry32) ────────────────────────────────────
let seed = 20261001;
function rand() {
  seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const pick = (list) => list[Math.floor(rand() * list.length)];
const pickMany = (list, n) => {
  const copy = [...list];
  const out = [];
  while (out.length < n && copy.length) out.push(copy.splice(Math.floor(rand() * copy.length), 1)[0]);
  return out;
};

// ── Geohash: le 8 celle attorno ─────────────────────────────────────────────
const B32 = '0123456789bcdefghjkmnpqrstuvwxyz';
function decode(hash) {
  let lat = [-90, 90], lon = [-180, 180], even = true;
  for (const c of hash) {
    const bits = B32.indexOf(c);
    for (let b = 4; b >= 0; b--) {
      const range = even ? lon : lat;
      const mid = (range[0] + range[1]) / 2;
      if ((bits >> b) & 1) range[0] = mid; else range[1] = mid;
      even = !even;
    }
  }
  return { lat: (lat[0] + lat[1]) / 2, lon: (lon[0] + lon[1]) / 2, dLat: lat[1] - lat[0], dLon: lon[1] - lon[0] };
}
function encode(lat, lon, len = 5) {
  let la = [-90, 90], lo = [-180, 180], even = true, bit = 0, ch = 0, out = '';
  while (out.length < len) {
    const range = even ? lo : la;
    const v = even ? lon : lat;
    const mid = (range[0] + range[1]) / 2;
    if (v >= mid) { ch |= 1 << (4 - bit); range[0] = mid; } else range[1] = mid;
    even = !even;
    if (++bit === 5) { out += B32[ch]; bit = 0; ch = 0; }
  }
  return out;
}
function around(hash) {
  const { lat, lon, dLat, dLon } = decode(hash);
  const cells = [];
  for (const y of [-1, 0, 1]) for (const x of [-1, 0, 1]) if (x || y) cells.push(encode(lat + y * dLat, lon + x * dLon));
  return cells;
}

// ── Rete ────────────────────────────────────────────────────────────────────
async function auth(op, email, extra = {}) {
  const res = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:${op}?key=${API_KEY}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password: PASSWORD, returnSecureToken: true, ...extra }),
  });
  return res.json();
}
async function token(name, create) {
  const email = `traderadar.test.${name}@pokevault.invalid`;
  let res = await auth('signInWithPassword', email);
  if (!res.idToken && create) res = await auth('signUp', email);
  return res.idToken ?? null;
}
async function api(tok, method, route, body) {
  const res = await fetch(`${BASE}${route}`, {
    method,
    headers: { authorization: `Bearer ${tok}`, 'content-type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok && !(method === 'DELETE' && res.status === 404)) throw new Error(`${method} ${route} -> ${res.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

// ── Pulizia ─────────────────────────────────────────────────────────────────
if (clean) {
  let removed = 0;
  for (let i = 0; i < count; i++) {
    const tok = await token(label(i), false);
    if (!tok) continue;
    await api(tok, 'DELETE', '/v1/trade/profile');
    await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${API_KEY}`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ idToken: tok }),
    });
    removed++;
  }
  console.log(`Cancellati ${removed} utenti della folla (profili TradeRadar e account).`);
  process.exit(0);
}

// ── Carte vere dal catalogo ─────────────────────────────────────────────────
const catalog = await fetch(CATALOG).then((r) => r.json());
const keysBySet = new Map();
for (const card of catalog) {
  const m = /^([A-Za-z0-9-]+)_IT_([A-Za-z0-9_]+)\.(png|webp|jpe?g)$/i.exec(card.cardId ?? '');
  if (!m) continue;
  const set = m[1].toLowerCase();
  const number = /^\d+$/.test(m[2]) ? String(parseInt(m[2], 10)) : m[2];
  if (!keysBySet.has(set)) keysBySet.set(set, []);
  keysBySet.get(set).push(`${set}:${number}`);
}
const POOL = ['swsh9', 'me02', 'sv01', 'sv02', 'me01', 'sv03', '30th'].filter((s) => keysBySet.has(s));
console.log(`Catalogo: ${catalog.length} carte; set usati: ${POOL.join(', ')}`);

const cells = [near, ...around(near)];
let made = 0;
for (let i = 0; i < count; i++) {
  // Sei su dieci nella tua cella, gli altri nelle otto attorno.
  const cell = rand() < 0.6 ? near : pick(cells.slice(1));
  const sets = pickMany(POOL, 2 + Math.floor(rand() * 3));
  if (rand() < 0.6 && !sets.includes('swsh9')) sets[0] = 'swsh9';
  const owned = new Set(sets.flatMap((s) => pickMany(keysBySet.get(s), 10 + Math.floor(rand() * 40))));
  if (rand() < 0.2) owned.add('me02:5');

  const ownedList = [...owned];
  const haves = pickMany(ownedList, 3 + Math.floor(rand() * 10)).map((key) => [key, 1 + Math.floor(rand() * 3)]);
  if (owned.has('me02:5') && !haves.some(([k]) => k === 'me02:5')) haves.push(['me02:5', 1]);

  const wants = [];
  if (rand() < 0.45) wants.push(pick(['30th-c:1', '30th-c:3']));
  for (const s of pickMany(POOL, 2)) {
    for (const key of pickMany(keysBySet.get(s), 2 + Math.floor(rand() * 5))) if (!owned.has(key)) wants.push(key);
  }

  const name = label(i);
  const tok = await token(name, true);
  if (!tok) throw new Error(`account ${name} non creato`);
  const nickname = `Test ${NAMES[i % NAMES.length]}${i >= NAMES.length ? ` ${Math.floor(i / NAMES.length) + 1}` : ''}`.slice(0, 20);
  await api(tok, 'PUT', '/v1/trade/profile', { nickname, geohash5: cell, adultConfirmed: true, collectionConsent: true });
  await api(tok, 'PUT', '/v1/trade/owned', { keys: ownedList, hash: `folla-${i}` });
  await api(tok, 'PUT', '/v1/trade/haves', {
    items: haves.map(([key, qty]) => ({ key, qty, variant: 'Normal', condition: pick(['Near Mint', 'Near Mint', 'Excellent']), language: 'Italiano' })),
  });
  await api(tok, 'PUT', '/v1/trade/wants', { items: [...new Set(wants)].map((key) => ({ key, source: 'wishlist' })) });
  made++;
  console.log(`${nickname.padEnd(20)} ${cell}  offre ${haves.length}, cerca ${new Set(wants).size}, possiede ${ownedList.length}`);
}
console.log(`\nFolla pronta: ${made} utenti attorno a ${near}. Per toglierla: --clean --count ${count}`);
