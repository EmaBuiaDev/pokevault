-- PokeVault catalog D1 schema v1
--
-- Replaces the single ~10MB JSON blob (it/catalog/cards.cleaned.json,
-- downloaded whole by every client every 5 minutes) with a queryable store.
-- Field shapes below mirror the REAL catalog data as verified on 2026-09-06/07
-- (15,406 cards / 106 expansions downloaded and inspected directly from R2):
--   - `tipo` and `regolaSpeciale` are ALWAYS NULL in the current dataset --
--     kept as nullable columns for forward-compat, not relied upon anywhere.
--   - `ps` (HP) arrives as a numeric-looking STRING (e.g. "70"), not an int.
--   - `attacchi` is a small array (0-4 items) of {nome, danno, descrizione},
--     always read as a whole with its card and never queried independently --
--     stored as a JSON column rather than a normalized child table.
--   - Known encoding bug in source data: some `descrizione`/`nome` values are
--     double-encoded UTF-8 (e.g. "PokÃ©mon" instead of "Pokémon"). The import
--     script (scripts/import-catalog-to-d1.mjs, not yet written) must repair
--     this on ingest, not perpetuate it into D1.

CREATE TABLE expansions (
  id            TEXT PRIMARY KEY,        -- espansioneId, e.g. "dp1", "sv10", "me04"
  card_count    INTEGER NOT NULL DEFAULT 0,
  sort_order    INTEGER NOT NULL DEFAULT 100,
  logo_key      TEXT,                    -- R2 key for the set logo
  published     INTEGER NOT NULL DEFAULT 1,  -- 0 = below IT coverage threshold, hidden from Pokedex
  coverage_pct  REAL,                     -- last-computed IT image coverage, drives `published`
  updated_at    INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE cards (
  card_id         TEXT PRIMARY KEY,      -- e.g. "DP1_IT_1.png" -- kept as-is, it IS the R2 filename
  expansion_id    TEXT NOT NULL REFERENCES expansions(id),
  card_number     TEXT NOT NULL,         -- extracted from card_id, e.g. "1" -- for sorting/lookup without regex
  nome            TEXT NOT NULL,
  tipo            TEXT,                  -- reserved; always NULL in source data today
  ps              TEXT,                  -- HP as string, matches source shape
  regola_speciale TEXT,                  -- reserved; always NULL in source data today
  attacchi_json   TEXT NOT NULL DEFAULT '[]',  -- JSON array of {nome, danno, descrizione}
  image_status    TEXT NOT NULL DEFAULT 'ok',  -- 'ok' | 'missing' -- drives placeholder rendering
  image_webp      INTEGER NOT NULL DEFAULT 0,  -- 1 once a .webp variant has been confirmed uploaded
  updated_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX idx_cards_expansion ON cards(expansion_id);
CREATE INDEX idx_cards_nome ON cards(nome);

-- Bulk price snapshot, replacing /ita/prices.json (built by the existing
-- buildItalianPriceSnapshot() cron job -- see src/index.ts). One row per
-- card; NULLs where PokeWallet has no price for that card/variant.
CREATE TABLE card_prices (
  card_id     TEXT PRIMARY KEY REFERENCES cards(card_id),
  avg         REAL,
  low         REAL,
  trend       REAL,
  avg1        REAL,
  avg7        REAL,
  avg30       REAL,
  usd         REAL,   -- TCGPlayer USD fallback for sets without CardMarket data
  usd_low     REAL,
  url         TEXT,
  updated_at  INTEGER NOT NULL DEFAULT (unixepoch())
);

-- IT expansion -> PokeWallet set identity, for the price cron's search
-- queries. Seeds from the existing SetCodeMapper.kt logic; grows as new
-- expansions are ingested.
CREATE TABLE set_map (
  it_expansion_id  TEXT PRIMARY KEY REFERENCES expansions(id),
  pw_set_id        TEXT,
  pw_set_code      TEXT,
  pw_query_override TEXT  -- JSON array of search query strings, when name-derivation fails (see deriveSearchQueryCandidatesForSetName)
);

-- Audit trail for every ingest/price-sync run (catalog-ingest.yml,
-- prices-sync.yml). Lets the automation be observable without digging
-- through GitHub Actions logs, and is what a future dashboard would read.
CREATE TABLE sync_runs (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  kind             TEXT NOT NULL,      -- 'catalog_ingest' | 'price_sync' | 'image_recompress'
  started_at       INTEGER NOT NULL,
  finished_at      INTEGER,
  cards_processed  INTEGER NOT NULL DEFAULT 0,
  cards_failed     INTEGER NOT NULL DEFAULT 0,
  pokewallet_requests INTEGER NOT NULL DEFAULT 0,  -- tracks the 1000/day budget, see api_budget
  notes            TEXT
);

-- Compliance kill-switch (plan sez. 4.2): setting a row here must make the
-- Worker stop serving that set/card within minutes, without a redeploy.
CREATE TABLE takedowns (
  target_type  TEXT NOT NULL CHECK (target_type IN ('expansion', 'card')),
  target_id    TEXT NOT NULL,          -- expansion_id or card_id
  reason       TEXT,
  created_at   INTEGER NOT NULL DEFAULT (unixepoch()),
  PRIMARY KEY (target_type, target_id)
);

-- PokeWallet daily request budget guard (plan sez. 2.3: 1000 req/day free
-- tier). One row per UTC day; the Worker/cron increments `request_count`
-- and refuses further upstream calls past the circuit-breaker threshold.
CREATE TABLE api_budget (
  day            TEXT PRIMARY KEY,     -- 'YYYY-MM-DD' UTC
  request_count  INTEGER NOT NULL DEFAULT 0,
  updated_at     INTEGER NOT NULL DEFAULT (unixepoch())
);

-- Single-row table for the catalog version counter clients use for delta
-- sync (replaces full-refresh in SetsSyncWorker.kt).
CREATE TABLE catalog_meta (
  id               INTEGER PRIMARY KEY CHECK (id = 1),
  catalog_version  INTEGER NOT NULL DEFAULT 1,
  updated_at       INTEGER NOT NULL DEFAULT (unixepoch())
);
INSERT INTO catalog_meta (id, catalog_version) VALUES (1, 1);
