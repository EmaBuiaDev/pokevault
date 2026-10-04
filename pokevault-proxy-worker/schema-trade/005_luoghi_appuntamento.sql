-- TradeRadar, migrazione 005: luoghi e appuntamento (fase 2b).
--
-- trade_spots: i luoghi dove incontrarsi. Arrivano da OpenStreetMap
-- (source 'osm', id 'osm:n123' / 'osm:w123'), scaricati per zona e tenuti qui;
-- o li segnala un utente (source 'user', approved = 0 finche' non li
-- approviamo: li vede solo chi li ha segnalati). kind: card_shop, comics,
-- games, toys, video_games, mall, library, other.
--
-- trade_spot_cells: le celle geohash gia' scaricate da OpenStreetMap e
-- quando, per non rifare la stessa richiesta (si riscarica dopo 30 giorni).
--
-- Su trade_proposals l'appuntamento: uno dei due propone un luogo e fino a
-- tre fasce, l'altro ne sceglie una. meet_status none | proposed | confirmed;
-- con confirmed la proposta passa a status 'scheduled'.
--
-- Si applica con:
--   npx wrangler d1 execute pokevault-trade-staging --env staging --remote --file schema-trade/005_luoghi_appuntamento.sql

CREATE TABLE IF NOT EXISTS trade_spots (
  id             TEXT PRIMARY KEY,
  name           TEXT NOT NULL,
  kind           TEXT NOT NULL,
  lat            REAL NOT NULL,
  lon            REAL NOT NULL,
  geohash5       TEXT NOT NULL,
  city           TEXT,
  opening_hours  TEXT,
  source         TEXT NOT NULL,
  approved       INTEGER NOT NULL DEFAULT 1,
  added_by       TEXT,
  created_at     INTEGER NOT NULL,
  updated_at     INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_trade_spots_cell ON trade_spots (geohash5);
CREATE INDEX IF NOT EXISTS idx_trade_spots_latlon ON trade_spots (lat, lon);

CREATE TABLE IF NOT EXISTS trade_spot_cells (
  geohash5    TEXT PRIMARY KEY,
  fetched_at  INTEGER NOT NULL,
  found       INTEGER NOT NULL DEFAULT 0
);

ALTER TABLE trade_proposals ADD COLUMN meet_status TEXT NOT NULL DEFAULT 'none';
ALTER TABLE trade_proposals ADD COLUMN meet_by TEXT;
ALTER TABLE trade_proposals ADD COLUMN meet_spot_id TEXT;
ALTER TABLE trade_proposals ADD COLUMN meet_slots TEXT;
ALTER TABLE trade_proposals ADD COLUMN meet_slot TEXT;

UPDATE trade_meta SET value = '5' WHERE key = 'schema_version';
