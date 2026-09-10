-- Adds expansions.series: the official series/generation name (e.g. "Scarlet &
-- Violet", "Sword & Shield", "Mega Evolution"), sourced once from TCGdex's own
-- `serie.name` taxonomy field (scripts/backfill-series-tcgdex.mjs) and owned
-- by us in D1 from then on. Replaces the ~150 lines of fuzzy name-matching
-- (deriveSeriesName/canonicalSeries in PokeTcgRepository.kt) that classified
-- ITA sets by guessing from a name that was often empty or wrong -- see
-- MIGRATION_PLAN.md M4.6 "Pokedex confusionario".

ALTER TABLE expansions ADD COLUMN series TEXT;
