#!/usr/bin/env node
// Verifica dal vivo che il catalogo si veda nell'app: loghi delle espansioni,
// immagini delle carte, liste delle carte, stato dei prezzi.
//
// Chiede al worker di produzione ESATTAMENTE gli URL che costruisce l'app
// (vedi sotto, ogni funzione dice da dove li copia), quindi un verde qui vuol
// dire che l'utente vede loghi e carte. Non tocca D1, R2 ne' il codice.
//
// Uso:
//   node scripts/verifica-catalogo-live.mjs                  (tutte le espansioni)
//   node scripts/verifica-catalogo-live.mjs --only me03,sv09 (solo alcune)
//   node scripts/verifica-catalogo-live.mjs --out report     (cartella del report)
//
// Produce <out>/report.html (galleria con loghi e carte di ogni set) e
// <out>/report.md; se gira in GitHub Actions scrive anche il riepilogo della
// run ($GITHUB_STEP_SUMMARY). Esce con 1 se c'e' almeno un errore; gli avvisi
// non fanno fallire.
//
// Tre cose imparate a spese nostre (vedi le note di progetto):
// - il worker rifiuta HEAD con 405: si usa sempre GET;
// - un 404 resta nella cache KV per 24 ore: un'immagine appena caricata puo'
//   risultare mancante fino al giorno dopo, e il report lo ricorda;
// - il logo non si chiede col baseSetCode ma col codice che l'app preferisce
//   (sv05 -> TEF, me01 -> MEG...), altrimenti escono decine di falsi mancanti.
import { mkdirSync, writeFileSync, appendFileSync } from 'node:fs';
import path from 'node:path';
import sharp from 'sharp';

const BASE = (process.env.POKEVAULT_WORKER_URL || 'https://pokevault-proxy.pokevault-emanu.workers.dev').replace(/\/+$/, '');
const CONCURRENCY = 6;
// Per la copertura completa serve leggere l'elenco di R2 (sola lettura). In CI
// e' il segreto CLOUDFLARE_API_TOKEN, lo stesso di catalog-ingest.yml; in
// locale si puo' passare `CLOUDFLARE_API_TOKEN=$(npx wrangler auth token)`.
// Senza token la copertura completa si salta e resta il campione.
const CF_TOKEN = process.env.CLOUDFLARE_API_TOKEN || '';
const CF_ACCOUNT_ID = process.env.CLOUDFLARE_ACCOUNT_ID || 'e6c4d1ff864abf6dbcb4ec1e0de6b34a';
const R2_BUCKET = 'pokevault-images';

// CatalogRepository.SET_IMAGE_CACHE_VERSION
const SET_IMAGE_CACHE_VERSION = 'setimg-v5';
// CatalogRepository.appendItalianImageCacheBuster
const ITALIAN_IMAGE_CACHE_BUSTER = 'itv=r2v3';

// CatalogRepository.preferredBaseSetCodeForItalianExpansion: copiata com'e',
// compreso lo scambio voluto di zsv10pt5/rsv10pt5 (i due loghi su R2 sono
// caricati sotto la chiave sbagliata e le due inversioni si annullano).
const PREFERRED_BASE_SET_CODE = {
  me01: 'MEG', me02: 'PFL', me03: 'ME03', me04: 'CRI', me2pt5: 'ASC', mep: 'MEP',
  sv01: 'SVI', sv02: 'PAL', sv03: 'OBF', sv04: 'PAR', sv05: 'TEF', sv06: 'TWM',
  sv07: 'SCR', sv08: 'SSP', sv09: 'JTG', sv10: 'DRI',
  zsv10pt5: 'BLK', rsv10pt5: 'WHT',
  sv3pt5: 'MEW', sv4pt5: 'PAF', sv6pt5: 'SFA', sv8pt5: 'PRE',
};

function parseArgs(argv) {
  const args = { only: null, out: 'report-catalogo' };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--only') args.only = new Set(argv[++i].split(',').map(s => s.trim().toLowerCase()));
    else if (argv[i] === '--out') args.out = argv[++i];
  }
  return args;
}

// CatalogRepository.buildSetImageUrl(baseRawSetCode, italianOnly = true)
function logoUrl(expansion) {
  const code = PREFERRED_BASE_SET_CODE[expansion.id.toLowerCase()]
    ?? expansion.baseSetCode
    ?? expansion.id.toUpperCase();
  return `${BASE}/sets/${code}/image?v=${SET_IMAGE_CACHE_VERSION}&source=ita`;
}

// ItalianCatalogNormalizer.toImageReference + ItalianCardRecord.imageUrl
const IMAGE_ID_RE = /^([A-Za-z0-9-]+)_IT_([A-Za-z0-9_]+)\.(png|webp|jpe?g)$/i;
function imageReference(cardId) {
  const m = IMAGE_ID_RE.exec(cardId.trim());
  if (!m) return null;
  const raw = m[2].trim();
  return { folder: m[1].trim().toUpperCase(), number: /^[+-]?\d+$/.test(raw) ? String(parseInt(raw, 10)) : raw };
}
function cardImageUrl(cardId, size) {
  const ref = imageReference(cardId);
  if (!ref) return null;
  return `${BASE}/images/it/${ref.folder}/${ref.number}?size=${size}&${ITALIAN_IMAGE_CACHE_BUSTER}`;
}

// Copia di normalizeCardNumber + buildItalianCardKeyCandidates del worker
// (src/index.ts): l'ordine in cui il worker cerca il file di una carta su R2.
// Serve alla copertura completa, che non puo' chiedere 18 mila immagini al
// worker (ogni richiesta nuova scrive una chiave KV). Se il worker cambia e
// questa copia no, se ne accorge il campione (fondo di checkExpansion).
function normalizeCardNumber(raw) {
  const digits = raw.replace(/[^0-9]/g, '');
  if (digits) return digits.replace(/^0+/, '') || '0';
  return raw.trim().replace(/^0+/, '') || '0';
}
function workerKeyCandidates(setCode, cardNumber, size) {
  const raw = cardNumber.trim();
  const isNum = /^\d+$/.test(raw);
  const normalized = isNum ? normalizeCardNumber(raw) : raw;
  const padded = isNum ? normalized.padStart(3, '0') : raw;
  const up = setCode.toUpperCase(), low = setCode.toLowerCase();
  const setTokens = [up, low];
  const cardTokens = [raw, raw.toUpperCase(), raw.toLowerCase(), ...(isNum ? [normalized, padded] : [])]
    .filter(Boolean).filter((v, i, l) => l.indexOf(v) === i);
  const bases = [`it/${up}`, `it/${low}`];
  const exts = ['webp', 'png', 'jpg', 'jpeg'];
  const out = [];
  const push = k => { if (k && !out.includes(k)) out.push(k); };
  for (const b of bases) for (const t of cardTokens.slice(0, 3)) for (const e of exts) push(`${b}/${t}.${e}`);
  if (size === 'low' || size === 'high') {
    for (const b of bases) for (const s of setTokens) for (const t of cardTokens.slice(0, 2)) for (const e of exts) {
      push(`${b}/${s}_IT_${t}_${size}.${e}`);
      push(`${b}/${s}_IT_${t}-${size}.${e}`);
    }
  }
  for (const b of bases) for (const s of setTokens.slice(0, 1)) {
    for (const t of cardTokens.slice(0, 2)) { push(`${b}/${s}_IT_${t}.webp`); push(`${b}/${s}_IT_${t}.png`); }
    for (const t of cardTokens.slice(0, 1)) push(`${b}/${t}.png`);
  }
  return out;
}

async function listR2Keys() {
  const keys = new Set();
  let cursor = null;
  do {
    const url = new URL(`https://api.cloudflare.com/client/v4/accounts/${CF_ACCOUNT_ID}/r2/buckets/${R2_BUCKET}/objects`);
    url.searchParams.set('prefix', 'it/');
    url.searchParams.set('per_page', '1000');
    if (cursor) url.searchParams.set('cursor', cursor);
    const res = await fetch(url, { headers: { Authorization: `Bearer ${CF_TOKEN}` } });
    const json = await res.json().catch(() => ({}));
    if (!res.ok || !json.success) throw new Error(`elenco R2: HTTP ${res.status} ${JSON.stringify(json.errors ?? '')}`);
    for (const o of json.result) keys.add(o.key);
    cursor = json.result_info?.is_truncated ? json.result_info.cursor : null;
  } while (cursor);
  return keys;
}

function cardReachable(cardId, r2Keys) {
  const ref = imageReference(cardId);
  if (!ref) return false;
  return workerKeyCandidates(ref.folder, ref.number, 'low').some(k => r2Keys.has(k));
}

async function get(url, { tries = 4 } = {}) {
  let lastError;
  for (let attempt = 1; attempt <= tries; attempt += 1) {
    const started = Date.now();
    try {
      const res = await fetch(url, { headers: { 'user-agent': 'pokevault-verifica-catalogo' } });
      const body = Buffer.from(await res.arrayBuffer());
      // I 5xx sono spesso momentanei: si riprova come per gli errori di rete.
      if (res.status >= 500 && attempt < tries) { lastError = new Error(`HTTP ${res.status}`); }
      else return { status: res.status, headers: res.headers, body, ms: Date.now() - started };
    } catch (error) {
      lastError = error;
    }
    await new Promise(r => setTimeout(r, 800 * attempt));
  }
  return { status: 0, headers: new Headers(), body: Buffer.alloc(0), ms: 0, error: String(lastError?.message ?? lastError) };
}

// Un'immagine "si vede" se il worker risponde 200 con un tipo immagine e i
// byte si decodificano davvero: un 200 con dentro un errore o un file
// troncato passerebbe un controllo sul solo codice.
async function checkImage(url, kind) {
  const res = await get(url);
  const out = { url, status: res.status, ms: res.ms, cache: res.headers.get('x-cache-status'), type: res.headers.get('content-type'), bytes: res.body.length };
  if (res.status !== 200) return { ...out, ok: false, problem: res.status === 0 ? `rete: ${res.error}` : `HTTP ${res.status}` };
  if (!out.type?.startsWith('image/')) return { ...out, ok: false, problem: `non e' un'immagine (${out.type})` };
  try {
    const meta = await sharp(res.body).metadata();
    out.width = meta.width; out.height = meta.height; out.format = meta.format;
  } catch (error) {
    return { ...out, ok: false, problem: `immagine illeggibile: ${error.message}` };
  }
  if (kind === 'card') {
    const ratio = out.width / out.height;
    if (out.width < 150) return { ...out, ok: false, problem: `troppo piccola (${out.width}x${out.height})` };
    if (ratio < 0.65 || ratio > 0.78) return { ...out, ok: false, problem: `proporzioni non da carta (${out.width}x${out.height})` };
  } else if (out.width < 40 || out.height < 15) {
    return { ...out, ok: false, problem: `logo troppo piccolo (${out.width}x${out.height})` };
  }
  return { ...out, ok: true };
}

function pickSample(cards) {
  if (cards.length === 0) return [];
  const idx = [0, Math.floor(cards.length / 2), cards.length - 1];
  return [...new Set(idx)].map(i => cards[i]);
}

async function checkExpansion(expansion, r2Keys) {
  const result = { id: expansion.id, name: expansion.name, series: expansion.series, cardCount: expansion.cardCount, errors: [], warnings: [], cards: [] };

  result.logo = await checkImage(logoUrl(expansion), 'logo');
  if (!result.logo.ok) result.errors.push(`Logo: ${result.logo.problem}`);

  const list = await get(`${BASE}/v1/expansions/${encodeURIComponent(expansion.id)}/cards`);
  let cards = [];
  if (list.status !== 200) {
    result.errors.push(`Lista carte: ${list.status === 0 ? `rete: ${list.error}` : `HTTP ${list.status}`}`);
  } else {
    try { cards = JSON.parse(list.body.toString('utf8')).cards ?? []; }
    catch (error) { result.errors.push(`Lista carte illeggibile: ${error.message}`); }
  }
  result.listed = cards.length;
  if (list.status === 200 && cards.length === 0) result.errors.push('Lista carte vuota');
  else if (cards.length && expansion.cardCount && cards.length !== expansion.cardCount) {
    result.warnings.push(`Carte in lista ${cards.length}, dichiarate ${expansion.cardCount}`);
  }

  const sample = pickSample(cards);
  for (const [i, card] of sample.entries()) {
    const url = cardImageUrl(card.cardId, 'low');
    if (!url) { result.errors.push(`Carta ${card.cardId}: id che l'app non sa trasformare in immagine`); continue; }
    const check = await checkImage(url, 'card');
    result.cards.push({ cardId: card.cardId, nome: card.nome, ...check });
    if (!check.ok) result.errors.push(`Carta ${card.nome} (${card.cardId}): ${check.problem}`);
    // Il dettaglio carta chiede size=high: basta provarlo su una carta per set.
    if (i === 0 && check.ok) {
      const high = await checkImage(cardImageUrl(card.cardId, 'high'), 'card');
      if (!high.ok) result.errors.push(`Carta ${card.nome} (${card.cardId}), dettaglio: ${high.problem}`);
    }
  }

  if (r2Keys) {
    // Copertura completa: ogni carta della lista, non solo il campione.
    result.unreachable = cards.filter(c => !cardReachable(c.cardId, r2Keys)).map(c => ({ cardId: c.cardId, nome: c.nome }));
    if (result.unreachable.length) {
      // Le carte del campione gia' contate qui non si ripetono una per una.
      const counted = new Set(result.unreachable.map(c => c.cardId));
      result.errors = result.errors.filter(e => !result.cards.some(c => !c.ok && counted.has(c.cardId) && e.includes(`(${c.cardId})`)));
      const examples = result.unreachable.slice(0, 5).map(c => `${c.nome} (${c.cardId})`).join(', ');
      result.errors.push(`${result.unreachable.length} carte su ${cards.length} senza immagine su R2 dove il worker la cerca, es. ${examples}`);
    }
    // Il campione e' passato dal worker vero: se la copia dell'ordine di
    // ricerca dice il contrario, e' la copia a essere rimasta indietro.
    for (const c of result.cards) {
      const simulated = cardReachable(c.cardId, r2Keys);
      if (c.ok && !simulated) result.errors.push(`Verifica disallineata dal worker: ${c.cardId} si vede, ma la copia di buildItalianCardKeyCandidates non la trova`);
      if (!c.ok && simulated && c.status === 404) result.warnings.push(`${c.cardId}: su R2 c'e', ma il worker risponde 404 (probabile 404 in cache da meno di 24 ore)`);
    }
  }
  return result;
}

async function checkHealth() {
  const res = await get(`${BASE}/v1/health`);
  const health = { errors: [], warnings: [] };
  if (res.status !== 200) { health.errors.push(`/v1/health: HTTP ${res.status}`); return health; }
  try {
    const h = JSON.parse(res.body.toString('utf8'));
    health.data = h;
    if (h.status !== 'ok') health.errors.push(`Stato del worker: ${h.status}`);
    if (!h.prices?.available) health.errors.push('Prezzi non disponibili');
    else if (h.prices.oldest_expansion_age_hours > 72) {
      health.warnings.push(`Il prezzo piu' vecchio ha ${Math.round(h.prices.oldest_expansion_age_hours)} ore`);
    }
  } catch (error) {
    health.errors.push(`/v1/health illeggibile: ${error.message}`);
  }
  return health;
}

async function pool(items, worker) {
  const queue = items.map((item, index) => ({ item, index }));
  const results = new Array(items.length);
  await Promise.all(Array.from({ length: CONCURRENCY }, async () => {
    while (queue.length) {
      const { item, index } = queue.shift();
      results[index] = await worker(item);
    }
  }));
  return results;
}

// ── Report ────────────────────────────────────────────────────────────────

const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

function summarize(results, health, startedAt) {
  const logos = results.filter(r => r.logo);
  const images = results.flatMap(r => r.cards);
  return {
    when: new Date(startedAt).toISOString(),
    seconds: Math.round((Date.now() - startedAt) / 1000),
    expansions: results.length,
    expansionsOk: results.filter(r => r.errors.length === 0).length,
    logosOk: logos.filter(r => r.logo.ok).length,
    logos: logos.length,
    imagesOk: images.filter(c => c.ok).length,
    images: images.length,
    cardsListed: results.reduce((n, r) => n + (r.listed ?? 0), 0),
    coverageChecked: results.some(r => r.unreachable),
    cardsUnreachable: results.reduce((n, r) => n + (r.unreachable?.length ?? 0), 0),
    errors: health.errors.length + results.reduce((n, r) => n + r.errors.length, 0),
    warnings: health.warnings.length + results.reduce((n, r) => n + r.warnings.length, 0),
  };
}

function markdownReport(s, results, health) {
  const ok = s.errors === 0;
  const lines = [
    `## ${ok ? '✅' : '❌'} Verifica catalogo dal vivo`,
    '',
    `${ok ? 'Tutto quello che l\'app mostra risponde e si vede.' : `**${s.errors} ${s.errors === 1 ? 'problema' : 'problemi'}** da guardare.`} Worker: \`${BASE}\` · ${s.when.replace('T', ' ').slice(0, 16)} UTC · ${s.seconds}s`,
    '',
    '| Controllo | Esito |',
    '|---|---|',
    `| Espansioni senza problemi | ${s.expansionsOk} / ${s.expansions} |`,
    `| Loghi che si vedono | ${s.logosOk} / ${s.logos} |`,
    `| Immagini carta che si vedono (3 per set) | ${s.imagesOk} / ${s.images} |`,
    `| Carte in catalogo | ${s.cardsListed.toLocaleString('it-IT')} |`,
    `| Carte con immagine raggiungibile (tutte) | ${s.coverageChecked ? `${(s.cardsListed - s.cardsUnreachable).toLocaleString('it-IT')} / ${s.cardsListed.toLocaleString('it-IT')}` : 'non verificato'} |`,
    `| Prezzi | ${health.data?.prices?.available ? `ok, ${health.data.prices.expansions} set su ${health.data.prices.total_expansions}, il piu' vecchio di ${Math.round(health.data.prices.oldest_expansion_age_hours)} ore` : 'non disponibili'} |`,
    `| Avvisi | ${s.warnings} |`,
    '',
  ];
  const problems = [
    ...health.errors.map(e => ['❌', 'Worker', e]),
    ...health.warnings.map(w => ['⚠️', 'Worker', w]),
    ...results.flatMap(r => [
      ...r.errors.map(e => ['❌', `${r.name} (\`${r.id}\`)`, e]),
      ...r.warnings.map(w => ['⚠️', `${r.name} (\`${r.id}\`)`, w]),
    ]),
  ];
  if (problems.length) {
    lines.push('### Da guardare', '', '| | Dove | Cosa |', '|---|---|---|');
    for (const [icon, where, what] of problems.slice(0, 80)) lines.push(`| ${icon} | ${where} | ${esc(what).replace(/\|/g, '\\|')} |`);
    if (problems.length > 80) lines.push('', `…e altri ${problems.length - 80}: sono tutti nel report HTML.`);
    lines.push('');
    if (problems.some(([, , what]) => /HTTP 404/.test(what))) {
      lines.push('> Un 404 resta nella cache del worker per 24 ore: se l\'immagine e\' stata caricata da poco, svuotare le chiavi `pokewallet:/images/it/<SET>/...` (con `--remote`) prima di crederle mancanti.', '');
    }
  }
  lines.push('La galleria con loghi e carte di ogni set e\' nel report HTML allegato alla run (artifact `report-catalogo`).');
  return lines.join('\n');
}

function htmlReport(s, results, health) {
  const ok = s.errors === 0;
  const badge = (good, text) => `<span class="badge ${good ? 'ok' : 'ko'}">${esc(text)}</span>`;
  const sets = [...results].sort((a, b) => (b.errors.length - a.errors.length) || (b.warnings.length - a.warnings.length));
  const setCards = sets.map(r => `
    <article class="set ${r.errors.length ? 'has-errors' : r.warnings.length ? 'has-warnings' : ''}">
      <header>
        <div class="logo">${r.logo?.ok ? `<img src="${esc(r.logo.url)}" alt="Logo ${esc(r.name)}" loading="lazy">` : '<span class="missing">logo mancante</span>'}</div>
        <div>
          <h3>${esc(r.name)}</h3>
          <p class="meta"><code>${esc(r.id)}</code> · ${esc(r.series ?? '')} · ${r.listed ?? 0} carte</p>
        </div>
        ${r.errors.length ? badge(false, `${r.errors.length} errori`) : r.warnings.length ? '<span class="badge warn">avviso</span>' : badge(true, 'ok')}
      </header>
      <div class="cards">
        ${r.cards.map(c => `
          <figure class="${c.ok ? '' : 'bad'}">
            ${c.ok ? `<img src="${esc(c.url)}" alt="${esc(c.nome)}" loading="lazy">` : `<div class="placeholder">${esc(c.problem)}</div>`}
            <figcaption>${esc(c.nome)}<br><small>${c.ok ? `${c.format} ${c.width}×${c.height} · ${Math.round(c.bytes / 1024)} KB · ${c.ms} ms` : 'non si vede'}</small></figcaption>
          </figure>`).join('')}
      </div>
      ${[...r.errors.map(e => `<p class="msg err">❌ ${esc(e)}</p>`), ...r.warnings.map(w => `<p class="msg warn">⚠️ ${esc(w)}</p>`)].join('')}
      ${r.unreachable?.length ? `<details><summary>${r.unreachable.length} carte senza immagine</summary><p class="list">${r.unreachable.map(c => `${esc(c.nome)} <code>${esc(c.cardId)}</code>`).join(' · ')}</p></details>` : ''}
    </article>`).join('');

  return `<!doctype html>
<html lang="it"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Verifica catalogo</title>
<style>
:root{--bg:#f6f7f9;--card:#fff;--text:#1b1f24;--muted:#5b6470;--line:#e3e6ea;--ok:#1a7f37;--ok-bg:#dafbe1;--ko:#cf222e;--ko-bg:#ffebe9;--warn:#9a6700;--warn-bg:#fff8c5}
@media (prefers-color-scheme:dark){:root{--bg:#0d1117;--card:#161b22;--text:#e6edf3;--muted:#8d96a0;--line:#30363d;--ok:#3fb950;--ok-bg:#12261e;--ko:#f85149;--ko-bg:#2d1214;--warn:#d29922;--warn-bg:#2b2111}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:15px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif}
main{max-width:1180px;margin:0 auto;padding:24px 16px 64px}
h1{font-size:26px;margin:0 0 4px}.sub{color:var(--muted);margin:0 0 20px}
.verdict{padding:16px 20px;border-radius:12px;margin-bottom:20px;font-weight:600;font-size:17px}
.verdict.ok{background:var(--ok-bg);color:var(--ok)}.verdict.ko{background:var(--ko-bg);color:var(--ko)}
.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:12px;margin-bottom:28px}
.stat{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px 16px}
.stat b{display:block;font-size:24px}.stat span{color:var(--muted);font-size:13px}
.sets{display:grid;grid-template-columns:repeat(auto-fill,minmax(340px,1fr));gap:16px}
.set{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px}
.set.has-errors{border-color:var(--ko)}.set.has-warnings{border-color:var(--warn)}
.set header{display:flex;gap:12px;align-items:center}
.set h3{margin:0;font-size:16px}.meta{margin:0;color:var(--muted);font-size:12px}
.logo{width:96px;height:44px;display:flex;align-items:center;justify-content:center;flex:none}
.logo img{max-width:96px;max-height:44px}.missing{color:var(--ko);font-size:12px}
.badge{margin-left:auto;font-size:12px;font-weight:600;padding:2px 10px;border-radius:999px;flex:none}
.badge.ok{background:var(--ok-bg);color:var(--ok)}.badge.ko{background:var(--ko-bg);color:var(--ko)}.badge.warn{background:var(--warn-bg);color:var(--warn)}
.cards{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:12px}
figure{margin:0}figure img,.placeholder{width:100%;aspect-ratio:245/342;border-radius:6px;object-fit:cover;background:var(--bg)}
.placeholder{display:flex;align-items:center;justify-content:center;text-align:center;padding:8px;color:var(--ko);font-size:12px;border:1px dashed var(--ko)}
figcaption{font-size:12px;margin-top:4px;overflow:hidden;text-overflow:ellipsis}figcaption small{color:var(--muted)}
.msg{margin:8px 0 0;font-size:13px}.msg.err{color:var(--ko)}.msg.warn{color:var(--warn)}
code{font-size:12px}details{margin-top:8px;font-size:13px}summary{cursor:pointer;color:var(--ko)}.list{color:var(--muted);font-size:12px;margin:6px 0 0}
</style></head><body><main>
<h1>Verifica catalogo dal vivo</h1>
<p class="sub">${esc(s.when.replace('T', ' ').slice(0, 16))} UTC · worker <code>${esc(BASE)}</code> · durata ${s.seconds}s</p>
<div class="verdict ${ok ? 'ok' : 'ko'}">${ok ? '✅ Tutto quello che l\'app mostra risponde e si vede.' : `❌ ${s.errors} ${s.errors === 1 ? 'problema' : 'problemi'} da guardare: i set coinvolti sono in cima.`}</div>
<section class="stats">
  <div class="stat"><b>${s.expansionsOk} / ${s.expansions}</b><span>espansioni senza problemi</span></div>
  <div class="stat"><b>${s.logosOk} / ${s.logos}</b><span>loghi che si vedono</span></div>
  <div class="stat"><b>${s.imagesOk} / ${s.images}</b><span>immagini carta controllate</span></div>
  <div class="stat"><b>${s.cardsListed.toLocaleString('it-IT')}</b><span>carte in catalogo</span></div>
  <div class="stat"><b>${s.coverageChecked ? (s.cardsListed - s.cardsUnreachable).toLocaleString('it-IT') : '—'}</b><span>${s.coverageChecked ? `carte con immagine raggiungibile · ${s.cardsUnreachable} senza` : 'copertura completa non verificata'}</span></div>
  <div class="stat"><b>${health.data?.prices?.available ? `${health.data.prices.expansions} / ${health.data.prices.total_expansions}` : '—'}</b><span>set con prezzi${health.data?.prices?.available ? ` · il piu' vecchio di ${Math.round(health.data.prices.oldest_expansion_age_hours)} h` : ''}</span></div>
  <div class="stat"><b>${s.warnings}</b><span>avvisi</span></div>
</section>
${[...health.errors.map(e => `<p class="msg err">❌ ${esc(e)}</p>`), ...health.warnings.map(w => `<p class="msg warn">⚠️ ${esc(w)}</p>`)].join('')}
<section class="sets">${setCards}</section>
</main></body></html>`;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const startedAt = Date.now();

  const listRes = await get(`${BASE}/v1/expansions`);
  if (listRes.status !== 200) {
    console.error(`/v1/expansions ha risposto ${listRes.status || listRes.error}: senza elenco non si puo' verificare niente.`);
    process.exit(1);
  }
  let expansions = JSON.parse(listRes.body.toString('utf8')).expansions ?? [];
  if (args.only) expansions = expansions.filter(e => args.only.has(e.id.toLowerCase()));
  console.log(`Espansioni da verificare: ${expansions.length}`);

  const health = await checkHealth();
  let r2Keys = null;
  if (CF_TOKEN) {
    try {
      r2Keys = await listR2Keys();
      console.log(`Oggetti su R2 sotto it/: ${r2Keys.size}`);
    } catch (error) {
      health.errors.push(`Copertura completa non verificata: ${error.message}`);
    }
  } else {
    health.warnings.push('Copertura completa saltata: manca CLOUDFLARE_API_TOKEN, controllate solo 3 carte per set');
  }

  let done = 0;
  const results = await pool(expansions, async e => {
    const r = await checkExpansion(e, r2Keys);
    done += 1;
    if (r.errors.length) console.log(`  [${done}/${expansions.length}] ${e.id}: ${r.errors.join('; ')}`);
    else if (done % 20 === 0) console.log(`  [${done}/${expansions.length}] ...`);
    return r;
  });

  const s = summarize(results, health, startedAt);
  const md = markdownReport(s, results, health);
  mkdirSync(args.out, { recursive: true });
  writeFileSync(path.join(args.out, 'report.md'), md);
  writeFileSync(path.join(args.out, 'report.html'), htmlReport(s, results, health));
  writeFileSync(path.join(args.out, 'risultati.json'), JSON.stringify({ summary: s, health, results }, null, 1));
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, md + '\n');

  console.log('');
  console.log(md);
  process.exit(s.errors === 0 ? 0 : 1);
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
