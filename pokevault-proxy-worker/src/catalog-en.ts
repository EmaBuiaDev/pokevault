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
import { CARDMARKET_EN_R2_KEY, CATALOG_EN_MAX_AGE_MS, CATALOG_EN_R2_KEY, enCardImageKey, enSetAssetKeys, parseEnglishRequest } from './catalog-en-paths';
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
  /** Il catalogo italiano, in sola lettura: il nome italiano dei set gemelli. */
  pokevault_catalog?: D1Database;
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
 * Tutte le carte pubblicate, per la ricerca dell'app ({cards: [...]}, la forma
 * di /v1/en/expansions/{id}/cards). Costruirlo legge ventimila righe di D1, piu'
 * le stampe: si tiene su R2 e si rifa' solo quando ha piu' di
 * CATALOG_EN_MAX_AGE_MS, cosi' l'import notturno compare entro qualche ora
 * senza che ogni apertura dell'app costi la quota.
 */
async function serveEnglishCatalog(db: D1Database, bucket: R2Bucket | undefined): Promise<Response> {
  const headers = {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Cache-Control': CATALOG_CACHE_CONTROL,
  };
  const stored = bucket ? await bucket.get(CATALOG_EN_R2_KEY).catch(() => null) : null;
  if (stored && Date.now() - stored.uploaded.getTime() < CATALOG_EN_MAX_AGE_MS) {
    return new Response(stored.body, { status: 200, headers: { ...headers, 'X-Catalog-Built-At': stored.uploaded.toISOString() } });
  }

  try {
    const { results } = await db
      .prepare(
        `SELECT c.card_id, c.expansion_id, c.nome, c.tipo, c.ps, c.regola_speciale, c.attacchi_json, c.rarity, c.stage, c.illustratore
         FROM cards c JOIN expansions e ON e.id = c.expansion_id
         WHERE e.published = 1
           AND NOT EXISTS (SELECT 1 FROM takedowns t WHERE (t.target_type = 'card' AND t.target_id = c.card_id) OR (t.target_type = 'expansion' AND t.target_id = c.expansion_id))
         ORDER BY c.expansion_id, CAST(c.card_number AS INTEGER), c.card_number`
      )
      .all<D1CardRow>();
    if (results.length === 0) throw new Error('nessuna carta pubblicata');

    const variants = new Map<string, string[]>();
    try {
      const { results: rows } = await db
        .prepare(
          `SELECT v.card_id, v.variant FROM card_variants v JOIN cards c ON c.card_id = v.card_id
           JOIN expansions e ON e.id = c.expansion_id
           WHERE e.published = 1 ORDER BY v.card_id, v.kind, v.variant`
        )
        .all<{ card_id: string; variant: string }>();
      for (const row of rows) {
        const list = variants.get(row.card_id);
        if (list) list.push(row.variant); else variants.set(row.card_id, [row.variant]);
      }
    } catch (error) {
      console.error('catalogo EN: stampe illeggibili, il catalogo esce senza:', error);
    }

    const body = JSON.stringify({ cards: results.map((r) => mapCardRow(r, variants)) });
    if (bucket) {
      await bucket.put(CATALOG_EN_R2_KEY, body, { httpMetadata: { contentType: 'application/json; charset=utf-8' } })
        .catch((error) => console.error('catalogo EN: copia su R2 non salvata:', error));
    }
    return new Response(body, { status: 200, headers: { ...headers, 'X-Catalog-Built-At': new Date().toISOString() } });
  } catch (error) {
    // D1 non risponde: meglio la copia vecchia che niente.
    console.error('catalogo EN: catalogo completo non ricostruito:', error);
    if (stored) {
      return new Response(stored.body, { status: 200, headers: { ...headers, 'X-Catalog-Built-At': stored.uploaded.toISOString() } });
    }
    return json({ error: 'English catalog not available' }, 503, MISSING_CACHE_CONTROL);
  }
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
      // Il nome italiano del gemello: l'app salva la carta inglese con quello, e
      // la Collezione la mette nella stessa tessera della carta italiana. Se il
      // catalogo italiano non risponde, il set esce senza (l'app ripiega).
      const itNames = new Map<string, string>();
      if (env.pokevault_catalog && results.some((r) => r.it_expansion_id)) {
        try {
          const { results: itRows } = await env.pokevault_catalog
            .prepare('SELECT id, name FROM expansions WHERE name IS NOT NULL')
            .all<{ id: string; name: string }>();
          for (const row of itRows) itNames.set(row.id, row.name);
        } catch (error) {
          console.error('catalogo EN: nomi italiani dei gemelli illeggibili:', error);
        }
      }
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
        itName: r.it_expansion_id ? itNames.get(r.it_expansion_id) ?? null : null,
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

    case 'catalog':
      return serveEnglishCatalog(db, env.IMAGES_BUCKET);

    // Stessa forma di /v1/illustrators e /v1/illustrators/{nome}/cards.
    case 'illustrators': {
      const takedownSql = `NOT EXISTS (SELECT 1 FROM takedowns t WHERE (t.target_type = 'card' AND t.target_id = c.card_id) OR (t.target_type = 'expansion' AND t.target_id = c.expansion_id))`;
      const { results } = await db
        .prepare(
          `SELECT c.illustratore AS name, c.card_id, c.expansion_id
           FROM cards c JOIN expansions e ON e.id = c.expansion_id
           WHERE e.published = 1 AND c.illustratore IS NOT NULL AND TRIM(c.illustratore) <> '' AND ${takedownSql}
           ORDER BY c.illustratore`
        )
        .all<{ name: string; card_id: string; expansion_id: string }>();
      const byName = new Map<string, { cardIds: string[]; expansions: Set<string> }>();
      for (const row of results) {
        const name = row.name.trim();
        if (!name) continue;
        let entry = byName.get(name);
        if (!entry) {
          entry = { cardIds: [], expansions: new Set() };
          byName.set(name, entry);
        }
        entry.cardIds.push(row.card_id);
        entry.expansions.add(row.expansion_id);
      }
      const illustrators = Array.from(byName.entries())
        .map(([name, entry]) => ({ name, cardCount: entry.cardIds.length, expansionCount: entry.expansions.size, cardIds: entry.cardIds }))
        .sort((a, b) => b.cardCount - a.cardCount || a.name.localeCompare(b.name));
      const missing = await db
        .prepare(
          `SELECT COUNT(*) AS n FROM cards c JOIN expansions e ON e.id = c.expansion_id
           WHERE e.published = 1 AND (c.illustratore IS NULL OR TRIM(c.illustratore) = '') AND ${takedownSql}`
        )
        .first<{ n: number }>();
      return json({ illustrators, cardsWithoutIllustrator: missing?.n ?? 0 }, 200, CATALOG_CACHE_CONTROL);
    }

    case 'illustratorCards': {
      const { results } = await db
        .prepare(
          `SELECT c.card_id, c.expansion_id, c.nome, c.tipo, c.ps, c.regola_speciale, c.attacchi_json, c.rarity, c.stage, c.illustratore
           FROM cards c JOIN expansions e ON e.id = c.expansion_id
           WHERE c.illustratore = ?1 AND e.published = 1
             AND NOT EXISTS (SELECT 1 FROM takedowns t WHERE (t.target_type = 'card' AND t.target_id = c.card_id) OR (t.target_type = 'expansion' AND t.target_id = c.expansion_id))
           ORDER BY c.expansion_id, CAST(c.card_number AS INTEGER), c.card_number`
        )
        .bind(route.name)
        .all<D1CardRow>();
      if (results.length === 0) return json({ error: 'illustrator not found or has no cards' }, 404);
      return json({ illustrator: route.name, cards: results.map((r) => mapCardRow(r)) }, 200, CATALOG_CACHE_CONTROL);
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
