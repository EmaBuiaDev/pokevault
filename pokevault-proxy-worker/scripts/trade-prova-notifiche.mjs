#!/usr/bin/env node
// Prova delle notifiche (fase 3) in STAGING, con gli utenti della folla.
// Non guarda il telefono: guarda la coda (trade_notifications), cioe' cosa
// il server ha deciso di mandare, a chi, e quante volte.
//
//   node scripts/trade-prova-notifiche.mjs              eventi immediati, preferenze, token
//   node scripts/trade-prova-notifiche.mjs --cron       anche promemoria e "com'e' andata"
//                                                       (aspetta il cron dello staging, max 16 min)
//
// Senza la chiave FCM le notifiche finiscono con status 'no_fcm': e' giusto,
// la prova controlla che ci siano, non che arrivino. Alla fine rimette tutto
// com'era (proposte, notifiche, preferenze, token).

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
const CRON = process.argv.includes('--cron');
const start = Date.now();

function sql(query) {
  const wrangler = path.join(here, '..', 'node_modules', 'wrangler', 'bin', 'wrangler.js');
  const out = execFileSync(process.execPath, [wrangler, 'd1', 'execute', 'pokevault-trade-staging', '--env', 'staging', '--remote', '--json', '--command', query], {
    cwd: path.join(here, '..'), encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'],
  });
  return JSON.parse(out.slice(out.indexOf('[')))[0].results ?? [];
}
const quote = (value) => `'${String(value).replace(/'/g, "''")}'`;
async function login(name) {
  const res = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${API_KEY}`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email: `traderadar.test.${name}@pokevault.invalid`, password: PASSWORD, returnSecureToken: true }),
  }).then((r) => r.json());
  if (!res.idToken) throw new Error(`login ${name}`);
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
const note = (id) => sql(`SELECT * FROM trade_notifications WHERE id = ${quote(id)}`)[0];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
/** Le notifiche partono dopo la risposta (waitUntil): un attimo di pazienza. */
async function noteSoon(id) {
  for (let i = 0; i < 5; i++) { const n = note(id); if (n?.sent_at) return n; await sleep(1500); }
  return note(id);
}
const asItem = ({ key, variant, condition, language }) => ({ key, variant, condition, language, qty: 1 });

const users = [];
for (let i = 1; i <= 30; i++) users.push(await login(`folla${String(i).padStart(2, '0')}`));
const ids = new Map(sql(`SELECT uid, public_id, nickname FROM trade_profiles`).map((r) => [r.uid, r]));
for (const u of users) Object.assign(u, { id: ids.get(u.uid)?.public_id, nickname: ids.get(u.uid)?.nickname });

// Due che si vedono, senza proposte aperte fra loro, con carte da scambiare.
let A = null, B = null, give = null, take = null;
for (const a of users) {
  const ms = (await call(a, 'GET', '/v1/trade/matches')).data.matches ?? [];
  for (const m of ms) {
    const b = users.find((u) => u.id === m.id);
    if (!b) continue;
    const open = sql(`SELECT 1 AS x FROM trade_proposals WHERE status IN ('open','accepted','scheduled')
      AND ((from_uid = ${quote(a.uid)} AND to_uid = ${quote(b.uid)}) OR (from_uid = ${quote(b.uid)} AND to_uid = ${quote(a.uid)}))`);
    if (open.length) continue;
    const theirs = (await call(a, 'GET', `/v1/trade/users/${b.id}/haves`)).data.items ?? [];
    const mine = (await call(b, 'GET', `/v1/trade/users/${a.id}/haves`)).data.items ?? [];
    if (theirs.length && mine.length) { A = a; B = b; take = asItem(theirs[0]); give = asItem(mine[0]); break; }
  }
  if (A) break;
}
if (!A) throw new Error('nessuna coppia adatta');
console.log(`${A.nickname} -> ${B.nickname}`);

const created = [];
const tokens = [];
try {
  console.log('\nTelefoni');
  const fake = `prova-${Date.now()}:APA91b${'x'.repeat(40)}`;
  tokens.push(fake);
  let r = await call(B, 'PUT', '/v1/trade/push', { token: fake, lang: 'it' });
  check('registra il telefono', r.status === 200 && sql(`SELECT uid FROM trade_push_tokens WHERE token = ${quote(fake)}`)[0]?.uid === B.uid, JSON.stringify(r));
  r = await call(A, 'PUT', '/v1/trade/push', { token: fake, lang: 'en' });
  check('lo stesso telefono con un altro account passa a lui', sql(`SELECT uid, lang FROM trade_push_tokens WHERE token = ${quote(fake)}`)[0]?.uid === A.uid);
  r = await call(A, 'PUT', '/v1/trade/push', { token: 'corto' });
  check('token non valido: 400', r.status === 400);
  r = await call(A, 'DELETE', '/v1/trade/push', { token: fake });
  check('lo toglie', sql(`SELECT 1 AS x FROM trade_push_tokens WHERE token = ${quote(fake)}`).length === 0);

  console.log('\nEventi');
  r = await call(A, 'POST', '/v1/trade/proposals', { to: B.id, give: [give], take: [take] });
  const pid = r.data.id;
  created.push(pid);
  check('proposta creata', r.status === 201, JSON.stringify(r));
  let n = await noteSoon(`proposal:${pid}:1:${B.uid}`);
  check('B: "nuova proposta"', n?.kind === 'proposals' && n?.uid === B.uid, JSON.stringify(n));
  check('senza chiave FCM: no_fcm (non si accumula)', n?.status === 'no_fcm', n?.status);
  check('ad A (che l\'ha mandata) niente', sql(`SELECT 1 AS x FROM trade_notifications WHERE uid = ${quote(A.uid)} AND id LIKE ${quote(`%${pid}%`)}`).length === 0);
  const payload = JSON.parse(n?.payload ?? '{}');
  check('testo con nickname e carte', payload.args?.nick === A.nickname && payload.args?.give === 1 && payload.args?.take === 1, n?.payload);

  r = await call(B, 'POST', `/v1/trade/proposals/${pid}/accept`, {});
  n = await noteSoon(`accepted:${pid}:${A.uid}`);
  check('A: "ha accettato"', r.status === 200 && n?.uid === A.uid, JSON.stringify(r));

  const spots = (await call(A, 'GET', `/v1/trade/proposals/${pid}/spots`)).data.spots ?? [];
  const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
  if (spots.length) {
    r = await call(A, 'POST', `/v1/trade/proposals/${pid}/meeting`, { spot: spots[0].id, slots: [{ day: tomorrow, time: '17:30' }] });
    const m = sql(`SELECT id FROM trade_notifications WHERE id LIKE ${quote(`meeting:${pid}:%`)}`);
    check('B: "propone un appuntamento"', r.status === 200 && m.length === 1, JSON.stringify(r));
    r = await call(B, 'POST', `/v1/trade/proposals/${pid}/meeting/confirm`, { slot: 0 });
    n = await noteSoon(`confirmed:${pid}:${tomorrow}T17:30:${A.uid}`);
    check('A: "appuntamento confermato"', r.status === 200 && n?.uid === A.uid, JSON.stringify(r));
    r = await call(B, 'POST', `/v1/trade/proposals/${pid}/meeting/confirm`, { slot: 0 });
    check('confermare due volte non duplica', sql(`SELECT COUNT(*) AS n FROM trade_notifications WHERE id LIKE ${quote(`confirmed:${pid}:%`)}`)[0].n === 1);
  } else {
    check('luoghi per l\'appuntamento', false, 'nessun luogo nella zona: salto la parte appuntamento');
  }

  console.log('\nPreferenze');
  r = await call(B, 'PUT', '/v1/trade/notify', { meetings: false });
  check('B spegne "appuntamenti"', r.status === 200 && r.data.meetings === false && r.data.proposals === true && r.data.wants === null, JSON.stringify(r.data));
  const prof = await call(B, 'GET', '/v1/trade/profile');
  check('il profilo le riporta', prof.data.notify?.meetings === false, JSON.stringify(prof.data.notify));
  r = await call(A, 'POST', `/v1/trade/proposals/${pid}/cancel`, {});
  await sleep(3000);
  check('A annulla l\'accordo: a B non arriva (categoria spenta)', r.status === 200 && !note(`cancelled:${pid}:${B.uid}`), JSON.stringify(r));

  if (CRON) {
    console.log('\nCron (promemoria e "com\'e\' andata")');
    const now = Date.now();
    const fmt = (ms) => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Rome', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(ms)).split(' ');
    const [soonDay, soonTime] = fmt(now + 60 * 60000);
    const [pastDay, pastTime] = fmt(now - 3 * 60 * 60000);
    const soon = crypto.randomUUID(), past = crypto.randomUUID();
    created.push(soon, past);
    const ins = (id, day, time) => sql(`INSERT INTO trade_proposals (id, from_uid, to_uid, status, revision, turn_uid, created_at, updated_at, meet_status, meet_slot)
      VALUES (${quote(id)}, ${quote(A.uid)}, ${quote(B.uid)}, 'scheduled', 1, ${quote(B.uid)}, ${now}, ${now}, 'confirmed', ${quote(JSON.stringify({ day, time, part: 'afternoon' }))})`);
    ins(soon, soonDay, soonTime);
    ins(past, pastDay, pastTime);
    // B ha "appuntamenti" spenti ma i promemoria accesi: categorie separate.
    console.log('  aspetto il cron (ogni 15 minuti)...');
    let got = false;
    for (let i = 0; i < 17 && !got; i++) {
      await sleep(60000);
      got = !!note(`reminder:${soon}:${soonDay}T${soonTime}:${A.uid}`);
    }
    check('promemoria ad A', got);
    check('promemoria a B', !!note(`reminder:${soon}:${soonDay}T${soonTime}:${B.uid}`));
    check('"com\'e\' andata" ad A e a B', !!note(`after:${past}:${A.uid}`) && !!note(`after:${past}:${B.uid}`));
    check('nessun "com\'e\' andata" per l\'appuntamento non ancora passato', !note(`after:${soon}:${A.uid}`));
  }
} finally {
  const all = users.map((u) => quote(u.uid)).join(', ');
  if (created.length) {
    const list = created.map(quote).join(', ');
    sql(`DELETE FROM trade_proposal_items WHERE proposal_id IN (${list})`);
    sql(`DELETE FROM trade_proposals WHERE id IN (${list})`);
  }
  sql(`DELETE FROM trade_notifications WHERE created_at >= ${start} AND uid IN (${all})`);
  sql(`UPDATE trade_profiles SET notify_prefs = NULL WHERE uid IN (${quote(A.uid)}, ${quote(B.uid)})`);
  if (tokens.length) sql(`DELETE FROM trade_push_tokens WHERE token IN (${tokens.map(quote).join(', ')})`);
  console.log(`\nPulito. ${failures === 0 ? 'Tutto come previsto.' : `${failures} controlli NON passati.`}`);
}
