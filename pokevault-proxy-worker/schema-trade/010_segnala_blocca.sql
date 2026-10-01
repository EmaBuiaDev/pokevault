-- TradeRadar, migrazione 010: segnala e blocca (fase 2f).
--
-- trade_blocks: chi ha bloccato chi. Vale nei due sensi (nessuno dei due
-- vede l'altro nei match ne' gli puo' mandare proposte), ma lo vede solo
-- chi ha bloccato. Se ne va col profilo di chi ha bloccato, non con quello
-- del bloccato: e' una scelta di un'altra persona.
--
-- trade_reports: le segnalazioni. weight = 1 se conta per la sospensione
-- automatica (motivo grave, fra persone che hanno davvero fatto un accordo,
-- segnalatore credibile, non una ritorsione), 0 se va solo vista da noi.
-- status: open | upheld (confermata) | dismissed (archiviata). Restano anche
-- se uno dei due disattiva il profilo: servono alla sicurezza degli altri.
--
-- trade_profiles.suspension_reason: perche' e' sospeso (no_show | reports |
-- admin), per dirglielo nell'app. Un ban e' una sospensione senza fine.
--
-- trade_sanctions: la sospensione di chi disattiva il profilo mentre e'
-- sospeso; torna quando lo riattiva. Senza, disattivare e riattivare
-- cancellerebbe la sospensione.
--
-- Si applica con:
--   npx wrangler d1 execute pokevault-trade-staging --env staging --remote --file schema-trade/010_segnala_blocca.sql

CREATE TABLE IF NOT EXISTS trade_blocks (
  blocker_uid  TEXT NOT NULL,
  blocked_uid  TEXT NOT NULL,
  created_at   INTEGER NOT NULL,
  PRIMARY KEY (blocker_uid, blocked_uid)
);
CREATE INDEX IF NOT EXISTS idx_trade_blocks_blocked ON trade_blocks (blocked_uid);

CREATE TABLE IF NOT EXISTS trade_reports (
  id            TEXT PRIMARY KEY,
  reporter_uid  TEXT NOT NULL,
  target_uid    TEXT NOT NULL,
  reason        TEXT NOT NULL,
  note          TEXT NOT NULL DEFAULT '',
  proposal_id   TEXT,
  weight        INTEGER NOT NULL DEFAULT 0,
  status        TEXT NOT NULL DEFAULT 'open',
  created_at    INTEGER NOT NULL,
  reviewed_at   INTEGER
);
CREATE INDEX IF NOT EXISTS idx_trade_reports_target ON trade_reports (target_uid, created_at);
CREATE INDEX IF NOT EXISTS idx_trade_reports_reporter ON trade_reports (reporter_uid, created_at);

CREATE TABLE IF NOT EXISTS trade_sanctions (
  uid              TEXT PRIMARY KEY,
  suspended_until  INTEGER NOT NULL,
  reason           TEXT,
  created_at       INTEGER NOT NULL
);

ALTER TABLE trade_profiles ADD COLUMN suspension_reason TEXT;

UPDATE trade_meta SET value = '10' WHERE key = 'schema_version';
