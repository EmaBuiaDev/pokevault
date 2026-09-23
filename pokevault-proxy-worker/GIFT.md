# Codici regalo da 1 mese

## Cosa sono

Due forme dello stesso oggetto, gestite dallo stesso endpoint:

| Tipo | Chi lo crea | Riscatti | A cosa serve |
|---|---|---|---|
| **AMICO** (`kind = 'friend'`) | l'app, alla prima apertura della schermata | fino a 5 | l'utente lo condivide per regalare un mese a un amico |
| **promo** (`kind = 'promo'`) | tu, con `/v1/gift/admin/create` | 1 | giveaway, supporto, scuse per un bug |

Il codice AMICO è **derivato**, non sorteggiato: `HMAC-SHA256(GIFT_CODE_SECRET,
"friend-code:" + uid)` ridotto a 8 caratteri su un alfabeto senza `I/1`, `O/0`,
`S/5`, `B/8`. Lo stesso account ritrova sempre lo stesso codice dopo un
reinstallo o un cambio telefono — e cambiare `GIFT_CODE_SECRET` li cambia tutti,
quindi non si cambia.

## Perché lato server

Un mese di premium è un entitlement. Se lo decidesse il client, chiunque se lo
concederebbe modificando l'app: è lo stesso ragionamento di `BILLING.md`, dove
l'entitlement Play era dichiarato dal telefono e nessuno lo rileggeva.
`GET /v1/billing/entitlement` ora restituisce la **somma** delle due fonti,
abbonamento Play e regalo, così il client non deve conciliare due verità.

## L'abuso da temere

Non è il codice rubato, è il multi-account: una persona si rifà l'account e
riscatta di nuovo, all'infinito. I freni, in ordine di efficacia:

1. **`gift_redemptions.uid` è PRIMARY KEY** → un riscatto per account, per
   sempre. Nemmeno lasciando scadere il mese si rigenera il diritto.
2. **Indice unico su `device_hash`** → un riscatto per dispositivo. È questo che
   blocca gli account usa e getta sullo stesso telefono. L'app manda
   `Settings.Secure.ANDROID_ID`; il Worker ne conserva solo un HMAC, mai il
   valore grezzo.
3. **`max_redemptions` sul codice** → un codice AMICO finito su un gruppo
   Telegram non diventa un distributore infinito.
4. **Rate limit per IP** (3/giorno, in KV) → freno morbido contro chi prova
   codici a tentativi. Non è un vincolo di correttezza: una rete mobile condivide
   un IP fra utenti veri e una VPN lo cambia a comando.
5. **`owner_uid != uid`** → non puoi riscattare il tuo codice.

Nessuno di questi è un muro: un factory reset azzera l'`ANDROID_ID`. Insieme
rendono il giro più scomodo del mese che si guadagna, che è l'obiettivo
realistico.

> **Nota privacy.** L'`ANDROID_ID` è un identificatore di dispositivo. Non entra
> nel database in chiaro e non viene usato per profilazione, ma se l'informativa
> pubblica (`docs/privacy-policy`) elenca i dati raccolti, va aggiunta una riga:
> un identificatore di installazione, conservato in forma non reversibile, al
> solo scopo di impedire riscatti multipli.

## Endpoint

| Metodo | Percorso | Autenticazione | Cosa fa |
|---|---|---|---|
| GET | `/v1/gift/me` | `Authorization: Bearer <ID token Firebase>` | Il mio codice AMICO (creandolo se manca), quante volte è stato speso, se ho già riscattato. |
| POST | `/v1/gift/redeem` | `Authorization: Bearer <ID token Firebase>` | Riceve `{"code": "...", "deviceId": "..."}` e concede il mese. |
| POST | `/v1/gift/admin/create` | header `x-gift-admin-secret` | Conia un lotto di codici promo monouso. |

Risposta di `/v1/gift/me`:

```json
{
  "code": "AMICOK7P2QX4M",
  "grantDays": 30,
  "invitesUsed": 2,
  "invitesMax": 5,
  "alreadyRedeemed": false,
  "giftUntilMs": null
}
```

Un riscatto riuscito risponde `{"entitled": true, "grantDays": 30,
"giftUntilMs": 1791633101186}`. Un rifiuto risponde `{"entitled": false,
"reason": "<sigla>"}` con una di queste sigle:

`code_missing`, `code_not_found`, `code_disabled`, `code_expired`,
`code_exhausted`, `own_code`, `already_redeemed`, `device_already_redeemed`,
`rate_limited`.

Sono sigle e non frasi di proposito: l'app le traduce nella lingua scelta
dall'utente, e correggere un refuso non richiede di ridistribuire il Worker. Il
contratto è verificato da `GiftRejectionMappingTest` lato Android: **se aggiungi
o rinomini una sigla qui, quel test va aggiornato**, altrimenti il rifiuto
scivola su `UNKNOWN` e l'utente legge un errore di rete che non c'è stato.

## Stato: in produzione

**La configurazione è già stata fatta, il 16 settembre 2026.** Non va rifatta.
Quello che segue serve solo a ricostruire l'ambiente da zero — un altro account
Cloudflare, un disastro, un secondo ambiente di prova.

| Cosa | Stato |
|---|---|
| `GIFT_CODE_SECRET` | impostato |
| `GIFT_ADMIN_SECRET` | impostato |
| `FIREBASE_PROJECT_ID` | impostato (`pokevault-32d28`) |
| `schema/010_gift_codes.sql` | applicato al D1 remoto |
| Deploy | fatto, versione `fbb2b7eb-ab28-49b5-aab1-b8cca1a71f0a` |

> ### ⚠️ Non rimettere `GIFT_CODE_SECRET`
>
> È il segreto da cui si deriva il codice AMICO di ogni account. Rimetterlo con
> un valore diverso **cambia il codice di tutti gli utenti in una volta**: chi
> aveva già condiviso il suo si ritrova con un codice che non esiste più, e i
> codici in giro smettono di funzionare senza alcun messaggio di errore
> utile — risponderebbero `code_not_found`, indistinguibile da un refuso.
>
> Le righe già in `gift_codes` non si aggiornano da sole, quindi il vecchio
> codice resterebbe nel database, orfano e non più raggiungibile da nessuno.
>
> Se devi davvero ruotarlo, va fatto insieme a una migrazione che ricalcola la
> colonna `code` di tutte le righe con `kind = 'friend'`. Non esiste ancora.

### 1. Secret del Worker

```bash
cd pokevault-proxy-worker

# Stringa casuale lunga. Deriva i codici AMICO e anonimizza i device id.
# Vedi l'avviso qui sopra prima di eseguirlo su un ambiente già attivo.
npx wrangler secret put GIFT_CODE_SECRET

# Altra stringa casuale, serve solo a te per coniare i lotti promo.
# Questa si può ruotare quando vuoi: non c'è niente di derivato da lei.
npx wrangler secret put GIFT_ADMIN_SECRET
```

`FIREBASE_PROJECT_ID` serve anche qui, perché è quello che permette di
verificare l'ID token. Se hai già seguito `BILLING.md` è a posto; altrimenti:

```bash
npx wrangler secret put FIREBASE_PROJECT_ID
```

Il service account della Play Developer API **non** serve ai codici regalo: un
regalo non è un acquisto e non c'è niente da chiedere a Google.

### 2. Migrazione D1

```bash
npx wrangler d1 execute pokevault-catalog --remote --file schema/010_gift_codes.sql
```

### 3. Deploy

```bash
npm run deploy
```

## Coniare un lotto di codici promo

```bash
curl -X POST https://<il-tuo-worker>/v1/gift/admin/create \
  -H "x-gift-admin-secret: <GIFT_ADMIN_SECRET>" \
  -H "content-type: application/json" \
  -d '{"count": 50, "grantDays": 30, "prefix": "NATALE"}'
```

Massimo 200 codici per chiamata. `expiresAtMs` (millisecondi epoch) è opzionale:
senza, i codici non scadono. I codici tornano nella risposta **una volta sola** —
salvali.

## Spegnere un codice

Un codice non si cancella, si disattiva: i riscatti già fatti devono restare
tracciabili.

```bash
npx wrangler d1 execute pokevault-catalog --remote \
  --command "UPDATE gift_codes SET disabled = 1 WHERE code = 'AMICOK7P2QX4M'"
```

## Cosa guardare quando qualcosa non torna

```bash
# Quanti riscatti, e su quali codici
npx wrangler d1 execute pokevault-catalog --remote \
  --command "SELECT code, COUNT(*) n FROM gift_redemptions GROUP BY code ORDER BY n DESC LIMIT 20"

# Codici AMICO che hanno saturato il tetto: se sono tanti, il tetto è basso
npx wrangler d1 execute pokevault-catalog --remote \
  --command "SELECT COUNT(*) FROM gift_codes WHERE kind='friend' AND redeemed_count >= max_redemptions"
```

Se `device_hash` risulta `NULL` su molte righe, quei riscatti sono arrivati da
client che non hanno potuto leggere l'`ANDROID_ID`: per loro il freno 2 non ha
agito, e resta solo il vincolo per account.
