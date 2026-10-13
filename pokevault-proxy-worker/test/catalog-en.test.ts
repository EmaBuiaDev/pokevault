import { test } from 'node:test';
import assert from 'node:assert/strict';
import { enCardImageKey, enSetAssetKeys, normalizeEnCardNumber, parseEnglishRequest } from '../src/catalog-en-paths.ts';

const req = (path: string) => parseEnglishRequest(new URL(`https://w.example${path}`));

test('le rotte del catalogo EN', () => {
  assert.deepEqual(req('/v1/en/health'), { kind: 'health' });
  assert.deepEqual(req('/v1/en/expansions'), { kind: 'expansions' });
  assert.deepEqual(req('/v1/en/expansions/SV08/cards'), { kind: 'cards', expansionId: 'sv08' });
  assert.deepEqual(req('/v1/en/expansions/30th-c/cards'), { kind: 'cards', expansionId: '30th-c' });
  assert.deepEqual(req('/images/en/sv08/012?size=low'), { kind: 'cardImage', setCode: 'SV08', cardNumber: '012' });
  assert.deepEqual(req('/sets/sv08/image?source=en'), { kind: 'setLogo', setCode: 'SV08' });
  assert.deepEqual(req('/sets/SV08/symbol?source=en'), { kind: 'setSymbol', setCode: 'SV08' });
});

test("le rotte italiane non sono del catalogo EN: restano al Worker di sempre", () => {
  assert.equal(req('/v1/expansions'), null);
  assert.equal(req('/v1/expansions/sv08/cards'), null);
  assert.equal(req('/images/it/SV08/12'), null);
  assert.equal(req('/sets/SV08/image'), null);
  assert.equal(req('/sets/SV08/image?source=ita'), null);
  assert.equal(req('/sets/SV08/symbol'), null);
  assert.equal(req('/ita/prices.json'), null);
  assert.equal(req('/v1/en'), null);
});

test('una richiesta di immagine strana non arriva a R2', () => {
  assert.equal(req('/images/en/SV08/..%2F..%2Fit'), null);
  assert.equal(req('/images/en/%E0%A4%A/1'), null);
  assert.equal(req('/images/en/SV08/'), null);
});

test('chiavi R2 delle carte: cartella maiuscola, numero senza zeri, promo come sono', () => {
  assert.equal(enCardImageKey('sv08', '012'), 'en/SV08/SV08_EN_12.webp');
  assert.equal(enCardImageKey('SV08', '0'), 'en/SV08/SV08_EN_0.webp');
  assert.equal(enCardImageKey('swshp', 'SWSH026'), 'en/SWSHP/SWSHP_EN_SWSH026.webp');
  assert.equal(enCardImageKey('30th-c', '7'), 'en/30TH-C/30TH-C_EN_7.webp');
  assert.equal(normalizeEnCardNumber(' 007 '), '7');
  assert.equal(normalizeEnCardNumber('24a'), '24a');
});

test('logo e simbolo: WebP prima del PNG', () => {
  assert.deepEqual(enSetAssetKeys('pl3', 'logo'), ['en/PL3/logo.webp', 'en/PL3/logo.png']);
  assert.deepEqual(enSetAssetKeys('PL3', 'symbol'), ['en/PL3/symbol.webp', 'en/PL3/symbol.png']);
});
