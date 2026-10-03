// Prezzi dal listino pubblico Cardmarket (price_guide_6.json), fusi sopra lo
// snapshot costruito da PokeWallet.
//
// Il listino lo scarica ogni mattina scripts/build-cardmarket-prices.mjs, che
// lo incrocia con la tabella card_cardmarket (carta -> idProduct, schema/013)
// e lo salva su R2 gia' diviso per espansione e numero di carta, nello stesso
// formato delle voci dello snapshot. Qui c'e' solo la fusione: niente rete,
// niente KV, cosi' si prova da sola (test/cardmarket-prices.test.ts).
//
// Regole, scelte perche' nessuna carta stia peggio di oggi:
//  - una carta col prezzo Cardmarket prende i suoi euro (avg, low, trend,
//    avg1, avg7, avg30); quella senza resta com'era, con PokeWallet;
//  - il link resta quello di PokeWallet se punta a cardmarket.com. Un link
//    TCGplayer invece si toglie: l'app mostra `url` come link Cardmarket
//    quando ci sono prezzi in euro (toPriceData()), e una carta che oggi ha
//    solo dollari passerebbe a un link TCGplayer etichettato Cardmarket;
//  - i dollari (usd, usdLow) restano dove c'erano;
//  - un listino piu' vecchio di CARDMARKET_MAX_AGE_MS non si usa affatto, e
//    torna tutto PokeWallet: meglio un prezzo di ieri che uno fermo da giorni.

export interface PriceEntry {
  avg?: number;
  low?: number;
  trend?: number;
  avg1?: number;
  avg7?: number;
  avg30?: number;
  usd?: number;
  usdLow?: number;
  url?: string;
}

export interface PriceExpansionEntry {
  baseSetCode: string;
  updatedAt: number;
  prices: Record<string, PriceEntry>;
  numbering?: 'catalog';
}

export interface PriceSnapshot {
  version: number;
  builtAt: number;
  expansions: Record<string, PriceExpansionEntry>;
  aliases: Record<string, string>;
  totalExpansions?: number;
  /** Solo diagnostica: data del listino Cardmarket fuso qui dentro. */
  cardmarketCreatedAt?: string;
}

export interface CardmarketPrice {
  avg?: number | null;
  low?: number | null;
  trend?: number | null;
  avg1?: number | null;
  avg7?: number | null;
  avg30?: number | null;
}

export interface CardmarketExpansion {
  /** Altri codici con cui l'app puo' chiedere l'espansione (prefisso del card_id). */
  aliases?: string[];
  /** Chiave: numero della carta normalizzato come normalizeCardNumberKeyForPrices. */
  prices: Record<string, CardmarketPrice>;
}

export interface CardmarketBlob {
  version: number;
  /** createdAt del listino Cardmarket, es. "2026-10-03T02:41:55+0200". */
  createdAt: string;
  builtAt: number;
  expansions: Record<string, CardmarketExpansion>;
}

export const CARDMARKET_BLOB_VERSION = 1;
export const CARDMARKET_MAX_AGE_MS = 72 * 60 * 60 * 1000;

const EUR_FIELDS = ['avg', 'low', 'trend', 'avg1', 'avg7', 'avg30'] as const;

function isPositive(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0;
}

export function isCardmarketUrl(url: string | undefined): boolean {
  return !!url && /^https?:\/\/(www\.)?cardmarket\.com\//i.test(url);
}

/** Motivo per cui il listino non si puo' usare, o null se va bene. */
export function cardmarketBlobProblem(blob: CardmarketBlob | null | undefined, now: number): string | null {
  if (!blob || typeof blob !== 'object') return 'listino assente';
  if (blob.version !== CARDMARKET_BLOB_VERSION) return `versione ${String(blob.version)} non riconosciuta`;
  const created = Date.parse(blob.createdAt);
  if (!Number.isFinite(created)) return 'createdAt illeggibile';
  if (now - created > CARDMARKET_MAX_AGE_MS) return `listino vecchio di ${Math.round((now - created) / 3_600_000)} ore`;
  if (!blob.expansions || Object.keys(blob.expansions).length === 0) return 'nessuna espansione';
  return null;
}

function eurEntry(price: CardmarketPrice): PriceEntry | null {
  if (!isPositive(price.avg) && !isPositive(price.low) && !isPositive(price.trend)) return null;
  const entry: PriceEntry = {};
  for (const field of EUR_FIELDS) {
    const value = price[field];
    if (isPositive(value)) entry[field] = value;
  }
  return entry;
}

/**
 * Snapshot da servire all'app: quello di PokeWallet con sopra i prezzi
 * Cardmarket. Non modifica gli argomenti.
 */
export function mergeCardmarketPrices(
  pokewallet: PriceSnapshot | null,
  cardmarket: CardmarketBlob,
  now: number,
): PriceSnapshot {
  const createdAt = Date.parse(cardmarket.createdAt);
  const expansions: Record<string, PriceExpansionEntry> = { ...(pokewallet?.expansions ?? {}) };
  const aliases: Record<string, string> = { ...(pokewallet?.aliases ?? {}) };

  for (const [expansionId, cmExpansion] of Object.entries(cardmarket.expansions)) {
    const base = expansions[expansionId];
    const prices: Record<string, PriceEntry> = { ...(base?.prices ?? {}) };
    let changed = 0;

    for (const [number, cmPrice] of Object.entries(cmExpansion.prices ?? {})) {
      const eur = eurEntry(cmPrice);
      if (!eur) continue;
      const previous = prices[number];
      const next: PriceEntry = { ...eur };
      if (isPositive(previous?.usd)) next.usd = previous.usd;
      if (isPositive(previous?.usdLow)) next.usdLow = previous.usdLow;
      if (isCardmarketUrl(previous?.url)) next.url = previous!.url;
      prices[number] = next;
      changed += 1;
    }

    if (changed === 0) continue;

    expansions[expansionId] = {
      ...(base ?? {}),
      baseSetCode: base?.baseSetCode || expansionId.toUpperCase(),
      updatedAt: Math.max(base?.updatedAt ?? 0, Number.isFinite(createdAt) ? createdAt : 0),
      prices,
    };
    // Gli alias di PokeWallet restano com'erano: si aggiungono solo i mancanti.
    if (!aliases[expansionId]) aliases[expansionId] = expansionId;
    for (const alias of cmExpansion.aliases ?? []) {
      const key = alias.trim().toLowerCase();
      if (key && !aliases[key]) aliases[key] = expansionId;
    }
  }

  return {
    version: pokewallet?.version ?? 1,
    builtAt: now,
    expansions,
    aliases,
    ...(pokewallet?.totalExpansions != null ? { totalExpansions: pokewallet.totalExpansions } : {}),
    cardmarketCreatedAt: cardmarket.createdAt,
  };
}
