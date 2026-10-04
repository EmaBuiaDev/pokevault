-- TradeRadar, migrazione 004: le proposte di scambio (fase 2a).
--
-- public_id: un id casuale per profilo, l'unico con cui l'app indica un'altra
-- persona (a chi mandare una proposta, di chi vedere le carte). L'uid
-- Firebase non esce mai dal server.
--
-- Una proposta ha uno stato e una revisione. Ogni controproposta e' una
-- revisione nuova con le sue carte: lo storico resta. turn_uid dice chi deve
-- rispondere finche' la proposta e' aperta. Stati:
--   open      in attesa di turn_uid (revision > 1 = controproposta)
--   accepted  accettata: da qui partono appuntamento e chiusura (2b, 2c)
--   declined  rifiutata da chi doveva rispondere
--   cancelled ritirata da chi l'aveva mandata, o annullata dopo l'accordo
--
-- Si applica con:
--   npx wrangler d1 execute pokevault-trade-staging --env staging --remote --file schema-trade/004_proposte.sql

ALTER TABLE trade_profiles ADD COLUMN public_id TEXT;
UPDATE trade_profiles SET public_id = lower(hex(randomblob(8))) WHERE public_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_trade_profiles_public ON trade_profiles (public_id);

CREATE TABLE IF NOT EXISTS trade_proposals (
  id          TEXT PRIMARY KEY,
  from_uid    TEXT NOT NULL,
  to_uid      TEXT NOT NULL,
  status      TEXT NOT NULL,
  revision    INTEGER NOT NULL DEFAULT 1,
  turn_uid    TEXT NOT NULL,
  closed_by   TEXT,
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_trade_proposals_from ON trade_proposals (from_uid, updated_at);
CREATE INDEX IF NOT EXISTS idx_trade_proposals_to ON trade_proposals (to_uid, updated_at);

-- Le carte di ogni revisione. giver_uid e' chi la da': per chi guarda, le
-- sue sono "dai", le altre "ricevi".
CREATE TABLE IF NOT EXISTS trade_proposal_items (
  proposal_id TEXT NOT NULL,
  revision    INTEGER NOT NULL,
  giver_uid   TEXT NOT NULL,
  card_key    TEXT NOT NULL,
  variant     TEXT NOT NULL DEFAULT '',
  condition   TEXT NOT NULL DEFAULT '',
  language    TEXT NOT NULL DEFAULT '',
  qty         INTEGER NOT NULL,
  PRIMARY KEY (proposal_id, revision, giver_uid, card_key, variant, condition, language)
);

UPDATE trade_meta SET value = '4' WHERE key = 'schema_version';
