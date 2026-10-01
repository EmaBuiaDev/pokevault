#!/usr/bin/env node
// Scambi finti fra gli utenti della folla, gia' chiusi e votati, in STAGING:
// servono a riempire la classifica e a vedere i livelli (fase 2e).
//
//   node scripts/trade-seed-classifica.mjs [--quanti 30]
//
// Ogni utente folla<k> scambia con i successivi (k % 8) + 1, in cerchio: chi
// ha piu' persone diverse sale di livello. Ogni scambio passa dal giro vero:
// proposta, accettazione, appuntamento oggi alle 07:00, "Scambio fatto" da
// entrambi, voto (quasi sempre 😊, a volte 😐 o 😞). Quattro utenti su cinque
// scelgono di comparire in classifica. Seme fisso: rilanciando si ottiene
// la stessa distribuzione (ma gli scambi si aggiungono).

import { execFileSync } from 'child_process';
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

let seed = 20261002;
function rand() {
  seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}

async function token(label) {
  const res = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${API_KEY}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email: `traderadar.test.${label}@pokevault.invalid`, password: 'TradeRadar-staging-1', returnSecureToken: true }),
  }).then((r) => r.json());
  if (!res.idToken) throw new Error(`login ${label}: ${JSON.stringify(res.error ?? res)}`);
  return res.idToken;
}
async function call(tok, method, route, body) {
  const res = await fetch(`${BASE}${route}`, {
    method,
    headers: { authorization: `Bearer ${tok}`, 'content-type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  return { status: res.status, data };
}
const asItem = ({ key, variant, condition, language }) => ({ key, variant, condition, language, qty: 1 });

// Gli id pubblici della folla: dal D1 di staging (l'API non li espone per nickname).
const wrangler = path.join(here, '..', 'node_modules', 'wrangler', 'bin', 'wrangler.js');
const out = execFileSync(process.execPath, [wrangler, 'd1', 'execute', 'pokevault-trade-staging', '--env', 'staging', '--remote', '--json', '--command',
  `SELECT nickname, public_id FROM trade_profiles WHERE nickname LIKE 'Test %'`], { cwd: path.join(here, '..'), encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
const publicIds = new Map(JSON.parse(out.slice(out.indexOf('[')))[0].results.map((r) => [r.nickname, r.public_id]));

const NAMES = [
  'Marco', 'Sara', 'Luca B', 'Giulia R', 'Matteo', 'Chiara', 'Andrea', 'Francesca', 'Davide', 'Elena',
  'Simone', 'Martina', 'Federico', 'Alessia', 'Lorenzo', 'Valentina', 'Riccardo', 'Giorgia', 'Stefano', 'Ilaria',
  'Paolo', 'Silvia', 'Nicola', 'Roberta', 'Fabio', 'Laura', 'Gabriele', 'Marta', 'Tommaso', 'Beatrice',
];
const users = [];
for (let i = 0; i < COUNT; i++) {
  const label = `folla${String(i + 1).padStart(2, '0')}`;
  users.push({ label, nickname: `Test ${NAMES[i % NAMES.length]}`, tok: await token(label) });
}
const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Rome' }).format(new Date());

async function freeHave(user) {
  const { data } = await call(user.tok, 'GET', '/v1/trade/haves');
  return (data.items ?? []).find((h) => h.qty - (h.reserved ?? 0) > 0);
}

let done = 0;
for (let k = 0; k < users.length; k++) {
  const partners = (k % 8) + 1;
  for (let step = 1; step <= partners; step++) {
    const a = users[k];
    const b = users[(k + step) % users.length];
    const bId = publicIds.get(b.nickname);
    const give = await freeHave(a);
    const { data: theirs } = await call(a.tok, 'GET', `/v1/trade/users/${bId}/haves`);
    const take = (theirs.items ?? [])[0];
    if (!bId || !give || !take) { console.log(`${a.nickname} -> ${b.nickname}: niente da scambiare`); continue; }
    const created = await call(a.tok, 'POST', '/v1/trade/proposals', { to: bId, give: [asItem(give)], take: [asItem(take)] });
    if (created.status !== 201) { console.log(`${a.nickname} -> ${b.nickname}: ${created.status} ${JSON.stringify(created.data)}`); continue; }
    const id = created.data.id;
    await call(b.tok, 'POST', `/v1/trade/proposals/${id}/accept`);
    const { data: spotData } = await call(a.tok, 'GET', `/v1/trade/proposals/${id}/spots`);
    const spot = (spotData.spots ?? [])[Math.floor(rand() * Math.min(5, (spotData.spots ?? []).length))];
    if (!spot) { console.log(`${a.nickname} -> ${b.nickname}: nessun luogo`); await call(a.tok, 'POST', `/v1/trade/proposals/${id}/cancel`); continue; }
    await call(a.tok, 'POST', `/v1/trade/proposals/${id}/meeting`, { spot: spot.id, slots: [{ day: today, time: '07:00' }] });
    await call(b.tok, 'POST', `/v1/trade/proposals/${id}/meeting/confirm`, { slot: 0 });
    await call(a.tok, 'POST', `/v1/trade/proposals/${id}/done`);
    const closed = await call(b.tok, 'POST', `/v1/trade/proposals/${id}/done`);
    if (closed.data?.status !== 'done') { console.log(`${a.nickname} -> ${b.nickname}: non chiuso ${JSON.stringify(closed.data)}`); continue; }
    for (const voter of [a, b]) {
      const r = rand();
      const mood = r < 0.88 ? 'good' : r < 0.95 ? 'ok' : 'bad';
      const tags = mood === 'good' ? ['punctual', 'as_described', 'kind'].filter(() => rand() < 0.6) : ['late'];
      await call(voter.tok, 'POST', `/v1/trade/proposals/${id}/rate`, { mood, tags });
    }
    done++;
    process.stdout.write(`\r${done} scambi chiusi (ultimo: ${a.nickname} <-> ${b.nickname}, ${spot.name})          `);
  }
}
console.log('');
let optedIn = 0;
for (const user of users) {
  const optIn = rand() < 0.8;
  await call(user.tok, 'PUT', '/v1/trade/leaderboard/optin', { optIn });
  if (optIn) optedIn++;
}
console.log(`Fatto: ${done} scambi chiusi e votati; ${optedIn} utenti su ${users.length} in classifica.`);
