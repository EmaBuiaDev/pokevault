# Pokévault Cloudflare Worker (`pokevault-proxy`)

Backend edge del catalogo italiano PokeVault. Non è più un semplice proxy-cache
davanti a PokeWallet: espone il catalogo proprietario (D1 + R2) e isola
PokeWallet ai soli prezzi. Storia ed evoluzione complete in
[`../MIGRATION_PLAN.md`](../MIGRATION_PLAN.md).

## Cosa serve oggi

| Endpoint | Sorgente | Note |
|---|---|---|
| `GET /v1/health` | — | `{"status":"ok","catalog_version":N}` |
| `GET /v1/expansions` | D1 | Solo espansioni con `published=1` (copertura IT ≥ soglia) |
| `GET /v1/expansions/{id}/cards` | D1 | Carte di una singola espansione |
| `GET /v1/cards/{cardId}` | D1 | Record singola carta |
| `GET /ita/catalog.json` | D1 (fallback: blob R2) | Legacy, usato dal client per il full-catalog e la ricerca |
| `GET /ita/prices.json` | KV (snapshot bulk PokeWallet) | Prezzi ITA→ENG pre-uniti |
| `GET /images/it/{set}/{numero}` | R2 (`pokevault-images`) | Prova `.webp` prima di `.png`, più varianti di chiave per i set con prefissi letterali (Shiny Vault, Trainer Gallery) e per i promo (`SVP`, `mep`, ...) |
| Route legacy (`/sets`, `/cards`, `/search`, ...) | KV cache davanti a `api.pokewallet.io` | Comportamento originale del proxy, invariato |

Bindings in `wrangler.toml`: `CACHE` (KV), `IMAGES_BUCKET` (R2), `pokevault_catalog` (D1).
Cron `*/30 * * * *` per il backfill dei conteggi reali PokeWallet.

## Setup locale

```bash
npm install
npm run type-check      # tsc --noEmit
npm run dev             # wrangler dev, http://localhost:8787
```

## Deploy

Il progetto è **già deployato** in produzione
(`pokevault-proxy.pokevault-emanu.workers.dev`, account Cloudflare
`emanuelebuia@live.it`) — non serve rifare il setup da zero. Per un nuovo
ambiente:

```bash
wrangler login
wrangler secret put POKEWALLET_API_KEY
wrangler d1 execute pokevault-catalog --remote --file schema/001_init.sql
wrangler deploy
```

Le migration successive dello schema sono in `schema/00N_*.sql`, da applicare
in ordine con `wrangler d1 execute pokevault-catalog --remote --file schema/00N_....sql`.
D1 non tratta un secondo `ALTER TABLE` sulla stessa colonna come errore fatale
solo se lo script chiamante lo gestisce esplicitamente (vedi
`.github/workflows/ops-dominant-set-code.yml` per il pattern).

**Prima di ogni deploy che tocca la risoluzione delle immagini**
(`buildItalianCardKeyCandidates`/`handleItalianR2AssetRequest`): il Worker
cachea le risposte immagine in **KV** con TTL 90 giorni. Un fix di logica non
invalida le risposte già cachate — serve un purge mirato:

```bash
wrangler kv key list --namespace-id=<ID> --prefix "pokewallet:/images/it/<SET>/"
wrangler kv bulk delete <lista-chiavi> --namespace-id=<ID>
```

## Script di ingest e manutenzione (`scripts/`)

| Script | Cosa fa |
|---|---|
| `ingest-tcgdex-set.mjs <setId> [--apply]` | Importa un set da TCGdex (MIT, gratuito) in D1+R2; dry-run di default |
| `discover-new-sets.mjs [--ingest]` | Confronta TCGdex vs D1, apre issue per i set genuinamente nuovi (usato dal cron) |
| `import-catalog-to-d1.mjs` | Import one-shot del blob JSON storico in D1 (batch da 50 righe, oltre D1 rifiuta con `SQLITE_TOOBIG`) |
| `recompress-webp.mjs` | Ricomprime le immagini PNG storiche in WebP, upload additivo accanto all'originale |
| `upload-ita-r2.ps1` | Upload manuale da cartella locale (unico percorso per lo storico pre-TCGdex) |

Tutti gli script D1/R2 richiedono `wrangler` autenticato (`wrangler whoami`) e
vanno lanciati con `--remote` esplicito — senza, `wrangler` scrive nello storage
locale simulato invece che in produzione (causa nota di falsi negativi, vedi
`MIGRATION_PLAN.md`).

## Automazione CI

`.github/workflows/catalog-ingest.yml`: cron giornaliero (06:00 UTC) +
`workflow_dispatch`, esegue `discover-new-sets.mjs --ingest`. Richiede il
secret di repository `CLOUDFLARE_API_TOKEN` (permessi minimi: `D1 Edit`,
`Workers R2 Storage Edit` sull'account del progetto).

## Monitoraggio e troubleshooting

```bash
wrangler tail                                  # log realtime
wrangler d1 execute pokevault-catalog --remote --command "SELECT COUNT(*) FROM cards"
wrangler kv key list --namespace-id=<ID>       # chiavi in cache
wrangler r2 object get pokevault-images/<key> --remote --file out.png
```

| Sintomo | Causa probabile | Verifica |
|---|---|---|
| 502 dalle route legacy PokeWallet | Secret mancante o API upstream giù | `wrangler secret list`, `wrangler tail` |
| Immagine sbagliata servita per una sotto-collezione (Shiny Vault, Trainer Gallery) | Cache KV non purgata dopo un fix — vedi sopra | Purge mirato, poi ricontrollare con `X-Cache-Status: MISS` |
| `wrangler r2 object get/put` crasha su Windows | Manca `--remote` esplicito (wrangler 4 non lo assume) | Ripetere il comando con `--remote` |
| App continua a chiamare `api.pokewallet.io` direttamente | `POKEWALLET_PROXY_ENABLED=false` in `local.properties` locale | Impostare `true` + `POKEWALLET_PROXY_URL`, ricompilare |

## Storia e decisioni

Tutta l'evoluzione (perché D1 e non solo KV, la regola di copertura al 80%,
i bug scoperti e risolti, cosa resta da fare) è documentata con i comandi
reali eseguiti in [`../MIGRATION_PLAN.md`](../MIGRATION_PLAN.md). Questo
README descrive solo lo stato attuale, non il percorso per arrivarci.
