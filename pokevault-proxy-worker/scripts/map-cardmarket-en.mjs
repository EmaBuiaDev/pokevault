#!/usr/bin/env node
// Collega ogni carta del catalogo EN al suo prodotto Cardmarket (idProduct) e
// copia le stampe (piano carte EN, Fase 2, 13/10/2026).
//
// Scrive SOLO nel database inglese (pokevault-catalog-en): card_cardmarket e
// card_variants. Il catalogo italiano lo legge e basta.
//
//   - Carta di un set GEMELLO con la stessa carta in italiano: stesso
//     idProduct e stesse stampe della carta italiana. Il listino Cardmarket non
//     distingue la lingua, e il collegamento italiano e' gia' stato verificato
//     (map-cardmarket-tcgdex.mjs, con l'arbitro): non si rifa'.
//   - Tutte le altre (set solo EN, carte che in italiano non ci sono):
//     l'idProduct che TCGdex associa alla carta (pricing.cardmarket.idProduct).
//
// Rilanciabile: riscrive solo cio' che e' cambiato.
//
//   node scripts/map-cardmarket-en.mjs                 (prova: solo i conteggi)
//   node scripts/map-cardmarket-en.mjs --apply
//   node scripts/map-cardmarket-en.mjs --apply --only sv08,pl3
//   node scripts/map-cardmarket-en.mjs --apply --only-missing   (TCGdex solo per le carte non ancora collegate)
import { writeFile, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { d1Select, priceKey, runWrangler, sqlString, workerRoot } from './lib/cardmarket.mjs';

const EN_DB = 'pokevault-catalog-en';
const TCGDEX = 'https://api.tcgdex.net/v2/en';
const CONCURRENCY = 6;
const args = process.argv.slice(2);
const APPLY = args.includes('--apply');
const ONLY_MISSING = args.includes('--only-missing');
const onlyIdx = args.indexOf('--only');
const ONLY = onlyIdx >= 0 ? new Set(args[onlyIdx + 1].split(',').map(s => s.trim().toLowerCase())) : null;
const tmpDir = path.join(workerRoot, 'scripts', '.map-cardmarket-en-tmp');

async function enSelect(sql) {
  const oneLine = sql.replace(/\s+/g, ' ').trim();
  const out = await runWrangler(['d1', 'execute', EN_DB, '--remote', '--json', '--command', `"${oneLine.replace(/"/g, '\\"')}"`]);
  const start = out.indexOf('[');
  return JSON.parse(start >= 0 ? out.slice(start) : out)[0]?.results ?? [];
}

async function fetchJson(url, attempts = 5) {
  for (let i = 1; ; i += 1) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 30000);
    try {
      const res = await fetch(url, { signal: controller.signal });
      if (res.status === 404) return null;
      if (!res.ok) throw new Error(`${url} -> HTTP ${res.status}`);
      return await res.json();
    } catch (err) {
      if (i >= attempts) return null;
      await new Promise(r => setTimeout(r, 1500 * i));
    } finally {
      clearTimeout(timer);
    }
  }
}

async function pool(items, fn) {
  const out = new Array(items.length);
  let next = 0;
  await Promise.all(Array.from({ length: CONCURRENCY }, async () => {
    while (next < items.length) {
      const i = next++;
      out[i] = await fn(items[i], i);
    }
  }));
  return out;
}

const sqlOrNull = v => (v === null || v === undefined ? 'NULL' : sqlString(v));

async function main() {
  const enExpansions = (await enSelect('SELECT id, tcgdex_id, it_expansion_id FROM expansions'))
    .filter(e => !ONLY || ONLY.has(e.id));
  const enCards = (await enSelect('SELECT card_id, expansion_id, card_number FROM cards'))
    .filter(c => !ONLY || ONLY.has(c.expansion_id));
  const enExisting = new Map((await enSelect('SELECT card_id, id_product, source FROM card_cardmarket')).map(r => [r.card_id, r]));
  console.log(`Catalogo EN: ${enExpansions.length} set, ${enCards.length} carte, ${enExisting.size} gia' collegate`);

  // Le carte italiane gemelle, per (espansione, numero).
  const twinIds = [...new Set(enExpansions.map(e => e.it_expansion_id).filter(Boolean))];
  const itByKey = new Map();
  const itVariants = new Map();
  if (twinIds.length) {
    const inList = twinIds.map(sqlString).join(', ');
    const itRows = await d1Select(
      `SELECT c.card_id, c.expansion_id, c.card_number, m.id_product FROM cards c JOIN card_cardmarket m ON m.card_id = c.card_id WHERE c.expansion_id IN (${inList})`
    );
    for (const r of itRows) itByKey.set(`${r.expansion_id}|${priceKey(r.card_number)}`, r);
    const variantRows = await d1Select(
      `SELECT v.card_id, v.variant, v.kind, v.type, v.stamp, v.foil, v.id_product, v.source FROM card_variants v JOIN cards c ON c.card_id = v.card_id WHERE c.expansion_id IN (${inList})`
    );
    for (const v of variantRows) {
      if (!itVariants.has(v.card_id)) itVariants.set(v.card_id, []);
      itVariants.get(v.card_id).push(v);
    }
    console.log(`Gemelli italiani: ${itRows.length} carte collegate, stampe per ${itVariants.size} carte`);
  }

  const expansionById = new Map(enExpansions.map(e => [e.id, e]));
  const links = new Map(); // card_id EN -> { id_product, source }
  const variants = [];      // righe card_variants per le carte EN
  const needTcgdex = new Map(); // tcgdex_id -> [card EN]

  for (const card of enCards) {
    const exp = expansionById.get(card.expansion_id);
    if (!exp) continue;
    const twin = exp.it_expansion_id ? itByKey.get(`${exp.it_expansion_id}|${priceKey(card.card_number)}`) : null;
    if (twin) {
      links.set(card.card_id, { id_product: twin.id_product, source: 'it-twin' });
      for (const v of itVariants.get(twin.card_id) ?? []) variants.push({ ...v, card_id: card.card_id, source: `it-twin:${v.source}` });
    } else if (ONLY_MISSING && enExisting.has(card.card_id)) {
      // Gia' collegata da un giro precedente: niente chiamata a TCGdex.
      continue;
    } else {
      if (!needTcgdex.has(exp.tcgdex_id)) needTcgdex.set(exp.tcgdex_id, []);
      needTcgdex.get(exp.tcgdex_id).push(card);
    }
  }
  console.log(`Dal gemello italiano: ${links.size}; da cercare su TCGdex: ${[...needTcgdex.values()].reduce((a, l) => a + l.length, 0)}`);

  // TCGdex: l'id della carta va preso dall'elenco del set (lo zero-padding e' suo).
  let fromTcgdex = 0;
  let withoutProduct = 0;
  for (const [tcgdexId, cards] of needTcgdex) {
    const set = await fetchJson(`${TCGDEX}/sets/${encodeURIComponent(tcgdexId)}`);
    const idByNumber = new Map((set?.cards || []).map(c => [priceKey(c.localId), c.id]));
    await pool(cards, async (card) => {
      const tcgdexCardId = idByNumber.get(priceKey(card.card_number));
      const detail = tcgdexCardId ? await fetchJson(`${TCGDEX}/cards/${encodeURIComponent(tcgdexCardId)}`) : null;
      const idProduct = detail?.pricing?.cardmarket?.idProduct;
      if (idProduct) {
        links.set(card.card_id, { id_product: idProduct, source: 'tcgdex' });
        fromTcgdex += 1;
      } else {
        withoutProduct += 1;
      }
    });
  }
  console.log(`Da TCGdex: ${fromTcgdex}; senza prodotto Cardmarket: ${withoutProduct}`);

  const changed = [...links].filter(([id, l]) => {
    const old = enExisting.get(id);
    return !old || old.id_product !== l.id_product || old.source !== l.source;
  });
  console.log(`Collegamenti nuovi o cambiati: ${changed.length}; stampe copiate dai gemelli: ${variants.length}`);

  if (!APPLY) {
    console.log('Prova: niente scritto (aggiungi --apply).');
    return;
  }

  await mkdir(tmpDir, { recursive: true });
  const lines = [];
  for (let i = 0; i < changed.length; i += 100) {
    lines.push(
      'INSERT INTO card_cardmarket (card_id, id_product, source) VALUES\n' +
      changed.slice(i, i + 100).map(([id, l]) => `(${sqlString(id)}, ${Number(l.id_product)}, ${sqlString(l.source)})`).join(',\n') +
      '\nON CONFLICT(card_id) DO UPDATE SET id_product = excluded.id_product, source = excluded.source, updated_at = unixepoch();'
    );
  }
  // Le stampe dei gemelli si riscrivono intere, carta per carta (quelle che
  // l'italiano non ha piu' spariscono anche qui), ma SOLO le carte cambiate:
  // riscriverle tutte ogni giorno sarebbero decine di migliaia di scritture D1.
  const signature = rows => rows
    .map(v => [v.variant, v.kind, v.type, v.stamp ?? '', v.foil ?? '', v.id_product ?? '', v.source].join('|'))
    .sort()
    .join('\n');
  const existingVariants = new Map();
  for (const v of await enSelect('SELECT card_id, variant, kind, type, stamp, foil, id_product, source FROM card_variants')) {
    if (!existingVariants.has(v.card_id)) existingVariants.set(v.card_id, []);
    existingVariants.get(v.card_id).push(v);
  }
  const wantedVariants = new Map();
  for (const v of variants) {
    if (!wantedVariants.has(v.card_id)) wantedVariants.set(v.card_id, []);
    wantedVariants.get(v.card_id).push(v);
  }
  const variantCards = [...wantedVariants.keys()]
    .filter(id => signature(wantedVariants.get(id)) !== signature(existingVariants.get(id) ?? []));
  const changedVariants = variantCards.flatMap(id => wantedVariants.get(id));
  console.log(`Carte con stampe da riscrivere: ${variantCards.length}`);
  for (let i = 0; i < variantCards.length; i += 200) {
    lines.push(`DELETE FROM card_variants WHERE card_id IN (${variantCards.slice(i, i + 200).map(sqlString).join(', ')});`);
  }
  for (let i = 0; i < changedVariants.length; i += 100) {
    lines.push(
      'INSERT INTO card_variants (card_id, variant, kind, type, stamp, foil, id_product, source) VALUES\n' +
      changedVariants.slice(i, i + 100).map(v => `(${[sqlString(v.card_id), sqlString(v.variant), sqlString(v.kind), sqlString(v.type), sqlOrNull(v.stamp), sqlOrNull(v.foil), v.id_product ?? 'NULL', sqlString(v.source)].join(', ')})`).join(',\n') +
      '\nON CONFLICT(card_id, variant) DO UPDATE SET kind = excluded.kind, type = excluded.type, stamp = excluded.stamp, foil = excluded.foil, id_product = excluded.id_product, source = excluded.source;'
    );
  }
  if (!lines.length) {
    console.log('Niente da scrivere.');
    return;
  }
  const file = path.join(tmpDir, 'map-cardmarket-en.sql');
  await writeFile(file, lines.join('\n\n'), 'utf8');
  await runWrangler(['d1', 'execute', EN_DB, '--remote', '--yes', `--file="${file}"`], {}, 1);
  await rm(tmpDir, { recursive: true, force: true });
  console.log('Scritto nel catalogo EN.');
}

main().catch((err) => {
  console.error('Errore:', err.message || err);
  process.exit(1);
});
