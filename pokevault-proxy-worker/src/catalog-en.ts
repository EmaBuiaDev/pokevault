// Catalogo INGLESE (piano carte EN, Fase 2, 13/10/2026).
//
// Solo aggiunte: rotte nuove sotto /v1/en/ e /images/en/, piu' logo e simbolo
// con ?source=en. Legge un database D1 suo (binding `catalog_en`,
// schema-en/001_init.sql) e la cartella en/ dello stesso bucket R2.
//
// Se il binding `catalog_en` non c'e' -- oggi in produzione -- ogni funzione
// qui restituisce null e la richiesta prosegue esattamente come prima: il
// Worker italiano non si accorge che il modulo esiste.
//
// Niente cache KV: le immagini si leggono da R2 a ogni richiesta (le Class B
// sono gratis fino a 10 milioni al mese) con Cache-Control per l'app. Cosi'
// un'immagine caricata dopo un 404 si vede subito, senza la trappola dei 404
// tenuti 24 ore che ha il catalogo italiano.

import { mapCardRow, type D1CardRow } from './card-row';
import { CARDMARKET_EN_R2_KEY, enCardImageKey, enSetAssetKeys, parseEnglishRequest } from './catalog-en-paths';
import { cardmarketBlobProblem, mergeCardmarketPrices, type CardmarketBlob, type PriceSnapshot } from './cardmarket-prices';
import { resolveExpansionPrices } from './price-lookup';

// Il listino EN servito, tenuto in memoria dall'isolate per qualche minuto:
// e' un file R2 che cambia due volte al giorno.
const PRICES_MEMO_MS = 10 * 60 * 1000;
let pricesMemo: { at: number; snapshot: PriceSnapshot; problem: string | null } | null = null;

async function loadEnglishPrices(bucket: R2Bucket | undefined): Promise<{ snapshot: PriceSnapshot; problem: string | null } | null> {
  const now = Date.now();
  if (pricesMemo && now - pricesMemo.at < PRICES_MEMO_MS) return pricesMemo;
  if (!bucket) return null;
  try {
    const object = await bucket.get(CARDMARKET_EN_R2_KEY);
    if (!object) return null;
    const blob = JSON.parse(await object.text()) as CardmarketBlob;
    // Stesso formato che l'app riceve per l'italiano (link Cardmarket compresi).
    // Un listino vecchio si serve lo stesso: per l'EN non c'e' un ripiego
    // PokeWallet, e un prezzo di tre giorni fa e' meglio di nessun prezzo.
    pricesMemo = { at: now, snapshot: mergeCardmarketPrices(null, blob, now), problem: cardmarketBlobProblem(blob, now) };
    return pricesMemo;
  } catch (error) {
    console.error('catalogo EN: listino prezzi illeggibile:', error);
    return null;
  }
}

export interface EnglishCatalogEnv {
  catalog_en?: D1Database;
  IMAGES_BUCKET?: R2Bucket;
}

const CATALOG_CACHE_CONTROL = 'public, max-age=300, s-maxage=3600, stale-while-revalidate=86400';
const IMAGE_CACHE_CONTROL = 'public, max-age=2592000, immutable';
const MISSING_CACHE_CONTROL = 'public, max-age=300';

function json(body: unknown, status = 200, cacheControl?: string): Response {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
  };
  if (cacheControl) headers['Cache-Control'] = cacheControl;
  return new Response(JSON.stringify(body), { status, headers });
}

async function serveR2(bucket: R2Bucket | undefined, keys: string[]): Promise<Response> {
  if (!bucket) return json({ error: 'R2 binding (IMAGES_BUCKET) not configured' }, 500);
  for (const key of keys) {
    const object = await bucket.get(key);
    if (!object) continue;
    const headers = new Headers();
    object.writeHttpMetadata(headers);
    if (!headers.get('Content-Type')) headers.set('Content-Type', key.endsWith('.png') ? 'image/png' : 'image/webp');
    headers.set('Cache-Control', IMAGE_CACHE_CONTROL);
    headers.set('ETag', object.httpEtag);
    headers.set('Access-Control-Allow-Origin', '*');
    return new Response(object.body, { status: 200, headers });
  }
  return new Response('Not found', { status: 404, headers: { 'Cache-Control': MISSING_CACHE_CONTROL } });
}

/**
 * Le rotte del catalogo EN. null = non e' una rotta EN, oppure il catalogo EN
 * non e' collegato in questo ambiente: la richiesta prosegue come prima.
 */
export async function handleEnglishCatalogRequest(request: Request, url: URL, env: EnglishCatalogEnv): Promise<Response | null> {
  const db = env.catalog_en;
  if (!db || request.method !== 'GET') return null;
  const route = parseEnglishRequest(url);
  if (!route) return null;

  switch (route.kind) {
    case 'health': {
      const row = await db
        .prepare(
          `SELECT
             (SELECT COUNT(*) FROM expansions WHERE published = 1) AS published,
             (SELECT COUNT(*) FROM expansions) AS expansions,
             (SELECT COUNT(*) FROM cards) AS cards,
             (SELECT COUNT(*) FROM cards WHERE image_status = 'ok') AS with_image`
        )
        .first<{ published: number; expansions: number; cards: number; with_image: number }>();
      return json({ status: 'ok', lang: 'en', ...row });
    }

    case 'expansions': {
      const { results } = await db
        .prepare(
          `SELECT e.id, e.name, e.card_count, e.official_count, e.sort_order, e.logo_key, e.base_set_code, e.release_date,
                  e.series, e.abbreviation, e.it_expansion_id
           FROM expansions e
           WHERE e.published = 1
             AND NOT EXISTS (SELECT 1 FROM takedowns t WHERE t.target_type = 'expansion' AND t.target_id = e.id)
           ORDER BY (e.release_date IS NULL), e.release_date DESC, e.sort_order, e.id`
        )
        .all<{
          id: string; name: string | null; card_count: number; official_count: number | null; sort_order: number;
          logo_key: string | null; base_set_code: string | null; release_date: string | null; series: string | null;
          abbreviation: string | null; it_expansion_id: string | null;
        }>();
      // Stessi campi di /v1/expansions (ItalianExpansionSummary), piu' il gemello italiano.
      const expansions = results.map((r) => ({
        id: r.id,
        name: r.name,
        cardCount: r.card_count,
        officialCount: r.official_count,
        sortOrder: r.sort_order,
        logoKey: r.logo_key,
        series: r.series,
        abbreviation: r.abbreviation,
        baseSetCode: r.base_set_code,
        releaseDate: r.release_date,
        itExpansionId: r.it_expansion_id,
      }));
      return json({ expansions }, 200, CATALOG_CACHE_CONTROL);
    }

    case 'cards': {
      const takedown = await db
        .prepare(`SELECT 1 FROM takedowns WHERE target_type = 'expansion' AND target_id = ?1`)
        .bind(route.expansionId)
        .first();
      if (takedown) return json({ error: 'expansion not available' }, 451);

      const { results } = await db
        .prepare(
          `SELECT c.card_id, c.expansion_id, c.nome, c.tipo, c.ps, c.regola_speciale, c.attacchi_json, c.rarity, c.stage, c.illustratore
           FROM cards c JOIN expansions e ON e.id = c.expansion_id
           WHERE c.expansion_id = ?1 AND e.published = 1
             AND NOT EXISTS (SELECT 1 FROM takedowns t WHERE t.target_type = 'card' AND t.target_id = c.card_id)
           ORDER BY CAST(c.card_number AS INTEGER), c.card_number`
        )
        .bind(route.expansionId)
        .all<D1CardRow>();
      if (results.length === 0) return json({ error: 'expansion not found or has no cards' }, 404);

      const variants = new Map<string, string[]>();
      try {
        const { results: rows } = await db
          .prepare(
            `SELECT v.card_id, v.variant FROM card_variants v JOIN cards c ON c.card_id = v.card_id
             WHERE c.expansion_id = ?1 ORDER BY v.card_id, v.kind, v.variant`
          )
          .bind(route.expansionId)
          .all<{ card_id: string; variant: string }>();
        for (const row of rows) {
          const list = variants.get(row.card_id);
          if (list) list.push(row.variant); else variants.set(row.card_id, [row.variant]);
        }
      } catch (error) {
        console.error('catalogo EN: stampe illeggibili, le carte escono senza:', error);
      }
      return json({ expansionId: route.expansionId, cards: results.map((r) => mapCardRow(r, variants)) }, 200, CATALOG_CACHE_CONTROL);
    }

    case 'cardImage':
      return serveR2(env.IMAGES_BUCKET, [enCardImageKey(route.setCode, route.cardNumber)]);

    case 'setLogo':
      return serveR2(env.IMAGES_BUCKET, enSetAssetKeys(route.setCode, 'logo'));

    case 'setSymbol':
      return serveR2(env.IMAGES_BUCKET, enSetAssetKeys(route.setCode, 'symbol'));

    case 'prices':
    case 'expansionPrices': {
      const loaded = await loadEnglishPrices(env.IMAGES_BUCKET);
      if (!loaded) return json({ error: 'English price list not available' }, 404, MISSING_CACHE_CONTROL);
      const { snapshot, problem } = loaded;
      const headers: Record<string, string> = { 'Cache-Control': 'public, max-age=1800' };
      if (snapshot.cardmarketCreatedAt) headers['X-Listino-Created-At'] = snapshot.cardmarketCreatedAt;
      if (problem) headers['X-Listino-Problem'] = problem;
      if (route.kind === 'prices') {
        return new Response(JSON.stringify(snapshot), { status: 200, headers: { 'Content-Type': 'application/json; charset=utf-8', ...headers } });
      }
      const resolved = resolveExpansionPrices(snapshot, route.code);
      if (!resolved) return json({ error: `no prices for "${route.code}"` }, 404, MISSING_CACHE_CONTROL);
      const body = {
        expansionId: resolved.expansionId,
        baseSetCode: resolved.entry.baseSetCode,
        updatedAt: resolved.entry.updatedAt,
        builtAt: snapshot.builtAt,
        prices: resolved.entry.prices,
      };
      return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json; charset=utf-8', ...headers } });
    }
  }
}
