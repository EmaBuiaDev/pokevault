-- Codici regalo da 1 mese: codice AMICO per account e codici promo monouso.
--
-- Perche' lato server. Un mese di premium regalato e' un entitlement a tutti
-- gli effetti: se lo decidesse il client, chiunque potrebbe concederselo
-- modificando l'app, che e' esattamente il problema che 004_entitlements.sql ha
-- risolto per gli abbonamenti Play. Qui la fonte di verita' e' il Worker, e
-- l'uid arriva sempre dall'ID token Firebase verificato, mai dal body.
--
-- L'abuso da temere non e' il codice rubato, e' il multi-account: una persona
-- si crea N account e riscatta un codice con ognuno. I tre freni sono, in
-- ordine di efficacia:
--   1. gift_redemptions.uid e' PRIMARY KEY  -> un riscatto per account, per
--      sempre. Nemmeno cancellando il mese si rigenera il diritto.
--   2. indice unico su device_hash          -> un riscatto per dispositivo.
--      E' questo che blocca gli account usa e getta sullo stesso telefono.
--   3. max_redemptions sul codice           -> un codice AMICO non puo'
--      diventare un distributore infinito se finisce in giro.
-- Nessuno dei tre e' un muro (un factory reset azzera l'ANDROID_ID), ma
-- insieme rendono il gioco piu' scomodo del mese che si guadagna.

CREATE TABLE IF NOT EXISTS gift_codes (
  -- Normalizzato in maiuscolo senza separatori: "amico-k7p2 qx4m" e
  -- "AMICO-K7P2QX4M" sono lo stesso codice.
  code             TEXT PRIMARY KEY,

  -- 'friend' = codice personale di un account, 'promo' = lotto creato dall'admin.
  kind             TEXT NOT NULL,

  -- Proprietario del codice AMICO. NULL per i promo: non hanno un mittente.
  owner_uid        TEXT,

  -- Giorni regalati. 30 oggi, ma il valore sta sulla riga e non nel codice
  -- cosi' una campagna diversa non richiede un deploy.
  grant_days       INTEGER NOT NULL DEFAULT 30,

  -- Quante volte il codice e' ancora spendibile. 1 per i promo (monouso),
  -- un tetto piccolo per i codici AMICO.
  max_redemptions  INTEGER NOT NULL DEFAULT 1,
  redeemed_count   INTEGER NOT NULL DEFAULT 0,

  -- Spegne un codice senza cancellarlo, cosi' i riscatti gia' fatti
  -- restano tracciabili.
  disabled         INTEGER NOT NULL DEFAULT 0,

  -- Millisecondi epoch. NULL = il codice non scade.
  expires_at_ms    INTEGER,

  created_at       INTEGER NOT NULL DEFAULT (unixepoch())
);

-- Un solo codice AMICO per account: la riga si crea alla prima apertura della
-- schermata e da li' in poi e' sempre la stessa.
CREATE UNIQUE INDEX IF NOT EXISTS idx_gift_codes_owner
  ON gift_codes(owner_uid) WHERE owner_uid IS NOT NULL;

CREATE TABLE IF NOT EXISTS gift_redemptions (
  -- PRIMARY KEY, non solo indicizzato: e' il vincolo "un riscatto per account".
  uid              TEXT PRIMARY KEY,
  code             TEXT NOT NULL,

  -- HMAC-SHA256 dell'ANDROID_ID con GIFT_CODE_SECRET. L'identificatore grezzo
  -- non entra mai nel database: serve solo a sapere se QUESTO dispositivo ha
  -- gia' riscattato, non a riconoscerlo altrove.
  device_hash      TEXT,

  -- Fino a quando vale il mese regalato, in millisecondi epoch.
  granted_until_ms INTEGER NOT NULL,

  redeemed_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_gift_redemptions_code ON gift_redemptions(code);

-- Il freno vero al multi-account. Parziale perche' un client che non riesce a
-- leggere l'ANDROID_ID manda NULL, e NULL non deve collidere con NULL.
CREATE UNIQUE INDEX IF NOT EXISTS idx_gift_redemptions_device
  ON gift_redemptions(device_hash) WHERE device_hash IS NOT NULL;
