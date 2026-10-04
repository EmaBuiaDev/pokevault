// node --test test/   (Node 22.6+ legge il TypeScript da solo)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  CARDMARKET_MAX_AGE_MS,
  cardmarketBlobProblem,
  isCardmarketUrl,
  mergeCardmarketPrices,
  type CardmarketBlob,
  type PriceSnapshot,
} from '../src/cardmarket-prices.ts';

const NOW = Date.parse('2026-10-03T08:00:00Z');
const CM_URL = 'https://www.cardmarket.com/en/Pokemon/Products/Singles/SV-Black-Star-Promos/Sprigatito-SVPen001';
const TP_URL = 'https://www.tcgplayer.com/product/123';

function pokewallet(): PriceSnapshot {
  return {
    version: 1,
    builtAt: NOW - 60_000,
    totalExpansions: 3,
    aliases: { svp: 'svp', xy1: 'xy1', '30th-c': '30th-c', '30c': '30th-c' },
    expansions: {
      svp: {
        baseSetCode: 'SVP',
        updatedAt: NOW - 3_600_000,
        prices: {
          '1': { avg: 6.48, low: 2, trend: 7.26, avg1: 6.49, avg7: 6.04, avg30: 6.76, url: CM_URL },
          '2': { avg: 2.49, low: 1.9, trend: 2.51, url: CM_URL },
          '3': { low: 5 },
        },
      },
      xy1: {
        baseSetCode: 'XY',
        updatedAt: NOW - 7_200_000,
        prices: {
          '1': { usd: 0.5, usdLow: 0.1, url: TP_URL },
        },
      },
      '30th-c': {
        baseSetCode: '30C',
        updatedAt: NOW - 7_200_000,
        numbering: 'catalog',
        prices: { '1': { low: 9, url: CM_URL } },
      },
    },
  };
}

function blob(overrides: Partial<CardmarketBlob> = {}): CardmarketBlob {
  return {
    version: 1,
    createdAt: '2026-10-03T02:41:55+0200',
    builtAt: NOW - 30_000,
    expansions: {
      svp: {
        aliases: ['svp'],
        prices: {
          '1': { avg: 6.5, low: 1.5, trend: 7, avg1: 6.4, avg7: 6, avg30: 6.7 },
          '3': { avg: null, low: null, trend: null, avg1: 3 },
        },
      },
      xy1: { aliases: ['xy'], prices: { '1': { low: 0.4, avg: 0.6, trend: 0.55 } } },
      sv10: { aliases: ['sv10', 'dri'], prices: { '7': { low: 0.02, avg: 0.1, trend: 0.08, avg30: 0 } } },
    },
    ...overrides,
  };
}

test('una carta col prezzo Cardmarket prende i suoi euro e tiene il link Cardmarket', () => {
  const merged = mergeCardmarketPrices(pokewallet(), blob(), NOW);
  assert.deepEqual(merged.expansions.svp.prices['1'], {
    avg: 6.5, low: 1.5, trend: 7, avg1: 6.4, avg7: 6, avg30: 6.7, url: CM_URL,
  });
});

test('una carta senza prezzo Cardmarket resta esattamente com era', () => {
  const pw = pokewallet();
  const merged = mergeCardmarketPrices(pw, blob(), NOW);
  assert.deepEqual(merged.expansions.svp.prices['2'], pw.expansions.svp.prices['2']);
});

test('una voce Cardmarket senza avg, low e trend non tocca il prezzo di oggi', () => {
  const merged = mergeCardmarketPrices(pokewallet(), blob(), NOW);
  assert.deepEqual(merged.expansions.svp.prices['3'], { low: 5 });
});

test('una carta che aveva solo dollari prende gli euro, tiene i dollari e perde il link TCGplayer', () => {
  const merged = mergeCardmarketPrices(pokewallet(), blob(), NOW);
  const entry = merged.expansions.xy1.prices['1'];
  assert.deepEqual(entry, { low: 0.4, avg: 0.6, trend: 0.55, usd: 0.5, usdLow: 0.1 });
  assert.equal(entry.url, undefined, 'con gli euro l app mostrerebbe il link TCGplayer come Cardmarket');
});

test('un espansione che PokeWallet non copre entra con i suoi alias', () => {
  const merged = mergeCardmarketPrices(pokewallet(), blob(), NOW);
  const sv10 = merged.expansions.sv10;
  assert.equal(sv10.baseSetCode, 'SV10');
  assert.deepEqual(sv10.prices['7'], { low: 0.02, avg: 0.1, trend: 0.08 }, 'zero e valori nulli non entrano');
  assert.equal(sv10.updatedAt, Date.parse('2026-10-03T02:41:55+0200'));
  assert.equal(merged.aliases.sv10, 'sv10');
  assert.equal(merged.aliases.dri, 'sv10');
});

test('gli alias di PokeWallet non vengono mai riscritti', () => {
  const pw = pokewallet();
  pw.aliases.xy = 'xy1-vecchio';
  const merged = mergeCardmarketPrices(pw, blob(), NOW);
  assert.equal(merged.aliases.xy, 'xy1-vecchio');
  assert.equal(merged.aliases['30c'], '30th-c');
});

test('le espansioni non toccate dal listino restano identiche, campi extra compresi', () => {
  const pw = pokewallet();
  const merged = mergeCardmarketPrices(pw, blob(), NOW);
  assert.deepEqual(merged.expansions['30th-c'], pw.expansions['30th-c']);
});

test('un espansione toccata conserva baseSetCode, numbering e gli altri campi', () => {
  const pw = pokewallet();
  const cm = blob({ expansions: { '30th-c': { prices: { '1': { low: 10 } } } } });
  const merged = mergeCardmarketPrices(pw, cm, NOW);
  assert.equal(merged.expansions['30th-c'].baseSetCode, '30C');
  assert.equal(merged.expansions['30th-c'].numbering, 'catalog');
  assert.deepEqual(merged.expansions['30th-c'].prices['1'], { low: 10, url: CM_URL });
});

test('la fusione non modifica gli argomenti', () => {
  const pw = pokewallet();
  const cm = blob();
  const pwCopy = structuredClone(pw);
  const cmCopy = structuredClone(cm);
  mergeCardmarketPrices(pw, cm, NOW);
  assert.deepEqual(pw, pwCopy);
  assert.deepEqual(cm, cmCopy);
});

test('versione, totalExpansions e builtAt dello snapshot', () => {
  const merged = mergeCardmarketPrices(pokewallet(), blob(), NOW);
  assert.equal(merged.version, 1);
  assert.equal(merged.totalExpansions, 3);
  assert.equal(merged.builtAt, NOW);
  assert.equal(merged.cardmarketCreatedAt, '2026-10-03T02:41:55+0200');
});

test('senza snapshot PokeWallet restano i soli prezzi Cardmarket', () => {
  const merged = mergeCardmarketPrices(null, blob(), NOW);
  assert.deepEqual(Object.keys(merged.expansions).sort(), ['sv10', 'svp', 'xy1']);
  assert.equal(merged.expansions.svp.prices['3'], undefined);
  assert.equal(merged.expansions.svp.prices['1'].url, undefined);
});

test('un espansione del listino senza nessun prezzo valido non viene creata', () => {
  const cm = blob({ expansions: { neo1: { prices: { '1': { low: 0, avg: null } } } } });
  const merged = mergeCardmarketPrices(pokewallet(), cm, NOW);
  assert.equal(merged.expansions.neo1, undefined);
  assert.equal(merged.aliases.neo1, undefined);
});

test('quando il listino si puo usare e quando no', () => {
  assert.equal(cardmarketBlobProblem(blob(), NOW), null);
  assert.match(cardmarketBlobProblem(null, NOW) ?? '', /assente/);
  assert.match(cardmarketBlobProblem(blob({ version: 2 }), NOW) ?? '', /versione/);
  assert.match(cardmarketBlobProblem(blob({ createdAt: 'ieri' }), NOW) ?? '', /illeggibile/);
  assert.match(cardmarketBlobProblem(blob({ expansions: {} }), NOW) ?? '', /nessuna/);
  const vecchio = new Date(NOW - CARDMARKET_MAX_AGE_MS - 60_000).toISOString();
  assert.match(cardmarketBlobProblem(blob({ createdAt: vecchio }), NOW) ?? '', /vecchio/);
  const appenaInTempo = new Date(NOW - CARDMARKET_MAX_AGE_MS + 60_000).toISOString();
  assert.equal(cardmarketBlobProblem(blob({ createdAt: appenaInTempo }), NOW), null);
});

test('con idProduct il link diventa quello diretto alla versione del prezzo', () => {
  const cm = blob({
    expansions: {
      svp: { prices: { '1': { low: 1.5, avg: 6.5, idProduct: 715758 } } },
      xy1: { prices: { '1': { low: 0.4, idProduct: 274409 } } },
      neo1: { prices: { '9': { low: 44.9, idProduct: 274409 } } },
    },
  });
  const merged = mergeCardmarketPrices(pokewallet(), cm, NOW);
  const direct = 'https://www.cardmarket.com/it/Pokemon/Products?idProduct=';
  assert.equal(merged.expansions.svp.prices['1'].url, `${direct}715758`, 'sostituisce il link PokeWallet');
  assert.equal(merged.expansions.xy1.prices['1'].url, `${direct}274409`, 'al posto del link TCGplayer');
  assert.equal(merged.expansions.neo1.prices['9'].url, `${direct}274409`, 'anche dove PokeWallet non aveva niente');
  assert.equal((merged.expansions.svp.prices['1'] as Record<string, unknown>).idProduct, undefined, 'idProduct non finisce nello snapshot');
});

test('un idProduct non valido non produce link', () => {
  const cm = blob({ expansions: { neo1: { prices: { '9': { low: 44.9, idProduct: 0 } }, aliases: [] }, xy1: { prices: { '1': { low: 1, idProduct: -3 } } } } });
  const merged = mergeCardmarketPrices(pokewallet(), cm, NOW);
  assert.equal(merged.expansions.neo1.prices['9'].url, undefined);
  assert.equal(merged.expansions.xy1.prices['1'].url, undefined, 'e il TCGplayer resta tolto');
});

test('riconosce i link Cardmarket', () => {
  assert.ok(isCardmarketUrl(CM_URL));
  assert.ok(isCardmarketUrl('https://cardmarket.com/it/Pokemon'));
  assert.ok(!isCardmarketUrl(TP_URL));
  assert.ok(!isCardmarketUrl('https://evil.com/?u=https://www.cardmarket.com/'));
  assert.ok(!isCardmarketUrl(undefined));
});
