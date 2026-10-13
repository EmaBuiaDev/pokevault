#!/usr/bin/env node
// Importa UN set inglese nel catalogo EN (piano carte EN, Fase 2, 13/10/2026).
//
// Scrive SOLO nel database D1 inglese (pokevault-catalog-en) e sotto en/ nel
// bucket R2: il catalogo italiano lo legge, per trovare il gemello, ma non lo
// scrive mai. Lo script italiano (ingest-tcgdex-set.mjs) resta com'e'.
//
// Fonti:
//   - dati: TCGdex /v2/en;
//   - immagini: TCGdex (high.webp), altrimenti pokemontcg.io (PNG grande,
//     convertito in WebP). Logo e simbolo: TCGdex, altrimenti pokemontcg.io.
//
// Regola dei codici (vedi lo studio, scripts/studio-en.mjs):
//   - set GEMELLO di uno italiano: stesso id e stessa cartella dell'italiano
//     (sv08 -> en/SV08/SV08_EN_1.webp), cosi' l'app ritrova la gemella IT
//     senza tabelle di traduzione;
//   - set SOLO EN: id TCGdex in minuscolo ("." -> "pt"), e lo script si ferma
//     se il codice coincide con un id o una cartella italiana.
//
// Un set si pubblica solo con almeno l'80% delle carte con immagine, come per
// l'italiano. Un rilancio non ricarica le immagini gia' 'ok' (--force-images
// per rifarle).
//
// Uso:
//   node scripts/ingest-en-set.mjs <tcgdexId>                  (a secco: solo report)
//   node scripts/ingest-en-set.mjs <tcgdexId> --apply
//   node scripts/ingest-en-set.mjs <tcgdexId> --ptcg <id>      (forza il set pokemontcg.io)
//   node scripts/ingest-en-set.mjs <tcgdexId> --no-ptcg
import { mkdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import { canonicalStage } from './lib/tcgdex-stage.mjs';
import { TCGDEX_ID_OVERRIDES } from './lib/tcgdex-set-id-map.mjs';
import { d1Select, runWrangler, workerRoot } from './lib/cardmarket.mjs';

const EN_DB = 'pokevault-catalog-en';
const BUCKET = 'pokevault-images';
const TCGDEX = 'https://api.tcgdex.net/v2/en';
const PTCG = 'https://api.pokemontcg.io/v2';
const COVERAGE_THRESHOLD = 0.8;
const WEBP_QUALITY = 82;
const CONCURRENCY = 6;
const tmpDir = path.join(workerRoot, 'scripts', '.ingest-en-tmp');

function parseArgs(argv) {
  const out = { tcgdexId: argv[0], apply: false, ptcg: undefined, forceImages: false };
  for (let i = 1; i < argv.length; i += 1) {
    if (argv[i] === '--apply') out.apply = true;
    else if (argv[i] === '--ptcg') out.ptcg = argv[++i];
    else if (argv[i] === '--no-ptcg') out.ptcg = null;
    else if (argv[i] === '--force-images') out.forceImages = true;
  }
  return out;
}

async function fetchJson(url, attempts = 5) {
  for (let i = 1; ; i += 1) {
    try {
      const res = await fetch(url);
      if (res.status === 404) return null;
      if (!res.ok) throw new Error(`${url} -> HTTP ${res.status}`);
      return await res.json();
    } catch (err) {
      if (i >= attempts) throw err;
      await new Promise(r => setTimeout(r, 1500 * i));
    }
  }
}

async function fetchBuffer(url, attempts = 3) {
  for (let i = 1; ; i += 1) {
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 20000);
      try {
        const res = await fetch(url, { signal: controller.signal });
        if (res.status === 404) return null;
        if (!res.ok) throw new Error(`${url} -> HTTP ${res.status}`);
        const buf = Buffer.from(await res.arrayBuffer());
        return buf.length > 0 ? buf : null;
      } finally {
        clearTimeout(timer);
      }
    } catch (err) {
      if (i >= attempts) return null;
      await new Promise(r => setTimeout(r, 1000 * i));
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

const sqlString = v => (v === null || v === undefined ? 'NULL' : `'${String(v).replace(/'/g, "''")}'`);
const normNumber = n => {
  const t = String(n ?? '').trim();
  return /^\d+$/.test(t) ? t.replace(/^0+(?=\d)/, '') : t;
};
const normName = s => String(s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]/g, '');
const normCode = s => String(s ?? '').toLowerCase().replace(/[^a-z0-9]/g, '');

async function enSelect(sql) {
  const oneLine = sql.replace(/\s+/g, ' ').trim();
  const out = await runWrangler(['d1', 'execute', EN_DB, '--remote', '--json', '--command', `"${oneLine.replace(/"/g, '\\"')}"`]);
  const start = out.indexOf('[');
  return JSON.parse(start >= 0 ? out.slice(start) : out)[0]?.results ?? [];
}

/** Il gemello italiano (id + cartella) di un set TCGdex, o null se e' un set solo EN. */
async function findItalianTwin(tcgdexId) {
  const overridesInverse = Object.fromEntries(Object.entries(TCGDEX_ID_OVERRIDES).map(([it, td]) => [td.toLowerCase(), it]));
  const itId = overridesInverse[tcgdexId.toLowerCase()] ?? tcgdexId.toLowerCase();
  const itExpansions = await d1Select('SELECT id, base_set_code FROM expansions');
  const folders = await d1Select(
    "SELECT expansion_id, substr(card_id, 1, instr(card_id, '_IT_') - 1) AS folder, COUNT(*) AS n FROM cards WHERE instr(card_id, '_IT_') > 0 GROUP BY 1, 2"
  );
  const twin = itExpansions.find(e => e.id.toLowerCase() === itId);
  return { twin, itExpansions, folders };
}

function matchPtcg(tset, ptcgSets) {
  const name = normName(tset.name);
  const date = String(tset.releaseDate || '');
  const idNorm = normCode(tset.id.replace(/\.5/g, 'pt5')).replace(/([a-z]+)0+(\d)/g, '$1$2');
  return ptcgSets.find(p => normName(p.name) === name && p.releaseDate.replaceAll('/', '-') === date)
    || ptcgSets.find(p => normCode(p.id).replace(/([a-z]+)0+(\d)/g, '$1$2') === idNorm)
    || ptcgSets.find(p => normName(p.name) === name)
    || null;
}

async function loadPtcgCards(ptcgId) {
  const byNumber = new Map();
  for (let page = 1; ; page += 1) {
    const j = await fetchJson(`${PTCG}/cards?q=set.id:${encodeURIComponent(ptcgId)}&select=number,name,images&pageSize=250&page=${page}`);
    const batch = j?.data || [];
    for (const c of batch) byNumber.set(normNumber(c.number), c);
    if (batch.length < 250) break;
  }
  return byNumber;
}

async function toCardWebp(buf, alreadyWebp) {
  if (alreadyWebp) return buf;
  return sharp(buf).resize({ width: 600, withoutEnlargement: true }).webp({ quality: WEBP_QUALITY }).toBuffer();
}

async function toAssetWebp(buf) {
  return sharp(buf).resize({ width: 800, withoutEnlargement: true }).webp({ nearLossless: true, quality: 80 }).toBuffer();
}

async function putR2(key, buf) {
  const file = path.join(tmpDir, key.replace(/[\\/]/g, '__'));
  await writeFile(file, buf);
  try {
    await runWrangler(['r2', 'object', 'put', `${BUCKET}/${key}`, '--file', `"${file}"`, '--remote', '--content-type', 'image/webp']);
  } finally {
    await rm(file, { force: true });
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.tcgdexId) throw new Error('Uso: node scripts/ingest-en-set.mjs <tcgdexId> [--apply] [--ptcg <id> | --no-ptcg] [--force-images]');

  const set = await fetchJson(`${TCGDEX}/sets/${encodeURIComponent(args.tcgdexId)}`);
  if (!set) throw new Error(`TCGdex non conosce il set "${args.tcgdexId}"`);
  if (set.serie?.id === 'tcgp') throw new Error('TCG Pocket e\' il gioco digitale: non si importa');
  const unique = new Map();
  for (const c of set.cards || []) if (!unique.has(normNumber(c.localId))) unique.set(normNumber(c.localId), c);
  const refs = [...unique.values()];
  console.log(`TCGdex EN: ${set.id} "${set.name}" (${set.releaseDate}), ${refs.length} carte`);

  // Gemello italiano e codici
  const { twin, itExpansions, folders } = await findItalianTwin(set.id);
  let expansionId;
  let folder;
  if (twin) {
    const own = folders.filter(f => f.expansion_id === twin.id).sort((a, b) => b.n - a.n);
    folder = (own[0]?.folder || twin.base_set_code || twin.id).toUpperCase();
    expansionId = twin.id;
    console.log(`Gemello italiano: ${twin.id} (cartella ${folder})`);
  } else {
    expansionId = set.id.toLowerCase().replace(/\./g, 'pt');
    folder = expansionId.toUpperCase();
    const clashId = itExpansions.find(e => normCode(e.id) === normCode(expansionId));
    const clashFolder = folders.find(f => normCode(f.folder) === normCode(folder));
    if (clashId || clashFolder) {
      throw new Error(`Il codice "${expansionId}" coincide con l'italiano (${clashId?.id ?? clashFolder.folder}): serve una decisione a mano`);
    }
    console.log(`Set solo EN: id ${expansionId}, cartella ${folder}`);
  }

  // pokemontcg.io, per le immagini che TCGdex non ha
  let ptcgCards = new Map();
  let ptcgSet = null;
  // E' un ripiego: se non risponde (502 frequenti sotto carico) si va avanti
  // con TCGdex soltanto, e le carte senza immagine restano 'missing'.
  if (args.ptcg !== null) {
    try {
      const sets = (await fetchJson(`${PTCG}/sets?pageSize=250&select=id,name,releaseDate,images`))?.data || [];
      ptcgSet = args.ptcg ? sets.find(s => s.id === args.ptcg) : matchPtcg(set, sets);
      if (ptcgSet) {
        ptcgCards = await loadPtcgCards(ptcgSet.id);
        console.log(`pokemontcg.io: ${ptcgSet.id} "${ptcgSet.name}", ${ptcgCards.size} carte`);
      } else {
        console.log('pokemontcg.io: nessun set abbinato (si usa solo TCGdex)');
      }
    } catch (err) {
      ptcgSet = null;
      ptcgCards = new Map();
      console.warn(`pokemontcg.io non risponde (${err.message}): si usa solo TCGdex`);
    }
  }

  // Carte gia' con immagine nel catalogo EN (un rilancio non le ricarica)
  const existingOk = new Set(
    args.apply && !args.forceImages
      ? (await enSelect(`SELECT card_id FROM cards WHERE expansion_id = ${sqlString(expansionId)} AND image_status = 'ok'`)).map(r => r.card_id)
      : []
  );

  console.log('Dettaglio carte...');
  const cards = await pool(refs, async (ref) => {
    const detail = await fetchJson(`${TCGDEX}/cards/${encodeURIComponent(ref.id)}`) || {};
    const number = normNumber(ref.localId);
    const ptcg = ptcgCards.get(number);
    return {
      number,
      cardId: `${folder}_EN_${number}.webp`,
      detail,
      sources: [
        ...(ref.image ? [{ kind: 'tcgdex', url: `${ref.image}/high.webp`, webp: true }] : []),
        ...(ptcg?.images?.large ? [{ kind: 'pokemontcg', url: ptcg.images.large, webp: false }] : []),
        ...(ptcg?.images?.small ? [{ kind: 'pokemontcg', url: ptcg.images.small, webp: false }] : []),
      ],
    };
  });

  const coverable = cards.filter(c => c.sources.length > 0 || existingOk.has(c.cardId)).length;
  console.log(`Immagini disponibili: ${coverable}/${cards.length} (${((100 * coverable) / cards.length).toFixed(1)}%)`);
  const noSource = cards.filter(c => c.sources.length === 0 && !existingOk.has(c.cardId)).map(c => c.number);
  if (noSource.length) console.log(`Senza nessuna fonte (${noSource.length}): ${noSource.slice(0, 25).join(', ')}${noSource.length > 25 ? '...' : ''}`);

  if (!args.apply) {
    console.log('\nA SECCO: niente scritto. Rilancia con --apply.');
    return;
  }

  await mkdir(tmpDir, { recursive: true });

  console.log('\nImmagini su R2...');
  let uploaded = 0;
  let failed = 0;
  await pool(cards, async (c) => {
    if (existingOk.has(c.cardId)) { c.imageStatus = 'ok'; c.imageSource = null; return; }
    for (const source of c.sources) {
      const raw = await fetchBuffer(source.url);
      if (!raw) continue;
      try {
        const webp = await toCardWebp(raw, source.webp);
        await putR2(`en/${folder}/${c.cardId}`, webp);
        c.imageStatus = 'ok';
        c.imageSource = source.kind;
        uploaded += 1;
        return;
      } catch (err) {
        console.error(`  ${c.number} da ${source.kind}: ${err.message.slice(0, 200)}`);
      }
    }
    c.imageStatus = 'missing';
    if (c.sources.length) failed += 1;
  });
  console.log(`Caricate ${uploaded}, fallite ${failed}, gia' presenti ${existingOk.size}`);

  // Logo e simbolo
  for (const [asset, urls] of [
    ['logo', [set.logo && `${set.logo}.png`, ptcgSet?.images?.logo]],
    ['symbol', [set.symbol && `${set.symbol}.png`, ptcgSet?.images?.symbol]],
  ]) {
    let done = false;
    for (const url of urls.filter(Boolean)) {
      const raw = await fetchBuffer(url);
      if (!raw) continue;
      try {
        await putR2(`en/${folder}/${asset}.webp`, await toAssetWebp(raw));
        console.log(`${asset}: caricato da ${new URL(url).host}`);
        done = true;
        break;
      } catch (err) {
        console.error(`${asset} da ${url}: ${err.message.slice(0, 200)}`);
      }
    }
    if (!done) console.log(`${asset}: nessuna fonte`);
  }

  const withImage = cards.filter(c => c.imageStatus === 'ok').length;
  const coverage = withImage / cards.length;
  const published = coverage >= COVERAGE_THRESHOLD ? 1 : 0;
  const officialCount = Number(set.cardCount?.official) > 0 ? Number(set.cardCount.official) : null;
  const abbreviation = (set.abbreviation?.official || '').trim().toUpperCase() || null;

  const lines = [
    'INSERT INTO expansions (id, name, card_count, official_count, sort_order, logo_key, published, coverage_pct, release_date, base_set_code, upstream_set_code, series, abbreviation, it_expansion_id, tcgdex_id) VALUES (' +
      [sqlString(expansionId), sqlString(set.name), cards.length, officialCount ?? 'NULL', 100, 'NULL', published, coverage.toFixed(4),
        sqlString(set.releaseDate), sqlString(folder), sqlString(abbreviation), sqlString(set.serie?.name), sqlString(abbreviation),
        sqlString(twin?.id ?? null), sqlString(set.id)].join(', ') +
      ') ON CONFLICT(id) DO UPDATE SET name = excluded.name, card_count = excluded.card_count, official_count = excluded.official_count, ' +
      'published = excluded.published, coverage_pct = excluded.coverage_pct, release_date = excluded.release_date, base_set_code = excluded.base_set_code, ' +
      'upstream_set_code = excluded.upstream_set_code, series = excluded.series, abbreviation = excluded.abbreviation, ' +
      'it_expansion_id = excluded.it_expansion_id, tcgdex_id = excluded.tcgdex_id, updated_at = unixepoch();',
  ];
  const rows = cards.map((c) => {
    const d = c.detail;
    const attacchi = (d.attacks || []).map(a => ({ nome: a.name ?? '', danno: a.damage != null ? String(a.damage) : '', descrizione: a.effect ?? '' }));
    return '(' + [
      sqlString(c.cardId), sqlString(expansionId), sqlString(c.number), sqlString(d.name || c.number),
      sqlString((d.types || []).join(', ') || null), sqlString(d.hp ?? null), 'NULL', sqlString(JSON.stringify(attacchi)),
      sqlString(d.rarity && d.rarity !== 'None' ? d.rarity : null), sqlString(canonicalStage(d.stage) ?? null), sqlString(d.illustrator ?? null),
      sqlString(c.imageStatus), sqlString(c.imageSource),
    ].join(', ') + ')';
  });
  for (let i = 0; i < rows.length; i += 50) {
    lines.push(
      'INSERT INTO cards (card_id, expansion_id, card_number, nome, tipo, ps, regola_speciale, attacchi_json, rarity, stage, illustratore, image_status, image_source) VALUES\n' +
      rows.slice(i, i + 50).join(',\n') +
      '\nON CONFLICT(card_id) DO UPDATE SET nome = excluded.nome, tipo = excluded.tipo, ps = excluded.ps, attacchi_json = excluded.attacchi_json, ' +
      'rarity = COALESCE(excluded.rarity, cards.rarity), stage = COALESCE(excluded.stage, cards.stage), illustratore = COALESCE(excluded.illustratore, cards.illustratore), ' +
      // L'immagine sale e non scende: un rilancio con la rete ballerina non declassa una carta gia' su R2.
      "image_status = CASE WHEN excluded.image_status = 'ok' THEN 'ok' ELSE cards.image_status END, " +
      'image_source = COALESCE(excluded.image_source, cards.image_source), updated_at = unixepoch();'
    );
  }
  const sqlFile = path.join(tmpDir, `ingest-en-${expansionId}.sql`);
  await writeFile(sqlFile, lines.join('\n\n'), 'utf8');
  await runWrangler(['d1', 'execute', EN_DB, '--remote', '--yes', `--file="${sqlFile}"`], {}, 1);
  await rm(tmpDir, { recursive: true, force: true });

  console.log('\n=== Riepilogo ===');
  console.log(`${expansionId} (${set.name}): ${cards.length} carte, con immagine ${withImage} (${(coverage * 100).toFixed(1)}%), published = ${published}`);
  if (twin) console.log(`Gemello IT: ${twin.id}`);
}

main().catch((err) => {
  console.error('Errore:', err.message || err);
  process.exit(1);
});
