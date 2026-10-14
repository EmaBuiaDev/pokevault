#!/usr/bin/env node
// Importa TUTTI i set inglesi nel catalogo EN, uno per uno, con
// scripts/ingest-en-set.mjs (piano carte EN, Fase 2, 13/10/2026).
//
// Fatto per girare ore senza nessuno davanti:
//   - ogni set e' un processo a parte, con un tempo massimo: se si pianta
//     viene ucciso (con tutto l'albero dei wrangler) e ritentato;
//   - dopo MAX_ATTEMPTS tentativi il set si segna 'fallito' e si passa oltre;
//   - lo stato sta in scripts/.ingest-en-stato.json: rilanciato, riparte da
//     dove era (i set 'fatto' non si rifanno, i 'fallito' solo con --ritenta);
//   - tutto finisce in scripts/.ingest-en-log.txt.
//
// Uso:
//   node scripts/ingest-en-tutti.mjs              (tutti quelli non ancora fatti)
//   node scripts/ingest-en-tutti.mjs --ritenta    (anche quelli falliti)
//   node scripts/ingest-en-tutti.mjs --incompleti (rifa' i set fatti con qualche immagine in meno:
//                                                  ricarica solo quelle mancanti)
//   node scripts/ingest-en-tutti.mjs --poi-prezzi (alla fine: collegamento Cardmarket e listino EN)
//   node scripts/ingest-en-tutti.mjs --solo sv08,pl3
//
// Freno sulla quota D1: prima di ogni set (e di ogni gruppo di prezzi) si
// leggono le righe scritte oggi da tutto l'account; sopra 70.000 si aspetta il
// nuovo giorno UTC, cosi' la produzione ha sempre spazio (lib/d1-quota.mjs).
import { appendFileSync, existsSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { spawn, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { waitForD1Quota } from './lib/d1-quota.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
// INGEST_EN_STATO / INGEST_EN_LOG: file diversi, per provare lo script senza toccare un giro in corso.
const STATE_FILE = process.env.INGEST_EN_STATO || path.join(__dirname, '.ingest-en-stato.json');
const LOG_FILE = process.env.INGEST_EN_LOG || path.join(__dirname, '.ingest-en-log.txt');
const PARALLEL = 2;
const MAX_ATTEMPTS = 3;
const SET_TIMEOUT_MS = 75 * 60 * 1000; // il set piu' grande e' ~450 carte: ~30 minuti

const args = process.argv.slice(2);
const retryFailed = args.includes('--ritenta');
const redoIncomplete = args.includes('--incompleti');
const thenPrices = args.includes('--poi-prezzi');

/** "... 132 carte, con immagine 128 ...": un set fatto a cui manca qualche immagine. */
function isIncomplete(entry) {
  const m = /: (\d+) carte, con immagine (\d+)/.exec(entry?.summary || '');
  return !!m && Number(m[2]) < Number(m[1]);
}
const onlyIdx = args.indexOf('--solo');
const only = onlyIdx >= 0 ? new Set(args[onlyIdx + 1].split(',').map(s => s.trim())) : null;

function log(line) {
  const stamped = `[${new Date().toISOString().replace('T', ' ').slice(0, 19)}] ${line}`;
  console.log(stamped);
  appendFileSync(LOG_FILE, `${stamped}\n`);
}

function loadState() {
  if (!existsSync(STATE_FILE)) return {};
  try {
    return JSON.parse(readFileSync(STATE_FILE, 'utf8'));
  } catch {
    return {};
  }
}

let state = loadState();
function saveState() {
  writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
}

function killTree(pid) {
  if (process.platform === 'win32') spawnSync('taskkill', ['/PID', String(pid), '/T', '/F'], { stdio: 'ignore' });
  else try { process.kill(-pid, 'SIGKILL'); } catch { /* gia' morto */ }
}

function runSet(setId) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [path.join(__dirname, 'ingest-en-set.mjs'), setId, '--apply'], {
      cwd: path.resolve(__dirname, '..'),
      stdio: ['ignore', 'pipe', 'pipe'],
      detached: process.platform !== 'win32',
    });
    let output = '';
    const keep = (d) => {
      output += d.toString();
      if (output.length > 20000) output = output.slice(-20000);
    };
    child.stdout.on('data', keep);
    child.stderr.on('data', keep);
    const timer = setTimeout(() => {
      output += `\n[timeout dopo ${SET_TIMEOUT_MS / 60000} minuti: processo ucciso]`;
      killTree(child.pid);
    }, SET_TIMEOUT_MS);
    child.on('close', (code) => {
      clearTimeout(timer);
      resolve({ code, output });
    });
    child.on('error', (err) => {
      clearTimeout(timer);
      resolve({ code: -1, output: `${output}\n${err.message}` });
    });
  });
}

async function fetchSetList() {
  for (let i = 1; i <= 5; i += 1) {
    try {
      const res = await fetch('https://api.tcgdex.net/v2/en/sets');
      if (res.ok) return await res.json();
    } catch { /* riprova */ }
    await new Promise(r => setTimeout(r, 3000 * i));
  }
  throw new Error('TCGdex non risponde: elenco dei set non disponibile');
}

/** Uno script node a parte, con tempo massimo: per i passi dopo l'import. */
function runScript(args, timeoutMs) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, args, {
      cwd: path.resolve(__dirname, '..'),
      stdio: ['ignore', 'pipe', 'pipe'],
      detached: process.platform !== 'win32',
    });
    let output = '';
    const keep = (d) => { output += d.toString(); if (output.length > 20000) output = output.slice(-20000); };
    child.stdout.on('data', keep);
    child.stderr.on('data', keep);
    const timer = setTimeout(() => { output += '\n[timeout: processo ucciso]'; killTree(child.pid); }, timeoutMs);
    child.on('close', (code) => { clearTimeout(timer); resolve({ code, output }); });
    child.on('error', (err) => { clearTimeout(timer); resolve({ code: -1, output: `${output}\n${err.message}` }); });
  });
}

/** Importa i set dati, due alla volta, col freno sulla quota D1 prima di ognuno. */
async function importSets(todo, phase) {
  log(`[${phase}] set da fare: ${todo.length} (in parallelo: ${PARALLEL})`);
  let next = 0;
  const worker = async () => {
    while (next < todo.length) {
      const setId = todo[next++];
      for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
        await waitForD1Quota(log);
        const started = Date.now();
        log(`${setId}: inizio (tentativo ${attempt}/${MAX_ATTEMPTS})`);
        const { code, output } = await runSet(setId);
        const minutes = ((Date.now() - started) / 60000).toFixed(1);
        const summary = output.split('\n').filter(l => /carte, con immagine|Errore|timeout/.test(l)).join(' | ').trim();
        if (code === 0) {
          state[setId] = { status: 'fatto', at: new Date().toISOString(), minutes: Number(minutes), summary };
          saveState();
          log(`${setId}: FATTO in ${minutes} min -- ${summary}`);
          break;
        }
        // Un errore "di decisione" (codice che collide, Pocket, set vuoto) non si ritenta.
        const permanent = /coincide con l'italiano|TCG Pocket|non conosce il set|set vuoto/.test(output);
        log(`${setId}: errore (codice ${code}, ${minutes} min) -- ${summary || output.slice(-400).replace(/\s+/g, ' ')}`);
        if (permanent || attempt === MAX_ATTEMPTS) {
          state[setId] = { status: 'fallito', at: new Date().toISOString(), attempts: attempt, error: (summary || output.slice(-800)).slice(0, 800) };
          saveState();
          break;
        }
        await new Promise(r => setTimeout(r, 60000 * attempt));
      }
    }
  };
  await Promise.all(Array.from({ length: PARALLEL }, worker));
}

async function main() {
  const list = await fetchSetList();
  // TCG Pocket (A1, B1, P-A...) e' il gioco digitale; i set vuoti non hanno niente da importare.
  const candidates = list
    .filter(s => (s.cardCount?.total ?? 0) > 0)
    .filter(s => !/^(A\d|B\d|P-A|P-B)/.test(s.id))
    .map(s => s.id)
    .filter(id => !only || only.has(id));
  log(`Set candidati: ${candidates.length}`);

  // Fase 1: i set non ancora fatti (e i falliti, con --ritenta).
  await importSets(candidates.filter((id) => {
    const st = state[id]?.status;
    if (st === 'fatto') return false;
    if (st === 'fallito' && !retryFailed) return false;
    return true;
  }), 'set');

  // Fase 2: i set fatti a cui manca qualche immagine (ricarica solo quelle).
  if (redoIncomplete) {
    await importSets(candidates.filter(id => state[id]?.status === 'fatto' && isIncomplete(state[id])), 'incompleti');
  }

  // Fase 3: collegamento Cardmarket (dieci set per volta, col freno) e listino EN.
  if (thenPrices) {
    const done = candidates.filter(id => state[id]?.status === 'fatto');
    const expansionIds = done.map(id => id.toLowerCase().replace(/\./g, 'pt'));
    // Gli id dei gemelli sono quelli italiani (sv03.5 -> sv3pt5): li conosce lo script stesso,
    // quindi qui si passano a gruppi solo per spezzare le scritture; --only li filtra lui.
    for (let i = 0; i < expansionIds.length; i += 10) {
      await waitForD1Quota(log);
      const batch = expansionIds.slice(i, i + 10);
      const { code, output } = await runScript([path.join(__dirname, 'map-cardmarket-en.mjs'), '--apply', '--only-missing', '--only', batch.join(',')], 30 * 60 * 1000);
      log(`prezzi ${i + 1}-${i + batch.length}/${expansionIds.length}: codice ${code} -- ${output.split('\n').filter(l => /Collegamenti|stampe da riscrivere|Errore/.test(l)).join(' | ')}`);
    }
    // I gemelli con id diverso da quello TCGdex (sv3pt5, pgo...) li prende un giro su tutto.
    await waitForD1Quota(log);
    const all = await runScript([path.join(__dirname, 'map-cardmarket-en.mjs'), '--apply', '--only-missing'], 60 * 60 * 1000);
    log(`prezzi, giro finale: codice ${all.code} -- ${all.output.split('\n').filter(l => /Collegamenti|stampe da riscrivere|Errore/.test(l)).join(' | ')}`);
    const build = await runScript([path.join(__dirname, 'build-cardmarket-prices-en.mjs'), '--upload', '--force'], 20 * 60 * 1000);
    log(`listino EN: codice ${build.code} -- ${build.output.split('\n').filter(l => /Carte EN|Caricato|ERRORE/.test(l)).join(' | ')}`);
  }

  const doneCount = Object.values(state).filter(s => s.status === 'fatto').length;
  const failed = Object.entries(state).filter(([, s]) => s.status === 'fallito').map(([id]) => id);
  log(`FINE. Fatti in tutto: ${doneCount}. Falliti: ${failed.length}${failed.length ? ` (${failed.join(', ')})` : ''}`);
}

main().catch((err) => {
  log(`Errore fatale: ${err.message || err}`);
  process.exit(1);
});
