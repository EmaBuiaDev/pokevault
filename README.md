# PokéVault

App Android nativa (Kotlin + Jetpack Compose) per collezionisti di carte
Pokémon: catalogo, gestione collezione, prezzi, deck lab e scanner OCR.

> Questo repository è a metà di una migrazione infrastrutturale in corso —
> da un catalogo interamente dipendente da PokeWallet (inglese) a un
> **catalogo italiano proprietario** su Cloudflare (D1 + R2 + Workers), con
> ingest automatico dei nuovi set. Lo stato dettagliato, aggiornato a ogni
> sessione di lavoro con comandi ed esiti reali, è in
> **[`MIGRATION_PLAN.md`](MIGRATION_PLAN.md)** — leggerlo prima di toccare
> qualsiasi cosa lato catalogo/prezzi/immagini.

## Struttura del repository

| Percorso | Cosa contiene |
|---|---|
| `app/` | App Android (`com.emabuia.pokevault`, `minSdk 26`, `compileSdk 36`) |
| `pokevault-proxy-worker/` | Worker Cloudflare (`pokevault-proxy`): API `/v1/*`, catalogo D1, immagini R2, prezzi PokeWallet isolati dietro il Worker — vedi il suo [README](pokevault-proxy-worker/README.md) |
| `docs/` | Documentazione di sviluppo ([ambienti](docs/AMBIENTI.md), [testing](docs/TESTING.md), ecc.) |
| `firebase.json`, `.firebaserc` | Regole e indici Firestore versionati, con gli alias dei due progetti — vedi [`docs/AMBIENTI.md`](docs/AMBIENTI.md) |
| `MIGRATION_PLAN.md` | Piano operativo della migrazione al catalogo proprietario, con checkpoint di sessione verificati |

## Stack tecnico

- **Android**: Kotlin, Jetpack Compose, Retrofit + OkHttp + Gson, Coil per le immagini, Room per la cache locale, Firebase Auth + Firestore per utenti/collezioni, WorkManager per la sincronizzazione in background
- **Edge**: Cloudflare Workers (TypeScript) + KV (cache) + R2 (immagini) + D1 (catalogo, SQLite distribuito)
- **Ingest**: script Node.js schedulati via GitHub Actions, sorgente dati `tcgdex/cards-database` (MIT)

## Setup locale

```bash
cp local.properties.example local.properties
# compila POKEWALLET_API_KEY / ITALIAN_CATALOG_URL solo se serve bypassare il proxy Cloudflare
./gradlew assembleProdDebug
```

Richiede **JDK 21** per Gradle (il JBR 25 incluso in Android Studio più recente
è incompatibile con Kotlin 2.0.21 — vedi `MIGRATION_PLAN.md` per il dettaglio
dell'errore). `local.properties` non va mai committato: contiene chiavi API e,
per le build di release, la configurazione della firma.

## Ambienti

Due flavor, `prod` e `staging`, su due progetti Firebase distinti: provare
l'app non significa più scrivere nei dati veri. Hanno `applicationId` diversi,
quindi si installano insieme sullo stesso telefono.

```bash
./gradlew installStagingDebug   # dati separati, per sviluppare
./gradlew installProdDebug      # quello che finisce su Play
```

Il Worker Cloudflare resta condiviso; gli acquisti Play e le rotte
autenticate del Worker funzionano solo in produzione. Setup del progetto
Firebase di staging, regole Firestore versionate e nomi dei task cambiati:
**[`docs/AMBIENTI.md`](docs/AMBIENTI.md)**.

## Testing

Guida completa in [`docs/TESTING.md`](docs/TESTING.md): comandi Gradle, template
per nuovi test, come funziona la CI su GitHub Actions e i problemi noti dei
due workflow attuali.

```bash
./gradlew testProdDebugUnitTest   # unit test, veloce
./gradlew connectedProdDebugAndroidTest  # test instrumentati, richiede emulatore
```

## Worker Cloudflare

Il proxy/backend edge vive in `pokevault-proxy-worker/` ed è un progetto npm
a sé (deploy con `wrangler`, non con Gradle). Dettagli, endpoint, script di
ingest e comandi di troubleshooting nel suo
[README](pokevault-proxy-worker/README.md).

## Stato del debito tecnico

Un elenco vivo di bug noti, pulizia repository e lavoro rimandato è nella
sezione 8 di [`MIGRATION_PLAN.md`](MIGRATION_PLAN.md#8-lista-bugfix-e-pulizia-m7).
Non duplicato qui per evitare che i due documenti divergano.

## Convenzioni di codice

- **Colori**: token in `ui/theme/`, con tema chiaro e scuro. Vanno letti da
  `AppColors`, mai scritti come costanti fisse nei composable. Dentro lo scope
  di disegno di `Canvas` i token vanno risolti prima, non letti nel blocco.
- **Testi dell'interfaccia**: `util/AppLocale.kt`. Il progetto non usa
  `res/values-*/strings.xml` per le stringhe della UI.
