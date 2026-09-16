-- Un acquisto appartiene a un account solo.
--
-- 004_entitlements.sql metteva un indice NON unico su purchase_token, e
-- saveEntitlement risolve il conflitto su uid. Messe insieme, le due cose
-- permettevano allo stesso abbonamento di essere rivendicato da quanti account
-- si vuole: ognuno chiamava /v1/billing/verify con lo stesso token e otteneva
-- la sua riga.
--
-- Cosi' la verifica lato server non avrebbe risolto niente: il sintomo era
-- proprio "cambio account e sono tutti premium", visto in test interno il
-- 16/09/2026 (vedi BILLING.md).
--
-- Regola scelta: vince il primo account che verifica quell'acquisto. Gli altri
-- ricevono un rifiuto e restano senza premium. Se qualcuno lega l'abbonamento
-- all'account sbagliato, si sblocca a mano:
--
--   DELETE FROM entitlements WHERE purchase_token = '<token>';
--
-- e al giro successivo il primo che verifica se lo riprende.

CREATE UNIQUE INDEX IF NOT EXISTS idx_entitlements_token_unique
  ON entitlements(purchase_token);

-- L'indice non unico di 004 diventa ridondante: quello unico serve gia' le
-- ricerche per token che fanno le RTDN.
DROP INDEX IF EXISTS idx_entitlements_token;
