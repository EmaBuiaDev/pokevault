-- TradeRadar, migrazione 009: avatar Pokemon (sul podio della classifica).
--
-- avatar: il numero di Pokedex scelto (1-1025), o NULL per l'iniziale.
-- avatar_animated: 1 se si mostra lo sprite animato (Premium, fino al 649).
-- Gratis una trentina di Pokemon, Premium tutti: lo decide l'app, che sa
-- se l'utente e' Premium (al lancio andra' controllato anche qui).
--
-- Si applica con:
--   npx wrangler d1 execute pokevault-trade-staging --env staging --remote --file schema-trade/009_avatar.sql

ALTER TABLE trade_profiles ADD COLUMN avatar INTEGER;
ALTER TABLE trade_profiles ADD COLUMN avatar_animated INTEGER NOT NULL DEFAULT 0;

UPDATE trade_meta SET value = '9' WHERE key = 'schema_version';
