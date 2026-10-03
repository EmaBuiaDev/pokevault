-- Il premio a chi invita col codice AMICO (decisione dell'utente del 03/10/2026).
--
-- Fino a oggi il mese lo riceveva solo l'amico che riscattava il codice. Da
-- adesso anche chi lo ha condiviso riceve 30 giorni per ogni amico, sommati:
-- il codice AMICO vale per 5 riscatti, quindi al massimo 150 giorni. Vale solo
-- per i riscatti da oggi: niente premi retroattivi.
--
-- Tabella a parte e non gift_redemptions: li' la chiave e' l'uid di chi
-- riscatta, uno per account per sempre, e chi invita puo' avere gia' la sua
-- riga per un codice ricevuto da altri. Le due scadenze si sommano in
-- activeGiftUntilMs (src/billing.ts), che prende la piu' lontana.
--
-- Scritta da handleRedeem (src/gift.ts) nella STESSA transazione del
-- riscatto: senza riscatto riuscito, nessun premio.
--
-- Si applica con:
--   npx wrangler d1 execute pokevault-catalog --remote --file schema/014_gift_referral_bonus.sql

CREATE TABLE IF NOT EXISTS gift_referral_bonus (
  uid              TEXT PRIMARY KEY,
  -- Fino a quando vale il Premium guadagnato invitando, in millisecondi epoch.
  granted_until_ms INTEGER NOT NULL,
  -- Quanti amici hanno portato un premio (da oggi in poi).
  invites          INTEGER NOT NULL DEFAULT 0,
  updated_at       INTEGER NOT NULL
);
