#!/usr/bin/env node
// Prova dal vivo del server di TradeRadar in STAGING, con tre utenti finti.
//
//   node scripts/trade-seed-staging.mjs            prepara, controlla, ripulisce
//   node scripts/trade-seed-staging.mjs --keep     lascia i profili (per provare
//                                                   l'app contro utenti veri)
//   node scripts/trade-seed-staging.mjs --near <geohash5>
//                                                   mette A e B nella cella data
//                                                   e accanto (per stare vicino a te)
//
// Gli account sono email/password nel progetto Firebase **pokevault-staging**
// (chiave presa da app/src/staging/google-services.json): mai la produzione.
// Si ricreano o si riusano a ogni giro.
//
//   A (Milano)          ha doppioni me02:1, me02:2; cerca sv01:5 in wishlist
//   B (cella accanto)   ha doppioni sv01:5, me02:9; possiede me02:1
//   C (Roma, lontano)   ha doppione sv01:5: non deve comparire per A
//   In piu' A possiede 16 carte su 30 di 30th-c (oltre il 50%) e B ha un
//   doppione di 30th-c:20: per A deve essere "cercata" col motivo "set".
//
// Attesi, per A: un solo match (B), reciproco, con sv01:5 "wanted"
// (wishlist) da B, e me02:2 per B ("useful": B colleziona me02) mentre
// me02:1 B ce l'ha gia' e non deve comparire.

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

const args = process.argv.slice(2);
const keep = args.includes('--keep');
const nearIdx = args.indexOf('--near');
const near = nearIdx >= 0 ? args[nearIdx + 1] : null;

async function token(label) {
  const email = `traderadar.test.${label}@pokevault.invalid`;
  const password = 'TradeRadar-staging-1';
  const call = (op) => fetch(`https://identitytoolkit.googleapis.com/v1/accounts:${op}?key=${API_KEY}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password, returnSecureToken: true }),
  }).then((r) => r.json());
  let res = await call('signInWithPassword');
  if (!res.idToken) res = await call('signUp');
  if (!res.idToken) throw new Error(`account ${label}: ${JSON.stringify(res.error ?? res)}`);
  return res.idToken;
}

async function api(tok, method, route, body) {
  const res = await fetch(`${BASE}${route}`, {
    method,
    headers: { authorization: `Bearer ${tok}`, 'content-type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  if (!res.ok) throw new Error(`${method} ${route} -> ${res.status} ${text}`);
  return data;
}

// Celle: A a Milano, B nella cella accanto, C a Roma. Con --near, A e B
// vanno dove indicato (B nella cella stessa).
const cellA = near ?? 'u0nd9';
const cellB = near ?? 'u0ndd';
const cellC = 'sr2yk';

const users = {
  A: { cell: cellA, haves: [['me02:1', 1], ['me02:2', 1]], wants: ['sv01:5'], owned: ['me02:1', 'me02:2', 'me02:3', ...Array.from({ length: 16 }, (_, i) => `30th-c:${i + 1}`)] },
  B: { cell: cellB, haves: [['sv01:5', 1], ['me02:9', 2], ['30th-c:20', 1]], wants: [], owned: ['sv01:5', 'me02:9', 'me02:1', 'me02:4', '30th-c:20'] },
  C: { cell: cellC, haves: [['sv01:5', 1]], wants: ['me02:1'], owned: ['sv01:5'] },
};

let failures = 0;
function check(label, ok, detail = '') {
  console.log(`${ok ? 'OK  ' : 'NO  '} ${label}${detail ? ` — ${detail}` : ''}`);
  if (!ok) failures++;
}

const tokens = {};
for (const [name, user] of Object.entries(users)) {
  tokens[name] = await token(name.toLowerCase());
  await api(tokens[name], 'PUT', '/v1/trade/profile', {
    nickname: `Test ${name}`, geohash5: user.cell, adultConfirmed: true, collectionConsent: true,
  });
  await api(tokens[name], 'PUT', '/v1/trade/owned', { keys: user.owned, hash: `seed-${name}` });
  await api(tokens[name], 'PUT', '/v1/trade/haves', {
    items: user.haves.map(([key, qty]) => ({ key, qty, variant: 'Normal', condition: 'Near Mint', language: 'Italiano' })),
  });
  await api(tokens[name], 'PUT', '/v1/trade/wants', { items: user.wants.map((key) => ({ key, source: 'wishlist' })) });
}

const profileA = await api(tokens.A, 'GET', '/v1/trade/profile');
check('profilo A con i conteggi', profileA.haves === 2 && profileA.wants === 1 && profileA.owned === 19, JSON.stringify(profileA));

const { matches } = await api(tokens.A, 'GET', '/v1/trade/matches');
check('A vede un solo vicino (C a Roma escluso)', matches.length === 1, matches.map((m) => m.nickname).join(', '));
const b = matches[0] ?? { theyGive: [], iGive: [] };
check('il vicino e\' B', b.nickname === 'Test B');
check('match reciproco', b.mutual === true);
const sv015 = b.theyGive.find((i) => i.key === 'sv01:5');
check('B da\' sv01:5 come cercata (wishlist)', sv015?.level === 'wanted' && sv015?.reason === 'wishlist', JSON.stringify(sv015));
const me029 = b.theyGive.find((i) => i.key === 'me02:9');
check('B da\' me02:9 come utile (A colleziona me02)', me029?.level === 'useful', JSON.stringify(me029));
check('A non offre a B me02:1, che B ha gia\'', !b.iGive.some((i) => i.key === 'me02:1'));
const set20 = b.theyGive.find((i) => i.key === '30th-c:20');
check('B da\' 30th-c:20 come cercata: A ha oltre meta\' del set', set20?.level === 'wanted' && set20?.reason === 'set', JSON.stringify(set20));
const me022 = b.iGive.find((i) => i.key === 'me02:2');
check('A offre a B me02:2 come utile', me022?.level === 'useful', JSON.stringify(me022));

const { matches: forC } = await api(tokens.C, 'GET', '/v1/trade/matches');
check('C a Roma non vede nessuno', forC.length === 0);

await api(tokens.B, 'PUT', '/v1/trade/profile', {
  nickname: 'Test B', geohash5: cellB, adultConfirmed: true, collectionConsent: true, paused: true,
});
const { matches: afterPause } = await api(tokens.A, 'GET', '/v1/trade/matches');
check('B in pausa sparisce dai match di A', afterPause.length === 0);

if (!keep) {
  for (const name of Object.keys(users)) await api(tokens[name], 'DELETE', '/v1/trade/profile');
  const gone = await fetch(`${BASE}/v1/trade/profile`, { headers: { authorization: `Bearer ${tokens.A}` } });
  check('dopo la disattivazione il profilo non c\'e\' piu\'', gone.status === 404);
} else {
  await api(tokens.B, 'PUT', '/v1/trade/profile', {
    nickname: 'Test B', geohash5: cellB, adultConfirmed: true, collectionConsent: true, paused: false,
  });
  console.log('Profili lasciati (--keep).');
}

console.log(failures === 0 ? '\nTutto ok.' : `\n${failures} controlli falliti.`);
process.exit(failures === 0 ? 0 : 1);
