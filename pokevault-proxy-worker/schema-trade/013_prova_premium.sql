-- TradeRadar, migrazione 013: la prova gratuita di 30 giorni.
--
-- Decisione dell'utente del 03/10/2026: TradeRadar e' gratis per 30 giorni
-- dall'attivazione, poi serve il Premium (lo stesso abbonamento dell'app).
-- Senza Premium, finita la prova, si resta "solo ricevere": visibili agli
-- altri, si risponde alle proposte e si finiscono gli scambi avviati, ma non
-- si sfogliano i match e non si mandano proposte nuove. Il blocco lo accende
-- TRADE_TRIAL_ENFORCE nel Worker, non questa migrazione.
--
-- trade_trials sta a parte da trade_profiles, come trade_sanctions: chi
-- disattiva e riattiva TradeRadar non riparte da una prova nuova. La riga se
-- ne va 12 mesi dopo l'inizio se il profilo non c'e' piu' (purgeExpired),
-- come il resto di cio' che resta dopo una disattivazione.
--
-- I profili gia' esistenti (solo staging: in produzione non ce ne sono)
-- partono dal giorno in cui li hanno creati.
--
-- Si applica con:
--   npx wrangler d1 execute pokevault-trade-staging --env staging --remote --file schema-trade/013_prova_premium.sql

CREATE TABLE IF NOT EXISTS trade_trials (
  uid        TEXT PRIMARY KEY,
  started_at INTEGER NOT NULL
);

INSERT OR IGNORE INTO trade_trials (uid, started_at)
  SELECT uid, created_at FROM trade_profiles;

UPDATE trade_meta SET value = '13' WHERE key = 'schema_version';
