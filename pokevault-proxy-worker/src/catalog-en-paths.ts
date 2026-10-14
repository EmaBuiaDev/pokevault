// Le parti pure del catalogo EN (src/catalog-en.ts): quale rotta e quale
// chiave R2. Senza import, cosi' i test le provano con node --test.

export const EN_R2_PREFIX = 'en';
// Deve restare uguale a CARDMARKET_EN_R2_KEY in scripts/build-cardmarket-prices-en.mjs.
export const CARDMARKET_EN_R2_KEY = 'prezzi/cardmarket-en-v1.json';
// Il catalogo EN intero per la ricerca dell'app, rifatto da D1 al massimo ogni
// CATALOG_EN_MAX_AGE_MS: tenerlo su R2 risparmia la quota D1 (ventimila righe a giro).
export const CATALOG_EN_R2_KEY = 'en/catalog/catalog.json';
export const CATALOG_EN_MAX_AGE_MS = 6 * 60 * 60 * 1000;
// L'indice degli illustratori EN, stessa regola: anche lui legge tutte le carte.
export const ILLUSTRATORS_EN_R2_KEY = 'en/catalog/illustrators.json';


export type EnglishRequest =
  | { kind: 'health' }
  | { kind: 'expansions' }
  | { kind: 'catalog' }
  | { kind: 'cards'; expansionId: string }
  | { kind: 'cardImage'; setCode: string; cardNumber: string }
  | { kind: 'setLogo'; setCode: string }
  | { kind: 'setSymbol'; setCode: string }
  | { kind: 'illustrators' }
  | { kind: 'illustratorCards'; name: string }
  | { kind: 'prices' }
  | { kind: 'expansionPrices'; code: string };

function decode(raw: string): string | null {
  try {
    return decodeURIComponent(raw).trim();
  } catch {
    return null;
  }
}

/** I numeri puri perdono gli zeri davanti ("007" -> "7"), i promo a lettere restano ("SWSH026"). */
export function normalizeEnCardNumber(raw: string): string {
  const trimmed = raw.trim();
  if (/^\d+$/.test(trimmed)) return trimmed.replace(/^0+(?=\d)/, '');
  return trimmed;
}

/** La chiave R2 di una carta: en/SV08/SV08_EN_12.webp. */
export function enCardImageKey(setCode: string, cardNumber: string): string {
  const folder = setCode.trim().toUpperCase();
  return `${EN_R2_PREFIX}/${folder}/${folder}_EN_${normalizeEnCardNumber(cardNumber)}.webp`;
}

export function enSetAssetKeys(setCode: string, asset: 'logo' | 'symbol'): string[] {
  const folder = setCode.trim().toUpperCase();
  return [`${EN_R2_PREFIX}/${folder}/${asset}.webp`, `${EN_R2_PREFIX}/${folder}/${asset}.png`];
}

const SET_CODE_RE = /^[A-Za-z0-9._-]{1,32}$/;
const CARD_NUMBER_RE = /^[A-Za-z0-9_-]{1,20}$/;

/** Cosa chiede la richiesta al catalogo EN, o null se non e' una rotta sua. */
export function parseEnglishRequest(url: URL): EnglishRequest | null {
  const { pathname } = url;

  if (pathname === '/v1/en/health') return { kind: 'health' };
  if (pathname === '/v1/en/expansions') return { kind: 'expansions' };
  // Come /ita/catalog.json, ma nella forma di /v1/en/expansions/{id}/cards ({cards: [...]}).
  if (pathname === '/en/catalog.json') return { kind: 'catalog' };
  if (pathname === '/v1/en/illustrators') return { kind: 'illustrators' };
  const illustrator = pathname.match(/^\/v1\/en\/illustrators\/(.+)\/cards$/);
  if (illustrator) {
    const name = decode(illustrator[1]);
    if (!name || name.length > 200) return null;
    return { kind: 'illustratorCards', name };
  }

  // Prezzi: stesso schema di /ita/prices.json e /ita/prices/{code}.json.
  if (pathname === '/en/prices.json') return { kind: 'prices' };
  const prices = pathname.match(/^\/en\/prices\/([^/]+)\.json$/i);
  if (prices) {
    const code = decode(prices[1]);
    if (!code || !SET_CODE_RE.test(code)) return null;
    return { kind: 'expansionPrices', code: code.toLowerCase() };
  }

  const cards = pathname.match(/^\/v1\/en\/expansions\/([A-Za-z0-9._-]+)\/cards$/);
  if (cards) return { kind: 'cards', expansionId: cards[1].toLowerCase() };

  const image = pathname.match(/^\/images\/en\/([^/]+)\/([^/]+)$/i);
  if (image) {
    const setCode = decode(image[1]);
    const cardNumber = decode(image[2]);
    if (!setCode || !cardNumber || !SET_CODE_RE.test(setCode) || !CARD_NUMBER_RE.test(cardNumber)) return null;
    return { kind: 'cardImage', setCode: setCode.toUpperCase(), cardNumber };
  }

  if (url.searchParams.get('source') === 'en') {
    const asset = pathname.match(/^\/sets\/([^/]+)\/(image|symbol)$/i);
    if (asset) {
      const setCode = decode(asset[1]);
      if (!setCode || !SET_CODE_RE.test(setCode)) return null;
      return asset[2].toLowerCase() === 'image'
        ? { kind: 'setLogo', setCode: setCode.toUpperCase() }
        : { kind: 'setSymbol', setCode: setCode.toUpperCase() };
    }
  }

  return null;
}

/**
 * La chiave TradeRadar di una carta del catalogo EN: SV08_EN_25.webp -> sv08:25.
 * Per i set gemelli e' la stessa chiave della carta italiana (stessa cartella,
 * stesso numero): negli scambi IT ed EN sono la stessa carta. I set solo EN
 * hanno codici che non collidono con l'italiano (lo garantisce l'import).
 */
export function cardKeyOfEnCardId(cardId: string): string | null {
  const match = /^([A-Za-z0-9-]+)_EN_([A-Za-z0-9_]+)\.(png|webp|jpe?g)$/i.exec(cardId.trim());
  if (!match) return null;
  const number = /^\d+$/.test(match[2]) ? String(parseInt(match[2], 10)) : match[2];
  return `${match[1].toLowerCase()}:${number}`;
}
