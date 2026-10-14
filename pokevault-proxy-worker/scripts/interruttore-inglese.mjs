#!/usr/bin/env node
// L'interruttore del catalogo inglese nell'app, senza una release (14/10/2026).
//
//   node scripts/interruttore-inglese.mjs stato
//   node scripts/interruttore-inglese.mjs acceso
//   node scripts/interruttore-inglese.mjs spento
//
// Scrive app/config.json sul bucket R2 di produzione; il Worker lo serve su
// /v1/app-config e l'app lo usa dall'avvio successivo (il Worker lo tiene un
// minuto in memoria, i telefoni fino a cinque).
//
// Prima di accendere controlla che la produzione serva davvero il catalogo
// EN (/v1/en/health): accenderlo senza farebbe vedere agli utenti selettori
// che portano a liste vuote. --forza salta il controllo.
import { writeFileSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { runWrangler } from './lib/cardmarket.mjs';

const PROD = 'https://pokevault-proxy.pokevault-emanu.workers.dev';
const OBJECT = 'pokevault-images/app/config.json';

const [command, flag] = process.argv.slice(2);

async function current() {
  const res = await fetch(`${PROD}/v1/app-config?t=${Date.now()}`);
  if (!res.ok) throw new Error(`/v1/app-config risponde ${res.status}: il Worker di produzione ha la rotta?`);
  return res.json();
}

async function write(englishCatalog) {
  const dir = mkdtempSync(join(tmpdir(), 'pv-config-'));
  const file = join(dir, 'config.json');
  writeFileSync(file, JSON.stringify({ englishCatalog }, null, 2) + '\n');
  await runWrangler(['r2', 'object', 'put', OBJECT, `--file="${file}"`, '--content-type', 'application/json', '--remote'], {}, 2);
}

if (command === 'stato') {
  const config = await current();
  console.log(`Catalogo inglese nell'app: ${config.englishCatalog ? 'ACCESO' : 'spento'}`);
} else if (command === 'acceso') {
  if (flag !== '--forza') {
    const health = await fetch(`${PROD}/v1/en/health`);
    if (!health.ok) {
      console.error(`Fermo: la produzione non serve il catalogo EN (/v1/en/health = ${health.status}).`);
      console.error("Prima va pubblicato il Worker con catalog_en; per accendere lo stesso: acceso --forza");
      process.exit(1);
    }
    const body = await health.json();
    console.log(`Catalogo EN in produzione: ${body.published} set pubblicati, ${body.cards} carte.`);
  }
  await write(true);
  console.log("Acceso. L'app lo vede dal prossimo avvio (fino a 5 minuti di cache).");
} else if (command === 'spento') {
  await write(false);
  console.log("Spento. L'app torna senza inglese dal prossimo avvio.");
} else {
  console.log('Uso: node scripts/interruttore-inglese.mjs stato | acceso [--forza] | spento');
  process.exit(command ? 1 : 0);
}
