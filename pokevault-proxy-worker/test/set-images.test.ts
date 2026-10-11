import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildItalianSetSymbolCandidates, isSetImagePath, parseSetSymbolCode } from '../src/set-images.ts';

test('logo e simbolo sono immagini del set, il payload del set no', () => {
  assert.equal(isSetImagePath('/sets/PAF/image'), true);
  assert.equal(isSetImagePath('/sets/PAF/symbol'), true);
  assert.equal(isSetImagePath('/sets/PAF'), false);
  assert.equal(isSetImagePath('/sets'), false);
  assert.equal(isSetImagePath('/sets/PAF/cards'), false);
  assert.equal(isSetImagePath('/images/it/PAF/1'), false);
});

test('il codice del simbolo esce in maiuscolo e decodificato', () => {
  assert.equal(parseSetSymbolCode('/sets/paf/symbol'), 'PAF');
  assert.equal(parseSetSymbolCode('/sets/SWSH12%20TG/symbol'), 'SWSH12 TG');
  assert.equal(parseSetSymbolCode('/sets/PAF/image'), null);
  assert.equal(parseSetSymbolCode('/sets/%20/symbol'), null);
  assert.equal(parseSetSymbolCode('/sets/%E0%A4%A/symbol'), null);
});

test('il simbolo si cerca nella cartella del logo, webp prima di png', () => {
  assert.deepEqual(buildItalianSetSymbolCandidates('it', 'PAF'), ['it/PAF/symbol.webp', 'it/PAF/symbol.png']);
});
