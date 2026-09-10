/**
 * Pokévault PokeWallet API Proxy Worker
 * 
 * Mirrors PokeWallet API (api.pokewallet.io) with Cloudflare KV caching.
 * - Uses route-specific TTLs for long-lived catalog content vs. volatile searches/prices
 * - Injects POKEWALLET_API_KEY on upstream requests
 * - Returns cache status headers for debugging
 */

import { handleBillingRequest } from './billing';

interface Env {
  CACHE: KVNamespace;
  IMAGES_BUCKET?: R2Bucket;
  POKEWALLET_API_KEY: string;
  ORIGIN_API: string;
  CACHE_TTL_SECONDS: string;
  BACKFILL_BATCH_SIZE?: string;
  IT_IMAGE_PREFIX?: string;
  IT_CATALOG_KEY?: string;
  // Queryable catalog store, additive alongside the legacy R2 JSON blob
  // (it/catalog/cards.cleaned.json) served by /ita/catalog.json. New /v1/*
  // routes read from here; nothing existing was changed to use it yet.
  pokevault_catalog?: D1Database;

  // Verifica lato server degli abbonamenti (vedi src/billing.ts e BILLING.md).
  // Sono secret: si impostano con `wrangler secret put`, non in wrangler.toml.
  PLAY_SERVICE_ACCOUNT_JSON?: string;
  PLAY_PACKAGE_NAME?: string;
  FIREBASE_PROJECT_ID?: string;
  RTDN_SHARED_SECRET?: string;
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

interface ItalianPriceEntry {
  avg?: number;
  low?: number;
  trend?: number;
  avg1?: number;
  avg7?: number;
  avg30?: number;
  /** TCGPlayer USD fallback for sets without CardMarket data upstream. */
  usd?: number;
  usdLow?: number;
  url?: string;
}

interface ItalianPriceExpansionEntry {
  baseSetCode: string;
  updatedAt: number;
  prices: Record<string, ItalianPriceEntry>;
}

interface ItalianPriceSnapshot {
  version: number;
  builtAt: number;
  expansions: Record<string, ItalianPriceExpansionEntry>;
  aliases: Record<string, string>;
  /** Number of catalog expansions eligible for pricing (coverage target). */
  totalExpansions?: number;
}

interface ItalianCatalogRecordPayload {
  cardId?: string;
  espansioneId?: string;
}

interface UpstreamCardMarketPricePayload {
  avg?: number | null;
  low?: number | null;
  trend?: number | null;
  avg1?: number | null;
  avg7?: number | null;
  avg30?: number | null;
  variant_type?: string | null;
}

interface UpstreamTcgPlayerPricePayload {
  market_price?: number | null;
  low_price?: number | null;
  mid_price?: number | null;
  sub_type_name?: string | null;
}

interface UpstreamSearchResultCard {
  card_info?: {
    card_number?: string | null;
    set_id?: string | null;
    set_code?: string | null;
    set_name?: string | null;
  } | null;
  cardmarket?: {
    product_url?: string | null;
    prices?: UpstreamCardMarketPricePayload[] | null;
  } | null;
  tcgplayer?: {
    url?: string | null;
    prices?: UpstreamTcgPlayerPricePayload[] | null;
  } | null;
}

interface UpstreamSearchPayload {
  results?: UpstreamSearchResultCard[];
  pagination?: {
    total_pages?: number;
  };
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

// ── Italian price snapshot (pre-merged ITA→ENG price index) ──
// Upstream note: /sets/{code} payloads no longer carry CardMarket prices;
// /search is the only price-bearing endpoint, so the snapshot is built by
// paging /search with the English set-name prefix (e.g. "ME01").
const ITA_PRICE_SNAPSHOT_KEY = 'it:prices:snapshot:v2';
const ITA_PRICE_SNAPSHOT_TTL = 7 * 24 * 60 * 60; // survives missed rebuilds
const ITA_PRICE_EXPANSION_STALE_MS = 24 * 60 * 60 * 1000; // refresh cadence per expansion
const ITA_PRICE_INLINE_REBUILD_MIN_AGE_MS = 30 * 60 * 1000; // ?rebuild=1 throttle
const ITA_PRICE_MAX_UPSTREAM_FETCHES = 30; // per run; KV-cached pages are free
const ITA_PRICE_SEARCH_PAGE_LIMIT = 100;
const ITA_PRICE_MAX_PAGES_PER_SET = 4;
// Catalog expansion id -> upstream numeric set_id(s). set_id is unambiguous
// (set_code collides: PR, RR, BKP, CL...). Multiple ids merge sub-sets that the
// catalog treats as one expansion (e.g. Generations + Radiant Collection).
const ITA_EXPANSION_UPSTREAM_SET_IDS: Record<string, string[]> = {
  bw1: ['1400'], // Black and White
  bw2: ['1424'], // Emerging Powers
  bw3: ['1385'], // Noble Victories
  bw4: ['1412'], // Next Destinies
  bw5: ['1386'], // Dark Explorers
  bw6: ['1394'], // Dragons Exalted
  bw7: ['1408'], // Boundaries Crossed
  bw8: ['1413'], // Plasma Storm
  bw9: ['1382'], // Plasma Freeze
  bwp: ['1407'], // Black and White Promos
  cel25: ['2867'], // Celebrations
  cel25c: ['2931'], // Celebrations: Classic Collection
  col1: ['1415'], // Call of Legends
  det: ['2409'], // Detective Pikachu
  dp1: ['1430'], // Diamond and Pearl
  dp2: ['1368'], // Mysterious Treasures
  dp3: ['1380'], // Secret Wonders
  dp5: ['1390'], // Majestic Dawn
  dp6: ['1417'], // Legends Awakened
  dp7: ['1369'], // Stormfront
  g1: ['1728', '1729'], // Generations + Radiant Collection
  hgss1: ['1402'], // HeartGold SoulSilver
  hgss2: ['1399'], // Unleashed
  hgss3: ['1403'], // Undaunted
  hgss4: ['1381'], // Triumphant
  me01: ['24380'], // ME01: Mega Evolution
  me02: ['24448'], // ME02: Phantasmal Flames
  me03: ['24587'], // ME03: Perfect Order
  me2pt5: ['24541'], // ME: Ascended Heroes
  mep: ['24451'], // ME: Mega Evolution Promo
  pgo: ['3064'], // Pokemon GO
  pl1: ['1406'], // Platinum
  pl2: ['1367'], // Rising Rivals
  pl4: ['1391'], // Arceus
  rsv10pt5: ['24326'], // SV: White Flare
  sm1: ['1863'], // SM Base Set
  sm2: ['1919'], // Guardians Rising
  sm3: ['1957'], // Burning Shadows
  sm35: ['2054'], // Shining Legends
  sm4: ['2071'], // Crimson Invasion
  sm5: ['2178'], // Ultra Prism
  sm6: ['2209'], // Forbidden Light
  sm7: ['2278'], // Celestial Storm
  sm75: ['2295'], // Dragon Majesty
  sm8: ['2328'], // Lost Thunder
  sm9: ['2377'], // Team Up
  sm10: ['2420'], // Unbroken Bonds
  sm11: ['2464'], // Unified Minds
  sm115: ['2480'], // Hidden Fates
  sm12: ['2534'], // Cosmic Eclipse
  sma: ['2594'], // Hidden Fates: Shiny Vault
  smp: ['1861'], // SM Promos
  sv01: ['22873'], // SV01: Scarlet & Violet Base Set
  sv02: ['23120'], // SV02: Paldea Evolved
  sv03: ['23228'], // SV03: Obsidian Flames
  sv04: ['23286'], // SV04: Paradox Rift
  sv05: ['23381'], // SV05: Temporal Forces
  sv06: ['23473'], // SV06: Twilight Masquerade
  sv07: ['23537'], // SV07: Stellar Crown
  sv08: ['23651'], // SV08: Surging Sparks
  sv09: ['24073'], // SV09: Journey Together
  sv10: ['24269'], // SV10: Destined Rivals
  sv3pt5: ['23237'], // SV: Scarlet & Violet 151
  sv4pt5: ['23353'], // SV: Paldean Fates
  sv6pt5: ['23529'], // SV: Shrouded Fable
  sv8pt5: ['23821'], // SV: Prismatic Evolutions
  svp: ['22872'], // SV: Scarlet & Violet Promo Cards
  swsh1: ['2585'],
  swsh2: ['2626'],
  swsh3: ['2675'],
  swsh35: ['2685'], // Champion's Path
  swsh4: ['2701'],
  swsh45: ['2754'], // Shining Fates
  swsh45sv: ['2781'], // Shining Fates: Shiny Vault
  swsh5: ['2765'],
  swsh6: ['2807'],
  swsh7: ['2848'],
  swsh8: ['2906'],
  swsh9: ['2948'],
  swsh9tg: ['3020'], // Brilliant Stars Trainer Gallery
  swsh10: ['3040'],
  swsh10tg: ['3068'], // Astral Radiance Trainer Gallery
  swsh11: ['3118'],
  swsh11tg: ['3172'], // Lost Origin Trainer Gallery
  swsh12: ['3170'],
  swsh12tg: ['17674'], // Silver Tempest Trainer Gallery
  swsh12pt5: ['17688'], // Crown Zenith
  swsh12pt5gg: ['17689'], // Crown Zenith: Galarian Gallery
  swshp: ['2545'], // SWSH Promo Cards
  xy0: ['1522'], // Kalos Starter Set
  xy1: ['1387'], // XY Base Set
  xy2: ['1464'], // Flashfire
  xy3: ['1481'], // Furious Fists
  xy4: ['1494'], // Phantom Forces
  xy5: ['1509'], // Primal Clash
  xy6: ['1534'], // Roaring Skies
  xy7: ['1576'], // Ancient Origins
  xy8: ['1661'], // BREAKthrough
  xy9: ['1701'], // XY - BREAKpoint
  xy10: ['1780'], // Fates Collide
  xy11: ['1815'], // Steam Siege
  xy12: ['1842'], // Evolutions
  xyp: ['1451'], // XY Promos
};
// Search query overrides for sets whose derived name queries return nothing upstream.
const ITA_EXPANSION_QUERY_OVERRIDE: Record<string, string[]> = {
  sv3pt5: ['SV 151', '151'],
  svp: ['SV Promo'],
  swsh35: ['Champion'], // apostrophe in "Champion's Path" breaks upstream search
  swshp: ['SWSH Promo', 'Sword Shield Promo'],
};
// Mirrors preferredBaseSetCodeForItalianExpansion in the Android app.
const ITA_EXPANSION_BASE_SET: Record<string, string> = {
  me01: 'MEG',
  me02: 'PFL',
  me03: 'ME03',
  me04: 'CRI',
  me2pt5: 'ASC',
  mep: 'MEP',
  sv01: 'SVI',
  sv02: 'PAL',
  sv03: 'OBF',
  sv04: 'PAR',
  sv05: 'TEF',
  sv06: 'TWM',
  sv07: 'SCR',
  sv08: 'SSP',
  sv09: 'JTG',
  sv10: 'DRI',
  zsv10pt5: 'BLK',
  rsv10pt5: 'WHT',
  sv3pt5: 'MEW',
  sv4pt5: 'PAF',
  sv6pt5: 'SFA',
  sv8pt5: 'PRE',
};
const ITALIAN_CARD_ID_PREFIX_REGEX = /^([A-Za-z0-9]+)_IT_/i;

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
    // Price-bearing set payloads should refresh daily, while static logos can stay long-lived.
    return pathname.endsWith('/image') ? TTL_90_DAYS : TTL_24_HOURS;
  }

  if (pathname.startsWith('/cards/')) {
    return TTL_90_DAYS;
  }

  return fallbackTtl;
}

function getTtlSeconds(pathname: string, status: number, fallbackTtl: number): number {
  if (status === 404 && (pathname.startsWith('/images/') || pathname.endsWith('/image'))) {
    if (pathname.startsWith('/images/it/')) {
      return TTL_24_HOURS;
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
  const setLogoMatch = urlObj.pathname.match(/^\/sets\/([^/]+)\/image$/i);
  if (setLogoMatch) {
    const setCode = decodeURIComponent(setLogoMatch[1]).trim().toUpperCase();
    if (!setCode) {
      return null;
    }
    return {
      kind: 'setLogo',
      setCode,
    };
  }

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
  // Alpha-prefixed numbers (e.g. "SV001"/"TG01" for the Shiny Vault / Trainer
  // Gallery secret-rare sub-collections) must NEVER fall back to a
  // digits-only candidate: normalizeCardNumber() strips letters entirely
  // ("SV001" -> "1"), which silently collides with an unrelated card that
  // happens to share the same base-set folder and plain numeric value --
  // verified in production 2026-09-07 (SWSH45 "SV001" served "Yanma", the
  // base set's card #1, instead of the real "Rowlet" secret rare). A
  // missing image (404 / placeholder) is the correct failure mode for a
  // sub-collection whose asset isn't uploaded under any known naming
  // pattern -- silently swapping in a different card's artwork is not.
  const isPureNumeric = /^\d+$/.test(rawCardNumber);
  const normalized = isPureNumeric ? normalizeCardNumber(rawCardNumber) : rawCardNumber;
  const padded = isPureNumeric ? normalized.padStart(3, '0') : rawCardNumber;
  const upperSetCode = setCode.toUpperCase();
  const lowerSetCode = setCode.toLowerCase();
  const setCodeTokens = [upperSetCode, lowerSetCode];
  const cardNumberTokens = [
    rawCardNumber,
    rawCardNumber.toUpperCase(),
    rawCardNumber.toLowerCase(),
    ...(isPureNumeric ? [normalized, padded] : []),
  ].filter(Boolean).filter((value, index, list) => list.indexOf(value) === index);
  const basePaths = [
    `${prefix}/${upperSetCode}`,
    `${prefix}/${lowerSetCode}`,
  ];
  const candidates: string[] = [];
  // WebP first: the ITA image library is being migrated PNG -> WebP (same
  // resolution, ~85-90% smaller). New uploads land as .webp alongside the
  // existing .png; trying webp first means a card "upgrades" transparently
  // the moment its .webp lands in R2, with .png remaining a safe fallback
  // until the migration is verified complete.
  const imageExtensions = ['webp', 'png', 'jpg', 'jpeg'];

  const pushCandidate = (key: string) => {
    if (!key) return;
    if (!candidates.includes(key)) {
      candidates.push(key);
    }
  };

  // Primary layout for all expansions: {setCode}/{number}.png
  // Keep this list intentionally short to reduce R2 lookup latency.
  for (const basePath of basePaths) {
    for (const cardToken of cardNumberTokens.slice(0, 3)) {
      for (const ext of imageExtensions) {
        pushCandidate(`${basePath}/${cardToken}.${ext}`);
      }
    }
  }

  if (size === 'low' || size === 'high') {
    for (const basePath of basePaths) {
      for (const setToken of setCodeTokens) {
        for (const cardToken of cardNumberTokens.slice(0, 2)) {
          for (const ext of imageExtensions) {
            pushCandidate(`${basePath}/${setToken}_IT_${cardToken}_${size}.${ext}`);
            pushCandidate(`${basePath}/${setToken}_IT_${cardToken}-${size}.${ext}`);
          }
        }
      }
    }
  }

  // Legacy layouts kept as fallback for already-uploaded historical assets.
  // Only add PNG/WebP fallback variants to avoid a large candidate explosion.
  // WebP first (see imageExtensions comment above): this is the naming
  // pattern actually used by the 15k+ existing ITA card library.
  for (const basePath of basePaths) {
    for (const setToken of setCodeTokens.slice(0, 1)) {
      for (const cardToken of cardNumberTokens.slice(0, 2)) {
        pushCandidate(`${basePath}/${setToken}_IT_${cardToken}.webp`);
        pushCandidate(`${basePath}/${setToken}_IT_${cardToken}.png`);
      }
      for (const cardToken of cardNumberTokens.slice(0, 1)) {
        pushCandidate(`${basePath}/${cardToken}.png`);
      }
    }
  }

  return candidates;
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
  if (keys.length === 0) {
    return null;
  }

  // Probe in small parallel batches to reduce tail latency while preserving key priority.
  const batchSize = 8;
  for (let i = 0; i < keys.length; i += batchSize) {
    const batch = keys.slice(i, i + batchSize);
    const results = await Promise.all(
      batch.map(async (key) => ({ key, object: await bucket.get(key) }))
    );
    const hit = results.find((entry) => entry.object != null);
    if (hit?.object) {
      return { key: hit.key, object: hit.object };
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
      'cache-control': `public, max-age=${TTL_90_DAYS}, immutable`,
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

// Builds the exact same JSON shape the app already expects from
// /ita/catalog.json (a flat array of {cardId, espansioneId, nome, tipo, ps,
// attacchi, regolaSpeciale} -- see ItalianCatalogNormalizer.kt on the
// Android side) but sourced from D1 instead of the static R2 blob. Only
// cards belonging to `published = 1` expansions are included, matching the
// coverage-threshold rule: a set below 80% IT image coverage (e.g. me05 at
// ingest time) must not appear in the app until it clears the bar, exactly
// as it's already hidden from /v1/expansions.
//
// ITALIAN_CATALOG_URL is baked into the app at BUILD time (BuildConfig, from
// local.properties) -- already-installed clients can never be pointed at a
// different URL remotely. Swapping what powers this SAME endpoint server-
// side is the only way to get D1 (and therefore new automated ingests, e.g.
// me05 once it clears 80%) in front of existing installs without a new
// app release. Returns null on any failure so the caller falls back to the
// pre-existing R2 blob behavior untouched -- this must never make the
// catalog endpoint LESS reliable than it is today.
async function buildCatalogJsonFromD1(db: D1Database): Promise<string | null> {
  try {
    const { results } = await db
      .prepare(
        `SELECT c.card_id, c.expansion_id, c.nome, c.tipo, c.ps, c.regola_speciale, c.attacchi_json
         FROM cards c JOIN expansions e ON e.id = c.expansion_id
         WHERE e.published = 1`
      )
      .all<{ card_id: string; expansion_id: string; nome: string; tipo: string | null; ps: string | null; regola_speciale: string | null; attacchi_json: string }>();

    if (results.length === 0) return null; // suspiciously empty -- prefer the R2 fallback over serving nothing

    const cards = results.map((r) => ({
      cardId: r.card_id,
      espansioneId: r.expansion_id,
      nome: r.nome,
      tipo: r.tipo,
      ps: r.ps,
      attacchi: JSON.parse(r.attacchi_json || '[]'),
      regolaSpeciale: r.regola_speciale,
    }));
    return JSON.stringify(cards);
  } catch (error) {
    console.error('buildCatalogJsonFromD1 failed, falling back to R2 blob:', error);
    return null;
  }
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

  const d1Json = env.pokevault_catalog ? await buildCatalogJsonFromD1(env.pokevault_catalog) : null;

  let status = 404;
  let statusText = 'Not Found';
  let headersToCache: Record<string, string> = {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': `public, max-age=${TTL_5_MINUTES}`,
  };
  let responseBodyText = '{"error":"Italian catalog not found"}';

  if (d1Json) {
    status = 200;
    statusText = 'OK';
    responseBodyText = d1Json;
  } else {
    const prefix = normalizeR2Prefix(env.IT_IMAGE_PREFIX);
    const keyCandidates = buildItalianCatalogKeyCandidates(prefix, env.IT_CATALOG_KEY);
    const hit = await getFirstExistingR2Object(env.IMAGES_BUCKET, keyCandidates);

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

// ── Italian price snapshot helpers ──

function normalizeCardNumberKeyForPrices(raw: string): string | null {
  const clean = (raw.split('/')[0] ?? '').trim();
  if (!clean) return null;
  if (/^\d+$/.test(clean)) {
    return parseInt(clean, 10).toString();
  }
  return clean.toUpperCase();
}

function extractPriceEntryFromSearchCard(card: UpstreamSearchResultCard): ItalianPriceEntry | null {
  const prices = card.cardmarket?.prices ?? [];

  const hasValue = (price: UpstreamCardMarketPricePayload): boolean =>
    (price.avg ?? 0) > 0 || (price.low ?? 0) > 0 || (price.trend ?? 0) > 0;

  const cm = prices.find((price) => price?.variant_type === 'normal' && hasValue(price))
    ?? prices.find((price) => price != null && hasValue(price));

  if (cm) {
    const entry: ItalianPriceEntry = {};
    if (typeof cm.avg === 'number' && cm.avg > 0) entry.avg = cm.avg;
    if (typeof cm.low === 'number' && cm.low > 0) entry.low = cm.low;
    if (typeof cm.trend === 'number' && cm.trend > 0) entry.trend = cm.trend;
    if (typeof cm.avg1 === 'number' && cm.avg1 > 0) entry.avg1 = cm.avg1;
    if (typeof cm.avg7 === 'number' && cm.avg7 > 0) entry.avg7 = cm.avg7;
    if (typeof cm.avg30 === 'number' && cm.avg30 > 0) entry.avg30 = cm.avg30;
    if (entry.avg != null || entry.low != null || entry.trend != null) {
      const url = card.cardmarket?.product_url;
      if (url) entry.url = url;
      return entry;
    }
  }

  // Older sets (SWSH and earlier) carry only TCGPlayer USD prices upstream.
  const tpPrices = card.tcgplayer?.prices ?? [];
  const hasTpValue = (price: UpstreamTcgPlayerPricePayload): boolean =>
    (price.market_price ?? 0) > 0 || (price.low_price ?? 0) > 0;
  const tp = tpPrices.find((price) => price?.sub_type_name === 'Normal' && hasTpValue(price))
    ?? tpPrices.find((price) => price != null && hasTpValue(price));
  if (tp) {
    const entry: ItalianPriceEntry = {};
    if (typeof tp.market_price === 'number' && tp.market_price > 0) entry.usd = tp.market_price;
    if (typeof tp.low_price === 'number' && tp.low_price > 0) entry.usdLow = tp.low_price;
    if (entry.usd != null || entry.usdLow != null) {
      const url = card.tcgplayer?.url;
      if (url) entry.url = url;
      return entry;
    }
  }

  return null;
}

function buildProxySearchCacheKey(query: string, page: number): string {
  const params = new URLSearchParams();
  params.set('q', query);
  params.set('page', page.toString());
  params.set('limit', ITA_PRICE_SEARCH_PAGE_LIMIT.toString());
  return `pokewallet:/search${normalizeSearchParams(params)}`;
}

/**
 * Reads a /search page from the same KV slot the proxy uses for app requests;
 * falls back to one upstream fetch (budget-capped) and pre-warms that KV slot
 * so user requests also benefit. Zero extra API cost when warm.
 */
async function getSearchPageKvFirst(
  env: Env,
  cache: KVNamespace,
  query: string,
  page: number,
  budget: { remaining: number }
): Promise<UpstreamSearchPayload | null> {
  const cacheKey = buildProxySearchCacheKey(query, page);
  const cached = await cache.get(cacheKey, 'json') as CachedResponse | null;
  if (cached && isCacheValid(cached)) {
    if (cached.status !== 200 || cached.bodyEncoding === 'base64') {
      return null;
    }
    try {
      return JSON.parse(cached.body) as UpstreamSearchPayload;
    } catch {
      return null;
    }
  }

  if (budget.remaining <= 0) {
    return null;
  }
  budget.remaining -= 1;

  try {
    const params = new URLSearchParams({
      q: query,
      page: page.toString(),
      limit: ITA_PRICE_SEARCH_PAGE_LIMIT.toString(),
    });
    const response = await fetch(buildUpstreamUrl(env, '/search', params), {
      method: 'GET',
      headers: createUpstreamHeaders(env),
    });
    if (!response.ok) {
      return null;
    }
    const body = await response.text();
    const cachedResponse: CachedResponse = {
      status: 200,
      statusText: 'OK',
      headers: { 'content-type': 'application/json' },
      body,
      bodyEncoding: 'text',
      cachedAt: Date.now(),
      ttl: TTL_24_HOURS,
    };
    await cache.put(cacheKey, JSON.stringify(cachedResponse), { expirationTtl: TTL_24_HOURS });
    return JSON.parse(body) as UpstreamSearchPayload;
  } catch (error) {
    console.error(`ITA price snapshot: search fetch failed for "${query}" p${page}:`, error);
    return null;
  }
}

/** Derives candidate search queries from an upstream set name, most selective first. */
function deriveSearchQueryCandidatesForSetName(setName: string): string[] {
  const trimmed = setName.trim();
  if (!trimmed) return [];
  const candidates: string[] = [];
  const colonIndex = trimmed.indexOf(':');
  if (colonIndex > 0) {
    const prefix = trimmed.slice(0, colonIndex).trim();
    const suffix = trimmed.slice(colonIndex + 1).trim();
    // Code-like prefixes with digits (ME01, SV04) are highly selective queries,
    // but some are unknown upstream (ME03) — always fall back to the suffix name.
    if (/\d/.test(prefix)) {
      candidates.push(prefix);
    }
    if (suffix) candidates.push(suffix);
    if (!/\d/.test(prefix) && candidates.length === 0) candidates.push(prefix);
  } else {
    // "SM - Burning Shadows" -> "Burning Shadows".
    const dashIndex = trimmed.indexOf(' - ');
    if (dashIndex > 0) {
      const suffix = trimmed.slice(dashIndex + 3).trim();
      if (suffix) candidates.push(suffix);
    }
    candidates.push(trimmed);
  }
  return [...new Set(candidates)];
}

/**
 * Pages /search for one upstream set summary and merges price entries into
 * `collected`. Tries each candidate query until one yields matches.
 * Returns `complete: false` when pagination was cut short by the fetch budget,
 * so the expansion stays stale and the next run resumes from warm KV pages.
 */
async function collectPricesForSetSummary(
  env: Env,
  cache: KVNamespace,
  summary: PokeWalletSetSummary,
  budget: { remaining: number },
  collected: Record<string, ItalianPriceEntry>,
  queryOverrides?: string[]
): Promise<{ complete: boolean }> {
  if (!summary.set_id || !summary.name) {
    return { complete: true };
  }
  const queries = queryOverrides && queryOverrides.length > 0
    ? queryOverrides
    : deriveSearchQueryCandidatesForSetName(summary.name);
  // Override queries are complementary (results merged); derived candidates are
  // alternatives (stop at the first that matches).
  const mergeAllQueries = !!queryOverrides && queryOverrides.length > 0;
  const targetSetId = String(summary.set_id);

  let anyFound = false;
  let anyTruncated = false;
  for (const searchQuery of queries) {
    const sizeBefore = Object.keys(collected).length;
    let truncated = false;
    for (let page = 1; page <= ITA_PRICE_MAX_PAGES_PER_SET; page += 1) {
      const payload = await getSearchPageKvFirst(env, cache, searchQuery, page, budget);
      if (!payload) {
        truncated = true;
        break;
      }
      for (const card of payload.results ?? []) {
        if (String(card.card_info?.set_id ?? '') !== targetSetId) {
          continue;
        }
        const numberKey = normalizeCardNumberKeyForPrices(card.card_info?.card_number ?? '');
        if (!numberKey || numberKey in collected) {
          continue;
        }
        const entry = extractPriceEntryFromSearchCard(card);
        if (entry) {
          collected[numberKey] = entry;
        }
      }
      const totalPages = payload.pagination?.total_pages ?? 1;
      if (page >= totalPages) {
        break;
      }
    }
    if (truncated) {
      anyTruncated = true;
    }
    if (Object.keys(collected).length > sizeBefore) {
      anyFound = true;
      if (!mergeAllQueries) {
        return { complete: !truncated };
      }
    }
    if (truncated && budget.remaining <= 0) {
      return { complete: false };
    }
  }
  if (anyFound) {
    return { complete: !anyTruncated };
  }
  return { complete: true };
}

/**
 * Loads the upstream /sets directory, KV-first via the same slot the proxy
 * uses for app traffic; falls back to one upstream fetch and pre-warms the slot.
 */
async function loadUpstreamSetsDirectoryKvFirst(
  env: Env,
  cache: KVNamespace
): Promise<PokeWalletSetSummary[] | null> {
  const cacheKey = 'pokewallet:/sets';
  const cached = await cache.get(cacheKey, 'json') as CachedResponse | null;
  if (cached && isCacheValid(cached) && cached.status === 200 && cached.bodyEncoding !== 'base64') {
    try {
      const payload = JSON.parse(cached.body) as PokeWalletSetsPayload;
      if (Array.isArray(payload.data) && payload.data.length > 0) {
        return payload.data;
      }
    } catch {
      // fall through to upstream
    }
  }

  try {
    const response = await fetch(buildUpstreamUrl(env, '/sets'), {
      method: 'GET',
      headers: createUpstreamHeaders(env),
    });
    if (!response.ok) {
      console.error(`ITA price snapshot: /sets directory fetch failed: ${response.status}`);
      return null;
    }
    const body = await response.text();
    const payload = JSON.parse(body) as PokeWalletSetsPayload;
    const cachedResponse: CachedResponse = {
      status: 200,
      statusText: 'OK',
      headers: { 'content-type': 'application/json' },
      body,
      bodyEncoding: 'text',
      cachedAt: Date.now(),
      ttl: TTL_24_HOURS,
    };
    await cache.put(cacheKey, JSON.stringify(cachedResponse), { expirationTtl: TTL_24_HOURS });
    return payload.data ?? [];
  } catch (error) {
    console.error('ITA price snapshot: failed to load /sets directory:', error);
    return null;
  }
}

async function loadItalianCatalogCards(env: Env): Promise<ItalianCatalogRecordPayload[]> {
  if (!env.IMAGES_BUCKET) {
    return [];
  }
  const prefix = normalizeR2Prefix(env.IT_IMAGE_PREFIX);
  const candidates = buildItalianCatalogKeyCandidates(prefix, env.IT_CATALOG_KEY);
  const hit = await getFirstExistingR2Object(env.IMAGES_BUCKET, candidates);
  if (!hit) {
    return [];
  }
  try {
    const rawText = await hit.object.text();
    // Strip UTF-8 BOM (the catalog file may include one; JSON.parse rejects it).
    const cleanText = rawText.charCodeAt(0) === 0xfeff ? rawText.slice(1) : rawText;
    const parsed = JSON.parse(cleanText) as unknown;
    const cards = Array.isArray(parsed)
      ? parsed
      : (parsed as { cards?: unknown[] } | null)?.cards ?? [];
    return Array.isArray(cards) ? cards as ItalianCatalogRecordPayload[] : [];
  } catch (error) {
    console.error('ITA price snapshot: failed to parse catalog:', error);
    return [];
  }
}

async function buildItalianPriceSnapshot(
  env: Env,
  cache: KVNamespace,
  options: { force?: boolean } = {}
): Promise<ItalianPriceSnapshot | null> {
  const existing = await cache.get(ITA_PRICE_SNAPSHOT_KEY, 'json') as ItalianPriceSnapshot | null;

  const records = await loadItalianCatalogCards(env);
  if (records.length === 0) {
    return existing;
  }

  // Group catalog records by expansion and tally raw image set codes.
  const rawCodeCountsByExpansion = new Map<string, Map<string, number>>();
  for (const record of records) {
    const expansionId = (record.espansioneId ?? '').trim().toLowerCase();
    if (!expansionId) {
      continue;
    }
    let counts = rawCodeCountsByExpansion.get(expansionId);
    if (!counts) {
      counts = new Map();
      rawCodeCountsByExpansion.set(expansionId, counts);
    }
    const match = (record.cardId ?? '').trim().match(ITALIAN_CARD_ID_PREFIX_REGEX);
    if (match) {
      const code = match[1].toUpperCase();
      counts.set(code, (counts.get(code) ?? 0) + 1);
    }
  }

  // Upstream set directory: maps base set codes to set_id + name (search filter).
  const setSummaries = await loadUpstreamSetsDirectoryKvFirst(env, cache);
  if (!setSummaries || setSummaries.length === 0) {
    return existing;
  }

  const findSetSummary = (code: string): PokeWalletSetSummary | undefined => {
    const target = code.trim().toUpperCase();
    const matches = setSummaries.filter(
      (summary) => (summary.set_code ?? '').trim().toUpperCase() === target
    );
    return matches.find((summary) => normalizeLanguageMacro(summary.language) === 'ENG') ?? matches[0];
  };

  // Merge over the previous snapshot so budget-capped runs converge over time.
  const snapshot: ItalianPriceSnapshot = {
    version: 1,
    builtAt: Date.now(),
    expansions: { ...(existing?.expansions ?? {}) },
    aliases: { ...(existing?.aliases ?? {}) },
    totalExpansions: rawCodeCountsByExpansion.size,
  };

  const budget = { remaining: ITA_PRICE_MAX_UPSTREAM_FETCHES };
  const now = Date.now();

  // Process stalest expansions first so refresh effort is spread fairly.
  const orderedExpansions = [...rawCodeCountsByExpansion.entries()].sort((a, b) => {
    const updatedA = snapshot.expansions[a[0]]?.updatedAt ?? 0;
    const updatedB = snapshot.expansions[b[0]]?.updatedAt ?? 0;
    return updatedA - updatedB;
  });

  for (const [expansionId, rawCodeCounts] of orderedExpansions) {
    const existingEntry = snapshot.expansions[expansionId];
    if (!options.force && existingEntry && now - existingEntry.updatedAt < ITA_PRICE_EXPANSION_STALE_MS) {
      continue;
    }

    const dominantRawCode = [...rawCodeCounts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0];

    let resolvedBase = '';
    let prices: Record<string, ItalianPriceEntry> = {};
    let complete = true;

    // Preferred path: explicit upstream set_id mapping (unambiguous, supports
    // multi-set expansions like Generations + Radiant Collection).
    const mappedSetIds = ITA_EXPANSION_UPSTREAM_SET_IDS[expansionId];
    const mappedSummaries = (mappedSetIds ?? [])
      .map((setId) => setSummaries.find((summary) => String(summary.set_id ?? '') === setId))
      .filter((summary): summary is PokeWalletSetSummary => !!summary?.set_id && !!summary.name);

    if (mappedSummaries.length > 0) {
      const collected: Record<string, ItalianPriceEntry> = {};
      const queryOverrides = ITA_EXPANSION_QUERY_OVERRIDE[expansionId];
      for (const summary of mappedSummaries) {
        const result = await collectPricesForSetSummary(env, cache, summary, budget, collected, queryOverrides);
        if (!result.complete) {
          complete = false;
        }
      }
      if (Object.keys(collected).length > 0 || complete) {
        resolvedBase = (mappedSummaries[0].set_code ?? '').trim().toUpperCase() || expansionId.toUpperCase();
        prices = collected;
      }
    } else {
      // Fallback path: resolve by set_code candidates.
      const preferredBase = ITA_EXPANSION_BASE_SET[expansionId];
      const candidates = [...new Set(
        [preferredBase, dominantRawCode, expansionId.toUpperCase()]
          .filter((candidate): candidate is string => !!candidate)
      )];

      for (const candidate of candidates) {
        const summary = findSetSummary(candidate);
        if (!summary?.set_id || !summary.name) {
          continue;
        }
        const collected: Record<string, ItalianPriceEntry> = {};
        const result = await collectPricesForSetSummary(env, cache, summary, budget, collected);
        if (Object.keys(collected).length > 0) {
          resolvedBase = candidate;
          prices = collected;
          complete = result.complete;
          break;
        }
        if (!result.complete) {
          complete = false;
          break;
        }
      }
    }

    const mergedPrices = { ...(existingEntry?.prices ?? {}), ...prices };
    const baseCode = resolvedBase || existingEntry?.baseSetCode || '';
    if (baseCode && Object.keys(mergedPrices).length > 0) {
      snapshot.expansions[expansionId] = {
        baseSetCode: baseCode,
        // Incomplete runs stay stale so the next run resumes (KV pages are warm).
        updatedAt: complete ? now : (existingEntry?.updatedAt ?? 0),
        prices: mergedPrices,
      };
      snapshot.aliases[expansionId] = expansionId;
      snapshot.aliases[baseCode.toLowerCase()] = expansionId;
      if (dominantRawCode) {
        snapshot.aliases[dominantRawCode.toLowerCase()] = expansionId;
      }
    }

    if (budget.remaining <= 0) {
      break;
    }
  }

  await cache.put(ITA_PRICE_SNAPSHOT_KEY, JSON.stringify(snapshot), {
    expirationTtl: ITA_PRICE_SNAPSHOT_TTL,
  });
  return snapshot;
}

async function handleItalianPricesRequest(
  requestUrl: URL,
  env: Env,
  cache: KVNamespace
): Promise<Response> {
  let snapshot = await cache.get(ITA_PRICE_SNAPSHOT_KEY, 'json') as ItalianPriceSnapshot | null;
  const wantsRebuild = requestUrl.searchParams.get('rebuild') === '1';
  const coveredCount = snapshot ? Object.keys(snapshot.expansions).length : 0;
  const coverageIncomplete = !snapshot
    || coveredCount === 0
    || snapshot.totalExpansions == null
    || coveredCount < snapshot.totalExpansions;
  const staleEnoughForRebuild = coverageIncomplete
    || !snapshot
    || Date.now() - snapshot.builtAt > ITA_PRICE_INLINE_REBUILD_MIN_AGE_MS;

  if (wantsRebuild && staleEnoughForRebuild) {
    // force=1 bypasses per-expansion freshness (warm KV pages keep it cheap);
    // otherwise staleness directs the budget to incomplete/stale expansions.
    const wantsForce = requestUrl.searchParams.get('force') === '1';
    snapshot = await buildItalianPriceSnapshot(env, cache, { force: wantsForce }) ?? snapshot;
  } else if (!snapshot) {
    snapshot = await buildItalianPriceSnapshot(env, cache);
  }

  if (!snapshot) {
    return new Response(JSON.stringify({ error: 'Italian price snapshot not available' }), {
      status: 404,
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'Cache-Control': `public, max-age=${TTL_5_MINUTES}`,
      },
    });
  }

  return new Response(JSON.stringify(snapshot), {
    status: 200,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'public, max-age=1800',
      'X-Snapshot-Built-At': new Date(snapshot.builtAt).toISOString(),
      'X-Snapshot-Expansions': Object.keys(snapshot.expansions).length.toString(),
    },
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

// ── D1-backed /v1 catalog API ──
// Additive, read-only routes on top of the new queryable catalog (see
// schema/001_init.sql). Deliberately independent of the CACHE/KV binding
// and of the legacy R2-blob catalog endpoints above: nothing existing
// changes behavior because these routes exist. `published = 0` expansions
// (below the IT image coverage threshold) are hidden from the listing but
// individual cards remain resolvable by id, matching the plan's coverage
// rule (a set can be incomplete without breaking a user's existing collection).

function jsonResponse(data: unknown, status = 200, cacheControl = 'public, max-age=300'): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': cacheControl },
  });
}

/**
 * Cache-control per le rotte di catalogo /v1.
 *
 * Il catalogo cambia al massimo una volta al giorno (cron catalog-ingest alle
 * 06:00), ma queste risposte uscivano con "max-age=300" e nient'altro. I
 * Worker non cachano da soli le risposte che generano, quindi ogni singola
 * richiesta arrivava fino a D1: su /v1/expansions/{id}/cards significa
 * scandire tutte le carte dell'espansione a ogni apertura di un set.
 *
 * s-maxage governa la cache edge, max-age quella del client, e
 * stale-while-revalidate evita che la scadenza si traduca in una richiesta
 * lenta per l'utente che capita nel momento sbagliato.
 */
const V1_CATALOG_CACHE_CONTROL = 'public, max-age=300, s-maxage=3600, stale-while-revalidate=86400';

async function handleV1ApiRequest(pathname: string, env: Env): Promise<Response | null> {
  if (!pathname.startsWith('/v1/')) return null;
  const db = env.pokevault_catalog;
  if (!db) {
    return jsonResponse({ error: 'D1 binding (pokevault_catalog) not configured in this environment' }, 500);
  }

  if (pathname === '/v1/health') {
    const row = await db.prepare('SELECT catalog_version FROM catalog_meta WHERE id = 1').first<{ catalog_version: number }>();
    return jsonResponse({ status: 'ok', catalog_version: row?.catalog_version ?? null });
  }

  if (pathname === '/v1/expansions') {
    // Ordinamento per data di uscita, non per sort_order: sort_order e' rimasto
    // 100 per tutte le espansioni importate (vedi schema/002 e
    // scripts/import-catalog-to-d1.mjs), quindi "ORDER BY sort_order, id"
    // dava di fatto un ordine alfabetico per id. release_date esisteva ed era
    // inutilizzata. Le espansioni senza data finiscono in fondo invece di
    // sparire in mezzo, e sort_order resta come discriminante manuale.
    const { results } = await db
      .prepare(
        `SELECT e.id, e.card_count, e.sort_order, e.logo_key, e.dominant_set_code, e.release_date
         FROM expansions e
         WHERE e.published = 1
           AND NOT EXISTS (
             SELECT 1 FROM takedowns t
             WHERE t.target_type = 'expansion' AND t.target_id = e.id
           )
         ORDER BY (e.release_date IS NULL), e.release_date DESC, e.sort_order, e.id`
      )
      .all();
    return jsonResponse({ expansions: results }, 200, V1_CATALOG_CACHE_CONTROL);
  }

  const cardsMatch = pathname.match(/^\/v1\/expansions\/([A-Za-z0-9._-]+)\/cards$/);
  if (cardsMatch) {
    const expansionId = cardsMatch[1].toLowerCase();

    // Kill-switch di compliance: la tabella takedowns esisteva dallo schema v1
    // ma nessuna rotta /v1 la consultava, quindi non aveva alcun effetto.
    const expansionTakedown = await db
      .prepare(`SELECT 1 FROM takedowns WHERE target_type = 'expansion' AND target_id = ?1`)
      .bind(expansionId)
      .first();
    if (expansionTakedown) {
      return jsonResponse({ error: 'expansion not available' }, 451);
    }

    const { results } = await db
      .prepare(
        `SELECT c.card_id, c.card_number, c.nome, c.tipo, c.ps, c.regola_speciale,
                c.attacchi_json, c.image_status
         FROM cards c
         WHERE c.expansion_id = ?1
           AND NOT EXISTS (
             SELECT 1 FROM takedowns t
             WHERE t.target_type = 'card' AND t.target_id = c.card_id
           )
         ORDER BY CAST(c.card_number AS INTEGER), c.card_number`
      )
      .bind(expansionId)
      .all();
    if (results.length === 0) {
      return jsonResponse({ error: 'expansion not found or has no cards' }, 404);
    }
    return jsonResponse({ expansionId, cards: results }, 200, V1_CATALOG_CACHE_CONTROL);
  }

  const cardMatch = pathname.match(/^\/v1\/cards\/([A-Za-z0-9._-]+)$/);
  if (cardMatch) {
    const cardId = cardMatch[1];
    const row = await db
      .prepare(
        `SELECT c.* FROM cards c
         WHERE c.card_id = ?1
           AND NOT EXISTS (
             SELECT 1 FROM takedowns t
             WHERE (t.target_type = 'card' AND t.target_id = c.card_id)
                OR (t.target_type = 'expansion' AND t.target_id = c.expansion_id)
           )`
      )
      .bind(cardId)
      .first();
    if (!row) return jsonResponse({ error: 'card not found' }, 404);
    return jsonResponse(row, 200, V1_CATALOG_CACHE_CONTROL);
  }

  return jsonResponse({ error: 'unknown /v1 route' }, 404);
}

/**
 * Main request handler
 */
export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const requestUrl = new URL(request.url);

    // Le rotte di billing usano POST: vanno risolte PRIMA del filtro sui GET.
    const billingResponse = await handleBillingRequest(request, requestUrl.pathname, env);
    if (billingResponse) return billingResponse;

    // Only cache GET requests
    if (request.method !== 'GET') {
      return new Response('Method not allowed', { status: 405 });
    }

    // New D1-backed catalog API: independent of the CACHE/KV binding
    // required below, and does not affect any existing route.
    const v1Response = await handleV1ApiRequest(requestUrl.pathname, env);
    if (v1Response) return v1Response;

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

    if (requestUrl.pathname === '/ita/prices.json') {
      return handleItalianPricesRequest(requestUrl, env, cache);
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

    ctx.waitUntil(Promise.allSettled([
      backfillRealSetTotals(env, env.CACHE),
      buildItalianPriceSnapshot(env, env.CACHE),
    ]));
  },
};
