#!/usr/bin/env node
// Le segnalazioni di TradeRadar (fase 2f), in STAGING: guardarle e decidere.
//
//   node scripts/trade-segnalazioni-staging.mjs
//       le persone con segnalazioni aperte: chi le ha fatte, perche', se
//       contano per la sospensione automatica, e la storia della persona
//       (voti ricevuti, "non si e' presentato", sospensioni)
//   node scripts/trade-segnalazioni-staging.mjs --conferma <chi> [--giorni 30 | --ban]
//       segnalazioni fondate: sospensione di N giorni (30 di default) o per sempre
//   node scripts/trade-segnalazioni-staging.mjs --archivia <chi>
//       segnalazioni infondate: archiviate, e se era sospeso per segnalazioni
//       la sospensione finisce. Chi accumula due segnalazioni archiviate
//       smette di pesare nella sospensione automatica.
//   node scripts/trade-segnalazioni-staging.mjs --rinomina <chi>
//       nickname offensivo: diventa "Allenatore" + 4 cifre
//   node scripts/trade-segnalazioni-staging.mjs --revoca <chi>
//       toglie una sospensione qualsiasi (errore nostro)
//
// <chi> e' il nickname esatto o l'id pubblico (16 caratteri). Legge e scrive
// il D1 di staging con wrangler, quindi serve essere loggati a Cloudflare.

import { execFileSync } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';

const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const arg = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : null; };
/** --prod: il D1 di PRODUZIONE (pokevault-trade). Senza, lo staging come sempre. */
const PROD = args.includes('--prod');
const D1_TARGET = PROD ? ['pokevault-trade', '--env='] : ['pokevault-trade-staging', '--env', 'staging'];
if (PROD) console.log('== D1 di PRODUZIONE (pokevault-trade) ==');
const DAY = 24 * 60 * 60 * 1000;
/** Un ban e' una sospensione che non finisce: 31/12/9999. */
const FOREVER = 253402300799000;

function sql(query) {
  const wrangler = path.join(here, '..', 'node_modules', 'wrangler', 'bin', 'wrangler.js');
  const out = execFileSync(process.execPath, [wrangler, 'd1', 'execute', ...D1_TARGET, '--remote', '--json', '--command', query], {
    cwd: path.join(here, '..'),
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'ignore'],
  });
  return JSON.parse(out.slice(out.indexOf('[')))[0].results ?? [];
}
const quote = (value) => `'${String(value).replace(/'/g, "''")}'`;
const date = (ms) => (ms >= FOREVER ? 'per sempre' : new Date(ms).toLocaleString('it-IT', { timeZone: 'Europe/Rome', dateStyle: 'short', timeStyle: 'short' }));
const REASONS = { behavior: 'comportamento scorretto', scam: 'truffa / soldi o spedizione', fake_cards: 'carte false o diverse', nickname: 'nickname offensivo', other: 'altro' };

function who(value) {
  if (!value) { console.log('Manca <chi>: nickname o id pubblico.'); process.exit(1); }
  const rows = sql(`SELECT uid, public_id, nickname, suspended_until, suspension_reason FROM trade_profiles
                    WHERE public_id = ${quote(value)} OR nickname = ${quote(value)}`);
  if (rows.length !== 1) {
    console.log(rows.length === 0 ? `Nessun profilo "${value}".` : `${rows.length} profili "${value}": usa l'id pubblico.`);
    process.exit(1);
  }
  return rows[0];
}

const now = Date.now();
const target = arg('--conferma') ?? arg('--archivia') ?? arg('--rinomina') ?? arg('--revoca');

if (args.includes('--conferma')) {
  const t = who(arg('--conferma'));
  const until = args.includes('--ban') ? FOREVER : now + Number(arg('--giorni') ?? 30) * DAY;
  sql(`UPDATE trade_reports SET status = 'upheld', reviewed_at = ${now} WHERE target_uid = ${quote(t.uid)} AND status = 'open'`);
  sql(`UPDATE trade_profiles SET suspended_until = ${until}, suspension_reason = 'admin' WHERE uid = ${quote(t.uid)}`);
  console.log(`${t.nickname}: segnalazioni confermate, sospeso fino a: ${date(until)}.`);
} else if (args.includes('--archivia')) {
  const t = who(arg('--archivia'));
  sql(`UPDATE trade_reports SET status = 'dismissed', reviewed_at = ${now} WHERE target_uid = ${quote(t.uid)} AND status = 'open'`);
  if (t.suspension_reason === 'reports') {
    sql(`UPDATE trade_profiles SET suspended_until = NULL, suspension_reason = NULL WHERE uid = ${quote(t.uid)}`);
    console.log(`${t.nickname}: segnalazioni archiviate e sospensione automatica tolta.`);
  } else {
    console.log(`${t.nickname}: segnalazioni archiviate.`);
  }
} else if (args.includes('--rinomina')) {
  const t = who(arg('--rinomina'));
  const name = `Allenatore ${String(Math.floor(1000 + Math.random() * 9000))}`;
  sql(`UPDATE trade_profiles SET nickname = ${quote(name)} WHERE uid = ${quote(t.uid)}`);
  sql(`UPDATE trade_reports SET status = 'upheld', reviewed_at = ${now} WHERE target_uid = ${quote(t.uid)} AND status = 'open' AND reason = 'nickname'`);
  console.log(`${t.nickname} ora si chiama ${name}.`);
} else if (args.includes('--revoca')) {
  const t = who(arg('--revoca'));
  sql(`UPDATE trade_profiles SET suspended_until = NULL, suspension_reason = NULL WHERE uid = ${quote(t.uid)}`);
  sql(`DELETE FROM trade_sanctions WHERE uid = ${quote(t.uid)}`);
  console.log(`${t.nickname}: nessuna sospensione.`);
} else if (target === null) {
  const reports = sql(`
    SELECT r.*, t.nickname AS target_nick, t.public_id AS target_id, t.suspended_until, t.suspension_reason,
           COALESCE(f.nickname, '(profilo disattivato)') AS reporter_nick,
           (SELECT COUNT(*) FROM trade_reports d WHERE d.reporter_uid = r.reporter_uid AND d.status = 'dismissed') AS reporter_dismissed
    FROM trade_reports r
    LEFT JOIN trade_profiles t ON t.uid = r.target_uid
    LEFT JOIN trade_profiles f ON f.uid = r.reporter_uid
    WHERE r.status = 'open' ORDER BY r.target_uid, r.created_at`);
  if (reports.length === 0) { console.log('Nessuna segnalazione aperta.'); process.exit(0); }
  const byTarget = new Map();
  for (const r of reports) (byTarget.get(r.target_uid) ?? byTarget.set(r.target_uid, []).get(r.target_uid)).push(r);
  for (const [uid, list] of byTarget) {
    const t = list[0];
    const [history] = sql(`
      SELECT (SELECT COUNT(*) FROM trade_ratings WHERE to_uid = ${quote(uid)} AND mood = 'good') AS good,
             (SELECT COUNT(*) FROM trade_ratings WHERE to_uid = ${quote(uid)} AND mood = 'ok') AS ok,
             (SELECT COUNT(*) FROM trade_ratings WHERE to_uid = ${quote(uid)} AND mood = 'bad') AS bad,
             (SELECT COUNT(*) FROM trade_no_shows WHERE target_uid = ${quote(uid)}) AS noShows,
             (SELECT COUNT(*) FROM trade_reports WHERE target_uid = ${quote(uid)} AND status = 'upheld') AS upheld,
             (SELECT COUNT(*) FROM trade_reports WHERE reporter_uid = ${quote(uid)} AND created_at > ${now - 30 * DAY}) AS madeRecently`);
    const weighted = new Set(list.filter((r) => r.weight === 1).map((r) => r.reporter_uid)).size;
    console.log(`\n■ ${t.target_nick ?? '(profilo disattivato)'}  ${t.target_id ?? ''}`);
    console.log(`  ${list.length} aperte, ${weighted} persone che contano per la sospensione automatica (soglia 3)`);
    console.log(`  sospeso: ${t.suspended_until > now ? `fino a ${date(t.suspended_until)} (${t.suspension_reason})` : 'no'}`);
    console.log(`  storia: 😊 ${history.good} 😐 ${history.ok} 😞 ${history.bad} · non presentato ${history.noShows} · confermate prima ${history.upheld} · ha segnalato ${history.madeRecently} persone in 30 giorni`);
    for (const r of list) {
      const why = r.weight === 1 ? 'conta' : 'non conta';
      console.log(`   - ${date(r.created_at)}  ${REASONS[r.reason] ?? r.reason}  da ${r.reporter_nick}${r.reporter_dismissed ? ` (${r.reporter_dismissed} archiviate)` : ''}  [${why}]`);
      if (r.note) console.log(`       "${r.note}"`);
    }
  }
}
