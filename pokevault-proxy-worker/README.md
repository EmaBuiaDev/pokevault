# Pokévault Cloudflare Worker Proxy

Global caching layer for PokeWallet API using Cloudflare Workers + KV storage.

## Quick Start

### Deploy Worker

```bash
# 1. Install dependencies
npm install

# 2. Login to Cloudflare
wrangler login

# 3. Create KV namespace
wrangler kv:namespace create pokevault-cache

# 4. Update wrangler.toml with:
#    - Your Account ID (from wrangler whoami)
#    - Your KV namespace IDs (from step 3)

# 5. Store API key
wrangler secret put POKEWALLET_API_KEY
# Paste your POKEWALLET_API_KEY when prompted

# 6. Deploy
wrangler publish
```

After deployment, your Worker will be available at:
```
https://pokevault-proxy.<ACCOUNT_ID>.workers.dev/
```

### Test Worker

```bash
# First request (cache miss)
curl "https://pokevault-proxy.<ACCOUNT_ID>.workers.dev/sets"
# Response includes: X-Cache-Status: MISS

# Second request (cache hit)
curl "https://pokevault-proxy.<ACCOUNT_ID>.workers.dev/sets"
# Response includes: X-Cache-Status: HIT
```

### Configure Android App

In `local.properties`:
```properties
POKEWALLET_PROXY_ENABLED=true
POKEWALLET_PROXY_URL=https://pokevault-proxy.<ACCOUNT_ID>.workers.dev/
```

Then rebuild the Android app.

---

## Features

✅ **Global Caching** — Responses cached in Cloudflare KV for 3 days  
✅ **Fast Fallback** — Stale cache served if PokeWallet API is unreachable  
✅ **API Key Security** — Secret stored server-side (never exposed to clients)  
✅ **Debug Headers** — `X-Cache-Status`, `X-Cached-At` for monitoring  
✅ **Real Set Totals Enrichment** — `/sets` responses can be transparently overridden with exact card totals precomputed in KV  
✅ **Free Tier** — Cloudflare free tier fully supported  

---

## Development

### Local Testing

```bash
npm run dev
# Worker runs on http://localhost:8787
curl http://localhost:8787/sets
```

### Type Checking

```bash
npm run type-check
```

### Real Totals Backfill

The worker now supports a scheduled backfill that computes exact set totals using the same filtering rules as the app set detail screen:

- reads upstream `/sets`
- processes a small ENG/JAP/CHN batch each cron run
- fetches set pages from upstream
- filters out non-card products
- stores exact totals in KV
- overrides raw `/sets` metadata before sending it to the app

Default schedule and batch size are configured in [wrangler.toml](wrangler.toml):

```toml
BACKFILL_BATCH_SIZE = "6"
crons = ["*/30 * * * *"]
```

You can increase or decrease `BACKFILL_BATCH_SIZE` depending on your API budget.

---

## Architecture

```
Android App
    ↓
Retrofit (Base URL: Worker)
    ↓
Cloudflare Worker (pokevault-proxy)
    ├─ Check KV Cache
    ├─ If HIT: Return cached response (50-100ms)
    └─ If MISS: 
       ├─ Call PokeWallet API with X-API-Key
       ├─ Cache response in KV (TTL: 3 days)
       └─ Return response (1-2s)

Scheduled Backfill
    ├─ Read upstream `/sets`
    ├─ Process next small batch of ENG/JAP/CHN sets
    ├─ Compute exact filtered card totals
    ├─ Persist totals index in KV
    └─ Override future `/sets` responses with exact totals

Firebase (Unchanged)
    ↓
Firestore (User auth + card collection)
```

---

## Documentation

- [Full Deployment Guide](./DEPLOYMENT_GUIDE.md)
- [Cloudflare Workers Docs](https://developers.cloudflare.com/workers/)
- [Worker Code](./src/index.ts)

---

## Monitoring

**View real-time logs:**
```bash
wrangler tail
```

**List cached keys:**
```bash
wrangler kv:key list --namespace-id=YOUR_NAMESPACE_ID
```

---

## Troubleshooting

**502 Bad Gateway?**
- Check API key: `wrangler secret list`
- View logs: `wrangler tail`

**Cache not working?**
- Verify KV namespace is bound in `wrangler.toml`
- Check cache keys: `wrangler kv:key list`

**App not using proxy?**
- Verify `local.properties`: `POKEWALLET_PROXY_ENABLED=true`
- Rebuild Android app: `./gradlew clean build`

---

## Support

See [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) for detailed troubleshooting and FAQ.
