-- Premium "per sempre" per un singolo account, senza toccare il codice.
--
-- Il Worker legge gift_redemptions.granted_until_ms (billing.ts) e lo somma
-- agli abbonamenti Play: una data lontanissima equivale a un premium a vita.
--
-- 1. Ricava l'UID Firebase di emanuelebuia@live.it dalla console Firebase
--    (Authentication > Users > colonna "User UID").
-- 2. Sostituisci <UID> qui sotto.
-- 3. Applica sul database dei billing (quello con la tabella gift_redemptions):
--      npx wrangler d1 execute <NOME_DB> --remote --file scripts/grant-lifetime-premium.sql
--
-- 4102444800000 = 2100-01-01 in millisecondi epoch.

INSERT INTO gift_redemptions (uid, code, device_hash, granted_until_ms)
VALUES ('<UID>', 'LIFETIME-OWNER', NULL, 4102444800000)
ON CONFLICT(uid) DO UPDATE SET granted_until_ms = 4102444800000;
