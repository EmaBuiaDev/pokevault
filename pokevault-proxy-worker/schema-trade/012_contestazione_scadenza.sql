-- TradeRadar, migrazione 012: "Io c'ero" e appuntamenti che scadono.
--
-- trade_no_shows.disputed_at: chi e' stato segnalato come assente puo'
-- rispondere "Io c'ero" entro 48 ore. Una segnalazione contestata e' una
-- parola contro l'altra: da sola non conta per la sospensione. Contano
-- invece tre persone diverse in 60 giorni, contestate o no: allora non e'
-- piu' un caso.
--
-- trade_proposals.status ha un valore nuovo, 'expired': 7 giorni dopo
-- l'appuntamento nessuno dei due aveva segnato niente. Nessuna colonna:
-- lo scrive il cron. Se invece uno solo aveva segnato "Scambio fatto", lo
-- stesso cron chiude lo scambio come 'done' (done_from o done_to restano
-- NULL per chi non ha risposto).
--
-- Si applica con:
--   npx wrangler d1 execute pokevault-trade-staging --env staging --remote --file schema-trade/012_contestazione_scadenza.sql

ALTER TABLE trade_no_shows ADD COLUMN disputed_at INTEGER;

UPDATE trade_meta SET value = '12' WHERE key = 'schema_version';
