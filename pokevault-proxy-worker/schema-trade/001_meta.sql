-- TradeRadar, migrazione 001: solo il segnaposto della versione di schema.
--
-- Il database degli scambi e' separato dal catalogo (vedi src/trade.ts) e ha
-- le sue migrazioni, numerate da capo in questa cartella. Si applicano con:
--   npx wrangler d1 execute pokevault-trade-staging --env staging --remote --file schema-trade/<file>
-- e ognuna aggiorna schema_version, che /v1/trade/health riporta.

CREATE TABLE IF NOT EXISTS trade_meta (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

INSERT INTO trade_meta (key, value) VALUES ('schema_version', '1')
  ON CONFLICT(key) DO UPDATE SET value = excluded.value;
