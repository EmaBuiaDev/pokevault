// Quale espansione dello snapshot prezzi risponde a un codice richiesto
// (/ita/prices/{code}.json).
//
// Il codice puo' essere l'id dell'espansione (me05), il codice inglese (pbl)
// o il prefisso del card_id italiano (sv10), risolti da `aliases`. Il caso
// storto: un id di espansione che e' anche il prefisso delle carte di
// un'ALTRA espansione. Le carte della Galleria Allenatori di Astri Lucenti
// (swsh9tg) hanno card_id "SWSH9_IT_TG01", quindi lo snapshot scrive
// aliases.swsh9 = "swsh9tg", e chi chiedeva "swsh9" riceveva le 30 carte della
// galleria al posto delle 216 del set: il Moltres di Galar-V 183 restava senza
// prezzo. Stesso destino per swsh10, swsh11 e swsh45 (03/10/2026).
//
// Togliere l'alias non basta: l'app cerca le carte della galleria con lo
// stesso prefisso ("ita:swsh10:TG01"). Quindi un codice che e' esso stesso
// un'espansione risponde con le sue carte PIU' quelle dell'espansione a cui
// punta l'alias. I numeri delle gallerie (TG01..., SV001...) non si
// sovrappongono a quelli del set, salvo le 30 TG che swsh9 ha anche fra le
// sue: li chiedono solo le carte della galleria ("ita:swsh9:TG01"), quindi
// li vince la galleria, come prima della correzione.

export interface LookupExpansion<P> {
  prices: Record<string, P>;
}

export interface LookupSnapshot<E extends LookupExpansion<unknown>> {
  expansions: Record<string, E>;
  aliases?: Record<string, string>;
}

export function resolveExpansionPrices<E extends LookupExpansion<unknown>>(
  snapshot: LookupSnapshot<E>,
  code: string,
): { expansionId: string; entry: E } | null {
  const own = snapshot.expansions[code];
  const aliasTarget = snapshot.aliases?.[code];
  if (own) {
    const other = aliasTarget && aliasTarget !== code ? snapshot.expansions[aliasTarget] : undefined;
    if (!other) return { expansionId: code, entry: own };
    return { expansionId: code, entry: { ...own, prices: { ...own.prices, ...other.prices } } };
  }
  const expansionId = aliasTarget ?? code;
  const entry = snapshot.expansions[expansionId];
  return entry ? { expansionId, entry } : null;
}
