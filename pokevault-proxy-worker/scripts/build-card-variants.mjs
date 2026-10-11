#!/usr/bin/env node
// Le stampe vere di ogni carta del catalogo: quali esistono (Normale, Reverse,
// Holo, 1a Edizione, e le speciali con timbro o lavorazione) e, per ognuna, il
// prodotto Cardmarket che ne da' il prezzo.
//
// Perche' esiste: l'app deduceva le stampe dalla rarita' ("una Comune dopo il
// 2002 ha Normale e Reverse"), e sbagliava interi set -- il 30 Anniversario non
// ha reverse, 151 ha anche le reverse Poke Ball e Master Ball, i set Wizards
// hanno la 1a Edizione, e le timbrate (Pokemon Day, prerelease...) non
// c'erano proprio.
//
// Le fonti, perche' nessuna basta da sola (misurato il 10/10/2026):
//  - pokemontcg.io: rarita' esatta ("Rare Holo" compresa) e stampe dai prezzi
//    TCGplayer (normal / reverseHolofoil / holofoil / 1stEdition...). E' la
//    fonte delle stampe base dove ha il set.
//  - TCGdex `variants_detailed` (EN): una voce per stampa, con tipo, timbri,
//    lavorazione e idProduct Cardmarket. Unica fonte delle stampe SPECIALI
//    (timbrate, Poke Ball, Master Ball, cosmo...) e della 1a Edizione.
//    Affidabile sulle base da SWSH in poi, su DP/EX e Wizards; per Nero e
//    Bianco, XY e Sole e Luna dice "solo normale" anche dove la reverse c'e'.
//  - Il listino Cardmarket: un prezzo nelle colonne "-holo" di una carta NON
//    olografica dice che la reverse esiste (sul 30 nessuno, su xy1/sm1 tutte
//    le comuni). Serve a capire per set se TCGdex regge, e alla regola.
//  - Le regole dell'era, ultimo ripiego dove nessuno parla: da Leggende
//    (maggio 2002) le semplici escono Normale e Reverse se il set ha le
//    reverse, le Rare Holo Holo e Reverse, le rarita' a stampa unica Holo.
//
// Le stampe base sono quelle di pokemontcg.io, piu' da TCGdex solo cio' che
// pokemontcg.io tipicamente omette (1a Edizione, Reverse, Holo delle rarita'
// olografiche, Normale con un prodotto suo); dove si contraddicono su QUALE
// stampa vince pokemontcg.io (vedi il commento sulla scelta). I Trainer Kit
// stanno su TCGdex: il listino li confonde con il set principale. Un timbro
// presente su tutto il set (il "30th" del 30
// Anniversario) e' la stampa del set, non una speciale. Le timbrate Play!
// Pokemon delle buste premio (serie 1-9) vengono da lib/prize-packs.json,
// cioe' dalle liste ufficiali italiane: TCGdex ne ha meno di un terzo e
// chiama la stessa olografica ora holo, ora reverse. I timbri fuori da
// KNOWN_STAMPS (i giocatori dei World Championship Deck) non entrano e
// finiscono nel rapporto. Il rapporto mostra set per set fonti, conteggi e
// disaccordi; --apply rifiuta di scrivere se pokemontcg.io non ha risposto
// per qualche set (si rilancia: la cache riprova solo quelli).
//
//   node scripts/build-card-variants.mjs <expansionId...> | --all
//        [--cache FILE] [--report FILE] [--guide FILE] [--apply] [--allow-missing-ptcg]
//
// Senza --apply non scrive niente (la tabella e' in schema/015).

import fs from 'node:fs';
import zlib from 'node:zlib';
import path from 'node:path';
import { d1Select, d1ExecuteFile, downloadPriceGuide, sqlString, workerRoot } from './lib/cardmarket.mjs';
import { TCGDEX_ID_OVERRIDES } from './lib/tcgdex-set-id-map.mjs';

const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const values = (name) => args.flatMap((a, i) => (a === name && args[i + 1] ? [args[i + 1]] : []));
const APPLY = flag('--apply');
const ALL = flag('--all');
const CACHE_FILE = values('--cache')[0] ?? null;
const REPORT_FILE = values('--report')[0] ?? null;
const GUIDE_FILES = values('--guide');
const optionValues = new Set([...values('--cache'), ...values('--report'), ...values('--guide')]);
const EXPANSIONS = args.filter((a) => !a.startsWith('--') && !optionValues.has(a));
const CONCURRENCY = 8;
const REVERSE_FROM = '2002-05-24';

if (!ALL && EXPANSIONS.length === 0) {
  console.error('Uso: node scripts/build-card-variants.mjs <expansionId...> | --all [--cache FILE] [--report FILE] [--guide FILE] [--apply]');
  process.exit(2);
}

// ── cache TCGdex (stessa forma di map-cardmarket-tcgdex.mjs) ──
const cache = new Map();
if (CACHE_FILE && fs.existsSync(CACHE_FILE)) {
  for (const line of fs.readFileSync(CACHE_FILE, 'utf8').split('\n')) {
    if (line) { const o = JSON.parse(line); cache.set(o.url, o.value); }
  }
}
const cacheOut = CACHE_FILE ? fs.createWriteStream(CACHE_FILE, { flags: 'a' }) : null;
function remember(url, value) { cache.set(url, value); cacheOut?.write(JSON.stringify({ url, value }) + '\n'); }
async function tcgdex(url, slim) {
  if (cache.has(url)) return cache.get(url);
  let lastErr;
  for (let i = 0; i < 4; i += 1) {
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(20000) });
      if (res.status === 404) { remember(url, null); return null; }
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const value = slim(await res.json());
      remember(url, value);
      return value;
    } catch (err) { lastErr = err; await new Promise((r) => setTimeout(r, 1000 * (i + 1))); }
  }
  throw new Error(`TCGdex ${url}: ${lastErr?.message}`);
}
const slimSet = (j) => ({
  name: j.name ?? null,
  abbreviation: j.abbreviation?.official ?? null,
  cards: (j.cards ?? []).map((c) => ({ id: c.id, localId: c.localId })),
});

// ── pokemontcg.io: rarita' e stampe esatte (dai prezzi TCGplayer) ──
// Risponde a volte 500/502: si riprova, e le risposte buone restano in cache.
async function ptcg(url) {
  if (cache.has(url)) return cache.get(url);
  let lastErr;
  for (let i = 0; i < 8; i += 1) {
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(60000) });
      if (res.status === 404) { remember(url, null); return null; }
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const value = await res.json();
      remember(url, value);
      return value;
    } catch (err) { lastErr = err; await new Promise((r) => setTimeout(r, 3000 * (i + 1))); }
  }
  console.warn(`pokemontcg.io non risponde: ${url} (${lastErr?.message})`);
  return undefined; // non in cache: al prossimo giro si riprova
}
const PTCG_KEY = {
  normal: 'Normal', holofoil: 'Holo', reverseHolofoil: 'Reverse',
  '1stEditionNormal': '1st Edition', '1stEditionHolofoil': '1st Edition Holo',
  unlimitedHolofoil: 'Holo', unlimited: 'Normal', '1stEdition': '1st Edition',
};
const normName = (s) => String(s ?? '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]/g, '');
const slimCard = (j) => ({
  id: j.id,
  rarity: j.rarity ?? null,
  variants: j.variants ?? null,
  detailed: (j.variants_detailed ?? []).map((v) => ({
    type: v.type ?? null, size: v.size ?? null, stamp: v.stamp ?? [], foil: v.foil ?? null,
    idProduct: v.thirdParty?.cardmarket ?? null,
  })),
});
const localKey = (raw) => { const s = String(raw ?? '').trim(); return /^\d+$/.test(s) ? String(parseInt(s, 10)) : s.toUpperCase(); };
async function pool(items, n, fn) {
  const out = new Array(items.length); let i = 0;
  await Promise.all(Array.from({ length: n }, async () => { while (i < items.length) { const k = i++; out[k] = await fn(items[k], k); } }));
  return out;
}

// ── rarita': stessa classificazione di CardOptions.getVariantsForCard ──
const SINGLE_VARIANT_TOKENS = [
  'ace spec', 'illustration rare', 'rare art', 'illustrazione rara', 'futuristic', 'futuristica',
  'shiny', 'ultra rare', 'ultra rara', 'ultrarara', 'full art', 'hyper rare', 'iper rara', 'gold',
  'secret rare', 'rara segreta', 'segret', 'double rare', 'doppia rara', 'vmax', 'vstar', 'holo ex',
  'rare holo v', 'promo', 'mega_attack_rare', 'mega hyper rare', 'legend', 'prime', 'radiant',
  'radiosa', 'amazing', 'lv.x', 'black white rare', 'classic collection', 'pikachu rare', 'rgb rare',
];
function rarityClass(rarity) {
  const r = String(rarity ?? '').toLowerCase().trim();
  if (!r) return 'ignota';
  if (SINGLE_VARIANT_TOKENS.some((t) => r.includes(t))) return 'unica';
  if (r.includes('rare holo') || r.includes('holo rare')) return 'holo';
  if (r === 'rare' || r === 'rara' || r.includes('uncommon') || r === 'non comune' || r.includes('common') || r === 'comune') return 'semplice';
  return 'ignota';
}

// ── chiavi delle stampe ──
// Le base restano le stringhe che l'app salva gia' in Firestore ("Normal",
// "Reverse", "Holo", "1st Edition", "1st Edition Holo"): le carte salvate non
// cambiano. Le speciali aggiungono lavorazione e timbri in un ordine fisso.
const TYPE_BASE = { normal: 'Normal', reverse: 'Reverse', holo: 'Holo' };
function variantKey(type, stamps, foil) {
  const first = stamps.includes('1st-edition');
  const rest = stamps.filter((s) => s !== '1st-edition').sort();
  let base = TYPE_BASE[type];
  if (!base) return null;
  if (first) base = type === 'holo' ? '1st Edition Holo' : '1st Edition';
  const parts = [base];
  if (foil) parts.push(`foil=${foil}`);
  if (rest.length) parts.push(`stamp=${rest.join('+')}`);
  return parts.join('|');
}
const isSpecial = (key) => key.includes('|');

// I timbri che sono stampe della carta. TCGdex mette fra i "timbri" anche i
// nomi dei giocatori dei World Championship Deck (jason-klaczynski,
// chris-fulop...): ristampe col retro diverso, non valide in torneo e solo
// inglesi -- non sono stampe della carta italiana. Un timbro fuori da questa
// lista non entra e finisce nel rapporto ("scartate"), cosi' un timbro vero
// nuovo si vede e si aggiunge qui (e in CardVariants lato app).
const KNOWN_STAMPS = new Set([
  '1st-edition', 'set-logo', 'player-rewards-program', 'staff', 'professor-program', 'pokemon-center',
  'snowflake', 'countdown-calendar', 'pre-release', '25th-celebration', '30th-anniversary', '30th-pokeday',
  'pokemon-day', 'worlds-2023', 'worlds-2024', 'worlds-2025', 'regional-championships', 'state-championships',
  'national-championships', 'city-championships', 'winner', 'top-thirty-two', 'top-sixteen', 'top-eight',
  'semi-finalist', 'finalist', 'wotc', 'pikachu', 'distributor-meeting', 'gamestop', 'eb-games',
  'pokemon-together', 'origins-2008', 'comic-con', 'platinum', 'd-edition-error', 'pikachu-tail',
  '1st-edition-scratch-error', 'ultra-ball-league', 'trick-or-trade', 'asia-2023-24', 'poketour-99', 'w-promo',
  'horizons',
  // Visti nel giro completo del 10/10/2026: eventi, leghe, campionati.
  '10th-anniversary', 'ace-trainer', 'asia-promo', 'champion', 'chicago-2009', 'destiny-deoxys', 'fossil-museum',
  'games-expo', 'gen-con', 'great-ball-league', 'gym-challenge', 'inquest-gamer', 'international-championship-europe',
  'international-championship-latin-america', 'international-championship-north-america', 'judge', 'kraze-club',
  'master-ball-league', 'nintendo-world', 'origins', 'poke-ball-league', 'pokemon-rocks-america', 'quarter-finalist',
  'scrye', 'stadium-challenge', 'thank-you', 'wizard-world-chicago', 'wizard-world-philadelphia', 'worlds-2022',
]);

// ── 1. catalogo ──
const scope = ALL ? '' : `AND c.expansion_id IN (${EXPANSIONS.map(sqlString).join(',')})`;
const cards = await d1Select(
  `SELECT c.card_id, c.expansion_id, c.card_number, c.rarity, e.release_date, m.id_product
     FROM cards c JOIN expansions e ON e.id = c.expansion_id
     LEFT JOIN card_cardmarket m ON m.card_id = c.card_id
    WHERE 1=1 ${scope} ORDER BY c.expansion_id, c.card_id`,
);
console.log(`Carte: ${cards.length}`);

// ── 2. listino ──
const guides = GUIDE_FILES.length > 0
  ? GUIDE_FILES.map((f) => JSON.parse(f.endsWith('.gz') ? zlib.gunzipSync(fs.readFileSync(f)) : fs.readFileSync(f)))
  : [await downloadPriceGuide()];
const guide = new Map();
for (const g of guides) for (const p of g.priceGuides) guide.set(p.idProduct, p);
const hasHoloPrice = (p) => !!p && (p['low-holo'] != null || p['avg-holo'] != null || (p['trend-holo'] ?? 0) > 0);
console.log(`Listino: ${guides.map((g) => g.createdAt).join(', ')} (${guide.size} prodotti)`);

// ── 3. set per set ──
const byExpansion = new Map();
for (const c of cards) { if (!byExpansion.has(c.expansion_id)) byExpansion.set(c.expansion_id, []); byExpansion.get(c.expansion_id).push(c); }

const ptcgSets = (await ptcg('https://api.pokemontcg.io/v2/sets?pageSize=250&select=id,name,ptcgoCode,releaseDate'))?.data ?? [];
console.log(`pokemontcg.io: ${ptcgSets.length} set`);
const ptcgByName = new Map(ptcgSets.map((s) => [normName(s.name), s]));
const ptcgById = new Map(ptcgSets.map((s) => [s.id.toLowerCase(), s]));
const codeCount = ptcgSets.reduce((m, s) => { if (s.ptcgoCode) m.set(s.ptcgoCode.toUpperCase(), (m.get(s.ptcgoCode.toUpperCase()) ?? 0) + 1); return m; }, new Map());
const ptcgByCode = new Map(ptcgSets.filter((s) => s.ptcgoCode && codeCount.get(s.ptcgoCode.toUpperCase()) === 1).map((s) => [s.ptcgoCode.toUpperCase(), s]));

/** Il set pokemontcg.io che corrisponde al nostro: per nome inglese, poi codice univoco, poi id. */
function ptcgSetFor(expansionId, tset) {
  const ids = [expansionId, expansionId.replace(/^([a-z]+)0(\d)/, '$1$2'), (TCGDEX_ID_OVERRIDES[expansionId] ?? expansionId).replace('.', 'pt')]
    .map((s) => s.toLowerCase());
  return (tset?.name && ptcgByName.get(normName(tset.name)))
    ?? ids.map((id) => ptcgById.get(id)).find(Boolean)
    ?? (tset?.abbreviation && ptcgByCode.get(tset.abbreviation.toUpperCase()))
    ?? null;
}
async function ptcgCards(setId) {
  const out = [];
  for (let page = 1; page <= 10; page += 1) {
    const j = await ptcg(`https://api.pokemontcg.io/v2/cards?q=set.id:${setId}&pageSize=250&page=${page}&select=number,rarity,tcgplayer`);
    if (j === undefined) return undefined;
    out.push(...(j?.data ?? []));
    if (!j || out.length >= (j.totalCount ?? 0) || (j.data ?? []).length === 0) break;
  }
  return out;
}

// Buste premio Play! Pokemon: "<expansion_id>|<numero>" -> serie in cui la
// carta esce normale (N) e olografica (H). Lo stesso timbro TCGdex lo usa
// anche per le vecchie Player Rewards (HGSS, Platino): fuori da questa
// lista quelle restano come le dice TCGdex.
const PRIZE_STAMP = 'player-rewards-program';
const PRIZE_PACKS = new Map();
for (const [serie, list] of Object.entries(JSON.parse(fs.readFileSync(new URL('./lib/prize-packs.json', import.meta.url), 'utf8')).serie)) {
  for (const line of list) {
    const [exp, num, finishes] = line.split(' ');
    const key = `${exp}|${localKey(num)}`;
    const e = PRIZE_PACKS.get(key) ?? { N: [], H: [] };
    for (const f of finishes) e[f].push(Number(serie));
    PRIZE_PACKS.set(key, e);
  }
}
const prizePackSeen = new Set();
const isPrizeStamp = (stamps) => stamps?.length === 1 && (stamps[0] === PRIZE_STAMP || stamps[0] === 'player-reward');

/**
 * Le stampe Play! Pokemon di una carta delle buste premio. "Set standard" e'
 * la carta senza olografia (anche una Rara Holo: Rillaboom SSH 14 esce solo
 * cosi'); "set standard olografico" e' la Holo per le rarita' gia'
 * olografiche (V, ex...), e per le altre la Holo cosmo dalla serie 3 in poi
 * -- le serie 1 e 2 erano olografiche anche loro, ma con quale disegno
 * nessuna fonte lo dice, quindi Holo e basta.
 */
function prizePackVariants(pp, rc) {
  const out = [];
  if (pp.N.length) out.push({ key: `Normal|stamp=${PRIZE_STAMP}`, type: 'normal', foil: null });
  if (pp.H.length) {
    const alreadyHolo = rc !== 'semplice';
    if (alreadyHolo || pp.H.some((s) => s <= 2)) out.push({ key: `Holo|stamp=${PRIZE_STAMP}`, type: 'holo', foil: null });
    if (!alreadyHolo && pp.H.some((s) => s >= 3)) out.push({ key: `Holo|foil=cosmos|stamp=${PRIZE_STAMP}`, type: 'holo', foil: 'cosmos' });
  }
  return out;
}

const rows = [];       // righe da scrivere
const perSet = [];     // rapporto
const anomalies = [];  // disaccordi da guardare
const unknownKeys = new Map();
const discarded = [];  // stampe con timbri fuori lista (World Championship Deck...)

for (const [expansionId, list] of byExpansion) {
  const tid = TCGDEX_ID_OVERRIDES[expansionId] ?? expansionId;
  const tset = await tcgdex(`https://api.tcgdex.net/v2/en/sets/${encodeURIComponent(tid)}`, slimSet);
  const byLocal = new Map((tset?.cards ?? []).map((c) => [localKey(c.localId), c.id]));
  const details = await pool(list, CONCURRENCY, async (c) => {
    const id = byLocal.get(localKey(c.card_number));
    return id ? tcgdex(`https://api.tcgdex.net/v2/en/cards/${encodeURIComponent(id)}`, slimCard) : null;
  });

  const pset = ptcgSetFor(expansionId, tset);
  const pcards = pset ? await ptcgCards(pset.id) : null;
  const pByNum = new Map((pcards ?? []).map((c) => [localKey(c.number), c]));
  // pokemontcg.io ha il set ma oggi non ha risposto: senza la fonte migliore
  // non si scrive (vedi --apply sotto); un secondo giro riprova solo questi.
  const ptcgFailed = !!pset && pcards === undefined;

  // Timbri che stanno su (quasi) tutte le carte del set: sono la stampa del
  // set, non una speciale (il "30th" del 30 Anniversario).
  const withDetail = details.filter((d) => d?.detailed?.length);
  const stampFreq = new Map();
  for (const d of withDetail) for (const s of new Set(d.detailed.flatMap((v) => v.stamp ?? []))) stampFreq.set(s, (stampFreq.get(s) ?? 0) + 1);
  const setStamps = new Set([...stampFreq].filter(([s, n]) => s !== '1st-edition' && withDetail.length >= 5 && n >= 0.9 * withDetail.length).map(([s]) => s));

  // Modalita' TCGdex: credibile sulle reverse delle carte semplici?
  const date = list[0].release_date ?? '';
  const reverseEra = !date || date >= REVERSE_FROM;
  let semplici = 0; let tRev = 0; let cmRev = 0;
  list.forEach((c, i) => {
    const d = details[i];
    if (rarityClass(c.rarity ?? d?.rarity) !== 'semplice') return;
    semplici += 1;
    if (d && (d.detailed.some((v) => v.type === 'reverse' && !v.foil) || d.variants?.reverse)) tRev += 1;
    if (hasHoloPrice(guide.get(c.id_product))) cmRev += 1;
  });
  // I Trainer Kit (tk-*) sono ristampe: le loro carte sono collegate ai
  // prodotti Cardmarket del set principale, che le reverse le hanno, e il
  // listino "vedrebbe" reverse che nel kit non esistono. Li' vale TCGdex.
  const isTrainerKit = expansionId.startsWith('tk-');
  const tcgdexReliable = isTrainerKit || !(reverseEra && semplici >= 5 && tRev / semplici < 0.2 && cmRev / semplici > 0.3);
  // Per la regola di ripiego: il set ha le reverse? Lo dice il listino sul
  // set intero (una carta senza inserzioni "-holo" non vuol dire niente).
  const setHasReverse = reverseEra && (tRev > 0 || (!isTrainerKit && semplici > 0 && cmRev / semplici > 0.3));

  const counts = {}; const sources = {}; let specials = 0;
  list.forEach((c, i) => {
    const d = details[i];
    const pc = pByNum.get(localKey(c.card_number));
    const rarity = pc?.rarity ?? c.rarity ?? d?.rarity;
    const rc = rarityClass(rarity);
    const p = guide.get(c.id_product);

    // Le tre letture delle stampe base.
    const fromPtcg = new Set();
    for (const k of Object.keys(pc?.tcgplayer?.prices ?? {})) {
      if (PTCG_KEY[k]) fromPtcg.add(PTCG_KEY[k]);
      else unknownKeys.set(k, (unknownKeys.get(k) ?? 0) + 1);
    }
    const fromTcgdex = new Set();
    const special = new Map();
    const tcgdexProduct = new Map(); // stampa base -> prodotto Cardmarket secondo TCGdex
    for (const v of d?.detailed ?? []) {
      if (v.size && v.size !== 'standard') continue; // jumbo
      const unknown = (v.stamp ?? []).filter((s) => !KNOWN_STAMPS.has(s) && !setStamps.has(s));
      if (unknown.length) { discarded.push({ expansionId, card_id: c.card_id, stamp: unknown.join('+'), type: v.type }); continue; }
      const key = variantKey(v.type, (v.stamp ?? []).filter((s) => !setStamps.has(s)), v.foil);
      if (!key) continue;
      if (isSpecial(key)) special.set(key, v); else { fromTcgdex.add(key); if (v.idProduct) tcgdexProduct.set(key, v.idProduct); }
    }
    const pp = PRIZE_PACKS.get(`${expansionId}|${localKey(c.card_number)}`);
    if (pp) {
      prizePackSeen.add(`${expansionId}|${localKey(c.card_number)}`);
      // Al posto delle Play! Pokemon di TCGdex; il loro prodotto Cardmarket
      // passa alla stampa uguale, o almeno della stessa olografia.
      const fromTcgdexPP = [...special].filter(([, v]) => isPrizeStamp(v.stamp));
      for (const [k] of fromTcgdexPP) special.delete(k);
      const products = new Map(fromTcgdexPP.filter(([, v]) => v.idProduct).map(([k, v]) => [k.replace('Reverse|', 'Holo|'), v]));
      const wanted = prizePackVariants(pp, rc);
      // Sulle ex e le V TCGdex chiama "normal" la loro unica stampa, che e'
      // olografica (SV03 22): se la carta non esce non olografica, va bene.
      const onlyHolo = rc !== 'semplice' && !wanted.some((x) => x.type === 'normal');
      const productFor = (w) => {
        const hit = products.get(w.key) ?? [...products].find(([k, v]) => (onlyHolo || (v.type === 'normal') === (w.type === 'normal')) && !wanted.some((x) => x.key === k))?.[1];
        if (!hit) return null;
        products.delete([...products].find(([, v]) => v === hit)[0]);
        return hit.idProduct;
      };
      for (const w of wanted) special.set(w.key, { type: w.type, stamp: [PRIZE_STAMP], foil: w.foil, idProduct: productFor(w), source: 'prize-pack' });
    }
    // Il prodotto "della carta" secondo TCGdex e' quello condiviso dalle sue
    // stampe principali; una stampa base con un prodotto diverso (la Normale
    // dei mazzi di una Holo Rara, ME01 10) ha un prezzo suo.
    const productVotes = [...tcgdexProduct.values()].reduce((m, id) => m.set(id, (m.get(id) ?? 0) + 1), new Map());
    const tcgdexMain = [...productVotes].sort((a, b) => b[1] - a[1])[0]?.[0] ?? null;
    if (fromTcgdex.size === 0 && d?.variants) {
      if (d.variants.normal) fromTcgdex.add(d.variants.firstEdition ? '1st Edition' : 'Normal');
      if (d.variants.holo) fromTcgdex.add('Holo');
      if (d.variants.reverse) fromTcgdex.add('Reverse');
    }
    const fromRule = new Set();
    if (rc === 'unica') fromRule.add('Holo');
    else if (rc === 'holo') { fromRule.add('Holo'); if (setHasReverse) fromRule.add('Reverse'); }
    else if (rc === 'semplice') { fromRule.add('Normal'); if (setHasReverse) fromRule.add('Reverse'); }
    else fromRule.add('Holo');

    // Scelta: l'unione delle fonti che reggono. L'errore tipico di queste
    // fonti e' OMETTERE una stampa (TCGplayer senza prezzo = niente chiave;
    // TCGdex che su BW/XY/SM non vede le reverse), non inventarla: una stampa
    // vista da una fonte affidabile entra. La regola solo se nessuna parla.
    // Non un'unione cieca: misurato sul catalogo intero (10/10/2026), dove le
    // due fonti si contraddicono su "quale" stampa (una promo Holo per
    // pokemontcg.io, Normale per TCGdex: 465 casi) ha ragione pokemontcg.io --
    // la promo esce in una stampa sola. Da TCGdex si aggiunge solo cio' che
    // pokemontcg.io tipicamente omette: la 1a Edizione, le Reverse (e-Card,
    // DP), la Holo delle rarita' olografiche, e una Normale solo se TCGdex le
    // da' un prodotto Cardmarket suo (la Normale dei mazzi di una Holo Rara,
    // ME01 10): senza prodotto non si puo' confermare che esista.
    let base; let source;
    const tcgdexUsable = fromTcgdex.size > 0 && (tcgdexReliable || [...fromTcgdex].some((k) => k.startsWith('1st Edition')));
    if (fromPtcg.size && tcgdexUsable && tcgdexReliable) {
      base = new Set(fromPtcg);
      for (const k of fromTcgdex) {
        if (base.has(k)) continue;
        const ownProduct = tcgdexProduct.get(k);
        const add = k.startsWith('1st Edition')
          || k === 'Reverse'
          || (k === 'Holo' && (rc === 'holo' || rc === 'unica'))
          || (k === 'Normal' && ownProduct && tcgdexMain && ownProduct !== tcgdexMain);
        if (add) base.add(k);
      }
      source = 'pokemontcg+tcgdex';
    }
    else if (fromPtcg.size) { base = new Set(fromPtcg); source = 'pokemontcg'; }
    else if (tcgdexReliable && fromTcgdex.size) { base = new Set(fromTcgdex); source = 'tcgdex'; }
    else { base = fromRule; source = 'regola'; }
    // La 1a Edizione: TCGdex la vede anche dove per il resto non regge.
    for (const k of fromTcgdex) if (k.startsWith('1st Edition')) base.add(k);

    const same = (a, b) => a.size === b.size && [...a].every((x) => b.has(x));
    if (fromPtcg.size && tcgdexReliable && fromTcgdex.size && !same(fromPtcg, fromTcgdex)) {
      anomalies.push({ expansionId, card_id: c.card_id, rarity, tipo: 'pokemontcg != tcgdex', pokemontcg: [...fromPtcg], tcgdex: [...fromTcgdex] });
    }
    if (source !== 'pokemontcg' && rc === 'semplice' && reverseEra && p && hasHoloPrice(p) && !base.has('Reverse')) {
      anomalies.push({ expansionId, card_id: c.card_id, rarity, tipo: 'listino ha la reverse, scelta no', source, base: [...base] });
    }

    const combo = [...base].sort().join('+');
    counts[combo] = (counts[combo] ?? 0) + 1;
    sources[source] = (sources[source] ?? 0) + 1;
    for (const key of base) {
      const own = tcgdexProduct.get(key);
      const idProduct = own && tcgdexMain && own !== tcgdexMain ? own : (c.id_product ?? null);
      rows.push({ card_id: c.card_id, variant: key, kind: 'base', type: key === 'Reverse' ? 'reverse' : key.includes('Holo') ? 'holo' : 'normal', stamp: key.startsWith('1st') ? '1st-edition' : null, foil: null, id_product: idProduct, source });
    }
    for (const [key, v] of special) {
      specials += 1;
      // Una speciale con lo stesso prodotto della carta non ha un prezzo suo.
      const own = v.idProduct && v.idProduct !== c.id_product ? v.idProduct : null;
      const stamps = (v.stamp ?? []).filter((s) => !setStamps.has(s) && s !== '1st-edition');
      rows.push({ card_id: c.card_id, variant: key, kind: 'special', type: v.type, stamp: stamps.join('+') || null, foil: v.foil, id_product: own, source: v.source ?? 'tcgdex' });
    }
  });
  perSet.push({ expansionId, date, ptcg: pset?.id ?? null, ptcgFailed, tcgdexReliable, setStamps: [...setStamps], carte: list.length, semplici, tRev, cmRev, specials, counts, sources });
}

// ── 4. rapporto ──
perSet.sort((a, b) => (a.date || '').localeCompare(b.date || ''));
for (const s of perSet) {
  const combos = Object.entries(s.counts).sort((a, b) => b[1] - a[1]).map(([k, n]) => `${k}:${n}`).join(' ');
  const src = Object.entries(s.sources).map(([k, n]) => `${k}:${n}`).join(' ');
  console.log(`${s.expansionId.padEnd(12)} ${String(s.date).padEnd(10)} ptcg=${String(s.ptcg).padEnd(10)} ${s.tcgdexReliable ? '' : 'TCGDEX-NO '}carte ${String(s.carte).padStart(3)}`
    + ` | fonti ${src} | rev TCGdex ${s.tRev}/${s.semplici} listino ${s.cmRev}/${s.semplici} | speciali ${s.specials}`
    + (s.setStamps.length ? ` | timbro del set ${s.setStamps.join(',')}` : '') + `\n             ${combos}`);
}
const byKind = rows.reduce((m, r) => { m[r.kind] = (m[r.kind] ?? 0) + 1; return m; }, {});
const byAnomaly = anomalies.reduce((m, a) => { m[a.tipo] = (m[a.tipo] ?? 0) + 1; return m; }, {});
console.log(`\nStampe: ${rows.length} (${JSON.stringify(byKind)}), disaccordi: ${JSON.stringify(byAnomaly)}`);
const discardedStamps = discarded.reduce((m, x) => { m[x.stamp] = (m[x.stamp] ?? 0) + 1; return m; }, {});
console.log(`Scartate (timbro fuori lista): ${discarded.length} ${JSON.stringify(discardedStamps)}`);
const prizePackMissing = [...PRIZE_PACKS.keys()].filter((k) => !prizePackSeen.has(k) && byExpansion.has(k.split('|')[0]));
console.log(`Buste premio: ${prizePackSeen.size} carte trovate${prizePackMissing.length ? `, NON nel catalogo: ${prizePackMissing.join(' ')}` : ''}`);
if (unknownKeys.size) console.log(`Chiavi TCGplayer non riconosciute: ${JSON.stringify(Object.fromEntries(unknownKeys))}`);
if (REPORT_FILE) {
  fs.writeFileSync(REPORT_FILE, JSON.stringify({ perSet, anomalies, discarded, rows }, null, 1));
  console.log(`Dettaglio in ${REPORT_FILE}`);
}

// ── 5. scrittura ──
const failedSets = perSet.filter((s) => s.ptcgFailed).map((s) => s.expansionId);
if (failedSets.length) console.log('pokemontcg.io non ha risposto per: ' + failedSets.join(', ') + ' -- rilanciare: la cache riprova solo questi.');
if (!APPLY) { console.log('Prova: niente scritto. Aggiungi --apply per salvare su D1.'); process.exit(0); }
if (failedSets.length && !flag('--allow-missing-ptcg')) {
  console.error('Non scrivo: ci sono set senza pokemontcg.io (vedi sopra). Rilanciare, o --allow-missing-ptcg per scrivere lo stesso.');
  process.exit(1);
}
const ids = [...new Set(rows.map((r) => r.card_id))];
const sql = [];
for (let i = 0; i < ids.length; i += 200) {
  sql.push(`DELETE FROM card_variants WHERE card_id IN (${ids.slice(i, i + 200).map(sqlString).join(',')});`);
}
for (let i = 0; i < rows.length; i += 200) {
  sql.push('INSERT INTO card_variants (card_id, variant, kind, type, stamp, foil, id_product, source) VALUES\n'
    + rows.slice(i, i + 200).map((r) => `(${sqlString(r.card_id)}, ${sqlString(r.variant)}, ${sqlString(r.kind)}, ${sqlString(r.type)}, ${r.stamp ? sqlString(r.stamp) : 'NULL'}, ${r.foil ? sqlString(r.foil) : 'NULL'}, ${r.id_product ?? 'NULL'}, ${sqlString(r.source)})`).join(',\n') + ';');
}
const file = path.join(workerRoot, 'card-variants.sql');
fs.writeFileSync(file, sql.join('\n') + '\n');
await d1ExecuteFile(file);
fs.rmSync(file);
console.log(`Scritte ${rows.length} stampe per ${ids.length} carte.`);
