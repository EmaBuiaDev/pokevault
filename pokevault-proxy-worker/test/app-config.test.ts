import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseAppConfig } from '../src/app-config.ts';

test("interruttori: senza file, file rotto o valori strani resta tutto spento", () => {
  assert.deepEqual(parseAppConfig(null), { englishCatalog: false });
  assert.deepEqual(parseAppConfig(''), { englishCatalog: false });
  assert.deepEqual(parseAppConfig('{rotto'), { englishCatalog: false });
  assert.deepEqual(parseAppConfig('{"englishCatalog":"true"}'), { englishCatalog: false });
  assert.deepEqual(parseAppConfig('{"englishCatalog":1}'), { englishCatalog: false });
});

test('interruttori: solo un true vero accende', () => {
  assert.deepEqual(parseAppConfig('{"englishCatalog":true}'), { englishCatalog: true });
  assert.deepEqual(parseAppConfig('{"englishCatalog":false,"altro":true}'), { englishCatalog: false });
});
