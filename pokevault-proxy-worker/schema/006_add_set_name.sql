-- Adds expansions.name: the Italian display name (e.g. "Buio Pesto", "Rivali
-- Predestinati"), sourced once from TCGdex's Italian locale (`GET /v2/it/sets/
-- {id}`, scripts/backfill-set-name-tcgdex.mjs) and owned by us in D1 from then
-- on. Fixes a real bug: the Pokedex previously showed a set's name borrowed at
-- runtime from a linked PokeWallet set, which fell back to a Japanese/Chinese
-- set's name when no English match existed for that raw set code (see
-- MIGRATION_PLAN.md M4.6, "Pokedex confusionario" follow-up).

ALTER TABLE expansions ADD COLUMN name TEXT;
