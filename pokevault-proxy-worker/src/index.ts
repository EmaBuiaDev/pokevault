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
  IMAGES_BUCKET?: R2Bucket;
  POKEWALLET_API_KEY: string;
  ORIGIN_API: string;
  CACHE_TTL_SECONDS: string;
  BACKFILL_BATCH_SIZE?: string;
  IT_IMAGE_PREFIX?: string;
  IT_CATALOG_KEY?: string;
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

interface PokeWalletSetSummary {
  name?: string;
  set_code?: string;
  set_id?: string;
  card_count?: number;
  total_cards?: number;
  language?: string | null;
}

interface PokeWalletSetsPayload {
  success?: boolean;
  data?: PokeWalletSetSummary[];
  total?: number;
}

interface PokeWalletCardInfoPayload {
  name?: string;
  card_number?: string | null;
}

interface PokeWalletCardPayload {
  id?: string;
  card_info?: PokeWalletCardInfoPayload | null;
}

interface PokeWalletSetDetailPayload {
  success?: boolean;
  set?: PokeWalletSetSummary;
  matches?: PokeWalletSetSummary[];
  cards?: PokeWalletCardPayload[];
  pagination?: {
    page?: number;
    limit?: number;
    total?: number;
    total_pages?: number;
  };
}

interface RealTotalsIndex {
  version: number;
  updatedAt: number;
  totals: Record<string, number>;
}

interface BackfillCursor {
  offset: number;
  updatedAt: number;
}

type ItalianAssetRequest =
  | {
    kind: 'card';
    setCode: string;
    cardNumber: string;
    size: string;
  }
  | {
    kind: 'setLogo';
    setCode: string;
  };

const TTL_24_HOURS = 24 * 60 * 60;
const TTL_5_MINUTES = 5 * 60;
const TTL_90_DAYS = 90 * 24 * 60 * 60;
const REAL_TOTALS_INDEX_KEY = 'pokewallet:real-totals:index:v1';
const REAL_TOTALS_CURSOR_KEY = 'pokewallet:real-totals:cursor:v1';
const REAL_TOTALS_INDEX_TTL = TTL_90_DAYS;
const DEFAULT_BACKFILL_BATCH_SIZE = 6;
const UPSTREAM_SET_PAGE_LIMIT = 200;
const FORCED_REAL_TOTALS_BY_SET_CODE: Record<string, number> = {
  // Perfect Order raw metadata includes non-card products (207). Real cards count is 124.
  ME03: 124,
};
const FORCED_REAL_TOTALS_BY_SET_ID: Record<string, number> = {
  '24587': 124,
};

const PRODUCT_PATTERNS = [
  /\bmini tin\b/i,
  /\bbooster box\b/i,
  /\bbooster bundle\b/i,
  /\bbooster pack\b/i,
  /\bcollection box\b/i,
  /\belite trainer box\b/i,
  /\betb\b/i,
  /\bblister\b/i,
  /\bdisplay\b/i,
  /\btheme deck\b/i,
  /\bstarter set\b/i,
  /\bbuild\s*&\s*battle\b/i,
  /\bbuild and battle\b/i,
];

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
  if (pathname === '/ita/catalog.json') {
    return TTL_5_MINUTES;
  }

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
    if (pathname.startsWith('/images/it/')) {
      return TTL_5_MINUTES;
    }
    return TTL_24_HOURS;
  }
  return getBaseTtlSeconds(pathname, fallbackTtl);
}

function shouldCacheStatus(status: number): boolean {
  // Cache success responses and permanent-not-found resources (common for missing images).
  return (status >= 200 && status < 300) || status === 404;
}

function normalizeLanguageMacro(raw: string | null | undefined): string | null {
  const normalized = raw?.trim().toLowerCase().replace(/_/g, ' ') ?? '';
  if (!normalized) return null;
  if (['it', 'ita', 'italian', 'italiano'].includes(normalized) || normalized.includes('ital')) return 'IT';
  if (['en', 'eng', 'english', 'inglese'].includes(normalized) || normalized.includes('engl') || normalized.includes('ingl')) return 'ENG';
  if (['jp', 'jap', 'ja', 'japanese', 'giapponese'].includes(normalized) || normalized.includes('jap') || normalized.includes('giapp')) return 'JAP';
  if (
    ['zh', 'zhs', 'zht', 'cn', 'chn', 'chi', 'chinese'].includes(normalized) ||
    normalized.includes('chinese') ||
    normalized.includes('mandarin') ||
    normalized.includes('simplified chinese') ||
    normalized.includes('traditional chinese') ||
    normalized.includes('cinese')
  ) {
    return 'CHN';
  }
  return null;
}

function shouldBackfillSet(setSummary: PokeWalletSetSummary): boolean {
  const macro = normalizeLanguageMacro(setSummary.language);
  return !!setSummary.set_code && !!setSummary.set_id && ['ENG', 'JAP', 'CHN', 'IT'].includes(macro ?? '');
}

function resolveRealTotal(index: RealTotalsIndex, setSummary: PokeWalletSetSummary | undefined): number | null {
  if (!setSummary) {
    return null;
  }

  const setId = setSummary.set_id ?? '';
  const setCode = (setSummary.set_code ?? '').trim().toUpperCase();

  const fromIndex = setId ? index.totals[setId] : undefined;
  if (typeof fromIndex === 'number' && fromIndex > 0) {
    return fromIndex;
  }

  const forcedByCode = setCode ? FORCED_REAL_TOTALS_BY_SET_CODE[setCode] : undefined;
  if (typeof forcedByCode === 'number' && forcedByCode > 0) {
    return forcedByCode;
  }

  const forcedById = setId ? FORCED_REAL_TOTALS_BY_SET_ID[setId] : undefined;
  if (typeof forcedById === 'number' && forcedById > 0) {
    return forcedById;
  }

  return null;
}

function isActualCard(card: PokeWalletCardPayload): boolean {
  const info = card.card_info;
  if (!info?.card_number?.trim()) {
    return false;
  }
  const name = info.name?.toLowerCase() ?? '';
  return !PRODUCT_PATTERNS.some((pattern) => pattern.test(name));
}

function normalizeR2Prefix(rawPrefix: string | undefined): string {
  const prefix = (rawPrefix || 'it').trim().replace(/^\/+|\/+$/g, '');
  return prefix || 'it';
}

function parseItalianCatalogRequest(urlObj: URL): boolean {
  return urlObj.pathname === '/ita/catalog.json';
}

function buildItalianCatalogKeyCandidates(prefix: string, envKey: string | undefined): string[] {
  const configured = (envKey || '').trim().replace(/^\/+/, '');
  const defaultCandidates = [
    `${prefix}/catalog/catalog.json`,
    `${prefix}/catalog/cards.catalog.json`,
    `${prefix}/catalog/cards.cleaned.json`,
    `${prefix}/cards.cleaned.json`,
  ];

  if (!configured) {
    return defaultCandidates;
  }

  return [configured, ...defaultCandidates.filter((candidate) => candidate !== configured)];
}

function parseItalianAssetRequest(urlObj: URL): ItalianAssetRequest | null {
  const cardMatch = urlObj.pathname.match(/^\/images\/it\/([^/]+)\/([^/]+)$/i);
  if (cardMatch) {
    const setCode = decodeURIComponent(cardMatch[1]).trim().toUpperCase();
    const cardNumber = decodeURIComponent(cardMatch[2]).trim();
    if (!setCode || !cardNumber) {
      return null;
    }
    return {
      kind: 'card',
      setCode,
      cardNumber,
      size: (urlObj.searchParams.get('size') || '').trim().toLowerCase(),
    };
  }

  return null;
}

function normalizeCardNumber(raw: string): string {
  const digitsOnly = raw.replace(/[^0-9]/g, '');
  if (digitsOnly) {
    return digitsOnly.replace(/^0+/, '') || '0';
  }
  return raw.trim().replace(/^0+/, '') || '0';
}

function buildItalianCardKeyCandidates(prefix: string, setCode: string, cardNumber: string, size: string): string[] {
  const rawCardNumber = cardNumber.trim();
  const normalized = normalizeCardNumber(rawCardNumber);
  const padded = normalized.padStart(3, '0');
  const upperSetCode = setCode.toUpperCase();
  const lowerSetCode = setCode.toLowerCase();
  const setCodeTokens = [upperSetCode, lowerSetCode];
  const cardNumberTokens = new Set<string>([
    rawCardNumber,
    rawCardNumber.toUpperCase(),
    rawCardNumber.toLowerCase(),
    normalized,
    padded,
  ].filter(Boolean));
  const basePaths = [
    `${prefix}/${upperSetCode}`,
    `${prefix}/${lowerSetCode}`,
  ];
  const candidates = new Set<string>();
  const imageExtensions = ['png', 'webp', 'jpg', 'jpeg'];

  // Primary layout for all expansions: {setCode}/{number}.png
  for (const basePath of basePaths) {
    for (const cardToken of cardNumberTokens) {
      for (const ext of imageExtensions) {
        candidates.add(`${basePath}/${cardToken}.${ext}`);
      }
    }
  }

  if (size === 'low' || size === 'high') {
    for (const basePath of basePaths) {
      for (const setToken of setCodeTokens) {
        for (const cardToken of cardNumberTokens) {
          for (const ext of imageExtensions) {
            candidates.add(`${basePath}/${setToken}_IT_${cardToken}_${size}.${ext}`);
            candidates.add(`${basePath}/${setToken}_IT_${cardToken}-${size}.${ext}`);
          }
        }
      }
    }
  }

  // Legacy layouts kept as fallback for already-uploaded historical assets.
  for (const basePath of basePaths) {
    for (const setToken of setCodeTokens) {
      for (const cardToken of cardNumberTokens) {
        for (const ext of imageExtensions) {
          candidates.add(`${basePath}/${setToken}_IT_${cardToken}.${ext}`);
        }
      }
    }
  }

  return Array.from(candidates);
}

function buildItalianSetLogoCandidates(prefix: string, setCode: string): string[] {
  const basePath = `${prefix}/${setCode}`;
  return [
    `${basePath}/set-logo.png`,
    `${basePath}/set_logo.png`,
    `${basePath}/logo.png`,
    `${basePath}/cover.png`,
    `${basePath}/${setCode}_IT_logo.png`,
  ];
}

function guessContentTypeFromKey(key: string): string {
  if (key.endsWith('.png')) return 'image/png';
  if (key.endsWith('.webp')) return 'image/webp';
  if (key.endsWith('.jpg') || key.endsWith('.jpeg')) return 'image/jpeg';
  return 'application/octet-stream';
}

async function getFirstExistingR2Object(
  bucket: R2Bucket,
  keys: string[]
): Promise<{ key: string; object: R2ObjectBody } | null> {
  for (const key of keys) {
    const object = await bucket.get(key);
    if (object) {
      return { key, object };
    }
  }
  return null;
}

async function handleItalianR2AssetRequest(
  requestUrl: URL,
  requestInfo: ItalianAssetRequest,
  env: Env,
  cache: KVNamespace,
  fallbackTtlSeconds: number,
  ctx: ExecutionContext
): Promise<Response | null> {
  if (!env.IMAGES_BUCKET) {
    return null;
  }

  const cacheKey = generateCacheKey(requestUrl.toString());
  const cachedData = await cache.get(cacheKey, 'json') as CachedResponse | null;
  if (cachedData && isCacheValid(cachedData)) {
    return createResponseFromCache(cachedData, true, requestUrl.pathname, cache);
  }

  const prefix = normalizeR2Prefix(env.IT_IMAGE_PREFIX);
  const keys = requestInfo.kind === 'card'
    ? buildItalianCardKeyCandidates(prefix, requestInfo.setCode, requestInfo.cardNumber, requestInfo.size)
    : buildItalianSetLogoCandidates(prefix, requestInfo.setCode);

  const hit = await getFirstExistingR2Object(env.IMAGES_BUCKET, keys);

  // For set logos, if nothing is in R2 fall through to the PokeWallet proxy so
  // the upstream API can serve the image (e.g. sets that don't have a local ITA logo).
  if (!hit && requestInfo.kind === 'setLogo') {
    return null;
  }

  let status = 404;
  let statusText = 'Not Found';
  let headersToCache: Record<string, string> = {
    'content-type': 'text/plain; charset=utf-8',
  };
  let responseBodyText = 'Asset not found';
  let responseBodyBinary: Uint8Array | null = null;
  let bodyEncoding: 'text' | 'base64' = 'text';

  if (hit) {
    const bytes = new Uint8Array(await hit.object.arrayBuffer());
    const keyContentType = hit.object.httpMetadata?.contentType || guessContentTypeFromKey(hit.key);

    status = 200;
    statusText = 'OK';
    headersToCache = {
      'content-type': keyContentType,
      'cache-control': `public, max-age=${TTL_90_DAYS}`,
    };
    if (hit.object.httpEtag) {
      headersToCache.etag = hit.object.httpEtag;
    }
    responseBodyBinary = bytes;
    bodyEncoding = 'base64';
  }

  if (shouldCacheStatus(status)) {
    const effectiveTtlSeconds = getTtlSeconds(requestUrl.pathname, status, fallbackTtlSeconds);
    const cachedResponse: CachedResponse = {
      status,
      statusText,
      headers: headersToCache,
      body: bodyEncoding === 'base64'
        ? Buffer.from(responseBodyBinary || new Uint8Array()).toString('base64')
        : responseBodyText,
      bodyEncoding,
      cachedAt: Date.now(),
      ttl: effectiveTtlSeconds,
    };

    ctx.waitUntil(
      cache.put(cacheKey, JSON.stringify(cachedResponse), {
        expirationTtl: effectiveTtlSeconds,
      }).catch((err) => {
        console.error(`Failed to cache ${cacheKey}:`, err);
      })
    );
  }

  const responseHeaders = new Headers(headersToCache);
  responseHeaders.set('X-Cache-Status', 'MISS');
  responseHeaders.set('X-Cached-At', new Date().toISOString());
  responseHeaders.set('X-Cache-TTL', getTtlSeconds(requestUrl.pathname, status, fallbackTtlSeconds).toString());
  responseHeaders.delete('content-length');

  return new Response(responseBodyBinary ?? responseBodyText, {
    status,
    statusText,
    headers: responseHeaders,
  });
}

async function handleItalianCatalogRequest(
  requestUrl: URL,
  env: Env,
  cache: KVNamespace,
  fallbackTtlSeconds: number,
  ctx: ExecutionContext
): Promise<Response | null> {
  if (!env.IMAGES_BUCKET) {
    return null;
  }

  const cacheKey = generateCacheKey(requestUrl.toString());
  const cachedData = await cache.get(cacheKey, 'json') as CachedResponse | null;
  if (cachedData && isCacheValid(cachedData)) {
    return createResponseFromCache(cachedData, true, requestUrl.pathname, cache);
  }

  const prefix = normalizeR2Prefix(env.IT_IMAGE_PREFIX);
  const keyCandidates = buildItalianCatalogKeyCandidates(prefix, env.IT_CATALOG_KEY);
  const hit = await getFirstExistingR2Object(env.IMAGES_BUCKET, keyCandidates);

  let status = 404;
  let statusText = 'Not Found';
  let headersToCache: Record<string, string> = {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': `public, max-age=${TTL_5_MINUTES}`,
  };
  let responseBodyText = '{"error":"Italian catalog not found"}';

  if (hit) {
    const text = await hit.object.text();
    status = 200;
    statusText = 'OK';
    headersToCache = {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': `public, max-age=${TTL_5_MINUTES}`,
    };
    if (hit.object.httpEtag) {
      headersToCache.etag = hit.object.httpEtag;
    }
    responseBodyText = text;
  }

  if (shouldCacheStatus(status)) {
    const effectiveTtlSeconds = getTtlSeconds(requestUrl.pathname, status, fallbackTtlSeconds);
    const cachedResponse: CachedResponse = {
      status,
      statusText,
      headers: headersToCache,
      body: responseBodyText,
      bodyEncoding: 'text',
      cachedAt: Date.now(),
      ttl: effectiveTtlSeconds,
    };

    ctx.waitUntil(
      cache.put(cacheKey, JSON.stringify(cachedResponse), {
        expirationTtl: effectiveTtlSeconds,
      }).catch((err) => {
        console.error(`Failed to cache ${cacheKey}:`, err);
      })
    );
  }

  const responseHeaders = new Headers(headersToCache);
  responseHeaders.set('X-Cache-Status', 'MISS');
  responseHeaders.set('X-Cached-At', new Date().toISOString());
  responseHeaders.set('X-Cache-TTL', getTtlSeconds(requestUrl.pathname, status, fallbackTtlSeconds).toString());

  return new Response(responseBodyText, {
    status,
    statusText,
    headers: responseHeaders,
  });
}

function buildUpstreamUrl(env: Env, path: string, query?: URLSearchParams): string {
  const origin = env.ORIGIN_API || 'https://api.pokewallet.io';
  const base = new URL(origin);
  const url = new URL(path, `${base.protocol}//${base.host}`);
  if (query) {
    url.search = query.toString();
  }
  return url.toString();
}

function createUpstreamHeaders(env: Env): HeadersInit {
  const rawApiKey = (env.POKEWALLET_API_KEY || '').trim();
  const apiKey = rawApiKey.replace(/^['\"]|['\"]$/g, '');
  if (!apiKey) {
    throw new Error('POKEWALLET_API_KEY secret is missing in this environment');
  }
  return {
    'X-API-Key': apiKey,
    'User-Agent': 'Pokevault-Proxy/1.1 (+https://pokevault.app)',
  };
}

async function fetchUpstreamJson<T>(env: Env, path: string, query?: URLSearchParams): Promise<T> {
  const response = await fetch(buildUpstreamUrl(env, path, query), {
    method: 'GET',
    headers: createUpstreamHeaders(env),
  });

  if (!response.ok) {
    throw new Error(`Upstream request failed for ${path}: ${response.status} ${response.statusText}`);
  }

  return response.json<T>();
}

async function getRealTotalsIndex(cache: KVNamespace): Promise<RealTotalsIndex> {
  const cached = await cache.get(REAL_TOTALS_INDEX_KEY, 'json') as RealTotalsIndex | null;
  return cached ?? {
    version: 1,
    updatedAt: 0,
    totals: {},
  };
}

async function saveRealTotalsIndex(cache: KVNamespace, index: RealTotalsIndex): Promise<void> {
  await cache.put(REAL_TOTALS_INDEX_KEY, JSON.stringify(index), {
    expirationTtl: REAL_TOTALS_INDEX_TTL,
  });
}

async function getBackfillCursor(cache: KVNamespace): Promise<BackfillCursor> {
  const cached = await cache.get(REAL_TOTALS_CURSOR_KEY, 'json') as BackfillCursor | null;
  return cached ?? {
    offset: 0,
    updatedAt: 0,
  };
}

async function saveBackfillCursor(cache: KVNamespace, cursor: BackfillCursor): Promise<void> {
  await cache.put(REAL_TOTALS_CURSOR_KEY, JSON.stringify(cursor), {
    expirationTtl: REAL_TOTALS_INDEX_TTL,
  });
}

function enrichSetsPayload(rawBody: string, index: RealTotalsIndex): string {
  const payload = JSON.parse(rawBody) as PokeWalletSetsPayload;
  if (!Array.isArray(payload.data)) {
    return rawBody;
  }

  payload.data = payload.data.map((setSummary) => {
    const realTotal = resolveRealTotal(index, setSummary);
    if (realTotal === null) {
      return setSummary;
    }
    return {
      ...setSummary,
      card_count: realTotal,
      total_cards: realTotal,
    };
  });

  return JSON.stringify(payload);
}

function enrichSetDetailPayload(rawBody: string, index: RealTotalsIndex): string {
  const payload = JSON.parse(rawBody) as PokeWalletSetDetailPayload;
  const targetSet = payload.set ?? payload.matches?.[0];
  const realTotal = resolveRealTotal(index, targetSet);
  if (realTotal === null) {
    return rawBody;
  }

  const setId = targetSet?.set_id ?? '';

  if (payload.set) {
    payload.set = {
      ...payload.set,
      total_cards: realTotal,
      card_count: realTotal,
    };
  }

  if (Array.isArray(payload.matches) && payload.matches.length > 0) {
    payload.matches = payload.matches.map((match) =>
      match.set_id === setId
        ? { ...match, total_cards: realTotal, card_count: realTotal }
        : match
    );
  }

  if (payload.pagination) {
    payload.pagination = {
      ...payload.pagination,
      total: realTotal,
      total_pages: Math.max(1, Math.ceil(realTotal / (payload.pagination.limit || 1))),
    };
  }

  return JSON.stringify(payload);
}

async function maybeEnrichJsonBody(pathname: string, contentType: string, rawBody: string, cache: KVNamespace): Promise<string> {
  if (!contentType.includes('application/json')) {
    return rawBody;
  }

  const index = await getRealTotalsIndex(cache);

  if (pathname === '/sets') {
    return enrichSetsPayload(rawBody, index);
  }

  if (pathname.startsWith('/sets/') && !pathname.endsWith('/image')) {
    return enrichSetDetailPayload(rawBody, index);
  }

  return rawBody;
}

async function computeRealSetTotal(env: Env, setCode: string): Promise<number> {
  const uniqueCardIds = new Set<string>();
  let page = 1;

  while (true) {
    const query = new URLSearchParams({
      page: page.toString(),
      limit: UPSTREAM_SET_PAGE_LIMIT.toString(),
    });
    const payload = await fetchUpstreamJson<PokeWalletSetDetailPayload>(env, `/sets/${encodeURIComponent(setCode)}`, query);
    const cards = payload.cards ?? [];

    for (const card of cards) {
      const cardId = card.id ?? '';
      if (cardId && isActualCard(card)) {
        uniqueCardIds.add(cardId);
      }
    }

    const totalPages = payload.pagination?.total_pages ?? 0;
    if (totalPages > 0) {
      if (page >= totalPages) {
        break;
      }
    } else if (cards.length < UPSTREAM_SET_PAGE_LIMIT) {
      break;
    }

    page += 1;
  }

  return uniqueCardIds.size;
}

async function backfillRealSetTotals(env: Env, cache: KVNamespace): Promise<void> {
  const payload = await fetchUpstreamJson<PokeWalletSetsPayload>(env, '/sets');
  const eligibleSets = (payload.data ?? []).filter(shouldBackfillSet);
  if (eligibleSets.length === 0) {
    return;
  }

  const requestedBatchSize = parseInt(env.BACKFILL_BATCH_SIZE || `${DEFAULT_BACKFILL_BATCH_SIZE}`, 10);
  const batchSize = Number.isFinite(requestedBatchSize) && requestedBatchSize > 0
    ? requestedBatchSize
    : DEFAULT_BACKFILL_BATCH_SIZE;

  const cursor = await getBackfillCursor(cache);
  const startOffset = cursor.offset % eligibleSets.length;
  const batch: PokeWalletSetSummary[] = [];
  for (let index = 0; index < Math.min(batchSize, eligibleSets.length); index += 1) {
    batch.push(eligibleSets[(startOffset + index) % eligibleSets.length]);
  }

  const realTotalsIndex = await getRealTotalsIndex(cache);
  for (const setSummary of batch) {
    const setCode = setSummary.set_code;
    const setId = setSummary.set_id;
    if (!setCode || !setId) {
      continue;
    }

    try {
      const count = await computeRealSetTotal(env, setCode);
      if (count > 0) {
        realTotalsIndex.totals[setId] = count;
      }
    } catch (error) {
      console.error(`Failed to backfill real total for ${setCode}:`, error);
    }
  }

  realTotalsIndex.updatedAt = Date.now();
  await saveRealTotalsIndex(cache, realTotalsIndex);
  await saveBackfillCursor(cache, {
    offset: (startOffset + batch.length) % eligibleSets.length,
    updatedAt: Date.now(),
  });
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
async function createResponseFromCache(cached: CachedResponse, isHit: boolean, pathname: string, cache: KVNamespace): Promise<Response> {
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

  const bodyForClient = typeof responseBody === 'string'
    ? await maybeEnrichJsonBody(pathname, responseHeaders.get('content-type') || '', responseBody, cache)
    : responseBody;

  return new Response(bodyForClient, {
    status: cached.status,
    statusText: cached.statusText,
    headers: responseHeaders,
  });
}

/**
 * Main request handler
 */
export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
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

    if (parseItalianCatalogRequest(requestUrl)) {
      const catalogResponse = await handleItalianCatalogRequest(
        requestUrl,
        env,
        cache,
        cacheTtlSeconds,
        ctx
      );
      if (catalogResponse) {
        return catalogResponse;
      }
    }

    const italianAssetRequest = parseItalianAssetRequest(requestUrl);
    if (italianAssetRequest) {
      const italianAssetResponse = await handleItalianR2AssetRequest(
        requestUrl,
        italianAssetRequest,
        env,
        cache,
        fallbackTtlSeconds,
        ctx
      );
      if (italianAssetResponse) {
        return italianAssetResponse;
      }
    }

    try {
      // ===== CACHE HIT CHECK =====
      const cachedData = await cache.get(cacheKey, 'json') as CachedResponse | null;

      if (cachedData && isCacheValid(cachedData)) {
        // Cache hit - return cached response with HIT status
        return createResponseFromCache(cachedData, true, requestUrl.pathname, cache);
      }

      // ===== CACHE MISS: FETCH FROM ORIGIN =====
      // Fail fast if API key is missing or malformed in env secrets.
      try {
        createUpstreamHeaders(env);
      } catch (error) {
        return new Response(
          JSON.stringify({
            error: 'Worker configuration error',
            message: error instanceof Error ? error.message : 'POKEWALLET_API_KEY secret is missing in this environment',
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
        headers: createUpstreamHeaders(env),
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

        // Register the KV write with the execution context so Cloudflare keeps
        // the Worker alive long enough to persist the cache entry reliably.
        ctx.waitUntil(
          cache.put(cacheKey, JSON.stringify(cachedResponse), {
            expirationTtl: effectiveTtlSeconds,
          }).catch((err) => {
            console.error(`Failed to cache ${cacheKey}:`, err);
          })
        );
      }

      // ===== RETURN RESPONSE WITH CACHE STATUS =====
      const responseHeaders = new Headers(originResponse.headers);
      responseHeaders.delete('content-length');
      responseHeaders.set('X-Cache-Status', 'MISS');
      responseHeaders.set('X-Cached-At', new Date().toISOString());
      responseHeaders.set('X-Cache-TTL', getTtlSeconds(requestUrl.pathname, originResponse.status, fallbackTtlSeconds).toString());

      const bodyForClient = isBinary
        ? responseBytes
        : await maybeEnrichJsonBody(requestUrl.pathname, contentType, responseBody, cache);

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
        return createResponseFromCache(cachedData, false, requestUrl.pathname, cache);
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

  async scheduled(_controller: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    if (!env.CACHE) {
      return;
    }

    ctx.waitUntil(backfillRealSetTotals(env, env.CACHE));
  },
};
