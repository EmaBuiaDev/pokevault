# Verifica lato server degli abbonamenti

## Perché esiste

Prima di questo lavoro l'entitlement premium era **interamente dichiarato dal
client**: `PremiumManager` scriveva `users/{uid}.isPremium` su Firestore e
nessuno lo rileggeva mai. Due conseguenze:

- chiunque poteva concedersi il premium modificando l'app o i dati locali;
- un utente pagante che perdeva lo stato locale (cache Play stantia, cambio
  dispositivo, reinstallazione andata male) non aveva modo di recuperarlo.

Ora la fonte di verità è la risposta della **Google Play Developer API**,
interrogata dal Worker con un service account. Il client può solo *chiedere* una
verifica passando il purchase token; l'uid viene sempre ricavato dall'ID token
Firebase, mai dal corpo della richiesta.

## Endpoint

| Metodo | Percorso | Autenticazione | Cosa fa |
|---|---|---|---|
| POST | `/v1/billing/verify` | `Authorization: Bearer <ID token Firebase>` | Riceve `{"purchaseToken": "..."}`, chiede lo stato a Google, lo salva e lo restituisce. |
| GET | `/v1/billing/entitlement` | `Authorization: Bearer <ID token Firebase>` | Restituisce l'entitlement memorizzato per l'utente autenticato. |
| POST | `/v1/billing/rtdn?key=<segreto>` | Segreto condiviso in query string | Endpoint push per le Real-time Developer Notifications. |

Risposta di `verify` e `entitlement`:

```json
{
  "entitled": true,
  "state": "active",
  "expiryTimeMs": 1789041101186,
  "autoRenewing": true,
  "productId": "pokevault_premium_monthly"
}
```

`entitled` è già la decisione finale: uno stato `canceled` resta valido fino
alla scadenza, perché l'utente ha disdetto il rinnovo ma ha pagato fino a quella
data.

## Cosa devi configurare tu

Questi passaggi richiedono accesso a Google Cloud e alla Play Console e **non
sono stati eseguiti**: il codice è pronto ma inattivo finché i secret mancano.

### 1. Service account con accesso alla Play Developer API

1. Google Cloud Console → progetto collegato alla Play Console → **IAM e
   amministrazione → Account di servizio → Crea**.
2. Crea una chiave JSON e scaricala.
3. Play Console → **Utenti e autorizzazioni** → invita l'indirizzo email del
   service account, con il permesso *Visualizza dati finanziari, ordini e
   risposte ai sondaggi di annullamento*.
4. Abilita la **Google Play Android Developer API** nel progetto Cloud.

> La propagazione dei permessi dalla Play Console può richiedere fino a 24 ore.
> Fino ad allora `/v1/billing/verify` risponde 502.

### 2. Secret del Worker

```bash
cd pokevault-proxy-worker

# JSON completo del service account, in una riga
npx wrangler secret put PLAY_SERVICE_ACCOUNT_JSON

# com.emabuia.pokevault
npx wrangler secret put PLAY_PACKAGE_NAME

# Project id Firebase (quello in google-services.json)
npx wrangler secret put FIREBASE_PROJECT_ID

# Stringa casuale lunga, la stessa che metterai nell'URL push di Pub/Sub
npx wrangler secret put RTDN_SHARED_SECRET
```

### 3. Migrazione D1

```bash
npx wrangler d1 execute pokevault-catalog --remote --file schema/004_entitlements.sql
```

### 4. Real-time Developer Notifications

1. Google Cloud → **Pub/Sub** → crea un topic, es. `play-rtdn`.
2. Play Console → **Monetizzazione → Configurazione monetizzazione** → incolla
   il nome completo del topic.
3. Concedi a `google-play-developer-notifications@system.gserviceaccount.com`
   il ruolo **Publisher** su quel topic.
4. Crea una **subscription di tipo push** verso:
   ```
   https://<il-tuo-worker>/v1/billing/rtdn?key=<RTDN_SHARED_SECRET>
   ```

L'endpoint risponde `403` se il segreto non combacia, e `200` con `skipped`
quando il messaggio non è una notifica di abbonamento o il purchase token non è
ancora associato a un utente: un non-2xx farebbe ritentare Pub/Sub all'infinito
su un messaggio che non diventerà mai valido.

## Cosa manca ancora nell'app

Il Worker è pronto; **il client Android non lo chiama ancora**. Per chiudere il
cerchio serve, in `PremiumManager`:

1. dopo un acquisto confermato, chiamare `POST /v1/billing/verify` con
   `purchase.purchaseToken` e l'ID token Firebase corrente;
2. all'avvio e a ogni `onResume`, chiamare `GET /v1/billing/entitlement` e usare
   quel risultato come stato autorevole;
3. mantenere `queryPurchasesAsync` locale come **fallback offline**: se il
   Worker non risponde, l'app continua a funzionare con la verifica locale come
   fa oggi. La verifica server è autorevole quando disponibile, non un single
   point of failure.

Questo passo è deliberatamente separato: senza i secret configurati (punto 2)
gli endpoint rispondono 500/502, e cablare subito il client renderebbe il
premium non funzionante finché la configurazione non è completa.
