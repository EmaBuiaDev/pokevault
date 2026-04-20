# Quick Deployment Commands

Copy & paste these commands in order to deploy the Cloudflare Worker.

## 1. Setup (One-time)

```bash
# Install Node.js + Wrangler CLI (if not already installed)
npm install -g wrangler

# Navigate to Worker directory
cd pokevault-proxy-worker

# Install dependencies
npm install
```

## 2. Authenticate

```bash
wrangler login
# Browser opens → Authorize → Copy/paste authorization code

# Verify login
wrangler whoami
# Shows: Account Email and Account ID (SAVE THIS)
```

## 3. Create KV Namespace

```bash
wrangler kv namespace create pokevault-cache
wrangler kv namespace create pokevault-cache-preview --preview
# Output shows:
# Created namespace with ID: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
# Created preview namespace with ID: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
# SAVE BOTH IDS
```

## 4. Update wrangler.toml

Edit `pokevault-proxy-worker/wrangler.toml`:
- Line 2: Replace `account_id = ""` with your Account ID from step 2
- Line 8: Replace `id = ""` with namespace ID from step 3
- Line 9: Replace `preview_id = ""` with preview namespace ID from step 3

```toml
account_id = "1a2b3c4d5e6f7g8h9i0j"  # Your Account ID

[env.production]
kv_namespaces = [
  { binding = "CACHE", id = "abc123def456ghi789jkl", preview_id = "xyz987wvu654tsr321onm" }
]
```

## 5. Store API Key

```bash
# From pokevault-proxy-worker directory
wrangler secret put POKEWALLET_API_KEY

# When prompted: Paste your POKEWALLET_API_KEY (from local.properties)
# Then press Enter twice

# Verify
wrangler secret list
# Shows: POKEWALLET_API_KEY
```

## 6. Deploy Worker

```bash
wrangler deploy
# Output: Published to https://pokevault-proxy.ACCOUNT_ID.workers.dev
# SAVE THIS URL
```

## 7. Test Worker

```bash
# Replace ACCOUNT_ID with your actual ID
curl "https://pokevault-proxy.ACCOUNT_ID.workers.dev/sets"

# Expected: JSON response with header X-Cache-Status: MISS
# Response time: ~1-2 seconds (first call, cache miss)

# Second call (wait 2 seconds):
curl "https://pokevault-proxy.ACCOUNT_ID.workers.dev/sets"
# Expected: Same response, but X-Cache-Status: HIT
# Response time: ~50-100ms (cached response)
```

## 8. Configure Android App

Edit `local.properties` (from project root):

```properties
POKEWALLET_PROXY_ENABLED=true
POKEWALLET_PROXY_URL=https://pokevault-proxy.ACCOUNT_ID.workers.dev/
POKETCG_API_KEY=your_key_here
POKEWALLET_API_KEY=your_key_here
```

## 9. Rebuild Android App

```bash
# From project root
./gradlew clean build

# Or in Android Studio:
# Build > Clean Project
# Build > Make Project
```

## 10. Test App

1. Run app on device/emulator
2. Open "Sets" screen → should load sets
3. Check Logcat for "https://pokevault-proxy.ACCOUNT_ID.workers.dev"
4. Sign in → personal collection should load from Firebase (unchanged)

---

## Troubleshooting Quick Fixes

**Worker returns 502?**
```bash
wrangler secret list
# Make sure POKEWALLET_API_KEY is in the list

wrangler tail
# View real-time logs for errors
```

**App not using proxy?**
```bash
# Verify local.properties exists and has correct values
Get-Content local.properties | Select-String POKEWALLET_PROXY

# Rebuild with clean
./gradlew clean build
```

**Test direct Worker call**
```bash
# Should return JSON, not error
curl -v "https://pokevault-proxy.ACCOUNT_ID.workers.dev/sets"
```

---

## Useful Commands Reference

```bash
# View Worker logs
wrangler tail

# List all cached keys
wrangler kv:key list --namespace-id=YOUR_NAMESPACE_ID

# Get specific cached value
wrangler kv:key get "pokewallet:/sets" --namespace-id=YOUR_NAMESPACE_ID

# Clear specific cache entry
wrangler kv:key delete "pokewallet:/sets" --namespace-id=YOUR_NAMESPACE_ID

# View secrets
wrangler secret list

# Redeploy after changes
wrangler deploy
```

---

## Success Checklist

- [ ] `wrangler whoami` shows Account ID
- [ ] `wrangler kv namespace create pokevault-cache` returns namespace ID
- [ ] `wrangler kv namespace create pokevault-cache-preview --preview` returns preview namespace ID
- [ ] `wrangler.toml` filled with Account ID and namespace IDs
- [ ] `wrangler secret list` shows POKEWALLET_API_KEY
- [ ] `wrangler publish` succeeds
- [ ] `curl https://pokevault-proxy.ACCOUNT_ID.workers.dev/sets` returns JSON
- [ ] `local.properties` has `POKEWALLET_PROXY_ENABLED=true` and Worker URL
- [ ] `./gradlew clean build` succeeds
- [ ] App loads Sets screen
- [ ] Logcat shows requests to Worker URL
- [ ] Firebase personal collection loads
- [ ] All set! 🎉
