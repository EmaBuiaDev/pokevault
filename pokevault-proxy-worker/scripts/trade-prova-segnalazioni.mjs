#!/usr/bin/env node
// Prova di segnala e blocca (fase 2f) in STAGING, dal punto di vista di chi
// lo usa male. Con gli utenti della folla:
//
//   - segnalazione senza un accordo vero: non conta;
//   - la stessa persona due volte: rifiutata;
//   - ritorsione (il segnalato segnala chi l'ha segnalato): non conta;
//   - tre persone con accordi veri: sospensione automatica di 14 giorni, e
//     il sospeso non vede match e non manda proposte;
//   - piu' di 5 segnalazioni in un giorno: rifiutate;
//   - blocco: spariscono a vicenda dai match, niente proposte ne' carte, le
//     proposte aperte si annullano ma non l'appuntamento gia' passato (che
//     non si puo' nemmeno ritirare);
//   - disattivare e riattivare il profilo non toglie la sospensione.
//
//   node scripts/trade-prova-segnalazioni.mjs
//
// Alla fine rimette tutto com'era (segnalazioni, blocchi, sospensioni, la
// proposta finta, l'account di prova).

import { execFileSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const here = path.dirname(fileURLToPath(import.meta.url));
const services = JSON.parse(fs.readFileSync(path.join(here, '../../app/src/staging/google-services.json'), 'utf8'));
if (services.project_info.project_id !== 'pokevault-staging') throw new Error('non e\' staging: mi fermo.');
const API_KEY = services.client[0].api_key[0].current_key;
const BASE = 'https://pokevault-trade-staging.pokevault-emanu.workers.dev';
const PASSWORD = 'TradeRadar-staging-1';
const start = Date.now();

function sql(query) {
  const wrangler = path.join(here, '..', 'node_modules', 'wrangler', 'bin', 'wrangler.js');
  const out = execFileSync(process.execPath, [wrangler, 'd1', 'execute', 'pokevault-trade-staging', '--env', 'staging', '--remote', '--json', '--command', query], {
    cwd: path.join(here, '..'), encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'],
  });
  return JSON.parse(out.slice(out.indexOf('[')))[0].results ?? [];
}
const quote = (value) => `'${String(value).replace(/'/g, "''")}'`;

async function auth(op, email) {
  const res = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:${op}?key=${API_KEY}`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password: PASSWORD, returnSecureToken: true }),
  });
  return res.json();
}
async function login(name, create = false) {
  const email = `traderadar.test.${name}@pokevault.invalid`;
  let res = await auth('signInWithPassword', email);
  if (!res.idToken && create) res = await auth('signUp', email);
  if (!res.idToken) throw new Error(`login ${name}: ${JSON.stringify(res.error ?? res)}`);
  return { label: name, tok: res.idToken, uid: res.localId };
}
async function call(user, method, route, body) {
  const res = await fetch(`${BASE}${route}`, {
    method, headers: { authorization: `Bearer ${user.tok}`, 'content-type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  return { status: res.status, data };
}

let failures = 0;
function check(what, ok, detail = '') {
  console.log(`${ok ? '  ok ' : '  NO '} ${what}${ok || !detail ? '' : `  -> ${detail}`}`);
  if (!ok) failures++;
}
const lastWeight = (from, to) =>
  sql(`SELECT weight FROM trade_reports WHERE reporter_uid = ${quote(from.uid)} AND target_uid = ${quote(to.uid)} ORDER BY created_at DESC LIMIT 1`)[0]?.weight;

// ── Chi fa cosa ──
const users = [];
for (let i = 1; i <= 30; i++) users.push(await login(`folla${String(i).padStart(2, '0')}`));
const byUid = new Map(users.map((u) => [u.uid, u]));
const profiles = sql(`SELECT uid, public_id, nickname, geohash5 FROM trade_profiles WHERE uid IN (${users.map((u) => quote(u.uid)).join(', ')})`);
for (const p of profiles) Object.assign(byUid.get(p.uid), { id: p.public_id, nickname: p.nickname, cell: p.geohash5 });
const deals = sql(`SELECT from_uid, to_uid, status FROM trade_proposals`);
const partners = new Map();
const touched = new Map();
for (const d of deals) {
  for (const [a, b] of [[d.from_uid, d.to_uid], [d.to_uid, d.from_uid]]) {
    (touched.get(a) ?? touched.set(a, new Set()).get(a)).add(b);
    if (d.status === 'done') (partners.get(a) ?? partners.set(a, new Set()).get(a)).add(b);
  }
}
const T = users.find((u) => [...(partners.get(u.uid) ?? [])].filter((x) => byUid.has(x)).length >= 4);
const [R1, R2, R3] = [...partners.get(T.uid)].filter((x) => byUid.has(x)).slice(0, 3).map((x) => byUid.get(x));
const N = users.find((u) => u !== T && !(touched.get(T.uid) ?? new Set()).has(u.uid));
console.log(`Segnalato: ${T.nickname}; con accordi veri: ${R1.nickname}, ${R2.nickname}, ${R3.nickname}; senza: ${N.nickname}`);

const extra = []; // id delle proposte finte da cancellare
let evasion = null;
try {
  // ── Segnalazioni ──
  console.log('\nSegnalazioni');
  let r = await call(N, 'POST', `/v1/trade/users/${T.id}/report`, { reason: 'behavior', note: 'prova', block: false });
  check('senza accordo: accettata', r.status === 200, JSON.stringify(r));
  check('senza accordo: non conta', lastWeight(N, T) === 0);
  check('la risposta non dice se conta', r.data && !('weight' in r.data) && !('suspended' in r.data));

  r = await call(R1, 'POST', `/v1/trade/users/${T.id}/report`, { reason: 'behavior', block: false });
  check('con accordo vero: conta', r.status === 200 && lastWeight(R1, T) === 1, JSON.stringify(r));
  r = await call(R1, 'POST', `/v1/trade/users/${T.id}/report`, { reason: 'scam', block: false });
  check('la stessa persona due volte: 409', r.status === 409, JSON.stringify(r));

  r = await call(T, 'POST', `/v1/trade/users/${R1.id}/report`, { reason: 'scam', block: false });
  check('ritorsione: accettata ma non conta', r.status === 200 && lastWeight(T, R1) === 0, JSON.stringify(r));

  r = await call(R2, 'POST', `/v1/trade/users/${T.id}/report`, { reason: 'scam', block: false });
  check('seconda persona: conta', r.status === 200 && lastWeight(R2, T) === 1);
  let p = await call(T, 'GET', '/v1/trade/profile');
  check('con due non e\' ancora sospeso', !p.data.suspendedUntil, JSON.stringify(p.data.suspendedUntil));

  r = await call(R3, 'POST', `/v1/trade/users/${T.id}/report`, { reason: 'fake_cards', block: false });
  p = await call(T, 'GET', '/v1/trade/profile');
  const days = p.data.suspendedUntil ? Math.round((p.data.suspendedUntil - Date.now()) / 86400000) : null;
  check('con tre: sospeso 14 giorni per segnalazioni', days === 14 && p.data.suspensionReason === 'reports', `${days} ${p.data.suspensionReason}`);
  const m = await call(T, 'GET', '/v1/trade/matches');
  check('il sospeso non vede match', m.data.suspended === true && m.data.matches.length === 0, JSON.stringify(m.data).slice(0, 100));
  r = await call(T, 'POST', '/v1/trade/proposals', { to: R1.id, give: [], take: [] });
  check('il sospeso non manda proposte', r.status === 403 && r.data.error === 'suspended', JSON.stringify(r));
  const visible = (await call(N, 'GET', '/v1/trade/matches')).data.matches.some((x) => x.id === T.id);
  check('gli altri non vedono il sospeso', !visible);

  // ── Troppe segnalazioni in un giorno ──
  console.log('\nLimite giornaliero');
  const others = users.filter((u) => ![T, N].includes(u)).slice(0, 5);
  const statuses = [];
  for (const o of others) statuses.push((await call(N, 'POST', `/v1/trade/users/${o.id}/report`, { reason: 'other', block: false })).status);
  check('dopo 5 al giorno: 429', statuses.slice(0, 4).every((s) => s === 200) && statuses[4] === 429, statuses.join(','));

  // ── Blocco ──
  console.log('\nBlocco');
  const free = users.filter((u) => ![T, N, R1, R2, R3].includes(u));
  let A = null, B = null;
  for (const a of free) {
    const ms = (await call(a, 'GET', '/v1/trade/matches')).data.matches ?? [];
    const b = ms.map((x) => users.find((u) => u.id === x.id)).find((u) => u && free.includes(u));
    if (b) { A = a; B = b; break; }
  }
  check('trovati due che si vedono nei match', !!A, '');
  if (A) {
    console.log(`  ${A.nickname} blocca ${B.nickname}`);
    const now = Date.now();
    const past = JSON.stringify({ day: '2026-09-30', time: '10:00', part: 'morning' });
    const ids = { open: crypto.randomUUID(), passed: crypto.randomUUID() };
    extra.push(ids.open, ids.passed);
    sql(`INSERT INTO trade_proposals (id, from_uid, to_uid, status, revision, turn_uid, created_at, updated_at)
         VALUES (${quote(ids.open)}, ${quote(B.uid)}, ${quote(A.uid)}, 'open', 1, ${quote(A.uid)}, ${now}, ${now})`);
    sql(`INSERT INTO trade_proposals (id, from_uid, to_uid, status, revision, turn_uid, created_at, updated_at, meet_status, meet_slot)
         VALUES (${quote(ids.passed)}, ${quote(A.uid)}, ${quote(B.uid)}, 'scheduled', 1, ${quote(B.uid)}, ${now}, ${now}, 'confirmed', ${quote(past)})`);
    r = await call(A, 'POST', `/v1/trade/proposals/${ids.passed}/cancel`);
    check('appuntamento passato: non si ritira', r.status === 409 && r.data.error === 'meeting_passed', JSON.stringify(r));

    r = await call(A, 'POST', `/v1/trade/users/${B.id}/block`);
    check('blocco fatto, una proposta annullata', r.status === 200 && r.data.cancelled === 1, JSON.stringify(r));
    const st = Object.fromEntries(sql(`SELECT id, status FROM trade_proposals WHERE id IN (${quote(ids.open)}, ${quote(ids.passed)})`).map((x) => [x.id, x.status]));
    check('la proposta aperta e\' annullata', st[ids.open] === 'cancelled', st[ids.open]);
    check('l\'appuntamento passato resta (per "non si e\' presentato")', st[ids.passed] === 'scheduled', st[ids.passed]);
    check('A non vede B', !(await call(A, 'GET', '/v1/trade/matches')).data.matches.some((x) => x.id === B.id));
    check('B non vede A', !(await call(B, 'GET', '/v1/trade/matches')).data.matches.some((x) => x.id === A.id));
    r = await call(B, 'POST', '/v1/trade/proposals', { to: A.id, give: [], take: [] });
    check('B non manda proposte ad A (come se A non ci fosse)', r.status === 404 && r.data.error === 'no_user', JSON.stringify(r));
    r = await call(B, 'GET', `/v1/trade/users/${A.id}/haves`);
    check('B non vede le carte di A', r.status === 404);
    r = await call(A, 'GET', '/v1/trade/blocks');
    check('A ritrova B fra i bloccati', r.data.items?.some((x) => x.id === B.id), JSON.stringify(r.data));
    r = await call(B, 'GET', '/v1/trade/blocks');
    check('B non sa di essere bloccato', !r.data.items?.some((x) => x.id === A.id));
    r = await call(A, 'DELETE', `/v1/trade/users/${B.id}/block`);
    check('sbloccato: B torna nei match di A', r.status === 200 && (await call(A, 'GET', '/v1/trade/matches')).data.matches.some((x) => x.id === B.id));
  }

  // ── Disattivare per sfuggire alla sospensione ──
  console.log('\nDisattivare e riattivare');
  evasion = await login('evasione', true);
  const profile = { nickname: 'Test Evasione', geohash5: T.cell, adultConfirmed: true, collectionConsent: true };
  await call(evasion, 'PUT', '/v1/trade/profile', profile);
  const until = Date.now() + 5 * 86400000;
  sql(`UPDATE trade_profiles SET suspended_until = ${until}, suspension_reason = 'admin' WHERE uid = ${quote(evasion.uid)}`);
  await call(evasion, 'DELETE', '/v1/trade/profile');
  check('disattivato: la sospensione resta da parte', sql(`SELECT 1 AS x FROM trade_sanctions WHERE uid = ${quote(evasion.uid)}`).length === 1);
  await call(evasion, 'PUT', '/v1/trade/profile', profile);
  p = await call(evasion, 'GET', '/v1/trade/profile');
  check('riattivato: ancora sospeso', p.data.suspendedUntil === until && p.data.suspensionReason === 'admin', JSON.stringify(p.data).slice(0, 160));
} finally {
  // ── Pulizia ──
  const all = [...users, ...(evasion ? [evasion] : [])].map((u) => quote(u.uid)).join(', ');
  sql(`DELETE FROM trade_reports WHERE created_at >= ${start} AND reporter_uid IN (${all})`);
  sql(`DELETE FROM trade_blocks WHERE created_at >= ${start} AND blocker_uid IN (${all})`);
  sql(`UPDATE trade_profiles SET suspended_until = NULL, suspension_reason = NULL WHERE uid = ${quote(T.uid)}`);
  if (extra.length) sql(`DELETE FROM trade_proposals WHERE id IN (${extra.map(quote).join(', ')})`);
  if (evasion) {
    await call(evasion, 'DELETE', '/v1/trade/profile');
    sql(`DELETE FROM trade_sanctions WHERE uid = ${quote(evasion.uid)}`);
    await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${API_KEY}`, {
      method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ idToken: evasion.tok }),
    });
  }
  console.log(`\nPulito. ${failures === 0 ? 'Tutto come previsto.' : `${failures} controlli NON passati.`}`);
}
