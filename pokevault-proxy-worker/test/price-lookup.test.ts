import { test } from 'node:test';
import assert from 'node:assert/strict';
import { resolveExpansionPrices } from '../src/price-lookup.ts';

type E = { baseSetCode: string; prices: Record<string, { low: number }> };

function snapshot() {
  return {
    expansions: {
      swsh9: { baseSetCode: 'BRS', prices: { '183': { low: 20 }, '1': { low: 0.1 }, TG24: { low: 5 } } } as E,
      swsh9tg: { baseSetCode: 'BRS-TG', prices: { TG01: { low: 3 }, TG24: { low: 4 } } } as E,
      me05: { baseSetCode: 'PBL', prices: { '30': { low: 0.02 } } } as E,
    },
    aliases: { swsh9: 'swsh9tg', swsh9tg: 'swsh9tg', me05: 'me05', pbl: 'me05' } as Record<string, string>,
  };
}

test('un set rubato da un alias risponde con le sue carte piu quelle della galleria', () => {
  const r = resolveExpansionPrices(snapshot(), 'swsh9');
  assert.equal(r?.expansionId, 'swsh9');
  assert.deepEqual(r?.entry.prices['183'], { low: 20 }, 'il Moltres 183 c e');
  assert.deepEqual(r?.entry.prices.TG01, { low: 3 }, 'e anche le carte della galleria');
  assert.deepEqual(r?.entry.prices.TG24, { low: 5 }, 'dove ci sono tutte e due vince il set chiesto');
  assert.equal(r?.entry.baseSetCode, 'BRS');
});

test('la galleria chiesta per nome resta solo la galleria', () => {
  const r = resolveExpansionPrices(snapshot(), 'swsh9tg');
  assert.equal(r?.expansionId, 'swsh9tg');
  assert.deepEqual(Object.keys(r!.entry.prices).sort(), ['TG01', 'TG24']);
});

test('alias normali e set senza alias funzionano come prima', () => {
  const s = snapshot();
  assert.equal(resolveExpansionPrices(s, 'pbl')?.expansionId, 'me05');
  assert.equal(resolveExpansionPrices(s, 'pbl')?.entry, s.expansions.me05, 'stesso oggetto, nessuna copia');
  assert.equal(resolveExpansionPrices(s, 'me05')?.entry, s.expansions.me05);
  assert.equal(resolveExpansionPrices(s, 'boh'), null);
});

test('non modifica lo snapshot', () => {
  const s = snapshot();
  const before = JSON.stringify(s);
  resolveExpansionPrices(s, 'swsh9');
  assert.equal(JSON.stringify(s), before);
});
