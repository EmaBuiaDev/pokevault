#!/usr/bin/env node
// Un avatar Pokemon a caso per gli utenti della folla, in STAGING: serve a
// vedere il podio della classifica con gli sprite (fase 2e).
//
//   node scripts/trade-avatar-folla.mjs [--quanti 30] [--nessuno]
//
// Passa dalla rotta vera PUT /v1/trade/avatar. Uno su tre e' animato (solo
// fino al 649, come per i Premium), uno su sei resta con l'iniziale.
// --nessuno toglie l'avatar a tutti.

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const here = path.dirname(fileURLToPath(import.meta.url));
const services = JSON.parse(fs.readFileSync(path.join(here, '../../app/src/staging/google-services.json'), 'utf8'));
if (services.project_info.project_id !== 'pokevault-staging') throw new Error('non e\' staging: mi fermo.');
const API_KEY = services.client[0].api_key[0].current_key;
const BASE = 'https://pokevault-trade-staging.pokevault-emanu.workers.dev';
const args = process.argv.slice(2);
const arg = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : null; };
const COUNT = Number(arg('--quanti') ?? 30);
const NONE = args.includes('--nessuno');

async function token(label) {
  const res = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${API_KEY}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email: `traderadar.test.${label}@pokevault.invalid`, password: 'TradeRadar-staging-1', returnSecureToken: true }),
  }).then((r) => r.json());
  if (!res.idToken) throw new Error(`login ${label}: ${JSON.stringify(res.error ?? res)}`);
  return res.idToken;
}

for (let i = 0; i < COUNT; i++) {
  const label = `folla${String(i + 1).padStart(2, '0')}`;
  const roll = Math.random();
  const body = NONE || roll < 1 / 6
    ? { avatar: null, animated: false }
    : roll < 0.5
      ? { avatar: 1 + Math.floor(Math.random() * 649), animated: true }
      : { avatar: 1 + Math.floor(Math.random() * 1025), animated: false };
  const res = await fetch(`${BASE}/v1/trade/avatar`, {
    method: 'PUT',
    headers: { authorization: `Bearer ${await token(label)}`, 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
  console.log(label, res.status, await res.text());
}
