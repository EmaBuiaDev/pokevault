# Catalogo dei test

Cosa verifichiamo prima di mandare una versione in produzione, dove sta il
test, quando gira e come si legge il risultato. Per come si scrivono e si
lanciano i test in generale vedi [TESTING.md](TESTING.md).

## I tre livelli

| Livello | Cosa prova | Dove | Quando gira | Blocca la release? |
|---|---|---|---|---|
| **1. Unitari** | La logica isolata: parser, prezzi, rarità, filtri… (550 test) | `app/src/test/` | A ogni push (`android-tests.yml`) | Sì |
| **2. Integrazione** | Il codice vero dell'app contro un Firebase finto: collezione, totali, import deck, regole di sicurezza | `app/src/test/.../integration/` | Push su `R*` e `release/**`, PR verso `master` (`test-integrazione.yml`) | Sì |
| **3. Catalogo dal vivo** | Il worker di produzione: loghi, immagini, liste carte, prezzi | `pokevault-proxy-worker/scripts/verifica-catalogo-live.mjs` | Ogni giorno alle 07:00 UTC, push su `R*` e `release/**` (`verifica-catalogo.yml`) | Sì, sui rami di release |

Il livello 2 non tocca dati veri: gira sull'emulatore Firebase, in un
progetto (`demo-pokevault`) che esiste solo lì. Il livello 3 non scrive
niente: legge il worker e l'elenco di R2.

## Livello 2 — Collezione (`CollezioneEmulatorTest`)

Ogni test registra un utente nuovo con la registrazione vera dell'app, quindi
parte da una collezione vuota. "Numero" e "valore" sono i contatori del
profilo (`totalCards`, `totalValue`) che l'app mostra in Home e Statistiche,
letti dal server.

| Test | Cosa garantisce |
|---|---|
| la registrazione crea il profilo con la collezione vuota | Un utente nuovo parte da 0 carte e 0 € |
| aggiungere una carta la salva e fa salire numero e valore | Aggiunta singola: documento creato, totali +quantità e +prezzo×quantità |
| la stessa stampa nella stessa lingua si somma invece di sdoppiarsi | Riaggiungere una carta che hai già ne aumenta la quantità |
| una stampa diversa della stessa carta resta separata | Normale e Reverse sono due carte distinte |
| aggiunta multipla somma le stampe gia' possedute | Selezione multipla in un set: crea le nuove, somma quelle che hai, totali giusti |
| eliminare una carta fa scendere numero e valore | Eliminazione singola |
| eliminazione multipla fa scendere i totali di tutte le carte tolte | Selezione multipla in Collezione → Elimina |
| togliere una copia scala la quantita' e i totali | L'"Annulla" dello scanner |
| cambiare la quantita' aggiorna i totali della sola differenza | Modifica quantità dal dettaglio carta |
| le carte solo-deck non entrano in collezione ne' nei totali | Le carte dei deck di prova non contano e non si vedono in Collezione |
| eliminare una carta dal set non tocca le sue copie solo-deck | Togliere una carta non rompe i deck di prova che la usano |
| i totali del profilo coincidono con la somma delle carte | Dopo aggiunte ed eliminazioni i contatori non si disallineano, e Statistiche dice lo stesso |
| un utente non puo' leggere le carte di un altro | Le regole di sicurezza di `firestore.rules` tengono |

## Livello 2 — Import deck (`ImportDeckEmulatorTest`)

Il percorso è quello del Deck Lab: `importFromText` (lo stesso per il testo
incollato e per il CSV) e poi la scelta fra "in collezione" e "deck di prova".
Le carte si cercano nel catalogo vero e il prezzo si risolve come nell'app.

| Test | Cosa garantisce |
|---|---|
| import dal tasto Importa in collezione fa salire numero e valore | Decklist in formato PTCG: le carte diventano tue, con immagine, e il valore della collezione sale della somma dei loro prezzi (più di 0) |
| import da CSV in collezione fa salire numero e valore | Lo stesso deck da CSV dà lo stesso risultato |
| import come deck di prova non tocca la collezione | Con "deck di prova" le carte restano nel deck: numero e valore della collezione non cambiano |

## Livello 3 — Catalogo dal vivo

| Controllo | Cosa garantisce |
|---|---|
| Logo di ogni espansione | Risponde, è un'immagine vera e ha dimensioni da logo. Si chiede con lo stesso codice che usa l'app (sv05 → TEF, me01 → MEG…) |
| 3 carte per set (prima, centrale, ultima) | Scaricate e decodificate con l'URL dell'app: formato e proporzioni da carta. La prima anche nella misura del dettaglio (`size=high`) |
| Copertura completa | **Ogni** carta del catalogo ha il suo file su R2 dove il worker lo cerca. Si fa sull'elenco di R2, senza chiedere 18 mila immagini al worker |
| Lista carte di ogni set | Risponde, non è vuota, e il numero di carte torna con quello dichiarato |
| Prezzi | Disponibili, e con un avviso se il più vecchio ha più di 72 ore |

La copertura completa usa una copia dell'ordine in cui il worker cerca i file
(`buildItalianCardKeyCandidates` in `src/index.ts`). Se il worker cambia e la
copia no, se ne accorge da solo: le 3 carte scaricate davvero devono dare lo
stesso esito della copia, altrimenti il report lo segnala come errore.

## Leggere i report

- **Livello 2**: nella pagina della run su GitHub (*Actions* → run →
  *Summary*) c'è il riepilogo "Test di integrazione" con i test passati e
  falliti. L'artifact `report-integrazione` ha il dettaglio di ogni test:
  aprire `index.html`. I nomi dei test sono frasi: quello che fallisce dice
  già cosa si è rotto.
- **Livello 3**: la tabella riassuntiva è nel *Summary* della run, con un
  elenco "Da guardare" (una riga per problema). L'artifact `report-catalogo`
  contiene `report.html`, una galleria con logo e carte di ogni set: i set con
  problemi stanno in cima, e per ognuno si può aprire l'elenco delle carte
  senza immagine. Se la run delle 07:00 fallisce, si apre (o si aggiorna) una
  issue con lo stesso riepilogo.

## Lanciarli in locale

```bash
# Livello 2: prima l'emulatore Firebase (serve Java), in un terminale a parte
npx firebase-tools emulators:start --config firebase.emulators.json --only firestore,auth --project demo-pokevault
# poi i test (JDK 21, vedi TESTING.md)
./gradlew testProdDebugUnitTest --tests "com.emabuia.pokevault.integration.*"

# Livello 3 (dalla cartella del worker); con il token fa anche la copertura completa
cd pokevault-proxy-worker
CLOUDFLARE_API_TOKEN=$(npx wrangler auth token) node scripts/verifica-catalogo-live.mjs --out report-catalogo
# solo alcuni set:
node scripts/verifica-catalogo-live.mjs --only me03,sv09
```

Senza emulatore i test del livello 2 si **saltano** da soli, così i test
unitari di sempre restano verdi. In CI il job di integrazione mette
`POKEVAULT_EMULATOR_REQUIRED=1`: lì un emulatore che non parte fa fallire,
invece di lasciar passare una release senza aver provato niente.

## Cose da sapere

- **Nomi dei test di integrazione: al massimo una settantina di caratteri.**
  Robolectric li mette nel percorso della cartella temporanea, e su Windows
  oltre i 260 caratteri la cache di Firestore non si apre: il test fallisce
  con `SQLITE_CANTOPEN`, e sembra un guasto di Firestore.
- **L'import deck dipende dal catalogo e dai prezzi di produzione.** Se il
  worker è giù o una carta del deck di prova sparisce dal catalogo, fallisce
  anche se l'app è a posto: il messaggio lo dice ("trovata nel catalogo",
  "il valore della collezione sale").
- **L'import deck vuole in `local.properties` sia `POKEWALLET_PROXY_URL` sia
  `ITALIAN_CATALOG_URL`** (`<worker>/ita/catalog.json`). Senza la seconda
  l'app non trova le carte e le crea "di ripiego": il primo giro in CI è
  fallito così. Ora se ne manca una il test si ferma subito e dice quale.
- **Un 404 resta nella cache del worker per 24 ore.** Un'immagine caricata da
  poco può risultare mancante: il livello 3 lo segnala come avviso quando su
  R2 il file c'è ma il worker risponde 404.
- **I test strumentati di `android-advanced-tests.yml` non girano davvero.**
  In `app/build.gradle.kts` manca `testInstrumentationRunner`, quindi l'APK
  di test usa il vecchio `android.test.InstrumentationTestRunner`, che non
  esegue i test JUnit4. Quel job non è bloccante e nessuno se n'era accorto.
  I test di integrazione qui sopra non ne hanno bisogno (usano Robolectric).

## Cosa hanno trovato al primo giro (27/09/2026)

Il livello 3 ha trovato **310 carte su 18.845 senza immagine** nell'app, tutte
risultate "ok" su D1 (che non verifica se il file si raggiunge davvero):

- Trainer Gallery SWSH9, SWSH10, SWSH11 e Tesoro Lucente SWSH45SV (212 carte,
  tutte): i file sono su R2 in `SWSH11TG/`, `SWSH45SV/`…, ma l'app li chiede in
  `SWSH11/`, `SWSH45/`.
- Destini Futuri (bw4): 94 carte su 103 mai caricate.
- Quattro carte singole: swsh12pt5 160, sm2 75, xy1 75, bw2 11.

Erano problemi presenti da maggio, non regressioni. **Sistemati lo stesso
giorno**, solo aggiungendo file su R2 (niente codice, niente cancellato):

- sottocollezioni: i 212 file copiati nella cartella che chiede l'app
  (`it/SWSH11TG/TG01.webp` → `it/SWSH11/TG01.webp`, e così via);
- Destini Futuri e le quattro singole: 97 immagini dall'archivio ufficiale
  pokemon.com, il Pikachu 160 di Zenit Regale da Limitless (`CRZ_160_R_IT`),
  tutte controllate a occhio prima del caricamento.

Dopo lo svuotamento delle chiavi KV la verifica dà 18.845 carte su 18.845.
