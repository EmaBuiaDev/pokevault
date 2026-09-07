#!/usr/bin/env node
// Generates a SQL file to bulk-load the existing catalog JSON blob
// (it/catalog/cards.cleaned.json, 15,406 cards / 106 expansions as of
// 2026-09-07) into the new D1 database (schema/001_init.sql).
//
// This is a ONE-TIME migration script: after this, the catalog lives in D1
// and the JSON blob becomes a legacy artifact (kept for rollback safety,
// not the source of truth).
//
// Usage:
//   node scripts/import-catalog-to-d1.mjs --catalog catalog-live.json --out import.sql
//   npx wrangler d1 execute pokevault-catalog --remote --file import.sql

import { readFile, writeFile } from 'node:fs/promises';

function parseArgs(argv) {
  const args = { catalog: null, out: 'import.sql', batchSize: 300 };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--catalog') args.catalog = argv[++i];
    else if (argv[i] === '--out') args.out = argv[++i];
    else if (argv[i] === '--batch-size') args.batchSize = parseInt(argv[++i], 10);
  }
  if (!args.catalog) throw new Error('Usage: --catalog <path> [--out <path>] [--batch-size N]');
  return args;
}

// Repairs the double-encoding bug present in the source data (UTF-8 bytes
// mis-decoded as Latin-1, e.g. "PokÃ©mon" instead of "Pokémon"). Only
// applied when the tell-tale "Ã" + continuation-byte pattern is present, and
// only kept if round-tripping actually produces valid, sane text -- never
// applied blindly, since not every "Ã" is guaranteed mojibake.
// CP1252 code points that occupy 0x80-0x9F (the block where CP1252 and
// ISO-8859-1/Latin-1 actually differ -- 0xA0-0xFF are identical in both).
// The source data was originally UTF-8, mis-decoded as CP1252 (not plain
// Latin-1: the mojibake shows visible glyphs like "\u20AC"/"\u2122" for byte
// 0x80/0x99, which only happens under CP1252 -- true Latin-1 has unprintable
// C1 control codes there). Reversing via Buffer.from(str, 'latin1') alone
// mishandles exactly this block, since Node's 'latin1' encoding truncates
// each UTF-16 code unit to its low byte rather than reversing the CP1252
// mapping -- so e.g. U+20AC (4 hex digits) would wrongly become 0xAC, not 0x80.
const CP1252_HIGH = new Map([
  [0x20AC, 0x80], [0x201A, 0x82], [0x0192, 0x83], [0x201E, 0x84], [0x2026, 0x85],
  [0x2020, 0x86], [0x2021, 0x87], [0x02C6, 0x88], [0x2030, 0x89], [0x0160, 0x8A],
  [0x2039, 0x8B], [0x0152, 0x8C], [0x017D, 0x8E], [0x2018, 0x91], [0x2019, 0x92],
  [0x201C, 0x93], [0x201D, 0x94], [0x2022, 0x95], [0x2013, 0x96], [0x2014, 0x97],
  [0x02DC, 0x98], [0x2122, 0x99], [0x0161, 0x9A], [0x203A, 0x9B], [0x0153, 0x9C],
  [0x017E, 0x9E], [0x0178, 0x9F],
]);

// Matches a lead byte (re-decoded as CP1252, U+00C2-U+00F4) followed by one
// or more continuation-byte characters -- either the plain Latin-1 range
// (U+0080-U+00BF) or one of the CP1252-specific glyphs above.
const CONTINUATION_CHARS = [...CP1252_HIGH.keys()].map((cp) => String.fromCodePoint(cp)).join('');
const MOJIBAKE_RUN_RE = new RegExp(`[\u00C2-\u00F4][\u0080-\u00BF${CONTINUATION_CHARS}]+`, 'g');

function toOriginalByte(codePoint) {
  if (CP1252_HIGH.has(codePoint)) return CP1252_HIGH.get(codePoint);
  return codePoint & 0xff; // 0x00-0x7F and 0xA0-0xFF are identical to Latin-1
}

function repairMojibake(str) {
  if (typeof str !== 'string') return str;
  // Repairs only runs that look like mis-decoded UTF-8, not the whole
  // string: many descriptions mix already-correct characters with mojibake
  // runs in the same text (inconsistent source curation), so a whole-string
  // reinterpretation would mangle the parts that were already fine.
  return str.replace(MOJIBAKE_RUN_RE, (run) => {
    try {
      const bytes = Buffer.from([...run].map((ch) => toOriginalByte(ch.codePointAt(0))));
      const repaired = bytes.toString('utf8');
      return repaired.indexOf('\uFFFD') === -1 ? repaired : run;
    } catch {
      return run;
    }
  });
}

function sqlString(value) {
  if (value === null || value === undefined) return 'NULL';
  return `'${String(value).replace(/'/g, "''")}'`;
}

const CARD_ID_RE = /^([A-Za-z0-9]+)_IT_([A-Za-z0-9_]+)\.(png|webp|jpe?g)$/i;

function cardNumberFromId(cardId) {
  const match = CARD_ID_RE.exec(cardId.trim());
  return match ? match[2] : null;
}

function setCodeFromId(cardId) {
  const match = CARD_ID_RE.exec(cardId.trim());
  return match ? match[1].toUpperCase() : null;
}

// Mirrors the Kotlin fallback in PokeTcgRepository.mergeItalianSets(): the
// set code that appears on the most cards of the expansion, via cardId's
// {SET}_IT_{number} prefix. Kept in sync deliberately -- see schema/003.
function dominantSetCode(setCodes) {
  const counts = new Map();
  for (const code of setCodes) {
    if (!code) continue;
    counts.set(code, (counts.get(code) ?? 0) + 1);
  }
  let best = null;
  let bestCount = 0;
  for (const [code, count] of counts) {
    if (count > bestCount) { best = code; bestCount = count; }
  }
  return best;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const raw = (await readFile(args.catalog, 'utf8')).replace(/^﻿/, '');
  const parsed = JSON.parse(raw);
  const cards = Array.isArray(parsed) ? parsed : parsed.cards;

  const expansionCounts = new Map();
  const expansionSetCodes = new Map(); // expansionId -> [rawSetCode, ...] (one per card)
  const lines = [];

  lines.push('-- Generated by scripts/import-catalog-to-d1.mjs -- one-time catalog migration.');
  // Note: D1 rejects explicit BEGIN TRANSACTION/COMMIT in an executed SQL
  // file (it manages atomicity itself) -- deliberately omitted here.

  // Cards reference expansions via FOREIGN KEY -- compute both from one pass
  // over the data, but expansions rows must be EMITTED first in the SQL file.
  const cardRows = [];
  let skipped = 0;
  for (const c of cards) {
    if (!c.cardId || !c.espansioneId || !c.nome) { skipped += 1; continue; }
    const expansionId = c.espansioneId.toLowerCase();
    expansionCounts.set(expansionId, (expansionCounts.get(expansionId) ?? 0) + 1);
    if (!expansionSetCodes.has(expansionId)) expansionSetCodes.set(expansionId, []);
    expansionSetCodes.get(expansionId).push(setCodeFromId(c.cardId));

    const cardNumber = cardNumberFromId(c.cardId) ?? '';
    const nome = repairMojibake(c.nome);
    const attacchi = (c.attacchi ?? []).map((a) => ({
      nome: repairMojibake(a.nome ?? ''),
      danno: repairMojibake(a.danno ?? ''), // e.g. "30Ã—" -> "30×" (multiplier attacks)
      descrizione: repairMojibake(a.descrizione ?? ''),
    }));

    cardRows.push(
      `(${sqlString(c.cardId)}, ${sqlString(expansionId)}, ${sqlString(cardNumber)}, ` +
      `${sqlString(nome)}, ${sqlString(c.tipo)}, ${sqlString(c.ps)}, ${sqlString(c.regolaSpeciale)}, ` +
      `${sqlString(JSON.stringify(attacchi))})`
    );
  }

  // Expansions FIRST (cards.expansion_id references them via FOREIGN KEY).
  // card_count computed from the data itself (no separate manifest was found
  // in the current R2 payload). sort_order defaults to 100 for everything --
  // proper chronological ordering is a follow-up, not a blocker for the
  // structural D1 migration.
  const expansionRows = [...expansionCounts.entries()].map(
    ([id, count]) => `(${sqlString(id)}, ${count}, 100, NULL, 1, ${sqlString(dominantSetCode(expansionSetCodes.get(id) ?? []))})`
  );
  for (let i = 0; i < expansionRows.length; i += args.batchSize) {
    const chunk = expansionRows.slice(i, i + args.batchSize);
    lines.push(
      'INSERT INTO expansions (id, card_count, sort_order, logo_key, published, dominant_set_code) VALUES\n' +
      chunk.join(',\n') +
      '\nON CONFLICT(id) DO UPDATE SET card_count = excluded.card_count, dominant_set_code = excluded.dominant_set_code;'
    );
  }

  for (let i = 0; i < cardRows.length; i += args.batchSize) {
    const chunk = cardRows.slice(i, i + args.batchSize);
    lines.push(
      'INSERT INTO cards (card_id, expansion_id, card_number, nome, tipo, ps, regola_speciale, attacchi_json) VALUES\n' +
      chunk.join(',\n') +
      '\nON CONFLICT(card_id) DO UPDATE SET expansion_id = excluded.expansion_id, card_number = excluded.card_number, ' +
      'nome = excluded.nome, tipo = excluded.tipo, ps = excluded.ps, regola_speciale = excluded.regola_speciale, ' +
      'attacchi_json = excluded.attacchi_json;'
    );
  }


  await writeFile(args.out, lines.join('\n\n'), 'utf8');
  console.log(`Carte importate: ${cardRows.length} (saltate per campi mancanti: ${skipped})`);
  console.log(`Espansioni: ${expansionRows.length}`);
  console.log(`File generato: ${args.out}`);
}

main().catch((err) => {
  console.error('Errore fatale:', err);
  process.exit(1);
});
