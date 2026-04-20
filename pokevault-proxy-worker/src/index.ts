/**
 * Pokévault PokeWallet API Proxy Worker
 * 
 * Mirrors PokeWallet API (api.pokewallet.io) with Cloudflare KV caching.
 * - Uses route-specific TTLs for long-lived catalog content vs. volatile searches/prices
 * - Injects POKEWALLET_API_KEY on upstream requests
 * - Returns cache status headers for debugging
 */

interface Env {
  CACHE: KVNamespace;
  POKEWALLET_API_KEY: string;
  ORIGIN_API: string;
  CACHE_TTL_SECONDS: string;
}

interface CachedResponse {
  status: number;
  statusText: string;
  headers: Record<string, string>;
  body: string;
  bodyEncoding?: 'text' | 'base64';
  cachedAt: number;
  ttl: number;
}

const TTL_24_HOURS = 24 * 60 * 60;
const TTL_90_DAYS = 90 * 24 * 60 * 60;

function normalizeSearchParams(searchParams: URLSearchParams): string {
  const entries = Array.from(searchParams.entries())
    .sort(([keyA, valueA], [keyB, valueB]) => {
      if (keyA === keyB) {
        return valueA.localeCompare(valueB);
      }
      return keyA.localeCompare(keyB);
    });

  if (entries.length === 0) {
    return '';
  }

  const normalized = new URLSearchParams();
  for (const [key, value] of entries) {
    normalized.append(key, value);
  }
  return `?${normalized.toString()}`;
}

function getBaseTtlSeconds(pathname: string, fallbackTtl: number): number {
  if (pathname === '/search') {
    return TTL_24_HOURS;
  }

  if (pathname.startsWith('/images/')) {
    return TTL_90_DAYS;
  }

  if (pathname === '/sets' || pathname.startsWith('/sets/')) {
    return pathname.endsWith('/image') ? TTL_90_DAYS : TTL_90_DAYS;
  }

  if (pathname.startsWith('/cards/')) {
    return TTL_90_DAYS;
  }

  return fallbackTtl;
}

function getTtlSeconds(pathname: string, status: number, fallbackTtl: number): number {
  if (status === 404 && (pathname.startsWith('/images/') || pathname.endsWith('/image'))) {
    return TTL_24_HOURS;
  }
  return getBaseTtlSeconds(pathname, fallbackTtl);
}

function shouldCacheStatus(status: number): boolean {
  // Cache success responses and permanent-not-found resources (common for missing images).
  return (status >= 200 && status < 300) || status === 404;
}

/**
 * Generates a cache key from the incoming request.
 * Uses the full URL path and query string to ensure uniqueness.
 */
function generateCacheKey(url: string): string {
  const urlObj = new URL(url);
  return `pokewallet:${urlObj.pathname}${normalizeSearchParams(urlObj.searchParams)}`;
}

/**
 * Checks if a cached response is still valid (within TTL).
 */
function isCacheValid(cached: CachedResponse): boolean {
  const ageSeconds = (Date.now() - cached.cachedAt) / 1000;
  return ageSeconds < cached.ttl;
}

/**
 * Creates a Response object from cached data.
 */
function createResponseFromCache(cached: CachedResponse, isHit: boolean): Response {
  const responseHeaders = new Headers(cached.headers);
  // Let the runtime compute body length to avoid stale/mismatched values.
  responseHeaders.delete('content-length');
  responseHeaders.set('X-Cache-Status', isHit ? 'HIT' : 'STALE');
  responseHeaders.set('X-Cached-At', new Date(cached.cachedAt).toISOString());
  responseHeaders.set('X-Cache-Age-Seconds', Math.floor((Date.now() - cached.cachedAt) / 1000).toString());
  responseHeaders.set('X-Cache-TTL', cached.ttl.toString());

  const responseBody = cached.bodyEncoding === 'base64'
    ? Uint8Array.from(Buffer.from(cached.body, 'base64'))
    : cached.body;

  return new Response(responseBody, {
    status: cached.status,
    statusText: cached.statusText,
    headers: responseHeaders,
  });
}

/**
 * Main request handler
 */
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    // Only cache GET requests
    if (request.method !== 'GET') {
      return new Response('Method not allowed', { status: 405 });
    }

    const requestUrl = new URL(request.url);
    const cacheKey = generateCacheKey(request.url);
    const fallbackTtlSeconds = parseInt(env.CACHE_TTL_SECONDS || `${TTL_90_DAYS}`, 10);
    const cacheTtlSeconds = getBaseTtlSeconds(requestUrl.pathname, fallbackTtlSeconds);
    const cache = env.CACHE;

    if (!cache || typeof cache.get !== 'function' || typeof cache.put !== 'function') {
      return new Response(
        JSON.stringify({
          error: 'Worker configuration error',
          message: 'CACHE KV binding is missing in this environment',
        }),
        {
          status: 500,
          headers: {
            'Content-Type': 'application/json',
            'X-Cache-Status': 'ERROR',
          },
        }
      );
    }

    try {
      // ===== CACHE HIT CHECK =====
      const cachedData = await cache.get(cacheKey, 'json') as CachedResponse | null;

      if (cachedData && isCacheValid(cachedData)) {
        // Cache hit - return cached response with HIT status
        return createResponseFromCache(cachedData, true);
      }

      // ===== CACHE MISS: FETCH FROM ORIGIN =====
      // Fail fast if API key is missing or malformed in env secrets.
      const rawApiKey = (env.POKEWALLET_API_KEY || '').trim();
      const apiKey = rawApiKey.replace(/^['\"]|['\"]$/g, '');
      if (!apiKey) {
        return new Response(
          JSON.stringify({
            error: 'Worker configuration error',
            message: 'POKEWALLET_API_KEY secret is missing in this environment',
          }),
          {
            status: 500,
            headers: {
              'Content-Type': 'application/json',
              'X-Cache-Status': 'ERROR',
            },
          }
        );
      }

      // Construct upstream URL
      const upstreamUrl = new URL(request.url);
      const origin = env.ORIGIN_API || 'https://api.pokewallet.io';
      const originUrl = new URL(origin);
      upstreamUrl.protocol = originUrl.protocol;
      upstreamUrl.hostname = originUrl.hostname;
      upstreamUrl.port = originUrl.port;

      // Create upstream request with API key
      const upstreamRequest = new Request(upstreamUrl.toString(), {
        method: 'GET',
        headers: {
          'X-API-Key': apiKey,
          'User-Agent': `Pokévault-Proxy/1.0 (+https://pokevault.app)`,
        },
      });

      // Fetch from PokeWallet API
      const originResponse = await fetch(upstreamRequest);

      // Prepare response body and headers to cache.
      const contentType = originResponse.headers.get('content-type') || '';
      const isBinary = contentType.startsWith('image/') || contentType.includes('application/octet-stream');
      const responseArrayBuffer = await originResponse.arrayBuffer();
      const responseBytes = new Uint8Array(responseArrayBuffer);
      const responseBody = isBinary
        ? Buffer.from(responseBytes).toString('base64')
        : new TextDecoder().decode(responseBytes);
      const headersToCache: Record<string, string> = {};

      // Cache relevant headers
      const relevantHeaders = ['content-type', 'content-length', 'cache-control', 'etag'];
      for (const header of relevantHeaders) {
        const value = originResponse.headers.get(header);
        if (value) {
          headersToCache[header] = value;
        }
      }

      // ===== CACHE STORAGE =====
      if (shouldCacheStatus(originResponse.status)) {
        const effectiveTtlSeconds = getTtlSeconds(requestUrl.pathname, originResponse.status, fallbackTtlSeconds);
        // Cache successful responses and 404 misses to reduce repeated token usage.
        const cachedResponse: CachedResponse = {
          status: originResponse.status,
          statusText: originResponse.statusText,
          headers: headersToCache,
          body: responseBody,
          bodyEncoding: isBinary ? 'base64' : 'text',
          cachedAt: Date.now(),
          ttl: effectiveTtlSeconds,
        };

        // Store in KV (fire and forget, don't block response)
        cache.put(cacheKey, JSON.stringify(cachedResponse), {
          expirationTtl: effectiveTtlSeconds,
        }).catch((err) => {
          console.error(`Failed to cache ${cacheKey}:`, err);
        });
      }

      // ===== RETURN RESPONSE WITH CACHE STATUS =====
      const responseHeaders = new Headers(originResponse.headers);
      responseHeaders.delete('content-length');
      responseHeaders.set('X-Cache-Status', 'MISS');
      responseHeaders.set('X-Cached-At', new Date().toISOString());
      responseHeaders.set('X-Cache-TTL', getTtlSeconds(requestUrl.pathname, originResponse.status, fallbackTtlSeconds).toString());

      const bodyForClient = isBinary
        ? responseBytes
        : responseBody;

      return new Response(bodyForClient, {
        status: originResponse.status,
        statusText: originResponse.statusText,
        headers: responseHeaders,
      });
    } catch (error) {
      console.error(`Worker error for ${cacheKey}:`, error);

      // ===== STALE CACHE FALLBACK =====
      // If upstream fetch fails, try to serve stale cache
      if (!cache || typeof cache.get !== 'function') {
        return new Response(
          JSON.stringify({
            error: 'Worker runtime error',
            message: 'CACHE KV binding unavailable while handling error',
            timestamp: new Date().toISOString(),
          }),
          {
            status: 500,
            headers: {
              'Content-Type': 'application/json',
              'X-Cache-Status': 'ERROR',
            },
          }
        );
      }

      const cachedData = await cache.get(cacheKey, 'json') as CachedResponse | null;

      if (cachedData) {
        // Return stale cache with STALE status
        return createResponseFromCache(cachedData, false);
      }

      // No cache available - return error
      return new Response(
        JSON.stringify({
          error: 'Service unavailable',
          message: 'Could not fetch from PokeWallet API and no cache available',
          timestamp: new Date().toISOString(),
        }),
        {
          status: 502,
          headers: {
            'Content-Type': 'application/json',
            'X-Cache-Status': 'ERROR',
          },
        }
      );
    }
  },
};
