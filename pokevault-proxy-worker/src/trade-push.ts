/**
 * Notifiche push di TradeRadar (fase 3): la consegna, non le regole.
 *
 * trade.ts decide CHI avvisare e QUANDO (una proposta arrivata, un
 * promemoria); qui si mette in coda, si rimanda di notte, si scrive il testo
 * nella lingua del telefono e si spedisce con Firebase Cloud Messaging.
 *
 * Pensato per non disturbare:
 * - ogni notifica ha un id che impedisce i doppioni: la stessa non parte
 *   mai due volte, nemmeno se il cron gira due volte o l'utente ripete;
 * - fra le 22 e le 8 (ora italiana) aspetta il mattino, tranne i promemoria
 *   degli appuntamenti, che hanno gia' il loro orario;
 * - una categoria spenta non entra nemmeno in coda;
 * - una notifica rimasta indietro di oltre 24 ore non parte piu': arriverebbe
 *   fuori tempo e confonderebbe;
 * - senza la chiave del servizio (secret FCM_SERVICE_ACCOUNT) non si manda
 *   niente, e le notifiche non si accumulano per partire tutte dopo.
 */

import { romeNow, romeToEpoch, addDays } from './trade-time';

export interface PushEnv {
  /** JSON dell'account di servizio Firebase (secret). Senza, niente invii. */
  FCM_SERVICE_ACCOUNT?: string;
  /** Il progetto a cui deve appartenere l'account di servizio. */
  FIREBASE_PROJECT_ID?: string;
}

export type NotifyKind = 'proposals' | 'meetings' | 'reminders' | 'after' | 'wants';

export interface NotifyPrefs {
  proposals: boolean;
  meetings: boolean;
  reminders: boolean;
  after: boolean;
  /** null = mai chiesto: finche' l'utente non risponde, niente avvisi sulle carte cercate. */
  wants: boolean | null;
}

export function prefsOf(raw: string | null | undefined): NotifyPrefs {
  let parsed: Record<string, unknown> = {};
  try {
    parsed = raw ? JSON.parse(raw) : {};
  } catch {
    parsed = {};
  }
  return {
    proposals: parsed.proposals !== false,
    meetings: parsed.meetings !== false,
    reminders: parsed.reminders !== false,
    after: parsed.after !== false,
    wants: typeof parsed.wants === 'boolean' ? parsed.wants : null,
  };
}

function wanted(prefs: NotifyPrefs, kind: NotifyKind): boolean {
  return kind === 'wants' ? prefs.wants === true : prefs[kind];
}

// ── Testi ───────────────────────────────────────────────────────────────────

type Lang = 'it' | 'en';
type Args = Record<string, string | number | boolean | string[] | undefined>;

const WEEKDAYS: Record<Lang, string[]> = {
  it: ['domenica', 'lunedì', 'martedì', 'mercoledì', 'giovedì', 'venerdì', 'sabato'],
  en: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],
};
const MONTHS: Record<Lang, string[]> = {
  it: ['gennaio', 'febbraio', 'marzo', 'aprile', 'maggio', 'giugno', 'luglio', 'agosto', 'settembre', 'ottobre', 'novembre', 'dicembre'],
  en: ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'],
};

/** "oggi alle 15:30", "domani alle 9:00", "giovedì 9 ottobre alle 15:30". Riferito a quando si scrive. */
function whenText(day: string, time: string, lang: Lang): string {
  const today = romeNow().day;
  const at = lang === 'it' ? `alle ${time}` : `at ${time}`;
  if (day === today) return lang === 'it' ? `oggi ${at}` : `today ${at}`;
  if (day === addDays(today, 1)) return lang === 'it' ? `domani ${at}` : `tomorrow ${at}`;
  const [y, m, d] = day.split('-').map(Number);
  const weekday = WEEKDAYS[lang][new Date(Date.UTC(y, m - 1, d)).getUTCDay()];
  return lang === 'it' ? `${weekday} ${d} ${MONTHS.it[m - 1]} ${at}` : `${weekday} ${MONTHS.en[m - 1]} ${d} ${at}`;
}

function cards(n: number, lang: Lang): string {
  if (lang === 'it') return n === 1 ? '1 carta' : `${n} carte`;
  return n === 1 ? '1 card' : `${n} cards`;
}

/** Nomi di carte in una riga: "Pikachu, Eevee e 2 altre". */
function names(list: string[], total: number, lang: Lang): string {
  const shown = list.slice(0, 2);
  const rest = total - shown.length;
  if (rest <= 0) return shown.join(lang === 'it' ? ' e ' : ' and ');
  return lang === 'it' ? `${shown.join(', ')} e ${rest} ${rest === 1 ? 'altra' : 'altre'}` : `${shown.join(', ')} and ${rest} more`;
}

const TEMPLATES: Record<string, (a: Args, lang: Lang) => { title: string; body: string }> = {
  proposal_new: (a, l) => l === 'it'
    ? { title: `Nuova proposta da ${a.nick}`, body: `Ti offre ${cards(Number(a.give), l)} per ${cards(Number(a.take), l)} delle tue: tocca a te rispondere.` }
    : { title: `New proposal from ${a.nick}`, body: `${cards(Number(a.give), l)} for ${cards(Number(a.take), l)} of yours: your turn to answer.` },
  proposal_counter: (a, l) => l === 'it'
    ? { title: `Controproposta da ${a.nick}`, body: 'Ha cambiato le carte: tocca a te rispondere.' }
    : { title: `Counter-offer from ${a.nick}`, body: 'The cards changed: your turn to answer.' },
  proposal_accepted: (a, l) => l === 'it'
    ? { title: `${a.nick} ha accettato!`, body: "Scegliete luogo e orario dell'appuntamento." }
    : { title: `${a.nick} accepted!`, body: 'Choose a place and time to meet.' },
  deal_cancelled: (a, l) => l === 'it'
    ? { title: `${a.nick} ha annullato l'accordo`, body: 'Le carte riservate tornano disponibili.' }
    : { title: `${a.nick} cancelled the deal`, body: 'The reserved cards are available again.' },
  meeting_proposed: (a, l) => l === 'it'
    ? { title: a.change ? `${a.nick} vuole cambiare l'appuntamento` : `${a.nick} propone un appuntamento`, body: "Scegli l'orario che ti va bene." }
    : { title: a.change ? `${a.nick} wants to change the meeting` : `${a.nick} suggests a meeting`, body: 'Pick the time that works for you.' },
  meeting_confirmed: (a, l) => ({
    title: l === 'it' ? 'Appuntamento confermato' : 'Meeting confirmed',
    body: `${l === 'it' ? 'Con' : 'With'} ${a.nick}, ${whenText(String(a.day), String(a.time), l)}${a.spot ? ` · ${a.spot}` : ''}`,
  }),
  reminder: (a, l) => ({
    title: l === 'it' ? `Scambio con ${a.nick} ${whenText(String(a.day), String(a.time), l)}` : `Trade with ${a.nick} ${whenText(String(a.day), String(a.time), l)}`,
    body: `${a.spot ? `${a.spot}. ` : ''}${l === 'it' ? 'Ricordati le carte!' : "Don't forget the cards!"}`,
  }),
  after: (a, l) => l === 'it'
    ? { title: `Com'è andata con ${a.nick}?`, body: 'Segna lo scambio e lascia un voto: aiuta tutti a fidarsi.' }
    : { title: `How did it go with ${a.nick}?`, body: 'Mark the trade and leave a rating: it helps everyone trust each other.' },
  done_by_other: (a, l) => l === 'it'
    ? { title: `${a.nick} ha segnato lo scambio fatto`, body: 'Confermalo anche tu per chiuderlo.' }
    : { title: `${a.nick} marked the trade as done`, body: 'Confirm it too to close it.' },
  trade_closed: (a, l) => l === 'it'
    ? { title: `Scambio con ${a.nick} chiuso!`, body: 'Aggiorna la collezione e lascia il tuo voto.' }
    : { title: `Trade with ${a.nick} closed!`, body: 'Update your collection and leave your rating.' },
  trade_auto_closed: (a, l) => l === 'it'
    ? { title: `Scambio con ${a.nick} chiuso`, body: "L'aveva segnato fatto e sono passati 7 giorni. Aggiorna la collezione e lascia il tuo voto." }
    : { title: `Trade with ${a.nick} closed`, body: 'They marked it done and 7 days have passed. Update your collection and leave your rating.' },
  meeting_expired: (a, l) => l === 'it'
    ? { title: `Appuntamento con ${a.nick} scaduto`, body: 'Nessuno dei due ha segnato lo scambio entro 7 giorni: le carte riservate tornano disponibili.' }
    : { title: `Meeting with ${a.nick} expired`, body: 'Neither of you marked the trade within 7 days: the reserved cards are available again.' },
  no_show_reported: (a, l) => l === 'it'
    ? { title: `${a.nick} dice che non ti sei presentato`, body: "Se c'eri, rispondi \"Io c'ero\" entro 48 ore." }
    : { title: `${a.nick} says you did not show up`, body: 'If you were there, answer "I was there" within 48 hours.' },
  wants: (a, l) => {
    const total = Number(a.count);
    const list = Array.isArray(a.names) ? a.names : [];
    if (total === 1) {
      return l === 'it'
        ? { title: 'Una carta che cerchi è vicino a te', body: `${list[0] ?? ''}: la trovi in TradeRadar.` }
        : { title: 'A card you want is near you', body: `${list[0] ?? ''}: find it in TradeRadar.` };
    }
    return l === 'it'
      ? { title: `${total} carte che cerchi sono vicino a te`, body: `${names(list, total, l)}: le trovi in TradeRadar.` }
      : { title: `${total} cards you want are near you`, body: `${names(list, total, l)}: find them in TradeRadar.` };
  },
};

// ── Coda ────────────────────────────────────────────────────────────────────

export interface Outgoing {
  /** Chiave anti-doppioni: la stessa notifica non entra due volte. Includere il destinatario. */
  id: string;
  uid: string;
  kind: NotifyKind;
  template: string;
  args: Args;
  /** Dove portare chi la tocca: screen = matches | proposals, e proposalId. */
  data?: Record<string, string>;
  /** Notifiche con lo stesso tag si sostituiscono sul telefono (es. le novita' di una proposta). */
  tag?: string;
}

const QUIET_FROM = '22:00';
const QUIET_TO = '08:00';
/** Oltre questo ritardo una notifica non parte piu'. */
const MAX_DELAY_MS = 24 * 60 * 60 * 1000;
const MAX_ATTEMPTS = 3;

/** Quando puo' partire: subito, o alle 8 se e' notte. I promemoria non aspettano. */
function sendAfter(kind: NotifyKind, now: number): number {
  if (kind === 'reminders') return now;
  const { day, time } = romeNow(now);
  if (time >= QUIET_FROM) return romeToEpoch(addDays(day, 1), QUIET_TO);
  if (time < QUIET_TO) return romeToEpoch(day, QUIET_TO);
  return now;
}

/**
 * Mette in coda, se il destinatario vuole quella categoria e la notifica non
 * c'e' gia'. Restituisce gli id entrati in coda e gia' spedibili.
 */
export async function enqueue(db: D1Database, items: Outgoing[]): Promise<string[]> {
  if (items.length === 0) return [];
  const uids = [...new Set(items.map((i) => i.uid))];
  const { results } = await db
    .prepare(`SELECT uid, notify_prefs FROM trade_profiles WHERE uid IN (SELECT value FROM json_each(?))`)
    .bind(JSON.stringify(uids))
    .all<{ uid: string; notify_prefs: string | null }>();
  const prefs = new Map(results.map((r) => [r.uid, prefsOf(r.notify_prefs)]));
  const now = Date.now();
  const accepted = items.filter((i) => {
    const p = prefs.get(i.uid);
    return p !== undefined && wanted(p, i.kind);
  });
  if (accepted.length === 0) return [];
  const outcome = await db.batch(
    accepted.map((i) =>
      db
        .prepare(
          `INSERT OR IGNORE INTO trade_notifications (id, uid, kind, payload, created_at, send_after) VALUES (?, ?, ?, ?, ?, ?)`
        )
        .bind(i.id, i.uid, i.kind, JSON.stringify({ template: i.template, args: i.args, data: i.data ?? {}, tag: i.tag ?? null }), now, sendAfter(i.kind, now))
    )
  );
  return accepted
    .filter((i, index) => (outcome[index]?.meta.changes ?? 0) > 0 && sendAfter(i.kind, now) <= now)
    .map((i) => i.id);
}

/** Le notifiche gia' in coda per questi id, o (senza ids) tutte quelle scadute, al piu' [limit]. */
export async function deliver(db: D1Database, env: PushEnv, ids?: string[], limit = 100): Promise<{ sent: number; skipped: number }> {
  const now = Date.now();
  const rows = ids
    ? ids.length === 0
      ? []
      : (
          await db
            .prepare(`SELECT * FROM trade_notifications WHERE sent_at IS NULL AND id IN (SELECT value FROM json_each(?))`)
            .bind(JSON.stringify(ids))
            .all<NotificationRow>()
        ).results
    : (
        await db
          .prepare(`SELECT * FROM trade_notifications WHERE sent_at IS NULL AND send_after <= ? ORDER BY send_after LIMIT ?`)
          .bind(now, limit)
          .all<NotificationRow>()
      ).results;
  let sent = 0;
  let skipped = 0;
  const account = serviceAccount(env);
  for (const row of rows) {
    const status = await deliverOne(db, account, row, now);
    if (status === 'sent') sent++;
    else skipped++;
  }
  return { sent, skipped };
}

interface NotificationRow {
  id: string;
  uid: string;
  kind: string;
  payload: string;
  created_at: number;
  send_after: number;
  attempts: number;
}

async function finish(db: D1Database, id: string, status: string): Promise<void> {
  await db.prepare(`UPDATE trade_notifications SET sent_at = ?, status = ? WHERE id = ?`).bind(Date.now(), status, id).run();
}

async function deliverOne(db: D1Database, account: ServiceAccount | null, row: NotificationRow, now: number): Promise<string> {
  if (now - row.send_after > MAX_DELAY_MS) {
    await finish(db, row.id, 'expired');
    return 'expired';
  }
  if (!account) {
    await finish(db, row.id, 'no_fcm');
    return 'no_fcm';
  }
  const { results: tokens } = await db
    .prepare(`SELECT token, lang FROM trade_push_tokens WHERE uid = ?`)
    .bind(row.uid)
    .all<{ token: string; lang: string }>();
  if (tokens.length === 0) {
    await finish(db, row.id, 'no_token');
    return 'no_token';
  }
  const payload = JSON.parse(row.payload) as { template: string; args: Args; data: Record<string, string>; tag: string | null };
  const render = TEMPLATES[payload.template];
  if (!render) {
    await finish(db, row.id, 'bad_template');
    return 'bad_template';
  }
  let delivered = false;
  let retry = false;
  for (const { token, lang } of tokens) {
    const text = render(payload.args, lang === 'en' ? 'en' : 'it');
    const result = await sendFcm(account, token, text, { ...payload.data, kind: row.kind }, payload.tag);
    if (result === 'ok') delivered = true;
    else if (result === 'gone') await db.prepare(`DELETE FROM trade_push_tokens WHERE token = ?`).bind(token).run();
    else retry = true;
  }
  if (delivered) {
    await finish(db, row.id, 'sent');
    return 'sent';
  }
  if (retry && row.attempts + 1 < MAX_ATTEMPTS) {
    await db.prepare(`UPDATE trade_notifications SET attempts = attempts + 1 WHERE id = ?`).bind(row.id).run();
    return 'retry';
  }
  await finish(db, row.id, retry ? 'failed' : 'no_token');
  return 'failed';
}

// ── Firebase Cloud Messaging ────────────────────────────────────────────────

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

/**
 * L'account di servizio, solo se e' del progetto giusto: una chiave di
 * produzione sullo staging (o il contrario) non deve mai spedire.
 */
function serviceAccount(env: PushEnv): ServiceAccount | null {
  if (!env.FCM_SERVICE_ACCOUNT) return null;
  try {
    const parsed = JSON.parse(env.FCM_SERVICE_ACCOUNT) as ServiceAccount;
    if (!parsed.client_email || !parsed.private_key || parsed.project_id !== env.FIREBASE_PROJECT_ID) return null;
    return parsed;
  } catch {
    return null;
  }
}

let accessTokenCache: { email: string; token: string; expires: number } | null = null;

function base64url(data: ArrayBuffer | string): string {
  const bytes = typeof data === 'string' ? new TextEncoder().encode(data) : new Uint8Array(data);
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function pemToDer(pem: string): ArrayBuffer {
  const body = pem.replace(/-----[^-]+-----/g, '').replace(/\s+/g, '');
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

/** Token OAuth per FCM (un'ora), dal JWT firmato con la chiave dell'account di servizio. */
async function accessToken(account: ServiceAccount): Promise<string | null> {
  const now = Date.now();
  if (accessTokenCache && accessTokenCache.email === account.client_email && accessTokenCache.expires > now + 60_000) {
    return accessTokenCache.token;
  }
  const iat = Math.floor(now / 1000);
  const unsigned = `${base64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))}.${base64url(
    JSON.stringify({
      iss: account.client_email,
      scope: 'https://www.googleapis.com/auth/firebase.messaging',
      aud: 'https://oauth2.googleapis.com/token',
      iat,
      exp: iat + 3600,
    })
  )}`;
  const key = await crypto.subtle.importKey('pkcs8', pemToDer(account.private_key), { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned));
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: `grant_type=${encodeURIComponent('urn:ietf:params:oauth:grant-type:jwt-bearer')}&assertion=${unsigned}.${base64url(signature)}`,
  });
  if (!res.ok) {
    console.warn('traderadar push: token OAuth rifiutato', res.status);
    return null;
  }
  const data = (await res.json()) as { access_token?: string; expires_in?: number };
  if (!data.access_token) return null;
  accessTokenCache = { email: account.client_email, token: data.access_token, expires: now + (data.expires_in ?? 3600) * 1000 };
  return data.access_token;
}

/** ok, gone (token da buttare: app disinstallata, account cambiato), retry (riprovare piu' tardi). */
async function sendFcm(
  account: ServiceAccount,
  token: string,
  text: { title: string; body: string },
  data: Record<string, string>,
  tag: string | null
): Promise<'ok' | 'gone' | 'retry'> {
  const bearer = await accessToken(account);
  if (!bearer) return 'retry';
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`, {
    method: 'POST',
    headers: { authorization: `Bearer ${bearer}`, 'content-type': 'application/json' },
    body: JSON.stringify({
      message: {
        token,
        notification: text,
        data,
        android: {
          priority: 'HIGH',
          notification: { channel_id: 'traderadar', ...(tag ? { tag } : {}) },
        },
      },
    }),
  });
  if (res.ok) return 'ok';
  const detail = await res.text();
  if (res.status === 404 || detail.includes('UNREGISTERED') || (res.status === 400 && detail.includes('registration token'))) return 'gone';
  console.warn('traderadar push: invio fallito', res.status, detail.slice(0, 200));
  return 'retry';
}
