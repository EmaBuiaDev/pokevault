// Abbinare carte del nostro catalogo ai prodotti del catalogo pubblico di
// Cardmarket (products_singles_6.json), senza rete: solo funzioni pure, per i
// test (test/cardmarket-catalog-match.test.ts).
//
// Il catalogo Cardmarket scrive i Pokemon col nome e gli attacchi
// ("Snivy [Tackle | Vine Whip]"), Allenatori ed Energie col solo nome
// ("Professor Juniper"). Lo stesso nome sta in tante espansioni: prima si
// decide l'espansione del nostro set, poi la carta dentro quella.

/** Minuscolo, senza accenti, senza segni: "Charizard-EX" e "Charizard EX" sono uguali. */
export function normText(raw) {
  return String(raw ?? '').normalize('NFD').replace(/\p{Mn}+/gu, '').toLowerCase().replace(/[^a-z0-9]+/g, '');
}

/** "Snivy [Tackle | Vine Whip]" -> { name: "snivy", attacks: ["tackle", "vinewhip"] }. */
export function parseProductName(name) {
  const m = /^(.*?)\s*\[([^\]]*)\]\s*$/.exec(String(name ?? ''));
  if (!m) return { name: normText(name), attacks: [] };
  return { name: normText(m[1]), attacks: m[2].split('|').map(normText).filter(Boolean) };
}

/** La chiave di confronto: nome, e per chi li ha gli attacchi in ordine. */
export function matchKey(name, attacks) {
  const list = (attacks ?? []).map(normText).filter(Boolean).sort();
  return `${normText(name)}|${list.join(',')}`;
}

/**
 * Collega le carte senza prodotto.
 *
 * @param cards      [{ card_id, expansion_id, nome, attacks: [..] }] da collegare
 * @param linked     [{ expansion_id, id_product }] le carte gia' collegate (per l'espansione)
 * @param products   [{ idProduct, name, idExpansion }] il catalogo Cardmarket
 * @returns { links: [{card_id, id_product}], perSet: {exp: {idExpansion, linked, ambiguous, none}} }
 */
export function matchCatalog(cards, linked, products) {
  const byId = new Map(products.map((p) => [p.idProduct, p]));
  const byKey = new Map();
  const byName = new Map();
  for (const p of products) {
    const parsed = parseProductName(p.name);
    const key = matchKey(parsed.name, parsed.attacks);
    if (!byKey.has(key)) byKey.set(key, []);
    byKey.get(key).push(p);
    if (!byName.has(parsed.name)) byName.set(parsed.name, []);
    byName.get(parsed.name).push(p);
  }
  const candidatesOf = (card) => (card.attacks?.length
    ? byKey.get(matchKey(card.nome, card.attacks))
    : byName.get(normText(card.nome))) ?? [];

  const groups = new Map();
  for (const c of cards) {
    if (!groups.has(c.expansion_id)) groups.set(c.expansion_id, []);
    groups.get(c.expansion_id).push(c);
  }

  const links = [];
  const perSet = {};
  for (const [exp, list] of groups) {
    // L'espansione Cardmarket per COPERTURA: ogni carta vota per tutte le
    // espansioni in cui ha un candidato, le carte gia' collegate valgono 3
    // (sono certe). Contare solo i candidati unici sbagliava: i promo BW
    // finivano nella raccolta delle timbrate (4218), dove molte carte hanno un
    // solo candidato, invece che nei promo veri (1611), che ne coprono di piu'.
    // A pari merito non si sceglie.
    const votes = new Map();
    const vote = (id, w) => { if (id != null) votes.set(id, (votes.get(id) ?? 0) + w); };
    for (const l of linked) if (l.expansion_id === exp) vote(byId.get(l.id_product)?.idExpansion, 3);
    for (const c of list) {
      for (const e of new Set(candidatesOf(c).map((p) => p.idExpansion))) vote(e, 1);
    }
    const ranked = [...votes].sort((a, b) => b[1] - a[1]);
    const best = ranked[0] && (!ranked[1] || ranked[0][1] > ranked[1][1]) ? ranked[0] : null;
    const stats = { idExpansion: best?.[0] ?? null, linked: 0, ambiguous: [], none: [] };
    perSet[exp] = stats;
    if (!best) { stats.none = list.map((c) => c.card_id); continue; }

    const taken = new Set(linked.filter((l) => l.expansion_id === exp).map((l) => l.id_product));
    for (const c of list) {
      const inExp = candidatesOf(c).filter((p) => p.idExpansion === best[0] && !taken.has(p.idProduct));
      if (inExp.length === 1) {
        links.push({ card_id: c.card_id, id_product: inExp[0].idProduct });
        taken.add(inExp[0].idProduct);
        stats.linked += 1;
      } else if (inExp.length > 1) {
        stats.ambiguous.push(c.card_id);
      } else {
        stats.none.push(c.card_id);
      }
    }
  }
  return { links, perSet };
}
