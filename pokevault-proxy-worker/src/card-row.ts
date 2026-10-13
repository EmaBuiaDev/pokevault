// Il contratto JSON di una carta del catalogo, uguale per italiano e inglese.
// Fuori da index.ts perche' lo usa anche src/catalog-en.ts: una sola funzione
// fa si' che le due lingue non possano divergere sui nomi dei campi, che
// devono restare quelli di ItalianCardRecord lato Android.

export type D1CardRow = {
  card_id: string;
  expansion_id: string;
  nome: string;
  tipo: string | null;
  ps: string | null;
  regola_speciale: string | null;
  attacchi_json: string;
  rarity: string | null;
  stage: string | null;
  illustratore: string | null;
};

// Shared row -> payload mapping, used by both /ita/catalog.json (buildCatalogJsonFromD1)
// and /v1/expansions/{id}/cards, so the two endpoints can never drift apart on field
// names -- both must match ItalianCardRecord on the Android side (cardId, espansioneId,
// nome, tipo, ps, attacchi, regolaSpeciale, rarity, stage).
export function mapCardRow(r: D1CardRow, variants?: Map<string, string[]>) {
  const stampe = variants?.get(r.card_id);
  return {
    cardId: r.card_id,
    espansioneId: r.expansion_id,
    nome: r.nome,
    tipo: r.tipo,
    ps: r.ps,
    attacchi: JSON.parse(r.attacchi_json || '[]'),
    regolaSpeciale: r.regola_speciale,
    rarity: r.rarity,
    // Lo stadio evolutivo (schema/009): e' il campo con cui l'app distingue un
    // Pokemon che si puo' calare in campo dalla mano da uno che va evoluto.
    stage: r.stage,
    // Omesso quando manca invece di serializzare null: lo ha solo il catalogo
    // storico preso dal wiki, e /ita/catalog.json porta sedicimila carte --
    // una chiave nulla per ognuna sarebbe peso puro per ogni client.
    ...(r.illustratore ? { illustratore: r.illustratore } : {}),
    // Le stampe vere della carta (schema/015, scripts/build-card-variants.mjs):
    // "Normal", "Reverse", "Holo", "1st Edition"... e le speciali
    // ("Reverse|foil=pokeball"). Omesse quando non ci sono: l'app ripiega
    // sulla rarita', come prima che la tabella esistesse.
    ...(stampe && stampe.length ? { stampe } : {}),
  };
}
