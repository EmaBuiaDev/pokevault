import { test } from 'node:test';
import assert from 'node:assert/strict';
import { quotaDecision } from '../scripts/lib/d1-quota.mjs';

test('quota D1: sotto la soglia si va, sopra ci si ferma', () => {
  assert.equal(quotaDecision(10_000, 0, 70_000), 'go');
  assert.equal(quotaDecision(70_000, 0, 70_000), 'pause');
});

test('quota D1 illeggibile: un errore isolato passa, di fila ci si ferma', () => {
  assert.equal(quotaDecision(null, 1, 70_000, 2), 'go');
  assert.equal(quotaDecision(null, 2, 70_000, 2), 'go');
  assert.equal(quotaDecision(null, 3, 70_000, 2), 'pause');
});
