#!/usr/bin/env node
// Quattro persone finte che rispondono da sole, in STAGING, per provare
// TradeRadar fuori casa senza nessuno al computer:
//
//   Bot Si'     accetta le proposte, conferma il primo orario, segna lo
//               scambio fatto dopo di te e vota 😊
//   Bot No      rifiuta tutte le proposte
//   Bot Cambia  la prima volta fa una controproposta e sposta l'appuntamento
//               di un'ora; dalla seconda accetta (e vota 😐)
//   Bot Propone ogni 30 minuti ti manda una proposta, se fra voi non ce n'e'
//               gia' una in corso; poi si comporta come Bot Si'
//
//   node scripts/trade-bot-staging.mjs [--segui ema994]
//
// Usa le stesse chiamate dell'app, come un utente qualsiasi: niente di
// speciale nel server, niente da togliere dopo. Ti segue: ogni 2 minuti si
// mette nella tua zona, offre le carte che cerchi e cerca quelle che offri,
// cosi' i bot sono sempre nei tuoi Match. Ogni 20 secondi guarda le proposte
// e risponde. Deve restare acceso (PC sveglio e connesso); Ctrl+C lo ferma.

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
const args = process.argv.slice(2);
const arg = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : null; };
const FOLLOW = arg('--segui') ?? 'ema994';
const ACT_EVERY_MS = 20_000;
const SYNC_EVERY_MS = 120_000;
const PROPOSE_EVERY_MS = 30 * 60_000;

const BOTS = [
  { label: 'bot-si', nickname: 'Bot Sì', mode: 'yes', avatar: 25 },
  { label: 'bot-no', nickname: 'Bot No', mode: 'no', avatar: 143 },
  { label: 'bot-cambia', nickname: 'Bot Cambia', mode: 'change', avatar: 133 },
  { label: 'bot-propone', nickname: 'Bot Propone', mode: 'propose', avatar: 6 },
];

const log = (...m) => console.log(new Date().toLocaleTimeString('it-IT'), ...m);

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
/** Il token dura un'ora: lo si rinnova ogni 45 minuti. */
async function login(bot) {
  if (bot.tok && Date.now() - bot.at < 45 * 60_000) return;
  const email = `traderadar.test.${bot.label}@pokevault.invalid`;
  let res = await auth('signInWithPassword', email);
  if (!res.idToken) res = await auth('signUp', email);
  if (!res.idToken) throw new Error(`login ${bot.label}: ${JSON.stringify(res.error ?? res)}`);
  Object.assign(bot, { tok: res.idToken, uid: res.localId, at: Date.now() });
}
async function call(bot, method, route, body) {
  await login(bot);
  const res = await fetch(`${BASE}${route}`, {
    method, headers: { authorization: `Bearer ${bot.tok}`, 'content-type': 'application/json' },
    body: body ? JSON.stringify(body) : (method === 'POST' || method === 'PUT' ? '{}' : undefined),
  });
  const text = await res.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  return { status: res.status, data };
}

// ── Seguire la persona: zona e carte ──
let follow = null;

async function sync() {
  const [me] = sql(`SELECT uid, public_id, geohash5 FROM trade_profiles WHERE nickname = ${quote(FOLLOW)}`);
  if (!me) { log(`nessun profilo "${FOLLOW}": aspetto`); return; }
  const haves = sql(`SELECT DISTINCT card_key FROM trade_haves WHERE uid = ${quote(me.uid)}`).map((r) => r.card_key);
  const wants = sql(`SELECT card_key FROM trade_wants WHERE uid = ${quote(me.uid)}`).map((r) => r.card_key);
  // Qualche carta in piu' da offrire, presa dalla folla: cosi' c'e' scelta anche con una lista desideri corta.
  const extra = sql(`SELECT DISTINCT card_key FROM trade_haves WHERE uid IN (SELECT uid FROM trade_profiles WHERE nickname LIKE 'Test %') LIMIT 6`).map((r) => r.card_key);
  const moved = follow?.geohash5 !== me.geohash5;
  follow = me;
  for (const bot of BOTS) {
    await call(bot, 'PUT', '/v1/trade/profile', { nickname: bot.nickname, geohash5: me.geohash5, adultConfirmed: true, collectionConsent: true, paused: false });
    if (!bot.avatarSet) { await call(bot, 'PUT', '/v1/trade/avatar', { avatar: bot.avatar, animated: true }); bot.avatarSet = true; }
    await call(bot, 'PUT', '/v1/trade/wants', { items: haves.map((key) => ({ key, source: 'wishlist', priority: 'need' })) });
    const offer = [...new Set([...wants, ...extra])].filter((key) => !haves.includes(key));
    await call(bot, 'PUT', '/v1/trade/haves', {
      items: offer.map((key) => ({ key, variant: 'Normal', condition: 'Near Mint', language: 'Italiano', qty: 2, manual: true, notify: true })),
    });
  }
  if (moved) log(`seguo ${FOLLOW} nella zona ${me.geohash5}: i bot offrono ${wants.length} carte cercate (+ qualche altra) e cercano le sue ${haves.length}`);
}

/** Bot Propone: una proposta nuova solo se fra voi non ce n'e' gia' una in corso. */
async function propose(bot) {
  if (!follow) return;
  const open = ((await call(bot, 'GET', '/v1/trade/proposals')).data.proposals ?? [])
    .some((p) => p.counterpart?.id === follow.public_id && ['open', 'accepted', 'scheduled'].includes(p.status));
  if (open) return;
  const mine = (await call(bot, 'GET', '/v1/trade/haves')).data.items ?? [];
  const theirs = (await call(bot, 'GET', `/v1/trade/users/${follow.public_id}/haves`)).data.items ?? [];
  if (!mine.length || !theirs.length) { log('Bot Propone: niente da scambiare per ora'); return; }
  const r = await call(bot, 'POST', '/v1/trade/proposals', { to: follow.public_id, give: [item({ ...mine[0], qty: 1 })], take: [item({ ...theirs[0], qty: 1 })] });
  log(`Bot Propone -> ${FOLLOW}: nuova proposta (${r.status}${r.data?.error ? ` ${r.data.error}` : ''})`);
}

// ── Rispondere ──
const changed = new Set(); // "id:meeting" gia' spostati da Bot Cambia
const item = (i) => ({ key: i.key, variant: i.variant, condition: i.condition, language: i.language, qty: i.qty ?? 1 });
const plusHour = (time) => {
  const [h, m] = time.split(':').map(Number);
  return `${String(Math.min(h + 1, 23)).padStart(2, '0')}:${String(h + 1 > 23 ? 0 : m).padStart(2, '0')}`;
};

async function counter(bot, p) {
  // Bot da' un po' meno, o chiede una carta in piu': basta che cambi qualcosa.
  const give = (p.give ?? []).map(item);
  let take = (p.take ?? []).map(item);
  if (give.length >= 2) give.pop();
  else {
    const theirs = (await call(bot, 'GET', `/v1/trade/users/${p.counterpart.id}/haves`)).data.items ?? [];
    const more = theirs.find((t) => !take.some((x) => x.key === t.key && x.variant === t.variant && x.condition === t.condition && x.language === t.language));
    if (!more) return null;
    take = [...take, item({ ...more, qty: 1 })];
  }
  return call(bot, 'POST', `/v1/trade/proposals/${p.id}/counter`, { give, take });
}

async function act(bot) {
  const { status, data } = await call(bot, 'GET', '/v1/trade/proposals');
  if (status !== 200) return;
  for (const p of data.proposals ?? []) {
    const who = p.counterpart?.nickname ?? '?';
    const done = (what, r) => log(`${bot.nickname} -> ${who}: ${what} (${r?.status})`);

    if (p.status === 'open' && p.myTurn) {
      if (bot.mode === 'no') { done('rifiuta la proposta', await call(bot, 'POST', `/v1/trade/proposals/${p.id}/decline`)); continue; }
      if (bot.mode === 'change' && (p.revision ?? 1) === 1) {
        const r = await counter(bot, p);
        if (r) { done('controproposta', r); continue; }
      }
      done('accetta la proposta', await call(bot, 'POST', `/v1/trade/proposals/${p.id}/accept`));
      continue;
    }

    const meeting = p.meeting ?? {};
    if ((p.status === 'accepted' || p.status === 'scheduled') && meeting.status === 'proposed' && meeting.byMe === false) {
      const slots = meeting.slots ?? [];
      if (bot.mode === 'change' && !changed.has(`${p.id}:meeting`) && meeting.spot?.id && slots.length) {
        changed.add(`${p.id}:meeting`);
        const moved = slots.map((s) => (s.time ? { day: s.day, time: plusHour(s.time) } : { day: s.day, part: s.part }));
        done(`sposta l'appuntamento di un'ora (${moved.map((s) => s.time ?? s.part).join(', ')})`,
          await call(bot, 'POST', `/v1/trade/proposals/${p.id}/meeting`, { spot: meeting.spot.id, slots: moved }));
        continue;
      }
      const index = Math.max(0, slots.findIndex((s) => s.time));
      done(`conferma l'appuntamento ${slots[index]?.day ?? ''} ${slots[index]?.time ?? slots[index]?.part ?? ''}`,
        await call(bot, 'POST', `/v1/trade/proposals/${p.id}/meeting/confirm`, { slot: index }));
      continue;
    }

    if (p.status === 'scheduled' && p.doneByOther && !p.doneByMe) {
      const r = await call(bot, 'POST', `/v1/trade/proposals/${p.id}/done`);
      if (r.status === 200) done('segna lo scambio fatto', r);
      continue;
    }

    if (p.status === 'done' && !p.myRating) {
      const rating = bot.mode === 'change' ? { mood: 'ok', tags: [] } : { mood: 'good', tags: ['punctual', 'kind'] };
      done(`vota ${rating.mood === 'good' ? '😊' : '😐'}`, await call(bot, 'POST', `/v1/trade/proposals/${p.id}/rate`, rating));
    }
  }
}

log(`bot di prova su STAGING, seguono "${FOLLOW}". Ctrl+C per fermarli.`);
for (const bot of BOTS) await login(bot);
await sync().catch((e) => log('sync:', e.message));
let lastSync = Date.now();
let lastPropose = 0;
for (;;) {
  for (const bot of BOTS) await act(bot).catch((e) => log(`${bot.nickname}:`, e.message));
  if (Date.now() - lastPropose > PROPOSE_EVERY_MS) {
    await propose(BOTS.find((b) => b.mode === 'propose')).catch((e) => log('Bot Propone:', e.message));
    lastPropose = Date.now();
  }
  if (Date.now() - lastSync > SYNC_EVERY_MS) {
    await sync().catch((e) => log('sync:', e.message));
    lastSync = Date.now();
  }
  await new Promise((r) => setTimeout(r, ACT_EVERY_MS));
}
