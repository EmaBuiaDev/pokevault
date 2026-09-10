-- Entitlement premium verificati lato server.
--
-- Fino a questo punto l'entitlement era interamente dichiarato dal client:
-- PremiumManager scriveva users/{uid}.isPremium su Firestore e nessuno lo
-- rileggeva mai. Chiunque poteva quindi ottenere il premium, e in senso opposto
-- un utente pagante che perdeva lo stato locale non aveva modo di recuperarlo.
--
-- Qui la fonte di verita' e' la risposta della Google Play Developer API,
-- interrogata dal Worker con un service account. Il client puo' solo CHIEDERE
-- una verifica passando il purchase token: non puo' asserire di essere premium.

CREATE TABLE IF NOT EXISTS entitlements (
  -- UID Firebase, ricavato dall'ID token verificato: mai preso dal body.
  uid             TEXT PRIMARY KEY,
  product_id      TEXT NOT NULL,
  purchase_token  TEXT NOT NULL,

  -- Stato normalizzato: 'active' | 'expired' | 'canceled' | 'on_hold' |
  -- 'in_grace' | 'paused' | 'revoked'. Solo 'active' e 'in_grace' danno accesso.
  state           TEXT NOT NULL,

  -- Millisecondi epoch. Finche' non e' passata, l'abbonamento e' valido anche
  -- se l'utente ha gia' disdetto (ha pagato fino a quella data).
  expiry_time_ms  INTEGER,

  -- Per il rinnovo automatico: distingue "scadra'" da "si rinnovera'".
  auto_renewing   INTEGER NOT NULL DEFAULT 0,

  updated_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

-- Le RTDN arrivano con il purchase token, non con l'uid: serve la ricerca
-- inversa per aggiornare l'entitlement giusto.
CREATE INDEX IF NOT EXISTS idx_entitlements_token ON entitlements(purchase_token);
