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

// ── Prova dell'appuntamento (fase 2b) ───────────────────────────────────────
// Luca propone a Giulia, Giulia accetta; poi carte riservate, luoghi,
// appuntamento proposto e confermato, ricerca e aggiunta di un luogo, e
// annullamento che libera le carte. Alla fine non resta niente di aperto.
if (args.includes('--prova-appuntamento')) {
  let failures = 0;
  const check = (label, ok, detail = '') => {
    console.log(`${ok ? 'OK  ' : 'NO  '} ${label}${detail ? ` — ${detail}` : ''}`);
    if (!ok) failures++;
  };
  const luca = await token('luca');
  const giulia = await token('giulia');
  for (const tok of [luca, giulia]) {
    const { data } = await call(tok, 'GET', '/v1/trade/proposals');
    for (const p of data.proposals ?? []) {
      if (!['Test Luca', 'Test Giulia'].includes(p.counterpart.nickname)) continue;
      if (p.status === 'open' && p.myTurn) await call(tok, 'POST', `/v1/trade/proposals/${p.id}/decline`);
      else if (['open', 'accepted', 'scheduled'].includes(p.status)) await call(tok, 'POST', `/v1/trade/proposals/${p.id}/cancel`);
    }
  }
  const { data: matches } = await call(luca, 'GET', '/v1/trade/matches');
  const giuliaId = (matches.matches ?? []).find((m) => m.nickname === 'Test Giulia')?.id;
  const { data: lucaHaves } = await call(luca, 'GET', '/v1/trade/haves');
  const { data: before } = await call(luca, 'GET', `/v1/trade/users/${giuliaId}/haves`);
  const card = before.items[0];
  const give = [asItem({ ...lucaHaves.items[0], qty: 1 })];
  const take = [asItem({ ...card, qty: card.qty })];
  const created = await call(luca, 'POST', '/v1/trade/proposals', { to: giuliaId, give, take });
  const id = created.data.id;
  const accepted = await call(giulia, 'POST', `/v1/trade/proposals/${id}/accept`);
  check('Giulia accetta la proposta di Luca', accepted.status === 200, `${card.name} x${card.qty}`);

  const { data: after } = await call(luca, 'GET', `/v1/trade/users/${giuliaId}/haves`);
  const left = (after.items ?? []).find((h) => asItem(h).key === card.key && h.variant === card.variant);
  check('le copie dell\'accordo spariscono dalle offerte di Giulia', !left, left ? `restano ${left.qty}` : 'riservate');
  const { data: giuliaOwn } = await call(giulia, 'GET', '/v1/trade/haves');
  const own = (giuliaOwn.items ?? []).find((h) => h.key === card.key && h.variant === card.variant);
  check('per Giulia restano in lista, segnate come riservate', own?.reserved === card.qty, JSON.stringify(own));
  const again = await call(luca, 'POST', '/v1/trade/proposals', { to: giuliaId, give, take: [{ ...take[0], qty: 1 }] });
  check('non si possono mettere in un\'altra proposta', again.status === 409 && again.data.error === 'not_offered', JSON.stringify(again.data));

  // Come fa il telefono: le zone che mancano si scaricano da Overpass e si mandano al server.
  let { data: spotData } = await call(luca, 'GET', `/v1/trade/proposals/${id}/spots`);
  // Overpass a volte e' occupato (504): come l'app, si riprova con una pausa.
  async function overpassFetch(query) {
    for (let attempt = 1; attempt <= 4; attempt++) {
      const res = await fetch('https://overpass-api.de/api/interpreter', {
        method: 'POST',
        headers: { 'user-agent': 'PokeVault-TradeRadar/1.0', accept: 'application/json', 'content-type': 'application/x-www-form-urlencoded' },
        body: `data=${encodeURIComponent(query)}`,
      });
      // Un timeout arriva come 200 con un remark e zero elementi: e' un fallimento.
      const data = res.ok ? await res.json() : null;
      if (data && !/error/i.test(data.remark ?? '')) return data;
      console.log(`     Overpass ${res.status}${data?.remark ? ` (${data.remark.slice(0, 60)})` : ''}, tentativo ${attempt}`);
      await new Promise((r) => setTimeout(r, 5000 * attempt));
    }
    return null;
  }
  for (const { cell, query } of spotData.missingCells ?? []) {
    const overpass = await overpassFetch(query);
    // Se Overpass non ha risposto non si manda niente: la zona resta da scaricare.
    if (!overpass) { check(`zona ${cell}: Overpass non risponde`, false); continue; }
    const stored = await call(luca, 'POST', '/v1/trade/spots/cell', { cell, elements: overpass.elements ?? [] });
    check(`zona ${cell}: ${overpass.elements?.length ?? 0} elementi da Overpass, tenuti dal server dopo il filtro`, stored.status === 200, JSON.stringify(stored.data));
  }
  if ((spotData.missingCells ?? []).length > 0) ({ data: spotData } = await call(luca, 'GET', `/v1/trade/proposals/${id}/spots`));
  check('dopo, nessuna zona da scaricare', (spotData.missingCells ?? []).length === 0);
  const spots = spotData.spots ?? [];
  check('ci sono luoghi a meta\' strada, da OpenStreetMap', spots.length > 0, spots.slice(0, 3).map((s) => `${s.name} (${s.kind}, ${s.distanceKm} km)`).join(' | '));

  const day = (n) => new Date(Date.now() + n * 86400000).toISOString().slice(0, 10);
  const badSlots = await call(luca, 'POST', `/v1/trade/proposals/${id}/meeting`, { spot: spots[0]?.id, slots: [{ day: day(40), part: 'morning' }] });
  check('una fascia oltre 21 giorni e\' rifiutata', badSlots.status === 400);
  const proposedMeeting = await call(luca, 'POST', `/v1/trade/proposals/${id}/meeting`, {
    spot: spots[0]?.id, slots: [{ day: day(2), part: 'afternoon' }, { day: day(3), part: 'morning' }],
  });
  check('Luca propone luogo e due fasce', proposedMeeting.status === 200);
  const selfConfirm = await call(luca, 'POST', `/v1/trade/proposals/${id}/meeting/confirm`, { slot: 0 });
  check('Luca non puo\' confermare da solo', selfConfirm.status === 409);
  const giuliaList = (await call(giulia, 'GET', '/v1/trade/proposals')).data.proposals.find((p) => p.id === id);
  check('per Giulia serve una sua mossa, con luogo e fasce', giuliaList?.actionNeeded === true && giuliaList.meeting.slots.length === 2, giuliaList?.meeting?.spot?.name);
  const confirmed = await call(giulia, 'POST', `/v1/trade/proposals/${id}/meeting/confirm`, { slot: 1 });
  check('Giulia sceglie la seconda fascia: appuntamento fissato', confirmed.status === 200 && confirmed.data.status === 'scheduled', JSON.stringify(confirmed.data.slot));
  const lucaList = (await call(luca, 'GET', '/v1/trade/proposals')).data.proposals.find((p) => p.id === id);
  check('per Luca risulta fissato', lucaList?.status === 'scheduled' && lucaList.meeting.status === 'confirmed' && lucaList.meeting.slot?.part === 'morning');

  const { data: found } = await call(luca, 'GET', `/v1/trade/spots/search?q=${encodeURIComponent('Star Shop')}&proposal=${id}`);
  const star = (found.results ?? []).find((r) => /napoli/i.test(r.city));
  check('"Manca un negozio?": la ricerca trova Star Shop a Napoli', !!star, star ? `${star.name} ${star.kind} ${star.distanceKm} km` : JSON.stringify(found));
  if (star) {
    const added = await call(luca, 'POST', '/v1/trade/spots', star);
    check('e lo si aggiunge come luogo', added.status === 201 && added.data.spot?.id === star.osmId, added.data.spot?.name);
  }
  const reported = await call(luca, 'POST', '/v1/trade/spots', { name: 'Fumetteria di prova', city: 'Portici' });
  check('una segnalazione senza coordinate resta in attesa', reported.data.spot?.pending === true);

  const cancelled = await call(giulia, 'POST', `/v1/trade/proposals/${id}/cancel`);
  const { data: freed } = await call(luca, 'GET', `/v1/trade/users/${giuliaId}/haves`);
  check('annullato l\'accordo, le carte tornano offerte', cancelled.status === 200 && (freed.items ?? []).some((h) => h.key === card.key && h.qty === card.qty));

  console.log(failures === 0 ? '\nTutto ok.' : `\n${failures} controlli falliti.`);
  process.exit(failures === 0 ? 0 : 1);
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
