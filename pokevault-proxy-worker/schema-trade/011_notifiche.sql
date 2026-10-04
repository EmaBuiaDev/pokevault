-- TradeRadar, migrazione 011: notifiche push (fase 3).
--
-- trade_push_tokens: i telefoni a cui mandare le notifiche (token FCM), con
-- la lingua dell'app per scrivere il testo. Un token appartiene a un solo
-- utente: se sullo stesso telefono entra un altro account, passa a lui.
--
-- trade_profiles.notify_prefs: le cinque categorie accese o spente, in JSON
-- (proposals, meetings, reminders, after, wants). Mancante = default: le
-- prime quattro accese; "wants" (carte che cerchi vicino a te) non e' mai
-- stata chiesta finche' non c'e'.
--
-- trade_notifications: la coda di uscita. id e' la chiave che impedisce i
-- doppioni (es. "reminder:<proposta>:<giorno>T<ora>"): la stessa notifica
-- non parte mai due volte. send_after rimanda quelle nate di notte (22-8).
-- sent_at NULL = da mandare; status: sent | no_token | failed.
--
-- trade_wants_seen: quali carte cercate sono gia' state segnalate, e da chi
-- le offre, per non ripetere ogni giorno le stesse.
--
-- Si applica con:
--   npx wrangler d1 execute pokevault-trade-staging --env staging --remote --file schema-trade/011_notifiche.sql

CREATE TABLE IF NOT EXISTS trade_push_tokens (
  token       TEXT PRIMARY KEY,
  uid         TEXT NOT NULL,
  lang        TEXT NOT NULL DEFAULT 'it',
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_trade_push_tokens_uid ON trade_push_tokens (uid);

ALTER TABLE trade_profiles ADD COLUMN notify_prefs TEXT;

CREATE TABLE IF NOT EXISTS trade_notifications (
  id          TEXT PRIMARY KEY,
  uid         TEXT NOT NULL,
  kind        TEXT NOT NULL,
  payload     TEXT NOT NULL,
  created_at  INTEGER NOT NULL,
  send_after  INTEGER NOT NULL,
  sent_at     INTEGER,
  status      TEXT,
  attempts    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_trade_notifications_pending ON trade_notifications (sent_at, send_after);
CREATE INDEX IF NOT EXISTS idx_trade_notifications_uid ON trade_notifications (uid, kind, created_at);

CREATE TABLE IF NOT EXISTS trade_wants_seen (
  uid         TEXT NOT NULL,
  card_key    TEXT NOT NULL,
  holder_uid  TEXT NOT NULL,
  seen_at     INTEGER NOT NULL,
  PRIMARY KEY (uid, card_key, holder_uid)
);

UPDATE trade_meta SET value = '11' WHERE key = 'schema_version';
