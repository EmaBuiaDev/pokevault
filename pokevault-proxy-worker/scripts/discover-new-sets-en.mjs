#!/usr/bin/env node
// Set inglesi nuovi su TCGdex -> catalogo EN (piano carte EN, Fase 2, 13/10/2026).
//
// Confronta l'elenco dei set di TCGdex /v2/en con il database inglese
// (pokevault-catalog-en, colonna tcgdex_id) e importa con ingest-en-set.mjs:
//   - i set che nel catalogo EN non ci sono ancora;
//   - i set non pubblicati usciti negli ultimi 120 giorni: le immagini di un
//     set appena uscito arrivano un po' alla volta, e a ogni giro il set si
//     riprova finche' non passa la soglia dell'80%.
// Il gemello del job italiano (discover-new-sets.mjs), che resta com'e'.
//
//   node scripts/discover-new-sets-en.mjs              (prova: elenca e basta)
//   node scripts/discover-new-sets-en.mjs --ingest     (importa)
//   opzioni: --ingested-list FILE  scrive gli id importati, uno per riga
import { writeFileSync } from 'node:fs';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { runWrangler } from './lib/cardmarket.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const EN_DB = 'pokevault-catalog-en';
const RECENT_DAYS = 120;
const SET_TIMEOUT_MS = 60 * 60 * 1000;
const args = process.argv.slice(2);
const INGEST = args.includes('--ingest');
const listIdx = args.indexOf('--ingested-list');
const LIST_FILE = listIdx >= 0 ? args[listIdx + 1] : null;

async function enSelect(sql) {
  const oneLine = sql.replace(/\s+/g, ' ').trim();
  const out = await runWrangler(['d1', 'execute', EN_DB, '--remote', '--json', '--command', `"${oneLine.replace(/"/g, '\\"')}"`]);
  const start = out.indexOf('[');
  return JSON.parse(start >= 0 ? out.slice(start) : out)[0]?.results ?? [];
}

function runIngest(setId) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [path.join(__dirname, 'ingest-en-set.mjs'), setId, '--apply'], {
      cwd: path.resolve(__dirname, '..'),
      stdio: ['ignore', 'inherit', 'inherit'],
    });
    const timer = setTimeout(() => child.kill('SIGKILL'), SET_TIMEOUT_MS);
    child.on('close', (code) => { clearTimeout(timer); resolve(code === 0 ? 'ok' : code === 3 ? 'vuoto' : 'errore'); });
    child.on('error', () => { clearTimeout(timer); resolve('errore'); });
  });
}

async function main() {
  const res = await fetch('https://api.tcgdex.net/v2/en/sets');
  if (!res.ok) throw new Error(`TCGdex /v2/en/sets: HTTP ${res.status}`);
  const list = (await res.json())
    .filter(s => (s.cardCount?.total ?? 0) > 0)
    .filter(s => !/^(A\d|B\d|P-A|P-B)/.test(s.id)); // TCG Pocket: gioco digitale

  const known = new Map((await enSelect('SELECT tcgdex_id, published, release_date FROM expansions')).map(r => [r.tcgdex_id, r]));
  const cutoff = new Date(Date.now() - RECENT_DAYS * 86400000).toISOString().slice(0, 10);
  const missing = list.filter(s => !known.has(s.id)).map(s => s.id);
  const retry = [...known.values()]
    .filter(r => !r.published && (r.release_date || '') >= cutoff)
    .map(r => r.tcgdex_id);

  console.log(`TCGdex EN: ${list.length} set; nel catalogo EN: ${known.size}`);
  console.log(`Set EN nuovi: ${missing.length}${missing.length ? ` (${missing.join(', ')})` : ''}`);
  console.log(`Set EN recenti non ancora pubblicati, da riprovare: ${retry.length}${retry.length ? ` (${retry.join(', ')})` : ''}`);

  const todo = [...new Set([...missing, ...retry])];
  const ingested = [];
  const failed = [];
  if (INGEST) {
    for (const id of todo) {
      console.log(`\n=== ${id} ===`);
      const outcome = await runIngest(id);
      if (outcome === 'ok') ingested.push(id);
      else if (outcome === 'vuoto') console.log(`${id}: vuoto su TCGdex, saltato`);
      else failed.push(id);
    }
    console.log(`\nImportati: ${ingested.length}${ingested.length ? ` (${ingested.join(', ')})` : ''}; falliti: ${failed.length}${failed.length ? ` (${failed.join(', ')})` : ''}`);
  } else {
    console.log('Prova: niente importato (aggiungi --ingest).');
  }
  if (LIST_FILE) writeFileSync(LIST_FILE, ingested.join('\n'));
  if (failed.length) process.exit(1);
}

main().catch((err) => {
  console.error('Errore:', err.message || err);
  process.exit(1);
});
