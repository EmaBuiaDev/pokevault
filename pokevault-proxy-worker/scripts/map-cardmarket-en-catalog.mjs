#!/usr/bin/env node
// Collega a Cardmarket le carte EN che il gemello italiano e TCGdex non
// collegano (14/10/2026): promo, Trainer Kit, set vecchi. Fonte: il catalogo
// pubblico dei prodotti Cardmarket (products_singles_6.json), lo stesso posto
// del listino. Nessuna chiamata a PokeWallet.
//
// L'abbinamento (lib/cardmarket-catalog-match.mjs): espansione Cardmarket a
// maggioranza, poi nome + attacchi dentro quella; si collega solo con UN
// candidato, i casi doppi finiscono nel rapporto. Scrive solo le carte senza
// collegamento, con source 'cm-catalog'.
//
//   node scripts/map-cardmarket-en-catalog.mjs              (prova: rapporto)
//   node scripts/map-cardmarket-en-catalog.mjs --apply
//   node scripts/map-cardmarket-en-catalog.mjs --catalog FILE   (catalogo da file)
import fs from 'node:fs';
import path from 'node:path';
import { d1Select, d1ExecuteFile, sqlString, workerRoot } from './lib/cardmarket.mjs';
import { matchCatalog } from './lib/cardmarket-catalog-match.mjs';

const EN_DB = 'pokevault-catalog-en';
const CATALOG_URL = 'https://downloads.s3.cardmarket.com/productCatalog/productList/products_singles_6.json';
const args = process.argv.slice(2);
const APPLY = args.includes('--apply');
const catalogFile = args.includes('--catalog') ? args[args.indexOf('--catalog') + 1] : null;

const catalog = catalogFile
  ? JSON.parse(fs.readFileSync(catalogFile, 'utf8'))
  : await (await fetch(CATALOG_URL, { signal: AbortSignal.timeout(120000) })).json();
const products = catalog.products ?? [];
console.log(`Catalogo Cardmarket: ${products.length} prodotti (${catalog.createdAt ?? '?'})`);

const missing = await d1Select(
  `SELECT c.card_id, c.expansion_id, c.nome, c.attacchi_json FROM cards c
    WHERE NOT EXISTS (SELECT 1 FROM card_cardmarket m WHERE m.card_id = c.card_id)`,
  EN_DB,
);
const expansions = [...new Set(missing.map((c) => c.expansion_id))];
const linked = expansions.length === 0 ? [] : await d1Select(
  `SELECT c.expansion_id, m.id_product FROM cards c JOIN card_cardmarket m ON m.card_id = c.card_id
    WHERE c.expansion_id IN (${expansions.map(sqlString).join(',')})`,
  EN_DB,
);
const cards = missing.map((c) => {
  let attacks = [];
  try { attacks = (JSON.parse(c.attacchi_json || '[]') ?? []).map((a) => a?.nome).filter(Boolean); } catch { /* attacchi illeggibili: solo nome */ }
  return { card_id: c.card_id, expansion_id: c.expansion_id, nome: c.nome, attacks };
});
console.log(`Carte EN senza collegamento: ${cards.length} in ${expansions.length} set`);

const { links, perSet } = matchCatalog(cards, linked, products);
for (const [exp, s] of Object.entries(perSet).sort((a, b) => b[1].linked - a[1].linked)) {
  const tot = s.linked + s.ambiguous.length + s.none.length;
  console.log(`${exp.padEnd(12)} espansione CM ${String(s.idExpansion ?? '-').padEnd(6)} collegate ${s.linked}/${tot}`
    + (s.ambiguous.length ? `  doppie: ${s.ambiguous.slice(0, 4).join(' ')}${s.ambiguous.length > 4 ? '...' : ''}` : ''));
}
console.log(`Totale: ${links.length} collegamenti su ${cards.length}`);

if (!APPLY) { console.log('Prova: niente scritto. Aggiungi --apply.'); }
else if (links.length) {
  const sql = [];
  for (let i = 0; i < links.length; i += 200) {
    sql.push('INSERT OR IGNORE INTO card_cardmarket (card_id, id_product, source) VALUES\n'
      + links.slice(i, i + 200).map((l) => `(${sqlString(l.card_id)}, ${l.id_product}, 'cm-catalog')`).join(',\n') + ';');
  }
  const file = path.join(workerRoot, 'map-cardmarket-en-catalog.sql');
  fs.writeFileSync(file, sql.join('\n') + '\n');
  await d1ExecuteFile(file, EN_DB);
  fs.rmSync(file);
  console.log(`Scritti ${links.length} collegamenti (source cm-catalog).`);
}
