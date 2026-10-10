-- Le stampe vere di ogni carta: una riga per stampa (Normale, Reverse, Holo,
-- 1a Edizione, e le speciali con timbro o lavorazione).
--
-- La scrive scripts/build-card-variants.mjs incrociando pokemontcg.io,
-- TCGdex (variants_detailed) e il listino Cardmarket; prima di questa tabella
-- l'app deduceva le stampe dalla rarita' e sbagliava interi set (il 30
-- Anniversario non ha reverse, 151 ha le reverse Poke Ball e Master Ball...).
--
-- variant: la chiave che l'app salva in Firestore. Le base sono le stringhe di
--   sempre ("Normal", "Reverse", "Holo", "1st Edition", "1st Edition Holo"),
--   cosi' le carte gia' salvate restano valide; le speciali aggiungono
--   lavorazione e timbri: "Reverse|foil=pokeball", "Normal|stamp=pokemon-day".
-- kind: 'base' o 'special' (l'app le mostra in due gruppi).
-- id_product: il prodotto Cardmarket che da' il prezzo di QUELLA stampa
--   (NULL = si usa quello della carta in card_cardmarket).
-- source: da dove viene la riga ('pokemontcg', 'tcgdex', 'pokemontcg+tcgdex',
--   'regola', 'manuale').
CREATE TABLE IF NOT EXISTS card_variants (
  card_id     TEXT NOT NULL,
  variant     TEXT NOT NULL,
  kind        TEXT NOT NULL,
  type        TEXT NOT NULL,
  stamp       TEXT,
  foil        TEXT,
  id_product  INTEGER,
  source      TEXT NOT NULL,
  PRIMARY KEY (card_id, variant)
);
