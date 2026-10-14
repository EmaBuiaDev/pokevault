-- Catalogo INGLESE (piano carte EN, Fase 2, 13/10/2026).
--
-- Vive in un database D1 a parte, `pokevault-catalog-en` (binding
-- `catalog_en`): il catalogo italiano (`pokevault-catalog`) non viene mai
-- scritto per l'inglese, nemmeno per sbaglio. Le tabelle hanno gli stessi
-- nomi e le stesse colonne delle italiane, cosi' gli stessi SQL e lo stesso
-- contratto JSON (mapCardRow) valgono per tutte e due le lingue.
--
--   npx wrangler d1 execute pokevault-catalog-en --remote --file schema-en/001_init.sql

CREATE TABLE IF NOT EXISTS expansions (
  id                TEXT PRIMARY KEY,      -- per i gemelli e' l'id italiano ("sv08"), per i solo EN l'id TCGdex ("pl3")
  name              TEXT,                  -- nome inglese ("Surging Sparks")
  card_count        INTEGER NOT NULL DEFAULT 0,
  official_count    INTEGER,               -- il numero stampato dopo la barra, NULL per i promo
  sort_order        INTEGER NOT NULL DEFAULT 100,
  logo_key          TEXT,
  published         INTEGER NOT NULL DEFAULT 0,  -- 1 solo con almeno l'80% delle immagini
  coverage_pct      REAL,
  release_date      TEXT,
  base_set_code     TEXT,                  -- la cartella R2: en/<base_set_code>/...
  upstream_set_code TEXT,
  series            TEXT,
  abbreviation      TEXT,                  -- sigla ufficiale ("SSP")
  it_expansion_id   TEXT,                  -- il gemello italiano; NULL per i set solo EN
  tcgdex_id         TEXT NOT NULL,         -- l'id su TCGdex ("sv08", "pl3"), per i rilanci
  updated_at        INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE IF NOT EXISTS cards (
  card_id         TEXT PRIMARY KEY,        -- "<CARTELLA>_EN_<numero>.webp": e' il nome del file su R2
  expansion_id    TEXT NOT NULL REFERENCES expansions(id),
  card_number     TEXT NOT NULL,           -- senza zeri davanti; i promo a lettere restano come sono
  nome            TEXT NOT NULL,
  tipo            TEXT,
  ps              TEXT,
  regola_speciale TEXT,
  attacchi_json   TEXT NOT NULL DEFAULT '[]',
  rarity          TEXT,
  stage           TEXT,
  illustratore    TEXT,
  image_status    TEXT NOT NULL DEFAULT 'missing',  -- 'ok' solo dopo l'upload riuscito su R2
  image_source    TEXT,                    -- 'tcgdex' | 'pokemontcg' | ...
  updated_at      INTEGER NOT NULL DEFAULT (unixepoch())
);
CREATE INDEX IF NOT EXISTS idx_cards_expansion ON cards(expansion_id);
CREATE INDEX IF NOT EXISTS idx_cards_illustratore ON cards(illustratore);

CREATE TABLE IF NOT EXISTS card_variants (
  card_id     TEXT NOT NULL,
  variant     TEXT NOT NULL,
  kind        TEXT NOT NULL,
  type        TEXT NOT NULL,
  stamp       TEXT,
  foil        TEXT,
  id_product  INTEGER,
  source      TEXT NOT NULL,
  PRIMARY KEY (card_id, variant)
);

CREATE TABLE IF NOT EXISTS card_cardmarket (
  card_id     TEXT PRIMARY KEY,
  id_product  INTEGER NOT NULL,
  source      TEXT NOT NULL,
  updated_at  INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE IF NOT EXISTS takedowns (
  target_type  TEXT NOT NULL CHECK (target_type IN ('expansion', 'card')),
  target_id    TEXT NOT NULL,
  reason       TEXT,
  created_at   INTEGER NOT NULL DEFAULT (unixepoch()),
  PRIMARY KEY (target_type, target_id)
);
