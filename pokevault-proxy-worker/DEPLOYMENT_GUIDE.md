# Pokévault Cloudflare Proxy Deployment Guide

## Overview

This guide walks you through deploying the Cloudflare Worker for caching the PokeWallet API and configuring the Android app to use it.

**Architecture:**
- **Cloudflare Worker** (pokevault-proxy): Caches PokeWallet API responses for 3 days in KV storage
- **Android App**: Routes PokeWallet requests through the Worker (optional, toggle via `local.properties`)
- **Firebase**: Remains untouched for authentication and user card collections

---

## Prerequisites

1. **Node.js & npm** installed (v18+)
2. **Wrangler CLI** (Cloudflare Workers CLI):
   ```bash
   npm install -g @cloudflare/wrangler
   ```
3. **Cloudflare Account** (free tier supported):
   - Sign up at https://dash.cloudflare.com
   - Note your Account ID (visible in account settings)

4. **PokeWallet API Key** (from current setup)
   - This is the same `POKEWALLET_API_KEY` you already have

---

## Phase 1: Deploy Cloudflare Worker

### Step 1: Navigate to Worker Project

```bash
cd pokevault-proxy-worker
npm install
```

### Step 2: Authenticate with Cloudflare

```bash
wrangler login
```

This opens a browser to authenticate. Authorize the CLI with your Cloudflare account.

**Verify login:**
```bash
wrangler whoami
```
Output will show your account email and Account ID. **Save the Account ID** for later.

### Step 3: Create KV Namespace

KV (Key-Value) storage is where cached responses are stored.

```bash
wrangler kv:namespace create pokevault-cache
```

**Output:**
```
Created namespace with ID: <NAMESPACE_ID>
Created preview namespace with ID: <PREVIEW_NAMESPACE_ID>
```

**Save both IDs** — you'll need them for `wrangler.toml`.

### Step 4: Update `wrangler.toml`

Open `pokevault-proxy-worker/wrangler.toml` and fill in:

```toml
account_id = "YOUR_ACCOUNT_ID"  # From "wrangler whoami"

[env.production]
kv_namespaces = [
  { binding = "CACHE", id = "YOUR_NAMESPACE_ID", preview_id = "YOUR_PREVIEW_NAMESPACE_ID" }
]
```

### Step 5: Store API Key as Secret

The PokeWallet API key is stored securely in Cloudflare Secrets (never exposed in code):

```bash
# From pokevault-proxy-worker directory
wrangler secret put POKEWALLET_API_KEY
```

When prompted, paste your `POKEWALLET_API_KEY` value and press Enter.

**Verify secret was stored:**
```bash
wrangler secret list
```

Should output `POKEWALLET_API_KEY` in the list.

### Step 6: Deploy Worker

```bash
wrangler publish
```

**Output:**
```
Uploaded pokevault-proxy (XX.XXXXXXX sec)
Published to https://pokevault-proxy.<ACCOUNT_ID>.workers.dev
```

**Save this URL** — you'll use it in the Android app.

### Step 7: Test Worker

Test the deployment with curl (or Postman):

```bash
curl "https://pokevault-proxy.<ACCOUNT_ID>.workers.dev/sets"
```

**Expected response:**
- First call: `X-Cache-Status: MISS` (fetches from PokeWallet API, caches response)
- Response body: List of Pokemon sets in JSON format
- Response time: ~1-2 seconds

**Second call (wait ~5 seconds):**
```bash
curl "https://pokevault-proxy.<ACCOUNT_ID>.workers.dev/sets"
```

**Expected response:**
- `X-Cache-Status: HIT` (served from KV cache)
- Response time: ~50-100ms (much faster!)

**If you get 502 or errors:**
- Verify `POKEWALLET_API_KEY` secret was set correctly: `wrangler secret list`
- Check Worker logs: `wrangler tail` (real-time logs)
- Verify KV namespace ID is correct in `wrangler.toml`

---

## Phase 2: Configure Android App

### Step 1: Copy local.properties Template

From project root:
```bash
cp local.properties.example local.properties
```

### Step 2: Edit `local.properties`

Open `local.properties` and update:

```properties
# Enable Cloudflare Worker proxy
POKEWALLET_PROXY_ENABLED=true

# URL of your deployed Worker
POKEWALLET_PROXY_URL=https://pokevault-proxy.<YOUR_ACCOUNT_ID>.workers.dev/

# Keep these for fallback (if proxy disabled):
POKETCG_API_KEY=your_key_here
POKEWALLET_API_KEY=your_key_here
```

**Important:**
- Replace `<YOUR_ACCOUNT_ID>` with your actual Cloudflare Account ID
- Ensure URL ends with `/` (trailing slash required)
- Do NOT commit `local.properties` to git (it contains secrets)

### Step 3: Rebuild Android App

In Android Studio:
1. **File > Sync Now** (to reload `local.properties`)
2. **Build > Clean Project**
3. **Build > Make Project**

Or via terminal:
```bash
./gradlew clean build
```

### Step 4: Verify Network Traffic

Run app on device/emulator:
1. Open app, navigate to any card set
2. Open Logcat (in Android Studio)
3. Filter for "Retrofit" or "PokeWallet"
4. You should see network calls to your Worker URL (not direct `api.pokewallet.io`)

Example log:
```
→ GET https://pokevault-proxy.abc123xyz.workers.dev/sets HTTP/1.1
```

---

## Phase 3: Verify Everything Works

### Test Scenarios

**1. First App Launch (Cache Miss)**
- Navigate to Sets screen
- Wait ~2 seconds for data to load
- Network request hits Worker → Worker calls PokeWallet API → Response cached in KV

**2. Subsequent Loads (Cache Hit)**
- Navigate away and back to Sets screen
- Load time should be <500ms
- Data served from KV cache (same 3-day cached data)

**3. Search & Card Details**
- Search for a specific card
- Open card detail page
- Both should load quickly (cached by Worker)

**4. Offline Fallback**
- Simulate network error (turn off WiFi/airplane mode)
- Room database provides offline fallback
- App gracefully handles missing data

**5. Firebase Integration Untouched**
- Sign in with your account
- Your card collection loads from Firebase Firestore (unchanged)
- All personal data syncs normally
- No modifications to Firebase auth or storage

### Monitor Cache Performance

**In Cloudflare Dashboard:**
1. Go to https://dash.cloudflare.com
2. Navigate to **Workers** > **pokevault-proxy**
3. View analytics dashboard:
   - Request count
   - Cache hit ratio
   - Error rates
   - P50/P95 response times

**Expected metrics after 24 hours:**
- Cache hit ratio: 70-80% (initial requests miss, subsequent hits)
- Average response time: 100-150ms (KV cache) vs 1-2s (API)
- Zero API calls for repeat requests (massive credit savings!)

---

## Fallback: Disable Proxy

If you want to revert to direct API calls:

1. Edit `local.properties`:
   ```properties
   POKEWALLET_PROXY_ENABLED=false
   ```

2. Rebuild app:
   ```bash
   ./gradlew clean build
   ```

The app will automatically use direct API calls to `api.pokewallet.io`.

---

## Troubleshooting

### Problem: 502 Bad Gateway from Worker

**Causes:**
- API key secret not set
- PokeWallet API is down
- Incorrect KV namespace binding

**Solutions:**
1. Verify secret: `wrangler secret list | grep POKEWALLET_API_KEY`
2. Check logs: `wrangler tail`
3. Test direct API: `curl -H "X-API-Key: YOUR_KEY" https://api.pokewallet.io/sets`

### Problem: App Still Calling api.pokewallet.io (Not Using Proxy)

**Causes:**
- `local.properties` not saved
- Android Studio needs sync
- `POKEWALLET_PROXY_ENABLED` is not `true`

**Solutions:**
1. Verify `local.properties`:
   ```bash
   grep POKEWALLET_PROXY local.properties
   ```
2. In Android Studio: **File > Sync Now**
3. Rebuild: **Build > Clean Project** then **Build > Make Project**

### Problem: Worker Response is Slow (Not Hitting Cache)

**Causes:**
- First request is always slow (cache miss)
- KV namespace not properly bound
- Cache TTL expired (3 days)

**Solutions:**
1. Make 2+ requests to same endpoint
2. Verify cache: `wrangler kv:key list --namespace-id=YOUR_NAMESPACE_ID`
3. Wait 3 seconds between requests, then re-test

### Problem: Firebase Queries Not Working

**Solution:**
Firebase queries should be **completely unaffected**. If they're broken:
- Verify Firebase configuration (google-services.json)
- Check Firestore permissions in Firebase console
- Ensure user is authenticated
- This is a Firebase issue, not related to the proxy

---

## Advanced: Monitoring & Analytics

### Enable Worker Logs

```bash
# Real-time logs
wrangler tail

# Filter for specific paths
wrangler tail --format json | grep "/sets"
```

### KV Storage Management

**List all cached keys:**
```bash
wrangler kv:key list --namespace-id=YOUR_NAMESPACE_ID
```

**Get specific cached value:**
```bash
wrangler kv:key get "pokewallet:/sets" --namespace-id=YOUR_NAMESPACE_ID
```

**Delete cache entry:**
```bash
wrangler kv:key delete "pokewallet:/sets" --namespace-id=YOUR_NAMESPACE_ID
```

**Purge entire cache:**
```bash
wrangler kv:namespace delete pokevault-cache
wrangler kv:namespace create pokevault-cache
# Update wrangler.toml with new namespace IDs
```

---

## Production Checklist

Before going live:

- [ ] Worker deployed: `wrangler publish`
- [ ] KV namespace created and bound in `wrangler.toml`
- [ ] API key secret set: `wrangler secret list`
- [ ] Worker tested: `curl` returns 200 with `X-Cache-Status: MISS|HIT`
- [ ] Android app rebuilt: `./gradlew clean build`
- [ ] `local.properties` configured with Worker URL
- [ ] Firebase integration verified (auth + Firestore working)
- [ ] Room cache still available for offline fallback
- [ ] No errors in Logcat for API calls
- [ ] Monitored Worker dashboard for 24 hours

---

## Support

**Issues?**
1. Check this guide (Troubleshooting section)
2. View Worker logs: `wrangler tail`
3. Review Cloudflare Dashboard: https://dash.cloudflare.com
4. Test Worker directly: `curl -v https://pokevault-proxy.<ID>.workers.dev/sets`

**Documentation:**
- [Cloudflare Workers](https://developers.cloudflare.com/workers/)
- [Cloudflare KV](https://developers.cloudflare.com/workers/runtime-apis/kv/)
- [Wrangler CLI](https://developers.cloudflare.com/workers/wrangler/)
