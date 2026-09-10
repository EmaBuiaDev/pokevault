# PokéVault

App Android per la gestione di collezioni Pokémon TCG, con backend su Cloudflare
Workers. Interfaccia in italiano e inglese.

## Struttura del repository

| Percorso | Contenuto |
|---|---|
| `app/` | App Android (Kotlin, Jetpack Compose, Material 3) |
| `pokevault-proxy-worker/` | Cloudflare Worker: catalogo su D1, immagini su R2, cache su KV |
| `docs/` | Sito GitHub Pages (privacy policy, termini) |
| `docs-dev/` | Note di sviluppo (setup GitHub, comandi rapidi) |
| `TESTING.md` | Come eseguire e scrivere i test |
| `MIGRATION_PLAN.md` | Diario della migrazione del catalogo verso D1 |

## Requisiti

- JDK 17 (AGP 8.13 non gira con versioni precedenti)
- Android SDK con `compileSdk` 36
- Node 22 per il Worker

## Configurazione

Le chiavi non stanno nel repository. Copia `local.properties.example` in
`local.properties` e valorizza le voci che ti servono:

```properties
POKETCG_API_KEY=...
POKEWALLET_API_KEY=...
POKEWALLET_PROXY_ENABLED=true
POKEWALLET_PROXY_URL=https://...
ITALIAN_CATALOG_URL=https://...
```

Con `POKEWALLET_PROXY_ENABLED=true` le chiamate passano dal Worker e la chiave
PokeWallet non viene inclusa nell'APK.

## Build e test

```bash
./gradlew testDebugUnitTest      # test unitari
./gradlew assembleDebug          # APK di debug
./gradlew assembleRelease        # richiede la configurazione di firma
```

La release ha `minifyEnabled` attivo: dopo modifiche a modelli serializzati o
alla libreria di billing, provala davvero prima di pubblicare.

### Worker

```bash
cd pokevault-proxy-worker
npm ci
npx tsc --noEmit                 # typecheck
npx wrangler dev                 # esecuzione locale
```

## Architettura in breve

- **Dati utente**: Firestore per collezione, deck, album, wishlist e tornei, con
  cache locale persistente abilitata all'avvio.
- **Catalogo carte**: servito dal Worker su `/v1` (D1), con Room come cache
  locale delle carte e dei set in inglese.
- **Immagini**: R2 dietro il Worker, con Coil lato app (cache 256 MB su disco).
- **Tema**: token di colore in `ui/theme/`, con supporto chiaro e scuro. I
  colori vanno letti da `AppColors`, mai come costanti fisse.
- **Testi**: `util/AppLocale.kt`. Il progetto non usa `res/values-*/strings.xml`
  per le stringhe dell'interfaccia.

## CI

`.github/workflows/`:

- `android-tests.yml` — test unitari su push e pull request. È il workflow di
  riferimento.
- `android-advanced-tests.yml` — **attualmente rotto**: usa JDK 11, che AGP 8.13
  non supporta, e invoca `jacocoTestDebugUnitTestReport`, task che non esiste
  perché nessun `JacocoReport` è registrato.
- `catalog-ingest.yml` — ingest giornaliero dei nuovi set.
- `ops-dominant-set-code.yml` — migrazione one-off, già eseguita in produzione.
