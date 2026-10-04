-- TradeRadar, migrazione 006: indirizzo dei luoghi segnalati.
--
-- Una segnalazione ("Manca un negozio?") ora porta nome, citta', indirizzo
-- facoltativo e tipo. Le coordinate il server le ricava dall'indirizzo (o
-- dalla citta'), cosi' "Apri in Maps" porta nel posto giusto anche prima
-- della verifica. Si applica con:
--   npx wrangler d1 execute pokevault-trade-staging --env staging --remote --file schema-trade/006_indirizzo_luoghi.sql

ALTER TABLE trade_spots ADD COLUMN address TEXT;

-- Le segnalazioni di prova del 01/10, fatte dagli script.
DELETE FROM trade_spots WHERE source = 'user' AND name = 'Fumetteria di prova';

UPDATE trade_meta SET value = '6' WHERE key = 'schema_version';
