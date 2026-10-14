#!/usr/bin/env node
// Loghi dei set EN che TCGdex non ha (14/10/2026): en/<FOLDER>/logo.webp su R2.
//
// FOLDER e' quello che chiede l'app: base_set_code (o id) in maiuscolo, rotta
// /sets/<FOLDER>/image?source=en. Le fonti, guardate una per una:
// - gemelli: il logo italiano, che e' gia' in inglese (rotta ?source=ita col
//   codice che usa l'app: sv01 -> SVI, sv05 -> TEF);
// - 30TH-C: l'italiano dice "ANNIVERSARIO", quindi il logo 30 di Bulbagarden;
// - Trainer Kit senza gemello: il logo della serie (DP1, HGSS1, BW1, XY1),
//   come fa l'italiano per i kit XY e SM;
// - Bulbagarden Archives: My First Battle, McDonald's 2023 (Match Battle) e
//   2024 (Dragon Discovery); scarica con curl, come fill-images-bulbagarden;
// - exu: il logo di Unseen Forces (EX10); ex5pt5 e miscp: la stella PROMO.
//
// Carica solo i set che oggi non hanno logo, a meno di --tutti.
//
//   node scripts/fill-set-logos-en.mjs            (a secco: cosa caricherebbe)
//   node scripts/fill-set-logos-en.mjs --apply
import { execFile } from 'node:child_process';
import { mkdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { promisify } from 'node:util';
import sharp from 'sharp';
import { runWrangler, workerRoot } from './lib/cardmarket.mjs';

const BASE = 'https://pokevault-proxy.pokevault-emanu.workers.dev';
const BULBA = 'https://archives.bulbagarden.net/media/upload';
const UA = 'PokeVault-catalog/1.0 (catalogo carte; GitHub EmaBuiaDev/pokevault)';
const APPLY = process.argv.includes('--apply');
const ALL = process.argv.includes('--tutti');

const it = (code) => `${BASE}/sets/${code}/image?source=ita`;
const en = (code) => `${BASE}/sets/${code}/image?source=en`;
const SOURCES = {
  SV01: it('SVI'), SV05: it('TEF'), MEE: it('MEE'), MEP: it('MEP'), XYA: it('XYA'),
  'TK-XY-SY': it('TK-XY-SY'), 'TK-XY-B': it('TK-XY-B'), 'TK-SM-L': it('TK-SM-L'), 'TK-SM-R': it('TK-SM-R'),
  '30TH-C': `${BULBA}/6/6c/30th_Anniversary_logo.png`,
  MFB: `${BULBA}/1/1d/My_First_Battle_logo.png`,
  '2023SV': `${BULBA}/6/6a/Match_Battle_logo.png`,
  '2024SV': `${BULBA}/9/95/M24_Logo_EN.png`,
  EXU: en('EX10'),
  'TK-DP-L': en('DP1'), 'TK-DP-M': en('DP1'),
  'TK-HS-R': en('HGSS1'), 'TK-HS-G': en('HGSS1'),
  'TK-BW-E': en('BW1'), 'TK-BW-Z': en('BW1'),
  'TK-XY-N': en('XY1'), 'TK-XY-W': en('XY1'), 'TK-XY-LATIA': en('XY1'), 'TK-XY-LATIO': en('XY1'), 'TK-XY-SU': en('XY1'), 'TK-XY-P': en('XY1'),
  EX5PT5: en('DPP'), MISCP: en('DPP'),
};

const execFileAsync = promisify(execFile);
async function download(url) {
  // curl e non fetch: Bulbagarden risponde 403 al fetch di Node.
  const { stdout } = await execFileAsync('curl', ['-s', '-f', '-L', '--max-time', '60', '-A', UA, url], { encoding: 'buffer', maxBuffer: 20 * 1024 * 1024 });
  return stdout;
}

async function hasLogo(folder) {
  const res = await fetch(en(folder));
  await res.arrayBuffer();
  return res.status === 200;
}

const tmpDir = path.join(workerRoot, 'scripts', '.logos-en-tmp');
await mkdir(tmpDir, { recursive: true });
let done = 0;
for (const [folder, url] of Object.entries(SOURCES)) {
  if (!ALL && await hasLogo(folder)) continue;
  const webp = await sharp(await download(url)).trim({ threshold: 1 })
    .resize({ width: 640, height: 240, fit: 'inside', withoutEnlargement: true })
    .webp({ quality: 90, alphaQuality: 100 }).toBuffer();
  const { width, height } = await sharp(webp).metadata();
  console.log(`${folder.padEnd(12)} ${width}x${height} <- ${url}`);
  if (!APPLY) continue;
  const file = path.join(tmpDir, `${folder}.webp`);
  await writeFile(file, webp);
  await runWrangler(['r2', 'object', 'put', `pokevault-images/en/${folder}/logo.webp`, '--file', `"${file}"`, '--remote', '--content-type', 'image/webp']);
  done += 1;
}
await rm(tmpDir, { recursive: true, force: true });
console.log(APPLY ? `Caricati ${done}.` : 'A SECCO: niente caricato. Rilancia con --apply.');
