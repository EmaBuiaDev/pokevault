#!/usr/bin/env node
// Confronta le rotte del catalogo ITALIANO fra due Worker (di solito la
// produzione e una versione nuova): devono rispondere uguale, byte per byte.
// E' la rete di sicurezza del piano carte EN: ogni cambio al Worker si prova
// cosi' prima di arrivare alla produzione.
//
// Sola lettura (solo GET). Le rotte che dipendono dalla cache KV dei prezzi
// (/ita/prices*, /v1/health) restano fuori: lo staging ha una cache sua.
//
// Uso:
//   node scripts/confronta-rotte.mjs                       (produzione vs staging del catalogo)
//   node scripts/confronta-rotte.mjs --a <url> --b <url>
//   node scripts/confronta-rotte.mjs --images 3            (immagini per set, default 2)
import { createHash } from 'node:crypto';

const args = process.argv.slice(2);
const arg = (name, fallback) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : fallback;
};
const A = arg('--a', 'https://pokevault-proxy.pokevault-emanu.workers.dev').replace(/\/+$/, '');
const B = arg('--b', 'https://pokevault-catalog-staging.pokevault-emanu.workers.dev').replace(/\/+$/, '');
const IMAGES_PER_SET = Number(arg('--images', '2'));
const CONCURRENCY = 6;

async function get(base, path) {
  for (let attempt = 1; ; attempt += 1) {
    try {
      const res = await fetch(base + path);
      const body = Buffer.from(await res.arrayBuffer());
      return { status: res.status, type: res.headers.get('content-type') || '', body };
    } catch (err) {
      if (attempt >= 3) return { status: 0, type: '', body: Buffer.from(String(err)) };
      await new Promise(r => setTimeout(r, 1000 * attempt));
    }
  }
}

const sha = buf => createHash('sha256').update(buf).digest('hex').slice(0, 16);

async function pool(items, fn) {
  let next = 0;
  await Promise.all(Array.from({ length: CONCURRENCY }, async () => {
    while (next < items.length) {
      const i = next++;
      await fn(items[i], i);
    }
  }));
}

async function main() {
  const listing = await get(A, '/v1/expansions');
  if (listing.status !== 200) throw new Error(`/v1/expansions su A: HTTP ${listing.status}`);
  const { expansions } = JSON.parse(listing.body.toString());

  const routes = ['/v1/expansions', '/v1/illustrators', '/ita/catalog.json'];
  for (const e of expansions) {
    routes.push(`/v1/expansions/${encodeURIComponent(e.id)}/cards`);
    const code = (e.baseSetCode || e.id).toUpperCase();
    routes.push(`/sets/${encodeURIComponent(code)}/image?source=ita`);
    routes.push(`/sets/${encodeURIComponent(code)}/symbol`);
  }

  // Immagini: le prime N carte di ogni set, con lo stesso percorso dell'app.
  const cardLists = new Map();
  await pool(expansions, async (e) => {
    const r = await get(A, `/v1/expansions/${encodeURIComponent(e.id)}/cards`);
    if (r.status === 200) cardLists.set(e.id, JSON.parse(r.body.toString()).cards || []);
  });
  for (const cards of cardLists.values()) {
    for (const c of cards.slice(0, IMAGES_PER_SET)) {
      const m = /^([A-Za-z0-9-]+)_IT_([A-Za-z0-9_]+)\./.exec(c.cardId || '');
      if (m) routes.push(`/images/it/${m[1].toUpperCase()}/${m[2]}?size=low`);
    }
  }

  // Qualche illustratore e qualche carta singola.
  const illustrators = await get(A, '/v1/illustrators');
  if (illustrators.status === 200) {
    const list = JSON.parse(illustrators.body.toString()).illustrators || [];
    for (const it of list.slice(0, 5)) {
      const key = it.key || it.name;
      if (key) routes.push(`/v1/illustrators/${encodeURIComponent(key)}/cards`);
    }
  }
  for (const cards of [...cardLists.values()].slice(0, 5)) {
    if (cards[0]?.cardId) routes.push(`/v1/cards/${encodeURIComponent(cards[0].cardId)}`);
  }

  console.log(`Confronto ${routes.length} rotte\n  A = ${A}\n  B = ${B}`);
  const diffs = [];
  let same = 0;
  await pool(routes, async (path) => {
    const [a, b] = await Promise.all([get(A, path), get(B, path)]);
    if (a.status === b.status && a.body.equals(b.body)) {
      same += 1;
      return;
    }
    diffs.push({ path, a: `${a.status} ${a.body.length}B ${sha(a.body)}`, b: `${b.status} ${b.body.length}B ${sha(b.body)}` });
  });

  console.log(`\nUguali: ${same}/${routes.length}`);
  if (diffs.length) {
    console.log(`Diverse: ${diffs.length}`);
    for (const d of diffs.sort((x, y) => x.path.localeCompare(y.path)).slice(0, 60)) {
      console.log(`  ${d.path}\n    A ${d.a}\n    B ${d.b}`);
    }
    process.exit(1);
  }
  console.log('Nessuna differenza.');
}

main().catch(err => { console.error(err); process.exit(1); });
