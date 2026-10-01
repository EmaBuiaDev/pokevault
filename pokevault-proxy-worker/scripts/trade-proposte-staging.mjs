#!/usr/bin/env node
// Proposte di TradeRadar in STAGING (fase 2a).
//
//   node scripts/trade-proposte-staging.mjs --prova
//       giro completo fra Test Luca e Test Giulia: proposta, controproposta,
//       accettazione e gli errori attesi; alla fine ritira tutto.
//
//   node scripts/trade-proposte-staging.mjs --as <utente> [--accept | --decline | --counter | --cancel]
//       fa rispondere un utente finto (luca, giulia, folla01..folla30) alla
//       proposta piu' recente in cui tocca a lui; senza azione elenca le sue.
//       --counter: toglie l'ultima carta che darebbe lui, o se ne da' una sola
//       chiede una carta in piu' all'altro.
//
// Account email/password del progetto Firebase pokevault-staging.

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

const short = (items) => items.map((i) => `${i.name ?? i.key}${i.qty > 1 ? ` x${i.qty}` : ''}`).join(', ') || '-';
const asItem = ({ key, variant, condition, language, qty }) => ({ key, variant, condition, language, qty });

// ── Tante proposte verso una persona ────────────────────────────────────────
// --manda-a <id pubblico> [--quanti 10] [--ritirate 3]: folla01.. mandano
// ciascuno una proposta (1-2 carte loro contro 1-2 di quella persona); le
// ultime --ritirate le ritirano subito, cosi' finiscono fra le chiuse.
if (arg('--manda-a')) {
  const to = arg('--manda-a');
  const quanti = Number(arg('--quanti') ?? 10);
  const ritirate = Number(arg('--ritirate') ?? 3);
  let sent = 0;
  for (let i = 1; i <= quanti; i++) {
    const label = `folla${String(i).padStart(2, '0')}`;
    const tok = await token(label);
    const { data: theirs } = await call(tok, 'GET', `/v1/trade/users/${to}/haves`);
    const { data: mine } = await call(tok, 'GET', '/v1/trade/haves');
    const wanted = (theirs.items ?? []).slice(0, 1 + (i % 2));
    const offered = (mine.items ?? []).slice(i % 3, (i % 3) + 1 + ((i + 1) % 2));
    if (wanted.length === 0 || offered.length === 0) { console.log(`${label}: niente da proporre`); continue; }
    const res = await call(tok, 'POST', '/v1/trade/proposals', {
      to,
      give: offered.map((h) => asItem({ ...h, qty: 1 })),
      take: wanted.map((h) => asItem({ ...h, qty: 1 })),
    });
    if (res.status !== 201) { console.log(`${label}: ${res.status} ${JSON.stringify(res.data)}`); continue; }
    sent++;
    const withdraw = i > quanti - ritirate;
    if (withdraw) await call(tok, 'POST', `/v1/trade/proposals/${res.data.id}/cancel`);
    console.log(`${label}: da' ${offered.map((h) => h.key).join(', ')} per ${wanted.map((h) => h.key).join(', ')}${withdraw ? '  (ritirata)' : ''}`);
  }
  console.log(`\nMandate ${sent} proposte.`);
  process.exit(0);
}

// ── Rispondere come un utente finto ─────────────────────────────────────────
if (arg('--as')) {
  const label = arg('--as');
  const tok = await token(label);
  const { data } = await call(tok, 'GET', '/v1/trade/proposals');
  const list = data.proposals ?? [];
  const action = ['--accept', '--decline', '--counter', '--cancel'].find((a) => args.includes(a));
  if (!action) {
    for (const p of list) {
      console.log(`${p.id.slice(0, 8)}  ${p.status.padEnd(9)} rev ${p.revision}  ${p.myTurn ? 'TOCCA A ME' : '          '}  con ${p.counterpart.nickname}`);
      console.log(`          do: ${short(p.give)}   ricevo: ${short(p.take)}`);
    }
    if (list.length === 0) console.log('Nessuna proposta.');
    process.exit(0);
  }
  const target = action === '--cancel'
    ? list.find((p) => (p.status === 'open' && !p.myTurn) || p.status === 'accepted')
    : list.find((p) => p.myTurn);
  if (!target) { console.log('Nessuna proposta su cui agire.'); process.exit(1); }

  let body;
  if (action === '--counter') {
    let give = target.give.map(asItem);
    let take = target.take.map(asItem);
    if (give.length > 1) give = give.slice(0, -1);
    else {
      const { data: theirs } = await call(tok, 'GET', `/v1/trade/users/${target.counterpart.id}/haves`);
      const extra = (theirs.items ?? []).find((h) => !take.some((t) => t.key === h.key));
      if (extra) take = [...take, asItem({ ...extra, qty: 1 })];
    }
    body = { give, take };
  }
  const res = await call(tok, 'POST', `/v1/trade/proposals/${target.id}/${action.slice(2)}`, body);
  console.log(`${label} ${action.slice(2)} su ${target.id.slice(0, 8)} (con ${target.counterpart.nickname}) -> ${res.status} ${JSON.stringify(res.data)}`);
  process.exit(res.status < 300 ? 0 : 1);
}

// ── Prova completa ──────────────────────────────────────────────────────────
if (!args.includes('--prova')) {
  console.log('Uso: --prova, oppure --as <utente> [--accept|--decline|--counter|--cancel]');
  process.exit(1);
}

let failures = 0;
const check = (label, ok, detail = '') => {
  console.log(`${ok ? 'OK  ' : 'NO  '} ${label}${detail ? ` — ${detail}` : ''}`);
  if (!ok) failures++;
};

const luca = await token('luca');
const giulia = await token('giulia');

// Pulizia di giri precedenti: niente proposte aperte fra i due.
for (const tok of [luca, giulia]) {
  const { data } = await call(tok, 'GET', '/v1/trade/proposals');
  for (const p of data.proposals ?? []) {
    if (p.counterpart.nickname !== 'Test Luca' && p.counterpart.nickname !== 'Test Giulia') continue;
    if (p.status === 'open' && p.myTurn) await call(tok, 'POST', `/v1/trade/proposals/${p.id}/decline`);
    else if (p.status === 'open' || p.status === 'accepted') await call(tok, 'POST', `/v1/trade/proposals/${p.id}/cancel`);
  }
}

const { data: matches } = await call(luca, 'GET', '/v1/trade/matches');
const giuliaMatch = (matches.matches ?? []).find((m) => m.nickname === 'Test Giulia');
check('Giulia compare nei match di Luca con un id pubblico', /^[0-9a-f]{16}$/.test(giuliaMatch?.id ?? ''), giuliaMatch?.id);
const { data: lucaHaves } = await call(luca, 'GET', '/v1/trade/haves');
const { data: giuliaHaves } = await call(luca, 'GET', `/v1/trade/users/${giuliaMatch.id}/haves`);
check('le offerte di Giulia si leggono con il suo id', (giuliaHaves.items ?? []).length > 0, short(giuliaHaves.items ?? []));

const give = [asItem({ ...lucaHaves.items[0], qty: 1 })];
const take = [asItem({ ...giuliaHaves.items[0], qty: 1 })];

const bad = await call(luca, 'POST', '/v1/trade/proposals', { to: giuliaMatch.id, give, take: [{ ...take[0], qty: 99 }] });
check('chiedere piu\' copie di quelle offerte e\' rifiutato', bad.status === 409 && bad.data.error === 'not_offered', JSON.stringify(bad.data));
const empty = await call(luca, 'POST', '/v1/trade/proposals', { to: giuliaMatch.id, give, take: [] });
check('una proposta senza carte da ricevere e\' rifiutata', empty.status === 400, JSON.stringify(empty.data));

const created = await call(luca, 'POST', '/v1/trade/proposals', { to: giuliaMatch.id, give, take });
check('Luca manda la proposta', created.status === 201, JSON.stringify(created.data));
const id = created.data.id;
const twice = await call(luca, 'POST', '/v1/trade/proposals', { to: giuliaMatch.id, give, take });
check('una seconda proposta aperta fra gli stessi due e\' rifiutata', twice.status === 409 && twice.data.error === 'already_open');

const lucaView = (await call(luca, 'GET', '/v1/trade/proposals')).data.proposals.find((p) => p.id === id);
const giuliaView = (await call(giulia, 'GET', '/v1/trade/proposals')).data.proposals.find((p) => p.id === id);
check('per Luca: aperta, non tocca a lui, da\' la sua carta', lucaView?.status === 'open' && !lucaView.myTurn && lucaView.give[0]?.key === give[0].key);
check('per Giulia: tocca a lei, e le stesse carte sono rovesciate', giuliaView?.myTurn === true && giuliaView.take[0]?.key === give[0].key && giuliaView.give[0]?.key === take[0].key);
check('i nomi delle carte arrivano dal catalogo', !!giuliaView?.take[0]?.name, giuliaView?.take[0]?.name);

const early = await call(luca, 'POST', `/v1/trade/proposals/${id}/accept`);
check('Luca non puo\' accettare la sua proposta', early.status === 409 && early.data.error === 'not_your_turn');

const extra = (lucaHaves.items ?? []).find((h) => h.key !== give[0].key);
const counter = await call(giulia, 'POST', `/v1/trade/proposals/${id}/counter`, {
  give: giuliaView.give.map(asItem),
  take: [...giuliaView.take.map(asItem), ...(extra ? [asItem({ ...extra, qty: 1 })] : [])],
});
check('Giulia controproposta (chiede una carta in piu\')', counter.status === 200 && counter.data.revision === 2, JSON.stringify(counter.data));
const afterCounter = (await call(luca, 'GET', '/v1/trade/proposals')).data.proposals.find((p) => p.id === id);
check('ora tocca a Luca, revisione 2', afterCounter?.myTurn === true && afterCounter.revision === 2, `da' ${short(afterCounter?.give ?? [])}`);

const accepted = await call(luca, 'POST', `/v1/trade/proposals/${id}/accept`);
check('Luca accetta', accepted.status === 200 && accepted.data.status === 'accepted', JSON.stringify(accepted.data));
const done = (await call(giulia, 'GET', '/v1/trade/proposals')).data.proposals.find((p) => p.id === id);
check('per Giulia risulta accettata', done?.status === 'accepted');

const cancelled = await call(giulia, 'POST', `/v1/trade/proposals/${id}/cancel`);
check('dopo l\'accordo si puo\' ancora annullare', cancelled.status === 200);

console.log(failures === 0 ? '\nTutto ok.' : `\n${failures} controlli falliti.`);
process.exit(failures === 0 ? 0 : 1);
