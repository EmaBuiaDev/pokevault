# Ambienti: staging e produzione

Provare l'app sul telefono significava scrivere nei dati veri: le carte, i
deck e gli album di produzione. Da qui in poi non più.

L'app ha due **flavor**, `prod` e `staging`, che puntano a due progetti
Firebase diversi. Hanno `applicationId` diversi (`com.emabuia.pokevault` e
`com.emabuia.pokevault.staging`), quindi **convivono sullo stesso telefono**:
sotto l'icona si legge "PokeVault" o "PokeVault Staging".

## Cosa è separato e cosa no

| | prod | staging |
|---|---|---|
| Progetto Firebase (Auth, Firestore, Storage) | `pokevault-32d28` | progetto a parte |
| Dati utente: carte, deck, album, wishlist | separati | separati |
| Worker Cloudflare: catalogo, prezzi, immagini | **condiviso** | **condiviso** |
| Worker Cloudflare: `/billing`, `/gift` | funzionano | **non funzionano** |
| Acquisti Google Play | funzionano (license tester) | **non funzionano** |

Il Worker resta condiviso di proposito: catalogo, prezzi e immagini sono dati
di riferimento, non dati dell'utente, e duplicarli vorrebbe dire tenerli
allineati a mano. Le rotte non autenticate funzionano identiche nei due
ambienti.

Le due righe in grassetto sono limiti reali, non dimenticanze:

- **`/billing` e `/gift` rifiutano gli utenti di staging.** Il Worker verifica
  l'ID token Firebase contro un solo `FIREBASE_PROJECT_ID`, quello di
  produzione (`pokevault-proxy-worker/src/billing.ts`). Un token emesso dal
  progetto di staging ha `aud`/`iss` diversi e viene scartato. Si può
  sistemare facendo accettare al Worker una lista di project id invece di uno
  solo — piccola modifica, non ancora fatta.
- **Play Billing non funziona con un `applicationId` diverso.** Play riconosce
  solo il package pubblicato. Gli abbonamenti si continuano a provare sulla
  build di produzione con i license tester. Non è aggirabile senza pubblicare
  un secondo package su Play.

## Setup iniziale (una volta sola)

### 1. Creare il progetto Firebase di staging

Nella console Firebase: nuovo progetto, nome a piacere (es.
`pokevault-staging`). Poi, dentro al progetto:

1. **Firestore Database** → crea il database.

   - **Località**: la stessa di produzione (`eur3`, se è lì che sta prod —
     si legge in cima alla scheda dati del progetto `pokevault-32d28`). Va
     scelta bene al primo colpo: **non è modificabile dopo**.
   - **Modalità**: *produzione*, non *test*. La modalità test scrive una
     regola che lascia leggere e scrivere a chiunque per 30 giorni: oltre a
     essere un database aperto, non sono le regole di produzione, quindi lo
     staging si comporterebbe come un'app diversa dalla tua — cioè non
     servirebbe a niente.

   La modalità produzione nega tutto finché non ci deployi sopra le regole
   vere (punti 2 e 3 qui sotto). Nel frattempo l'app di staging non legge e
   non scrive: è atteso, non è un guasto.
2. **Authentication → Sign-in method** → abilita **Google**, e imposta l'email
   di supporto. Senza questo passaggio il login non parte e non si arriva a
   vedere nient'altro, perché l'app si apre sulla schermata di accesso.
3. **Project settings → Add app → Android**:
   - package name: `com.emabuia.pokevault.staging` (con il suffisso, è quello
     che l'app installa davvero);
   - SHA-1 del keystore di **debug**, che su questa macchina è:

     ```
     13:B0:EC:19:58:32:39:2C:41:81:CD:31:2D:6D:6E:76:F8:4E:A8:64
     ```

     Da un'altra macchina cambia: lo stampa `./gradlew signingReport`, riga
     `Variant: stagingDebug`, `Config: debug`.
4. Scarica il `google-services.json` che la console propone e mettilo in:

   ```
   app/src/staging/google-services.json
   ```

   Non sovrascrivere `app/google-services.json`: quello è di produzione, e il
   plugin lo usa per il flavor `prod`.

Finché quel file non c'è, il flavor `staging` non compila e si ferma così:

```
> No matching client found for package name 'com.emabuia.pokevault.staging'
  in .../app/google-services.json
```

È il plugin `google-services` che, non trovando un file per il flavor, ripiega
su quello di produzione e lì il package col suffisso non esiste. Il flavor
`prod` non se ne accorge: continua a compilare e a testare come prima.

### 2. Portare regole e indici Firestore nel repo

Oggi le regole di sicurezza e gli indici vivono **solo nella console di
produzione**: se qualcuno le cambia non se ne accorge nessuno, e lo staging
partirebbe con regole diverse da prod — cioè testerebbe un'altra app.

Serve la CLI. Node è già richiesto dagli script di ingest, e `npx` evita
un'installazione globale:

```bash
npx firebase-tools login      # apre il browser, una volta sola
```

**Gli indici** si esportano da riga di comando:

```bash
npx firebase-tools firestore:indexes --project prod > firestore.indexes.json
```

Oggi quel file esce vuoto (`"indexes": []`), e non è un export fallito: le
query dell'app sono a sola uguaglianza (`apiCardId` + `variant`, `set`) o con
un solo `orderBy("createdAt")`, e Firestore le serve con gli indici a campo
singolo che crea da sé. Gli indici compositi servono per uguaglianza + range
o ordinamento su un altro campo, che qui non compaiono ancora.

**Le regole no**: non esiste un comando per scaricarle (`firebase firestore`
ha solo `delete`, `bulkdelete`, `indexes`, `locations`, `operations`,
`databases`, `backups`). Si copiano dalla console:

> Console Firebase → progetto `pokevault-32d28` → *Firestore Database* →
> scheda **Regole** → seleziona tutto il testo e incollalo in
> `firestore.rules`.

In alternativa `npx firebase-tools init firestore` propone di scaricarle, ma è
interattivo e fa domande anche sugli indici: il copia-incolla è più corto e non
lascia dubbi su cosa hai preso.

Qualunque strada, **non inventare quelle regole**: devono essere esattamente
quelle in vigore, altrimenti lo staging testerebbe un'app diversa e un deploy
distratto su prod aprirebbe i dati di tutti. Rileggi il file prima di
committarlo.

### 3. Allineare lo staging a produzione

Gli alias `prod` e `staging` sono già in `.firebaserc`, quindi basta:

```bash
npx firebase-tools deploy --only firestore:rules,firestore:indexes --project staging
```

Su **prod non serve deployare adesso**: le regole lì sono già quelle, il file
nel repo ne è solo la copia versionata. Il deploy su prod si fa quando le
regole cambiano davvero, e dopo averle provate su staging.

Quando quel momento arriva, è la stessa riga con l'altro progetto:

```bash
npx firebase-tools deploy --only firestore:rules,firestore:indexes --project prod
```

> In `.firebaserc` **non c'è un alias `default`**, di proposito: senza, la CLI
> si rifiuta di agire finché non dici esplicitamente su quale progetto stai
> lavorando. Un `firebase deploy` distratto non può finire su produzione.

## Uso quotidiano

```bash
# Staging: dati separati, è quello che vuoi mentre sviluppi
./gradlew installStagingDebug

# Produzione: quello che poi finisce su Play
./gradlew installProdDebug

# Test e build (prod è la variante che si pubblica, quindi è quella che si misura)
./gradlew testProdDebugUnitTest
./gradlew assembleProdDebug
```

Per provare sul telefono una build **minificata da R8** senza rischiare i dati
veri — il caso in cui una release risulta verde ma una funzione è morta
nell'artefatto — ora c'è la combinazione che prima non esisteva:

```bash
./gradlew installStagingReleaseSmoke
```

Stesse regole R8 e stessi `BuildConfig` della release, firmata con la chiave di
debug (serve per il Google Sign-In, vedi il commento in `app/build.gradle.kts`),
ma contro il Firestore di staging.

## Nomi dei task: cosa è cambiato

Con i flavor, AGP genera un task per ogni combinazione flavor + build type. I
task per variante **cambiano nome**, quelli aggregati restano:

| Prima | Adesso |
|---|---|
| `testDebugUnitTest` | `testProdDebugUnitTest` / `testStagingDebugUnitTest` |
| `jacocoTestDebugUnitTestReport` | `jacocoTestProdDebugUnitTestReport` |
| `connectedAndroidTest` | `connectedProdDebugAndroidTest` |
| `lint` | `lintProdDebug` |
| `assembleDebug` | esiste ancora, ma costruisce **entrambi** i flavor |
| `installDebug` | `installProdDebug` / `installStagingDebug` |

`assembleDebug` e `lint` senza variante provano a costruire anche lo staging,
quindi falliscono se `app/src/staging/google-services.json` non c'è. Per
questo la CI ora nomina sempre `prod`: deve verificare ciò che si pubblica.
