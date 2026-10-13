// Le parti pure del catalogo EN (src/catalog-en.ts): quale rotta e quale
// chiave R2. Senza import, cosi' i test le provano con node --test.

export const EN_R2_PREFIX = 'en';


export type EnglishRequest =
  | { kind: 'health' }
  | { kind: 'expansions' }
  | { kind: 'cards'; expansionId: string }
  | { kind: 'cardImage'; setCode: string; cardNumber: string }
  | { kind: 'setLogo'; setCode: string }
  | { kind: 'setSymbol'; setCode: string };

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
