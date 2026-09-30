-- TradeRadar, migrazione 002: le fondamenta della fase 1.
--
-- La carta si identifica con card_key = "<codice set>:<numero>", minuscolo
-- il codice (me02:1, bwp:BW01, 30th-c:12): e' il nome del file del catalogo
-- (ME02_IT_1.png) senza "_IT_" ne' estensione, e l'app lo ricava dall'id
-- "ita:me02:1" che scrive in apiCardId. set_code e' la parte prima dei due
-- punti, tenuta a parte per non doverla ricavare in ogni query.
--
-- Sul server non arriva mai una coordinata: solo la cella geohash di 5
-- caratteri (~5 km), calcolata sul telefono.

CREATE TABLE IF NOT EXISTS trade_profiles (
  uid                   TEXT PRIMARY KEY,
  nickname              TEXT NOT NULL,
  geohash5              TEXT NOT NULL,
  adult_confirmed_at    INTEGER NOT NULL,
  collection_consent_at INTEGER NOT NULL,
  paused                INTEGER NOT NULL DEFAULT 0,
  owned_hash            TEXT,
  -- Pronti per la fase 2 (feedback e classifica), vuoti fino ad allora.
  rep_up                INTEGER NOT NULL DEFAULT 0,
  rep_down              INTEGER NOT NULL DEFAULT 0,
  trades_done           INTEGER NOT NULL DEFAULT 0,
  leaderboard_opt_in    INTEGER NOT NULL DEFAULT 0,
  created_at            INTEGER NOT NULL,
  updated_at            INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_trade_profiles_geohash ON trade_profiles (geohash5);

-- Le copie che l'utente ha acceso per lo scambio. Una riga per stampa e
-- condizione: due doppioni della stessa carta, uno NM e uno Played, non sono
-- la stessa offerta.
CREATE TABLE IF NOT EXISTS trade_haves (
  uid        TEXT NOT NULL,
  card_key   TEXT NOT NULL,
  set_code   TEXT NOT NULL,
  variant    TEXT NOT NULL DEFAULT '',
  condition  TEXT NOT NULL DEFAULT '',
  language   TEXT NOT NULL DEFAULT '',
  qty        INTEGER NOT NULL,
  PRIMARY KEY (uid, card_key, variant, condition, language)
);
CREATE INDEX IF NOT EXISTS idx_trade_haves_card ON trade_haves (card_key);

-- Le carte cercate esplicitamente: wishlist e album obiettivo. I set quasi
-- completi non stanno qui: li ricava il server da trade_owned e dal catalogo.
CREATE TABLE IF NOT EXISTS trade_wants (
  uid       TEXT NOT NULL,
  card_key  TEXT NOT NULL,
  source    TEXT NOT NULL,                 -- 'wishlist' | 'album'
  priority  TEXT NOT NULL DEFAULT 'nice',  -- 'need' | 'nice'
  PRIMARY KEY (uid, card_key)
);
CREATE INDEX IF NOT EXISTS idx_trade_wants_card ON trade_wants (card_key);

-- Tutte le carte possedute (consenso esplicito all'attivazione): servono ai
-- livelli "utile" e "possibile" e ai set quasi completi.
CREATE TABLE IF NOT EXISTS trade_owned (
  uid       TEXT NOT NULL,
  card_key  TEXT NOT NULL,
  set_code  TEXT NOT NULL,
  PRIMARY KEY (uid, card_key)
);
CREATE INDEX IF NOT EXISTS idx_trade_owned_set ON trade_owned (uid, set_code);

UPDATE trade_meta SET value = '2' WHERE key = 'schema_version';
