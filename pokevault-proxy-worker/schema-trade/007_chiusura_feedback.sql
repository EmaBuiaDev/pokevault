-- TradeRadar, migrazione 007: chiusura dello scambio e feedback (fase 2c).
--
-- Su trade_proposals: quando ciascuno dei due ha segnato "Scambio fatto"
-- (done_from per chi ha proposto, done_to per l'altro). Con entrambi la
-- proposta passa a status 'done' e closed_at; con "Non si e' presentato" a
-- 'no_show'.
--
-- trade_ratings: il voto di ciascuno sull'altro, alla cieca. mood: good |
-- ok | bad; tags: lista JSON di sigle ("punctual", "late"...). Un voto si
-- vede quando hanno votato entrambi, o dopo 7 giorni.
--
-- trade_no_shows: chi ha segnalato chi. Due persone diverse in 60 giorni
-- tolgono il profilo dai match per 30 giorni (trade_profiles.suspended_until).
--
-- trade_spot_votes: dopo uno scambio chiuso in un luogo, cosa e' quel luogo
-- (tournaments | comics | card_shop). Tre persone diverse = badge.
--
-- Si applica con:
--   npx wrangler d1 execute pokevault-trade-staging --env staging --remote --file schema-trade/007_chiusura_feedback.sql

ALTER TABLE trade_proposals ADD COLUMN done_from INTEGER;
ALTER TABLE trade_proposals ADD COLUMN done_to INTEGER;
ALTER TABLE trade_proposals ADD COLUMN closed_at INTEGER;
ALTER TABLE trade_profiles ADD COLUMN suspended_until INTEGER;

CREATE TABLE IF NOT EXISTS trade_ratings (
  proposal_id  TEXT NOT NULL,
  from_uid     TEXT NOT NULL,
  to_uid       TEXT NOT NULL,
  mood         TEXT NOT NULL,
  tags         TEXT NOT NULL DEFAULT '[]',
  created_at   INTEGER NOT NULL,
  PRIMARY KEY (proposal_id, from_uid)
);
CREATE INDEX IF NOT EXISTS idx_trade_ratings_to ON trade_ratings (to_uid);

CREATE TABLE IF NOT EXISTS trade_no_shows (
  proposal_id   TEXT NOT NULL,
  reporter_uid  TEXT NOT NULL,
  target_uid    TEXT NOT NULL,
  created_at    INTEGER NOT NULL,
  PRIMARY KEY (proposal_id, reporter_uid)
);
CREATE INDEX IF NOT EXISTS idx_trade_no_shows_target ON trade_no_shows (target_uid, created_at);

CREATE TABLE IF NOT EXISTS trade_spot_votes (
  spot_id     TEXT NOT NULL,
  uid         TEXT NOT NULL,
  tag         TEXT NOT NULL,
  created_at  INTEGER NOT NULL,
  PRIMARY KEY (spot_id, uid, tag)
);

UPDATE trade_meta SET value = '7' WHERE key = 'schema_version';
