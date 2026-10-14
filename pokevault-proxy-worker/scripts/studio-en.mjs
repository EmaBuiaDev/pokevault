#!/usr/bin/env node
// Studio del catalogo inglese (Fase 0 del piano carte EN, 13/10/2026).
//
// SOLA LETTURA: non tocca D1, R2, KV ne' il codice. Incrocia tre fonti:
//   - il catalogo italiano dal vivo (/v1/expansions e /v1/expansions/{id}/cards);
//   - TCGdex /v2/en (fonte principale dei dati EN);
//   - pokemontcg.io (seconda fonte: completezza e immagini).
// e scrive <out>/studio-en.md + <out>/studio-en.json con:
//   A. set gemelli IT->EN: numerazione a confronto carta per carta;
//   B. set solo EN: carte, immagini TCGdex, carte e immagini pokemontcg.io;
//   C. codici dei set solo EN che collidono con codici/sigle italiane;
//   D. copertura idProduct Cardmarket (campione) sui set solo EN.
//
// Uso:
//   node scripts/studio-en.mjs --out <cartella>
//   node scripts/studio-en.mjs --out <cartella> --idproduct-sample 8
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { TCGDEX_ID_OVERRIDES } from './lib/tcgdex-set-id-map.mjs';

const WORKER = (process.env.POKEVAULT_WORKER_URL || 'https://pokevault-proxy.pokevault-emanu.workers.dev').replace(/\/+$/, '');
const TCGDEX = 'https://api.tcgdex.net/v2/en';
const PTCG = 'https://api.pokemontcg.io/v2';
const CONCURRENCY = 6;

function parseArgs(argv) {
  const args = { out: 'studio-en', sample: 5 };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--out') args.out = argv[++i];
    else if (argv[i] === '--idproduct-sample') args.sample = Number(argv[++i]);
  }
  return args;
}

// Cache su disco delle risposte (cartella <out>/cache): rilanciare lo studio
// non riscarica cio' che e' gia' arrivato. Le risposte del Worker non si cachano.
let CACHE_DIR = null;

async function getJson(url, tries = 6) {
  const cacheFile = CACHE_DIR && !url.startsWith(WORKER) ? path.join(CACHE_DIR, `${createHash('sha1').update(url).digest('hex')}.json`) : null;
  if (cacheFile && existsSync(cacheFile)) return JSON.parse(readFileSync(cacheFile, 'utf8'));
  for (let attempt = 1; ; attempt += 1) {
    try {
      const res = await fetch(url, { headers: { accept: 'application/json' } });
      if (res.status === 404) return null;
      if (!res.ok) throw new Error(`${res.status} ${url}`);
      const j = await res.json();
      if (cacheFile) writeFileSync(cacheFile, JSON.stringify(j));
      return j;
    } catch (err) {
      if (attempt >= tries) throw err;
      await new Promise(r => setTimeout(r, 2000 * attempt));
    }
  }
}

async function pool(items, fn, concurrency = CONCURRENCY) {
  const out = new Array(items.length);
  let next = 0;
  await Promise.all(Array.from({ length: concurrency }, async () => {
    while (next < items.length) {
      const i = next++;
      out[i] = await fn(items[i], i);
    }
  }));
  return out;
}

// "001" -> "1", "SV001" resta "SV001", "24a" resta "24a" (vedi promo numerati a lettere).
const normNumber = n => String(n ?? '').trim().replace(/^0+(?=\d)/, '').toLowerCase();
const normName = s => String(s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]/g, '');
const normCode = s => String(s ?? '').toLowerCase().replace(/[^a-z0-9]/g, '');
const pct = (a, b) => (b ? `${Math.round((100 * a) / b)}%` : '-');

// Il numero di una carta italiana sta nel cardId: SV08_IT_12.png -> 12.
const itNumber = cardId => {
  const m = /_IT_([A-Za-z0-9_]+)\.(png|webp|jpe?g)$/i.exec(cardId || '');
  return m ? normNumber(m[1]) : null;
};

async function loadItalian() {
  const { expansions } = await getJson(`${WORKER}/v1/expansions`);
  const withCards = await pool(expansions, async exp => {
    const j = await getJson(`${WORKER}/v1/expansions/${encodeURIComponent(exp.id)}/cards`);
    const cards = (j?.cards || []).map(c => ({ number: itNumber(c.cardId), name: c.nome })).filter(c => c.number);
    return { ...exp, cards };
  });
  return withCards;
}

async function loadTcgdex() {
  const list = await getJson(`${TCGDEX}/sets`);
  const sets = await pool(list, async s => {
    const d = await getJson(`${TCGDEX}/sets/${encodeURIComponent(s.id)}`);
    if (!d) return null;
    return {
      id: d.id, name: d.name, serie: d.serie?.id || '', releaseDate: d.releaseDate || '',
      official: d.cardCount?.official ?? null, abbreviation: d.abbreviation?.official || '',
      cards: (d.cards || []).map(c => ({ id: c.id, number: normNumber(c.localId), name: c.name, hasImage: Boolean(c.image) })),
    };
  });
  return sets.filter(Boolean);
}

async function loadPtcg() {
  const { data: sets } = await getJson(`${PTCG}/sets?pageSize=250&select=id,name,series,printedTotal,total,ptcgoCode,releaseDate`);
  // pokemontcg.io risponde 500 sotto carico: due richieste alla volta, e un set
  // che fallisce finisce nel report come errore invece di fermare lo studio.
  return pool(sets, async s => {
    const cards = [];
    let error = null;
    try {
      for (let page = 1; ; page += 1) {
        const j = await getJson(`${PTCG}/cards?q=set.id:${encodeURIComponent(s.id)}&select=number,name,images&pageSize=250&page=${page}`);
        const batch = j?.data || [];
        for (const c of batch) cards.push({ number: normNumber(c.number), name: c.name, hasImage: Boolean(c.images?.large || c.images?.small) });
        if (batch.length < 250) break;
      }
    } catch (err) {
      error = String(err.message || err);
      console.warn(`  pokemontcg.io ${s.id}: ${error}`);
    }
    return { ...s, releaseDate: String(s.releaseDate || '').replaceAll('/', '-'), cards, error };
  }, 2);
}

// Abbina un set TCGdex al set pokemontcg.io: prima stesso nome e data, poi id
// normalizzato (sv08 ~ sv8, sv03.5 ~ sv3pt5), poi solo nome.
function matchPtcg(tset, ptcgSets) {
  const name = normName(tset.name);
  const idNorm = normCode(tset.id.replace(/\.5/g, 'pt5')).replace(/([a-z]+)0+(\d)/g, '$1$2');
  return ptcgSets.find(p => normName(p.name) === name && p.releaseDate === tset.releaseDate)
    || ptcgSets.find(p => normCode(p.id).replace(/([a-z]+)0+(\d)/g, '$1$2') === idNorm)
    || ptcgSets.find(p => normName(p.name) === name)
    || null;
}

function compareNumbers(aCards, bCards) {
  const a = new Set(aCards.map(c => c.number));
  const b = new Set(bCards.map(c => c.number));
  return {
    onlyA: [...a].filter(n => !b.has(n)),
    onlyB: [...b].filter(n => !a.has(n)),
    both: [...a].filter(n => b.has(n)).length,
  };
}

async function sampleIdProduct(tset, size) {
  const sample = tset.cards.filter((_, i) => i % Math.max(1, Math.floor(tset.cards.length / size)) === 0).slice(0, size);
  const got = await pool(sample, async c => {
    const d = await getJson(`${TCGDEX}/cards/${encodeURIComponent(c.id)}`);
    return Boolean(d?.pricing?.cardmarket?.idProduct);
  });
  return { checked: got.length, withIdProduct: got.filter(Boolean).length };
}

const fmtList = (xs, max = 12) => (xs.length ? `${xs.slice(0, max).join(', ')}${xs.length > max ? ` …(+${xs.length - max})` : ''}` : '');

async function main() {
  const args = parseArgs(process.argv.slice(2));
  mkdirSync(args.out, { recursive: true });
  CACHE_DIR = path.join(args.out, 'cache');
  mkdirSync(CACHE_DIR, { recursive: true });

  console.log('Catalogo italiano…');
  const italian = await loadItalian();
  console.log(`  ${italian.length} set, ${italian.reduce((a, e) => a + e.cards.length, 0)} carte`);
  console.log('TCGdex EN…');
  const tcgdex = await loadTcgdex();
  console.log(`  ${tcgdex.length} set`);
  console.log('pokemontcg.io…');
  const ptcg = await loadPtcg();
  console.log(`  ${ptcg.length} set, ${ptcg.reduce((a, s) => a + s.cards.length, 0)} carte`);

  const tcgdexById = new Map(tcgdex.map(s => [s.id.toLowerCase(), s]));
  const usedTcgdex = new Set();

  // A. gemelli
  const twins = italian.map(exp => {
    const tid = (TCGDEX_ID_OVERRIDES[exp.id] || exp.id).toLowerCase();
    const en = tcgdexById.get(tid) || null;
    if (en) usedTcgdex.add(en.id);
    if (!en) return { it: exp.id, itName: exp.name, itCards: exp.cards.length, en: null };
    const cmp = compareNumbers(exp.cards, en.cards);
    const enByNum = new Map(en.cards.map(c => [c.number, c]));
    // I nomi dei Pokemon coincidono quasi sempre fra IT ed EN: misura grezza
    // per scoprire numerazioni sfasate (stesso numero, carta diversa).
    const sameName = exp.cards.filter(c => enByNum.has(c.number) && normName(enByNum.get(c.number).name) === normName(c.name)).length;
    // Immagini: TCGdex oppure pokemontcg.io (set abbinato per nome/data/id).
    const p = matchPtcg(en, ptcg);
    const imgNumbers = new Set([...en.cards.filter(c => c.hasImage), ...(p?.cards || []).filter(c => c.hasImage)].map(c => c.number));
    const noImageAnywhere = en.cards.map(c => c.number).filter(n => !imgNumbers.has(n));
    return {
      ptcg: p?.id || null, noImageAnywhere,
      it: exp.id, itName: exp.name, itCards: exp.cards.length,
      en: en.id, enName: en.name, enCards: en.cards.length, enImages: en.cards.filter(c => c.hasImage).length,
      onlyIt: cmp.onlyA, onlyEn: cmp.onlyB, both: cmp.both, sameName,
    };
  });

  // B. solo EN (TCG Pocket escluso: e' il gioco digitale)
  const enOnly = await pool(tcgdex.filter(s => !usedTcgdex.has(s.id) && s.serie !== 'tcgp'), async s => {
    const p = matchPtcg(s, ptcg);
    const cmp = p ? compareNumbers(s.cards, p.cards) : null;
    const tcgdexImg = new Set(s.cards.filter(c => c.hasImage).map(c => c.number));
    const ptcgImg = new Set((p?.cards || []).filter(c => c.hasImage).map(c => c.number));
    const allNumbers = new Set([...s.cards.map(c => c.number), ...(p?.cards || []).map(c => c.number)]);
    const noImage = [...allNumbers].filter(n => !tcgdexImg.has(n) && !ptcgImg.has(n));
    const idp = args.sample > 0 && s.cards.length ? await sampleIdProduct(s, args.sample) : null;
    return {
      id: s.id, name: s.name, serie: s.serie, releaseDate: s.releaseDate, abbreviation: s.abbreviation,
      tcgdexCards: s.cards.length, tcgdexImages: tcgdexImg.size,
      ptcg: p?.id || null, ptcgCards: p?.cards.length ?? 0, ptcgImages: ptcgImg.size,
      onlyTcgdex: cmp?.onlyA || [], onlyPtcg: cmp?.onlyB || [],
      union: allNumbers.size, noImageAnywhere: noImage, idProduct: idp,
    };
  });
  enOnly.sort((a, b) => (a.releaseDate < b.releaseDate ? 1 : -1));

  // C. collisioni dei codici solo EN con codici/sigle italiani
  const itCodes = new Map();
  for (const e of italian) {
    for (const c of [e.id, e.baseSetCode, e.abbreviation]) if (c) itCodes.set(normCode(c), e.id);
  }
  const collisions = [];
  for (const s of enOnly) {
    for (const c of [s.id, s.abbreviation]) {
      const hit = c && itCodes.get(normCode(c));
      if (hit) collisions.push({ en: s.id, code: c, it: hit });
    }
  }

  // set pokemontcg.io senza corrispondente TCGdex (possibili buchi di TCGdex)
  const matchedPtcg = new Set([...twins.filter(t => t.en).map(t => matchPtcg(tcgdexById.get(t.en.toLowerCase()), ptcg)?.id), ...enOnly.map(s => s.ptcg)].filter(Boolean));
  const ptcgOrphans = ptcg.filter(p => !matchedPtcg.has(p.id)).map(p => ({ id: p.id, name: p.name, releaseDate: p.releaseDate, cards: p.cards.length }));

  const ptcgErrors = ptcg.filter(p => p.error).map(p => ({ id: p.id, error: p.error }));
  const report = { generatedAt: new Date().toISOString(), twins, enOnly, collisions, ptcgOrphans, ptcgErrors };
  writeFileSync(path.join(args.out, 'studio-en.json'), JSON.stringify(report, null, 2));

  // Markdown
  const L = [];
  const twinsOk = twins.filter(t => t.en);
  const twinsExact = twinsOk.filter(t => !t.onlyIt.length);
  L.push(`# Studio catalogo EN (${report.generatedAt.slice(0, 10)})`, '');
  L.push(`- Set italiani: ${italian.length}; con gemello TCGdex EN: ${twinsOk.length}; senza: ${twins.length - twinsOk.length}.`);
  L.push(`- Carte EN dei set gemelli senza immagine in nessuna fonte: ${twinsOk.reduce((a, t) => a + t.noImageAnywhere.length, 0)} su ${twinsOk.reduce((a, t) => a + t.enCards, 0)}.`);
  L.push(`- Gemelli in cui ogni numero IT esiste anche in EN: ${twinsExact.length}/${twinsOk.length}.`);
  L.push(`- Set solo EN (Pocket escluso): ${enOnly.length}, ${enOnly.reduce((a, s) => a + s.union, 0)} carte (unione TCGdex + pokemontcg.io), senza immagine in nessuna fonte: ${enOnly.reduce((a, s) => a + s.noImageAnywhere.length, 0)}.`);
  L.push(`- Collisioni di codice: ${collisions.length}. Set pokemontcg.io senza corrispondente TCGdex: ${ptcgOrphans.length}.`, '');

  L.push('## A. Set gemelli IT → EN', '', '| IT | EN | carte IT | carte EN | img TCGdex | ptcg | senza img | stesso nome | solo IT | solo EN |', '|---|---|---|---|---|---|---|---|---|---|');
  for (const t of twins) {
    if (!t.en) { L.push(`| ${t.it} (${t.itName}) | **nessuno** | ${t.itCards} | | | | | | | |`); continue; }
    L.push(`| ${t.it} | ${t.en} ${t.enName} | ${t.itCards} | ${t.enCards} | ${pct(t.enImages, t.enCards)} | ${t.ptcg || '-'} | ${t.noImageAnywhere.length} | ${pct(t.sameName, t.itCards)} | ${fmtList(t.onlyIt)} | ${fmtList(t.onlyEn)} |`);
  }
  L.push('', '## B. Set solo EN', '', '| set | uscita | serie | TCGdex carte/img | pokemontcg.io | solo TCGdex | solo ptcg | senza immagine | idProduct (campione) |', '|---|---|---|---|---|---|---|---|---|');
  for (const s of enOnly) {
    const idp = s.idProduct ? `${s.idProduct.withIdProduct}/${s.idProduct.checked}` : '-';
    L.push(`| ${s.id} ${s.name} | ${s.releaseDate} | ${s.serie} | ${s.tcgdexCards}/${s.tcgdexImages} | ${s.ptcg ? `${s.ptcg} ${s.ptcgCards}/${s.ptcgImages}` : '**nessuno**'} | ${fmtList(s.onlyTcgdex, 6)} | ${fmtList(s.onlyPtcg, 6)} | ${s.noImageAnywhere.length} | ${idp} |`);
  }
  L.push('', '## C. Collisioni di codice', '');
  for (const c of collisions) L.push(`- EN \`${c.en}\` usa \`${c.code}\`, che in IT e' gia' \`${c.it}\``);
  if (!collisions.length) L.push('Nessuna.');
  if (ptcgErrors.length) L.push('', `**pokemontcg.io non ha risposto per ${ptcgErrors.length} set** (rilanciare: la cache tiene il resto): ${ptcgErrors.map(e => e.id).join(', ')}`);
  L.push('', '## D. Set pokemontcg.io senza corrispondente TCGdex', '');
  for (const p of ptcgOrphans) L.push(`- ${p.id} ${p.name} (${p.releaseDate}), ${p.cards} carte`);
  if (!ptcgOrphans.length) L.push('Nessuno.');
  writeFileSync(path.join(args.out, 'studio-en.md'), `${L.join('\n')}\n`);
  console.log(`Report in ${path.resolve(args.out)}`);
}

main().catch(err => { console.error(err); process.exit(1); });
