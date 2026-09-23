#!/usr/bin/env node
// Ingest di un'espansione storica da Pokemon Central Wiki (wiki.pokemoncentral.it).
//
// Perche' esiste, accanto a ingest-tcgdex-set.mjs e ingest-pokemon-official-set.mjs:
// per i set precedenti a Diamante & Perla nessuna delle due fonti solite ha le
// carte in italiano -- l'archivio ufficiale it-it parte da DP e le immagini IT
// di TCGdex partono da bw1 (2011). Il wiki italiano invece ha sia le scansioni
// sia una scheda per carta con illustratore, tipo, PS e stadio.
//
// Due avvertenze che valgono per tutti i set di questa fonte:
//  - le scansioni NON sono tutte italiane: dove non ne avevano una, hanno messo
//    quella inglese. La lingua va verificata a mano PRIMA, e i numeri inglesi
//    si passano in `inglesi`: quelle carte entrano in D1 con
//    image_status='missing' e non vengono caricate su R2.
//  - il nome del file non dice la lingua: "EnergiaAcquaSetBase102.jpg" e' una
//    scansione inglese che dice "ENERGY".
//
// Il manifest e' { setId, nome, serie, releaseDate, officialCount, inglesi[],
// carte: [{numero, nome, illustratore, tipo, ps, stadio, fileLocale}] }.
//
// Uso:
//   node scripts/ingest-pokemoncentral-set.mjs <manifest.json>            (dry-run)
//   node scripts/ingest-pokemoncentral-set.mjs <manifest.json> --apply

import { readFile, writeFile, mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import sharp from 'sharp';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerRoot = path.resolve(__dirname, '..');
const tmpDir = path.join(__dirname, '.pcw-tmp');
const BUCKET = 'pokevault-images';
const WEBP_QUALITY = 82;
const COVERAGE_THRESHOLD = 0.8;

const wranglerBin = path.join(workerRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'wrangler.cmd' : 'wrangler');

function runWrangler(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(wranglerBin, args, { cwd: workerRoot, shell: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let err = '';
    child.stderr.on('data', (d) => { err += d.toString(); });
    child.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`wrangler ${args.slice(0, 3).join(' ')} -> ${code}: ${err.slice(0, 300)}`))));
  });
}

const sql = (v) => (v === null || v === undefined || v === '' ? 'NULL' : `'${String(v).replace(/'/g, "''")}'`);

async function main() {
  const manifestPath = process.argv[2];
  const apply = process.argv.includes('--apply');
  if (!manifestPath) throw new Error('Uso: node scripts/ingest-pokemoncentral-set.mjs <manifest.json> [--apply]');

  const m = JSON.parse(await readFile(manifestPath, 'utf8'));
  const code = m.setId.toUpperCase();
  const inglesi = new Set((m.inglesi ?? []).map(String));

  const conImmagine = m.carte.filter((c) => !inglesi.has(String(c.numero)));
  const copertura = conImmagine.length / m.carte.length;
  const published = copertura >= COVERAGE_THRESHOLD;

  console.log(`Set ${m.setId} "${m.nome}" -- ${m.carte.length} carte`);
  console.log(`Scansioni italiane: ${conImmagine.length}/${m.carte.length} (${(copertura * 100).toFixed(1)}%) -> published = ${published ? 1 : 0}`);
  console.log(`Scartate perche' la scansione e' inglese: ${[...inglesi].join(', ') || 'nessuna'}`);

  if (!apply) {
    console.log('\nDRY RUN: nessuna scrittura. Rilancia con --apply.');
    return;
  }

  await rm(tmpDir, { recursive: true, force: true });
  await mkdir(tmpDir, { recursive: true });

  let caricate = 0;
  for (const c of conImmagine) {
    const webp = path.join(tmpDir, `${c.numero}.webp`);
    await sharp(c.fileLocale).flatten({ background: '#ffffff' }).webp({ quality: WEBP_QUALITY }).toFile(webp);
    await runWrangler(['r2', 'object', 'put', `${BUCKET}/it/${code}/${code}_IT_${c.numero}.webp`, '--file', webp, '--remote', '--content-type', 'image/webp']);
    caricate++;
    if (caricate % 20 === 0) console.log(`  caricate ${caricate}/${conImmagine.length}`);
  }
  console.log(`Immagini caricate su R2: ${caricate}`);

  const righe = m.carte.map((c) => {
    const stato = inglesi.has(String(c.numero)) ? 'missing' : 'ok';
    return `(${sql(`${code}_IT_${c.numero}.webp`)}, ${sql(m.setId)}, ${sql(c.numero)}, ${sql(c.nome)}, ${sql(c.tipo)}, ${sql(c.ps)}, NULL, '[]', ${sql(stato)}, ${stato === 'ok' ? 1 : 0}, ${sql(c.stadio)}, ${sql(c.illustratore)})`;
  });

  const linee = [
    `INSERT INTO expansions (id, card_count, official_count, name, series, sort_order, logo_key, published, coverage_pct, release_date, dominant_set_code, base_set_code) VALUES (` +
    `${sql(m.setId)}, ${m.carte.length}, ${m.officialCount ?? 'NULL'}, ${sql(m.nome)}, ${sql(m.serie)}, 100, NULL, ${published ? 1 : 0}, ${copertura.toFixed(4)}, ${sql(m.releaseDate)}, ${sql(code)}, ${sql(code)}) ` +
    `ON CONFLICT(id) DO UPDATE SET card_count = excluded.card_count, official_count = excluded.official_count, name = excluded.name, series = excluded.series, published = excluded.published, coverage_pct = excluded.coverage_pct, release_date = excluded.release_date, dominant_set_code = excluded.dominant_set_code, base_set_code = excluded.base_set_code;`,
  ];
  for (let i = 0; i < righe.length; i += 40) {
    linee.push(
      `INSERT INTO cards (card_id, expansion_id, card_number, nome, tipo, ps, regola_speciale, attacchi_json, image_status, image_webp, stage, illustratore) VALUES\n` +
      righe.slice(i, i + 40).join(',\n') +
      `\nON CONFLICT(card_id) DO UPDATE SET nome = excluded.nome, tipo = excluded.tipo, ps = excluded.ps, image_status = excluded.image_status, image_webp = excluded.image_webp, stage = excluded.stage, illustratore = excluded.illustratore;`
    );
  }

  const sqlFile = path.join(workerRoot, `ingest-${m.setId}-pcw.sql`);
  await writeFile(sqlFile, linee.join('\n\n'), 'utf8');
  await runWrangler(['d1', 'execute', 'pokevault-catalog', '--remote', '--file', sqlFile]);
  await rm(sqlFile, { force: true });
  await rm(tmpDir, { recursive: true, force: true });
  console.log(`\nFatto: ${m.carte.length} carte in D1, ${caricate} immagini su R2.`);
}

main().catch((e) => { console.error(e.message); process.exit(1); });
