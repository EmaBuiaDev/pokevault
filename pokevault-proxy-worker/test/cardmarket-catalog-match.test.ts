import { test } from 'node:test';
import assert from 'node:assert/strict';
import { matchCatalog, matchKey, normText, parseProductName } from '../scripts/lib/cardmarket-catalog-match.mjs';

test('nome del prodotto Cardmarket: nome e attacchi', () => {
  assert.deepEqual(parseProductName('Snivy [Tackle | Vine Whip]'), { name: 'snivy', attacks: ['tackle', 'vinewhip'] });
  assert.deepEqual(parseProductName('Professor Juniper'), { name: 'professorjuniper', attacks: [] });
  assert.equal(normText('Charizard-EX'), normText('Charizard EX'));
  assert.equal(matchKey('Snivy', ['Vine Whip', 'Tackle']), matchKey('snivy', ['tackle', 'vine whip']));
});

const products = [
  { idProduct: 1, name: 'Snivy [Slam]', idExpansion: 100 },
  { idProduct: 2, name: 'Snivy [Slam]', idExpansion: 200 },
  { idProduct: 3, name: 'Tepig [Ember]', idExpansion: 100 },
  { idProduct: 4, name: 'Oshawott [Water Pulse]', idExpansion: 100 },
  { idProduct: 5, name: 'Potion', idExpansion: 100 },
  { idProduct: 6, name: 'Potion', idExpansion: 200 },
  { idProduct: 7, name: 'Switch', idExpansion: 100 },
  { idProduct: 8, name: 'Switch', idExpansion: 100 },
];

test("l'espansione si decide a maggioranza, poi la carta dentro quella", () => {
  const cards = [
    { card_id: 'A1', expansion_id: 'bwp', nome: 'Snivy', attacks: ['Slam'] },
    { card_id: 'A2', expansion_id: 'bwp', nome: 'Tepig', attacks: ['Ember'] },
    { card_id: 'A3', expansion_id: 'bwp', nome: 'Oshawott', attacks: ['Water Pulse'] },
    { card_id: 'A4', expansion_id: 'bwp', nome: 'Potion', attacks: [] },
  ];
  const { links, perSet } = matchCatalog(cards, [], products);
  assert.equal(perSet.bwp.idExpansion, 100);
  assert.deepEqual(links.map((l) => [l.card_id, l.id_product]), [['A1', 1], ['A2', 3], ['A3', 4], ['A4', 5]]);
});

test('le carte gia collegate decidono l espansione e i loro prodotti non si riusano', () => {
  const cards = [{ card_id: 'B2', expansion_id: 'x', nome: 'Snivy', attacks: ['Slam'] }];
  // Una carta del set e' gia' collegata a un prodotto dell'espansione 200.
  const { links, perSet } = matchCatalog(cards, [{ expansion_id: 'x', id_product: 6 }], products);
  assert.equal(perSet.x.idExpansion, 200);
  assert.deepEqual(links, [{ card_id: 'B2', id_product: 2 }]);
});

test('due candidati nella stessa espansione: non si collega, si segnala', () => {
  const cards = [
    { card_id: 'C1', expansion_id: 'y', nome: 'Tepig', attacks: ['Ember'] },
    { card_id: 'C2', expansion_id: 'y', nome: 'Switch', attacks: [] },
  ];
  const { links, perSet } = matchCatalog(cards, [], products);
  assert.deepEqual(links, [{ card_id: 'C1', id_product: 3 }]);
  assert.deepEqual(perSet.y.ambiguous, ['C2']);
});

test('nessun indizio sull espansione: niente collegamenti', () => {
  const cards = [{ card_id: 'D1', expansion_id: 'z', nome: 'Potion', attacks: [] }];
  const { links, perSet } = matchCatalog(cards, [], products);
  assert.deepEqual(links, []);
  assert.deepEqual(perSet.z.none, ['D1']);
});

test("vince l'espansione che copre piu' carte, non quella con piu' candidati unici (promo BW)", () => {
  // 1611 = i promo veri: hanno tutte e tre le carte, ma Snivy e Tepig stanno
  // anche altrove. 4218 = le timbrate: solo due carte, uniche li'.
  const catalog = [
    { idProduct: 10, name: 'Snivy [Slam]', idExpansion: 1611 },
    { idProduct: 11, name: 'Snivy [Slam]', idExpansion: 1623 },
    { idProduct: 12, name: 'Tepig [Ember]', idExpansion: 1611 },
    { idProduct: 13, name: 'Tepig [Ember]', idExpansion: 1623 },
    { idProduct: 14, name: 'Keldeo EX [Rush In]', idExpansion: 1611 },
    { idProduct: 20, name: 'Pansage [Bullet Seed]', idExpansion: 4218 },
    { idProduct: 21, name: 'Pidove [Gust]', idExpansion: 4218 },
  ];
  const cards = [
    { card_id: 'S', expansion_id: 'bwp', nome: 'Snivy', attacks: ['Slam'] },
    { card_id: 'T', expansion_id: 'bwp', nome: 'Tepig', attacks: ['Ember'] },
    { card_id: 'K', expansion_id: 'bwp', nome: 'Keldeo EX', attacks: ['Rush In'] },
    { card_id: 'P', expansion_id: 'bwp', nome: 'Pansage', attacks: ['Bullet Seed'] },
    { card_id: 'D', expansion_id: 'bwp', nome: 'Pidove', attacks: ['Gust'] },
  ];
  const { links, perSet } = matchCatalog(cards, [], catalog);
  assert.equal(perSet.bwp.idExpansion, 1611);
  assert.deepEqual(links.map((l) => l.id_product), [10, 12, 14]);
});

test('due espansioni a pari merito: nessuna scelta', () => {
  const catalog = [
    { idProduct: 1, name: 'Potion', idExpansion: 100 },
    { idProduct: 2, name: 'Potion', idExpansion: 200 },
  ];
  const { links, perSet } = matchCatalog([{ card_id: 'X', expansion_id: 'q', nome: 'Potion', attacks: [] }], [], catalog);
  assert.equal(perSet.q.idExpansion, null);
  assert.deepEqual(links, []);
});
