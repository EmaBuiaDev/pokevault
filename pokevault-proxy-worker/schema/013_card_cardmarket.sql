-- Carta del catalogo -> prodotto Cardmarket (idProduct del listino pubblico
-- price_guide_6.json). La riempie scripts/map-cardmarket-tcgdex.mjs, la legge
-- scripts/build-cardmarket-prices.mjs ogni mattina per mettere su R2 i prezzi
-- del giorno, che il Worker fonde sopra quelli PokeWallet (src/cardmarket-prices.ts).
--
-- Tabella a parte e non colonne in `cards`, di proposito:
--  - /v1/cards/{id} restituisce `SELECT c.*`: una colonna nuova cambierebbe la
--    risposta di una rotta che non c'entra;
--  - si svuota o si elimina senza toccare il catalogo, se si torna indietro.
-- Niente REFERENCES cards(card_id): D1 applica le chiavi esterne, e gli script
-- che ricostruiscono un set (DELETE FROM cards ...) fallirebbero per una riga
-- qui. Una riga orfana non fa danni: build-cardmarket-prices.mjs fa JOIN su cards.
--
-- source: da dove viene il collegamento (vedi le regole nello script)
--   'both'       TCGdex e PokeWallet indicano lo stesso prodotto
--   'tcgdex'     solo TCGdex, passato i controlli (prodotto non condiviso,
--                prezzo non lontano da TCGplayer)
--   'pokewallet' il prodotto che PokeWallet usa oggi
--   'arbiter'    TCGdex e PokeWallet in disaccordo, deciso dal prezzo TCGplayer

CREATE TABLE IF NOT EXISTS card_cardmarket (
  card_id     TEXT PRIMARY KEY,
  id_product  INTEGER NOT NULL,
  source      TEXT NOT NULL,
  updated_at  INTEGER NOT NULL DEFAULT (unixepoch())
);
