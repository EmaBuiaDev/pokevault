-- Adds expansions.upstream_set_code: the ENGLISH trading abbreviation that
-- PokeWallet indexes a set by (me05 "Buio Pesto" -> "PBL"), which is NOT the
-- same thing as base_set_code/dominant_set_code (schema/003). Those hold the
-- raw prefix of our own Italian card_id ("ME05"), derived from the TCGdex set
-- id -- fine for linking images and metadata, useless for looking the set up
-- upstream, where it is called PBL.
--
-- Why this column exists: the price snapshot resolved that mapping from two
-- hand-written tables in the Worker (ITA_EXPANSION_UPSTREAM_SET_IDS and
-- ITA_EXPANSION_BASE_SET). Every newly ingested set therefore had NO prices
-- until somebody edited src/index.ts by hand -- the automation could publish a
-- set in the Pokedex but never price it. Holding the code as data lets
-- scripts/ingest-tcgdex-set.mjs fill it in at ingest time (TCGdex exposes it as
-- `abbreviation.official`) and closes that manual step.
--
-- Verified on 2026-09-11 before writing this, against the live PokeWallet
-- /sets directory (859 sets): TCGdex's abbreviation resolves to exactly ONE
-- English upstream set for 18/18 of the sets we already map by hand, with zero
-- collisions, and reproduces every set_id in ITA_EXPANSION_UPSTREAM_SET_IDS.
-- The three sets where it finds nothing (swsh1/SSH, sm1/SUM, cel25/CEL) are
-- pre-2022 sets PokeWallet files under its own codes; they keep working
-- through the existing hand-written tables, which stay as the fallback.

ALTER TABLE expansions ADD COLUMN upstream_set_code TEXT;

-- Seed: the codes already proven in production today, plus me05 (the set that
-- exposed the gap). ME03 is seeded as POR, its real upstream code -- the Worker
-- hand-map says "ME03", which only ever worked because an explicit set_id
-- override bypassed it.
UPDATE expansions SET upstream_set_code = 'MEG' WHERE id = 'me01';
UPDATE expansions SET upstream_set_code = 'PFL' WHERE id = 'me02';
UPDATE expansions SET upstream_set_code = 'POR' WHERE id = 'me03';
UPDATE expansions SET upstream_set_code = 'CRI' WHERE id = 'me04';
UPDATE expansions SET upstream_set_code = 'PBL' WHERE id = 'me05';
UPDATE expansions SET upstream_set_code = 'ASC' WHERE id = 'me2pt5';
UPDATE expansions SET upstream_set_code = 'MEP' WHERE id = 'mep';
UPDATE expansions SET upstream_set_code = 'SVI' WHERE id = 'sv01';
UPDATE expansions SET upstream_set_code = 'PAL' WHERE id = 'sv02';
UPDATE expansions SET upstream_set_code = 'OBF' WHERE id = 'sv03';
UPDATE expansions SET upstream_set_code = 'PAR' WHERE id = 'sv04';
UPDATE expansions SET upstream_set_code = 'TEF' WHERE id = 'sv05';
UPDATE expansions SET upstream_set_code = 'TWM' WHERE id = 'sv06';
UPDATE expansions SET upstream_set_code = 'SCR' WHERE id = 'sv07';
UPDATE expansions SET upstream_set_code = 'SSP' WHERE id = 'sv08';
UPDATE expansions SET upstream_set_code = 'JTG' WHERE id = 'sv09';
UPDATE expansions SET upstream_set_code = 'DRI' WHERE id = 'sv10';
UPDATE expansions SET upstream_set_code = 'MEW' WHERE id = 'sv3pt5';
UPDATE expansions SET upstream_set_code = 'PAF' WHERE id = 'sv4pt5';
UPDATE expansions SET upstream_set_code = 'SFA' WHERE id = 'sv6pt5';
UPDATE expansions SET upstream_set_code = 'PRE' WHERE id = 'sv8pt5';
UPDATE expansions SET upstream_set_code = 'SVP' WHERE id = 'svp';
UPDATE expansions SET upstream_set_code = 'BLK' WHERE id = 'zsv10pt5';
UPDATE expansions SET upstream_set_code = 'WHT' WHERE id = 'rsv10pt5';
