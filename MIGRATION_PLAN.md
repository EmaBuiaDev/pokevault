# PokeVault — Piano di Aggiornamento, Migrazione a Cloudflare e Catalogo IT Proprietario

> Documento operativo. Da seguire fino alla fine, milestone per milestone.
> Ultimo aggiornamento: 2026-09-06 (revisione post-M0)

---

## ⚠️ Aggiornamento post-approvazione: la baseline reale non era master

Il piano sotto (sezioni 1-9) e stato scritto analizzando **`master`**. Durante l'esecuzione di M0 e emerso che **`master` era il branch sbagliato da cui partire**: esisteva gia, fermo dal 11 giugno 2026, il branch **`release/R2.0.21`** con 27 commit di lavoro reale su esattamente il problema che il piano voleva risolvere.

### Cosa e stato trovato e verificato

| Verifica | Esito |
|---|---|
| `release/R2.0.21` vs `master` | 27 commit avanti, **0 dietro** — superset completo, nessun conflitto |
| `release/R2.0.21` vs `prod/R2.0.17` | 19 commit avanti, 0 dietro — e il branch piu avanzato che esiste |
| Versione | `versionCode 28`, `versionName 2.0.20` (master era fermo a 2.0.14) |
| **R2 gia attivo** | Bucket `pokevault-images` collegato al Worker (`wrangler.toml`: `IMAGES_BUCKET`) |
| **Layer italiano gia scritto** | `data/italian/ItalianCatalog.kt`, `ItalianCatalogRemoteRepository.kt`, `ItalianPriceSnapshotRepository.kt` |
| **Copertura dichiarata** | Commit dell'11 giugno: *"snapshot ITA completo ... 105/106 espansioni"* |
| **Worker esteso** | `src/index.ts` passato da 674 a **1798 righe**: endpoint `/ita/catalog.json`, `/ita/prices.json`, `/images/it/*` |
| **Pipeline prezzi bulk gia implementata** | `buildItalianPriceSnapshot()`: budget di 30 fetch upstream/run, cache KV-first, cursore resumibile — esattamente il pattern "bulk invece di per-carta" che il piano (sez. 2.3) proponeva di costruire da zero |
| **Acquisizione immagini oggi** | `pokevault-proxy-worker/scripts/upload-ita-r2.ps1`: carica su R2 da **una cartella locale sul PC**, non da alcuna fonte automatica. Le 105/106 espansioni sono frutto di lavoro manuale, non di ingest |
| **Persistenza catalogo** | **Nessun D1**: il catalogo e un **unico blob JSON** (`it/catalog/cards.cleaned.json` su R2) scaricato per intero dal client e tenuto in `SharedPreferences` (TTL 5 min) — non interrogabile |
| Toolchain di build | JDK 25 (JBR di Android Studio) e **incompatibile** con Kotlin 2.0.21 (`IllegalArgumentException: 25.0.2` nel parser versione). Serve **JDK 21**, trovato in `C:\Users\ASUS\.jdks\jbr-21.0.11` |
| Build con JDK 21 | **`./gradlew :app:compileDebugKotlin` -> BUILD SUCCESSFUL** (1m40s) sulla baseline R2.0.21 |

### Decisioni prese con l'utente per correggere la rotta

1. **Adottare `release/R2.0.21` come baseline** — eseguito: `master` locale portato in fast-forward a `origin/release/R2.0.21` (nessuna perdita di commit, nessun force-push, non ancora pushato su origin).
2. **Migrare il blob JSON verso D1** — confermato: si procede con l'introduzione di D1 come da sezione 2.1/2.4 del piano originale, ma **come evoluzione** dell'endpoint `/ita/catalog.json` e `/ita/prices.json` gia esistenti, non come sistema parallelo.

### Come questo cambia le sezioni sotto

Le sezioni 1-9 restano valide nell'**impianto** (architettura target D1+R2+Worker, regola di copertura a 3 livelli, isolamento prezzi, compliance, roadmap, bugfix) ma vanno lette con queste correzioni:

| Sezione originale | Correzione |
|---|---|
| 1.1 "Stack reale" | Riferita a `master` (obsoleta): la baseline vera e `release/R2.0.21`, versione 2.0.20, con layer `data/italian/` gia presente |
| 2.1 "Perche D1 e non KV" | Resta valida, ma il bersaglio non e piu un blob KV: e il **blob JSON su R2** (`it/catalog.json`, `it/prices.json`) gia servito dal Worker attuale |
| 2.3 "Pipeline prezzi bulk" | **Gia implementata** in `buildItalianPriceSnapshot()`. Il lavoro restante e migrare la sua **destinazione di scrittura** da JSON-su-R2 a righe D1, non costruire la logica di raccolta da zero |
| 2.5 "Migrazione step-by-step" | Il passo "Deploy Worker `pokevault-api` affiancato" non serve: **si estende il Worker `pokevault-proxy` esistente** con un nuovo backend D1 dietro gli stessi endpoint (`/ita/catalog.json`, `/ita/cards`, ecc.), mantenendo la stessa interfaccia verso il client dove possibile |
| M1 "Fondamenta Cloudflare" | Non si parte da zero: R2 e gia provisionato. Il lavoro reale e **aggiungere D1** accanto a R2/KV esistenti e scrivere la migration del blob -> righe |
| M2 "Ingest pilota" | L'ingest automatico da fonte IT (TCGdex, verificato gratuito e senza rate limit in sez. 1.1) **va ancora costruito**: oggi l'unica via di aggiornamento immagini e manuale (`upload-ita-r2.ps1`). Qui il piano originale resta interamente da eseguire |
| M0 "Ripresa" | Completato per le parti fattibili offline (build, branch, versioni). Restano da fare: `wrangler whoami`/inventario risorse live (richiede Node.js, **non installato** su questa macchina — vedi sotto) e verifica di quale sia l'unica espansione mancante (105/106) |

### M0 completato: inventario reale delle risorse Cloudflare

Node.js LTS installato, `wrangler login` eseguito (account confermato: `emanuelebuia@live.it`, stesso `account_id` del progetto), push del fast-forward eseguito su `origin/master` (ora allineato a `release/R2.0.21`, v2.0.20).

| Risorsa | Stato reale verificato |
|---|---|
| Worker `pokevault-proxy` | **Vivo**, ultimo deploy 10 giugno 2026 |
| KV `pokevault-proxy-pokevault-cache-20260421` | Esiste, id coincide con `wrangler.toml` |
| R2 `pokevault-images` | **Esiste**, creato 14 maggio 2026 |
| **D1** | Nessun database — da creare in M1, come previsto |
| Permessi token | `d1 (write)` gia incluso — nessun ostacolo a creare D1 |

### Scoperta maggiore: la copertura italiana e MOLTO piu ampia del previsto

Scaricato ed analizzato `it/catalog/cards.cleaned.json` da R2 (10,4 MB):

| Metrica | Valore reale |
|---|---|
| Carte totali nel catalogo | **15.406** |
| Espansioni distinte | **106** (tutte presenti, non 105/106 — il gap del commit di giugno risulta chiuso) |
| Copertura temporale | Da **`dp1` (Diamond & Pearl, 2007)** fino a **`me04`/`sv10` (2026)** — BW, DP, HGSS, XY, SM, SWSH, SV: **quasi due decenni**, non solo i set moderni |
| Immagini in R2 | **15.539 file, 2,49 GB**, verificate esistenti campionando 6 carte su 6 sparse in tutte le ere (`DP1`, `XY1`, `BW1`, `SM1`, `SWSH1`, `ME04`) |
| Formato immagini | **PNG non compresso**, 245×342px, ~150-200 KB/file |

**Conseguenza pratica**: il backfill storico (M8 del piano originale, previsto come lavoro "continuo" a valle) **e gia sostanzialmente fatto**. Non sappiamo ancora da quale fonte sia stato acquisito (non e TCGdex: i test diretti su TCGdex mostravano 404 su tutti i set pre-2023 in italiano) — probabilmente lavoro di raccolta/curazione manuale pregresso, coerente con lo script di upload da cartella locale.

**Il problema reale da risolvere non e piu "costruire la copertura storica"**, ma:
1. **Automatizzare l'ingresso dei set futuri** (l'unico pezzo davvero mancante — oggi richiede intervento manuale a ogni nuova espansione)
2. **Ricomprimere le 15.539 immagini PNG in WebP** — stesso contenuto, stima **2,49 GB -> ~300-400 MB**, nessuna perdita di copertura
3. **Migrare 15.406 righe da blob JSON a D1** (confermato dall'utente) — un file da 10,4 MB scaricato per intero ogni 5 minuti di utilizzo attivo e il costo concreto, misurato, che D1 elimina

### Fonte delle immagini esistenti — chiarita dall'utente: scraping sito ufficiale + scansioni personali

Le 15.539 immagini gia in R2 provengono da **scraping del sito ufficiale Pokemon** e da **scansioni personali** di carte fisiche. Questo cambia il profilo di rischio rispetto a quanto scritto in sezione 4 (che assumeva una fonte terza con licenza aperta tipo TCGdex):

| Aspetto | Implicazione |
|---|---|
| Scraping dal sito ufficiale | E' un'esposizione **diretta** verso il detentore dei diritti (Pokemon Company/Nintendo), non una ridistribuzione di materiale di terzi con licenza propria. Il sito ufficiale ha quasi certamente ToS che vietano scraping ed estrazione massiva |
| Scansioni personali | Restano riproduzioni di opere protette: possedere fisicamente la carta non concede diritti di riproduzione/distribuzione digitale, indipendentemente dalla fonte |
| Le mitigazioni gia previste (sez. 4.2) | **Diventano piu importanti, non meno**: bassa risoluzione, nessun ritaglio, copyright notice intatto, kill-switch per set, procedura di notice-and-takedown. Vanno mantenute come minimo, non come opzionali |
| Direzione futura | L'adozione di **TCGdex per i set nuovi** (M2, da costruire) riduce l'esposizione **andando avanti**: niente piu nuovo scraping del sito ufficiale, solo ingest da un dataset MIT con licenza propria. Lo storico gia acquisito resta com'e, trattato come patrimonio esistente da proteggere con le mitigazioni sopra, non da rifare |

**Nessuna azione distruttiva consigliata sullo storico** (es. rimuoverlo preventivamente): rischierebbe di privare l'app di quasi 20 anni di catalogo per un rischio che le mitigazioni tecniche gia riducono concretamente. La raccomandazione e rinforzare le mitigazioni (specialmente il kill-switch per set e la pagina di takedown, sez. 4.5) prima del rilascio pubblico, non smontare il lavoro fatto.

**Correzione 2026-09-09**: il dettaglio "scraping del sito ufficiale Pokemon" sopra e' stato corretto dall'utente durante la stesura della pagina `docs/copyright/index.html` (sez. 4.5). Fonte reale delle immagini storiche: **scansioni condivise dalla community di collezionisti** (non uno scraping del sito ufficiale) **e immagini scaricate dal catalogo di Pokewallet.io**. Non cambia la conclusione pratica del blocco sopra (restano opere protette indipendentemente dalla fonte, le mitigazioni di sez. 4.2 restano necessarie), ma cambia il profilo di rischio descritto in tabella: niente esposizione diretta verso il sito ufficiale Pokemon Company/Nintendo. La tabella sopra e la sez. 4.2/4.3 sotto restano nel testo cosi' come scritte il 2026-09-06 (record storico di quella sessione), corrette qui invece di riscritte silenziosamente.

### Decisione: ricompressione WebP delle immagini esistenti (confermata dall'utente)

Le 15.539 PNG (245×342px, ~150-200 KB/file, 2,49 GB totali) vengono ricompresse in WebP alla stessa risoluzione. Stima: **2,49 GB -> ~300-400 MB**, nessuna perdita di copertura, stessa qualita visiva percepita.

**Approccio scelto: convivenza .webp/.png, nessun cutover a rischio.**

Il codice esistente (sia Kotlin che Worker) gia usa un pattern "prova candidati in ordine, prendi il primo che esiste" per risolvere le chiavi R2 (vedi `ItalianImageReference.preferredFileName`/`fallbackFileName` in Kotlin, `buildItalianCatalogKeyCandidates`/`getFirstExistingR2Object` nel Worker). La migrazione sfrutta lo stesso pattern:

1. Upload dei nuovi `.webp` **accanto** ai `.png` esistenti (stessa cartella, stesso nome base, estensione diversa) — nessuna cancellazione immediata
2. Le liste di candidati (Kotlin + Worker) vengono estese per provare `.webp` **prima** di `.png`
3. Verifica: l'app continua a funzionare in ogni momento della migrazione (se un `.webp` manca, il `.png` esistente e ancora li)
4. Solo a migrazione confermata completa (tutte le 15.539 carte hanno un `.webp` funzionante), pulizia dei `.png` in un passo separato e reversibile

Questo evita un taglio netto rischioso su un asset di produzione da 2,49 GB usato dall'app in questo momento.

### Cosa manca ancora per proseguire

- **Costruire lo script di ricompressione** — FATTO, vedi checkpoint sotto
- **Validare su un piccolo campione** — FATTO, vedi checkpoint sotto
- **Estendere i due punti di risoluzione chiave** per provare `.webp` prima di `.png` — fatto lato Worker, **lato Kotlin ancora da fare** (vedi checkpoint)
- **Costruire l'automazione per i set futuri** da TCGdex (verificata gratuita, senza rate limit, aggiornata quotidianamente) — resta interamente da fare, ed e il vero cuore del lavoro residuo insieme alla migrazione D1
- **Creare il database D1** e la migration del blob JSON (schema, script di import una tantum dei 15.406 record esistenti)

---

## 📍 CHECKPOINT DI FINE SESSIONE — 2026-09-06, sera

> Sessione interrotta su richiesta esplicita dell'utente ("dopo di questo salviamo il punto e proseguiamo domani"). Tutto quanto sotto e verificato, non ipotizzato: ogni riga e frutto di un comando eseguito con il suo output osservato in questa sessione.

### Ambiente di sviluppo — ora pronto (prima non lo era)

| Cosa | Stato a fine sessione |
|---|---|
| Node.js | **Installato**: v24.19.0 (via winget, `OpenJS.NodeJS.LTS`) |
| npm | 11.17.0 |
| JDK per Android | **JDK 21** in `C:\Users\ASUS\.jdks\jbr-21.0.11` — usare SEMPRE questo per Gradle, MAI il JBR 25 di Android Studio (`C:\Program Files\Android\Android Studio\jbr`), che crasha Kotlin 2.0.21 con `IllegalArgumentException: 25.0.2` |
| `local.properties` | Creato da `local.properties.example` con chiavi vuote (build debug non le richiede) |
| wrangler | Autenticato via `wrangler login` (OAuth), account `emanuelebuia@live.it` = **stesso account del progetto** (`account_id e6c4d1ff864abf6dbcb4ec1e0de6b34a`). Credenziali salvate in `C:\Users\ASUS\AppData\Roaming\xdg.config\.wrangler\config\default.toml` |
| **Attenzione wrangler su Windows** | La versione **locale del progetto (3.114.17, pinnata in `package.json`)** e' instabile su questa macchina: crash nativo intermittente (`Assertion failed: !(handle->flags & UV_HANDLE_CLOSING)`, way `src\win\async.c:94`) su **qualsiasi** comando `r2 object get/put`, sia su successo che su errore. Non e' un problema di concorrenza (fallisce anche con `--concurrency 1`). Gli errori reali (es. "The specified key does not exist") **appaiono comunque in stderr prima del crash**, quindi sono recuperabili, ma serve retry-logic per gli aborti spuri. Da valutare domani: aggiornare a wrangler 4 (`npm install --save-dev wrangler@4`, gia verificato funzionante e piu stabile in questa sessione) oppure tenere v3 con retry robusto |
| Git | `master` locale = `origin/master` = `release/R2.0.21` (v2.0.20), pushato. Build verificata: `./gradlew :app:compileDebugKotlin` -> **BUILD SUCCESSFUL** con JDK 21 |

### Lavoro svolto stasera, non ancora committato

`git status` a fine sessione:
```
 M app/google-services.json                    <- pre-esistente, non nostro, NON toccare senza chiedere
 M pokevault-proxy-worker/.gitignore            <- aggiunto: .recompress-tmp/, recompress-progress.ndjson, catalog-live.json
 M pokevault-proxy-worker/package.json          <- aggiunto sharp@0.33.5 come devDependency esplicita (prima era solo transitiva)
 M pokevault-proxy-worker/src/index.ts          <- vedi sotto: priorita .webp prima di .png
?? pokevault-proxy-worker/scripts/recompress-webp.mjs   <- NUOVO: script di ricompressione PNG->WebP
```

**Nessun commit creato, nessun deploy del Worker eseguito.** Tutte le modifiche sono solo locali, in attesa di revisione.

#### 1. `src/index.ts` — priorita WebP nella risoluzione immagini (2 modifiche, non deployate)

- Riga ~553: `imageExtensions` riordinato da `['png', 'webp', 'jpg', 'jpeg']` a **`['webp', 'png', 'jpg', 'jpeg']`**
- Blocco "Legacy layouts" (~riga 589): le due `pushCandidate(...)` per il pattern reale (`{SET}_IT_{numero}.ext`, quello usato dalle 15.539 immagini esistenti) invertite: **`.webp` provato prima di `.png`**
- Effetto: appena un `.webp` esiste per una carta, il Worker lo serve al posto del `.png` — automaticamente, senza toccare altro codice. Se il `.webp` non esiste ancora, il `.png` resta il fallback (nessuna carta puo "sparire" durante la migrazione)
- **Verificato**: `npm run type-check` (tsc --noEmit) passa senza errori
- **Non deployato**: serve `wrangler deploy` esplicito, da fare solo dopo conferma (tocca il serving in produzione)

#### 2. `scripts/recompress-webp.mjs` — script di ricompressione, VALIDATO funzionante

Legge il catalogo JSON (non fa listing del bucket: `wrangler r2 object list` non esiste come comando), deriva la chiave R2 sorgente/destinazione da ogni `cardId` (che e' letteralmente il nome file, es. `DP1_IT_1.png` -> set `DP1`), scarica il PNG, converte in WebP (qualita 82, sharp), carica il `.webp` **accanto** al `.png` esistente (additivo, nessuna cancellazione), con log di progresso resumibile in `recompress-progress.ndjson`.

**Risultato del test di validazione** (5 carte campione: DP1, DP1, XY1, SM1, SWSH1):
```
DP1_IT_1.png:   201.604 -> 28.622 byte  (-85,8%)
DP1_IT_2.png:   182.392 -> 22.796 byte  (-87,5%)
XY1_IT_1.png:   196.773 -> 25.252 byte  (-87,2%)
SM1_IT_1.png:   154.942 -> 18.114 byte  (-88,3%)
SWSH1_IT_1.png: fallita (crash wrangler, vedi sopra — non un problema della logica)
```
**4 file `.webp` reali sono gia stati caricati su R2** (produzione, bucket `pokevault-images`) come effetto collaterale della validazione: `it/DP1/DP1_IT_1.webp`, `it/DP1/DP1_IT_2.webp`, `it/XY1/XY1_IT_1.webp`, `it/SM1/SM1_IT_1.webp`. Innocuo: sono aggiunte, non hanno toccato i `.png` originali, e il Worker in produzione non li usa ancora (modifica non deployata).

**Risparmio medio confermato: ~87%** — coerente con la stima (85-90%). Su 2,49 GB totali, proiezione: **~320 MB** a conversione completa.

**Problema scoperto, da risolvere domani**: le espansioni **promozionali** (`svp`, `mep`, e probabilmente le altre con suffisso "p": `bwp`, `pgo`, `sma`, `smp`, `swshp`, `xya`, `xyp`) **non hanno la chiave R2 nel pattern standard** dedotto dal `cardId` — `wrangler r2 object get` risponde "The specified key does not exist" per queste. Vanno investigate separatamente (probabile naming diverso per i promo, da scoprire ispezionando come il Worker le risolve oggi in produzione, dato che l'app le mostra correttamente).

### Prossimi passi per la sessione di domani, in ordine

1. **Decidere su wrangler**: aggiornare a v4 (piu stabile, gia testato in questa sessione) o tenere v3 con retry. Consigliato: aggiornare, e ri-testare `npm run type-check` + un giro di validazione immagini per conferma.
2. **Investigare la chiave R2 reale dei set promozionali** (svp, mep, ...) — probabilmente basta ispezionare 2-3 oggetti candidati o guardare come il Worker li serve oggi (endpoint `/images/it/SVP/1?size=low` in produzione, che gia funziona per l'app).
3. **Aggiungere retry logic** allo script (pattern gia presente in `upload-ita-r2.ps1`: retry su errori transitori, non su "key not found" che e' definitivo).
4. **Lanciare la conversione completa** (15.406 carte, esclusi promo se non ancora risolti) in background — stimata alcune ore data la CLI overhead. Monitorare via `recompress-progress.ndjson` (resumibile, si puo interrompere e riprendere).
5. **Aggiornare `ItalianImageReference.kt`** (Kotlin) con lo stesso ordine di priorita `.webp` prima di `.png` — oggi prova solo `.png`, va allineato al Worker per coerenza (anche se il Worker e' l'unico che risolve davvero le chiavi R2 lato server; il client chiama solo l'URL del Worker, quindi questa modifica potrebbe non essere strettamente necessaria — **da verificare leggendo come il client costruisce l'URL finale prima di modificare**).
6. **Solo dopo conferma esplicita**: `wrangler deploy` del Worker con le modifiche a `index.ts`.
7. Poi proseguire con M1 (creazione D1) come da roadmap originale.

### File di lavoro lasciati sul disco (non in git)

- `pokevault-proxy-worker/scripts/recompress-progress.ndjson` — log di 5 tentativi (4 ok, 1 fallito), riprendibile domani
- `pokevault-proxy-worker/node_modules/` — dipendenze installate (`npm install` gia eseguito, incluso `sharp` con script approvati)

---

## 📍 CHECKPOINT — 2026-09-08 (M4: fine del fetch "tutto il catalogo ogni volta")

Ripresa dal checkpoint del 7/9 sera, che lasciava aperto esattamente questo: "cambiare il pattern di fetch dell'app da 'scarica tutto il catalogo' a 'carica per espansione'". Fatto in due passi, entrambi committati e deployati in produzione, verificati end-to-end su dispositivo reale (non solo `curl`).

### 1. Dettaglio set (`c03799e`) — `/v1/expansions/{id}/cards`

L'endpoint esisteva gia' (dal 7/9) ma con nomi di colonna D1 grezzi (`card_number`, `regola_speciale`, ...), mai realmente consumato dal client. Portato allo stesso shape di `/ita/catalog.json` (`cardId`, `espansioneId`, `attacchi` come array, ecc.) tramite un mapper condiviso `mapCardRow()`, e aggiunto il filtro `published = 1` che gia' aveva `/ita/catalog.json` ma mancava qui.

Lato Android, `getCardsByItalianSet()` (il path attivato ogni volta che si apre il dettaglio di un set ITA) ora prova prima `ItalianCatalogRemoteRepository.getExpansionCards()` — **~40 KB invece di ~4,6 MB** per aprire un set — e ricade sul catalogo completo solo se quel fetch fallisce. Nessuna regressione possibile per costruzione.

### 2. Lista Pokedex (`f4c2da9`) — `/v1/expansions` + `base_set_code` precalcolato

Problema diverso: `mergeItalianSets()` (che costruisce la lista set del Pokedex) non usava le carte di *un* set ma doveva scandire le carte di *ogni* espansione per calcolarne il "codice set inglese dominante" (serve per agganciare logo/serie/data uscita al set base ENG) — quindi non bastava spostare la chiamata su un endpoint per-espansione, serviva **precalcolare quel valore lato server**.

- `schema/003_add_base_set_code.sql`: nuova colonna `expansions.base_set_code`, popolata con una query D1 (CTE + window function) che replica esattamente la logica Kotlin (moda del prefisso set estratto da `card_id` prima di `_IT_`, per espansione). Validata prima su dati di test in locale, poi applicata in produzione: **107/107 espansioni popolate, 0 NULL**.
- Worker: `/v1/expansions` la espone (rimappata in camelCase, stesso principio di `mapCardRow`).
- Android: `mergeItalianSets()` usa `/v1/expansions` (poche KB per tutte le 107 espansioni) come fast path; la logica di risoluzione set-base/logo/serie e' stata estratta in `buildItalianTcgSet()` e condivisa col fallback (catalogo completo, invariato) cosi' il comportamento resta identico in entrambi i casi.

**Bug trovato e corretto prima del deploy definitivo**: il primo deploy di `/v1/expansions` restituiva i nomi colonna D1 grezzi (snake_case) invece di camelCase — Gson lato Android avrebbe silenziosamente prodotto zeri/null su tutti i campi (nessun crash, solo dati sbagliati). Preso solo perche' verificato manualmente il payload con `curl` prima di ricompilare l'app, non dal type-check (che non poteva saperlo). Corretto con lo stesso mapper esplicito gia' usato per le carte.

### Ambiente di test locale, ora impostato

Per verificare modifiche al Worker **prima** del deploy in produzione senza passare da PokeWallet/prod: `wrangler dev --remote --ip 0.0.0.0` sul PC (dati D1/R2 reali, solo letture, nessun impatto su prod) + un piccolo server Node (`apk-server.mjs`, nello scratchpad di sessione) che serve l'ultimo APK debug compilato su `http://192.168.1.13:8081/` — il telefono scarica e installa da li' via browser, niente cavo/ADB. Aggiunta anche `app/src/debug/res/xml/network_security_config_debug.xml` (solo build debug, mai release) per permettere all'app di parlare in HTTP con un Worker locale in LAN durante i test.

**Nota operativa**: il debug via USB/ADB e il debug wireless di Android Studio si sono rivelati inaffidabili su questa macchina (interfaccia USB sana ma mai autorizzata, build Studio-deployate che non rileggevano `local.properties` nonostante Invalidate Caches + clean). Il metodo file-over-WiFi sopra e' quello che funziona in modo consistente ed e' da preferire per i test futuri.

### Cosa resta aperto per M4

- `getItalianOverlayCards()` e le funzioni di ricerca/scanner (`searchItalianCardsByName`, `searchItalianScannerCandidates`, `searchItalianCardsByNumber`) restano sul catalogo completo: richiedono match cross-espansione senza un set noto a priori, quindi il fetch per-espansione non si applica. Un endpoint di ricerca server-side (`/v1/search?q=`) risolverebbe anche questi, ma e' lavoro nuovo, non un porting — da valutare come step separato.
- Con questi due cambi, il traffico dell'uso quotidiano (aprire il Pokedex, aprire un set) non tocca piu' il blob da 4,6 MB. Il blob resta necessario solo per ricerca globale e scanner.

---

## M4.5 — Catalogo ITA-only "pulito" + isolamento budget PokeWallet + fix ricerca/scanner (NUOVO, aggiunto 2026-09-08)

> Richiesta esplicita dell'utente: ripartire da una base pulita dove l'app mostra **solo carte ITA** dal nostro D1, PokeWallet resta **esclusivamente** sorgente prezzi (il budget richieste e' scarso e va riservato a quello), e si sistemano lentezza/rarita' mancante sulle carte ITA insieme alla qualita' di ricerca testuale e scanner.

### Cosa e' gia' cosi' (verificato oggi, non serve rifare)

- **Il Pokedex ha gia' un filtro lingua che di default e' ITA**: `SetsViewModel.kt` — `selectedLanguageMacro = "ITA"` di default, con switcher ITA/ENG/JAP/CHN (`languageMacros`, `buildMacroGroups()`). Il "solo ITA" nel Pokedex e' quindi in gran parte gia' li' come default; il pezzo mancante e' semmai decidere se **nascondere del tutto** lo switcher verso ENG/JAP/CHN o lasciarlo (vedi decisione da prendere sotto) — coerente con la decisione gia' presa il 6/9 ("Scope v1: Solo italiano") ma mai realmente chiusa a livello di UI.
- La pipeline prezzi bulk (`buildItalianPriceSnapshot()`, cron) e' gia' quella "giusta" per il budget (sez. 2.3 del piano originale): non chiama PokeWallet per singola carta per i prezzi.

### Il problema reale sul budget PokeWallet: non e' solo prezzi

`toItalianTcgCard()` (in `PokeTcgRepository.kt`) oggi arricchisce ogni carta ITA con `rarity`, `supertype`, `subtypes` **presi a runtime dalla carta base ENG corrispondente** (via `loadStandardCardsForSet`/`getEnglishBaseCardForItalianOverlay`, che a loro volta possono innescare chiamate PokeWallet per risolvere il set base inglese). Questo consuma budget PokeWallet **non per i prezzi** ma per metadati — e se il matching fallisce (numero non allineato, set base non risolto), la carta ITA resta **senza rarita' visibile**, che e' esattamente il sintomo "rarita'" segnalato dall'utente.

**Causa strutturale**: lo schema D1 (`cards`) non ha affatto una colonna `rarity` (ne' `supertype`/`subtypes`) — verificato in `schema/001_init.sql` e nello script di ingest TCGdex (`scripts/ingest-tcgdex-set.mjs`, che oggi non legge/scrive rarita'). Il dato non e' mai stato nostro: viene sempre "preso in prestito" da PokeWallet ad ogni caricamento.

**Direzione proposta** (da confermare prima di implementare): portare `rarity` **dentro il nostro dataset**, cosi' da eliminare la dipendenza runtime da PokeWallet per questo dato:
1. Aggiungere `rarity` (e valutare `supertype`/`subtypes` se utili alla UI) come colonne in `cards`.
2. Per i set futuri: `ingest-tcgdex-set.mjs` gia' chiama l'API dettaglio carta TCGdex, che espone `rarity` — basta leggerlo e scriverlo, nessuna chiamata in piu'.
3. Per lo storico (15.526 carte gia' in D1): serve una fonte per il backfill one-shot. Candidati da valutare: (a) un unico giro di matching PokeWallet fatto **una volta sola** invece che ad ogni apertura carta (stesso risultato finale, budget speso una volta e non ad ogni utente/apertura), (b) TCGdex stesso se copre anche lo storico in altre lingue con lo stesso numero/set, da incrociare per numero+set. Da verificare quale sia piu' completa prima di scegliere.

### Nascondere ENG/JAP/CHN dall'esperienza utente

Da decidere col dettaglio: rimuovere del tutto lo switcher lingua dal Pokedex (nessun accesso a set ENG/JAP/CHN), o lasciarlo ma **disattivato/nascosto per ora** (piu' reversibile, coerente con lo stile "flag, mai un taglio netto" gia' usato nel resto del piano). Riguarda anche: ricerca (`searchCards`/ricerca generica oggi non risulta scoperta per lingua — da verificare) e Scanner (`ScannerViewModel.kt` + `searchItalianScannerCandidates`, che e' gia' ITA-only lato dati ma va verificato lato UI/flusso).

**Nota importante**: gli utenti hanno gia' collezioni/carte salvate che potrebbero includere carte ENG/JAP prese prima di oggi (Firestore, `users/{uid}/...`). Nascondere la sfoglia ENG/JAP non deve rompere la visualizzazione di carte gia' possedute — va verificato in Collection/Album/WishList/DeckLab separatamente dal Pokedex/ricerca/scanner "di scoperta".

### Fix ricerca testuale e scanner — segnalato come rotto oggi, non solo da riorganizzare

L'utente riporta che la ricerca (testo) e lo scanner **non trovano bene le carte** oggi, indipendentemente dal tema ITA-only. Questo e' un bug/qualita' da diagnosticare con casi concreti (non ipotizzabile a tavolino) prima di cambiare euristiche di matching in `searchItalianCardsByName`, `searchItalianScannerCandidates`, `searchItalianCardsByNumber`, `scoreItalianNameMatch`, `scannerNameScore` — serve raccogliere esempi reali di ricerche che falliscono (nome cercato, cosa ci si aspettava, cosa e' uscito) prima di intervenire.

### Ordine di lavoro proposto (proposta, da confermare)

1. **Ricerca e scanner**: diagnosi con casi concreti + fix — e' l'unico dei quattro filoni gia' segnalato come rotto (non solo da migliorare), quindi il piu' urgente.
2. **Rarita' in D1**: elimina sia il sintomo (rarita' mancante) sia la causa strutturale (consumo budget PokeWallet non-prezzo) in un colpo solo.
3. **Nascondere ENG/JAP/CHN**: cambio piu' "meccanico" una volta chiari i punti 1-2, e quello con piu' superficie UI da testare (Pokedex, ricerca, scanner, collection).
4. **Verifica lentezza residua**: da ripetere a valle di 1-3, perche' buona parte della lentezza percepita puo' gia' venire dalle chiamate ENG-per-carta eliminate al punto 2.

**Aggiornamento 2026-09-08 (dopo il fix ricerca)**: primo pezzo del punto 1 fatto e verificato su dispositivo — `SetsViewModel.searchCardsByName()` non chiama piu' `repository.searchCards()`/traduzione IT->EN, cerca solo tra le carte ITA. Risultato confermato dall'utente: la ricerca testuale ora restituisce solo risultati ITA e trova le carte correttamente. Lo scanner era gia' ITA-only (verificato, non modificato). Resta aperto il punto 2 (rarita' in D1) e il punto 3 (nascondere ENG/JAP/CHN) — vedi M4.6 sotto, che estende l'obiettivo a una rimozione completa, non solo un default nascosto.

---

## M4.6 — Rimozione completa del catalogo carte/immagini PokeWallet (NUOVO, aggiunto 2026-09-08)

> Richiesta esplicita dell'utente, che estende M4.5: non solo *nascondere* ENG/JAP/CHN di default, ma **cancellare** il codice che sfoglia/mostra il catalogo carte e immagini di PokeWallet, ripartendo da una base pulita con **solo carte ITA dal nostro D1** nel Pokedex. Motivazione dell'utente: "abbiamo troppo codice confusionario delle rarita' [e] organizzazione delle espansioni" — ora che il D1 e' una base dati coerente (M1-M4.5), il codice di compatibilita' con PokeWallet-come-catalogo e' debito puro, non piu' necessario.

### Cosa resta (non tocca questa milestone)

- **Prezzi**: gia' isolati oggi. `ItalianPriceSnapshotRepository` legge `/ita/prices.json` dal nostro Worker; l'app non chiama mai PokeWallet direttamente per i prezzi. PokeWallet resta dietro il Worker esattamente come da sez. 2.3 del piano originale — questa milestone non lo tocca.
- **Le collezioni utente gia' esistenti**: verificato in `data/model/PokemonCard.kt` — `imageUrl`, `rarity`, `name`, `supertype`, `subtypes` sono salvati come **snapshot su Firestore al momento dell'aggiunta della carta**, non come riferimenti live al catalogo. Cancellare il codice di sfoglia PokeWallet **non fa sparire ne' rompe visivamente nessuna carta gia' posseduta** in Collection/Album/WishList.

### Cosa va rimosso (analisi di oggi, `PokeTcgRepository.kt` e dintorni)

| Area | Cosa | Note |
|---|---|---|
| Sfoglia catalogo | `getSets()`/`getCardsBySet()` percorso non-ITA, `fetchAllCardsForSet`, Room `SetEntity`/`CardEntity` + DAO, `SetsSyncWorker`/`CardsSyncWorker` (WorkManager) | Cache di un catalogo che non si vuole piu' mostrare |
| Ricerca/matching | `searchCards`, `searchCardsFuzzy`, `performGenericSearch`, `performPreciseNumberSearch`, `performSetNumberSearch`, `performApiSearch`, `performAdaptiveApiSearch`, `searchCardsFromLocalCache`, `rankSearchResults` e le funzioni di query-building associate (~15 funzioni) | Gran parte del "codice confusionario" citato dall'utente |
| Glue ITA->ENG | `getEnglishBaseCardForItalianOverlay`, `resolveEnglishBaseSetIdForItalianSet`, `loadStandardCardsForSet`, `resolveItalianCardRarity` | **Bloccante**: va rimossa solo dopo che rarity/supertype/subtypes vivono in D1 (M4.5 punto 2), altrimenti questi campi spariscono per tutte le carte ITA |
| Immagini/URL | `buildCardImageUrl`/`buildSetImageUrl` per PokeWallet diretto | Le immagini ITA sono gia' self-hosted via Worker/R2 |
| Config/wiring | `PokeWalletApiService`/`PokeWalletRetrofitClient` (client Retrofit diretto), `POKEWALLET_API_KEY`/`POKETCG_API_KEY` in `local.properties`/`BuildConfig` | Coerente con l'item 4 gia' presente in sez. 8 (bugfix M7): "rimuovere le key dal client" |

**Nota su `PokeWalletRetrofitClient.imageBaseUrl`**: non e' solo "roba PokeWallet" — e' anche la base URL del nostro Worker, gia' riusata oggi per `/v1/expansions` e `/v1/expansions/{id}/cards` (vedi checkpoint M4 sopra). Va **rinominato/isolato concettualmente** (es. `PokeVaultApiClient`), non cancellato — il binding verso il Worker resta, cambia solo il fatto che non serve piu' anche per chiamare PokeWallet direttamente.

### Il vincolo che decide l'ordine: DeckLab e Album Obiettivo dipendono anche loro dalla ricerca PokeWallet

`DeckLabViewModel.searchCardsInSets()` (→ `searchCardsFuzzy`) e le schermate Album Obiettivo (`GoalAlbumViewModel`, `CreateGoalAlbumScreen`, `GoalAlbumDetailScreen`, via `searchCards` con query tipo `rarity:"..."`/`supertype:"..."`) usano la ricerca sul catalogo PokeWallet per funzioni proprie (suggerimenti mazzo, ricerca carte per obiettivo). **Vanno ripuntate su una ricerca ITA-only prima di cancellare le funzioni PokeWallet corrispondenti**, altrimenti quelle due feature si rompono. Le query per `rarity:`/`supertype:` in particolare presuppongono che quei campi esistano e siano interrogabili — altro motivo per cui servono prima in D1 (vedi punto sotto).

### Sequenza proposta (dipendenze, non solo priorita')

1. **Rarita' (+ supertype/subtypes se serve) dentro D1** — gia' identificato in M4.5 come prossimo passo naturale, ora e' anche un **prerequisito bloccante** per tutto il resto di questa milestone, non solo un miglioramento isolato.
2. **Ripuntare DeckLab e Album Obiettivo sulla ricerca ITA** (una volta che rarity/supertype sono interrogabili in D1, si puo' costruire una ricerca ITA che copre anche i loro casi d'uso, es. filtro per rarita').
3. **Nascondere ENG/JAP/CHN dal Pokedex** (gia' pianificato in M4.5) — a questo punto e' un passo sicuro perche' nessuna feature attiva dipende piu' dal catalogo PokeWallet.
4. **Cancellazione del codice**: sfoglia/ricerca/matching PokeWallet, Room `SetEntity`/`CardEntity`/DAO, `SetsSyncWorker`/`CardsSyncWorker`, glue ITA->ENG, key dal client. Ultimo passo, quando 1-3 sono verificati stabili.
5. **Rinomina finale** (coerente con sez. 2.4 del piano originale): `PokeTcgRepository` -> nome che non menzioni piu' ne' PokeWallet ne' TCG generico (es. `CatalogRepository`), `PokeWalletRetrofitClient` -> `PokeVaultApiClient`.

**Perche' questo ordine e non "cancella e poi aggiusta quello che si rompe"**: DeckLab e Album Obiettivo sono feature attive con dati utente reali (mazzi salvati, obiettivi in corso) — romperle anche temporaneamente e' un costo evitabile pianificando la sequenza al contrario (prima le fondamenta dati, poi i consumatori, poi la cancellazione).

### Rarita' in D1 — fonte scelta e mappatura completa validata (2026-09-08)

Richiesta esplicita dell'utente: rarita' **precisa** (non una classificazione approssimata a fasce), ma **senza dipendenza a runtime** — importata una volta da una fonte esterna e resa nostra per sempre in D1, esattamente come il resto del catalogo.

**Verificato**: TCGdex (`api.tcgdex.net/v2/en/cards/{setId}-{localId}`) espone `rarity` con precisione ("Rare Holo", "Ultra Rare", "Common", ecc.) e in piu' `category` (utile per `supertype`). Confermato su carte reali: `dp1-1` (Dialga, storico) -> `"Rare Holo"`; `swsh10tg-TG22` -> `"Ultra Rare"`.

**Copertura verificata set per set: 107/107**. La maggior parte dei nostri id espansione coincide direttamente con l'id TCGdex. I 12 casi che non coincidevano sono stati risolti cercando per nome nell'elenco completo set di TCGdex (`GET /v2/en/sets`, ~35.000 set):

| Nostro id | Id TCGdex | Nome set |
|---|---|---|
| `sv3pt5` | `sv03.5` | Pokémon 151 |
| `sv4pt5` | `sv04.5` | Paldean Fates |
| `sv6pt5` | `sv06.5` | Shrouded Fable |
| `sv8pt5` | `sv08.5` | Prismatic Evolutions |
| `zsv10pt5` | `sv10.5w` | White Flare |
| `rsv10pt5` | `sv10.5b` | Black Bolt |
| `swsh45sv` | `swsh4.5sv` | Shining Fates Shiny Vault |
| `pgo` | `swsh10.5` | Pokémon GO |
| `cel25c` | `cel25cc` | Celebrations Classic Collection |
| `det` | `det1` | Detective Pikachu |
| `sm35` | `sm3.5` | Shining Legends |
| `sm75` | `sm7.5` | Dragon Majesty |
| `swsh12pt5` | `swsh12.5` | Crown Zenith |
| `swsh12pt5gg` | `swsh12.5gg` | Crown Zenith Galarian Gallery |

Tutti gli altri ~93 id coincidono 1:1 (es. `me03`, `me04`, `svp`, `mep`, `xyp`, `swsh9tg`, `swsh10tg`, `cel25`, `col1`, `g1` risultano validi cosi' come sono).

**Piano di esecuzione**:
1. `schema/004_add_rarity.sql`: nuova colonna `cards.rarity` (e valutare `cards.category`/supertype).
2. Script di backfill one-shot (analogo a `ingest-tcgdex-set.mjs` ma per rarita' su tutto lo storico): per ognuno dei 107 set, scarica la lista carte TCGdex (usando l'id corretto dalla tabella sopra dove serve), fa match per `card_number`/`localId` (attenzione ai formati diversi: numerico semplice, zero-paddato a 3 cifre, o con prefisso `SV`/`TG`/`GG`/`CC` — gia' visti nei dati reali sopra), scrive `rarity` in D1.
3. Worker: aggiungere `rarity` alle risposte di `/v1/expansions/{id}/cards`, `/ita/catalog.json` (via `mapCardRow`), `/v1/cards/{id}`.
4. Android: `ItalianCardRecord` guadagna il campo `rarity`; `toItalianTcgCard()` lo usa direttamente invece di richiamare `resolveItalianCardRarity()`/`loadStandardCardsForSet()` per prenderlo in prestito da PokeWallet — primo pezzo concreto di rimozione della glue ITA->ENG (M4.6).
5. Una volta verificato stabile: rimuovere `resolveItalianCardRarity`, e valutare se `getEnglishBaseCardForItalianOverlay`/`loadStandardCardsForSet` restano necessarie per altro (supertype/subtypes, se non ancora coperti) o possono sparire del tutto.

### ✅ Fatto e deployato (2026-09-08, stessa sessione)

Tutti i 5 punti sopra completati, committati (`a9e58c1`) e deployati in produzione. Risultato reale del backfill: **15.488/15.526 carte (99,76%)**, non solo i 107/107 set stimati in fase di analisi — 3 set (`me2pt5`, `swsh35`, `swsh45`) avevano un id TCGdex sbagliato nella prima stesura della mappa, scoperti dal riepilogo del giro completo e corretti con un secondo run mirato (100% su tutti e 3). I 38 residui senza rarita' sono sparsi su poche espansioni minori (l'unico set con numerazione carte anomala + qualche promo non coperta da TCGdex) — non bloccanti.

`toItalianTcgCard()` ora usa `record.rarity` (da D1) come sorgente primaria, con fallback al prestito dalla carta base ENG solo per residui non coperti — verificato in produzione via `curl` e **su dispositivo reale, confermato dall'utente** ("perfetto mostra la rarita'"). Suite di test completa verde dopo la modifica.

**Prossimo passo naturale in M4.6**: rimuovere `resolveItalianCardRarity()` (ora ridondante nella stragrande maggioranza dei casi) e valutare se la glue ITA->ENG (`getEnglishBaseCardForItalianOverlay`, `loadStandardCardsForSet`) serve ancora per altro (supertype/subtypes) prima di procedere al punto 2 della sequenza M4.6 (ripuntare DeckLab/Album Obiettivo sulla ricerca ITA).

### Pokedex "confusionario" — segnalazione utente 2026-09-08, stessa causa architetturale

Dopo il fix rarita', l'utente ha chiesto di sistemare il Pokedex: espansioni ordinate/raggruppate male e che "si muovono" durante lo scroll, loghi assenti su parecchie espansioni, Buio Pesto (me05) apparentemente non presente. Analisi: **stessa identica causa strutturale della rarita'** — data di uscita e logo dei set ITA venivano anche loro "presi in prestito" dal set base ENG a runtime, con lo stesso tasso di fallimento.

- **Buio Pesto**: verificato `published=1` in D1, presente in `/v1/expansions` — non e' un bug del catalogo. Ipotesi piu' probabile: build testata prima del fix data di uscita, con date quasi tutte vuote non c'era un ordine affidabile. Da riconfermare con l'utente sulla build corrente (ora dovrebbe comparire per primo, essendo il piu' recente: 2026-07-17).
- **Ordinamento/"si muovono durante lo scroll"**: il comparatore che ordina per data DESC (`setDisplayComparator`, gia' corretto) semplicemente non aveva quasi mai un dato reale da ordinare. Risolto dalla stessa `release_date` backfill del punto sotto — nessuna modifica UI necessaria.
- **Loghi assenti**: causa identica (linking runtime a un set ENG fragile). **Non serviva pero' nessuna nuova infrastruttura**: il Worker gia' sapeva servire `GET /sets/{CODICE}/image` cercando prima in R2 (`buildItalianSetLogoCandidates`/`handleItalianR2AssetRequest`, gia' scritto, mai popolato) prima di ricadere su PokeWallet. Bastava caricare i file.

**Fatto, in un'unica sessione**:
1. `expansions.release_date` (colonna gia' esistente da schema/002, popolata solo per me05) backfillata per **tutte le 107 espansioni** da TCGdex (`scripts/backfill-release-date-tcgdex.mjs`) — piu' semplice della rarita', un solo campo per SET non per carta, 107/107 al primo giro. `/v1/expansions` la espone; `buildItalianTcgSet()` la usa come sorgente primaria.
2. Loghi set: `scripts/backfill-set-logos-tcgdex.mjs` scarica il logo TCGdex di ogni espansione e lo carica su R2 sotto lo stesso `SETCODE` che il client gia' costruisce (`base_set_code` di schema/003, o l'override manuale `preferredBaseSetCodeForItalianExpansion` quando presente — la mappa e' duplicata nello script perche' e' Kotlin-only, con nota per tenerle sincronizzate). **92/107 caricati**; i 15 mancanti sono quasi tutti sottocollezioni senza logo distinto su TCGdex (Trainer Gallery, Shiny Vault, promo) — restano sul fallback PokeWallet esistente, comportamento invariato rispetto a prima, nessuna regressione. Un solo vero buco isolato: `sv05` (Temporal Forces), TCGdex non ha il campo `logo` per quel set specifico.
3. **Purge KV mirato** delle 92 chiavi cache (`pokewallet:/sets/{CODICE}/image`) subito dopo l'upload — stessa lezione del bugfix SWSH45 di stamattina, senza purge il fix sarebbe rimasto invisibile fino a scadenza naturale della cache (fino a 90 giorni).
4. Estratta `scripts/lib/tcgdex-set-id-map.mjs`: la mappa id-nostro -> id-TCGdex (17 casi divergenti) ora e' condivisa tra i tre script di backfill (rarita', data, loghi), non puo' piu' derivare.

**Nessuna modifica Android necessaria per i loghi** (il codice che costruisce l'URL esisteva gia'); per la data di uscita si', committata insieme a rarita' in precedenza. Deploy Worker + D1 fatti, verificati in produzione via `curl`. Test unitari verdi.

**Da riverificare con l'utente sulla build corrente**: Buio Pesto in prima posizione, stabilita' dell'ordine durante lo scroll, presenza dei loghi (92/107 attesi, gli altri restano sul fallback come prima).

### ✅ Rifacimento completato + 2 bug scoperti e corretti sul dispositivo (2026-09-08, stessa sessione)

L'utente ha approvato un piano dedicato (vedi analisi sopra) ed e' stato eseguito interamente: tab lingua rimossi, chip serie sostituite da sezioni comprimibili ordinate per data, `hasPrioritizedLogo` rimosso dai criteri di ordinamento/filtro, filtri rarita'/tipo aggiunti alla ricerca carte. Committato in `d2fe722` insieme a due bug reali scoperti testando la build sul telefono, stessa causa architetturale (dato preso in prestito da PokeWallet a runtime invece che nostro in D1):

1. **Nomi set giapponesi/cinesi mischiati a carte italiane**: `linkedBase?.name` (set PokeWallet collegato) poteva risolvere a un set JAP/CHN quando non esisteva un match ENG per quel raw set code — la lingua non era mai stata garantita ITA/ENG. Fix: `expansions.name` da TCGdex locale IT (107/107, es. "Buio Pesto" per me05 — verificato identico a come l'utente stesso chiama il set).
2. **Colori/etichette rarita' sbagliati su alcune carte**: la mappatura (`RarityUtils.kt`) era tarata sul vocabolario PokeWallet ("Rare Holo") e non riconosceva quello reale di TCGdex ("Holo Rare", ordine invertito) ne' "Secret Rare" (577 carte — il piu' grande buco), "LEGEND", "Rare PRIME", "Radiant Rare", "Amazing Rare", "Black White Rare". Diagnosticato interrogando i valori distinti REALMENTE presenti in D1 (`SELECT DISTINCT rarity, COUNT(*) ...`), non ipotizzato — ~1200 carte coinvolte.

**Metodo confermato efficace**: ogni volta che un problema di visualizzazione ITA riemerge, la causa e' quasi sempre la stessa — qualcosa preso in prestito da PokeWallet a runtime invece che backfillato una volta da TCGdex in D1. Prima di patchare a vista, verificare cosa c'e' davvero nei dati (`SELECT DISTINCT ...`) invece di ipotizzare.

### Richiesta 2026-09-08 (dopo il rifacimento): rinnovamento visivo + ricerca carte piu' avanzata

L'utente ha chiesto, sulla stessa sezione: scritte/animazioni piu' moderne senza perdere velocita', e una ricerca carte con **piu' filtri** e **visualizzazione diversa**, "fatta bene per non incasinare la UI" — con delega esplicita ("puoi rifare la sezione come vuoi").

**Fatto e committato (`3bf8b75`)**: filtri (rarita'/tipo/categoria/sottotipo — copre esplicitamente "Strumento" richiesto dall'utente) raccolti in un unico pannello (`ModalBottomSheet`, pulsante "Filtri (N)" con badge conteggio) invece di righe di chip sempre visibili, cosi' la barra di ricerca resta pulita anche con 4 dimensioni di filtro. Aggiunto un toggle griglia/lista (`CardViewMode`) e badge rarita'/prezzo direttamente sulle card. Nuova mappa `subtypeEnToIt`/`translateSubtype` in `AppLocale.kt` (mancava del tutto). Nessuna chiamata di rete in piu' — i filtri operano sui risultati gia' scaricati, caching/budget prezzi invariati come richiesto esplicitamente dall'utente.

### ⚠️ Non ancora verificato su dispositivo — causa trovata, non e' un problema del codice

L'utente non e' riuscito a vedere ne' i fix nomi/rarita' ne' il rifacimento ricerca sul telefono nonostante build compilate correttamente, deploy Worker verificati via `curl`, e server WiFi locale (`http://192.168.1.13:8081/`) confermato servire il file giusto e aggiornato (timestamp verificato ad ogni giro). Causa piu' probabile, **non risolta con certezza in questa sessione**: il telefono/browser riapriva un `app-debug.apk` gia' scaricato in precedenza (stesso nome file ad ogni build) invece di riscaricare quello nuovo — un problema del **flusso di consegna locale**, non delle modifiche app/Worker/D1 (tutte verificate funzionanti server-side).

**Mitigazione applicata, da verificare alla ripresa**: `apk-server.mjs` (script nello scratchpad di sessione, non nel repo) ora genera un nome file diverso ad ogni build (`app-debug-HHMMSS.apk`, calcolato dal mtime dell'APK) invece del fisso `app-debug.apk`, oltre alle intestazioni anti-cache HTTP gia' aggiunte in precedenza. Il server e' stato riavviato con questa versione e lasciato attivo, ma **lo script vive solo nello scratchpad di sessione — se la sessione/il processo Node termina, va ricreato da zero** (il contenuto e' comunque conservato in questa conversazione se serve rigenerarlo).

**Primo passo della prossima sessione**: verificare che il nuovo nome-file-per-build risolva davvero il problema (chiedere all'utente di cancellare ogni `app-debug*.apk` vecchio da Download sul telefono prima di riprovare, per eliminare ogni ambiguita' residua). Se il problema persiste anche cosi', il flusso WiFi va rivisto (es. servire via `adb install` se nel frattempo si risolve il driver USB, o un altro meccanismo di consegna) prima di poter continuare a verificare qualsiasi modifica UI.

**Nota**: l'utente ha menzionato la possibilita' di dare comandi da remoto durante la giornata — se la sessione riprende in quel modo, il primo comando utile e' probabilmente riavviare/verificare il server WiFi (vedi sopra) o testare direttamente l'installazione con il nuovo schema di nome file.

---

## 📍 CHECKPOINT — 2026-09-08, notte (M4.6 punto 2 completato: DeckLab e Album Obiettivo staccati da PokeWallet + rifacimento DeckLab)

Sessione ripresa dal checkpoint sopra: nome-file-per-build confermato dall'utente funzionante ("ok già va bene"), ricerca carte in DeckLab confermata funzionante dopo il fix del punto 1. Poi due richieste nuove nella stessa sessione, entrambe completate.

### Bug reale trovato e risolto: import decklist senza immagini

L'utente ha segnalato che l'aggiunta automatica delle carte mancanti dopo un import (testo o Meta Deck) mostrava carte senza immagine. **Causa verificata con una chiamata diretta al Worker in produzione**, non ipotizzata:

```
curl "https://pokevault-proxy.pokevault-emanu.workers.dev/search?q=Pikachu"
-> {"error":"Rate limit exceeded","message":"Daily limit exceeded","limits":{"daily":{"limit":1000,"used":1317,"remaining":0}}}
```

Il budget giornaliero PokeWallet (1000 richieste/giorno) era esaurito. `lookupAndCreateCard()` (DeckLabViewModel) cercava le carte mancanti su PokeWallet per nome+set+numero: con budget a zero la ricerca falliva sempre, la carta veniva creata con dati minimi e **nessuna immagine**.

### M4.6 punto 2 — completato: DeckLab e Album Obiettivo non chiamano piu' PokeWallet

Estesa `PokeTcgRepository` con due funzioni nuove, entrambe operanti solo sul catalogo ITA gia' in cache locale (nessuna chiamata di rete oltre al fetch del catalogo stesso, gia' esistente):
- `searchItalianCardsByAttribute(RARITY|SUPERTYPE|TYPE, valore, context)` — sostituisce le vecchie query `searchCards("rarity:\"...\"")` ecc.
- `findExactItalianCard(setCode, number, context)` — matcha per set+numero (indipendente dalla lingua, funziona anche su decklist in inglese) invece che per nome.

Applicate a:
- **DeckLab**: `lookupAndCreateCard` (aggiunta automatica carte mancanti) ora usa solo `findExactItalianCard` — **rimosso del tutto** il fallback PokeWallet (`searchPokewalletCard` e la sua cache dedicata `tcgLookupCache`/`CachedLookup`/`lookupKey`, ~50 righe di codice morto cancellate). Se una carta non e' nel nostro D1 (raro, ~15.500 carte coperte su 107 espansioni), resta con dati minimi e nessuna immagine, ma non tenta piu' PokeWallet.
- **`resolveBestPrice`**: le carte ITA leggono il prezzo dallo snapshot precalcolato (`/ita/prices.json`, gia' costruito lato Worker dal cron prezzi) invece di una chiamata PokeWallet diretta per carta. Vale sia per l'aggiunta automatica sia per l'aggiunta manuale dalla ricerca (`searchCardsInSets`, gia' passata al catalogo ITA nel fix precedente della stessa sessione).
- **Album Obiettivo** (`GoalAlbumViewModel`, `CreateGoalAlbumScreen`, `GoalAlbumDetailScreen`): stessi rami ripuntati. **Scoperta collaterale**: i criteri RARITY/SUPERTYPE/TYPE/CUSTOM per gli Album Obiettivo sono codice morto lato UI — `CreateGoalAlbumScreen` forza sempre `GoalCriteriaType.SET` alla creazione, `CustomCardSearch` e' definita ma mai chiamata. Ripuntati comunque (zero rischio, non eseguiti oggi) per coerenza e per sbloccare la futura cancellazione del codice PokeWallet (M4.6 punto 4).

Commit: `f2eea29` (catalogo/Album Obiettivo), `32b520b` (DeckLab).

### Rifacimento DeckLab — 3 problemi UX concreti risolti

Su richiesta esplicita dell'utente ("fixa tutta la struttura DeckLab"), identificati leggendo il codice (non ipotizzati) e confermati dall'utente prima di procedere:

1. **Analisi mazzo mai mostrata**: `AnalysisSection` (HP medio, tipi, ripartizione Pokemon/Trainer/Energia) esisteva gia' completa nel ViewModel (`analyzeDeck()`/`currentAnalysis`) ma nessuna schermata la richiamava — codice orfano. Resa pura (riceve un `DeckAnalysis` invece del ViewModel) e agganciata alla vista dettaglio mazzo, che la calcola localmente dalle carte del mazzo visualizzato.
2. **Flusso post-import duplicato in 4 punti** (import testo, import Meta Deck da 3 schermate diverse) con la stessa logica `if (missing.isEmpty() && matched > 0) showSheet = true` ripetuta — causava il dialog risultati e il foglio di modifica ad aprirsi **contemporaneamente** quando l'import matchava tutto. Rimossa la duplicazione: `ImportResultDialog` resta l'unico punto che decide il passo successivo.
3. **Ricerca online scoperta solo per caso**: il bottone "Cerca nei set TCG" compariva solo dopo zero risultati nella collezione locale. Ora e' sempre visibile appena si scrive una query. Aggiunto anche un banner quando si e' in modalita' revisione import (post-import), con bottone per tornare a vedere tutta la collezione.

Commit incluso in `32b520b` sopra.

### Verificato e non verificato

**Verificato**: compila pulito (`compileDebugKotlin`), build APK riuscita (`app-debug-225855.apk`, 56.67 MB), servita correttamente dal server WiFi locale.
**Non ancora verificato su device fisico**: nessuna delle modifiche di stasera (fix immagini import, analisi mazzo, flusso import, ricerca online) e' stata confermata sul telefono prima della fine sessione.

### Cosa resta aperto per M4.6

1. ~~Rarita' in D1~~ — fatto (sessione precedente).
2. ~~Ripuntare DeckLab e Album Obiettivo sulla ricerca ITA~~ — **fatto in questa sessione**.
3. Nascondere ENG/JAP/CHN dal Pokedex — sostanzialmente gia' fatto (tab lingua rimossi in `d2fe722`), da riverificare che Collection/Album/WishList con carte ENG/JAP gia' possedute non si siano rotte (mai verificato esplicitamente).
4. Cancellazione del codice PokeWallet (sfoglia/matching catalogo, Room `SetEntity`/`CardEntity`/DAO, `SetsSyncWorker`/`CardsSyncWorker`, glue ITA->ENG, chiavi API dal client) — **ora sbloccata** per la parte DeckLab/Album Obiettivo (punto 2 completato), ma da fare solo dopo conferma su device che tutto funziona stabile.
5. Rinomina finale (`PokeTcgRepository` -> `CatalogRepository`, `PokeWalletRetrofitClient` -> `PokeVaultApiClient`).

### Nota sul budget PokeWallet

A fine sessione: **0/1000 richieste residue** per oggi (si resetta a mezzanotte, fuso orario non verificato con certezza — probabile UTC). Non blocca il testing di domani per i flussi DeckLab (non lo usano piu'), ma altre funzioni non ancora migrate (prezzi diretti per carte fuori dal nostro catalogo, eventuale sfoglia catalogo legacy) potrebbero restare limitate finche' il budget non si resetta.

---

## Context (piano originale — vedi correzioni sopra)

PokeVault e un'app **Android nativa** (Kotlin + Jetpack Compose, `com.emabuia.pokevault`, `versionName 2.0.14`), ferma da **~4 mesi** (ultimo commit `cc44d45`, 7 maggio 2026).

Il problema centrale: **il 100% del catalogo dipende da PokeWallet** (`api.pokewallet.io`) — set, carte, **immagini** e prezzi. Il Worker Cloudflare esistente (`pokevault-proxy`) non e un backend: e solo una cache KV davanti a PokeWallet. Se PokeWallet cambia prezzo, API o chiude, l'app muore. In piu le carte PokeWallet sono **in inglese**, mentre l'obiettivo di prodotto e esporre **carte italiane**.

Esito voluto: un **catalogo proprietario italiano** su infrastruttura Cloudflare (D1 + R2 + Workers), alimentato da una **pipeline di ingest automatica** che si aggiorna da sola a ogni nuova espansione. PokeWallet resta **solo** come sorgente prezzi, dietro il nostro Worker, ed e sostituibile in qualsiasi momento. In coda: bugfix e pulizia del debito accumulato.

### Decisioni gia prese (input utente, 2026-09-06)

| Tema | Decisione |
|---|---|
| Catalogo | **Dataset proprietario** su D1 (schema, ID, traduzioni, conteggi = nostri) |
| Immagini | **Self-host su R2**, WebP a bassa risoluzione |
| Acquisizione | **Ingest automatico** da fonte IT pubblica, verificata fattibile (sotto) |
| Prezzi | **PokeWallet mantenuto solo per i prezzi**, dietro il Worker |
| Lingua | **Solo italiano** — coerenza e velocita prima di tutto |
| Scope v1 | **Set moderni** (SV + SWSH + ME), storico con backfill progressivo |
| Budget prezzi | PokeWallet **1000 richieste/giorno gratuite**: la cache va sfruttata a fondo (sez. 2.3) |
| Asset esistenti | Quello che e gia nel KV Cloudflare va inventariato e riusato dove ha valore (sez. M0.5.1) |

**Nota di inquadramento**: l'app oggi **funziona**. Questo non e un piano di salvataggio ma di consolidamento — si migra l'infrastruttura mantenendo l'app in funzione a ogni passo (flag remoto, rollback in minuti), e si chiude con una fase dedicata a sistemare tutto il debito accumulato (M7).

---

## 1. Analisi tecnica

### 1.1 Stato attuale

**Stack reale**
- Android: Kotlin 2.0.21, AGP 8.13.2, Compose BOM 2024.09.00, `compileSdk 36` / `minSdk 26`, Java 11
- Rete: Retrofit 2.11 + Gson + OkHttp; immagini Coil 2.6 (memory 25%, disk 256 MB)
- Locale: Room v1 (`pokevault_cache.db`) — tabelle `sets`, `cards`, `prices`
- Utente: Firebase Auth + Firestore (`users/{uid}/...`), cache offline illimitata
- Background: WorkManager — `sets_sync` 24h, `cards_sync` 7g, `price_sync` 12h, `cache_cleanup` 24h
- Edge: 1 Worker TS (`pokevault-proxy-worker/src/index.ts`, 674 righe) + 1 KV namespace, cron `*/30`

**Il vincolo PokeWallet, in dettaglio**

| Cosa | Dove |
|---|---|
| Catalogo set/carte | `data/remote/PokeTcgRepository.kt` (1735 righe) — nonostante il nome usa PokeWallet |
| Client HTTP | `data/remote/PokeWalletApiService.kt:162` — `DIRECT_API_URL = "https://api.pokewallet.io/"` |
| Prezzi | `data/remote/PokeWalletRepository.kt` — cache L1 memoria 24h -> L2 Room -> L3 rete |
| **Immagini** | `PokeTcgRepository.kt:1482` `buildCardImageUrl()` -> `{base}images/{id}?size=low\|high` |
| Loghi set | `PokeTcgRepository.kt:1491` `buildSetImageUrl()` + `SET_IMAGE_CACHE_VERSION = "setimg-v4"` |
| API key nel client | `CardsVaultTCGApp.kt:71-77` — interceptor Coil che inietta `X-API-Key` |
| Proxy edge | `pokevault-proxy-worker/src/index.ts` + `wrangler.toml` (`ORIGIN_API`) |

**Verifiche eseguite oggi**

| Verifica | Esito |
|---|---|
| `api.pokewallet.io/sets` | HTTP **401** — servizio vivo, richiede key valida |
| Fonte IT — freschezza | `tcgdex/cards-database`: ultimo commit **6 set 2026**, piu commit al giorno |
| Fonte IT — set recenti | Presenti `me05 Buio Pesto`, `me04 Caos Nascente`, `B2a Paldean Wonders`; `me03` = 27 mar 2026 |
| Immagini **IT** set moderni | ✅ `assets.tcgdex.net/it/me/me03/001/low.webp` -> 200 (19 KB) / `high.webp` -> 200 (94 KB) |
| Immagini IT set storici | ❌ 404 su `xy1`, `bw1`, `dp1`, `base1` (anche in EN) |
| Buchi sparsi | ⚠️ `me05/084` -> 404 ma `me05/120` -> 200 — la copertura non e prevedibile a priori |
| Licenza dataset | `cards-database` = **MIT**; sito TCGdex = GPL3 (non ci riguarda) |
| **Costo fonte IT** | **Gratuita, nessuna API key.** 12 richieste consecutive -> 12× HTTP 200 a ~200 ms, nessun header di rate limit, nessun throttling. Chiedono solo di non ripetere fetch massivi: e esattamente il nostro pattern (ingest una volta, poi serviamo noi) |
| `local.properties` | **Assente** sul disco -> oggi il progetto non builda con le chiavi |

> **Conclusione sulla fattibilita dell'ingest automatico (domanda esplicita dell'utente):**
> La fonte IT e **aggiornata quotidianamente** e ha immagini italiane native per tutti i set moderni. L'ingest automatico e la strada giusta: **niente webscraping**. Lo scraping sarebbe piu fragile (HTML che cambia), piu lento, piu esposto a blocchi/ToS e non piu legale. Il dataset resta comunque **nostro**: importiamo una volta, normalizziamo nel nostro schema, e a runtime non contattiamo mai la fonte. Se un giorno sparisse, il catalogo continuerebbe a funzionare — perderemmo solo i set futuri.

### 1.2 Debito tecnico che pesa sulla migrazione

| # | Problema | Impatto |
|---|---|---|
| 1 | Repo **327 MB** — 23 AAB in cronologia + `app/release/app-release.aab` 26 MB tracciato | Clone lentissimo, CI lenta |
| 2 | CI gira su `main`/`develop`, **branch di default = `master`** | Nessun test gira davvero |
| 3 | `android-advanced-tests.yml` usa **JDK 11** con AGP 8.13.2 + task `jacocoTestDebugUnitTestReport` **mai registrato** | Workflow rotto in partenza |
| 4 | `util/AppLocale.kt` e `viewmodel/DeckLabViewModel.kt` **duplicati in root**, fuori dal source set, divergenti (577 e 908 righe di diff) | Confusione, merge sbagliati |
| 5 | PaddleOCR + TensorFlow Lite in build ma **nessun `.tflite`**, `assets/` inesistente | ~2 librerie native inutili nell'APK |
| 6 | 55 `AsyncImage` **senza `ImageRequest.size()`** | Decodifica a risoluzione piena: causa #1 dei consumi memoria |
| 7 | `safeImageUrl()` duplicato **7 volte** e applicato in modo incoerente | Bug di rendering sparsi |
| 8 | Room **v1 senza infrastruttura di migration** | Qualsiasi cambio schema = crash all'avvio |
| 9 | i18n: `strings.xml` con **1 stringa**, 1019 righe di getter in `AppLocale.kt`, `stringResource` mai usato | Manutenzione pesante |
| 10 | `POKETCG_API_KEY` iniettata in BuildConfig ma **mai letta** | Codice morto |
| 11 | `HttpLoggingInterceptor` BASIC **attivo anche in release** (`LimitlessTcgRepository.kt:25-28`) | Leak di log in produzione |
| 12 | Loop di rete sequenziali in `PokeTcgRepository.kt` (righe 438, 497, 534, 576, **600 `for (page in 1..3)`**, 801, 856) | Collo di bottiglia di scanner e ricerca |
| 13 | `account_id`, KV id e `.wrangler/cache/wrangler-account.json` (con email) **committati** | Leak minore |
| 14 | 12 file markdown (3848 righe), 8 sul solo testing, **nessun README di root** | Doc inutilizzabile |
| 15 | 37 branch remoti mai eliminati, `master` indietro rispetto a `release/R2.0.21` | Confusione di versioning |

---

## 2. Strategia di migrazione

### 2.1 Architettura target

```
  GITHUB ACTIONS  (ingest — cron giornaliero + manuale)
  ┌──────────────────────────────────────────────────────────────┐
  │ 1. clone dataset IT   2. diff vs manifest    3. verifica      │
  │    (MIT, pinned)         (solo delta)           HTTP 200 IT   │
  │ 4. WebP thumb 245px q70 + detail 600px q80                    │
  │ 5. upload R2 (S3 API, content-hash)   6. SQL delta -> D1      │
  └──────────────────────────────────────────────────────────────┘
                                │
                                ▼
  CLOUDFLARE  (serving — nessuna dipendenza runtime da terzi)
  ┌──────────────────────────────────────────────────────────────┐
  │  D1  pokevault-catalog   sets · cards · card_prices ·        │
  │                          set_map · sync_runs · takedowns      │
  │  R2  pokevault-cards     /c/{set}/{num}/{thumb|detail}.webp  │
  │                          -> img.pokevault.app (custom domain) │
  │  Worker  api.pokevault.app                                    │
  │     GET /v1/manifest          (versione catalogo, delta sync) │
  │     GET /v1/sets                                              │
  │     GET /v1/sets/{id}/cards                                   │
  │     GET /v1/cards/{id}                                        │
  │     GET /v1/prices/{id}       -> PokeWallet (key server-side) │
  │     Cache API + KV davanti a tutto                            │
  └──────────────────────────────────────────────────────────────┘
                                │
                                ▼
  ANDROID   Retrofit -> api.pokevault.app · Coil -> img.pokevault.app
            Room v2 (migration reale) · WorkManager delta-sync
```

**Perche D1 e non KV**: il catalogo va interrogato (filtri per set, rarita, tipo, ricerca per nome). KV e chiave-valore e non lo permette; oggi questo obbliga il client a scaricare interi set e filtrare in locale. D1 e SQLite all'edge: query vere, indici, `LIKE` per la ricerca.

**Perche R2 e non Cloudflare Images**: R2 ha **egress a zero** e 10 GB nel piano gratuito. Le varianti le pre-generiamo in ingest (2 taglie fisse), quindi non serve resize a runtime. Cloudflare Images costerebbe per immagine servita senza darci nulla in piu.

**Perche l'ingest in GitHub Actions e non in un Worker cron**: l'ingest scarica e ricomprime migliaia di immagini. I Workers hanno limiti di CPU-time per invocazione — il cron attuale (`*/30`, che pagina interi set) e gia a rischio. Un runner Actions non ha questo vincolo, e il log resta ispezionabile.

### 2.2 Gestione della copertura IT (risposta a "esponiamo solo le carte italiane")

I test mostrano che l'italiano copre bene i set moderni ma ha **buchi sparsi e imprevedibili**. La regola proposta, per ottenere "veloce e coerente" senza buchi visivi:

**Regola dei tre livelli, applicata a ingest-time (mai a runtime):**

1. **Livello set** — un set entra nel catalogo solo se la copertura IT verificata e **≥ 90%** delle carte ufficiali. Sotto soglia, il set resta in `sets` con `published = 0`: invisibile nel Pokedex, ma le sue carte restano risolvibili se un utente le ha gia in collezione. A ogni run l'ingest ricontrolla: quando la copertura sale, il set si pubblica **da solo**.
2. **Livello carta** — le carte senza immagine IT restano nel catalogo con `image_status = 'missing'` (i conteggi dei set restano corretti) e ricevono un **placeholder generato da noi** (fondo neutro + nome + numero + simbolo set): coerente, nessun mix IT/EN, nessuna cella vuota.
3. **Auto-guarigione** — ogni carta `missing` viene ricontrollata a ogni run. TCGdex aggiunge immagini nel tempo: i buchi si chiudono senza intervento e senza rilasciare una nuova versione dell'app.

Conseguenza voluta: alla v1 il Pokedex mostra **solo set moderni completi in italiano**. Nessuna carta inglese, nessun badge di lingua, nessuna ambiguita. Lo storico entra progressivamente.

### 2.3 Prezzi: PokeWallet isolato dietro il Worker, entro il budget di 1000 richieste/giorno

I prezzi sono l'unico pezzo che resta su PokeWallet, con un vincolo preciso: **il piano gratuito da 1000 richieste/giorno**. Il design nasce attorno a quel numero.

**Isolamento**
- L'app **non conosce piu** PokeWallet: chiama solo `GET /v1/prices/{cardId}`. La API key resta secret del Worker.
- Il Worker traduce il nostro `cardId` italiano nell'identificativo PokeWallet inglese usando la tabella **`set_map`** in D1 (`it_set_id` -> `pw_set_code`), popolata partendo dalla logica gia esistente in `data/remote/SetCodeMapper.kt`. Chiave di join: **set + numero carta** (invariante tra lingue).
- Sostituire il fornitore prezzi in futuro = cambiare **un solo file** del Worker.

**Il cambio che libera il budget: bulk invece di per-carta**

Oggi `PokeWalletRepository.getCardPrices()` chiama `search` **per singola carta**: 1 richiesta = 1 prezzo. Con 10.000 carte a catalogo, il budget da 1000/giorno si esaurirebbe dopo il 10% della collezione.

PokeWallet espone pero `GET sets/{setCode}` **paginato a 200 elementi** — lo usa gia il cron del Worker attuale (`index.ts`, backfill). Passando al bulk:

| Approccio | Richieste per refresh completo (10.000 carte) | % del budget giornaliero |
|---|---|---|
| Per-carta (attuale) | ~10.000 | **1000% — impossibile** |
| **Bulk per set (proposto)** | **~50-60** | **~6%** |

Un refresh **completo** di tutti i prezzi costa ~60 richieste. Restano **~940 richieste/giorno** di margine.

**Cache a 4 livelli**

| Livello | Cosa | TTL | Costo PokeWallet |
|---|---|---|---|
| L1 | Snapshot in **`card_prices` (D1)** — servito sempre e subito, con `updatedAt` visibile in UI | — | **0** |
| L2 | Cache API / KV sul Worker | 6h | **0** |
| L3 | Job schedulato bulk per set | 4×/giorno | ~60/giorno |
| L4 | On-demand, solo carte fuori catalogo | — | residuo |

**Guardie sul budget** (in D1, tabella `api_budget`):
- Contatore giornaliero delle chiamate PokeWallet, resettato a mezzanotte UTC
- **Circuit breaker a 800/giorno**: oltre soglia il Worker serve solo L1/L2 e smette di chiamare l'origin
- Alert (issue GitHub automatica) al superamento di 600/giorno
- L'app mostra sempre un prezzo — al peggio l'ultimo noto, con la data accanto. **Nessuna schermata vuota, mai.**

Effetto collaterale: anche se PokeWallet chiudesse domani, il catalogo e i prezzi storici restano nostri in D1.

### 2.4 Migrazione lato Android

L'app non viene riscritta: si introduce un'astrazione e si commuta con un feature flag.

1. **Interfaccia comune** `CardCatalogSource` con due implementazioni: `PokeWalletCatalogSource` (attuale) e `PokeVaultCatalogSource` (nuova API). `data/remote/RepositoryProvider.kt` sceglie quale iniettare.
2. **Flag remoto** servito da `GET /v1/manifest` (campo `catalog_source`): permette rollback istantaneo **senza pubblicare una release**.
3. **Room v2** con `Migration(1,2)` **reale** — oggi non esiste alcuna infrastruttura di migration e un bump farebbe crashare l'app. Nuove colonne: `imageStatus`, `language`, `catalogVersion`, `publishedAt`.
4. **Delta sync**: il client tiene `catalogVersion`; il worker `sets_sync` chiede solo il delta. Sostituisce il full-refresh attuale di `SetsSyncWorker` (`forceRefresh = true`).
5. **Rinomina** al termine: `PokeTcgRepository` -> `CatalogRepository`, `PokeTcgApiService` -> `CatalogModels`, rimozione di `POKETCG_API_KEY` e `POKEWALLET_API_KEY` dal client.

### 2.5 Migrazione step-by-step

| # | Passo | Reversibile? |
|---|---|---|
| 1 | Verifica accesso Cloudflare, inventario risorse esistenti | — |
| 2 | Crea D1 + R2 + custom domain, **senza toccare** il Worker attuale | Si |
| 3 | Ingest pilota: 3 set (`me05`, `sv10`, `swsh12`) -> D1 + R2 | Si |
| 4 | Deploy Worker `pokevault-api` **affiancato** al proxy esistente | Si |
| 5 | App: `CardCatalogSource` + flag remoto, default **OFF** (nulla cambia) | Si |
| 6 | Test interno con flag ON su build debug | Si |
| 7 | Ingest completo set moderni (SV + SWSH + ME) | Si |
| 8 | Beta Play Store con flag ON al 100% dei tester | Si (flag) |
| 9 | Produzione: flag ON progressivo | Si (flag) |
| 10 | Prezzi spostati su `/v1/prices` | Si |
| 11 | **Spegnimento `pokevault-proxy`** e rimozione codice PokeWallet dal client | No |
| 12 | Backfill storico progressivo | — |

Nessun passo prima dell'11 e distruttivo: si puo tornare indietro in qualsiasi momento con un flag.

---

## 3. Automazioni

### 3.1 Pipeline di ingest — `.github/workflows/catalog-ingest.yml`

Trigger: `schedule: '0 4 * * *'` (04:00 UTC) + `workflow_dispatch` con input `set_id` per forzare un singolo set.

| Step | Azione | Nota |
|---|---|---|
| 1 | Clone `tcgdex/cards-database` a **commit pinnato** | Build riproducibile; il pin si aggiorna nello stesso job |
| 2 | Filtra lingua `it` + set nello scope configurato | `scope.json` versionato nel repo |
| 3 | Diff contro `manifest.json` dell'ultima run (letto da R2) | Solo il delta viene processato |
| 4 | Per ogni carta nuova/cambiata: `HEAD` sull'immagine IT | Determina `image_status` |
| 5 | Download + `sharp`: WebP **thumb 245px q70** e **detail 600px q80** | ~12 KB + ~35 KB per carta |
| 6 | Upload su R2 via S3 API, chiave `c/{setId}/{number}/{variant}.webp` | Skip se content-hash invariato |
| 7 | Genera SQL `INSERT ... ON CONFLICT DO UPDATE` -> `wrangler d1 execute` | Transazionale |
| 8 | Ricalcola copertura per set, aggiorna `published` | Auto-pubblicazione ≥ 90% |
| 9 | Bump `catalog_version`, scrive `sync_runs`, invalida cache Worker | Sblocca il delta-sync dei client |
| 10 | Se nuovo set rilevato -> apre **issue GitHub** automatica | Notifica umana |
| 11 | Se copertura di un set gia pubblicato **cala** -> fallisce il job | Guardia anti-regressione |

**Stima volumi**: SV + SWSH + ME ≈ 10.000 carte × ~47 KB = **~470 MB** su R2 — abbondantemente dentro i 10 GB gratuiti. Prima run completa: ~40-60 min. Run incrementali: **1-3 min**.

### 3.2 Job prezzi — `.github/workflows/prices-sync.yml`

Cron `0 */6 * * *` (4 run/giorno). **Non interroga per carta: itera i set** con `GET sets/{setCode}?limit=200`, come gia fa il cron del Worker attuale.

| | |
|---|---|
| Costo per run completa | ~60 richieste (10.000 carte / 200 per pagina) |
| Costo giornaliero | **~60-240 richieste** contro un budget di 1000 |
| Margine residuo | **~760-940 richieste/giorno** per l'on-demand |
| Scrittura | Snapshot in `card_prices` (D1) con `updatedAt` |
| Guardia | Legge `api_budget`; sopra 800/giorno il job si ferma da solo |

Priorita di aggiornamento: i set piu consultati (da `sync_runs`) vengono rinfrescati a ogni run, i set vecchi a rotazione giornaliera. Cosi le carte che gli utenti guardano davvero hanno prezzi al massimo di 6 ore, senza sprecare budget sullo storico.

### 3.3 Rilevamento nuova espansione

Nessuna configurazione manuale: quando il dataset upstream aggiunge un set con `releaseDate` nel futuro prossimo, l'ingest lo inserisce con `published = 0` e apre la issue. Man mano che le immagini IT compaiono, la copertura sale e **il set si pubblica da solo**. Nessun rilascio dell'app necessario.

### 3.4 CI/CD Android da riparare

| Workflow | Intervento |
|---|---|
| `android-tests.yml` | Aggiungere `master` ai trigger — oggi **non gira mai** |
| `android-advanced-tests.yml` | JDK 11 -> **17**; registrare davvero il task `JacocoReport` o rimuovere lo step |
| **Nuovo** `worker-deploy.yml` | Deploy Worker su push a `master` che tocca `cloudflare/` |
| **Nuovo** `release-build.yml` | Build AAB firmato su tag, artefatto **non committato** |

---

## 4. Compliance legale

> Nota: non sono un avvocato; quanto segue riduce il rischio in modo concreto ma non lo azzera. Per un'app monetizzata vale una consulenza legale prima della v3 pubblica.

### 4.1 Cosa possiamo usare con serenita

- **Metadati fattuali**: nomi carte, numeri, HP, tipi, rarita, date di uscita, conteggi set. Sono fatti; la loro raccolta strutturata da parte nostra e nostra. Il dataset di origine e **MIT**, che ne consente esplicitamente uso e ridistribuzione.
- **Il nostro schema, i nostri ID, le nostre traduzioni, i nostri conteggi**: interamente proprietari.

### 4.2 Immagini — il punto sensibile, trattato con onesta

Le immagini delle carte sono **opere protette di The Pokémon Company / Nintendo / Creatures / GAME FREAK**. Nessuna configurazione tecnica le rende "libere". Quello che possiamo fare e ridurre concretamente l'esposizione:

| Mitigazione | Perche funziona |
|---|---|
| **Bassa risoluzione** (thumb 245px, detail 600px) | Inutilizzabili per stampa o contraffazione: riduce il danno di mercato, il fattore piu pesante in ogni valutazione |
| **Nessun artwork isolato** | Serviamo sempre la **carta intera** come identificatore di prodotto, mai l'illustrazione ritagliata (uso espressivo, molto meno difendibile) |
| **Nessun export/download di massa** | Nessuna funzione "scarica tutte le immagini", niente API pubblica sul bucket |
| **Nessuna ridistribuzione del dataset immagini** | R2 servito solo all'app, con `Referer`/token check sul custom domain |
| **Kill-switch per set** | Tabella `takedowns` in D1: il Worker smette di servire un set in **< 5 minuti**, senza rilasciare l'app |
| **Procedura di notice-and-takedown** | Pagina pubblica + email dedicata + SLA dichiarato di 72h |
| **Disclaimer marchi** | Gia presente in `docs/index.html` e `SettingsScreen.kt:294-311`: mantenerlo e renderlo visibile anche in Home |

### 4.3 Sul punto "carte senza firma Pokemon" — raccomandazione contraria, motivata

Hai chiesto di caricare le carte **senza la firma Pokémon**. Qui devo darti un'indicazione opposta, perche l'intuizione si ritorce contro:

Il testo `© 2026 Pokémon / Nintendo / Creatures / GAME FREAK` stampato sulla carta e **Copyright Management Information**. Rimuoverlo o coprirlo e un **illecito autonomo e piu grave** (DMCA §1202 nell'ordinamento USA, e trattato con analogo sfavore in UE): trasforma un uso potenzialmente tollerato in una condotta che appare deliberatamente elusiva, ed e esattamente il tipo di dettaglio che pesa in una contestazione. **Un'immagine integrale a bassa risoluzione e piu difendibile di un'immagine alterata ad alta risoluzione.**

**Quindi:**
- ✅ **Riduciamo la qualita** — questo si, ed e la mitigazione che conta davvero
- ✅ **Manteniamo intatto il copyright notice** della carta
- ✅ **Rimuoviamo invece i watermark di terzi** (loghi di siti/aggregatori): quelli sono marchi altrui e non c'entrano con la carta
- ❌ Non ritagliamo, non alteriamo, non copriamo la grafica originale

### 4.4 Monetizzazione — attenzione specifica

L'app ha **Google Play Billing** e una `PremiumScreen`. Vendere l'accesso a contenuti protetti alza sensibilmente il profilo di rischio.

**Regola da fissare in modo vincolante:** il Premium vende **funzionalita** (statistiche avanzate, album illimitati, Deck Lab, export della propria collezione). **Le immagini delle carte non devono mai stare dietro il paywall**, ne integralmente ne per qualita. Da verificare in `ui/premium/PremiumScreen.kt` e `data/billing/PremiumManager.kt` durante M7.

### 4.5 Documenti da aggiornare

| Documento | Intervento | Stato |
|---|---|---|
| `docs/privacy-policy/index.html` | Rimuovere il riferimento a Pokewallet come fornitore dati; aggiungere Cloudflare (R2/D1/Workers) come sub-responsabile | **Fatto 2026-09-09** — vedi checkpoint in fondo |
| `docs/terms/index.html` | Aggiungere sezione **Proprieta intellettuale** + procedura di takedown con contatto | **Fatto 2026-09-09** |
| **Nuovo** `docs/copyright/index.html` | Pagina dedicata: fonte dei dati, natura delle immagini, come richiedere rimozione, SLA 72h | **Fatto 2026-09-09** |
| `util/AppLocale.kt:719-727` | Aggiornare il disclaimer in-app (oggi cita esplicitamente Pokewallet.io) | **Fatto 2026-09-09** |
| Tutte le pagine `docs/` | Sono **solo in italiano** mentre l'app e IT/EN: allineare o dichiarare l'italiano come lingua ufficiale | **Fatto 2026-09-09** — dichiarato l'italiano lingua ufficiale, non tradotto (resta voce #26 sez. 8) |

---

## 5. Roadmap completa

### M0 — Ripresa e stato dei fatti · 3-4 giorni
**Perche prima di tutto**: lo stato di Cloudflare e oggi ignoto e `local.properties` non esiste.

| Attivita | Deliverable |
|---|---|
| `wrangler whoami`, `wrangler kv namespace list`, `wrangler deployments list --name pokevault-proxy` | Inventario risorse Cloudflare + costi attuali |
| Verificare se il Worker risponde e se il cron `*/30` gira ancora | Report stato edge |
| **Inventario di cosa c'e gia nel KV** (vedi 5.1 sotto) | Report riuso: cosa salviamo, cosa no |
| Ricreare `local.properties` da `local.properties.example`; verificare `./gradlew assembleDebug` | Build locale funzionante |
| Allineare `master` con `release/R2.0.21`; decidere la versione di partenza | Branch unico di lavoro |
| Aggiornare `gradle/libs.versions.toml` (4 mesi di drift) e verificare che compili | Dipendenze aggiornate |
| Fix CI: `master` nei trigger, JDK 17 ovunque, task Jacoco | CI verde sul branch di default |

**Rischi**: credenziali Cloudflare non recuperabili -> ricreare account e risorse (mezza giornata). Aggiornamenti Gradle/AGP che rompono il build -> aggiornare per gradi, un blocco alla volta.

#### 5.1 Cosa recuperiamo davvero da quello che e gia su Cloudflare

Il KV `CACHE` (id `14e664fe...`) contiene materiale accumulato in mesi di uso. Va inventariato, ma serve chiarezza su cosa e riutilizzabile e cosa no:

| Contenuto nel KV | Riutilizzabile? | Motivo |
|---|---|---|
| **Metadati set e carte** (`pokewallet:/sets`, `/sets/*`, `/cards/*`) | ✅ **Si, molto utile** | Alimenta la tabella **`set_map`** (codici set, ID, numeri) che serve al mapping IT->EN per i prezzi. Ci risparmia decine di richieste PokeWallet e lavoro manuale |
| **`pokewallet:real-totals:index:v1`** (conteggi carte reali per set, frutto di mesi di backfill) | ✅ **Si** | Diventa il valore di riferimento per validare i conteggi del nostro catalogo. E lavoro gia fatto: si importa in D1, non si ricalcola |
| **Immagini carte** (`/images/*`, base64 dentro JSON) | ⚠️ **Solo come ponte** | Sono immagini **in inglese**, e l'obiettivo e un catalogo italiano. Inoltre sono base64 in KV (+33% di dimensione, `JSON.parse` a ogni HIT). Contraddirebbero la coerenza IT: **non migrarle in R2** |
| Risposte `/search` | ❌ No | Cache effimera, TTL breve, nessun valore residuo |

**Quindi si**, quello che c'e va sfruttato — ma il valore vero sono i **metadati e i conteggi**, non le immagini. Le immagini KV sono inglesi e sostituirle con quelle italiane e precisamente l'obiettivo del progetto. L'inventario in M0 (`wrangler kv key list --namespace-id ...`) dice esattamente quante chiavi ci sono e di che tipo, e da li si decide cosa importare in D1.

Deliverable: script `cloudflare/ingest/import-kv-legacy.js` che estrae metadati e real-totals dal KV e li scrive in D1 come base di partenza.

---

### M1 — Fondamenta Cloudflare · 1 settimana

| Attivita | Deliverable |
|---|---|
| Creare D1 `pokevault-catalog` + schema versionato | `cloudflare/schema/001_init.sql` |
| Creare R2 `pokevault-cards` + custom domain `img.pokevault.app` | Bucket attivo, egress zero |
| Scaffold Worker `pokevault-api` (nuovo, affiancato al proxy) | `cloudflare/api/` |
| Ambienti `staging` / `production` in wrangler | Deploy isolati |
| **Rimuovere `account_id` e `.wrangler/` da git**, spostare in secret | Leak chiuso |

**Deliverable chiave**: `api-staging.pokevault.app/v1/health` risponde 200.
**Rischio**: dominio `pokevault.app` non nel nostro account -> fallback su `*.workers.dev` (nessun blocco).

---

### M2 — Pipeline di ingest, pilota · 1,5 settimane

| Attivita | Deliverable |
|---|---|
| Script ingest Node (`cloudflare/ingest/`) con `sharp` | Codice ingest |
| Ingest di **3 set pilota**: `me05`, `sv10`, `swsh12` | ~550 carte in D1 + R2 |
| Verifica copertura IT reale carta per carta | **Report copertura** — dato che decide lo scope |
| Placeholder generato per le carte `missing` | Asset placeholder |
| `manifest.json` + logica di delta | Ingest incrementale |

**Deliverable chiave**: report di copertura IT. Se un set moderno risultasse sotto il 90%, si rivede la soglia qui, non dopo.
**Rischio**: buchi IT piu ampi del previsto -> soglia abbassata a 80% + placeholder, oppure scansioni manuali mirate sui set piu usati.

---

### M3 — API di catalogo · 1 settimana

| Attivita | Deliverable |
|---|---|
| `/v1/manifest`, `/v1/sets`, `/v1/sets/{id}/cards`, `/v1/cards/{id}` | API funzionante |
| Cache API + KV, `ETag` e `Cache-Control` corretti | p95 < 100 ms |
| Ricerca per nome con indice D1 | Sostituisce `/search` di PokeWallet |
| Rate limiting + tabella `takedowns` operativa | Guardie attive |
| Test automatici del Worker (oggi: **zero**) | `cloudflare/api/test/` |

**Deliverable chiave**: API in staging con dati reali dei 3 set pilota.

---

### M4 — Integrazione Android · 2 settimane

| Attivita | File principali |
|---|---|
| Interfaccia `CardCatalogSource` + 2 implementazioni | `data/remote/CardCatalogSource.kt` (nuovo), `RepositoryProvider.kt` |
| Client Retrofit nuova API | `data/remote/PokeVaultApiService.kt` (nuovo) |
| **Room v2 + `Migration(1,2)` reale** | `data/local/PokeVaultDatabase.kt`, `app/schemas/.../2.json` |
| Coil -> `img.pokevault.app`, **rimozione interceptor `X-API-Key`** | `CardsVaultTCGApp.kt:66-103` |
| Sostituire `buildCardImageUrl`/`buildSetImageUrl` | `PokeTcgRepository.kt:1482-1493` |
| Delta-sync nei worker WorkManager | `workers/SetsSyncWorker.kt`, `CardsSyncWorker.kt` |
| Flag remoto + rollback istantaneo | `data/remote/RemoteConfig.kt` (nuovo) |

**Deliverable chiave**: build debug che funziona **interamente** sul catalogo proprietario.
**Rischio (il piu serio del piano)**: la migration Room e delicata e oggi non esiste alcuna infrastruttura -> test strumentato di migrazione **obbligatorio** prima del merge; in caso di fallimento, ricostruzione della cache da zero (non e persistenza utente: i dati utente stanno su Firestore).

---

### M5 — Ingest completo + automazione · 1 settimana

| Attivita | Deliverable |
|---|---|
| Ingest di tutti i set moderni (SV + SWSH + ME) | ~10.000 carte, ~470 MB su R2 |
| `catalog-ingest.yml` schedulato + issue automatica | Automazione viva |
| `prices-sync.yml` + `set_map` completa | Prezzi indipendenti da runtime PokeWallet |
| Dashboard di monitoraggio (`sync_runs`) | Visibilita |

**Deliverable chiave**: una nuova espansione entra nel catalogo **senza intervento umano**.

---

### M6 — Beta e taglio del cordone · 1,5 settimane

| Attivita | Deliverable |
|---|---|
| Beta Play Store (internal -> closed), flag ON | Feedback reale |
| Monitoraggio p95, error rate, costi Cloudflare | Metriche |
| Rollout progressivo in produzione | Release stabile |
| **Spegnimento `pokevault-proxy`**, rimozione codice PokeWallet dal client | Dipendenza catalogo azzerata |
| Aggiornare tutti i documenti legali (sezione 4.5) | Compliance allineata |

**Deliverable chiave**: PokeWallet resta **solo** come sorgente prezzi, dietro il nostro Worker.
**Rischio**: regressioni non viste in beta -> il flag remoto consente rollback in minuti, quindi lo spegnimento del proxy avviene **solo dopo 2 settimane stabili**.

---

### M7 — Bugfix e pulizia · 2 settimane
Vedi sezione 8. Include la verifica del punto 4.4 (paywall e immagini).

---

### M8 — Backfill storico progressivo · continuo
Estensione dello `scope.json` era per era, in ordine di richiesta degli utenti. Nessun rilascio dell'app necessario: i set si auto-pubblicano al superamento della soglia di copertura.

### Riepilogo

| Milestone | Durata | Cumulato |
|---|---|---|
| M0 Ripresa | 3-4 gg | ~1 sett |
| M1 Fondamenta CF | 1 sett | ~2 sett |
| M2 Ingest pilota | 1,5 sett | ~3,5 sett |
| M3 API | 1 sett | ~4,5 sett |
| M4 Android | 2 sett | ~6,5 sett |
| M5 Ingest completo | 1 sett | ~7,5 sett |
| M6 Beta + taglio | 1,5 sett | ~9 sett |
| M7 Bugfix | 2 sett | **~11 sett** |
| M8 Storico | continuo | — |

### Costi a regime

| Voce | Costo |
|---|---|
| Workers Paid (10M richieste/mese) | **$5/mese** |
| D1 | Incluso (free tier ampiamente sufficiente) |
| R2 (~500 MB, egress zero) | **$0** (free tier 10 GB) |
| GitHub Actions | **$0** (repo pubblico / free tier) |
| Fonte catalogo IT | **$0** — gratuita, nessuna API key, nessun rate limit (verificato) |
| PokeWallet (solo prezzi) | **$0** — resta dentro il piano gratuito da 1000 req/giorno usando ~60-240 (vedi 2.3) |
| Firebase | Invariato |

---

## 6. Tabella pro/contro

### Fonte del catalogo

| Soluzione | Pro | Contro |
|---|---|---|
| **Dataset proprietario su D1 con ingest automatico** ✅ | Nessuna dipendenza a runtime; schema e ID nostri; query vere all'edge; costo ~$5/mese; italiano nativo | Va costruita la pipeline; dipendenza sulla fonte al momento dell'ingest |
| Restare su PokeWallet | Zero lavoro | Catalogo inglese; costo per chiamata; single point of failure; nessun controllo |
| Webscraping | Nessuna dipendenza da API | Fragile (HTML che cambia); lento; esposto a blocchi e ToS; **nessun vantaggio legale** |
| Dataset 100% manuale | Controllo totale | Migliaia di inserimenti; irrealistico sullo storico |

### Immagini

| Soluzione | Pro | Contro |
|---|---|---|
| **R2 self-host low-res** ✅ | Velocissimo; egress zero; controllo totale; takedown in minuti; funziona offline via cache | Ospitiamo noi contenuti protetti (mitigato in sez. 4) |
| Hotlink upstream | Zero storage, zero rischio hosting | Lentezza; dipendenza runtime; puo sparire da un giorno all'altro |
| Cache on-demand | Storage minimo | Prima visualizzazione lenta; dipendenza runtime persiste |

### Ingest

| Soluzione | Pro | Contro |
|---|---|---|
| **GitHub Actions** ✅ | Nessun limite di CPU; log ispezionabili; gratis; `workflow_dispatch` manuale | Fuori da Cloudflare (accettabile: e build-time, non runtime) |
| Worker cron | Tutto in un posto | Limiti CPU-time; il cron attuale e gia al limite; debug difficile |

### Copertura linguistica

| Soluzione | Pro | Contro |
|---|---|---|
| **Solo IT, set sopra soglia** ✅ | Coerenza totale; nessun mix; catalogo piu leggero e veloce | Catalogo iniziale piu piccolo; storico assente all'inizio |
| IT + fallback EN | Copertura completa | Mix visivo incoerente; non e cio che vuoi |
| Multi-lingua | Massima copertura | Storage e pipeline moltiplicati; complessita alta |

---

## 7. Raccomandazione finale

**Procedere con: dataset proprietario su D1 + immagini low-res su R2 + ingest automatico via GitHub Actions + PokeWallet isolato ai soli prezzi dietro il Worker.**

Le ragioni, in ordine di peso:

1. **Risolve la causa, non il sintomo.** Oggi Cloudflare e una cache davanti a un fornitore che controlla catalogo, immagini, prezzi e lingua. Dopo la migrazione, Cloudflare **e** il backend: PokeWallet diventa un dettaglio sostituibile in un file.
2. **L'italiano diventa possibile.** E l'obiettivo che PokeWallet non puo soddisfare: le sue carte sono inglesi. Le verifiche di oggi confermano che le immagini italiane esistono, sono native e sono aggiornate.
3. **L'automazione e reale.** Con ingest quotidiano e auto-pubblicazione a soglia, una nuova espansione entra da sola. Nessun intervento, nessun rilascio.
4. **I costi crollano.** Da chiamate a consumo verso un fornitore esterno a **$5/mese** di Workers Paid, con D1 e R2 nel free tier.
5. **La velocita migliora strutturalmente**, per quattro motivi indipendenti e cumulativi:

   | Oggi | Dopo | Perche |
   |---|---|---|
   | Il client scarica **interi set** e filtra in locale (KV non sa interrogare) | Query SQL all'edge con indici, si scarica solo cio che serve | D1 e SQLite distribuito |
   | Immagini **full-res** decodificate sul telefono (55 `AsyncImage` senza `ImageRequest.size()`) | WebP **245px / 600px** pre-generati + `size()` impostata | Meno banda, molta meno memoria |
   | Immagini servite come **base64 dentro JSON in KV** (+33% peso, `JSON.parse` a ogni HIT) | Byte binari diretti da R2, egress zero | Nessuna serializzazione |
   | `SetsSyncWorker` fa **full refresh** (`forceRefresh = true`) | **Delta-sync** su `catalog_version` | Si scarica solo il cambiato |

   In piu spariscono i **loop di rete sequenziali** di `PokeTcgRepository.kt` (fino a `for (page in 1..3)` in serie per risolvere una carta), oggi il collo di bottiglia di scanner e ricerca.
6. **Il rischio legale e gestito, non ignorato.** Bassa risoluzione, carta integrale con copyright notice intatto, nessun export di massa, kill-switch per set e procedura di takedown documentata. Con l'avvertenza esplicita della sezione 4.3: **non rimuovere la firma Pokémon** — e la mossa che peggiora la posizione invece di migliorarla.

**Sulla scelta ingest vs webscraping** (tua domanda diretta): l'ingest e la scelta giusta e le verifiche lo confermano — fonte aggiornata **oggi stesso**, commit multipli al giorno, dataset MIT, immagini italiane native. Lo scraping sarebbe piu fragile, piu lento e non darebbe alcun vantaggio legale. Resta comunque disponibile come piano B mirato per singoli set scoperti, senza cambiare architettura.

**Il rischio maggiore del piano non e Cloudflare: e la migration Room** (M4). Oggi il database e a `version = 1` senza alcuna infrastruttura di migrazione, con un commento nel codice che avverte che ogni cambio di schema richiede una `Migration` esplicita. Va affrontata con test strumentato dedicato prima del merge.

---

## 8. Lista bugfix e pulizia (M7)

### Priorita alta — correttezza e sicurezza

| # | Intervento | Dove |
|---|---|---|
| 1 | ~~Migration Room reale~~ + test strumentato di migrazione | `data/local/PokeVaultDatabase.kt` — **rivalutata e chiusa 2026-09-09**: il database e' solo cache (dati utente veri su Firestore), fallback distruttivo su upgrade invece di Migration incrementali. Vedi checkpoint in fondo |
| 2 | `HttpLoggingInterceptor` attivo in release -> gate su `BuildConfig.DEBUG` | `LimitlessTcgRepository.kt:25-28` — **gia' fatto, verificato 2026-09-09 (non si sa quando)** |
| 3 | Rimuovere `account_id` e `.wrangler/cache/wrangler-account.json` da git; aggiungere `.wrangler/` al `.gitignore` di root | `pokevault-proxy-worker/wrangler.toml:3` — **fatto (merge del 2026-09-09)** |
| 4 | Rimuovere `POKEWALLET_API_KEY` e `POKETCG_API_KEY` dal client (key mai piu nell'APK) | `app/build.gradle.kts:28-31` — **fatto 2026-09-09**: `POKETCG_API_KEY` gia' assente dal sorgente; `POKEWALLET_API_KEY` ora forzata a `""` (e proxy forzato `true`) in `buildTypes.release`, verificato sul `BuildConfig.java` generato + `assembleRelease` verde. Vedi checkpoint in fondo |
| 5 | Set giapponesi taggati erroneamente come ENG | `viewmodel/SetsViewModel.kt:118` |
| 6 | Verificare che le immagini non finiscano dietro paywall (sez. 4.4) | `ui/premium/PremiumScreen.kt`, `data/billing/PremiumManager.kt` |
| 7 | **BUG PRODUZIONE confermato 2026-09-07: `normalizeCardNumber()` mostra la carta sbagliata per le sotto-collezioni "Shiny Vault"** — vedi dettaglio sotto | `pokevault-proxy-worker/src/index.ts:530-536` |
| 8 | **BUG segnalato dall'utente: loghi delle espansioni sbagliati o assenti nel Pokedex** — logo giapponese/cinese su alcune espansioni ITA, nessun logo su molte altre — vedi dettaglio sotto | `PokeTcgRepository.kt` (`mergeItalianSets`, `buildSetImageUrl`), `pokevault-proxy-worker/src/index.ts` (`buildItalianSetLogoCandidates`, `handleItalianR2AssetRequest`) |

#### Dettaglio bug #7 — immagine sbagliata per le carte "Shiny Vault" (preesistente, non causato dalla sessione del 2026-09-07)

**Sintomo**: l'utente che apre la carta `SV001` (Rowlet) dell'espansione `swsh45sv` (Shiny Vault, secret rare di Shining Fates) vede invece l'immagine di `Yanma`, la carta numero 1 del set base `swsh45`.

**Causa**: `normalizeCardNumber(raw)` (riga 530-536) fa `raw.replace(/[^0-9]/g, '')` — rimuove **tutte** le lettere dal numero carta prima di cercare la chiave immagine su R2. Per un numero come `"SV001"` il risultato e' `"1"`, che collide con qualunque carta del set base numerata `1`. Le immagini delle Shiny Vault sono salvate nella **stessa cartella R2** del set base (`it/SWSH45/`, non una cartella separata `it/SWSH45SV/`), quindi la collisione e' reale, non solo teorica — verificato in produzione: `GET /images/it/SWSH45/SV001?size=low` risponde 200 con l'immagine di Yanma (carta base #1), confermato confrontando con D1 (`SELECT nome FROM cards WHERE expansion_id='swsh45' AND card_number='1'` -> "Yanma").

**Portata**: 122 carte di `swsh45sv` + **confermato anche su Trainer Gallery** (`swsh10tg` almeno, visto fallire durante la run del 2026-09-07 su carte come `SWSH10_IT_TG22.png`...`TG30.png`) — stesso identico meccanismo (prefisso letterale nel numero carta, `normalizeCardNumber()` lo rimuove). Da considerare probabile su **tutte** le sotto-collezioni a prefisso: `swsh9tg`, `swsh11tg`, `swsh12tg` (altri Trainer Gallery), `swsh12pt5gg` (Galarian Gallery). Stima totale espansioni potenzialmente coinvolte: 5-6, per qualche centinaio di carte complessive — non uno o due casi isolati ma **una categoria intera di sotto-collezioni**, tutte con lo stesso fix.

**Perche' non e' stato toccato oggi**: `normalizeCardNumber()` e' condivisa da piu' percorsi (risoluzione immagini, matching prezzi via ricerca PokeWallet) — una modifica va verificata su tutti gli usi, non solo sulle immagini, prima di un deploy dedicato. Rimandato a M7 su decisione esplicita dell'utente (non e' un blocco per la migrazione D1/R2 in corso).

**Spiega anche perche' la ricompressione WebP fallisce "in blocco" su queste carte**: lo script di ricompressione cerca la chiave `it/SWSH45/SWSH45_IT_SV001.png` (che non esiste — verificato con `wrangler r2 object get`, "specified key does not exist"), perche' il nome file reale non include il prefisso `SV`. Non e' un problema della pipeline di conversione: e' lo stesso bug di fondo, visto da un angolo diverso.

**Aggiornamento 2026-09-09**: la parte pericolosa del bug (mostrare silenziosamente la carta sbagliata) **era gia' stata corretta** in una sessione precedente -- `buildItalianCardKeyCandidates` (righe ~556-558) non fa piu' fallback a `normalizeCardNumber()` per numeri alfa-prefissati (`isPureNumeric` guard), solo il codice non era stato ricollegato a questa voce del piano. Verificato **dal vivo** dopo il deploy di oggi: `GET /images/it/SWSH45/SV001?size=low` risponde `404 Asset not found`, non piu' l'immagine di Yanma. Verificato anche con `wrangler r2 object get` su tutti i pattern di chiave provati (`SWSH45_IT_SV001.{png,webp}`, `SV001.{png,webp}`) che l'immagine vera **non esiste in R2 sotto nessuno di essi** -- quello che resta e' un buco di contenuto (le immagini Shiny Vault/Trainer Gallery non sono mai state caricate con un nome risolvibile), stessa natura del bug #8 punto 1 (acquisizione immagini, non codice), non un fix da scrivere.

#### Dettaglio bug #8 — loghi delle espansioni: giapponesi/cinesi su alcune, assenti su molte (segnalato dall'utente 2026-09-08, causa root tracciata per intero via lettura codice -- nessuna build disponibile per riprodurlo visivamente)

**Sintomo riportato**: nella sezione espansioni del Pokedex, alcune espansioni mostrano un logo in giapponese o cinese; molte altre non mostrano alcun logo.

**Come funziona oggi, tracciato riga per riga**:

1. Per ogni espansione ITA, `mergeItalianSets()` (`PokeTcgRepository.kt:~2060`) calcola un `baseRawSetCode` (da `preferredBaseSetCodeForItalianExpansion()` se l'espansione e' tra le poche mappate esplicitamente — solo `me01-04`/`sv01-10` e varianti — altrimenti da `dominant_set_code`, gia' arrivato da D1) e chiede il logo con `buildSetImageUrl(baseRawSetCode)` -> `GET {worker}/sets/{CODE}/image`.
2. **Questa e' la stessa identica route usata anche per i set ENG/JAP/CHN** (`PokeWalletSet.toTcgSet()`, riga 2532): non c'e' distinzione lato URL tra "voglio il logo italiano" e "voglio il logo del set PokeWallet generico".
3. Lato Worker, `parseItalianAssetRequest()` (riga 500) intercetta `^/sets/([^/]+)/image$` e lo instrada come `kind: 'setLogo'` a `handleItalianR2AssetRequest()`: prova prima `buildItalianSetLogoCandidates()` in R2 (`{prefix}/{SET}/set-logo.png`, `set_logo.png`, `logo.png`, `cover.png`, `{SET}_IT_logo.png`) — **l'infrastruttura per servire un logo italiano self-hosted esiste gia' ed e' corretta**. Se non trova nulla in R2, ritorna `null` esplicitamente (riga 691-692, commento: *"fall through to the PokeWallet proxy so the upstream API can serve the image"*) e la richiesta prosegue verso il passthrough generico PokeWallet, che restituisce l'immagine associata a quel codice **senza alcuna selezione di lingua**.

**Causa del logo giapponese/cinese**: il lavoro di raccolta immagini fatto finora (`upload-ita-r2.ps1`, poi la ricompressione WebP) si e' concentrato **solo sulle carte**, mai sui loghi dei set — verificato che `buildItalianSetLogoCandidates()` non ha mai un candidato popolato per la stragrande maggioranza delle 106 espansioni, quindi il fallback a PokeWallet scatta quasi sempre. Quando scatta, PokeWallet restituisce quello che ha per quel codice esatto, senza preferenza di lingua: se un set e' stato pubblicato da PokeWallet solo in giapponese o cinese (es. i set esclusivamente giapponesi gia' noti da `SetsViewModel.languageNameOverrides` — "Mega Evolution All-Stars", "Pokémon Card Game Classic", ecc. — mai localizzati in inglese), il logo restituito e' in quella lingua. Non e' un errore di matching: e' l'unico logo che esiste upstream per quel codice.

**Causa del logo assente**: per le ~90 espansioni storiche senza una entry esplicita in `preferredBaseSetCodeForItalianExpansion()`, `dominant_set_code` e' un valore euristico — il prefisso piu' frequente tra i `card_id` di quell'espansione nel nostro dataset (`dominantSetCode()` in `scripts/import-catalog-to-d1.mjs`), **non garantito coincidere con un `set_code` realmente indicizzato da PokeWallet**. Quando non coincide, sia R2 (nessun logo caricato) sia il fallback PokeWallet (nessun set con quel codice) rispondono vuoto/404 — nessun logo mostrato, senza errori visibili in log lato client.

**Perche' non e' stato toccato ora**: sistemarlo bene richiede due cose che questa sessione non ha:
1. **Contenuto**: caricare loghi ITA reali in R2 per le espansioni che li vogliono (stesso tipo di lavoro fatto per le 15.539 immagini carte — acquisizione, non solo codice), oppure decidere un placeholder coerente (stesso principio gia' adottato per le carte senza immagine IT, sez. 2.2: "nessun mix IT/EN, nessuna cella vuota") per i set senza logo proprio, invece di mostrare un logo in lingua sbagliata o niente
2. **Verifica visiva**: qualunque modifica a `mergeItalianSets()`/`buildSetImageUrl()` (es. distinguere esplicitamente il caso ITA da ENG/JAP/CHN, o non richiedere affatto il logo PokeWallet quando manca quello R2) cambia cosa vede l'utente nel Pokedex — va controllata su device/emulatore, non solo per lettura di codice, prima di un deploy

**Direzione raccomandata per la prossima sessione con build disponibile**: trattare il logo dei set ITA come le immagini delle carte — self-hosted in R2 con soglia di pubblicazione, mai un fallback silenzioso a una fonte in lingua diversa. Concretamente: se `buildItalianSetLogoCandidates()` non trova nulla in R2 per un'espansione ITA, il Worker dovrebbe rispondere con un placeholder proprio (o 404 esplicito) invece di innescare il passthrough PokeWallet — lo stesso pattern "mai un mix di lingue" gia' applicato con successo alle carte.

**Aggiornamento 2026-09-09**: il punto 2 sopra (distinguere ITA da ENG/JAP/CHN, mai un fallback silenzioso) e' stato implementato — vedi checkpoint in fondo al documento. Resta aperto solo il punto 1 (contenuto: loghi ITA reali da caricare in R2), che il fix di oggi non affronta e non puo' affrontare senza acquisizione immagini.

### Priorita alta — performance

| # | Intervento | Dove |
|---|---|---|
| 7 | **`ImageRequest.size()` su tutte le 55 `AsyncImage`** — causa #1 dei consumi memoria | Tutte le schermate con Coil — **investigata 2026-09-09, chiusa come non necessaria, vedi checkpoint in fondo** |
| 8 | Loop di rete sequenziali -> `async`/`awaitAll` (pattern gia usato nel progetto) | `PokeTcgRepository.kt` righe 438, 497, 534, 576, **600**, 801, 856 — **investigato 2026-09-09, sospeso su decisione utente, vedi checkpoint in fondo** |
| 9 | `Column + verticalScroll` su liste potenzialmente lunghe -> `LazyColumn` | `CollectionScreen.kt`, `StatsScreen.kt`, `SettingsScreen.kt` |
| 10 | Rimuovere PaddleOCR + TensorFlow Lite (nessun `.tflite` esiste, `assets/` non c'e) | `ocr/PaddleOCREngine.kt`, `app/build.gradle.kts` — **fatto 2026-09-09, vedi checkpoint in fondo** |

### Priorita media — pulizia

| # | Intervento | Dove |
|---|---|---|
| 11 | **Cancellare i duplicati orfani in root** | `util/AppLocale.kt`, `viewmodel/DeckLabViewModel.kt` |
| 12 | `safeImageUrl()` duplicato 7 volte -> utility unica in `util/` e applicata ovunque | 7 file + i 3 punti che non la usano — **fatto per intero il 2026-09-09 (dedup + i 14 punti reali), vedi checkpoint in fondo** |
| 13 | Rinominare i file `_v2`/`_v3` e allineare nome file/classe | `MainActivity_v2.kt`, `CardsVaultTCGApp.kt`, `AppNavigation_v3.kt`, `HomeScreen_v2.kt` — **fatto 2026-09-09, vedi checkpoint in fondo** |
| 14 | Logging unificato su Timber (38 usi di `Log`/`println` residui) | `PaddleOCREngine.kt`, `MLKitOCREngine.kt`, `LimitlessTcgRepository.kt` — **fatto 2026-09-09, vedi checkpoint in fondo** |
| 15 | Spezzare i file monolitici (`DeckLabScreen.kt` 2240 righe, `PokeTcgRepository.kt` 1735) | — **`DeckLabScreen.kt` fatto 2026-09-09 (2241 -> 6 file), `PokeTcgRepository.kt` non è lo stesso tipo di lavoro, vedi checkpoint in fondo** |
| 16 | Rimuovere `FORCED_REAL_TOTALS_BY_SET_CODE = { ME03: 124 }` — i conteggi ora sono nostri in D1 | `pokevault-proxy-worker/src/index.ts:82-88` |
| 17 | Rimuovere `TranslationService` (MyMemory) se le traduzioni arrivano dal catalogo IT | `data/remote/TranslationService.kt` |

### Priorita media — repository e processo

| # | Intervento | Dove |
|---|---|---|
| 18 | **`git filter-repo`/BFG sui 23 AAB in cronologia**: 327 MB -> pochi MB (richiede force-push coordinato) | Storia git |
| 19 | `.gitignore` di root: aggiungere `.kotlin/`, `.wrangler/`, `app/release/`, `*.aab`, `*.apk`, `node_modules/` | `.gitignore` |
| 20 | Eliminare i 37 branch remoti obsoleti; adottare tag di release | — |
| 21 | Consolidare 12 markdown (3848 righe) in `README.md` + `docs/` reale; aggiornare i comandi wrangler v2 deprecati | Root |
| 22 | Rimuovere `.kotlin/errors/*.log` e `.idea/` da git | — |
| 23 | Sostituire `ExampleUnitTest.kt` / `ExampleInstrumentedTest.kt` (template mai toccati) | `app/src/test`, `app/src/androidTest` |

### Priorita bassa — qualita

| # | Intervento | Stato |
|---|---|---|
| 24 | Migrare `AppLocale.kt` (1019 righe di getter) verso `strings.xml` + `stringResource` — grosso, valutare se ne vale la pena ora che il catalogo e IT-only | **Investigata 2026-09-09, sospesa su decisione utente — portata reale molto piu' grande del previsto, vedi checkpoint in fondo** |
| 25 | Portare la copertura test al 50% (target gia dichiarato in `README_TESTING.md`, mai raggiunto) | Non toccata |
| 26 | Tradurre le pagine `docs/` in inglese o dichiarare l'italiano come lingua ufficiale | **Fatto 2026-09-09** — dichiarato l'italiano lingua ufficiale, vedi checkpoint sez. 4.5 |
| 27 | Convenzione commit unica (oggi misto IT/EN, prefissi `- `) | Non toccata — nessun rischio ad adottarla da qui in avanti, non tocca commit passati |

---

## 9. Verifica

### Per milestone

| Milestone | Come si verifica |
|---|---|
| M0 | `./gradlew assembleDebug` passa; CI verde su `master`; inventario Cloudflare scritto |
| M1 | `curl api-staging.pokevault.app/v1/health` -> 200; `wrangler d1 execute --command "SELECT 1"` |
| M2 | Report copertura: numero carte IT presenti/mancanti per i 3 set pilota; immagini raggiungibili su `img.pokevault.app` |
| M3 | Confronto risposta `/v1/sets/me05/cards` vs dati attesi; p95 < 100 ms su 100 richieste |
| M4 | Build debug con flag ON: Pokedex, SetDetail, Collection, Scanner, DeckLab tutti funzionanti; **test strumentato di migrazione Room 1->2 verde** |
| M5 | Forzare `workflow_dispatch` su un set: il delta viene applicato e `catalog_version` incrementa |
| M6 | Beta: crash-free rate ≥ 99,5%; costi Cloudflare entro $5/mese; nessuna regressione segnalata |
| M7 | Suite test verde; APK piu leggero (rimozione TFLite misurabile); repo sotto i 50 MB |

### Test end-to-end del sistema automatico

1. Aggiungere un set fittizio a `scope.json` e lanciare `workflow_dispatch`
2. Verificare: righe in D1, oggetti in R2, `catalog_version` incrementata, issue GitHub aperta
3. Aprire l'app: il nuovo set compare **senza aggiornamento dell'app**
4. Simulare copertura < 90%: il set resta `published = 0` e invisibile
5. Portare la copertura sopra soglia: il set si pubblica da solo alla run successiva
6. Inserire una riga in `takedowns`: il Worker smette di servire il set entro 5 minuti

### Regressioni da controllare esplicitamente in M4

Scanner OCR (risoluzione carta da foto), import decklist in DeckLab, conteggi di completamento set, statistiche di collezione, wishlist, album obiettivo. Sono tutti percorsi che oggi dipendono dagli ID PokeWallet: la mappatura verso i nuovi ID italiani va verificata uno per uno.

---

## 📍 CHECKPOINT — 2026-09-07, mattina (M1 sostanzialmente completato)

> Sessione ripresa dal checkpoint di ieri sera. Ambiente confermato intatto. Progressi sostanziali su M1 (D1) e M2 (ricompressione immagini). Tutto verificato con comandi reali, nessuna ipotesi.

### D1 — creato, popolato, verificato funzionante

- Database `pokevault-catalog` creato (id `2a219637-d11d-42fe-b888-8b95f2d7e6f7`), schema in **`schema/001_init.sql`** applicato: 8 tabelle (`expansions`, `cards`, `card_prices`, `set_map`, `sync_runs`, `takedowns`, `api_budget`, `catalog_meta`)
- **Importate tutte le 15.406 carte + 106 espansioni** dal blob JSON esistente, via **`scripts/import-catalog-to-d1.mjs`** (nuovo script, genera SQL batchato — batch da 50 righe: D1 rifiuta batch da 300 con `SQLITE_TOOBIG`, e rifiuta `BEGIN TRANSACTION`/`COMMIT` espliciti, gestisce l'atomicita da solo)
- Query verificate: **~1ms** per lettura, contro i 10,4 MB del blob attuale
- **Binding aggiunto**: `wrangler.toml` (`d1_databases`) + `Env.pokevault_catalog` in `src/index.ts` — **non deployato**

### Bug reale trovato e corretto: doppia codifica UTF-8 -> Windows-1252 (non Latin-1)

Il catalogo sorgente ha ~500+ stringhe corrotte (`"PokÃ©mon"` invece di `"Pokémon"`, `"unâ€™Energia"` invece di `"un'Energia"`). Investigazione:
- Prima ipotesi (Latin-1) sbagliata: il repair andava a vuoto su ~500 stringhe che mischiavano caratteri gia corretti e sequenze corrotte nello stesso testo
- Causa reale: **Windows-1252**, non Latin-1 (differiscono nel blocco 0x80-0x9F: cp1252 ha lì `€ ‚ ƒ „ … † ‡ ˆ ‰ Š ‹ Œ Ž ' ' " " • – — ˜ ™ š › œ ž Ÿ`, Latin-1 ha codici di controllo)
- Fix in **`scripts/import-catalog-to-d1.mjs`** (`repairMojibake()`): ripara solo le **sequenze** che sembrano UTF-8 mal decodificato (lead byte + continuation), non l'intera stringa — cosi non tocca i caratteri gia corretti. Include mappa esplicita CP1252 per il blocco 0x80-0x9F
- Bug collaterale trovato durante la verifica: il campo `danno` degli attacchi (es. `"30×"` per attacchi moltiplicatori) non veniva riparato — corretto
- **Verificato: 0 residui reali** su tutte le 15.406 carte (i 2 "falsi positivi" rilevati in verifica erano testo italiano legittimo: `abilità"` — à corretto seguito da virgolette corrette, non mojibake)

### API `/v1/*` su D1 — costruite e testate, non deployate

Nuove funzioni in `src/index.ts` (`handleV1ApiRequest` + helper), **puramente additive**: non toccano nessuna route esistente, non richiedono il binding KV/CACHE.

| Route | Verificata con |
|---|---|
| `GET /v1/health` | `wrangler dev --remote` + curl -> `{"status":"ok","catalog_version":1}` |
| `GET /v1/expansions` | Lista tutte le espansioni `published=1` |
| `GET /v1/expansions/{id}/cards` | Testato su `me04` -> carte corrette con `attacchi_json` |
| `GET /v1/cards/{cardId}` | Testato su `DP1_IT_1.png` -> record completo, encoding corretto |

Testate con **bindings reali** (D1, R2, KV) via `wrangler dev --remote`, non mock. Type-check pulito (`npm run type-check`).

### Ricompressione WebP — pipeline corretta, RUN COMPLETA IN CORSO

Percorso di debug (utile se si ripresenta): wrangler v3 crashava nativamente su Windows su *qualsiasi* `r2 object get/put`; **causa reale scoperta**: non era instabilita di v3, era l'uso accidentale dello **storage locale simulato** invece di quello remoto (mancava `--remote`, obbligatorio esplicito su v4). Con `--remote` sempre presente, **concorrenza 8 e stabile e corretta** (validata su 175 carte reali, 0 fallimenti).

Scoperti e risolti **3 pattern di chiavi R2 diversi** per le immagini (verificato sondando R2 direttamente, non ipotizzato):
| Set | Pattern reale |
|---|---|
| Standard (DP1, XY1, SM1, SWSH1, ME04, ...) | `it/{SET}/{SET}_IT_{numero}.png` |
| SVP (promo) | `it/SVP/{numero}.png` (cartella maiuscola, numero nudo) |
| MEP (promo) | `it/mep/{numero}.png` (cartella **minuscola**, numero nudo) |

`scripts/recompress-webp.mjs` ora prova tutte le combinazioni (2 case cartella × 2 pattern nome), con retry su errori transitori (non su "key not found", permanente).

**Run completa lanciata in background** (15.406 carte, concorrenza 8, log in `recompress-full-run.log` + `scripts/recompress-progress.ndjson` per resume). A metà mattina: **>125 carte, 0 fallimenti**. Upload additivi (`.webp` accanto a `.png` esistente, nessuna cancellazione) — sicuro anche se interrotta a meta.

### Non ancora fatto — decisioni in sospeso per l'utente

1. **Nessun deploy del Worker**: le modifiche a `src/index.ts` (binding D1, route `/v1/*`, priorita `.webp` su `.png`) sono solo locali. Le route `/v1/*` sono a rischio zero (additive). La riscrittura priorita webp/png **cambia comportamento delle route esistenti** (in meglio, ma e' pur sempre un cambio in produzione) — da confermare prima di `wrangler deploy`
2. **Import-catalog-to-d1.mjs e' one-shot**: se il blob JSON cambia (nuove carte caricate) va rilanciato manualmente. Non ancora integrato in un'automazione (quello e' M2/M5 dell'architettura originale: ingest automatico da TCGdex per i set futuri)
3. **`sort_order` e `logo_key` in `expansions`**: non c'e' un manifest separato nel payload R2 attuale con l'ordine reale dei set — tutti a `100` per ora. L'ordinamento cronologico corretto e' un follow-up, non blocca la migrazione strutturale
4. Al termine della run immagini: verificare il risparmio finale reale (stimato ~85-87% su ~2,49 GB -> ~320-370 MB) e decidere se/quando aggiornare l'ordine `imageExtensions` nel Worker per usare i `.webp` appena caricati

---

## 📍 CHECKPOINT — 2026-09-07, pomeriggio (deploy in produzione + run immagini completata)

### Deploy eseguito (con conferma esplicita dell'utente dopo revisione diff)

L'utente ha rivisto il diff completo (`wrangler.toml` + `src/index.ts`) prima di autorizzare. `wrangler deploy` eseguito con successo. **URL reale del Worker, ora noto**: `https://pokevault-proxy.pokevault-emanu.workers.dev`.

Verificato in produzione, tutto confermato funzionante:
- Route `/v1/health`, `/v1/expansions`, `/v1/cards/{id}` -> 200
- Route esistenti (`/ita/catalog.json`, `/ita/prices.json`, `/images/it/*`) -> 200, nessuna regressione
- **Priorita webp/png funziona esattamente come progettato**: carta gia convertita (`SVP_IT_1`) servita come `.webp`; carta non ancora convertita al momento del test (`SV10_IT_1`) servita come `.png` — fallback automatico confermato con richieste reali, non ipotizzato

### Run completa di ricompressione — TERMINATA

| Metrica | Valore finale |
|---|---|
| Carte totali | 15.406 |
| Convertite con successo | **15.059** |
| Fallite | 347 |
| Peso PNG originale | 2.307,5 MB |
| Peso WebP | 335,8 MB |
| **Risparmio reale** | **85,4%** |
| Durata | Circa 6 ore e mezza (avviata ~09:23, terminata ~15:53), rallentata nel tempo dall'overhead cumulativo della CLI wrangler (~60 carte/minuto in media nella seconda meta, misurato con campionamenti diretti, non stimato) |

### I 347 fallimenti — analizzati a fondo, DUE cause distinte (non una sola come pensato inizialmente)

**A) Bug "prefisso letterale nel numero carta" — 217 carte** (SWSH45 Shiny Vault 122, SWSH9/10/11 Trainer Gallery 30+30+30, piu singoli in ME01/SWSH12PT5/XY1/SM2/BW2). Immagine ESISTE su R2 ma sotto una chiave diversa da quella attesa (numero nudo, non `{SET}_IT_{numero}`). Causa radice gia identificata e documentata in dettaglio sopra (bugfix #7, sez. 8): `normalizeCardNumber()` nel Worker rimuove i prefissi letterali (`SV`, `TG`) prima di cercare la chiave — bug di PRODUZIONE preesistente che causa anche la visualizzazione della carta sbagliata per gli utenti, non solo il fallimento della ricompressione.

**B) BW4 "Next Destinies" — 94 carte, causa DIVERSA, scoperta oggi**: non e' il bug del prefisso (i numeri sono nudi, nessun prefisso letterale coinvolto). Verificato direttamente contro il Worker live: `BW4/1` e `BW4/100` esistono (200), `BW4/2`, `BW4/5`, `BW4/50` no (404) — **copertura italiana genuinamente incompleta e a macchia di leopardo per questo set storico**, non un bug di codice. E' esattamente lo scenario che la regola di copertura a soglia (sez. 2.2 del piano: pubblica solo sopra 90%, placeholder per le mancanti, auto-guarigione ai run successivi) e' pensata per gestire.

**C) 36 fallimenti transitori** (blip di rete, un timeout 504) — passaggio di pulizia lanciato subito dopo la fine della run principale, sfruttando la resumibilita' (il file di progresso segna solo le carte riuscite come "fatte": rilanciare lo stesso comando ritenta automaticamente tutte le altre 347, senza toccare le 15.059 gia buone).

### Aggiornamento alla stima di rischio del bugfix #7 (sez. 8)

La portata reale e' **maggiore** di quanto scritto inizialmente: non "5-6 espansioni per qualche centinaio di carte" ma **almeno 217 carte confermate su 6 espansioni diverse**, con SWSH45 (Shiny Vault) da solo per 122. Il fix resta bene isolato (una sola funzione, `normalizeCardNumber()`) ma l'impatto utente-visibile (carta sbagliata mostrata) e' piu esteso di quanto stimato.

### Bugfix #7 — RISOLTO E DEPLOYATO (2026-09-07, stesso giorno della scoperta)

Su richiesta esplicita dell'utente ("fixa ora"), il bug e' stato corretto lo stesso giorno invece di essere rimandato a M7.

**Fix applicato** in `buildItalianCardKeyCandidates()` (`src/index.ts`): quando il numero carta contiene lettere (non e' puramente numerico, `/^\d+$/`), i candidati "solo cifre" (`normalized`, `padded`) non vengono piu generati affatto — solo le varianti che preservano il testo originale (raw, maiuscolo, minuscolo). Per i numeri puramente numerici il comportamento e' identico a prima (nessuna regressione). Confermato con `normalizeCardNumber()` usata in **un solo punto** nel file (non condivisa con la logica prezzi, che ha una funzione propria gia corretta) — blast radius piccolo come sperato.

**Verificato con richieste reali** (`wrangler dev --remote`, poi in produzione dopo deploy):
| Caso | Prima | Dopo |
|---|---|---|
| `SWSH45/SV001` (bug) | 200, immagine di Yanma (sbagliata) | **404** |
| `SWSH10/TG22` (bug) | 200, immagine sbagliata | **404** |
| `SWSH45/1` (Yanma, carta reale) | 200 | 200 (invariato) |
| `DP1/1`, `DP1/007` (casi normali) | 200 | 200 (invariato) |
| `SVP/1` (promo) | 200 | 200 (invariato) |

**Lezione operativa importante, riemersa oggi**: il primo test in produzione post-deploy ha dato un falso negativo — `SWSH45/SV001` continuava a rispondere 200 con l'immagine sbagliata **anche dopo il deploy del fix**, perche' il Worker cachea le risposte immagine in **KV** (non Cache API — verificato leggendo il tipo, e' `env.CACHE: KVNamespace`) con TTL 90 giorni, e la risposta sbagliata era gia in cache da prima. **Deployare un fix di logica non invalida automaticamente le risposte gia cachate.** Risolto con `wrangler kv bulk delete` (162 chiavi identificate via `kv key list --prefix "pokewallet:/images/it/{SET}/"` sui set coinvolti, poi cancellate in blocco) — dopo la purge, `X-Cache-Status: MISS` confermato e risposta corretta (404) servita fresca. **Da tenere a mente per ogni futuro fix che tocca `buildItalianCardKeyCandidates`/`handleItalianR2AssetRequest`: serve sempre un purge mirato delle chiavi KV interessate dopo il deploy, altrimenti il fix resta invisibile fino a naturale scadenza TTL (90 giorni).**

**Cosa resta aperto**: il fix previene la visualizzazione della carta SBAGLIATA, ma le 311 immagini (217 bug + 94 BW4) restano assenti — servono ancora gli asset reali (fonte da individuare, probabilmente la stessa usata per il resto del dataset storico) per completare la copertura, non solo il fix di codice.

---

## 📍 CHECKPOINT — 2026-09-07, tardo pomeriggio (prototipo ingest automatico TCGdex, primo set reale importato)

### Commit e push eseguiti

Tutto il lavoro di M1/M2 di oggi (schema D1, script import/ricompressione, modifiche Worker inclusi i due fix) e' stato committato (`242ce3a`) e pushato su `origin/master`. `app/google-services.json` lasciato intenzionalmente fuori (modifica pre-esistente non correlata, non nostra).

### Verifica copertura storica: confronto TCGdex vs D1

Confrontati i 106 id espansione in D1 con l'elenco completo TCGdex (esclusi Pokemon TCG Pocket `A#`/`B#`, una linea di prodotto digitale separata dal cartaceo). La maggior parte delle differenze erano **falsi positivi da convenzioni di naming diverse** (es. TCGdex `sv03.5` vs nostro `sv3pt5`, TCGdex `swsh10.5` vs nostro `pgo` per lo stesso set Pokemon GO) — non vale la pena inseguire una riconciliazione perfetta di ogni alias storico. L'unico gap **reale e attuale** confermato: **`me05` "Buio Pesto" (uscito 2026-07-17) assente da D1** — esattamente il caso che l'automazione ingest deve intercettare.

### Script `scripts/ingest-tcgdex-set.mjs` — creato, validato su un set reale

Prototipo funzionante del job di ingest automatico (architettura sez. "Come applicarlo ai set futuri" di questa conversazione): TCGdex viene contattato **solo qui**, mai dal Worker/app.

Flusso: fetch set summary + dettaglio per ogni carta da TCGdex -> verifica esistenza immagine IT (HEAD) -> calcola copertura -> se >= 90% pubblica il set, sotto soglia scrive comunque tutto ma con `published=0` -> carica immagini su R2 (gia WebP nativo da TCGdex, **zero conversione necessaria** a differenza del dataset storico) -> upsert D1 (`ON CONFLICT DO UPDATE`, idempotente, rilanciabile in sicurezza).

Modalita `--apply` vs dry-run (default): permette di vedere il risultato prima di scrivere qualunque cosa.

**Bug trovato e corretto durante il primo test reale**: il controllo di esistenza immagine (`headOk`, HEAD request singola) dava **falsi negativi sotto concorrenza** — due run consecutivi sullo stesso identico set hanno dato 85% e poi 43,3% di copertura, con carte segnate "missing" che in realta esistevano (verificato con curl diretto). Causa probabile: contesa sul pool di connessioni tra le fetch di dettaglio (`api.tcgdex.net`) e i controlli HEAD (`assets.tcgdex.net`) a concorrenza 6. **Fix**: retry (3 tentativi, backoff crescente) + timeout esplicito (8s, `AbortController`) su `headOk()`. Ri-testato: risultato stabile e identico su due run consecutive (102/120, stessa lista di 18 mancanti) dopo il fix.

**Primo ingest reale eseguito** (`node scripts/ingest-tcgdex-set.mjs me05 --apply`):
| Metrica | Valore |
|---|---|
| Carte totali | 120 |
| Con immagine IT | 102 (85,0%) |
| Immagini caricate su R2 | 102/102, 0 falliti |
| `published` | **0** (sotto soglia 90%) |

**Verificato end-to-end in produzione**:
- `GET /v1/cards/ME05_IT_001.webp` -> dati completi e corretti (Tropius, tipo Erba, PS 110, attacco "Aroma Fruttato" con testo italiano integro)
- `GET /images/it/ME05/ME05_IT_001?size=low` -> 200, immagine reale
- `GET /v1/expansions` -> **me05 correttamente ASSENTE** dall'elenco (published=0) — la regola di soglia funziona esattamente come progettato: la carta e' gia risolvibile singolarmente, ma il set non appare nel Pokedex finche' la copertura non sale

**Falso allarme, non un bug**: durante la verifica e' sembrato esserci un carattere di sostituzione (`Pok�`) nella descrizione di un attacco — indagine ha confermato che i dati in D1 sono **perfettamente puliti** (verificato con query diretta a D1, non solo via HTTP); l'artefatto era dovuto a `curl | head -c 300` che tagliava a meta un carattere UTF-8 multi-byte (`é`) nel comando di test, non ai dati reali.

### Prossimi passi naturali per l'automazione

1. Generalizzare lo script per iterare su **piu' set** in un colpo solo (oggi prende un solo `setId` per invocazione)
2. Aggiungere il confronto "cosa c'e' su TCGdex che non abbiamo ancora" come step automatico (oggi fatto a mano una tantum in questa sessione)
3. Wrappare in un GitHub Actions workflow schedulato (M5 del piano originale) — lo script stesso e le credenziali Cloudflare (API token, non piu OAuth interattivo) sono l'unico pezzo mancante per renderlo eseguibile senza intervento umano
4. Il set `me05` va ri-controllato periodicamente (la copertura potrebbe salire sopra il 90% quando TCGdex completa le 18 carte mancanti) — oggi va rilanciato a mano, in futuro lo fara' il cron

---

## 📍 CHECKPOINT — 2026-09-07, sera (automazione completa: scoperta + workflow schedulato)

Punti 1-3 della lista sopra completati nella stessa sessione.

### `schema/002_add_release_date.sql` — nuova colonna `release_date` su `expansions`

Applicata direttamente in produzione (`ALTER TABLE`), poi backfillata per `me05` (`2026-07-17`). Serve a due cose: ordinamento cronologico futuro del Pokedex (oggi `sort_order` e' fisso a 100 per tutti), e soprattutto come **cutoff per la scoperta di set nuovi**.

### `scripts/discover-new-sets.mjs` — confronto TCGdex vs D1, risolto il problema degli alias storici

Primo tentativo (confronto per id, escludendo Pokemon TCG Pocket `A#`/`B#`, Trainer Kit `tk-*`, promo McDonald's `20XXxx`): **61 falsi positivi** — quasi tutti alias storici dello stesso identico set gia' presente sotto un altro nome (es. TCGdex `sv03.5` = nostro `sv3pt5`, TCGdex `swsh10.5` "Pokemon GO" = nostro `pgo`). Confermato controllando le date di uscita: tutti i 61 candidati erano precedenti o uguali al 2026-07-17 (la data di `me05`, il nostro set piu' recente).

**Fix strutturale, non un elenco di eccezioni**: un candidato conta come "davvero nuovo" solo se la sua `releaseDate` su TCGdex e' **successiva** alla piu' recente gia' in D1 (`SELECT MAX(release_date)`). Qualunque cosa uscita prima o nello stesso periodo del nostro storico e', per costruzione, gia' coperta sotto un nome diverso — non serve indovinare quale. Ri-testato dopo il fix: **0 falsi positivi**, i 61 candidati correttamente scartati, 0 set genuinamente nuovi trovati (corretto: `me05` e' gia' il piu' recente).

### `.github/workflows/catalog-ingest.yml` — workflow schedulato, creato e validato staticamente

Cron giornaliero (`0 6 * * *`) + trigger manuale (`workflow_dispatch`). Esegue `discover-new-sets.mjs --ingest`; se trova set nuovi, apre automaticamente una issue di riepilogo. YAML validato con un parser reale (`npx js-yaml`), non solo letto a occhio.

**Bug trovato e corretto in fase di costruzione**: il workflow usava `npm ci`, che richiede un `package-lock.json` — assente perche' il `.gitignore` del worker lo escludeva (debito tecnico gia' segnalato in sez. 8, voce implicita). **Risolto**: lockfile generato ieri durante gli `npm install`, rimosso dal `.gitignore` e committato. Verificato in locale: `npm ci` pulito (nessun blocco sugli `allowScripts`, dato che l'approvazione e' salvata in `package.json` e viaggia con il lockfile) + `type-check` pulito con l'ambiente ricostruito da zero.

### ⚠️ Azione richiesta dall'utente — unica cosa che non posso fare io

Il workflow e' pronto ma **non puo' ancora girare**: serve il secret `CLOUDFLARE_API_TOKEN` nel repository GitHub, che richiede un'azione manuale sulla dashboard (stessa natura del `wrangler login` di ieri — non automatizzabile da qui):

1. `dash.cloudflare.com` -> **My Profile** -> **API Tokens** -> **Create Token**
2. Permessi minimi necessari: **Account > D1 > Edit**, **Account > Workers R2 Storage > Edit** (account: quello con id `e6c4d1ff864abf6dbcb4ec1e0de6b34a`, `emanuelebuia@live.it`)
3. Copiare il token generato
4. Sul repository GitHub (`EmaBuiaDev/pokevault`): **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**, nome `CLOUDFLARE_API_TOKEN`, valore il token copiato

Senza questo secret il workflow schedulato fallisce silenziosamente ogni notte (o va disabilitato/non schedulato finche' non e' pronto). Il primo run va idealmente osservato manualmente (tab Actions -> "Run workflow") per confermare che funzioni prima di fidarsi del cron automatico.

---

## 📍 CHECKPOINT — 2026-09-07, sera (secret aggiunto, workflow verificato end-to-end in CI reale)

L'utente ha aggiunto `CLOUDFLARE_API_TOKEN` su GitHub. Verifica fatta lanciando il workflow via API (`workflow_dispatch`, autenticato riusando la credenziale git gia' configurata in locale — nessun nuovo token creato), non solo controllando che il file YAML fosse sintatticamente corretto.

### Bug reale trovato al primo run e corretto

Il primo dispatch e' apparso **"success"** ma **non aveva fatto nulla**: due problemi distinti, entrambi silenziosi.

1. **Wrangler 4.x richiede Node >=22**; il workflow pinnava `node-version: '20'` (mai testato in questa combinazione perche' in locale si usa Node 24). Lo script falliva al primo comando (`wrangler d1 execute`) con un errore di versione.
2. **Il fallimento non si propagava**: `node ... | tee ingest-output.log` senza `set -o pipefail` fa si' che bash riporti l'exit code dell'**ultimo** comando della pipe (`tee`, che riesce sempre a scrivere il file anche se e' vuoto o contiene un messaggio di errore), non quello di `node`. Il job segnava "success" nonostante l'ingest non fosse mai partito — esattamente il tipo di fallimento silenzioso che rende un'automazione inaffidabile senza controlli.

**Fix**: `node-version: '22'` + `set -o pipefail` prima della pipe. Committato, pushato, e **ri-verificato con un secondo dispatch reale**.

### Verifica finale — output CI identico a quello del test locale

```
D1: 107 espansioni presenti, data di uscita piu' recente conosciuta: 2026-07-17
TCGdex: 191 espansioni totali
Candidati per id: 61
Scartati (non piu' recenti di 2026-07-17): 61
Set DAVVERO nuovi: 0
```

Confermato anche: **nessuna issue aperta** creata (comportamento atteso con 0 set nuovi — la condizione dello step "apri issue" e' stata valutata correttamente).

**L'automazione e' ora genuinamente operativa**: gira ogni giorno alle 06:00 UTC, e la prossima volta che TCGdex pubblica un set con `releaseDate` successiva al 2026-07-17, verra' rilevato, valutato per copertura, importato in D1+R2 e segnalato con una issue — senza intervento umano, verificato end-to-end e non solo "dovrebbe funzionare".

---

## 📍 CHECKPOINT — 2026-09-07, sera (M4 ridimensionato: collegato l'endpoint principale a D1, zero rischio Android)

### Scoperta che cambia la valutazione di rischio di M4 rispetto al piano originale

Il piano originale (scritto guardando `master`, prima di adottare `release/R2.0.21`) assumeva che M4 richiedesse toccare `PokeVaultDatabase` (Room) e introdurre un flag lato client con doppia implementazione. Verificato oggi che **non e' cosi'**: il percorso dati reale in produzione (`ItalianCatalogRemoteRepository`) non usa Room affatto — cachea il JSON grezzo in `SharedPreferences` (`italian_catalog_cache_v3`, TTL 5 min). Room/`PokeVaultDatabase` resta usato da `PokeTcgRepository` per altri scopi (prezzi PokeWallet, cache set/carte legacy), ma **non e' nel percorso critico del catalogo italiano**. Il rischio piu' grosso segnalato nel piano originale (migration Room) semplicemente non si applica a questo pezzo.

### Vincolo scoperto e usato a proprio vantaggio: `ITALIAN_CATALOG_URL` e' compilato nell'app

E' un valore `BuildConfig` letto da `local.properties` in fase di build — **non modificabile da remoto** per chi ha gia' installato l'app (a differenza di un vero feature-flag remoto, che qui non esiste). Questo elimina l'opzione "cambiamo url o flag e basta": l'unico modo per far arrivare D1 a un'installazione esistente e' cambiare cosa risponde **la stessa identica URL** lato server.

### Fix: `/ita/catalog.json` ora generato da D1, deployato e verificato

`buildCatalogJsonFromD1()` in `src/index.ts`: se il binding D1 e' disponibile e la query restituisce righe, genera la risposta da li' (solo carte di espansioni `published=1`); altrimenti ripiega esattamente sul comportamento precedente (lettura del blob R2) — **impossibile che diventi meno affidabile di oggi**.

**Verificato prima del deploy** (`wrangler dev --remote`), confrontando byte per byte con la produzione live:
- 15.406 carte su entrambi i lati, stesso insieme esatto di id — 0 mancanti, 0 duplicate
- `me05` correttamente assente (sotto soglia 90%)
- Le uniche differenze trovate: il fix di encoding CP1252 di ieri (`PokÃ©mon` -> `Pokémon`) ora si riflette nella risposta live, cosa che il vecchio blob non aveva mai avuto — un miglioramento silenzioso, non una discrepanza

**Deployato e verificato in produzione**. Nota tecnica utile per il futuro: subito dopo il deploy, la cache KV ha continuato a servire la vecchia risposta per ~20-30 secondi nonostante il purge esplicito della chiave — **KV di Cloudflare e' eventually-consistent** (fino a ~60s di propagazione dichiarati), non serve preoccuparsi se un purge non sembra avere effetto immediato, basta ri-controllare dopo una breve attesa.

**Effetto pratico**: gli utenti con l'app gia' installata vedono ora testo italiano corretto (niente piu' mojibake) e, da qui in avanti, qualunque set che l'automazione pubblica (sopra soglia 90%) compare nel loro Pokedex **senza aggiornare l'app**. E' il primo punto in cui il lavoro infrastrutturale di questa sessione arriva davvero a un utente finale.

### Soglia di copertura abbassata da 90% a 80% (decisione utente)

Dopo aver verificato che `me05` (85% di copertura) restava correttamente nascosto sotto la vecchia soglia del 90%, l'utente ha deciso di abbassarla all'80% per vederlo comparire subito. `COVERAGE_THRESHOLD` in `scripts/ingest-tcgdex-set.mjs` aggiornato (usato per tutti i prossimi ingest); `me05` aggiornato direttamente in D1 (`published=1`, gia' a 0,85 ≥ 0,80) senza rilanciare l'ingest. **Verificato in produzione**: tutte le 120 carte di Buio Pesto ora presenti in `/ita/catalog.json` e in `/v1/expansions`. Regola non rimossa, solo ricalibrata: un set sotto l'80% resta comunque nascosto.

### Cosa NON e' stato fatto (deliberatamente, fuori scope per oggi)

Il vero passo successivo di M4 previsto dal piano — cambiare il **pattern di fetch** dell'app (da "scarica tutto il catalogo in un colpo" a "carica per espansione, on-demand", usando `/v1/expansions` + `/v1/expansions/{id}/cards`) — resta da fare. E' un cambiamento di codice Kotlin vero e proprio (non solo backend), con un ciclo di verifica piu' lento (build Gradle, non `curl`), e va affrontato come lavoro a se stante quando si decide di aprirlo.

---

## 📍 CHECKPOINT — sessione successiva (fetch on-demand per SetDetail, parziale e motivato)

Ripresa da una sessione remota (container cloud, non la macchina Windows delle sessioni precedenti). **Nessun accesso a `dl.google.com`** dalla rete di questo ambiente (bloccato dalla policy di rete del container): `./gradlew :app:compileDebugKotlin` non e' eseguibile qui, a differenza delle sessioni precedenti sulla macchina Windows. Le modifiche sotto sono state verificate a mano, rilettura riga per riga del diff e dei tipi coinvolti, **non con una build reale** — da confermare con `./gradlew :app:compileDebugKotlin` (JDK 21) prima di mergiare.

### Cosa e' stato implementato

Nuovi metodi `ItalianCatalogRemoteRepository.getExpansions()` (`GET /v1/expansions`) e `getExpansionCards(expansionId)` (`GET /v1/expansions/{id}/cards`), con lo stesso pattern di cache a due livelli (memoria + `SharedPreferences`, TTL 5 min, fallback su stale se la rete fallisce) gia' usato da `getCatalog()`. L'URL base e' derivato da `BuildConfig.ITALIAN_CATALOG_URL` togliendo il suffisso `ita/catalog.json` (nessun nuovo campo `BuildConfig`, nessuna modifica a `build.gradle.kts`/`local.properties` — stesso host del Worker gia' configurato).

`getCardsByItalianSet()` (il percorso reale di `SetDetail`, la schermata aperta ripetutamente dagli utenti) ora chiama `getExpansionCards(expansionId)` invece di scaricare l'intero blob da 10+ MB e filtrarlo in locale con `catalog.cardsByExpansion()[expansionId]`. `expansionId` si ottiene da `parseItalianExpansionId(setId)`, un parsing di stringa puro — nessuna dipendenza dal catalogo completo lungo questo percorso, verificato leggendo tutta la funzione fino in fondo (incluso `resolveEnglishBaseSetIdForItalianSet`, che usa la cache dei set ENG, non quella ITA).

### Cosa NON e' stato convertito, e perche' (non e' stato dimenticato)

`mergeItalianSets()` (la lista set, aperta all'avvio app) e `getItalianOverlayCards()` restano su `getCatalog()` (blob intero). Motivo verificato leggendo il codice, non assunto: entrambe le funzioni derivano il "set base ENG" da collegare (per logo/nome/serie) contando `imageReference()?.setCode` **su tutte le carte dell'espansione**, per le ~90 espansioni storiche (`dp1`, `xy1`, `bw1`, `sm1`, `swsh1`, ...) che non hanno una entry in `preferredBaseSetCodeForItalianExpansion()` (quella mappa copre solo `me01-04`/`sv01-10` e varianti). `GET /v1/expansions` oggi restituisce solo `id, card_count, sort_order, logo_key` — non il "codice set dominante" — quindi sostituire la fonte qui perderebbe silenziosamente la corrispondenza corretta con logo/nome/serie ENG per la maggioranza storica del catalogo, un rischio non verificabile senza una build+test reale su device. Per fare questo pezzo in modo sicuro serve prima un campo aggiuntivo lato Worker/D1 (es. `dominant_set_code` precalcolato in `expansions`), lavoro non fatto oggi.

### Prossimi passi

1. **Verificare con una build reale** (`./gradlew :app:compileDebugKotlin`, poi test strumentato/manuale di `SetDetail` su almeno un set con mapping esplicito e uno senza, es. `sv10` e `dp1`) prima di considerare il pezzo fatto oggi definitivo
2. Se si vuole completare anche `mergeItalianSets`/`getItalianOverlayCards`: aggiungere `dominant_set_code` a `expansions` in D1 (calcolato una volta in ingest, non a ogni richiesta), esporlo in `/v1/expansions`, poi ripetere la stessa conversione fatta oggi per queste due funzioni
3. Le funzioni di ricerca (`searchItalianCardsByName/ByNumber`, `searchItalianScannerCandidates`) restano intenzionalmente sul catalogo completo: hanno bisogno di scansionare tutte le carte per il matching fuzzy, e il Worker non espone oggi un endpoint di ricerca full-catalog lato server

---

## 📍 CHECKPOINT — sessione successiva (dominant_set_code: mergeItalianSets e getItalianOverlayCards completati)

Fatto il punto 2 della lista sopra: `mergeItalianSets()`, `getItalianOverlayCards()`/`resolveItalianExpansionIdForSet()` e `getEnglishBaseCardForItalianOverlay()` (un quarto punto trovato rileggendo il file per intero, stesso pattern degli altri tre) **non usano piu' `getCatalog()` (blob intero)**. Con questo, l'unico consumo residuo del blob intero sono le 3 funzioni di ricerca (punto 3 sopra), lasciate cosi' deliberatamente.

### Cosa e' cambiato, lato server (preparato ma NON deployato -- vedi sotto)

- **`schema/003_add_dominant_set_code.sql`**: nuova colonna `expansions.dominant_set_code` -- il codice set ENG dominante di un'espansione (es. "DP1"), lo stesso valore che il Kotlin calcolava al volo scansionando le carte.
- **`scripts/import-catalog-to-d1.mjs`**: calcola `dominant_set_code` per le 106 espansioni storiche (stesso algoritmo del Kotlin: conteggio del prefisso `{SET}_IT_` per cardId, replicato in Node -- vedi commento `dominantSetCode()` nello script). **Testato con un catalogo sintetico di 3 carte/2 espansioni**: output SQL verificato a mano, `dominant_set_code` corretto per entrambe.
- **`scripts/ingest-tcgdex-set.mjs`**: per i set ingeriti da TCGdex il valore e' banale (`setId.toUpperCase()`, sempre lo stesso per costruzione del cardId) -- nessuno scan necessario, aggiunto come valore costante nell'INSERT.
- **`src/index.ts`**: `GET /v1/expansions` ora seleziona anche `dominant_set_code`. `npm run type-check` pulito.

### Cosa e' cambiato, lato Android

- `ItalianExpansionManifest` ha un nuovo campo `dominantSetCode: String?` (null per i catalog costruiti dal blob intero, che non lo calcolano piu' localmente -- solo i tre consumer sotto lo popolano, dal blob completo nessuno lo legge piu').
- `mergeItalianSets()`, `resolveItalianExpansionIdForSet()`/`getItalianOverlayCards()`, `getEnglishBaseCardForItalianOverlay()`: usano `manifest.dominantSetCode` invece di scansionare le carte dell'espansione, e recuperano i dati via `getExpansions()`/`getExpansionCards()` invece di `getCatalog()`.
- **Fallback esplicito e verificato per il rollout fuori ordine**: se il client aggiorna prima del backend (o il D1 non e' ancora stato ribackfillato con lo script sopra), `dominant_set_code` arriva `null` dal server. In quel caso tutte e tre le funzioni ricadono su `expansionId.uppercase()` -- lo stesso fallback che il codice usava gia' come ultima risorsa. Non e' un crash ne' un dato mancante, solo una risoluzione meno precisa per le espansioni storiche senza mapping esplicito finche' il backfill non gira. Confermato rileggendo ogni punto di uscita delle tre funzioni, non assunto.
- `ItalianCatalog.cardsByExpansion()` rimosso: dopo questi cambi non aveva piu' nessun chiamante (verificato con una grep sull'intero `app/src/main`).

### Cosa NON e' stato fatto (richiede l'utente)

1. **Migration D1 in produzione**: `schema/003_add_dominant_set_code.sql` va applicata (`wrangler d1 execute pokevault-catalog --remote --file schema/003_add_dominant_set_code.sql`) e poi va rilanciato `scripts/import-catalog-to-d1.mjs` contro il catalogo attuale per popolare `dominant_set_code` sulle 106 espansioni esistenti. Nessuna delle due e' stata eseguita qui: **questa sessione remota non ha credenziali Cloudflare** (`wrangler whoami` -> "You are not authenticated"), a differenza delle sessioni precedenti sulla macchina Windows dell'utente.
2. **Deploy del Worker**: `src/index.ts` con la nuova colonna in `/v1/expansions` non e' stato deployato, stesso motivo (nessuna credenziale).
3. **Build Android reale**: come nel checkpoint precedente, questo ambiente non raggiunge `dl.google.com` (bloccato dalla policy di rete del container) quindi l'Android Gradle Plugin non si risolve e `./gradlew` non parte qui. Tutte le modifiche Kotlin sono state rilette a mano con attenzione (inclusi gli import, che avevano bisogno di un fix -- `ItalianExpansionManifest` non era importato, `ItalianCatalog` era rimasto importato da inutilizzato), ma **non compilate**. Da verificare con `./gradlew :app:compileDebugKotlin` prima di considerare il lavoro definitivo.

### Ordine consigliato per chiudere il cerchio

1. Applicare `schema/003_add_dominant_set_code.sql` a D1 (`wrangler d1 execute ... --remote`)
2. Rilanciare `scripts/import-catalog-to-d1.mjs` sul catalogo corrente e applicare il SQL generato (backfilla `dominant_set_code` sulle 106 espansioni)
3. `wrangler deploy` del Worker aggiornato (dopo revisione diff, come per ogni deploy precedente di questa migrazione)
4. `./gradlew :app:compileDebugKotlin` + verifica manuale/strumentata di: lista set (nomi/loghi/serie invariati per un set con mapping esplicito tipo `sv10` e uno senza tipo `dp1`), `SetDetail` su entrambi, l'overlay ME03 (`preferredImageMacro = "ITA"`)

---

## 📍 CHECKPOINT — sessione successiva (punti 1-3 eseguiti in produzione, punto 4 ancora aperto)

Eseguiti i punti 1-3 sopra tramite `.github/workflows/ops-dominant-set-code.yml` (l'utente ha lanciato il workflow da GitHub Actions sul branch `release/R3.0.0`, io ho monitorato via API GitHub e corretto tre bug emersi durante l'esecuzione reale, non trovati nella sola rilettura statica):

1. `scripts/import-catalog-to-d1.mjs`: l'INSERT su `cards` non aveva `ON CONFLICT` (pensato per un DB vuoto) -> `UNIQUE constraint failed: cards.card_id` al primo re-run. Aggiunto `ON CONFLICT(card_id) DO UPDATE`, stesso pattern di `expansions` e di `ingest-tcgdex-set.mjs` -- lo script e' ora idempotente per intero, non solo per le espansioni.
2. Il workflow falliva duro su un secondo tentativo di `ALTER TABLE` (colonna gia' aggiunta dal run precedente) invece di trattarlo come "gia' fatto". Aggiunto un controllo esplicito su "duplicate column name" che tratta quel caso specifico come successo, senza mascherare altri errori wrangler.
3. Il default dello script (`--batch-size` 300) produceva un singolo INSERT troppo grande per D1 (`SQLITE_TOOBIG`) una volta sommato il blob `attacchi_json` di ogni carta -- coerente con la nota gia' in questo documento ("batch da 50, D1 rifiuta batch da 300"). Il workflow ora passa esplicitamente `--batch-size 50`.

**Verificato in produzione** (non solo "il job e' verde"): lo step di verifica ha confermato `"populated": 106` su 106 espansioni; lo smoke test su `/v1/expansions` mostra `dominant_set_code` popolato correttamente per ogni riga (es. `dp1` -> `"DP1"`, `bw4` -> `"BW4"`). Il Worker deployato mostra tutti i binding attesi (CACHE, pokevault_catalog, IMAGES_BUCKET). `release/R3.0.0` e `claude/continue-plan-fxtrod` sono allineati (fast-forward) con tutti questi fix.

**Cosa resta**: solo il punto 4 -- build Android reale (`./gradlew :app:compileDebugKotlin`) e verifica manuale/strumentata su device o emulatore, che questa sessione non puo' fare (nessun accesso a `dl.google.com`). Il codice Kotlin di oggi (`mergeItalianSets`, `getItalianOverlayCards`, `getEnglishBaseCardForItalianOverlay`) ora legge `dominant_set_code` da un backend che lo popola davvero -- il percorso "felice" del fallback e' quindi gia' coperto, ma la build resta da confermare prima di un rilascio.

---

## 📍 CHECKPOINT — sessione successiva (M7: pulizia git/repo + consolidamento documentazione)

Sessione remota, stesse limitazioni di quella precedente (nessuna credenziale Cloudflare, nessun accesso a `dl.google.com`). Lavoro scelto deliberatamente per non richiedere build Android ne' deploy: voci di sez. 8 verificabili per lettura/grep, non per compilazione.

### Git/repo hygiene (sez. 8, voci #3, #19, #22)

Rimossi dal tracking futuro (history esistente intatta, nessun `filter-repo`/BFG eseguito -- resta voce #18, esplicitamente rimandata: richiede force-push coordinato):
- `.wrangler/cache/wrangler-account.json` e `cf.json` -- **leak reale, non solo teorico**: il primo conteneva l'email dell'account Cloudflare, il secondo geolocalizzazione (Napoli), ISP (Vodafone Italia) e fingerprint TLS della sessione `wrangler login` di una sessione precedente
- `.kotlin/errors/*.log` (4 file), `.idea/` (13 file), `app/release/app-release.aab` (26 MB)
- `.gitignore` di root aggiornato con `.kotlin/`, `.wrangler/`, `.idea/`, `node_modules/`, `app/release/`, `*.aab`, `*.apk`

### Bugfix isolati (sez. 8, priorita' alta)

- `LimitlessTcgRepository`: `HttpLoggingInterceptor` ora `NONE` in release, `BASIC` solo in `BuildConfig.DEBUG` -- chiudeva il leak di log in produzione (voce #2)
- Rimossa `POKETCG_API_KEY`: campo `BuildConfig` mai letto in nessun punto del client (verificato con grep sull'intero `app/src`), coerente con debito #10 (sez. 1.2, "codice morto"). Ripulita anche da `local.properties.example`, dai workflow CI (`android-tests.yml`, `android-advanced-tests.yml`) e dai doc del worker. **`POKEWALLET_API_KEY` non toccata**: e' ancora attivamente letta (`CardsVaultTCGApp.kt`, `PokeTcgRepository.kt`, `PokeWalletRepository.kt`) -- rimuoverla ora, prima del taglio del cordone PokeWallet (M6), romperebbe funzionalita' live.

### Consolidamento documentazione (sez. 8 voce #21)

I 12 markdown ridondanti descritti in quella voce (3848 righe circa, in gran parte AI-slop con playbook duplicati) sono stati sostituiti da:
- **`README.md`** di root (non esisteva -- debito #14 chiuso): overview, struttura repo, stack, setup locale
- **`docs/TESTING.md`**: consolidamento di `TESTING_GUIDE.md`, `README_TESTING.md`, `TESTING_COMPLETE.md`, `TESTING_SUMMARY.md`, `QUICK_REFERENCE.md`, `TESTING_START_HERE.md`, `QUICK_START_TESTING.md`, `GITHUB_SETUP.md` -- comandi Gradle, template di test, setup branch protection, e il debito CI reale (sez. 1.2 #2/#3) citato esplicitamente invece di essere ripetuto in modo impreciso
- **`pokevault-proxy-worker/README.md`** riscritto da zero: la versione precedente (insieme a `DEPLOYMENT_GUIDE.md` e `QUICK_DEPLOY.md`, entrambi eliminati) descriveva il Worker come un semplice proxy-cache KV per PokeWallet `/sets` -- non rifletteva affatto D1, R2, le route `/v1/*` ne' gli script di ingest costruiti nelle sessioni precedenti. Il nuovo README descrive lo stato attuale reale e rimanda qui per la storia
- `CLOUDFLARE_PROXY_IMPLEMENTATION.md` (root) eliminato: descriveva un'architettura (proxy KV puro) ampiamente superata da questo stesso documento

Non toccato: contenuto dei test stessi (16 file reali in `app/src/test`+`app/src/androidTest`, molti di piu' dei "5 file/19 test" che i vecchi doc dichiaravano -- erano scritti prima che la suite crescesse).

### Non fatto, deliberatamente (troppo rischioso senza build/deploy disponibili qui)

- `FORCED_REAL_TOTALS_BY_SET_CODE` (voce #16): verificato che e' ancora nel percorso attivo dei conteggi PokeWallet legacy, non un residuo isolato come assumeva la voce -- rimuoverlo senza poter testare con wrangler e' rischioso, lasciato per una sessione con credenziali

### Approfondimento successivo: `FORCED_REAL_TOTALS_BY_SET_CODE`, indagine conclusa -- **non rimovibile**

Tracciata l'intera catena: `FORCED_REAL_TOTALS_BY_SET_CODE`/`BY_SET_ID` alimentano `resolveRealTotal()`, usata solo da `enrichSetsPayload()`/`enrichSetDetailPayload()` -- funzioni che intercettano le risposte delle route **legacy** `/sets` e `/sets/{code}` (sia sul path cache-HIT via `createResponseFromCache()` che su quello cache-MISS nel fetch handler principale). Queste route non sono mai state toccate dalla migrazione D1: la voce #16 del piano ("i conteggi ora sono nostri in D1") era basata su un'assunzione sbagliata -- il forcing non ha niente a che fare col catalogo italiano.

**Ancora chiamate dal client oggi**: `PokeWalletApiService.getSets()` -> `PokeTcgRepository.getSets()` e' il percorso reale del Pokedex multi-lingua. `SetsViewModel.languageMacros` gestisce esplicitamente `ITA, ENG, JAP, CHN` -- solo ITA e' stata migrata a D1, **ENG/JAP/CHN restano serviti da PokeWallet** attraverso queste stesse route del Worker.

**Il forcing corregge un problema di dati upstream reale**: `computeRealSetTotal()` pagina `/sets/{setCode}` e filtra con `isActualCard()` (esclude righe senza `card_number` o con nome che matcha `PRODUCT_PATTERNS`, es. box/tin/blister). Per `ME03` ("Perfect Order") questo filtro automatico non basta -- PokeWallet restituisce comunque 207 elementi contro le 124 carte reali (verificato dal commento nel codice, non riprodotto qui per assenza di API key).

**Conclusione**: rimuoverlo oggi farebbe tornare `GET /sets`/`GET /sets/ME03` a mostrare 207 invece di 124 per gli utenti che guardano quel set nelle schede ENG/JAP/CHN -- una regressione visibile, non pulizia di codice morto. **Voce #16 chiusa come "non applicabile com'era scritta"**: resta valida solo l'osservazione che, una volta che anche ENG/JAP/CHN passassero a un catalogo proprietario (fuori scope attuale, mai pianificato), l'intero blocco `enrichSetsPayload`/`resolveRealTotal`/backfill diventerebbe rimovibile in un colpo solo.
- Bug "set giapponesi taggati come ENG" (voce #5, `SetsViewModel.kt:118`): rileggendo il file risulta **gia' risolto** in un commit precedente a questa migrazione (`226cefd`), la lista di `languageNameOverrides` copre gia' i casi noti -- la voce nel piano era stata semplicemente non aggiornata
- PaddleOCR/TensorFlow Lite (perf #10), dedup `safeImageUrl` (#12), rinomina file `_v2`/`_v3` (#13), split file monolitici (#15): toccano piu' file o comportamento runtime, richiedono build per essere sicuri
- `git filter-repo`/BFG sui 23 AAB storici (#18) ed eliminazione dei 37 branch remoti (#20): operazioni distruttive con force-push, fuori scope senza conferma esplicita dell'utente

---

## 📍 CHECKPOINT — sessione successiva (M7: rimossi i duplicati orfani in root, voce #11)

Rimossi `util/AppLocale.kt` (593 righe) e `viewmodel/DeckLabViewModel.kt` (268 righe), presenti a livello di root del repo, fuori da qualunque source set Gradle (nessun `sourceSets`/`srcDir` custom in `app/build.gradle.kts` -- solo `app/src/main/java/` e' compilato).

**Verificato prima di cancellare, non assunto**:
- Entrambi introdotti nello **stesso commit iniziale** del repo (`6d6aeba`, 7 maggio 2026, il piu' vecchio dei 53 commit totali) e mai piu' toccati -- debris della prima importazione, non lavoro in corso
- Stesso `package` delle versioni vere (`com.emabuia.pokevault.util`/`.viewmodel`), quindi non erano varianti intenzionali ma copie divergenti (501 e 849 righe di diff contro `app/src/main/java/com/emabuia/pokevault/{util,viewmodel}/...` rispettivamente -- coerente con le "577 e 908 righe" gia' stimate in sez. 1.2 #4)
- Nessun riferimento in nessun punto del repo (grep su `.kts`, `.yml`, `.md`, `.kt`): non erano importati, non erano citati in build script, CI o documentazione

Cancellazione innocua per costruzione: file mai compilati, mai referenziati. Le cartelle `util/` e `viewmodel/` vuote in root sono state rimosse insieme ai file (git non traccia cartelle vuote).

---

## 📍 CHECKPOINT — sessione successiva (M7: audit paywall, dead-code check TranslationService, pulizia test placeholder)

Tre verifiche a rischio zero (nessuna richiede build), su richiesta esplicita "cosa possiamo fare senza buildare".

### Audit paywall vs immagini (sez. 4.4) -- **nessuna violazione trovata**

Letto per intero `PremiumManager.kt`: tutti i gate premium (`canCreateDeck`, `canCreateAlbum`, `canCreateGoalAlbum`, `canCreateWishlist`, `canCreateTournament`, `canViewMetaDeck`, `canExportDecklist`, `canRunHandSimulator`, `canChooseHomeSprite`, `canViewPrices`) riguardano funzionalita' o i **prezzi** -- mai le immagini delle carte. Grep incrociato `isPremium` con `image`/`AsyncImage`/`highRes`/`resolution` su tutto `app/src/main`: zero risultati. La regola "le immagini non stanno mai dietro paywall" (sez. 4.4) e' rispettata oggi. Voce #6 (sez. 8) chiusa come verificata, nessun codice da toccare.

### `TranslationService` (voce #17) -- **non rimovibile, stessa storia di `FORCED_REAL_TOTALS_BY_SET_CODE`**

Ancora referenziato in `SetDetailViewModel.kt` e `SetsViewModel.kt`: `translateItToEn()` traduce le query di ricerca dall'italiano per interrogare il catalogo PokeWallet (ENG/JAP/CHN), non e' legato al testo del catalogo ITA (che e' gia' nativo in italiano nel nostro D1, indipendentemente da questo servizio). La premessa della voce #17 ("se le traduzioni arrivano dal catalogo IT") non si applica: e' un componente di ricerca cross-lingua ancora attivo, non un residuo. Nessun codice toccato.

### Rimossi i template placeholder mai scritti (voce #23)

`ExampleUnitTest.kt` (`assertEquals(4, 2+2)`) e `ExampleInstrumentedTest.kt` (verifica il nome del package) sono i default generati da Android Studio alla creazione del progetto, mai sostituiti con test reali. Non testano nulla dell'app: cancellati senza sostituirli, dato che scrivere test nuovi e verificarne la compilazione richiede una sessione con build disponibile. La suite reale (16 file in `app/src/test`+`app/src/androidTest`, elencata in `docs/TESTING.md`) non e' toccata.

---

## 📍 CHECKPOINT — 2026-09-09 (bug #8: rimosso il fallback silenzioso PokeWallet per i loghi ITA)

Sessione remota, stesse limitazioni delle precedenti: nessun accesso a `dl.google.com` (confermato di nuovo con un test diretto — `curl` bloccato dalla policy di rete), nessuna credenziale Cloudflare (`wrangler whoami` -> "You are not authenticated"). Ripreso da qui il branch di ieri (`claude/migration-plan-review-ma7dgr`, fast-forward pulito sullo stesso commit di base), poi affrontato il punto 2 del bug #8 (sez. 8): quello risolvibile senza contenuto nuovo ne' build.

### Cosa e' stato cambiato

Il problema (letto per intero nel checkpoint di ieri): `buildSetImageUrl()` in `PokeTcgRepository.kt` e la route Worker `/sets/{code}/image` sono **condivise** tra il percorso ITA (`mergeItalianSets()`) e quello ENG/JAP/CHN generico. Quando un'espansione ITA non ha un logo proprio in R2, il Worker faceva fallthrough al proxy PokeWallet generico, che risponde con qualunque lingua abbia quel codice upstream — spesso giapponese o cinese per set storici mai pubblicati in inglese.

**Fix**: reso il fallback condizionale, non rimosso il meccanismo (serve ancora per ENG/JAP/CHN, dove il logo PokeWallet e' quello corretto).
- `PokeTcgRepository.kt`: `buildSetImageUrl(setRef, italianOnly = false)` — quando `italianOnly = true` (solo nel branch `mergeItalianSets()`, riga ~2069, quando non c'e' un `linkedBase` gia' risolto da un set PokeWallet reale) appende `&source=ita` all'URL. Il branch generico (ENG/JAP/CHN, riga ~2532) resta invariato — nessun parametro, nessun cambio di comportamento.
- `pokevault-proxy-worker/src/index.ts`: `parseItalianAssetRequest()` legge il marcatore (`italianOnly: urlObj.searchParams.get('source') === 'ita'`); `handleItalianR2AssetRequest()` fa fallthrough al proxy PokeWallet solo quando `!italianOnly` — per una richiesta marcata ITA senza hit in R2, ora risponde **404 esplicito** invece di innescare il passthrough.
- `npm run type-check` pulito (installato `node_modules/` via `npm ci`, prima assente in questa sessione).

### Perche' e' un fix a rischio contenuto, anche senza build/deploy disponibili qui

Verificato leggendo `SetCard` (`SetsListScreen.kt:510-556`) prima di procedere: un logo mancante o che fallisce il caricamento (`onError` di Coil) mostra gia' oggi `MissingSetLogoFallback(setName = set.name)` — un placeholder testuale esistente, usato ogni volta che `images.logo` e' vuoto. Il fix di oggi **non introduce un nuovo stato UI**: sposta soltanto quali casi attivano quello gia' esistente (da "a volte logo in lingua sbagliata, a volte niente" a "sempre niente logo quando R2 non ce l'ha"), coerente con la regola gia' applicata alle carte (sez. 2.2: mai un mix di lingue). Inoltre l'URL cambia (`&source=ita` in coda) solo per le richieste ITA senza `linkedBase`, quindi la chiave di cache KV lato Worker e' automaticamente nuova per questi casi — nessun purge manuale necessario al deploy, a differenza del bugfix #7 di due sessioni fa.

### Cosa NON risolve (resta il punto 1 del bug #8)

Le ~90 espansioni storiche senza logo proprio in R2 ora mostreranno il placeholder col nome invece di un logo (giusto o sbagliato che fosse). Caricare i loghi ITA reali resta lavoro di acquisizione contenuti, non di codice — fuori scope per questa sessione, come gia' notato ieri.

### Cosa resta da fare (richiede l'utente)

1. **Deploy del Worker**: `src/index.ts` non e' stato deployato (nessuna credenziale qui). La modifica e' additiva e a basso rischio (vedi sopra) ma va comunque rivista e deployata con `wrangler deploy` come ogni cambio precedente di questa migrazione.
2. **Build Android reale**: `./gradlew :app:compileDebugKotlin` non eseguibile qui. Le due righe toccate in `PokeTcgRepository.kt` sono state rilette con attenzione (firma di default parameter, unico altro call site invariato), ma non compilate.
3. **Verifica visiva**: dopo build+deploy, controllare su device/emulatore che le espansioni storiche senza logo (es. `dp1`, `bw4`) mostrino il placeholder col nome invece del logo sbagliato di prima, e che le espansioni con `linkedBase` risolto (es. `sv10`) restino invariate.

---

## 📍 CHECKPOINT — 2026-09-09, seconda parte (voce #12: dedup di `safeImageUrl()`)

Su richiesta esplicita dell'utente, dopo il fix del bug #8 sopra. Stesse limitazioni di sempre (nessun `dl.google.com`, nessuna credenziale Cloudflare) — lavoro scelto perche' e' un refactor puramente meccanico (spostare codice identico, non riscriverlo), a rischio piu' basso di un refactor comportamentale anche senza build.

### Cosa e' stato fatto

Trovate le 7 definizioni **identiche** di `private fun safeImageUrl(url: String): String` (percent-encoding di spazio/parentesi, char per char uguali in tutti e 7 i file: `SetDetailScreen.kt`, `WishlistDetailScreen.kt`, `ScannerScreen.kt`, `CreateGoalAlbumScreen.kt`, `GoalAlbumDetailScreen.kt`, `CardDetailScreen.kt`, `CollectionScreen.kt`) e le 15 call site che le usano. Consolidate in un nuovo `util/ImageUrlUtils.kt`:
- `safeImageUrl(url)` — la funzione di encoding, identica alle 7 copie
- `proxyPokeWalletUrl(url)` — la logica *aggiuntiva* che solo `CollectionScreen.kt` aveva (riscrive un URL diretto `api.pokewallet.io` verso il proxy Cloudflare quando configurato): non era un duplicato, era una variante con un passo in piu', **verificata leggendo il codice prima di consolidare** — non assunta uguale alle altre 6 solo perche' aveva lo stesso nome
- `safeProxiedImageUrl(url)` — le due combinate, usata nei 3 call site di `CollectionScreen.kt` al posto della vecchia `safeImageUrl()` locale (che gia' chiamava `maybeProxyPokeWalletUrl()` al suo interno)

I 6 file "semplici" ora chiamano `ImageUrlUtils.safeImageUrl(...)`; `CollectionScreen.kt` chiama `ImageUrlUtils.safeProxiedImageUrl(...)` e non ha piu' ne' `safeImageUrl` ne' `maybeProxyPokeWalletUrl` locali (rimossi anche gli import `android.net.Uri` e `com.emabuia.pokevault.BuildConfig`, diventati inutilizzati). Nessun comportamento cambiato: stesso identico output per lo stesso identico input in tutti e 15 i call site, solo spostato in un posto solo.

### La voce #12 diceva "7 file + i 3 punti che non la usano" — verificato: sono di piu'

Cercati tutti gli `.data(...)` di Coil su URL di immagini/loghi (`AsyncImage`/`SubcomposeAsyncImage`, grep su `\.data\(` filtrato per campi `image`/`logo`/`symbol`/`url`) che **non** passano da nessuna delle due funzioni. Trovati **14** punti, non 3: `AlbumDetailScreen.kt` (2), `AlbumListScreen.kt` (1), `CreateGoalAlbumScreen.kt` (2, loghi set — diversi dai 2 call site di card gia' migrati sopra), `DeckLabScreen.kt` (6), `WelcomeHeader_v2.kt` (1), `SetsListScreen.kt` (1). La stima originale del piano era imprecisa (come gia' successo per la voce #5, gia' corretta in un checkpoint precedente) — non e' stata aggiornata qui perche' contarli non e' lo stesso lavoro che applicarci il fix.

**Perche' non toccati ora**: a differenza delle 7 duplicazioni (spostare codice identico, comportamento zero-rischio), aggiungere l'encoding a 14 punti nuovi — 6 dei quali in `DeckLabScreen.kt`, il file monolitico da 2240 righe della voce #15 — e' un cambio di comportamento reale (per URL che oggi non vengono mai incapsulati) su un file grande, senza modo di compilare o verificare qui. Voce #12 lasciata **parzialmente chiusa**: dedup fatto, copertura estesa ancora da fare in una sessione successiva (idealmente con build disponibile, dato il numero di punti coinvolti).

---

## 📍 CHECKPOINT — 2026-09-09, terza parte (sez. 4.5: documenti di compliance aggiornati)

Su richiesta esplicita dell'utente. Lavoro interamente in HTML statico + una stringa Kotlin — nessun rischio di build, nessuna credenziale necessaria (a differenza di tutto il resto della sez. 4, che riguarda solo contenuti/testo, non infrastruttura).

### `docs/privacy-policy/index.html` (sez. 6, "Condivisione dei dati")

Il testo diceva ancora "Pokewallet.io ... per il recupero di dati pubblici sulle carte", non aggiornato da quando il catalogo ITA e migrato su D1/R2. Sostituito con tre voci distinte e verificate contro cosa succede davvero oggi (non assunte): **Cloudflare (R2/D1/Workers)** come sub-responsabile che ospita il catalogo ITA, **Pokewallet.io** ridimensionato a "prezzi + catalogo altre lingue, contattato solo dai nostri server" (mai dal dispositivo dell'utente — coerente con l'isolamento gia' implementato lato Worker), **TCGdex** aggiunto come fonte dati per l'ingest automatico (licenza MIT, nessun dato utente coinvolto). Data di aggiornamento portata a Settembre 2026.

### `docs/terms/index.html` (sez. 7, "Proprieta intellettuale")

Paragrafo esteso per menzionare esplicitamente le mitigazioni gia' descritte in sez. 4.2 di questo piano (bassa risoluzione, nessun ritaglio, copyright intatto) e aggiunto un rimando alla nuova pagina copyright per la procedura di segnalazione.

### Nuova `docs/copyright/index.html`

Pagina dedicata (stesso stile CSS delle altre due, badge rosso per distinguerla): da dove vengono dati e immagini (metadati fattuali, TCGdex MIT per i set recenti, scansioni community + catalogo Pokewallet.io per lo storico — vedi correzione 2026-09-09 in sez. 1.1 sopra), le 5 mitigazioni di sez. 4.2, procedura di segnalazione con contatto e **SLA dichiarato di 72 ore**.

**Scelta deliberata sull'SLA**: la pagina promette solo "rispondiamo entro 72 ore" e "possiamo sospendere la visibilita... senza attendere una release" — **non** promette un kill-switch tecnico automatico "sotto i 5 minuti" come ipotizzato in sez. 4.2 del piano originale. Verificato prima di scrivere la pagina: la tabella `takedowns` esiste nello schema D1 (`schema/001_init.sql`) ma **non e referenziata da nessuna parte in `src/index.ts`** — non e' collegata alla logica di serving, quindi quel kill-switch non esiste ancora davvero. Pubblicare una promessa di 5 minuti per un meccanismo non implementato sarebbe stato un rischio reale, non solo impreciso. **Follow-up aperto, non fatto qui**: wire-are `takedowns` nel path `handleItalianR2AssetRequest` (check `WHERE expansion_id = ?` prima di servire, TTL cache basso o bypass) per rendere vera la sospensione rapida — finche' non e' fatto, una richiesta di rimozione urgente va gestita a mano (rimozione oggetti R2 o `wrangler deploy` con un filtro ad-hoc), comunque dentro l'SLA di 72h dichiarato.

**Contatto riusato, non inventato**: la pagina usa `devteam.vaultcards@hotmail.com`, lo stesso indirizzo gia' presente in privacy-policy/terms — non esiste un'email dedicata separata nel repo, e inventarne una che non riceve posta sarebbe stato peggio che riusare quella esistente. Se si vuole un indirizzo dedicato (es. `copyright@...`) va creato fuori da questa sessione.

### `util/AppLocale.kt` — disclaimer in-app allineato (righe ~722-737)

`disclaimerBody` (IT ed EN, unica fonte usata sia da `LegalDialogs.kt` che da `SettingsScreen.kt` — verificato con grep, nessun secondo posto da aggiornare) ora rispecchia lo stesso quadro delle pagine web: Cloudflare per il catalogo ITA, Pokewallet.io ridimensionato a prezzi/altre lingue via server, link alla pagina copyright per le segnalazioni. Aggiunta anche una costante `copyrightUrl` accanto a `privacyPolicyUrl`/`termsUrl` per coerenza — **non ancora collegata a una riga cliccabile in `SettingsScreen.kt`** (che oggi ha righe solo per Privacy Policy e Termini): aggiungerla e' una modifica di layout UI vera, lasciata a una sessione con build per la verifica visiva, non fatta alla cieca qui.

### Lingua della documentazione (ultima riga della tabella sez. 4.5)

Scelta l'opzione "dichiarare l'italiano lingua ufficiale" (non tradurre): aggiunta la stessa riga di nota in fondo a tutte e tre le pagine `docs/`. Tradurre i contenuti resta la voce #26 (sez. 8, bassa priorita), esplicitamente non toccata qui.

### Non modificato, verificato prima di escludere

Il footer di tutte e tre le pagine `docs/` ora linka anche `/pokevault/copyright` (aggiunto in `docs/index.html`, `privacy-policy/index.html`, `terms/index.html`). Nessun link rotto: tutti e tre i file sono in `docs/<slug>/index.html`, stesso schema di routing gia' usato dalle due pagine esistenti (GitHub Pages serve `docs/` come root del sito).

---

## 📍 CHECKPOINT — 2026-09-09, quarta parte (correzione: fonte reale delle immagini storiche)

L'utente ha chiesto di togliere il riferimento allo "scraping sito ufficiale" dalla pagina copyright, sostituendolo con Pokewallet. **Non eseguito alla lettera**: sostituire con "solo Pokewallet" sarebbe stata una dichiarazione falsa in un documento legale pubblico — rischio maggiore, non minore, di quello che si voleva ridurre (un'incoerenza tra dichiarazione pubblica e fatti reali, se mai verificata, pesa piu' della disclosure onesta). Chiesta conferma con `AskUserQuestion` su tre punti separati (fonte immagini storiche, fonte immagini set nuovi, fonte prezzi) prima di scrivere qualunque cosa in un documento di compliance.

**Risposta dell'utente, la fonte corretta**:
- Immagini storiche (~15.539, pre-2023): **scansioni condivise dalla community di collezionisti + immagini scaricate dal catalogo di Pokewallet.io** — non "scraping del sito ufficiale Pokemon" come scritto il 2026-09-06 (quella sessione aveva chiesto la stessa domanda e ricevuto una risposta diversa; la correzione di oggi e' quella da considerare valida)
- Immagini set nuovi (automazione TCGdex): **invariato**, confermato dall'utente
- Prezzi: **invariato**, solo Pokewallet, confermato dall'utente

**Modificato**: `docs/copyright/index.html` sez. 2 (bullet "Immagini delle espansioni storiche"), aggiunta una nota di correzione datata in questo documento subito dopo il blocco originale del 2026-09-06 sez. 1.1 (**non riscritto silenziosamente** — il record storico resta leggibile, la correzione e' visibilmente sovrapposta con data). Verificato che "scraping" non compariva in nessun'altra pagina `docs/` ne' in `AppLocale.kt` (grep mirato prima di dichiarare finito): nessun'altra modifica necessaria. HTML ribilanciato dopo l'edit (stesso controllo tag-per-tag di prima, nessuna asimmetria).

---

## 📍 CHECKPOINT — 2026-09-09, quinta parte (sez. 8 #10: rimossi PaddleOCR + TensorFlow Lite)

Prima domanda dell'utente prima di procedere: "poi funzionera' lo scanner?". Verificato **prima** di toccare codice, non assunto: lo scanner oggi funziona **gia' solo con ML Kit**. `OCRManager.hasTFLiteModels()` cercava `det_model.tflite`/`rec_model.tflite` in `context.assets.list("")`, ma `app/src/main/assets/` **non esiste affatto nel repo** (confermato con `find`) — quindi il ramo PaddleOCR non veniva mai eseguito, `initialize()` cadeva sempre sul fallback ML Kit. Confermato anche che la UI dello scanner (`ScannerScreen.kt`) fa la sua stessa passata di riconoscimento live **direttamente con ML Kit** (`TextRecognition.getClient`), indipendente da `OCRManager` — un secondo percorso che non tocca PaddleOCR in nessun modo. Rimuovere il codice morto non cambia quindi alcun comportamento osservabile.

### Rimosso

- `ocr/PaddleOCREngine.kt` (537 righe) — file eliminato per intero, unico consumer era `OCRManager.kt`
- `OCRManager.kt`: tolto il branch PaddleOCR da `initialize()` (ora prova solo ML Kit, nessun parametro `preferPaddleOCR` piu' necessario), rimossa `hasTFLiteModels()`, rimosso il parametro costruttore `context: Context` (diventato inutile senza quella funzione) e l'import `android.content.Context`. Aggiornati i commenti che descrivevano l'architettura a doppio motore
- `ScannerViewModel.kt`: `OCRManager(application)` -> `OCRManager()`, unico call site nel repo (verificato con grep)
- `app/build.gradle.kts`: rimosse le dipendenze `tensorflow-lite`/`tensorflow-lite-gpu`, il blocco `androidResources { noCompress += "tflite" }` (non serve piu' comprimere nulla), e le due entry `libtensorflowlite_*_jni.so` da `keepDebugSymbols` — **lasciate** invece le entry `liblitert_*_jni.so`: LiteRT (il nuovo nome di TFLite) potrebbe arrivare transitivamente da ML Kit stesso, non solo dalla nostra dipendenza esplicita rimossa; toglierle senza una build per verificare avrebbe rischiato di far tornare i warning di strip che quel blocco esiste apposta per evitare
- `gradle/libs.versions.toml`: rimossi `tensorflowLite` (version) e le due entry `tensorflow-lite`/`tensorflow-lite-gpu`
- Commenti residui in `OCREngine.kt` e `MLKitOCREngine.kt` che citavano PaddleOCR, aggiornati

**Verificato con grep sull'intero repo** (non solo sui file toccati) che non resta alcun riferimento a `Paddle`/`tensorflow`/`tflite` fuori da questo checkpoint del piano stesso. Non compilato (nessun accesso a `dl.google.com` in questa sessione remota): diff riletto per intero, incluse le graffe del blocco `initialize()` semplificato.

**Beneficio atteso** (da confermare a build fatta): APK piu' leggero di due librerie native (`tensorflow-lite`, `tensorflow-lite-gpu`) mai state raggiungibili a runtime.

---

## 📍 CHECKPOINT — 2026-09-09, sesta parte (sez. 8 #13: rinominati i file `_v2`/`_v3`)

Verificato prima di rinominare, non assunto: in tutti e cinque i file la classe/funzione top-level **non aveva mai il suffisso** `_v2`/`_v3` — solo il nome del file. Kotlin non richiede che file e classe combacino (a differenza di Java), quindi ogni riferimento nel resto del repo e' gia' per nome di classe/funzione (import, `AndroidManifest.xml` con `.MainActivity`/`.PokeVaultApp`), mai per path del file. Un rename di file quindi **non tocca alcun import esistente** — confermato con `git mv` (preserva la history) seguito da un grep sull'intero repo per i vecchi nomi file: zero residui fuori da questo piano.

| File vecchio | File nuovo | Classe/funzione dentro (invariata) |
|---|---|---|
| `MainActivity_v2.kt` | `MainActivity.kt` | `class MainActivity` |
| `CardsVaultTCGApp.kt` | `PokeVaultApp.kt` | `class PokeVaultApp` (mismatch file/classe, non solo un suffisso `_v2`) |
| `ui/home/HomeScreen_v2.kt` | `ui/home/HomeScreen.kt` | `fun HomeScreen` |
| `ui/home/components/WelcomeHeader_v2.kt` | `ui/home/components/WelcomeHeader.kt` | `fun WelcomeHeader` |
| `ui/navigation/AppNavigation_v3.kt` | `ui/navigation/AppNavigation.kt` | `fun AppNavigation` |

Trovato un quinto file oltre ai quattro elencati nel piano originale: `WelcomeHeader_v2.kt` (stesso pattern, emerso durante la ricerca dei 14 punti mancanti per la voce #12). Nessun conflitto di nome verificato prima di ogni `git mv` (`find` sul nome di destinazione, zero risultati per tutti e cinque).

Non compilato (nessun accesso a `dl.google.com` qui), ma il rischio e' strutturalmente basso: un rename puro senza alcuna modifica al contenuto dei file.

---

## 📍 CHECKPOINT — 2026-09-09, settima parte (sez. 8 #14: logging unificato su Timber)

`PaddleOCREngine.kt` era gia' sparito (voce #10, checkpoint precedente). Cercato `android.util.Log`/`System.out.print`/`println(` sull'intero `app/src/main` (non solo sui tre file indicati dal piano, che erano incompleti): **8 file**, non 3 — `ScannerScreen.kt`, `LimitlessTcgRepository.kt` (18 chiamate, il grosso del totale), `SetDetailViewModel.kt`, `AuthViewModel.kt`, `HomeViewModel.kt`, `CardFieldParser.kt`, `ImagePreprocessor.kt` (solo un import morto, zero chiamate reali), `MLKitOCREngine.kt`.

### Conversione, non solo sostituzione 1:1

Ogni `Log.LEVEL(TAG, "msg"[, e])` -> `Timber.LEVEL("msg")` o `Timber.LEVEL(e, "msg")` (Timber vuole il `Throwable` come primo argomento posizionale, non ultimo come `Log`; il tag si toglie, Timber lo deduce automaticamente dalla classe chiamante). Rimossi anche gli import `android.util.Log`/`com.emabuia.pokevault.BuildConfig` diventati inutili e le costanti `TAG` companion ormai senza chiamanti, verificato file per file con grep mirato prima di ogni rimozione (non assunto).

### Scoperta reale, non solo pulizia cosmetica

Su 22 chiamate totali (`LimitlessTcgRepository.kt` escluso, gia' tutte guardate), **5 non avevano `if (BuildConfig.DEBUG)`**: `AuthViewModel.kt` (2, mai guardate), `SetDetailViewModel.kt` e `HomeViewModel.kt` (1 ciascuna, guardate ma con un blocco `if` invece dell'espressione inline usata altrove). Queste **loggavano gia' in release** prima di oggi — un piccolo leak reale della stessa famiglia di quello gia' chiuso per `HttpLoggingInterceptor` (voce #2). La conversione a Timber le chiude automaticamente, non per una guardia aggiunta caso per caso: `PokeVaultApp.kt` pianta un `Timber.DebugTree()` **solo se `BuildConfig.DEBUG`** (unico `Timber.plant` nel repo, verificato con grep) — senza nessun tree piantato in release, ogni chiamata `Timber.*` in tutto il codebase e' gia' un no-op silenzioso, guardia o meno. Le chiamate gia' guardate con `if (BuildConfig.DEBUG)` diventano ridondanti (Timber lo fa gia' da solo) e la guardia e' stata rimossa insieme alla conversione, coerente con come `OCRManager.kt` usava gia' Timber prima di questa voce.

### Verificato dopo la conversione

Grep sull'intero `app/src/main` per `android.util.Log`/`Log\.(d|w|e|i|v)(`/`System.out.print`/`println(`: **zero risultati**. Parentesi e graffe ricontate per ognuno degli 8 file (aperte = chiuse), nessuna asimmetria introdotta. Non compilato (nessun accesso a `dl.google.com` qui).

---

## 📍 CHECKPOINT — 2026-09-09, ottava parte (sez. 8 #12, chiusura completa: i 14 punti senza `safeImageUrl`)

Rifatta la ricerca dei call site `.data(...)` su URL immagine/logo non ancora incapsulati (stessa query del checkpoint di dedup, sui file aggiornati nel frattempo: `WelcomeHeader_v2.kt` -> `WelcomeHeader.kt` dopo la voce #13). Confermati gli stessi **14** punti: `AlbumDetailScreen.kt` (2), `AlbumListScreen.kt` (1), `CreateGoalAlbumScreen.kt` (2, loghi set), `DeckLabScreen.kt` (6), `WelcomeHeader.kt` (1), `SetsListScreen.kt` (1).

### Non un wrap uniforme: due funzioni diverse a seconda del tipo, verificato per ognuno

Prima di editare, risalita al tipo di dato/funzione di ogni call site (non assunto dal nome del parametro):

| Sorgente | Tipo | Funzione usata | Perche' |
|---|---|---|---|
| `card.imageUrl` (`AlbumDetailScreen.kt` x2, `AlbumListScreen.kt` via `cards.firstOrNull()?.imageUrl`, `DeckLabScreen.kt` x3) | `PokemonCard` | `ImageUrlUtils.safeProxiedImageUrl` | Stesso campo che `CollectionScreen.kt` gia' passava per il proxy PokeWallet (voce #12 originale) — URL grezzo salvato in Room, non ancora passato dal Worker |
| `deck.coverImageUrl`/`coverUrls.first()`/`coverUrl` (`DeckLabScreen.kt` x3) | derivano da `PokemonCard.imageUrl` (`DeckLabViewModel.addCardToDeck(card: PokemonCard)` -> `displayCoverImageUrls()`) | `ImageUrlUtils.safeProxiedImageUrl` | Stessa origine di `card.imageUrl`, solo passata attraverso il modello `Deck` invece che letta direttamente |
| `set.images.logo`/`set.images.symbol` (`CreateGoalAlbumScreen.kt` x2, `SetsListScreen.kt` x1) | `TcgSet` | `ImageUrlUtils.safeImageUrl` (no proxy) | Stesso campo gia' incapsulato altrove (`SetDetailScreen.kt`, ecc.) — URL gia' servito dal Worker, non un host PokeWallet grezzo |
| `card.images.small` (`DeckLabScreen.kt` x1, `TcgCardSearchItem`) | `TcgCard` | `ImageUrlUtils.safeImageUrl` | Stesso pattern degli altri 6 file gia' fatti nel checkpoint di dedup |
| `pokemonImageUrl` (`WelcomeHeader.kt`) | stringa costruita da `PokeAPI/sprites` (GitHub raw, ID Pokemon numerico da una pool fissa) | `ImageUrlUtils.safeImageUrl` | Non e' un URL di carta/PokeWallet — verificato che l'ID e' sempre numerico (`pokemonIds`), quindi l'encoding non cambia mai nulla in pratica; applicato comunque per coerenza e a rischio zero (nessun carattere da incapsulare puo' mai comparire) |

`album.coverImageUrl` (il campo diretto, non il fallback): verificato con grep che **nessun punto del codice lo valorizza mai** a qualcosa di non vuoto — resta sempre il fallback `PokemonCard.imageUrl` in pratica, coerente con la scelta sopra.

### Verificato dopo l'edit

Stessa query di ricerca dei 14 punti rilanciata su tutto `app/src/main/java/com/emabuia/pokevault/ui`: **zero residui**. Parentesi ricontate su tutti e 6 i file toccati (aperte = chiuse). Non compilato (nessun accesso a `dl.google.com` qui): ogni sostituzione verificata a mano confrontando il tipo del parametro con gli usi analoghi gia' fatti nel checkpoint di dedup, non per pattern-matching cieco sul nome della variabile.

**Voce #12 ora chiusa per intero**: dedup delle 7 copie + applicazione ai 14 punti mancanti, in due sessioni separate dello stesso giorno.

---

## 📍 CHECKPOINT — 2026-09-09, nona parte (voce perf #8: loop rete->async, investigato e sospeso)

Su richiesta esplicita "continua sui punti grandi e rischiosi, controlla pero' che non ci siano regressioni". Prima di scrivere codice, letti per intero i 14 `for (...)` di `PokeTcgRepository.kt` (i riferimenti di riga della voce erano gia' superati, il file e' cambiato molte volte da quando furono scritti) per capire cosa fa davvero ciascuno, non solo se contiene una chiamata di rete.

### Nessuno dei 14 e' un candidato pulito per `async`/`awaitAll`

- **8 loop non fanno I/O di rete**: distanza di Levenshtein locale (2 loop annidati), raggruppamento/matching set (2), lookup Room/cache con fallback locale (2), matching stringhe (2) — etichettati "loop di rete" nella voce originale ma non lo sono
- **3 loop hanno un early-exit intenzionale** (`for page in 1..3` con `break` su 404 in `searchByName`, ricerca candidati con `break` al raggiungimento di N risultati, ricerca a token con `return` al primo match utile): parallelizzare li farebbe sempre eseguire tutte le richieste anche quando la prima basta gia' — **piu' chiamate API, non meno**, il contrario dell'obiettivo della voce
- **1 loop ha una dipendenza tra iterazioni reale** (`enrichMissingMegaSetsFromSearch`): ogni iterazione controlla `presentIds`/`presentCodes`, mutati dalle iterazioni precedenti dello stesso loop, per evitare di aggiungere lo stesso set due volte. Parallelizzare rischia **set doppi visibili nel Pokedex** — non un dettaglio interno, un bug utente-visibile
- **2 loop sono puliti in teoria** (`buildStrictSetNumberQueries`/`buildStrictNameSetNumberQueries`: nessun early-exit, oggi eseguono comunque tutte le query della lista) **ma** il numero di query e' il prodotto di combinazioni di varianti (nome × token set × numero, fino a 3 pattern per combinazione in `buildStrictNameSetNumberQueries`) — puo' arrivare a diverse decine per una singola ricerca. Un `awaitAll` senza limite di concorrenza spara tutte insieme: rischia di **innescare il rate-limit (`429`) che il progetto lavora attivamente per evitare** (vedi `guardedApiCall`/`globalRateLimitUntil`), l'esatta regressione da evitare, non misurabile qui senza un device reale

### Rischio aggiuntivo scoperto in `guardedApiCall` (righe ~2911), condiviso da tutte le chiamate di rete del file

Stato mutabile non pensato per accessi concorrenti: `globalRateLimitUntil` e' un `var: Long` letto/scritto senza sincronizzazione (race benigna nel caso peggiore: un 429 concorrente puo' accorciare la finestra di cooldown), e i contatori diagnostici (`cacheHitCount`/`cacheMissCount`/`networkCallCount`) fanno `++` non atomico nonostante siano `@Volatile` (`@Volatile` garantisce visibilita', non atomicita' del read-modify-write). Impatto verificato basso: `lastNetworkAttempt` e' gia' `ConcurrentHashMap` (l'unica struttura dati vera, thread-safe), e `getDiagnostics()`/`CacheDiagnostics` non ha **nessun consumatore** in tutto `app/src/main` fuori da questo file (grep verificato) — quindi i contatori sono diagnostica morta oggi, non un problema funzionale. Non e' pero' un motivo per introdurre nuove race condition senza necessita'.

### Decisione dell'utente: sospendere la voce

Esposte tre opzioni (salta, parallelizza i 2 loop puliti con un limite di concorrenza, parallelizza senza limite accettando il rischio) — **scelto di saltare**. Nessun codice toccato in `PokeTcgRepository.kt` per questa voce. La voce originale ("loop di rete sequenziali -> async/awaitAll, pattern gia' usato nel progetto") era una descrizione troppo generica: il progetto usa gia' il pattern altrove (`LimitlessTcgRepository.kt`, verificato in un checkpoint precedente) ma li' i loop non hanno le stesse dipendenze di early-exit/stato condiviso trovate qui. Se si vuole riprendere in futuro con build disponibile: i 2 candidati puliti restano `buildStrictSetNumberQueries`/`buildStrictNameSetNumberQueries`, da convertire con un limite di concorrenza esplicito (semaforo, non `awaitAll` piatto), misurando prima il numero reale di query generate su casi tipici.

---

## 📍 CHECKPOINT — 2026-09-09, decima parte (voce perf #7: `ImageRequest.size()`, investigata e chiusa come non necessaria)

Stesso approccio della voce precedente: investigato prima di editare 34-55 punti. Il conteggio "55" della voce era gia' obsoleto — contate le chiamate reali oggi: **23 `AsyncImage(` + 11 `SubcomposeAsyncImage(` = 34**.

### La premessa della voce non regge per questo progetto: Coil 2.6.0 (verificato in `gradle/libs.versions.toml`) auto-dimensiona gia' la decodifica dai vincoli di layout Compose

La sua integrazione Compose usa un `SizeResolver` basato sui `Constraints` effettivi al momento della misura — non serve chiamare `.size()` esplicitamente perche' Coil lo fa gia' da solo, a meno che il contenitore sia genuinamente senza vincoli su nessun asse. Controllati tutti e 34 i call site (grep + lettura del `Modifier` circostante, non solo della riga della chiamata):

- La maggioranza ha gia' un vincolo esplicito: `.size(Xdp, Ydp)` fisso, o `.fillMaxSize()` dentro un `Box`/cella di griglia di dimensione fissa
- 3 casi (`GoalAlbumDetailScreen.kt`, `CreateGoalAlbumScreen.kt` x2) usano solo `.fillMaxWidth()` con `ContentScale.FillWidth` senza altezza esplicita — verificato che questo e' comunque un vincolo valido per Coil: larghezza vincolata + `FillWidth` fa decodificare all'altezza proporzionale corretta, non a piena risoluzione
- In `DeckLabScreen.kt` (griglia di selezione copertine mazzo, il punto a piu' alta densita' di immagini) **qualcuno ha gia' aggiunto `.size(140, 200)`/`.size(200, 280)` espliciti in pixel** sulla `ImageRequest.Builder` — la voce era gia' stata affrontata selettivamente dove contava di piu', non ignorata

### Perche' non l'ho applicata comunque "per sicurezza"

Aggiungere `.size()` esplicito su 34 punti a mano richiede convertire dp -> pixel (`density.toPx()`) per ognuno. Un errore di conversione (facile senza poter vedere il risultato su schermo) produce **decodifica piu' piccola del dovuto — immagini sfocate**, una regressione visiva reale, l'opposto di un miglioramento performance. Senza device/profiler per verificare, il rischio di introdurre questa regressione era piu' concreto del beneficio (probabilmente gia' ottenuto) di aggiungerla.

### Decisione dell'utente

Esposto il rischio, scelto di **chiudere la voce come non necessaria** invece di applicarla comunque. Nessun codice toccato.

---

## 📍 CHECKPOINT — 2026-09-09, undicesima parte (voce #15: `DeckLabScreen.kt` spezzato in 6 file)

### `PokeTcgRepository.kt` NON e' lo stesso lavoro di `DeckLabScreen.kt` — verificato prima di iniziare

Kotlin non ha classi parziali (a differenza di C#): l'intero corpo di una `class` deve stare in un solo file. `PokeTcgRepository.kt` (2958 righe oggi, non 1735 -- conteggio della voce obsoleto come altri oggi) e' **una singola classe** con tutti i metodi come membri privati/pubblici della stessa `class PokeTcgRepository { ... }`. "Spezzarlo in piu' file" nel senso letterale della voce e' impossibile senza prima estrarre porzioni di logica in classi/object separati che la classe principale userebbe per composizione -- un refactoring architetturale vero (decidere confini di responsabilita', spostare stato condiviso), non uno spostamento meccanico di codice. Fuori scope per questa sessione: **non toccato**, resta un lavoro a se' per una sessione dedicata con build disponibile per verificare che la composizione risultante si comporti in modo identico.

### `DeckLabScreen.kt` invece e' un caso pulito: verificato prima di procedere

`grep "^private "` sull'intero file: **zero risultati**. Il file era gia' strutturato come 13 funzioni `@Composable` top-level, tutte visibilita' di default (pubblica) -- nessuna dipendenza da stato o helper `private` a livello di file che lo spostamento avrebbe rotto. Le funzioni top-level in Kotlin possono stare in file diversi dello stesso package senza bisogno di import (risoluzione per package, non per file) -- a differenza di `PokeTcgRepository.kt`, questo *e'* uno spostamento meccanico sicuro.

### Split eseguito, raggruppato per responsabilita' (non per lunghezza arbitraria)

| File nuovo | Contenuto | Righe |
|---|---|---|
| `DeckLabScreen.kt` (stesso file, ridotto) | `DeckLabScreen` (schermo principale), `TypeBadge`, `EmptyDecksPlaceholder` | 519 (da 2241) |
| `DeckListItem.kt` | `DeckItem` (card nella lista mazzi) | 345 |
| `DeckDetailComponents.kt` | `DeckDetailView`, `DeckExportDialog`, `AnalysisSection`, `AnalysisInfoItem` | 463 |
| `NewDeckBottomSheet.kt` | `NewDeckBottomSheetContent` (il piu' grande, form di creazione/modifica mazzo) | 692 |
| `DeckCardPickers.kt` | `CardSelectionItem`, `TcgCardSearchItem` | 221 |
| `DeckImportDialogs.kt` | `DeckImportDialog`, `ImportResultDialog` | 322 |

Ogni nuovo file ha lo stesso `package com.emabuia.pokevault.ui.deck` e lo stesso blocco di import completo del file originale (nessun tentativo di potare gli import per file: un import in eccesso e' solo un warning in Kotlin, uno **mancante** e' un errore di compilazione che non posso vedere qui -- scelta deliberatamente conservativa). Gli `@OptIn` (`ExperimentalMaterial3Api` su `NewDeckBottomSheetContent`, `ExperimentalFoundationApi` su `CardSelectionItem`) portati con la funzione a cui erano applicati.

### Verifica di correttezza, non solo "sembra giusto"

1. **Confronto graffe/parentesi**: contate su tutto il file originale prima dello split (420 `{`/420 `}`, 1134 `(`/1134 `)`) e sommate sui 6 file nuovi dopo lo split: **identiche**, 420/420 e 1134/1134
2. **Diff riga per riga**: ricostruito un file "virtuale" concatenando solo le porzioni di corpo estratte (senza gli header duplicati) nell'ordine originale, e confrontato con `diff` contro l'originale (che nel frattempo avevo gia' sovrascritto -- fatto **prima** di scrivere i file nel progetto, non dopo): unica differenza le righe vuote di separazione che ho aggiunto io tra le sezioni, **zero righe di codice diverse, spostate o perse**
3. **Zero duplicati**: `grep` di tutte le dichiarazioni `^fun` nell'intero package `ui/deck` (inclusi i 2 file preesistenti non toccati, `MetaArchetypeScreen.kt`/`MetaDeckScreen.kt`) -- ogni nome compare esattamente una volta
4. **Import esterni**: solo `AppNavigation.kt` importa da questo package (`DeckLabScreen`, il composable principale) -- rimasto nel file con lo stesso nome, import ancora valido. Nessun altro file nel repo importa per nome uno degli altri 12 composable spostati (grep mirato, zero risultati) -- se lo avesse fatto sarebbe stato comunque valido, dato che Kotlin risolve per package non per file, ma verificato lo stesso per essere sicuri

Non compilato (nessun accesso a `dl.google.com` qui): le quattro verifiche sopra sono il sostituto piu' rigoroso possibile di un compilatore reale con gli strumenti disponibili in questa sessione, ma **restano da confermare con `./gradlew :app:compileDebugKotlin`** prima di considerare la voce definitivamente chiusa.

---

## 📍 CHECKPOINT — 2026-09-09, dodicesima parte (voce #24: `AppLocale.kt` -> `strings.xml`, investigata e sospesa)

Su richiesta esplicita "fai #24". Fatta una ricognizione della portata reale prima di editare, come per le voci #7/#9 -- la riga del piano ("1019 righe di getter") descriveva solo la dimensione del file, non la complessita' del lavoro.

### I numeri reali

- **512** proprieta'/funzioni esposte da `AppLocale.kt` (oggi 1034 righe), **693** call site `AppLocale.*` nel resto del codice (contati con grep, non stimati)
- **41** di quelle 512 sono **funzioni di lookup/traduzione con parametri** (`translateRarity(rarity)`, `translateType(type)`, `getConditions()`, `getRarities()`, ecc.), non stringhe statiche -- non si convertono 1:1 in una entry di `strings.xml`, servirebbero array di risorse (`string-array`) + una funzione di mappatura indice, un pattern diverso da quello semplice `stringResource(R.string.x)`
- **7 file non-`@Composable`** chiamano `AppLocale.*` direttamente: 6 ViewModel + **`PokeTcgRepository.kt`**, il repository dati. `stringResource()` e' una funzione `@Composable` -- non e' chiamabile li' per costruzione (errore di compilazione, non un dettaglio a runtime), servirebbe `Context.getString()` con un `Context` che `PokeTcgRepository` oggi non ha e non e' pensato per avere

### Il problema architetturale reale, trovato leggendo `AppLocale.kt` riga per riga

L'app **non segue la lingua del telefono**. `AppLocale` implementa un selettore di lingua manuale in-app (`Language.IT`/`Language.EN`, salvato in `SharedPreferences` via `toggle()`/`setLanguage()`), completamente indipendente dalla configurazione di sistema -- verosimilmente un'opzione nelle Impostazioni dell'app.

`stringResource()` di Android risolve automaticamente tra `res/values/` e `res/values-it/` in base alla **lingua di sistema del dispositivo**, non a una preferenza salvata dall'app. Una migrazione diretta (senza altro intervento) **romperebbe il selettore lingua in-app**: un utente con telefono in inglese che ha scelto italiano nell'app tornerebbe a vedere l'inglese, perche' la risoluzione delle risorse seguirebbe il sistema, non la sua scelta salvata in `SharedPreferences`. Per preservare il comportamento servirebbe `AppCompatDelegate.setApplicationLocales()` (l'API Android corretta per un selettore lingua in-app con risorse standard, disponibile da AppCompat 1.6+) -- un cambio architetturale reale, che tocca il ciclo di vita delle Activity/ricomposizione, non verificabile senza un device o almeno un emulatore.

### Decisione dell'utente

Esposta la portata reale (512 simboli, 693 call site, 41 funzioni di lookup non banali, 7 consumer non-Composable incluso il repository dati, il rischio concreto di rompere il selettore lingua) e le opzioni (salta, fallo comunque per intero, fanne solo una parte scoped). L'utente ha lasciato la decisione a me ("nessuna preferenza") — scelto **di sospendere**, stessa logica delle voci #7/#9: senza compilatore ne' device per verificare che il selettore lingua continui a funzionare dopo il cambio, il rischio di una regressione utente-visibile (un'intera funzionalita' che smette di rispettare la scelta salvata) supera il beneficio di manutenibilita' a lungo termine. Nessun codice toccato.

**Se si vuole riprendere in futuro**: la sessione dovrebbe avere accesso a build + emulatore/device per verificare concretamente che `AppCompatDelegate.setApplicationLocales()` (o l'alternativa scelta) preservi il comportamento attuale del selettore lingua ad ogni ricomposizione, prima di toccare anche solo il primo dei 693 call site.

---

## 📍 CHECKPOINT — 2026-09-09, tredicesima parte (merge di `origin/claude/piano-lavoro-remoto-1422cy` in `R3.0.0` + prima verifica reale su build/device)

Sessione con, per la prima volta, build Gradle e device disponibili (`:app:assembleDebug` funzionante da riga di comando dopo aver aggiornato il Gradle wrapper 8.13 -> 9.5.0, incompatibile col JDK 25 imbottito nella build di Android Studio installata). Questo ha permesso di **trovare e correggere bug che nessuna sessione precedente poteva vedere**, non solo procedere sul piano.

### Il merge in se': due branch che avevano fatto la stessa migrazione in parallelo

`R3.0.0` e `piano-lavoro-remoto-1422cy` erano divergenti dallo stesso punto (`f4d0b5e`) e avevano entrambi convertito il fetch del catalogo da "blob intero" a "per-espansione via `/v1/*`", con implementazioni diverse. Conflitti risolti tenendo la versione `R3.0.0` (piu' recente, con `name`/`baseSetCode`/`releaseDate`/`series` su `/v1/expansions` e fallback al catalogo intero se il fast path fallisce); riportate su di essa le modifiche non in conflitto di piano-lavoro (consolidamento `ImageUrlUtils`, logging Timber, rimozione PaddleOCR/TFLite, rename `_v2`/`_v3`, consolidamento docs). Lo split di `DeckLabScreen.kt` in 6 file (voce #15, sez. 8) e' stato **scartato**: operava sulla versione del file precedente alla riscrittura R3.0.0 del giorno prima, avrebbe duplicato ogni simbolo.

### Tre "conflitti semantici" — bug che git non poteva vedere, trovati solo grazie alla build reale

Un ramo aveva rimosso/modificato qualcosa credendolo sicuro nel proprio branch; l'altro ramo, in parallelo, ne dipendeva ancora. Git unisce senza conflitto (righe diverse), il risultato non compila o si comporta male:

1. **`ItalianCatalog.cardsByExpansion()` rimossa**: piano-lavoro l'aveva tolta (commit `6c50223`, "nessun chiamante" -- vero nel suo branch) ma R3.0.0 ne aveva nel frattempo aggiunti 5 usi nel proprio percorso di fallback. `Unresolved reference` alla prima build reale. Ripristinata.
2. **`buildSetImageUrl` senza `italianOnly=true` nel fast path**: piano-lavoro aveva aggiunto il parametro (per marcare `source=ita` verso il Worker, bug #8 punto 2), ma `buildItalianTcgSet` (il fast path R3.0.0, l'unico usato ora) continuava a chiamarla a un argomento -- compilava (il parametro ha un default), ma il client non mandava mai il marcatore. Segnalato dall'utente ("loghi giapponesi ancora visibili"), trovato leggendo il diff, corretto.
3. Questi due punti non erano nel piano: **nessuna quantita' di lettura del codice li avrebbe trovati senza compilare ed eseguire**.

### Bug trovato per segnalazione utente, causa radice diversa da quella ipotizzata

Utente: *"in DeckLab i Tipi sono errati anche quando metto piu' tipo"* (riquadro "Analisi Lab"). Prima ipotesi (sbagliata, poi scartata dopo verifica): `PokeTcgRepository.toItalianTcgCard` incolla `record.tipo` come UN solo elemento di `types` invece di splittarlo -- plausibile ma **verificato con una query D1 diretta** (`SELECT DISTINCT tipo FROM cards`) che all'epoca **zero carte avevano una virgola in `tipo`**, quindi quel percorso non era la causa. Scartata la fix speculativa (avrebbe anche rotto il conteggio "Tipi" per i pochi casi doppi, che oggi funziona per coincidenza tramite lo split su virgola in `analyzeDeck()`).

**Causa reale, trovata sempre con una query D1** (`SELECT expansion_id, COUNT(*) FROM cards WHERE tipo IS NOT NULL GROUP BY expansion_id`): **106 espansioni su 107 avevano `tipo` sempre `NULL`** (15.431 carte) -- solo `me05` (95 carte, ingerita col nuovo script) lo aveva. Il vecchio import in blocco (`import-catalog-to-d1.mjs`, dal blob JSON originale) non aveva mai portato un campo tipo. L'app ripiegava silenziosamente sul tipo del set INGLESE abbinato via PokeWallet, o su `"Colorless"` -- da cui l'incoerenza segnalata.

**Fix**: `scripts/backfill-tipo-tcgdex.mjs` (nuovo, ricalcato su `backfill-rarity-tcgdex.mjs`), locale IT di TCGdex (non EN come la rarita', per restare coerenti col principio "mai un mix di lingue" e coi valori italiani gia' presenti su `me05`). Verificato con un dry-run su tutte le 107 espansioni prima di scrivere. **Applicato in produzione**: 11.181/15.431 carte nuove (**72,5%**, vicino al massimo teorico dato che solo le carte Pokemon hanno un tipo, mai le Trainer/Energia). 22 espansioni restano a 0% -- confermato con curl diretto che TCGdex non ha **alcuna** traduzione italiana per quei set (`/it/sets/dp1` risponde `cards:[]`, `/it/cards/dp1-1` risponde 404, mentre `/en/` ha entrambi): non e' un bug dello script, e' un buco di copertura TCGdex sui set piu' vecchi. Nessuna regressione: quelle espansioni restano sul fallback preesistente.

**Scoperta collaterale dalla stessa query**: la query di verifica post-backfill ha rivelato che esistono davvero poche carte a doppio tipo (es. `"Metallo, Lotta"`) -- quindi il bug ipotizzato all'inizio (e scartato) *esiste*, solo su una manciata di carte reali invece che ovunque: per quelle specifiche, `TypeBadge` riceve l'intera stringa incollata e mostra il badge generico invece del primo tipo. **Non ancora corretto** (interagisce con lo stesso compromesso gia' notato: splittare `types` correttamente sistema il badge ma toglierebbe il secondo tipo dal conteggio "Tipi" in Analisi Lab, perche' quel conteggio dipende dal trucco della virgola in `PokemonCard.type`). Follow-up aperto, non bloccante (poche carte coinvolte).

### Altro bug trovato per lettura, non per segnalazione: nomi tipo italiani sbagliati in `TypeBadge`

Verificato con l'elenco ufficiale TCGdex (`GET /v2/it/types`): i nomi italiani reali sono **"Lampo"** (Lightning) e **"Incolore"** (Colorless) -- gli stessi gia' presenti in D1 per `me05` -- ma `TypeBadge` cercava `"elettro"`/`"normale"`, che non combaciano con nessuno dei due. Ogni carta Lampo o Incolore cadeva nel caso `else` (badge generico). Corretto, tenendo `"elettro"`/`"normale"` come alias di fallback.

### Non fatto, richiede l'utente

1. ~~Deploy del Worker~~ **fatto**, vedi sotto.
2. **Badge sbagliato sulle carte a doppio tipo reale** (vedi sopra) -- poche carte, fix rimandato in attesa di decidere il compromesso conteggio-vs-badge.
3. **Verifica visiva su device** dei fix di oggi (loghi, Tipi in Analisi Lab) -- il codice e i dati sono verificati (D1, build, grep), ma non ancora rivisti a schermo dall'utente dopo l'ultima build.

### Deploy del Worker eseguito, verificato dal vivo

`wrangler deploy` (dopo `npm run type-check` pulito) -- prima volta in questa sessione con accesso diretto a `wrangler` (autenticato, account gia' collegato). Version ID `0bbe7d32`. Verificato con curl reale, non solo "deploy riuscito":
- `GET /v1/expansions` risponde gia' nella forma nuova (`name`/`series`/`baseSetCode`/`releaseDate`)
- `GET /sets/DP1/image?source=ita` -> 200 (DP1 ha davvero un logo proprio in R2, `it/DP1/logo.png` -- confermato con `wrangler r2 object get`)
- `GET /sets/SV05/image?source=ita` -> 404 pulito (nessun logo ne' in R2 ne' su PokeWallet per quel codice)

### Bug #7 (Shiny Vault): la parte pericolosa era gia' risolta, il piano non lo sapeva

Prima di scrivere codice nuovo, verificato lo stato attuale: `buildItalianCardKeyCandidates` (righe ~556-558) gia' non fa piu' fallback a `normalizeCardNumber()` per numeri alfa-prefissati (`isPureNumeric` guard) -- il fix era stato scritto in una sessione precedente ma mai ricollegato a questa voce del piano ne' verificato dal vivo. Verificato ora con curl reale dopo il deploy: `GET /images/it/SWSH45/SV001?size=low` risponde `404 Asset not found`, non piu' l'immagine di Yanma. Controllato anche in R2 (`wrangler r2 object get` su tutti i pattern di chiave provati) che l'immagine vera non esiste sotto **nessuno** di essi -- quello che resta e' un buco di contenuto (acquisizione immagini Shiny Vault/Trainer Gallery), stessa natura del bug #8 punto 1, non un fix di codice.

### Sez. 8 #4 (API key mai nell'APK) -- fatto

`POKETCG_API_KEY` gia' assente dal sorgente (rimossa in una sessione precedente, non registrato nel piano). `POKEWALLET_API_KEY`: verificato che il client la allega solo se il proxy e' disattivato (`PokeWalletRepository` fallisce gia' chiuso altrimenti) -- ma restava comunque scritta in chiaro nell'APK di release via `BuildConfig`, estraibile decompilando anche se mai usata a runtime in produzione. `buildTypes.release` in `app/build.gradle.kts` ora forza `POKEWALLET_API_KEY = ""` e `POKEWALLET_PROXY_ENABLED = true` indipendentemente da `local.properties`, cosi' una release non puo' piu' finire nella configurazione pericolosa (proxy spento + chiave presente). Build debug invariata. Verificato sul `BuildConfig.java` generato (non solo per lettura del gradle script) + `:app:assembleRelease` verde.

### Sez. 8 #1 (Migration Room reale) -- rivalutata e chiusa con una soluzione piu' semplice

Prima di scrivere `Migration(1,2)` + `MigrationTestHelper` come chiedeva la voce alla lettera, verificato cosa protegge davvero `PokeVaultDatabase`: contiene solo `CachedSetEntity`/`CachedCardEntity`/`CachedPriceEntity` -- una cache locale di dati di rete (PokeWallet/D1), sempre ricostruibile. I dati veri dell'utente (carte possedute, mazzi, album, wishlist, tornei, log partite) vivono tutti su **Firestore** (`FirestoreRepository.kt`, cloud), mai in Room -- verificato leggendo le collection reali (`users/{id}/cards|decks|albums|wishlists|match_logs|tournaments|goal_albums`).

Conseguenza: senza un fallback per l'upgrade (`fallbackToDestructiveMigrationOnDowngrade(false)` copriva solo il downgrade), un futuro bump di versione senza `Migration` esplicita farebbe **crashare l'app all'avvio**, non perdere dati reali. Costruire Migration incrementali per un database che e' solo cache sarebbe lavoro sproporzionato al rischio effettivo. Cambiato in `fallbackToDestructiveMigration(true)`: un bump futuro ricostruisce la cache da rete invece di crashare. Se in futuro questo database dovesse mai contenere qualcosa di non ricostruibile, questa scelta va rivista.
