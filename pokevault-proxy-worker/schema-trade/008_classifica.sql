-- TradeRadar, migrazione 008: classifica e livelli (fase 2e).
--
-- trade_profiles.leaderboard_opt_in esiste dallo schema 2 (NOT NULL DEFAULT 0:
-- 0 = no, 1 = si'), quindi da solo non distingue "no" da "mai chiesto".
-- leaderboard_asked_at lo fa: NULL = mai chiesto (l'app chiede quando si
-- entra in classifica), altrimenti quando si e' risposto. In classifica si
-- compare solo con il si'; il livello invece si vede per tutti.
--
-- trade_leaderboard: i numeri di fiducia di ognuno, ricalcolati al bisogno
-- (in staging al massimo ogni 10 minuti; al lancio, una volta al giorno).
--   trades     scambi chiusi validi: la stessa coppia conta una volta ogni 30 giorni
--   partners   persone diverse con cui si e' scambiato
--   good/ok/bad voti visibili ricevuti, con la stessa regola della coppia
--   score      limite inferiore di Wilson (95%) sulla quota di 😊 fra 😊 e 😞
--   tier       bronze | silver | gold | platinum, o NULL
--   eligible   3 scambi con 3 persone diverse, profilo non sospeso
--
-- Si applica con:
--   npx wrangler d1 execute pokevault-trade-staging --env staging --remote --file schema-trade/008_classifica.sql
-- (Sullo staging, il 01/10, la prima versione di questo file aggiungeva di
-- nuovo leaderboard_opt_in e si fermava li': il resto e' stato applicato a mano.)

ALTER TABLE trade_profiles ADD COLUMN leaderboard_asked_at INTEGER;

CREATE TABLE IF NOT EXISTS trade_leaderboard (
  uid          TEXT PRIMARY KEY,
  trades       INTEGER NOT NULL,
  partners     INTEGER NOT NULL,
  good         INTEGER NOT NULL,
  ok           INTEGER NOT NULL,
  bad          INTEGER NOT NULL,
  score        REAL NOT NULL,
  tier         TEXT,
  eligible     INTEGER NOT NULL,
  computed_at  INTEGER NOT NULL
);

UPDATE trade_meta SET value = '8' WHERE key = 'schema_version';
