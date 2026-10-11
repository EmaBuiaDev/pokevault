// Immagini statiche di un'espansione: il logo (/sets/{SETCODE}/image) e il
// simbolo (/sets/{SETCODE}/symbol). Fuori da index.ts per poterle provare:
// gli export del modulo principale il runtime li tratta come entrypoint.

/** Logo o simbolo di un'espansione: immagini statiche, non il payload del set. */
export function isSetImagePath(pathname: string): boolean {
  return /^\/sets\/[^/]+\/(image|symbol)$/i.test(pathname);
}

/** Il SETCODE di una richiesta del simbolo, in maiuscolo; null se non lo e'. */
export function parseSetSymbolCode(pathname: string): string | null {
  const match = pathname.match(/^\/sets\/([^/]+)\/symbol$/i);
  if (!match) return null;
  let raw: string;
  try {
    raw = decodeURIComponent(match[1]);
  } catch {
    return null;
  }
  const setCode = raw.trim().toUpperCase();
  return setCode || null;
}

/**
 * Dove cercare su R2 il simbolo: it/{SETCODE}/symbol.png, caricato da
 * scripts/backfill-set-symbols-tcgdex.mjs. WebP prima, come per loghi e
 * carte, se un giorno si ricomprimono.
 */
export function buildItalianSetSymbolCandidates(prefix: string, setCode: string): string[] {
  const basePath = `${prefix}/${setCode}`;
  return [`${basePath}/symbol.webp`, `${basePath}/symbol.png`];
}
