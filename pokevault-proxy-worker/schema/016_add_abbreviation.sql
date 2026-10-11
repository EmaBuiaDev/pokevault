-- Adds expansions.abbreviation: la sigla ufficiale dell'espansione ("PAF",
-- "SSH", "BLW"), quella stampata in basso sulle carte da Scarlatto e Violetto
-- in poi e usata come codice del gioco online per quelle prima. Presa una
-- volta da TCGdex (`abbreviation.official`, ripiego `tcgOnline`) con
-- scripts/backfill-set-symbols-tcgdex.mjs, che carica anche il simbolo su R2.
-- La mostra la tessera della ricerca carte accanto al simbolo. NULL dove
-- TCGdex non la conosce: l'app allora non mostra niente.

ALTER TABLE expansions ADD COLUMN abbreviation TEXT;
