-- TradeRadar, migrazione 003: carte aggiunte a mano e avvisi per carta.
--
-- manual = 1: una carta che l'utente possiede in una copia sola e ha messo in
-- lista lui. TradeRadar non la propone mai da solo.
-- notify = 1: la carta partecipa agli avvisi (quando arriveranno, fase 2):
-- chi vicino la cerca puo' essere avvisato, e il proprietario con lui. Per i
-- doppioni e' sempre 1; per le carte a mano parte da 0 e la accende l'utente
-- con la campanella.
--
-- Si applica con:
--   npx wrangler d1 execute pokevault-trade-staging --env staging --remote --file schema-trade/003_avvisi_carte.sql

ALTER TABLE trade_haves ADD COLUMN manual INTEGER NOT NULL DEFAULT 0;
ALTER TABLE trade_haves ADD COLUMN notify INTEGER NOT NULL DEFAULT 1;

UPDATE trade_meta SET value = '3' WHERE key = 'schema_version';
